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
 * Service provider interface for a compute runtime.
 *
 * <p>Implementations are discovered with {@link java.util.ServiceLoader}, so a
 * new backend can be added by dropping a jar on the classpath.
 *
 * <p><b>Contract.</b> Implementations must not throw from any method, including
 * when their native library is absent — a node has to boot on hardware where
 * most backends are missing. An unavailable backend returns an empty device
 * list and a non-blank {@link #unavailableReason()}. An available backend
 * returns at least one device it can genuinely execute on.
 */
public interface AcceleratorBackend {

    /** Which runtime this implements. */
    Backend backend();

    /** Whether this runtime can be used on this host right now. */
    boolean isAvailable();

    /**
     * Why the runtime cannot be used, phrased for an operator reading a log.
     * Empty when {@link #isAvailable()} is true.
     */
    String unavailableReason();

    /** Devices this runtime exposes; empty when unavailable. */
    List<Device> devices();
}
