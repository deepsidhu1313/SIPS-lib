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

import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a program's simulation and AST databases actually land.
 *
 * <p>These are built from the process working directory, so they must be
 * absolute.
 * A relative one resolves against whatever directory the process was started
 * in, which means the same program finds its database when launched one way and
 * silently does not when launched another — and {@code saveValues} returns
 * quietly when the database is missing, so the symptom is a wrong answer rather
 * than an error.
 */
class SIPSPathsRegressionTest {

    @Test
    void theSimulationDatabasePathIsAbsolute() {
        SIPS sim = new SIPS("Search");

        assertTrue(Path.of(sim.simDBLoc).isAbsolute(),
                sim.simDBLoc + " must not depend on the working directory");
    }

    @Test
    void theParsedDatabasePathIsAbsolute() {
        SIPS sim = new SIPS("Search");

        assertTrue(Path.of(sim.parsedCodeDBLoc).isAbsolute(),
                sim.parsedCodeDBLoc + " must not depend on the working directory");
    }

    @Test
    void neitherPathMixesSeparators() {
        // The original report: on Windows these read C:\Users\x/.simulated/Foo.
        // Java's file APIs tolerate it, which is why it never failed outright --
        // it breaks where a path is compared or split rather than opened.
        SIPS sim = new SIPS("Search");
        char foreign = File.separatorChar == '/' ? '\\' : '/';

        assertEquals(-1, sim.simDBLoc.indexOf(foreign), sim.simDBLoc);
        assertEquals(-1, sim.parsedCodeDBLoc.indexOf(foreign), sim.parsedCodeDBLoc);
    }

    @Test
    void aPackagedClassStillLandsUnderTheHomeDirectory() {
        // ClassName carries dots for a packaged class; the path must not escape
        // the SIPS home directory when they are expanded.
        SIPS sim = new SIPS("in.co.s13.samples.Search");

        assertTrue(Path.of(sim.simDBLoc).isAbsolute());
        assertTrue(Path.of(sim.simDBLoc).normalize().toString()
                .contains(Path.of(".simulated").toString()));
    }
}
