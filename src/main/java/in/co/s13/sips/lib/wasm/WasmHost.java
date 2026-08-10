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
package in.co.s13.sips.lib.wasm;

import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Memory;
import com.dylibso.chicory.wasm.types.ValType;
import in.co.s13.sips.lib.loop.EarlyExit;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

/**
 * What a WebAssembly chunk is allowed to do besides arithmetic.
 *
 * <p>A module with no host interface can only turn two integers into one, which
 * is not enough to be a unit of real work. This is the smallest interface that
 * makes it one: read the chunk's input, write a result, say something, and stop
 * the loop early.
 *
 * <p>Everything the module can reach is on this list. There is no file access,
 * no network, no clock and no allocator — which is why a module can be run
 * inside the node's own process rather than behind a fork. Deciding what a chunk
 * may touch by choosing what to import is the whole security model, and it is
 * enforced by the runtime rather than by convention.
 *
 * <h2>The imports</h2>
 *
 * <pre>
 * (import "sips" "input_size"   (func (result i32)))
 * (import "sips" "input_read"   (func (param i32 i32 i32) (result i32)))
 * (import "sips" "output_write" (func (param i32 i32)))
 * (import "sips" "log"          (func (param i32 i32)))
 * (import "sips" "break_all"    (func (param i64 i32 i32)))
 * (import "sips" "break_after"  (func (param i64)))
 * </pre>
 *
 * <p>All six are supplied whether or not a module declares them; a module
 * imports only what it uses. {@code input_read} copies into memory the module
 * already owns — {@code (destination, sourceOffset, length)}, returning the
 * bytes actually copied — so no allocator is needed on either side.
 *
 * <p>A module that touches memory must export it, conventionally as
 * {@code memory}.
 *
 * <p>One host serves one run: it accumulates that run's output. Reuse across
 * chunks would mix their results.
 */
public final class WasmHost {

    /** The import namespace every SIPS host function lives in. */
    public static final String NAMESPACE = "sips";

    /** Enough for an image tile or a model shard; small enough that a runaway module is caught. */
    public static final int DEFAULT_MAX_OUTPUT_BYTES = 64 * 1024 * 1024;

    private static final int MAX_LOG_LINE_BYTES = 64 * 1024;

