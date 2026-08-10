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
import java.util.Map;
import java.util.Set;

/**
 * A computation described before it is run.
 *
 * <p>{@code x.matmul(w).relu()} builds a graph and computes nothing. That is
 * the load-bearing choice: the evaluator needs the whole graph to send the
 * O(n³) nodes to an accelerator, fuse the memory-bound chains into single
 * passes, and decide whether the expression can be cut into row shards — none
 * of which is possible if each op runs the moment it is written.
 *
 * <p>The op set is deliberately small and closed. It is the BLAS observation,
 * forty years on: dense numerical programs concentrate their time in a handful
 * of operations, so tuning those once — the tiled GEMM, the fused pass — pays
 * for every problem expressed in them. What the set does not cover (sorting,
 * graphs, branchy code) belongs in ordinary Java or WASM chunks, and no op will
 * be added to pretend otherwise.
 *
 * <p>Shapes are checked at construction. A mismatch caught here is a stack
 * trace at the line that made it; the same mistake discovered at evaluation is
 * a failed chunk on a remote node.
 */
public final class Expr {

    /** Every operation the evaluator understands. */
    public enum Kind {
        INPUT, MATMUL, TRANSPOSE, ADD, SUB, MUL, ADD_ROW,
        RELU, EXP, SCALE, PLUS, ROW_SUM, ROW_MAX, ROW_ARG_MAX
    }

    private final Kind kind;
    private final int rows;
    private final int cols;
    private final Expr left;
    private final Expr right;
    private final String name;
    private final float scalar;

    /** Every input under this node, by name, with the shape it must have. */
    private final Map<String, int[]> inputShapes;

    private Expr(Kind kind, int rows, int cols, Expr left, Expr right, String name,
            float scalar) {
        this.kind = kind;
        this.rows = rows;
        this.cols = cols;
        this.left = left;
        this.right = right;
        this.name = name;
        this.scalar = scalar;
        this.inputShapes = mergeInputs(kind, left, right, name, rows, cols);
    }

    /**
     * Merges the input shapes of the children, refusing one name at two
     * shapes: the same name is the same matrix, and letting it appear at two
     * shapes would bind one array to both and read it two ways.
     */
    private static Map<String, int[]> mergeInputs(Kind kind, Expr left, Expr right,
            String name, int rows, int cols) {
        Map<String, int[]> merged = new LinkedHashMap<>();
        if (kind == Kind.INPUT) {
            merged.put(name, new int[]{rows, cols});
            return merged;
        }
        for (Expr child : new Expr[]{left, right}) {
            if (child == null) {
                continue;
            }
            for (Map.Entry<String, int[]> entry : child.inputShapes.entrySet()) {
                int[] existing = merged.get(entry.getKey());
                if (existing != null && (existing[0] != entry.getValue()[0]
                        || existing[1] != entry.getValue()[1])) {
                    throw new IllegalArgumentException("Input '" + entry.getKey()
                            + "' appears as " + existing[0] + "x" + existing[1] + " and as "
                            + entry.getValue()[0] + "x" + entry.getValue()[1]
                            + "; one name is one matrix");
                }
                merged.put(entry.getKey(), entry.getValue());
            }
        }
        return merged;
    }

