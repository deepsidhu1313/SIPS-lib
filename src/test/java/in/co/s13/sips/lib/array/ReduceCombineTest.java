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

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Splitting an expression whose result crosses rows — the MapReduce shape.
 *
 * <p>{@code RowSplit.plan} handles the case where row i of the result depends
 * only on row i of the data. This handles the other family: results that
 * <em>sum over</em> the rows — a column total, a grand total, a gram matrix.
 * No shard can produce the answer alone, but every shard can produce its
 * partial, and the partials add. Each shard computes over its rows, the master
 * sums what comes back, and the sum is exact in structure because addition is
 * associative over shards — though not over <em>floats</em>, which is why the
 * tests here use tolerances and the docs say so out loud.
 *
 * <p>The soundness rule is linearity. Summing per-shard results is the same as
 * summing whole-data results only for operations that commute with addition:
 * scaling does, so it is hoisted above the combine; {@code exp} and {@code
 * plus} do not, so an expression applying them above the reduction is refused
 * with the reason — applied per shard, {@code plus(1)} would add 1 once per
 * shard instead of once.
 */
class ReduceCombineTest {

    private static final Random RANDOM = new Random(21);

    private static Mat random(int rows, int cols) {
        float[] data = new float[rows * cols];
        for (int i = 0; i < data.length; i++) {
            data[i] = (float) RANDOM.nextGaussian();
        }
        return new Mat(rows, cols, data);
    }

    @Test
    void columnSumsAgreeWithTheLoop() {
        Mat x = new Mat(2, 3, new float[]{1, 2, 3, 10, 20, 30});

        Mat sums = ArrayCompute.eval(Expr.input("x", 2, 3).colSum(), Map.of("x", x));

        assertEquals(1, sums.rows());
        assertEquals(11f, sums.at(0, 0), 0f);
        assertEquals(22f, sums.at(0, 1), 0f);
        assertEquals(33f, sums.at(0, 2), 0f);
    }

    @Test
    void aGrandTotalAgreesWithTheLoop() {
        Mat x = new Mat(2, 2, new float[]{1, 2, 3, 4});

        Mat total = ArrayCompute.eval(Expr.input("x", 2, 2).sumAll(), Map.of("x", x));

        assertEquals(1, total.rows());
        assertEquals(1, total.cols());
        assertEquals(10f, total.at(0, 0), 0f);
    }

    @Test
    void aColumnSumOverAFusedChainStaysOnePass() {
        ArrayCompute.Evaluated evaluated = ArrayCompute.evaluate(
                Expr.input("x", 4, 4).relu().scale(2f).colSum(),
                Map.of("x", new Mat(4, 4, new float[16])));

        assertEquals(1, evaluated.materialised(),
                "the chain should fuse into the column reduction");
    }

    @Test
    void columnSumsSplitIntoPartialsThatAdd() {
        Expr expr = Expr.input("x", 90, 5).relu().colSum();
        Map<String, Mat> inputs = Map.of("x", random(90, 5));

        Mat whole = ArrayCompute.eval(expr, inputs);
        RowSplit.CombinePlan plan = RowSplit.planReduce(expr, Set.of("x"));
        Mat combined = plan.combine(List.of(
                ArrayCompute.eval(plan.expr(30), plan.shard(inputs, 0, 30)),
                ArrayCompute.eval(plan.expr(60), plan.shard(inputs, 30, 90))));

        for (int c = 0; c < 5; c++) {
            assertEquals(whole.at(0, c), combined.at(0, c), 1e-4f, "column " + c);
        }
    }

    @Test
    void theGramMatrixSplitsWithACombine() {
        // The expression RowSplit.plan refuses -- transpose of sharded data --
        // is exactly the one this exists for: X'X is the sum of each shard's
        // Xs'Xs, because matrix multiplication distributes over the row blocks.
        int n = 120;
        int d = 7;
        Expr gram = Expr.input("x", n, d).transpose().matmul(Expr.input("x", n, d));
        Map<String, Mat> inputs = Map.of("x", random(n, d));

        Mat whole = ArrayCompute.eval(gram, inputs);
        RowSplit.CombinePlan plan = RowSplit.planReduce(gram, Set.of("x"));
        Mat combined = plan.combine(List.of(
                ArrayCompute.eval(plan.expr(40), plan.shard(inputs, 0, 40)),
                ArrayCompute.eval(plan.expr(40), plan.shard(inputs, 40, 80)),
                ArrayCompute.eval(plan.expr(40), plan.shard(inputs, 80, 120))));

        for (int i = 0; i < d * d; i++) {
            assertEquals(whole.data()[i], combined.data()[i], 1e-3f, "at " + i);
        }
    }

