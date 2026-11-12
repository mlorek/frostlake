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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SYSTEM$ABORT_SESSION of an in-process connection's session ends the statement it waits in for a table lock at
 * once, with code 604 and SQLSTATE 57014, as the HTTP transport's abort does: the write never lands, the session's
 * open transaction is rolled back once that statement is over, and the session's next statement is refused. The
 * waiting statement went on instead, and wrote and committed once the holder's transaction ended. The account's text
 * for the waiting statement is unmeasured on live; the class runs embedded only.
 */
public class DirectConnectionAbortWaitTest extends BaseDatabaseTest {

    private static int engineSequence = 0;

    private String engineName;
    private Connection holder;
    private Connection waiter;
    private Connection aborter;

    @BeforeEach
    public void openThreeConnections() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), "aborts sessions of the embedded engine's in-process driver");
        engineSequence++;
        engineName = "direct_abort_wait_" + engineSequence;
        holder = DriverManager.getConnection("jdbc:frostlake:direct:" + engineName);
        waiter = DriverManager.getConnection("jdbc:frostlake:direct:" + engineName);
        aborter = DriverManager.getConnection("jdbc:frostlake:direct:" + engineName);
        say(holder, "CREATE DATABASE IF NOT EXISTS test_db");
        say(holder, "CREATE SCHEMA IF NOT EXISTS test_db.test_schema");
        say(holder, "USE SCHEMA test_db.test_schema");
        say(waiter, "USE SCHEMA test_db.test_schema");
        say(aborter, "USE SCHEMA test_db.test_schema");
        say(holder, "CREATE OR REPLACE TABLE dab (id INT, v INT)");
        say(holder, "INSERT INTO dab VALUES (1, 1)");
        say(holder, "CREATE OR REPLACE TABLE dau (id INT, v INT)");
        say(waiter, "ALTER SESSION SET LOCK_TIMEOUT = 60");
    }

    @AfterEach
    public void closeThreeConnections() throws SQLException {
        if (engineName == null) {
            return;
        }
        holder.close();
        waiter.close();
        aborter.close();
        final DatabaseEngine engine = DatabaseDriver.unregisterDirectEngine(engineName);
        if (engine != null) {
            engine.shutdown();
        }
    }

    /**
     * Runs a statement over one connection.
     *
     * @return its rows as {@code a|b ;; c|d}, the count of a statement that answers none, or {@code REFUSED}, the
     *         refusal's code and SQLSTATE and its first line
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
            return "REFUSED " + refused.getErrorCode() + "|" + refused.getSQLState() + "|"
                + String.valueOf(refused.getMessage()).split("\n")[0];
        }
    }

    /** Whether the holder's SHOW LOCKS lists a WAITING lock on the table, polled for up to 30 seconds. */
    private boolean waitingListed() throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            for (final String row : say(holder, "SHOW LOCKS").split(" ;; ")) {
                if (row.contains("DAB|") && row.contains("|WAITING|")) {
                    return true;
                }
            }
            Thread.sleep(50L);
        }
        return false;
    }

    /** Starts the waiter's statement on a thread of its own; its answer lands in {@code answered[0]}. */
    private Thread startWaiting(final String sql, final String[] answered) {
        final Thread waiting = new Thread(new Runnable() {
            @Override
            public void run() {
                answered[0] = say(waiter, sql);
            }
        }, "direct-abort-waiter");
        waiting.start();
        return waiting;
    }

    @Test
    public void theAbortEndsTheWaitingWriteAtOnce() throws InterruptedException {
        say(holder, "BEGIN");
        say(holder, "UPDATE dab SET v = 10 WHERE id = 1");
        final String session = say(waiter, "SELECT CURRENT_SESSION()");
        final String[] answered = new String[1];
        final Thread waiting = startWaiting("UPDATE dab SET v = 999 WHERE id = 1", answered);
        assertTrue(waitingListed(), "the writer never waited for the lock");
        assertEquals("session [" + session + "] terminated.",
            say(aborter, "SELECT SYSTEM$ABORT_SESSION(" + session + ")"));
        waiting.join(TimeUnit.SECONDS.toMillis(30));
        assertFalse(waiting.isAlive(), "the aborted session's statement kept waiting");
        assertEquals("REFUSED 604|57014|SQL execution canceled", answered[0]);
        assertEquals("count 0", say(holder, "COMMIT"));
        assertEquals("1|10", say(aborter, "SELECT id, v FROM dab"));
        assertEquals("REFUSED 0|08003|Session no longer exists.  New login required to access the service.",
            say(waiter, "SELECT 1"));
    }

    @Test
    public void theWaitingSessionsTransactionIsRolledBackOnceItsStatementEnds() throws InterruptedException {
        say(waiter, "BEGIN");
        assertEquals("count 1", say(waiter, "INSERT INTO dau VALUES (8, 8)"));
        say(holder, "BEGIN");
        say(holder, "UPDATE dab SET v = 10 WHERE id = 1");
        final String session = say(waiter, "SELECT CURRENT_SESSION()");
        final String[] answered = new String[1];
        final Thread waiting = startWaiting("UPDATE dab SET v = 999 WHERE id = 1", answered);
        assertTrue(waitingListed(), "the writer never waited for the lock");
        say(aborter, "SELECT SYSTEM$ABORT_SESSION(" + session + ")");
        waiting.join(TimeUnit.SECONDS.toMillis(30));
        assertFalse(waiting.isAlive(), "the aborted session's statement kept waiting");
        assertEquals("REFUSED 604|57014|SQL execution canceled", answered[0]);
        say(holder, "COMMIT");
        assertEquals("1|10", say(aborter, "SELECT id, v FROM dab"));
        assertEquals("0", say(aborter, "SELECT COUNT(*) FROM dau"));
        // Nothing of the aborted session's transaction holds the table it wrote.
        say(aborter, "ALTER SESSION SET LOCK_TIMEOUT = 0");
        assertEquals("count 0", say(aborter, "DELETE FROM dau"));
    }
}
