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
 * Vulkan compute.
 *
 * <p>Not yet implemented. Attractive as a single cross-vendor path covering
 * Intel, AMD and NVIDIA, at the cost of considerably more setup than OpenCL.
 */
public final class VulkanBackend extends UnavailableBackend {

    @Override
    public Backend backend() {
        return Backend.VULKAN;
    }

    @Override
    protected String probe() {
        if (osContains("mac") && !anyFileExists("/usr/local/lib/libMoltenVK.dylib")) {
            return "Vulkan on macOS requires MoltenVK, which was not found";
        }
        return "Vulkan binding is not implemented yet";
    }
}
