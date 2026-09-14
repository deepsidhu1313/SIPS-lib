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
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A layer group is a small, real computation: a graph plus the weights it
 * reads, run through the same {@code Expr}/{@code ArrayCompute} evaluator
 * every other array-op user goes through. These tests hand-compute a small
 * matmul-then-relu case so a wiring mistake shows up as a wrong number, not
 * just an exception.
 */
class LayerGroupTest {

    /** x: 2x3, w: 3x2 — chosen so relu(x @ w) zeroes exactly one row. */
    private static LayerGroup matmulReluStage() {
        Expr graph = Expr.input("x", 2, 3).matmul(Expr.input("w", 3, 2)).relu();
        Mat weight = new Mat(3, 2, new float[]{
            1, 0,
            0, 1,
            1, 1,
        });
        return new LayerGroup("matmul-relu", graph, "x", Map.of("w", weight));
    }

    @Test
    void forwardComputesTheHandCheckedAnswer() {
        LayerGroup stage = matmulReluStage();
        Mat x = new Mat(2, 3, new float[]{
            1, -2, 3,
            4, 5, -6,
        });

        // Row 0: [1*1+-2*0+3*1, 1*0+-2*1+3*1] = [4, 1], both positive.
        // Row 1: [4*1+5*0+-6*1, 4*0+5*1+-6*1] = [-2, -1], both negative -> 0 after relu.
        Mat result = stage.forward(x);

        assertEquals(2, result.rows());
        assertEquals(2, result.cols());
        assertArrayEquals(new float[]{4, 1, 0, 0}, result.data());
    }

    @Test
    void weightBytesCountsFourPerFloat() {
        LayerGroup stage = matmulReluStage();

        // The weight is a 3x2 matrix: 6 floats, 4 bytes each.
        assertEquals(24L, stage.weightBytes());
    }

    @Test
    void aBlankNameIsRejected() {
        Expr graph = Expr.input("x", 1, 1);
        assertThrows(IllegalArgumentException.class,
                () -> new LayerGroup("  ", graph, "x", Map.of()));
    }

    @Test
    void anUnboundGraphInputIsRejected() {
        // The graph reads both "x" and "w", but only "x" is bound; "w" is
        // neither the activation nor a supplied weight.
        Expr graph = Expr.input("x", 2, 3).matmul(Expr.input("w", 3, 2)).relu();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new LayerGroup("stage", graph, "x", Map.of()));
        assertTrue(thrown.getMessage().contains("w"), thrown.getMessage());
    }

    @Test
    void aWeightNameNotInTheGraphIsRejected() {
        Expr graph = Expr.input("x", 1, 1);
        Mat bogus = new Mat(1, 1, new float[]{1});

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new LayerGroup("stage", graph, "x", Map.of("not-an-input", bogus)));
        assertTrue(thrown.getMessage().contains("not-an-input"), thrown.getMessage());
    }

    @Test
    void anActivationInputNameNotInTheGraphIsRejected() {
        Expr graph = Expr.input("x", 1, 1);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new LayerGroup("stage", graph, "not-x", Map.of()));
        assertTrue(thrown.getMessage().contains("not-x"), thrown.getMessage());
    }
}
