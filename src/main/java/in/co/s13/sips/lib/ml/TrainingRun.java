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
package in.co.s13.sips.lib.ml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A training run in progress: what each round achieved, and whether to do
 * another.
 *
 * <p>{@link FedAvgPlan} unrolls a fixed number of rounds because a task graph
 * is decided before it runs. This is the other way of driving the same
 * algorithm — append a round, look at the loss, decide — for when the right
 * number of rounds is not known in advance, which is most of the time.
 *
 * <p>Keeps every round rather than only the last. A run that stopped is a
 * result, and which rounds it took to get there is the evidence — the same
 * reason the warehouses keep per-chunk timings instead of a total.
 */
public final class TrainingRun {

    /** One round: what it was, and what it achieved. */
    public record Round(int number, double loss) {

        public Round {
            if (number < 1) {
                throw new IllegalArgumentException("Rounds count from one: " + number);
            }
        }
    }

    private final StopWhen stopWhen;
    private final List<Round> history = new ArrayList<>();
    private String stoppedBecause = "";

    private TrainingRun(StopWhen stopWhen) {
        this.stopWhen = stopWhen;
    }

    /** A run that ends when the criterion says so. */
    public static TrainingRun until(StopWhen stopWhen) {
        if (stopWhen == null) {
            throw new IllegalArgumentException("A run needs a stopping criterion");
        }
        return new TrainingRun(stopWhen);
    }

    /**
     * Records what a round achieved and decides whether to continue.
     *
     * @return this run, so the decision can be read straight away
     */
    public TrainingRun record(int roundNumber, double loss) {
        history.add(new Round(roundNumber, loss));

        if (!Double.isFinite(loss)) {
            // A learning rate too high sends the loss to infinity or NaN, and
            // every further round is wasted cluster time. Nothing recovers from
            // this, so it is not left to the criterion.
            stoppedBecause = "the loss diverged to " + loss;
            return this;
        }
        stopWhen.reasonToStop(Collections.unmodifiableList(history))
                .ifPresent(reason -> stoppedBecause = reason);
        return this;
    }

    /** Whether another round is worth running. */
    public boolean shouldContinue() {
        return stoppedBecause.isEmpty();
    }

    /** Why the run ended, or empty while it is still going. */
    public String stoppedBecause() {
        return stoppedBecause;
    }

    /** Every round so far, oldest first. */
    public List<Round> history() {
        return List.copyOf(history);
    }

    /**
     * The best loss seen, which is not always the last.
     *
     * <p>Federated averaging can overshoot; reporting the final loss as the
     * result would understate a run that had already found something better.
     */
    public double bestLoss() {
        return best().map(Round::loss).orElse(Double.NaN);
    }

    /** The round that achieved {@link #bestLoss()}, or -1 if none has. */
    public int bestRound() {
        return best().map(Round::number).orElse(-1);
    }

    private Optional<Round> best() {
        return history.stream()
                .filter(round -> Double.isFinite(round.loss()))
                .min(java.util.Comparator.comparingDouble(Round::loss));
    }

    /** A line for a job log or a report. */
    public String summary() {
        if (history.isEmpty()) {
            return "no rounds run";
        }
        return history.size() + " rounds, best loss " + bestLoss() + " at round " + bestRound()
                + (stoppedBecause.isEmpty() ? " (still running)" : "; stopped because "
                        + stoppedBecause);
    }
}
