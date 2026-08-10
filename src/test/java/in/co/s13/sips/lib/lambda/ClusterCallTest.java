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
package in.co.s13.sips.lib.lambda;

import in.co.s13.sips.lib.job.Job;
import in.co.s13.sips.lib.job.Stage;
import in.co.s13.sips.lib.manifest.TaskType;
import in.co.s13.sips.lib.wasm.TestModules;
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

import static in.co.s13.sips.lib.wasm.TestModules.FIRST_LOCAL;
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
 * Submitting a function and getting an answer.
 *
 * <p>Deliberately the smallest thing SIPS can be asked to do — bytes in, bytes
 * out, no manifest and no project directory. Whether that is worth having comes
 * down to whether the call is cheap enough to be worth scheduling at all, which
 * is why the module runs as WebAssembly and not as anything that needs compiling
 * first.
 */
class ClusterCallTest {

    private LocalCallDispatcher dispatcher;

    @BeforeEach
    void open() {
        dispatcher = new LocalCallDispatcher();
    }

    @AfterEach
    void close() {
        dispatcher.close();
    }

    /** A module that copies its input to its output. */
    private static Path echo(Path dir) throws IOException {
        return TestModules.hosted(dir, "echo.wasm", 1, concat(
                readAllInput(FIRST_LOCAL),
                i32(0), localGet(FIRST_LOCAL), call(OUTPUT_WRITE),
                i64(0)));
    }

    @Test
    @Timeout(30)
    void bytesInBytesOut(@TempDir Path dir) throws IOException {
        byte[] result = ClusterCall.of(echo(dir), "an image".getBytes(StandardCharsets.UTF_8))
                .on(dispatcher)
                .orThrow();

        assertEquals("an image", new String(result, StandardCharsets.UTF_8));
    }

    @Test
    @Timeout(30)
    void aResultSaysWhereItRanAndHowLongItTook(@TempDir Path dir) throws IOException {
        // A caller who chose a placement policy cannot tell whether it did
        // anything unless the result says which node was picked.
        CallResult result = ClusterCall.of(echo(dir), new byte[]{1}).on(dispatcher);

        assertTrue(result.isSuccess());
        assertEquals(LocalCallDispatcher.LOCAL_NODE, result.node().orElseThrow());
        assertFalse(result.took().isNegative());
    }

    @Test
    @Timeout(30)
    void aCallIsOneInvocationUnlessAskedOtherwise(@TempDir Path dir) throws IOException {
        // The range defaults to [0, 1): a call is a function, not a loop.
        Path reportsRange = TestModules.hosted(dir, "range.wasm", 0, concat(
                i32(0), localGet(0), i64Store(0),
                i32(0), localGet(1), i64Store(8),
                i32(0), i32(16), call(OUTPUT_WRITE),
                i64(0)));

        byte[] narrow = ClusterCall.of(reportsRange).on(dispatcher).orThrow();
        assertEquals(0, readLong(narrow, 0));
        assertEquals(1, readLong(narrow, 8));

        byte[] wide = ClusterCall.of(reportsRange).range(100, 200).on(dispatcher).orThrow();
        assertEquals(100, readLong(wide, 0));
        assertEquals(200, readLong(wide, 8));
    }

    @Test
    @Timeout(30)
    void aFunctionCanSayWhatItIsDoing(@TempDir Path dir) throws IOException {
        List<String> lines = new ArrayList<>();
        Path talks = TestModules.hosted(dir, "talks.wasm", 1, concat(
                readAllInput(FIRST_LOCAL),
                i32(0), localGet(FIRST_LOCAL), call(LOG),
                i64(0)));

        try (LocalCallDispatcher watching = new LocalCallDispatcher(lines::add)) {
            ClusterCall.of(talks, "resizing to 128px".getBytes(StandardCharsets.UTF_8))
                    .on(watching);
        }

        assertEquals(List.of("resizing to 128px"), lines);
    }

    // ---- failure is an outcome, not an exception ----

    @Test
    @Timeout(30)
    void aFunctionReportingFailureComesBackAsAFailedResult(@TempDir Path dir)
            throws IOException {
        Path fails = TestModules.bare(dir, "fails.wasm", i64(7));

        CallResult result = ClusterCall.of(fails).on(dispatcher);

        assertFalse(result.isSuccess());
        assertEquals(7, result.status());
        assertTrue(result.failureReason().orElseThrow().contains("7"));
    }

