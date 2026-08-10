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
package in.co.s13.sips.lib.ml;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The aggregation step of federated averaging: one model from many.
 *
 * <p>This is the piece the framework never had — "there is no result-merge
 * primitive" has been on the known-constraints list since the architecture was
 * first written down. For training it is not a convenience but the algorithm:
 * each worker trains on its shard, and the next round's model is the
 * sample-weighted mean of what they produced (McMahan et&nbsp;al., 2017).
 *
 * <p>Weighted by <em>sample count</em>, not equally: shards are rarely equal —
 * a benchmark-weighted split makes them deliberately unequal — and an equal
 * mean would let a ten-example shard pull as hard as a ten-thousand-example
 * one.
 *
 * <h2>Determinism</h2>
 *
 * <p>Results arrive from the cluster in whatever order nodes finish, and
 * floating-point addition is not associative. So contributions are ordered by
 * chunk number before summing, and accumulation is in double precision: the
 * averaged model is byte-identical run to run, whichever node finished first.
 * A training run nobody can reproduce is not evidence, the same rule the
 * evaluators already follow.
 */
public final class WeightAverage {

    /**
     * One worker's result: which shard it was, how many samples it trained on,
     * and the weights it produced.
     */
    public record Contribution(int chunkNumber, long sampleCount, float[] weights) {

        public Contribution {
            if (sampleCount <= 0) {
                throw new IllegalArgumentException("Chunk " + chunkNumber + " trained on "
                        + sampleCount + " samples; a model trained on nothing has no vote");
            }
            if (weights == null) {
                throw new IllegalArgumentException("Chunk " + chunkNumber + " carries no weights");
            }
        }
    }

    private WeightAverage() {
    }

    /**
     * The sample-weighted mean of the contributions.
     *
     * @throws IllegalArgumentException on no contributions, mismatched model
     *         sizes, or two contributions for the same chunk — duplicates can
     *         happen when backup tasks run, and averaging both would double
     *         that shard's vote, so the caller must deduplicate first
     */
    public static float[] of(List<Contribution> contributions) {
        if (contributions == null || contributions.isEmpty()) {
            throw new IllegalArgumentException("Nothing to average: no contributions");
        }

        List<Contribution> ordered = new ArrayList<>(contributions);
        ordered.sort(Comparator.comparingInt(Contribution::chunkNumber));

        Set<Integer> seen = new HashSet<>();
        int length = ordered.get(0).weights().length;
        for (Contribution contribution : ordered) {
            if (!seen.add(contribution.chunkNumber())) {
                throw new IllegalArgumentException("Two results for chunk "
                        + contribution.chunkNumber() + "; a duplicate (a backup task, or a "
                        + "retry) would double that shard's vote. Deduplicate before averaging.");
            }
            if (contribution.weights().length != length) {
                throw new IllegalArgumentException("Chunk " + contribution.chunkNumber()
                        + " has " + contribution.weights().length + " weights where chunk "
                        + ordered.get(0).chunkNumber() + " has " + length
                        + "; these are not the same model");
            }
        }

        double totalSamples = 0;
        for (Contribution contribution : ordered) {
            totalSamples += contribution.sampleCount();
        }

        double[] sum = new double[length];
        for (Contribution contribution : ordered) {
            double weight = contribution.sampleCount() / totalSamples;
            float[] weights = contribution.weights();
            for (int i = 0; i < length; i++) {
                sum[i] += weight * weights[i];
            }
        }

        float[] averaged = new float[length];
        for (int i = 0; i < length; i++) {
            averaged[i] = (float) sum[i];
        }
        return averaged;
    }
}
