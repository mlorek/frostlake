/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package dev.frostlake.transaction;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.LiveSnowflake;
import dev.frostlake.http.SessionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A transaction that has written a table another session drops or replaces while it is open commits its changes to
 * the table it wrote (live-verified): an UNDROP brings the dropped table back with the committed UPDATE or INSERT,
 * and a table that replaced it by CREATE OR REPLACE stays as its CREATE left it, empty, while the table it replaced
 * comes back by UNDROP, once its name is free, with the committed change.
 *
 * <p>Live, the two sessions are two connections; embedded, two sessions of one concurrent engine.
 */
public class DroppedTableTransactionTest extends BaseDatabaseTest {

    private ConcurrentDatabaseEngine concurrent;
    private SessionContext sessionA;
    private SessionContext sessionB;
    private Connection connA;
    private Connection connB;

    @BeforeEach
    public void openTwoSessions() {
        if (isLiveSnowflake()) {
            connA = LiveSnowflake.open();
            connB = LiveSnowflake.open();
        } else {
            concurrent = new ConcurrentDatabaseEngine();
            sessionA = concurrent.createSession();
            sessionB = concurrent.createSession();
            say("A", "CREATE DATABASE IF NOT EXISTS test_db");
            say("A", "CREATE SCHEMA IF NOT EXISTS test_db.test_schema");
        }
        say("A", "USE SCHEMA test_db.test_schema");
        say("B", "USE SCHEMA test_db.test_schema");
    }

    @AfterEach
    public void closeTwoSessions() throws SQLException {
        say("A", "ROLLBACK");
        say("B", "ROLLBACK");
        for (final String name : new String[] {"dt3", "dt7", "dt6", "dt5", "dt8", "dt8_new", "dt9", "dt9_new"}) {
            say("A", "DROP TABLE IF EXISTS " + name);
        }
        if (isLiveSnowflake()) {
            connA.close();
            connB.close();
        } else {
            concurrent.shutdown();
        }
    }

    /**
     * Runs a statement in one session.
     *
     * @return its rows as {@code a|b ;; c|d}, the count of a statement that answers none, or {@code REFUSED} and
     *         the refusal with its query and transaction IDs masked
     */
    private String say(final String side, final String sql) {
        try {
            if (isLiveSnowflake()) {
                try (Statement statement = ("A".equals(side) ? connA : connB).createStatement()) {
                    if (!statement.execute(sql)) {
                        return "count " + statement.getUpdateCount();
                    }
                    final java.sql.ResultSet rows = statement.getResultSet();
                    final ResultSetMetaData columns = rows.getMetaData();
                    final StringBuilder out = new StringBuilder();
                    while (rows.next()) {
                        out.append(out.length() == 0 ? "" : " ;; ");
                        for (int i = 1; i <= columns.getColumnCount(); i++) {
                            out.append(i == 1 ? "" : "|").append(rows.getString(i));
                        }
                    }
                    return out.toString();
                }
            }
            final dev.frostlake.storage.ResultSet rows = concurrent.executeQuery(sql,
                "A".equals(side) ? sessionA : sessionB);
            final StringBuilder out = new StringBuilder();
            while (rows.next()) {
                out.append(out.length() == 0 ? "" : " ;; ");
                for (int i = 0; i < rows.getColumnCount(); i++) {
                    out.append(i == 0 ? "" : "|").append(rows.getValue(i));
                }
            }
            return out.toString();
        } catch (final SQLException | RuntimeException refused) {
            return "REFUSED " + String.valueOf(refused.getMessage())
                .replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "<QID>")
                .replaceAll("\\b\\d{19}\\b", "<TXN>");
        }
    }

    @Test
    public void aDroppedTableComesBackWithTheCommittedUpdate() {
        say("A", "CREATE OR REPLACE TABLE dt3 (id INT, v INT)");
        say("A", "INSERT INTO dt3 VALUES (1, 1)");
        say("A", "BEGIN");
        say("A", "UPDATE dt3 SET v = 100 WHERE id = 1");
        say("B", "DROP TABLE dt3");
        say("A", "COMMIT");
        say("B", "UNDROP TABLE dt3");
        assertEquals("1|100", say("B", "SELECT id, v FROM dt3 ORDER BY 1"));
    }

    @Test
    public void aDroppedTableComesBackWithTheCommittedInsert() {
        say("A", "CREATE OR REPLACE TABLE dt7 (id INT, v INT)");
        say("A", "INSERT INTO dt7 VALUES (1, 1)");
        say("A", "BEGIN");
        say("A", "INSERT INTO dt7 VALUES (9, 9)");
        say("B", "DROP TABLE dt7");
        say("A", "COMMIT");
        say("B", "UNDROP TABLE dt7");
        assertEquals("1|1 ;; 9|9", say("B", "SELECT id, v FROM dt7 ORDER BY 1"));
    }

    @Test
    public void aReplacingTableStaysAsItsCreateLeftIt() {
        say("A", "CREATE OR REPLACE TABLE dt6 (id INT, v INT)");
        say("A", "INSERT INTO dt6 VALUES (1, 1)");
        say("A", "BEGIN");
        say("A", "INSERT INTO dt6 VALUES (9, 9)");
        say("B", "CREATE OR REPLACE TABLE dt6 (id INT, v INT)");
        say("A", "COMMIT");
        assertEquals("", say("B", "SELECT id, v FROM dt6 ORDER BY 1"));
        say("A", "CREATE OR REPLACE TABLE dt5 (id INT, v INT)");
        say("A", "INSERT INTO dt5 VALUES (1, 1)");
        say("A", "BEGIN");
        say("A", "UPDATE dt5 SET v = 100 WHERE id = 1");
        say("B", "CREATE OR REPLACE TABLE dt5 (id INT, v INT)");
        say("A", "COMMIT");
        assertEquals("", say("B", "SELECT id, v FROM dt5 ORDER BY 1"));
    }

    @Test
    public void aReplacedTableComesBackWithTheCommittedChange() {
        say("A", "CREATE OR REPLACE TABLE dt8 (id INT, v INT)");
        say("A", "INSERT INTO dt8 VALUES (1, 1)");
        say("A", "BEGIN");
        say("A", "INSERT INTO dt8 VALUES (9, 9)");
        say("B", "CREATE OR REPLACE TABLE dt8 (id INT, v INT)");
        say("A", "COMMIT");
        say("B", "ALTER TABLE dt8 RENAME TO dt8_new");
        say("B", "UNDROP TABLE dt8");
        assertEquals("1|1 ;; 9|9", say("B", "SELECT id, v FROM dt8 ORDER BY 1"));
        say("A", "CREATE OR REPLACE TABLE dt9 (id INT, v INT)");
        say("A", "INSERT INTO dt9 VALUES (1, 1)");
        say("A", "BEGIN");
        say("A", "UPDATE dt9 SET v = 100 WHERE id = 1");
        say("B", "CREATE OR REPLACE TABLE dt9 (id INT, v INT)");
        say("A", "COMMIT");
        say("B", "ALTER TABLE dt9 RENAME TO dt9_new");
        say("B", "UNDROP TABLE dt9");
        assertEquals("1|100", say("B", "SELECT id, v FROM dt9 ORDER BY 1"));
    }
}
