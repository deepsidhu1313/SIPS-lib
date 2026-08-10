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

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bringing a database up to the schema the running code expects.
 *
 * <p>The scenarios that matter are the ones nobody can rehearse: a node
 * restarted twice, an installation upgraded across several versions at once, a
 * migration that fails half way. All of them are cheap to write here and
 * expensive to discover in the field.
 */
class MigratorTest {

    private static Migration migration(String id, String sql) {
        return new Migration() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public void up(Connection database) throws SQLException {
                try (Statement statement = database.createStatement()) {
                    statement.execute(sql);
                }
            }
        };
    }

    private static final Migration CREATE_JOBS =
            migration("2026_01_01_000001_create_jobs", "CREATE TABLE jobs (id TEXT PRIMARY KEY)");
    private static final Migration ADD_WATTS =
            migration("2026_01_02_000001_add_watts", "ALTER TABLE jobs ADD COLUMN watts REAL");

    private static String databaseIn(Path dir) {
        return dir.resolve("warehouse.db").toString();
    }

    private static List<String> columnsOf(String database, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rows.next()) {
                columns.add(rows.getString("name"));
            }
        }
        return columns;
    }

    @Test
    void afreshDatabaseGetsEveryMigration(@TempDir Path dir) throws SQLException {
        String database = databaseIn(dir);

        List<String> applied = new Migrator().with(CREATE_JOBS, ADD_WATTS).migrate(database);

        assertEquals(List.of(CREATE_JOBS.id(), ADD_WATTS.id()), applied);
        assertEquals(List.of("id", "watts"), columnsOf(database, "jobs"));
    }

    @Test
    void startingTwiceChangesNothingTheSecondTime(@TempDir Path dir) {
        // A node restarts. Running the migrator again must be a no-op, not a
        // failed CREATE TABLE that someone learns to ignore in the log.
        String database = databaseIn(dir);
        new Migrator().with(CREATE_JOBS, ADD_WATTS).migrate(database);

        List<String> second = new Migrator().with(CREATE_JOBS, ADD_WATTS).migrate(database);

        assertTrue(second.isEmpty(), "nothing should have been applied twice: " + second);
    }

    @Test
    void anOldInstallationCatchesUpInOneStep(@TempDir Path dir) throws SQLException {
        // Somebody upgrades from a version that only ever had the first
        // migration. Skipping versions has to work, because people do it.
        String database = databaseIn(dir);
        new Migrator().with(CREATE_JOBS).migrate(database);

        List<String> applied = new Migrator().with(CREATE_JOBS, ADD_WATTS).migrate(database);

        assertEquals(List.of(ADD_WATTS.id()), applied);
        assertTrue(columnsOf(database, "jobs").contains("watts"));
    }

    @Test
    void migrationsRunInIdOrderNotDeclarationOrder(@TempDir Path dir) throws SQLException {
        // Two people add a migration on separate branches. The result must not
        // depend on how the merge happened to order them.
        String database = databaseIn(dir);

        new Migrator().with(ADD_WATTS, CREATE_JOBS).migrate(database);

        assertTrue(columnsOf(database, "jobs").contains("watts"));
    }

    @Test
    void aFailedMigrationLeavesTheDatabaseAtThePreviousOne(@TempDir Path dir)
            throws SQLException {
        // The case that decides whether an upgrade is recoverable. Half-applied
        // is the state nobody can reason about.
        String database = databaseIn(dir);
        Migration broken = migration("2026_01_03_000001_broken",
                "ALTER TABLE does_not_exist ADD COLUMN nope TEXT");

        assertThrows(Migrator.MigrationException.class,
                () -> new Migrator().with(CREATE_JOBS, ADD_WATTS, broken).migrate(database));

        assertEquals(List.of(CREATE_JOBS.id(), ADD_WATTS.id()), Migrator.appliedTo(database));
        assertEquals(List.of("id", "watts"), columnsOf(database, "jobs"),
                "the migrations before the failure still stand");
    }

    @Test
    void aFailedMigrationIsRetriedOnTheNextStart(@TempDir Path dir) throws SQLException {
        // It was never recorded, so fixing the cause and restarting is enough.
        String database = databaseIn(dir);
        Migration broken = migration("2026_01_03_000001_broken",
                "ALTER TABLE does_not_exist ADD COLUMN nope TEXT");
        assertThrows(Migrator.MigrationException.class,
                () -> new Migrator().with(CREATE_JOBS, broken).migrate(database));

        Migration fixed = migration("2026_01_03_000001_broken",
                "ALTER TABLE jobs ADD COLUMN nope TEXT");
        List<String> applied = new Migrator().with(CREATE_JOBS, fixed).migrate(database);

        assertEquals(List.of("2026_01_03_000001_broken"), applied);
        assertTrue(columnsOf(database, "jobs").contains("nope"));
    }

    @Test
    void aFailureNamesTheMigrationThatCausedIt(@TempDir Path dir) {
        Migration broken = migration("2026_01_03_000001_broken", "THIS IS NOT SQL");

        String message = assertThrows(Migrator.MigrationException.class,
                () -> new Migrator().with(broken).migrate(databaseIn(dir))).getMessage();

        assertTrue(message.contains("2026_01_03_000001_broken"), message);
    }

    @Test
    void twoMigrationsCannotShareAnId(@TempDir Path dir) {
        // One of them would silently never run, and which one would depend on
        // sort stability.
        Migration clash = migration(CREATE_JOBS.id(), "CREATE TABLE other (id TEXT)");

        assertThrows(IllegalStateException.class,
                () -> new Migrator().with(CREATE_JOBS, clash).migrate(databaseIn(dir)));
    }

    @Test
    void whatHasBeenAppliedCanBeAsked(@TempDir Path dir) {
        String database = databaseIn(dir);

        assertTrue(Migrator.appliedTo(database).isEmpty(), "nothing has run yet");
        new Migrator().with(CREATE_JOBS, ADD_WATTS).migrate(database);

        assertEquals(List.of(CREATE_JOBS.id(), ADD_WATTS.id()), Migrator.appliedTo(database));
    }

    @Test
    void anUpgradeThatChangedSomethingSaysSo(@TempDir Path dir) {
        List<String> log = new ArrayList<>();
        String database = databaseIn(dir);

        new Migrator().with(CREATE_JOBS).logTo(log::add).migrate(database);
        assertEquals(1, log.size());
        assertTrue(log.get(0).contains(CREATE_JOBS.id()));

        log.clear();
        new Migrator().with(CREATE_JOBS).logTo(log::add).migrate(database);
        assertTrue(log.isEmpty(), "a startup that changed nothing should be quiet");
    }

    @Test
    void nonsenseIsRefused(@TempDir Path dir) {
        assertThrows(IllegalArgumentException.class, () -> new Migrator().migrate(" "));
        assertThrows(IllegalArgumentException.class, () -> new Migrator().migrate(null));
        assertThrows(IllegalArgumentException.class,
                () -> new Migrator().with(migration("  ", "SELECT 1")));
        assertThrows(IllegalArgumentException.class, () -> new Migrator().with((Migration) null));
    }

    @Test
    void aDatabaseWithNoMigrationsAtAllIsStillUsable(@TempDir Path dir) {
        // The ledger is created regardless, so the next release has somewhere
        // to record its first migration.
        String database = databaseIn(dir);

        assertTrue(new Migrator().migrate(database).isEmpty());
        assertFalse(new java.io.File(database).length() == 0, "the file should exist");
    }
}
