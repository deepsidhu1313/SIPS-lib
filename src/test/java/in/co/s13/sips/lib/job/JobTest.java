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
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Declaring a pipeline.
 *
 * <p>The graph is the part a user writes, so its mistakes are the ones that need
 * to fail loudly: a cycle, a duplicate name, an edge to a stage nobody is
 * scheduling. Each of those, left to runtime, looks identical from outside — a
 * job that sits at zero progress forever.
 */
class JobTest {

    /** A policy standing in for GSS, Factoring or anything else. */
    private static LoopPolicy anyPolicy() {
        return new LoopPolicy() {
            @Override
            public String name() {
                return "Test";
            }

            @Override
            public long nextBatchSize(long remaining, int nodes, int round) {
                return Math.max(1, remaining / nodes);
            }
        };
    }

    @Test
    void aPipelineReadsAsTheWorkDoes() {
        // The motivating example: parallel, then serial, then parallel again.
        // Today this is four submissions with a human waiting in between.
        Job job = new Job("mri-pipeline");
        Stage load = job.single("load");
        Stage correct = job.parallelFor("bias", 0, 256).after(load);
        Stage register = job.single("register").after(correct);
        Stage segment = job.parallelFor("segment", 0, 256).after(register);
        job.single("merge").after(segment);

        assertEquals(5, job.stages().size());
        assertEquals(List.of(load), job.roots());
        assertTrue(job.isValid());
    }

    @Test
    void aSingleStageIsAnIterationSpaceOfOne() {
        // Modelled this way so chunking, distribution and the task record treat
        // both kinds of stage identically, and only the scheduler cares.
        Stage single = new Job("j").single("register");

        assertEquals(Stage.Kind.SINGLE, single.kind());
        assertEquals(1, single.iterationCount());
    }

    @Test
    void aParallelStageKeepsItsRange() {
        Stage stage = new Job("j").parallelFor("bias", 100, 356);

        assertEquals(Stage.Kind.PARALLEL_FOR, stage.kind());
        assertEquals(100, stage.firstIndex());
        assertEquals(356, stage.lastIndexExclusive());
        assertEquals(256, stage.iterationCount());
    }

    @Test
    void aStageCanDependOnSeveralAtOnce() {
        // "after all of these" is the only structural operator, and it is enough
        // for any graph worth expressing.
        Job job = new Job("fan-in");
        Stage left = job.parallelFor("left", 0, 10);
        Stage right = job.parallelFor("right", 0, 10);
        Stage merge = job.single("merge").after(left, right);

        assertEquals(2, merge.dependencies().size());
        assertEquals(List.of(left, right), List.copyOf(merge.dependencies()));
    }

    @Test
    void dependenciesAreDeclaredOnceEvenIfRepeated() {
        Job job = new Job("j");
        Stage first = job.single("first");
        Stage second = job.single("second").after(first).after(first);

        assertEquals(1, second.dependencies().size());
    }

    @Test
    void eachStageChoosesItsOwnPolicy() {
        // The reason a pipeline is worth expressing as a graph at all: its steps
        // do not behave the same, so one policy for the whole job is wrong.
        Job job = new Job("j");
        Stage ragged = job.parallelFor("ragged", 0, 100).using(anyPolicy());
        Stage even = job.parallelFor("even", 0, 100);

        assertTrue(ragged.policy().isPresent());
        assertTrue(even.policy().isEmpty(), "an unset policy leaves the choice to the job");
    }

    @Test
    void aSingleStageRefusesABatchPolicy() {
        Stage single = new Job("j").single("register");

        assertTrue(assertThrows(IllegalStateException.class, () -> single.using(anyPolicy()))
                .getMessage().contains("single task"));
    }

    @Test
    void eachStageChoosesHowItRuns() {
        // A pipeline can mix: a WASM stage for the tight per-pixel work, a Java
        // stage for the step that needs a library.
        Job job = new Job("j");
        Stage kernel = job.parallelFor("kernel", 0, 100).type(TaskType.WASM);
        Stage report = job.single("report");

        assertEquals(TaskType.WASM, kernel.taskType());
        assertEquals(TaskType.JAVA, report.taskType(), "the default stays Java");
    }

