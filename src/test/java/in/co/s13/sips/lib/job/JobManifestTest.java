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
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A pipeline that survives the trip through a manifest.
 *
 * <p>The submitter, the IDE and the node all read the same file, so the graph
 * has to exist as JSON and not only as Java calls. And because it is a file a
 * person edits, its mistakes are ordinary human ones — a misspelled predecessor,
 * a missing bound, an accidental loop — which is why they are caught while
 * reading rather than by watching a job make no progress.
 */
class JobManifestTest {

    private static JSONObject stage(String name, String kind) {
        return new JSONObject().put("NAME", name).put("KIND", kind);
    }

    private static JSONObject pipeline() {
        JSONArray stages = new JSONArray()
                .put(stage("load", "single"))
                .put(stage("bias", "parallelFor").put("FIRST", 0).put("LAST", 256)
                        .put("AFTER", new JSONArray().put("load")))
                .put(stage("register", "single")
                        .put("AFTER", new JSONArray().put("bias")).put("TIMEOUT", 600))
                .put(stage("segment", "parallelFor").put("FIRST", 0).put("LAST", 256)
                        .put("AFTER", new JSONArray().put("register")).put("TYPE", "wasm"));
        return new JSONObject().put("PROJECT", "mri-pipeline").put("STAGES", stages);
    }

    @Test
    void aManifestBecomesTheGraphItDescribes() {
        Job job = JobManifest.read("mri-pipeline", pipeline());

        assertEquals(4, job.stages().size());
        assertEquals(List.of(job.stage("load").orElseThrow()), job.roots());
        assertEquals(Stage.Kind.SINGLE, job.stage("register").orElseThrow().kind());
        assertEquals(256, job.stage("bias").orElseThrow().iterationCount());
    }

    @Test
    void perStageSettingsSurviveTheTrip() {
        Job job = JobManifest.read("mri-pipeline", pipeline());

        assertEquals(TaskType.WASM, job.stage("segment").orElseThrow().taskType());
        assertEquals(TaskType.JAVA, job.stage("bias").orElseThrow().taskType());
        assertEquals(Duration.ofMinutes(10),
                job.stage("register").orElseThrow().timeout().orElseThrow());
        assertTrue(job.stage("bias").orElseThrow().timeout().isEmpty());
    }

    @Test
    void aStageMayNameAPredecessorDeclaredBelowIt() {
        // A manifest is written by hand; insisting on declaration order would be
        // a rule with no purpose beyond making the parser simpler.
        JSONArray stages = new JSONArray()
                .put(stage("second", "single").put("AFTER", new JSONArray().put("first")))
                .put(stage("first", "single"));

        Job job = JobManifest.read("j", new JSONObject().put("STAGES", stages));

        assertEquals(List.of(job.stage("first").orElseThrow()), job.roots());
    }

    @Test
    void parallelForIsTheDefaultKind() {
        JSONArray stages = new JSONArray().put(
                new JSONObject().put("NAME", "work").put("FIRST", 0).put("LAST", 10));

        Job job = JobManifest.read("j", new JSONObject().put("STAGES", stages));

        assertEquals(Stage.Kind.PARALLEL_FOR, job.stage("work").orElseThrow().kind());
    }

    // ---- what a bad manifest gets told ----

    @Test
    void aMisspelledPredecessorIsNamed() {
        // The single most likely mistake in a hand-written pipeline. Left to
        // runtime it looks like a stage that simply never starts.
        JSONArray stages = new JSONArray()
                .put(stage("load", "single"))
                .put(stage("bias", "parallelFor").put("FIRST", 0).put("LAST", 10)
                        .put("AFTER", new JSONArray().put("laod")));

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> JobManifest.read("j", new JSONObject().put("STAGES", stages)));
        assertTrue(thrown.getMessage().contains("laod"), thrown.getMessage());
    }

    @Test
    void aCycleInTheFileIsRefused() {
        JSONArray stages = new JSONArray()
                .put(stage("a", "single").put("AFTER", new JSONArray().put("b")))
                .put(stage("b", "single").put("AFTER", new JSONArray().put("a")));

        assertThrows(IllegalStateException.class,
                () -> JobManifest.read("j", new JSONObject().put("STAGES", stages)));
    }

    @Test
    void aParallelStageWithoutBoundsIsRefused() {
        JSONArray stages = new JSONArray().put(stage("work", "parallelFor"));

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> JobManifest.read("j", new JSONObject().put("STAGES", stages)))
                .getMessage().contains("FIRST and LAST"));
    }

    @Test
    void anUnknownKindNamesWhatIsAllowed() {
        JSONArray stages = new JSONArray().put(stage("work", "mapReduce"));

        String message = assertThrows(IllegalArgumentException.class,
                () -> JobManifest.read("j", new JSONObject().put("STAGES", stages)))
                .getMessage();
        assertTrue(message.contains("mapReduce"));
        assertTrue(message.contains("parallelFor"), message);
    }

    @Test
    void aStageWithoutANameIsRefused() {
        JSONArray stages = new JSONArray().put(new JSONObject().put("KIND", "single"));

        assertThrows(IllegalArgumentException.class,
                () -> JobManifest.read("j", new JSONObject().put("STAGES", stages)));
    }

    @Test
    void aNonsenseTimeoutIsRefused() {
        JSONArray stages = new JSONArray().put(stage("work", "single").put("TIMEOUT", 0));

        assertThrows(IllegalArgumentException.class,
                () -> JobManifest.read("j", new JSONObject().put("STAGES", stages)));
    }

    // ---- telling a pipeline from an ordinary job ----

    @Test
    void anOrdinaryManifestIsNotAPipeline() {
        // Almost every manifest in existence. Reading one as a pipeline, or
        // refusing it, would break every job already written.
        assertFalse(JobManifest.hasStages(new JSONObject().put("PROJECT", "mandelbrot")));
        assertFalse(JobManifest.hasStages(new JSONObject().put("STAGES", new JSONArray())));
        assertFalse(JobManifest.hasStages(null));
        assertTrue(JobManifest.hasStages(pipeline()));
    }

    @Test
    void readingAManifestWithoutStagesSaysSo() {
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> JobManifest.read("j", new JSONObject()))
                .getMessage().contains("STAGES"));
    }

    // ---- the return trip ----

    @Test
    void aGraphBuiltInJavaCanBeWrittenBackOut() {
        // A submitter builds the pipeline in code; the node reads it from the
        // file. If the two are not the same graph, the submitter is debugging
        // something they never wrote.
        Job original = JobManifest.read("mri-pipeline", pipeline());

        Job roundTripped = JobManifest.read("mri-pipeline",
                new JSONObject().put("STAGES", JobManifest.write(original)));

        assertEquals(original.stages().size(), roundTripped.stages().size());
        for (Stage stage : original.stages()) {
            Stage copy = roundTripped.stage(stage.name()).orElseThrow();
            assertEquals(stage.kind(), copy.kind());
            assertEquals(stage.firstIndex(), copy.firstIndex());
            assertEquals(stage.lastIndexExclusive(), copy.lastIndexExclusive());
            assertEquals(stage.taskType(), copy.taskType());
            assertEquals(stage.timeout(), copy.timeout());
            assertEquals(stage.dependencies().size(), copy.dependencies().size());
        }
    }
}
