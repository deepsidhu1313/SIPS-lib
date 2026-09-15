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

import java.time.Clock;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-to-node pinning with expiry, so a multi-turn client keeps landing
 * on the node already warm for it without pinning forever.
 *
 * <p>A pin that never expired would slowly turn into a map of every session
 * that ever connected, most of them long abandoned, and would keep routing a
 * returning client to a node that evicted its context ages ago. Expiry is
 * measured against an injected {@link Clock} rather than the wall clock so
 * tests can move time forward deterministically instead of sleeping through
 * a real timeout.
 *
 * <p>A pinned node can also be marked unavailable, independently of expiry:
 * {@link #nodeFor} then reads as absent for routing purposes, exactly the
 * way an expired session does, but the underlying {@link Session} — crucially
 * its {@link Session#state()} — is not deleted. That is what lets a session
 * survive its node disappearing mid-conversation: {@link #stateFor} keeps
 * answering for it regardless of node availability, and {@link #pin}
 * carries the old state into wherever the session lands next.
 */
public final class SessionRegistry {

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final Set<String> unavailableNodes = ConcurrentHashMap.newKeySet();
    private final Duration timeout;
    private final Clock clock;

    public SessionRegistry(Duration timeout, Clock clock) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.timeout = timeout;
        this.clock = clock;
    }

    /**
     * Creates or overwrites the session's pinned node and refreshes its last-seen time,
     * preserving any state the session already carried — re-pinning, including onto a
     * different node after the old one was marked unavailable, must not discard it.
     */
    public void pin(String sessionId, String nodeUuid) {
        Session existing = sessions.get(sessionId);
        Object state = existing == null ? null : existing.state();
        sessions.put(sessionId, new Session(sessionId, nodeUuid, clock.millis(), state));
    }

    /**
     * Refreshes the last-seen time of an existing session without changing its pinned node.
     *
     * @throws IllegalStateException naming the session if it does not exist (including if it
     *         has already expired and been evicted) — touching a session that is gone is a
     *         real caller bug worth surfacing, not a silent no-op
     */
    public void touch(String sessionId) {
        Session current = sessions.get(sessionId);
        if (current == null || isExpired(current)) {
            throw new IllegalStateException("No such session '" + sessionId + "'");
        }
        sessions.put(sessionId, new Session(sessionId, current.nodeUuid(), clock.millis(), current.state()));
    }

    /**
     * Updates only the {@code state} of an existing session, refreshing its last-seen time too
     * since an update is itself activity. Works regardless of whether the session's pinned node
     * is currently marked unavailable — state is specifically what is supposed to outlive a node
     * failure — but not for a session that never existed at all.
     *
     * @throws IllegalStateException naming the session if it has never been {@link #pin}ned —
     *         mirrors {@link #touch}'s convention for an unknown session. A time-expired session
     *         is refused the same way {@link #touch} refuses one.
     */
    public void updateState(String sessionId, Object state) {
        Session current = sessions.get(sessionId);
        if (current == null || isExpired(current)) {
            throw new IllegalStateException("No such session '" + sessionId + "'");
        }
        sessions.put(sessionId, new Session(sessionId, current.nodeUuid(), clock.millis(), state));
    }

    /**
     * @return the session's state if it exists and is not time-expired, regardless of whether
     *         its pinned node is currently marked unavailable — empty for an unknown, expired,
     *         or state-never-set session
     */
    public Optional<Object> stateFor(String sessionId) {
        Session current = sessions.get(sessionId);
        if (current == null || isExpired(current) || current.state() == null) {
            return Optional.empty();
        }
        return Optional.of(current.state());
    }

    /**
     * @return the pinned node for {@code sessionId}, or empty if the session does not exist, has
     *         expired, or is pinned to a node currently marked {@link #markUnavailable} — an
     *         expired-but-not-yet-evicted session, or one pinned to an unavailable node, behaves
     *         as if absent, so a caller never has to call {@link #evictExpired()} first for
     *         correctness
     */
    public Optional<String> nodeFor(String sessionId) {
        Session current = sessions.get(sessionId);
        if (current == null || isExpired(current) || unavailableNodes.contains(current.nodeUuid())) {
            return Optional.empty();
        }
        return Optional.of(current.nodeUuid());
    }

    /** Marks {@code nodeUuid} unavailable: sessions pinned to it read as absent from {@link #nodeFor}. */
    public void markUnavailable(String nodeUuid) {
        if (nodeUuid == null || nodeUuid.isBlank()) {
            throw new IllegalArgumentException("nodeUuid must not be blank");
        }
        unavailableNodes.add(nodeUuid);
    }

    /** Marks {@code nodeUuid} available again: a still-live pin to it becomes routable once more. */
    public void markAvailable(String nodeUuid) {
        if (nodeUuid == null || nodeUuid.isBlank()) {
            throw new IllegalArgumentException("nodeUuid must not be blank");
        }
        unavailableNodes.remove(nodeUuid);
    }

    /** Removes every session whose last-seen time is past the timeout. @return the count removed */
    public int evictExpired() {
        int removed = 0;
        Iterator<Map.Entry<String, Session>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            if (isExpired(iterator.next().getValue())) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    private boolean isExpired(Session session) {
        return clock.millis() - session.lastSeenMillis() > timeout.toMillis();
    }
}
