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
package in.co.s13.sips.lib.ml;

import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether a device should be given work at all.
 *
 * <p>A cluster of servers has one answer: yes, that is what they are for. A
 * cluster that includes someone's phone does not. Training on a stranger's
 * battery, or their mobile data, or a handset already too hot to hold, is a
 * cost the framework imposes on a person rather than on a machine — and the
 * device will thermally throttle anyway, so the work is slow as well as rude.
 *
 * <p>The rule that matters most is what happens when the answer is unknown.
 * A battery reading that cannot be had must count as unfit, not as fine: the
 * failure being guarded against is exactly the one where the device cannot say
 * how it is doing.
 */
class WorkerEligibilityTest {

    private static final WorkerEligibility.Policy DEFAULTS = WorkerEligibility.Policy.defaults();

    @Test
    void aPluggedInMachineIsAlwaysFit() {
        // A desktop or a server: no battery, nothing to protect.
        WorkerEligibility.Report report = WorkerEligibility.of(WorkerEligibility.Reading.mains(30.0), DEFAULTS);

        assertTrue(report.fit());
        assertEquals(Optional.empty(), report.refusal());
    }

    @Test
    void aPhoneWithPlentyOfBatteryIsFit() {
        assertTrue(WorkerEligibility.of(WorkerEligibility.Reading.onBattery(80, 30.0), DEFAULTS).fit());
    }

    @Test
    void aPhoneBelowTheBatteryFloorIsRefused() {
        WorkerEligibility.Report report =
                WorkerEligibility.of(WorkerEligibility.Reading.onBattery(12, 30.0), DEFAULTS);

        assertFalse(report.fit());
        assertTrue(report.refusal().orElseThrow().contains("battery"),
                report.refusal().orElseThrow());
    }

    @Test
    void aHotDeviceIsRefusedEvenOnMains() {
        // Thermal is not about politeness: a throttled device produces a slow
        // shard, and a round ends when its slowest worker ends. Refusing it is
        // faster than accepting it.
        WorkerEligibility.Report report = WorkerEligibility.of(WorkerEligibility.Reading.mains(52.0), DEFAULTS);

        assertFalse(report.fit());
        assertTrue(report.refusal().orElseThrow().toLowerCase().contains("temperature"),
                report.refusal().orElseThrow());
    }

    @Test
    void anUnreadableBatteryFailsClosed() {
        // The rule this class exists for. A device that cannot report its
        // battery is exactly the device whose battery should not be assumed
        // fine -- and on Android a broken reading is a real, observed
        // condition, not a hypothetical.
        WorkerEligibility.Report report = WorkerEligibility.of(
                WorkerEligibility.Reading.unknownBattery(30.0), DEFAULTS);

        assertFalse(report.fit());
        assertTrue(report.refusal().orElseThrow().contains("could not"),
                report.refusal().orElseThrow());
    }

    @Test
    void aNegativeBatteryReadingIsNotAReading() {
        // Observed on Android: the battery line reports -1 rather than
        // failing, so a naive floor check reads it as "below 30" or, worse, a
        // naive percentage check reads it as valid.
        assertFalse(WorkerEligibility.of(WorkerEligibility.Reading.onBattery(-1, 30.0), DEFAULTS).fit());
    }

    @Test
    void anImpossiblePercentageIsNotAReading() {
        assertFalse(WorkerEligibility.of(WorkerEligibility.Reading.onBattery(140, 30.0), DEFAULTS).fit());
    }

    @Test
    void anUnreadableTemperatureIsAcceptedOnMains() {
        // Deliberately different from battery. Plenty of ordinary servers
        // expose no thermal sensor, and refusing every one of them would make
        // the check useless where it is needed least.
        assertTrue(WorkerEligibility.of(WorkerEligibility.Reading.unknownTemperature(), DEFAULTS).fit());
    }

    @Test
    void aPolicyCanDemandMainsPower() {
        // For a long training run: a phone that is charging will still be
        // there in an hour, and one on battery probably will not.
        WorkerEligibility.Policy plugged = new WorkerEligibility.Policy(30, 45.0, true);

        assertFalse(WorkerEligibility.of(WorkerEligibility.Reading.onBattery(95, 25.0), plugged).fit());
        assertTrue(WorkerEligibility.of(WorkerEligibility.Reading.mains(25.0), plugged).fit());
    }

    @Test
    void theRefusalSaysWhichLimitWasHit() {
        // A node that is quietly never scheduled is much harder to debug than
        // one that says why.
        String refusal = WorkerEligibility.of(WorkerEligibility.Reading.onBattery(5, 30.0), DEFAULTS)
                .refusal().orElseThrow();

        assertTrue(refusal.contains("5"), refusal);
        assertTrue(refusal.contains("30"), refusal);
    }

    @Test
    void unfitNodesAreDroppedFromASpeedMap() {
        // What a scheduler actually calls: ShardPlan wants speeds, and an
        // unfit node must not merely get a small shard, it must get none.
        java.util.Map<String, Double> speeds = new java.util.LinkedHashMap<>();
        speeds.put("server", 2.0);
        speeds.put("hot-phone", 1.0);
        java.util.Map<String, WorkerEligibility.Reading> readings = new java.util.LinkedHashMap<>();
        readings.put("server", WorkerEligibility.Reading.mains(30.0));
        readings.put("hot-phone", WorkerEligibility.Reading.onBattery(90, 60.0));

        java.util.Map<String, Double> eligible =
                WorkerEligibility.eligible(speeds, readings, DEFAULTS);

        assertEquals(java.util.Set.of("server"), eligible.keySet());
    }

    @Test
    void aNodeWithNoReadingAtAllIsTreatedAsAServer() {
        // Most of the cluster is machines that have never reported a battery,
        // and a framework that refused all of them would refuse everything.
        java.util.Map<String, Double> speeds = java.util.Map.of("old-node", 1.0);

        assertEquals(1, WorkerEligibility.eligible(speeds, java.util.Map.of(), DEFAULTS).size());
    }
}
