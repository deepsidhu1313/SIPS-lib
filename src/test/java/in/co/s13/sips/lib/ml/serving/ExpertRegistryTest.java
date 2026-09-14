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

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who hosts what, and how backed up each host is right now.
 */
class ExpertRegistryTest {

    @Test
    void aReplicaIsRegisteredAndListedSorted() {
        ExpertRegistry experts = new ExpertRegistry();
        experts.addReplica("router-8x7b", "node-b");
        experts.addReplica("router-8x7b", "node-a");

        assertEquals(List.of("node-a", "node-b"), experts.replicasOf("router-8x7b"));
    }

    @Test
    void addingTheSameReplicaTwiceIsANoOp() {
        ExpertRegistry experts = new ExpertRegistry();
        experts.addReplica("router-8x7b", "node-a");
        experts.addReplica("router-8x7b", "node-a");

        assertEquals(List.of("node-a"), experts.replicasOf("router-8x7b"));
    }

    @Test
    void anUnregisteredExpertIsRefusedByName() {
        ExpertRegistry experts = new ExpertRegistry();

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> experts.replicasOf("ghost-expert"));
        assertTrue(thrown.getMessage().contains("ghost-expert"), thrown.getMessage());
    }

    @Test
    void leastLoadedPicksTheActuallyLeastLoadedReplica() {
        ExpertRegistry experts = new ExpertRegistry();
        experts.addReplica("coder", "node-a");
        experts.addReplica("coder", "node-b");
        experts.addReplica("coder", "node-c");

        experts.beginRequest("coder", "node-a");
        experts.beginRequest("coder", "node-a");
        experts.beginRequest("coder", "node-a");
        experts.beginRequest("coder", "node-b");
        // node-c stays at zero.

        assertEquals("node-c", experts.leastLoaded("coder"));
    }

    @Test
    void leastLoadedTiesAreBrokenByLowestNodeUuid() {
        ExpertRegistry experts = new ExpertRegistry();
        experts.addReplica("coder", "node-z");
        experts.addReplica("coder", "node-a");
        experts.beginRequest("coder", "node-z");
        experts.beginRequest("coder", "node-a");
        // Both replicas are now at depth 1: an exact tie.

        assertEquals("node-a", experts.leastLoaded("coder"));
    }

    @Test
    void beginAndEndRequestMoveTheCounterExactly() {
        ExpertRegistry experts = new ExpertRegistry();
        experts.addReplica("coder", "node-a");
        assertEquals(0, experts.queueDepth("coder", "node-a"));

        experts.beginRequest("coder", "node-a");
        experts.beginRequest("coder", "node-a");
        assertEquals(2, experts.queueDepth("coder", "node-a"));

        experts.endRequest("coder", "node-a");
        assertEquals(1, experts.queueDepth("coder", "node-a"));
    }

    @Test
    void endRequestPastZeroClampsInsteadOfGoingNegative() {
        // Chosen behaviour: clamp at zero rather than throw. A caller bug that double-ends a
        // request should not permanently corrupt the counter for every request after it.
        ExpertRegistry experts = new ExpertRegistry();
        experts.addReplica("coder", "node-a");

        experts.endRequest("coder", "node-a");

        assertEquals(0, experts.queueDepth("coder", "node-a"));
    }
}
