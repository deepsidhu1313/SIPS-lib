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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adds a replica of an overloaded expert reactively, instead of provisioning
 * every expert for its worst case up front.
 *
 * <p>Most experts in a mixture-of-experts fleet are cold most of the time;
 * capacity is cheaper spent watching queue depth and reacting to the expert
 * that is actually hot right now than replicating everything for a load
 * spike that may never come. This policy is deliberately simple — one
 * threshold, one new replica per over-threshold expert per call — so its
 * decisions stay auditable: a caller who runs it on a schedule can watch
 * replica counts track load, rather than trusting an opaque autoscaler.
 */
public final class HotExpertPolicy {

    private final long queueDepthThreshold;

    public HotExpertPolicy(long queueDepthThreshold) {
        if (queueDepthThreshold <= 0) {
            throw new IllegalArgumentException("queueDepthThreshold must be positive");
        }
        this.queueDepthThreshold = queueDepthThreshold;
    }

    /**
     * Adds one replica for each expert in {@code expertIds} whose total queue depth across its
     * current replicas is at or above the threshold, choosing the eligible candidate node with
     * the lowest total queue depth across everything it already hosts. Experts are processed in
     * order of severity — {@code totalQueueDepth(expertId) - queueDepthThreshold} — most
     * severely overloaded first (ties broken by expertId, ascending), rather than in whatever
     * order {@code expertIds} happens to list them, so that when several experts are
     * simultaneously hot and spare capacity is scarce, the worst-off expert is served first. A
     * node's memory is tracked as it depletes across replicas placed earlier in this same call,
     * so two different experts cannot each be handed a replica on a node that could not actually
     * hold both of them. An over-threshold expert with no eligible candidate remaining is
     * skipped, not an error.
     *
     * @return the total number of replicas actually added
     */
    public int rebalance(ExpertRegistry experts, List<String> expertIds, Map<String, Long> nodeMemoryBytes,
            Map<String, Long> expertWeightBytes) {
        List<String> orderedExpertIds = new ArrayList<>(expertIds);
        Comparator<String> bySeverityDesc = Comparator
                .comparingLong((String expertId) -> totalQueueDepth(experts, expertId) - queueDepthThreshold)
                .reversed();
        orderedExpertIds.sort(bySeverityDesc.thenComparing(Comparator.naturalOrder()));

        Map<String, Long> remainingMemory = new HashMap<>(nodeMemoryBytes);
        int added = 0;
        for (String expertId : orderedExpertIds) {
            if (totalQueueDepth(experts, expertId) < queueDepthThreshold) {
                continue;
            }
            String candidate = bestCandidate(experts, expertId, orderedExpertIds, remainingMemory, expertWeightBytes);
            if (candidate != null) {
                experts.addReplica(expertId, candidate);
                remainingMemory.merge(candidate, -expertWeightBytes.getOrDefault(expertId, 0L), Long::sum);
                added++;
            }
        }
        return added;
    }

    private long totalQueueDepth(ExpertRegistry experts, String expertId) {
        long total = 0;
        for (String node : experts.replicasOf(expertId)) {
            total += experts.queueDepth(expertId, node);
        }
        return total;
    }

    private String bestCandidate(ExpertRegistry experts, String expertId, List<String> expertIds,
            Map<String, Long> remainingMemory, Map<String, Long> expertWeightBytes) {
        List<String> currentReplicas = experts.replicasOf(expertId);
        long requiredBytes = expertWeightBytes.getOrDefault(expertId, 0L);

        String best = null;
        long bestLoad = Long.MAX_VALUE;
        List<String> candidates = new ArrayList<>(remainingMemory.keySet());
        Collections.sort(candidates);
        for (String candidate : candidates) {
            if (currentReplicas.contains(candidate)) {
                continue;
            }
            if (remainingMemory.get(candidate) < requiredBytes) {
                continue;
            }
            long load = totalLoadOn(experts, candidate, expertIds);
            if (load < bestLoad) {
                bestLoad = load;
                best = candidate;
            }
        }
        return best;
    }

    private long totalLoadOn(ExpertRegistry experts, String node, List<String> expertIds) {
        long total = 0;
        for (String otherExpertId : expertIds) {
            List<String> replicas;
            try {
                replicas = experts.replicasOf(otherExpertId);
            } catch (IllegalStateException noReplicasYet) {
                continue;
            }
            if (replicas.contains(node)) {
                total += experts.queueDepth(otherExpertId, node);
            }
        }
        return total;
    }
}
