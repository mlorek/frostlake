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
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A session's own temporary table is its own to lock: a transaction that writes one holds nothing of the permanent
 * table its name hides, so another session writes that permanent table, in a transaction of its own too, without
 * waiting (live-verified).
 *
 * <p>Live, the sessions are connections; embedded, sessions of one concurrent engine.
 */
public class TemporaryTableLockTest extends BaseDatabaseTest {

    private ConcurrentDatabaseEngine concurrent;
    private final SessionContext[] sessions = new SessionContext[3];
    private final Connection[] conns = new Connection[3];

    @BeforeEach
    public void openSessions() {
        for (int i = 0; i < 3; i++) {
            if (isLiveSnowflake()) {
                conns[i] = LiveSnowflake.open();
            } else {
                if (concurrent == null) {
                    concurrent = new ConcurrentDatabaseEngine();
                }
                sessions[i] = concurrent.createSession();
            }
        }
        if (!isLiveSnowflake()) {
            say(2, "CREATE DATABASE IF NOT EXISTS test_db");
            say(2, "CREATE SCHEMA IF NOT EXISTS test_db.test_schema");
        }
        for (int i = 0; i < 3; i++) {
            say(i, "USE SCHEMA test_db.test_schema");
        }
    }

    @AfterEach
    public void closeSessions() throws SQLException {
        say(0, "ROLLBACK");
        say(1, "ROLLBACK");
        say(1, "ALTER SESSION UNSET LOCK_TIMEOUT");
        say(2, "DROP TABLE IF EXISTS tlk");
        if (isLiveSnowflake()) {
            for (final Connection conn : conns) {
                conn.close();
            }
        } else {
            concurrent.shutdown();
        }
    }

    /**
     * Runs a statement in one session.
     *
     * @return a query's first cell, OK for any other statement that runs, or {@code REFUSED} and the refusal
     */
    private String say(final int side, final String sql) {
        try {
            if (isLiveSnowflake()) {
                try (Statement statement = conns[side].createStatement()) {
                    if (!statement.execute(sql)) {
                        return "OK";
                    }
                    final java.sql.ResultSet rows = statement.getResultSet();
                    return sql.startsWith("SELECT") ? (rows.next() ? rows.getString(1) : "no rows") : "OK";
                }
            }
            final dev.frostlake.storage.ResultSet rows = concurrent.executeQuery(sql, sessions[side]);
            return sql.startsWith("SELECT") ? (rows.next() ? String.valueOf(rows.getValue(0)) : "no rows") : "OK";
        } catch (final SQLException | RuntimeException refused) {
            return "REFUSED " + refused.getMessage();
        }
    }

    @Test
    public void aTransactionOnATemporaryTableHoldsNothingOfThePermanentOneItHides() {
        say(2, "CREATE OR REPLACE TABLE tlk (i INT)");
        say(2, "INSERT INTO tlk VALUES (1)");
        say(0, "CREATE TEMPORARY TABLE tlk (i INT)");
        say(0, "INSERT INTO tlk VALUES (5)");
        say(0, "BEGIN");
        assertEquals("OK", say(0, "UPDATE tlk SET i = 6"));
        say(1, "ALTER SESSION SET LOCK_TIMEOUT = 1");
        final String alone = say(1, "UPDATE tlk SET i = 2");
        assertTrue(!alone.startsWith("REFUSED"), alone);
        say(1, "BEGIN");
        final String inTransaction = say(1, "UPDATE tlk SET i = 3");
        assertTrue(!inTransaction.startsWith("REFUSED"), inTransaction);
        assertEquals("OK", say(0, "UPDATE tlk SET i = 7"));
        say(1, "COMMIT");
        say(0, "COMMIT");
        assertEquals("3", say(2, "SELECT i FROM tlk"));
        assertEquals("7", say(0, "SELECT i FROM tlk"));
    }
}