    @Test
    @Timeout(30)
    void aTrappingFunctionDoesNotTakeTheCallerWithIt(@TempDir Path dir) throws IOException {
        Path traps = TestModules.bare(dir, "traps.wasm",
                concat(new byte[]{TestModules.UNREACHABLE}, i64(0)));

        CallResult result = ClusterCall.of(traps).on(dispatcher);

        assertFalse(result.isSuccess());
        assertTrue(result.failureReason().isPresent());
    }

    @Test
    @Timeout(30)
    void aMissingModuleIsAFailedCall(@TempDir Path dir) {
        CallResult result = ClusterCall.of(dir.resolve("absent.wasm")).on(dispatcher);

        assertFalse(result.isSuccess());
        assertTrue(result.failureReason().orElseThrow().contains("absent.wasm"));
    }

    @Test
    @Timeout(30)
    void orThrowIsForCallersWhoWouldRatherNotCheck(@TempDir Path dir) throws IOException {
        Path fails = TestModules.bare(dir, "fails.wasm", i64(3));

        assertTrue(assertThrows(IllegalStateException.class,
                () -> ClusterCall.of(fails).on(dispatcher).orThrow())
                .getMessage().contains("3"));
    }

    // ---- it is a one-stage job, and says so ----

    @Test
    @Timeout(30)
    void aCallIsAPipelineOfExactlyOneStage(@TempDir Path dir) throws IOException {
        // Which is why it needs no execution machinery of its own: the same
        // graph, sequencer and placement policies apply.
        Job job = ClusterCall.of(echo(dir), new byte[]{1})
                .timeout(Duration.ofSeconds(5))
                .build()
                .asJob("thumbnail-request");

        assertEquals(1, job.stages().size());
        Stage stage = job.stage(ClusterCall.STAGE_NAME).orElseThrow();
        assertEquals(Stage.Kind.SINGLE, stage.kind());
        assertEquals(TaskType.WASM, stage.taskType());
        assertEquals(Duration.ofSeconds(5), stage.timeout().orElseThrow());
    }

    @Test
    @Timeout(30)
    void aCallAlwaysHasATimeout(@TempDir Path dir) throws IOException {
        // Unlike a stage. Something waiting on a result needs an answer, and
        // "never" is not one.
        assertFalse(ClusterCall.of(echo(dir)).build().timeout().isZero());
    }

    @Test
    @Timeout(30)
    void repeatedCallsShareTheParse(@TempDir Path dir) throws IOException {
        // What makes a request-sized unit of work worth scheduling: after the
        // first call the module is already parsed, and invoking it is
        // microseconds.
        Path module = echo(dir);
        ClusterCall.of(module, new byte[]{1}).on(dispatcher);

        long start = System.nanoTime();
        for (int i = 0; i < 200; i++) {
            ClusterCall.of(module, new byte[]{(byte) i}).on(dispatcher);
        }
        double msPerCall = (System.nanoTime() - start) / 1e6 / 200;

        assertTrue(msPerCall < 5.0, "a warm call took " + msPerCall + " ms");
    }

    @Test
    @Timeout(30)
    void oneCallsResultIsNotAnothers(@TempDir Path dir) throws IOException {
        Path module = echo(dir);

        byte[] first = ClusterCall.of(module, "one".getBytes(StandardCharsets.UTF_8))
                .on(dispatcher).orThrow();
        byte[] second = ClusterCall.of(module, "two".getBytes(StandardCharsets.UTF_8))
                .on(dispatcher).orThrow();

        assertArrayEquals("one".getBytes(StandardCharsets.UTF_8), first);
        assertArrayEquals("two".getBytes(StandardCharsets.UTF_8), second);
    }

    // ---- validation ----

    @Test
    void nonsenseIsRefused(@TempDir Path dir) {
        assertThrows(IllegalArgumentException.class, () -> ClusterCall.of(null));
        assertThrows(IllegalArgumentException.class,
                () -> ClusterCall.of(dir.resolve("m.wasm")).range(10, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ClusterCall.of(dir.resolve("m.wasm")).timeout(Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> ClusterCall.of(dir.resolve("m.wasm")).build().on(null));
    }

    @Test
    void inputIsCopiedNotShared(@TempDir Path dir) {
        byte[] mutable = {1, 2, 3};
        ClusterCall call = ClusterCall.of(dir.resolve("m.wasm"), mutable).build();
        mutable[0] = 99;

        assertArrayEquals(new byte[]{1, 2, 3}, call.input());
    }

    @Test
    void aResultsBytesAreCopiedNotShared() {
        byte[] produced = {1, 2, 3};
        CallResult result = CallResult.success(produced, "node-a", Duration.ofMillis(5));
        result.output()[0] = 99;

        assertArrayEquals(new byte[]{1, 2, 3}, result.output());
    }
}
