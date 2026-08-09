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

/**
 * The host CPU, through plain Java.
 *
 * <p>Always available and requires no native library, so it is the correctness
 * reference every other backend is checked against and the fallback when no
 * accelerator is present.
 */
public final class JavaCpuBackend implements AcceleratorBackend {

    @Override
    public Backend backend() {
        return Backend.JAVA_CPU;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String unavailableReason() {
        return "";
    }

    @Override
    public List<Device> devices() {
        Runtime runtime = Runtime.getRuntime();
        return List.of(new Device(
                Backend.JAVA_CPU,
                "cpu:0",
                System.getProperty("os.arch", "cpu") + " host CPU",
                System.getProperty("java.vendor", "Unknown vendor"),
                AcceleratorType.CPU,
                runtime.availableProcessors(),
                runtime.maxMemory()));
    }
}