    @Test
    void aStageCanBeGivenATimeout() {
        Stage stage = new Job("j").single("s").timeout(Duration.ofMinutes(10));

        assertEquals(Duration.ofMinutes(10), stage.timeout().orElseThrow());
        assertThrows(IllegalArgumentException.class,
                () -> new Job("j").single("s").timeout(Duration.ZERO));
    }

    // ---- what a graph must refuse ----

    @Test
    void aCycleIsNamedRatherThanDiscoveredAtRuntime() {
        // Left to runtime this is indistinguishable from a slow job.
        Job job = new Job("looped");
        Stage a = job.single("a");
        Stage b = job.single("b").after(a);
        Stage c = job.single("c").after(b);
        a.after(c);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, job::validate);
        assertTrue(thrown.getMessage().contains("cycle"));
        assertTrue(thrown.getMessage().contains("a"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("c"), thrown.getMessage());
    }

    @Test
    void aTwoStageCycleIsCaught() {
        Job job = new Job("looped");
        Stage a = job.single("a");
        Stage b = job.single("b").after(a);
        a.after(b);

        assertThrows(IllegalStateException.class, job::validate);
    }

    @Test
    void aStageCannotDependOnItself() {
        Job job = new Job("j");
        Stage a = job.single("a");

        assertTrue(assertThrows(IllegalArgumentException.class, () -> a.after(a))
                .getMessage().contains("itself"));
    }

    @Test
    void aStageCannotDependOnAnotherJobsStage() {
        // Such an edge would leave this stage waiting on something nobody in
        // this job is scheduling.
        Stage foreign = new Job("other").single("theirs");
        Stage mine = new Job("mine").single("ours");

        assertTrue(assertThrows(IllegalArgumentException.class, () -> mine.after(foreign))
                .getMessage().contains("other"));
    }

    @Test
    void twoStagesCannotShareAName() {
        Job job = new Job("j");
        job.single("register");

        assertTrue(assertThrows(IllegalArgumentException.class, () -> job.single("register"))
                .getMessage().contains("register"));
    }

    @Test
    void anEmptyJobIsNotRunnable() {
        assertTrue(assertThrows(IllegalStateException.class, () -> new Job("empty").validate())
                .getMessage().contains("no stages"));
    }

    @Test
    void nonsenseIsRefusedAtTheEdge() {
        assertThrows(IllegalArgumentException.class, () -> new Job(" "));
        assertThrows(IllegalArgumentException.class, () -> new Job("j").single(null));
        assertThrows(IllegalArgumentException.class, () -> new Job("j").parallelFor("s", 10, 0));
    }

    // ---- walking the graph ----

    @Test
    void dependencyOrderPutsEveryStageAfterWhatItNeeds() {
        Job job = new Job("diamond");
        Stage load = job.single("load");
        Stage left = job.parallelFor("left", 0, 10).after(load);
        Stage right = job.parallelFor("right", 0, 10).after(load);
        Stage merge = job.single("merge").after(left, right);

        List<Stage> ordered = job.inDependencyOrder();

        assertEquals(4, ordered.size());
        assertTrue(ordered.indexOf(load) < ordered.indexOf(left));
        assertTrue(ordered.indexOf(load) < ordered.indexOf(right));
        assertTrue(ordered.indexOf(left) < ordered.indexOf(merge));
        assertTrue(ordered.indexOf(right) < ordered.indexOf(merge));
    }

    @Test
    void aGraphWithSeveralStartingPointsHasSeveralRoots() {
        Job job = new Job("two-inputs");
        Stage volume = job.single("load-volume");
        Stage atlas = job.single("load-atlas");
        job.single("register").after(volume, atlas);

        assertEquals(List.of(volume, atlas), job.roots());
    }
}
