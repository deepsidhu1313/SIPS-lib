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

import in.co.s13.sips.lib.loop.EarlyExit;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static in.co.s13.sips.lib.wasm.TestModules.BREAK_AFTER;
import static in.co.s13.sips.lib.wasm.TestModules.BREAK_ALL;
import static in.co.s13.sips.lib.wasm.TestModules.DROP;
import static in.co.s13.sips.lib.wasm.TestModules.FIRST_LOCAL;
import static in.co.s13.sips.lib.wasm.TestModules.INPUT_READ;
import static in.co.s13.sips.lib.wasm.TestModules.INPUT_SIZE;
import static in.co.s13.sips.lib.wasm.TestModules.LOG;
import static in.co.s13.sips.lib.wasm.TestModules.OUTPUT_WRITE;
import static in.co.s13.sips.lib.wasm.TestModules.call;
import static in.co.s13.sips.lib.wasm.TestModules.concat;
import static in.co.s13.sips.lib.wasm.TestModules.i32;
import static in.co.s13.sips.lib.wasm.TestModules.i64;
import static in.co.s13.sips.lib.wasm.TestModules.i64Store;
import static in.co.s13.sips.lib.wasm.TestModules.localGet;
import static in.co.s13.sips.lib.wasm.TestModules.readAllInput;
import static in.co.s13.sips.lib.wasm.TestModules.readLong;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a WebAssembly chunk can reach outside itself.
 *
 * <p>Without this, a module turns two integers into one and is not a unit of
 * work. With it, a chunk can be handed an image tile and hand back a processed
 * one — and still cannot open a file, a socket or a clock, because the only
 * capabilities it has are the ones bound into it here.
 */
class WasmHostTest {

    private WasmRunner runner;

    @BeforeEach
    void startRunner() {
        runner = new WasmRunner();
    }

    @AfterEach
    void stopRunner() {
        runner.close();
    }

    private WasmTask task(Path module, long first, long last) {
        return new WasmTask("job-host-test", 0, module, null, first, last);
    }

    // ---- input and output ----

    @Test
    @Timeout(30)
    void aChunkCanReadItsInputAndWriteAResult(@TempDir Path dir) throws IOException {
        // The smallest thing that makes a module a unit of work: bytes in,
        // bytes out. This one copies input straight through.
        Path echo = TestModules.hosted(dir, "echo.wasm", 1, concat(
                readAllInput(FIRST_LOCAL),
                i32(0), localGet(FIRST_LOCAL), call(OUTPUT_WRITE),
                i64(WasmTask.SUCCESS)));
        byte[] tile = "a tile of pixels".getBytes(StandardCharsets.UTF_8);
        WasmHost host = WasmHost.builder().input(tile).build();

        assertEquals(WasmTask.SUCCESS, runner.run(task(echo, 0, 16), host, Duration.ofSeconds(10)));
        assertArrayEquals(tile, host.output());
    }

    @Test
    @Timeout(30)
    void inputCanBeReadInPieces(@TempDir Path dir) throws IOException {
        // A module with a small scratch buffer streams a large input rather
        // than needing memory for all of it at once.
        Path halves = TestModules.hosted(dir, "halves.wasm", 0, concat(
                i32(0), i32(4), i32(4), call(INPUT_READ), new byte[]{DROP},
                i32(4), i32(0), i32(4), call(INPUT_READ), new byte[]{DROP},
                i32(0), i32(8), call(OUTPUT_WRITE),
                i64(WasmTask.SUCCESS)));
        WasmHost host = WasmHost.builder()
                .input("HEADbody".getBytes(StandardCharsets.UTF_8)).build();

        runner.run(task(halves, 0, 1), host, Duration.ofSeconds(10));

        assertEquals("bodyHEAD", new String(host.output(), StandardCharsets.UTF_8),
                "each read should start where the module asked, not where the last one ended");
    }

    @Test
    @Timeout(30)
    void aShortInputIsTruncatedRatherThanOverrun(@TempDir Path dir) throws IOException {
        // input_read returns what it actually copied, so a module asking for
        // more than exists learns the real size instead of reading rubbish.
        Path asksTooMuch = TestModules.hosted(dir, "greedy.wasm", 0, concat(
                i32(0), i32(0), i32(4096), call(INPUT_READ),      // returns bytes copied
                i32(0), i32(4), call(OUTPUT_WRITE),               // unused, keeps the stack tidy
                new byte[]{DROP},
                i64(WasmTask.SUCCESS)));
        WasmHost host = WasmHost.builder().input(new byte[]{1, 2, 3}).build();

        runner.run(task(asksTooMuch, 0, 1), host, Duration.ofSeconds(10));

        assertArrayEquals(new byte[]{1, 2, 3, 0}, host.output(),
                "only three bytes existed; the fourth is untouched memory, not overrun input");
    }

