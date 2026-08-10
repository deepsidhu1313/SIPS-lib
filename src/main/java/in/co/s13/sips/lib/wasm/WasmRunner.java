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
package in.co.s13.sips.lib.wasm;

import com.dylibso.chicory.runtime.ExportFunction;
import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.WasmModule;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs a {@link WasmTask} on this node.
 *
 * <p>Uses Chicory, a WebAssembly runtime written in pure Java. That choice is
 * deliberate: a native runtime would reintroduce exactly the per-node toolchain
 * requirement that makes the Ant path painful, whereas Chicory arrives as an
 * ordinary dependency and works wherever the JVM does.
 *
 * <p>Parsed modules are cached by path. Parsing is the only meaningful cost —
 * once a module is loaded, instantiating and invoking it is microseconds — so a
 * job whose chunks share a module pays that once rather than per chunk.
 *
 * <p>What is <em>not</em> cached is the instance. Host functions are bound to
 * one instance when it is built, so reusing an instance across chunks would let
 * a later chunk write into an earlier chunk's output. Each run gets a fresh
 * instance, and with it a fresh linear memory.
 */
public final class WasmRunner implements AutoCloseable {

    private final ConcurrentHashMap<String, WasmModule> modules = new ConcurrentHashMap<>();
    private final ExecutorService watchdog = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "wasm-watchdog");
        t.setDaemon(true);
        return t;
    });

    /**
     * Runs a chunk with no host interface, returning only its status.
     *
     * <p>Enough for a module whose whole job is arithmetic over an index range.
     * Anything that needs to read inputs or produce results wants the
     * {@link WasmHost} overload.
     */
    public long run(WasmTask task, Duration timeout) {
        return run(task, WasmHost.none(), timeout);
    }

    /**
     * Runs a chunk against a host interface.
     *
     * <p>The host is single-use: it collects this run's output, readable
     * afterwards through {@link WasmHost#output()}.
     *
     * @param timeout how long the module may run before being abandoned
     * @return the module's status; {@link WasmTask#SUCCESS} on success
     * @throws WasmExecutionException if the module is unreadable, does not
     *         export the entry point, traps, or exceeds the timeout
     */
    public long run(WasmTask task, WasmHost host, Duration timeout) {
        Instance instance;
        try {
            instance = Instance.builder(load(task))
                    .withImportValues(ImportValues.builder()
                            .addFunction(host.functions().toArray(
                                    new com.dylibso.chicory.runtime.HostFunction[0]))
                            .build())
                    .build();
        } catch (WasmExecutionException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new WasmExecutionException("Could not instantiate " + task.module()
                    + ". A module may import only the sips host functions; see WasmHost.", ex);
        }

        ExportFunction entry;
        try {
            entry = instance.export(task.entryPoint());
        } catch (RuntimeException ex) {
            throw new WasmExecutionException("Module " + task.module()
                    + " does not export '" + task.entryPoint() + "'. A SIPS module must export "
                    + "(param i64 i64) (result i64).", ex);
        }

        // Run on a separate thread so a module that loops forever cannot hang
        // the node. WebAssembly has no interrupt, so the thread is abandoned
        // rather than stopped -- but the node stays responsive.
        Future<long[]> result = watchdog.submit(
                () -> entry.apply(task.firstIndex(), task.lastIndexExclusive()));
        try {
            long[] returned = result.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return returned.length > 0 ? returned[0] : WasmTask.SUCCESS;
        } catch (TimeoutException ex) {
            result.cancel(true);
            throw new WasmExecutionException("Module exceeded " + timeout.toMillis()
                    + " ms and was abandoned: " + task, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new WasmExecutionException("Interrupted while running " + task, ex);
        } catch (java.util.concurrent.ExecutionException ex) {
            // A trap -- division by zero, out-of-bounds memory, unreachable --
            // or a host function rejecting what the module asked for.
            if (ex.getCause() instanceof WasmExecutionException) {
                throw (WasmExecutionException) ex.getCause();
            }
            throw new WasmExecutionException("Module trapped running " + task + ": "
                    + ex.getCause(), ex.getCause());
        }
    }

    private WasmModule load(WasmTask task) {
        String key = task.module().toAbsolutePath().toString();
        return modules.computeIfAbsent(key, path -> {
            try {
                if (!Files.isReadable(task.module())) {
                    throw new WasmExecutionException("Module not readable: " + task.module());
                }
                return Parser.parse(task.module().toFile());
            } catch (WasmExecutionException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw new WasmExecutionException("Could not load module " + task.module(), ex);
            }
        });
    }

    /** How many distinct modules are cached. */
    public int cachedModuleCount() {
        return modules.size();
    }

    @Override
    public void close() {
        modules.clear();
        watchdog.shutdownNow();
    }

    /** A module could not be loaded or did not complete. */
    public static class WasmExecutionException extends RuntimeException {

        public WasmExecutionException(String message) {
            super(message);
        }

        public WasmExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
