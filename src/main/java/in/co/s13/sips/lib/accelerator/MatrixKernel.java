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
 * One float matrix operation, expressed twice: as OpenCL C for accelerators,
 * and as Java for the CPU.
 *
 * <p>The counterpart of {@link ImageKernel} for the arithmetic training is made
 * of. Writing one is how a researcher adds an operation — a fused GEMM plus
 * activation, a convolution, a custom loss gradient — and has it run on
 * whatever hardware each node happens to have, scheduled by the policies that
 * already exist.
 *
 * <h2>How the contract differs from {@link ImageKernel}</h2>
 *
 * <p>Image kernels must agree <em>bit for bit</em> between CPU and accelerator,
 * because neighbouring tiles of one picture are computed on different devices
 * and any difference is a visible seam. That requirement is what forces them to
 * integers.
 *
 * <p>Matrix kernels agree to a <em>relative tolerance</em> instead. Training is
 * stochastic, models are judged by loss rather than bit equality, and a weight
 * differing in its last mantissa bit is indistinguishable from a different
 * shuffle seed. Floats are therefore allowed — which is the only way this is
 * useful — and {@link #tolerance()} is the honest statement of how much drift
 * the operation permits.
 *
 * <p>That tolerance is not decoration: the conformance test runs every
 * registered kernel on every available device and holds it to the number it
 * declares. A kernel that claims 1e-6 and delivers 1e-2 fails the build.
 *
 * <h2>Shapes</h2>
 *
 * <p>Buffers are row-major and dense. {@code A} is {@code m × k}, {@code B} is
 * {@code k × n}, {@code C} is {@code m × n}, and a kernel that reads its inputs
 * transposed says so in its documentation rather than in a flag.
 */
public interface MatrixKernel {

    /** Kernel function name; must match the function in {@link #openClSource()}. */
    String name();

    /**
     * OpenCL C defining
     * {@code __kernel void <name>(__global const float* a, __global const
     * float* b, __global float* c, int m, int k, int n)}, indexed by a 2-D
     * NDRange over {@code (m, n)} — one work item per output element.
     *
     * <p>Accumulate in {@code float} unless the operation needs otherwise;
     * doubles are optional on OpenCL devices and a kernel requiring them will
     * not build on much consumer hardware.
     */
    String openClSource();

    /**
     * The same operation in Java, writing into {@code c}.
     *
     * <p>This is the oracle the accelerator form is checked against, and the
     * answer on a node with no usable device — so it must be correct first and
     * fast second.
     */
    void applyOnCpu(float[] a, float[] b, float[] c, int m, int k, int n);

    /**
     * How far the accelerator form may drift from the Java form, relative to
     * the magnitude of each element.
     *
     * <p>The default suits a plain accumulation over a few hundred terms. A
     * kernel that reassociates aggressively, or that uses {@code native_} maths
     * functions, should raise it and say why.
     */
    default float tolerance() {
        return 1e-2f;
    }

    /** What this kernel does, for a report or a device listing. */
    default String description() {
        return name();
    }

    /**
     * The square work-group edge this kernel needs, or zero to let the driver
     * choose.
     *
     * <p>A kernel that stages tiles through {@code __local} memory needs its
     * work-group size fixed, and needs the global range rounded up to a
     * multiple of it. Declaring the number here is what lets the executor do
     * both — and a tiled kernel is the difference between an accelerator that
     * matches the CPU and one that beats it.
     */
    default int tileSize() {
        return 0;
    }

    /**
     * Multiply-accumulates per output element, used to decide whether a problem
     * is worth an accelerator's transfer cost.
     *
     * <p>Defaults to {@code k}, which is right for anything shaped like a
     * matrix product.
     */
    default long workPerElement(int m, int k, int n) {
        return k;
    }
}
