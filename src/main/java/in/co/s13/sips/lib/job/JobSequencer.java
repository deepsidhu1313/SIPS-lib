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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Tracks one run of a {@link Job} and answers the only question the graph exists
 * to answer: what may start now.
 *
 * <p>Deliberately not "run the stages in order". Independent stages are released
 * together, and a stage is released the instant its own dependencies finish
 * rather than at the end of some round — which is the whole point. Submitting a
 * pipeline as separate jobs drains the cluster to idle at every boundary; a
 * sequencer never does.
 *
 * <p>It schedules nothing itself. A caller asks {@link #ready()}, hands those
 * stages to whatever distributes work, and reports back through
 * {@link #started}, {@link #completed} and {@link #failed}. Keeping the decision
 * separate from the doing is what lets the same logic drive a live cluster and
 * an offline evaluation.
 *
 * <h2>Failure</h2>
 *
 * <p>A failed stage's dependents can never run, so they are marked
 * {@link State#SKIPPED} immediately rather than left pending. A pipeline that
 * quietly waits forever on a step that already failed is the worst outcome
 * available, and it is what happens by default if nobody decides otherwise.
 *
 * <p>Stages that do not depend on the failure keep going: the results they
 * produce are still results, and throwing them away helps nobody.
 *
 * <p>Not thread-safe. Drive it from one thread, or guard it — the transitions
 * are cheap and holding a lock across them costs nothing.
 */
public final class JobSequencer {

    /** Where a stage is in its life. */
    public enum State {
        /** Waiting: either for its dependencies, or for a caller to start it. */
        PENDING,
        /** Handed out and running somewhere. */
        RUNNING,
        /** Finished successfully. */
        COMPLETE,
        /** Finished badly. */
        FAILED,
        /** Never ran, because something it depends on failed. */
        SKIPPED
    }

    private final Job job;
    private final Map<Stage, State> states = new LinkedHashMap<>();
    private final Map<Stage, String> failures = new LinkedHashMap<>();
    private final Map<Stage, List<Stage>> dependents = new LinkedHashMap<>();

    public JobSequencer(Job job) {
        if (job == null) {
            throw new IllegalArgumentException("job must not be null");
        }
        job.validate();
        this.job = job;
        for (Stage stage : job.stages()) {
            states.put(stage, State.PENDING);
            dependents.computeIfAbsent(stage, key -> new ArrayList<>());
        }
        for (Stage stage : job.stages()) {
            for (Stage dependency : stage.dependencies()) {
                dependents.computeIfAbsent(dependency, key -> new ArrayList<>()).add(stage);
            }
        }
    }

    public Job job() {
        return job;
    }

    /**
     * The stages that may start now: pending, with every dependency complete.
     *
     * <p>Ask again after every completion. The list grows as the graph opens up,
     * and a caller that asks once at the start will only ever see the roots.
     */
    public List<Stage> ready() {
        List<Stage> ready = new ArrayList<>();
        for (Stage stage : job.stages()) {
            if (states.get(stage) == State.PENDING && dependenciesComplete(stage)) {
                ready.add(stage);
            }
        }
        return ready;
    }

    private boolean dependenciesComplete(Stage stage) {
        for (Stage dependency : stage.dependencies()) {
            if (states.get(dependency) != State.COMPLETE) {
                return false;
            }
        }
        return true;
    }

    /**
     * Records that a stage has been handed out.
     *
     * @throws IllegalStateException if the stage is not pending, or a dependency
     *         has not finished — starting a stage early would produce a wrong
     *         answer rather than a slow one, so it is refused rather than warned
     *         about
     */
    public void started(Stage stage) {
        require(stage);
        if (states.get(stage) != State.PENDING) {
            throw new IllegalStateException("Stage '" + stage.name() + "' is "
                    + states.get(stage) + ", not waiting to start");
        }
        if (!dependenciesComplete(stage)) {
            throw new IllegalStateException("Stage '" + stage.name() + "' cannot start: "
                    + unfinishedDependencies(stage) + " has not finished");
        }
        states.put(stage, State.RUNNING);
    }

    private String unfinishedDependencies(Stage stage) {
        List<String> waiting = new ArrayList<>();
        for (Stage dependency : stage.dependencies()) {
            if (states.get(dependency) != State.COMPLETE) {
                waiting.add(dependency.name() + " (" + states.get(dependency) + ")");
            }
        }
        return String.join(", ", waiting);
    }

    /** Records a stage finishing successfully. Its dependents may now become ready. */
    public void completed(Stage stage) {
        require(stage);
        if (states.get(stage) != State.RUNNING) {
            throw new IllegalStateException("Stage '" + stage.name() + "' is "
                    + states.get(stage) + ", not running");
        }
        states.put(stage, State.COMPLETE);
    }

    /**
     * Records a stage failing, and skips everything downstream of it.
     *
     * @param reason kept for reporting; a failed pipeline should be able to say
     *        which step failed and why without anyone reading a log
     */
    public void failed(Stage stage, String reason) {
        require(stage);
        if (states.get(stage) != State.RUNNING) {
            throw new IllegalStateException("Stage '" + stage.name() + "' is "
                    + states.get(stage) + ", not running");
        }
        states.put(stage, State.FAILED);
        failures.put(stage, reason == null ? "no reason given" : reason);
        skipDependentsOf(stage);
    }

    private void skipDependentsOf(Stage stage) {
        for (Stage dependent : dependents.get(stage)) {
            if (states.get(dependent) == State.PENDING) {
                states.put(dependent, State.SKIPPED);
                skipDependentsOf(dependent);
            }
        }
    }

    public State stateOf(Stage stage) {
        require(stage);
        return states.get(stage);
    }

    /** Why a stage failed, if it did. */
    public Optional<String> failureReason(Stage stage) {
        require(stage);
        return Optional.ofNullable(failures.get(stage));
    }

    /** The stages in a given state, in declaration order. */
    public List<Stage> stagesIn(State state) {
        List<Stage> matching = new ArrayList<>();
        states.forEach((stage, current) -> {
            if (current == state) {
                matching.add(stage);
            }
        });
        return matching;
    }

    /** Whether nothing is left to run — successfully or otherwise. */
    public boolean isFinished() {
        for (State state : states.values()) {
            if (state == State.PENDING || state == State.RUNNING) {
                return false;
            }
        }
        return true;
    }

    /** Whether every stage completed. */
    public boolean isSuccessful() {
        for (State state : states.values()) {
            if (state != State.COMPLETE) {
                return false;
            }
        }
        return true;
    }

    /** Whether any stage failed. */
    public boolean hasFailed() {
        return !failures.isEmpty();
    }

    /** A one-line account of where the run got to, for logs and job listings. */
    public String progress() {
        Map<State, Integer> counts = new LinkedHashMap<>();
        for (State state : State.values()) {
            counts.put(state, 0);
        }
        states.values().forEach(state -> counts.merge(state, 1, Integer::sum));

        List<String> parts = new ArrayList<>();
        counts.forEach((state, count) -> {
            if (count > 0) {
                parts.add(count + " " + state.name().toLowerCase(Locale.ROOT));
            }
        });
        return job.name() + ": " + String.join(", ", parts);
    }

    /** Every stage's state, in declaration order. */
    public Map<Stage, State> states() {
        return Collections.unmodifiableMap(states);
    }

    private void require(Stage stage) {
        if (stage == null || !states.containsKey(stage)) {
            throw new IllegalArgumentException("Stage " + stage + " is not part of " + job);
        }
    }
}
