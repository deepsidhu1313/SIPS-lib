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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link PipelineRunner} as an actually-concurrent pipeline: real
 * threads pulling from real queues, not a scheduling simulation. Every test
 * here stays well under a second of wall time — small item counts, blocking
 * queue waits instead of sleeps.
 */
@Timeout(10)
class PipelineRunnerTest {

    private static Mat one(float value) {
        return new Mat(1, 1, new float[]{value});
    }

    /** No weights: the op's constant is baked into the graph itself. */
    private static LayerGroup add1() {
        return new LayerGroup("add1", Expr.input("act", 1, 1).plus(1f), "act", Map.of());
    }

    private static LayerGroup scale2() {
        return new LayerGroup("scale2", Expr.input("act", 1, 1).scale(2f), "act", Map.of());
    }

    @Test
    void itemsFlowThroughEveryStageInOrder() throws InterruptedException {
        PipelineModel model = new PipelineModel(List.of(add1(), scale2()));
        List<PipelinePlan.NodeCapacity> nodes = List.of(
                new PipelinePlan.NodeCapacity("n1", 1024, 1.0),
                new PipelinePlan.NodeCapacity("n2", 1024, 1.0));
        PipelinePlan.Assignment assignment = PipelinePlan.assign(model, nodes);

        try (PipelineRunner runner = new PipelineRunner(model, assignment, PipelineRunner.local())) {
            runner.submit(one(5));
            runner.submit(one(0));
            runner.submit(one(-3));

            // (5+1)*2=12, (0+1)*2=2, (-3+1)*2=-4 -- and in submission order,
            // since each stage here has exactly one replica.
            assertEquals(12f, runner.take().at(0, 0));
            assertEquals(2f, runner.take().at(0, 0));
            assertEquals(-4f, runner.take().at(0, 0));
        }
    }

    @Test
    void aTwoReplicaStageActuallyUsesBothReplicas() throws InterruptedException {
        // n3 is far faster, so PipelinePlan gives stage 0 (add1) just n3 and
        // gives stage 1 (scale2) both slow nodes -- it is the bottleneck.
        PipelineModel model = new PipelineModel(List.of(add1(), scale2()));
        List<PipelinePlan.NodeCapacity> nodes = List.of(
                new PipelinePlan.NodeCapacity("n1", 1024, 1.0),
                new PipelinePlan.NodeCapacity("n2", 1024, 1.0),
                new PipelinePlan.NodeCapacity("n3", 1024, 10.0));
        PipelinePlan.Assignment assignment = PipelinePlan.assign(model, nodes);
        assertEquals(2, assignment.replicaCount(1), "test assumes scale2 gets two replicas");

        Set<String> add1Nodes = ConcurrentHashMap.newKeySet();
        Set<String> scale2Nodes = ConcurrentHashMap.newKeySet();
        PipelineRunner.LayerExecutor recording = (stage, nodeUuid, input) -> {
            (stage.name().equals("scale2") ? scale2Nodes : add1Nodes).add(nodeUuid);
            return stage.forward(input);
        };

        try (PipelineRunner runner = new PipelineRunner(model, assignment, recording)) {
            for (int i = 0; i < 12; i++) {
                runner.submit(one(i));
            }
            for (int i = 0; i < 12; i++) {
                runner.take();
            }
        }

        assertEquals(1, add1Nodes.size(), add1Nodes.toString());
        assertTrue(scale2Nodes.size() > 1, "expected more than one distinct nodeUuid, saw " + scale2Nodes);
    }

    @Test
    void closeDrainsCleanlyWithItemsStillToBeTaken() throws InterruptedException {
        PipelineModel model = new PipelineModel(List.of(add1(), scale2()));
        List<PipelinePlan.NodeCapacity> nodes = List.of(
                new PipelinePlan.NodeCapacity("n1", 1024, 1.0),
                new PipelinePlan.NodeCapacity("n2", 1024, 1.0));
        PipelinePlan.Assignment assignment = PipelinePlan.assign(model, nodes);

        PipelineRunner runner = new PipelineRunner(model, assignment, PipelineRunner.local());
        for (int i = 0; i < 5; i++) {
            runner.submit(one(i));
        }
        // Take some before close, leaving the rest still queued.
        assertEquals(2f, runner.take().at(0, 0));
        assertEquals(4f, runner.take().at(0, 0));

        assertDoesNotThrow(runner::close);

        // What was already submitted still comes back correctly after close.
        assertEquals(6f, runner.take().at(0, 0));
        assertEquals(8f, runner.take().at(0, 0));
        assertEquals(10f, runner.take().at(0, 0));

        assertThrows(IllegalStateException.class, runner::take);
    }

