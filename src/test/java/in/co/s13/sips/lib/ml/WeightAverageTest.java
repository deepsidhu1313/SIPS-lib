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
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One model from many — the aggregation step training turns on.
 *
 * <p>Getting this subtly wrong does not fail; it trains a slightly different
 * model each run, which is the least debuggable outcome available. So the tests
 * here are mostly about the ways it could be quietly wrong: arrival order,
 * unequal shards, duplicated results.
 */
class WeightAverageTest {

    @Test
    void equalShardsAverageToThePlainMean() {
        List<Contribution> results = List.of(
                new Contribution(0, 100, new float[]{1f, 2f}),
                new Contribution(1, 100, new float[]{3f, 4f}));

        assertArrayEquals(new float[]{2f, 3f}, WeightAverage.of(results));
    }

    @Test
    void aBiggerShardPullsHarder() {
        // Sample-weighted, not equal: a ten-example shard must not pull as hard
        // as a ten-thousand-example one.
        List<Contribution> results = List.of(
                new Contribution(0, 3, new float[]{0f}),
                new Contribution(1, 1, new float[]{4f}));

        assertArrayEquals(new float[]{1f}, WeightAverage.of(results));
    }

    @Test
    void arrivalOrderCannotChangeTheModel() {
        // Results come back in whatever order nodes finish, and float addition
        // is not associative. The averaged model must be byte-identical anyway,
        // or no training run is reproducible.
        Random random = new Random(42);
        List<Contribution> results = new ArrayList<>();
        for (int chunk = 0; chunk < 32; chunk++) {
            float[] weights = new float[257];
            for (int i = 0; i < weights.length; i++) {
                weights[i] = (random.nextFloat() - 0.5f) * 2e3f;
            }
            results.add(new Contribution(chunk, 100 + random.nextInt(9000), weights));
        }

        float[] inOrder = WeightAverage.of(results);
        List<Contribution> shuffled = new ArrayList<>(results);
        Collections.shuffle(shuffled, new Random(7));
        float[] outOfOrder = WeightAverage.of(shuffled);

        assertArrayEquals(inOrder, outOfOrder,
                "which node finished first must not change the model");
    }

    @Test
    void aSingleContributionIsItself() {
        float[] weights = {1.5f, -2.5f, 3.25f};

        assertArrayEquals(weights,
                WeightAverage.of(List.of(new Contribution(0, 10, weights))));
    }

    @Test
    void duplicatedResultsAreRefusedNotDoubleCounted() {
        // Backup tasks and retries can produce two results for one shard.
        // Averaging both doubles that shard's vote -- silently.
        List<Contribution> results = List.of(
                new Contribution(0, 100, new float[]{1f}),
                new Contribution(0, 100, new float[]{1f}));

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> WeightAverage.of(results)).getMessage().contains("chunk 0"));
    }

    @Test
    void mismatchedModelsAreRefusedWithBothSizes() {
        List<Contribution> results = List.of(
                new Contribution(0, 100, new float[]{1f, 2f}),
                new Contribution(1, 100, new float[]{1f, 2f, 3f}));

        String message = assertThrows(IllegalArgumentException.class,
                () -> WeightAverage.of(results)).getMessage();
        assertTrue(message.contains("2") && message.contains("3"), message);
    }

    @Test
    void aShardThatTrainedOnNothingHasNoVote() {
        assertThrows(IllegalArgumentException.class,
                () -> new Contribution(0, 0, new float[]{1f}));
        assertThrows(IllegalArgumentException.class,
                () -> new Contribution(0, -5, new float[]{1f}));
    }

    @Test
    void nothingToAverageIsAnErrorNotAnEmptyModel() {
        assertThrows(IllegalArgumentException.class, () -> WeightAverage.of(List.of()));
        assertThrows(IllegalArgumentException.class, () -> WeightAverage.of(null));
    }

    @Test
    void theRoundTripThroughBytesIsExact() {
        // The full wire path a round takes: average, encode, decode. Weights
        // must survive it bit for bit.
        float[] averaged = WeightAverage.of(List.of(
                new Contribution(0, 7, new float[]{0.1f, -0.2f, 3e-8f}),
                new Contribution(1, 13, new float[]{1.1f, 2.2f, -3e8f})));

        assertArrayEquals(averaged, Tensors.fromBytes(Tensors.toBytes(averaged)));
    }

    @Test
    void bytesAreLittleEndianForWasm() {
        // 1.0f is 0x3F800000; little-endian puts the zeros first. A WASM kernel
        // f32.loads these bytes directly, so the layout is a contract.
        assertArrayEquals(new byte[]{0, 0, -128, 63}, Tensors.toBytes(new float[]{1.0f}));
    }

    @Test
    void truncatedBytesAreRefusedNotRounded() {
        // A short transfer read as a shorter model would train on corrupt
        // weights with nothing failing.
        assertThrows(IllegalArgumentException.class,
                () -> Tensors.fromBytes(new byte[]{1, 2, 3}));
        assertThrows(IllegalArgumentException.class, () -> Tensors.fromBytes(null));
        assertThrows(IllegalArgumentException.class, () -> Tensors.toBytes(null));
        assertEquals(0, Tensors.fromBytes(new byte[0]).length);
    }
}
