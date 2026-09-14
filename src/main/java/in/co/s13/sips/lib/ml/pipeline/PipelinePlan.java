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
package in.co.s13.sips.lib.ml.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Deciding which node runs which stage, and how many copies of each.
 *
 * <p>A layer-group pipeline has a barrier {@link in.co.s13.sips.lib.ml.ShardPlan
 * ShardPlan} does not: throughput, not just completion time. Items stream
 * through every stage in order, so the pipeline's steady-state rate is capped
 * by its slowest stage — one under-provisioned stage backs up every stage
 * behind it and starves every stage ahead of it. Replicating a stage across
 * more than one node is the only lever this model has to raise that stage's
 * share of throughput, since a stage's own layers cannot be split further
 * without changing what "one stage" means.
 *
 * <p>The algorithm is two passes for the same reason {@code ShardPlan}
 * reserves at least one sample per worker before proportioning the rest: a
 * stage with zero replicas cannot run at all, so fairness is suspended until
 * every stage clears that bar. Pass one hands out exactly one replica per
 * stage, fastest eligible node first, so the whole pipeline is at least
 * runnable. Pass two then treats every leftover node as bandwidth to spend on
 * whichever stage is currently the bottleneck — the same "balance the
 * barrier" principle {@code ShardPlan} applies to sample counts, applied here
 * to discrete replica counts instead of a continuous split, because a stage
 * either has a node or it does not.
 *
 * <p>Eligibility is memory, not speed: a node whose memory cannot hold a
 * stage's resident weights cannot run that stage at any speed, so it is
 * removed from that stage's pool before either pass runs. A stage with no
 * eligible node anywhere is a real, reportable planning failure — the model
 * does not fit this cluster as split — not a bug to paper over with a smaller
 * shard, since a layer group's weights are not something this planner is
 * allowed to cut.
 */
public final class PipelinePlan {

    /** What an unbenchmarked node is assumed to be worth, mirroring {@code ShardPlan.UNMEASURED_SPEED}. */
    static final double UNMEASURED_SPEED = 1.0;

    /**
     * One node's usable memory and relative speed.
     *
     * @param speed zero or negative means unmeasured, treated as
     *        {@link #UNMEASURED_SPEED} wherever it is used — an unbenchmarked
     *        node is assumed average, not idle, so it is not starved of
     *        replicas simply for being new
     */
    public record NodeCapacity(String nodeUuid, long memoryBytes, double speed) {

        public NodeCapacity {
            if (nodeUuid == null || nodeUuid.isBlank()) {
                throw new IllegalArgumentException("A node needs a uuid");
            }
            if (memoryBytes <= 0) {
                throw new IllegalArgumentException("Node '" + nodeUuid
                        + "' needs positive memory, not " + memoryBytes);
            }
        }

        /** The speed this node is planned against. */
        double usableSpeed() {
            return speed > 0 ? speed : UNMEASURED_SPEED;
        }
    }

    /** Which node uuids run each stage index, in the order they were assigned. */
    public record Assignment(Map<Integer, List<String>> replicasByStage) {

        public Assignment {
            if (replicasByStage == null) {
                throw new IllegalArgumentException("An assignment needs a replica map, even if empty");
            }
            Map<Integer, List<String>> copy = new LinkedHashMap<>();
            for (Map.Entry<Integer, List<String>> entry : replicasByStage.entrySet()) {
                copy.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            replicasByStage = Collections.unmodifiableMap(copy);
        }

        /** The node uuids running one stage, in assignment order. */
        public List<String> replicasFor(int stageIndex) {
            return replicasByStage.getOrDefault(stageIndex, List.of());
        }

        /** How many replicas one stage has. */
        public int replicaCount(int stageIndex) {
            return replicasFor(stageIndex).size();
        }
    }

    private PipelinePlan() {
    }

    /**
     * Assigns every stage at least one node, then spends whatever nodes are
     * left over on the current throughput bottleneck. See the class
     * documentation for why this is two passes rather than one proportional
     * split.
     */
    public static Assignment assign(PipelineModel model, List<NodeCapacity> nodes) {
        if (model == null) {
            throw new IllegalArgumentException("Pipeline assignment needs a model");
        }
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("Pipeline assignment needs at least one node");
        }

        // Sorted, so the same cluster produces the same plan every run,
        // whatever order the caller's list happened to be in -- ShardPlan's
        // own reasoning for TreeMap, applied here to a List.
        List<NodeCapacity> ordered = new ArrayList<>(nodes);
        ordered.sort(Comparator.comparing(NodeCapacity::nodeUuid));