    @Test
    void everyStageNeedsAtLeastOneReplica() {
        PipelineModel model = new PipelineModel(List.of(add1(), scale2()));
        PipelinePlan.Assignment onlyFirstStageAssigned = new PipelinePlan.Assignment(
                Map.of(0, List.of("n1")));

        assertThrows(IllegalArgumentException.class,
                () -> new PipelineRunner(model, onlyFirstStageAssigned, PipelineRunner.local()));
    }

    @Test
    void aQueueCapacityBelowOneIsRefused() {
        PipelineModel model = new PipelineModel(List.of(add1(), scale2()));
        List<PipelinePlan.NodeCapacity> nodes = List.of(
                new PipelinePlan.NodeCapacity("n1", 1024, 1.0),
                new PipelinePlan.NodeCapacity("n2", 1024, 1.0));
        PipelinePlan.Assignment assignment = PipelinePlan.assign(model, nodes);

        assertThrows(IllegalArgumentException.class,
                () -> new PipelineRunner(model, assignment, PipelineRunner.local(), 0));
    }

    @Test
    void boundedQueueCapacityThrottlesAFastSubmitterBehindASlowStage() throws InterruptedException {
        // n3 is far faster, so add1 (stage 0) gets it alone and scale2
        // (stage 1) -- deliberately slowed down below -- gets both n1 and
        // n2, the same shape as aTwoReplicaStageActuallyUsesBothReplicas.
        // Two replicas draining one shared, bounded input queue is exactly
        // the case where a bounded queue's poison re-queue (putOrInterrupt)
        // has to rely on a sibling replica draining concurrently rather than
        // deadlocking -- this test actually runs that, not just reasons
        // about it.
        PipelineModel model = new PipelineModel(List.of(add1(), scale2()));
        List<PipelinePlan.NodeCapacity> nodes = List.of(
                new PipelinePlan.NodeCapacity("n1", 1024, 1.0),
                new PipelinePlan.NodeCapacity("n2", 1024, 1.0),
                new PipelinePlan.NodeCapacity("n3", 1024, 10.0));
        PipelinePlan.Assignment assignment = PipelinePlan.assign(model, nodes);
        assertEquals(2, assignment.replicaCount(1), "test assumes scale2 gets two replicas");

        PipelineRunner.LayerExecutor slowScale2 = (stage, nodeUuid, input) -> {
            if (stage.name().equals("scale2")) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            return stage.forward(input);
        };

        int itemCount = 20;
        AtomicInteger submitted = new AtomicInteger(0);
        List<Float> results = new ArrayList<>();

        try (PipelineRunner runner = new PipelineRunner(model, assignment, slowScale2, 2)) {
            Thread submitter = new Thread(() -> {
                for (int i = 0; i < itemCount; i++) {
                    runner.submit(one(i));
                    submitted.incrementAndGet();
                }
            });
            submitter.start();

            // Nobody drains the output queue yet, and every queue between
            // stages caps at 2, so no matter how long this waits, the
            // submitter can never get all itemCount items in -- an
            // unbounded queue would have accepted every one of them
            // essentially instantly instead.
            Thread.sleep(150);
            assertTrue(submitted.get() < itemCount,
                    "expected the bounded queues to have blocked the submitter by now, submitted="
                            + submitted.get());

            // Draining unblocks the backed-up pipeline; every item still
            // arrives correctly. scale2 has two replicas here, so output
            // order across them is not guaranteed -- compare as a multiset.
            for (int i = 0; i < itemCount; i++) {
                results.add(runner.take().at(0, 0));
            }
            submitter.join(5_000);
            assertFalse(submitter.isAlive(), "submitter thread should have finished once fully drained");
        }

        List<Float> expected = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            expected.add((i + 1) * 2f);
        }
        Collections.sort(results);
        Collections.sort(expected);
        assertEquals(expected, results);
        assertEquals(itemCount, submitted.get());
    }
}