    @Test
    void scalingIsHoistedAboveTheCombine() {
        // Scaling commutes with addition, so it is applied once to the
        // combined result rather than once per shard -- either would be
        // correct, but hoisting keeps the partials raw and reusable.
        int n = 60;
        Expr scaled = Expr.input("x", n, 4).transpose()
                .matmul(Expr.input("x", n, 4)).scale(1f / (n - 1));
        Map<String, Mat> inputs = Map.of("x", random(n, 4));

        Mat whole = ArrayCompute.eval(scaled, inputs);
        RowSplit.CombinePlan plan = RowSplit.planReduce(scaled, Set.of("x"));
        Mat combined = plan.combine(List.of(
                ArrayCompute.eval(plan.expr(20), plan.shard(inputs, 0, 20)),
                ArrayCompute.eval(plan.expr(40), plan.shard(inputs, 20, 60))));

        for (int i = 0; i < 16; i++) {
            assertEquals(whole.data()[i], combined.data()[i], 1e-4f, "at " + i);
        }
    }

    @Test
    void aNonlinearOpAboveTheReductionIsRefused() {
        // exp(colSum(x)) per shard then summed is not exp of the total; it is
        // not anything. The refusal has to arrive before the wrong number does.
        Expr expr = Expr.input("x", 10, 3).colSum().exp();

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> RowSplit.planReduce(expr, Set.of("x")));

        assertTrue(refused.getMessage().contains("EXP"), refused.getMessage());
    }

    @Test
    void addingAConstantAboveTheReductionIsRefused() {
        // The subtle one: plus(1) applied per shard adds 1 once per shard,
        // so three shards would add 3. Linear in the value, wrong in the count.
        Expr expr = Expr.input("x", 10, 3).colSum().plus(1f);

        assertThrows(IllegalArgumentException.class,
                () -> RowSplit.planReduce(expr, Set.of("x")));
    }

    @Test
    void aRowLocalExpressionIsPointedBackToPlan() {
        // Nothing here crosses rows, so there is nothing to combine; the
        // ordinary row split handles it better.
        Expr expr = Expr.input("x", 10, 3).relu();

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> RowSplit.planReduce(expr, Set.of("x")));

        assertTrue(refused.getMessage().contains("plan("), refused.getMessage());
    }

    @Test
    void aReductionOverReplicatedDataNeedsNoCluster() {
        Expr expr = Expr.input("w", 10, 3).colSum();

        assertThrows(IllegalArgumentException.class,
                () -> RowSplit.planReduce(expr, Set.of()));
    }

    @Test
    void plainPlanPointsAtPlanReduceForCrossRowResults() {
        // The two entries name each other, so a caller landing on the wrong
        // one is told where the right one is.
        Expr gram = Expr.input("x", 10, 3).transpose().matmul(Expr.input("x", 10, 3));

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> RowSplit.plan(gram, Set.of("x")));

        assertTrue(refused.getMessage().contains("planReduce"), refused.getMessage());
    }

    @Test
    void aCrossProductOfTwoShardedInputsCombines() {
        // X'Y with both sharded the same way: each shard contributes its
        // cross-product block. The covariance-between-datasets shape.
        int n = 50;
        Expr cross = Expr.input("x", n, 3).transpose().matmul(Expr.input("y", n, 4));
        Map<String, Mat> inputs = Map.of("x", random(n, 3), "y", random(n, 4));

        Mat whole = ArrayCompute.eval(cross, inputs);
        RowSplit.CombinePlan plan = RowSplit.planReduce(cross, Set.of("x", "y"));
        Mat combined = plan.combine(List.of(
                ArrayCompute.eval(plan.expr(25), plan.shard(inputs, 0, 25)),
                ArrayCompute.eval(plan.expr(25), plan.shard(inputs, 25, 50))));

        for (int i = 0; i < 12; i++) {
            assertEquals(whole.data()[i], combined.data()[i], 1e-4f, "at " + i);
        }
    }

    @Test
    void combiningNothingIsRefused() {
        Expr expr = Expr.input("x", 10, 3).colSum();
        RowSplit.CombinePlan plan = RowSplit.planReduce(expr, Set.of("x"));

        assertThrows(IllegalArgumentException.class, () -> plan.combine(List.of()));
    }
}
