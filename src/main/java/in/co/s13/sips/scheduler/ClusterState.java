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

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * What the cluster looks like at the moment a task is being placed.
 *
 * <p>Immutable, and updated by producing a new one. A policy that could change
 * the state it is reasoning about would make its own decisions depend on the
 * order it happened to consider things in, which is the difference between a
 * heuristic you can reason about and one you cannot.
 */
public final class ClusterState {

    private final Map<String, Double> availableAt;
    private final Map<String, Double> wattsPerNode;

    private ClusterState(Map<String, Double> availableAt, Map<String, Double> wattsPerNode) {
        // LinkedHashMap, not Map.copyOf: the JDK's immutable maps randomise
        // iteration order per JVM run, which would make a policy's tie-breaking
        // -- and therefore any comparison between policies -- differ between
        // runs of the same experiment.
        this.availableAt = Collections.unmodifiableMap(new LinkedHashMap<>(availableAt));
        this.wattsPerNode = Collections.unmodifiableMap(new LinkedHashMap<>(wattsPerNode));
    }

    /**
     * A cluster of idle nodes.
     *
     * <p>Nodes are considered in the order given, and an unordered collection is
     * sorted so the result does not depend on how the caller happened to build
     * it. Ties between equally good nodes then break the same way every run.
     */
    public static ClusterState idle(Collection<String> nodeUuids) {
        if (nodeUuids == null || nodeUuids.isEmpty()) {
            throw new IllegalArgumentException("A cluster needs at least one node");
        }
        Collection<String> ordered = nodeUuids instanceof List
                || nodeUuids instanceof LinkedHashSet
                || nodeUuids instanceof java.util.SortedSet
                ? nodeUuids
                : nodeUuids.stream().sorted().toList();
        Map<String, Double> free = new LinkedHashMap<>();
        ordered.forEach(uuid -> free.put(uuid, 0.0));
        return new ClusterState(free, Map.of());
    }

    /** Every node, in a stable order so a policy's choices are reproducible. */
    public Set<String> nodes() {
        return new LinkedHashSet<>(availableAt.keySet());
    }

    /** When a node next has nothing to do. */
    public double availableAt(String nodeUuid) {
        Double when = availableAt.get(nodeUuid);
        if (when == null) {
            throw new IllegalArgumentException("Node " + nodeUuid + " is not in this cluster");
        }
        return when;
    }

    /**
     * What a node draws while working, if anyone measured it.
     *
     * <p>Zero when unknown, which leaves an energy-aware policy ranking on
     * whatever it does know rather than preferring the unmeasured node.
     */
    public double wattsOf(String nodeUuid) {
        return wattsPerNode.getOrDefault(nodeUuid, 0.0);
    }

    /** The same cluster with one node busy until a given time. */
    public ClusterState busyUntil(String nodeUuid, double time) {
        availableAt(nodeUuid);
        Map<String, Double> updated = new LinkedHashMap<>(availableAt);
        updated.put(nodeUuid, time);
        return new ClusterState(updated, wattsPerNode);
    }

    /** The same cluster with power figures attached. */
    public ClusterState withWatts(Map<String, Double> watts) {
        return new ClusterState(availableAt, watts == null ? Map.of() : watts);
    }

    /** The latest any node is busy until: the makespan so far. */
    public double busiest() {
        return availableAt.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
    }

    @Override
    public String toString() {
        return "ClusterState" + availableAt;
    }
}
