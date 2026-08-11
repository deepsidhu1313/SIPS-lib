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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The classes a phone worker embeds must not use APIs a phone does not have.
 *
 * <p>An Android worker reuses these classes directly on ART, which supports
 * roughly the Java 11 library surface (java.nio.file from API 26). Anything
 * newer — {@code HexFormat} (Java 17), {@code System.Logger} (Java 9 but
 * absent from ART) — crashes at class-load time with a
 * {@code NoClassDefFoundError}, on the device, after everything worked on the
 * desktop. That is the same failure class as the Windows RAPL bug this
 * project has already shipped once: a platform difference that no amount of
 * desktop testing can reach.
 *
 * <p>So the portable set is named, and this test greps it — the same approach
 * {@code NoPathConcatenationTest} uses for path bugs. A class that needs a
 * banned API either finds a portable alternative or leaves the portable set
 * explicitly.
 */
class WorkerCorePortabilityTest {

    /** The packages a mobile worker embeds. */
    private static final List<String> WORKER_CORE = List.of(
            "src/main/java/in/co/s13/sips/lib/array",
            "src/main/java/in/co/s13/sips/lib/ml",
            "src/main/java/in/co/s13/sips/lib/wasm");

    /** APIs absent on ART, with the portable replacement to use instead. */
    private static final Map<String, String> BANNED = Map.of(
            "HexFormat", "a plain hex loop; HexFormat is Java 17 and ART has no such class",
            "System.getLogger", "java.util.logging, which Android ships",
            "System.Logger", "java.util.logging, which Android ships",
            "ProcessBuilder", "nothing; a worker must not spawn processes",
            "java.awt", "nothing; there is no AWT on a phone");

    @Test
    void workerCoreClassesUseOnlyApisAPhoneHas() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String root : WORKER_CORE) {
            try (var files = Files.walk(Path.of(root))) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(file);
                    for (Map.Entry<String, String> ban : BANNED.entrySet()) {
                        if (source.contains(ban.getKey())
                                && !source.contains("portable-ok: " + ban.getKey())) {
                            violations.add(file.getFileName() + " uses " + ban.getKey()
                                    + " -- use " + ban.getValue());
                        }
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "Worker-core classes must run on ART:\n  "
                + String.join("\n  ", violations));
    }
}