    @Test
    @Timeout(30)
    void severalWritesAccumulateInOrder(@TempDir Path dir) throws IOException {
        Path twice = TestModules.hosted(dir, "twice.wasm", 1, concat(
                readAllInput(FIRST_LOCAL),
                i32(0), i32(2), call(OUTPUT_WRITE),
                i32(2), i32(2), call(OUTPUT_WRITE),
                i64(WasmTask.SUCCESS)));
        WasmHost host = WasmHost.builder()
                .input("ABCD".getBytes(StandardCharsets.UTF_8)).build();

        runner.run(task(twice, 0, 1), host, Duration.ofSeconds(10));

        assertEquals("ABCD", new String(host.output(), StandardCharsets.UTF_8));
    }

    @Test
    @Timeout(30)
    void theRangeAndTheOutputChannelWorkTogether(@TempDir Path dir) throws IOException {
        // The combination a real chunk needs: it is told which slice it owns and
        // reports something about it.
        Path reportsRange = TestModules.hosted(dir, "range.wasm", 0, concat(
                i32(0), localGet(0), i64Store(0),
                i32(0), localGet(1), i64Store(8),
                i32(0), i32(16), call(OUTPUT_WRITE),
                i64(WasmTask.SUCCESS)));
        WasmHost host = WasmHost.builder().build();

        runner.run(task(reportsRange, 4096, 8192), host, Duration.ofSeconds(10));

        assertEquals(4096, readLong(host.output(), 0));
        assertEquals(8192, readLong(host.output(), 8));
    }

    @Test
    @Timeout(30)
    void aRunawayModuleCannotFillTheNodesHeap(@TempDir Path dir) throws IOException {
        // The output channel is the one place a module can consume unbounded
        // host memory, so it is capped.
        Path floods = TestModules.hosted(dir, "flood.wasm", 0, concat(
                i32(0), i32(1024), call(OUTPUT_WRITE),
                i32(0), i32(1024), call(OUTPUT_WRITE),
                i64(WasmTask.SUCCESS)));
        WasmHost host = WasmHost.builder().maxOutputBytes(1500).build();

        assertTrue(assertThrows(WasmRunner.WasmExecutionException.class,
                () -> runner.run(task(floods, 0, 1), host, Duration.ofSeconds(10)))
                .getMessage().contains("1500"));
    }

    // ---- logging ----

    @Test
    @Timeout(30)
    void aChunkCanSayWhatItIsDoing(@TempDir Path dir) throws IOException {
        Path talks = TestModules.hosted(dir, "talks.wasm", 1, concat(
                readAllInput(FIRST_LOCAL),
                i32(0), localGet(FIRST_LOCAL), call(LOG),
                i64(WasmTask.SUCCESS)));
        List<String> lines = new ArrayList<>();
        WasmHost host = WasmHost.builder()
                .input("tile 7 of 64 done".getBytes(StandardCharsets.UTF_8))
                .log(lines::add)
                .build();

        runner.run(task(talks, 0, 1), host, Duration.ofSeconds(10));

        assertEquals(List.of("tile 7 of 64 done"), lines);
    }

    // ---- early exit ----

    @Test
    @Timeout(30)
    void aSearchingChunkCanStopTheWholeJobAndCarryItsAnswer(@TempDir Path dir) throws IOException {
        // The scenario break_all exists for: one node finds the key, and the
        // other nodes should stop looking rather than finish their ranges.
        Path finds = TestModules.hosted(dir, "finds.wasm", 1, concat(
                readAllInput(FIRST_LOCAL),
                localGet(0), i32(0), localGet(FIRST_LOCAL), call(BREAK_ALL),
                i64(WasmTask.SUCCESS)));
        EarlyExit exit = new EarlyExit("job-host-test");
        WasmHost host = WasmHost.builder()
                .input("0xCAFE".getBytes(StandardCharsets.UTF_8))
                .earlyExit(exit)
                .build();

        runner.run(task(finds, 900, 1000), host, Duration.ofSeconds(10));

        assertTrue(exit.isStopped());
        assertEquals(900, exit.foundAt().orElseThrow());
        assertEquals("0xCAFE",
                new String((byte[]) exit.result().orElseThrow(), StandardCharsets.UTF_8));
        assertFalse(exit.shouldRunChunk(0, 100), "nobody else needs to keep looking");
    }

    @Test
    @Timeout(30)
    void aConvergingChunkCanBoundTheJobWithoutDiscardingThePrefix(@TempDir Path dir)
            throws IOException {
        Path converges = TestModules.hosted(dir, "converges.wasm", 0, concat(
                localGet(0), call(BREAK_AFTER),
                i64(WasmTask.SUCCESS)));
        EarlyExit exit = new EarlyExit("job-host-test");
        WasmHost host = WasmHost.builder().earlyExit(exit).build();

        runner.run(task(converges, 100, 200), host, Duration.ofSeconds(10));

        assertTrue(exit.shouldRunChunk(0, 50), "the prefix is still part of the answer");
        assertFalse(exit.shouldRunChunk(101, 200));
    }

