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
package in.co.s13.sips.lib.array;

import java.util.Map;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One self-contained unit of work: an expression and everything it reads.
 *
 * <p>{@code Expr.toJson} moves the computation; this moves the computation
 * <em>and its data</em> as a single document, which is what a phone actually
 * receives — a dialled-in worker gets one frame and must need nothing else to
 * produce its answer. Matrix bytes travel in the little-endian layout
 * {@code Tensors} defines, the same one WASM linear memory uses, so every
 * platform reads the same floats without a byte-order decision.
 *
 * <p>Decoding validates everything the evaluator would: a worker must refuse a
 * bad task at the frame boundary, before it burns battery on it.
 */
class ExprTaskTest {

    private static ExprTask task() {
        Expr expr = Expr.input("x", 2, 3).matmul(Expr.input("w", 3, 2)).relu();
        return new ExprTask(expr, Map.of(
                "x", new Mat(2, 3, new float[]{1, -2, 3, 4, 5, -6}),
                "w", new Mat(3, 2, new float[]{1, 2, 3, 4, 5, 6})));
    }

    @Test
    void aTaskSurvivesTheRoundTripAndComputesTheSameAnswer() {
        ExprTask original = task();

        ExprTask landed = ExprTask.fromJson(original.toJson());

        assertArrayEquals(original.run().data(), landed.run().data(), 0f,
                "the wire must not change a single bit of the answer");
    }

    @Test
    void runEvaluatesTheExpressionAgainstTheCarriedInputs() {
        Mat answer = task().run();

        assertEquals(2, answer.rows());
        assertEquals(2, answer.cols());
        // Row 0: [1,-2,3]·columns of w, relu'd.
        assertArrayEquals(new float[]{10f, 12f, 0f, 0f}, answer.data(), 1e-4f);
    }

    @Test
    void theAnswerTravelsAsTensorBytes() {
        // What the worker actually sends back: the little-endian float layout
        // both WASM memory and the ml codec already use. One layout everywhere
        // means a Swift worker and a Java master cannot disagree about bytes.
        byte[] wire = task().runToBytes();

        float[] decoded = in.co.s13.sips.lib.ml.Tensors.fromBytes(wire);
        assertArrayEquals(new float[]{10f, 12f, 0f, 0f}, decoded, 1e-4f);
    }

    @Test
    void aTaskMissingAnInputIsRefusedAtConstruction() {
        Expr needsTwo = Expr.input("x", 2, 2).add(Expr.input("y", 2, 2));

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> new ExprTask(needsTwo, Map.of("x", new Mat(2, 2, new float[4]))));

        assertTrue(refused.getMessage().contains("y"), refused.getMessage());
    }

    @Test
    void aTaskWithAWronglyShapedInputIsRefusedAtConstruction() {
        Expr expr = Expr.input("x", 2, 3);

        assertThrows(IllegalArgumentException.class,
                () -> new ExprTask(expr, Map.of("x", new Mat(3, 2, new float[6]))));
    }

    @Test
    void aCorruptedPayloadIsRefusedOnDecode() {
        // Truncated matrix bytes must fail at the frame boundary, before the
        // worker spends battery computing on garbage.
        JSONObject document = task().toJson();
        JSONObject inputs = document.getJSONObject("inputs");
        JSONObject x = inputs.getJSONObject("x");
        x.put("data", x.getString("data").substring(4));

        assertThrows(IllegalArgumentException.class, () -> ExprTask.fromJson(document));
    }

    @Test
    void aHostileShapeIsRefusedOnDecode() {
        JSONObject document = task().toJson();
        document.getJSONObject("inputs").getJSONObject("x").put("rows", 1_000_000);

        assertThrows(IllegalArgumentException.class, () -> ExprTask.fromJson(document));
    }

    @Test
    void anExtraInputTheExpressionNeverReadsIsRefused() {
        // On a phone, bytes are battery. A task carrying data its expression
        // never reads is at best waste and at worst a smuggling channel.
        Expr expr = Expr.input("x", 1, 1);
        JSONObject document = new ExprTask(expr, Map.of("x", new Mat(1, 1, new float[]{1})))
                .toJson();
        document.getJSONObject("inputs").put("smuggled", document.getJSONObject("inputs")
                .getJSONObject("x"));

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> ExprTask.fromJson(document));

        assertTrue(refused.getMessage().contains("smuggled"), refused.getMessage());
    }

    @Test
    void nonsenseIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> ExprTask.fromJson(null));
        assertThrows(IllegalArgumentException.class,
                () -> new ExprTask(null, Map.of()));
    }
}
