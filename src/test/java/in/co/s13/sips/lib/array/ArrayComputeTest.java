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
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Running an expression, and the two things that make it worth having.
 *
 * <p>Correctness is table stakes: every op must agree with the loop anyone
 * would have written by hand. The value is in what the evaluator does with the
 * whole graph — matmuls go through {@code MatrixCompute} and land on whatever
 * device the node has, and chains of memory-bound ops are fused into single
 * passes instead of materialising an intermediate array per op.
 *
 * <p>Fusion is asserted by counting, not trusted from a comment: an elementwise
 * op moves twelve bytes per FLOP, so the pass count over the data <em>is</em>
 * its cost, and a fusion that silently stopped fusing would be invisible in a
 * correctness test.
 */
class ArrayComputeTest {

    private static Mat mat(int rows, int cols, float... values) {
        return new Mat(rows, cols, values);
    }

    @Test
    void aMatmulAgreesWithTheHandWrittenLoop() {
        Expr expr = Expr.input("a", 2, 3).matmul(Expr.input("b", 3, 2));
        Mat a = mat(2, 3, 1, 2, 3, 4, 5, 6);
        Mat b = mat(3, 2, 7, 8, 9, 10, 11, 12);

        Mat result = ArrayCompute.eval(expr, Map.of("a", a, "b", b));

        assertArrayEquals(new float[]{58, 64, 139, 154}, result.data(), 1e-4f);
    }

    @Test
    void elementwiseAndScalarOpsAgreeWithTheLoop() {
        Expr x = Expr.input("x", 1, 4);
        Expr y = Expr.input("y", 1, 4);
        Mat xv = mat(1, 4, 1, -2, 3, -4);
        Mat yv = mat(1, 4, 10, 20, 30, 40);

        assertArrayEquals(new float[]{11, 18, 33, 36},
                ArrayCompute.eval(x.add(y), Map.of("x", xv, "y", yv)).data(), 0f);
        assertArrayEquals(new float[]{-9, -22, -27, -44},
                ArrayCompute.eval(x.sub(y), Map.of("x", xv, "y", yv)).data(), 0f);
        assertArrayEquals(new float[]{10, -40, 90, -160},
                ArrayCompute.eval(x.mul(y), Map.of("x", xv, "y", yv)).data(), 0f);
        assertArrayEquals(new float[]{1, 0, 3, 0},
                ArrayCompute.eval(x.relu(), Map.of("x", xv)).data(), 0f);
        assertArrayEquals(new float[]{3, -6, 9, -12},
                ArrayCompute.eval(x.scale(3f), Map.of("x", xv)).data(), 0f);
        assertArrayEquals(new float[]{2, -1, 4, -3},
                ArrayCompute.eval(x.plus(1f), Map.of("x", xv)).data(), 0f);
    }

    @Test
    void expAgreesWithMath() {
        Mat result = ArrayCompute.eval(Expr.input("x", 1, 2).exp(),
                Map.of("x", mat(1, 2, 0, 1)));

        assertEquals(1f, result.data()[0], 1e-6f);
        assertEquals((float) Math.E, result.data()[1], 1e-5f);
    }

    @Test
    void transposeAndReductionsAgreeWithTheLoop() {
        Expr x = Expr.input("x", 2, 3);
        Mat xv = mat(2, 3, 1, 5, 3, 4, 2, 6);

        assertArrayEquals(new float[]{1, 4, 5, 2, 3, 6},
                ArrayCompute.eval(x.transpose(), Map.of("x", xv)).data(), 0f);
        assertArrayEquals(new float[]{9, 12},
                ArrayCompute.eval(x.rowSum(), Map.of("x", xv)).data(), 0f);
        assertArrayEquals(new float[]{5, 6},
                ArrayCompute.eval(x.rowMax(), Map.of("x", xv)).data(), 0f);
        assertArrayEquals(new float[]{1, 2},
                ArrayCompute.eval(x.rowArgMax(), Map.of("x", xv)).data(), 0f);
    }

    @Test
    void rowArgMaxTiesGoToTheFirst() {
        // Deterministic, whichever device or fusion path evaluated it: two
        // nodes evaluating the same expression must pick the same winner.
        Mat result = ArrayCompute.eval(Expr.input("x", 1, 3).rowArgMax(),
                Map.of("x", mat(1, 3, 7, 7, 7)));

        assertEquals(0f, result.data()[0], 0f);
    }

    @Test
    void aRowVectorIsAddedToEveryRow() {
        Expr biased = Expr.input("x", 2, 3).addRow(Expr.input("b", 1, 3));

        Mat result = ArrayCompute.eval(biased, Map.of(
                "x", mat(2, 3, 1, 2, 3, 4, 5, 6),
                "b", mat(1, 3, 10, 20, 30)));

        assertArrayEquals(new float[]{11, 22, 33, 14, 25, 36}, result.data(), 0f);
    }

