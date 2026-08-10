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

import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Weights as one byte each instead of four.
 *
 * <p>Inference ships a model to every worker and then reads it once per batch.
 * The model is the whole transfer cost and the arithmetic is unchanged by
 * carrying it more cheaply, which makes this the one compression that is close
 * to free: a quarter of the bytes for an error the model was already robust
 * to, since the weights came out of stochastic gradient descent and were never
 * precise to seven digits.
 *
 * <p>What it is not free for is <em>training</em>. Averaging quantised weights
 * accumulates the error every round, so this is deliberately an inference-side
 * codec — {@link Tensors} stays the format weights are averaged in.
 */
class QuantizedTest {

    @Test
    void survivesTheRoundTripCloselyEnoughToInferWith() {
        float[] weights = {-1.0f, -0.5f, 0f, 0.25f, 0.75f, 1.0f};

        float[] back = Quantized.of(weights).dequantize();

        // One step of the scale is the most any value can move, and the scale
        // is the largest magnitude over 127.
        assertArrayEquals(weights, back, 1.0f / 127);
    }

    @Test
    void isAQuarterOfTheSize() {
        // The entire point: the model is the transfer, and this is what makes
        // shipping it to every worker cheap enough to do per batch.
        float[] weights = new float[1000];
        new Random(1).nextInt();

        assertEquals(4000, Tensors.toBytes(weights).length);
        assertEquals(1000, Quantized.of(weights).values().length);
    }

    @Test
    void keepsZeroExactlyZero() {
        // Symmetric quantisation, so zero maps to zero with no offset. Padding,
        // masks and pruned weights are all exactly zero and stay that way; an
        // asymmetric scheme would drift them and quietly unprune a model.
        float[] weights = {-3f, 0f, 3f};

        float[] back = Quantized.of(weights).dequantize();

        assertEquals(0f, back[1], 0f);
    }

    @Test
    void aConstantTensorIsExact() {
        float[] weights = {2.5f, 2.5f, 2.5f};

        assertArrayEquals(weights, Quantized.of(weights).dequantize(), 1e-6f);
    }

    @Test
    void allZeroWeightsDoNotDivideByZero() {
        // A freshly initialised layer, or a fully pruned one. The scale would
        // be 0/127, and every weight would come back NaN.
        float[] back = Quantized.of(new float[]{0f, 0f, 0f}).dequantize();

        assertArrayEquals(new float[]{0f, 0f, 0f}, back, 0f);
    }

    @Test
    void anEmptyTensorIsAllowed() {
        assertEquals(0, Quantized.of(new float[0]).dequantize().length);
    }

    @Test
    void crossesTheWireAndComesBackTheSame() {
        float[] weights = {-2f, -1f, 0f, 1f, 2f};
        Quantized quantized = Quantized.of(weights);

        Quantized landed = Quantized.fromBytes(quantized.toBytes());

        assertEquals(quantized.scale(), landed.scale(), 0f);
        assertArrayEquals(quantized.values(), landed.values());
        assertArrayEquals(weights, landed.dequantize(), 2f / 127);
    }

    @Test
    void aTruncatedTransferIsRefused() {
        // Same reasoning as Tensors: half a model is still a well-formed byte
        // array, so inference on it produces a wrong answer rather than an
        // error.
        byte[] wire = Quantized.of(new float[]{1f, 2f}).toBytes();
        byte[] truncated = new byte[2];
        System.arraycopy(wire, 0, truncated, 0, 2);

        assertThrows(IllegalArgumentException.class, () -> Quantized.fromBytes(truncated));
    }

    @Test
    void aTensorThatIsNotFiniteIsRefused() {
        // The scale comes from the largest magnitude, so one infinity makes
        // every weight zero and one NaN makes them all NaN. Either way the
        // model is destroyed silently, and a diverged model is exactly when
        // this happens.
        assertThrows(IllegalArgumentException.class,
                () -> Quantized.of(new float[]{1f, Float.NaN}));
        assertThrows(IllegalArgumentException.class,
                () -> Quantized.of(new float[]{1f, Float.POSITIVE_INFINITY}));
    }

    @Test
    void theErrorIsSmallEnoughToBeWorthTheQuarter() {
        // The claim the whole codec rests on, measured rather than asserted by
        // hand: on normally distributed weights the mean absolute error stays
        // far below the spread of the weights themselves.
        float[] weights = new float[10_000];
        Random random = new Random(7);
        for (int i = 0; i < weights.length; i++) {
            weights[i] = (float) random.nextGaussian() * 0.1f;
        }

        float[] back = Quantized.of(weights).dequantize();

        double error = 0;
        for (int i = 0; i < weights.length; i++) {
            error += Math.abs(weights[i] - back[i]);
        }
        double mean = error / weights.length;
        assertTrue(mean < 0.002, "mean absolute error was " + mean);
    }

    @Test
    void anOutlierCostsTheRestTheirPrecision() {
        // Documented, not defended: one huge weight sets the scale for the
        // whole tensor and everything else loses resolution. It is why this
        // quantises a tensor at a time -- a caller with an outlier-prone layer
        // splits it and quantises the parts.
        float[] withOutlier = {0.01f, -0.01f, 0.02f, 1000f};

        float[] back = Quantized.of(withOutlier).dequantize();

        assertEquals(0f, back[0], 0f, "a weight far below the scale rounds to zero");
    }

    @Test
    void twoTensorsWithTheSameWeightsAreEqual() {
        // A record carrying an array compares the array by reference unless it
        // is told otherwise, so two identical models would look different --
        // and this is exactly the type that gets put in a map keyed by model.
        Quantized one = Quantized.of(new float[]{1f, 2f, 3f});
        Quantized other = Quantized.fromBytes(one.toBytes());

        assertEquals(one, other);
        assertEquals(one.hashCode(), other.hashCode());
    }

    @Test
    void nonsenseIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Quantized.of(null));
        assertThrows(IllegalArgumentException.class, () -> Quantized.fromBytes(null));
    }
}
