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
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reactive rebalancer: add a replica when an expert is hot, and only when
 * there is somewhere sane to put it.
 */
class HotExpertPolicyTest {

    @Test
    void underThresholdTriggersNoRebalance() {
        ExpertRegistry experts = new ExpertRegistry();
        experts.addReplica("coder", "node-a");
        experts.beginRequest("coder", "node-a");
        // depth 1, threshold 3 -- not hot.
        HotExpertPolicy policy = new HotExpertPolicy(3);

        int added = policy.rebalance(experts, List.of("coder"), Map.of("node-a", 1000L, "node-b", 1000L),
                Map.of("coder", 100L));

        assertEquals(0, added);
        assertEquals(List.of("node-a"), experts.replicasOf("coder"));
    }

    @Test
    void anOverThresholdExpertGetsTheLeastLoadedEligibleCandidate() {
        ExpertRegistry experts = new ExpertRegistry();
        experts.addReplica("coder", "node-a");
        for (int i = 0; i < 11; i++) {
            experts.beginRequest("coder", "node-a");
        }
        // coder's total queue depth is 11, over the threshold of 10.
        experts.addReplica("other", "node-b");
        for (int i = 0; i < 5; i++) {
            experts.beginRequest("other", "node-b");
        }
        // node-b already carries load 5 from a different expert; node-z carries none. Both
        // have enough memory and neither already hosts "coder", so this is a genuine choice
        // between two eligible candidates, not just the first one found.
        HotExpertPolicy policy = new HotExpertPolicy(10);
        Map<String, Long> nodeMemoryBytes = Map.of("node-a", 1000L, "node-b", 1000L, "node-z", 1000L);
        Map<String, Long> expertWeightBytes = Map.of("coder", 100L);

        int added = policy.rebalance(experts, List.of("coder", "other"), nodeMemoryBytes, expertWeightBytes);

        assertEquals(1, added);
        assertEquals(List.of("node-a", "node-z"), experts.replicasOf("coder"));
    }

    @Test
    void noEligibleCandidateAddsNothingAndDoesNotThrow() {
        ExpertRegistry experts = new ExpertRegistry();
        experts.addReplica("coder", "node-a");
        experts.addReplica("coder", "node-b");
        for (int i = 0; i < 3; i++) {
            experts.beginRequest("coder", "node-a");
        }
        // Both candidate nodes already host "coder" -- there is nowhere left to add.
        HotExpertPolicy policy = new HotExpertPolicy(2);
        Map<String, Long> nodeMemoryBytes = Map.of("node-a", 1000L, "node-b", 1000L);

        int added = policy.rebalance(experts, List.of("coder"), nodeMemoryBytes, Map.of("coder", 100L));

        assertEquals(0, added);
        assertEquals(List.of("node-a", "node-b"), experts.replicasOf("coder"));
    }

    @Test
    void aCandidateWithTooLittleMemoryIsExcluded() {
        ExpertRegistry experts = new ExpertRegistry();
        experts.addReplica("coder", "node-a");
        for (int i = 0; i < 3; i++) {
            experts.beginRequest("coder", "node-a");
        }
        HotExpertPolicy policy = new HotExpertPolicy(2);
        // node-b has only 50 bytes free; the expert weighs 100.
        Map<String, Long> nodeMemoryBytes = Map.of("node-a", 1000L, "node-b", 50L);
        Map<String, Long> expertWeightBytes = Map.of("coder", 100L);

        int added = policy.rebalance(experts, List.of("coder"), nodeMemoryBytes, expertWeightBytes);

        assertEquals(0, added);
        assertEquals(List.of("node-a"), experts.replicasOf("coder"));
    }

    @Test
    void theMostSeverelyOverloadedExpertWinsTheOneGenuinelyScarceCandidate() {
        ExpertRegistry experts = new ExpertRegistry();
        // e1: depth 10 (severity 7), e2: depth 7 (severity 4), e3: depth 5 (severity 2) --
        // each expert's home node is its only existing replica, so totalQueueDepth resolves.
        experts.addReplica("e1", "home-e1");
        for (int i = 0; i < 10; i++) {
            experts.beginRequest("e1", "home-e1");
        }
        experts.addReplica("e2", "home-e2");
        for (int i = 0; i < 7; i++) {
            experts.beginRequest("e2", "home-e2");
        }
        experts.addReplica("e3", "home-e3");
        for (int i = 0; i < 5; i++) {
            experts.beginRequest("e3", "home-e3");
        }
        HotExpertPolicy policy = new HotExpertPolicy(3);
        // Every home node is far too small to host any of the three experts; "big-spare" has
        // just enough memory for exactly ONE of them, so once one expert's replica lands there,
        // the memory-depletion accounting must rule the other two out within the same call.
        Map<String, Long> nodeMemoryBytes = Map.of(
                "home-e1", 10L, "home-e2", 10L, "home-e3", 10L, "big-spare", 500L);
        Map<String, Long> expertWeightBytes = Map.of("e1", 500L, "e2", 500L, "e3", 500L);

        // Passed out of severity order -- e3 (least severe) is listed first, e1 (most severe)
        // last -- so a correct result here can only come from sorting by severity, not from
        // input order.
        int added = policy.rebalance(experts, List.of("e3", "e2", "e1"), nodeMemoryBytes, expertWeightBytes);

        assertEquals(1, added);
        assertTrue(experts.replicasOf("e1").contains("big-spare"), "e1, the most severe, should win big-spare");
        assertFalse(experts.replicasOf("e2").contains("big-spare"), "e2 must not also fit on the depleted node");
        assertFalse(experts.replicasOf("e3").contains("big-spare"), "e3 must not also fit on the depleted node");
    }
}
