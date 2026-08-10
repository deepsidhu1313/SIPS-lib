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

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Combining models that were never trained together.
 *
 * <p>The other way to avoid barriers entirely. Each worker trains on its own
 * shard to convergence with no communication at all, and the coordinator
 * combines what comes back — not by averaging the weights, which only works
 * when the models started from the same place and stayed near each other, but
 * by combining their <em>outputs</em>, which needs no such thing.
 *
 * <p>That difference is what makes it robust: models of different shapes,
 * trained for different lengths, on different data, still combine. And it is
 * what makes it expensive to serve, which is why the ensemble is usually a step
 * on the way to a single distilled student rather than the deliverable.
 */
class EnsembleTest {

    @Test
    void averagesTheProbabilitiesTheMembersReport() {
        // Soft targets, not votes: the confidence is the part a student
        // learns from, and a vote throws it away.
        List<float[]> members = List.of(
                new float[]{0.8f, 0.2f},
                new float[]{0.6f, 0.4f});

        assertArrayEquals(new float[]{0.7f, 0.3f}, Ensemble.soften(members, 1.0f), 1e-6f);
    }

    @Test
    void aConfidentMajorityOutweighsAHesitantOne() {
        List<float[]> members = List.of(
                new float[]{0.9f, 0.1f},
                new float[]{0.9f, 0.1f},
                new float[]{0.4f, 0.6f});

        float[] combined = Ensemble.soften(members, 1.0f);

        assertTrue(combined[0] > combined[1]);
    }

    @Test
    void temperatureSpreadsTheProbabilityMass() {
        // Hinton et al. (2015): a hard target teaches only the answer, while a
        // softened one teaches which wrong answers were nearly right, which is
        // most of what the ensemble knows that one model does not.
        List<float[]> confident = List.of(new float[]{0.99f, 0.01f});

        float[] softened = Ensemble.soften(confident, 4.0f);

        assertTrue(softened[1] > 0.01f, "softening should raise the runner-up: " + softened[1]);
        assertTrue(softened[0] < 0.99f);
    }

    @Test
    void aSoftenedDistributionStillSumsToOne() {
        float[] softened = Ensemble.soften(List.of(
                new float[]{0.7f, 0.2f, 0.1f},
                new float[]{0.1f, 0.8f, 0.1f}), 3.0f);

        float total = 0;
        for (float probability : softened) {
            total += probability;
        }
        assertEquals(1.0f, total, 1e-5f);
    }

    @Test
    void memberThatDidNotArriveIsJustOneFewerVoice() {
        // The property worth having: a worker that vanished costs a little
        // accuracy and nothing else. There is no hole to fill.
        float[] two = Ensemble.soften(List.of(
                new float[]{0.8f, 0.2f}, new float[]{0.6f, 0.4f}), 1.0f);
        float[] one = Ensemble.soften(List.of(new float[]{0.8f, 0.2f}), 1.0f);

        assertEquals(1.0f, two[0] + two[1], 1e-6f);
        assertEquals(1.0f, one[0] + one[1], 1e-6f);
    }

    @Test
    void theHardAnswerIsAvailableToo() {
        assertEquals(2, Ensemble.argmax(new float[]{0.1f, 0.2f, 0.7f}));
        assertEquals(0, Ensemble.argmax(new float[]{0.5f, 0.5f}), "ties go to the first");
    }

    @Test
    void membersOfDifferentWidthsAreRefused() {
        // Two models over different label sets. Combining them coordinate-wise
        // would silently mean something else.
        assertThrows(IllegalArgumentException.class, () -> Ensemble.soften(List.of(
                new float[]{0.5f, 0.5f}, new float[]{0.3f, 0.3f, 0.4f}), 1.0f));
    }

    @Test
    void aMemberThatReturnedNonsenseIsRefused() {
        // A diverged model returns NaN, and averaging it makes the whole
        // ensemble NaN -- one broken worker destroying every distilled target.
        assertThrows(IllegalArgumentException.class, () -> Ensemble.soften(List.of(
                new float[]{0.5f, 0.5f}, new float[]{Float.NaN, 1f}), 1.0f));
    }

    @Test
    void nonsenseIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Ensemble.soften(List.of(), 1.0f));
        assertThrows(IllegalArgumentException.class, () -> Ensemble.soften(null, 1.0f));
        assertThrows(IllegalArgumentException.class,
                () -> Ensemble.soften(List.of(new float[]{1f}), 0f));
    }
}
