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

import in.co.s13.sips.lib.accelerator.MatrixCompute;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Runs an expression, putting each op where the physics says it belongs.
 *
 * <p>Two rules, both from measurement rather than fashion:
 *
 * <ul>
 *   <li><b>Matmuls go to the accelerator</b>, through {@link MatrixCompute} —
 *       O(n³) work on O(n²) data repays the transfer, and the tiled kernel has
 *       measured 19.8× on real hardware. Everything about device choice,
 *       thresholds and fallback is already that class's job.</li>
 *   <li><b>Memory-bound ops stay on the CPU, fused.</b> An elementwise op does
 *       about one FLOP per twelve bytes moved; PCIe moves bytes slower than
 *       DRAM does, so shipping an add to a GPU costs more in transfer than the
 *       CPU would spend just doing it. What <em>is</em> worth buying is passes:
 *       a chain like {@code relu(x).scale(2).plus(1)} runs as one sweep
 *       producing one array, not three sweeps producing three.</li>
 * </ul>
 *
 * <p>Fusion is implemented by composing per-element reads: a pointwise node
 * evaluates as a function over its children's functions, and only a
 * non-pointwise boundary — a matmul, a transpose, a reduction, the final
 * result — materialises an array. A reduction consumes its fused chain
 * directly, so {@code x.relu().scale(2).rowSum()} allocates the output column
 * and nothing else.
 *
 * <p>Not built here, with reasons: keeping intermediates resident on the
 * accelerator across ops would help chains of <em>matmuls</em>, but the
 * executor releases its buffers per call today, and the memory-bound ops in
 * between are exactly the ones not worth the wire. Revisit when a measured
 * workload shows back-to-back GEMMs dominated by transfer.
 */
public final class ArrayCompute {

    /** A result, with the counts that make the evaluator's claims checkable. */
    public record Evaluated(Mat value, int materialised, int matmuls) {
    }

    /** Reads one element of a node's value, fused through pointwise parents. */
    @FunctionalInterface
    private interface Source {

        float at(int row, int col);
    }

    private ArrayCompute() {
    }

    /** Evaluates an expression against named inputs. */
    public static Mat eval(Expr expr, Map<String, Mat> inputs) {
        return evaluate(expr, inputs).value();
    }

    /** Evaluates, reporting how much work it took — what the tests pin. */
    public static Evaluated evaluate(Expr expr, Map<String, Mat> inputs) {
        for (String needed : expr.inputNames()) {
            Mat supplied = inputs.get(needed);
            if (supplied == null) {
                throw new IllegalArgumentException("Input '" + needed + "' was not supplied; "
                        + "the expression needs " + expr.inputNames());
            }
            int[] shape = expr.inputShape(needed);
            if (supplied.rows() != shape[0] || supplied.cols() != shape[1]) {
                throw new IllegalArgumentException("Input '" + needed + "' must be "
                        + shape[0] + "x" + shape[1] + ", not "
                        + supplied.rows() + "x" + supplied.cols());
            }
        }
        Evaluation evaluation = new Evaluation(inputs);
        Mat value = evaluation.materialise(expr);
        return new Evaluated(value, evaluation.materialised, evaluation.matmuls);
    }

    /** One evaluation: the memo that makes a DAG cost what a DAG should. */
    private static final class Evaluation {

        private final Map<String, Mat> inputs;
        private final IdentityHashMap<Expr, Mat> memo = new IdentityHashMap<>();
        private int materialised;
        private int matmuls;

        Evaluation(Map<String, Mat> inputs) {
            this.inputs = inputs;
        }

        /** Whether a node fuses into its parent instead of allocating. */
        private static boolean pointwise(Expr.Kind kind) {
            return switch (kind) {
                case ADD, SUB, MUL, ADD_ROW, RELU, EXP, SCALE, PLUS -> true;
                default -> false;
            };
        }

        /**
         * The per-element view of a node. Pointwise nodes compose; anything
         * else materialises first and reads from the array.
         */
        private Source source(Expr node) {
            if (!pointwise(node.kind())) {
                Mat value = materialise(node);
                return value::at;
            }
            Source left = source(node.left());
            return switch (node.kind()) {
                case ADD -> {
                    Source right = source(node.right());
                    yield (r, c) -> left.at(r, c) + right.at(r, c);
                }
                case SUB -> {
                    Source right = source(node.right());
                    yield (r, c) -> left.at(r, c) - right.at(r, c);
                }
                case MUL -> {
                    Source right = source(node.right());
                    yield (r, c) -> left.at(r, c) * right.at(r, c);
                }
                case ADD_ROW -> {
                    Source row = source(node.right());
                    yield (r, c) -> left.at(r, c) + row.at(0, c);
                }
                case RELU -> (r, c) -> Math.max(0f, left.at(r, c));
                case EXP -> (r, c) -> (float) Math.exp(left.at(r, c));
                case SCALE -> {
                    float by = node.scalar();
                    yield (r, c) -> left.at(r, c) * by;
                }
                case PLUS -> {
                    float value = node.scalar();
                    yield (r, c) -> left.at(r, c) + value;
                }
                default -> throw new IllegalStateException("Not pointwise: " + node.kind());
            };
        }

