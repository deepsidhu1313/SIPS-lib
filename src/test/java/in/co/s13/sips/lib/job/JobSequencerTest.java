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

import in.co.s13.sips.lib.job.JobSequencer.State;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deciding what may run now.
 *
 * <p>The value of the graph is entirely in this: releasing a stage the moment
 * its own dependencies finish, and releasing independent stages together.
 * Anything that waits for a round boundary, or runs stages one at a time in
 * topological order, gives back exactly the idle cluster the graph was meant to
 * avoid — so these tests are as much about what is <em>not</em> serialised.
 */
class JobSequencerTest {

    /** load → (left ∥ right) → merge. */
    private static Job diamond() {
        Job job = new Job("diamond");
        Stage load = job.single("load");
        Stage left = job.parallelFor("left", 0, 100).after(load);
        Stage right = job.parallelFor("right", 0, 100).after(load);
        job.single("merge").after(left, right);
        return job;
    }

    private static Stage of(Job job, String name) {
        return job.stage(name).orElseThrow();
    }

    @Test
    void onlyTheRootsAreReadyAtTheStart() {
        Job job = diamond();
        JobSequencer run = new JobSequencer(job);

        assertEquals(List.of(of(job, "load")), run.ready());
    }

    @Test
    void independentStagesAreReleasedTogether() {
        // The property that keeps the cluster busy. Releasing left, then waiting
        // for it before releasing right, would halve the throughput of this
        // pipeline for no reason.
        Job job = diamond();
        JobSequencer run = new JobSequencer(job);
        Stage load = of(job, "load");

        run.started(load);
        run.completed(load);

        assertEquals(List.of(of(job, "left"), of(job, "right")), run.ready());
    }

    @Test
    void aStageIsReleasedAsSoonAsItsOwnDependenciesFinish() {
        // Not at a round boundary, and not when the whole level is done: the
        // moment this stage's own predecessors are complete.
        Job job = new Job("chain-and-branch");
        Stage load = job.single("load");
        Stage quick = job.single("quick").after(load);
        Stage slow = job.parallelFor("slow", 0, 1_000_000).after(load);
        Stage afterQuick = job.single("after-quick").after(quick);
        JobSequencer run = new JobSequencer(job);

        run.started(load);
        run.completed(load);
        run.started(quick);
        run.started(slow);
        run.completed(quick);

        assertTrue(run.ready().contains(afterQuick),
                "after-quick waits on quick alone; slow is nothing to do with it");
        assertEquals(State.RUNNING, run.stateOf(slow), "and slow keeps running meanwhile");
    }

    @Test
    void aStageWaitsForEveryPredecessorNotJustOne() {
        Job job = diamond();
        JobSequencer run = new JobSequencer(job);
        Stage load = of(job, "load");
        Stage left = of(job, "left");

        run.started(load);
        run.completed(load);
        run.started(left);
        run.completed(left);

        assertFalse(run.ready().contains(of(job, "merge")),
                "merge needs right as well, and merging half the data is a wrong answer");
    }

    @Test
    void aRunningStageIsNotOfferedAgain() {
        // A caller polling ready() in a loop must not hand the same stage out
        // twice.
        Job job = diamond();
        JobSequencer run = new JobSequencer(job);
        Stage load = of(job, "load");

        run.started(load);

        assertTrue(run.ready().isEmpty());
    }

    @Test
    void aWholePipelineRunsToCompletion() {
        Job job = diamond();
        JobSequencer run = new JobSequencer(job);

        while (!run.isFinished()) {
            List<Stage> ready = run.ready();
            assertFalse(ready.isEmpty(), "a valid graph should never stall: " + run.progress());
            for (Stage stage : ready) {
                run.started(stage);
            }
            for (Stage stage : ready) {
                run.completed(stage);
            }
        }

        assertTrue(run.isSuccessful());
        assertFalse(run.hasFailed());
    }

    // ---- the claim that motivates the graph ----

