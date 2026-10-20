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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SYSTEM$ABORT_TRANSACTION of a transaction whose statement waits for another session's table lock (live-verified):
 * the waiting statement ends at once, naming the transaction and the user who aborted it, the transaction's writes are
 * gone, and its session holds the transaction as aborted, refusing every statement until a COMMIT, which fails, or a
 * ROLLBACK. An autocommit statement's own transaction is not aborted: SHOW TRANSACTIONS lists it, but the abort
 * answers that it could not abort it, and the statement runs once the lock is free.
 *
 * <p>Live, the three sessions are three connections; embedded, three sessions of one concurrent engine.
 */
public class LockWaitTransactionAbortTest extends BaseDatabaseTest {

    private static final int HOLDER = 0;
    private static final int WAITER = 1;
    private static final int OTHER = 2;
    private static final long LONG_WAIT_MILLIS = 60000L;
    private static final String IGNORED = "Your transaction was aborted. All statements will be ignored until you"
        + " issue a COMMIT or a ROLLBACK.";

    private ConcurrentDatabaseEngine concurrent;
    private final SessionContext[] sessions = new SessionContext[3];
    private final Connection[] connections = new Connection[3];

    @BeforeEach
    public void openThreeSessions() {
        for (int i = 0; i < 3; i++) {
            if (isLiveSnowflake()) {
                connections[i] = LiveSnowflake.open();
            } else {
                if (concurrent == null) {
                    concurrent = new ConcurrentDatabaseEngine();
                }
                sessions[i] = concurrent.createSession();
            }
        }
        if (!isLiveSnowflake()) {
            say(OTHER, "CREATE DATABASE IF NOT EXISTS test_db");
            say(OTHER, "CREATE SCHEMA IF NOT EXISTS test_db.test_schema");
        }
        for (int i = 0; i < 3; i++) {
            say(i, "USE SCHEMA test_db.test_schema");
        }
        say(OTHER, "CREATE OR REPLACE TABLE lt_t (id INT, v INT)");
        say(OTHER, "INSERT INTO lt_t VALUES (1, 1), (2, 2)");
        say(OTHER, "CREATE OR REPLACE TABLE lt_u (id INT, v INT)");
        say(OTHER, "INSERT INTO lt_u VALUES (1, 1)");
        say(WAITER, "ALTER SESSION SET LOCK_TIMEOUT = 120");
    }

    @AfterEach
    public void closeThreeSessions() throws SQLException {
        say(HOLDER, "ROLLBACK");
        say(WAITER, "ROLLBACK");
        say(WAITER, "ALTER SESSION UNSET LOCK_TIMEOUT");
        say(OTHER, "DROP TABLE IF EXISTS lt_t");
        say(OTHER, "DROP TABLE IF EXISTS lt_u");
        if (isLiveSnowflake()) {
            for (final Connection connection : connections) {
                connection.close();
            }
        } else {
            concurrent.shutdown();
        }
    }

    /** A statement's rows, each as its cells, or a single row holding "REFUSED " and the refusal. */
    private List<String[]> rows(final int side, final String sql) {
        final List<String[]> out = new ArrayList<String[]>();
        try {
            if (isLiveSnowflake()) {
                try (Statement statement = connections[side].createStatement()) {
                    if (!statement.execute(sql)) {
                        out.add(new String[] {"count " + statement.getUpdateCount()});
                        return out;
                    }
                    final java.sql.ResultSet result = statement.getResultSet();
                    final ResultSetMetaData columns = result.getMetaData();
                    while (result.next()) {
                        final String[] row = new String[columns.getColumnCount()];
                        for (int i = 0; i < row.length; i++) {
                            row[i] = result.getString(i + 1);
                        }
                        out.add(row);
                    }
                    return out;
                }
            }
            final dev.frostlake.storage.ResultSet result = concurrent.executeQuery(sql, sessions[side]);
            while (result.next()) {
                final String[] row = new String[result.getColumnCount()];
                for (int i = 0; i < row.length; i++) {
                    row[i] = String.valueOf(result.getValue(i));
                }
                out.add(row);
            }
        } catch (final SQLException | RuntimeException refused) {
            out.clear();
            out.add(new String[] {"REFUSED " + refused.getMessage()});
        }
        return out;
    }

    /** A statement's first cell, as it is. */
    private String cell(final int side, final String sql) {
        return rows(side, sql).get(0)[0];
    }

