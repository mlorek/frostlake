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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.DatabaseEngine;
import dev.frostlake.jdbc.DatabaseDriver;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QUERY_HISTORY reports the time a statement waited for another session's table lock as TRANSACTION_BLOCKED_TIME and
 * leaves it out of EXECUTION_TIME, while TOTAL_ELAPSED_TIME counts both. The column read 0 and the wait counted as
 * execution. The holder keeps its lock for well over a second, and the assertions allow for scheduling. Unmeasured on
 * live; the class runs embedded, over two in-process connections.
 */
public class QueryHistoryBlockedTimeTest extends BaseDatabaseTest {

    private static int engineSequence = 0;

    private String engineName;
    private Connection holder;
    private Connection waiter;

    @BeforeEach
    public void openConnections() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), "holds a lock of an embedded engine");
        engineSequence++;
        engineName = "query_history_blocked_" + engineSequence;
        holder = DriverManager.getConnection("jdbc:frostlake:direct:" + engineName);
        waiter = DriverManager.getConnection("jdbc:frostlake:direct:" + engineName);
        say(holder, "CREATE DATABASE IF NOT EXISTS qhb");
        say(holder, "USE SCHEMA qhb.public");
        say(waiter, "USE SCHEMA qhb.public");
        say(holder, "CREATE OR REPLACE TABLE qhbt (id INT, v INT)");
        say(holder, "INSERT INTO qhbt VALUES (1, 1)");
        say(waiter, "ALTER SESSION SET LOCK_TIMEOUT = 60");
    }

    @AfterEach
    public void closeConnections() throws SQLException {
        if (engineName == null) {
            return;
        }
        holder.close();
        waiter.close();
        final DatabaseEngine engine = DatabaseDriver.unregisterDirectEngine(engineName);
        if (engine != null) {
            engine.shutdown();
        }
    }

    private static void say(final Connection connection, final String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    /** Whether SHOW LOCKS lists a WAITING lock, polled for up to 30 seconds. */
    private boolean waitingListed() throws SQLException, InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            try (Statement statement = holder.createStatement();
                 ResultSet rows = statement.executeQuery("SHOW LOCKS")) {
                while (rows.next()) {
                    for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) {
                        if ("WAITING".equals(rows.getString(i))) {
                            return true;
                        }
                    }
                }
            }
            Thread.sleep(50L);
        }
        return false;
    }

    @Test
    public void aLockWaitIsBlockedTimeNotExecutionTime() throws SQLException, InterruptedException {
        say(holder, "BEGIN");
        say(holder, "UPDATE qhbt SET v = 10 WHERE id = 1");
        final SQLException[] failed = new SQLException[1];
        final Thread waiting = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    say(waiter, "UPDATE qhbt SET v = 99 WHERE id = 1");
                } catch (final SQLException refused) {
                    failed[0] = refused;
                }
            }
        }, "query-history-blocked-waiter");
        waiting.start();
        assertTrue(waitingListed(), "the writer never waited for the lock");
        Thread.sleep(1500L);
        say(holder, "COMMIT");
        waiting.join(TimeUnit.SECONDS.toMillis(30));
        assertFalse(waiting.isAlive(), "the writer kept waiting");
        assertTrue(failed[0] == null, String.valueOf(failed[0]));
        try (Statement statement = waiter.createStatement(); ResultSet rows = statement.executeQuery("""
                SELECT transaction_blocked_time, execution_time, total_elapsed_time
                FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY())
                WHERE query_text = 'UPDATE qhbt SET v = 99 WHERE id = 1'""")) {
            assertTrue(rows.next(), "the statement is in the history");
            final long blocked = rows.getLong(1);
            final long execution = rows.getLong(2);
            final long total = rows.getLong(3);
            assertTrue(blocked >= 1000L, "blocked " + blocked);
            assertTrue(total >= blocked + execution, "total " + total + ", blocked " + blocked + ", execution "
                + execution);
            assertTrue(execution < blocked, "execution " + execution + ", blocked " + blocked);
        }
    }
}
