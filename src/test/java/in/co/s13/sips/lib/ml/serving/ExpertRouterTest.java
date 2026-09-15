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
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The routing decision itself: affinity first, load-aware fallback second.
 */
class ExpertRouterTest {

    @Test
    void aFreshSessionRoutesToLeastLoadedAndThenPins() {
        ExpertRegistry experts = new ExpertRegistry();
        experts.addReplica("coder", "node-a");
        experts.addReplica("coder", "node-b");
        experts.beginRequest("coder", "node-a");
        // node-b is the least loaded.
        SessionRegistry sessions = new SessionRegistry(Duration.ofMinutes(5), Clock.systemUTC());
        ExpertRouter router = new ExpertRouter(experts, sessions);

        ExpertRouter.Routed routed = router.route("s1", "coder");

        assertEquals("node-b", routed.nodeUuid());
        assertFalse(routed.sessionAffinityHit());
        assertFalse(routed.recovered());
        assertEquals(Optional.of("node-b"), sessions.nodeFor("s1"));
    }

    @Test
    void aSessionPinnedToAHostOfTheRequestedExpertKeepsItEvenWhenNotLeastLoaded() {
        ExpertRegistry experts = new ExpertRegistry();
        experts.addReplica("coder", "node-a");
        experts.addReplica("coder", "node-b");
        SessionRegistry sessions = new SessionRegistry(Duration.ofMinutes(5), Clock.systemUTC());
        sessions.pin("s1", "node-a");
        // Load node-a up so it is clearly NOT the least loaded replica at call time -- if
        // affinity were not really overriding load balancing, this request would go to node-b.
        experts.beginRequest("coder", "node-a");
        experts.beginRequest("coder", "node-a");
        experts.beginRequest("coder", "node-a");
        ExpertRouter router = new ExpertRouter(experts, sessions);

        ExpertRouter.Routed routed = router.route("s1", "coder");

        assertEquals("node-a", routed.nodeUuid());
        assertTrue(routed.sessionAffinityHit());
        assertFalse(routed.recovered());
    }

    @Test
    void aSessionPinnedToANonHostFallsThroughAndRePins() {
        ExpertRegistry experts = new ExpertRegistry();
        experts.addReplica("embedder", "node-a");
        experts.addReplica("coder", "node-b");
        SessionRegistry sessions = new SessionRegistry(Duration.ofMinutes(5), Clock.systemUTC());
        // Pinned from a previous request to a different expert -- node-a does not host "coder".
        sessions.pin("s1", "node-a");
        ExpertRouter router = new ExpertRouter(experts, sessions);

        ExpertRouter.Routed routed = router.route("s1", "coder");

        assertEquals("node-b", routed.nodeUuid());
        assertFalse(routed.sessionAffinityHit());
        // s1 was pinned before, but never carried any state, so this is a fresh fallback,
        // not a recovery.
        assertFalse(routed.recovered());
        assertEquals(Optional.of("node-b"), sessions.nodeFor("s1"));
    }

    @Test
    void aSessionWithPriorStateRecoversToADifferentNodeWhenItsNodeGoesUnavailable() {
        ExpertRegistry experts = new ExpertRegistry();
        experts.addReplica("coder", "node-a");
        experts.addReplica("coder", "node-b");
        SessionRegistry sessions = new SessionRegistry(Duration.ofMinutes(5), Clock.systemUTC());
        sessions.pin("s1", "node-a");
        sessions.updateState("s1", "conversation-history");
        ExpertRouter router = new ExpertRouter(experts, sessions);
        // s1 is happily pinned to node-a and would keep hitting it...
        assertTrue(router.route("s1", "coder").sessionAffinityHit());

        // ...until node-a disappears mid-conversation. Loading node-a up first ensures the
        // fallback below genuinely picks node-b on its own load-balancing merits, rather than
        // by a tie-break that would happen to also pick node-a if both sat at equal depth.
        experts.beginRequest("coder", "node-a");
        experts.beginRequest("coder", "node-a");
        sessions.markUnavailable("node-a");

        ExpertRouter.Routed routed = router.route("s1", "coder");

        assertEquals("node-b", routed.nodeUuid());
        assertFalse(routed.sessionAffinityHit());
        assertTrue(routed.recovered(), "a session with real prior state landing elsewhere is a recovery");
        assertEquals(Optional.of("conversation-history"), sessions.stateFor("s1"));
    }

    @Test
    void aGenuinelyFreshSessionsFirstRouteIsNeverAReportedRecovery() {
        ExpertRegistry experts = new ExpertRegistry();
        experts.addReplica("coder", "node-a");
        SessionRegistry sessions = new SessionRegistry(Duration.ofMinutes(5), Clock.systemUTC());
        ExpertRouter router = new ExpertRouter(experts, sessions);

        ExpertRouter.Routed routed = router.route("brand-new-session", "coder");

        assertEquals("node-a", routed.nodeUuid());
        assertFalse(routed.sessionAffinityHit());
        assertFalse(routed.recovered());
    }
}
