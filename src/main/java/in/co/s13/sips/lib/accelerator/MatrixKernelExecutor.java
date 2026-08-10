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

/**
 * Runs a {@link MatrixKernel} on one device.
 *
 * <p>The float counterpart of {@link KernelExecutor}. Kept a separate interface
 * rather than widened into that one: an image executor promises bit-identical
 * results and a matrix executor promises agreement to a tolerance, and folding
 * two different guarantees into one type would make both unclear.
 */
public interface MatrixKernelExecutor extends AutoCloseable {

    /** The device this executor runs on. */
    Device device();

    /**
     * Whether this device can run this kernel at all.
     *
     * <p>A tiled kernel needs a work group of a particular size, and devices
     * differ in what they permit — an integrated GPU or an OpenCL CPU device
     * will often refuse a group a discrete GPU accepts. Asking first turns that
     * into a choice of kernel rather than a {@code CL_INVALID_WORK_ITEM_SIZE}
     * from inside the driver.
     */
    default boolean supports(MatrixKernel kernel) {
        return true;
    }

    /**
     * Applies a kernel.
     *
     * @param a {@code m × k}, row-major — or {@code k × m} for a kernel that
     *        documents itself as reading A transposed
     * @param b {@code k × n}, row-major
     * @return a new {@code m × n} buffer; the inputs are not modified
     * @throws IllegalArgumentException if a buffer does not match its shape
     */
    float[] execute(MatrixKernel kernel, float[] a, float[] b, int m, int k, int n);

    /**
     * Applies a kernel down a chain: {@code ((a·rights[0])·rights[1])·…}.
     *
     * <p>The default is the honest fallback — one {@link #execute} per link,
     * paying the full transfer each time. A device executor overrides it to
     * keep the running product resident, which is the entire point: the
     * intermediate is O(n²) bytes that would otherwise cross the bus down and
     * straight back up between every pair of links.
     *
     * @param rightCols the column count of each right operand; its row count
     *        is the previous link's columns
     */
    default float[] chain(MatrixKernel kernel, float[] a, int m, int k,
            java.util.List<float[]> rights, int[] rightCols) {
        float[] current = a;
        int currentCols = k;
        for (int i = 0; i < rights.size(); i++) {
            current = execute(kernel, current, rights.get(i), m, currentCols, rightCols[i]);
            currentCols = rightCols[i];
        }
        return current;
    }

    @Override
    void close();
}
