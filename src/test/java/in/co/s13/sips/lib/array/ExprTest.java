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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Describing a computation before running it.
 *
 * <p>An expression is a plan, not a result: {@code x.matmul(w).relu()} builds a
 * graph and computes nothing. The evaluator needs the whole graph to do its two
 * jobs — sending the O(n³) nodes to an accelerator and fusing the memory-bound
 * ones into single passes — and neither is possible if each op runs eagerly the
 * moment it is written.
 *
 * <p>Shapes are checked at construction, not at evaluation. A shape mismatch
 * discovered mid-evaluation on a cluster is a failed chunk on a remote node; the
 * same mistake caught while building the graph is a stack trace at the line that
 * made it.
 */
class ExprTest {

    @Test
    void anExpressionKnowsItsShape() {
        Expr x = Expr.input("x", 40, 8);
        Expr w = Expr.input("w", 8, 3);

        Expr product = x.matmul(w);

        assertEquals(40, product.rows());
        assertEquals(3, product.cols());
    }

    @Test
    void shapesAreCheckedWhenTheGraphIsBuilt() {
        // (40x8)·(7x3): the inner dimensions disagree. This must fail here,
        // at the line that wrote it, not later inside a chunk on another node.
        Expr x = Expr.input("x", 40, 8);
        Expr wrong = Expr.input("w", 7, 3);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> x.matmul(wrong));

        assertTrue(refused.getMessage().contains("8"), refused.getMessage());
        assertTrue(refused.getMessage().contains("7"), refused.getMessage());
    }

    @Test
    void elementwiseOperandsMustAgreeExactly() {
        Expr a = Expr.input("a", 4, 4);
        Expr b = Expr.input("b", 4, 5);

        assertThrows(IllegalArgumentException.class, () -> a.add(b));
        assertThrows(IllegalArgumentException.class, () -> a.sub(b));
        assertThrows(IllegalArgumentException.class, () -> a.mul(b));
    }

    @Test
    void aRowVectorBroadcastsOntoEveryRow() {
        // The bias-add shape: X (n x d) plus b (1 x d). Broadcasting is
        // explicit -- addRow, not add -- because silent broadcasting is how
        // array bugs hide.
        Expr x = Expr.input("x", 40, 8);
        Expr bias = Expr.input("b", 1, 8);

        Expr biased = x.addRow(bias);

        assertEquals(40, biased.rows());
        assertEquals(8, biased.cols());
        assertThrows(IllegalArgumentException.class,
                () -> x.addRow(Expr.input("wrong", 1, 9)));
        assertThrows(IllegalArgumentException.class,
                () -> x.addRow(Expr.input("notARow", 2, 8)));
    }

    @Test
    void transposeSwapsTheShape() {
        assertEquals(8, Expr.input("x", 40, 8).transpose().rows());
        assertEquals(40, Expr.input("x", 40, 8).transpose().cols());
    }

    @Test
    void rowReductionsProduceAColumn() {
        Expr x = Expr.input("x", 40, 8);

        assertEquals(40, x.rowSum().rows());
        assertEquals(1, x.rowSum().cols());
        assertEquals(1, x.rowMax().cols());
        assertEquals(1, x.rowArgMax().cols());
    }

    @Test
    void unaryAndScalarOpsKeepTheShape() {
        Expr x = Expr.input("x", 5, 6);

        assertEquals(5, x.relu().scale(2f).plus(1f).exp().rows());
        assertEquals(6, x.relu().scale(2f).plus(1f).exp().cols());
    }

    @Test
    void buildingAnExpressionComputesNothing() {
        // The property everything else rests on. If construction computed, the
        // evaluator could never see a whole chain to fuse it.
        Expr x = Expr.input("x", 1000, 1000);

        Expr chained = x.matmul(Expr.input("w", 1000, 1000)).relu().exp().scale(0.5f);

        // No data was ever supplied, and nothing has been asked to compute:
        // reaching here without an exception is the assertion.
        assertEquals(1000, chained.rows());
    }

    @Test
    void anExpressionListsTheInputsItNeeds() {
        // The evaluator binds by name, and a cluster shard needs to know which
        // matrices to put in the chunk before it ships it.
        Expr x = Expr.input("x", 4, 8);
        Expr w = Expr.input("w", 8, 2);
        Expr expr = x.matmul(w).relu();

        assertEquals(java.util.Set.of("x", "w"), expr.inputNames());
    }

    @Test
    void twoInputsWithOneNameMustAgreeOnShape() {
        // The same name twice is the same matrix; letting it appear at two
        // shapes would bind one array to both and read it two ways.
        Expr once = Expr.input("x", 4, 8);
        Expr again = Expr.input("x", 3, 3);

        assertThrows(IllegalArgumentException.class, () -> once.matmul(
                Expr.input("w", 8, 3)).add(again.matmul(Expr.input("w2", 3, 3))));
    }

    @Test
    void nonsenseIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Expr.input(null, 2, 2));
        assertThrows(IllegalArgumentException.class, () -> Expr.input(" ", 2, 2));
        assertThrows(IllegalArgumentException.class, () -> Expr.input("x", 0, 2));
        assertThrows(IllegalArgumentException.class, () -> Expr.input("x", 2, -1));
    }
}
