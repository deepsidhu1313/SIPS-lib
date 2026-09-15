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

import java.util.Map;

import in.co.s13.sips.lib.ml.elastic.ElasticNodeCache;

/**
 * The composition point {@code docs/MODEL_PARALLELISM.md} names as unstarted: a node serving
 * expert requests consulting its own elastic memory pool to decide whether the expert it was
 * just asked for is already fast-tier resident, instead of assuming its own workload is always
 * immediately ready.
 *
 * <p>Expert serving and elastic nodes answer two different questions — which node should handle
 * this request, and what should that node keep in its own fast-but-small memory — and neither
 * package needs to know the other exists to answer its own question correctly. This class is
 * the seam between them: it wraps a {@link ExpertServingRunner.RequestExecutor}, so from
 * {@link ExpertServingRunner}'s point of view nothing has changed, while on the way to the real
 * work it gives the node's {@link ElasticNodeCache} — if the node is configured with one at all
 * — a chance to record the access and run its own promotion/eviction accounting. The real work
 * always still happens; this class only ever adds a side effect in front of it, the same way
 * {@code ElasticLayerExecutor} does for the pipeline package against the same
 * {@link ElasticNodeCache} API.
 *
 * <p>A node with no entry in {@code cacheByNode} is opting out of elastic accounting entirely —
 * every mixture-of-experts fleet need not model per-node memory hierarchy, and a node that
 * doesn't should not be forced into it just to reach the rest of the fleet that does.
 */
public final class ElasticRequestExecutor implements ExpertServingRunner.RequestExecutor {

    private final Map<String, ElasticNodeCache> cacheByNode;
    private final ExpertServingRunner.RequestExecutor delegate;

    public ElasticRequestExecutor(Map<String, ElasticNodeCache> cacheByNode,
            ExpertServingRunner.RequestExecutor delegate) {
        if (cacheByNode == null) {
            throw new IllegalArgumentException("cacheByNode must not be null");
        }
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        this.cacheByNode = Map.copyOf(cacheByNode);
        this.delegate = delegate;
    }

    /**
     * Consults {@code nodeUuid}'s elastic cache, if it has one, using {@code expertId} as the
     * resident-unit id, then always runs the real request.
     *
     * @throws IllegalStateException propagated uncaught from {@link ElasticNodeCache#access} if
     *         {@code expertId} was never registered with that node's cache — a real
     *         configuration bug (a node given a cache but never told what it should hold), not
     *         something to swallow silently
     */
    @Override
    public void process(String expertId, String nodeUuid, Object payload) {
        ElasticNodeCache cache = cacheByNode.get(nodeUuid);
        if (cache == null) {
            delegate.process(expertId, nodeUuid, payload);
            return;
        }
        cache.access(expertId);
        delegate.process(expertId, nodeUuid, payload);
    }
}
