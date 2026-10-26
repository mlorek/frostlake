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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two sessions over one stream: a read of the stream inside an explicit transaction, a query or a consuming DML,
 * holds the stream until the transaction ends. The other session's reads that would take it - a consuming DML, a
 * CTAS, a query inside its own explicit transaction - wait for it and are refused once LOCK_TIMEOUT passes; an
 * autocommit query, SYSTEM$STREAM_HAS_DATA, a write of the stream's table and a CREATE OR REPLACE STREAM go ahead.
 * A transaction that has only begun holds nothing, and SHOW LOCKS lists a held stream with the type STREAM
 * (live-verified).
 *
 * <p>Live, the two sessions are two connections; embedded, two sessions of one concurrent engine.
 */
public class StreamLockWaitTest extends BaseDatabaseTest {

    private static final String LOCKED = "REFUSED Statement '<QID>' has locked stream 'S4' in transaction <TXN> and"
        + " this lock has not yet been released.\nYour statement '<QID>' was aborted because waiting for this lock is ";

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
        say("A", "CREATE OR REPLACE TABLE t4 (id INT)");
        say("A", "CREATE OR REPLACE STREAM s4 ON TABLE t4");
        say("A", "INSERT INTO t4 VALUES (1)");
        say("A", "CREATE OR REPLACE TABLE sink4 (id INT)");
        say("A", "INSERT INTO sink4 VALUES (5)");
    }

    @AfterEach
    public void closeTwoSessions() throws SQLException {
        say("A", "ROLLBACK");
        say("B", "ROLLBACK");
        say("A", "ALTER SESSION UNSET LOCK_TIMEOUT");
        say("B", "ALTER SESSION UNSET LOCK_TIMEOUT");
        say("A", "DROP STREAM IF EXISTS s4");
        say("A", "DROP TABLE IF EXISTS t4");
        say("A", "DROP TABLE IF EXISTS sink4");
        say("A", "DROP TABLE IF EXISTS x4");
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
    public void aReadInATransactionHoldsTheStreamAgainstTheReadsThatWouldTakeIt() {
        say("A", "BEGIN");
        assertEquals("1", say("A", "SELECT id FROM s4"));
        say("B", "ALTER SESSION SET LOCK_TIMEOUT = 1");
        assertEquals(LOCKED + "limited to 1 seconds.", say("B", "INSERT INTO sink4 SELECT id FROM s4"));
        say("B", "ALTER SESSION SET LOCK_TIMEOUT = 0");
        assertEquals(LOCKED + "currently not allowed.", say("B", "INSERT INTO sink4 SELECT id FROM s4"));
        assertEquals(LOCKED + "currently not allowed.",
            say("B", "DELETE FROM sink4 WHERE id IN (SELECT id FROM s4)"));
        assertEquals(LOCKED + "currently not allowed.", say("B", "CREATE OR REPLACE TABLE x4 AS SELECT id FROM s4"));
        say("B", "BEGIN");
        assertEquals(LOCKED + "currently not allowed.", say("B", "SELECT COUNT(*) FROM s4"));
        say("B", "ROLLBACK");
        // What would not take the stream reads at once.
        assertEquals("1", say("B", "SELECT id FROM s4"));
        assertEquals("true", say("B", "SELECT SYSTEM$STREAM_HAS_DATA('s4')"));
        assertTrue(!say("B", "UPDATE t4 SET id = id").startsWith("REFUSED"));
        final String locks = say("A", "SHOW LOCKS");
        assertTrue(locks.contains("TEST_DB.TEST_SCHEMA.S4|STREAM|"), locks);
        assertTrue(locks.contains("|HOLDING|"), locks);
        say("A", "COMMIT");
        assertEquals("1", say("B", "INSERT INTO sink4 SELECT id FROM s4"));
    }

    @Test
    public void aConsumingDmlInATransactionHoldsTheStream() {
        say("A", "BEGIN");
        assertEquals("1", say("A", "INSERT INTO sink4 SELECT id FROM s4"));
        say("B", "ALTER SESSION SET LOCK_TIMEOUT = 1");
        assertEquals(LOCKED + "limited to 1 seconds.", say("B", "INSERT INTO sink4 SELECT id FROM s4"));
        say("A", "COMMIT");
        // The holder consumed the records: the read after it finds none.
        assertEquals("0", say("B", "INSERT INTO sink4 SELECT id FROM s4"));
    }

    @Test
    public void aTransactionThatOnlyBeganOrAskedWhetherTheStreamHasDataHoldsNothing() {
        say("A", "BEGIN");
        assertEquals("true", say("A", "SELECT SYSTEM$STREAM_HAS_DATA('s4')"));
        say("B", "ALTER SESSION SET LOCK_TIMEOUT = 1");
        assertEquals("1", say("B", "INSERT INTO sink4 SELECT id FROM s4"));
        // The offset the other session committed is where the transaction's read starts too.
        assertEquals("", say("A", "SELECT id FROM s4"));
    }
}
