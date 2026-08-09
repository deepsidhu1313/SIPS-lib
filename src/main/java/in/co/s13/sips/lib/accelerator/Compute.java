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

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point for running kernels.
 *
 * <pre>{@code
 * try (KernelExecutor executor = Compute.best()) {
 *     int[] out = executor.execute(Kernels.SOBEL, pixels, width, height);
 * }
 * }</pre>
 *
 * <p>Selection never fails: if no accelerator can be opened, the CPU executor
 * is returned. A node must be able to run its share of the work regardless of
 * what hardware it has, and silently doing so is better than refusing the job.
 */
public final class Compute {

    private static final Logger LOG = Logger.getLogger(Compute.class.getName());

    private Compute() {
    }

    /** An executor for the most capable device on this node. */
    public static KernelExecutor best() {
        return AcceleratorRegistry.bestDevice()
                .map(Compute::on)
                .orElseGet(JavaKernelExecutor::new);
    }

    /**
     * An executor for a specific device, falling back to the CPU if that device
     * cannot be opened.
     */
    public static KernelExecutor on(Device device) {
        if (device.backend() == Backend.OPENCL) {
            try {
                return new OpenCLKernelExecutor(device);
            } catch (IllegalStateException | IllegalArgumentException ex) {
                LOG.log(Level.WARNING, "Falling back to CPU; could not open " + device, ex);
                // Deliberately not carrying the requested device through: the
                // work is running on the CPU, and device() must say so rather
                // than name hardware that is not being used.
                return new JavaKernelExecutor();
            }
        }
        return new JavaKernelExecutor(device);
    }

    /**
     * An executor for the fastest device of a given type, or the CPU if this
     * node has none of that type.
     */
    public static KernelExecutor onType(AcceleratorType type) {
        return AcceleratorRegistry.best(AcceleratorRegistry.devicesOfType(type))
                .map(Compute::on)
                .orElseGet(JavaKernelExecutor::new);
    }
}
