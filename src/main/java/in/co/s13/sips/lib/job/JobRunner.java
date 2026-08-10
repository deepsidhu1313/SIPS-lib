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

import in.co.s13.sips.lib.job.StageRunner.StageExecution;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Drives a {@link Job} to completion: starts what is ready, watches what is
 * running, and keeps going until nothing is left.
 *
 * <p>The loop is deliberately trivial, because everything interesting already
 * lives elsewhere — {@link JobSequencer} decides what may run, a
 * {@link StageRunner} decides what running means. What this adds is the part
 * neither of them can own: turning "ready" into "started" as often as possible,
 * and noticing when a stage has taken too long.
 *
 * <p>{@link #tick()} is the whole thing, and it neither sleeps nor spawns
 * threads. That is what makes the behaviour testable without a cluster: a test
 * calls tick, changes what the fake runner reports, and calls tick again.
 * {@link #run(Duration)} is the convenience wrapper that adds the waiting.
 */
public final class JobRunner {

    private final JobSequencer sequencer;
    private final StageRunner runner;
    private final LongSupplier clock;

    private final Map<Stage, StageExecution> running = new LinkedHashMap<>();
    private final Map<Stage, Long> startedAt = new LinkedHashMap<>();

    public JobRunner(Job job, StageRunner runner) {
        this(job, runner, System::currentTimeMillis);
    }

    /**
     * @param clock milliseconds, injectable so timeout behaviour can be tested
     *        without a test that actually waits
     */
    public JobRunner(Job job, StageRunner runner, LongSupplier clock) {
        if (runner == null || clock == null) {
            throw new IllegalArgumentException("runner and clock must not be null");
        }
        this.sequencer = new JobSequencer(job);
        this.runner = runner;
        this.clock = clock;
    }

    public JobSequencer sequencer() {
        return sequencer;
    }

    /**
     * One pass: check everything running, then start everything that is now
     * ready.
     *
     * <p>Polled before started, so a stage released by this pass's completions
     * goes out in the same pass rather than waiting a full poll interval. The
     * other order costs one interval of idle cluster per graph edge, which is
     * the exact cost the graph exists to remove.
     *
     * @return true while the job still has work in it
     */
    public boolean tick() {
        pollRunningStages();
        startReadyStages();
        return !sequencer.isFinished();
    }

    private void startReadyStages() {
        for (Stage stage : sequencer.ready()) {
            sequencer.started(stage);
            startedAt.put(stage, clock.getAsLong());
            try {
                running.put(stage, runner.start(stage));
            } catch (RuntimeException ex) {
                // A stage that could not even be distributed has failed; the
                // alternative is a job that sits at zero progress forever.
                running.remove(stage);
                sequencer.failed(stage, "could not start: " + ex);
            }
        }
    }

    private void pollRunningStages() {
        for (Stage stage : List.copyOf(running.keySet())) {
            StageExecution execution = running.get(stage);
            StageExecution.Outcome outcome;
            try {
                outcome = execution.poll();
            } catch (RuntimeException ex) {
                running.remove(stage);
                sequencer.failed(stage, "could not be polled: " + ex);
                continue;
            }

            switch (outcome) {
                case COMPLETE -> {
                    running.remove(stage);
                    sequencer.completed(stage);
                }
                case FAILED -> {
                    running.remove(stage);
                    sequencer.failed(stage, execution.failureReason().orElse("stage failed"));
                }
                case RUNNING -> failIfOverdue(stage, execution);
            }
        }
    }

    private void failIfOverdue(Stage stage, StageExecution execution) {
        if (stage.timeout().isEmpty()) {
            return;
        }
        long elapsed = clock.getAsLong() - startedAt.get(stage);
        if (elapsed < stage.timeout().get().toMillis()) {
            return;
        }
        // Without this a stage whose node died holds the whole pipeline open,
        // and the timeout the user set would be a field nothing reads.
        execution.cancel();
        running.remove(stage);
        sequencer.failed(stage, "timed out after " + elapsed + " ms");
    }

    /**
     * Runs the job to completion, waiting between passes.
     *
     * @param pollInterval how long to wait when nothing changed
     * @return the sequencer, so a caller can ask what happened
     */
    public JobSequencer run(Duration pollInterval) throws InterruptedException {
        if (pollInterval == null || pollInterval.isNegative()) {
            throw new IllegalArgumentException("pollInterval must not be negative");
        }
        while (tick()) {
            Thread.sleep(pollInterval.toMillis());
        }
        return sequencer;
    }

    /** The stages started and not yet finished, in the order they were started. */
    public List<Stage> inFlight() {
        return List.copyOf(running.keySet());
    }
}
