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

import in.co.s13.sips.lib.array.Expr;
import in.co.s13.sips.lib.array.Mat;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PipelinePlan#assign} balances a discrete, memory-constrained
 * resource (replicas) rather than the continuous one {@code ShardPlan}
 * balances (samples), so these tests pin exact replica counts the way
 * {@code ShardPlanTest} pins exact sample counts, not just "some balancing
 * happened".
 */
class PipelinePlanTest {

    /** A minimal, valid stage whose weight is exactly {@code floats} floats — {@code 4 * floats} bytes. */
    private static LayerGroup stage(String name, int floats) {
        Expr graph = Expr.input("act", 1, 1).matmul(Expr.input("w", 1, floats));
        Mat weight = new Mat(1, floats, new float[floats]);
        return new LayerGroup(name, graph, "act", Map.of("w", weight));
    }

    private static PipelinePlan.NodeCapacity node(String uuid, long memoryBytes, double speed) {
        return new PipelinePlan.NodeCapacity(uuid, memoryBytes, speed);
    }

    @Test
    void equalNodesGetEqualReplicas() {
        PipelineModel model = new PipelineModel(List.of(stage("s0", 2), stage("s1", 2)));
        List<PipelinePlan.NodeCapacity> nodes = List.of(
                node("a", 1000, 1.0), node("b", 1000, 1.0),
                node("c", 1000, 1.0), node("d", 1000, 1.0));

        PipelinePlan.Assignment assignment = PipelinePlan.assign(model, nodes);

        assertEquals(2, assignment.replicaCount(0));
        assertEquals(2, assignment.replicaCount(1));
    }

    @Test
    void theCurrentBottleneckStageGetsTheExtraReplica() {
        // One very fast node and two slow ones. Pass one gives the fast node
        // to stage 0 and one slow node to stage 1; the leftover slow node
        // then goes to stage 1, since stage 1's one slow replica is the
        // bottleneck next to stage 0's fast one.
        PipelineModel model = new PipelineModel(List.of(stage("s0", 2), stage("s1", 2)));
        List<PipelinePlan.NodeCapacity> nodes = List.of(
                node("n1", 1000, 1.0), node("n2", 1000, 1.0), node("n3", 1000, 10.0));

        PipelinePlan.Assignment assignment = PipelinePlan.assign(model, nodes);

        assertEquals(1, assignment.replicaCount(0), assignment.toString());
        assertEquals(List.of("n3"), assignment.replicasFor(0));
        assertEquals(2, assignment.replicaCount(1), assignment.toString());
        assertEquals(List.of("n1", "n2"), assignment.replicasFor(1));
    }

    @Test
    void aStageThatFitsOnNoNodeIsRefused() {
        // 400 bytes of weights; the only node has 100.
        PipelineModel model = new PipelineModel(List.of(stage("too-big", 100)));
        List<PipelinePlan.NodeCapacity> nodes = List.of(node("only", 100, 1.0));

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> PipelinePlan.assign(model, nodes));
        assertTrue(thrown.getMessage().contains("too-big"), thrown.getMessage());
    }

    @Test
    void moreStagesThanUsableNodesIsRefused() {
        PipelineModel model = new PipelineModel(List.of(stage("s0", 1), stage("s1", 1), stage("s2", 1)));
        List<PipelinePlan.NodeCapacity> nodes = List.of(node("a", 1000, 1.0), node("b", 1000, 1.0));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> PipelinePlan.assign(model, nodes));
        assertTrue(thrown.getMessage().contains("s2"), thrown.getMessage());
    }

    @Test
    void thePlanDoesNotDependOnInputListOrder() {
        PipelineModel model = new PipelineModel(List.of(stage("s0", 2), stage("s1", 2)));
        List<PipelinePlan.NodeCapacity> nodes = List.of(
                node("n1", 1000, 1.0), node("n2", 1000, 1.0), node("n3", 1000, 10.0));
        List<PipelinePlan.NodeCapacity> shuffled = List.of(nodes.get(2), nodes.get(0), nodes.get(1));

        PipelinePlan.Assignment fromOriginalOrder = PipelinePlan.assign(model, nodes);
        PipelinePlan.Assignment fromShuffledOrder = PipelinePlan.assign(model, shuffled);

        assertEquals(fromOriginalOrder, fromShuffledOrder);
        assertEquals(fromOriginalOrder.replicasByStage(), fromShuffledOrder.replicasByStage());
    }

    @Test
    void aNodeTooSmallForOneStageIsEligibleForAnother() {
        // "small" cannot host s0's 1000-byte weights but can host s1's 8.
        // "big" can host both. If eligibility were computed wrong, the
        // faster "small" node would have been picked for s0 in pass one.
        PipelineModel model = new PipelineModel(List.of(stage("s0", 250), stage("s1", 2)));
        List<PipelinePlan.NodeCapacity> nodes = List.of(
                node("small", 50, 10.0), node("big", 2000, 1.0));

        PipelinePlan.Assignment assignment = PipelinePlan.assign(model, nodes);

        assertEquals(List.of("big"), assignment.replicasFor(0));
        assertFalse(assignment.replicasFor(0).contains("small"));
        assertTrue(assignment.replicasFor(1).contains("small"));
    }

    @Test
    void aNullOrEmptyNodeListIsRefused() {
        PipelineModel model = new PipelineModel(List.of(stage("s0", 1)));
        assertThrows(IllegalArgumentException.class, () -> PipelinePlan.assign(model, List.of()));
        assertThrows(IllegalArgumentException.class, () -> PipelinePlan.assign(model, null));
        assertThrows(IllegalArgumentException.class, () -> PipelinePlan.assign(null, List.of(node("a", 1, 1.0))));
    }

    @Test
    void anAssignmentIsUnmodifiable() {
        PipelinePlan.Assignment assignment = new PipelinePlan.Assignment(
                Collections.singletonMap(0, List.of("a")));

        assertThrows(UnsupportedOperationException.class, () -> assignment.replicasFor(0).add("b"));
        assertThrows(UnsupportedOperationException.class,
                () -> assignment.replicasByStage().put(1, List.of("c")));
    }
}
