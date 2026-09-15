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

import java.util.List;
import java.util.Optional;

/**
 * Decides which node serves one request, combining session affinity with
 * load-aware expert selection.
 *
 * <p>Affinity and load balancing pull in opposite directions: affinity wants
 * a session to keep landing on the node it already warmed, load balancing
 * wants every request to land on whoever is least busy right now. This class
 * resolves the conflict with a strict priority order — affinity wins, but
 * only when the pinned node can actually serve the expert this request
 * needs, since a session's pin may be left over from a previous request to a
 * different expert entirely.
 *
 * <p>Routing is kept separate from executing the request (see {@link
 * ExpertServingRunner}) so the decision itself — which is all this class
 * makes — can be unit tested without simulating any work or its completion.
 */
public final class ExpertRouter {

    private final ExpertRegistry experts;
    private final SessionRegistry sessions;

    public ExpertRouter(ExpertRegistry experts, SessionRegistry sessions) {
        if (experts == null) {
            throw new IllegalArgumentException("experts must not be null");
        }
        if (sessions == null) {
            throw new IllegalArgumentException("sessions must not be null");
        }
        this.experts = experts;
        this.sessions = sessions;
    }

    /**
     * Where a request landed, whether it landed there because of session affinity, and whether
     * landing elsewhere counted as recovering a session that already carried state (as opposed
     * to a genuinely fresh one) — see {@link #route} for exactly when each is set.
     */
    public record Routed(String nodeUuid, boolean sessionAffinityHit, boolean recovered) {
    }

    /**
     * Routes one request for {@code expertId} on behalf of {@code sessionId}.
     *
     * <p>Prefers the session's pinned node when that node actually hosts the requested expert;
     * otherwise falls back to the least-loaded replica and re-pins the session there. The pinned
     * node reads as absent — and this method falls through the same way — whether the session
     * was never pinned, has expired, or its previously-pinned node has since been marked
     * unavailable. In that fallback case, a non-empty {@link SessionRegistry#stateFor} means the
     * session existed before with real carried-over state, so this is a recovery rather than a
     * fresh session; {@code recovered} reports exactly that. A normal affinity hit is never
     * itself a recovery.
     */
    public Routed route(String sessionId, String expertId) {
        Optional<String> pinned = sessions.nodeFor(sessionId);
        if (pinned.isPresent()) {
            List<String> replicas = experts.replicasOf(expertId);
            if (replicas.contains(pinned.get())) {
                sessions.touch(sessionId);
                return new Routed(pinned.get(), true, false);
            }
        }
        boolean recovered = sessions.stateFor(sessionId).isPresent();
        String chosen = experts.leastLoaded(expertId);
        sessions.pin(sessionId, chosen);
        return new Routed(chosen, false, recovered);
    }
}
