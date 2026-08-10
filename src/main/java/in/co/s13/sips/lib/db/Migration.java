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
import java.sql.SQLException;

/**
 * One change to a database's shape.
 *
 * <p>Every long-lived table in SIPS was created by a bare {@code CREATE TABLE}
 * at the point of first use, with no record of whether it had been created
 * before and no way to change it afterwards. That is fine until the first time
 * a column is added, at which point every existing installation breaks: the
 * create fails because the table is there, and the inserts fail because the
 * column is not.
 *
 * <p>A migration is that change made explicit and recorded. Once it has run
 * against a database it never runs again, so starting a node twice, or
 * upgrading from any older version, converges on the same schema.
 *
 * <h2>Writing one</h2>
 *
 * <pre>{@code
 * new Migration() {
 *     public String id() {
 *         return "2026_08_10_000002_add_watts_to_distwh";
 *     }
 *     public void up(Connection db) throws SQLException {
 *         try (Statement s = db.createStatement()) {
 *             s.execute("ALTER TABLE DISTWH ADD COLUMN WATTS REAL");
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p>The id orders them and identifies them forever. Date-prefixing keeps new
 * ones sorting last without anyone maintaining a counter.
 *
 * <h2>Rules</h2>
 *
 * <ul>
 *   <li><b>Never change a migration that has shipped.</b> It has already run
 *       somewhere, so editing it changes that database's history and not its
 *       contents. Add another one instead.</li>
 *   <li><b>Do not read application state.</b> A migration runs at startup, in a
 *       state its author cannot see. It should depend only on the schema it
 *       declares as its predecessors.</li>
 * </ul>
 */
public interface Migration {

    /**
     * What this migration is called, and where it sorts.
     *
     * <p>Recorded in the database, so it must never change once shipped.
     * Convention is {@code yyyy_MM_dd_HHmmss_what_it_does}.
     */
    String id();

    /** Applies the change. Anything thrown rolls the whole migration back. */
    void up(Connection database) throws SQLException;

    /** A sentence for the log, so an operator can see what a startup did. */
    default String description() {
        return id().replace('_', ' ');
    }
}
