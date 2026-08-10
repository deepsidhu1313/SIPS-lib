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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Finishing a round when a worker goes into someone's pocket.
 *
 * <p>A round ends when its slowest worker ends, and on a fleet of phones the
 * slowest worker is sometimes one that will never answer at all — the screen
 * locked, the network dropped, the owner walked out of range. Waiting stalls
 * everything behind it; abandoning the shard trains the round on less data than
 * it believes it has, which is worse because nothing reports it.
 *
 * <p>So a shard that misses its deadline is handed to somebody else while the
 * original is still out, and the first answer wins. The loser's answer is not a
 * failure — it is simply late, and is dropped.
 *
 * <h2>The trap</h2>
 *
 * <p>Accepting one shard twice would overweight that shard's data in the
 * average. Not a crash: a plausible-looking model that is quietly wrong. So
 * acceptance is once per shard, and {@link #accept} says whether it counted.
 *
 * <p>This tracks assignments and nothing else — it holds no weights and does no
 * networking, because whether a round can finish is a question about who was
 * asked and who replied.
 */
public final class SpeculativeRound {

    /** One shard handed to one worker. */
    public record Assignment(int shard, String worker) {
    }

    private final int shards;
    private final List<String> workers;
    private final long deadlineMillis;

    /** Who has been asked for each shard, in the order they were asked. */
    private final Map<Integer, Set<String>> issued = new LinkedHashMap<>();

    /** When each shard was first issued, so lateness is measurable. */
    private final Map<Integer, Long> issuedAt = new LinkedHashMap<>();

    /** Who answered each shard first. */
    private final Map<Integer, String> answered = new LinkedHashMap<>();

    /**
     * @param shards how many pieces the round is divided into
     * @param workers every worker that may be asked, most capable first
     * @param deadlineMillis how long a shard may be out before it is also
     *        given to someone else
     */
    public SpeculativeRound(int shards, List<String> workers, long deadlineMillis) {
        if (shards < 1) {
            throw new IllegalArgumentException("A round needs at least one shard");
        }
        if (workers == null || workers.isEmpty()) {
            throw new IllegalArgumentException("A round needs at least one worker");
        }
        if (deadlineMillis < 1) {
            throw new IllegalArgumentException("A deadline of " + deadlineMillis
                    + "ms would re-issue everything immediately");
        }
        if (workers.size() < shards) {
            throw new IllegalArgumentException(workers.size() + " workers cannot each take "
                    + "one of " + shards + " shards; ask for less parallelism");
        }
        this.shards = shards;
        this.workers = List.copyOf(workers);
        this.deadlineMillis = deadlineMillis;
    }

    /** Hands every shard to a worker, one each. */
    public List<Assignment> issue(long now) {
        List<Assignment> assignments = new ArrayList<>();
        for (int shard = 0; shard < shards; shard++) {
            String worker = workers.get(shard % workers.size());
            issued.computeIfAbsent(shard, key -> new LinkedHashSet<>()).add(worker);
            issuedAt.put(shard, now);
            assignments.add(new Assignment(shard, worker));
        }
        return assignments;
    }

    /**
     * Hands any overdue shard to an additional worker.
     *
     * <p>The original copy is left running rather than cancelled: it may
     * still be the one that answers, and cancelling costs a round trip to a
     * node that is by definition not responding.
     *
     * @return the new assignments, empty if nothing is overdue or there is
     *         nobody left who has not already been asked
     */
    public List<Assignment> reissue(long now) {
        List<Assignment> assignments = new ArrayList<>();
        // Spread across workers rather than piling every stalled shard onto
        // the one healthy node, which would just make it the new bottleneck.
        Set<String> usedThisPass = new LinkedHashSet<>();
        for (int shard = 0; shard < shards; shard++) {
            if (answered.containsKey(shard)) {
                continue;
            }
            Long since = issuedAt.get(shard);
            if (since == null || now - since < deadlineMillis) {
                continue;
            }
            Set<String> already = issued.getOrDefault(shard, Set.of());
            String fresh = null;
            for (String worker : workers) {
                if (!already.contains(worker) && !usedThisPass.contains(worker)) {
                    fresh = worker;
                    break;
                }
            }
            if (fresh == null) {
                // Everyone has been asked. Not silently dropped: the shard
                // stays outstanding and the round stays incomplete, so a
                // caller sees that it is short rather than averaging one
                // fewer contribution than it thinks it has.
                continue;
            }
            already.add(fresh);
            usedThisPass.add(fresh);
            issuedAt.put(shard, now);
            assignments.add(new Assignment(shard, fresh));
        }
        return assignments;
    }

    /**
     * Records an answer.
     *
     * @return whether it counted — false if this shard already has an answer,
     *         or if this worker was never asked for it
     */
    public boolean accept(int shard, String worker, long now) {
        Set<String> asked = issued.get(shard);
        if (asked == null || !asked.contains(worker)) {
            // Once workers are strangers' devices, a result nobody asked for
            // has no place in the average.
            return false;
        }
        if (answered.containsKey(shard)) {
            return false;
        }
        answered.put(shard, worker);
        return true;
    }

    /** Whether every shard has an answer. */
    public boolean complete() {
        return answered.size() == shards;
    }

    /** The shards still without an answer. */
    public List<Integer> outstanding() {
        List<Integer> waiting = new ArrayList<>();
        for (int shard = 0; shard < shards; shard++) {
            if (!answered.containsKey(shard)) {
                waiting.add(shard);
            }
        }
        return waiting;
    }

    /** The answers taken, by shard. */
    public Map<Integer, String> accepted() {
        return Map.copyOf(answered);
    }

    /** Who answered a shard, if anyone has. */
    public Optional<String> answeredBy(int shard) {
        return Optional.ofNullable(answered.get(shard));
    }
}