    /** A statement's rows as {@code a|b ;; c|d}, with the query and transaction IDs masked. */
    private String say(final int side, final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final String[] row : rows(side, sql)) {
            out.append(out.length() == 0 ? "" : " ;; ").append(String.join("|", row));
        }
        return out.toString().replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "<QID>")
            .replaceAll("\\b\\d{19}\\b", "<TXN>");
    }

    /** Starts the waiter's statement on a thread of its own; its answer lands in {@code answered}. */
    private Thread waiting(final String[] answered, final String sql) {
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                answered[0] = say(WAITER, sql);
            }
        });
        thread.start();
        return thread;
    }

    /** Whether SHOW LOCKS IN ACCOUNT lists a WAITING lock on LT_T, looked at for up to thirty seconds. */
    private boolean waitingSeen() throws InterruptedException {
        for (int attempt = 0; attempt < 120; attempt++) {
            for (final String[] row : rows(OTHER, "SHOW LOCKS IN ACCOUNT")) {
                if (String.join("|", row).contains("LT_T") && String.join("|", row).contains("WAITING")) {
                    return true;
                }
            }
            Thread.sleep(250L);
        }
        return false;
    }

    @Test
    public void anAbortOfTheWaitersTransactionEndsItsStatementAtOnce() throws InterruptedException {
        say(HOLDER, "BEGIN");
        say(HOLDER, "UPDATE lt_t SET v = 100 WHERE id = 1");
        say(WAITER, "BEGIN");
        say(WAITER, "UPDATE lt_u SET v = 5 WHERE id = 1");
        final String transaction = cell(WAITER, "SELECT CURRENT_TRANSACTION()");
        final String user = cell(OTHER, "SELECT CURRENT_USER()");
        final String[] answered = new String[1];
        final Thread thread = waiting(answered, "UPDATE lt_t SET v = 7 WHERE id = 1");
        assertTrue(waitingSeen(), "the waiter's UPDATE waits for the holder");
        assertEquals("Aborted transaction id: <TXN>",
            say(OTHER, "SELECT SYSTEM$ABORT_TRANSACTION(" + transaction + ")"));
        thread.join(LONG_WAIT_MILLIS);
        assertFalse(thread.isAlive(), "the waiting statement ended while the holder held on");
        assertEquals("REFUSED Transaction '<QID>', id '<TXN>', was aborted by user '" + user
            + "'. SQL   execution canceled.", answered[0]);
        say(HOLDER, "ROLLBACK");
        assertEquals("REFUSED " + IGNORED, say(WAITER, "SELECT 1"));
        assertTrue(say(WAITER, "COMMIT").startsWith("REFUSED COMMIT failed. Your transaction '<QID>', id '<TXN>', was"
            + " already aborted."));
        assertEquals("1", say(WAITER, "SELECT 1"));
        assertEquals("1|1", say(OTHER, "SELECT id, v FROM lt_u"));
        assertEquals("1|1 ;; 2|2", say(OTHER, "SELECT id, v FROM lt_t ORDER BY id"));
    }

    @Test
    public void anAutocommitStatementsTransactionIsNotAborted() throws InterruptedException {
        say(HOLDER, "BEGIN");
        say(HOLDER, "UPDATE lt_t SET v = 200 WHERE id = 1");
        final String session = cell(WAITER, "SELECT CURRENT_SESSION()");
        final String[] answered = new String[1];
        final Thread thread = waiting(answered, "UPDATE lt_t SET v = 9 WHERE id = 1");
        assertTrue(waitingSeen(), "the waiter's UPDATE waits for the holder");
        String statements = null;
        for (final String[] row : rows(OTHER, "SHOW TRANSACTIONS IN ACCOUNT")) {
            if (row.length > 2 && session.equals(row[2])) {
                statements = row[0];
            }
        }
        assertTrue(statements != null, "SHOW TRANSACTIONS lists the waiting statement's transaction");
        assertEquals("Could not abort txn: <TXN>",
            say(OTHER, "SELECT SYSTEM$ABORT_TRANSACTION(" + statements + ")"));
        say(HOLDER, "ROLLBACK");
        thread.join(LONG_WAIT_MILLIS);
        assertFalse(thread.isAlive(), "the waiting statement ran once the holder let go");
        assertEquals("1|9 ;; 2|2", say(OTHER, "SELECT id, v FROM lt_t ORDER BY id"));
        assertEquals("1", say(WAITER, "SELECT 1"));
    }
}
