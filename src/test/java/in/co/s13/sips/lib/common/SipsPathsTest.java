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
package in.co.s13.sips.lib.common;

import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Path building that works on Windows.
 *
 * <p>SIPS-Node#4 and SIPS-lib#1. Paths were built by concatenating {@code "/"}
 * — {@code homeDir + "/.simulated/" + name} — which on Windows yields
 * {@code C:\Users\x/.simulated/name}. Java's file APIs mostly tolerate that,
 * which is why it never failed loudly, but the string is still wrong wherever
 * a path is compared, split, stored or shown to a user.
 */
class SipsPathsTest {

    @Test
    void joinsWithThePlatformSeparator() {
        String joined = SipsPaths.join("base", "child", "leaf.txt");

        assertEquals(Path.of("base", "child", "leaf.txt").toString(), joined);
        assertTrue(joined.contains(File.separator));
    }

    @Test
    void producesNoForwardSlashOnWindowsStyleRoots() {
        // The reported symptom: a Windows root concatenated with "/segment".
        String joined = SipsPaths.join("C:\\Users\\x", ".simulated", "Foo-sim.db");

        assertFalse(joined.contains("\\/") || joined.contains("/\\"),
                "mixed separators leaked through: " + joined);
    }

    @Test
    void normalisesRedundantSegments() {
        assertEquals(Path.of("a", "c").toString(), SipsPaths.join("a", "b", "..", "c"));
    }

    @Test
    void skipsEmptyAndNullSegments() {
        // Callers build these from optional pieces; an empty one must not
        // introduce a doubled separator.
        assertEquals(Path.of("a", "b").toString(), SipsPaths.join("a", "", "b"));
        assertEquals(Path.of("a", "b").toString(), SipsPaths.join("a", null, "b"));
        assertEquals(Path.of("a", "b").toString(), SipsPaths.join("a", "   ", "b"));
    }

    @Test
    void acceptsSegmentsThatAlreadyContainSeparators() {
        // Much existing code passes "proc/uuid/job" as one string. Both
        // separators must be understood, whichever platform wrote them.
        assertEquals(Path.of("proc", "uuid", "job").toString(),
                SipsPaths.join("proc/uuid", "job"));
        assertEquals(Path.of("proc", "uuid", "job").toString(),
                SipsPaths.join("proc\\uuid", "job"));
    }

    @Test
    void aLeadingSeparatorOnALaterSegmentDoesNotResetToTheRoot() {
        // Path.resolve would discard everything before an absolute segment.
        // The distributor sends names like "/src/Main.java" meaning "relative
        // to here", and losing the base would write to the filesystem root.
        String joined = SipsPaths.join("proc", "/src/Main.java");

        assertEquals(Path.of("proc", "src", "Main.java").toString(), joined);
    }

    @Test
    void rejectsAnEmptyBase() {
        assertThrows(IllegalArgumentException.class, () -> SipsPaths.join());
        assertThrows(IllegalArgumentException.class, () -> SipsPaths.join("", ""));
    }

    @Test
    void classNameToPathUsesTheSeparatorNotASlash() {
        // SIPS.java did ClassName.replaceAll("\\.", "/"), which is the same bug
        // for packaged classes.
        assertEquals(Path.of("in", "co", "s13", "Foo").toString(),
                SipsPaths.classNameToPath("in.co.s13.Foo"));
        assertEquals("Foo", SipsPaths.classNameToPath("Foo"));
    }

    @Test
    void classNameToPathToleratesNothing() {
        assertEquals("", SipsPaths.classNameToPath(null));
        assertEquals("", SipsPaths.classNameToPath("  "));
    }

    @Test
    void roundTripsThroughFileWithoutChanging() {
        // Whatever we build must survive being handed to the file APIs.
        String joined = SipsPaths.join("a", "b", "c.txt");
        assertEquals(joined, new File(joined).getPath());
    }
}
