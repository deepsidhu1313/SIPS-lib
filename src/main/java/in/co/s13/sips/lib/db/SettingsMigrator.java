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
package in.co.s13.sips.lib.db;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.json.JSONObject;

/**
 * Brings a settings file up to the shape this version of SIPS expects.
 *
 * <p>Adding a setting has always worked without help: every value is read as
 * {@code get(name, currentDefault)}, so a key an old file lacks falls back to
 * the code. That is why nothing like this existed.
 *
 * <p>What has never worked is any change that is not a pure addition. Rename a
 * key and the old value is silently dropped in favour of a default. Change a
 * unit from seconds to milliseconds and the old number is read as the new unit,
 * which is worse — it is not obviously wrong, just a thousand times off.
 *
 * <p>A version number in the file is what makes those possible: it says which
 * transformations a file has already had, so each runs exactly once.
 */
public final class SettingsMigrator {

    /** The key holding a settings file's version. */
    public static final String VERSION_KEY = "SETTINGS_VERSION";

    /** What a file with no version is treated as: the shape before any of this. */
    public static final int UNVERSIONED = 0;

    private final List<Step> steps = new ArrayList<>();
    private Consumer<String> log = line -> {
    };

    /**
     * Adds a transformation taking a file from {@code version - 1} to
     * {@code version}.
     *
     * @param version the version this step produces; must be positive and
     *        unique
     * @param what a sentence for the log, so an operator can see what changed
     */
    public SettingsMigrator step(int version, String what, UnaryOperator<JSONObject> change) {
        if (version <= UNVERSIONED) {
            throw new IllegalArgumentException("A settings version must be positive: " + version);
        }
        if (change == null) {
            throw new IllegalArgumentException("A step needs a change");
        }
        for (Step existing : steps) {
            if (existing.version == version) {
                throw new IllegalStateException("Two steps produce settings version " + version);
            }
        }
        steps.add(new Step(version, what == null ? "" : what, change));
        return this;
    }

    public SettingsMigrator logTo(Consumer<String> log) {
        this.log = log == null ? line -> {
        } : log;
        return this;
    }

    /** The version a file reaching the end of every step would carry. */
    public int targetVersion() {
        return steps.stream().mapToInt(step -> step.version).max().orElse(UNVERSIONED);
    }

    /**
     * Applies whatever this file has not had yet.
     *
     * <p>Returns a new object; the input is not modified, so a caller that
     * decides not to write the result has lost nothing.
     *
     * @return the settings at {@link #targetVersion()}
     */
    public JSONObject migrate(JSONObject settings) {
        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }
        JSONObject migrated = new JSONObject(settings.toString());
        int from = migrated.optInt(VERSION_KEY, UNVERSIONED);

        // Sorted, so a file two versions behind takes both steps in order.
        Map<Integer, Step> byVersion = new LinkedHashMap<>();
        steps.stream().sorted((a, b) -> Integer.compare(a.version, b.version))
                .forEach(step -> byVersion.put(step.version, step));

        for (Step step : byVersion.values()) {
            if (step.version <= from) {
                continue;
            }
            JSONObject result = step.change.apply(migrated);
            if (result == null) {
                throw new IllegalStateException("Settings step " + step.version
                        + " returned nothing");
            }
            migrated = result;
            migrated.put(VERSION_KEY, step.version);
            log.accept("Settings upgraded to version " + step.version
                    + (step.what.isEmpty() ? "" : ": " + step.what));
        }

        if (!migrated.has(VERSION_KEY)) {
            // A file that needed no step still gets stamped, so the next
            // release can tell it apart from one written before versioning.
            migrated.put(VERSION_KEY, targetVersion());
        }
        return migrated;
    }

    /** Whether this file would be changed by migrating it. */
    public boolean needsMigrating(JSONObject settings) {
        return settings == null || settings.optInt(VERSION_KEY, UNVERSIONED) < targetVersion()
                || !settings.has(VERSION_KEY);
    }

    private static final class Step {

        private final int version;
        private final String what;
        private final UnaryOperator<JSONObject> change;

        private Step(int version, String what, UnaryOperator<JSONObject> change) {
            this.version = version;
            this.what = what;
            this.change = change;
        }
    }

    /**
     * A step that renames a key, keeping its value.
     *
     * <p>The commonest non-additive change, and the one that silently loses a
     * setting without this.
     */
    public static UnaryOperator<JSONObject> rename(String from, String to) {
        return settings -> {
            if (settings.has(from)) {
                if (!settings.has(to)) {
                    settings.put(to, settings.get(from));
                }
                settings.remove(from);
            }
            return settings;
        };
    }

    /**
     * A step that drops a key that no longer means anything.
     *
     * <p>Leaving it behind is not harmful, but it invites someone to edit a
     * value that nothing reads and wonder why nothing happened.
     */
    public static UnaryOperator<JSONObject> remove(String key) {
        return settings -> {
            settings.remove(key);
            return settings;
        };
    }
}
