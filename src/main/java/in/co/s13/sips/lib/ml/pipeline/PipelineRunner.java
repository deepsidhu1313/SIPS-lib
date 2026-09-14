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
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Streams items through a {@link PipelineModel} on real, concurrently running
 * stages, so a throughput number measured against it means something.
 *
 * <p>A {@link PipelinePlan.Assignment} is a static decision; this class is
 * what makes it actually move items. Each stage gets its own bounded-nowhere
 * queue and its own fixed thread pool sized to its replica count, wired queue
 * to queue so stage {@code i}'s output becomes stage {@code i+1}'s input —
 * the pipeline a plan describes, running. It is deliberately not a simulation
 * with sleeps standing in for work: the whole reason to model pipeline
 * parallelism this way is to get a scheduling and throughput story that is
 * still true when {@link LayerExecutor} is swapped from {@link #local} for a
 * network call to a real node, and a simulated concurrency model would prove
 * nothing about that swap.
 *
 * <p>{@link LayerExecutor} is the seam that swap happens at. {@link #local}
 * runs a stage's own {@link LayerGroup#forward} in this process regardless of
 * which node uuid it was told to use — today there is no cluster to dial —
 * but the interface exists so a future executor can look at {@code nodeUuid}
 * and make a remote call instead, without this class's queueing or shutdown
 * logic changing at all.
 *
 * <p>Shutdown has to drain rather than abandon in-flight work, so the poison
 * sentinel that {@link #close} injects is not simply forwarded stage to
 * stage: every replica of a stage must individually see it before the stage
 * as a whole is done, because any replica that has not yet seen it might
 * still be holding a real item. Each replica that dequeues the sentinel puts
 * it straight back on its own input queue for a sibling to find and only the
 * replica that observes the count reach zero forwards it downstream — so
 * exactly one sentinel is ever in flight per stage, and it cannot reach the
 * next stage until this one has genuinely finished.
 */
public final class PipelineRunner implements AutoCloseable {

    /** How one stage's replica actually computes its output for one item. */
    @FunctionalInterface
    public interface LayerExecutor {

        Mat execute(LayerGroup stage, String nodeUuid, Mat input);
    }

    /**
     * The in-process default: runs the stage's own {@link LayerGroup#forward}
     * no matter which node uuid it is handed. A real-cluster executor would
     * instead dial {@code nodeUuid} and run there — this is exactly why
     * {@link LayerExecutor} is an interface a caller can swap out later,
     * rather than behaviour hardcoded into this class.
     */
    public static LayerExecutor local() {
        return (stage, nodeUuid, input) -> stage.forward(input);
    }

    /** Shutdown signal threaded through the queues as data, since a {@link Mat} travels as plain Object. */
    private static final Object POISON = new Object();

    private final List<BlockingQueue<Object>> queues;
    private final List<ExecutorService> pools;

    public PipelineRunner(PipelineModel model, PipelinePlan.Assignment assignment, LayerExecutor executor) {
        if (model == null) {
            throw new IllegalArgumentException("A pipeline runner needs a model");
        }
        if (assignment == null) {
            throw new IllegalArgumentException("A pipeline runner needs an assignment");
        }
        if (executor == null) {
            throw new IllegalArgumentException("A pipeline runner needs an executor");
        }
        int stageCount = model.stageCount();
        for (int i = 0; i < stageCount; i++) {
            if (assignment.replicaCount(i) <= 0) {
                throw new IllegalArgumentException("Stage '" + model.stages().get(i).name()
                        + "' has no assigned replicas; every stage needs at least one to run");
            }
        }

        List<BlockingQueue<Object>> builtQueues = new ArrayList<>();
        for (int i = 0; i <= stageCount; i++) {
            builtQueues.add(new LinkedBlockingQueue<>());
        }

        List<ExecutorService> builtPools = new ArrayList<>();
        for (int i = 0; i < stageCount; i++) {
            LayerGroup stage = model.stages().get(i);
            List<String> replicas = assignment.replicasFor(i);
            int replicaCount = replicas.size();
            BlockingQueue<Object> inQueue = builtQueues.get(i);
            BlockingQueue<Object> outQueue = builtQueues.get(i + 1);
            ExecutorService pool = Executors.newFixedThreadPool(replicaCount);
            builtPools.add(pool);

            // Counts down as each of this stage's replicas sees the poison;
            // only the replica that drives it to zero forwards downstream, so
            // the sentinel reaches stage i+1 exactly once, only after every
            // replica of stage i has genuinely stopped.
            AtomicInteger stillToSeePoison = new AtomicInteger(replicaCount);
            AtomicInteger nextReplica = new AtomicInteger(0);
            for (int r = 0; r < replicaCount; r++) {
                pool.submit(() -> {
                    while (true) {
                        Object item;
                        try {
                            item = inQueue.take();
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        if (item == POISON) {
                            if (stillToSeePoison.decrementAndGet() > 0) {
                                putOrInterrupt(inQueue, POISON);
                            } else {
                                putOrInterrupt(outQueue, POISON);
                            }
                            return;
                        }
                        String nodeUuid = replicas.get(nextReplica.getAndIncrement() % replicaCount);
                        Mat output = executor.execute(stage, nodeUuid, (Mat) item);
                        putOrInterrupt(outQueue, output);
                    }
                });
            }
        }

        this.queues = builtQueues;
        this.pools = builtPools;
    }

    /** Feeds one item into the front of the pipeline. */
    public void submit(Mat item) {
        try {
            queues.get(0).put(item);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while submitting to the pipeline", interrupted);
        }
    }

    /** Blocks for the next fully-processed item off the back of the pipeline. */
    public Mat take() throws InterruptedException {
        Object item = queues.get(queues.size() - 1).take();
        if (item == POISON) {
            throw new IllegalStateException("Pipeline is closed and drained");
        }
        return (Mat) item;
    }

    /**
     * Signals shutdown and waits for every stage to drain and stop. Items
     * already {@link #submit}ted are still processed and reach {@link #take}
     * before the pipeline reports itself drained.
     */
    @Override
    public void close() {
        putOrInterrupt(queues.get(0), POISON);
        for (ExecutorService pool : pools) {
            pool.shutdown();
        }
        for (ExecutorService pool : pools) {
            try {
                if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void putOrInterrupt(BlockingQueue<Object> queue, Object item) {
        try {
            queue.put(item);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
