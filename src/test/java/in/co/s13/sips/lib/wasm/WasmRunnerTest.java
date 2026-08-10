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

import java.io.ByteArrayOutputStream;
import in.co.s13.sips.lib.ml.WarmModels;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Running a chunk as WebAssembly.
 *
 * <p>Modules are assembled here byte by byte rather than compiled from source,
 * so the suite needs no WASM toolchain and runs anywhere the JVM does — which
 * is the same property that makes Chicory the right runtime for a node.
 */
class WasmRunnerTest {

    /** Assembles a module exporting {@code run(i64,i64)->i64}. */
    private static byte[] module(byte[] bodyInstructions) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{0x00, 'a', 's', 'm'});
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(1).array());

        // type: (i64, i64) -> i64
        out.write(section(1, new byte[]{1, 0x60, 2, 0x7e, 0x7e, 1, 0x7e}));
        out.write(section(3, new byte[]{1, 0}));                       // one function
        ByteArrayOutputStream export = new ByteArrayOutputStream();
        export.write(1);
        export.write(3);
        export.write("run".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        export.write(0x00);
        export.write(0);
        out.write(section(7, export.toByteArray()));

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(0);                                                  // no locals
        body.write(bodyInstructions);
        body.write(0x0b);                                               // end
        ByteArrayOutputStream code = new ByteArrayOutputStream();
        code.write(1);
        code.write(body.size());
        code.write(body.toByteArray());
        out.write(section(10, code.toByteArray()));
        return out.toByteArray();
    }

    private static byte[] section(int id, byte[] body) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(id);
        out.write(body.length);
        out.write(body);
        return out.toByteArray();
    }

    /** Returns last - first, i.e. the iteration count, as a success proxy. */
    private static Path countingModule(Path dir) throws IOException {
        // local.get 1; local.get 0; i64.sub
        Path file = dir.resolve("count.wasm");
        Files.write(file, module(new byte[]{0x20, 1, 0x20, 0, (byte) 0x7d}));
        return file;
    }

    /** Always returns 0, the success status. */
    private static Path successModule(Path dir) throws IOException {
        Path file = dir.resolve("ok.wasm");
        Files.write(file, module(new byte[]{0x42, 0}));       // i64.const 0
        return file;
    }

    @Test
    @Timeout(30)
    void runsAChunkAndReturnsItsStatus(@TempDir Path dir) throws IOException {
        try (WasmRunner runner = new WasmRunner()) {
            WasmTask task = new WasmTask("job-1", 0, successModule(dir), null, 0, 1000);

            assertEquals(WasmTask.SUCCESS, runner.run(task, Duration.ofSeconds(10)));
        }
    }

    @Test
    @Timeout(30)
    void theChunkRangeReachesTheModule(@TempDir Path dir) throws IOException {
        // The range is passed in rather than baked into the module, so one
        // module serves every chunk of a loop.
        try (WasmRunner runner = new WasmRunner()) {
            Path module = countingModule(dir);

            assertEquals(500, runner.run(
                    new WasmTask("job-1", 0, module, null, 0, 500), Duration.ofSeconds(10)));
            assertEquals(250, runner.run(
                    new WasmTask("job-1", 1, module, null, 500, 750), Duration.ofSeconds(10)));
        }
    }

    @Test
    @Timeout(30)
    void aModuleIsParsedOncePerPath(@TempDir Path dir) throws IOException {
        // Parsing is the only meaningful cost; invocation is microseconds. A
        // job whose chunks share a module must not pay it repeatedly.
        try (WasmRunner runner = new WasmRunner()) {
            Path module = successModule(dir);
            for (int chunk = 0; chunk < 20; chunk++) {
                runner.run(new WasmTask("job-1", chunk, module, null, chunk * 10, chunk * 10 + 10),
                        Duration.ofSeconds(10));
            }
            assertEquals(1, runner.cachedModuleCount());
        }
    }

    @Test
    @Timeout(30)
    void theSameModuleIsParsedOnceAcrossChunksAndRunners(@TempDir Path dir) throws IOException {
        // What a node actually does, which the per-path test above does not
        // reach: every chunk gets its own sandbox, so the same module arrives
        // at proc/<node>/<job>/0/m.wasm, then .../1/m.wasm, and a new runner
        // is built for each. Keyed by path, on a per-runner map, that is a
        // parse per chunk -- the cost this task type exists to avoid, paid
        // every time anyway.
        WarmModels.standDown();
        byte[] moduleBytes = Files.readAllBytes(successModule(dir));

        for (int chunk = 0; chunk < 5; chunk++) {
            Path sandbox = Files.createDirectories(dir.resolve("chunk-" + chunk));
            Path module = Files.write(sandbox.resolve("module.wasm"), moduleBytes);
            try (WasmRunner runner = new WasmRunner()) {
                runner.run(new WasmTask("job-1", chunk, module, null, 0, 10),
                        Duration.ofSeconds(10));
            }
        }

        assertEquals(1, WarmModels.held(),
                "the same bytes at five paths should be one parsed module");
    }

    @Test
    @Timeout(30)
    void twoDifferentModulesAreParsedSeparately(@TempDir Path dir) throws IOException {
        WarmModels.standDown();

        try (WasmRunner runner = new WasmRunner()) {
            runner.run(new WasmTask("job-1", 0, successModule(dir), null, 0, 10),
                    Duration.ofSeconds(10));
            Path other = Files.createDirectories(dir.resolve("other"));
            runner.run(new WasmTask("job-1", 1, countingModule(other), null, 0, 10),
                    Duration.ofSeconds(10));
        }

        assertEquals(2, WarmModels.held());
    }

    @Test
    @Timeout(30)
    void startupIsOrdersOfMagnitudeCheaperThanCompiling(@TempDir Path dir) throws IOException {
        // The claim that justifies this task type at all. The Ant path costs
        // hundreds of milliseconds per chunk before any work happens.
        try (WasmRunner runner = new WasmRunner()) {
            Path module = successModule(dir);
            WasmTask warm = new WasmTask("job-1", 0, module, null, 0, 1);
            runner.run(warm, Duration.ofSeconds(10));

            long start = System.nanoTime();
            for (int i = 0; i < 1000; i++) {
                runner.run(new WasmTask("job-1", i, module, null, i, i + 1),
                        Duration.ofSeconds(10));
            }
            double msPerChunk = (System.nanoTime() - start) / 1e6 / 1000;

            assertTrue(msPerChunk < 5.0,
                    "a WASM chunk should start in well under a millisecond, took "
                    + msPerChunk + " ms");
        }
    }

    @Test
    @Timeout(30)
    void aMissingEntryPointSaysWhatIsExpected(@TempDir Path dir) throws IOException {
        try (WasmRunner runner = new WasmRunner()) {
            WasmTask task = new WasmTask("job-1", 0, successModule(dir), "notThere", 0, 10);

            WasmRunner.WasmExecutionException thrown = assertThrows(
                    WasmRunner.WasmExecutionException.class,
                    () -> runner.run(task, Duration.ofSeconds(5)));
            assertTrue(thrown.getMessage().contains("notThere"));
            assertTrue(thrown.getMessage().contains("i64"),
                    "should state the expected signature: " + thrown.getMessage());
        }
    }

    @Test
    @Timeout(30)
    void anUnreadableModuleIsReportedClearly(@TempDir Path dir) {
        try (WasmRunner runner = new WasmRunner()) {
            WasmTask task = new WasmTask("job-1", 0, dir.resolve("absent.wasm"), null, 0, 10);

            assertTrue(assertThrows(WasmRunner.WasmExecutionException.class,
                    () -> runner.run(task, Duration.ofSeconds(5)))
                    .getMessage().contains("absent.wasm"));
        }
    }

    @Test
    void rejectsNonsenseTasks(@TempDir Path dir) throws IOException {
        Path module = successModule(dir);

        assertThrows(IllegalArgumentException.class,
                () -> new WasmTask(null, 0, module, null, 0, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new WasmTask("j", 0, null, null, 0, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new WasmTask("j", -1, module, null, 0, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new WasmTask("j", 0, module, null, 10, 0));
    }

    @Test
    void reportsItsIterationCount(@TempDir Path dir) throws IOException {
        assertEquals(250,
                new WasmTask("j", 0, successModule(dir), null, 500, 750).iterationCount());
    }

    @Test
    void defaultsToTheConventionalEntryPoint(@TempDir Path dir) throws IOException {
        assertEquals(WasmTask.DEFAULT_ENTRY_POINT,
                new WasmTask("j", 0, successModule(dir), null, 0, 1).entryPoint());
        assertEquals(WasmTask.DEFAULT_ENTRY_POINT,
                new WasmTask("j", 0, successModule(dir), "  ", 0, 1).entryPoint());
    }
}