    @Test
    @Timeout(30)
    void aModuleWrittenForSearchStillRunsInAJobThatDoesNotSearch(@TempDir Path dir)
            throws IOException {
        // No EarlyExit was supplied. Trapping here would mean a module could
        // only ever be used by the job type it was written for.
        Path finds = TestModules.hosted(dir, "finds.wasm", 0, concat(
                localGet(0), i32(0), i32(0), call(BREAK_ALL),
                i64(WasmTask.SUCCESS)));

        assertEquals(WasmTask.SUCCESS,
                runner.run(task(finds, 0, 10), WasmHost.none(), Duration.ofSeconds(10)));
    }

    // ---- isolation ----

    @Test
    @Timeout(30)
    void oneChunksOutputNeverLeaksIntoAnothers(@TempDir Path dir) throws IOException {
        // Host functions are bound to an instance, so an instance cached across
        // chunks would append chunk two's result to chunk one's host. Each run
        // gets a fresh instance.
        Path echo = TestModules.hosted(dir, "echo.wasm", 1, concat(
                readAllInput(FIRST_LOCAL),
                i32(0), localGet(FIRST_LOCAL), call(OUTPUT_WRITE),
                i64(WasmTask.SUCCESS)));

        WasmHost first = WasmHost.builder().input("one".getBytes(StandardCharsets.UTF_8)).build();
        WasmHost second = WasmHost.builder().input("two".getBytes(StandardCharsets.UTF_8)).build();
        runner.run(task(echo, 0, 1), first, Duration.ofSeconds(10));
        runner.run(task(echo, 1, 2), second, Duration.ofSeconds(10));

        assertEquals("one", new String(first.output(), StandardCharsets.UTF_8));
        assertEquals("two", new String(second.output(), StandardCharsets.UTF_8));
        assertEquals(1, runner.cachedModuleCount(), "the parse is still shared");
    }

    @Test
    @Timeout(30)
    void memoryDoesNotSurviveBetweenChunks(@TempDir Path dir) throws IOException {
        // A fresh instance means a fresh linear memory. If it did not, a chunk
        // could read what the previous chunk left behind.
        Path leaks = TestModules.hosted(dir, "leaks.wasm", 1, concat(
                i32(0), i32(0), i32(1), call(INPUT_READ), new byte[]{DROP},
                i32(0), i32(1), call(OUTPUT_WRITE),
                i64(WasmTask.SUCCESS)));

        WasmHost wrote = WasmHost.builder().input(new byte[]{(byte) 0xEE}).build();
        runner.run(task(leaks, 0, 1), wrote, Duration.ofSeconds(10));

        WasmHost fresh = WasmHost.builder().input(new byte[0]).build();
        runner.run(task(leaks, 1, 2), fresh, Duration.ofSeconds(10));

        assertArrayEquals(new byte[]{0}, fresh.output(),
                "a chunk with no input must see zeroed memory, not the last chunk's bytes");
    }

    @Test
    @Timeout(30)
    void aModuleImportingSomethingElseIsRefusedWithAnExplanation(@TempDir Path dir)
            throws IOException {
        // The capability list is the security model, so an unknown import has to
        // fail loudly rather than be quietly stubbed out.
        Path reachesOut = TestModules.hosted(dir, "reaches.wasm", 0, i64(WasmTask.SUCCESS));
        byte[] bytes = java.nio.file.Files.readAllBytes(reachesOut);
        // Rename the imported "log" to "env" -- same length, still a valid module.
        for (int i = 0; i < bytes.length - 2; i++) {
            if (bytes[i] == 'l' && bytes[i + 1] == 'o' && bytes[i + 2] == 'g') {
                bytes[i] = 'e';
                bytes[i + 1] = 'n';
                bytes[i + 2] = 'v';
                break;
            }
        }
        Path tampered = dir.resolve("tampered.wasm");
        java.nio.file.Files.write(tampered, bytes);

        assertTrue(assertThrows(WasmRunner.WasmExecutionException.class,
                () -> runner.run(task(tampered, 0, 1), WasmHost.none(), Duration.ofSeconds(10)))
                .getMessage().contains("WasmHost"),
                "the error should point at the list of what a module may import");
    }

    @Test
    void aHostRejectsNonsense() {
        assertThrows(IllegalArgumentException.class, () -> WasmHost.builder().log(null));
        assertThrows(IllegalArgumentException.class, () -> WasmHost.builder().maxOutputBytes(0));
    }

    @Test
    void inputAndOutputAreCopiedNotShared() {
        byte[] mutable = {1, 2, 3};
        WasmHost host = WasmHost.builder().input(mutable).build();
        mutable[0] = 99;

        assertArrayEquals(new byte[]{1, 2, 3}, host.input(),
                "a caller mutating its array must not change what the module sees");
    }
}
