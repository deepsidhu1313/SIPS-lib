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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Early exit from a parallel loop, and what it does and does not promise.
 *
 * <h2>Why this is not called break</h2>
 *
 * Sequential {@code break} promises that no later iteration runs. A distributed
 * loop cannot promise that: by the time one node decides to stop, another may
 * already have finished iterations far ahead of it. Honouring the sequential
 * promise would mean committing iterations strictly in order, which throws away
 * the parallelism that justified distributing the loop.
 *
 * <p>Every framework that has met this problem resolves it the same way, and
 * names it honestly:
 *
 * <ul>
 *   <li><b>OpenMP</b> forbids branching out of a parallel region at all, and
 *       added {@code #pragma omp cancel} — cooperative, best-effort.</li>
 *   <li><b>Java streams</b> separate {@code findFirst} (ordered, expensive)
 *       from {@code findAny} (unordered, cheap).</li>
 *   <li><b>Rayon</b> separates {@code find_first} from {@code find_any}.</li>
 *   <li><b>Intel TBB</b> has no break; it has {@code task_group::cancel}.</li>
 * </ul>
 *
 * <h2>What cancelling guarantees</h2>
 *
 * <ul>
 *   <li>No <em>further</em> chunks are handed out once cancellation is
 *       observed.</li>
 *   <li>Cancellation is visible to every node that checks after it is set.</li>
 *   <li>Exactly one reason is recorded, even if several nodes cancel at once.</li>
 * </ul>
 *
 * <h2>What it does not guarantee</h2>
 *
 * <ul>
 *   <li>Iterations already running are <b>not</b> interrupted; they finish.</li>
 *   <li>Iterations with an index after the cancelling one may already have
 *       completed. Their side effects stand.</li>
 *   <li>There is no "first match" ordering. If you need the lowest index that
 *       satisfies a condition, collect all matches and take the minimum — do
 *       not assume the first cancel came from the lowest index.</li>
 * </ul>
 *
 * <p>This suits search: "some node found an answer, stop spending effort". It
 * does not suit a loop whose correctness depends on stopping before a
 * particular iteration.
 */
public final class LoopCancellation implements Serializable {

    private final String jobToken;
    private final AtomicReference<String> reason = new AtomicReference<>(null);
    private final AtomicInteger completed = new AtomicInteger();
    private final AtomicInteger completedAfterCancel = new AtomicInteger();

    public LoopCancellation(String jobToken) {
        if (jobToken == null || jobToken.isBlank()) {
            throw new IllegalArgumentException("A job token is required");
        }
        this.jobToken = jobToken;
    }

    public String jobToken() {
        return jobToken;
    }

    /**
     * Requests that no further chunks be handed out.
     *
     * @param why recorded for the job log, so an early finish is explainable
     * @return true if this call was the one that cancelled; false if the loop
     *         was already cancelled
     */
    public boolean cancel(String why) {
        // compareAndSet, so exactly one reason wins a race between nodes and
        // the outcome is deterministic to read afterwards.
        return reason.compareAndSet(null, why == null ? "" : why);
    }

    public boolean isCancelled() {
        return reason.get() != null;
    }

    /** Why the loop stopped early, or empty if it has not. */
    public String reason() {
        String why = reason.get();
        return why == null ? "" : why;
    }

    /** Whether the scheduler should keep handing out chunks. */
    public boolean shouldContinue() {
        return !isCancelled();
    }

    /** Records that an iteration finished, so the tail can be reported. */
    public void recordCompleted(int index) {
        completed.incrementAndGet();
        if (isCancelled()) {
            completedAfterCancel.incrementAndGet();
        }
    }

    public int completedCount() {
        return completed.get();
    }

    /**
     * How many iterations finished after cancellation was set.
     *
     * <p>Expected to be non-zero on any real cluster. It is reported rather
     * than hidden, because a user who assumed sequential {@code break} needs to
     * see that the assumption did not hold.
     */
    public int completedAfterCancel() {
        return completedAfterCancel.get();
    }
}
