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
 * NVIDIA CUDA.
 *
 * <p>Not yet implemented. The probe below reports precisely why, so an operator
 * can tell a missing driver apart from unsupported hardware.
 *
 * <p>To implement: add a JCuda or Panama FFM binding, enumerate with
 * {@code cuDeviceGetCount}/{@code cuDeviceGetAttribute}, and map every device to
 * {@link AcceleratorType#DISCRETE_GPU}. The suite in
 * {@code AcceleratorRegistryTest} is the contract it must satisfy.
 */
public final class CudaBackend extends UnavailableBackend {

    @Override
    public Backend backend() {
        return Backend.CUDA;
    }

    @Override
    protected String probe() {
        if (osContains("mac")) {
            return "NVIDIA has shipped no macOS driver since 10.13; CUDA cannot run on this host";
        }
        if (!anyFileExists("/usr/lib/x86_64-linux-gnu/libcuda.so", "/usr/lib64/libcuda.so",
                "/usr/local/cuda", "C:\\Windows\\System32\\nvcuda.dll")) {
            return "no CUDA driver found; install the NVIDIA driver and CUDA toolkit";
        }
        return "CUDA driver present but the SIPS binding is not implemented yet";
    }
}
