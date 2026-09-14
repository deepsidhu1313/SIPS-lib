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
 */
public final class SessionRegistry {

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
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

    /** Creates or overwrites the session's pinned node and refreshes its last-seen time. */
    public void pin(String sessionId, String nodeUuid) {
        sessions.put(sessionId, new Session(sessionId, nodeUuid, clock.millis()));
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
        sessions.put(sessionId, new Session(sessionId, current.nodeUuid(), clock.millis()));
    }

    /**
     * @return the pinned node for {@code sessionId}, or empty if the session does not exist or
     *         has expired — an expired-but-not-yet-evicted session behaves as if absent, so a
     *         caller never has to call {@link #evictExpired()} first for correctness
     */
    public Optional<String> nodeFor(String sessionId) {
        Session current = sessions.get(sessionId);
        if (current == null || isExpired(current)) {
            return Optional.empty();
        }
        return Optional.of(current.nodeUuid());
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