        int stageCount = model.stageCount();
        List<List<NodeCapacity>> eligibleByStage = new ArrayList<>(stageCount);
        for (int i = 0; i < stageCount; i++) {
            LayerGroup stage = model.stages().get(i);
            long weightBytes = stage.weightBytes();
            List<NodeCapacity> eligible = new ArrayList<>();
            long largestMemory = 0;
            for (NodeCapacity node : ordered) {
                largestMemory = Math.max(largestMemory, node.memoryBytes());
                if (node.memoryBytes() >= weightBytes) {
                    eligible.add(node);
                }
            }
            if (eligible.isEmpty()) {
                throw new IllegalArgumentException("Stage '" + stage.name() + "' needs "
                        + weightBytes + " bytes of weights, which fits on none of these nodes; "
                        + "the largest available node has " + largestMemory + " bytes");
            }
            eligibleByStage.add(eligible);
        }

        Map<Integer, List<String>> replicasByStage = new LinkedHashMap<>();
        for (int i = 0; i < stageCount; i++) {
            replicasByStage.put(i, new ArrayList<>());
        }
        Set<String> claimed = new HashSet<>();

        // Pass one: every stage must be runnable before any stage gets extra
        // throughput, so each claims its single fastest still-unclaimed
        // eligible node in stage order.
        for (int i = 0; i < stageCount; i++) {
            NodeCapacity chosen = fastestWhere(eligibleByStage.get(i), uuid -> !claimed.contains(uuid));
            if (chosen == null) {
                throw new IllegalStateException("Stage '" + model.stages().get(i).name()
                        + "' has no eligible node left to claim; there are more stages "
                        + "than usable nodes for this cluster");
            }
            replicasByStage.get(i).add(chosen.nodeUuid());
            claimed.add(chosen.nodeUuid());
        }

        // Pass two: every leftover node goes to whichever stage is currently
        // the throughput bottleneck, provided that stage can actually host it.
        Map<String, NodeCapacity> byUuid = new HashMap<>();
        for (NodeCapacity node : ordered) {
            byUuid.put(node.nodeUuid(), node);
        }
        Set<String> remaining = new LinkedHashSet<>();
        for (NodeCapacity node : ordered) {
            if (!claimed.contains(node.nodeUuid())) {
                remaining.add(node.nodeUuid());
            }
        }

        while (!remaining.isEmpty()) {
            int bottleneckStage = -1;
            double bottleneckSpeed = Double.MAX_VALUE;
            for (int i = 0; i < stageCount; i++) {
                if (fastestWhere(eligibleByStage.get(i), remaining::contains) == null) {
                    // No remaining node is eligible for this stage; skip it in
                    // favour of the next-lowest-throughput stage that has one.
                    continue;
                }
                double totalSpeed = 0;
                for (String uuid : replicasByStage.get(i)) {
                    totalSpeed += byUuid.get(uuid).usableSpeed();
                }
                if (totalSpeed < bottleneckSpeed) {
                    bottleneckSpeed = totalSpeed;
                    bottleneckStage = i;
                }
            }
            if (bottleneckStage == -1) {
                // No remaining node is eligible for any stage with room to
                // grow; the rest simply go unused, which is not an error.
                break;
            }
            NodeCapacity chosen = fastestWhere(eligibleByStage.get(bottleneckStage), remaining::contains);
            replicasByStage.get(bottleneckStage).add(chosen.nodeUuid());
            remaining.remove(chosen.nodeUuid());
        }

        return new Assignment(replicasByStage);
    }

    /**
     * The fastest node in {@code candidates} for which {@code allowed} holds,
     * ties broken by uuid ascending — the same tie rule at both passes, so the
     * plan never depends on which pass happened to run first.
     */
    private static NodeCapacity fastestWhere(List<NodeCapacity> candidates, Predicate<String> allowed) {
        NodeCapacity best = null;
        for (NodeCapacity candidate : candidates) {
            if (!allowed.test(candidate.nodeUuid())) {
                continue;
            }
            if (best == null || candidate.usableSpeed() > best.usableSpeed()
                    || (candidate.usableSpeed() == best.usableSpeed()
                        && candidate.nodeUuid().compareTo(best.nodeUuid()) < 0)) {
                best = candidate;
            }
        }
        return best;
    }
}
