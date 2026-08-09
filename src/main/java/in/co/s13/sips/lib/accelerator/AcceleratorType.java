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
 * Class of compute device, ordered by how strongly SIPS prefers it for
 * data-parallel image work.
 *
 * <p>The ordering is a default, not a rule: a small kernel over a small image
 * can be slower on a discrete GPU than on the CPU once transfer cost is counted.
 */
public enum AcceleratorType {

    /** Always present. The correctness reference and the universal fallback. */
    CPU(0),
    /** Neural processing unit. Excellent for inference, narrow otherwise. */
    NPU(1),
    /** GPU sharing system memory: Intel iGPU, Apple Silicon, AMD APU graphics. */
    INTEGRATED_GPU(2),
    /** CPU and GPU on one die with a unified memory pool. */
    APU(2),
    /** Reconfigurable logic. */
    FPGA(3),
    /** GPU with its own memory. Highest throughput, highest transfer cost. */
    DISCRETE_GPU(4),
    /** Reported by a backend but not classifiable. */
    OTHER(0);

    private final int preference;

    AcceleratorType(int preference) {
        this.preference = preference;
    }

    /** Higher wins when choosing a device. */
    public int preference() {
        return preference;
    }

    public boolean isGpu() {
        return this == INTEGRATED_GPU || this == DISCRETE_GPU || this == APU;
    }
}
