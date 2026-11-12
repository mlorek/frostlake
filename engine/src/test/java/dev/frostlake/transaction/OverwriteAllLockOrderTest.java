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
import dev.frostlake.LiveSnowflake;
import dev.frostlake.jdbc.DatabaseDriver;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A multi-table INSERT OVERWRITE that meets a target another session's transaction holds waits for it before it
 * empties any target, so once the holder ends its source query reads the targets as they stand and the statement is
 * all or nothing ("each statement is its own transaction", documented). It emptied the targets one by one instead,
 * and a wait on a later target ran the statement again over its own half-done truncation: a source query over the
 * first target counted none of its rows. Whether it holds the targets it has reached while it waits the account leaves
 * open; inside an explicit transaction the engine already answered right.
 */
public class OverwriteAllLockOrderTest extends BaseDatabaseTest {

    private static int engineSequence = 0;

    private String engineName;
    private Connection writer;
    private Connection holder;

    @BeforeEach
    public void openTwoConnections() throws SQLException {
        if (isLiveSnowflake()) {
            writer = LiveSnowflake.open();
            holder = LiveSnowflake.open();
        } else {
            engineSequence++;
            engineName = "overwrite_all_lock_" + engineSequence;
            writer = DriverManager.getConnection("jdbc:frostlake:direct:" + engineName);
            holder = DriverManager.getConnection("jdbc:frostlake:direct:" + engineName);
            say(writer, "CREATE DATABASE IF NOT EXISTS test_db");
            say(writer, "CREATE SCHEMA IF NOT EXISTS test_db.test_schema");
        }
        say(writer, "USE SCHEMA test_db.test_schema");
        say(holder, "USE SCHEMA test_db.test_schema");
        say(writer, "CREATE OR REPLACE TABLE ow1 (id INT)");
        say(writer, "CREATE OR REPLACE TABLE ow2 (id INT)");
        say(writer, "INSERT INTO ow1 VALUES (1), (2)");
        say(writer, "INSERT INTO ow2 VALUES (1)");
        say(writer, "ALTER SESSION SET LOCK_TIMEOUT = 60");
    }

    @AfterEach
    public void closeTwoConnections() throws SQLException {
        say(holder, "ROLLBACK");
        say(writer, "ROLLBACK");
        say(writer, "ALTER SESSION UNSET LOCK_TIMEOUT");
        say(writer, "DROP TABLE IF EXISTS ow1");
        say(writer, "DROP TABLE IF EXISTS ow2");
        writer.close();
        holder.close();
        if (engineName != null) {
            final DatabaseEngine engine = DatabaseDriver.unregisterDirectEngine(engineName);
            if (engine != null) {
                engine.shutdown();
            }
        }
    }

    /** Runs a statement: its rows as {@code a ;; b}, the count of one that answers none, or the refusal. */
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
            return "REFUSED " + refused.getMessage();
        }
    }

    /** Whether the holder's SHOW LOCKS lists a WAITING lock on the second target, polled for up to 30 seconds. */
    private boolean waitingListed() throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            for (final String row : say(holder, "SHOW LOCKS").split(" ;; ")) {
                if (row.contains("OW2|") && row.contains("|WAITING|")) {
                    return true;
                }
            }
            Thread.sleep(50L);
        }
        return false;
    }

    @Test
    public void theStatementWaitsBeforeItEmptiesAnyTarget() throws InterruptedException {
        say(holder, "BEGIN");
        say(holder, "DELETE FROM ow2");
        final String[] answered = new String[1];
        final Thread waiting = new Thread(new Runnable() {
            @Override
            public void run() {
                answered[0] = say(writer, """
                    INSERT OVERWRITE ALL INTO ow1 VALUES (n) INTO ow2 VALUES (n)
                    SELECT COUNT(*) + 100 AS n FROM ow1""");
            }
        }, "overwrite-all-waiter");
        waiting.start();
        assertTrue(waitingListed(), "the statement never waited for the lock");
        // It waits for the second target alone.
        final String locks = say(holder, "SHOW LOCKS");
        for (final String row : locks.split(" ;; ")) {
            assertFalse(row.contains("OW1|") && row.contains("|WAITING|"), locks);
        }
        say(holder, "COMMIT");
        waiting.join(TimeUnit.SECONDS.toMillis(60));
        assertFalse(waiting.isAlive(), "the statement kept waiting");
        assertFalse(answered[0].startsWith("REFUSED"), answered[0]);
        assertEquals("102", say(writer, "SELECT id FROM ow1"));
        assertEquals("102", say(writer, "SELECT id FROM ow2"));
    }
}
