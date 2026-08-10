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
package in.co.s13.sips.lib.job;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a stage produces, and what the next one reads.
 *
 * <p>An ordering edge says <em>when</em> a stage may run. A read edge says
 * <em>where it should</em> — its inputs are already sitting on the nodes that
 * produced them, and a placement that ignores that can lose to one that does not
 * even when it picks a faster machine.
 */
class StageDataFlowTest {

    @Test
    void readingAStageAlsoWaitsForIt() {
        // You cannot read output that has not been written. Requiring both
        // after() and reads() would be a rule whose only effect is letting
        // someone declare one and forget the other.
        Job job = new Job("pipeline");
        Stage produce = job.parallelFor("bias", 0, 256).writes("corrected/{index}.raw");
        Stage consume = job.single("register").reads(produce);

        assertTrue(consume.dependencies().contains(produce));
        assertTrue(consume.inputs().contains(produce));
    }

    @Test
    void waitingForAStageIsNotTheSameAsReadingIt() {
        // "Do not start until the checkpoint is written" is ordering, not data.
        // A policy that treated it as data would pin work to a node for no
        // reason.
        Job job = new Job("pipeline");
        Stage checkpoint = job.single("checkpoint");
        Stage next = job.single("next").after(checkpoint);

        assertTrue(next.dependencies().contains(checkpoint));
        assertFalse(next.inputs().contains(checkpoint),
                "an ordering edge leaves nothing behind worth staying near");
    }

    @Test
    void aStageCanReadSomeOfWhatItWaitsFor() {
        Job job = new Job("pipeline");
        Stage data = job.single("data").writes("volume.raw");
        Stage barrier = job.single("licence-check");
        Stage work = job.single("work").reads(data).after(barrier);

        assertEquals(2, work.dependencies().size());
        assertEquals(1, work.inputs().size());
        assertTrue(work.inputs().contains(data));
    }

    @Test
    void aParallelStageWritesOnceForEachChunk() {
        Stage stage = new Job("j").parallelFor("bias", 0, 256)
                .writes("corrected/{index}.raw");

        assertEquals("corrected/0.raw", stage.outputFor(0));
        assertEquals("corrected/128.raw", stage.outputFor(128));
    }

    @Test
    void aParallelStageWritingOnePathIsRefused() {
        // Every chunk writing the same file means the last one wins and the rest
        // are silently lost -- the kind of bug that shows up as a wrong answer
        // rather than an error.
        Job job = new Job("j");

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> job.parallelFor("bias", 0, 256).writes("corrected.raw"))
                .getMessage().contains("{index}"));
    }

    @Test
    void aSingleStageMayWriteOnePath() {
        // One task, one output. No placeholder needed, and demanding one would
        // be noise.
        Stage stage = new Job("j").single("merge").writes("volume.nii");

        assertEquals("volume.nii", stage.outputFor(0));
    }

    @Test
    void aStageWithNoDeclaredOutputSaysSoRatherThanGuessing() {
        Stage stage = new Job("j").single("compute");

        assertTrue(stage.output().isEmpty());
        assertThrows(IllegalStateException.class, () -> stage.outputFor(0));
    }

    @Test
    void nonsenseOutputIsRefused() {
        Job job = new Job("j");

        assertThrows(IllegalArgumentException.class, () -> job.single("a").writes(""));
        assertThrows(IllegalArgumentException.class, () -> job.single("b").writes(null));
    }

    @Test
    void aStageCannotReadItself() {
        Job job = new Job("j");
        Stage stage = job.single("a");

        assertThrows(IllegalArgumentException.class, () -> stage.reads(stage));
    }

    @Test
    void aStageCannotReadAnotherJobsOutput() {
        Stage foreign = new Job("other").single("theirs").writes("x");
        Stage mine = new Job("mine").single("ours");

        assertThrows(IllegalArgumentException.class, () -> mine.reads(foreign));
    }

    @Test
    void dataFlowSurvivesTheManifest() {
        // The submitter builds it in Java, the node reads it from the file. If
        // the read edges were lost in between, locality would silently stop
        // working and nothing would say so.
        Job original = new Job("pipeline");
        Stage bias = original.parallelFor("bias", 0, 256).writes("corrected/{index}.raw");
        Stage barrier = original.single("licence-check");
        original.single("register").reads(bias).after(barrier);

        Job copy = JobManifest.read("pipeline",
                new org.json.JSONObject().put("STAGES", JobManifest.write(original)));

        Stage register = copy.stage("register").orElseThrow();
        assertEquals(2, register.dependencies().size());
        assertEquals(1, register.inputs().size());
        assertEquals("bias", register.inputs().iterator().next().name());
        assertEquals("corrected/{index}.raw",
                copy.stage("bias").orElseThrow().output().orElseThrow());
    }

    @Test
    void aManifestNamingAnUnknownProducerSaysWhich() {
        org.json.JSONArray stages = new org.json.JSONArray()
                .put(new org.json.JSONObject().put("NAME", "bias").put("KIND", "single"))
                .put(new org.json.JSONObject().put("NAME", "register").put("KIND", "single")
                        .put("READS", new org.json.JSONArray().put("baiys")));

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> JobManifest.read("j", new org.json.JSONObject().put("STAGES", stages)))
                .getMessage().contains("baiys"));
    }
}
