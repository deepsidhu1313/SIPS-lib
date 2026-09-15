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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import in.co.s13.sips.lib.ml.elastic.ElasticNodeCache;
import in.co.s13.sips.lib.ml.elastic.UnitProfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The composition seam between expert serving and elastic nodes: a node's own cache gets a
 * chance to record the access, but the real work always still happens.
 */
class ElasticRequestExecutorTest {

    private record Call(String expertId, String nodeUuid, Object payload) {
    }

    @Test
    void aNodeWithNoConfiguredCacheDelegatesStraightThrough() {
        List<Call> calls = new ArrayList<>();
        ExpertServingRunner.RequestExecutor delegate =
                (expertId, nodeUuid, payload) -> calls.add(new Call(expertId, nodeUuid, payload));
        ElasticRequestExecutor executor = new ElasticRequestExecutor(Map.of(), delegate);

        executor.process("coder", "node-a", "payload-1");

        assertEquals(List.of(new Call("coder", "node-a", "payload-1")), calls);
    }

    @Test
    void aConfiguredCacheRecordsTheAccessAndTheRealWorkStillHappens() {
        List<Call> calls = new ArrayList<>();
        ExpertServingRunner.RequestExecutor delegate =
                (expertId, nodeUuid, payload) -> calls.add(new Call(expertId, nodeUuid, payload));
        ElasticNodeCache cache = new ElasticNodeCache(1000, true);
        cache.register(new UnitProfile("coder", 100, 5.0, 1.0, 50.0));
        ElasticRequestExecutor executor = new ElasticRequestExecutor(Map.of("node-a", cache), delegate);

        executor.process("coder", "node-a", "payload-1");

        // Consistent with ElasticNodeCache's own tested behaviour: a first, cheap-to-promote
        // access lands "coder" in the HOT tier.
        assertTrue(cache.hotUnits().contains("coder"));
        assertEquals(100, cache.hotBytesUsed());
        assertEquals(List.of(new Call("coder", "node-a", "payload-1")), calls);
    }

    @Test
    void anUnregisteredUnitOnAConfiguredNodeThrowsAndIsNotSwallowed() {
        List<Call> calls = new ArrayList<>();
        ExpertServingRunner.RequestExecutor delegate =
                (expertId, nodeUuid, payload) -> calls.add(new Call(expertId, nodeUuid, payload));
        ElasticNodeCache cache = new ElasticNodeCache(1000, true);
        // "coder" was never registered with this node's cache.
        ElasticRequestExecutor executor = new ElasticRequestExecutor(Map.of("node-a", cache), delegate);

        assertThrows(IllegalStateException.class, () -> executor.process("coder", "node-a", "payload-1"));
        assertEquals(List.of(), calls);
    }

    @Test
    void constructorRejectsNulls() {
        ExpertServingRunner.RequestExecutor delegate = (expertId, nodeUuid, payload) -> { };
        assertThrows(IllegalArgumentException.class, () -> new ElasticRequestExecutor(null, delegate));
        assertThrows(IllegalArgumentException.class, () -> new ElasticRequestExecutor(Map.of(), null));
    }
}
