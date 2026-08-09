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

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two ways a parallel loop can stop early.
 *
 * <p>They answer different questions, and conflating them is how a user ends up
 * with a wrong answer rather than a slow one.
 *
 * <ul>
 *   <li><b>breakAll</b> — "I found it, nobody needs to keep looking." Search.
 *       Any match will do, and the finder carries a value back.</li>
 *   <li><b>breakAfter</b> — "nothing beyond index N is wanted." A prefix
 *       computation. Chunks entirely past N are cancelled; chunks before it
 *       must still finish, because their results are part of the answer.</li>
 * </ul>
 */
class EarlyExitTest {

    private EarlyExit exit;

    @BeforeEach
    void setUp() {
        exit = new EarlyExit("job-1");
    }

    // ---- breakAll: search ----

    @Test
    void breakAllCarriesTheFoundValueBack() {
        exit.breakAll(42, "key-0xCAFE");

        assertTrue(exit.isStopped());
        assertEquals("key-0xCAFE", exit.result().orElseThrow());
        assertEquals(42, exit.foundAt().orElseThrow());
    }

    @Test
    void breakAllWithoutAValueIsStillAStop() {
        // "Stop, I am done" without carrying anything back.
        exit.breakAll(7, null);

        assertTrue(exit.isStopped());
        assertTrue(exit.result().isEmpty());
        assertEquals(7, exit.foundAt().orElseThrow());
    }

    @Test
    void theFirstFinderWins() {
        // Two nodes may find a match at once. Which arrives first is not
        // deterministic, but the recorded answer must be, so callers can rely
        // on reading one consistent result.
        exit.breakAll(10, "from-node-A");
        exit.breakAll(3, "from-node-B");

        assertEquals("from-node-A", exit.result().orElseThrow());
        assertEquals(10, exit.foundAt().orElseThrow());
    }

    /**
     * The guarantee breakAll deliberately does <em>not</em> make.
     */
    @Test
    void breakAllDoesNotPromiseTheLowestMatchingIndex() {
        // Node B's match at index 3 is earlier than node A's at 10, but A
        // reported first. If you need the lowest index, collect all matches
        // and take the minimum — do not use breakAll.
        exit.breakAll(10, "later-index-but-reported-first");
        exit.breakAll(3, "lower-index-reported-second");

        assertEquals(10, exit.foundAt().orElseThrow(),
                "breakAll is find-any, not find-first");
    }

    @Test
    void everyChunkIsCancelledByBreakAll() {
        exit.breakAll(50, "found");

        assertFalse(exit.shouldRunChunk(0, 10));
        assertFalse(exit.shouldRunChunk(90, 100));
    }

    // ---- breakAfter: prefix ----

    @Test
    void breakAfterCancelsOnlyChunksEntirelyBeyondTheBoundary() {
        exit.breakAfter(100, "converged");

        // Entirely before the boundary: still needed, its results are part of
        // the answer.
        assertTrue(exit.shouldRunChunk(0, 50));
        // Straddles the boundary: must run, it contains wanted iterations.
        assertTrue(exit.shouldRunChunk(90, 110));
        // Entirely beyond: nothing in it is wanted.
        assertFalse(exit.shouldRunChunk(101, 200));
        assertFalse(exit.shouldRunChunk(500, 600));
    }

    @Test
    void aChunkStartingExactlyAtTheBoundaryStillRuns() {
        // The boundary index itself is wanted; "after 100" means 101 onward.
        exit.breakAfter(100, "converged");
        assertTrue(exit.shouldRunChunk(100, 120));
    }

    @Test
    void theLowestBoundaryWins() {
        // Two nodes may each decide to stop at different points. The tighter
        // bound is the correct one — anything past it was already unwanted.
        exit.breakAfter(500, "first");
        exit.breakAfter(100, "tighter");

        assertEquals(100, exit.boundary().orElseThrow());
        assertFalse(exit.shouldRunChunk(200, 300));
    }

    @Test
    void aLaterHigherBoundaryDoesNotLoosenAnEarlierOne() {
        exit.breakAfter(100, "tight");
        exit.breakAfter(900, "looser");

        assertEquals(100, exit.boundary().orElseThrow());
    }

    @Test
    void breakAfterIsStoppedButNotEverywhere() {
        exit.breakAfter(100, "converged");

        assertTrue(exit.isStopped());
        assertTrue(exit.shouldRunChunk(0, 99), "the prefix is still required");
    }

    // ---- interaction and validation ----

    @Test
    void breakAllOverridesAPriorBreakAfter() {
        // A definite answer makes even the prefix unnecessary.
        exit.breakAfter(100, "converged");
        exit.breakAll(5, "actually found it");

        assertFalse(exit.shouldRunChunk(0, 50));
        assertEquals("actually found it", exit.result().orElseThrow());
    }

    @Test
    void aFreshExitRunsEverything() {
        assertFalse(exit.isStopped());
        assertTrue(exit.shouldRunChunk(0, 10));
        assertTrue(exit.shouldRunChunk(1_000_000, 2_000_000));
        assertTrue(exit.result().isEmpty());
    }

    @Test
    void rejectsNonsense() {
        assertThrows(IllegalArgumentException.class, () -> new EarlyExit(null));
        assertThrows(IllegalArgumentException.class, () -> exit.breakAfter(-1, "x"));
        assertThrows(IllegalArgumentException.class, () -> exit.breakAll(-1, "x"));
    }

    @Test
    void resultsAreSerialisableForTheReturnTrip() {
        // The found value travels from the finding node back to the submitter.
        exit.breakAll(1, "answer");
        Optional<Object> value = exit.result();

        assertTrue(value.isPresent());
        assertTrue(value.get() instanceof java.io.Serializable,
                "a value that cannot be serialised cannot come home");
    }
}
