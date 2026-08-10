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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * The power telemetry sources SIPS knows how to read.
 *
 * <p>Each probes for its source and reports plainly when it is unusable, so an
 * operator can tell "this machine has no such sensor" from "the binding was
 * never written". None of these are verified against real hardware: the
 * development machine is an Intel Mac, where RAPL does not exist, powermetrics
 * needs root, and no NVIDIA or AMD compute tooling is installed.
 */
public final class PowerMonitors {

    private PowerMonitors() {
    }

    /** Every known source, available or not. */
    public static List<PowerMonitor> all() {
        return List.of(new LinuxRaplMonitor(), new NvidiaSmiMonitor(),
                new RocmSmiMonitor(), new UnavailableMonitor());
    }

    /** The first usable source, or one that explains why there is none. */
    public static PowerMonitor best() {
        return all().stream().filter(PowerMonitor::isAvailable).findFirst()
                .orElseGet(UnavailableMonitor::new);
    }

    /**
     * Intel and AMD RAPL, exposed by Linux through sysfs as a running energy
     * counter in microjoules. Power is the difference between two samples over
     * the interval between them.
     *
     * <p>Many distributions restrict these files to root following CVE-2020-8694,
     * which used the counter as a side channel, so unreadable is common.
     */
    public static final class LinuxRaplMonitor implements PowerMonitor {

        // Kept as text, not a Path. Windows rejects the colon in "intel-rapl:0"
        // with an InvalidPathException, and building it in a static initialiser
        // turned that into a NoClassDefFoundError for every caller -- including
        // ones that only wanted to ask whether RAPL was available and be told no.
        private static final String ENERGY = "/sys/class/powercap/intel-rapl:0/energy_uj";

        /** The counter, or empty on a platform whose paths cannot express it. */
        private static Optional<Path> energyFile() {
            try {
                return Optional.of(Path.of(ENERGY));
            } catch (java.nio.file.InvalidPathException ex) {
                return Optional.empty();
            }
        }

        @Override
        public String name() {
            return "Linux RAPL";
        }

        @Override
        public boolean isAvailable() {
            return unavailableReason().isEmpty();
        }

        @Override
        public String unavailableReason() {
            if (!System.getProperty("os.name", "").toLowerCase().contains("linux")) {
                return "RAPL is exposed through sysfs on Linux only";
            }
            Path energy = energyFile().orElse(null);
            if (energy == null || !Files.exists(energy)) {
                return "no RAPL counter at " + ENERGY + "; the CPU or kernel does not expose one";
            }
            if (!Files.isReadable(energy)) {
                return ENERGY + " is not readable; many distributions restrict it to root "
                        + "after CVE-2020-8694";
            }
            return "";
        }

        @Override
        public Scope scope() {
            return Scope.CPU_PACKAGE;
        }

        @Override
        public Optional<Double> readWatts() {
            if (!isAvailable()) {
                return Optional.empty();
            }
            try {
                long first = readMicrojoules();
                long start = System.nanoTime();
                Thread.sleep(100);
                long second = readMicrojoules();
                double seconds = (System.nanoTime() - start) / 1e9;
                if (second < first || seconds <= 0) {
                    // The counter wrapped; a single sample cannot be salvaged.
                    return Optional.empty();
                }
                return Optional.of((second - first) / 1e6 / seconds);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            } catch (RuntimeException | java.io.IOException ex) {
                return Optional.empty();
            }
        }

        private long readMicrojoules() throws java.io.IOException {
            Path energy = energyFile().orElseThrow(
                    () -> new java.io.IOException("no readable path for " + ENERGY));
            return Long.parseLong(Files.readString(energy).trim());
        }
    }

    /** NVIDIA, through nvidia-smi. */
    public static final class NvidiaSmiMonitor extends CommandMonitor {

        NvidiaSmiMonitor() {
            super("NVIDIA SMI", "nvidia-smi",
                    List.of("nvidia-smi", "--query-gpu=power.draw", "--format=csv,noheader,nounits"));
        }
    }

    /** AMD, through rocm-smi. */
    public static final class RocmSmiMonitor extends CommandMonitor {

        RocmSmiMonitor() {
            super("ROCm SMI", "rocm-smi", List.of("rocm-smi", "--showpower", "--csv"));
        }
    }

    /** Nothing measurable here. */
    public static final class UnavailableMonitor implements PowerMonitor {

        @Override
        public String name() {
            return "none";
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public String unavailableReason() {
            if (System.getProperty("os.name", "").toLowerCase().contains("mac")) {
                return "macOS exposes power only through powermetrics, which requires root; "
                        + "battery draw is readable but reads zero on mains power";
            }
            return "no power telemetry source detected on this host";
        }

        @Override
        public Scope scope() {
            return Scope.NONE;
        }

        @Override
        public Optional<Double> readWatts() {
            return Optional.empty();
        }
    }

    /** Shared behaviour for monitors that shell out to a vendor tool. */
    abstract static class CommandMonitor implements PowerMonitor {

        private final String displayName;
        private final String tool;
        private final List<String> command;

        CommandMonitor(String displayName, String tool, List<String> command) {
            this.displayName = displayName;
            this.tool = tool;
            this.command = command;
        }

        @Override
        public String name() {
            return displayName;
        }

        @Override
        public Scope scope() {
            return Scope.DEVICE;
        }

        @Override
        public boolean isAvailable() {
            return unavailableReason().isEmpty();
        }

        @Override
        public String unavailableReason() {
            return onPath(tool) ? "" : tool + " is not on PATH";
        }

        @Override
        public Optional<Double> readWatts() {
            if (!isAvailable()) {
                return Optional.empty();
            }
            try {
                Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
                String output;
                try (var in = process.getInputStream()) {
                    output = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    return Optional.empty();
                }
                return firstNumber(output);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            } catch (java.io.IOException | RuntimeException ex) {
                return Optional.empty();
            }
        }

        /** Vendor tools vary in layout; the first number is the draw in watts. */
        static Optional<Double> firstNumber(String output) {
            if (output == null) {
                return Optional.empty();
            }
            // Not preceded by a letter: rocm-smi prints "card0, 101 W", and a
            // bare number match would take the 0 out of "card0".
            var matcher = java.util.regex.Pattern
                    .compile("(?<![A-Za-z])\\d+(?:\\.\\d+)?").matcher(output);
            return matcher.find() ? Optional.of(Double.parseDouble(matcher.group())) : Optional.empty();
        }

        static boolean onPath(String tool) {
            String path = System.getenv("PATH");
            if (path == null) {
                return false;
            }
            for (String dir : path.split(java.io.File.pathSeparator)) {
                if (Files.isExecutable(Path.of(dir, tool))) {
                    return true;
                }
            }
            return false;
        }
    }
}
