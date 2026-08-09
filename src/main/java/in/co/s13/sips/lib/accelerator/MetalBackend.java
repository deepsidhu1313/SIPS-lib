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
 * Apple Metal.
 *
 * <p>Not yet implemented. Relevant on Apple platforms, where Apple has
 * deprecated OpenCL in favour of Metal; on Apple Silicon it is also the only way
 * to reach the Neural Engine.
 *
 * <p>To implement: bind MTLCopyAllDevices through Panama FFM. Apple Silicon GPUs
 * are {@link AcceleratorType#INTEGRATED_GPU} since they share system memory.
 */
public final class MetalBackend extends UnavailableBackend {

    @Override
    public Backend backend() {
        return Backend.METAL;
    }

    @Override
    protected String probe() {
        if (!osContains("mac")) {
            return "Metal is available on Apple platforms only";
        }
        return "Metal present but the SIPS binding is not implemented yet; "
                + "OpenCL covers the same devices on this host";
    }
}
