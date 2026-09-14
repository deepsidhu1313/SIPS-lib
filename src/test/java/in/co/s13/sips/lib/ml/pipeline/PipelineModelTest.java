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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineModelTest {

    /** A minimal, valid stage whose weight is exactly {@code floats} floats — {@code 4 * floats} bytes. */
    private static LayerGroup stage(String name, int floats) {
        Expr graph = Expr.input("act", 1, 1).matmul(Expr.input("w", 1, floats));
        Mat weight = new Mat(1, floats, new float[floats]);
        return new LayerGroup(name, graph, "act", Map.of("w", weight));
    }

    @Test
    void anEmptyStageListIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PipelineModel(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new PipelineModel(null));
    }

    @Test
    void duplicateStageNamesAreRejected() {
        LayerGroup first = stage("encoder", 1);
        LayerGroup duplicate = stage("encoder", 2);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new PipelineModel(List.of(first, duplicate)));
        assertTrue(thrown.getMessage().contains("encoder"), thrown.getMessage());
    }

    @Test
    void stageCountMatchesTheList() {
        PipelineModel model = new PipelineModel(List.of(stage("a", 1), stage("b", 1), stage("c", 1)));

        assertEquals(3, model.stageCount());
    }

    @Test
    void totalWeightBytesSumsEveryStage() {
        // 2 floats + 3 floats = 5 floats, 4 bytes each.
        PipelineModel model = new PipelineModel(List.of(stage("a", 2), stage("b", 3)));

        assertEquals(20L, model.totalWeightBytes());
    }
}
