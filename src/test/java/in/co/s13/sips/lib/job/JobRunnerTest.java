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
import in.co.s13.sips.lib.job.StageRunner.StageExecution;
import in.co.s13.sips.lib.job.StageRunner.StageExecution.Outcome;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Driving a pipeline without a cluster.
 *
 * <p>Every scenario worth being sure about — a stage failing, a node dying, a
 * distribution that never lands — is one you cannot reliably produce on real
 * hardware. Keeping the drive loop free of threads and sleeps is what makes them
 * testable at all, so these tests drive {@code tick()} by hand and move a fake
 * clock rather than waiting.
 */
class JobRunnerTest {

    /** A stage runner whose outcomes the test sets. */
    private static final class FakeRunner implements StageRunner {

        private final Map<String, Outcome> outcomes = new LinkedHashMap<>();
        private final List<String> started = new ArrayList<>();
        private final List<String> cancelled = new ArrayList<>();
        private String failToStart;

        @Override
        public StageExecution start(Stage stage) {
            if (stage.name().equals(failToStart)) {
                throw new IllegalStateException("no node would take it");
            }
            started.add(stage.name());
            outcomes.putIfAbsent(stage.name(), Outcome.RUNNING);
            return new StageExecution() {
                @Override
                public Outcome poll() {
                    return outcomes.get(stage.name());
                }

                @Override
                public Optional<String> failureReason() {
                    return Optional.of("chunk 3 exited non-zero");
                }

                @Override
                public void cancel() {
                    cancelled.add(stage.name());
                }
            };
        }

        void set(String stage, Outcome outcome) {
            outcomes.put(stage, outcome);
        }
    }

    /** load → (left ∥ right) → merge. */
    private static Job diamond() {
        Job job = new Job("diamond");
        Stage load = job.single("load");
        Stage left = job.parallelFor("left", 0, 100).after(load);
        Stage right = job.parallelFor("right", 0, 100).after(load);
        job.single("merge").after(left, right);
        return job;
    }

    @Test
    @Timeout(20)
    void aPipelineRunsThroughToTheEnd() {
        FakeRunner runner = new FakeRunner();
        JobRunner driver = new JobRunner(diamond(), runner);

        // One pass per level: each completion releases its dependents in the
        // same pass that observes it.
        driver.tick();
        runner.set("load", Outcome.COMPLETE);
        driver.tick();
        runner.set("left", Outcome.COMPLETE);
        runner.set("right", Outcome.COMPLETE);
        driver.tick();
        runner.set("merge", Outcome.COMPLETE);
        driver.tick();

        assertTrue(driver.sequencer().isSuccessful(), driver.sequencer().progress());
        assertEquals(List.of("load", "left", "right", "merge"), runner.started);
    }

    @Test
    @Timeout(20)
    void independentStagesAreStartedInTheSamePass() {
        // Not one per tick: a driver that started a single stage per pass would
        // serialise a graph that was written to be parallel.
        FakeRunner runner = new FakeRunner();
        JobRunner driver = new JobRunner(diamond(), runner);
        driver.tick();
        runner.set("load", Outcome.COMPLETE);

        driver.tick();

        assertEquals(List.of("load", "left", "right"), runner.started);
        assertEquals(2, driver.inFlight().size());
    }

    @Test
    @Timeout(20)
    void aStageStillRunningIsNotStartedAgain() {
        FakeRunner runner = new FakeRunner();
        JobRunner driver = new JobRunner(diamond(), runner);

        driver.tick();
        driver.tick();
        driver.tick();

        assertEquals(List.of("load"), runner.started);
    }

    @Test
    @Timeout(20)
    void tickSaysWhenThereIsNothingLeft() {
        FakeRunner runner = new FakeRunner();
        Job job = new Job("one-stage");
        job.single("only");
        JobRunner driver = new JobRunner(job, runner);

        assertTrue(driver.tick(), "the stage has only just started");
        runner.set("only", Outcome.COMPLETE);
        assertFalse(driver.tick(), "and now there is nothing to do");
    }

    // ---- failure ----

    @Test
    @Timeout(20)
    void aFailedStageCarriesItsReasonThrough() {
        FakeRunner runner = new FakeRunner();
        JobRunner driver = new JobRunner(diamond(), runner);
        driver.tick();
        runner.set("load", Outcome.FAILED);

        driver.tick();

        assertEquals(State.FAILED, driver.sequencer().stateOf(stage(driver, "load")));
        assertEquals("chunk 3 exited non-zero",
                driver.sequencer().failureReason(stage(driver, "load")).orElseThrow());
    }

    @Test
    @Timeout(20)
    void aStageThatCannotBeDistributedFailsRatherThanHangs() {
        // Nothing was ever handed out, so nothing will ever report back. Left
        // alone this is a job stuck at zero progress with no explanation.
        FakeRunner runner = new FakeRunner();
        runner.failToStart = "load";
        JobRunner driver = new JobRunner(diamond(), runner);

        driver.tick();

        assertEquals(State.FAILED, driver.sequencer().stateOf(stage(driver, "load")));
        assertTrue(driver.sequencer().failureReason(stage(driver, "load")).orElseThrow()
                .contains("no node would take it"));
        assertTrue(driver.sequencer().isFinished(), "and the whole graph is settled");
    }

