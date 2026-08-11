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
package in.co.s13.sips.lib.array;

import in.co.s13.sips.lib.ml.ShardPlan;
import java.util.Map;
import java.util.Random;

/**
 * The fixed task a joining worker runs to prove what it is worth.
 *
 * <p>Capability flags predict nothing — the negative-results register has a
 * machine whose GPU is 4× slower than its CPU for one workload and 19.8×
 * faster for another, and a phone's real throughput moves by multiples with
 * thermals and battery governors. So a worker <em>measures</em>: run
 * {@link #standard()} several times, report the timings in its hello
 * announcement, and the roster turns them into the speed the shard planner
 * weights by.
 *
 * <p>The task is the same bytes for everyone — scores are only comparable if
 * everyone measured the same work — and small enough to be polite on a
 * battery. The first run is discarded: it pays JIT warm-up and cache misses,
 * and charging a device for its cold start would understate every worker that
 * just joined, which is every worker.
 */
public final class WorkerBench {

    /** Runs after the discarded warm-up; spread needs at least two. */
    public static final int MINIMUM_TIMED_RUNS = 2;

    private WorkerBench() {
    }

    /**
     * The standard measurement task: a GEMM-plus-sweep at a size that costs
     * tens of milliseconds on a laptop and stays polite on a phone.
     */
    public static ExprTask standard() {
        int n = 128;
        // Seeded, so every worker on every platform builds identical bytes.
        Random random = new Random(1313);
        float[] a = new float[n * n];
        float[] b = new float[n * n];
        for (int i = 0; i < n * n; i++) {
            a[i] = (float) random.nextGaussian();
            b[i] = (float) random.nextGaussian();
        }
        Expr expr = Expr.input("a", n, n)
                .matmul(Expr.input("b", n, n))
                .relu()
                .colSum();
        return new ExprTask(expr, Map.of(
                "a", new Mat(n, n, a), "b", new Mat(n, n, b)));
    }

    /**
     * Turns repeated timings into the speed the planner weights by.
     *
     * @param timingsMillis wall time of each run, warm-up first; the warm-up
     *        is discarded and at least {@value #MINIMUM_TIMED_RUNS} timed runs
     *        must remain — one number has no spread, and spread is half of
     *        what the measurement is for
     * @return speed as work-per-millisecond with its dispersion, ready for
     *         {@code ShardPlan.acrossMeasured}
     */
    public static ShardPlan.Measured measured(long[] timingsMillis) {
        if (timingsMillis == null || timingsMillis.length < MINIMUM_TIMED_RUNS + 1) {
            throw new IllegalArgumentException("Need a warm-up plus at least "
                    + MINIMUM_TIMED_RUNS + " timed runs, not "
                    + (timingsMillis == null ? 0 : timingsMillis.length));
        }
        int timed = timingsMillis.length - 1;
        double[] speeds = new double[timed];
        for (int i = 0; i < timed; i++) {
            long millis = timingsMillis[i + 1];
            if (millis <= 0) {
                throw new IllegalArgumentException("A run of " + millis + " ms is not a "
                        + "measurement; the clock did not move or the number is hostile");
            }
            // Speed, not time, so faster is bigger and the planner's
            // proportional split reads naturally.
            speeds[i] = 1000.0 / millis;
        }
        double mean = 0;
        for (double speed : speeds) {
            mean += speed;
        }
        mean /= timed;
        double variance = 0;
        for (double speed : speeds) {
            variance += (speed - mean) * (speed - mean);
        }
        return new ShardPlan.Measured(mean, Math.sqrt(variance / timed));
    }
}
