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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferCostTest {

    @Test
    void nonPositiveSizeBytesIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> TransferCost.measureMillis(0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> TransferCost.measureMillis(-1, 1, 1));
    }

    @Test
    void sizeBytesLargerThanIntIsRefused() {
        // measureMillis can only copy what a Java array can hold.
        assertThrows(IllegalArgumentException.class,
                () -> TransferCost.measureMillis((long) Integer.MAX_VALUE + 1, 1, 1));
    }

    @Test
    void negativeWarmupRunsIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> TransferCost.measureMillis(1024, -1, 1));
    }

    @Test
    void fewerThanOneMeasuredRunIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> TransferCost.measureMillis(1024, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> TransferCost.measureMillis(1024, 1, -1));
    }

    @Test
    void aRealisticSizeMeasuresAPositiveCost() {
        // No upper bound asserted: real timing on an unknown CI/dev machine has no reliable
        // ceiling, so the only honest assertion is that a real, positive number came back.
        double millis = TransferCost.measureMillis(4 * 1024 * 1024, 3, 7);
        assertTrue(millis > 0, "expected a positive measured cost, got " + millis);
    }

    @Test
    void zeroWarmupRunsIsAllowed() {
        double millis = TransferCost.measureMillis(1024, 0, 3);
        assertTrue(millis > 0, "expected a positive measured cost, got " + millis);
    }

    @Test
    void aMuchLargerCopyCostsAtLeastAsMuchAsAMuchSmallerOne() {
        // >=, not >, so the (unlikely but real) case where both round to sub-millisecond
        // noise on a very fast machine still passes rather than flaking.
        double small = TransferCost.measureMillis(1024, 3, 7);
        double large = TransferCost.measureMillis(100L * 1024 * 1024, 3, 7);
        assertTrue(large >= small,
                "a 100 MiB copy (" + large + " ms) should cost at least as much as a 1 KiB copy ("
                        + small + " ms)");
    }

    @Test
    void profileForReturnsAProfileWithARealMeasuredFetchMillis() {
        UnitProfile profile = TransferCost.profileFor("unit-a", 1024 * 1024, 12.0, 34.0);

        assertEquals("unit-a", profile.unitId());
        assertEquals(1024 * 1024, profile.sizeBytes());
        assertTrue(profile.fetchMillis() >= 0, "measureMillis can legitimately return 0 or more");
        assertEquals(12.0, profile.hotComputeMillis());
        assertEquals(34.0, profile.coldComputeMillis());
    }
}
