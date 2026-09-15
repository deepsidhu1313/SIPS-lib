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
package in.co.s13.sips.lib.ml.pipeline;

import in.co.s13.sips.lib.array.Mat;
import in.co.s13.sips.lib.ml.elastic.ElasticNodeCache;
import java.util.Map;

/**
 * The composition point {@code docs/MODEL_PARALLELISM.md} names as real,
 * unstarted work: a node hosting a pipeline stage consulting its own
 * {@link ElasticNodeCache} before running that stage, instead of assuming its
 * resident weights are always immediately ready.
 *
 * <p>This class decides nothing about correctness. The wrapped
 * {@link PipelineRunner.LayerExecutor} delegate is still the one and only
 * source of the real answer, every time, for every node — what
 * {@code cacheByNode} decides is purely conceptual expense: whether this
 * particular access to this particular stage on this particular node ran HOT
 * or COLD, and whether it was worth promoting. A node with no entry in
 * {@code cacheByNode} has opted out of that accounting entirely and behaves
 * exactly as if this wrapper were not there at all — composition with
 * elastic nodes is strictly opt-in, per node, not a global switch every
 * {@link PipelineRunner} now has to pay for.
 *
 * <p>A stage's own {@link LayerGroup#name()} doubles as the resident-unit id
 * {@link ElasticNodeCache} tracks, since a pipeline stage already has a name
 * unique within its model (see {@link PipelineModel}) and there is no reason
 * to invent a second identifier for the same thing. A stage a node is
 * supposedly hosting but was never {@link ElasticNodeCache#register}ed for is
 * a real configuration bug — the wiring between a plan's assignment and a
 * node's cache setup disagreeing about what that node hosts — and is
 * surfaced by letting {@link ElasticNodeCache}'s own
 * {@code IllegalStateException} propagate rather than being swallowed or
 * defaulted around.
 */
public final class ElasticLayerExecutor implements PipelineRunner.LayerExecutor {

    private final Map<String, ElasticNodeCache> cacheByNode;
    private final PipelineRunner.LayerExecutor delegate;

    public ElasticLayerExecutor(Map<String, ElasticNodeCache> cacheByNode, PipelineRunner.LayerExecutor delegate) {
        if (cacheByNode == null) {
            throw new IllegalArgumentException("An elastic layer executor needs a cache-by-node map, even if empty");
        }
        if (delegate == null) {
            throw new IllegalArgumentException("An elastic layer executor needs a delegate executor");
        }
        this.cacheByNode = Map.copyOf(cacheByNode);
        this.delegate = delegate;
    }

    /**
     * Charges this access against {@code nodeUuid}'s cache, if it has one,
     * then always runs the real computation through the delegate — the cache
     * only ever observes an access, it never stands in for one.
     */
    @Override
    public Mat execute(LayerGroup stage, String nodeUuid, Mat input) {
        ElasticNodeCache cache = cacheByNode.get(nodeUuid);
        if (cache != null) {
            cache.access(stage.name());
        }
        return delegate.execute(stage, nodeUuid, input);
    }
}
