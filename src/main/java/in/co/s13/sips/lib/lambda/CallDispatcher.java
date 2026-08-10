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
package in.co.s13.sips.lib.lambda;

/**
 * Where a {@link ClusterCall} actually runs.
 *
 * <p>The same split as {@link in.co.s13.sips.lib.job.StageRunner}: the call
 * describes what should happen, a dispatcher makes it happen. {@link
 * LocalCallDispatcher} runs it in this process, which is how a module gets
 * tested before it is ever sent anywhere; a cluster dispatcher chooses a node
 * and sends it.
 */
@FunctionalInterface
public interface CallDispatcher {

    /**
     * Runs a call and waits for its result.
     *
     * <p>Expected to return a failed {@link CallResult} rather than throw: a
     * function that did not work is an outcome, not an error in the caller.
     */
    CallResult dispatch(ClusterCall call);
}
