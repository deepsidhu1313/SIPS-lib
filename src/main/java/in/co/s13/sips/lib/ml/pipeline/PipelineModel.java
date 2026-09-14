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
package in.co.s13.sips.lib.ml.pipeline;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The whole model, as the ordered stages a pipeline splits it into.
 *
 * <p>Order is the only structure this adds on top of a bag of
 * {@link LayerGroup}s, and it is the structure that matters: stage {@code i}'s
 * output is stage {@code i+1}'s input, so the list index <em>is</em> the
 * wiring. There is no separate graph of stage dependencies to keep in sync
 * with the list, because a pipeline — unlike a general DAG of layers — has
 * none to describe.
 *
 * <p>Stage names must be unique because {@link PipelinePlan} and
 * {@link PipelineRunner} error messages, and any future distributed executor
 * that has to say which stage failed, identify a stage by that name. A
 * duplicate would make "stage 'encoder' is unreachable" ambiguous about which
 * one, which defeats the entire point of naming stages instead of just
 * indexing them.
 */
public record PipelineModel(List<LayerGroup> stages) {

    public PipelineModel {
        if (stages == null || stages.isEmpty()) {
            throw new IllegalArgumentException("A pipeline needs at least one stage");
        }
        stages = List.copyOf(stages);

        Set<String> seen = new LinkedHashSet<>();
        for (LayerGroup stage : stages) {
            if (!seen.add(stage.name())) {
                throw new IllegalArgumentException("Stage name '" + stage.name()
                        + "' is used by more than one stage; stage names must be unique");
            }
        }
    }

    /** How many stages an item passes through. */
    public int stageCount() {
        return stages.size();
    }

    /** Total resident weight bytes across every stage — what the whole model costs to host. */
    public long totalWeightBytes() {
        return stages.stream().mapToLong(LayerGroup::weightBytes).sum();
    }
}
