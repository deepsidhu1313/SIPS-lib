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
 * One image operation, expressed twice: as OpenCL C for accelerators, and as
 * Java for the CPU.
 *
 * <p>Both forms must produce <em>bit-identical</em> results. That is a hard
 * requirement, not a nicety: a distributed job may place one tile on a GPU and
 * its neighbour on a CPU, and any difference between the two would show up as a
 * visible seam at the tile boundary. It is also what makes the CPU form usable
 * as a test oracle for the accelerator form.
 *
 * <p>Keeping to integer arithmetic is what makes that achievable. Floating
 * point differs between devices — fused multiply-add, flush-to-zero, and the
 * precision of {@code sqrt} are all implementation-defined in OpenCL — so the
 * built-in kernels avoid it entirely.
 *
 * <p>Pixels are packed 0x00RRGGBB in row-major order.
 */
public interface ImageKernel {

    /** Kernel function name; must match the function in {@link #openClSource()}. */
    String name();

    /**
     * OpenCL C source defining {@code __kernel void <name>(__global const int*
     * in, __global int* out, int width, int height)}, indexed by a 2-D NDRange
     * over (width, height).
     */
    String openClSource();

    /** The reference implementation, and the fallback when no accelerator exists. */
    int[] applyOnCpu(int[] pixels, int width, int height);
}
