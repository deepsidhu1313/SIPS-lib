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

import in.co.s13.sips.lib.array.ArrayCompute;
import in.co.s13.sips.lib.array.Expr;
import in.co.s13.sips.lib.array.Mat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A contiguous slice of a model's layers, small enough for one machine.
 *
 * <p>Pipeline parallelism exists because a large model's weights do not fit on
 * a single node's memory; the fix is to cut the layer stack into ordered
 * groups and put each group on its own machine. A group is therefore exactly
 * two things: the {@link Expr} graph the layers compute, and the weight
 * matrices that graph reads as constants. Everything else — activations —
 * flows in from the previous stage and out to the next.
 *
 * <p>Reusing {@link Expr} and {@link ArrayCompute} here rather than inventing
 * a second tensor runtime is the load-bearing choice: those two classes
 * already know how to check shapes, fuse pointwise chains and send matmuls to
 * an accelerator, and a layer group is nothing more than one more caller of
 * {@code ArrayCompute.eval}. The only thing new is bookkeeping — which graph
 * input is the streaming activation and which are frozen weights supplied at
 * construction — and that bookkeeping is exactly what construction validates,
 * so a graph with a dangling or misnamed input fails here, at the point the
 * pipeline is assembled, rather than the first time an item happens to flow
 * through it.
 */
public record LayerGroup(String name, Expr graph, String activationInputName, Map<String, Mat> weights) {

    public LayerGroup {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A layer group needs a name");
        }
        if (graph == null) {
            throw new IllegalArgumentException("Layer group '" + name + "' needs a computation graph");
        }
        if (activationInputName == null || activationInputName.isBlank()) {
            throw new IllegalArgumentException("Layer group '" + name
                    + "' needs the name of the graph input its activation binds to");
        }
        if (weights == null) {
            throw new IllegalArgumentException("Layer group '" + name
                    + "' needs a weights map, even if it is empty");
        }
        weights = Map.copyOf(weights);

        for (String weightName : weights.keySet()) {
            if (!graph.inputNames().contains(weightName)) {
                throw new IllegalArgumentException("Weight '" + weightName
                        + "' in layer group '" + name + "' is not an input of its graph; "
                        + "the graph reads " + graph.inputNames());
            }
        }
        if (!graph.inputNames().contains(activationInputName)) {
            throw new IllegalArgumentException("Activation input '" + activationInputName
                    + "' in layer group '" + name + "' is not an input of its graph; "
                    + "the graph reads " + graph.inputNames());
        }
        for (String input : graph.inputNames()) {
            if (!input.equals(activationInputName) && !weights.containsKey(input)) {
                throw new IllegalArgumentException("Graph input '" + input + "' in layer group '"
                        + name + "' is bound by neither the activation nor a weight; "
                        + "every input a graph reads must be supplied from somewhere");
            }
        }
    }

    /** Bytes the weights occupy resident in memory — four per float, the plan's eligibility test. */
    public long weightBytes() {
        long total = 0;
        for (Mat mat : weights.values()) {
            total += 4L * mat.data().length;
        }
        return total;
    }

    /**
     * Runs this group's layers on one activation, producing the next stage's
     * input. A shape mismatch is left to {@link ArrayCompute#eval} to report:
     * it already knows exactly which input and which shape disagree.
     */
    public Mat forward(Mat activation) {
        Map<String, Mat> bound = new LinkedHashMap<>(weights);
        bound.put(activationInputName, activation);
        return ArrayCompute.eval(graph, bound);
    }
}
