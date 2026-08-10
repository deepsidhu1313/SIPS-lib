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

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * What one chunk of a stage is being asked to do, written beside its sources.
 *
 * <p>A chunk arrives on a node as a directory of files plus the job's manifest,
 * and the manifest is the same for every chunk. Anything that differs per chunk
 * — which slice of the iteration space, which inputs, where to leave the result
 * — has to travel some other way. This is that way: a small JSON file the
 * distributor writes into the chunk directory and the executor reads back.
 *
 * <p>It started as the two numbers a WebAssembly module needs. A pipeline needs
 * more: a stage that reads another stage's output has to be told which files
 * those are, and a stage whose output feeds the next one has to be told what to
 * call it. Both are per chunk, so both belong here.
 *
 * <p>Every field is optional on read. A chunk written by an older master carries
 * only the range, and must still run.
 */
public final class ChunkSpec {

    /** The file this is written to, inside the chunk directory. */
    public static final String FILE = "chunk.json";

    private static final String FIRST = "FIRST";
    private static final String LAST = "LAST";
    private static final String OUTPUT = "OUTPUT";
    private static final String INPUTS = "INPUTS";
    private static final String STAGE = "STAGE";
    private static final String SHARD = "SHARD";

    private final long firstIndex;
    private final long lastIndexExclusive;
    private final String output;
    private final List<String> inputs;
    private final String stageName;
    private final int shard;

    private ChunkSpec(Builder builder) {
        this.firstIndex = builder.firstIndex;
        this.lastIndexExclusive = builder.lastIndexExclusive;
        this.output = builder.output;
        this.inputs = List.copyOf(builder.inputs);
        this.stageName = builder.stageName;
        this.shard = builder.shard;
    }

    public static Builder range(long firstIndex, long lastIndexExclusive) {
        return new Builder(firstIndex, lastIndexExclusive);
    }

    /** Reads a spec, tolerating one written by an older master. */
    public static ChunkSpec read(JSONObject json) {
        if (json == null) {
            throw new IllegalArgumentException("A chunk spec is required");
        }
        Builder builder = range(json.optLong(FIRST, 0), json.optLong(LAST, 1))
                .output(json.optString(OUTPUT, null))
                .stage(json.optString(STAGE, null))
                .shard(json.optInt(SHARD, -1));
        JSONArray inputs = json.optJSONArray(INPUTS);
        if (inputs != null) {
            for (int i = 0; i < inputs.length(); i++) {
                builder.input(inputs.getString(i));
            }
        }
        return builder.build();
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put(FIRST, firstIndex);
        json.put(LAST, lastIndexExclusive);
        if (output != null) {
            json.put(OUTPUT, output);
        }
        if (!inputs.isEmpty()) {
            json.put(INPUTS, new JSONArray(inputs));
        }
        if (stageName != null) {
            json.put(STAGE, stageName);
        }
        if (shard >= 0) {
            json.put(SHARD, shard);
        }
        return json;
    }

    public long firstIndex() {
        return firstIndex;
    }

    public long lastIndexExclusive() {
        return lastIndexExclusive;
    }

    /** What this chunk should call its result, relative to the chunk directory. */
    public java.util.Optional<String> output() {
        return java.util.Optional.ofNullable(output);
    }

    /**
     * Files placed in the chunk directory by the stages this one reads.
     *
     * <p>Named rather than discovered, so a task knows what to open without
     * listing a directory and guessing.
     */
    public List<String> inputs() {
        return inputs;
    }

    /** The stage this chunk belongs to, for logs and for a task that cares. */
    public java.util.Optional<String> stageName() {
        return java.util.Optional.ofNullable(stageName);
    }

    /**
     * Which shard of its stage this is, counting from zero.
     *
     * <p>Distinct from the chunk number, which runs across the whole job so two
     * stages cannot collide in the distribution table. A task that wants "am I
     * worker 3 of 8" wants this one.
     */
    public int shard() {
        return shard;
    }

    /** Builds a spec. Only the range is required. */
    public static final class Builder {

        private final long firstIndex;
        private final long lastIndexExclusive;
        private String output;
        private final List<String> inputs = new ArrayList<>();
        private String stageName;
        private int shard = -1;

        private Builder(long firstIndex, long lastIndexExclusive) {
            if (lastIndexExclusive < firstIndex) {
                throw new IllegalArgumentException("Inverted range: " + firstIndex
                        + ".." + lastIndexExclusive);
            }
            this.firstIndex = firstIndex;
            this.lastIndexExclusive = lastIndexExclusive;
        }

        public Builder output(String output) {
            this.output = output == null || output.isBlank() ? null : output.trim();
            return this;
        }

        public Builder input(String input) {
            if (input != null && !input.isBlank()) {
                inputs.add(input.trim());
            }
            return this;
        }

        public Builder stage(String stageName) {
            this.stageName = stageName == null || stageName.isBlank() ? null : stageName.trim();
            return this;
        }

        public Builder shard(int shard) {
            this.shard = shard;
            return this;
        }

        public ChunkSpec build() {
            return new ChunkSpec(this);
        }
    }
}
