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

import java.util.Optional;

/**
 * Reads instantaneous power draw, so throughput can be expressed per watt.
 *
 * <p>Follows the same contract as {@link AcceleratorBackend}: implementations
 * must never throw, and one that cannot read power says why rather than
 * pretending. Power telemetry is unavailable far more often than not — it
 * usually needs a specific platform, and frequently elevated privileges — so
 * absence is the normal case, not an error.
 *
 * <p>Energy-aware scheduling only makes sense where this works; everywhere else
 * the scheduler must fall back to raw capability.
 */
public interface PowerMonitor {

    /** Human-readable name of the telemetry source. */
    String name();

    /** Whether power can be read on this host right now. */
    boolean isAvailable();

    /** Why power cannot be read, phrased for an operator. Empty when available. */
    String unavailableReason();

    /**
     * Current draw in watts.
     *
     * @return the reading, or empty when unavailable or momentarily unreadable
     */
    Optional<Double> readWatts();

    /** What the reading covers, since sources differ in scope. */
    Scope scope();

    /** The part of the machine a reading accounts for. */
    enum Scope {
        /** CPU package only, as Intel/AMD RAPL reports. */
        CPU_PACKAGE,
        /** A single accelerator, as nvidia-smi or rocm-smi reports. */
        DEVICE,
        /** The whole machine, as a battery or wall meter reports. */
        SYSTEM,
        /** Nothing is being measured. */
        NONE
    }
}
