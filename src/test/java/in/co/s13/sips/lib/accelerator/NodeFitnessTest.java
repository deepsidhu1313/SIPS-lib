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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Matching work to hardware.
 *
 * <p>Scheduling heavy kernels onto GPU nodes only pays off when the work is
 * actually compute-bound. Measured on the reference machine at 2048x2048,
 * sobel runs 13.3x faster on a discrete GPU than on the CPU, while grayscale
 * runs 5x <em>slower</em> there — the copy to the device and back costs more
 * than the arithmetic saves.
 *
 * <p>So a scheduler that simply prefers the most capable device makes cheap
 * kernels slower. Fitness has to depend on the workload, which is what these
 * tests pin down.
 */
class NodeFitnessTest {

    private static final Device CPU = new Device(Backend.JAVA_CPU, "cpu:0", "host CPU",
            "Test", AcceleratorType.CPU, 12, 16L << 30);
    private static final Device IGPU = new Device(Backend.OPENCL, "opencl:1", "UHD 630",
            "Intel", AcceleratorType.INTEGRATED_GPU, 24, 1536L << 20);
    private static final Device DGPU = new Device(Backend.OPENCL, "opencl:2", "Radeon Pro 5300M",
            "AMD", AcceleratorType.DISCRETE_GPU, 20, 4L << 30);

    // ---- workload classification ----

    @Test
    void heavyKernelsAreComputeBound() {
        assertTrue(WorkloadProfile.of(30).isComputeBound());
        assertTrue(WorkloadProfile.of(30).prefersAccelerator());
    }

    @Test
    void cheapKernelsAreTransferBound() {
        // grayscale does about three operations per pixel and loses on a GPU.
        assertFalse(WorkloadProfile.of(3).isComputeBound());
        assertFalse(WorkloadProfile.of(3).prefersAccelerator());
    }

    @Test
    void theBuiltInKernelsClassifyAsMeasured() {
        // Mirrors the benchmark: sobel/sharpen/blur3 win on a GPU, the
        // pointwise pair does not.
        assertTrue(WorkloadProfile.forKernel(Kernels.SOBEL).prefersAccelerator());
        assertTrue(WorkloadProfile.forKernel(Kernels.SHARPEN).prefersAccelerator());
        assertTrue(WorkloadProfile.forKernel(Kernels.BLUR3).prefersAccelerator());
        assertFalse(WorkloadProfile.forKernel(Kernels.GRAYSCALE).prefersAccelerator());
        assertFalse(WorkloadProfile.forKernel(Kernels.INVERT).prefersAccelerator());
    }

    @Test
    void anUnknownWorkloadDefaultsToNoPreference() {
        // Without information, do not gamble on a GPU: the downside of guessing
        // wrong (5x slower) exceeds the upside of guessing right.
        assertFalse(WorkloadProfile.unknown().prefersAccelerator());
    }

    @Test
    void profilesCanBeNamedInAManifest() {
        assertTrue(WorkloadProfile.byName("compute-bound").prefersAccelerator());
        assertFalse(WorkloadProfile.byName("transfer-bound").prefersAccelerator());
        assertFalse(WorkloadProfile.byName("unknown").prefersAccelerator());
    }

    @Test
    void anUnrecognisedProfileNameNamesTheAlternatives() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> WorkloadProfile.byName("gpu-please"));
        assertTrue(thrown.getMessage().contains("compute-bound"), thrown.getMessage());
    }

    @Test
    void negativeIntensityIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> WorkloadProfile.of(-1));
    }

    // ---- node fitness ----

    @Test
    void computeBoundWorkPrefersTheNodeWithADiscreteGpu() {
        double gpuNode = NodeFitness.score(List.of(CPU, DGPU), WorkloadProfile.of(30));
        double cpuNode = NodeFitness.score(List.of(CPU), WorkloadProfile.of(30));

        assertTrue(gpuNode > cpuNode,
                "a GPU node should outrank a CPU-only node for heavy work");
    }

    @Test
    void transferBoundWorkDoesNotPreferAGpuNode() {
        double gpuNode = NodeFitness.score(List.of(CPU, DGPU), WorkloadProfile.of(3));
        double cpuNode = NodeFitness.score(List.of(CPU), WorkloadProfile.of(3));

        assertEquals(cpuNode, gpuNode, 0.0001,
                "for cheap kernels the GPU is irrelevant, so nodes should tie on CPU capacity");
    }

    @Test
    void discreteGpuOutranksIntegratedForHeavyWork() {
        assertTrue(NodeFitness.score(List.of(CPU, DGPU), WorkloadProfile.of(30))
                > NodeFitness.score(List.of(CPU, IGPU), WorkloadProfile.of(30)));
    }

    @Test
    void aNodeWithNoDevicesStillScores() {
        // Peers running an older build advertise nothing. They must remain
        // schedulable, just not preferred.
        double none = NodeFitness.score(List.of(), WorkloadProfile.of(30));
        assertTrue(none >= 0, "must not be negative");
        assertTrue(none < NodeFitness.score(List.of(CPU, DGPU), WorkloadProfile.of(30)));
    }

    @Test
    void nullDeviceListIsTreatedAsNoDevices() {
        assertEquals(NodeFitness.score(List.of(), WorkloadProfile.of(30)),
                NodeFitness.score(null, WorkloadProfile.of(30)), 0.0001);
    }

    @Test
    void moreCapableGpusScoreHigher() {
        Device small = new Device(Backend.OPENCL, "opencl:9", "small", "T",
                AcceleratorType.DISCRETE_GPU, 4, 1L << 30);
        assertTrue(NodeFitness.score(List.of(CPU, DGPU), WorkloadProfile.of(30))
                > NodeFitness.score(List.of(CPU, small), WorkloadProfile.of(30)));
    }

    @Test
    void hasAcceleratorReportsUsableGpuPresence() {
        assertTrue(NodeFitness.hasAccelerator(List.of(CPU, IGPU)));
        assertTrue(NodeFitness.hasAccelerator(List.of(CPU, DGPU)));
        assertFalse(NodeFitness.hasAccelerator(List.of(CPU)));
        assertFalse(NodeFitness.hasAccelerator(List.of()));
        assertFalse(NodeFitness.hasAccelerator(null));
    }
}
