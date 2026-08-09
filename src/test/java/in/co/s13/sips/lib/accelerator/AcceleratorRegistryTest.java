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
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Discovery and selection of compute devices.
 *
 * <p>This is the conformance suite every backend must satisfy. A backend is
 * allowed to be unavailable — most will be, on any given host — but it must say
 * so honestly rather than throwing, and it must never advertise devices it
 * cannot actually use.
 */
class AcceleratorRegistryTest {

    @Test
    void alwaysFindsAtLeastTheCpuBackend() {
        // The CPU backend has no native dependency, so a node can always fall
        // back to it. If this fails, scheduling has nothing to target.
        List<Device> devices = AcceleratorRegistry.devices();
        assertFalse(devices.isEmpty(), "no compute devices discovered at all");
        assertTrue(devices.stream().anyMatch(d -> d.type() == AcceleratorType.CPU),
                "a CPU device must always be available");
    }

    /**
     * The conformance contract, applied to every registered backend.
     */
    @Test
    void everyBackendEitherWorksOrExplainsWhyNot() {
        for (AcceleratorBackend backend : AcceleratorRegistry.backends()) {
            assertNotNull(backend.backend(), "backend() must not be null");

            if (backend.isAvailable()) {
                List<Device> devices = backend.devices();
                assertNotNull(devices, backend + " returned null devices");
                assertFalse(devices.isEmpty(),
                        backend + " claims to be available but exposes no devices");
                for (Device device : devices) {
                    assertEquals(backend.backend(), device.backend(),
                            "device must report the backend that produced it");
                    assertFalse(device.name().isBlank(), "device name must not be blank");
                    assertTrue(device.computeUnits() > 0,
                            device + " must report at least one compute unit");
                    assertTrue(device.globalMemoryBytes() > 0,
                            device + " must report its memory");
                }
            } else {
                assertFalse(backend.unavailableReason().isBlank(),
                        backend + " is unavailable but gives no reason");
                assertTrue(backend.devices().isEmpty(),
                        backend + " is unavailable but still lists devices");
            }
        }
    }

    @Test
    void unavailableBackendsNeverThrow() {
        // A node must boot on hardware where most backends are missing.
        for (AcceleratorBackend backend : AcceleratorRegistry.backends()) {
            backend.isAvailable();
            backend.devices();
            backend.unavailableReason();
        }
    }

    @Test
    void everyDeclaredBackendIsRegistered() {
        // A backend that is not registered is invisible, which looks identical
        // to it being unsupported. Catch that at build time.
        for (Backend declared : Backend.values()) {
            assertTrue(AcceleratorRegistry.backends().stream()
                    .anyMatch(b -> b.backend() == declared),
                    "no implementation registered for " + declared);
        }
    }

    @Test
    void deviceIdsAreUnique() {
        List<String> ids = AcceleratorRegistry.devices().stream().map(Device::id).toList();
        assertEquals(ids.size(), ids.stream().distinct().count(),
                "device ids must be unique so scheduling can address them: " + ids);
    }

    @Test
    void selectsTheMostCapableDeviceAvailable() {
        Optional<Device> best = AcceleratorRegistry.bestDevice();
        assertTrue(best.isPresent(), "there is always at least a CPU");
    }

    @Test
    void prefersDiscreteGpuOverIntegratedOverCpu() {
        Device cpu = new Device(Backend.JAVA_CPU, "cpu", "CPU", "Test",
                AcceleratorType.CPU, 8, 16L << 30);
        Device igpu = new Device(Backend.OPENCL, "igpu", "iGPU", "Test",
                AcceleratorType.INTEGRATED_GPU, 24, 1536L << 20);
        Device dgpu = new Device(Backend.OPENCL, "dgpu", "dGPU", "Test",
                AcceleratorType.DISCRETE_GPU, 20, 4L << 30);

        assertEquals(dgpu, AcceleratorRegistry.best(List.of(cpu, igpu, dgpu)).orElseThrow());
        assertEquals(igpu, AcceleratorRegistry.best(List.of(cpu, igpu)).orElseThrow());
        assertEquals(cpu, AcceleratorRegistry.best(List.of(cpu)).orElseThrow());
    }

    @Test
    void breaksTiesOnComputeUnits() {
        Device small = new Device(Backend.OPENCL, "a", "Small", "Test",
                AcceleratorType.DISCRETE_GPU, 10, 4L << 30);
        Device large = new Device(Backend.OPENCL, "b", "Large", "Test",
                AcceleratorType.DISCRETE_GPU, 40, 4L << 30);

        assertEquals(large, AcceleratorRegistry.best(List.of(small, large)).orElseThrow());
    }

    @Test
    void selectingFromNothingYieldsEmptyRatherThanThrowing() {
        assertTrue(AcceleratorRegistry.best(List.of()).isEmpty());
    }

    @Test
    void filtersByType() {
        List<Device> cpus = AcceleratorRegistry.devicesOfType(AcceleratorType.CPU);
        assertFalse(cpus.isEmpty());
        assertTrue(cpus.stream().allMatch(d -> d.type() == AcceleratorType.CPU));
    }

    @Test
    void describesTheHostForOperators() {
        String summary = AcceleratorRegistry.describe();
        assertNotNull(summary);
        assertTrue(summary.contains("CPU"), "summary should mention the CPU device");
    }
}