    /**
     * Runs a job to completion, releasing each stage the moment its own
     * dependencies finish, and returns how long it took.
     */
    private static long makespanWithSequencer(Job job, Map<String, Long> durations) {
        JobSequencer run = new JobSequencer(job);
        Map<Stage, Long> finishAt = new java.util.HashMap<>();
        long now = 0;

        while (!run.isFinished()) {
            for (Stage stage : run.ready()) {
                run.started(stage);
                finishAt.put(stage, now + durations.get(stage.name()));
            }
            // Advance to the next completion, not to the end of a round.
            long next = finishAt.values().stream().mapToLong(Long::longValue).min().orElseThrow();
            now = next;
            for (Stage stage : List.copyOf(finishAt.keySet())) {
                if (finishAt.get(stage) <= now) {
                    run.completed(stage);
                    finishAt.remove(stage);
                }
            }
        }
        return now;
    }

    /**
     * What submitting the same pipeline as one job per level costs: nothing in
     * level n+1 may start until everything in level n has finished, because the
     * submitter has no way to say otherwise.
     */
    private static long makespanBySubmittingEachLevel(Job job, Map<String, Long> durations) {
        Map<Stage, Integer> depth = new java.util.LinkedHashMap<>();
        for (Stage stage : job.inDependencyOrder()) {
            int deepest = 0;
            for (Stage dependency : stage.dependencies()) {
                deepest = Math.max(deepest, depth.get(dependency) + 1);
            }
            depth.put(stage, deepest);
        }

        Map<Integer, Long> longestInLevel = new java.util.TreeMap<>();
        depth.forEach((stage, level) ->
                longestInLevel.merge(level, durations.get(stage.name()), Math::max));
        return longestInLevel.values().stream().mapToLong(Long::longValue).sum();
    }

    @Test
    void anUnevenPipelineFinishesSoonerThanLevelBySubmission() {
        // Three quick steps beside one slow one. Submitting level by level makes
        // every quick step wait for the slow step it has nothing to do with;
        // the sequencer runs the quick chain right through while the slow stage
        // is still going.
        Job job = new Job("uneven");
        Stage load = job.single("load");
        job.parallelFor("slow", 0, 1_000_000).after(load);
        Stage a = job.single("quick-a").after(load);
        Stage b = job.single("quick-b").after(a);
        job.single("quick-c").after(b);

        Map<String, Long> durations = Map.of(
                "load", 1L, "slow", 100L, "quick-a", 1L, "quick-b", 1L, "quick-c", 1L);

        long sequenced = makespanWithSequencer(job, durations);
        long levelled = makespanBySubmittingEachLevel(job, durations);

        assertEquals(101, sequenced, "load, then the slow stage runs while the chain drains");
        assertEquals(103, levelled, "each quick step waits out the slow stage beside it");
        assertTrue(sequenced < levelled,
                "the graph exists to remove this wait: " + sequenced + " vs " + levelled);
    }

    @Test
    void anEvenPipelineGainsNothingAndLosesNothing() {
        // Honest boundary: where every level is balanced there is no idle time
        // to remove, and the sequencer must not somehow be worse.
        Job job = new Job("even");
        Stage load = job.single("load");
        job.parallelFor("left", 0, 100).after(load);
        job.parallelFor("right", 0, 100).after(load);

        Map<String, Long> durations = Map.of("load", 5L, "left", 10L, "right", 10L);

        assertEquals(makespanBySubmittingEachLevel(job, durations),
                makespanWithSequencer(job, durations));
    }

    // ---- failure ----

    @Test
    void afailedStageSkipsEverythingDownstream() {
        // Left pending, those stages would keep a job "in progress" forever on
        // work that can never start.
        Job job = new Job("chain");
        Stage first = job.single("first");
        Stage second = job.single("second").after(first);
        Stage third = job.single("third").after(second);
        JobSequencer run = new JobSequencer(job);

        run.started(first);
        run.failed(first, "out of memory");

        assertEquals(State.FAILED, run.stateOf(first));
        assertEquals(State.SKIPPED, run.stateOf(second));
        assertEquals(State.SKIPPED, run.stateOf(third), "skipping is transitive");
        assertTrue(run.isFinished());
        assertTrue(run.hasFailed());
    }

