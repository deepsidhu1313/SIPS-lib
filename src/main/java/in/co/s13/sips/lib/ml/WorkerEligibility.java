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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Whether a device should be given work at all.
 *
 * <p>A cluster of servers has one answer: yes, that is what they are for. A
 * cluster that includes someone's phone does not. Training on a stranger's
 * battery, or their mobile data, or a handset already too hot to hold, is a
 * cost the framework imposes on a person rather than on a machine.
 *
 * <p>Refusing a hot device is not only politeness. It will thermally throttle,
 * and a round ends when its slowest worker ends, so an overheating phone slows
 * everyone — leaving it out is faster than putting it in.
 *
 * <h2>Unknown is not fine</h2>
 *
 * <p>A battery reading that cannot be had counts as unfit. The device that
 * cannot say how it is doing is exactly the device whose battery should not be
 * assumed healthy, and a broken reading is a real observed condition rather
 * than a hypothetical: Android has been seen reporting {@code -1} instead of
 * failing, which a naive percentage check reads as a valid number.
 *
 * <p>Not to be confused with {@code accelerator.NodeFitness}, which asks a
 * different question: given that this node is working, which of its devices
 * suits this kernel. This one asks whether the node should be working at all.
 *
 * <p>Temperature is deliberately the other way round. Plenty of ordinary
 * servers expose no thermal sensor at all, and refusing every one of them would
 * make the check useless exactly where it is needed least.
 *
 * <h2>Active use</h2>
 *
 * <p>JPPF's "Idle Host" mode restricts a node to only accepting work while its
 * host is otherwise idle — a screensaver-triggered check, applied to any
 * machine, not only phones. The reasoning generalises: stealing cycles from
 * someone actively at the keyboard is rude whichever way the device is
 * powered, and the social cost of draining a stranger's battery is worse when
 * they are mid-conversation than when the phone is face-down on a charger
 * overnight. {@link Reading#activelyInUse()} carries that signal.
 *
 * <p>Unlike battery, this is treated leniently when absent. Every device can
 * cheaply report its battery, so silence there is itself suspicious; hardly
 * any platform — a desktop, an older phone, anything not wired up to report
 * foreground state — can report active use at all, so treating silence as
 * refusal here would refuse nearly the whole fleet and defeat the point of
 * asking it for help.
 *
 * <h2>Temperature without Celsius</h2>
 *
 * <p>iOS deliberately does not expose a raw temperature to apps at all — only
 * {@code ProcessInfo.thermalState}, a coarse four-level enum mirrored here as
 * {@link ThermalLevel}. Fabricating a Celsius number to fit the numeric
 * ceiling below would misrepresent what the device actually reported, so this
 * is a genuinely separate reading shape rather than a unit conversion: a
 * platform reports Celsius, or a discrete level, or neither — never both, and
 * the {@link #of} judgment checks Celsius first only because a reading built
 * from one shape's factory never populates the other.
 */
public final class WorkerEligibility {

    /**
     * The four levels {@code ProcessInfo.ThermalState} reports on iOS, in
     * ascending severity — the ordering itself is load-bearing, since a
     * policy ceiling is judged by {@link Enum#compareTo}.
     */
    public enum ThermalLevel {
        NOMINAL, FAIR, SERIOUS, CRITICAL
    }

    /** What a device says about itself. */
    public record Reading(boolean onMains, OptionalInt batteryPercent,
            OptionalDouble temperatureCelsius, Optional<ThermalLevel> thermalLevel,
            Optional<Boolean> inUse) {

        /** A machine on mains power, with a temperature. */
        public static Reading mains(double temperatureCelsius) {
            return new Reading(true, OptionalInt.empty(),
                    OptionalDouble.of(temperatureCelsius), Optional.empty(), Optional.empty());
        }

        /** A device running on its battery. */
        public static Reading onBattery(int percent, double temperatureCelsius) {
            return new Reading(false, OptionalInt.of(percent),
                    OptionalDouble.of(temperatureCelsius), Optional.empty(), Optional.empty());
        }

        /** A device on battery that could not say how much is left. */
        public static Reading unknownBattery(double temperatureCelsius) {
            return new Reading(false, OptionalInt.empty(),
                    OptionalDouble.of(temperatureCelsius), Optional.empty(), Optional.empty());
        }

        /** A machine with no thermal sensor, which is most of them. */
        public static Reading unknownTemperature() {
            return new Reading(true, OptionalInt.empty(), OptionalDouble.empty(),
                    Optional.empty(), Optional.empty());
        }

        /** A device on battery reporting only a discrete thermal level — iOS. */
        public static Reading onBatteryWithThermalLevel(int percent, ThermalLevel level) {
            if (level == null) {
                throw new IllegalArgumentException("A thermal level is required");
            }
            return new Reading(false, OptionalInt.of(percent), OptionalDouble.empty(),
                    Optional.of(level), Optional.empty());
        }

        /** A device on mains reporting only a discrete thermal level — iOS. */
        public static Reading mainsWithThermalLevel(ThermalLevel level) {
            if (level == null) {
                throw new IllegalArgumentException("A thermal level is required");
            }
            return new Reading(true, OptionalInt.empty(), OptionalDouble.empty(),
                    Optional.of(level), Optional.empty());
        }

        /** The same reading, with its owner confirmed to be using it right now. */
        public Reading activelyInUse() {
            return new Reading(onMains, batteryPercent, temperatureCelsius, thermalLevel,
                    Optional.of(true));
        }

        /** The same reading, with the device confirmed idle. */
        public Reading confirmedIdle() {
            return new Reading(onMains, batteryPercent, temperatureCelsius, thermalLevel,
                    Optional.of(false));
        }
    }

    /** What a job is willing to ask of a device. */
    public record Policy(int minBatteryPercent, double maxTemperatureCelsius,
            boolean requireMains, boolean avoidWhenInUse, ThermalLevel maxThermalLevel) {

        /**
         * Enough headroom that a round does not strand someone at zero, a
         * ceiling below the point at which handsets throttle, battery power
         * allowed — most volunteers are not plugged in — a device its owner
         * is actively using left alone, and the same throttle-avoidance
         * reasoning as the Celsius ceiling expressed in iOS's own vocabulary:
         * a device already at FAIR is not yet mitigating, one at SERIOUS is.
         */
        public static Policy defaults() {
            return new Policy(30, 45.0, false, true, ThermalLevel.FAIR);
        }
    }

    /** Whether a device is fit, and if not, which limit it hit. */
    public record Report(boolean fit, Optional<String> refusal) {

        static Report ok() {
            return new Report(true, Optional.empty());
        }

        static Report no(String because) {
            return new Report(false, Optional.of(because));
        }
    }

    private WorkerEligibility() {
    }

    /** Judges one device against a policy. */
    public static Report of(Reading reading, Policy policy) {
        if (reading == null || policy == null) {
            throw new IllegalArgumentException("A reading and a policy are both required");
        }

        if (reading.temperatureCelsius().isPresent()
                && reading.temperatureCelsius().getAsDouble() > policy.maxTemperatureCelsius()) {
            return Report.no("temperature is "
                    + reading.temperatureCelsius().getAsDouble() + " C, above the "
                    + policy.maxTemperatureCelsius() + " C ceiling");
        }
        // The discrete path, checked only when Celsius was not reported: a
        // reading is built from one shape's factory and never carries both,
        // but Celsius wins on principle if it somehow did -- a real number is
        // more informative than a four-level bucket.
        if (reading.temperatureCelsius().isEmpty() && reading.thermalLevel().isPresent()
                && reading.thermalLevel().get().compareTo(policy.maxThermalLevel()) > 0) {
            return Report.no("thermal state is " + reading.thermalLevel().get()
                    + ", above the " + policy.maxThermalLevel() + " ceiling");
        }

        // Checked before the mains short-circuit below: a plugged-in phone
        // its owner is actively using is still rude to burden, so being on
        // mains must not exempt a device from this check.
        if (policy.avoidWhenInUse() && reading.inUse().orElse(false)) {
            return Report.no("the device is in active use, and this job leaves "
                    + "in-use devices alone");
        }

        if (reading.onMains()) {
            return Report.ok();
        }
        if (policy.requireMains()) {
            return Report.no("this job needs nodes on mains power");
        }

        if (reading.batteryPercent().isEmpty()) {
            return Report.no("could not read the battery, and a device that cannot say "
                    + "is not assumed to be fine");
        }
        int percent = reading.batteryPercent().getAsInt();
        if (percent < 0 || percent > 100) {
            return Report.no("could not read the battery: " + percent
                    + "% is not a battery level");
        }
        if (percent < policy.minBatteryPercent()) {
            return Report.no("battery is at " + percent + "%, below the "
                    + policy.minBatteryPercent() + "% floor");
        }
        return Report.ok();
    }

    /**
     * Keeps only the nodes fit to be given work.
     *
     * <p>What a scheduler actually calls before {@link ShardPlan}: an unfit
     * node must get no shard rather than a small one, because a shard it never
     * finishes stalls the round either way.
     *
     * <p>A node with no reading is treated as a server. Most of any cluster is
     * machines that have never reported a battery, and refusing those would
     * refuse everything.
     */
    public static Map<String, Double> eligible(Map<String, Double> speedByNode,
            Map<String, Reading> readingByNode, Policy policy) {
        Map<String, Double> eligible = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : speedByNode.entrySet()) {
            Reading reading = readingByNode.get(entry.getKey());
            if (reading == null || of(reading, policy).fit()) {
                eligible.put(entry.getKey(), entry.getValue());
            }
        }
        return eligible;
    }
}
