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
package in.co.s13.sips.lib.job;

import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What travels with a chunk that the manifest cannot carry.
 *
 * <p>The manifest is one file for every chunk of a job. Anything that differs
 * per chunk — the slice, the inputs a pipeline stage was handed, where to leave
 * the result — has to ride separately, and this is it.
 */
class ChunkSpecTest {

    @Test
    void therangeSurvivesTheRoundTrip() {
        ChunkSpec spec = ChunkSpec.range(100, 200).build();

        ChunkSpec reread = ChunkSpec.read(spec.toJSON());

        assertEquals(100, reread.firstIndex());
        assertEquals(200, reread.lastIndexExclusive());
    }

    @Test
    void aPipelineChunkCarriesItsInputsAndOutput() {
        ChunkSpec spec = ChunkSpec.range(0, 8)
                .stage("train-2")
                .shard(3)
                .output("model-3.bin")
                .input("weights.bin")
                .input("shard-3.data")
                .build();

        ChunkSpec reread = ChunkSpec.read(spec.toJSON());

        assertEquals("train-2", reread.stageName().orElseThrow());
        assertEquals(3, reread.shard());
        assertEquals("model-3.bin", reread.output().orElseThrow());
        assertEquals(List.of("weights.bin", "shard-3.data"), reread.inputs());
    }

    @Test
    void aSpecFromAnOlderMasterStillRuns() {
        // Every chunk written before pipelines carries only the two numbers.
        // Refusing it would break the single-loop path this shares.
        ChunkSpec spec = ChunkSpec.read(new JSONObject().put("FIRST", 0).put("LAST", 500));

        assertEquals(500, spec.lastIndexExclusive());
        assertTrue(spec.output().isEmpty());
        assertTrue(spec.inputs().isEmpty());
        assertTrue(spec.stageName().isEmpty());
        assertEquals(-1, spec.shard(), "no shard is not shard zero");
    }

    @Test
    void aShardIsNotAChunkNumber() {
        // Chunk numbers run across the whole job so two stages cannot collide
        // in the distribution table. A task asking "am I worker 3 of 8" wants
        // the shard, and conflating them puts round two's worker 0 at index 8.
        ChunkSpec spec = ChunkSpec.range(3, 4).shard(3).build();

        assertEquals(3, spec.shard());
        assertEquals(3, spec.firstIndex());
    }

    @Test
    void blankFieldsAreAbsentRatherThanEmpty() {
        ChunkSpec spec = ChunkSpec.range(0, 1).output("  ").input("").stage("").build();

        assertTrue(spec.output().isEmpty());
        assertTrue(spec.inputs().isEmpty());
        assertTrue(spec.stageName().isEmpty());
        assertTrue(spec.toJSON().isNull("OUTPUT"));
    }

    @Test
    void nonsenseIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> ChunkSpec.range(10, 0));
        assertThrows(IllegalArgumentException.class, () -> ChunkSpec.read(null));
    }
}
