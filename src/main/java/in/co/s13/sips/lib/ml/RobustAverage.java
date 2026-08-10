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

import in.co.s13.sips.lib.ml.WeightAverage.Contribution;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Averaging weights when not every worker can be trusted to send good ones.
 *
 * <p>{@link WeightAverage} takes a mean, and a mean has breakdown point zero:
 * one contribution can move it anywhere at all. That is fine on a cluster of
 * machines one person owns. It stops being fine the moment the workers are
 * strangers' phones — a device that is broken, that ran out of memory half way
 * through, or whose owner would like to steer the model, poisons every round it
 * takes part in, and nothing about the run looks wrong afterwards.
 *
 * <p>This is the coordinate-wise trimmed mean of Yin et al. (2018): for each
 * weight, drop the {@code trim} largest and {@code trim} smallest values and
 * average the rest. It tolerates {@code trim} arbitrarily bad contributions,
 * whatever they contain.
 *
 * <h2>Per coordinate, not per contribution</h2>
 *
 * <p>Trimming whole models would only catch a worker that is extreme
 * <em>everywhere</em>, and a targeted attack is the opposite of that: it moves
 * the few weights it cares about and leaves the rest alone, so it would sit
 * comfortably inside any whole-model bound.
 *
 * <h2>What it costs</h2>
 *
 * <p>Two things. Sample-count weighting is gone — a trimmed set no longer has
 * the counts that made {@link WeightAverage} an unbiased estimate of training
 * on the union of the shards, so this treats survivors equally. And with honest
 * workers it is a slightly noisier estimate than the mean, since it throws away
 * real data. Neither matters next to the failure it prevents, but both are
 * reasons to use {@code trim = 0} — which is exactly the ordinary mean — on a
 * cluster whose members are all your own.
 */
public final class RobustAverage {

    private RobustAverage() {
    }

    /**
     * The trimmed mean of a round's contributions.
     *
     * @param trim how many extremes to drop from each end of each coordinate;
     *        zero gives the ordinary weighted mean
     * @throws IllegalArgumentException if trimming would leave nothing to
     *         average — an average of one worker is not an average, and
     *         returning it silently would look like it worked
     */
    public static float[] of(List<Contribution> contributions, int trim) {
        if (contributions == null || contributions.isEmpty()) {
            throw new IllegalArgumentException("Nothing to average");
        }
        if (trim < 0) {
            throw new IllegalArgumentException("Cannot trim " + trim + " contributions");
        }
        if (trim == 0) {
            // Identical to the ordinary mean, sample weighting and all, so a
            // cluster that trusts its members loses nothing by calling this.
            return WeightAverage.of(contributions);
        }

        int workers = contributions.size();
        int surviving = workers - 2 * trim;
        if (surviving < 2) {
            throw new IllegalArgumentException("Trimming " + trim + " from each end of "
                    + workers + " contributions leaves " + Math.max(0, surviving)
                    + "; at most " + trimmableFrom(workers) + " can be trimmed here");
        }

        // Sorted by chunk number for the same reason WeightAverage is: two
        // nodes averaging the same contributions must agree bit for bit, and a
        // model that depends on who answered first is not reproducible.
        List<Contribution> ordered = new ArrayList<>(contributions);
        ordered.sort(Comparator.comparingInt(Contribution::chunkNumber));

        int length = ordered.get(0).weights().length;
        for (Contribution contribution : ordered) {
            if (contribution.weights().length != length) {
                throw new IllegalArgumentException("Contribution from chunk "
                        + contribution.chunkNumber() + " has " + contribution.weights().length
                        + " weights, not " + length + "; these are different models");
            }
        }

        float[] averaged = new float[length];
        float[] coordinate = new float[workers];
        for (int weight = 0; weight < length; weight++) {
            for (int worker = 0; worker < workers; worker++) {
                coordinate[worker] = ordered.get(worker).weights()[weight];
            }
            java.util.Arrays.sort(coordinate);
            // Accumulated in double: a coordinate summed across many workers
            // loses float precision, and this is the model itself.
            double total = 0;
            for (int i = trim; i < workers - trim; i++) {
                total += coordinate[i];
            }
            averaged[weight] = (float) (total / surviving);
        }
        return averaged;
    }

    /**
     * The most that can be trimmed from a round of this size and still leave
     * an average.
     *
     * <p>Worth asking before a round starts: a cluster of two cannot tolerate
     * anything, and a caller that wanted robustness should know that rather
     * than discover it when the round fails.
     */
    public static int trimmableFrom(int workers) {
        return Math.max(0, (workers - 2) / 2);
    }
}
