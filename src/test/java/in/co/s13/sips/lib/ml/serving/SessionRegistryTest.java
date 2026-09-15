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
package in.co.s13.sips.lib.ml.serving;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Session-to-node pinning with expiry, exercised with a clock the test moves
 * forward by hand -- no {@code Thread.sleep} required.
 */
class SessionRegistryTest {

    /** A {@link Clock} whose {@code instant()} the test advances deterministically. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void aFreshPinIsFound() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        SessionRegistry sessions = new SessionRegistry(Duration.ofMinutes(5), clock);

        sessions.pin("s1", "node-a");

        assertEquals(Optional.of("node-a"), sessions.nodeFor("s1"));
    }

    @Test
    void justUnderTheTimeoutTheSessionIsStillPresent() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        SessionRegistry sessions = new SessionRegistry(Duration.ofMinutes(5), clock);
        sessions.pin("s1", "node-a");

        clock.advance(Duration.ofMinutes(5).minusMillis(1));

        assertEquals(Optional.of("node-a"), sessions.nodeFor("s1"));
    }

    @Test
    void pastTheTimeoutTheSessionReadsAsAbsent() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        SessionRegistry sessions = new SessionRegistry(Duration.ofMinutes(5), clock);
        sessions.pin("s1", "node-a");

        clock.advance(Duration.ofMinutes(5).plusMillis(1));

        assertEquals(Optional.empty(), sessions.nodeFor("s1"));
    }

    @Test
    void touchRefreshesLastSeenWithoutChangingTheNode() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        SessionRegistry sessions = new SessionRegistry(Duration.ofMinutes(5), clock);
        sessions.pin("s1", "node-a");

        clock.advance(Duration.ofMinutes(4));
        sessions.touch("s1");
        clock.advance(Duration.ofMinutes(4));

        // 8 minutes have passed in total, but the touch at minute 4 reset the clock, so this
        // session is only 4 minutes stale -- still within the 5 minute timeout.
        assertEquals(Optional.of("node-a"), sessions.nodeFor("s1"));
    }

    @Test
    void touchingAnUnknownSessionThrows() {
        SessionRegistry sessions = new SessionRegistry(Duration.ofMinutes(5), new MutableClock(Instant.EPOCH));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> sessions.touch("ghost"));
        assertTrue(thrown.getMessage().contains("ghost"), thrown.getMessage());
    }

    @Test
    void touchingAnExpiredSessionThrows() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        SessionRegistry sessions = new SessionRegistry(Duration.ofMinutes(5), clock);
        sessions.pin("s1", "node-a");

        clock.advance(Duration.ofMinutes(10));

        assertThrows(IllegalStateException.class, () -> sessions.touch("s1"));
    }

    @Test
    void evictExpiredRemovesOnlyTheStaleEntriesAndCountsThem() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        SessionRegistry sessions = new SessionRegistry(Duration.ofMinutes(5), clock);
        sessions.pin("stale-1", "node-a");
        sessions.pin("stale-2", "node-a");

        clock.advance(Duration.ofMinutes(10));
        sessions.pin("fresh", "node-b");

        int removed = sessions.evictExpired();

        assertEquals(2, removed);
        assertEquals(Optional.empty(), sessions.nodeFor("stale-1"));
        assertEquals(Optional.empty(), sessions.nodeFor("stale-2"));
        assertEquals(Optional.of("node-b"), sessions.nodeFor("fresh"));
    }

    @Test
    void constructorRejectsNonsense() {
        assertThrows(IllegalArgumentException.class,
                () -> new SessionRegistry(Duration.ZERO, Clock.systemUTC()));
        assertThrows(IllegalArgumentException.class,
                () -> new SessionRegistry(Duration.ofMinutes(1), null));
    }

    @Test
    void updateStateAndStateForRoundTrip() {
        SessionRegistry sessions = new SessionRegistry(Duration.ofMinutes(5), new MutableClock(Instant.EPOCH));
        sessions.pin("s1", "node-a");

        sessions.updateState("s1", "kv-cache-ref-42");

        assertEquals(Optional.of("kv-cache-ref-42"), sessions.stateFor("s1"));
        // The pin itself is untouched by a state-only update.
        assertEquals(Optional.of("node-a"), sessions.nodeFor("s1"));
    }

    @Test
    void updateStateOnASessionThatNeverExistedThrows() {
        SessionRegistry sessions = new SessionRegistry(Duration.ofMinutes(5), new MutableClock(Instant.EPOCH));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> sessions.updateState("ghost", "some-state"));
        assertTrue(thrown.getMessage().contains("ghost"), thrown.getMessage());
    }

    @Test
    void pinPreservesExistingStateAcrossAReRepinToADifferentNode() {
        SessionRegistry sessions = new SessionRegistry(Duration.ofMinutes(5), new MutableClock(Instant.EPOCH));
        sessions.pin("s1", "node-a");
        sessions.updateState("s1", "conversation-history");

        sessions.pin("s1", "node-b");

        assertEquals(Optional.of("node-b"), sessions.nodeFor("s1"));
        assertEquals(Optional.of("conversation-history"), sessions.stateFor("s1"));
    }

    @Test
    void markUnavailableHidesTheNodeFromRoutingButNotTheState() {
        SessionRegistry sessions = new SessionRegistry(Duration.ofMinutes(5), new MutableClock(Instant.EPOCH));
        sessions.pin("s1", "node-a");
        sessions.updateState("s1", "conversation-history");

        sessions.markUnavailable("node-a");

        assertEquals(Optional.empty(), sessions.nodeFor("s1"));
        assertEquals(Optional.of("conversation-history"), sessions.stateFor("s1"));
    }

    @Test
    void markAvailableMakesAStillLiveSessionRoutableAgainWithoutReRepinning() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        SessionRegistry sessions = new SessionRegistry(Duration.ofMinutes(5), clock);
        sessions.pin("s1", "node-a");
        sessions.markUnavailable("node-a");
        assertEquals(Optional.empty(), sessions.nodeFor("s1"));

        clock.advance(Duration.ofMinutes(1));
        sessions.markAvailable("node-a");

        assertEquals(Optional.of("node-a"), sessions.nodeFor("s1"));
    }
}
