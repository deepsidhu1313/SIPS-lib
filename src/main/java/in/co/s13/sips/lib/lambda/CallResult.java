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
package in.co.s13.sips.lib.lambda;

import java.time.Duration;
import java.util.Optional;

/**
 * What came back from a {@link ClusterCall}.
 *
 * <p>Carries where it ran and how long it took as well as the bytes. A caller
 * choosing a placement policy has no way to tell whether it did anything unless
 * the result says which node was chosen, and a policy nobody can evaluate is a
 * setting rather than a decision.
 */
public final class CallResult {

    private final long status;
    private final byte[] output;
    private final String node;
    private final Duration took;
    private final String failure;

    private CallResult(long status, byte[] output, String node, Duration took, String failure) {
        this.status = status;
        this.output = output;
        this.node = node;
        this.took = took;
        this.failure = failure;
    }

    public static CallResult success(byte[] output, String node, Duration took) {
        return new CallResult(0, output == null ? new byte[0] : output.clone(),
                node, took == null ? Duration.ZERO : took, null);
    }

    /** A call the function itself reported as failed, by returning non-zero. */
    public static CallResult status(long status, String node, Duration took) {
        return new CallResult(status, new byte[0], node,
                took == null ? Duration.ZERO : took,
                "the module returned status " + status);
    }

    /** A call that never produced a status: it trapped, timed out, or never landed. */
    public static CallResult failed(String reason, String node, Duration took) {
        return new CallResult(-1, new byte[0], node, took == null ? Duration.ZERO : took,
                reason == null ? "no reason given" : reason);
    }

    public boolean isSuccess() {
        return failure == null;
    }

    /** What the function wrote. Empty unless it succeeded. */
    public byte[] output() {
        return output.clone();
    }

    /** The status the function returned; zero is success. */
    public long status() {
        return status;
    }

    /** Which node ran it, if it got that far. */
    public Optional<String> node() {
        return Optional.ofNullable(node);
    }

    public Duration took() {
        return took;
    }

    public Optional<String> failureReason() {
        return Optional.ofNullable(failure);
    }

    /**
     * The output, or an exception if the call failed.
     *
     * <p>For callers who would rather not check — the common case when a call
     * stands in for a function.
     */
    public byte[] orThrow() {
        if (!isSuccess()) {
            throw new IllegalStateException("Call failed: " + failure
                    + node().map(where -> " (on " + where + ")").orElse(""));
        }
        return output();
    }

    @Override
    public String toString() {
        return isSuccess()
                ? "CallResult[ok " + output.length + " bytes from " + node
                        + " in " + took.toMillis() + " ms]"
                : "CallResult[" + failure + "]";
    }
}
