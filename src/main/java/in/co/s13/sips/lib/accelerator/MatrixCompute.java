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

/**
 * Dense float matrix multiply, on the best device this node has.
 *
 * <p>The inner loop of training, and the one operation where an accelerator
 * earns its keep: the image kernels are memory-bound, so a GPU mostly waits on
 * transfers, but GEMM does O(n³) arithmetic on O(n²) data and is what graphics
 * hardware exists to do.
 *
 * <h2>Why this may use floats where {@link ImageKernel} may not</h2>
 *
 * <p>The image kernels are integer-only, deliberately: a tile computed on a GPU
 * sits beside a tile computed on a CPU, and any difference is a visible seam.
 * Nothing about training works that way. The algorithm is stochastic to begin
 * with, models are judged by loss and accuracy rather than bit equality, and a
 * weight differing in its last mantissa bit is indistinguishable from a
 * different shuffle seed.
 *
 * <p>So the guarantee here is weaker and stated openly: results agree to a
 * relative tolerance across devices, not bit for bit. That is enough for
 * federated averaging — models from a GPU node and a CPU node average together
 * meaningfully — and it is asserted by the tests rather than assumed.
 *
 * <p>A caller that needs reproducibility to the bit should use
 * {@link #multiplyOnCpu}, or run the whole job as WebAssembly, where floats
 * <em>are</em> pinned across nodes.
 *
 * <h2>Choosing a device</h2>
 *
 * <p>OpenCL when a usable device is present and the problem is big enough to
 * repay the transfer; the Java path otherwise. Small matrices stay on the CPU
 * because moving a few kilobytes across PCIe costs more than the arithmetic
 * saves — a GPU that is slower than the CPU is the usual outcome of ignoring
 * that.
 */
public final class MatrixCompute {

    /**
     * Below this many multiply-accumulates, the CPU wins.
     *
     * <p>Transfer and kernel launch cost roughly a hundred microseconds; the
     * CPU does a few hundred million MACs a second. The crossover is around a
     * million, and being wrong here is only ever a modest slowdown, not a wrong
     * answer.
     */
    static final long ACCELERATOR_THRESHOLD = 1_000_000L;

    private MatrixCompute() {
    }

    /**
     * {@code C = A × B}, row-major.
     *
     * @param a {@code m × k}
     * @param b {@code k × n}
     * @return {@code m × n}
     */
    public static float[] multiply(float[] a, float[] b, int m, int k, int n) {
        check(a, b, m, k, n);
        // Tiled first: it is several times faster where it runs. A device
        // whose work-group limit will not take a 16x16 group -- integrated
        // GPUs and OpenCL CPU devices often will not -- gets the plain kernel
        // instead, which is still worth the transfer at this size. The CPU is
        // the last resort, and identical arithmetic in every case.
        if (worthAccelerating(MatrixKernels.GEMM_TILED, m, k, n)) {
            float[] tiled = onAccelerator(MatrixKernels.GEMM_TILED, a, b, m, k, n);
            if (tiled != null) {
                return tiled;
            }
            float[] plain = onAccelerator(MatrixKernels.GEMM, a, b, m, k, n);
            if (plain != null) {
                return plain;
            }
        }
        return multiplyOnCpu(a, b, m, k, n);
    }

    /**
     * Runs any {@link MatrixKernel} on the best device for the problem.
     *
     * <p>The front door for a researcher's own kernel: written once as the
     * OpenCL/Java pair, then placed here without knowing what hardware the node
     * has.
     */
    public static float[] run(MatrixKernel kernel, float[] a, float[] b, int m, int k, int n) {
        if (kernel == null) {
            throw new IllegalArgumentException("kernel must not be null");
        }
        if (worthAccelerating(kernel, m, k, n)) {
            float[] accelerated = onAccelerator(kernel, a, b, m, k, n);
            if (accelerated != null) {
                return accelerated;
            }
        }
        float[] c = new float[m * n];
        kernel.applyOnCpu(a, b, c, m, k, n);
        return c;
    }

    /**
     * Whether a problem repays an accelerator's transfer cost.
     *
     * <p>Asks the kernel rather than assuming {@code k}: a row-wise softmax
     * does work proportional to the row, so a wide one is cheap and belongs on
     * the CPU whatever {@code k} says.
     */
    static boolean worthAccelerating(MatrixKernel kernel, int m, int k, int n) {
        return (long) m * n * kernel.workPerElement(m, k, n) >= ACCELERATOR_THRESHOLD;
    }

