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
 * The implicit transaction a session holds open with AUTOCOMMIT off holds its locks as one a BEGIN opened does:
 * another session's write of a row it wrote waits for its COMMIT, and so does a consuming read of a stream it read.
 * The other session's UPDATE ran at once and committed, and the first session's COMMIT then wrote over it; both
 * sessions consumed the same stream records.
 *
 * <p>Live, the two sessions are two connections; embedded, two sessions of one concurrent engine.
 */
public class AutocommitOffLockTest extends BaseDatabaseTest {

    private static final String WAIT_REFUSED = "this lock has not yet been released. | Your statement '<QID>' was "
        + "aborted because waiting for this lock is currently not allowed.";

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
        say("A", "CREATE OR REPLACE TABLE aol_t (id INT, v INT)");
        say("A", "CREATE OR REPLACE STREAM aol_s ON TABLE aol_t");
        say("A", "INSERT INTO aol_t VALUES (1, 1)");
        say("A", "CREATE OR REPLACE TABLE aol_sink (id INT, who STRING)");
        say("B", "ALTER SESSION SET LOCK_TIMEOUT = 0");
    }

    @AfterEach
    public void closeTwoSessions() throws SQLException {
        say("A", "ROLLBACK");
        say("A", "ALTER SESSION SET AUTOCOMMIT = TRUE");
        say("B", "ALTER SESSION UNSET LOCK_TIMEOUT");
        say("A", "DROP STREAM IF EXISTS aol_s");
        say("A", "DROP TABLE IF EXISTS aol_t");
        say("A", "DROP TABLE IF EXISTS aol_sink");
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
     * @return its rows as {@code a|b ;; c|d}, the count of a statement that answers none, or {@code REFUSED} and the
     *         refusal on one line with its query and transaction IDs masked
     */
    private String say(final String side, final String sql) {
        try {
            if (isLiveSnowflake()) {
                final Statement statement = ("A".equals(side) ? connA : connB).createStatement();
                try {
                    if (!statement.execute(sql)) {
                        return String.valueOf(statement.getUpdateCount());
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
                } finally {
                    statement.close();
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
            return "REFUSED " + String.valueOf(refused.getMessage()).replace("\n", " | ")
                .replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "<QID>")
                .replaceAll("\\b\\d{19}\\b", "<TXN>");
        }
    }

    @Test
    public void anotherSessionsWriteWaitsForTheImplicitTransaction() {
        say("A", "ALTER SESSION SET AUTOCOMMIT = FALSE");
        say("A", "UPDATE aol_t SET v = v + 10 WHERE id = 1");
        final String refused = say("B", "UPDATE aol_t SET v = v + 100 WHERE id = 1");
        assertEquals("REFUSED Statement '<QID>' has locked table 'AOL_T' in transaction <TXN> and " + WAIT_REFUSED,
            refused);
        say("A", "COMMIT");
        assertEquals("11", say("B", "SELECT v FROM aol_t"));
        say("B", "UPDATE aol_t SET v = v + 100 WHERE id = 1");
        assertEquals("111", say("A", "SELECT v FROM aol_t"));
    }

    @Test
    public void anotherSessionsConsumingReadWaitsForTheImplicitTransaction() {
        say("A", "ALTER SESSION SET AUTOCOMMIT = FALSE");
        say("A", "INSERT INTO aol_sink SELECT id, 'A' FROM aol_s");
        final String refused = say("B", "INSERT INTO aol_sink SELECT id, 'B' FROM aol_s");
        assertEquals("REFUSED Statement '<QID>' has locked stream 'AOL_S' in transaction <TXN> and " + WAIT_REFUSED,
            refused);
        say("A", "COMMIT");
        say("B", "INSERT INTO aol_sink SELECT id, 'B' FROM aol_s");
        assertEquals("1|A", say("B", "SELECT id, who FROM aol_sink ORDER BY who"));
    }
}
