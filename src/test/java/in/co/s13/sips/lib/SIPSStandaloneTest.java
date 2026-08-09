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
package in.co.s13.sips.lib;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Running a SIPS program on its own, with no cluster.
 *
 * <p>The loop markers are deliberately inert, so a SIPS program is an ordinary
 * Java program that happens to be distributable. That property is what lets a
 * user develop and verify a job locally before submitting it, and it only holds
 * if the capture calls also tolerate the absence of the databases that SIPS-Run
 * creates during its parsing pass.
 *
 * <p>Previously {@code saveValues} threw NullPointerException in that
 * situation, so any program calling it could not be run standalone at all.
 */
class SIPSStandaloneTest {

    @Test
    void markersAreInert() {
        SIPS sim = new SIPS("Standalone");
        assertDoesNotThrow(() -> {
            sim.simulateSection();
            sim.endSimulateSection();
            sim.parallelFor();
            sim.endParallelFor();
            sim.simulateLoop();
        });
    }

    @Test
    void saveValuesToleratesAMissingSimulationDatabase() {
        SIPS sim = new SIPS("NoSuchProjectAnywhere");
        assertDoesNotThrow(() -> sim.saveValues("8", "800", "600"));
    }

    @Test
    void saveValuesWithNoArgumentsIsHarmless() {
        SIPS sim = new SIPS("NoSuchProjectAnywhere");
        assertDoesNotThrow(() -> sim.saveValues());
    }

    @Test
    void repeatedCapturesDoNotAccumulateFailures() {
        SIPS sim = new SIPS("NoSuchProjectAnywhere");
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 5; i++) {
                sim.saveValues("" + i);
            }
        });
    }
}
