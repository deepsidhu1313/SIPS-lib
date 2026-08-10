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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A pipeline of {@link Stage}s and the order they must run in.
 *
 * <p>A parallel loop splits one iteration space across nodes. Real work is
 * rarely one loop — an imaging pipeline loads a volume, corrects every slice,
 * registers the whole thing, segments every slice again, then merges — and
 * today that has to be submitted as several separate jobs with someone waiting
 * in between. The cluster drains to idle at every boundary.
 *
 * <p>A job says the ordering once, so the framework can keep the cluster busy
 * across it:
 *
 * <pre>{@code
 * Job job = new Job("mri-pipeline");
 * Stage load     = job.single("load");
 * Stage correct  = job.parallelFor("bias", 0, slices).after(load);
 * Stage register = job.single("register").after(correct);
 * Stage segment  = job.parallelFor("segment", 0, slices).after(register);
 * job.single("merge").after(segment);
 * }</pre>
 *
 * <p>This type is the graph and its rules; {@link JobSequencer} decides what may
 * run when. Keeping them apart means the graph can be inspected, drawn and
 * validated without pretending to execute anything.
 *
 * <p>Not thread-safe while being built. Build it on one thread, then hand it to
 * a sequencer.
 */
public final class Job {

    private final String name;
    private final Map<String, Stage> stages = new LinkedHashMap<>();

    public Job(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A job needs a name");
        }
        this.name = name.trim();
    }

    public String name() {
        return name;
    }

    /**
     * Adds a stage that runs as one task on one node.
     *
     * <p>Modelled as an iteration space of one, so everything downstream treats
     * it like any other stage.
     */
    public Stage single(String stageName) {
        return add(new Stage(this, validName(stageName), Stage.Kind.SINGLE, 0, 1));
    }

    /**
     * Adds a stage whose iteration space is split across nodes, exactly as
     * {@code sim.parallelFor()} splits one today.
     *
     * @param lastIndexExclusive one past the last iteration
     */
    public Stage parallelFor(String stageName, long firstIndex, long lastIndexExclusive) {
        if (lastIndexExclusive < firstIndex) {
            throw new IllegalArgumentException("Stage '" + stageName + "' has an inverted range: "
                    + firstIndex + ".." + lastIndexExclusive);
        }
        return add(new Stage(this, validName(stageName), Stage.Kind.PARALLEL_FOR,
                firstIndex, lastIndexExclusive));
    }

    private String validName(String stageName) {
        if (stageName == null || stageName.isBlank()) {
            throw new IllegalArgumentException("A stage needs a name");
        }
        return stageName.trim();
    }

    private Stage add(Stage stage) {
        if (stages.containsKey(stage.name())) {
            // Names are how a stage is referred to in logs, in the evaluator and
            // by whoever is debugging at 2am. Two of them is not worth the
            // convenience of allowing it.
            throw new IllegalArgumentException("Job '" + name + "' already has a stage named '"
                    + stage.name() + "'");
        }
        stages.put(stage.name(), stage);
        return stage;
    }

    /** Every stage, in the order it was declared. */
    public Collection<Stage> stages() {
        return Collections.unmodifiableCollection(stages.values());
    }

    public Optional<Stage> stage(String stageName) {
        return Optional.ofNullable(stages.get(stageName));
    }

    /** The stages with no dependencies — where execution starts. */
    public List<Stage> roots() {
        List<Stage> roots = new ArrayList<>();
        for (Stage stage : stages.values()) {
            if (stage.dependencies().isEmpty()) {
                roots.add(stage);
            }
        }
        return roots;
    }

    /**
     * Checks the graph can actually be run.
     *
     * <p>Called by {@link JobSequencer} before anything is scheduled. Finding a
     * cycle here costs a millisecond; finding it by watching a job sit at zero
     * progress costs an afternoon.
     *
     * @throws IllegalStateException if the job is empty or contains a cycle,
     *         naming the stages involved
     */
    public void validate() {
        if (stages.isEmpty()) {
            throw new IllegalStateException("Job '" + name + "' has no stages");
        }
        List<Stage> cycle = findCycle();
        if (cycle != null) {
            StringBuilder path = new StringBuilder();
            for (Stage stage : cycle) {
                path.append(stage.name()).append(" -> ");
            }
            path.append(cycle.get(0).name());
            throw new IllegalStateException("Job '" + name + "' has a dependency cycle: " + path);
        }
    }

    /** Whether the graph is runnable, for callers that would rather ask than catch. */
    public boolean isValid() {
        return !stages.isEmpty() && findCycle() == null;
    }

    /**
     * Every stage in an order where each appears after everything it depends on.
     *
     * <p>Not the execution order — independent stages run at the same time. This
     * is for printing a job, and for anything that needs to walk the graph once
     * without revisiting.
     *
     * @throws IllegalStateException if the job is empty or contains a cycle
     */
    public List<Stage> inDependencyOrder() {
        validate();
        Map<Stage, Integer> remaining = new LinkedHashMap<>();
        Map<Stage, List<Stage>> dependents = new LinkedHashMap<>();
        for (Stage stage : stages.values()) {
            remaining.put(stage, stage.dependencies().size());
            dependents.computeIfAbsent(stage, key -> new ArrayList<>());
        }
        for (Stage stage : stages.values()) {
            for (Stage dependency : stage.dependencies()) {
                dependents.computeIfAbsent(dependency, key -> new ArrayList<>()).add(stage);
            }
        }

        Deque<Stage> ready = new ArrayDeque<>();
        remaining.forEach((stage, count) -> {
            if (count == 0) {
                ready.add(stage);
            }
        });

        List<Stage> ordered = new ArrayList<>(stages.size());
        while (!ready.isEmpty()) {
            Stage stage = ready.poll();
            ordered.add(stage);
            for (Stage dependent : dependents.get(stage)) {
                if (remaining.merge(dependent, -1, Integer::sum) == 0) {
                    ready.add(dependent);
                }
            }
        }
        return ordered;
    }

    /** The stages on a cycle, or null if there is none. */
    private List<Stage> findCycle() {
        Set<Stage> settled = new HashSet<>();
        Set<Stage> onPath = new LinkedHashSet<>();
        for (Stage stage : stages.values()) {
            List<Stage> cycle = walk(stage, settled, onPath);
            if (cycle != null) {
                return cycle;
            }
        }
        return null;
    }

    private List<Stage> walk(Stage stage, Set<Stage> settled, Set<Stage> onPath) {
        if (settled.contains(stage)) {
            return null;
        }
        if (!onPath.add(stage)) {
            // Reached a stage already on the current path: everything from it
            // onward is the cycle.
            List<Stage> path = new ArrayList<>(onPath);
            return path.subList(path.indexOf(stage), path.size());
        }
        for (Stage dependency : stage.dependencies()) {
            List<Stage> cycle = walk(dependency, settled, onPath);
            if (cycle != null) {
                return cycle;
            }
        }
        onPath.remove(stage);
        settled.add(stage);
        return null;
    }

    @Override
    public String toString() {
        return "Job[" + name + ", " + stages.size() + " stages]";
    }
}
