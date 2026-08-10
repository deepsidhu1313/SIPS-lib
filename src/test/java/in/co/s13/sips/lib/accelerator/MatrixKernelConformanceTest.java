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
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every matrix kernel, on every device this machine has.
 *
 * <p>The safety net for a researcher adding one. A kernel is written twice —
 * OpenCL C and Java — and the two forms drifting apart is not a crash but a
 * model that trains differently depending on which node ran it. This holds each
 * kernel to the tolerance it declares, on whatever hardware is present.
 *
 * <p>Where the image kernels demand bit equality, these demand agreement to a
 * declared bound. That is the honest requirement for float work, and declaring
 * a bound nobody checks would be worse than declaring none — so the number in
 * {@link MatrixKernel#tolerance()} is enforced here.
 *
 * <p>On a machine with no OpenCL device the device half is skipped and the CPU
 * half still runs. A test that quietly passes because it did nothing is worth
 * knowing about, so what ran is printed.
 */
class MatrixKernelConformanceTest {

    private static final int M = 24;
    private static final int K = 40;
    private static final int N = 16;

    private static float[] random(int length, long seed) {
        Random random = new Random(seed);
        float[] values = new float[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (float) random.nextGaussian();
        }
        return values;
    }

    @Test
    @Timeout(300)
    void everyKernelAgreesWithItselfOnEveryDevice() {
        List<Device> devices = AcceleratorRegistry.devicesOf(Backend.OPENCL);
        System.out.println("Matrix conformance across " + devices.size()
                + " OpenCL device(s): " + devices);

        for (Map.Entry<String, MatrixKernel> entry : MatrixKernels.all().entrySet()) {
            MatrixKernel kernel = entry.getValue();
            float[] a = random(M * K, 11);
            float[] b = random(K * N, 12);

            float[] expected = new float[M * N];
            kernel.applyOnCpu(a, b, expected, M, K, N);

            for (Device device : devices) {
                try (MatrixKernelExecutor executor = new OpenCLMatrixExecutor(device)) {
                    if (!executor.supports(kernel)) {
                        // A work group this device will not allow. A fact about
                        // the machine, and the reason MatrixCompute keeps a
                        // non-tiled kernel to fall back to.
                        System.out.println("  " + device.name() + " cannot run "
                                + kernel.name() + " (work-group size)");
                        continue;
                    }
                    float[] actual = executor.execute(kernel, a, b, M, K, N);
                    assertClose(expected, actual, kernel.tolerance(),
                            kernel.name() + " on " + device.name());
                } catch (IllegalStateException unusable) {
                    // A device the driver will not build for is a fact about
                    // this machine, not a failure of the kernel.
                    System.out.println("  skipped " + device.name() + ": "
                            + unusable.getMessage());
                }
            }
        }
    }

    @Test
    void everyKernelDeclaresWhatItPromises() {
        // A kernel with no name cannot be compiled against its source, and one
        // whose tolerance is zero is claiming bit equality it cannot deliver in
        // float.
        for (MatrixKernel kernel : MatrixKernels.all().values()) {
            assertNotNull(kernel.name());
            assertTrue(kernel.openClSource().contains(kernel.name()),
                    kernel.name() + "'s source must define a function of that name");
            assertTrue(kernel.tolerance() > 0,
                    kernel.name() + " claims exact float agreement, which is not achievable");
            assertTrue(kernel.workPerElement(M, K, N) > 0);
        }
    }

    @Test
    void theTransposedKernelReallyReadsItsInputTransposed() {
        // The one indexing mistake that produces a plausible matrix of the right
        // shape and entirely wrong numbers.
        int m = 3;
        int k = 4;
        int n = 2;
        float[] a = random(k * m, 21);
        float[] b = random(k * n, 22);

        float[] viaKernel = new float[m * n];
        MatrixKernels.GEMM_TRANSPOSED.applyOnCpu(a, b, viaKernel, m, k, n);

        float[] transposed = new float[m * k];
        for (int row = 0; row < k; row++) {
            for (int col = 0; col < m; col++) {
                transposed[col * k + row] = a[row * m + col];
            }
        }
        float[] viaPlainGemm = new float[m * n];
        MatrixKernels.GEMM.applyOnCpu(transposed, b, viaPlainGemm, m, k, n);

        assertClose(viaPlainGemm, viaKernel, 1e-4f, "A^T B against transpose-then-multiply");
    }

    @Test
    void softmaxRowsSumToOneAndSurviveLargeLogits() {
        // exp() overflows on logits a trained model routinely produces; the
        // stabilised form is the only one that survives, and forgetting it
        // yields NaN rather than a wrong number.
        int rows = 4;
        int columns = 5;
        float[] logits = new float[rows * columns];
        for (int i = 0; i < logits.length; i++) {
            logits[i] = 700f + i;
        }
        float[] out = new float[rows * columns];

        MatrixKernels.SOFTMAX_ROWS.applyOnCpu(logits, new float[0], out, rows, 1, columns);

        for (int row = 0; row < rows; row++) {
            double sum = 0;
            for (int col = 0; col < columns; col++) {
                float value = out[row * columns + col];
                assertTrue(Float.isFinite(value), "row " + row + " produced " + value);
                sum += value;
            }
            assertEquals(1.0, sum, 1e-4, "row " + row + " does not sum to one");
        }
    }

    @Test
    void cheapWorkStaysOnTheCpu() {
        // A softmax's work scales with the row, not with k. Sending it to an
        // accelerator would pay a transfer to save nothing -- the usual way a
        // GPU ends up slower than the CPU.
        assertTrue(MatrixCompute.worthAccelerating(MatrixKernels.GEMM, 512, 512, 512));
        assertTrue(!MatrixCompute.worthAccelerating(MatrixKernels.SOFTMAX_ROWS, 64, 512, 10),
                "a narrow softmax is not worth a transfer");
    }

    private static void assertClose(float[] expected, float[] actual, float tolerance,
            String what) {
        assertEquals(expected.length, actual.length, what + ": wrong size");
        for (int i = 0; i < expected.length; i++) {
            float difference = Math.abs(expected[i] - actual[i]);
            float scale = Math.max(1f, Math.abs(expected[i]));
            assertTrue(difference / scale <= tolerance,
                    what + " differs at " + i + ": CPU " + expected[i] + " vs " + actual[i]
                    + ", relative " + (difference / scale) + " over tolerance " + tolerance);
        }
    }
}
