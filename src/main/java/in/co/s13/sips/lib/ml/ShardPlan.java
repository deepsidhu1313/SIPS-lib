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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Dividing a dataset so every worker reaches the round barrier together.
 *
 * <p>A round ends when its slowest worker ends. Equal shards on unequal
 * machines therefore waste the difference on every single round — a node twice
 * as fast spends half of each round idle, and there are as many rounds as the
 * run has.
 *
 * <p>Federated averaging weights each model by the number of samples it saw, so
 * unequal shards cost nothing in accuracy. That is what makes this free: the
 * arithmetic already accounts for it, and {@link WeightAverage} needs no
 * knowledge of why the shards differ.
 *
 * <p>Ordinary loop scheduling cannot do this job. {@code GSS} and its relatives
 * balance by handing out more batches to whoever comes back first, which needs
 * many batches per node; here each worker gets exactly one shard for the whole
 * round, so the division has to be right the first time.
 */
public final class ShardPlan {

    /** One worker's slice of the dataset. */
    public record Shard(int shard, String nodeUuid, long firstIndex, long lastIndexExclusive) {

        /** How many samples this worker trains on — its weight when averaging. */
        public long sampleCount() {
            return lastIndexExclusive - firstIndex;
        }
    }

    /** What an unbenchmarked node is assumed to be worth. */
    static final double UNMEASURED_SPEED = 1.0;

    private ShardPlan() {
    }

    /**
     * Divides {@code samples} across nodes in proportion to their speed.
     *
     * @param speedByNode relative speed per node uuid; a node measured at 2.0
     *        is worth two of a node at 1.0. Zero or negative means unmeasured,
     *        which is treated as average rather than idle — treating unknown as
     *        slow would starve every machine not yet benchmarked, which is
     *        every new machine.
     * @return one shard per node, in a stable order, covering every sample
     *         exactly once
     */
    public static List<Shard> across(long samples, Map<String, Double> speedByNode) {
        if (speedByNode == null || speedByNode.isEmpty()) {
            throw new IllegalArgumentException("Sharding needs at least one node");
        }
        if (samples < 1) {
            throw new IllegalArgumentException("Nothing to shard: " + samples + " samples");
        }
        // Sorted, so the same cluster produces the same division every run --
        // a training run nobody can reproduce is not evidence.
        Map<String, Double> ordered = new TreeMap<>(speedByNode);
        if (samples < ordered.size()) {
            throw new IllegalArgumentException(ordered.size() + " workers cannot each take a "
                    + "sample from a set of " + samples + "; ask for less parallelism");
        }

        double totalSpeed = 0;
        for (double speed : ordered.values()) {
            totalSpeed += usable(speed);
        }

        List<Shard> shards = new ArrayList<>();
        long cursor = 0;
        int index = 0;
        int last = ordered.size() - 1;
        for (Map.Entry<String, Double> entry : ordered.entrySet()) {
            long size;
            if (index == last) {
                // The last shard takes the remainder, so rounding can never
                // lose a sample or hand one to two workers. Losing one quietly
                // shrinks the training set; duplicating one overweights it.
                size = samples - cursor;
            } else {
                size = Math.round(samples * (usable(entry.getValue()) / totalSpeed));
                // Every worker needs at least one sample: a model trained on
                // nothing has no vote, and WeightAverage refuses it outright.
                size = Math.max(1, Math.min(size, samples - cursor - (last - index)));
            }
            shards.add(new Shard(index, entry.getKey(), cursor, cursor + size));
            cursor += size;
            index++;
        }
        return shards;
    }

    private static double usable(double speed) {
        return speed > 0 ? speed : UNMEASURED_SPEED;
    }

    /** How unevenly the work landed, as slowest shard over fastest. Useful in a report. */
    public static double imbalance(List<Shard> shards, Map<String, Double> speedByNode) {
        double slowest = 0;
        double fastest = Double.MAX_VALUE;
        for (Shard shard : shards) {
            double time = shard.sampleCount()
                    / usable(speedByNode.getOrDefault(shard.nodeUuid(), UNMEASURED_SPEED));
            slowest = Math.max(slowest, time);
            fastest = Math.min(fastest, time);
        }
        return fastest <= 0 ? 1 : slowest / fastest;
    }
}
