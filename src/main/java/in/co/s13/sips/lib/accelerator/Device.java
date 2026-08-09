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

import java.io.Serializable;

/**
 * A compute device a node can offer to the cluster.
 *
 * <p>Serializable because nodes advertise their devices to peers, so schedulers
 * can place tiles on hardware that suits them.
 *
 * @param backend            runtime that exposed this device
 * @param id                 stable identifier, unique within a node
 * @param name               human-readable model name
 * @param vendor             device vendor
 * @param type               class of device
 * @param computeUnits       parallel execution units the runtime reports
 * @param globalMemoryBytes  device-visible memory
 */
public record Device(Backend backend, String id, String name, String vendor,
        AcceleratorType type, int computeUnits, long globalMemoryBytes) implements Serializable {

    public Device {
        if (backend == null || type == null) {
            throw new IllegalArgumentException("backend and type are required");
        }
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("device id is required");
        }
        name = (name == null || name.isBlank()) ? "Unknown device" : name.strip();
        vendor = (vendor == null || vendor.isBlank()) ? "Unknown vendor" : vendor.strip();
    }

    /** Rough capability score, used only to break ties between like devices. */
    public long capacityScore() {
        return (long) computeUnits * Math.max(1, globalMemoryBytes >> 20);
    }

    @Override
    public String toString() {
        return backend.displayName() + ":" + name + " (" + type + ", " + computeUnits
                + " CUs, " + (globalMemoryBytes >> 20) + " MB)";
    }
}