    /**
     * One executor per thread, reused.
     *
     * <p>Not an optimisation — a correctness condition for the whole idea.
     * Compiling a kernel costs tens of milliseconds against a fraction of one
     * to run it, so building an executor per multiply spends nearly all its
     * time in the driver's compiler and is measurably <em>slower</em> than the
     * CPU. Per thread rather than shared because an OpenCL command queue is not
     * safe to share.
     */
    private static final ThreadLocal<MatrixKernelExecutor> EXECUTOR = new ThreadLocal<>();

    /** Set once a device has been tried and found unusable, so it is not retried. */
    private static volatile boolean acceleratorUnusable;

    /** The chain on this thread's device, or null -- same policy as multiply. */
    private static float[] chainOnAccelerator(float[] a, int m, int k,
            java.util.List<float[]> rights, int[] rightCols) {
        if (acceleratorUnusable) {
            return null;
        }
        try {
            MatrixKernelExecutor executor = EXECUTOR.get();
            if (executor == null) {
                Device device = matrixDevice().orElse(null);
                if (device == null) {
                    acceleratorUnusable = true;
                    return null;
                }
                executor = new OpenCLMatrixExecutor(device);
                EXECUTOR.set(executor);
            }
            MatrixKernel kernel = executor.supports(MatrixKernels.GEMM_TILED)
                    ? MatrixKernels.GEMM_TILED : MatrixKernels.GEMM;
            if (!executor.supports(kernel)) {
                return null;
            }
            return executor.chain(kernel, a, m, k, rights, rightCols);
        } catch (RuntimeException | Error ex) {
            releaseThreadExecutor();
            acceleratorUnusable = true;
            return null;
        }
    }

    private static float[] onAccelerator(MatrixKernel kernel, float[] a, float[] b,
            int m, int k, int n) {
        if (acceleratorUnusable) {
            return null;
        }
        try {
            MatrixKernelExecutor executor = EXECUTOR.get();
            if (executor == null) {
                Device device = matrixDevice().orElse(null);
                if (device == null) {
                    acceleratorUnusable = true;
                    return null;
                }
                executor = new OpenCLMatrixExecutor(device);
                EXECUTOR.set(executor);
            }
            if (!executor.supports(kernel)) {
                return null;
            }
            return executor.execute(kernel, a, b, m, k, n);
        } catch (RuntimeException | Error ex) {
            // A driver that will not build a kernel, or a device that vanished,
            // is a reason to use the CPU -- not to fail the job. The CPU form is
            // the oracle anyway, so the answer is the same to a tolerance.
            releaseThreadExecutor();
            acceleratorUnusable = true;
            return null;
        }
    }

    /**
     * Closes this thread's executor.
     *
     * <p>Worth calling when a worker thread is finished with matrix work; an
     * OpenCL context holds device memory that the garbage collector cannot free
     * on its own.
     */
    public static void releaseThreadExecutor() {
        MatrixKernelExecutor executor = EXECUTOR.get();
        EXECUTOR.remove();
        if (executor != null) {
            try {
                executor.close();
            } catch (RuntimeException | Error ignored) {
                // Already going away; a failure to close is not worth raising.
            }
        }
    }

    /** The OpenCL device matrix work would use, if there is one. */
    static java.util.Optional<Device> matrixDevice() {
        try {
            return AcceleratorRegistry.devicesOf(Backend.OPENCL).stream()
                    // A discrete GPU first: matrix work is compute-bound, which
                    // is the one case where crossing PCIe is worth it.
                    .max(java.util.Comparator.comparingInt(MatrixCompute::preference));
        } catch (RuntimeException | Error noRuntime) {
            return java.util.Optional.empty();
        }
    }

    private static int preference(Device device) {
        // AcceleratorType already ranks itself; matrix work wants the same
        // order, so there is nothing to invent here.
        return device.type().preference();
    }

