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
package in.co.s13.sips.lib.accelerator;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Back-to-back matmuls with the intermediate left on the device.
 *
 * <p>{@code multiply} uploads both operands and downloads the result on every
 * call, so {@code (A·B1)·B2} pays the wire three extra times: C1 comes down
 * only to go straight back up. A chain keeps the running product resident and
 * downloads once at the end.
 *
 * <p>This is an <em>experiment</em>, in the research sense: the transfer it
 * avoids is O(n²) against O(n³) compute per link, so the win should shrink as
 * matrices grow — and the sample measures exactly that, publishing the
 * crossover rather than assuming the feature pays everywhere. These tests pin
 * only what must be true regardless of the measurement: the chain computes the
 * same numbers the one-at-a-time path does.
 */
class GemmChainTest {

    private static float[] random(int count, long seed) {
        Random random = new Random(seed);
        float[] values = new float[count];
        for (int i = 0; i < count; i++) {
            values[i] = (float) random.nextGaussian();
        }
        return values;
    }

    /** The chain applied one multiply at a time — the reference. */
    private static float[] sequential(float[] a, int m, int k, List<float[]> rights,
            int[] rightCols) {
        float[] current = a;
        int currentCols = k;
        for (int i = 0; i < rights.size(); i++) {
            current = MatrixCompute.multiplyOnCpu(current, rights.get(i),
                    m, currentCols, rightCols[i]);
            currentCols = rightCols[i];
        }
        return current;
    }

    @Test
    void aChainComputesWhatSequentialMultipliesWould() {
        int m = 8;
        int k = 6;
        float[] a = random(m * k, 1);
        List<float[]> rights = List.of(random(6 * 5, 2), random(5 * 7, 3), random(7 * 4, 4));
        int[] rightCols = {5, 7, 4};

        float[] chained = MatrixCompute.chain(a, m, k, rights, rightCols);
        float[] reference = sequential(a, m, k, rights, rightCols);

        assertArrayEquals(reference, chained, 1e-3f);
    }

    @Test
    void aChainOfOneIsJustAMultiply() {
        int m = 4;
        int k = 3;
        float[] a = random(m * k, 5);
        List<float[]> rights = List.of(random(3 * 2, 6));

        assertArrayEquals(MatrixCompute.multiplyOnCpu(a, rights.get(0), m, k, 2),
                MatrixCompute.chain(a, m, k, rights, new int[]{2}), 1e-3f);
    }

    @Test
    void aLargeChainAgreesWhereverItRan() {
        // Big enough to land on an accelerator when one exists; the answer
        // must agree with the CPU within float tolerance either way. On a
        // machine with no device this still passes, through the fallback --
        // which is itself worth pinning.
        int n = 96;
        float[] a = random(n * n, 7);
        List<float[]> rights = List.of(random(n * n, 8), random(n * n, 9));
        int[] rightCols = {n, n};

        float[] chained = MatrixCompute.chain(a, n, n, rights, rightCols);
        float[] reference = sequential(a, n, n, rights, rightCols);

        for (int i = 0; i < n * n; i++) {
            assertEquals(reference[i], chained[i],
                    Math.max(1e-2f, Math.abs(reference[i]) * 1e-3f), "at " + i);
        }
    }

    @Test
    void dimensionsThatDoNotChainAreRefused() {
        // rights[1] must have as many rows as rights[0] has columns. On the
        // resident path the intermediate never comes back to the host, so a
        // mismatch must be caught before anything is enqueued.
        float[] a = random(4 * 3, 10);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> MatrixCompute.chain(a, 4, 3,
                        List.of(random(3 * 5, 11), random(9 * 2, 12)), new int[]{5, 2}));

        assertTrue(refused.getMessage().contains("5"), refused.getMessage());
    }

    @Test
    void nonsenseIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> MatrixCompute.chain(null, 2, 2, List.of(new float[4]), new int[]{2}));
        assertThrows(IllegalArgumentException.class,
                () -> MatrixCompute.chain(new float[4], 2, 2, List.of(), new int[]{}));
        assertThrows(IllegalArgumentException.class,
                () -> MatrixCompute.chain(new float[4], 2, 2,
                        List.of(new float[4]), new int[]{2, 3}));
    }
}
