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
package in.co.s13.sips.lib.ml.pipeline;

import in.co.s13.sips.lib.array.Expr;
import in.co.s13.sips.lib.array.Mat;
import in.co.s13.sips.lib.ml.elastic.ElasticNodeCache;
import in.co.s13.sips.lib.ml.elastic.UnitProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ElasticLayerExecutor} is the composition point between a pipeline
 * stage and a node's own {@link ElasticNodeCache}; these tests check it never
 * changes the real answer, only whether the access was accounted for.
 */
class ElasticLayerExecutorTest {

    private static Mat one(float value) {
        return new Mat(1, 1, new float[]{value});
    }

    /** No weights: the op's constant is baked into the graph itself, same as {@code PipelineRunnerTest}. */
    private static LayerGroup add1() {
        return new LayerGroup("add1", Expr.input("act", 1, 1).plus(1f), "act", Map.of());
    }

    @Test
    void aNodeWithNoConfiguredCacheDelegatesStraightThrough() {
        LayerGroup stage = add1();
        Mat input = one(5);
        Mat expected = stage.forward(input);

        LayerGroup[] seenStage = new LayerGroup[1];
        String[] seenNodeUuid = new String[1];
        Mat[] seenInput = new Mat[1];
        PipelineRunner.LayerExecutor recording = (s, nodeUuid, in) -> {
            seenStage[0] = s;
            seenNodeUuid[0] = nodeUuid;
            seenInput[0] = in;
            return s.forward(in);
        };

        ElasticLayerExecutor executor = new ElasticLayerExecutor(Map.of(), recording);
        Mat result = executor.execute(stage, "node-without-a-cache", input);

        assertEquals(stage, seenStage[0]);
        assertEquals("node-without-a-cache", seenNodeUuid[0]);
        assertEquals(input, seenInput[0]);
        assertArrayEquals(expected.data(), result.data());
    }

    @Test
    void aNodeWithARegisteredProfilePromotesAndStillComputesTheRealAnswer() {
        LayerGroup stage = add1();
        Mat input = one(5);
        Mat expected = stage.forward(input);

        // Cheap to fetch and run hot, expensive to run cold -- the adaptive
        // policy ElasticNodeCache's own tests already exercise will promote
        // on the first access.
        ElasticNodeCache cache = new ElasticNodeCache(1024, true);
        cache.register(new UnitProfile(stage.name(), 100, 1.0, 1.0, 100.0));

        ElasticLayerExecutor executor = new ElasticLayerExecutor(
                Map.of("node-with-cache", cache), PipelineRunner.local());
        Mat result = executor.execute(stage, "node-with-cache", input);

        assertArrayEquals(expected.data(), result.data());
        assertTrue(cache.hotUnits().contains(stage.name()),
                "expected the profile's own numbers to make promotion the cheaper choice");
    }

    @Test
    void aNodeWithACacheButNoRegisteredProfileThrows() {
        LayerGroup stage = add1();
        ElasticNodeCache cache = new ElasticNodeCache(1024, true);
        // Deliberately not registering "add1" on this cache.

        ElasticLayerExecutor executor = new ElasticLayerExecutor(
                Map.of("node-with-cache", cache), PipelineRunner.local());

        assertThrows(IllegalStateException.class,
                () -> executor.execute(stage, "node-with-cache", one(5)));
    }

    @Test
    void constructorRejectsNullArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> new ElasticLayerExecutor(null, PipelineRunner.local()));
        assertThrows(IllegalArgumentException.class,
                () -> new ElasticLayerExecutor(Map.of(), null));
    }
}
