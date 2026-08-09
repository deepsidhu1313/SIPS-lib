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
 * AMD ROCm.
 *
 * <p>Not yet implemented. ROCm is Linux-only, so this backend is permanently
 * unavailable on macOS and Windows.
 *
 * <p>To implement: bind HIP through Panama FFM and enumerate with
 * {@code hipGetDeviceCount}/{@code hipGetDeviceProperties}. Note that AMD APUs
 * should map to {@link AcceleratorType#APU}, not DISCRETE_GPU, because their
 * memory is shared with the host and transfer costs differ sharply.
 */
public final class RocmBackend extends UnavailableBackend {

    @Override
    public Backend backend() {
        return Backend.ROCM;
    }

    @Override
    protected String probe() {
        if (!osContains("linux")) {
            return "ROCm is supported on Linux only";
        }
        if (!anyFileExists("/opt/rocm", "/usr/lib/x86_64-linux-gnu/libamdhip64.so")) {
            return "no ROCm installation found under /opt/rocm";
        }
        return "ROCm present but the SIPS binding is not implemented yet";
    }
}
