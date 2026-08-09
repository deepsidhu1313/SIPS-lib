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
package in.co.s13.sips.scheduler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writing a scheduling policy should take one method.
 *
 * <p>These double as the worked example. If implementing a policy needs more
 * than what this file shows, the abstraction has failed its purpose — which
 * was to remove the eight-method barrier between having an idea and finding
 * out whether it works.
 */
class LoopPolicyTest {

    /** A complete, working policy. This is the entire contract. */
    private static final class HalfRemainder implements LoopPolicy {

        @Override
        public String name() {
            return "HalfRemainder";
        }

        @Override
        public long nextBatchSize(long remaining, int nodes, int round) {
            return Math.max(1, remaining / (nodes * 2L));
        }
    }

    @Test
    void aPolicyIsOneMethodAndAName() {
        LoopPolicy policy = new HalfRemainder();

        assertEquals("HalfRemainder", policy.name());
        assertEquals(50, policy.nextBatchSize(400, 4, 0));
    }

    @Test
    void defaultsCoverEverythingElse() {
        LoopPolicy policy = new HalfRemainder();

        assertFalse(policy.isStatic(), "on-demand by default");
        assertTrue(policy.description().contains("HalfRemainder"));
    }

    @Test
    void batchesShrinkAsWorkRunsOut() {
        // The property that lets self-scheduling balance a ragged tail.
        LoopPolicy policy = new HalfRemainder();
        long early = policy.nextBatchSize(1000, 8, 0);
        long late = policy.nextBatchSize(20, 8, 20);

        assertTrue(late < early, "batches should shrink toward the end");
        assertTrue(late >= 1, "a policy must never stall the loop");
    }

    @Test
    void roundNumberIsAvailableForAdaptivePolicies() {
        // A policy that starts cautious and grows once it has seen throughput.
        LoopPolicy ramp = new LoopPolicy() {
            @Override
            public String name() {
                return "Ramp";
            }

            @Override
            public long nextBatchSize(long remaining, int nodes, int round) {
                return Math.max(1, Math.min(remaining, 1L << Math.min(round, 10)));
            }
        };

        assertEquals(1, ramp.nextBatchSize(1000, 4, 0));
        assertEquals(16, ramp.nextBatchSize(1000, 4, 4));
    }

    @Test
    void aStaticPolicyDeclaresItself() {
        LoopPolicy upFront = new LoopPolicy() {
            @Override
            public String name() {
                return "AllAtOnce";
            }

            @Override
            public long nextBatchSize(long remaining, int nodes, int round) {
                return Math.max(1, remaining / nodes);
            }

            @Override
            public boolean isStatic() {
                return true;
            }
        };

        assertTrue(upFront.isStatic(),
                "the evaluator needs this to model that it cannot rebalance");
    }

    @Test
    void theClassicPoliciesFitTheInterface() {
        // Proof the abstraction is the right shape: GSS, Factoring and QSS are
        // each one expression.
        LoopPolicy gss = policy("GSS", (remaining, nodes, round) ->
                (long) Math.ceil((double) remaining / nodes));
        LoopPolicy factoring = policy("Factoring", (remaining, nodes, round) ->
                (long) Math.ceil(remaining / (2.0 * nodes)));
        LoopPolicy qss = policy("QSS", (remaining, nodes, round) ->
                (long) Math.ceil(Math.sqrt(remaining) / nodes));

        assertEquals(25, gss.nextBatchSize(100, 4, 0));
        assertEquals(13, factoring.nextBatchSize(100, 4, 0));
        assertEquals(3, qss.nextBatchSize(100, 4, 0));
    }

    /** Lets a test express a policy as a lambda. */
    private interface BatchRule {
        long apply(long remaining, int nodes, int round);
    }

    private static LoopPolicy policy(String name, BatchRule rule) {
        return new LoopPolicy() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public long nextBatchSize(long remaining, int nodes, int round) {
                return Math.max(1, rule.apply(remaining, nodes, round));
            }
        };
    }
}
