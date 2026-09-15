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

/**
 * One multi-turn client's pin to the node currently warm for it.
 *
 * <p>Repeat requests from the same session are cheaper to serve on whatever
 * node already holds its context than on whichever replica happens to be
 * least loaded this instant, so {@link SessionRegistry} remembers this pin
 * until it expires.
 *
 * <p>{@code state} is an opaque payload this package never inspects — a
 * conversation history, a KV-cache reference, whatever a caller needs to
 * resume this session elsewhere. It is what is supposed to survive a pin
 * being replaced, whether by ordinary re-pinning or by recovery onto a
 * different node after the pinned one becomes unavailable.
 */
public record Session(String sessionId, String nodeUuid, long lastSeenMillis, Object state) {

    public Session {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (nodeUuid == null || nodeUuid.isBlank()) {
            throw new IllegalArgumentException("nodeUuid must not be blank");
        }
    }
}
