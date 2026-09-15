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
package in.co.s13.sips.lib.ml.elastic;

import java.util.Arrays;

/**
 * Measures real local memory-copy bandwidth on the current JVM/machine.
 *
 * <p>SIPS has no real GPU/CUDA bindings anywhere: the accelerator backends for CUDA, ROCm and
 * Vulkan are unimplemented stubs, so there is no way to measure a real PCIe transfer or
 * anything actually GPU-bound. What this class measures instead is a different, more modest
 * thing, and it must not be mistaken for the real thing it stands in for: the wall-clock cost
 * of moving {@code sizeBytes} between two heap {@code byte[]} arrays via {@link
 * System#arraycopy} on whatever machine this JVM happens to be running on. That is the same
 * epistemic move {@code WorkerBench} makes for compute — a genuine number from a genuine
 * operation, offered as the honest ceiling on what can be measured without real accelerator
 * bindings, rather than a declared guess dressed up as a measurement.
 */
public final class TransferCost {

    private TransferCost() {
    }

    /**
     * Times {@code measuredRuns} copies of {@code sizeBytes} bytes between two heap arrays,
     * after {@code warmupRuns} untimed copies to let the JIT and caches settle, and returns
     * the median elapsed time in milliseconds.
     *
     * @param sizeBytes bytes to copy on each run; must be {@code > 0} and must fit in an
     *        {@code int}, since a Java array cannot be longer than {@link Integer#MAX_VALUE}
     *        and this method can only actually copy what a Java array can hold
     * @param warmupRuns untimed copies run first, to absorb JIT warm-up; must be {@code >= 0}
     * @param measuredRuns timed copies whose elapsed times are aggregated; must be {@code >= 1}
     * @return the median of the {@code measuredRuns} timed elapsed times, in milliseconds
     */
    public static double measureMillis(long sizeBytes, int warmupRuns, int measuredRuns) {
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be positive: " + sizeBytes);
        }
        if (sizeBytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("sizeBytes must fit in an int (max "
                    + Integer.MAX_VALUE + "), not " + sizeBytes);
        }
        if (warmupRuns < 0) {
            throw new IllegalArgumentException("warmupRuns must not be negative: " + warmupRuns);
        }
        if (measuredRuns < 1) {
            throw new IllegalArgumentException("measuredRuns must be at least 1: " + measuredRuns);
        }

        int length = (int) sizeBytes;
        // Allocated once, outside both loops below: timing an allocation would measure heap
        // allocation cost, not copy bandwidth, and would dishonestly inflate or deflate the
        // number this method exists to produce.
        byte[] source = new byte[length];
        byte[] destination = new byte[length];

        for (int i = 0; i < warmupRuns; i++) {
            System.arraycopy(source, 0, destination, 0, length);
        }

        long[] elapsedNanos = new long[measuredRuns];
        for (int i = 0; i < measuredRuns; i++) {
            long start = System.nanoTime();
            System.arraycopy(source, 0, destination, 0, length);
            elapsedNanos[i] = System.nanoTime() - start;
        }

        Arrays.sort(elapsedNanos);
        // Median, not mean: a single slow outlier (a GC pause, a scheduler hiccup) should not
        // dominate a small number of samples the way it would a mean. Even count -> average
        // the two middle samples; odd count -> take the middle one.
        int mid = elapsedNanos.length / 2;
        double medianNanos = (elapsedNanos.length % 2 == 0)
                ? (elapsedNanos[mid - 1] + elapsedNanos[mid]) / 2.0
                : elapsedNanos[mid];
        return medianNanos / 1_000_000.0;
    }

    /**
     * Convenience that measures a real {@code fetchMillis} for {@code sizeBytes} (3 warm-up
     * runs, 7 measured runs) and builds the resulting {@link UnitProfile} from it, so a caller
     * gets a profile backed by a genuine measurement instead of a guessed transfer-cost
     * constant.
     *
     * @param unitId see {@link UnitProfile#unitId()}
     * @param sizeBytes see {@link UnitProfile#sizeBytes()}; also the size measured to produce
     *        {@code fetchMillis}
     * @param hotComputeMillis see {@link UnitProfile#hotComputeMillis()}
     * @param coldComputeMillis see {@link UnitProfile#coldComputeMillis()}
     * @return a {@link UnitProfile} whose {@code fetchMillis} is a real measured value
     */
    public static UnitProfile profileFor(String unitId, long sizeBytes, double hotComputeMillis,
            double coldComputeMillis) {
        double fetchMillis = measureMillis(sizeBytes, 3, 7);
        return new UnitProfile(unitId, sizeBytes, fetchMillis, hotComputeMillis, coldComputeMillis);
    }
}
