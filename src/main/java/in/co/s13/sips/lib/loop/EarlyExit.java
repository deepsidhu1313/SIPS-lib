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
package in.co.s13.sips.lib.loop;

import java.io.Serializable;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stopping a parallel loop early, in the two ways that are actually wanted.
 *
 * <p>A single "break" conflates two different questions. Separating them is
 * what stops a user getting a wrong answer instead of merely a slow one.
 *
 * <h2>breakAll — search</h2>
 *
 * "I found it; nobody needs to keep looking." Brute-force key recovery, a
 * preimage search, a SAT solver. Any match will do, and the finder carries a
 * value home.
 *
 * <pre>{@code
 * for (long candidate = 0; candidate < keyspace; candidate++) {
 *     if (matches(candidate)) {
 *         sim.breakAll(candidate, describe(candidate));   // every node stops
 *     }
 * }
 * }</pre>
 *
 * This is <b>find-any, not find-first</b>. If two nodes match simultaneously,
 * the one that reports first wins — which may not be the lower index. Where the
 * lowest matching index is required, collect every match and take the minimum
 * instead.
 *
 * <h2>breakAfter — prefix</h2>
 *
 * "Nothing beyond index N is wanted." A series that has converged, a simulation
 * that reached steady state, a scan that passed the last relevant record.
 *
 * <pre>{@code
 * if (converged(i)) {
 *     sim.breakAfter(i, "converged");   // chunks past i are cancelled
 * }
 * }</pre>
 *
 * Crucially this <b>does not cancel everything</b>. Chunks before the boundary
 * are still needed — their results are part of the answer — and a chunk
 * straddling the boundary must run because it contains wanted iterations.
 * Cancelling those would silently truncate the result.
 *
 * <h2>What neither can promise</h2>
 *
 * Iterations already running are not interrupted; they finish. Iterations with
 * an index beyond the stopping point may already have completed elsewhere, and
 * their side effects stand.
 */
public final class EarlyExit implements Serializable {

    /** How the loop was stopped. */
    public enum Mode {
        /** Still running. */
        RUNNING,
        /** A value was found; nothing further is wanted. */
        BREAK_ALL,
        /** Only iterations up to a boundary are wanted. */
        BREAK_AFTER
    }

    private static final long NO_BOUNDARY = Long.MAX_VALUE;

    private final String jobToken;
    private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.RUNNING);
    private final AtomicReference<Object> result = new AtomicReference<>();
    private final AtomicLong foundAt = new AtomicLong(-1);
    private final AtomicLong boundary = new AtomicLong(NO_BOUNDARY);
    private final AtomicReference<String> reason = new AtomicReference<>("");

    public EarlyExit(String jobToken) {
        if (jobToken == null || jobToken.isBlank()) {
            throw new IllegalArgumentException("A job token is required");
        }
        this.jobToken = jobToken;
    }

    public String jobToken() {
        return jobToken;
    }

    /**
     * Stops the whole loop, carrying a value back.
     *
     * @param index the iteration that found it, for reporting
     * @param value the answer; may be null to stop without returning anything.
     *              Must be serialisable, since it travels back from the node
     *              that found it.
     * @return true if this call stopped the loop, false if it was already stopped
     */
    public boolean breakAll(long index, Serializable value) {
        if (index < 0) {
            throw new IllegalArgumentException("Index cannot be negative: " + index);
        }
        // A definite answer supersedes a prefix boundary: if we have the value,
        // even the prefix is unnecessary.
        Mode previous = mode.getAndSet(Mode.BREAK_ALL);
        if (previous == Mode.BREAK_ALL) {
            return false;               // an earlier finder already won
        }
        result.set(value);
        foundAt.set(index);
        boundary.set(-1);               // nothing at all should run
        reason.compareAndSet("", "breakAll at " + index);
        return true;
    }

    /**
     * Stops iterations beyond {@code lastWantedIndex}.
     *
     * <p>The boundary index itself is wanted; "after 100" means 101 onward.
     *
     * @return true if this tightened the boundary
     */
    public boolean breakAfter(long lastWantedIndex, String why) {
        if (lastWantedIndex < 0) {
            throw new IllegalArgumentException("Index cannot be negative: " + lastWantedIndex);
        }
        if (mode.get() == Mode.BREAK_ALL) {
            return false;               // already stopping everything
        }
        // The tighter bound wins: anything past it was already unwanted, so a
        // later, looser bound must not loosen it.
        boolean tightened = boundary.getAndAccumulate(lastWantedIndex, Math::min) > lastWantedIndex;
        mode.compareAndSet(Mode.RUNNING, Mode.BREAK_AFTER);
        if (tightened) {
            reason.set(why == null ? "" : why);
        }
        return tightened;
    }

    public boolean isStopped() {
        return mode.get() != Mode.RUNNING;
    }

    public Mode mode() {
        return mode.get();
    }

    public String reason() {
        return reason.get();
    }

    /** The value carried back by {@link #breakAll}, if any. */
    @SuppressWarnings("unchecked")
    public Optional<Object> result() {
        return Optional.ofNullable(result.get());
    }

    /** The iteration that called {@link #breakAll}. */
    public Optional<Long> foundAt() {
        long at = foundAt.get();
        return at < 0 ? Optional.empty() : Optional.of(at);
    }

    /** The last wanted index, if a boundary was set. */
    public Optional<Long> boundary() {
        long at = boundary.get();
        return at == NO_BOUNDARY || at < 0 ? Optional.empty() : Optional.of(at);
    }

    /**
     * Whether a chunk covering {@code [firstIndex, lastIndex]} should still run.
     *
     * <p>This is the question the scheduler and each node ask. A chunk that
     * merely straddles the boundary must run: cancelling it would drop wanted
     * iterations and silently truncate the result.
     */
    public boolean shouldRunChunk(long firstIndex, long lastIndex) {
        switch (mode.get()) {
            case BREAK_ALL:
                return false;
            case BREAK_AFTER:
                return firstIndex <= boundary.get();
            case RUNNING:
            default:
                return true;
        }
    }
}
