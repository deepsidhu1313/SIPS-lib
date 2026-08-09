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
 * A runtime capable of executing kernels.
 *
 * <p>Every value here has a registered implementation, enforced by the
 * conformance suite. Most will report themselves unavailable on any given host;
 * that is expected and must never be an error.
 */
public enum Backend {

    /** Plain Java on the host CPU. No native dependency, always available. */
    JAVA_CPU("Java"),
    /** Portable across Intel, AMD and NVIDIA, integrated and discrete. */
    OPENCL("OpenCL"),
    /** NVIDIA only. */
    CUDA("CUDA"),
    /** AMD, Linux only. */
    ROCM("ROCm"),
    /** Apple platforms. */
    METAL("Metal"),
    /** Cross-vendor compute via Vulkan. */
    VULKAN("Vulkan"),
    /** Vendor NPU runtimes. */
    NPU_RUNTIME("NPU");

    private final String displayName;

    Backend(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
