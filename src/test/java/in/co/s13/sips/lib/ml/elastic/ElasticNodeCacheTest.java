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

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticNodeCacheTest {

    @Test
    void accessingAnUnregisteredUnitThrows() {
        ElasticNodeCache cache = new ElasticNodeCache(1000, true);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> cache.access("ghost"));
        assertTrue(thrown.getMessage().contains("ghost"), thrown.getMessage());
    }

    @Test
    void aFreshColdAccessPromotesWhenCheaper() {
        ElasticNodeCache cache = new ElasticNodeCache(1000, true);
        // fetch(2) + hot(3) == 5 < cold(10): promoting is the cheaper choice.
        cache.register(new UnitProfile("u", 100, 2.0, 3.0, 10.0));

        ElasticNodeCache.Access access = cache.access("u");

        assertEquals(Tier.HOT, access.ranFrom());
        assertTrue(access.promoted());
        assertFalse(access.evictedToMakeRoom());
        assertEquals(5.0, access.costMillis(), 1e-9);
        assertTrue(cache.hotUnits().contains("u"));
    }

    @Test
    void aFreshColdAccessDoesNotPromoteWhenRunningInPlaceIsCheaper() {
        ElasticNodeCache cache = new ElasticNodeCache(1000, true);
        // fetch(8) + hot(5) == 13 > cold(10): running cold is the cheaper choice.
        cache.register(new UnitProfile("u", 100, 8.0, 5.0, 10.0));

        ElasticNodeCache.Access access = cache.access("u");

        assertEquals(Tier.COLD, access.ranFrom());
        assertFalse(access.promoted());
        assertFalse(access.evictedToMakeRoom());
        assertEquals(10.0, access.costMillis(), 1e-9);
        assertFalse(cache.hotUnits().contains("u"));
    }

    @Test
    void anExactTieRunsColdNotHot() {
        ElasticNodeCache cache = new ElasticNodeCache(1000, true);
        // fetch(4) + hot(6) == cold(10) exactly: a tie is not worth the churn of promoting.
        cache.register(new UnitProfile("u", 100, 4.0, 6.0, 10.0));

        ElasticNodeCache.Access access = cache.access("u");

        assertEquals(Tier.COLD, access.ranFrom());
        assertFalse(access.promoted());
        assertEquals(10.0, access.costMillis(), 1e-9);
    }

    @Test
    void nonAdaptivePromotesEvenWhenNotWorthIt() {
        ElasticNodeCache cache = new ElasticNodeCache(1000, false);
        // fetch(100) + hot(100) == 200, far more than cold(10): the cost check would refuse
        // this, but adaptive=false disables the check entirely.
        cache.register(new UnitProfile("u", 100, 100.0, 100.0, 10.0));

        ElasticNodeCache.Access access = cache.access("u");

        assertEquals(Tier.HOT, access.ranFrom());
        assertTrue(access.promoted());
        assertEquals(200.0, access.costMillis(), 1e-9);
    }

    @Test
    void aUnitTooBigForTheFastTierNeverPromotes() {
        // A hard physical constraint, not a cost tradeoff: adaptive=false and an extremely
        // cheap fetch must not matter when the unit can never fit at all.
        ElasticNodeCache cache = new ElasticNodeCache(10, false);
        cache.register(new UnitProfile("u", 100, 0.0001, 1.0, 1000.0));

        ElasticNodeCache.Access access = cache.access("u");

        assertEquals(Tier.COLD, access.ranFrom());
        assertFalse(access.promoted());
        assertFalse(access.evictedToMakeRoom());
    }

    @Test
    void lruEvictsTheLeastRecentlyUsedUnit() {
        // Capacity holds exactly two of these three 40-byte units at once, so the third
        // unit's arrival must evict one of the other two to make room.
        ElasticNodeCache cache = new ElasticNodeCache(90, false);
        cache.register(new UnitProfile("a", 40, 1.0, 1.0, 1000.0));
        cache.register(new UnitProfile("b", 40, 1.0, 1.0, 1000.0));
        cache.register(new UnitProfile("c", 40, 1.0, 1.0, 1000.0));

        cache.access("a");
        cache.access("b");
        ElasticNodeCache.Access accessC = cache.access("c");

        assertTrue(accessC.evictedToMakeRoom());
        // Exactly a -- the least recently used -- was evicted, not b or c.
        assertEquals(Set.of("b", "c"), cache.hotUnits());

        // b was never touched since becoming hot: it must still be resident. Checked before
        // disturbing the LRU order further by re-promoting a.
        ElasticNodeCache.Access accessBAgain = cache.access("b");
        assertEquals(Tier.HOT, accessBAgain.ranFrom());
        assertFalse(accessBAgain.promoted());

        // a was evicted earlier, so it must be a fresh promotion decision again.
        ElasticNodeCache.Access accessAAgain = cache.access("a");
        assertEquals(Tier.HOT, accessAAgain.ranFrom());
        assertTrue(accessAAgain.promoted());
    }

    @Test
    void touchingAHotUnitUpdatesItsRecency() {
        ElasticNodeCache cache = new ElasticNodeCache(90, false);
        cache.register(new UnitProfile("a", 40, 1.0, 1.0, 1000.0));
        cache.register(new UnitProfile("b", 40, 1.0, 1.0, 1000.0));
        cache.register(new UnitProfile("c", 40, 1.0, 1.0, 1000.0));

        cache.access("a");
        cache.access("b");
        cache.access("a"); // refreshes a's recency; b becomes the true least-recently-used

        ElasticNodeCache.Access accessC = cache.access("c");
        assertTrue(accessC.evictedToMakeRoom());
        assertEquals(Set.of("a", "c"), cache.hotUnits(), "b, not a, should have been evicted");

        ElasticNodeCache.Access accessAAgain = cache.access("a");
        assertEquals(Tier.HOT, accessAAgain.ranFrom());
        assertFalse(accessAAgain.promoted(), "a was touched last and should not have been evicted");
    }

    @Test
    void reRegisteringAHotUnitResetsItToCold() {
        ElasticNodeCache cache = new ElasticNodeCache(1000, true);
        cache.register(new UnitProfile("x", 100, 1.0, 1.0, 100.0));
        ElasticNodeCache.Access first = cache.access("x");
        assertTrue(first.promoted());
        assertTrue(cache.hotUnits().contains("x"));

        // Overwriting the profile must treat the unit as new for residency purposes.
        cache.register(new UnitProfile("x", 100, 2.0, 2.0, 100.0));

        ElasticNodeCache.Access second = cache.access("x");
        assertTrue(second.promoted(), "re-registering must not leave the unit silently hot");
        assertEquals(4.0, second.costMillis(), 1e-9, "the new profile's numbers must be used");
    }

    @Test
    void hotBytesUsedTracksExactByteCounts() {
        ElasticNodeCache cache = new ElasticNodeCache(100, false);
        cache.register(new UnitProfile("a", 40, 1.0, 1.0, 1000.0));
        cache.register(new UnitProfile("b", 40, 1.0, 1.0, 1000.0));
        cache.register(new UnitProfile("c", 40, 1.0, 1.0, 1000.0));

        assertEquals(0, cache.hotBytesUsed());
        cache.access("a");
        assertEquals(40, cache.hotBytesUsed());
        cache.access("b");
        assertEquals(80, cache.hotBytesUsed());
        cache.access("c"); // evicts a: 80 - 40 + 40 == 80
        assertEquals(80, cache.hotBytesUsed());
    }

    @Test
    void concurrentAccessHoldsTheCapacityInvariant() throws InterruptedException {
        long capacity = 300;
        ElasticNodeCache cache = new ElasticNodeCache(capacity, true);
        List<String> ids = List.of("u0", "u1", "u2", "u3", "u4", "u5");
        for (String id : ids) {
            // fetch(1) + hot(1) == 2 < cold(100): every access wants to promote.
            cache.register(new UnitProfile(id, 100, 1.0, 1.0, 100.0));
        }

        int threadCount = 8;
        int accessesPerThread = 500;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        for (int t = 0; t < threadCount; t++) {
            pool.submit(() -> {
                try {
                    Random random = new Random();
                    for (int i = 0; i < accessesPerThread; i++) {
                        cache.access(ids.get(random.nextInt(ids.size())));
                    }
                } catch (Throwable failed) {
                    failure.compareAndSet(null, failed);
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(done.await(30, TimeUnit.SECONDS), "worker threads did not finish in time");
        pool.shutdown();

        assertNull(failure.get(), "no accessing thread should throw");
        assertTrue(cache.hotBytesUsed() <= capacity,
                "hot bytes used (" + cache.hotBytesUsed() + ") must never exceed capacity (" + capacity + ")");
    }
}
