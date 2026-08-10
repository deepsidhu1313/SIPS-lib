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

import in.co.s13.sips.lib.common.SipsPaths;
import in.co.s13.sips.lib.job.Job;
import in.co.s13.sips.lib.job.Stage;

/**
 * A federated-averaging run as a {@link Job}.
 *
 * <p>Training in rounds is exactly the shape the task graph was built for —
 * parallel, then serial, then parallel again — so this adds no execution
 * machinery at all. It only spells the graph out, because writing 2R stages and
 * their edges by hand is where an off-by-one hides:
 *
 * <pre>
 * train-1 (parallel, one chunk per shard)
 *    │ reads
 * average-1 (single)
 *    │ reads
 * train-2 (parallel)
 *    │ reads
 * average-2 (single)
 *    ⋮
 * </pre>
 *
 * <p>Each train stage reads the previous round's averaged model, so
 * {@code reads()} carries the data-locality hint the placement policies
 * already understand — and each barrier between rounds is a stage barrier the
 * {@code JobSequencer} already enforces.
 *
 * <p>Rounds are fixed up front. Stopping early on convergence is the
 * {@code breakAfter} shape and belongs to the iterative runner planned in
 * <a href="../../../../../../../docs/ML_TRAINING.md">ML_TRAINING.md</a>, phase 2.
 */
public final class FedAvgPlan {

    /** Stage names, so a runner can recognise its stages without parsing. */
    public static final String TRAIN_PREFIX = "train-";
    public static final String AVERAGE_PREFIX = "average-";

    private FedAvgPlan() {
    }

    /**
     * Builds the unrolled graph.
     *
     * @param name the job's name
     * @param shards how many workers train in parallel each round — one chunk
     *        per shard, so the scheduler hands each worker its shard index
     * @param rounds how many times the workers synchronise
     */
    public static Job job(String name, int shards, int rounds) {
        if (shards < 1) {
            throw new IllegalArgumentException("Training needs at least one shard: " + shards);
        }
        if (rounds < 1) {
            throw new IllegalArgumentException("Training needs at least one round: " + rounds);
        }

        Job job = new Job(name);
        Stage previousAverage = null;
        for (int round = 1; round <= rounds; round++) {
            Stage train = job.parallelFor(TRAIN_PREFIX + round, 0, shards)
                    .writes(SipsPaths.canonicalJoin("round-" + round, "model-{index}.bin"));
            if (previousAverage != null) {
                // Reads, not merely after: the next round consumes the averaged
                // model, and saying so is what lets a locality-aware policy keep
                // the round where the weights already are.
                train.reads(previousAverage);
            }
            previousAverage = job.single(AVERAGE_PREFIX + round)
                    .reads(train)
                    .writes(SipsPaths.canonicalJoin("round-" + round, "weights.bin"));
        }
        job.validate();
        return job;
    }

    /** The round a stage belongs to, or -1 for a stage this plan did not make. */
    public static int roundOf(Stage stage) {
        String stageName = stage.name();
        String number;
        if (stageName.startsWith(TRAIN_PREFIX)) {
            number = stageName.substring(TRAIN_PREFIX.length());
        } else if (stageName.startsWith(AVERAGE_PREFIX)) {
            number = stageName.substring(AVERAGE_PREFIX.length());
        } else {
            return -1;
        }
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
