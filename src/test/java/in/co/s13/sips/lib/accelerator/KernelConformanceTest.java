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
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every kernel must produce bit-identical output on every device.
 *
 * <p>This is the load-bearing test of the accelerator work. A distributed job
 * may place one tile on a GPU and its neighbour on a CPU; if the two disagree by
 * even one least-significant bit, the result is a visible seam at the tile
 * boundary — and a seam that only appears on heterogeneous clusters, which is
 * about the worst possible bug to diagnose.
 *
 * <p>The suite adapts to the host: on a machine with no OpenCL runtime the
 * cross-device comparisons have nothing to compare and pass trivially, while on
 * this development machine they exercise an Intel CPU, an Intel integrated GPU
 * and an AMD discrete GPU.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KernelConformanceTest {

    private static final int WIDTH = 97;
    private static final int HEIGHT = 61;

    private static int[] noise(long seed) {
        Random random = new Random(seed);
        int[] pixels = new int[WIDTH * HEIGHT];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = random.nextInt(0x01000000);
        }
        return pixels;
    }

    private static List<Device> openClDevices() {
        return AcceleratorRegistry.devicesOf(Backend.OPENCL);
    }

    @Test
    void everyKernelMatchesTheCpuReferenceOnEveryOpenClDevice() {
        int[] pixels = noise(1313);

        for (Device device : openClDevices()) {
            try (KernelExecutor gpu = Compute.on(device);
                 KernelExecutor cpu = new JavaKernelExecutor()) {

                if (!(gpu instanceof OpenCLKernelExecutor)) {
                    continue;   // could not be opened; Compute fell back
                }
                for (ImageKernel kernel : Kernels.all().values()) {
                    int[] expected = cpu.execute(kernel, pixels, WIDTH, HEIGHT);
                    int[] actual = gpu.execute(kernel, pixels, WIDTH, HEIGHT);

                    assertArrayEquals(expected, actual,
                            () -> "kernel '" + kernel.name() + "' differs on " + device
                            + " — a heterogeneous cluster would show a seam here");
                }
            }
        }
    }

    @Test
    void allOpenClDevicesAgreeWithEachOther() {
        List<Device> devices = openClDevices();
        if (devices.size() < 2) {
            return;
        }
        int[] pixels = noise(7);

        try (KernelExecutor first = Compute.on(devices.get(0))) {
            for (int i = 1; i < devices.size(); i++) {
                try (KernelExecutor other = Compute.on(devices.get(i))) {
                    for (ImageKernel kernel : Kernels.all().values()) {
                        assertArrayEquals(
                                first.execute(kernel, pixels, WIDTH, HEIGHT),
                                other.execute(kernel, pixels, WIDTH, HEIGHT),
                                "kernel '" + kernel.name() + "' differs between "
                                + devices.get(0) + " and " + devices.get(i));
                    }
                }
            }
        }
    }

    /**
     * Dimensions that are not multiples of any plausible work-group size, so
     * the guard against a rounded-up NDRange is actually exercised.
     */
    @Test
    void awkwardDimensionsDoNotWriteOutOfBounds() {
        int[][] shapes = {{1, 1}, {1, 257}, {257, 1}, {13, 7}, {101, 103}};

        for (Device device : openClDevices()) {
            try (KernelExecutor gpu = Compute.on(device);
                 KernelExecutor cpu = new JavaKernelExecutor()) {
                for (int[] shape : shapes) {
                    int w = shape[0];
                    int h = shape[1];
                    Random random = new Random(w * 31L + h);
                    int[] pixels = new int[w * h];
                    for (int i = 0; i < pixels.length; i++) {
                        pixels[i] = random.nextInt(0x01000000);
                    }
                    assertArrayEquals(
                            cpu.execute(Kernels.SOBEL, pixels, w, h),
                            gpu.execute(Kernels.SOBEL, pixels, w, h),
                            "sobel differs at " + w + "x" + h + " on " + device);
                }
            }
        }
    }

    @Test
    void cpuExecutorIsAlwaysAvailable() {
        try (KernelExecutor cpu = new JavaKernelExecutor()) {
            int[] out = cpu.execute(Kernels.INVERT, noise(3), WIDTH, HEIGHT);
            assertEquals(WIDTH * HEIGHT, out.length);
        }
    }

    @Test
    void bestDeviceProducesTheReferenceResult() {
        int[] pixels = noise(99);
        try (KernelExecutor best = Compute.best();
             KernelExecutor cpu = new JavaKernelExecutor()) {
            assertArrayEquals(cpu.execute(Kernels.GRAYSCALE, pixels, WIDTH, HEIGHT),
                    best.execute(Kernels.GRAYSCALE, pixels, WIDTH, HEIGHT));
        }
    }

    @Test
    void selectionAlwaysYieldsAUsableExecutor() {
        // A node must run its share of the work whatever hardware it has.
        for (AcceleratorType type : AcceleratorType.values()) {
            try (KernelExecutor executor = Compute.onType(type)) {
                assertTrue(executor.execute(Kernels.INVERT, new int[]{0xFFFFFF}, 1, 1).length == 1,
                        "no usable executor for " + type);
            }
        }
    }

    @Test
    void mismatchedBufferLengthIsRejected() {
        try (KernelExecutor cpu = new JavaKernelExecutor()) {
            assertThrows(IllegalArgumentException.class,
                    () -> cpu.execute(Kernels.INVERT, new int[10], 4, 4));
        }
    }

    @Test
    void unknownKernelNameNamesTheAlternatives() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> Kernels.byName("bilateral"));
        assertTrue(thrown.getMessage().contains("sobel"), thrown.getMessage());
    }

    @Test
    void kernelsAreLookedUpCaseInsensitively() {
        assertEquals(Kernels.SOBEL, Kernels.byName("SOBEL"));
        assertEquals(Kernels.BLUR3, Kernels.byName("blur3"));
    }

    @Test
    void usingAClosedExecutorIsRejected() {
        for (Device device : openClDevices()) {
            KernelExecutor executor = Compute.on(device);
            executor.close();
            if (executor instanceof OpenCLKernelExecutor) {
                assertThrows(IllegalStateException.class,
                        () -> executor.execute(Kernels.INVERT, new int[]{1}, 1, 1));
            }
            return;
        }
    }

    @Test
    void closingTwiceIsHarmless() {
        KernelExecutor executor = Compute.best();
        executor.close();
        executor.close();
    }
}
