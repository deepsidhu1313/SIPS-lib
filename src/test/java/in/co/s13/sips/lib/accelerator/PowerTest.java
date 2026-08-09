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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Performance per watt.
 *
 * <p>The arithmetic is tested exactly; the telemetry sources are tested for the
 * contract they must honour. Power is unreadable on most hosts — it needs a
 * specific platform and often root — so the suite asserts that absence is
 * reported honestly rather than assuming any source works here.
 */
class PowerTest {

    // ---- the efficiency arithmetic ----

    @Test
    void computesThroughputAndEfficiency() {
        // A million elements in one second at 10 W: 1 MPix/s, 10 J, 0.1 MPix/J.
        PowerReading reading = new PowerReading(1_000_000, 1000.0, 10.0);

        assertEquals(1.0, reading.throughputMillionsPerSecond(), 0.0001);
        assertEquals(10.0, reading.joules(), 0.0001);
        assertEquals(0.1, reading.millionsPerJoule(), 0.0001);
    }

    @Test
    void halvingPowerDoublesEfficiencyAtTheSameSpeed() {
        PowerReading thirsty = new PowerReading(1_000_000, 1000.0, 20.0);
        PowerReading frugal = new PowerReading(1_000_000, 1000.0, 10.0);

        assertEquals(thirsty.throughputMillionsPerSecond(),
                frugal.throughputMillionsPerSecond(), 0.0001);
        assertEquals(2.0, frugal.millionsPerJoule() / thirsty.millionsPerJoule(), 0.0001);
    }

    /**
     * The case this guards: a device twice as fast but three times as thirsty
     * is the *worse* choice on a power budget, and only this figure shows it.
     */
    @Test
    void afasterDeviceCanBeLessEfficient() {
        PowerReading gpu = new PowerReading(2_000_000, 1000.0, 150.0);
        PowerReading cpu = new PowerReading(1_000_000, 1000.0, 25.0);

        assertTrue(gpu.throughputMillionsPerSecond() > cpu.throughputMillionsPerSecond());
        assertTrue(cpu.millionsPerJoule() > gpu.millionsPerJoule(),
                "the CPU should win on efficiency despite being slower");
    }

    @Test
    void withoutAPowerReadingEfficiencyIsZeroRatherThanInfinite() {
        PowerReading timingOnly = new PowerReading(1_000_000, 1000.0, 0.0);

        assertFalse(timingOnly.hasPowerData());
        assertEquals(0.0, timingOnly.millionsPerJoule(), 0.0001,
                "dividing by zero watts would otherwise report infinite efficiency");
        assertEquals(1.0, timingOnly.throughputMillionsPerSecond(), 0.0001,
                "throughput is still meaningful without power");
    }

    @Test
    void rejectsNonsenseInputs() {
        assertThrows(IllegalArgumentException.class, () -> new PowerReading(-1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new PowerReading(1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new PowerReading(1, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new PowerReading(1, 1, -1));
    }

    // ---- the telemetry contract ----

    @Test
    void everyMonitorEitherWorksOrExplainsWhyNot() {
        for (PowerMonitor monitor : PowerMonitors.all()) {
            assertNotNull(monitor.name());
            assertNotNull(monitor.scope());

            if (monitor.isAvailable()) {
                assertEquals("", monitor.unavailableReason());
            } else {
                assertFalse(monitor.unavailableReason().isBlank(),
                        monitor.name() + " is unavailable but gives no reason");
                assertTrue(monitor.readWatts().isEmpty(),
                        monitor.name() + " is unavailable but returned a reading");
            }
        }
    }

    @Test
    void noMonitorThrows() {
        // A node must boot where no telemetry exists, which is most of them.
        for (PowerMonitor monitor : PowerMonitors.all()) {
            monitor.isAvailable();
            monitor.unavailableReason();
            monitor.readWatts();
            monitor.scope();
        }
    }

    @Test
    void selectionAlwaysYieldsAMonitor() {
        PowerMonitor best = PowerMonitors.best();
        assertNotNull(best);
        // On a host with no source this is the one that says so.
        assertNotNull(best.unavailableReason());
    }

    @Test
    void aReadingIsPlausibleWhenOneIsAvailable() {
        for (PowerMonitor monitor : PowerMonitors.all()) {
            monitor.readWatts().ifPresent(watts -> assertTrue(watts >= 0 && watts < 10_000,
                    monitor.name() + " reported an implausible " + watts + " W"));
        }
    }

    @Test
    void parsesTheFirstNumberFromVendorToolOutput() {
        // nvidia-smi and rocm-smi differ in layout; the draw is the first number.
        assertEquals(42.5,
                PowerMonitors.CommandMonitor.firstNumber("42.50\n").orElseThrow(), 0.0001);
        assertEquals(101.0,
                PowerMonitors.CommandMonitor.firstNumber("card0, 101 W\n").orElseThrow(), 0.0001);
        assertTrue(PowerMonitors.CommandMonitor.firstNumber("N/A").isEmpty());
        assertTrue(PowerMonitors.CommandMonitor.firstNumber(null).isEmpty());
    }
}
