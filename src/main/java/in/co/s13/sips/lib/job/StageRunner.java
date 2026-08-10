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
package in.co.s13.sips.lib.job;

import java.util.Optional;

/**
 * What it means to actually run a {@link Stage}.
 *
 * <p>The one thing {@link JobSequencer} deliberately does not know. Splitting it
 * out is what lets the same driving logic run a pipeline on a live cluster, in a
 * simulation, or in a test with no network at all — and it means the ordering
 * rules can be proven correct without standing up nodes.
 *
 * <p>Starting is expected to be asynchronous: a stage becomes many chunks on many
 * nodes, and {@link #start} returns as soon as they are away rather than when
 * they finish. Progress is reported by polling, because that is what the node's
 * distribution table already supports — chunk results arrive as messages and
 * land in a table, with nobody to call back.
 */
public interface StageRunner {

    /**
     * Begins running a stage.
     *
     * <p>Anything thrown here is treated as the stage failing immediately, so an
     * implementation may report a distribution failure either way.
     */
    StageExecution start(Stage stage);

    /** A stage that has been started, and how to ask how it is going. */
    interface StageExecution {

        /** Where a started stage has got to. */
        enum Outcome {
            RUNNING, COMPLETE, FAILED
        }

        /**
         * How the stage is doing right now.
         *
         * <p>Called repeatedly and expected to be cheap: on a node this is a
         * count over the distribution table, not a network round trip.
         */
        Outcome poll();

        /** Why it failed, if it did. */
        default Optional<String> failureReason() {
            return Optional.empty();
        }

        /**
         * Asks for the stage to stop.
         *
         * <p>Called when the stage has outlived its {@link Stage#timeout()}.
         * Best-effort: a chunk already running on a node may not be
         * interruptible, and the runner does not wait to find out.
         */
        default void cancel() {
        }
    }
}
