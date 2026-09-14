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
package in.co.s13.sips.lib.ml.serving;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Which nodes host which experts, and how deep each node's queue is for each.
 *
 * <p>Mixture-of-experts serving spreads distinct experts across a fleet, often
 * with more than one replica of a hot expert. Routing a request needs two
 * live facts: who currently hosts the expert the request needs, and which of
 * those hosts is least backed up right now. Both facts change continuously
 * under concurrent traffic, so this class is the single mutable source of
 * truth the router and the rebalancer both read and write — no snapshot ever
 * goes stale because there is no snapshot, only {@link ConcurrentHashMap}s and
 * atomics doing their own thing under concurrent access.
 *
 * <p>Every method here is safe to call from many threads at once: {@link
 * ExpertServingRunner} calls {@link #beginRequest} and {@link #endRequest}
 * from a request-handling thread pool while {@link ExpertRouter} concurrently
 * reads {@link #leastLoaded} to make its next decision.
 */
public final class ExpertRegistry {

    private final ConcurrentHashMap<String, Set<String>> replicasByExpert = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Key, AtomicLong> queueDepth = new ConcurrentHashMap<>();

    private record Key(String expertId, String nodeUuid) {
    }

    /** Registers {@code nodeUuid} as a replica of {@code expertId}; a repeat call is a no-op. */
    public void addReplica(String expertId, String nodeUuid) {
        requireNonBlank(expertId, "expertId");
        requireNonBlank(nodeUuid, "nodeUuid");
        replicasByExpert.computeIfAbsent(expertId, key -> ConcurrentHashMap.newKeySet()).add(nodeUuid);
    }

    /**
     * @return the current replica node UUIDs for {@code expertId}, sorted for determinism
     * @throws IllegalStateException if the expert has no replicas registered at all
     */
    public List<String> replicasOf(String expertId) {
        requireNonBlank(expertId, "expertId");
        Set<String> replicas = replicasByExpert.get(expertId);
        if (replicas == null || replicas.isEmpty()) {
            throw new IllegalStateException("No replicas registered for expert '" + expertId + "'");
        }
        List<String> sorted = new ArrayList<>(replicas);
        Collections.sort(sorted);
        return sorted;
    }

    /** Marks one more request as in flight against this (expert, node) pair. */
    public void beginRequest(String expertId, String nodeUuid) {
        requireNonBlank(expertId, "expertId");
        requireNonBlank(nodeUuid, "nodeUuid");
        queueDepth.computeIfAbsent(new Key(expertId, nodeUuid), key -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Marks one request against this (expert, node) pair as finished.
     *
     * <p>Clamped at zero rather than thrown: an unmatched {@code endRequest} is a caller
     * bug, but corrupting the counter into permanent negative territory would punish every
     * request after it, which is worse than the bug that caused it.
     */
    public void endRequest(String expertId, String nodeUuid) {
        requireNonBlank(expertId, "expertId");
        requireNonBlank(nodeUuid, "nodeUuid");
        AtomicLong counter = queueDepth.get(new Key(expertId, nodeUuid));
        if (counter != null) {
            counter.updateAndGet(depth -> Math.max(0, depth - 1));
        }
    }

    /** @return the live in-flight count for this (expert, node) pair, or 0 if never begun */
    public long queueDepth(String expertId, String nodeUuid) {
        requireNonBlank(expertId, "expertId");
        requireNonBlank(nodeUuid, "nodeUuid");
        AtomicLong counter = queueDepth.get(new Key(expertId, nodeUuid));
        return counter == null ? 0 : counter.get();
    }

    /**
     * @return the replica of {@code expertId} with the lowest live queue depth, ties broken
     *         by lowest node UUID
     */
    public String leastLoaded(String expertId) {
        List<String> replicas = replicasOf(expertId);
        String best = null;
        long bestDepth = Long.MAX_VALUE;
        for (String node : replicas) {
            long depth = queueDepth(expertId, node);
            if (depth < bestDepth) {
                bestDepth = depth;
                best = node;
            }
        }
        return best;
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