    /**
     * {@code C = Aᵀ × B}, without materialising the transpose.
     *
     * <p>The backward pass's shape. Building the transpose first would double
     * the memory traffic of the step that already dominates it.
     *
     * @param a {@code k × m}, used transposed
     * @param b {@code k × n}
     * @return {@code m × n}
     */
    /**
     * Multiplies down a chain, keeping the running product on the device when
     * one is available: {@code ((a·rights[0])·rights[1])·…}.
     *
     * <p>Every link's intermediate would otherwise be downloaded only to be
     * uploaded straight back. Whether avoiding that is worth anything is a
     * measured question — transfer is O(n²) against O(n³) compute per link, so
     * the saving shrinks as matrices grow — and the ArrayProgramming sample
     * publishes the numbers rather than assuming them.
     *
     * @param rightCols column count of each right operand; row count is the
     *        previous link's columns
     */
    public static float[] chain(float[] a, int m, int k, java.util.List<float[]> rights,
            int[] rightCols) {
        if (a == null || a.length != m * k) {
            throw new IllegalArgumentException("A must be " + m + "x" + k);
        }
        if (rights == null || rights.isEmpty() || rightCols == null
                || rights.size() != rightCols.length) {
            throw new IllegalArgumentException("A chain needs matching rights and rightCols; "
                    + "got " + (rights == null ? 0 : rights.size()) + " matrices and "
                    + (rightCols == null ? 0 : rightCols.length) + " widths");
        }
        int currentCols = k;
        for (int i = 0; i < rights.size(); i++) {
            if (rights.get(i) == null || rights.get(i).length != currentCols * rightCols[i]) {
                throw new IllegalArgumentException("Link " + i + " must be " + currentCols
                        + "x" + rightCols[i] + " (" + (currentCols * rightCols[i])
                        + " values), not " + (rights.get(i) == null ? "null"
                        : rights.get(i).length));
            }
            currentCols = rightCols[i];
        }

        // Worth the device only if the whole chain is: one small link would
        // drag the resident product down anyway, so the decision is all-or-
        // nothing, judged on the largest link.
        long largest = 0;
        currentCols = k;
        for (int i = 0; i < rights.size(); i++) {
            largest = Math.max(largest, (long) m * rightCols[i]
                    * MatrixKernels.GEMM_TILED.workPerElement(m, currentCols, rightCols[i]));
            currentCols = rightCols[i];
        }
        if (largest >= ACCELERATOR_THRESHOLD) {
            float[] resident = chainOnAccelerator(a, m, k, rights, rightCols);
            if (resident != null) {
                return resident;
            }
        }
        float[] current = a;
        currentCols = k;
        for (int i = 0; i < rights.size(); i++) {
            current = multiply(current, rights.get(i), m, currentCols, rightCols[i]);
            currentCols = rightCols[i];
        }
        return current;
    }

    public static float[] multiplyTransposed(float[] a, float[] b, int m, int k, int n) {
        checkShape(m, k, n);
        require(a != null && b != null, "matrices must not be null");
        require(a.length == k * m, "A is " + a.length + " values, expected " + (k * m));
        require(b.length == k * n, "B is " + b.length + " values, expected " + (k * n));
        return run(MatrixKernels.GEMM_TRANSPOSED, a, b, m, k, n);
    }

    /**
     * The CPU form: the oracle, and the answer when no accelerator is usable.
     *
     * <p>Accumulates in double. A float accumulator over a few hundred terms
     * loses enough precision to show up as a systematically different model,
     * and the cost of the wider accumulator is nothing next to the memory
     * traffic.
     */
    public static float[] multiplyOnCpu(float[] a, float[] b, int m, int k, int n) {
        check(a, b, m, k, n);
        float[] c = new float[m * n];
        MatrixKernels.GEMM.applyOnCpu(a, b, c, m, k, n);
        return c;
    }

    /** Which device the next large multiply would use, for a log or a report. */
    public static String describe() {
        return matrixDevice()
                .map(device -> device.name() + " (" + device.type() + ", OpenCL)")
                .orElse("CPU (Java); no OpenCL device available for matrix work");
    }

    /** Whether an accelerator is available for matrix work at all. */
    public static boolean acceleratorAvailable() {
        return matrixDevice().isPresent();
    }

    private static void check(float[] a, float[] b, int m, int k, int n) {
        checkShape(m, k, n);
        require(a != null && b != null, "matrices must not be null");
        require(a.length == m * k, "A is " + a.length + " values, expected " + (m * k)
                + " for " + m + "x" + k);
        require(b.length == k * n, "B is " + b.length + " values, expected " + (k * n)
                + " for " + k + "x" + n);
    }

    private static void checkShape(int m, int k, int n) {
        require(m > 0 && k > 0 && n > 0,
                "dimensions must be positive: " + m + "x" + k + " times " + k + "x" + n);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            // Reading past a buffer produces a plausible matrix of nonsense,
            // which trains into a model nobody can debug.
            throw new IllegalArgumentException(message);
        }
    }
}
