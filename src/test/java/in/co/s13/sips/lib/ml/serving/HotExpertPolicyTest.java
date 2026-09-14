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
}
