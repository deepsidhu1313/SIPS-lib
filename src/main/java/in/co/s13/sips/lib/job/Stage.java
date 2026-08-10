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
import in.co.s13.sips.scheduler.LoopPolicy;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * One step of a {@link Job}.
 *
 * <p>Two kinds, and the difference is the point of the whole graph model:
 *
 * <ul>
 *   <li>a <b>parallel-for</b> stage is an iteration space split across nodes,
 *       exactly what {@code sim.parallelFor()} already does;</li>
 *   <li>a <b>single</b> stage is one task on one node — the thing the loop model
 *       has no word for, and the reason a pipeline currently has to be submitted
 *       as several jobs with a human waiting in between.</li>
 * </ul>
 *
 * <p>A single stage is modelled as an iteration space of one, so everything
 * downstream — chunking, distribution, the task record — treats both kinds the
 * same way and only the scheduler notices the difference.
 *
 * <p>Built through {@link Job}, never directly: a stage only means anything as
 * part of a graph, and letting one exist outside a job would allow edges between
 * stages that are never scheduled together.
 */
public final class Stage {

    /** Stands for a chunk's first iteration index in an output pattern. */
    public static final String INDEX_PLACEHOLDER = "{index}";

    /** What a stage is: an iteration space, or a single task. */
    public enum Kind {
        SINGLE, PARALLEL_FOR
    }

    private final Job job;
    private final String name;
    private final Kind kind;
    private final long firstIndex;
    private final long lastIndexExclusive;

    // Insertion-ordered so error messages and scheduling decisions are
    // reproducible run to run; a graph that schedules differently each time is
    // one nobody can debug.
    private final Set<Stage> dependencies = new LinkedHashSet<>();

    // A subset of the dependencies: the ones this stage reads from, as opposed
    // to the ones it merely waits for.
    private final Set<Stage> inputs = new LinkedHashSet<>();

    private String output;
    private LoopPolicy policy;
    private TaskType taskType = TaskType.DEFAULT;
    private Duration timeout;

    Stage(Job job, String name, Kind kind, long firstIndex, long lastIndexExclusive) {
        this.job = job;
        this.name = name;
        this.kind = kind;
        this.firstIndex = firstIndex;
        this.lastIndexExclusive = lastIndexExclusive;
    }

    /**
     * Declares that this stage cannot start until all of the given stages have
     * finished.
     *
     * <p>The only structural operator there is. Anything expressible as a
     * dependency graph is expressible with it, and refusing anything richer
     * keeps a scheduler's input a plain graph rather than a small language.
     *
     * @throws IllegalArgumentException on a self-dependency, or a stage from
     *         another job — an edge to a stage nobody is scheduling would leave
     *         this one waiting forever
     */
    public Stage after(Stage... predecessors) {
        for (Stage predecessor : predecessors) {
            if (predecessor == null) {
                throw new IllegalArgumentException("Stage '" + name + "' cannot depend on null");
            }
            if (predecessor == this) {
                throw new IllegalArgumentException("Stage '" + name + "' cannot depend on itself");
            }
            if (predecessor.job != job) {
                throw new IllegalArgumentException("Stage '" + name + "' cannot depend on '"
                        + predecessor.name + "': it belongs to job '" + predecessor.job.name()
                        + "', which this job does not schedule");
            }
            dependencies.add(predecessor);
        }
        return this;
    }

    /**
     * Declares that this stage consumes what the given stages produced.
     *
     * <p>Implies {@link #after}: you cannot read a stage's output before it has
     * written it. Requiring both would be a rule whose only effect is to let
     * someone declare one and forget the other.
     *
     * <p>The distinction from a plain dependency is the whole point. Ordering
     * says <em>when</em> this stage may run; reading says <em>where it should
     * run</em>, because its inputs are already sitting on the nodes that
     * produced them and moving them is work nobody asked for.
     */
    public Stage reads(Stage... producers) {
        after(producers);
        inputs.addAll(java.util.Arrays.asList(producers));
        return this;
    }

