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

import java.util.List;

/**
 * Combining models that were never trained together.
 *
 * <p>Federated averaging combines <em>weights</em>, which only works when every
 * model started from the same place and stayed near it — that is what the
 * per-round barrier buys, and it is the expensive part. This combines
 * <em>outputs</em> instead, and needs none of it: models of different shapes,
 * trained for different lengths, on different data, still combine.
 *
 * <p>So the graph loses its inner loop entirely. Instead of
 * {@code train → average → train → average}, it is {@code train → distil} with
 * one barrier in the whole run, and each worker trains alone to convergence. On
 * a fleet where a barrier costs more than the work between two of them, that is
 * the difference between a run that finishes and one that does not.
 *
 * <p>The catch is that an ensemble of eight models costs eight models to serve,
 * which is why it is a step on the way to one distilled student rather than the
 * deliverable. The student learns from {@link #soften softened} targets: a hard
 * label teaches only the answer, while a softened distribution teaches which
 * wrong answers were nearly right, which is most of what the ensemble knows
 * that one model does not (Hinton et al., 2015).
 */
public final class Ensemble {

    private Ensemble() {
    }

    /**
     * The ensemble's opinion for one input, as a distribution a student can
     * learn from.
     *
     * @param memberProbabilities each member's probabilities over the same
     *        labels, in any order
     * @param temperature above 1 spreads the mass towards the runners-up; 1
     *        leaves the mean distribution alone
     */
    public static float[] soften(List<float[]> memberProbabilities, float temperature) {
        if (memberProbabilities == null || memberProbabilities.isEmpty()) {
            throw new IllegalArgumentException("An ensemble needs at least one member");
        }
        if (temperature <= 0 || !Float.isFinite(temperature)) {
            throw new IllegalArgumentException("Temperature must be positive, not " + temperature);
        }

        int labels = memberProbabilities.get(0).length;
        double[] mean = new double[labels];
        for (float[] member : memberProbabilities) {
            if (member.length != labels) {
                throw new IllegalArgumentException("Members disagree on the number of labels: "
                        + labels + " and " + member.length + "; these are different tasks");
            }
            for (int label = 0; label < labels; label++) {
                if (!Float.isFinite(member[label])) {
                    // One diverged model would otherwise make every distilled
                    // target NaN, destroying the whole round's teaching signal.
                    throw new IllegalArgumentException("An ensemble member returned "
                            + member[label] + "; it has diverged and cannot be combined");
                }
                mean[label] += member[label];
            }
        }
        for (int label = 0; label < labels; label++) {
            mean[label] /= memberProbabilities.size();
        }

        if (temperature == 1.0f) {
            return normalised(mean);
        }
        // Raising to 1/T and renormalising: the standard way to soften a
        // distribution when the logits are gone and only probabilities remain.
        for (int label = 0; label < labels; label++) {
            mean[label] = Math.pow(mean[label], 1.0 / temperature);
        }
        return normalised(mean);
    }

    /** The label an ensemble would pick. Ties go to the first, so it is stable. */
    public static int argmax(float[] probabilities) {
        int best = 0;
        for (int label = 1; label < probabilities.length; label++) {
            if (probabilities[label] > probabilities[best]) {
                best = label;
            }
        }
        return best;
    }

    private static float[] normalised(double[] values) {
        double total = 0;
        for (double value : values) {
            total += value;
        }
        float[] distribution = new float[values.length];
        if (total <= 0) {
            // Every member said zero everywhere, which is not an opinion.
            // A uniform distribution teaches nothing, which is honest.
            java.util.Arrays.fill(distribution, 1f / values.length);
            return distribution;
        }
        for (int i = 0; i < values.length; i++) {
            distribution[i] = (float) (values[i] / total);
        }
        return distribution;
    }
}
