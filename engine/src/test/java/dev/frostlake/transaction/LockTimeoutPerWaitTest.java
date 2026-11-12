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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LOCK_TIMEOUT bounds each lock wait on its own, as the parameter's description says: a statement that waits for one
 * table's holder and then for another's gets the whole timeout for each. It got one deadline, counted from its first
 * wait, so it was refused while neither wait had reached the timeout. The holders end seconds apart, well inside the
 * timeout each and past it together. Unmeasured on live; the class runs embedded, over in-process connections.
 */
public class LockTimeoutPerWaitTest extends BaseDatabaseTest {

    private static int engineSequence = 0;

    private String engineName;
    private Connection first;
    private Connection second;
    private Connection writer;

    @BeforeEach
    public void openConnections() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), "holds locks of an embedded engine for seconds");
        engineSequence++;
        engineName = "lock_timeout_per_wait_" + engineSequence;
        first = DriverManager.getConnection("jdbc:frostlake:direct:" + engineName);
        second = DriverManager.getConnection("jdbc:frostlake:direct:" + engineName);
        writer = DriverManager.getConnection("jdbc:frostlake:direct:" + engineName);
        say(first, "CREATE DATABASE IF NOT EXISTS ltpw");
        for (final Connection connection : new Connection[] {first, second, writer}) {
            say(connection, "USE SCHEMA ltpw.public");
        }
        say(first, "CREATE OR REPLACE TABLE lt1 (id INT)");
        say(first, "CREATE OR REPLACE TABLE lt2 (id INT)");
        say(first, "INSERT INTO lt1 VALUES (1)");
        say(first, "INSERT INTO lt2 VALUES (1)");
        say(writer, "ALTER SESSION SET LOCK_TIMEOUT = 6");
    }

    @AfterEach
    public void closeConnections() throws SQLException {
        if (engineName == null) {
            return;
        }
        first.close();
        second.close();
        writer.close();
        final DatabaseEngine engine = DatabaseDriver.unregisterDirectEngine(engineName);
        if (engine != null) {
            engine.shutdown();
        }
    }

    private static String say(final Connection connection, final String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            return statement.execute(sql) ? "rows" : "count " + statement.getUpdateCount();
        }
    }

    /** Whether SHOW LOCKS lists the writer WAITING on the table, polled for up to 30 seconds. */
    private boolean waitingOn(final String table) throws SQLException, InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            try (Statement statement = first.createStatement();
                 ResultSet rows = statement.executeQuery("SHOW LOCKS")) {
                while (rows.next()) {
                    final StringBuilder row = new StringBuilder();
                    for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) {
                        row.append(rows.getString(i)).append('|');
                    }
                    if (row.indexOf(table + "|") >= 0 && row.indexOf("|WAITING|") >= 0) {
                        return true;
                    }
                }
            }
            Thread.sleep(50L);
        }
        return false;
    }

    @Test
    public void eachWaitHasTheWholeTimeout() throws SQLException, InterruptedException {
        say(first, "BEGIN");
        say(first, "DELETE FROM lt1");
        say(second, "BEGIN");
        say(second, "DELETE FROM lt2");
        final String[] answered = new String[1];
        final Thread waiting = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    answered[0] = say(writer, "INSERT OVERWRITE ALL INTO lt1 VALUES (n) INTO lt2 VALUES (n)"
                        + " SELECT 7 AS n");
                } catch (final SQLException refused) {
                    answered[0] = "REFUSED " + refused.getMessage();
                }
            }
        }, "lock-timeout-writer");
        waiting.start();
        assertTrue(waitingOn("LT1"), "the writer never waited for LT1");
        Thread.sleep(3500L);
        say(first, "COMMIT");
        assertTrue(waitingOn("LT2"), "the writer never waited for LT2");
        Thread.sleep(3500L);
        say(second, "COMMIT");
        waiting.join(TimeUnit.SECONDS.toMillis(30));
        assertFalse(waiting.isAlive(), "the writer kept waiting");
        assertFalse(answered[0].startsWith("REFUSED"), answered[0]);
        try (Statement statement = first.createStatement();
             ResultSet rows = statement.executeQuery(
                 "SELECT (SELECT id FROM lt1) || '|' || (SELECT id FROM lt2)")) {
            rows.next();
            assertEquals("7|7", rows.getString(1));
        }
    }
}
