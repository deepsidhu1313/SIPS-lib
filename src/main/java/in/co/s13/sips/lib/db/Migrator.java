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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Brings a database up to the schema this version of SIPS expects.
 *
 * <p>Run it at startup, every time. It compares the migrations the code carries
 * against the ones this database has already had, applies the difference in
 * order, and records what it did. Running it against an up-to-date database
 * costs one query and changes nothing.
 *
 * <p>That property is the whole point: there is no separate "upgrade" step for
 * an operator to remember, forget, or run twice. A node started after an
 * upgrade is a node with the right schema.
 *
 * <h2>What it guarantees</h2>
 *
 * <ul>
 *   <li><b>Each migration runs once.</b> Recorded by id, so a restart, a
 *       reinstall, or an upgrade skipping several versions all converge.</li>
 *   <li><b>A migration is all or nothing.</b> Each runs in its own transaction;
 *       a failure rolls that one back and stops, leaving the database at the
 *       last migration that did succeed rather than half-way through one.</li>
 *   <li><b>Order is by id, not by declaration.</b> Two people adding migrations
 *       on separate branches cannot produce a different result depending on how
 *       the merge happened to order them.</li>
 * </ul>
 *
 * <h2>What it does not do</h2>
 *
 * <p>There is no rollback. A {@code down()} that undoes a schema change can
 * rarely undo the data change that went with it, and offering one invites
 * treating it as a safety net it is not. Restore a backup.
 */
public final class Migrator {

    /** Where applied migrations are recorded. */
    public static final String LEDGER = "sips_migrations";

    private final List<Migration> migrations = new ArrayList<>();
    private Consumer<String> log = line -> {
    };

    /** Adds migrations. Order here does not matter; they are sorted by id. */
    public Migrator with(Migration... toAdd) {
        for (Migration migration : toAdd) {
            if (migration == null || migration.id() == null || migration.id().isBlank()) {
                throw new IllegalArgumentException("A migration needs an id");
            }
            migrations.add(migration);
        }
        return this;
    }

    /** Where progress is reported. An upgrade that changed the schema should say so. */
    public Migrator logTo(Consumer<String> log) {
        this.log = log == null ? line -> {
        } : log;
        return this;
    }

    /**
     * Applies everything this database has not had yet.
     *
     * @param databaseFile path to the SQLite file; created if absent
     * @return the ids applied by this call, in the order they ran
     * @throws MigrationException if one fails, naming it — the database is left
     *         at the last migration that succeeded
     */
    public List<String> migrate(String databaseFile) {
        if (databaseFile == null || databaseFile.isBlank()) {
            throw new IllegalArgumentException("A database file is required");
        }
        List<Migration> ordered = new ArrayList<>(migrations);
        ordered.sort(Comparator.comparing(Migration::id));
        rejectDuplicates(ordered);

        List<String> applied = new ArrayList<>();
        try (Connection database = DriverManager.getConnection("jdbc:sqlite:" + databaseFile)) {
            createLedger(database);
            Set<String> already = alreadyApplied(database);

            for (Migration migration : ordered) {
                if (already.contains(migration.id())) {
                    continue;
                }
                apply(database, migration);
                applied.add(migration.id());
                log.accept("Applied " + migration.id() + ": " + migration.description());
            }
        } catch (SQLException ex) {
            throw new MigrationException("Could not open " + databaseFile, ex);
        }
        return applied;
    }

    private void rejectDuplicates(List<Migration> ordered) {
        Set<String> ids = new LinkedHashSet<>();
        for (Migration migration : ordered) {
            if (!ids.add(migration.id())) {
                // Two migrations sharing an id means one of them silently never
                // runs, and which one depends on sort stability.
                throw new IllegalStateException("Two migrations share the id " + migration.id());
            }
        }
    }

    private void createLedger(Connection database) throws SQLException {
        try (Statement statement = database.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + LEDGER + " ("
                    + "id TEXT PRIMARY KEY NOT NULL, "
                    + "applied_on INTEGER NOT NULL)");
        }
    }

    private Set<String> alreadyApplied(Connection database) throws SQLException {
        Set<String> applied = new LinkedHashSet<>();
        try (Statement statement = database.createStatement();
                ResultSet rows = statement.executeQuery("SELECT id FROM " + LEDGER)) {
            while (rows.next()) {
                applied.add(rows.getString("id"));
            }
        }
        return applied;
    }

    private void apply(Connection database, Migration migration) {
        boolean autoCommit = true;
        try {
            autoCommit = database.getAutoCommit();
            database.setAutoCommit(false);

            migration.up(database);
            try (PreparedStatement record = database.prepareStatement(
                    "INSERT INTO " + LEDGER + " (id, applied_on) VALUES (?, ?)")) {
                record.setString(1, migration.id());
                record.setLong(2, System.currentTimeMillis());
                record.executeUpdate();
            }
            database.commit();
        } catch (SQLException ex) {
            rollback(database);
            throw new MigrationException("Migration " + migration.id() + " failed: "
                    + ex.getMessage() + ". The database is unchanged by it and still at the "
                    + "migration before.", ex);
        } finally {
            restoreAutoCommit(database, autoCommit);
        }
    }

    private void rollback(Connection database) {
        try {
            database.rollback();
        } catch (SQLException ignored) {
            // Already failing; the original cause is the one worth reporting.
        }
    }

    private void restoreAutoCommit(Connection database, boolean autoCommit) {
        try {
            database.setAutoCommit(autoCommit);
        } catch (SQLException ignored) {
            // As above.
        }
    }

    /** Which migrations a database has had, oldest first. */
    public static List<String> appliedTo(String databaseFile) {
        List<String> applied = new ArrayList<>();
        try (Connection database = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
                Statement statement = database.createStatement()) {
            try (ResultSet rows = statement.executeQuery(
                    "SELECT id FROM " + LEDGER + " ORDER BY id")) {
                while (rows.next()) {
                    applied.add(rows.getString("id"));
                }
            } catch (SQLException noLedger) {
                // A database nothing has migrated yet has had none.
                return List.of();
            }
        } catch (SQLException ex) {
            throw new MigrationException("Could not read " + databaseFile, ex);
        }
        return applied;
    }

    /** A migration could not be applied. */
    public static class MigrationException extends RuntimeException {

        public MigrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