    @Test
    void workUnaffectedByAFailureStillRuns() {
        // Its results are still results. Throwing them away helps nobody, and a
        // partial answer beats none.
        Job job = new Job("two-branches");
        Stage doomed = job.single("doomed");
        Stage afterDoomed = job.single("after-doomed").after(doomed);
        Stage independent = job.parallelFor("independent", 0, 100);
        JobSequencer run = new JobSequencer(job);

        run.started(doomed);
        run.failed(doomed, "disk full");

        assertEquals(State.SKIPPED, run.stateOf(afterDoomed));
        assertTrue(run.ready().contains(independent));
        assertFalse(run.isFinished(), "there is still work worth doing");
    }

    @Test
    void aFailureSaysWhichStageAndWhy() {
        Job job = new Job("j");
        Stage stage = job.single("segment");
        JobSequencer run = new JobSequencer(job);

        run.started(stage);
        run.failed(stage, "model file missing");

        assertEquals("model file missing", run.failureReason(stage).orElseThrow());
        assertTrue(run.failureReason(job.stage("segment").orElseThrow()).isPresent());
    }

    @Test
    void aFailureWithNoReasonStillReportsSomething() {
        Job job = new Job("j");
        Stage stage = job.single("s");
        JobSequencer run = new JobSequencer(job);

        run.started(stage);
        run.failed(stage, null);

        assertTrue(run.failureReason(stage).isPresent());
    }

    // ---- refusing impossible transitions ----

    @Test
    void aStageCannotStartBeforeItsDependencies() {
        // Running this early gives a wrong answer rather than a slow one, so it
        // is refused rather than warned about.
        Job job = diamond();
        JobSequencer run = new JobSequencer(job);

        assertTrue(assertThrows(IllegalStateException.class, () -> run.started(of(job, "merge")))
                .getMessage().contains("left"));
    }

    @Test
    void aStageCannotBeStartedTwice() {
        Job job = diamond();
        JobSequencer run = new JobSequencer(job);
        Stage load = of(job, "load");
        run.started(load);

        assertThrows(IllegalStateException.class, () -> run.started(load));
    }

    @Test
    void aStageThatNeverStartedCannotComplete() {
        Job job = diamond();
        JobSequencer run = new JobSequencer(job);

        assertThrows(IllegalStateException.class, () -> run.completed(of(job, "load")));
    }

    @Test
    void aStageFromAnotherJobIsRejected() {
        JobSequencer run = new JobSequencer(diamond());
        Stage foreign = new Job("other").single("theirs");

        assertThrows(IllegalArgumentException.class, () -> run.stateOf(foreign));
    }

    @Test
    void aCyclicJobIsRefusedBeforeAnythingIsScheduled() {
        Job job = new Job("looped");
        Stage a = job.single("a");
        Stage b = job.single("b").after(a);
        a.after(b);

        assertThrows(IllegalStateException.class, () -> new JobSequencer(job));
    }

    // ---- reporting ----

    @Test
    void progressSaysWhereTheRunGotTo() {
        Job job = diamond();
        JobSequencer run = new JobSequencer(job);
        Stage load = of(job, "load");
        run.started(load);
        run.completed(load);
        run.started(of(job, "left"));

        String progress = run.progress();

        assertTrue(progress.startsWith("diamond:"));
        assertTrue(progress.contains("1 running"), progress);
        assertTrue(progress.contains("1 complete"), progress);
        assertTrue(progress.contains("2 pending"), progress);
    }

    @Test
    void stagesCanBeListedByState() {
        Job job = diamond();
        JobSequencer run = new JobSequencer(job);
        run.started(of(job, "load"));

        assertEquals(List.of(of(job, "load")), run.stagesIn(State.RUNNING));
        assertEquals(3, run.stagesIn(State.PENDING).size());
    }
}
