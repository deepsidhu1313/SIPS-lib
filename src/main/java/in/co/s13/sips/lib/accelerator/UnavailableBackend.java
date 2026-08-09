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
package in.co.s13.sips.lib.accelerator;

import java.util.List;

/**
 * Base for backends whose runtime is not present on this host.
 *
 * <p>These are deliberately not silent stubs. Each probes for its runtime and,
 * when it is missing, reports a reason an operator can act on. That distinguishes
 * "this hardware does not exist here" from "the binding was never wired up",
 * which otherwise look identical from the outside.
 */
abstract class UnavailableBackend implements AcceleratorBackend {

    /** Empty when the runtime is usable; otherwise the reason it is not. */
    protected abstract String probe();

    private String cachedReason;

    private String reason() {
        if (cachedReason == null) {
            try {
                cachedReason = probe();
            } catch (RuntimeException ex) {
                cachedReason = "probe failed: " + ex;
            }
        }
        return cachedReason;
    }

    @Override
    public boolean isAvailable() {
        return reason().isEmpty();
    }

    @Override
    public String unavailableReason() {
        return reason();
    }

    @Override
    public List<Device> devices() {
        // Subclasses that become available override this with real enumeration.
        return List.of();
    }

    static boolean osContains(String fragment) {
        return System.getProperty("os.name", "").toLowerCase().contains(fragment);
    }

    static boolean anyFileExists(String... paths) {
        for (String path : paths) {
            if (new java.io.File(path).exists()) {
                return true;
            }
        }
        return false;
    }
}
