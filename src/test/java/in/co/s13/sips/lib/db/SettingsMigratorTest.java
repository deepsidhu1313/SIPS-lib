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
import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Changing a settings file in ways that are not pure additions.
 *
 * <p>Adding a key has always worked by itself. Renaming one silently drops the
 * operator's value; changing a unit silently reads it as the new one, which is
 * worse, because a number a thousand times off does not look wrong.
 */
class SettingsMigratorTest {

    private static SettingsMigrator twoSteps() {
        return new SettingsMigrator()
                .step(1, "rename the ping delay",
                        SettingsMigrator.rename("PING_DELAY", "PING_DELAY_MS"))
                .step(2, "the ping delay is now milliseconds", settings -> {
                    if (settings.has("PING_DELAY_MS")) {
                        settings.put("PING_DELAY_MS", settings.getLong("PING_DELAY_MS") * 1000);
                    }
                    return settings;
                });
    }

    @Test
    void aFileWrittenBeforeVersioningTakesEveryStep() {
        // What every existing installation looks like: no version key at all.
        JSONObject old = new JSONObject().put("PING_DELAY", 30);

        JSONObject migrated = twoSteps().migrate(old);

        assertFalse(migrated.has("PING_DELAY"), "the old key should be gone");
        assertEquals(30000, migrated.getLong("PING_DELAY_MS"));
        assertEquals(2, migrated.getInt(SettingsMigrator.VERSION_KEY));
    }

    @Test
    void aFilePartWayThroughTakesOnlyWhatIsLeft() {
        // Upgrading from a release that had the rename but not the unit change.
        JSONObject halfWay = new JSONObject()
                .put("PING_DELAY_MS", 30)
                .put(SettingsMigrator.VERSION_KEY, 1);

        JSONObject migrated = twoSteps().migrate(halfWay);

        assertEquals(30000, migrated.getLong("PING_DELAY_MS"));
        assertEquals(2, migrated.getInt(SettingsMigrator.VERSION_KEY));
    }

    @Test
    void anUpToDateFileIsLeftAlone() {
        JSONObject current = new JSONObject()
                .put("PING_DELAY_MS", 30000)
                .put(SettingsMigrator.VERSION_KEY, 2);

        JSONObject migrated = twoSteps().migrate(current);

        assertEquals(30000, migrated.getLong("PING_DELAY_MS"),
                "migrating twice must not multiply it again");
    }

    @Test
    void migratingTwiceIsTheSameAsMigratingOnce() {
        // The property that makes it safe to run at every startup.
        SettingsMigrator migrator = twoSteps();
        JSONObject once = migrator.migrate(new JSONObject().put("PING_DELAY", 30));

        JSONObject twice = migrator.migrate(once);

        assertEquals(once.toString(), twice.toString());
    }

    @Test
    void theInputIsNotModified() {
        // A caller that decides not to write the result has lost nothing.
        JSONObject original = new JSONObject().put("PING_DELAY", 30);

        twoSteps().migrate(original);

        assertTrue(original.has("PING_DELAY"));
        assertFalse(original.has(SettingsMigrator.VERSION_KEY));
    }

    @Test
    void aRenameKeepsTheOperatorsValue() {
        JSONObject settings = new JSONObject().put("OLD", 7);

        JSONObject migrated = new SettingsMigrator()
                .step(1, "rename", SettingsMigrator.rename("OLD", "NEW")).migrate(settings);

        assertEquals(7, migrated.getInt("NEW"));
        assertFalse(migrated.has("OLD"));
    }

    @Test
    void aRenameOntoAnExistingKeyKeepsTheNewerValue() {
        // Both present means someone already set the new one by hand. Theirs
        // wins; the stale one is simply dropped.
        JSONObject settings = new JSONObject().put("OLD", 7).put("NEW", 9);

        JSONObject migrated = new SettingsMigrator()
                .step(1, "rename", SettingsMigrator.rename("OLD", "NEW")).migrate(settings);

        assertEquals(9, migrated.getInt("NEW"));
        assertFalse(migrated.has("OLD"));
    }

    @Test
    void aRemovedKeyIsDropped() {
        JSONObject settings = new JSONObject().put("GONE", true);

        JSONObject migrated = new SettingsMigrator()
                .step(1, "drop", SettingsMigrator.remove("GONE")).migrate(settings);

        assertFalse(migrated.has("GONE"));
    }

    @Test
    void aRenameOfSomethingAbsentIsHarmless() {
        JSONObject migrated = new SettingsMigrator()
                .step(1, "rename", SettingsMigrator.rename("NEVER_SET", "NEW"))
                .migrate(new JSONObject().put("OTHER", 1));

        assertEquals(1, migrated.getInt("OTHER"));
        assertFalse(migrated.has("NEW"));
    }

    @Test
    void aFileWithNothingToDoIsStillStamped() {
        // So the next release can tell it from one written before versioning.
        JSONObject migrated = new SettingsMigrator().migrate(new JSONObject().put("A", 1));

        assertEquals(SettingsMigrator.UNVERSIONED,
                migrated.getInt(SettingsMigrator.VERSION_KEY));
    }

    @Test
    void whetherAFileNeedsMigratingCanBeAsked() {
        SettingsMigrator migrator = twoSteps();

        assertTrue(migrator.needsMigrating(new JSONObject().put("PING_DELAY", 30)));
        assertFalse(migrator.needsMigrating(
                new JSONObject().put(SettingsMigrator.VERSION_KEY, 2)));
        assertTrue(migrator.needsMigrating(null));
    }

    @Test
    void anUpgradeThatChangedSomethingSaysSo() {
        List<String> log = new ArrayList<>();

        twoSteps().logTo(log::add).migrate(new JSONObject().put("PING_DELAY", 30));

        assertEquals(2, log.size());
        assertTrue(log.get(0).contains("rename"), log.get(0));

        log.clear();
        twoSteps().logTo(log::add).migrate(
                new JSONObject().put(SettingsMigrator.VERSION_KEY, 2));
        assertTrue(log.isEmpty(), "a startup that changed nothing should be quiet");
    }

    @Test
    void nonsenseIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new SettingsMigrator().step(0, "x", s -> s));
        assertThrows(IllegalArgumentException.class,
                () -> new SettingsMigrator().step(1, "x", null));
        assertThrows(IllegalArgumentException.class,
                () -> new SettingsMigrator().migrate(null));
        assertThrows(IllegalStateException.class, () -> new SettingsMigrator()
                .step(1, "a", s -> s).step(1, "b", s -> s));
        assertThrows(IllegalStateException.class, () -> new SettingsMigrator()
                .step(1, "returns nothing", s -> null).migrate(new JSONObject()));
    }
}
