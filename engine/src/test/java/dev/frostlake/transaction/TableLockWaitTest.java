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
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two sessions over one table: an open transaction that has written a row of a table holds it against the other
 * session's UPDATE, DELETE, MERGE and TRUNCATE that would change a row there, which wait for it, run over what it
 * left when it ends, and are refused once LOCK_TIMEOUT passes; reads, INSERTs and statements that change no row
 * neither wait nor hold, and a statement refused for a lock leaves its own transaction open (live-verified).
 *
 * <p>Live, the two sessions are two connections; embedded, two sessions of one concurrent engine.
 */
public class TableLockWaitTest extends BaseDatabaseTest {

    private static final String LOCKED = "Statement '<QID>' has locked table 'LK' in transaction <TXN> and this lock"
        + " has not yet been released.\nYour statement '<QID>' was aborted because waiting for this lock is ";

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
        say("A", "CREATE OR REPLACE TABLE lk (id INT, v INT)");
        say("A", "INSERT INTO lk VALUES (1, 1), (2, 2)");
        say("A", "CREATE OR REPLACE TABLE lk2 (id INT, v INT)");
        say("A", "INSERT INTO lk2 VALUES (1, 1)");
    }

    @AfterEach
    public void closeTwoSessions() throws SQLException {
        say("A", "ROLLBACK");
        say("B", "ROLLBACK");
        say("A", "ALTER SESSION UNSET LOCK_TIMEOUT");
        say("B", "ALTER SESSION UNSET LOCK_TIMEOUT");
        say("A", "DROP TABLE IF EXISTS lk");
        say("A", "DROP TABLE IF EXISTS lk2");
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
                final Statement statement = ("A".equals(side) ? connA : connB).createStatement();
                try {
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
            return "REFUSED " + String.valueOf(refused.getMessage())
                .replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "<QID>")
                .replaceAll("\\b\\d{19}\\b", "<TXN>");
        }
    }

    @Test
    public void aWriterWaitsForAnotherSessionsTransactionAndGivesUp() {
        say("A", "BEGIN");
        say("A", "UPDATE lk SET v = v + 1 WHERE id = 1");
        say("B", "ALTER SESSION SET LOCK_TIMEOUT = 1");
        assertEquals("REFUSED " + LOCKED + "limited to 1 seconds.", say("B", "UPDATE lk SET v = 0 WHERE id = 2"));
        assertEquals("REFUSED " + LOCKED + "limited to 1 seconds.", say("B", "DELETE FROM lk WHERE id = 2"));
        assertEquals("REFUSED " + LOCKED + "limited to 1 seconds.", say("B", "MERGE INTO lk USING (SELECT 9 AS id) s"
            + " ON lk.id = s.id WHEN NOT MATCHED THEN INSERT (id, v) VALUES (9, 9)"));
        assertEquals("REFUSED " + LOCKED + "limited to 1 seconds.", say("B", "TRUNCATE TABLE lk"));
        say("B", "ALTER SESSION SET LOCK_TIMEOUT = 0");
        assertEquals("REFUSED " + LOCKED + "currently not allowed.", say("B", "UPDATE lk SET v = 0 WHERE id = 2"));
        // What changes no row of the held table, and an INSERT, neither waits.
        say("B", "UPDATE lk SET v = 0 WHERE id = 99");
        say("B", "UPDATE lk2 SET v = 5");
        say("B", "INSERT INTO lk VALUES (3, 3)");
        assertEquals("1|1 ;; 2|2 ;; 3|3", say("B", "SELECT id, v FROM lk ORDER BY id"));
        assertEquals("5", say("B", "SELECT v FROM lk2"));
        say("A", "COMMIT");
        assertEquals("1|2 ;; 2|2 ;; 3|3", say("B", "SELECT id, v FROM lk ORDER BY id"));
    }

    @Test
    public void aWaitingWriterRunsOverTheCommittedRow() throws InterruptedException {
        say("A", "BEGIN");
        say("A", "UPDATE lk SET v = v + 1 WHERE id = 1");
        say("B", "ALTER SESSION SET LOCK_TIMEOUT = 60");
        final String[] answered = new String[1];
        final Thread waiting = new Thread(new Runnable() {
            @Override
            public void run() {
                answered[0] = say("B", "UPDATE lk SET v = v + 100 WHERE id = 1");
            }
        });
        waiting.start();
        // Whether the writer is already waiting when the holder commits or starts after it, it updates the row the
        // holder committed.
        Thread.sleep(500L);
        say("A", "COMMIT");
        waiting.join(60000L);
        assertTrue(answered[0] != null && !answered[0].startsWith("REFUSED"), String.valueOf(answered[0]));
        assertEquals("1|102 ;; 2|2", say("A", "SELECT id, v FROM lk ORDER BY id"));
    }

    @Test
    public void aRefusedStatementLeavesItsTransactionOpen() {
        say("A", "BEGIN");
        say("A", "INSERT INTO lk VALUES (5, 5)");
        say("B", "ALTER SESSION SET LOCK_TIMEOUT = 1");
        say("B", "BEGIN");
        say("B", "INSERT INTO lk VALUES (7, 7)");
        assertEquals("REFUSED " + LOCKED + "limited to 1 seconds.", say("B", "UPDATE lk SET v = 0 WHERE id = 2"));
        assertEquals("3", say("B", "SELECT COUNT(*) FROM lk"));
        assertEquals("true", say("B", "SELECT CURRENT_TRANSACTION() IS NOT NULL").toLowerCase(Locale.ROOT));
        say("B", "ROLLBACK");
        assertEquals("2", say("B", "SELECT COUNT(*) FROM lk"));
    }

    @Test
    public void readsAndWritesOfNoRowTakeNoLock() {
        say("A", "BEGIN");
        say("A", "SELECT * FROM lk");
        say("A", "DELETE FROM lk WHERE id = 99");
        say("B", "ALTER SESSION SET LOCK_TIMEOUT = 1");
        say("B", "UPDATE lk SET v = v + 1 WHERE id = 1");
        say("A", "ROLLBACK");
        assertEquals("1|2 ;; 2|2", say("A", "SELECT id, v FROM lk ORDER BY id"));
    }
}
