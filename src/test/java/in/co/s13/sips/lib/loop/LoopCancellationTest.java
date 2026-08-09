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
package in.co.s13.sips.lib.loop;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Early exit from a parallel loop.
 *
 * <p>{@code break} has no exact parallel meaning. Sequentially it promises that
 * no later iteration runs; in a distributed loop, later iterations may already
 * have finished on another node before the break is even observed. Enforcing
 * the sequential promise would mean committing iterations in order, which
 * discards the parallelism that made the loop worth distributing.
 *
 * <p>Every framework that has faced this resolves it the same way. OpenMP
 * forbids branching out of a parallel region and offers {@code cancel} instead;
 * Java streams distinguish {@code findFirst} from {@code findAny}; Rayon
 * distinguishes {@code find_first} from {@code find_any}. SIPS follows that
 * precedent and names the operation for what it does — cancellation, not break.
 *
 * <p>These pin the guarantees, which is what stops a user assuming the
 * sequential ones.
 */
class LoopCancellationTest {

    private LoopCancellation loop;

    @BeforeEach
    void setUp() {
        loop = new LoopCancellation("job-1");
    }

    @Test
    void aFreshLoopIsNotCancelled() {
        assertFalse(loop.isCancelled());
        assertTrue(loop.reason().isEmpty());
    }

    @Test
    void cancellingIsVisibleToEveryLaterCheck() {
        loop.cancel("found the answer");

        assertTrue(loop.isCancelled());
        assertEquals("found the answer", loop.reason());
    }

    @Test
    void theFirstReasonWins() {
        // Two nodes may cancel at once. Keeping the first makes the outcome
        // deterministic to read afterwards, even though which arrives first
        // is not.
        loop.cancel("node A found it");
        loop.cancel("node B found it too");

        assertEquals("node A found it", loop.reason());
    }

    @Test
    void cancellingTwiceIsHarmless() {
        assertTrue(loop.cancel("first"));
        assertFalse(loop.cancel("second"), "only the first cancel takes effect");
        assertTrue(loop.isCancelled());
    }

    @Test
    void aBlankReasonIsStillARealCancellation() {
        loop.cancel("");
        assertTrue(loop.isCancelled());
    }

    @Test
    void cancellationIsScopedToItsJob() {
        LoopCancellation other = new LoopCancellation("job-2");
        loop.cancel("only job-1");

        assertFalse(other.isCancelled(),
                "one job's early exit must not stop another job");
    }

    /**
     * The guarantee that is deliberately <em>not</em> made.
     */
    @Test
    void iterationsAlreadyRunningAreNotUndone() {
        loop.recordCompleted(5);
        loop.recordCompleted(9);
        loop.cancel("stop");
        loop.recordCompleted(12);   // was already in flight when cancel arrived

        // Cancelling stops new work being handed out. It cannot unwind
        // iterations that already ran, and any side effects they had stand.
        assertEquals(3, loop.completedCount());
        assertTrue(loop.completedAfterCancel() >= 1,
                "iterations completing after a cancel are expected, not a bug");
    }

    @Test
    void reportsWhetherWorkShouldStillBeHandedOut() {
        assertTrue(loop.shouldContinue());
        loop.cancel("done");
        assertFalse(loop.shouldContinue());
    }

    @Test
    void requiresAJobToken() {
        assertThrows(IllegalArgumentException.class, () -> new LoopCancellation(null));
        assertThrows(IllegalArgumentException.class, () -> new LoopCancellation("  "));
    }

    @Test
    void isSafeUnderConcurrentCancellation() throws InterruptedException {
        // Chunks run on many threads; exactly one cancel must win.
        int threads = 16;
        Thread[] racers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            final int id = i;
            racers[i] = new Thread(() -> loop.cancel("node-" + id));
            racers[i].start();
        }
        for (Thread racer : racers) {
            racer.join();
        }

        assertTrue(loop.isCancelled());
        assertTrue(loop.reason().startsWith("node-"), "one reason should have won");
    }
}
