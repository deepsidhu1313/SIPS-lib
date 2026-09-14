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
package in.co.s13.sips.lib.ml.elastic;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A two-tier (HOT/COLD) resident-unit cache that decides, on every cold access, whether it is
 * cheaper to pay a transfer cost and promote a unit into the fast tier or cheaper to just run it
 * in place at the slow tier's speed.
 *
 * <p>This models the core mechanism behind an edge-native mixture-of-experts serving engine:
 * fast-but-small memory (e.g. GPU VRAM) and slow-but-large memory (e.g. host RAM) treated as one
 * elastic pool, with an LRU policy keeping frequently used units resident in the fast tier and a
 * live, bandwidth-adaptive decision made per access for everything that is not.
 *
 * <p>The {@code adaptive} constructor flag exists because this class's real purpose is to let a
 * caller compare that cost-aware policy against a naive always-promote policy on the identical
 * workload. A research capability whose only mode is "trust me it's better" proves nothing; this
 * one is built so a caller can measure the difference directly, the same way
 * {@code SIPS-samples/LayeredPipeline} measures an automatic planner against a hand-tuned one
 * rather than asserting one is better. That is why {@code adaptive} is a flag passed in, not a
 * hardcoded behaviour.
 *
 * <p>Every public method here is {@code synchronized}. A single access touches LRU order and
 * capacity bookkeeping together — moving a unit's recency, evicting others, and updating the
 * byte count all have to happen as one step or the accounting drifts. Getting that right with
 * lock-free primitives is not worth the complexity for a first, correctness-focused version, so
 * this is a real, stated limitation rather than an oversight: this class is a real bottleneck if
 * it sits on a hot path under heavy concurrent access from many threads. A first version
 * prioritizes getting the eviction and promotion accounting correct under concurrent use over
 * lock-free performance.
 */
public final class ElasticNodeCache {

    private final long hotCapacityBytes;
    private final boolean adaptive;

    private final Map<String, UnitProfile> catalog = new HashMap<>();
    /** HOT unit ids in LRU order (eldest-accessed first); access-ordered so a get() bumps recency. */
    private final LinkedHashMap<String, Boolean> hotOrder = new LinkedHashMap<>(16, 0.75f, true);
    private long hotBytes = 0;

    /**
     * @param hotCapacityBytes fast-tier capacity in bytes; must be {@code >= 0}. Zero is legal
     *        and meaningful: it models "no fast tier at all," every access runs COLD — a real,
     *        useful baseline configuration for comparison, not a degenerate case to reject.
     * @param adaptive when {@code true}, a COLD access is promoted only if it is cheaper to
     *        promote-then-run-fast than to run in place; when {@code false}, a COLD access is
     *        always promoted whenever it is physically possible to make room for it, tying
     *        bandwidth-blind classic-LRU behaviour for comparison against the adaptive policy
     */
    public ElasticNodeCache(long hotCapacityBytes, boolean adaptive) {
        if (hotCapacityBytes < 0) {
            throw new IllegalArgumentException("hotCapacityBytes must not be negative: " + hotCapacityBytes);
        }
        this.hotCapacityBytes = hotCapacityBytes;
        this.adaptive = adaptive;
    }

    /**
     * Adds (or overwrites) {@code profile} in the catalog.
     *
     * <p>A freshly-registered unit starts as COLD regardless of whether an older profile for the
     * same id was previously HOT — overwriting a profile is treated as a new unit for residency
     * purposes, since its size/cost numbers may have changed and any assumption the old
     * residency state was still valid would be exactly the kind of silent staleness a cache
     * class must not have.
     */
    public synchronized void register(UnitProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
        String unitId = profile.unitId();
        if (hotOrder.remove(unitId) != null) {
            hotBytes -= catalog.get(unitId).sizeBytes();
        }
        catalog.put(unitId, profile);
    }

    /** What happened on one access to a unit. */
    public record Access(String unitId, Tier ranFrom, boolean promoted, boolean evictedToMakeRoom,
            double costMillis) {
    }

    /**
     * Runs one access to {@code unitId}, promoting it into the fast tier first if that is the
     * cheaper choice.
     *
     * @throws IllegalStateException if {@code unitId} was never {@link #register}ed
     */
    public synchronized Access access(String unitId) {
        UnitProfile profile = catalog.get(unitId);
        if (profile == null) {
            throw new IllegalStateException("Unknown unit '" + unitId + "'");
        }

        if (hotOrder.containsKey(unitId)) {
            hotOrder.get(unitId); // access-ordered map: this get() alone bumps its recency
            return new Access(unitId, Tier.HOT, false, false, profile.hotComputeMillis());
        }

        if (profile.sizeBytes() > hotCapacityBytes) {
            // A hard physical constraint, not a cost tradeoff: it can never fit, no matter
            // what gets evicted, so promotion is never attempted regardless of adaptive.
            return new Access(unitId, Tier.COLD, false, false, profile.coldComputeMillis());
        }

        boolean promote = !adaptive
                || profile.fetchMillis() + profile.hotComputeMillis() < profile.coldComputeMillis();
        if (!promote) {
            return new Access(unitId, Tier.COLD, false, false, profile.coldComputeMillis());
        }

        boolean evicted = makeRoom(profile.sizeBytes());
        hotOrder.put(unitId, Boolean.TRUE);
        hotBytes += profile.sizeBytes();
        return new Access(unitId, Tier.HOT, true, evicted, profile.fetchMillis() + profile.hotComputeMillis());
    }

    /** Evicts LRU HOT units until {@code neededBytes} fits; returns whether any eviction happened. */
    private boolean makeRoom(long neededBytes) {
        boolean evictedAny = false;
        while (hotBytes + neededBytes > hotCapacityBytes && !hotOrder.isEmpty()) {
            String lruUnitId = hotOrder.keySet().iterator().next();
            hotOrder.remove(lruUnitId);
            hotBytes -= catalog.get(lruUnitId).sizeBytes();
            evictedAny = true;
        }
        return evictedAny;
    }

    /** @return the currently-HOT unit ids, sorted for determinism (a snapshot, not LRU order) */
    public synchronized Set<String> hotUnits() {
        return new TreeSet<>(hotOrder.keySet());
    }

    /** @return the sum of {@code sizeBytes} over currently-HOT units */
    public synchronized long hotBytesUsed() {
        return hotBytes;
    }
}
