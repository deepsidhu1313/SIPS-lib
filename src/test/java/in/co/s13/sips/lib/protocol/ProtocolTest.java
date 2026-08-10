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
package in.co.s13.sips.lib.protocol;

import in.co.s13.sips.lib.protocol.Protocol.Compatibility;
import in.co.s13.sips.lib.protocol.Protocol.Feature;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What one node may ask another to do.
 *
 * <p>The whole reason for this is a half-upgraded cluster, which is the normal
 * state during any rollout and the one state nobody tests. The interesting cases
 * are all about a peer that predates negotiation entirely — every release so far
 * — because that is what an upgrade actually meets.
 */
class ProtocolTest {

    @Test
    void apeerThatSaysNothingIsUnknownRatherThanCurrent() {
        // Assuming a silent peer is current is exactly the bug this replaces.
        assertEquals(Protocol.UNKNOWN, Protocol.of(new JSONObject()));
        assertEquals(Protocol.UNKNOWN, Protocol.of(null));
        assertEquals(Protocol.UNKNOWN, Protocol.of(new JSONObject().put("HOSTNAME", "n1")));
    }

    @Test
    void apeerAnnouncesItselfInItsReply() {
        JSONObject reply = Protocol.stamp(new JSONObject().put("HOSTNAME", "n1"));

        assertEquals(Protocol.VERSION, Protocol.of(reply));
        assertEquals("n1", reply.getString("HOSTNAME"), "stamping must not disturb the rest");
    }

    // ---- what a mixed cluster can still do ----

    @Test
    void anOldPeerStillGetsWhatItWouldSafelyIgnore() {
        // Early exit is the case. An older node has no handler for breakAll, so
        // it drops the message and its chunk runs to completion -- slower than
        // it needed to be, but correct. Withholding it would buy nothing.
        assertTrue(Protocol.canSend(Feature.EARLY_EXIT, Protocol.UNKNOWN));
        assertTrue(Protocol.canSend(Feature.INLINE_RESULTS, Protocol.UNKNOWN));
    }

    @Test
    void anOldPeerIsNotGivenWorkItWouldFail() {
        // A WASM manifest has no MAIN, so an older node throws reading it. A
        // staged manifest goes down the single-loop path and produces nothing.
        // Both waste the chunk and report the failure somewhere unhelpful.
        assertFalse(Protocol.canSend(Feature.WASM_TASKS, Protocol.UNKNOWN));
        assertFalse(Protocol.canSend(Feature.STAGED_JOBS, Protocol.UNKNOWN));
    }

    @Test
    void acurrentPeerGetsEverything() {
        for (Feature feature : Feature.values()) {
            assertTrue(Protocol.canSend(feature, Protocol.VERSION),
                    feature + " should be sendable to a current node");
        }
    }

    @Test
    void everyFeatureSaysHowItFailsOnAnOlderNode() {
        // The classification is the substance here; a feature added without
        // thinking about it would default to whatever the enum author typed.
        for (Feature feature : Feature.values()) {
            assertTrue(feature.compatibility() == Compatibility.IGNORED_BY_OLDER
                    || feature.compatibility() == Compatibility.BREAKS_ON_OLDER);
            assertTrue(feature.since() >= 1, feature + " must be introduced by some version");
            assertTrue(feature.since() <= Protocol.VERSION,
                    feature + " claims to need protocol " + feature.since()
                    + ", which is newer than this build speaks");
        }
    }

    @Test
    void arefusalSaysWhatToDoAboutIt() {
        // "protocol 0 < 1" is not something an operator can act on.
        String reason = Protocol.refusalReason(Feature.WASM_TASKS, Protocol.UNKNOWN);

        assertTrue(reason.contains("wasm tasks"), reason);
        assertTrue(reason.toLowerCase().contains("upgrade"), reason);
        assertTrue(reason.contains("before protocol negotiation"), reason);
    }

    // ---- agreeing on a version ----

    @Test
    void twoNodesAgreeOnTheOlderOfThem() {
        assertEquals(Protocol.UNKNOWN, Protocol.agreed(Protocol.VERSION, Protocol.UNKNOWN));
        assertEquals(Protocol.VERSION, Protocol.agreed(Protocol.VERSION, Protocol.VERSION));
    }

    @Test
    void apeerClaimingTheFutureIsTreatedAsCurrent() {
        // A newer node knows about this one; this one knows nothing about it.
        // Staying compatible is the newer node's job, so believing its claim
        // beyond what this build understands would be the wrong risk.
        JSONObject fromTheFuture = new JSONObject().put(Protocol.FIELD, Protocol.VERSION + 99);

        assertEquals(Protocol.VERSION, Protocol.of(fromTheFuture));
    }

    @Test
    void anonsensicalVersionIsTreatedAsUnknown() {
        assertEquals(Protocol.UNKNOWN,
                Protocol.of(new JSONObject().put(Protocol.FIELD, -5)));
        assertEquals(Protocol.UNKNOWN, Protocol.agreed(-3, 7));
    }

    @Test
    void nonsenseIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> Protocol.canSend(null, Protocol.VERSION));
        assertThrows(IllegalArgumentException.class, () -> Protocol.stamp(null));
    }
}
