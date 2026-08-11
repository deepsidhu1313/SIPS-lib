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
        WorkerEligibility.Policy plugged =
                new WorkerEligibility.Policy(30, 45.0, true, false, WorkerEligibility.ThermalLevel.FAIR);

        assertFalse(WorkerEligibility.of(WorkerEligibility.Reading.onBattery(95, 25.0), plugged).fit());
        assertTrue(WorkerEligibility.of(WorkerEligibility.Reading.mains(25.0), plugged).fit());
    }

    @Test
    void iosHasNoCelsiusOnlyAFourLevelThermalState() {
        // iOS deliberately does not expose a raw temperature to apps -- only
        // ProcessInfo.thermalState, a coarse .nominal/.fair/.serious/.critical
        // enum. Fabricating a Celsius number to fit the existing shape would
        // misrepresent what the device actually reported, so this is a
        // genuinely separate reading shape, not a unit conversion.
        WorkerEligibility.Reading cool = WorkerEligibility.Reading
                .onBatteryWithThermalLevel(80, WorkerEligibility.ThermalLevel.NOMINAL);

        assertTrue(WorkerEligibility.of(cool, DEFAULTS).fit());
    }

    @Test
    void aSeriousOrWorseThermalStateIsRefused() {
        WorkerEligibility.Report serious = WorkerEligibility.of(WorkerEligibility.Reading
                .mainsWithThermalLevel(WorkerEligibility.ThermalLevel.SERIOUS), DEFAULTS);
        WorkerEligibility.Report critical = WorkerEligibility.of(WorkerEligibility.Reading
                .mainsWithThermalLevel(WorkerEligibility.ThermalLevel.CRITICAL), DEFAULTS);

        assertFalse(serious.fit());
        assertTrue(serious.refusal().orElseThrow().toLowerCase().contains("thermal"),
                serious.refusal().orElseThrow());
        assertFalse(critical.fit());
    }

    @Test
    void nominalAndFairThermalStatesAreFit() {
        assertTrue(WorkerEligibility.of(WorkerEligibility.Reading
                .mainsWithThermalLevel(WorkerEligibility.ThermalLevel.NOMINAL), DEFAULTS).fit());
        assertTrue(WorkerEligibility.of(WorkerEligibility.Reading
                .mainsWithThermalLevel(WorkerEligibility.ThermalLevel.FAIR), DEFAULTS).fit());
    }

    @Test
    void aPolicyCanSetItsOwnThermalCeiling() {
        // A short, light job might tolerate SERIOUS; a long training round
        // should not even risk it. This is a policy choice, like the battery
        // floor and the mains requirement beside it.
        WorkerEligibility.Policy tolerant = new WorkerEligibility.Policy(
                30, 45.0, false, true, WorkerEligibility.ThermalLevel.SERIOUS);

        assertTrue(WorkerEligibility.of(WorkerEligibility.Reading
                .mainsWithThermalLevel(WorkerEligibility.ThermalLevel.SERIOUS), tolerant).fit());
        assertFalse(WorkerEligibility.of(WorkerEligibility.Reading
                .mainsWithThermalLevel(WorkerEligibility.ThermalLevel.CRITICAL), tolerant).fit());
    }

    @Test
    void celsiusTakesPriorityWhenSomehowBothArePresent() {
        // Should not happen from any one platform's real announcement, but
        // the precedence needs to be defined rather than accidental: a
        // reading built from a Celsius factory has no thermalLevel to
        // conflict with it, so this pins that the Celsius path is untouched
        // by the new one rather than testing an actually-reachable ambiguity.
        assertFalse(WorkerEligibility.of(WorkerEligibility.Reading.mains(52.0), DEFAULTS).fit());
    }

    @Test
    void anAbsentThermalLevelIsAcceptedJustLikeAbsentCelsius() {
        // Same asymmetry as temperature: most platforms report neither
        // Celsius nor a discrete level, and refusing all of them would make
        // the check useless where it is needed least. unknownTemperature()
        // already covers this shape; this test names the new field
        // explicitly so the coverage is not accidental.
        assertTrue(WorkerEligibility.of(WorkerEligibility.Reading.unknownTemperature(), DEFAULTS).fit());
    }

    @Test
    void aDeviceActivelyInUseIsRefusedUnderTheDefaultPolicy() {
        // JPPF's "Idle Host" mode does this for any node, not only phones:
        // stealing cycles from someone actively at the keyboard is rude
        // whichever way the device is powered, so this is checked regardless
        // of mains vs battery.
        WorkerEligibility.Report report = WorkerEligibility.of(
                WorkerEligibility.Reading.mains(25.0).activelyInUse(), DEFAULTS);

        assertFalse(report.fit());
        assertTrue(report.refusal().orElseThrow().contains("use"),
                report.refusal().orElseThrow());
    }

    @Test
    void aDeviceConfirmedIdleIsFit() {
        WorkerEligibility.Report report = WorkerEligibility.of(
                WorkerEligibility.Reading.onBattery(80, 25.0).confirmedIdle(), DEFAULTS);

        assertTrue(report.fit());
    }

    @Test
    void unknownActiveUseDoesNotRefuse() {
        // Unlike battery, most platforms -- desktops, older phones, this
        // library's own existing Reading factories -- have never reported
        // this signal at all. Treating "did not say" as "refuse" here would
        // refuse nearly everyone and defeat the point: unlike battery, there
        // is no cheap universal way for a device to know, so silence is not
        // suspicious the way a missing battery reading is.
        assertTrue(WorkerEligibility.of(
                WorkerEligibility.Reading.onBattery(80, 25.0), DEFAULTS).fit());
        assertTrue(WorkerEligibility.of(
                WorkerEligibility.Reading.mains(25.0), DEFAULTS).fit());
    }

    @Test
    void aPolicyCanAllowWorkWhileInUse() {
        // Some jobs are light enough that running alongside the owner is
        // fine; the check is a policy choice, not a hard law.
        WorkerEligibility.Policy tolerant = new WorkerEligibility.Policy(
                30, 45.0, false, false, WorkerEligibility.ThermalLevel.FAIR);

        assertTrue(WorkerEligibility.of(
                WorkerEligibility.Reading.mains(25.0).activelyInUse(), tolerant).fit());
    }

    @Test
    void activeUseIsCheckedEvenOnMains() {
        // The mains check already short-circuits straight to fit(); this
        // pins that active-use is judged before that short-circuit fires,
        // not skipped by it.
        assertFalse(WorkerEligibility.of(
                WorkerEligibility.Reading.mains(25.0).activelyInUse(), DEFAULTS).fit());
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
