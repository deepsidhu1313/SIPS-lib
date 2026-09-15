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

import in.co.s13.sips.lib.array.Mat;
import java.util.ArrayList;
import java.util.Arrays;
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
     *
     * <p>Equivalent to calling the three-argument overload with an empty cost
     * map, so every stage defaults to a uniform cost of {@code 1.0} — exactly
     * this method's own implicit behaviour, which is why adding a cost model
     * did not require a second copy of the algorithm.
     */
    public static Assignment assign(PipelineModel model, List<NodeCapacity> nodes) {
        return assign(model, nodes, Map.of());
    }

    /**
     * Same as {@link #assign(PipelineModel, List)}, except pass two picks the
     * bottleneck stage by its actual items/second capacity —
     * {@code totalAssignedSpeed / cost} — rather than by raw assigned node
     * speed alone. A stage's cost is what it takes to run one item through
     * it; two stages given identical nodes do not necessarily process items
     * at the same rate, and node speed alone cannot tell them apart.
     *
     * @param stageCostMillis per-item cost in milliseconds, keyed by
     *        {@link LayerGroup#name()}; a stage absent from this map (or a
     *        {@code null} map entirely) defaults to {@code 1.0} — uniform
     *        cost, i.e. today's behaviour when nothing has been measured yet.
     *        Cost only matters relative to other stages' costs, so the unit
     *        does not have to be milliseconds; {@link #measureStageCost} is
     *        the intended source of a real value.
     */
    public static Assignment assign(PipelineModel model, List<NodeCapacity> nodes, Map<String, Double> stageCostMillis) {
        if (model == null) {
            throw new IllegalArgumentException("Pipeline assignment needs a model");
        }
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("Pipeline assignment needs at least one node");
        }
        Map<String, Double> costs = stageCostMillis == null ? Map.of() : stageCostMillis;

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
            double bottleneckCapacity = Double.MAX_VALUE;
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
                // Items/second, not raw node speed: a stage's cost is how
                // much of that speed one item actually consumes, so two
                // stages with identical assigned speed are not necessarily
                // equally backed up.
                double capacity = totalSpeed / costOf(model.stages().get(i), costs);
                if (capacity < bottleneckCapacity) {
                    bottleneckCapacity = capacity;
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

    /** A stage's declared or measured cost, or the uniform default of {@code 1.0} if it has neither. */
    private static double costOf(LayerGroup stage, Map<String, Double> stageCostMillis) {
        return stageCostMillis.getOrDefault(stage.name(), 1.0);
    }

    /**
     * Measures one stage's own per-item cost, the missing input the class
     * documentation calls out: {@code assign} can only weigh a stage's true
     * bottleneck-ness against others if something actually times the stage,
     * the same way {@code WorkerBench} times a node rather than asking it to
     * guess its own speed.
     *
     * <p>{@code warmupRuns} pay for JIT warm-up and are discarded entirely —
     * not even timed — before {@code measuredRuns} are individually timed
     * with {@link System#nanoTime()}. The return value is the <em>median</em>
     * of those timings, not the mean: a single slow outlier (a GC pause, a
     * scheduler hiccup) should not move the estimate the way it would move an
     * average, and a per-stage cost feeding a planner is exactly the kind of
     * number one bad sample should not be allowed to distort. For an even
     * {@code measuredRuns}, the median is the average of the two middle
     * sorted samples.
     *
     * @param stage the stage to measure; not mutated by measurement
     * @param sampleInput representative input, shaped the way real traffic
     *        would shape it — the cost of a stage depends on what it is fed
     * @param warmupRuns discarded runs before timing begins; must be {@code >= 0}
     * @param measuredRuns timed runs the median is computed from; must be {@code >= 1}
     * @return the median wall time of the measured runs, in milliseconds
     */
    public static double measureStageCost(LayerGroup stage, Mat sampleInput, int warmupRuns, int measuredRuns) {
        if (stage == null) {
            throw new IllegalArgumentException("Measuring a stage's cost needs the stage");
        }
        if (sampleInput == null) {
            throw new IllegalArgumentException("Measuring a stage's cost needs a sample input");
        }
        if (warmupRuns < 0) {
            throw new IllegalArgumentException("warmupRuns must not be negative, not " + warmupRuns);
        }
        if (measuredRuns < 1) {
            throw new IllegalArgumentException("measuredRuns must be at least 1, not " + measuredRuns);
        }

        for (int i = 0; i < warmupRuns; i++) {
            stage.forward(sampleInput);
        }

        double[] millis = new double[measuredRuns];
        for (int i = 0; i < measuredRuns; i++) {
            long start = System.nanoTime();
            stage.forward(sampleInput);
            millis[i] = (System.nanoTime() - start) / 1_000_000.0;
        }

        Arrays.sort(millis);
        int mid = measuredRuns / 2;
        return measuredRuns % 2 == 0 ? (millis[mid - 1] + millis[mid]) / 2.0 : millis[mid];
    }
}