    @Test
    @Timeout(20)
    void aRunnerThatThrowsWhilePollingFailsThatStageOnly() {
        Job job = new Job("two");
        job.single("flaky");
        Stage steady = job.single("steady");
        JobRunner driver = new JobRunner(job, new StageRunner() {
            @Override
            public StageExecution start(Stage stage) {
                return () -> {
                    if (stage.name().equals("flaky")) {
                        throw new IllegalStateException("distribution table vanished");
                    }
                    return Outcome.COMPLETE;
                };
            }
        });

        driver.tick();          // both start
        driver.tick();          // and are polled

        assertEquals(State.FAILED, driver.sequencer().stateOf(stage(driver, "flaky")));
        assertEquals(State.COMPLETE, driver.sequencer().stateOf(steady));
    }

    @Test
    @Timeout(20)
    void workUnaffectedByAFailureKeepsGoing() {
        FakeRunner runner = new FakeRunner();
        JobRunner driver = new JobRunner(diamond(), runner);
        driver.tick();
        runner.set("load", Outcome.COMPLETE);
        driver.tick();
        runner.set("left", Outcome.FAILED);

        driver.tick();

        assertEquals(State.SKIPPED, driver.sequencer().stateOf(stage(driver, "merge")));
        assertEquals(State.RUNNING, driver.sequencer().stateOf(stage(driver, "right")),
                "right has nothing to do with left, and its result is still a result");
    }

    // ---- timeout ----

    @Test
    @Timeout(20)
    void aStageThatOutlivesItsTimeoutIsFailedAndCancelled() {
        // Otherwise a stage whose node died holds the pipeline open forever, and
        // the timeout the user set is a field nothing reads.
        AtomicLong now = new AtomicLong(0);
        FakeRunner runner = new FakeRunner();
        Job job = new Job("slow");
        Stage stage = job.single("stuck").timeout(Duration.ofSeconds(30));
        JobRunner driver = new JobRunner(job, runner, now::get);

        driver.tick();
        now.set(29_000);
        driver.tick();
        assertEquals(State.RUNNING, driver.sequencer().stateOf(stage), "still within its time");

        now.set(30_001);
        driver.tick();

        assertEquals(State.FAILED, driver.sequencer().stateOf(stage));
        assertTrue(driver.sequencer().failureReason(stage).orElseThrow().contains("timed out"));
        assertEquals(List.of("stuck"), runner.cancelled);
    }

    @Test
    @Timeout(20)
    void aStageWithNoTimeoutIsNeverFailedForTakingLong() {
        AtomicLong now = new AtomicLong(0);
        FakeRunner runner = new FakeRunner();
        Job job = new Job("patient");
        Stage stage = job.parallelFor("long-haul", 0, 1_000_000);
        JobRunner driver = new JobRunner(job, runner, now::get);

        driver.tick();
        now.set(Duration.ofDays(7).toMillis());
        driver.tick();

        assertEquals(State.RUNNING, driver.sequencer().stateOf(stage));
    }

    @Test
    @Timeout(20)
    void aStageThatFinishesInTimeIsNotPenalised() {
        AtomicLong now = new AtomicLong(0);
        FakeRunner runner = new FakeRunner();
        Job job = new Job("j");
        Stage stage = job.single("quick").timeout(Duration.ofSeconds(30));
        JobRunner driver = new JobRunner(job, runner, now::get);

        driver.tick();
        now.set(29_999);
        runner.set("quick", Outcome.COMPLETE);
        driver.tick();

        assertEquals(State.COMPLETE, driver.sequencer().stateOf(stage));
        assertTrue(runner.cancelled.isEmpty());
    }

    @Test
    @Timeout(20)
    void timeoutIsMeasuredPerStageNotPerJob() {
        AtomicLong now = new AtomicLong(0);
        FakeRunner runner = new FakeRunner();
        Job job = new Job("chain");
        Stage first = job.single("first").timeout(Duration.ofSeconds(10));
        Stage second = job.single("second").after(first).timeout(Duration.ofSeconds(10));
        JobRunner driver = new JobRunner(job, runner, now::get);

        driver.tick();
        now.set(9_000);
        runner.set("first", Outcome.COMPLETE);
        driver.tick();                       // first completes, second starts at t=9000
        now.set(15_000);
        driver.tick();                       // second is 6s old, not 15s

        assertEquals(State.RUNNING, driver.sequencer().stateOf(second),
                "second's clock starts when second does");
    }

    // ---- the convenience wrapper ----

    @Test
    @Timeout(20)
    void runDrivesToCompletionOnItsOwn() throws InterruptedException {
        Job job = new Job("auto");
        job.single("a");
        job.single("b").after(job.stage("a").orElseThrow());

        JobSequencer finished = new JobRunner(job, stage -> () -> Outcome.COMPLETE)
                .run(Duration.ZERO);

        assertTrue(finished.isSuccessful());
    }

    private static Stage stage(JobRunner driver, String name) {
        return driver.sequencer().job().stage(name).orElseThrow();
    }
}
