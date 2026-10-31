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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A procedure that waits for another session's table lock gives the engine up meanwhile, and the statements other
 * sessions run then are their own: a block in another session answers its own RETURN and leaves the waiting procedure
 * to run on to its own, and another session's top-level SET, USE and SYSTEM$ABORT_TRANSACTION are judged as top-level
 * statements, not as statements of the waiting owner's-rights procedure. Sessions are independent (Snowflake); held
 * by the embedded engine, whose sessions share one executor.
 */
public class TableLockWaitProceduralStateTest {

    private static final long LONG_WAIT_MILLIS = 60000L;

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
        for (final SessionContext session : new SessionContext[] {holder, waiter, other}) {
            say(session, "USE SCHEMA d.PUBLIC");
        }
        say(holder, "CREATE TABLE t (id INT, v INT)");
        say(holder, "INSERT INTO t VALUES (1, 0)");
        say(holder, "CREATE TABLE lg (m VARCHAR)");
        say(waiter, "ALTER SESSION SET LOCK_TIMEOUT = 60");
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

    /** Runs a CALL in the waiting session on a thread of its own, its answer kept in {@code answer[0]}. */
    private Thread callApart(final String call, final String[] answer) {
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                answer[0] = say(waiter, call);
            }
        });
        thread.start();
        return thread;
    }

    /** Whether the waiting session's statement waits for a lock, looked at for up to thirty seconds. */
    private boolean waiting() throws InterruptedException {
        for (int attempt = 0; attempt < 1500; attempt++) {
            if (say(other, "SHOW LOCKS IN ACCOUNT").contains("WAITING")) {
                return true;
            }
            Thread.sleep(20L);
        }
        return false;
    }

    @Test
    public void aBlockInAnotherSessionDoesNotEndAProcedureThatWaits() throws InterruptedException {
        say(holder, "CREATE PROCEDURE up(n INT) RETURNS VARCHAR LANGUAGE SQL AS $$\n"
            + "DECLARE x INT DEFAULT 7;\n"
            + "BEGIN\n"
            + "  UPDATE t SET v = :n;\n"
            + "  INSERT INTO lg VALUES ('after update x=' || :x);\n"
            + "  RETURN 'done x=' || :x;\n"
            + "END\n$$");
        say(holder, "BEGIN");
        say(holder, "UPDATE t SET v = 1");
        final String[] answer = new String[1];
        final Thread call = callApart("CALL up(3)", answer);
        assertTrue(waiting(), "the procedure waits for the holder's lock");
        assertEquals("C block x=99",
            say(other, "EXECUTE IMMEDIATE $$DECLARE x INT DEFAULT 99; BEGIN RETURN 'C block x=' || :x; END$$"));
        assertEquals("42", say(other, "BEGIN LET x INT := 42; RETURN x; END"));
        assertTrue(call.isAlive(), "another session's RETURN does not end the waiting procedure: " + answer[0]);
        say(holder, "COMMIT");
        call.join(LONG_WAIT_MILLIS);
        assertFalse(call.isAlive(), "the procedure ran on once the lock was free");
        assertEquals("done x=7", answer[0]);
        assertEquals("after update x=7", say(other, "SELECT m FROM lg"));
        assertEquals("1|3", say(other, "SELECT id, v FROM t"));
    }

    @Test
    public void anotherSessionsTopLevelStatementsAreNotJudgedInTheWaitingOwnersRightsProcedure()
            throws InterruptedException {
        say(holder, "CREATE PROCEDURE up() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS OWNER AS "
            + "'BEGIN UPDATE t SET v = 3; SET z = 1; RETURN ''done''; "
            + "EXCEPTION WHEN OTHER THEN RETURN SQLERRM; END'");
        say(holder, "BEGIN");
        say(holder, "UPDATE t SET v = 1");
        final String[] answer = new String[1];
        final Thread call = callApart("CALL up()", answer);
        assertTrue(waiting(), "the procedure waits for the holder's lock");
        assertEquals("Statement executed successfully.", say(other, "SET sv = 1"));
        assertEquals("Statement executed successfully.", say(other, "USE SCHEMA PUBLIC"));
        assertEquals("1", say(other, "SELECT $sv"));
        assertFalse(say(other, "SELECT SYSTEM$ABORT_TRANSACTION(1)").startsWith("REFUSED"),
            "a top-level SYSTEM$ABORT_TRANSACTION is no statement of a procedure");
        // The waiting procedure is still judged as one: its owner's rights hold after the wait.
        say(holder, "COMMIT");
        call.join(LONG_WAIT_MILLIS);
        assertEquals("Stored procedure execution error: Unsupported statement type 'SET'.", answer[0]);
        assertEquals("1|3", say(other, "SELECT id, v FROM t"));
    }
}
