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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deciding when a training run is finished.
 *
 * <p>A fixed round count is a guess in both directions: too few and the model is
 * undertrained, too many and the cluster spends hours moving weights that no
 * longer change. Neither shows up as a failure, which is what makes guessing
 * expensive.
 */
class TrainingRunTest {

    @Test
    void stopsWhenTheLossStopsImproving() {
        // The usual case: real progress, then a plateau. Continuing past it
        // costs a full round of cluster time per round for nothing.
        TrainingRun run = TrainingRun.until(StopWhen.lossImprovesLessThan(0.01, 2));

        assertTrue(run.record(1, 2.0).shouldContinue());
        assertTrue(run.record(2, 1.0).shouldContinue());
        assertTrue(run.record(3, 0.999).shouldContinue(), "one flat round may be noise");
        assertFalse(run.record(4, 0.9985).shouldContinue(), "two in a row is a plateau");
        assertTrue(run.stoppedBecause().contains("improv"), run.stoppedBecause());
    }

    @Test
    void aNoisyRoundDoesNotStopIt() {
        // SGD is stochastic; a single round that fails to improve is normal.
        // Stopping on it would end most runs early.
        TrainingRun run = TrainingRun.until(StopWhen.lossImprovesLessThan(0.01, 2));

        run.record(1, 2.0);
        run.record(2, 1.999);
        assertTrue(run.record(3, 1.5).shouldContinue(),
                "improvement resets the count; the plateau was not one");
    }

    @Test
    void stopsWhenTheLossIsGoodEnough() {
        TrainingRun run = TrainingRun.until(StopWhen.lossBelow(0.5));

        assertTrue(run.record(1, 0.9).shouldContinue());
        assertFalse(run.record(2, 0.4).shouldContinue());
    }

    @Test
    void alwaysStopsAtTheRoundCap() {
        // Whatever the criterion says, a run has to end. A diverging model
        // never improves and would otherwise train forever.
        TrainingRun run = TrainingRun.until(StopWhen.lossBelow(0.0).orAfter(3));

        run.record(1, 5.0);
        run.record(2, 50.0);
        assertFalse(run.record(3, 500.0).shouldContinue());
        assertTrue(run.stoppedBecause().contains("3"), run.stoppedBecause());
    }

    @Test
    void divergenceStopsTheRunRatherThanBurningTheCluster() {
        // A learning rate too high sends the loss up, and every further round
        // is wasted. Non-finite is the same thing, arrived at faster.
        TrainingRun run = TrainingRun.until(StopWhen.lossImprovesLessThan(0.01, 5));

        run.record(1, 1.0);
        assertFalse(run.record(2, Double.NaN).shouldContinue());
        assertTrue(run.stoppedBecause().toLowerCase().contains("diverg"), run.stoppedBecause());
    }

    @Test
    void everyRoundIsKeptForTheReport() {
        // A run that stopped is a result; which rounds it took to get there is
        // the evidence, and the warehouses already keep per-chunk timings.
        TrainingRun run = TrainingRun.until(StopWhen.lossBelow(0.5));
        run.record(1, 0.9);
        run.record(2, 0.7);
        run.record(3, 0.4);

        List<TrainingRun.Round> history = run.history();

        assertEquals(3, history.size());
        assertEquals(0.7, history.get(1).loss(), 1e-9);
        assertEquals(2, history.get(1).number());
        assertEquals(0.4, run.bestLoss(), 1e-9);
    }

    @Test
    void theBestRoundIsNotNecessarilyTheLast() {
        // FedAvg can overshoot. Reporting the last loss as the result would
        // understate a run that had already found something better.
        TrainingRun run = TrainingRun.until(StopWhen.lossImprovesLessThan(0.01, 2));
        run.record(1, 1.0);
        run.record(2, 0.3);
        run.record(3, 0.35);

        assertEquals(0.3, run.bestLoss(), 1e-9);
        assertEquals(2, run.bestRound());
    }

    @Test
    void aRunThatHasNotStartedHasNotStopped() {
        TrainingRun run = TrainingRun.until(StopWhen.lossBelow(0.5));

        assertTrue(run.shouldContinue());
        assertTrue(run.history().isEmpty());
        assertEquals("", run.stoppedBecause());
    }

    @Test
    void nonsenseIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> TrainingRun.until(null));
        assertThrows(IllegalArgumentException.class,
                () -> StopWhen.lossImprovesLessThan(-1, 2));
        assertThrows(IllegalArgumentException.class,
                () -> StopWhen.lossImprovesLessThan(0.01, 0));
        assertThrows(IllegalArgumentException.class,
                () -> StopWhen.lossBelow(0.5).orAfter(0));
    }
}
