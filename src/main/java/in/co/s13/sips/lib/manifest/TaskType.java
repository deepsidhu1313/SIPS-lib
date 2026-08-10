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
package in.co.s13.sips.lib.manifest;

/**
 * How a chunk is executed, declared by {@code TYPE} in a job's manifest.
 *
 * <p>Inferring this from which other fields happen to be present works right up
 * until a manifest carries both, or carries one by mistake — at which point the
 * node silently picks an executor the author did not intend. Declaring it makes
 * the manifest say what the job is, and makes a wrong manifest fail with a
 * sentence instead of a surprise.
 *
 * <p>A type is added here only once a node can actually run it. A manifest that
 * names a type this node does not know is rejected rather than guessed at.
 */
public enum TaskType {

    /**
     * Java source, compiled on the node and run in a forked JVM. The original
     * SIPS model, and still the default when {@code TYPE} is absent, so every
     * manifest written before this field existed keeps working.
     *
     * <p>Requires {@code MAIN}.
     */
    JAVA,

    /**
     * A precompiled WebAssembly module, run inside the node's own process.
     *
     * <p>Costs microseconds to start rather than the hundreds of milliseconds of
     * javac plus JVM startup, which is what lets a scheduler hand out chunks
     * small enough to balance uneven work.
     *
     * <p>Requires a {@code WASM} block naming the module.
     */
    WASM;

    /** What a manifest omitting {@code TYPE} means. */
    public static final TaskType DEFAULT = JAVA;

    /**
     * Reads the value of a manifest's {@code TYPE} field.
     *
     * @param declared the manifest value; null or blank means {@link #DEFAULT}
     * @throws IllegalArgumentException naming the types this node supports
     */
    public static TaskType of(String declared) {
        if (declared == null || declared.isBlank()) {
            return DEFAULT;
        }
        for (TaskType type : values()) {
            if (type.name().equalsIgnoreCase(declared.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown task TYPE '" + declared
                + "'. This node runs: " + java.util.Arrays.toString(values()));
    }

    /** The manifest value for this type, as it should be written. */
    public String manifestValue() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
