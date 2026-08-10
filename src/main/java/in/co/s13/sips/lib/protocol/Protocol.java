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
package in.co.s13.sips.lib.protocol;

import org.json.JSONObject;

/**
 * What a node can be told, and how it says what it understands.
 *
 * <p>Nodes talk to each other in JSON over sockets, and until now every message
 * assumed the peer was running the same build. That assumption held only because
 * nobody had upgraded half a cluster. The failures it produces are quiet: a node
 * given work it cannot run fails the chunk, and the reason surfaces as a
 * compilation error in a log on a different machine.
 *
 * <h2>How a peer announces itself</h2>
 *
 * <p>Every node puts {@link #FIELD} in its ping reply. A peer that sends none is
 * {@link #UNKNOWN}: it predates this, so nothing can be assumed about it beyond
 * the original protocol.
 *
 * <p>Deliberately carried on the ping rather than a handshake of its own. Ping
 * already happens once per discovery cycle and already carries what a node can
 * do — memory, accelerators, benchmarks — so a version belongs beside them. A
 * handshake per connection would add a round trip to every chunk sent.
 *
 * <h2>The distinction that matters</h2>
 *
 * <p>Not every new message is dangerous to an old node. Sending one it does not
 * recognise is harmless — the handler ignores it. Sending a <em>job</em> it
 * cannot run is not: it accepts the work, fails, and the cluster loses the time.
 * {@link Compatibility} is that difference, and it is why this is negotiation
 * rather than a version stamp.
 */
public final class Protocol {

    /** The field a node announces its protocol version in. */
    public static final String FIELD = "PROTOCOL";

    /**
     * What this build speaks.
     *
     * <p>Raise it when a change would confuse a node running the previous
     * release, and give the feature below the new number.
     */
    public static final int VERSION = 2;

    /**
     * A peer that announced no version at all.
     *
     * <p>Every release before this one. It may well understand more than the
     * baseline — early exit shipped in 1.1.0 — but it never says so, and
     * guessing is what this exists to stop.
     */
    public static final int UNKNOWN = 0;

    private Protocol() {
    }

    /** How an older node reacts to something it was not built for. */
    public enum Compatibility {

        /**
         * It ignores the message. Nothing is lost by trying, so a peer of
         * unknown version is worth attempting.
         */
        IGNORED_BY_OLDER,

        /**
         * It accepts the work and then fails it. Attempting this against a peer
         * that cannot say it understands wastes the chunk and reports the
         * failure somewhere unhelpful.
         */
        BREAKS_ON_OLDER
    }

    /** Something one node may ask another to do. */
    public enum Feature {

        /**
         * {@code breakAll} and {@code breakAfter}. An older node has no case for
         * them and drops the message, so the loop simply runs to completion —
         * slower, but correct.
         */
        EARLY_EXIT(1, Compatibility.IGNORED_BY_OLDER),

        /**
         * A chunk delivered as a WebAssembly module. An older node reads the
         * manifest expecting {@code MAIN}, does not find it, and throws.
         */
        WASM_TASKS(1, Compatibility.BREAKS_ON_OLDER),

        /**
         * A manifest declaring {@code STAGES}. An older node runs it down the
         * single-loop path, which finds no loop and produces nothing.
         */
        STAGED_JOBS(1, Compatibility.BREAKS_ON_OLDER),

        /**
         * A small chunk result carried home inside the finish message. An older
         * master ignores the extra field, so the result is simply not collected
         * that way.
         */
        INLINE_RESULTS(1, Compatibility.IGNORED_BY_OLDER),

        /**
         * Fetching a chunk result too large to have ridden home, by asking the
         * node that produced it for it by name. A version 1 node does not
         * recognise the command and answers nothing at all, so a master that
         * needs one gets a timeout rather than a model — which is why this
         * breaks rather than degrades: the alternative is a stage that
         * averages the shards it happened to receive.
         */
        FETCHED_RESULTS(2, Compatibility.BREAKS_ON_OLDER);

        private final int since;
        private final Compatibility compatibility;

        Feature(int since, Compatibility compatibility) {
            this.since = since;
            this.compatibility = compatibility;
        }

        /** The first protocol version that understands this. */
        public int since() {
            return since;
        }

        public Compatibility compatibility() {
            return compatibility;
        }
    }

    /**
     * Whether it is worth sending this to a peer.
     *
     * <p>A peer of {@link #UNKNOWN} version gets features an older node would
     * ignore, and not the ones it would fail. That is the useful default: it
     * keeps a mixed cluster working for everything that degrades safely, and
     * refuses only what would waste work.
     */
    public static boolean canSend(Feature feature, int peerVersion) {
        if (feature == null) {
            throw new IllegalArgumentException("feature must not be null");
        }
        if (peerVersion >= feature.since()) {
            return true;
        }
        return feature.compatibility() == Compatibility.IGNORED_BY_OLDER;
    }

    /**
     * Why a peer cannot be sent something, for a log or a job status.
     *
     * <p>Stated in terms of what to do about it, because "protocol 0 &lt; 1" is
     * not something an operator can act on.
     */
    public static String refusalReason(Feature feature, int peerVersion) {
        return "This node speaks protocol " + peerVersion + " ("
                + (peerVersion == UNKNOWN ? "a release before protocol negotiation" : "older")
                + ") and cannot run " + feature.name().toLowerCase(java.util.Locale.ROOT)
                .replace('_', ' ') + ", which needs protocol " + feature.since()
                + ". Upgrade it, or the job will only use nodes that can.";
    }

    /** The version two nodes have in common: the older of the two. */
    public static int agreed(int mine, int theirs) {
        return Math.min(Math.max(mine, UNKNOWN), Math.max(theirs, UNKNOWN));
    }

    /** Reads a peer's version out of a message, defaulting to {@link #UNKNOWN}. */
    public static int of(JSONObject message) {
        if (message == null) {
            return UNKNOWN;
        }
        int announced = message.optInt(FIELD, UNKNOWN);
        // A peer claiming a version this build has never heard of is treated as
        // speaking this build's version: it knows about us, we do not know about
        // it, and it is the newer one's job to stay compatible.
        return Math.max(UNKNOWN, Math.min(announced, VERSION));
    }

    /** Puts this build's version on an outgoing message. */
    public static JSONObject stamp(JSONObject message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        return message.put(FIELD, VERSION);
    }
}
