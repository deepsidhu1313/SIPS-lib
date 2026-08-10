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
package in.co.s13.sips.lib.job;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * How much work sits between a stage and the end of the job.
 *
 * <p>The <em>upward rank</em> of Topcuoglu, Hariri and Wu's HEFT: a stage's own
 * cost plus the longest chain of stages that still has to follow it. It is the
 * number that turns "these three stages are all ready" into an order, and the
 * reason is simple — running a cheap stage first while an expensive chain waits
 * makes the whole job longer, and nothing local to a stage reveals that.
 *
 * <p>Computed over the graph, so it costs one pass and does not need a cluster.
 */
public final class StageRanks {

    private StageRanks() {
    }

    /**
     * The upward rank of every stage.
     *
     * @param costOf what a stage costs; any consistent unit will do, since only
     *        the ordering the ranks produce is used
     * @throws IllegalStateException if the job is empty or has a cycle
     */
    public static Map<Stage, Double> upward(Job job, ToDoubleFunction<Stage> costOf) {
        if (costOf == null) {
            throw new IllegalArgumentException("costOf must not be null");
        }
        List<Stage> ordered = job.inDependencyOrder();
        Map<Stage, List<Stage>> successors = successorsOf(job);

        Map<Stage, Double> ranks = new LinkedHashMap<>();
        // Reverse dependency order: every successor is ranked before the stage
        // that needs it, so one pass suffices.
        for (int i = ordered.size() - 1; i >= 0; i--) {
            Stage stage = ordered.get(i);
            double longestAhead = 0;
            for (Stage successor : successors.get(stage)) {
                longestAhead = Math.max(longestAhead, ranks.get(successor));
            }
            ranks.put(stage, costOf.applyAsDouble(stage) + longestAhead);
        }
        return ranks;
    }

    /**
     * The length of the critical path: the shortest the job could possibly take
     * given unlimited nodes.
     *
     * <p>A floor worth knowing before blaming a scheduler for a slow pipeline.
     */
    public static double criticalPathLength(Job job, ToDoubleFunction<Stage> costOf) {
        return upward(job, costOf).values().stream()
                .mapToDouble(Double::doubleValue).max().orElse(0);
    }

    private static Map<Stage, List<Stage>> successorsOf(Job job) {
        Map<Stage, List<Stage>> successors = new LinkedHashMap<>();
        for (Stage stage : job.stages()) {
            successors.computeIfAbsent(stage, key -> new java.util.ArrayList<>());
        }
        for (Stage stage : job.stages()) {
            for (Stage dependency : stage.dependencies()) {
                successors.get(dependency).add(stage);
            }
        }
        return successors;
    }
}
