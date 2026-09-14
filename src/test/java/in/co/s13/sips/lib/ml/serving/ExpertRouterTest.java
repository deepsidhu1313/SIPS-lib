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
        assertEquals(Optional.of("node-b"), sessions.nodeFor("s1"));
    }
}