    private final byte[] input;
    private final Consumer<String> logger;
    private final EarlyExit earlyExit;
    private final int maxOutputBytes;
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    private WasmHost(Builder builder) {
        this.input = builder.input;
        this.logger = builder.logger;
        this.earlyExit = builder.earlyExit;
        this.maxOutputBytes = builder.maxOutputBytes;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** A host offering no input, discarding output and ignoring early exit. */
    public static WasmHost none() {
        return builder().build();
    }

    /** Everything the module wrote through {@code output_write}, in order. */
    public byte[] output() {
        return output.toByteArray();
    }

    /** The bytes this chunk was given. */
    public byte[] input() {
        return input.clone();
    }

    /**
     * The host functions to bind into an instance.
     *
     * <p>Bound to one {@code Instance} at instantiation time, which is why the
     * runner instantiates per run rather than caching instances.
     */
    List<HostFunction> functions() {
        return List.of(
                function("input_size", List.of(), List.of(ValType.I32),
                        (instance, args) -> new long[]{input.length}),

                function("input_read", List.of(ValType.I32, ValType.I32, ValType.I32),
                        List.of(ValType.I32),
                        (instance, args) -> new long[]{inputRead(instance,
                            (int) args[0], (int) args[1], (int) args[2])}),

                function("output_write", List.of(ValType.I32, ValType.I32), List.of(),
                        (instance, args) -> {
                            outputWrite(instance, (int) args[0], (int) args[1]);
                            return null;
                        }),

                function("log", List.of(ValType.I32, ValType.I32), List.of(),
                        (instance, args) -> {
                            log(instance, (int) args[0], (int) args[1]);
                            return null;
                        }),

                function("break_all", List.of(ValType.I64, ValType.I32, ValType.I32), List.of(),
                        (instance, args) -> {
                            breakAll(instance, args[0], (int) args[1], (int) args[2]);
                            return null;
                        }),

                function("break_after", List.of(ValType.I64), List.of(),
                        (instance, args) -> {
                            if (earlyExit != null) {
                                earlyExit.breakAfter(args[0], "requested by module");
                            }
                            return null;
                        }));
    }

    private static HostFunction function(String name, List<ValType> params, List<ValType> results,
            com.dylibso.chicory.runtime.WasmFunctionHandle handle) {
        return new HostFunction(NAMESPACE, name, params, results, handle);
    }

    private int inputRead(Instance instance, int destination, int sourceOffset, int length) {
        if (sourceOffset < 0 || length < 0 || sourceOffset > input.length) {
            throw new WasmRunner.WasmExecutionException("input_read(" + destination + ", "
                    + sourceOffset + ", " + length + ") is outside the " + input.length
                    + "-byte input");
        }
        int copied = Math.min(length, input.length - sourceOffset);
        memoryOf(instance, "input_read").write(destination, input, sourceOffset, copied);
        return copied;
    }

    private void outputWrite(Instance instance, int offset, int length) {
        if (length < 0) {
            throw new WasmRunner.WasmExecutionException("output_write length is negative: " + length);
        }
        if (output.size() + (long) length > maxOutputBytes) {
            throw new WasmRunner.WasmExecutionException("module wrote more than the "
                    + maxOutputBytes + "-byte output limit");
        }
        output.writeBytes(memoryOf(instance, "output_write").readBytes(offset, length));
    }

    private void log(Instance instance, int offset, int length) {
        if (length < 0 || length > MAX_LOG_LINE_BYTES) {
            throw new WasmRunner.WasmExecutionException("log length out of range: " + length);
        }
        logger.accept(new String(memoryOf(instance, "log").readBytes(offset, length),
                StandardCharsets.UTF_8));
    }

    private void breakAll(Instance instance, long index, int offset, int length) {
        if (earlyExit == null) {
            return;
        }
        if (length < 0) {
            throw new WasmRunner.WasmExecutionException("break_all length is negative: " + length);
        }
        // Raw bytes: only the module knows what its answer means, so the host
        // carries it home without interpreting it.
        earlyExit.breakAll(index,
                length == 0 ? null : memoryOf(instance, "break_all").readBytes(offset, length));
    }

    private static Memory memoryOf(Instance instance, String called) {
        Memory memory = instance.memory();
        if (memory == null) {
            throw new WasmRunner.WasmExecutionException("Module called " + called
                    + " but declares no memory. A module using the host interface must define "
                    + "and export its memory.");
        }
        return memory;
    }

    /** Builds a host. Every part is optional; what you leave out, the module cannot use. */
    public static final class Builder {

        private byte[] input = new byte[0];
        private Consumer<String> logger = line -> {
        };
        private EarlyExit earlyExit;
        private int maxOutputBytes = DEFAULT_MAX_OUTPUT_BYTES;

        private Builder() {
        }

        /** The bytes this chunk operates on — an image tile, a shard, a parameter block. */
        public Builder input(byte[] input) {
            this.input = input == null ? new byte[0] : input.clone();
            return this;
        }

        /** Where {@code log} lines go. Defaults to discarding them. */
        public Builder log(Consumer<String> logger) {
            if (logger == null) {
                throw new IllegalArgumentException("logger must not be null");
            }
            this.logger = logger;
            return this;
        }

        /**
         * The job's early-exit state. Without one, {@code break_all} and
         * {@code break_after} are accepted and ignored, so a module written for
         * a searching job still runs in a job that does not search.
         */
        public Builder earlyExit(EarlyExit earlyExit) {
            this.earlyExit = earlyExit;
            return this;
        }

        public Builder maxOutputBytes(int maxOutputBytes) {
            if (maxOutputBytes <= 0) {
                throw new IllegalArgumentException("maxOutputBytes must be positive");
            }
            this.maxOutputBytes = maxOutputBytes;
            return this;
        }

        public WasmHost build() {
            return new WasmHost(this);
        }
    }
}
