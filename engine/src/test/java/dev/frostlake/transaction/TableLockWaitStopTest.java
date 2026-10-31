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

import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.http.SessionContext;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A statement waiting for another session's table lock ends as a running statement does: at its session's
 * STATEMENT_TIMEOUT_IN_SECONDS, at SYSTEM$CANCEL_QUERY, when its session is aborted, and when the engine shuts down —
 * not once the holder lets go, and an aborted waiter writes nothing. An aborted session's transaction is rolled
 * back, its locks released, even when the request that is waiting opened it.
 */
public class TableLockWaitStopTest {

    private static final long LONG_WAIT_MILLIS = 30000L;

    private ConcurrentDatabaseEngine engine;
    private SessionContext holder;
    private SessionContext waiter;
    private SessionContext other;

    @BeforeEach
    public void setUp() {
        engine = new ConcurrentDatabaseEngine();
        holder = engine.createSession();
        waiter = engine.createSession();
        other = engine.createSession();
        say(holder, "CREATE DATABASE d");
        say(holder, "CREATE TABLE d.PUBLIC.t (id INT, v INT)");
        say(holder, "INSERT INTO d.PUBLIC.t VALUES (1, 1), (2, 2)");
        for (final SessionContext session : new SessionContext[] {holder, waiter, other}) {
            say(session, "USE SCHEMA d.PUBLIC");
        }
        say(holder, "BEGIN");
        say(holder, "UPDATE t SET v = 100 WHERE id = 1");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** A statement's answer on one line: rows as "a|b ;; c|d", or "REFUSED " and the refusal. */
    private String say(final SessionContext session, final String sql) {
        try {
            final StringBuilder out = new StringBuilder();
            for (final Row row : engine.executeQuery(sql, session).getRows()) {
                out.append(out.length() == 0 ? "" : " ;; ");
                for (int i = 0; i < row.getValues().size(); i++) {
                    out.append(i == 0 ? "" : "|").append(row.getValue(i));
                }
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return "REFUSED " + refused.getMessage();
        }
    }

    /** Starts the waiter's UPDATE of the held row on a thread of its own; its answer lands in {@code answered}. */
    private Thread waiting(final String[] answered) {
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                answered[0] = say(waiter, "UPDATE t SET v = v + 10 WHERE id = 1");
            }
        });
        thread.start();
        return thread;
    }

    /** The query ID of the waiter's running UPDATE, once the history lists it. */
    private String runningUpdate() throws InterruptedException {
        return running("UPDATE t SET v = v + 10%");
    }

    /** The query ID of a running statement whose text is like {@code pattern}, once the history lists it. */
    private String running(final String pattern) throws InterruptedException {
        for (int attempt = 0; attempt < 300; attempt++) {
            final String id = say(other, "SELECT query_id FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY()) WHERE "
                + "execution_status = 'RUNNING' AND query_text LIKE '" + pattern + "'");
            if (!id.isEmpty() && !id.startsWith("REFUSED")) {
                return id;
            }
            Thread.sleep(20L);
        }
        return null;
    }

    @Test
    public void theStatementTimeoutEndsTheWait() throws InterruptedException {
        say(waiter, "ALTER SESSION SET STATEMENT_TIMEOUT_IN_SECONDS = 1");
        final String[] answered = new String[1];
        final Thread thread = waiting(answered);
        thread.join(LONG_WAIT_MILLIS);
        assertEquals("REFUSED Statement reached its statement or warehouse timeout of 1 second(s) and was canceled.",
            answered[0]);
        say(holder, "COMMIT");
        assertEquals("1|100 ;; 2|2", say(other, "SELECT id, v FROM t ORDER BY id"));
    }

    @Test
    public void aCancelEndsTheWait() throws InterruptedException {
        final String[] answered = new String[1];
        final Thread thread = waiting(answered);
        final String id = runningUpdate();
        assertTrue(id != null, "the waiting UPDATE is running");
        assertEquals("query [" + id + "] terminated.", say(other, "SELECT SYSTEM$CANCEL_QUERY('" + id + "')"));
        thread.join(LONG_WAIT_MILLIS);
        assertEquals("REFUSED SQL execution canceled", answered[0]);
        say(holder, "COMMIT");
        assertEquals("1|100 ;; 2|2", say(other, "SELECT id, v FROM t ORDER BY id"));
    }

    @Test
    public void anAbortEndsTheWaitAndWritesNothing() throws InterruptedException {
        final String session = say(waiter, "SELECT CURRENT_SESSION()");
        final String[] answered = new String[1];
        final Thread thread = waiting(answered);
        assertTrue(runningUpdate() != null, "the waiting UPDATE is running");
        assertEquals("session [" + session + "] terminated.",
            say(other, "SELECT SYSTEM$ABORT_SESSION(" + session + ")"));
        thread.join(LONG_WAIT_MILLIS);
        assertEquals("REFUSED SQL execution canceled", answered[0]);
        say(holder, "COMMIT");
        assertEquals("1|100 ;; 2|2", say(other, "SELECT id, v FROM t ORDER BY id"));
    }

    @Test
    public void aShutdownEndsTheWait() throws InterruptedException {
        final String[] answered = new String[1];
        final Thread thread = waiting(answered);
        assertTrue(runningUpdate() != null, "the waiting UPDATE is running");
        engine.shutdown();
        thread.join(LONG_WAIT_MILLIS);
        assertTrue(!thread.isAlive(), "the waiter ended");
        assertTrue(answered[0] != null && answered[0].startsWith("REFUSED"), String.valueOf(answered[0]));
    }
    @Test
    public void anAbortedRequestLeavesNoTransactionBehind() throws InterruptedException {
        say(other, "CREATE TABLE u (id INT, v INT)");
        say(other, "INSERT INTO u VALUES (1, 1)");
        final String session = say(waiter, "SELECT CURRENT_SESSION()");
        final String[] answered = new String[1];
        // One request opens a transaction, writes u, then waits for the holder's lock on t.
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                answered[0] = say(waiter, "BEGIN TRANSACTION; UPDATE u SET v = 5 WHERE id = 1; "
                    + "UPDATE t SET v = 7 WHERE id = 1; COMMIT");
            }
        });
        thread.start();
        assertTrue(running("%UPDATE t SET v = 7%") != null, "the waiting UPDATE is running");
        assertEquals("session [" + session + "] terminated.",
            say(other, "SELECT SYSTEM$ABORT_SESSION(" + session + ")"));
        thread.join(LONG_WAIT_MILLIS);
        assertTrue(answered[0] != null && answered[0].startsWith("REFUSED"), String.valueOf(answered[0]));
        say(holder, "COMMIT");
        // The aborted session's transaction is rolled back with it: u is free and holds what it held.
        say(other, "ALTER SESSION SET LOCK_TIMEOUT = 1");
        assertEquals("1|1", say(other, "SELECT id, v FROM u"));
        assertEquals("1|0", say(other, "UPDATE u SET v = 9 WHERE id = 1"));
        assertEquals("", say(other, "SHOW LOCKS"));
        assertEquals("1|100 ;; 2|2", say(other, "SELECT id, v FROM t ORDER BY id"));
    }
}
