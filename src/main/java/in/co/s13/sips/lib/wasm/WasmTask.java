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

import java.io.Serializable;
import java.nio.file.Path;

/**
 * A unit of work as a precompiled WebAssembly module.
 *
 * <h2>Why this exists alongside the Java path</h2>
 *
 * A Java chunk is source shipped to a node, compiled with Ant, and run on a
 * JVM. That costs hundreds of milliseconds before any work happens, requires a
 * JDK and Ant on every node, and cannot run on iOS at all, since the platform
 * forbids both a JIT and loading executable code at runtime.
 *
 * <p>A WebAssembly chunk is a precompiled module. Measured with Chicory, a
 * module starts and runs in about two microseconds — five orders of magnitude
 * cheaper than the Ant path. That difference is what makes fine-grained work
 * and phone participation possible rather than theoretical.
 *
 * <h2>The contract a module must satisfy</h2>
 *
 * Export a function taking the half-open iteration range and returning a status:
 *
 * <pre>
 * (func (export "run") (param i64 i64) (result i64))
 *                            ^     ^            ^
 *                        first   last+1     0 = success
 * </pre>
 *
 * The range is passed rather than baked in, so one module serves every chunk of
 * a loop — the node does not recompile or rewrite anything per chunk.
 *
 * <h2>What it deliberately cannot do</h2>
 *
 * A module has no ambient authority: no sockets, no filesystem, no clock,
 * unless a host function is explicitly supplied. That is a property of the
 * format rather than a policy this class enforces, and it is the reason a WASM
 * chunk from an untrusted submitter is a far smaller risk than a Java one.
 */
public final class WasmTask implements Serializable {

    /** Exported function used when a manifest names none. */
    public static final String DEFAULT_ENTRY_POINT = "run";

    /** Returned by a module that completed successfully. */
    public static final long SUCCESS = 0;

    private final String jobToken;
    private final int chunkNumber;
    private final Path module;
    private final String entryPoint;
    private final long firstIndex;
    private final long lastIndexExclusive;

    /**
     * @param module             the {@code .wasm} file
     * @param entryPoint         exported function; null uses {@link #DEFAULT_ENTRY_POINT}
     * @param firstIndex         first iteration, inclusive
     * @param lastIndexExclusive one past the last iteration
     */
    public WasmTask(String jobToken, int chunkNumber, Path module, String entryPoint,
            long firstIndex, long lastIndexExclusive) {
        if (jobToken == null || jobToken.isBlank()) {
            throw new IllegalArgumentException("A job token is required");
        }
        if (module == null) {
            throw new IllegalArgumentException("A module path is required");
        }
        if (chunkNumber < 0) {
            throw new IllegalArgumentException("Chunk number cannot be negative: " + chunkNumber);
        }
        if (lastIndexExclusive < firstIndex) {
            throw new IllegalArgumentException("Range " + firstIndex + ".."
                    + lastIndexExclusive + " ends before it starts");
        }
        this.jobToken = jobToken;
        this.chunkNumber = chunkNumber;
        this.module = module;
        this.entryPoint = entryPoint == null || entryPoint.isBlank()
                ? DEFAULT_ENTRY_POINT : entryPoint;
        this.firstIndex = firstIndex;
        this.lastIndexExclusive = lastIndexExclusive;
    }

    public String jobToken() {
        return jobToken;
    }

    public int chunkNumber() {
        return chunkNumber;
    }

    public Path module() {
        return module;
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

    /** How many iterations this chunk covers. */
    public long iterationCount() {
        return lastIndexExclusive - firstIndex;
    }

    @Override
    public String toString() {
        return "WasmTask[" + jobToken + " chunk " + chunkNumber + " "
                + firstIndex + ".." + lastIndexExclusive + " -> " + entryPoint + "]";
    }
}
