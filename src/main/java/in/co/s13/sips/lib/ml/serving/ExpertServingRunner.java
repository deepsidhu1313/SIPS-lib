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
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Drives concurrent request arrival and dispatch against {@link ExpertRouter}
 * and {@link ExpertRegistry}, so the latency and affinity numbers this
 * measures are real — real threads, real elapsed time — rather than a
 * simulation that assumes the scheduling logic is sound instead of proving it.
 *
 * <p>What actually happens inside a request is entirely the caller's affair:
 * this class never computes anything an expert would, the same way {@code
 * ShardPlan} never runs the training step it divides work for. A {@link
 * RequestExecutor} stands in for that work, so this class stays a pure
 * exercise of the routing and load-tracking machinery.
 */
public final class ExpertServingRunner implements AutoCloseable {

    /** Stand-in for the actual expert computation a request would trigger. */
    @FunctionalInterface
    public interface RequestExecutor {
        void process(String expertId, String nodeUuid, Object payload);
    }

    /** A point-in-time read of everything submitted so far. */
    public record Metrics(long count, double p50Millis, double p99Millis, double sessionAffinityHitRate) {
    }

    private final ExpertRouter router;
    private final ExpertRegistry experts;
    private final RequestExecutor executor;
    private final ExecutorService pool;
    private final ConcurrentLinkedQueue<Long> latencyNanos = new ConcurrentLinkedQueue<>();
    private final AtomicLong sessionAffinityHits = new AtomicLong();

    public ExpertServingRunner(ExpertRouter router, ExpertRegistry experts, RequestExecutor executor,
            int concurrency) {
        if (router == null) {
            throw new IllegalArgumentException("router must not be null");
        }
        if (experts == null) {
            throw new IllegalArgumentException("experts must not be null");
        }
        if (executor == null) {
            throw new IllegalArgumentException("executor must not be null");
        }
        if (concurrency < 1) {
            throw new IllegalArgumentException("concurrency must be at least 1: " + concurrency);
        }
        this.router = router;
        this.experts = experts;
        this.executor = executor;
        this.pool = Executors.newFixedThreadPool(concurrency);
    }

    /** Fires one request into the pool and returns immediately; does not wait for it to finish. */
    public void submit(String sessionId, String expertId, Object payload) {
        pool.execute(() -> {
            long start = System.nanoTime();
            ExpertRouter.Routed routed = router.route(sessionId, expertId);
            experts.beginRequest(expertId, routed.nodeUuid());
            try {
                executor.process(expertId, routed.nodeUuid(), payload);
            } finally {
                experts.endRequest(expertId, routed.nodeUuid());
            }
            latencyNanos.add(System.nanoTime() - start);
            if (routed.sessionAffinityHit()) {
                sessionAffinityHits.incrementAndGet();
            }
        });
    }

    /**
     * A snapshot of everything completed so far — safe to call while requests are still in
     * flight. Percentiles use the nearest-rank method: sort the completed latencies and take
     * index {@code ceil(p * n) - 1}, clamped into {@code [0, n - 1]}.
     */
    public Metrics metricsSnapshot() {
        // Read hits before copying latencies: a request always records its latency before
        // it is counted as an affinity hit, so this order guarantees hits never outruns the
        // completed-request count below, even mid-flight.
        long hits = sessionAffinityHits.get();
        List<Long> nanos = new ArrayList<>(latencyNanos);
        int count = nanos.size();
        if (count == 0) {
            return new Metrics(0, 0.0, 0.0, 0.0);
        }
        Collections.sort(nanos);
        double p50 = percentileMillis(nanos, 0.50);
        double p99 = percentileMillis(nanos, 0.99);
        double hitRate = (double) hits / count;
        return new Metrics(count, p50, p99, hitRate);
    }

    private static double percentileMillis(List<Long> sortedNanos, double percentile) {
        int n = sortedNanos.size();
        int index = (int) Math.ceil(percentile * n) - 1;
        index = Math.max(0, Math.min(index, n - 1));
        return sortedNanos.get(index) / 1_000_000.0;
    }

    /** Shuts the pool down and waits for every submitted request to finish before returning. */
    @Override
    public void close() {
        pool.shutdown();
        try {
            pool.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
