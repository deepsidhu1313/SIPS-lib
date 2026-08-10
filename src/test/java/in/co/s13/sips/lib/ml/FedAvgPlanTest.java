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

import in.co.s13.sips.lib.job.Job;
import in.co.s13.sips.lib.job.JobManifest;
import in.co.s13.sips.lib.job.JobSequencer;
import in.co.s13.sips.lib.job.Stage;
import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A training run as a graph.
 *
 * <p>The plan adds no execution machinery — that is its whole argument — so
 * what needs testing is the graph it draws: the barriers land between rounds and
 * nowhere else, the data edges say what each round consumes, and the whole thing
 * survives the manifest, because a cluster run starts from the file.
 */
class FedAvgPlanTest {

    @Test
    void threeRoundsAreSixStagesInAChainOfBarriers() {
        Job job = FedAvgPlan.job("fedavg", 8, 3);

        assertEquals(6, job.stages().size());
        assertEquals(List.of(job.stage("train-1").orElseThrow()), job.roots());
        assertTrue(job.isValid());
    }

    @Test
    void aTrainStageIsOneChunkPerShard() {
        Job job = FedAvgPlan.job("fedavg", 8, 2);
        Stage train = job.stage("train-1").orElseThrow();

        assertEquals(Stage.Kind.PARALLEL_FOR, train.kind());
        assertEquals(8, train.iterationCount());
        assertEquals("round-1/model-3.bin", train.outputFor(3));
    }

    @Test
    void eachRoundReadsThePreviousAverage() {
        // reads, not merely after: the data edge is what a locality-aware
        // policy uses to keep a round where the weights already sit.
        Job job = FedAvgPlan.job("fedavg", 4, 2);
        Stage train2 = job.stage("train-2").orElseThrow();
        Stage average1 = job.stage("average-1").orElseThrow();

        assertTrue(train2.inputs().contains(average1));
        assertTrue(average1.inputs().contains(job.stage("train-1").orElseThrow()));
    }

    @Test
    void roundsCannotOverlap() {
        // The barrier is the algorithm: averaging half a round's models is a
        // different (and wrong) model, not a faster one.
        Job job = FedAvgPlan.job("fedavg", 4, 2);
        JobSequencer run = new JobSequencer(job);

        assertEquals(List.of(job.stage("train-1").orElseThrow()), run.ready());
        run.started(job.stage("train-1").orElseThrow());
        assertTrue(run.ready().isEmpty(), "nothing else may start until round 1 trains");

        run.completed(job.stage("train-1").orElseThrow());
        assertEquals(List.of(job.stage("average-1").orElseThrow()), run.ready());
    }

    @Test
    void theWholeRunDrivesThroughTheSequencer() {
        Job job = FedAvgPlan.job("fedavg", 4, 5);
        JobSequencer run = new JobSequencer(job);

        while (!run.isFinished()) {
            for (Stage stage : run.ready()) {
                run.started(stage);
                run.completed(stage);
            }
        }
        assertTrue(run.isSuccessful());
    }

    @Test
    void thePlanSurvivesTheManifest() {
        // A cluster run starts from the file, not from the Java that built it.
        Job original = FedAvgPlan.job("fedavg", 8, 3);

        Job reread = JobManifest.read("fedavg",
                new JSONObject().put("STAGES", JobManifest.write(original)));

        assertEquals(original.stages().size(), reread.stages().size());
        Stage train2 = reread.stage("train-2").orElseThrow();
        assertEquals(8, train2.iterationCount());
        assertEquals(1, train2.inputs().size());
        assertEquals("average-1", train2.inputs().iterator().next().name());
        assertEquals("round-2/model-{index}.bin", train2.output().orElseThrow());
    }

    @Test
    void stagesKnowWhichRoundTheyBelongTo() {
        Job job = FedAvgPlan.job("fedavg", 2, 3);

        assertEquals(2, FedAvgPlan.roundOf(job.stage("train-2").orElseThrow()));
        assertEquals(3, FedAvgPlan.roundOf(job.stage("average-3").orElseThrow()));
        assertEquals(-1, FedAvgPlan.roundOf(new Job("other").single("merge")));
    }

    @Test
    void oneRoundIsStillARealPlan() {
        // One-shot averaging (Zinkevich et al., 2010) is rounds = 1, and it is
        // a legitimate strategy, not an edge case.
        Job job = FedAvgPlan.job("one-shot", 16, 1);

        assertEquals(2, job.stages().size());
        assertFalse(job.stage("average-1").orElseThrow().inputs().isEmpty());
    }

    @Test
    void nonsenseIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> FedAvgPlan.job("j", 0, 3));
        assertThrows(IllegalArgumentException.class, () -> FedAvgPlan.job("j", 4, 0));
    }
}
