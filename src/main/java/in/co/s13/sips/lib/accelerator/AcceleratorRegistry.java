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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Discovers the compute devices available on this node.
 *
 * <p>Backends are loaded once via {@link ServiceLoader} and cached: enumeration
 * touches native libraries and is far too expensive to repeat per task.
 */
public final class AcceleratorRegistry {

    private static final Logger LOG = Logger.getLogger(AcceleratorRegistry.class.getName());

    /** Discrete GPU beats integrated beats NPU beats CPU; capacity breaks ties. */
    private static final Comparator<Device> BY_CAPABILITY =
            Comparator.comparingInt((Device d) -> d.type().preference())
                    .thenComparingLong(Device::capacityScore);

    private static final List<AcceleratorBackend> BACKENDS = load();
    private static final List<Device> DEVICES = enumerate();

    private AcceleratorRegistry() {
    }

    private static List<AcceleratorBackend> load() {
        List<AcceleratorBackend> found = new ArrayList<>();
        try {
            ServiceLoader.load(AcceleratorBackend.class).forEach(found::add);
        } catch (ServiceConfigurationError error) {
            LOG.log(Level.WARNING, "Some accelerator backends could not be loaded", error);
        }
        return List.copyOf(found);
    }

    private static List<Device> enumerate() {
        List<Device> found = new ArrayList<>();
        for (AcceleratorBackend backend : BACKENDS) {
            try {
                if (backend.isAvailable()) {
                    found.addAll(backend.devices());
                } else {
                    LOG.log(Level.FINE, () -> backend.backend().displayName()
                            + " unavailable: " + backend.unavailableReason());
                }
            } catch (RuntimeException ex) {
                // A misbehaving backend must not stop the node from booting.
                LOG.log(Level.WARNING, "Backend " + backend.backend() + " failed during discovery", ex);
            }
        }
        return List.copyOf(found);
    }

    /** All registered backends, available or not. */
    public static List<AcceleratorBackend> backends() {
        return BACKENDS;
    }

    /** Every usable device on this node. */
    public static List<Device> devices() {
        return DEVICES;
    }

    public static List<Device> devicesOfType(AcceleratorType type) {
        return DEVICES.stream().filter(d -> d.type() == type).toList();
    }

    public static List<Device> devicesOf(Backend backend) {
        return DEVICES.stream().filter(d -> d.backend() == backend).toList();
    }

    /** The most capable device on this node. */
    public static Optional<Device> bestDevice() {
        return best(DEVICES);
    }

    /** The most capable of the given devices. */
    public static Optional<Device> best(List<Device> candidates) {
        return candidates.stream().max(BY_CAPABILITY);
    }

    /** One line per device, for startup logs and the admin API. */
    public static String describe() {
        if (DEVICES.isEmpty()) {
            return "No compute devices detected";
        }
        return DEVICES.stream().map(Device::toString).collect(Collectors.joining("\n"));
    }

    /** Why each unavailable backend is unavailable, for troubleshooting. */
    public static String describeUnavailable() {
        return BACKENDS.stream()
                .filter(b -> !b.isAvailable())
                .map(b -> b.backend().displayName() + ": " + b.unavailableReason())
                .collect(Collectors.joining("\n"));
    }
}
