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
package in.co.s13.sips.lib.wasm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vectors another language can run to prove it implements the same host ABI.
 *
 * <p>An Android worker reuses {@link WasmRunner} directly — Chicory is pure JVM
 * bytecode with no JNI, so it runs on ART. iOS has no JVM at all, so a worker
 * there is Swift around a Swift WebAssembly runtime, implementing the same six
 * imports by hand. Two implementations of one contract is exactly the situation
 * that drifts, and it drifts silently: a module that runs on the cluster and
 * returns subtly different bytes on a phone is a wrong answer, not a crash.
 *
 * <p>So the contract is written down as data. This test builds the vectors,
 * runs every one through the real runner, and asserts the answer — which is
 * what makes the file trustworthy for a runtime that cannot run this test.
 * Ported to Swift or Kotlin, "conformant" means: same module bytes, same input,
 * same output, same status.
 *
 * <p>Deliberately data and not a Java interface. An interface would need the
 * other platform to depend on this library, which is the one thing it cannot
 * do.
 */
class AbiConformanceTest {

    /** Where the vectors are written for other runtimes to consume. */
    static final Path VECTORS = Path.of("src", "test", "resources", "abi-conformance.json");

    /** One case: a module, an input, and what a conformant host must produce. */
    private record Vector(String name, String why, byte[] module, byte[] input,
            byte[] expectedOutput, long expectedStatus, long firstIndex, long lastIndex) {
    }

    private List<Vector> vectors(Path dir) throws IOException {
        List<Vector> vectors = new ArrayList<>();

        // A module that uses no host functions at all. The floor of the
        // contract: a runtime must run a plain module before anything else.
        vectors.add(new Vector("bare-success",
                "a module with no imports returns its status and nothing else",
                Files.readAllBytes(TestModules.bare(dir, "bare.wasm",
                        TestModules.i64(0))),
                new byte[0], new byte[0], 0, 0, 10));

        // The index range arrives as the two parameters of run, and a module
        // that returns their difference proves both were delivered and in the
        // right order -- an implementation that swapped them would still run.
        vectors.add(new Vector("index-range",
                "run(firstIndex, lastIndexExclusive), in that order",
                Files.readAllBytes(TestModules.bare(dir, "range.wasm",
                        TestModules.concat(TestModules.localGet(1), TestModules.localGet(0),
                                new byte[]{0x7d}))),
                new byte[0], new byte[0], 90, 10, 100));

        // input_size then input_read then output_write: the whole data path in
        // one module, and the case a host gets wrong by copying the wrong way.
        vectors.add(new Vector("echo-input",
                "input_read copies into module memory; output_write publishes from it",
                Files.readAllBytes(TestModules.hosted(dir, "echo.wasm", 1,
                        TestModules.concat(
                                TestModules.readAllInput(TestModules.FIRST_LOCAL),
                                TestModules.i32(0), TestModules.localGet(TestModules.FIRST_LOCAL),
                                TestModules.call(TestModules.OUTPUT_WRITE),
                                TestModules.i64(0)))),
                "weights".getBytes(StandardCharsets.UTF_8),
                "weights".getBytes(StandardCharsets.UTF_8), 0, 0, 1));

        // An empty input is not an error: a chunk that computes purely from
        // its index range has none, and input_size must say zero rather than
        // failing.
        vectors.add(new Vector("empty-input",
                "input_size is 0 when a chunk has no input, and that is not an error",
                Files.readAllBytes(TestModules.hosted(dir, "empty.wasm", 1,
                        TestModules.concat(
                                TestModules.readAllInput(TestModules.FIRST_LOCAL),
                                TestModules.i32(0), TestModules.localGet(TestModules.FIRST_LOCAL),
                                TestModules.call(TestModules.OUTPUT_WRITE),
                                TestModules.i64(0)))),
                new byte[0], new byte[0], 0, 0, 1));

        // A non-zero status must reach the caller unchanged. A host that
        // normalised it to 1 would lose which failure happened.
        vectors.add(new Vector("failure-status",
                "a non-zero status is returned as-is, not normalised",
                Files.readAllBytes(TestModules.bare(dir, "fail.wasm",
                        TestModules.i64(42))),
                new byte[0], new byte[0], 42, 0, 1));

        return vectors;
    }

    @Test
    @Timeout(60)
    void everyVectorHoldsAgainstTheRealRunner(@TempDir Path dir) throws IOException {
        // The vectors are only worth publishing if they are right, and the one
        // implementation that can say so is this one.
        try (WasmRunner runner = new WasmRunner()) {
            for (Vector vector : vectors(dir)) {
                Path module = Files.write(dir.resolve(vector.name() + "-run.wasm"),
                        vector.module());
                WasmTask task = new WasmTask("conformance", 0, module, null,
                        vector.firstIndex(), vector.lastIndex());
                WasmHost host = WasmHost.builder().input(vector.input()).build();

                long status = runner.run(task, host, Duration.ofSeconds(20));

                assertEquals(vector.expectedStatus(), status, vector.name() + ": " + vector.why());
                assertArrayEquals(vector.expectedOutput(), host.output(),
                        vector.name() + ": " + vector.why());
            }
        }
    }

    @Test
    @Timeout(60)
    void thevectorsAreWrittenWhereAnotherRuntimeCanReadThem(@TempDir Path dir)
            throws IOException {
        // Regenerated rather than hand-maintained, so the published contract
        // cannot drift from the one this library actually implements.
        JSONArray cases = new JSONArray();
        for (Vector vector : vectors(dir)) {
            cases.put(new JSONObject()
                    .put("name", vector.name())
                    .put("why", vector.why())
                    .put("module", Base64.getEncoder().encodeToString(vector.module()))
                    .put("input", Base64.getEncoder().encodeToString(vector.input()))
                    .put("firstIndex", vector.firstIndex())
                    .put("lastIndexExclusive", vector.lastIndex())
                    .put("expectedOutput",
                            Base64.getEncoder().encodeToString(vector.expectedOutput()))
                    .put("expectedStatus", vector.expectedStatus()));
        }

        JSONObject document = new JSONObject()
                .put("abiVersion", 1)
                .put("namespace", WasmHost.NAMESPACE)
                .put("imports", new JSONArray(List.of(
                        "input_size", "input_read", "output_write",
                        "log", "break_all", "break_after")))
                .put("entryPoint", "run")
                .put("note", "A conformant runtime loads each module, calls run(firstIndex, "
                        + "lastIndexExclusive), and must produce expectedOutput and "
                        + "expectedStatus exactly. Regenerated by AbiConformanceTest; "
                        + "do not edit by hand.")
                .put("cases", cases);

        Files.createDirectories(VECTORS.getParent());
        Files.writeString(VECTORS, document.toString(2) + "\n");

        assertTrue(Files.size(VECTORS) > 0);
        assertEquals(5, cases.length(), "every vector should be published");
    }
}
