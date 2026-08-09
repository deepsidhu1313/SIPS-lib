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

import java.util.Collection;

/**
 * Scores a node's hardware against a workload, so schedulers can place heavy
 * kernels on nodes that will actually run them faster.
 *
 * <p>Fitness is workload-dependent on purpose. Ranking purely by "most capable
 * device" would send a pointwise kernel to a discrete GPU and make it about
 * five times slower than leaving it on the CPU.
 */
public final class NodeFitness {

    private NodeFitness() {
    }

    /**
     * @param devices what a node advertises; may be null or empty for peers
     *                running a build without device discovery
     * @return a non-negative score, comparable only against other scores for
     *         the same workload
     */
    public static double score(Collection<Device> devices, WorkloadProfile workload) {
        double cpu = capacityOf(devices, AcceleratorType.CPU);

        if (!workload.prefersAccelerator()) {
            // Transfer-bound: the accelerator is irrelevant, so nodes are
            // ranked purely on CPU capacity and a GPU confers no advantage.
            return cpu;
        }
        double accelerator = bestAcceleratorCapacity(devices);
        // Weighted so that any real accelerator outranks CPU-only nodes, while
        // still separating stronger accelerators from weaker ones.
        return cpu + accelerator * 4.0;
    }

    /** Whether the node has a GPU or APU it could run kernels on. */
    public static boolean hasAccelerator(Collection<Device> devices) {
        if (devices == null) {
            return false;
        }
        return devices.stream().anyMatch(d -> d.type().isGpu());
    }

    private static double bestAcceleratorCapacity(Collection<Device> devices) {
        if (devices == null) {
            return 0;
        }
        return devices.stream()
                .filter(d -> d.type().isGpu())
                // Weight by class as well as capacity: a discrete GPU with its
                // own memory sustains more throughput than an integrated part
                // of the same nominal size.
                .mapToDouble(d -> normalise(d) * (1 + d.type().preference()))
                .max()
                .orElse(0);
    }

    private static double capacityOf(Collection<Device> devices, AcceleratorType type) {
        if (devices == null) {
            return 0;
        }
        return devices.stream()
                .filter(d -> d.type() == type)
                .mapToDouble(NodeFitness::normalise)
                .max()
                .orElse(0);
    }

    /** Compute units scaled by memory, in gibibytes, to keep scores small. */
    private static double normalise(Device device) {
        return device.computeUnits() * (device.globalMemoryBytes() / (double) (1L << 30));
    }
}
