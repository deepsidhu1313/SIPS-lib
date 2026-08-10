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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether an expression can be cut into row shards, decided from the graph.
 *
 * <p>This is what makes the op set a distribution story rather than a local
 * convenience: {@code parallelFor} and {@code ShardPlan} already know how to
 * split a range of rows across nodes, but only if splitting is <em>sound</em> —
 * and soundness is a property of the expression, not of the data. Elementwise
 * ops and row-reductions never look across rows; a matmul is row-local exactly
 * when its right operand is the same on every shard; a transpose of sharded
 * data mixes rows across shards and is not row-local at all.
 *
 * <p>The analysis answers it from the graph, so a caller cannot distribute an
 * expression whose shards would each compute something subtly different — the
 * kind of wrong that returns plausible numbers.
 */
class RowSplitTest {

    private static final Random RANDOM = new Random(11);

    private static Mat random(int rows, int cols) {
        float[] data = new float[rows * cols];
        for (int i = 0; i < data.length; i++) {
            data[i] = (float) RANDOM.nextGaussian();
        }
        return new Mat(rows, cols, data);
    }

    @Test
    void aRowLocalPipelineIsSplittable() {
        // The MLP shape: sharded data against replicated weights.
        Expr expr = Expr.input("x", 100, 8)
                .matmul(Expr.input("w", 8, 4))
                .addRow(Expr.input("b", 1, 4))
                .relu()
                .rowArgMax();

        RowSplit.Plan plan = RowSplit.plan(expr, Set.of("x"));

        assertEquals(100, plan.rows());
    }

    @Test
    void shardsComputeExactlyWhatTheWholeWould() {
        // The correctness contract: concatenated shard results must be
        // indistinguishable from the unsharded evaluation, or distribution
        // quietly changes answers.
        Expr expr = Expr.input("x", 90, 6)
                .matmul(Expr.input("w", 6, 5))
                .addRow(Expr.input("b", 1, 5))
                .relu();
        Map<String, Mat> inputs = Map.of(
                "x", random(90, 6), "w", random(6, 5), "b", random(1, 5));
        RowSplit.Plan plan = RowSplit.plan(expr, Set.of("x"));

        Mat whole = ArrayCompute.eval(expr, inputs);
        // plan.expr(n) rebuilds the graph at the shard's row count -- the
        // plan knows which inputs are sharded, which Expr itself does not.
        Mat sharded = RowSplit.concat(List.of(
                ArrayCompute.eval(plan.expr(30), plan.shard(inputs, 0, 30)),
                ArrayCompute.eval(plan.expr(45), plan.shard(inputs, 30, 75)),
                ArrayCompute.eval(plan.expr(15), plan.shard(inputs, 75, 90))));

        assertEquals(whole.rows(), sharded.rows());
        assertArrayEquals(whole.data(), sharded.data(), 0f,
                "shards must agree bit for bit with the whole");
    }

    @Test
    void aMatmulWithShardedWeightsIsRefused() {
        // If the right operand is sharded, each shard would multiply against a
        // different slice of it and every shard's answer would be wrong.
        Expr expr = Expr.input("x", 10, 8).matmul(Expr.input("w", 8, 4));

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> RowSplit.plan(expr, Set.of("x", "w")));

        assertTrue(refused.getMessage().contains("w"), refused.getMessage());
    }

    @Test
    void aTransposeOfShardedDataIsRefused() {
        // Transposing sharded rows turns them into columns of the whole; no
        // shard can compute its piece from its rows alone.
        Expr expr = Expr.input("x", 10, 8).transpose();

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> RowSplit.plan(expr, Set.of("x")));

        assertTrue(refused.getMessage().toLowerCase().contains("transpose"),
                refused.getMessage());
    }

    @Test
    void aTransposeOfReplicatedWeightsIsFine() {
        // The covariance shape on the *weights* side: transposing something
        // every shard holds whole is computed identically everywhere.
        Expr expr = Expr.input("x", 100, 8).matmul(Expr.input("w", 4, 8).transpose());

        assertEquals(100, RowSplit.plan(expr, Set.of("x")).rows());
    }

    @Test
    void mixingShardedAndReplicatedElementwiseIsRefused() {
        // x + y where x is sharded and y is a replicated full-height matrix:
        // shard k of x has 30 rows, y has 90, and no slice of y is implied.
        // addRow is the explicit way to broadcast something small.
        Expr expr = Expr.input("x", 90, 6).add(Expr.input("y", 90, 6));

        assertThrows(IllegalArgumentException.class,
                () -> RowSplit.plan(expr, Set.of("x")));
    }

    @Test
    void anExpressionOverOnlyReplicatedInputsNeedsNoSplitting() {
        // Nothing is sharded, so every shard would compute the identical
        // thing: distributing it is pure waste, and the plan says so.
        Expr expr = Expr.input("w", 8, 8).relu();

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> RowSplit.plan(expr, Set.of()));

        assertTrue(refused.getMessage().contains("replicated"), refused.getMessage());
    }

    @Test
    void shardingSlicesTheDataAndReplicatesTheWeights() {
        Expr expr = Expr.input("x", 10, 2).matmul(Expr.input("w", 2, 2));
        Map<String, Mat> inputs = Map.of("x", random(10, 2), "w", random(2, 2));
        RowSplit.Plan plan = RowSplit.plan(expr, Set.of("x"));

        Map<String, Mat> shard = plan.shard(inputs, 4, 7);

        assertEquals(3, shard.get("x").rows(), "the sharded input is sliced");
        assertEquals(2, shard.get("w").rows(), "the replicated input travels whole");
        assertArrayEquals(inputs.get("w").data(), shard.get("w").data(), 0f);
    }

    @Test
    void anUnknownShardedNameIsRefused() {
        Expr expr = Expr.input("x", 10, 2).relu();

        assertThrows(IllegalArgumentException.class,
                () -> RowSplit.plan(expr, Set.of("nonexistent")));
    }
}
