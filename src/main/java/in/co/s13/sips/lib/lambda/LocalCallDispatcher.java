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

import in.co.s13.sips.lib.wasm.WasmHost;
import in.co.s13.sips.lib.wasm.WasmRunner;
import in.co.s13.sips.lib.wasm.WasmTask;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Runs a call in this process.
 *
 * <p>Two jobs, both real. It is how a module gets checked before it is sent
 * anywhere — the alternative being to find out it does not export {@code run}
 * after it has been distributed to forty nodes. And on a single machine it is
 * simply the answer: there is no cluster to place anything on.
 *
 * <p>Modules are cached across calls, so a function called repeatedly pays for
 * parsing once. Close it when done.
 */
public final class LocalCallDispatcher implements CallDispatcher, AutoCloseable {

    /** The node name a local result reports, so it is never mistaken for a remote one. */
    public static final String LOCAL_NODE = "local";

    private final WasmRunner runner = new WasmRunner();
    private final Consumer<String> logger;
    private final List<String> log = new ArrayList<>();

    public LocalCallDispatcher() {
        this(null);
    }

    /** @param logger where the module's log lines go; collected if null */
    public LocalCallDispatcher(Consumer<String> logger) {
        this.logger = logger;
    }

    @Override
    public CallResult dispatch(ClusterCall call) {
        long started = System.nanoTime();
        WasmHost host = WasmHost.builder()
                .input(call.input())
                .log(logger == null ? log::add : logger)
                .build();
        try {
            WasmTask task = new WasmTask(LOCAL_NODE, 0, call.module(), call.entryPoint(),
                    call.firstIndex(), call.lastIndexExclusive());
            long status = runner.run(task, host, call.timeout());
            Duration took = Duration.ofNanos(System.nanoTime() - started);

            return status == WasmTask.SUCCESS
                    ? CallResult.success(host.output(), LOCAL_NODE, took)
                    : CallResult.status(status, LOCAL_NODE, took);
        } catch (RuntimeException ex) {
            // A module that traps, times out or will not load is a failed call,
            // not a broken caller.
            return CallResult.failed(String.valueOf(ex.getMessage()), LOCAL_NODE,
                    Duration.ofNanos(System.nanoTime() - started));
        }
    }

    /** Lines the modules logged, when no logger was supplied. */
    public List<String> log() {
        return List.copyOf(log);
    }

    @Override
    public void close() {
        runner.close();
    }
}
