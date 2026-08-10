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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dividing the data so every worker reaches the barrier together.
 *
 * <p>A round ends when its slowest worker ends. Equal shards on unequal
 * machines therefore waste the difference every single round — and because
 * federated averaging weights by sample count, unequal shards cost nothing in
 * accuracy. This is the one place where knowing the cluster is heterogeneous
 * pays directly.
 */
class ShardPlanTest {

    @Test
    void equalMachinesGetEqualShards() {
        List<ShardPlan.Shard> shards = ShardPlan.across(1000, Map.of("a", 1.0, "b", 1.0));

        assertEquals(2, shards.size());
        assertEquals(500, shards.get(0).sampleCount());
        assertEquals(500, shards.get(1).sampleCount());
    }

    @Test
    void afasterMachineGetsMoreData() {
        // The point: a node twice as fast should finish its epoch at the same
        // moment, which means twice the samples.
        List<ShardPlan.Shard> shards = ShardPlan.across(900, Map.of("fast", 2.0, "slow", 1.0));

        assertEquals(600, byNode(shards, "fast").sampleCount());
        assertEquals(300, byNode(shards, "slow").sampleCount());
    }

    @Test
    void everySampleIsUsedExactlyOnce() {
        // Rounding must not lose a sample or hand one to two workers: the
        // first quietly shrinks the training set, the second overweights it.
        List<ShardPlan.Shard> shards = ShardPlan.across(1001,
                Map.of("a", 1.0, "b", 3.0, "c", 7.0));

        long total = 0;
        long expectedStart = 0;
        for (ShardPlan.Shard shard : shards) {
            assertEquals(expectedStart, shard.firstIndex(), "shards must not have gaps");
            total += shard.sampleCount();
            expectedStart = shard.lastIndexExclusive();
        }
        assertEquals(1001, total);
        assertEquals(1001, expectedStart, "the last shard must reach the end");
    }

    @Test
    void everyWorkerGetsAtLeastOneSample() {
        // A worker with nothing to train on contributes a model trained on
        // nothing, which WeightAverage refuses outright.
        // A machine a thousand times slower than the rest still has to train
        // on something; 1000 samples across these speeds would round its share
        // to zero.
        List<ShardPlan.Shard> shards = ShardPlan.across(1000,
                Map.of("a", 1.0, "b", 1.0, "c", 1.0, "d", 0.001));

        assertTrue(shards.stream().allMatch(shard -> shard.sampleCount() >= 1),
                shards.toString());
    }

    @Test
    void moreWorkersThanSamplesIsRefused() {
        // Not a rounding problem to solve: it means the caller has asked for
        // more parallelism than the data can support.
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> ShardPlan.across(2, Map.of("a", 1.0, "b", 1.0, "c", 1.0)))
                .getMessage().contains("3"));
    }

    @Test
    void anUnmeasuredMachineIsAssumedAverageNotIdle() {
        // Treating unknown as slow would starve every machine not yet
        // benchmarked, which is every new machine.
        List<ShardPlan.Shard> shards = ShardPlan.across(1000, Map.of("known", 1.0, "new", 0.0));

        assertTrue(byNode(shards, "new").sampleCount() > 0);
    }

    @Test
    void shardsAreOrderedAndNumberedFromZero() {
        List<ShardPlan.Shard> shards = ShardPlan.across(100, Map.of("a", 1.0, "b", 1.0));

        assertEquals(0, shards.get(0).shard());
        assertEquals(1, shards.get(1).shard());
    }

    @Test
    void nonsenseIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> ShardPlan.across(0, Map.of("a", 1.0)));
        assertThrows(IllegalArgumentException.class, () -> ShardPlan.across(10, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> ShardPlan.across(10, null));
    }

    private static ShardPlan.Shard byNode(List<ShardPlan.Shard> shards, String node) {
        return shards.stream().filter(shard -> shard.nodeUuid().equals(node))
                .findFirst().orElseThrow();
    }

    @Test
    void anErraticNodeLosesShareBeforeItsAverageDoes() {
        // Two nodes with the same mean speed, one steady and one that swings.
        // A round ends when its slowest worker ends, so what costs the barrier
        // is the bad round, not the average round -- and by the time the mean
        // has dropped, the tail has been stalling every round for a while.
        //
        // The measured basis is a sibling project's two-device fleet: per-round
        // cost on a busy endpoint ran about twice the idle probe at p50, with
        // p99 amplified three- to twenty-fold. A plan built from means alone
        // hands that node work it will not return on time.
        ShardPlan.Measured steady = new ShardPlan.Measured(10.0, 0.0);
        ShardPlan.Measured erratic = new ShardPlan.Measured(10.0, 5.0);

        assertTrue(steady.weight() > erratic.weight(),
                "steady " + steady.weight() + " should outweigh erratic " + erratic.weight());
    }

    @Test
    void aPerfectlySteadyNodeIsWorthItsMean() {
        assertEquals(10.0, new ShardPlan.Measured(10.0, 0.0).weight(), 1e-9);
    }

    @Test
    void aNodeThatIsAllNoiseIsWorthAlmostNothing() {
        // Dispersion equal to the mean halves it; ten times the mean nearly
        // erases it. That ordering is the whole point.
        assertEquals(5.0, new ShardPlan.Measured(10.0, 10.0).weight(), 1e-9);
        assertTrue(new ShardPlan.Measured(10.0, 100.0).weight() < 1.0);
    }

    @Test
    void measurementsDivideTheDataJustAsSpeedsDo() {
        // The same planner, so a caller with dispersion data does not need a
        // different code path -- and one without it gets identical behaviour.
        Map<String, ShardPlan.Measured> measured = new LinkedHashMap<>();
        measured.put("steady", new ShardPlan.Measured(10.0, 0.0));
        measured.put("erratic", new ShardPlan.Measured(10.0, 10.0));

        List<ShardPlan.Shard> shards = ShardPlan.acrossMeasured(3000, measured);

        // Looked up by name, not by position: shards come out in node-name
        // order so that the same cluster produces the same division every run.
        Map<String, Long> byNode = new LinkedHashMap<>();
        shards.forEach(shard -> byNode.put(shard.nodeUuid(), shard.sampleCount()));
        assertEquals(2000L, byNode.get("steady"), "the steady node takes twice the work");
        assertEquals(1000L, byNode.get("erratic"));
    }

    @Test
    void anUnmeasuredDispersionIsTreatedAsSteady() {
        // A node benchmarked once has a mean and no spread yet. Assuming the
        // worst would starve every newly measured machine.
        assertEquals(10.0, new ShardPlan.Measured(10.0, -1).weight(), 1e-9);
    }
}