    /**
     * Where this stage's output lands.
     *
     * <p>A pattern rather than a path: a parallel stage produces one of these
     * per chunk, and {@code {index}} is replaced with the chunk's first
     * iteration index.
     */
    public Stage writes(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("Stage '" + name + "' needs a real output pattern");
        }
        if (kind == Kind.PARALLEL_FOR && !pattern.contains(INDEX_PLACEHOLDER)) {
            // Every chunk writing the same path means the last one wins and the
            // rest are silently lost.
            throw new IllegalArgumentException("Stage '" + name + "' runs many chunks, so its "
                    + "output pattern must contain " + INDEX_PLACEHOLDER + ": " + pattern);
        }
        this.output = pattern.trim();
        return this;
    }

    /** The output path for one chunk of this stage. */
    public String outputFor(long firstIndex) {
        if (output == null) {
            throw new IllegalStateException("Stage '" + name + "' declares no output");
        }
        return output.replace(INDEX_PLACEHOLDER, String.valueOf(firstIndex));
    }

    /**
     * The scheduling policy for this stage's iteration space.
     *
     * <p>Per stage, not per job: the whole reason a pipeline is worth expressing
     * as a graph is that its steps behave differently. A stage over ragged work
     * wants Factoring where an even one is fine with Chunk.
     *
     * @throws IllegalStateException on a single stage, where there is no
     *         iteration space to batch
     */
    public Stage using(LoopPolicy policy) {
        if (kind == Kind.SINGLE) {
            throw new IllegalStateException("Stage '" + name + "' is a single task; there is no "
                    + "iteration space for a " + (policy == null ? "policy" : policy.name())
                    + " policy to divide");
        }
        if (policy == null) {
            throw new IllegalArgumentException("policy must not be null");
        }
        this.policy = policy;
        return this;
    }

    /** How this stage's tasks are executed. Defaults to {@link TaskType#DEFAULT}. */
    public Stage type(TaskType taskType) {
        if (taskType == null) {
            throw new IllegalArgumentException("taskType must not be null");
        }
        this.taskType = taskType;
        return this;
    }

    /** How long this stage may run before it is treated as failed. */
    public Stage timeout(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive, was " + timeout);
        }
        this.timeout = timeout;
        return this;
    }

    public Job job() {
        return job;
    }

    public String name() {
        return name;
    }

    public Kind kind() {
        return kind;
    }

    public long firstIndex() {
        return firstIndex;
    }

    public long lastIndexExclusive() {
        return lastIndexExclusive;
    }

    /** How many iterations this stage covers. A single stage covers one. */
    public long iterationCount() {
        return lastIndexExclusive - firstIndex;
    }

    /** Where this stage's output lands, if it declared anywhere. */
    public Optional<String> output() {
        return Optional.ofNullable(output);
    }

    /**
     * The stages whose output this one reads — a subset of its dependencies.
     *
     * <p>A stage can depend on another without reading it: "do not start until
     * the checkpoint is written" is ordering, not data.
     */
    public Set<Stage> inputs() {
        return Collections.unmodifiableSet(inputs);
    }

    /** The stages that must finish before this one starts, in declaration order. */
    public Set<Stage> dependencies() {
        return Collections.unmodifiableSet(dependencies);
    }

    /** The policy for this stage, or empty to let the job's scheduler choose. */
    public Optional<LoopPolicy> policy() {
        return Optional.ofNullable(policy);
    }

    public TaskType taskType() {
        return taskType;
    }

    public Optional<Duration> timeout() {
        return Optional.ofNullable(timeout);
    }

    @Override
    public String toString() {
        return kind == Kind.SINGLE
                ? "Stage[" + name + "]"
                : "Stage[" + name + " " + firstIndex + ".." + lastIndexExclusive + ")]";
    }
}
