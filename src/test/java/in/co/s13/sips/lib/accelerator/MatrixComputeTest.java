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

import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dense float matrix multiply, on whatever hardware is here.
 *
 * <p>This is the inner loop of training: a batch of samples times a weight
 * matrix, and its transpose on the way back. It is also the one operation where
 * an accelerator earns its keep — the image kernels are memory-bound, GEMM is
 * not.
 *
 * <h2>Why this may use floats where {@link ImageKernel} may not</h2>
 *
 * <p>The image kernels are integer-only because a tile computed on a GPU sits
 * next to a tile computed on a CPU, and any difference shows up as a visible
 * seam. Nothing about training works that way: the algorithm is stochastic
 * already, results are judged by loss and accuracy rather than bit equality,
 * and a weight that differs in its last mantissa bit is indistinguishable from
 * a different shuffle seed.
 *
 * <p>So the contract here is <em>agreement to a tolerance</em>, not bit
 * equality — and the tolerance is asserted rather than assumed, because "close
 * enough" that nobody measured is how a broken kernel ships.
 */
class MatrixComputeTest {

    /** The reference: plainly written, obviously correct, the oracle. */
    private static float[] reference(float[] a, float[] b, int m, int k, int n) {
        float[] c = new float[m * n];
        for (int row = 0; row < m; row++) {
            for (int col = 0; col < n; col++) {
                double sum = 0;
                for (int i = 0; i < k; i++) {
                    sum += (double) a[row * k + i] * b[i * n + col];
                }
                c[row * n + col] = (float) sum;
            }
        }
        return c;
    }

    private static float[] random(int length, long seed) {
        Random random = new Random(seed);
        float[] values = new float[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (float) random.nextGaussian();
        }
        return values;
    }

    @Test
    @Timeout(60)
    void multipliesAKnownPairCorrectly() {
        // [1 2] [5 6]   [19 22]
        // [3 4] [7 8] = [43 50]
        float[] a = {1, 2, 3, 4};
        float[] b = {5, 6, 7, 8};

        float[] c = MatrixCompute.multiply(a, b, 2, 2, 2);

        assertArrayClose(new float[]{19, 22, 43, 50}, c, 1e-4f);
    }

    @Test
    @Timeout(60)
    void handlesNonSquareShapes() {
        // The shape training actually uses: batch x features times features x
        // classes. Square-only kernels pass the obvious test and fail this one.
        int m = 7;
        int k = 13;
        int n = 5;
        float[] a = random(m * k, 1);
        float[] b = random(k * n, 2);

        float[] c = MatrixCompute.multiply(a, b, m, k, n);

        assertEquals(m * n, c.length);
        assertArrayClose(reference(a, b, m, k, n), c, 1e-3f);
    }

    @Test
    @Timeout(120)
    void agreesWithTheReferenceAtTrainingScale() {
        // Big enough that a tiled or vectorised implementation takes a different
        // path from the naive one, which is where the disagreements live.
        int m = 128;
        int k = 256;
        int n = 64;
        float[] a = random(m * k, 3);
        float[] b = random(k * n, 4);

        float[] c = MatrixCompute.multiply(a, b, m, k, n);

        assertArrayClose(reference(a, b, m, k, n), c, 1e-2f);
    }

    @Test
    @Timeout(60)
    void whicheverDeviceRunsItTheAnswerAgrees() {
        // The property that makes an accelerator usable here: a cluster mixing
        // GPU and CPU nodes must produce models that can be averaged together.
        // Bit equality is not required -- see the class comment -- but drift
        // beyond tolerance would make the averaged model meaningless.
        int m = 32;
        int k = 48;
        int n = 16;
        float[] a = random(m * k, 5);
        float[] b = random(k * n, 6);

        float[] onCpu = MatrixCompute.multiplyOnCpu(a, b, m, k, n);
        float[] onDevice = MatrixCompute.multiply(a, b, m, k, n);

        assertArrayClose(onCpu, onDevice, 1e-2f);
    }

    @Test
    @Timeout(60)
    void saysWhereItRan() {
        // A speedup nobody can attribute is a speedup nobody can trust. The
        // caller has to be able to tell CPU from accelerator.
        assertTrue(MatrixCompute.describe().length() > 0);
    }

    @Test
    @Timeout(60)
    void aTransposedMultiplyMatchesTransposingFirst() {
        // The backward pass needs A^T B without materialising A^T, which is a
        // separate kernel and a separate chance to get the indexing wrong.
        int m = 6;
        int k = 9;
        int n = 4;
        float[] a = random(k * m, 7);
        float[] b = random(k * n, 8);

        float[] transposed = new float[m * k];
        for (int row = 0; row < k; row++) {
            for (int col = 0; col < m; col++) {
                transposed[col * k + row] = a[row * m + col];
            }
        }

        assertArrayClose(reference(transposed, b, m, k, n),
                MatrixCompute.multiplyTransposed(a, b, m, k, n), 1e-3f);
    }

    @Test
    void mismatchedShapesAreRefused() {
        // Silently reading past the end of a buffer produces a plausible matrix
        // of nonsense, which trains into a model nobody can debug.
        assertThrows(IllegalArgumentException.class,
                () -> MatrixCompute.multiply(new float[3], new float[4], 2, 2, 2));
        assertThrows(IllegalArgumentException.class,
                () -> MatrixCompute.multiply(new float[4], new float[3], 2, 2, 2));
        assertThrows(IllegalArgumentException.class,
                () -> MatrixCompute.multiply(new float[4], new float[4], 0, 2, 2));
        assertThrows(IllegalArgumentException.class,
                () -> MatrixCompute.multiply(null, new float[4], 2, 2, 2));
    }

    private static void assertArrayClose(float[] expected, float[] actual, float tolerance) {
        assertEquals(expected.length, actual.length, "wrong result size");
        for (int i = 0; i < expected.length; i++) {
            float difference = Math.abs(expected[i] - actual[i]);
            float scale = Math.max(1f, Math.abs(expected[i]));
            assertTrue(difference / scale <= tolerance,
                    "element " + i + ": expected " + expected[i] + " but was " + actual[i]
                    + " (relative difference " + (difference / scale) + ")");
        }
    }
}
