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

import java.io.Serializable;

/**
 * How much arithmetic a job does per element, and therefore whether an
 * accelerator will help it.
 *
 * <p>Moving an image to a device and back costs the same whatever the kernel
 * does with it. That fixed cost is only worth paying when there is enough
 * arithmetic to amortise it. Measured on the reference machine at 2048x2048:
 *
 * <table border="1">
 * <caption>Speedup against the Java CPU reference</caption>
 * <tr><th>kernel</th><th>ops/pixel</th><th>discrete GPU</th></tr>
 * <tr><td>sobel</td><td>~30</td><td>13.3x</td></tr>
 * <tr><td>sharpen</td><td>~20</td><td>6.4x</td></tr>
 * <tr><td>blur3</td><td>~14</td><td>2.7x</td></tr>
 * <tr><td>invert</td><td>~2</td><td>0.6x</td></tr>
 * <tr><td>grayscale</td><td>~3</td><td>0.2x</td></tr>
 * </table>
 *
 * <p>The crossover sits between blur3 and grayscale, so the threshold is set at
 * ten operations per element. It is a heuristic drawn from one machine, and
 * deliberately conservative: guessing "GPU" wrongly costs about 5x, while
 * guessing "CPU" wrongly forgoes a speedup. The cheaper mistake is preferred.
 */
public final class WorkloadProfile implements Serializable {

    /**
     * Operations per element above which an accelerator is expected to win.
     * Between blur3 (~14, wins) and grayscale (~3, loses).
     */
    public static final int COMPUTE_BOUND_THRESHOLD = 10;

    private static final WorkloadProfile UNKNOWN = new WorkloadProfile(0, false);

    private final int opsPerElement;
    private final boolean known;

    private WorkloadProfile(int opsPerElement, boolean known) {
        this.opsPerElement = opsPerElement;
        this.known = known;
    }

    /**
     * @param opsPerElement rough arithmetic operations per pixel or element
     * @throws IllegalArgumentException if negative
     */
    public static WorkloadProfile of(int opsPerElement) {
        if (opsPerElement < 0) {
            throw new IllegalArgumentException("Operations per element cannot be negative: "
                    + opsPerElement);
        }
        return new WorkloadProfile(opsPerElement, true);
    }

    /** No information about the workload. */
    public static WorkloadProfile unknown() {
        return UNKNOWN;
    }

    /** The profile of a built-in kernel, from its declared cost. */
    public static WorkloadProfile forKernel(ImageKernel kernel) {
        return of(Kernels.opsPerPixel(kernel));
    }

    /**
     * Parses the {@code Workload} value from a job manifest.
     *
     * @throws IllegalArgumentException naming the accepted values
     */
    public static WorkloadProfile byName(String name) {
        String value = name == null ? "" : name.trim().toLowerCase();
        switch (value) {
            case "compute-bound":
                return of(COMPUTE_BOUND_THRESHOLD * 3);
            case "transfer-bound":
                return of(1);
            case "":
            case "unknown":
                return unknown();
            default:
                throw new IllegalArgumentException("Unknown workload profile: " + name
                        + ". Use compute-bound, transfer-bound or unknown.");
        }
    }

    public int opsPerElement() {
        return opsPerElement;
    }

    /** Whether the arithmetic is heavy enough to amortise a device transfer. */
    public boolean isComputeBound() {
        return known && opsPerElement >= COMPUTE_BOUND_THRESHOLD;
    }

    /**
     * Whether an accelerator should be preferred.
     *
     * <p>False when the workload is unknown: without evidence, the risk of a
     * 5x slowdown outweighs the missed speedup.
     */
    public boolean prefersAccelerator() {
        return isComputeBound();
    }

    @Override
    public String toString() {
        return known ? opsPerElement + " ops/element"
                + (isComputeBound() ? " (compute-bound)" : " (transfer-bound)")
                : "unknown workload";
    }
}
