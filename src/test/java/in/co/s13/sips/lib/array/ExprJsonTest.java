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
 * An expression as data, so a manifest can carry it to a worker.
 *
 * <p>Everything else in a SIPS job already travels as JSON — the manifest, the
 * chunk spec, the stage graph. An expression that cannot is an expression that
 * can only run where it was written. Serialised, the same graph a researcher
 * built on a laptop is what every node evaluates, and the shape checks run
 * again on the receiving side — a manifest is input from the network, and
 * parsing it must not be the one place shapes go unverified.
 */
class ExprJsonTest {

    @Test
    void anExpressionSurvivesTheRoundTrip() {
        Expr original = Expr.input("x", 40, 8)
                .matmul(Expr.input("w", 8, 4))
                .addRow(Expr.input("b", 1, 4))
                .relu()
                .scale(2f)
                .rowArgMax();

        Expr landed = Expr.fromJson(original.toJson());

        assertEquals(original.rows(), landed.rows());
        assertEquals(original.cols(), landed.cols());
        assertEquals(original.inputNames(), landed.inputNames());
        assertEquals(original.toJson().toString(), landed.toJson().toString(),
                "re-serialising the parsed graph must reproduce the document");
    }

    @Test
    void theRoundTrippedExpressionComputesTheSameAnswer() {
        Expr original = Expr.input("x", 3, 2).matmul(Expr.input("w", 2, 2)).relu();
        Map<String, Mat> inputs = Map.of(
                "x", new Mat(3, 2, new float[]{1, -2, 3, -4, 5, -6}),
                "w", new Mat(2, 2, new float[]{1, 2, 3, 4}));

        Mat before = ArrayCompute.eval(original, inputs);
        Mat after = ArrayCompute.eval(Expr.fromJson(original.toJson()), inputs);

        assertArrayEquals(before.data(), after.data(), 0f,
                "the wire must not change a single bit of the answer");
    }

    @Test
    void everyOpKindRoundTrips() {
        Expr everything = Expr.input("x", 4, 4)
                .add(Expr.input("y", 4, 4))
                .sub(Expr.input("y", 4, 4))
                .mul(Expr.input("y", 4, 4))
                .matmul(Expr.input("w", 4, 4).transpose())
                .addRow(Expr.input("b", 1, 4))
                .relu().exp().scale(0.5f).plus(-1f);

        for (Expr expr : new Expr[]{everything, everything.rowSum(), everything.rowMax(),
            everything.rowArgMax(), everything.colSum(), everything.sumAll()}) {
            assertEquals(expr.toJson().toString(), Expr.fromJson(expr.toJson())
                    .toJson().toString());
        }
    }

    @Test
    void scalarsSurviveExactly() {
        // 0.1f has no exact decimal representation; a codec that went through
        // a rounded string would compute something subtly different on the
        // worker than on the laptop.
        Expr scaled = Expr.input("x", 2, 2).scale(0.1f).plus(1e-7f);

        Expr landed = Expr.fromJson(scaled.toJson());

        Mat in = new Mat(2, 2, new float[]{1, 2, 3, 4});
        assertArrayEquals(ArrayCompute.eval(scaled, Map.of("x", in)).data(),
                ArrayCompute.eval(landed, Map.of("x", in)).data(), 0f);
    }

    @Test
    void anUnknownOpIsRefusedByName() {
        // A newer laptop sending a newer op to an older node: the node must
        // say which op it does not know, not throw something generic from the
        // middle of a parse.
        JSONObject document = Expr.input("x", 2, 2).relu().toJson();
        document.put("op", "CONV2D");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Expr.fromJson(document));

        assertTrue(refused.getMessage().contains("CONV2D"), refused.getMessage());
    }

    @Test
    void aHostileShapeIsRefusedOnParse() {
        // The manifest is input from the network. The same shape rules that
        // protect the builder protect the parser.
        JSONObject document = Expr.input("x", 2, 3).toJson();
        document.put("rows", -5);

        assertThrows(IllegalArgumentException.class, () -> Expr.fromJson(document));
    }

    @Test
    void mismatchedShapesInsideTheDocumentAreRefused() {
        // Hand-edited or corrupted: a matmul whose children do not agree. The
        // parser rebuilds through the same constructors, so the same check
        // fires.
        Expr valid = Expr.input("a", 2, 3).matmul(Expr.input("b", 3, 2));
        JSONObject document = valid.toJson();
        document.getJSONObject("right").put("rows", 7);

        assertThrows(IllegalArgumentException.class, () -> Expr.fromJson(document));
    }

    @Test
    void theDocumentSaysWhichVersionWroteIt() {
        assertEquals(1, Expr.input("x", 1, 1).toJson().getInt("v"));

        JSONObject future = Expr.input("x", 1, 1).toJson().put("v", 99);
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Expr.fromJson(future));
        assertTrue(refused.getMessage().contains("99"), refused.getMessage());
    }

    @Test
    void sharedSubtreesAreDuplicatedOnTheWireAndTheDocSaysSo() {
        // v1 serialises the tree, so a shared subexpression appears twice in
        // the document and is evaluated twice after the round trip. Not a
        // correctness problem -- the answer is identical -- but a cost one,
        // and this test exists so the behaviour is a documented choice rather
        // than a surprise.
        Expr shared = Expr.input("x", 2, 2).matmul(Expr.input("w", 2, 2));
        Expr expr = shared.relu().add(shared.scale(2f));
        Map<String, Mat> inputs = Map.of(
                "x", new Mat(2, 2, new float[]{1, 2, 3, 4}),
                "w", new Mat(2, 2, new float[]{1, 0, 0, 1}));

        ArrayCompute.Evaluated before = ArrayCompute.evaluate(expr, inputs);
        ArrayCompute.Evaluated after = ArrayCompute.evaluate(
                Expr.fromJson(expr.toJson()), inputs);

        assertArrayEquals(before.value().data(), after.value().data(), 0f);
        assertEquals(1, before.matmuls());
        assertEquals(2, after.matmuls(),
                "the wire flattens sharing; if this starts failing, the codec "
                + "learned references and the docs should be updated");
    }

    @Test
    void nonsenseIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Expr.fromJson(null));
        assertThrows(IllegalArgumentException.class,
                () -> Expr.fromJson(new JSONObject().put("v", 1)));
    }
}
