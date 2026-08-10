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
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Training by selection instead of by averaging.
 *
 * <p>Federated averaging needs every worker to come back: the average is over
 * a fixed set, a missing shard is a hole, and a liar moves the mean. That is a
 * demanding contract to put on a fleet of phones.
 *
 * <p>Selection asks for much less. Each worker trains a variant on its own,
 * with no communication at all — not one barrier per round, none — and the
 * coordinator keeps the ones that scored well. A worker that vanished simply
 * has no candidate, which is not a failure but an absence, and a worker that
 * returns nonsense scores badly and is not selected. The failure model of a
 * volunteer cluster stops being something to defend against and becomes the
 * mechanism.
 *
 * <p>The cost is sample efficiency: selection needs more total compute than
 * gradient averaging to reach the same place. On a cluster of idle phones,
 * compute is the cheap thing and coordination is the expensive one.
 */
class PopulationTest {

    private static final float[] BASE = {1f, 2f, 3f};

    @Test
    void keepsTheBestScoringVariants() {
        Population population = new Population(4, 2, 0.1f, 7);
        Map<Integer, Float> losses = Map.of(0, 5.0f, 1, 1.0f, 2, 9.0f, 3, 2.0f);

        List<Integer> survivors = population.select(losses);

        assertEquals(List.of(1, 3), survivors, "lowest loss first");
    }

    @Test
    void aVariantThatNeverReportedIsSimplyNotSelected() {
        // The whole point. A phone that went into a pocket contributes no
        // candidate, and the generation continues with what did arrive --
        // where averaging would have a hole in it.
        Population population = new Population(4, 2, 0.1f, 7);
        Map<Integer, Float> partial = Map.of(0, 5.0f, 2, 1.0f);

        List<Integer> survivors = population.select(partial);

        assertEquals(List.of(2, 0), survivors);
    }

    @Test
    void aVariantThatScoredNonsenseIsNotSelected() {
        // A broken worker returns NaN or infinity rather than a loss. Sorting
        // would place NaN anywhere at all, so it is dropped outright.
        Population population = new Population(3, 1, 0.1f, 7);
        Map<Integer, Float> losses = Map.of(0, Float.NaN, 1, 4.0f, 2, Float.POSITIVE_INFINITY);

        assertEquals(List.of(1), population.select(losses));
    }

    @Test
    void aGenerationWhereNobodyReportedIsRefused() {
        // Every worker silent is not a generation, and breeding from nothing
        // would quietly restart from the seed.
        Population population = new Population(4, 2, 0.1f, 7);

        assertThrows(IllegalStateException.class, () -> population.select(Map.of()));
    }

    @Test
    void thebestSurvivesUnchanged() {
        // Elitism. Without it a generation can be worse than the one before,
        // and a long run can wander away from a good model it already had.
        Population population = new Population(4, 2, 0.5f, 7);

        List<float[]> next = population.breed(List.of(BASE, new float[]{9f, 9f, 9f}));

        assertArrayEquals(BASE, next.get(0), 0f, "the best is carried over untouched");
    }

    @Test
    void fillsThePopulationBackUp() {
        Population population = new Population(5, 2, 0.1f, 7);

        List<float[]> next = population.breed(List.of(BASE, new float[]{2f, 3f, 4f}));

        assertEquals(5, next.size());
    }

    @Test
    void mutantsDifferFromTheirParent() {
        Population population = new Population(4, 1, 0.5f, 7);

        List<float[]> next = population.breed(List.of(BASE));

        assertFalse(java.util.Arrays.equals(BASE, next.get(1)),
                "a generation of identical copies explores nothing");
    }

    @Test
    void aGenerationIsReproducibleFromItsSeed() {
        // A training run nobody can reproduce is not evidence, and randomness
        // is the whole mechanism here.
        List<float[]> once = new Population(6, 2, 0.3f, 42).breed(List.of(BASE));
        List<float[]> twice = new Population(6, 2, 0.3f, 42).breed(List.of(BASE));

        for (int i = 0; i < once.size(); i++) {
            assertArrayEquals(once.get(i), twice.get(i), 0f);
        }
    }

    @Test
    void differentSeedsExploreDifferently() {
        List<float[]> one = new Population(6, 2, 0.3f, 1).breed(List.of(BASE));
        List<float[]> other = new Population(6, 2, 0.3f, 2).breed(List.of(BASE));

        assertFalse(java.util.Arrays.equals(one.get(1), other.get(1)));
    }

    @Test
    void mutationStaysProportionalToTheWeights() {
        // A fixed step would destroy a model whose weights are tiny and barely
        // move one whose weights are large.
        Population gentle = new Population(20, 1, 0.01f, 7);

        List<float[]> next = gentle.breed(List.of(new float[]{100f}));

        for (int i = 1; i < next.size(); i++) {
            assertTrue(Math.abs(next.get(i)[0] - 100f) < 25f,
                    "a 1% mutation moved 100 to " + next.get(i)[0]);
        }
    }

    @Test
    void aSurvivorSetThatIsEmptyIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new Population(4, 2, 0.1f, 7).breed(List.of()));
    }

    @Test
    void nonsenseIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Population(1, 1, 0.1f, 7));
        assertThrows(IllegalArgumentException.class, () -> new Population(4, 0, 0.1f, 7));
        assertThrows(IllegalArgumentException.class, () -> new Population(4, 5, 0.1f, 7));
        assertThrows(IllegalArgumentException.class, () -> new Population(4, 2, -0.1f, 7));
    }
}
