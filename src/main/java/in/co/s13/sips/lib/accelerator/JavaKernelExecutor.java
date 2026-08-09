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
 * Runs kernels on the CPU through their Java form.
 *
 * <p>Always available, so it is both the fallback when no accelerator is
 * present and the oracle the accelerator executors are checked against.
 */
public final class JavaKernelExecutor implements KernelExecutor {

    private final Device device;

    public JavaKernelExecutor() {
        this(AcceleratorRegistry.devicesOfType(AcceleratorType.CPU).stream()
                .filter(d -> d.backend() == Backend.JAVA_CPU)
                .findFirst()
                .orElseGet(() -> new Device(Backend.JAVA_CPU, "cpu:0", "host CPU", "",
                        AcceleratorType.CPU, Runtime.getRuntime().availableProcessors(),
                        Runtime.getRuntime().maxMemory())));
    }

    public JavaKernelExecutor(Device device) {
        this.device = device;
    }

    @Override
    public Device device() {
        return device;
    }

    @Override
    public int[] execute(ImageKernel kernel, int[] pixels, int width, int height) {
        if (pixels.length != width * height) {
            throw new IllegalArgumentException("Buffer is " + pixels.length
                    + " but " + width + "x" + height + " needs " + width * height);
        }
        return kernel.applyOnCpu(pixels, width, height);
    }

    @Override
    public void close() {
        // No native resources.
    }
}
