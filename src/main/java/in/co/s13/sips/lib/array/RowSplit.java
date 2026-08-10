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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Whether an expression can be cut into row shards, proven from the graph.
 *
 * <p>This is what turns the op set into a distribution story: {@code
 * parallelFor} and {@code ShardPlan} already know how to divide rows across
 * nodes, but only if dividing is <em>sound</em> — and soundness is a property
 * of the expression, not the data. The rules are short:
 *
 * <ul>
 *   <li>Pointwise ops and row-reductions never look across rows.</li>
 *   <li>A matmul is row-local exactly when its right operand is replicated —
 *       the same on every shard. Sharded weights would have each shard multiply
 *       against a different slice, and every answer would be wrong.</li>
 *   <li>A transpose of sharded data turns rows into columns of the whole; no
 *       shard can compute its piece from its rows alone. Refused.</li>
 *   <li>Mixing a sharded and a full-height replicated operand elementwise is
 *       refused: shard k has fewer rows than the replica and no slice of the
 *       replica is implied. {@link Expr#addRow} is the explicit broadcast.</li>
 * </ul>
 *
 * <p>Everything refused here would have <em>run</em> — and returned plausible,
 * wrong numbers from every shard. That is the failure this analysis exists to
 * make impossible, and why it runs before any data moves.
 *
 * <p>Results that cross rows — a column total, a grand total, a gram matrix —
 * are the other family: no shard can produce them alone, but every shard can
 * produce its partial and the partials add. That is {@link #planReduce}, the
 * MapReduce shape, and the two entries name each other so a caller landing on
 * the wrong one is told where the right one is.
 */
public final class RowSplit {

    /** How a node relates to the sharded rows. */
    private enum Locality {
        /** Row i of this node depends only on row i of the sharded inputs. */
        ROW_LOCAL,
        /** Contains no sharded input: identical on every shard. */
        REPLICATED
    }

    private RowSplit() {
    }

    /**
     * Proves an expression row-splittable, or refuses with the op that is not.
     *
     * @param sharded the inputs divided across shards; everything else is
     *        replicated to every shard whole
     */
    public static Plan plan(Expr expr, Set<String> sharded) {
        for (String name : sharded) {
            if (!expr.inputNames().contains(name)) {
                throw new IllegalArgumentException("'" + name + "' is not an input of this "
                        + "expression; it has " + expr.inputNames());
            }
        }
        // Classified before the row-count check: a refusal that names the op
        // (a transposed shard, sharded weights) is more useful than one about
        // a row count that is wrong *because* of that op.
        Locality locality = classify(expr, sharded);
        for (String name : sharded) {
            int[] shape = expr.inputShape(name);
            if (shape[0] != expr.rows()) {
                throw new IllegalArgumentException("Sharded input '" + name + "' has "
                        + shape[0] + " rows but the expression produces " + expr.rows()
                        + "; sharded inputs must share the expression's row count");
            }
        }
        if (locality == Locality.REPLICATED) {
            throw new IllegalArgumentException("Nothing in this expression is sharded, so "
                    + "every shard would compute the identical replicated result; "
                    + "distributing it is pure waste");
        }
        return new Plan(expr, Set.copyOf(sharded));
    }

    private static Locality classify(Expr node, Set<String> sharded) {
        switch (node.kind()) {
            case INPUT -> {
                return sharded.contains(node.name()) ? Locality.ROW_LOCAL
                        : Locality.REPLICATED;
            }
            case MATMUL -> {
                Locality left = classify(node.left(), sharded);
                Locality right = classify(node.right(), sharded);
                if (right == Locality.ROW_LOCAL) {
                    throw new IllegalArgumentException("A matmul's right operand must be "
                            + "replicated, but it contains sharded input(s) "
                            + shardedWithin(node.right(), sharded) + ": each shard would "
                            + "multiply against a different slice of it");
                }
                return left;
            }
            case TRANSPOSE -> {
                if (classify(node.left(), sharded) == Locality.ROW_LOCAL) {
                    throw new IllegalArgumentException("Cannot split a transpose of sharded "
                            + "data: it turns the shard's rows into columns of the whole, "
                            + "which no shard can compute from its rows alone. If this is "
                            + "part of a sum over the rows (a gram matrix, a column total), "
                            + "planReduce splits it into partials that add.");
                }
                return Locality.REPLICATED;
            }
            case COL_SUM, SUM_ALL -> {
                if (classify(node.left(), sharded) == Locality.ROW_LOCAL) {
                    throw new IllegalArgumentException(node.kind() + " sums across the "
                            + "sharded rows, which no row shard can finish alone; "
                            + "planReduce splits it into partials that add");
                }
                return Locality.REPLICATED;
            }
            case ADD, SUB, MUL -> {
                Locality left = classify(node.left(), sharded);
                Locality right = classify(node.right(), sharded);
                if (left != right) {
                    throw new IllegalArgumentException("Elementwise " + node.kind()
                            + " mixes a sharded operand with a replicated one: a shard "
                            + "holds fewer rows than the replica and no slice of the "
                            + "replica is implied. addRow is the explicit broadcast.");
                }
                return left;
            }
            case ADD_ROW -> {
                if (classify(node.right(), sharded) == Locality.ROW_LOCAL) {
                    throw new IllegalArgumentException("The broadcast row of addRow must be "
                            + "replicated; sharding a single row has no meaning");
                }
                return classify(node.left(), sharded);
            }
            default -> {
                // Pointwise unary, scalar ops and row reductions: row i of the
                // result depends only on row i of the child.
                return classify(node.left(), sharded);
            }
        }
    }

    private static Set<String> shardedWithin(Expr node, Set<String> sharded) {
        Set<String> within = new java.util.LinkedHashSet<>(node.inputNames());
        within.retainAll(sharded);
        return within;
    }

    /**
     * Proves an expression splittable as partials-that-add, or refuses.
     *
     * <p>Three shapes qualify, all sums over the sharded rows:
     * {@code colSum} and {@code sumAll} of a row-local subtree, and
     * {@code transpose(A).matmul(B)} with both operands row-local — the gram
     * and cross-product shapes, splittable because matrix multiplication
     * distributes over row blocks: {@code X'Y = Σ Xs'Ys}.
     *
     * <p>{@code scale} above the reduction is hoisted and applied after the
     * combine, because scaling commutes with addition. Nothing else does:
     * {@code exp} per shard then summed is not {@code exp} of the total, and
     * {@code plus(1)} per shard adds 1 once per shard instead of once. Both
     * are refused by name.
     */
    public static CombinePlan planReduce(Expr expr, Set<String> sharded) {
        for (String name : sharded) {
            if (!expr.inputNames().contains(name)) {
                throw new IllegalArgumentException("'" + name + "' is not an input of this "
                        + "expression; it has " + expr.inputNames());
            }
        }

        // Hoist the linear tail: scale commutes with the combine's addition.
        float factor = 1f;
        Expr inner = expr;
        while (inner.kind() == Expr.Kind.SCALE) {
            factor *= inner.scalar();
            inner = inner.left();
        }

        Expr reduced;
        if (inner.kind() == Expr.Kind.COL_SUM || inner.kind() == Expr.Kind.SUM_ALL) {
            reduced = inner.left();
        } else if (inner.kind() == Expr.Kind.MATMUL
                && inner.left().kind() == Expr.Kind.TRANSPOSE) {
            Expr a = inner.left().left();
            Expr b = inner.right();
            Locality left = classify(a, sharded);
            Locality right = classify(b, sharded);
            if (left != right) {
                throw new IllegalArgumentException("transpose(A).matmul(B) combines only "
                        + "when A and B are sharded the same way; here one is sharded "
                        + "and the other replicated");
            }
            if (left == Locality.REPLICATED) {
                throw new IllegalArgumentException("Nothing in this expression is sharded; "
                        + "every shard would compute the identical replicated result");
            }
            return new CombinePlan(inner, sharded, factor, a.rows());
        } else {
            // Not a recognised reduction shape. Say precisely why: a row-local
            // expression belongs with plan(), and a nonlinear op above a
            // reduction is unsound however it is split.
            try {
                if (classify(expr, sharded) == Locality.ROW_LOCAL) {
                    throw new IllegalArgumentException("This expression is row-local -- "
                            + "row i depends only on row i -- so it needs no combine; "
                            + "use plan() and concatenate the shards");
                }
                throw new IllegalArgumentException("Nothing in this expression is sharded; "
                        + "every shard would compute the identical replicated result");
            } catch (IllegalArgumentException fromClassify) {
                if (fromClassify.getMessage().contains("planReduce")) {
                    throw new IllegalArgumentException("Only scale() may appear above the "
                            + "reduction: " + inner.kind() + " applied per shard and then "
                            + "summed is not " + inner.kind() + " of the total. Move it "
                            + "after the combine, outside the expression.");
                }
                throw fromClassify;
            }
        }

        Locality locality = classify(reduced, sharded);
        if (locality == Locality.REPLICATED) {
            throw new IllegalArgumentException("Nothing in this expression is sharded; "
                    + "every shard would compute the identical replicated result");
        }
        return new CombinePlan(inner, sharded, factor, reduced.rows());
    }

    /** A proven partials-that-add split. */
    public static final class CombinePlan {

        private final Expr inner;
        private final Set<String> sharded;
        private final float hoistedScale;
        private final int dataRows;

        private CombinePlan(Expr inner, Set<String> sharded, float hoistedScale,
                int dataRows) {
            this.inner = inner;
            this.sharded = Set.copyOf(sharded);
            this.hoistedScale = hoistedScale;
            this.dataRows = dataRows;
        }

        /** How many data rows there are to divide. */
        public int rows() {
            return dataRows;
        }

        /**
         * The expression one shard evaluates: the reduction over its rows,
         * without the hoisted scale — partials stay raw so they add.
         */
        public Expr expr(int shardRows) {
            return inner.withInputRows(sharded, shardRows);
        }

        /** One shard's inputs: sharded sliced, replicated passed whole. */
        public Map<String, Mat> shard(Map<String, Mat> inputs, int from, int to) {
            Map<String, Mat> cut = new LinkedHashMap<>();
            for (Map.Entry<String, Mat> entry : inputs.entrySet()) {
                cut.put(entry.getKey(), sharded.contains(entry.getKey())
                        ? entry.getValue().slice(from, to)
                        : entry.getValue());
            }
            return cut;
        }

        /**
         * Sums the partials and applies the hoisted scale.
         *
         * <p>Accumulated in double per element. The grouping still differs
         * from the unsharded evaluation — float addition is not associative —
         * so results agree to float tolerance, not bit for bit. The docs say
         * this out loud because a training pipeline comparing the two would
         * otherwise conclude it has a bug.
         */
        public Mat combine(List<Mat> partials) {
            if (partials == null || partials.isEmpty()) {
                throw new IllegalArgumentException("Nothing to combine");
            }
            int rows = partials.get(0).rows();
            int cols = partials.get(0).cols();
            double[] totals = new double[rows * cols];
            for (Mat partial : partials) {
                if (partial.rows() != rows || partial.cols() != cols) {
                    throw new IllegalArgumentException("Partials disagree on shape: "
                            + rows + "x" + cols + " and " + partial.rows() + "x"
                            + partial.cols());
                }
                for (int i = 0; i < totals.length; i++) {
                    totals[i] += partial.data()[i];
                }
            }
            float[] combined = new float[totals.length];
            for (int i = 0; i < totals.length; i++) {
                combined[i] = (float) (totals[i] * hoistedScale);
            }
            return new Mat(rows, cols, combined);
        }
    }

    /** Stacks shard results back into one matrix, in shard order. */
    public static Mat concat(List<Mat> shards) {
        if (shards == null || shards.isEmpty()) {
            throw new IllegalArgumentException("Nothing to concatenate");
        }
        int cols = shards.get(0).cols();
        int rows = 0;
        for (Mat shard : shards) {
            if (shard.cols() != cols) {
                throw new IllegalArgumentException("Shards disagree on width: " + cols
                        + " and " + shard.cols());
            }
            rows += shard.rows();
        }
        float[] stacked = new float[rows * cols];
        int at = 0;
        for (Mat shard : shards) {
            System.arraycopy(shard.data(), 0, stacked, at, shard.data().length);
            at += shard.data().length;
        }
        return new Mat(rows, cols, stacked);
    }

    /** A proven split: how to cut the inputs and rebuild the expression. */
    public static final class Plan {

        private final Expr expr;
        private final Set<String> sharded;

        private Plan(Expr expr, Set<String> sharded) {
            this.expr = expr;
            this.sharded = sharded;
        }

        /** How many rows there are to divide. */
        public int rows() {
            return expr.rows();
        }

        /** The expression a shard of this many rows evaluates. */
        public Expr expr(int shardRows) {
            return expr.withInputRows(sharded, shardRows);
        }

        /**
         * One shard's inputs: sharded matrices sliced to {@code [from, to)},
         * replicated ones passed through whole.
         */
        public Map<String, Mat> shard(Map<String, Mat> inputs, int from, int to) {
            Map<String, Mat> cut = new LinkedHashMap<>();
            for (Map.Entry<String, Mat> entry : inputs.entrySet()) {
                cut.put(entry.getKey(), sharded.contains(entry.getKey())
                        ? entry.getValue().slice(from, to)
                        : entry.getValue());
            }
            return cut;
        }
    }
}
