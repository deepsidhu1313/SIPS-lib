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

import in.co.s13.sips.lib.ml.Tensors;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vectors a Swift or Kotlin evaluator can run to prove it is this evaluator.
 *
 * <p>The easier mobile path is not WASM at all: a serialised expression needs
 * only the fifteen ops reimplemented — a few hundred lines in any language,
 * no runtime embedded. But two implementations of one evaluator drift, and
 * they drift silently: an iPhone that computes subtly different floats than
 * the cluster is a wrong answer, not a crash. Same cure as the WASM ABI:
 * the contract as data, every case verified against the real evaluator by
 * this test, regenerated so it cannot go stale.
 *
 * <p>Each case is a complete {@link ExprTask} document plus the expected
 * result bytes and a tolerance. Tolerance is zero for everything built from
 * +, × and comparisons — IEEE-754 fixes those exactly, on every platform,
 * given the same evaluation order, which the ops define. It is nonzero only
 * where a transcendental appears: this side pins {@code StrictMath} (fdlibm),
 * but Swift's libm is not fdlibm, and demanding bit-equality there would make
 * every honest port fail conformance for a difference of one ulp.
 */
class ExprConformanceTest {

    static final Path VECTORS = Path.of("src", "test", "resources", "expr-conformance.json");

    private record Vector(String name, String why, ExprTask task, float tolerance) {
    }

    private static Mat random(int rows, int cols, long seed) {
        Random random = new Random(seed);
        float[] data = new float[rows * cols];
        for (int i = 0; i < data.length; i++) {
            data[i] = (float) random.nextGaussian();
        }
        return new Mat(rows, cols, data);
    }

    private List<Vector> vectors() {
        List<Vector> vectors = new ArrayList<>();

        vectors.add(new Vector("matmul-exact",
                "row-major GEMM. This side accumulates sequentially in double, row by "
                + "row -- a DEFINED order, but not a defined RESULT: a port that hand-rolls "
                + "the identical loop reproduces this exactly, while one that delegates to "
                + "a vendor BLAS (measured: Apple Accelerate's cblas_sgemm) may differ by a "
                + "few ULP, because that routine's internal accumulation grouping is not "
                + "documented to match. Found porting to iOS, where Accelerate is the "
                + "obviously correct choice for this op -- not a defect in either side.",
                new ExprTask(Expr.input("a", 4, 3).matmul(Expr.input("b", 3, 5)),
                        Map.of("a", random(4, 3, 1), "b", random(3, 5, 2))), 2e-6f));

        vectors.add(new Vector("fused-chain-exact",
                "pointwise fusion must not change results: relu, scale, addRow in one pass",
                new ExprTask(Expr.input("x", 5, 4).relu().scale(2f)
                        .addRow(Expr.input("b", 1, 4)),
                        Map.of("x", random(5, 4, 3), "b", random(1, 4, 4))), 0f));

        vectors.add(new Vector("argmax-ties-first",
                "rowArgMax ties go to the FIRST index; a port that used >= breaks here",
                new ExprTask(Expr.input("x", 2, 3).rowArgMax(),
                        Map.of("x", new Mat(2, 3, new float[]{7, 7, 7, 1, 9, 9}))), 0f));

        vectors.add(new Vector("rowsum-double-accumulator",
                "sums accumulate in double then round once; summing in float drifts "
                + "on long rows and fails this case",
                new ExprTask(Expr.input("x", 3, 2000).rowSum(),
                        Map.of("x", random(3, 2000, 5))), 0f));

        vectors.add(new Vector("colsum-double-accumulator",
                "the column variant of the same rule, over many rows",
                new ExprTask(Expr.input("x", 2000, 3).colSum(),
                        Map.of("x", random(2000, 3, 6))), 0f));

        vectors.add(new Vector("transcendental-tolerance",
                "exp differs across libms by ulps; this side is fdlibm, yours need not be",
                new ExprTask(Expr.input("x", 3, 3).exp().rowSum(),
                        Map.of("x", random(3, 3, 7))), 1e-4f));

        vectors.add(new Vector("gram-partial",
                "the partial a shard computes under planReduce: transpose(x).matmul(x). "
                + "Also routes through matmul, so it carries the same vendor-BLAS "
                + "tolerance as matmul-exact and for the identical reason.",
                new ExprTask(Expr.input("x", 6, 4).transpose().matmul(Expr.input("x", 6, 4)),
                        Map.of("x", random(6, 4, 8))), 2e-6f));

        return vectors;
    }

    @Test
    void everyVectorHoldsAgainstTheRealEvaluator() {
        for (Vector vector : vectors()) {
            // Through the wire format, not the objects: what a port receives
            // is the document, so the document is what must produce the bytes.
            ExprTask landed = ExprTask.fromJson(vector.task().toJson());
            float[] expected = Tensors.fromBytes(vector.task().runToBytes());
            float[] actual = Tensors.fromBytes(landed.runToBytes());

            assertEquals(expected.length, actual.length, vector.name());
            for (int i = 0; i < expected.length; i++) {
                assertEquals(expected[i], actual[i], vector.tolerance(),
                        vector.name() + " at " + i + ": " + vector.why());
            }
        }
    }

    @Test
    void theVectorsAreWrittenWhereAnotherEvaluatorCanReadThem() throws IOException {
        JSONArray cases = new JSONArray();
        for (Vector vector : vectors()) {
            cases.put(new JSONObject()
                    .put("name", vector.name())
                    .put("why", vector.why())
                    .put("task", vector.task().toJson())
                    .put("expected", Base64.getEncoder()
                            .encodeToString(vector.task().runToBytes()))
                    .put("tolerance", vector.tolerance()));
        }
        JSONObject document = new JSONObject()
                .put("evaluatorVersion", 1)
                .put("note", "A conformant evaluator decodes each task, evaluates, and "
                        + "must match `expected` (little-endian float32) within "
                        + "`tolerance` per element. Zero tolerance means exactly zero, and "
                        + "is achievable by matching this reference's algorithm term for "
                        + "term -- but a defined summation ORDER is not the same guarantee "
                        + "as a defined RESULT: it holds only when the evaluator performs "
                        + "the same sequence of additions itself. A reduction that instead "
                        + "delegates to a vendor library (BLAS's sgemm, a GPU kernel) may "
                        + "use a different, internally undocumented accumulation grouping "
                        + "that is equally valid and not bit-identical; those cases declare "
                        + "a small measured tolerance instead of zero, named per case. "
                        + "Regenerated by ExprConformanceTest; do not edit by hand.")
                .put("cases", cases);

        Files.createDirectories(VECTORS.getParent());
        Files.writeString(VECTORS, document.toString(2) + "\n");
        assertTrue(Files.size(VECTORS) > 0);
        assertEquals(7, cases.length());
    }
}
