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
package in.co.s13.sips.scheduler;

import java.util.List;
import java.util.Optional;

/**
 * Which node should run this task.
 *
 * <p>The second scheduling question SIPS asks, and deliberately not the same
 * interface as {@link LoopPolicy}. A loop policy answers <em>how big is the next
 * batch</em> of one iteration space; this answers <em>which of several ready
 * tasks goes where</em>, and the good answers turn on estimated durations, where
 * the data already is, and how much work still sits on the critical path.
 *
 * <p>Same bargain as {@link LoopPolicy}: one method between having an idea and
 * finding out whether it works. Everything else has a default. That matters more
 * here than for loops — DAG scheduling is an active field where loop scheduling
 * is largely settled, and the barrier to trying a new heuristic is the reason
 * most of them are only ever evaluated in simulators.
 */
public interface PlacementPolicy {

    /** How this policy is named in a manifest and in results. */
    String name();

    /**
     * Chooses a node for a task.
     *
     * @return the chosen node, or empty to leave the task queued — which is a
     *         real answer, not a failure: a policy may prefer to wait for a
     *         better node rather than take the first free one
     */
    Optional<String> place(ReadyTask task, ClusterState cluster);

    /**
     * The order to consider ready tasks in.
     *
     * <p>Arrival order by default. Overriding this is how a policy expresses
     * priority — HEFT's whole contribution is placing the most critical task
     * first, and a policy that only chose nodes could not express it.
     */
    default List<ReadyTask> order(List<ReadyTask> ready) {
        return ready;
    }

    default String description() {
        return name() + " placement policy";
    }
}
