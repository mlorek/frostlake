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

package dev.frostlake.jdbc;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.DatabaseEngine;
import dev.frostlake.LiveSnowflake;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two connections of the in-process driver over one engine take turns at a table as two sessions of the account do:
 * a writer that meets a table the other connection's open transaction has written waits for it, runs over what it
 * committed, and is refused once its LOCK_TIMEOUT passes, with code 627 and SQLSTATE 57014 — 626 where it may not
 * wait at all (live-verified).
 *
 * <p>Live, the two sessions are two connections; embedded, two {@code jdbc:frostlake:direct:} connections sharing
 * one engine.
 */
public class DirectConnectionLockWaitTest extends BaseDatabaseTest {

    private static final String LOCKED = "Statement '<QID>' has locked table 'DLK' in transaction <TXN> and this lock"
        + " has not yet been released.\nYour statement '<QID>' was aborted because waiting for this lock is ";

    private static int engineSequence = 0;

    private String engineName;
    private Connection connA;
    private Connection connB;

    @BeforeEach
    public void openTwoConnections() throws SQLException {
        if (isLiveSnowflake()) {
            connA = LiveSnowflake.open();
            connB = LiveSnowflake.open();
        } else {
            engineSequence++;
            engineName = "direct_lock_wait_" + engineSequence;
            connA = DriverManager.getConnection("jdbc:frostlake:direct:" + engineName);
            connB = DriverManager.getConnection("jdbc:frostlake:direct:" + engineName);
            say(connA, "CREATE DATABASE IF NOT EXISTS test_db");
            say(connA, "CREATE SCHEMA IF NOT EXISTS test_db.test_schema");
        }
        say(connA, "USE SCHEMA test_db.test_schema");
        say(connB, "USE SCHEMA test_db.test_schema");
        say(connA, "CREATE OR REPLACE TABLE dlk (id INT, v INT)");
        say(connA, "INSERT INTO dlk VALUES (1, 1), (2, 2)");
    }

    @AfterEach
    public void closeTwoConnections() throws SQLException {
        say(connA, "ROLLBACK");
        say(connB, "ROLLBACK");
        say(connB, "ALTER SESSION UNSET LOCK_TIMEOUT");
        say(connA, "DROP TABLE IF EXISTS dlk");
        connA.close();
        connB.close();
        if (engineName != null) {
            final DatabaseEngine engine = DatabaseDriver.unregisterDirectEngine(engineName);
            if (engine != null) {
                engine.shutdown();
            }
        }
    }

    /**
     * Runs a statement over one connection.
     *
     * @return its rows as {@code a|b ;; c|d}, the count of a statement that answers none, or {@code REFUSED} and
     *         the refusal with its query and transaction IDs masked
     */
    private static String say(final Connection connection, final String sql) {
        try (Statement statement = connection.createStatement()) {
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
        } catch (final SQLException refused) {
            return "REFUSED " + String.valueOf(refused.getMessage())
                .replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "<QID>")
                .replaceAll("\\b\\d{19}\\b", "<TXN>");
        }
    }

    /** The code and the SQLSTATE of the refusal a statement meets, as the driver reads them. */
    private static String codeOf(final Connection connection, final String sql) {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
            return "no refusal";
        } catch (final SQLException refused) {
            return refused.getErrorCode() + "|" + refused.getSQLState();
        }
    }

    /** Whether connection A's SHOW LOCKS lists a WAITING lock on the table, polled for up to 30 seconds. */
    private boolean waitingListed() throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            for (final String row : say(connA, "SHOW LOCKS").split(" ;; ")) {
                if (row.contains("DLK|") && row.contains("|WAITING|")) {
                    return true;
                }
            }
            Thread.sleep(50L);
        }
        return false;
    }

    @Test
    public void aWriterWaitsForTheOtherConnectionsTransactionAndGivesUp() {
        say(connA, "BEGIN");
        say(connA, "UPDATE dlk SET v = 10 WHERE id = 1");
        say(connB, "ALTER SESSION SET LOCK_TIMEOUT = 1");
        assertEquals("REFUSED " + LOCKED + "limited to 1 seconds.", say(connB, "UPDATE dlk SET v = 20 WHERE id = 2"));
        assertEquals("REFUSED " + LOCKED + "limited to 1 seconds.", say(connB, "DELETE FROM dlk WHERE id = 2"));
        say(connB, "ALTER SESSION SET LOCK_TIMEOUT = 0");
        assertEquals("REFUSED " + LOCKED + "currently not allowed.", say(connB, "UPDATE dlk SET v = 20 WHERE id = 2"));
        // Each connection reads its own side of the open transaction.
        assertEquals("1|10 ;; 2|2", say(connA, "SELECT id, v FROM dlk ORDER BY id"));
        assertEquals("1|1 ;; 2|2", say(connB, "SELECT id, v FROM dlk ORDER BY id"));
        say(connA, "ROLLBACK");
        assertEquals("count 1", say(connB, "UPDATE dlk SET v = 20 WHERE id = 2"));
        assertEquals("1|1 ;; 2|20", say(connA, "SELECT id, v FROM dlk ORDER BY id"));
    }

    @Test
    public void aRefusedWriterReadsTheLockCodes() {
        say(connA, "BEGIN");
        say(connA, "UPDATE dlk SET v = 10 WHERE id = 1");
        say(connB, "ALTER SESSION SET LOCK_TIMEOUT = 1");
        assertEquals("627|57014", codeOf(connB, "UPDATE dlk SET v = 20 WHERE id = 2"));
        say(connB, "ALTER SESSION SET LOCK_TIMEOUT = 0");
        assertEquals("626|57014", codeOf(connB, "UPDATE dlk SET v = 20 WHERE id = 2"));
    }

    @Test
    public void aWaitingWriterRunsOverTheCommittedRow() throws InterruptedException {
        say(connA, "BEGIN");
        say(connA, "UPDATE dlk SET v = v + 10 WHERE id = 1");
        say(connB, "ALTER SESSION SET LOCK_TIMEOUT = 60");
        final String[] answered = new String[1];
        final Thread waiting = new Thread(new Runnable() {
            @Override
            public void run() {
                answered[0] = say(connB, "UPDATE dlk SET v = v + 100 WHERE id = 1");
            }
        }, "direct-lock-waiter");
        waiting.start();
        assertTrue(waitingListed(), "the writer never waited for the lock");
        say(connA, "COMMIT");
        waiting.join(TimeUnit.SECONDS.toMillis(60));
        assertEquals("count 1", answered[0]);
        assertEquals("1|111 ;; 2|2", say(connA, "SELECT id, v FROM dlk ORDER BY id"));
    }
}
