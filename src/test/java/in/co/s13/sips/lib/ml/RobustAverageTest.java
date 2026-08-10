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

import in.co.s13.sips.lib.ml.WeightAverage.Contribution;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Averaging weights when not every worker can be trusted to send good ones.
 *
 * <p>A mean has breakdown point zero: one contribution can move it anywhere at
 * all. That is fine on a cluster of machines one person owns, and not fine the
 * moment the workers are phones — a device that is broken, that ran out of
 * memory half way, or whose owner would like to steer the model, poisons every
 * round it takes part in, and nothing about the run looks wrong afterwards.
 *
 * <p>So this is the trimmed mean of Yin et al. (2018): drop the extremes of
 * each coordinate and average what is left. It costs a little accuracy when
 * everyone is honest, and it is the difference between a training run that can
 * accept strangers and one that cannot.
 */
class RobustAverageTest {

    private static Contribution honest(int chunk, float value) {
        return new Contribution(chunk, 100, new float[]{value, value});
    }

    @Test
    void agreesWithTheOrdinaryMeanWhenEveryoneIsHonest() {
        // The cost of the insurance, on the case where it buys nothing. With
        // symmetric contributions the trimmed mean lands on the same answer.
        List<Contribution> contributions = List.of(
                honest(0, 1.0f), honest(1, 2.0f), honest(2, 3.0f),
                honest(3, 4.0f), honest(4, 5.0f));

        float[] robust = RobustAverage.of(contributions, 1);

        assertArrayEquals(new float[]{3.0f, 3.0f}, robust, 1e-6f);
    }

    @Test
    void onePoisonedModelDoesNotMoveTheAverage() {
        // The whole point. Four honest workers around 1.0 and one sending an
        // enormous value: the plain mean lands nowhere near the honest ones.
        List<Contribution> contributions = List.of(
                honest(0, 1.0f), honest(1, 1.1f), honest(2, 0.9f),
                honest(3, 1.0f), honest(4, 1_000_000f));

        float[] plain = WeightAverage.of(contributions);
        float[] robust = RobustAverage.of(contributions, 1);

        assertTrue(plain[0] > 1000, "the plain mean should be ruined: " + plain[0]);
        assertEquals(1.0f, robust[0], 0.1f);
    }

    @Test
    void aPoisonerAtEitherEndIsTrimmed() {
        // Cheap to send a huge negative instead of a huge positive, so both
        // tails have to go.
        List<Contribution> contributions = List.of(
                honest(0, -1_000_000f), honest(1, 1.0f), honest(2, 1.1f),
                honest(3, 0.9f), honest(4, 1_000_000f));

        float[] robust = RobustAverage.of(contributions, 1);

        assertEquals(1.0f, robust[0], 0.1f);
    }

    @Test
    void trimsEachCoordinateSeparately() {
        // A poisoner need not be extreme in every weight, and probably will
        // not be: a targeted attack moves the few weights it cares about.
        // Trimming whole contributions would miss that.
        List<Contribution> contributions = List.of(
                new Contribution(0, 100, new float[]{1f, 1f, 1f}),
                new Contribution(1, 100, new float[]{1f, 9999f, 1f}),
                new Contribution(2, 100, new float[]{1f, 1f, 1f}),
                new Contribution(3, 100, new float[]{9999f, 1f, 1f}),
                new Contribution(4, 100, new float[]{1f, 1f, 1f}));

        float[] robust = RobustAverage.of(contributions, 1);

        assertArrayEquals(new float[]{1f, 1f, 1f}, robust, 1e-3f);
    }

    @Test
    void trimmingMoreThanTheHonestMajorityIsRefused() {
        // Trimming two from each end of five leaves one contribution, which is
        // not an average of anything. Refused rather than silently returning
        // whichever worker happened to be in the middle.
        List<Contribution> five = List.of(honest(0, 1f), honest(1, 2f), honest(2, 3f),
                honest(3, 4f), honest(4, 5f));

        assertThrows(IllegalArgumentException.class, () -> RobustAverage.of(five, 2));
    }

    @Test
    void trimmingNothingIsTheOrdinaryMean() {
        // The honest-cluster setting, and what makes this safe to default to.
        List<Contribution> contributions = List.of(
                new Contribution(0, 100, new float[]{1f}),
                new Contribution(1, 300, new float[]{5f}));

        assertArrayEquals(WeightAverage.of(contributions),
                RobustAverage.of(contributions, 0), 1e-6f);
    }

    @Test
    void aRunWithTooFewWorkersToTrimSaysSo() {
        // Two workers cannot be made robust: trimming one from each end leaves
        // nothing. Better to say so than to pretend.
        List<Contribution> two = List.of(honest(0, 1f), honest(1, 1000f));

        assertThrows(IllegalArgumentException.class, () -> RobustAverage.of(two, 1));
    }

    @Test
    void howManyCanBeTrimmedIsAnswerable() {
        // A caller sizing a round needs to know, before it starts, whether the
        // cluster it has can tolerate anything at all. Three workers cannot:
        // trimming one from each end leaves one, and one contribution is not
        // an average of anything. Four is the smallest round that tolerates a
        // liar, which is worth knowing when deciding how many phones to ask.
        assertEquals(0, RobustAverage.trimmableFrom(2));
        assertEquals(0, RobustAverage.trimmableFrom(3));
        assertEquals(1, RobustAverage.trimmableFrom(4));
        assertEquals(2, RobustAverage.trimmableFrom(6));
    }

    @Test
    void theTrimmedResultIsTheSameWhateverOrderTheyArriveIn() {
        // Same reason WeightAverage sorts: a model that depends on which
        // worker answered first is not reproducible, and two nodes averaging
        // the same contributions must agree bit for bit.
        List<Contribution> forwards = List.of(honest(0, 1f), honest(1, 2f), honest(2, 3f),
                honest(3, 4f), honest(4, 5f));
        List<Contribution> backwards = new ArrayList<>(forwards);
        java.util.Collections.reverse(backwards);

        assertArrayEquals(RobustAverage.of(forwards, 1), RobustAverage.of(backwards, 1), 0f);
    }

    @Test
    void contributionsOfDifferentLengthsAreRefused() {
        // Two different models, not two views of one. Averaging them
        // coordinate-wise is meaningless whatever the trim.
        List<Contribution> mismatched = List.of(
                new Contribution(0, 100, new float[]{1f, 2f}),
                new Contribution(1, 100, new float[]{1f}),
                new Contribution(2, 100, new float[]{1f, 2f}));

        assertThrows(IllegalArgumentException.class, () -> RobustAverage.of(mismatched, 1));
    }

    @Test
    void nonsenseIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> RobustAverage.of(null, 1));
        assertThrows(IllegalArgumentException.class, () -> RobustAverage.of(List.of(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> RobustAverage.of(List.of(honest(0, 1f), honest(1, 2f),
                        honest(2, 3f)), -1));
    }
}
