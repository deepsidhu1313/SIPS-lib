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

import in.co.s13.sips.lib.job.Job;
import in.co.s13.sips.lib.job.Stage;
import in.co.s13.sips.lib.manifest.TaskType;
import in.co.s13.sips.lib.wasm.WasmTask;
import in.co.s13.sips.scheduler.PlacementPolicy;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * One function, some bytes, and a policy deciding where it runs.
 *
 * <pre>{@code
 * byte[] thumbnail = ClusterCall.of(Path.of("thumbnail.wasm"), imageBytes)
 *         .placedBy(new NearestData())
 *         .timeout(Duration.ofSeconds(30))
 *         .on(dispatcher)
 *         .orThrow();
 * }</pre>
 *
 * <p>No manifest, no distribution directory, no chunk range. It is a pipeline of
 * exactly one stage, and says so — {@link #asJob(String)} produces that stage, so a
 * call goes through the same graph, the same sequencer and the same placement
 * policies as anything else. Building a second execution path for it would mean
 * two sets of bugs.
 *
 * <p>What makes this worth having only became true recently. A request-sized
 * unit of work is worth scheduling at all when it starts in microseconds; it
 * never was when every task cost a javac and a JVM, which is why this is a WASM
 * front door and not a general one.
 *
 * <p>What it deliberately is not: SIPS provisions nothing, so this is not the
 * defining property of a serverless platform. It is placement as a service over
 * a cluster you already run — which is the half a serverless platform does not
 * let you touch.
 */
public final class ClusterCall {

    /** The stage name a call becomes. Single, so it needs no index placeholder. */
    public static final String STAGE_NAME = "call";

    private final Path module;
    private final byte[] input;
    private final String entryPoint;
    private final long firstIndex;
    private final long lastIndexExclusive;
    private final Duration timeout;
    private final PlacementPolicy placement;

    private ClusterCall(Builder builder) {
        this.module = builder.module;
        this.input = builder.input;
        this.entryPoint = builder.entryPoint;
        this.firstIndex = builder.firstIndex;
        this.lastIndexExclusive = builder.lastIndexExclusive;
        this.timeout = builder.timeout;
        this.placement = builder.placement;
    }

    /** A call with no input: everything the function needs is in its range. */
    public static Builder of(Path module) {
        return of(module, new byte[0]);
    }

    public static Builder of(Path module, byte[] input) {
        return new Builder(module, input);
    }

    public Path module() {
        return module;
    }

    public byte[] input() {
        return input.clone();
    }

    public String entryPoint() {
        return entryPoint;
    }

    public long firstIndex() {
        return firstIndex;
    }

    public long lastIndexExclusive() {
        return lastIndexExclusive;
    }

    public Duration timeout() {
        return timeout;
    }

    /** How the node is chosen, if the caller cared. */
    public Optional<PlacementPolicy> placement() {
        return Optional.ofNullable(placement);
    }

    /**
     * This call as a one-stage job.
     *
     * <p>The reason a call needs no machinery of its own.
     */
    public Job asJob(String name) {
        Job job = new Job(name);
        Stage stage = job.single(STAGE_NAME).type(TaskType.WASM).timeout(timeout);
        job.validate();
        return job;
    }

    /** Runs this call and waits for its result. */
    public CallResult on(CallDispatcher dispatcher) {
        if (dispatcher == null) {
            throw new IllegalArgumentException("dispatcher must not be null");
        }
        return dispatcher.dispatch(this);
    }

    @Override
    public String toString() {
        return "ClusterCall[" + module.getFileName() + " " + input.length + " bytes in]";
    }

    /** Builds a call. Only the module is required. */
    public static final class Builder {

        private final Path module;
        private final byte[] input;
        private String entryPoint = WasmTask.DEFAULT_ENTRY_POINT;
        private long firstIndex;
        private long lastIndexExclusive = 1;
        private Duration timeout = Duration.ofMinutes(1);
        private PlacementPolicy placement;

        private Builder(Path module, byte[] input) {
            if (module == null) {
                throw new IllegalArgumentException("A call needs a module");
            }
            this.module = module;
            this.input = input == null ? new byte[0] : input.clone();
        }

        /** The exported function to invoke. Defaults to {@code run}. */
        public Builder entryPoint(String entryPoint) {
            this.entryPoint = entryPoint == null || entryPoint.isBlank()
                    ? WasmTask.DEFAULT_ENTRY_POINT
                    : entryPoint.trim();
            return this;
        }

        /**
         * The index range handed to the function.
         *
         * <p>Defaults to a single iteration, {@code [0, 1)} — a call is one
         * invocation, not a loop. Widening it is how a caller asks for a small
         * loop without building a whole job.
         */
        public Builder range(long firstIndex, long lastIndexExclusive) {
            if (lastIndexExclusive < firstIndex) {
                throw new IllegalArgumentException("Inverted range: " + firstIndex
                        + ".." + lastIndexExclusive);
            }
            this.firstIndex = firstIndex;
            this.lastIndexExclusive = lastIndexExclusive;
            return this;
        }

        /**
         * How long to wait.
         *
         * <p>A call always has one, unlike a stage. Something waiting on a
         * result needs an answer, and "never" is not one.
         */
        public Builder timeout(Duration timeout) {
            if (timeout == null || timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("timeout must be positive, was " + timeout);
            }
            this.timeout = timeout;
            return this;
        }

        /** Which node should run it. Left to the dispatcher when unset. */
        public Builder placedBy(PlacementPolicy placement) {
            this.placement = placement;
            return this;
        }

        public ClusterCall build() {
            return new ClusterCall(this);
        }

        /** Builds and runs in one step. */
        public CallResult on(CallDispatcher dispatcher) {
            return build().on(dispatcher);
        }
    }
}
