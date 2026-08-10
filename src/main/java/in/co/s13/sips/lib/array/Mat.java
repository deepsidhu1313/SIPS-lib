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

/**
 * A row-major float matrix: the one data shape the array ops move.
 *
 * <p>The same {@code float[]} convention every kernel in the accelerator
 * package already uses, with the dimensions attached so they can be checked
 * instead of remembered. A bare array with its shape carried in the caller's
 * head is how a transposed argument goes unnoticed until the answers are
 * subtly wrong.
 */
public record Mat(int rows, int cols, float[] data) {

    public Mat {
        if (rows < 1 || cols < 1) {
            throw new IllegalArgumentException("A matrix needs positive dimensions, not "
                    + rows + "x" + cols);
        }
        if (data == null || data.length != rows * cols) {
            throw new IllegalArgumentException(rows + "x" + cols + " needs "
                    + (rows * cols) + " values, not "
                    + (data == null ? "null" : data.length));
        }
    }

    /** The value at one position. */
    public float at(int row, int col) {
        return data[row * cols + col];
    }

    /** Rows {@code from} (inclusive) to {@code to} (exclusive), as a copy. */
    public Mat slice(int from, int to) {
        if (from < 0 || to > rows || from >= to) {
            throw new IllegalArgumentException("Rows " + from + ".." + to
                    + " are not a slice of " + rows);
        }
        float[] sliced = new float[(to - from) * cols];
        System.arraycopy(data, from * cols, sliced, 0, sliced.length);
        return new Mat(to - from, cols, sliced);
    }

    @Override
    public String toString() {
        return "Mat[" + rows + "x" + cols + "]";
    }
}
