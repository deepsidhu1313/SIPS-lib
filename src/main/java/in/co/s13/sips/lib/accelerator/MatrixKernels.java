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
package in.co.s13.sips.lib.accelerator;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The matrix kernels SIPS ships.
 *
 * <p>Enough to train with, and enough to show a researcher the shape of one.
 * Adding a kernel means writing the pair — OpenCL C and Java — and putting it
 * in {@link #all()}; the conformance test then holds the two forms to the
 * tolerance the kernel declares, on every device the machine has.
 */
public final class MatrixKernels {

    private MatrixKernels() {
    }

    /**
     * {@code C = A × B}. The forward pass, and the operation everything else
     * is measured against.
     */
    public static final MatrixKernel GEMM = new MatrixKernel() {

        @Override
        public String name() {
            return "gemm";
        }

        @Override
        public String description() {
            return "dense matrix product, C = A x B";
        }

        @Override
        public String openClSource() {
            return """
                   __kernel void gemm(__global const float* a,
                                      __global const float* b,
                                      __global float* c,
                                      const int m, const int k, const int n) {
                       int row = get_global_id(0);
                       int col = get_global_id(1);
                       if (row >= m || col >= n) {
                           return;
                       }
                       float sum = 0.0f;
                       for (int i = 0; i < k; i++) {
                           sum += a[row * k + i] * b[i * n + col];
                       }
                       c[row * n + col] = sum;
                   }
                   """;
        }

        @Override
        public void applyOnCpu(float[] a, float[] b, float[] c, int m, int k, int n) {
            // Accumulates in double: a float accumulator over a few hundred
            // terms drifts enough to show up as a systematically different
            // model, and the wider accumulator costs nothing against the
            // memory traffic.
            double[] row = new double[n];
            for (int i = 0; i < m; i++) {
                java.util.Arrays.fill(row, 0);
                int aRow = i * k;
                for (int inner = 0; inner < k; inner++) {
                    float scale = a[aRow + inner];
                    if (scale == 0f) {
                        continue;
                    }
                    int bRow = inner * n;
                    for (int col = 0; col < n; col++) {
                        row[col] += (double) scale * b[bRow + col];
                    }
                }
                int cRow = i * n;
                for (int col = 0; col < n; col++) {
                    c[cRow + col] = (float) row[col];
                }
            }
        }
    };

    /**
     * {@code C = Aᵀ × B}, with {@code A} stored {@code k × m}.
     *
     * <p>The backward pass's shape. Materialising the transpose first would
     * double the memory traffic of the step that already dominates it, so the
     * kernel indexes {@code A} transposed instead.
     */
    public static final MatrixKernel GEMM_TRANSPOSED = new MatrixKernel() {

        @Override
        public String name() {
            return "gemm_at_b";
        }

        @Override
        public String description() {
            return "transposed product, C = A^T x B, without materialising A^T";
        }

        @Override
        public String openClSource() {
            return """
                   __kernel void gemm_at_b(__global const float* a,
                                           __global const float* b,
                                           __global float* c,
                                           const int m, const int k, const int n) {
                       int row = get_global_id(0);
                       int col = get_global_id(1);
                       if (row >= m || col >= n) {
                           return;
                       }
                       float sum = 0.0f;
                       for (int i = 0; i < k; i++) {
                           sum += a[i * m + row] * b[i * n + col];
                       }
                       c[row * n + col] = sum;
                   }
                   """;
        }

        @Override
        public void applyOnCpu(float[] a, float[] b, float[] c, int m, int k, int n) {
            java.util.Arrays.fill(c, 0f);
            for (int i = 0; i < k; i++) {
                int aRow = i * m;
                int bRow = i * n;
                for (int row = 0; row < m; row++) {
                    float scale = a[aRow + row];
                    if (scale == 0f) {
                        continue;
                    }
                    int cRow = row * n;
                    for (int col = 0; col < n; col++) {
                        c[cRow + col] += scale * b[bRow + col];
                    }
                }
            }
        }
    };

    /**
     * {@code C = A × B}, staging tiles through local memory.
     *
     * <p>The same arithmetic as {@link #GEMM} and a different memory pattern,
     * which is the whole difference. The naive kernel has every work item read
     * a full row of {@code A} and column of {@code B} from global memory, so a
     * tile of 16×16 items fetches the same values 16 times over and the device
     * spends its life waiting on memory — measurably no faster than the CPU.
     *
     * <p>Here a work group co-operates: each item loads one element of each
     * tile into {@code __local} memory, the group synchronises, and then every
     * item reads its 16 values from local. Each global value is fetched once
     * per tile instead of sixteen times.
     *
     * <p>The default for large problems, because that is the version that
     * actually earns the transfer.
     */
    public static final MatrixKernel GEMM_TILED = new MatrixKernel() {

        private static final int TILE = 16;

        @Override
        public String name() {
            return "gemm_tiled";
        }

        @Override
        public String description() {
            return "dense matrix product staged through local memory, C = A x B";
        }

        @Override
        public int tileSize() {
            return TILE;
        }

        @Override
        public String openClSource() {
            return """
                   #define TS 16
                   __kernel void gemm_tiled(__global const float* a,
                                            __global const float* b,
                                            __global float* c,
                                            const int m, const int k, const int n) {
                       const int lrow = get_local_id(0);
                       const int lcol = get_local_id(1);
                       const int grow = get_group_id(0) * TS + lrow;
                       const int gcol = get_group_id(1) * TS + lcol;

                       __local float atile[TS][TS];
                       __local float btile[TS][TS];

                       float sum = 0.0f;
                       const int tiles = (k + TS - 1) / TS;
                       for (int t = 0; t < tiles; t++) {
                           const int acol = t * TS + lcol;
                           const int brow = t * TS + lrow;
                           atile[lrow][lcol] =
                               (grow < m && acol < k) ? a[grow * k + acol] : 0.0f;
                           btile[lrow][lcol] =
                               (brow < k && gcol < n) ? b[brow * n + gcol] : 0.0f;
                           barrier(CLK_LOCAL_MEM_FENCE);

                           for (int i = 0; i < TS; i++) {
                               sum += atile[lrow][i] * btile[i][lcol];
                           }
                           barrier(CLK_LOCAL_MEM_FENCE);
                       }

                       if (grow < m && gcol < n) {
                           c[grow * n + gcol] = sum;
                       }
                   }
                   """;
        }

        @Override
        public void applyOnCpu(float[] a, float[] b, float[] c, int m, int k, int n) {
            // Same answer, same oracle: the tiling is a memory strategy, not a
            // different operation.
            GEMM.applyOnCpu(a, b, c, m, k, n);
        }
    };

    /**
     * Row-wise softmax of {@code A}, ignoring {@code B}.
     *
     * <p>Included because it is the other half of what a classifier's forward
     * pass costs, and because it shows a kernel whose work does not scale with
     * {@code k} — the framework has to be told that, or it will send a cheap
     * operation to an accelerator and lose to the transfer.
     */
    public static final MatrixKernel SOFTMAX_ROWS = new MatrixKernel() {

        @Override
        public String name() {
            return "softmax_rows";
        }

        @Override
        public String description() {
            return "row-wise softmax, numerically stabilised by the row maximum";
        }

        @Override
        public String openClSource() {
            return """
                   __kernel void softmax_rows(__global const float* a,
                                              __global const float* b,
                                              __global float* c,
                                              const int m, const int k, const int n) {
                       int row = get_global_id(0);
                       int col = get_global_id(1);
                       if (row >= m || col >= n) {
                           return;
                       }
                       int base = row * n;
                       float largest = a[base];
                       for (int i = 1; i < n; i++) {
                           largest = fmax(largest, a[base + i]);
                       }
                       float total = 0.0f;
                       for (int i = 0; i < n; i++) {
                           total += exp(a[base + i] - largest);
                       }
                       c[base + col] = exp(a[base + col] - largest) / total;
                   }
                   """;
        }

        @Override
        public void applyOnCpu(float[] a, float[] b, float[] c, int m, int k, int n) {
            for (int row = 0; row < m; row++) {
                int base = row * n;
                float largest = a[base];
                for (int i = 1; i < n; i++) {
                    largest = Math.max(largest, a[base + i]);
                }
                // Subtracting the row maximum first: without it exp overflows
                // on logits a trained model routinely produces.
                double total = 0;
                for (int i = 0; i < n; i++) {
                    total += Math.exp(a[base + i] - largest);
                }
                for (int i = 0; i < n; i++) {
                    c[base + i] = (float) (Math.exp(a[base + i] - largest) / total);
                }
            }
        }

        @Override
        public float tolerance() {
            // exp() is implementation-defined in OpenCL to a few ULP, and the
            // division compounds it. Still far tighter than training cares
            // about, but claiming the default here would be a claim nobody
            // checked.
            return 5e-2f;
        }

        @Override
        public long workPerElement(int m, int k, int n) {
            // Two passes over the row per element, not k -- so a wide softmax
            // is cheap and belongs on the CPU whatever k says.
            return 2L * n;
        }
    };

    /** Every built-in kernel, by name. */
    public static Map<String, MatrixKernel> all() {
        Map<String, MatrixKernel> kernels = new LinkedHashMap<>();
        for (MatrixKernel kernel : new MatrixKernel[]{
            GEMM, GEMM_TILED, GEMM_TRANSPOSED, SOFTMAX_ROWS}) {
            kernels.put(kernel.name(), kernel);
        }
        return kernels;
    }

    /** One by name, or empty if nothing goes by it. */
    public static java.util.Optional<MatrixKernel> byName(String name) {
        return java.util.Optional.ofNullable(all().get(name));
    }
}
