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
package in.co.s13.sips.lib.array;

import in.co.s13.sips.lib.ml.ShardPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Measuring what a worker is actually worth, at the moment it joins.
 *
 * <p>The negative-results register is blunt about capability flags: on one
 * machine in a sibling fleet the GPU was 4× slower than the CPU for one
 * workload and 19.8× faster for another, so "has a GPU" predicts nothing. A
 * phone model name predicts even less — thermals, background load and battery
 * governors move real throughput by multiples. So a joining worker runs a
 * fixed task and reports what it <em>measured</em>, and the shard planner
 * weights it by that.
 *
 * <p>Run several times, because one number lies twice over: the first run
 * pays JIT warm-up, and a busy device's spread is a leading indicator the
 * planner already knows how to use ({@code ShardPlan.Measured}'s
 * {@code mean/(1+cv)} weighting — a phone that swings loses share before its
 * average degrades).
 */
class WorkerBenchTest {

    @Test
    void theStandardTaskIsTheSameForEveryone() {
        // Scores are only comparable if every worker measured the same work.
        assertArrayEquals(WorkerBench.standard().runToBytes(),
                WorkerBench.standard().runToBytes(),
                "two builds of the standard task must be byte-identical");
    }

    @Test
    void theStandardTaskIsSmallEnoughToBePolite() {
        // It runs on someone's phone at join time, possibly on battery. It
        // must cost well under a second there -- and it travels in the hello
        // exchange, so the document must be small too.
        long start = System.nanoTime();
        WorkerBench.standard().run();
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertTrue(millis < 1000, "the bench took " + millis + " ms on this machine; "
                + "a phone is slower and it must still be polite there");
        assertTrue(WorkerBench.standard().toJson().toString().length() < 200_000,
                "the bench travels in the hello exchange");
    }

    @Test
    void timingsBecomeAMeasuredSpeed() {
        // 20 units of work in 10 ms is twice the speed of 20 units in 20 ms.
        ShardPlan.Measured fast = WorkerBench.measured(new long[]{10, 10, 10});
        ShardPlan.Measured slow = WorkerBench.measured(new long[]{20, 20, 20});

        assertEquals(2.0, fast.mean() / slow.mean(), 1e-6);
        assertEquals(0.0, fast.dispersion(), 1e-9, "steady timings mean no spread");
    }

    @Test
    void anErraticDeviceShowsItsSpreads() {
        ShardPlan.Measured steady = WorkerBench.measured(new long[]{10, 10, 10});
        ShardPlan.Measured erratic = WorkerBench.measured(new long[]{5, 10, 15});

        assertTrue(erratic.dispersion() > 0, "swinging timings must show as dispersion");
        assertTrue(steady.weight() > erratic.weight(),
                "the planner should prefer the steady device at equal mean");
    }

    @Test
    void theWarmupRunIsDiscarded() {
        // The first run pays JIT compilation and cache misses; charging the
        // device for it would understate every worker that just joined --
        // which is every worker.
        ShardPlan.Measured withColdStart = WorkerBench.measured(new long[]{500, 10, 10, 10});

        assertEquals(0.0, withColdStart.dispersion(), 1e-9,
                "the cold first run must not count as spread");
    }

    @Test
    void tooFewRunsAreRefused() {
        // One post-warm-up run has no spread to report, and spread is half of
        // what the measurement is for.
        assertThrows(IllegalArgumentException.class,
                () -> WorkerBench.measured(new long[]{10}));
        assertThrows(IllegalArgumentException.class,
                () -> WorkerBench.measured(new long[]{500, 10}));
        assertThrows(IllegalArgumentException.class, () -> WorkerBench.measured(null));
    }

    @Test
    void aZeroTimingIsRefusedRatherThanDividedBy() {
        // A clock that returned the same millisecond twice, or a hostile
        // worker claiming infinite speed.
        assertThrows(IllegalArgumentException.class,
                () -> WorkerBench.measured(new long[]{10, 0, 10}));
    }
}
