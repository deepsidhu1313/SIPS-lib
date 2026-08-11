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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Models that outlive the request that loaded them.
 *
 * <p>A chunk is a cold process: start, load, run, exit. That is the right
 * shape for a ten-minute simulation chunk, where start-up is noise. It is the
 * wrong shape for inference, where the work per request is milliseconds and
 * loading the model is seconds — a framework built that way spends all of its
 * time getting ready to work, and no amount of parallelism fixes it because
 * every worker pays it separately.
 *
 * <p>So a loaded model stays loaded, and the next request for it finds it
 * ready. Held by content address, so "the same model" is a question about
 * bytes and never about a name that might now mean something else.
 *
 * <h2>What is kept, and for how long</h2>
 *
 * <p>Anything expensive to build: a parsed WASM module, a dequantised weight
 * matrix, a native handle. A model held forever is a leak with a plausible
 * excuse, so anything untouched for {@link #IDLE_MILLIS} is released the next
 * time {@link #releaseIdle()} runs, and {@link AutoCloseable} models are closed
 * rather than merely dropped.
 *
 * <p>Loading happens under a per-address lock, so a batch that starts eight
 * threads all wanting the same model loads it once and the other seven wait.
 * Loading it per thread would multiply the exact cost this exists to avoid, and
 * on a large model would run the node out of memory doing it.
 */
public final class WarmModels {

    /** How long a model may go untouched before it is released. */
    public static final long IDLE_MILLIS = 10 * 60 * 1000L;

    private static final Map<String, Held> HELD = new ConcurrentHashMap<>();

    /** Injectable so idle behaviour can be tested without waiting ten minutes. */
    private static volatile LongSupplier clock = System::currentTimeMillis;

    /** Builds something worth keeping. */
    @FunctionalInterface
    public interface Loader<T> {

        T load() throws Exception;
    }

    private record Held(Object model, long[] lastUsed) {
    }

    private WarmModels() {
    }

    /**
     * The model at this address, loading it if this is the first request.
     *
     * @param address the content address of the model — the checksum the asset
     *        cache holds it under, so two jobs shipping the same weights share
     *        one loaded copy
     * @param loader how to build it, called at most once per address
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(String address, Loader<T> loader) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("A model address is required");
        }
        if (loader == null) {
            throw new IllegalArgumentException("A loader is required");
        }

        // computeIfAbsent holds a lock on the bin for the duration, which is
        // what makes eight threads wanting one model load it once. The cost is
        // that a slow load blocks other addresses hashing to the same bin --
        // acceptable, since the alternative is loading a model per thread.
        Held held = HELD.computeIfAbsent(address, key -> {
            try {
                return new Held(loader.load(), new long[]{clock.getAsLong()});
            } catch (RuntimeException | Error alreadyUnchecked) {
                throw alreadyUnchecked;
            } catch (Exception checked) {
                throw new IllegalStateException("Could not load model " + key, checked);
            }
        });
        // A failed load throws out of computeIfAbsent without recording
        // anything, so one bad load never becomes permanent.
        held.lastUsed()[0] = clock.getAsLong();
        return (T) held.model();
    }

    /** Whether a model is loaded right now. */
    public static boolean holds(String address) {
        return HELD.containsKey(address);
    }

    /** How many models are loaded. */
    public static int held() {
        return HELD.size();
    }

    /**
     * Releases anything untouched for {@link #IDLE_MILLIS}.
     *
     * <p>Called on a timer by a node, or by a job that knows it is finished.
     * Deliberately not automatic on a background thread inside this class: a
     * library that starts threads is a library that is hard to embed.
     *
     * @return how many models were released
     */
    public static int releaseIdle() {
        long now = clock.getAsLong();
        int released = 0;
        for (Map.Entry<String, Held> entry : HELD.entrySet()) {
            if (now - entry.getValue().lastUsed()[0] > IDLE_MILLIS
                    && HELD.remove(entry.getKey(), entry.getValue())) {
                close(entry.getValue().model());
                released++;
            }
        }
        return released;
    }

    /** Releases everything, closing what can be closed. */
    public static void standDown() {
        List<Object> models = new ArrayList<>();
        for (String address : List.copyOf(HELD.keySet())) {
            Held held = HELD.remove(address);
            if (held != null) {
                models.add(held.model());
            }
        }
        // Removed from the map first, so one resource that will not let go
        // cannot strand the rest -- stand-down runs when a node is shutting
        // down, which is the worst time to leave things half done.
        for (Object model : models) {
            close(model);
        }
    }

    /** Replaces the clock. For tests; idle behaviour is otherwise ten minutes away. */
    static void clock(LongSupplier replacement) {
        clock = replacement == null ? System::currentTimeMillis : replacement;
    }

    private static void close(Object model) {
        if (model instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception refused) {
                // Nothing useful to do and nothing worth failing for: the
                // model is already out of the map, so it will not be handed to
                // anyone again whether or not it let go of its resources.
                // java.util.logging rather than the Java 9 platform logger,
                // which ART does not have -- this class runs on Android workers.
                java.util.logging.Logger.getLogger(WarmModels.class.getName())
                        .log(java.util.logging.Level.WARNING,
                                "A warm model refused to close", refused);
            }
        }
    }
}
