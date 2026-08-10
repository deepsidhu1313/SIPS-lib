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
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Training by selection instead of by averaging.
 *
 * <p>Federated averaging needs every worker to come back. The average is over a
 * fixed set, so a missing shard is a hole, a duplicate overweights its data,
 * and a liar moves the mean — which is why {@link RobustAverage},
 * {@link SpeculativeRound} and {@link WorkerEligibility} all exist. That is a
 * lot of machinery to make a demanding contract survive undemanding hardware.
 *
 * <p>Selection asks for much less. Each worker trains a variant alone, with no
 * communication at all — not one barrier per round, none — and the coordinator
 * keeps the ones that scored well. A worker that vanished has no candidate,
 * which is an absence rather than a failure. A worker that returns nonsense
 * scores badly and is not selected. The failure model of a volunteer cluster
 * stops being something to defend against and becomes the mechanism.
 *
 * <h2>What it costs</h2>
 *
 * <p>Sample efficiency. Selection needs more total compute than gradient
 * averaging to reach the same place, because it learns only from the ranking
 * rather than from the gradient. That is the right trade exactly when compute
 * is cheap and coordination is expensive, which is the definition of a cluster
 * of idle phones — and the wrong one on eight GPUs in a rack.
 *
 * <p>Related to Jaderberg et al. (2017) on population-based training, minus the
 * hyperparameter exploitation: here the population is over weights, so a round
 * needs nothing from the workers but a model and a score.
 */
public final class Population {

    private final int size;
    private final int survivors;
    private final float mutationRate;
    private final long seed;

    /**
     * @param size how many variants a generation holds
     * @param survivors how many are kept to breed the next one
     * @param mutationRate how far a child moves from its parent, as a fraction
     *        of each weight — proportional rather than absolute, since a fixed
     *        step would destroy a model whose weights are tiny and barely move
     *        one whose weights are large
     * @param seed so a run is reproducible; randomness is the whole mechanism
     *        here, and a training run nobody can repeat is not evidence
     */
    public Population(int size, int survivors, float mutationRate, long seed) {
        if (size < 2) {
            throw new IllegalArgumentException("A population of " + size
                    + " has nothing to select between");
        }
        if (survivors < 1 || survivors > size) {
            throw new IllegalArgumentException("Cannot keep " + survivors
                    + " of a population of " + size);
        }
        if (mutationRate < 0) {
            throw new IllegalArgumentException("A negative mutation rate is not a rate");
        }
        this.size = size;
        this.survivors = survivors;
        this.mutationRate = mutationRate;
        this.seed = seed;
    }

    /**
     * Ranks a generation, best first.
     *
     * @param lossByVariant what each variant scored; a variant missing from
     *        the map never reported and is simply not considered
     * @return the indices of the variants to breed from
     * @throws IllegalStateException if nothing reported — breeding from an
     *         empty generation would quietly restart from the seed
     */
    public List<Integer> select(Map<Integer, Float> lossByVariant) {
        List<Map.Entry<Integer, Float>> scored = new ArrayList<>();
        for (Map.Entry<Integer, Float> entry : lossByVariant.entrySet()) {
            // Not merely bad: a NaN sorts anywhere at all, so one broken
            // worker could otherwise decide the whole ranking.
            if (entry.getValue() != null && Float.isFinite(entry.getValue())) {
                scored.add(entry);
            }
        }
        if (scored.isEmpty()) {
            throw new IllegalStateException("No variant of this generation reported a "
                    + "usable score; there is nothing to select from");
        }
        // Ties broken by index so the same generation always ranks the same
        // way, whichever order the results arrived in.
        scored.sort(Map.Entry.<Integer, Float>comparingByValue()
                .thenComparing(Comparator.comparingInt(Map.Entry::getKey)));

        List<Integer> best = new ArrayList<>();
        for (int i = 0; i < Math.min(survivors, scored.size()); i++) {
            best.add(scored.get(i).getKey());
        }
        return best;
    }

    /**
     * Builds the next generation from the survivors.
     *
     * <p>The first survivor is carried over untouched. Without that elitism a
     * generation can be worse than the one before it, and a long run can wander
     * away from a good model it already had.
     */
    public List<float[]> breed(List<float[]> survivingWeights) {
        if (survivingWeights == null || survivingWeights.isEmpty()) {
            throw new IllegalArgumentException("Cannot breed a generation from nothing");
        }
        Random random = new Random(seed);
        List<float[]> next = new ArrayList<>(size);
        next.add(survivingWeights.get(0).clone());

        while (next.size() < size) {
            float[] parent = survivingWeights.get(next.size() % survivingWeights.size());
            float[] child = new float[parent.length];
            for (int i = 0; i < parent.length; i++) {
                child[i] = parent[i] + (float) (random.nextGaussian() * mutationRate
                        * Math.abs(parent[i]));
            }
            next.add(child);
        }
        return next;
    }

    /** How many variants a generation holds. */
    public int size() {
        return size;
    }

    /** How many are kept each generation. */
    public int survivors() {
        return survivors;
    }
}