    @Test
    void wholeProblemsComposeFromTheOps() {
        // The claim the package makes: a real computation is these ops chained.
        // relu(X.W + b), then the predicted class per row.
        Expr x = Expr.input("x", 2, 2);
        Expr w = Expr.input("w", 2, 2);
        Expr b = Expr.input("b", 1, 2);
        Expr predicted = x.matmul(w).addRow(b).relu().rowArgMax();

        Mat result = ArrayCompute.eval(predicted, Map.of(
                "x", mat(2, 2, 1, 0, 0, 1),
                "w", mat(2, 2, 2, -1, -1, 2),
                "b", mat(1, 2, 0, 0)));

        assertArrayEquals(new float[]{0, 1}, result.data(), 0f);
    }

    @Test
    void chainsOfMemoryBoundOpsAreFusedIntoOnePass() {
        // The measurable half of the design. Four elementwise ops naively
        // materialise four arrays; fused, they are one pass producing one.
        // An elementwise op is ~1 FLOP per 12 bytes moved, so the pass count
        // over the data is the cost.
        Expr chained = Expr.input("x", 8, 8).relu().scale(2f).plus(1f).exp();

        ArrayCompute.Evaluated evaluated = ArrayCompute.evaluate(chained,
                Map.of("x", new Mat(8, 8, new float[64])));

        assertEquals(1, evaluated.materialised(),
                "four chained elementwise ops should produce exactly one array");
    }

    @Test
    void fusionDoesNotChangeTheAnswer() {
        Random random = new Random(7);
        float[] data = new float[100];
        for (int i = 0; i < data.length; i++) {
            data[i] = (float) random.nextGaussian();
        }
        Mat x = new Mat(10, 10, data);

        Mat fused = ArrayCompute.eval(Expr.input("x", 10, 10).relu().scale(2f).plus(1f),
                Map.of("x", x));

        for (int i = 0; i < data.length; i++) {
            assertEquals(Math.max(0f, data[i]) * 2f + 1f, fused.data()[i], 0f,
                    "at " + i);
        }
    }

    @Test
    void aFusedChainEndingInAReductionStaysOnePass() {
        // The reduction consumes the fused values as they are produced; there
        // is no reason to write the intermediate matrix out at all.
        Expr expr = Expr.input("x", 4, 4).relu().scale(2f).rowSum();

        ArrayCompute.Evaluated evaluated = ArrayCompute.evaluate(expr,
                Map.of("x", new Mat(4, 4, new float[16])));

        assertEquals(1, evaluated.materialised(),
                "the chain should fuse into the reduction, producing only the column");
    }

    @Test
    void aSharedSubexpressionIsEvaluatedOnce() {
        // The same matmul feeding two consumers must not run twice: the graph
        // is a DAG, not a tree, and O(n^3) work is the whole cost.
        Expr shared = Expr.input("x", 2, 2).matmul(Expr.input("w", 2, 2));
        Expr expr = shared.relu().add(shared.scale(2f));

        ArrayCompute.Evaluated evaluated = ArrayCompute.evaluate(expr, Map.of(
                "x", mat(2, 2, 1, 2, 3, 4),
                "w", mat(2, 2, 1, 0, 0, 1)));

        assertEquals(1, evaluated.matmuls(), "one matmul in the graph, one executed");
    }

    @Test
    void evaluationAgainstTheWrongShapeIsRefused() {
        Expr expr = Expr.input("x", 2, 3).relu();

        assertThrows(IllegalArgumentException.class,
                () -> ArrayCompute.eval(expr, Map.of("x", mat(3, 2, 1, 2, 3, 4, 5, 6))));
    }

    @Test
    void aMissingInputIsNamedRatherThanNullPointered() {
        Expr expr = Expr.input("x", 2, 2).add(Expr.input("y", 2, 2));

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> ArrayCompute.eval(expr, Map.of("x", mat(2, 2, 1, 2, 3, 4))));

        assertTrue(refused.getMessage().contains("y"), refused.getMessage());
    }

    @Test
    void bigMatmulsAgreeWithTheCpuWhereverTheyRan() {
        // Large enough that MatrixCompute may route it to an accelerator; the
        // answer must agree with the CPU within float tolerance regardless of
        // which device the node happened to have.
        int n = 128;
        Random random = new Random(3);
        float[] av = new float[n * n];
        float[] bv = new float[n * n];
        for (int i = 0; i < n * n; i++) {
            av[i] = (float) random.nextGaussian();
            bv[i] = (float) random.nextGaussian();
        }
        Expr expr = Expr.input("a", n, n).matmul(Expr.input("b", n, n));

        Mat viaEvaluator = ArrayCompute.eval(expr,
                Map.of("a", new Mat(n, n, av), "b", new Mat(n, n, bv)));
        float[] onCpu = in.co.s13.sips.lib.accelerator.MatrixCompute
                .multiplyOnCpu(av, bv, n, n, n);

        for (int i = 0; i < n * n; i++) {
            assertEquals(onCpu[i], viaEvaluator.data()[i], 1e-2f, "at " + i);
        }
    }

    @Test
    void matIsBoundsChecked() {
        assertThrows(IllegalArgumentException.class, () -> new Mat(2, 2, new float[3]));
        assertThrows(IllegalArgumentException.class, () -> new Mat(0, 2, new float[0]));
        assertThrows(IllegalArgumentException.class, () -> new Mat(2, 2, null));
    }
}
