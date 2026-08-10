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
package in.co.s13.sips.scheduler;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A task whose dependencies are done, described in the terms a placement
 * heuristic reasons about.
 *
 * <p>Everything the literature's heuristics need and nothing else. Handing a
 * policy the live cluster objects would tie every experiment to a running
 * system; this can be built from a benchmark file, from a trace, or by a test,
 * which is what makes comparing policies offline possible at all.
 */
public final class ReadyTask {

    private final String name;
    private final double defaultCost;
    private final Map<String, Double> costByNode;
    private final double upwardRank;
    private final Set<String> inputLocations;
    private final double readyAt;

    private ReadyTask(Builder builder) {
        this.name = builder.name;
        this.defaultCost = builder.defaultCost;
        this.costByNode = Map.copyOf(builder.costByNode);
        this.upwardRank = builder.upwardRank;
        this.inputLocations = Set.copyOf(builder.inputLocations);
        this.readyAt = builder.readyAt;
    }

    public static Builder named(String name) {
        return new Builder(name);
    }

    public String name() {
        return name;
    }

    /**
     * What this task costs on a node.
     *
     * <p>Falls back to the default when a node has no measurement, so a cluster
     * with one unbenchmarked machine still schedules rather than refusing to.
     */
    public double costOn(String nodeUuid) {
        return costByNode.getOrDefault(nodeUuid, defaultCost);
    }

    /** The cost used for a node with no measurement of its own. */
    public double defaultCost() {
        return defaultCost;
    }

    /**
     * How critical this task is: its own cost plus the longest path of work
     * still ahead of it.
     *
     * <p>The number HEFT orders by. Zero when nobody computed ranks, which
     * leaves rank-ordering policies behaving like arrival order.
     */
    public double upwardRank() {
        return upwardRank;
    }

    /** Nodes that already hold this task's inputs. */
    public Set<String> inputLocations() {
        return inputLocations;
    }

    /** The earliest time this task could start, once its predecessors are done. */
    public double readyAt() {
        return readyAt;
    }

    @Override
    public String toString() {
        return "ReadyTask[" + name + " cost=" + defaultCost + " rank=" + upwardRank + "]";
    }

    /** Builds a task. Only the name and a cost are required. */
    public static final class Builder {

        private final String name;
        private double defaultCost = 1;
        private final Map<String, Double> costByNode = new LinkedHashMap<>();
        private double upwardRank;
        private final Set<String> inputLocations = new LinkedHashSet<>();
        private double readyAt;

        private Builder(String name) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("A ready task needs a name");
            }
            this.name = name.trim();
        }

        /** What it costs on a node with no measurement of its own. */
        public Builder cost(double cost) {
            if (cost < 0) {
                throw new IllegalArgumentException("Cost must not be negative: " + cost);
            }
            this.defaultCost = cost;
            return this;
        }

        /** What it costs on one particular node. */
        public Builder costOn(String nodeUuid, double cost) {
            if (cost < 0) {
                throw new IllegalArgumentException("Cost must not be negative: " + cost);
            }
            costByNode.put(nodeUuid, cost);
            return this;
        }

        public Builder upwardRank(double upwardRank) {
            this.upwardRank = upwardRank;
            return this;
        }

        /** A node that already holds this task's inputs. */
        public Builder inputAt(String nodeUuid) {
            inputLocations.add(nodeUuid);
            return this;
        }

        public Builder readyAt(double readyAt) {
            if (readyAt < 0) {
                throw new IllegalArgumentException("readyAt must not be negative: " + readyAt);
            }
            this.readyAt = readyAt;
            return this;
        }

        public ReadyTask build() {
            return new ReadyTask(this);
        }
    }
}
