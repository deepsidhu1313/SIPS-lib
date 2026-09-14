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

class UnitProfileTest {

    @Test
    void blankUnitIdIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new UnitProfile("  ", 1, 1, 1, 1));
    }

    @Test
    void nonPositiveSizeBytesIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new UnitProfile("a", 0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new UnitProfile("a", -1, 1, 1, 1));
    }

    @Test
    void negativeFetchMillisIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new UnitProfile("a", 1, -0.1, 1, 1));
    }

    @Test
    void zeroFetchMillisIsAllowed() {
        UnitProfile profile = new UnitProfile("a", 1, 0, 1, 1);
        assertEquals(0, profile.fetchMillis());
    }

    @Test
    void nonPositiveHotComputeMillisIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new UnitProfile("a", 1, 1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new UnitProfile("a", 1, 1, -1, 1));
    }

    @Test
    void nonPositiveColdComputeMillisIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new UnitProfile("a", 1, 1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new UnitProfile("a", 1, 1, 1, -1));
    }

    @Test
    void aUnitCheaperColdThanHotIsALegitimateProfile() {
        // The whole point: nothing here assumes the fast tier always wins.
        UnitProfile profile = new UnitProfile("bad-occupancy", 1024, 5.0, 50.0, 10.0);
        assertEquals(50.0, profile.hotComputeMillis());
        assertEquals(10.0, profile.coldComputeMillis());
    }
}