    /** A named matrix the caller will supply at evaluation. */
    public static Expr input(String name, int rows, int cols) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("An input needs a name to be bound by");
        }
        if (rows < 1 || cols < 1) {
            throw new IllegalArgumentException("Input '" + name + "' needs positive "
                    + "dimensions, not " + rows + "x" + cols);
        }
        return new Expr(Kind.INPUT, rows, cols, null, null, name, 0);
    }

    /** Matrix product. The one op worth sending to an accelerator. */
    public Expr matmul(Expr other) {
        if (cols != other.rows) {
            throw new IllegalArgumentException("Cannot multiply " + rows + "x" + cols
                    + " by " + other.rows + "x" + other.cols + ": " + cols
                    + " and " + other.rows + " must agree");
        }
        return new Expr(Kind.MATMUL, rows, other.cols, this, other, null, 0);
    }

    public Expr transpose() {
        return new Expr(Kind.TRANSPOSE, cols, rows, this, null, null, 0);
    }

    public Expr add(Expr other) {
        return elementwise(Kind.ADD, other);
    }

    public Expr sub(Expr other) {
        return elementwise(Kind.SUB, other);
    }

    /** Elementwise product — not {@link #matmul}. */
    public Expr mul(Expr other) {
        return elementwise(Kind.MUL, other);
    }

    private Expr elementwise(Kind op, Expr other) {
        if (rows != other.rows || cols != other.cols) {
            throw new IllegalArgumentException("Elementwise " + op + " needs equal shapes, "
                    + "not " + rows + "x" + cols + " and " + other.rows + "x" + other.cols);
        }
        return new Expr(op, rows, cols, this, other, null, 0);
    }

    /**
     * Adds a single row to every row — the bias-add shape.
     *
     * <p>Broadcasting is explicit, not inferred from a shape coincidence,
     * because silent broadcasting is how array bugs hide.
     */
    public Expr addRow(Expr rowVector) {
        if (rowVector.rows != 1 || rowVector.cols != cols) {
            throw new IllegalArgumentException("addRow needs a 1x" + cols + " vector, not "
                    + rowVector.rows + "x" + rowVector.cols);
        }
        return new Expr(Kind.ADD_ROW, rows, cols, this, rowVector, null, 0);
    }

    public Expr relu() {
        return new Expr(Kind.RELU, rows, cols, this, null, null, 0);
    }

    public Expr exp() {
        return new Expr(Kind.EXP, rows, cols, this, null, null, 0);
    }

    public Expr scale(float by) {
        return new Expr(Kind.SCALE, rows, cols, this, null, null, by);
    }

    public Expr plus(float value) {
        return new Expr(Kind.PLUS, rows, cols, this, null, null, value);
    }

    /** The sum of each row, as a column. */
    public Expr rowSum() {
        return new Expr(Kind.ROW_SUM, rows, 1, this, null, null, 0);
    }

    /** The largest value in each row, as a column. */
    public Expr rowMax() {
        return new Expr(Kind.ROW_MAX, rows, 1, this, null, null, 0);
    }

    /**
     * The index of each row's largest value, as a column. Ties go to the
     * first, so two nodes evaluating the same expression pick the same winner.
     */
    public Expr rowArgMax() {
        return new Expr(Kind.ROW_ARG_MAX, rows, 1, this, null, null, 0);
    }

    public int rows() {
        return rows;
    }

    public int cols() {
        return cols;
    }

    public Kind kind() {
        return kind;
    }

    Expr left() {
        return left;
    }

    Expr right() {
        return right;
    }

    String name() {
        return name;
    }

    float scalar() {
        return scalar;
    }

    /** The inputs this expression must be given to evaluate. */
    public Set<String> inputNames() {
        return Set.copyOf(inputShapes.keySet());
    }

    /** The shape one input must have. */
    int[] inputShape(String inputName) {
        return inputShapes.get(inputName);
    }

    /**
     * The same graph with the named inputs rebuilt at a different row count —
     * how a shard's expression is derived from the whole. Package-private:
     * callers go through {@code RowSplit.Plan}, which knows which inputs are
     * sharded and has proven the reshape is sound.
     */
    Expr withInputRows(Set<String> inputsToResize, int newRows) {
        switch (kind) {
            case INPUT -> {
                return inputsToResize.contains(name) ? input(name, newRows, cols) : this;
            }
            case MATMUL -> {
                return left.withInputRows(inputsToResize, newRows)
                        .matmul(right.withInputRows(inputsToResize, newRows));
            }
            case TRANSPOSE -> {
                return left.withInputRows(inputsToResize, newRows).transpose();
            }
            case ADD -> {
                return left.withInputRows(inputsToResize, newRows)
                        .add(right.withInputRows(inputsToResize, newRows));
            }
            case SUB -> {
                return left.withInputRows(inputsToResize, newRows)
                        .sub(right.withInputRows(inputsToResize, newRows));
            }
            case MUL -> {
                return left.withInputRows(inputsToResize, newRows)
                        .mul(right.withInputRows(inputsToResize, newRows));
            }
            case ADD_ROW -> {
                return left.withInputRows(inputsToResize, newRows)
                        .addRow(right.withInputRows(inputsToResize, newRows));
            }
            case RELU -> {
                return left.withInputRows(inputsToResize, newRows).relu();
            }
            case EXP -> {
                return left.withInputRows(inputsToResize, newRows).exp();
            }
            case SCALE -> {
                return left.withInputRows(inputsToResize, newRows).scale(scalar);
            }
            case PLUS -> {
                return left.withInputRows(inputsToResize, newRows).plus(scalar);
            }
            case ROW_SUM -> {
                return left.withInputRows(inputsToResize, newRows).rowSum();
            }
            case ROW_MAX -> {
                return left.withInputRows(inputsToResize, newRows).rowMax();
            }
            case ROW_ARG_MAX -> {
                return left.withInputRows(inputsToResize, newRows).rowArgMax();
            }
            default -> throw new IllegalStateException("Unhandled kind " + kind);
        }
    }

    @Override
    public String toString() {
        return kind + "[" + rows + "x" + cols + "]";
    }
}
