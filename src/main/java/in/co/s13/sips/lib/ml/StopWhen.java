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

import java.util.List;

/**
 * When a training run has done enough.
 *
 * <p>A fixed round count is a guess in both directions: too few and the model is
 * undertrained, too many and the cluster spends hours exchanging weights that no
 * longer move. Neither shows up as a failure, which is what makes the guess
 * expensive rather than merely untidy.
 *
 * <p>Written as a single method so a researcher can express a criterion the
 * built-ins do not — a validation metric, a wall-clock budget, an energy budget
 * from the power monitors — without touching the runner.
 */
@FunctionalInterface
public interface StopWhen {

    /**
     * Whether the run should end, given every round so far.
     *
     * @return a reason to stop, or empty to continue. A reason rather than a
     *         boolean because a run that stopped without saying why leaves
     *         nobody able to tell convergence from a cap.
     */
    java.util.Optional<String> reasonToStop(List<TrainingRun.Round> history);

    /**
     * Stops once the loss has failed to improve by {@code minimum} for
     * {@code patience} consecutive rounds.
     *
     * <p>Patience matters: SGD is stochastic, so a single round that fails to
     * improve is normal and stopping on it would end most runs early.
     */
    static StopWhen lossImprovesLessThan(double minimum, int patience) {
        if (minimum < 0) {
            throw new IllegalArgumentException("A minimum improvement cannot be negative: "
                    + minimum);
        }
        if (patience < 1) {
            throw new IllegalArgumentException("Patience must be at least one round: "
                    + patience);
        }
        return history -> {
            if (history.size() <= patience) {
                return java.util.Optional.empty();
            }
            double before = history.get(history.size() - patience - 1).loss();
            for (int i = history.size() - patience; i < history.size(); i++) {
                if (before - history.get(i).loss() >= minimum) {
                    return java.util.Optional.empty();
                }
            }
            return java.util.Optional.of("the loss improved by less than " + minimum
                    + " for " + patience + " rounds");
        };
    }

    /** Stops once the loss reaches a target. */
    static StopWhen lossBelow(double target) {
        return history -> history.isEmpty()
                ? java.util.Optional.empty()
                : history.get(history.size() - 1).loss() <= target
                        ? java.util.Optional.of("the loss reached " + target)
                        : java.util.Optional.empty();
    }

    /**
     * This criterion, but never past {@code rounds}.
     *
     * <p>Whatever the criterion says, a run has to end: a diverging model never
     * improves and would otherwise train until someone noticed.
     */
    default StopWhen orAfter(int rounds) {
        if (rounds < 1) {
            throw new IllegalArgumentException("A run needs at least one round: " + rounds);
        }
        return history -> {
            java.util.Optional<String> mine = reasonToStop(history);
            if (mine.isPresent()) {
                return mine;
            }
            return history.size() >= rounds
                    ? java.util.Optional.of("the run reached its cap of " + rounds + " rounds")
                    : java.util.Optional.empty();
        };
    }

    /** Either criterion ending the run ends it. */
    default StopWhen or(StopWhen other) {
        return history -> {
            java.util.Optional<String> mine = reasonToStop(history);
            return mine.isPresent() ? mine : other.reasonToStop(history);
        };
    }
}
