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
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Finishing a round when a worker goes into someone's pocket.
 *
 * <p>A round ends when its slowest worker ends, and on a fleet of phones the
 * slowest worker is sometimes one that will never answer at all — the screen
 * locked, the network dropped, the owner walked out of range. Waiting for it
 * stalls everything behind it; abandoning its shard trains the round on less
 * data than it thinks.
 *
 * <p>So a shard that misses its deadline is handed to somebody else while the
 * original is still out, and the first answer to arrive wins.
 */
class SpeculativeRoundTest {

    private static SpeculativeRound round(int shards, String... workers) {
        return new SpeculativeRound(shards, List.of(workers), 1000);
    }

    @Test
    void everyShardIsIssuedOnceToStart() {
        SpeculativeRound round = round(3, "a", "b", "c");

        List<SpeculativeRound.Assignment> issued = round.issue(0);

        assertEquals(3, issued.size());
        assertEquals(3, issued.stream().map(SpeculativeRound.Assignment::worker)
                .distinct().count(), "one shard each, not three to one worker");
    }

    @Test
    void nothingIsReissuedBeforeItsDeadline() {
        SpeculativeRound round = round(2, "a", "b", "c");
        round.issue(0);

        assertTrue(round.reissue(999).isEmpty(), "the deadline has not passed yet");
    }

    @Test
    void aLateShardIsHandedToSomeoneElse() {
        SpeculativeRound round = round(2, "a", "b", "c");
        round.issue(0);
        round.accept(0, "a", 500);

        List<SpeculativeRound.Assignment> again = round.reissue(1001);

        assertEquals(1, again.size(), "only the unanswered shard");
        assertEquals(1, again.get(0).shard());
    }

    @Test
    void areissueGoesToAWorkerThatDoesNotAlreadyHaveIt() {
        // Handing it back to the phone that is already not answering achieves
        // nothing at all.
        SpeculativeRound round = round(1, "a", "b");
        round.issue(0);

        SpeculativeRound.Assignment again = round.reissue(1001).get(0);

        assertEquals("b", again.worker());
    }

    @Test
    void theFirstAnswerWinsAndTheSecondIsIgnored() {
        // Both copies are running, so both may come back. The second is not a
        // failure -- it is just late.
        SpeculativeRound round = round(1, "a", "b");
        round.issue(0);
        round.reissue(1001);

        assertTrue(round.accept(0, "b", 1200), "the first answer counts");
        assertFalse(round.accept(0, "a", 1300), "the loser's answer is dropped");
    }

    @Test
    void aShardIsNeverAcceptedTwice() {
        // The trap this class exists to avoid. Averaging one shard's model
        // twice overweights that shard's data -- a wrong model rather than a
        // failure, and one that looks entirely plausible.
        SpeculativeRound round = round(2, "a", "b", "c");
        round.issue(0);
        round.reissue(1001);

        round.accept(0, "a", 1100);
        round.accept(0, "c", 1150);

        assertEquals(1, round.accepted().size());
    }

    @Test
    void aRoundIsCompleteOnlyWhenEveryShardHasAnAnswer() {
        SpeculativeRound round = round(2, "a", "b");
        round.issue(0);

        round.accept(0, "a", 100);
        assertFalse(round.complete());

        round.accept(1, "b", 200);
        assertTrue(round.complete());
    }

    @Test
    void aShardWithNobodyLeftToTryIsNotSilentlyDropped() {
        // Two workers, both already given this shard, both silent. There is
        // nothing more to do -- and a round that quietly averaged one shard
        // fewer would be training on less data than it reports.
        SpeculativeRound round = round(1, "a", "b");
        round.issue(0);
        round.reissue(1001);

        assertTrue(round.reissue(2001).isEmpty());
        assertFalse(round.complete());
        assertEquals(List.of(0), round.outstanding());
    }

    @Test
    void aWorkerThatAnswersAShardItWasNeverGivenIsRefused() {
        // Not paranoia once the workers are strangers' devices: a result that
        // was never asked for has no place in the average.
        SpeculativeRound round = round(2, "a", "b");
        round.issue(0);

        assertFalse(round.accept(1, "a", 100),
                "worker a was given shard 0, not shard 1");
    }

    @Test
    void reissuingSpreadsAcrossWorkersRatherThanPilingOnOne() {
        // Otherwise the one healthy node inherits every stalled shard and
        // becomes the new bottleneck.
        SpeculativeRound round = new SpeculativeRound(2, List.of("a", "b", "c", "d"), 1000);
        round.issue(0);

        List<SpeculativeRound.Assignment> again = round.reissue(1001);

        assertEquals(2, again.stream().map(SpeculativeRound.Assignment::worker)
                .distinct().count());
    }

    @Test
    void whoAnsweredAShardIsAnswerable() {
        SpeculativeRound round = round(1, "a", "b");
        round.issue(0);
        round.accept(0, "a", 100);

        assertEquals(Optional.of("a"), round.answeredBy(0));
        assertEquals(Optional.empty(), round.answeredBy(99));
    }

    @Test
    void nonsenseIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new SpeculativeRound(0, List.of("a"), 1000));
        assertThrows(IllegalArgumentException.class,
                () -> new SpeculativeRound(1, List.of(), 1000));
        assertThrows(IllegalArgumentException.class,
                () -> new SpeculativeRound(1, List.of("a"), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new SpeculativeRound(3, List.of("a", "b"), 1000));
    }
}
