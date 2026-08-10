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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No source file builds a path by gluing a separator onto a string.
 *
 * <p>This module's path bugs were all one mistake repeated: {@code dir + "/" +
 * name}. On Windows that yields {@code C:\Users\x/.simulated/Foo} — which the
 * file APIs tolerate, so it never fails where it is written, only somewhere a
 * path is compared, split or parsed. The matching {@code lastIndexOf("/proc/")}
 * did throw, from a stack frame with no obvious connection to the cause.
 *
 * <p>Fixing every site is worth little if the next one reintroduces it, and this
 * is not a mistake code review reliably catches — it looks like ordinary string
 * concatenation. So it is checked mechanically, the same way the compiler checks
 * types.
 *
 * <p>Skipped when the sources are not on disk, so a build from a distributed jar
 * does not fail for want of something to read.
 */
class NoPathConcatenationTest {

    private static final Path SOURCES = Path.of("src", "main", "java");

    /**
     * A separator immediately beside a {@code +}: {@code "…/" +} or
     * {@code + "/…"}. Two backslashes are required for the escaped form, which
     * keeps {@code "\n"} and {@code "\t"} out of the results.
     */
    private static final Pattern JOINED_SEPARATOR = Pattern.compile(
            "\"[^\"\\n]*(?:/|\\\\\\\\)\"\\s*\\+|\\+\\s*\"(?:/|\\\\\\\\)[^\"\\n]*\"");

    static boolean sourcesArePresent() {
        return Files.isDirectory(SOURCES);
    }

    @Test
    @EnabledIf("sourcesArePresent")
    void noSourceFileGluesASeparatorOntoAPath() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).strip();
                    if (line.startsWith("*") || line.startsWith("//")) {
                        continue;
                    }
                    if (JOINED_SEPARATOR.matcher(line).find()) {
                        offenders.add(file + ":" + (i + 1) + "  " + line);
                    }
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "Build these with SipsPaths.join instead — a glued separator is wrong on "
                + "Windows and the symptom shows up far from here:\n  "
                + String.join("\n  ", offenders));
    }

    @Test
    @EnabledIf("sourcesArePresent")
    void noSourceFileSearchesAPathForALiteralSeparator() throws IOException {
        // dir.substring(0, dir.lastIndexOf("/proc/")) returns -1 on Windows and
        // then throws out of substring. Use SipsPaths.ancestorAbove.
        Pattern searching = Pattern.compile(
                "(?:lastIndexOf|indexOf)\\(\"(?:/|\\\\\\\\)[^\"]*\"\\)");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).strip();
                    if (line.startsWith("*") || line.startsWith("//")) {
                        continue;
                    }
                    if (searching.matcher(line).find()) {
                        offenders.add(file + ":" + (i + 1) + "  " + line);
                    }
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "Read structure out of a path with SipsPaths, not by searching for a "
                + "separator:\n  " + String.join("\n  ", offenders));
    }
}
