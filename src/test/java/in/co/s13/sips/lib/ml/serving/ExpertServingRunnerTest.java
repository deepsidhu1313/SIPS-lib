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

import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs real requests through real threads and checks the measured numbers
 * against a scenario worked out by hand, the same way {@code ShardPlanTest}
 * asserts exact sample counts rather than "roughly balanced".
 */
class ExpertServingRunnerTest {

    @Test
    void metricsMatchAHandCountedMixedScenario() {
        ExpertRegistry experts = new ExpertRegistry();
        experts.addReplica("coder", "node-a");
        experts.addReplica("coder", "node-b");
        experts.addReplica("embedder", "node-c");
        experts.addReplica("embedder", "node-d");
        SessionRegistry sessions = new SessionRegistry(Duration.ofMinutes(10), Clock.systemUTC());
        ExpertRouter router = new ExpertRouter(experts, sessions);
        ExpertServingRunner.RequestExecutor executor = (expertId, nodeUuid, payload) -> {
            try {
                Thread.sleep(2);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        };

        // Concurrency 1 so the eight requests below execute strictly in submission order --
        // that is what makes the hit count below a hand-countable fact rather than a race.
        // Each request begins and ends before the next starts, so queue depth is always back
        // at zero when the next routing decision is made, and leastLoaded's tie-break (lowest
        // node uuid) resolves every fresh pin deterministically:
        //
        // 1. s1 coder    -> miss, pins s1->node-a (tie broken alphabetically)
        // 2. s1 coder    -> hit  (node-a still hosts coder)
        // 3. s2 embedder -> miss, pins s2->node-c
        // 4. s2 embedder -> hit  (node-c still hosts embedder)
        // 5. s1 embedder -> miss (s1 was pinned to node-a, which does not host embedder);
        //                   re-pins s1->node-c
        // 6. s2 coder    -> miss (s2 was pinned to node-c, which does not host coder);
        //                   re-pins s2->node-a
        // 7. s1 coder    -> miss (s1 was pinned to node-c, which does not host coder);
        //                   re-pins s1->node-a
        // 8. s1 coder    -> hit  (node-a still hosts coder)
        //
        // 8 requests, 3 hits.
        ExpertServingRunner runner = new ExpertServingRunner(router, experts, executor, 1);
        try {
            runner.submit("s1", "coder", "p1");
            runner.submit("s1", "coder", "p2");
            runner.submit("s2", "embedder", "p3");
            runner.submit("s2", "embedder", "p4");
            runner.submit("s1", "embedder", "p5");
            runner.submit("s2", "coder", "p6");
            runner.submit("s1", "coder", "p7");
            runner.submit("s1", "coder", "p8");
        } finally {
            runner.close();
        }

        ExpertServingRunner.Metrics metrics = runner.metricsSnapshot();
        assertEquals(8, metrics.count());
        assertTrue(metrics.p50Millis() > 0, "p50 should be positive: " + metrics.p50Millis());
        assertTrue(metrics.p99Millis() >= metrics.p50Millis(),
                "p99 " + metrics.p99Millis() + " should be at least p50 " + metrics.p50Millis());
        assertEquals(3.0 / 8.0, metrics.sessionAffinityHitRate(), 1e-9);
    }

    @Test
    void aFreshRunnerReportsZeroWithoutDividingByZero() {
        ExpertRegistry experts = new ExpertRegistry();
        experts.addReplica("coder", "node-a");
        SessionRegistry sessions = new SessionRegistry(Duration.ofMinutes(10), Clock.systemUTC());
        ExpertRouter router = new ExpertRouter(experts, sessions);
        ExpertServingRunner.RequestExecutor executor = (expertId, nodeUuid, payload) -> { };

        try (ExpertServingRunner runner = new ExpertServingRunner(router, experts, executor, 1)) {
            ExpertServingRunner.Metrics metrics = runner.metricsSnapshot();
            assertEquals(0, metrics.count());
            assertEquals(0.0, metrics.sessionAffinityHitRate());
        }
    }
}
