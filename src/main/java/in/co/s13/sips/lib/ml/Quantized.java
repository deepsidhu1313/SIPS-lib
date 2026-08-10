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

/**
 * Weights carried as one byte each instead of four.
 *
 * <p>Distributed inference ships a model to every worker and then reads it
 * once per batch. The model is the whole transfer cost, and the arithmetic is
 * unchanged by carrying it more cheaply — which makes this close to free: a
 * quarter of the bytes for an error the model was already robust to, since the
 * weights came out of stochastic gradient descent and were never precise to
 * seven digits.
 *
 * <h2>Symmetric, per tensor</h2>
 *
 * <p>One scale, no offset: {@code q = round(x / scale)} with
 * {@code scale = max|x| / 127}. Zero therefore maps to zero exactly, which
 * matters more than it sounds — padding, masks and pruned weights are all
 * exactly zero, and an asymmetric scheme would drift them and quietly unprune
 * a model.
 *
 * <p>The cost is that one outlier sets the scale for the whole tensor and
 * everything else loses resolution. That is why this quantises a tensor at a
 * time rather than a whole model: a caller with an outlier-prone layer splits
 * it and quantises the parts.
 *
 * <h2>Not for training</h2>
 *
 * <p>Federated averaging adds a rounding error every round and then averages
 * the result, so the error accumulates across rounds rather than cancelling.
 * {@link Tensors} stays the format weights are trained and averaged in; this is
 * for shipping a finished model to the workers that will run it.
 */
public record Quantized(float scale, byte[] values) {

    /** The largest magnitude a signed byte can carry, leaving -128 unused. */
    public static final int LEVELS = 127;

    /** Scale, then one byte per weight. */
    private static final int HEADER_BYTES = Float.BYTES;

    /**
     * Quantises a tensor.
     *
     * @throws IllegalArgumentException if any value is not finite — the scale
     *         comes from the largest magnitude, so one infinity makes every
     *         weight zero and one NaN makes them all NaN. Either way the model
     *         is destroyed silently, and a diverged model is exactly when this
     *         would happen.
     */
    public static Quantized of(float[] weights) {
        if (weights == null) {
            throw new IllegalArgumentException("weights must not be null");
        }
        float largest = 0;
        for (float weight : weights) {
            if (!Float.isFinite(weight)) {
                throw new IllegalArgumentException("Cannot quantise a tensor containing "
                        + weight + "; the model has diverged");
            }
            largest = Math.max(largest, Math.abs(weight));
        }

        // An all-zero tensor -- a freshly initialised layer, or a fully pruned
        // one -- would otherwise divide by zero and come back NaN.
        float scale = largest == 0 ? 1 : largest / LEVELS;
        byte[] values = new byte[weights.length];
        for (int i = 0; i < weights.length; i++) {
            values[i] = (byte) Math.round(weights[i] / scale);
        }
        return new Quantized(scale, values);
    }

    /** The weights again, within one step of the scale of where they started. */
    public float[] dequantize() {
        float[] weights = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            weights[i] = values[i] * scale;
        }
        return weights;
    }

    /** How many weights this carries. */
    public int size() {
        return values.length;
    }

    /** The wire form: the scale, then one byte per weight. */
    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_BYTES + values.length)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.putFloat(scale);
        buffer.put(values);
        return buffer.array();
    }

    /**
     * Reads the wire form.
     *
     * @throws IllegalArgumentException if there is not even a scale in there —
     *         the same reasoning as {@link Tensors#fromBytes}: half a model is
     *         still a well-formed byte array, so inference on it produces a
     *         wrong answer rather than an error
     */
    public static Quantized fromBytes(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes must not be null");
        }
        if (bytes.length < HEADER_BYTES) {
            throw new IllegalArgumentException(bytes.length + " bytes is not a quantised "
                    + "tensor; the transfer was truncated");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float scale = buffer.getFloat();
        byte[] values = new byte[bytes.length - HEADER_BYTES];
        buffer.get(values);
        return new Quantized(scale, values);
    }

    /**
     * Compares the weights, not the array they happen to sit in.
     *
     * <p>A record carrying an array gets reference equality by default, so two
     * identical models would look different — and a model is exactly the kind
     * of thing that ends up as a map key or compared against a cached copy.
     *
     * <p>{@link #values()} still hands back the live array rather than a copy:
     * this exists to avoid moving model-sized data around, and copying one on
     * every read would undo that.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof Quantized quantized
                && Float.compare(scale, quantized.scale) == 0
                && java.util.Arrays.equals(values, quantized.values);
    }

    @Override
    public int hashCode() {
        return 31 * Float.hashCode(scale) + java.util.Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        return "Quantized[" + values.length + " weights, scale=" + scale + "]";
    }
}
