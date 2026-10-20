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
package dev.frostlake.session;

import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.http.SessionContext;
import dev.frostlake.storage.Row;
import dev.frostlake.transaction.Transaction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SYSTEM$ABORT_SESSION of a session whose request is running is carried out as the aborting request is read: the
 * request running ends at its statement's next checkpoint, fails, and its open transaction is rolled back, rather
 * than running on to its end and committing while the abort waits for it; the abort answers as soon as it may run.
 * Engine only: SYSTEM$ABORT_SESSION is not run on the live account.
 */
public class AbortSessionRunningRequestTest {

    private static final long LONG_WAIT_MILLIS = 60000L;

    private ConcurrentDatabaseEngine engine;
    private SessionContext waiter;
    private SessionContext other;

    @BeforeEach
    public void setUp() {
        engine = new ConcurrentDatabaseEngine();
        waiter = engine.createSession();
        other = engine.createSession();
        say(other, "CREATE DATABASE d");
        say(other, "CREATE TABLE d.PUBLIC.u (id INT, v INT)");
        say(other, "INSERT INTO d.PUBLIC.u VALUES (1, 1)");
        say(waiter, "USE SCHEMA d.PUBLIC");
        say(other, "USE SCHEMA d.PUBLIC");
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

    /** Whether the session of that number holds an explicit transaction, looked at for up to thirty seconds. */
    private boolean transactionOpen(final long session) throws InterruptedException {
        for (int attempt = 0; attempt < 1500; attempt++) {
            for (final Transaction open : engine.getEngine().getTransactionManager().getActiveTransactions()) {
                if (open.isExplicit() && open.getSessionNumber() != null && open.getSessionNumber() == session) {
                    return true;
                }
            }
            Thread.sleep(20L);
        }
        return false;
    }

    @Test
    public void anAbortEndsTheRunningRequestAndRollsItBack() throws InterruptedException {
        final String session = say(waiter, "SELECT CURRENT_SESSION()");
        final String[] answered = new String[1];
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                answered[0] = say(waiter, "BEGIN TRANSACTION; UPDATE u SET v = 5 WHERE id = 1; "
                    + "SELECT SYSTEM$WAIT(25); COMMIT");
            }
        });
        thread.start();
        assertTrue(transactionOpen(Long.parseLong(session)), "the request opened its transaction");
        assertEquals("session [" + session + "] terminated.",
            say(other, "SELECT SYSTEM$ABORT_SESSION(" + session + ")"));
        thread.join(LONG_WAIT_MILLIS);
        assertFalse(thread.isAlive(), "the request ended");
        assertTrue(answered[0].startsWith("REFUSED") && answered[0].contains("SQL execution canceled"), answered[0]);
        // The request's transaction is rolled back with its session: u holds what it held, and is free.
        say(other, "ALTER SESSION SET LOCK_TIMEOUT = 1");
        assertEquals("1|1", say(other, "SELECT id, v FROM u"));
        assertEquals("1|0", say(other, "UPDATE u SET v = 9 WHERE id = 1"));
    }

    @Test
    public void anAbortWrittenOtherwiseStillEndsTheSessionAfterItsRequest() {
        final String session = say(waiter, "SELECT CURRENT_SESSION()");
        say(waiter, "BEGIN");
        say(waiter, "UPDATE u SET v = 5 WHERE id = 1");
        say(other, "SET s = " + session);
        assertEquals("session [" + session + "] terminated.", say(other, "SELECT SYSTEM$ABORT_SESSION($s)"));
        assertEquals("1|1", say(other, "SELECT id, v FROM u"));
    }
}
