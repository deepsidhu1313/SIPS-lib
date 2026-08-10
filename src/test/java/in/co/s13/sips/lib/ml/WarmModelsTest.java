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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loading a model once and answering many requests with it.
 *
 * <p>A chunk is a cold process: start, load, run, exit. That is the right
 * shape for a ten-minute simulation chunk, where the start-up cost is noise.
 * It is the wrong shape for inference, where the work per request is
 * milliseconds and loading the model is seconds — the framework would spend
 * all of its time getting ready to work.
 *
 * <p>So a loaded model outlives the request that needed it, and the next
 * request for the same model finds it ready. Held by content address, so
 * "the same model" is a question about bytes and never about a name that
 * might now mean something else.
 */
class WarmModelsTest {

    @AfterEach
    void standDown() {
        WarmModels.standDown();
    }

    /** Something expensive to build and worth closing. */
    static final class Loaded implements AutoCloseable {

        final String name;
        boolean closed;

        Loaded(String name) {
            this.name = name;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @Test
    void loadsOnceAndAnswersTwice() {
        AtomicInteger loads = new AtomicInteger();

        Loaded first = WarmModels.get("abc123", () -> {
            loads.incrementAndGet();
            return new Loaded("model");
        });
        Loaded second = WarmModels.get("abc123", () -> {
            loads.incrementAndGet();
            return new Loaded("model");
        });

        assertEquals(1, loads.get(), "the second request should not reload the model");
        assertSame(first, second);
    }

    @Test
    void adifferentModelIsLoadedSeparately() {
        Loaded one = WarmModels.get("aaa", () -> new Loaded("one"));
        Loaded other = WarmModels.get("bbb", () -> new Loaded("other"));

        assertEquals("one", one.name);
        assertEquals("other", other.name);
        assertEquals(2, WarmModels.held());
    }

    @Test
    void aModelThatFailsToLoadIsNotHeld() {
        // Caching a failure would make one bad load permanent for the life of
        // the process, and the next request would get the same exception with
        // no attempt to do better.
        assertThrows(IllegalStateException.class, () -> WarmModels.get("bad", () -> {
            throw new IllegalStateException("corrupt weights");
        }));

        assertEquals(0, WarmModels.held());
        Loaded recovered = WarmModels.get("bad", () -> new Loaded("second attempt"));
        assertEquals("second attempt", recovered.name);
    }

    @Test
    void anIdleModelIsReleased() {
        // A model held forever is a leak with a plausible excuse. A worker
        // that served one job an hour ago should not still be holding its
        // weights.
        AtomicLong now = new AtomicLong(1000);
        WarmModels.clock(now::get);

        Loaded held = WarmModels.get("abc", () -> new Loaded("model"));
        now.addAndGet(WarmModels.IDLE_MILLIS + 1);
        WarmModels.releaseIdle();

        assertEquals(0, WarmModels.held());
        assertTrue(held.closed, "an evicted model should be closed, not just dropped");
    }

    @Test
    void aModelStillInUseIsNotReleased() {
        AtomicLong now = new AtomicLong(1000);
        WarmModels.clock(now::get);
        WarmModels.get("abc", () -> new Loaded("model"));

        now.addAndGet(WarmModels.IDLE_MILLIS + 1);
        // Asked for again, so it is not idle any more.
        WarmModels.get("abc", () -> new Loaded("should not be built"));
        WarmModels.releaseIdle();

        assertEquals(1, WarmModels.held());
    }

    @Test
    void standingDownClosesEverything() {
        Loaded one = WarmModels.get("aaa", () -> new Loaded("one"));
        Loaded other = WarmModels.get("bbb", () -> new Loaded("other"));

        WarmModels.standDown();

        assertEquals(0, WarmModels.held());
        assertTrue(one.closed);
        assertTrue(other.closed);
    }

    @Test
    void aModelThatFailsToCloseDoesNotStopTheOthersBeingClosed() {
        // Stand-down runs when a node is shutting down or a job is finishing.
        // One resource that will not let go must not strand the rest.
        WarmModels.get("bad", () -> (AutoCloseable) () -> {
            throw new IllegalStateException("will not close");
        });
        Loaded good = WarmModels.get("good", () -> new Loaded("good"));

        WarmModels.standDown();

        assertTrue(good.closed);
        assertEquals(0, WarmModels.held());
    }

    @Test
    @Timeout(30)
    void concurrentRequestsForOneModelLoadItOnce() throws Exception {
        // The case that matters: a batch arrives and every worker thread wants
        // the same model at the same moment. Loading it once per thread would
        // multiply the one cost this exists to avoid -- and on a large model
        // would run the node out of memory doing it.
        int threads = 8;
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Loaded> seen = new CopyOnWriteArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    seen.add(WarmModels.get("abc", () -> {
                        loads.incrementAndGet();
                        Thread.sleep(50);
                        return new Loaded("model");
                    }));
                    return null;
                });
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
        }

        assertEquals(1, loads.get(), "every thread should have waited for the one load");
        assertEquals(threads, seen.size());
        assertTrue(seen.stream().allMatch(model -> model == seen.get(0)),
                "every thread should have got the same instance");
    }

    @Test
    void aModelThatIsNotCloseableIsStillHeldAndDropped() {
        // Not everything worth keeping warm owns a resource: a parsed WASM
        // module is just memory.
        WarmModels.get("plain", () -> "just a string");

        assertEquals(1, WarmModels.held());
        WarmModels.standDown();
        assertEquals(0, WarmModels.held());
    }

    @Test
    void nothingIsHeldBeforeAnythingIsAskedFor() {
        assertEquals(0, WarmModels.held());
        assertFalse(WarmModels.holds("abc"));
    }

    @Test
    void nonsenseIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> WarmModels.get(null, () -> "x"));
        assertThrows(IllegalArgumentException.class, () -> WarmModels.get("  ", () -> "x"));
        assertThrows(IllegalArgumentException.class, () -> WarmModels.get("abc", null));
    }
}