        private Mat materialise(Expr node) {
            Mat remembered = memo.get(node);
            if (remembered != null) {
                return remembered;
            }
            Mat value = switch (node.kind()) {
                case INPUT -> inputs.get(node.name());
                case MATMUL -> {
                    Mat left = materialise(node.left());
                    Mat right = materialise(node.right());
                    matmuls++;
                    materialised++;
                    yield new Mat(node.rows(), node.cols(), MatrixCompute.multiply(
                            left.data(), right.data(),
                            left.rows(), left.cols(), right.cols()));
                }
                case TRANSPOSE -> {
                    Mat of = materialise(node.left());
                    float[] transposed = new float[of.rows() * of.cols()];
                    for (int r = 0; r < of.rows(); r++) {
                        for (int c = 0; c < of.cols(); c++) {
                            transposed[c * of.rows() + r] = of.at(r, c);
                        }
                    }
                    materialised++;
                    yield new Mat(of.cols(), of.rows(), transposed);
                }
                case ROW_SUM, ROW_MAX, ROW_ARG_MAX -> reduce(node);
                case COL_SUM, SUM_ALL -> reduceColumns(node);
                default -> sweep(node);
            };
            memo.put(node, value);
            return value;
        }

        /** One pass over a fused pointwise chain, producing its array. */
        private Mat sweep(Expr node) {
            Source source = source(node);
            float[] out = new float[node.rows() * node.cols()];
            for (int r = 0; r < node.rows(); r++) {
                for (int c = 0; c < node.cols(); c++) {
                    out[r * node.cols() + c] = source.at(r, c);
                }
            }
            materialised++;
            return new Mat(node.rows(), node.cols(), out);
        }

        /**
         * A column-crossing reduction — one pass over the fused chain, with
         * per-column accumulators in double so a tall matrix summed in float
         * does not drift.
         */
        private Mat reduceColumns(Expr node) {
            Expr of = node.left();
            Source source = source(of);
            double[] totals = new double[of.cols()];
            for (int r = 0; r < of.rows(); r++) {
                for (int c = 0; c < of.cols(); c++) {
                    totals[c] += source.at(r, c);
                }
            }
            materialised++;
            if (node.kind() == Expr.Kind.SUM_ALL) {
                double total = 0;
                for (double column : totals) {
                    total += column;
                }
                return new Mat(1, 1, new float[]{(float) total});
            }
            float[] out = new float[of.cols()];
            for (int c = 0; c < of.cols(); c++) {
                out[c] = (float) totals[c];
            }
            return new Mat(1, of.cols(), out);
        }

        /**
         * A row reduction, consuming its child's fused chain directly — the
         * intermediate matrix is never written anywhere.
         */
        private Mat reduce(Expr node) {
            Expr of = node.left();
            Source source = source(of);
            float[] out = new float[of.rows()];
            for (int r = 0; r < of.rows(); r++) {
                switch (node.kind()) {
                    case ROW_SUM -> {
                        // Accumulated in double: a long row summed in float
                        // drifts, and two nodes must agree on the answer.
                        double total = 0;
                        for (int c = 0; c < of.cols(); c++) {
                            total += source.at(r, c);
                        }
                        out[r] = (float) total;
                    }
                    case ROW_MAX -> {
                        float best = source.at(r, 0);
                        for (int c = 1; c < of.cols(); c++) {
                            best = Math.max(best, source.at(r, c));
                        }
                        out[r] = best;
                    }
                    case ROW_ARG_MAX -> {
                        int best = 0;
                        float bestValue = source.at(r, 0);
                        for (int c = 1; c < of.cols(); c++) {
                            float value = source.at(r, c);
                            // Strictly greater: ties go to the first, so every
                            // device and fusion path picks the same winner.
                            if (value > bestValue) {
                                best = c;
                                bestValue = value;
                            }
                        }
                        out[r] = best;
                    }
                    default -> throw new IllegalStateException("Not a reduction: "
                            + node.kind());
                }
            }
            materialised++;
            return new Mat(of.rows(), 1, out);
        }
    }
}
