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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * A statement refused for another session's table lock names itself by the query ID it ran under, the one
 * LAST_QUERY_ID answers once it is refused (live-verified); and a waiting statement whose thread is interrupted ends
 * as a cancel, {@code SQL execution canceled}, not as its LOCK_TIMEOUT running out (engine only: the account's
 * statements run on no thread of the client's).
 *
 * <p>Live, the two sessions are two connections; embedded, two sessions of one concurrent engine.
 */
public class LockRefusalQueryIdTest extends BaseDatabaseTest {

    private static final Pattern WAITER = Pattern.compile("Your statement '([0-9a-f-]{36})' was aborted");

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
        say("A", "CREATE OR REPLACE TABLE lq (id INT, v INT)");
        say("A", "INSERT INTO lq VALUES (1, 1), (2, 2)");
    }

    @AfterEach
    public void closeTwoSessions() throws SQLException {
        say("A", "ROLLBACK");
        say("B", "ROLLBACK");
        say("B", "ALTER SESSION UNSET LOCK_TIMEOUT");
        say("A", "DROP TABLE IF EXISTS lq");
        if (isLiveSnowflake()) {
            connA.close();
            connB.close();
        } else {
            concurrent.shutdown();
        }
    }

    @Test
    public void theRefusalNamesTheStatementByTheIdLastQueryIdAnswers() {
        say("A", "BEGIN");
        say("A", "UPDATE lq SET v = v + 1 WHERE id = 1");
        say("B", "ALTER SESSION SET LOCK_TIMEOUT = 1");
        final String refused = say("B", "UPDATE lq SET v = 0 WHERE id = 2");
        final Matcher waiter = WAITER.matcher(refused);
        assertTrue(waiter.find(), refused);
        assertEquals(waiter.group(1), say("B", "SELECT LAST_QUERY_ID()"));
        say("B", "ALTER SESSION SET LOCK_TIMEOUT = 0");
        final Matcher notAllowed = WAITER.matcher(say("B", "DELETE FROM lq WHERE id = 2"));
        assertTrue(notAllowed.find());
        assertEquals(notAllowed.group(1), say("B", "SELECT LAST_QUERY_ID()"));
    }

    @Test
    public void anInterruptedWaitEndsAsACancel() throws InterruptedException {
        assumeFalse(isLiveSnowflake());
        say("A", "BEGIN");
        say("A", "UPDATE lq SET v = v + 1 WHERE id = 1");
        say("B", "ALTER SESSION SET LOCK_TIMEOUT = 60");
        final String[] answered = new String[1];
        final Thread waiting = new Thread(new Runnable() {
            @Override
            public void run() {
                answered[0] = say("B", "UPDATE lq SET v = 0 WHERE id = 2");
            }
        });
        waiting.start();
        // The writer is interrupted once SHOW LOCKS lists its lock as waiting.
        for (int i = 0; i < 500 && !say("A", "SHOW LOCKS").contains("WAITING"); i++) {
            Thread.sleep(20L);
        }
        waiting.interrupt();
        waiting.join(60000L);
        assertEquals("REFUSED SQL execution canceled", answered[0]);
    }

    /** Runs a statement in one session: its first cell, or {@code REFUSED} and the refusal. */
    private String say(final String side, final String sql) {
        try {
            if (isLiveSnowflake()) {
                try (Statement statement = ("A".equals(side) ? connA : connB).createStatement()) {
                    if (!statement.execute(sql)) {
                        return "count " + statement.getUpdateCount();
                    }
                    final StringBuilder out = new StringBuilder();
                    try (ResultSet rows = statement.getResultSet()) {
                        while (rows.next()) {
                            out.append(out.length() == 0 ? "" : " ;; ").append(rows.getString(1));
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
            return "REFUSED " + refused.getMessage();
        }
    }
}
