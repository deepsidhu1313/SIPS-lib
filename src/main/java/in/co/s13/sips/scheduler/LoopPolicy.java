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

/**
 * A loop-scheduling policy, in one method.
 *
 * <p>The full {@link Scheduler} interface has eight methods, most of which are
 * bookkeeping — backup nodes, chunk counts, error and output lists. A
 * researcher wanting to try a new policy had to implement all of them before
 * finding out whether their idea worked, which is a real barrier to trying one.
 *
 * <p>Everything that distinguishes the classic policies from one another is a
 * single decision: <b>how many iterations to hand out next</b>. Guided
 * self-scheduling takes 1/P of what remains; factoring takes half the remainder
 * split among the nodes; trapezoid decreases linearly. That is the whole
 * difference.
 *
 * <p>So a new policy is this:
 *
 * <pre>{@code
 * public class MyPolicy implements LoopPolicy {
 *     public String name() { return "MyPolicy"; }
 *
 *     public long nextBatchSize(long remaining, int nodes, int round) {
 *         return Math.max(1, remaining / (nodes * 2L));
 *     }
 * }
 * }</pre>
 *
 * <p>Evaluate it against the classics without a cluster using
 * {@code in.co.s13.sips.schedulers.eval.Evaluator}, then adapt it into a full
 * {@link Scheduler} only once it looks promising.
 */
public interface LoopPolicy {

    /** Name used in a manifest and in evaluation output. */
    String name();

    /**
     * How many iterations to hand to the next available node.
     *
     * <p>Called repeatedly until nothing remains. Returning a large value
     * front-loads the work and risks a long tail; returning small values
     * balances better at the cost of more scheduling round trips.
     *
     * @param remaining iterations not yet assigned; always positive
     * @param nodes     nodes available; always at least one
     * @param round     how many batches have been handed out so far, from 0.
     *                  Useful for a policy that changes behaviour over time.
     * @return the batch size. Values below 1 are treated as 1, and values above
     *         {@code remaining} are truncated, so an implementation cannot
     *         stall or overrun the loop.
     */
    long nextBatchSize(long remaining, int nodes, int round);

    /**
     * Whether the policy assigns everything up front rather than on demand.
     *
     * <p>Static assignment cannot rebalance, which is exactly why it loses on
     * irregular work — one expensive block delays the whole job. Default false.
     */
    default boolean isStatic() {
        return false;
    }

    /** A one-line description for documentation and tooling. */
    default String description() {
        return name() + " loop-scheduling policy";
    }
}
