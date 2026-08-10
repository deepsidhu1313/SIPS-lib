/*
 * Copyright (C) 2026 Navdeep Singh Sidhu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package in.co.s13.sips.lib.job;

import in.co.s13.sips.lib.manifest.TaskType;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Reads a pipeline out of a job's manifest.
 *
 * <p>The manifest is what a submitter, the IDE and the node all agree on, so the
 * graph has to survive the trip as JSON rather than only existing as Java calls:
 *
 * <pre>{@code
 * {
 *   "PROJECT": "mri-pipeline",
 *   "SCHEDULER": { "Name": "in.co.s13.sips.schedulers.GSS" },
 *   "STAGES": [
 *     { "NAME": "load",     "KIND": "single" },
 *     { "NAME": "bias",     "KIND": "parallelFor", "FIRST": 0, "LAST": 256,
 *       "AFTER": ["load"] },
 *     { "NAME": "register", "KIND": "single", "AFTER": ["bias"],
 *       "TIMEOUT": 600 },
 *     { "NAME": "segment",  "KIND": "parallelFor", "FIRST": 0, "LAST": 256,
 *       "AFTER": ["register"], "TYPE": "wasm" }
 *   ]
 * }
 * }</pre>
 *
 * <p>A manifest with no {@code STAGES} is a single-loop job, which is what
 * almost every existing manifest is — {@link #hasStages} is how a caller tells
 * the two apart without guessing.
 *
 * <p>Everything is checked here rather than at distribution time. A stage naming
 * a predecessor that does not exist, or a cycle, is a mistake a person made
 * while writing the file; finding it while reading the file lets the error name
 * the line rather than the symptom.
 */
public final class JobManifest {

    private JobManifest() {
    }

    /** Whether this manifest describes a pipeline rather than a single loop. */
    public static boolean hasStages(JSONObject manifest) {
        return manifest != null && manifest.optJSONArray("STAGES") != null
                && manifest.getJSONArray("STAGES").length() > 0;
    }

    /**
     * Builds the graph a manifest describes.
     *
     * @param jobName used as the job's name; usually the project or job token
     * @throws IllegalArgumentException if a stage is malformed or names a
     *         predecessor that does not exist
     * @throws IllegalStateException if the resulting graph has a cycle
     */
    public static Job read(String jobName, JSONObject manifest) {
        if (!hasStages(manifest)) {
            throw new IllegalArgumentException("Manifest for '" + jobName
                    + "' declares no STAGES");
        }
        JSONArray declared = manifest.getJSONArray("STAGES");
        Job job = new Job(jobName);
        Map<String, JSONObject> byName = new LinkedHashMap<>();

        // Two passes: every stage exists before any edge is drawn, so a stage
        // may name a predecessor declared after it.
        for (int i = 0; i < declared.length(); i++) {
            JSONObject entry = declared.getJSONObject(i);
            String name = entry.optString("NAME", "");
            if (name.isBlank()) {
                throw new IllegalArgumentException("Stage " + i + " of '" + jobName
                        + "' has no NAME");
            }
            byName.put(name, entry);
            configure(addStage(job, name, entry), entry);
        }

        for (Map.Entry<String, JSONObject> entry : byName.entrySet()) {
            Stage stage = job.stage(entry.getKey()).orElseThrow();
            link(job, stage, entry.getValue().optJSONArray("AFTER"), false);
            link(job, stage, entry.getValue().optJSONArray("READS"), true);
        }

        job.validate();
        return job;
    }

    private static void link(Job job, Stage stage, JSONArray named, boolean reads) {
        if (named == null) {
            return;
        }
        for (int i = 0; i < named.length(); i++) {
            String predecessor = named.getString(i);
            Stage producer = job.stage(predecessor).orElseThrow(() ->
                    new IllegalArgumentException("Stage '" + stage.name() + "' "
                            + (reads ? "reads" : "runs after") + " '" + predecessor
                            + "', which this job does not declare"));
            if (reads) {
                stage.reads(producer);
            } else {
                stage.after(producer);
            }
        }
    }

    private static Stage addStage(Job job, String name, JSONObject entry) {
        String kind = entry.optString("KIND", "parallelFor");
        if (kind.equalsIgnoreCase("single")) {
            return job.single(name);
        }
        if (!kind.equalsIgnoreCase("parallelFor")) {
            throw new IllegalArgumentException("Stage '" + name + "' has unknown KIND '"
                    + kind + "'; expected single or parallelFor");
        }
        if (!entry.has("FIRST") || !entry.has("LAST")) {
            throw new IllegalArgumentException("Stage '" + name
                    + "' is a parallelFor and needs FIRST and LAST");
        }
        return job.parallelFor(name, entry.getLong("FIRST"), entry.getLong("LAST"));
    }

    private static void configure(Stage stage, JSONObject entry) {
        if (entry.has("TYPE")) {
            stage.type(TaskType.of(entry.getString("TYPE")));
        }
        if (entry.has("WRITES")) {
            stage.writes(entry.getString("WRITES"));
        }
        if (entry.has("TIMEOUT")) {
            long seconds = entry.getLong("TIMEOUT");
            if (seconds <= 0) {
                throw new IllegalArgumentException("Stage '" + stage.name()
                        + "' has a TIMEOUT of " + seconds + " seconds");
            }
            stage.timeout(Duration.ofSeconds(seconds));
        }
    }

    /** Writes a graph back out, so a submitter can build one in Java and ship it. */
    public static JSONArray write(Job job) {
        JSONArray stages = new JSONArray();
        for (Stage stage : job.stages()) {
            JSONObject entry = new JSONObject();
            entry.put("NAME", stage.name());
            entry.put("KIND", stage.kind() == Stage.Kind.SINGLE ? "single" : "parallelFor");
            if (stage.kind() == Stage.Kind.PARALLEL_FOR) {
                entry.put("FIRST", stage.firstIndex());
                entry.put("LAST", stage.lastIndexExclusive());
            }
            // Split: a stage read from is already ordered by READS, so listing
            // it under AFTER as well would say the same thing twice.
            JSONArray after = new JSONArray();
            stage.dependencies().stream()
                    .filter(predecessor -> !stage.inputs().contains(predecessor))
                    .forEach(predecessor -> after.put(predecessor.name()));
            if (after.length() > 0) {
                entry.put("AFTER", after);
            }
            if (!stage.inputs().isEmpty()) {
                JSONArray reads = new JSONArray();
                stage.inputs().forEach(producer -> reads.put(producer.name()));
                entry.put("READS", reads);
            }
            stage.output().ifPresent(pattern -> entry.put("WRITES", pattern));
            if (stage.taskType() != TaskType.DEFAULT) {
                entry.put("TYPE", stage.taskType().manifestValue());
            }
            stage.timeout().ifPresent(timeout -> entry.put("TIMEOUT", timeout.toSeconds()));
            stages.put(entry);
        }
        return stages;
    }
}
