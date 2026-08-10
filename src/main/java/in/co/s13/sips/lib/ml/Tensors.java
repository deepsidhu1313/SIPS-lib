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
package in.co.s13.sips.lib.ml;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Model weights as bytes, so they can cross the wire.
 *
 * <p>Training on a cluster means weights travel: out to every worker at the
 * start of a round, back from every worker at the end. Everything in between —
 * the chunk directory, the inline result, the file server — moves bytes, so
 * there has to be exactly one answer to "which bytes".
 *
 * <p>The answer is little-endian IEEE-754, deliberately the layout of
 * WebAssembly linear memory. A WASM training kernel can {@code f32.load}
 * straight out of a buffer this wrote, and its output reads back with
 * {@link #fromBytes} — no translation layer, and the WASM float guarantees
 * (bit-identical across nodes) extend to the serialised form.
 */
public final class Tensors {

    private Tensors() {
    }

    /** Encodes weights. Empty is allowed; a zero-parameter model is just odd. */
    public static byte[] toBytes(float[] values) {
        if (values == null) {
            throw new IllegalArgumentException("values must not be null");
        }
        ByteBuffer buffer = ByteBuffer.allocate(values.length * Float.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.asFloatBuffer().put(values);
        return buffer.array();
    }

    /**
     * Decodes weights.
     *
     * @throws IllegalArgumentException if the length is not a multiple of four
     *         — a truncated transfer, and silently rounding it would train on a
     *         corrupted model without anything failing
     */
    public static float[] fromBytes(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes must not be null");
        }
        if (bytes.length % Float.BYTES != 0) {
            throw new IllegalArgumentException(bytes.length + " bytes is not a whole number "
                    + "of floats; the transfer was truncated or is not a tensor");
        }
        FloatBuffer floats = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                .asFloatBuffer();
        float[] values = new float[floats.remaining()];
        floats.get(values);
        return values;
    }
}
