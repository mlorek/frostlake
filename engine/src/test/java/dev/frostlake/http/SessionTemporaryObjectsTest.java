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

package dev.frostlake.http;

import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.ExecutionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A session of the concurrent engine takes what it created TEMPORARY with it when it ends - released, expired
 * after sitting idle, or aborted from another session: its tables, views, functions, procedures and stages are
 * dropped, the names are free for the sessions after it, and a permanent table a temporary one hid is there
 * again. What other sessions created, temporary or not, stays.
 */
public class SessionTemporaryObjectsTest {

    private ConcurrentDatabaseEngine engine;
    private SessionContext first;
    private SessionContext second;

    @BeforeEach
    public void startEngine() {
        engine = new ConcurrentDatabaseEngine();
        first = engine.createSession();
        second = engine.createSession();
        run(first, "CREATE DATABASE tmp_sessions");
        run(first, "USE SCHEMA tmp_sessions.public");
        run(second, "USE SCHEMA tmp_sessions.public");
    }

    @AfterEach
    public void stopEngine() {
        engine.shutdown();
    }

    /** A statement's first cell, or its refusal on one line. */
    private String answer(final SessionContext session, final String sql) {
        final ExecutionResult result;
        try {
            result = engine.execute(sql, session);
        } catch (final RuntimeException refused) {
            return "REFUSED " + String.valueOf(refused.getMessage()).replace("\n", " | ");
        }
        if (!result.isSuccess()) {
            return "REFUSED " + String.valueOf(result.getErrorMessage()).replace("\n", " | ");
        }
        if (result.getResultSets().isEmpty() || result.getResultSets().get(0).getRows().isEmpty()) {
            return "";
        }
        return String.valueOf(result.getResultSets().get(0).getRows().get(0).getValue(0));
    }

    private void run(final SessionContext session, final String sql) {
        final String answered = answer(session, sql);
        assertTrue(!answered.startsWith("REFUSED"), sql + " ==> " + answered);
    }

    private void createTemporaryObjects(final SessionContext session) {
        run(session, "CREATE TEMPORARY FUNCTION tmpf() RETURNS NUMBER AS '1'");
        run(session, "CREATE TEMPORARY TABLE tmpt (x NUMBER)");
        run(session, "INSERT INTO tmpt VALUES (7)");
        run(session, "CREATE TEMPORARY VIEW tmpv AS SELECT 5 AS c");
        run(session, "CREATE TEMPORARY PROCEDURE tmpp() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 9; END; $$");
        run(session, "CREATE TEMPORARY STAGE tmps");
    }

    private void assertTemporaryObjectsGone(final SessionContext session) {
        assertEquals("REFUSED SQL compilation error: | Unknown function TMPF.", answer(session, "SELECT tmpf()"));
        assertTrue(answer(session, "SELECT COUNT(*) FROM tmpt")
            .startsWith("REFUSED SQL compilation error: | Object 'TMPT' does not exist or not authorized."));
        assertTrue(answer(session, "SELECT c FROM tmpv")
            .startsWith("REFUSED SQL compilation error: | Object 'TMPV' does not exist or not authorized."));
        assertEquals("REFUSED SQL compilation error: | Unknown function TMPP.", answer(session, "CALL tmpp()"));
        assertEquals("", answer(session, "SHOW STAGES LIKE 'TMPS' IN SCHEMA tmp_sessions.public"));
    }

    @Test
    public void aReleasedSessionTakesItsTemporaryObjects() {
        createTemporaryObjects(first);
        assertEquals("1", answer(first, "SELECT tmpf()"));
        assertEquals("7", answer(first, "SELECT x FROM tmpt"));
        engine.endSession(first);
        assertTemporaryObjectsGone(second);
        // The names are free again: the next session creates its own.
        createTemporaryObjects(second);
        assertEquals("9", answer(second, "CALL tmpp()"));
    }

    @Test
    public void anIdleSessionThatExpiresTakesThem() {
        createTemporaryObjects(first);
        // Every session has sat idle for longer than no time at all as soon as the clock moves on.
        while (engine.getActiveSessionCount() > 0) {
            engine.expireIdleSessions(0);
        }
        final SessionContext later = engine.createSession();
        run(later, "USE SCHEMA tmp_sessions.public");
        assertTemporaryObjectsGone(later);
    }

    @Test
    public void anAbortedSessionTakesThem() {
        createTemporaryObjects(first);
        final String number = answer(first, "SELECT CURRENT_SESSION()");
        assertEquals("session [" + number + "] terminated.",
            answer(second, "SELECT SYSTEM$ABORT_SESSION(" + number + ")"));
        assertTemporaryObjectsGone(second);
    }

    @Test
    public void whatOtherSessionsCreatedStays() {
        createTemporaryObjects(first);
        run(second, "CREATE TEMPORARY TABLE mine (y NUMBER)");
        run(second, "INSERT INTO mine VALUES (3)");
        run(first, "CREATE TABLE kept (z NUMBER)");
        run(first, "INSERT INTO kept VALUES (4)");
        run(first, "CREATE FUNCTION keptf() RETURNS NUMBER AS '6'");
        engine.endSession(first);
        assertTemporaryObjectsGone(second);
        assertEquals("3", answer(second, "SELECT y FROM mine"));
        assertEquals("4", answer(second, "SELECT z FROM kept"));
        assertEquals("6", answer(second, "SELECT keptf()"));
        final SessionContext third = engine.createSession();
        run(third, "USE SCHEMA tmp_sessions.public");
        engine.endSession(second);
        assertTrue(answer(third, "SELECT y FROM mine")
            .startsWith("REFUSED SQL compilation error: | Object 'MINE' does not exist or not authorized."));
        assertEquals("4", answer(third, "SELECT z FROM kept"));
    }

    @Test
    public void aPermanentTableATemporaryOneHidIsThereAgain() {
        run(second, "CREATE TABLE both_t (x NUMBER)");
        run(second, "INSERT INTO both_t VALUES (1), (2)");
        run(first, "CREATE TEMPORARY TABLE both_t (x NUMBER)");
        assertEquals("0", answer(first, "SELECT COUNT(*) FROM both_t"));
        engine.endSession(first);
        assertEquals("2", answer(second, "SELECT COUNT(*) FROM both_t"));
    }

    @Test
    public void anOpenTransactionIsRolledBackBeforeTheObjectsGo() {
        run(second, "CREATE TABLE written (x NUMBER)");
        run(first, "CREATE TEMPORARY TABLE scratch (x NUMBER)");
        run(first, "BEGIN");
        run(first, "INSERT INTO scratch VALUES (1)");
        run(first, "INSERT INTO written VALUES (1)");
        engine.endSession(first);
        assertEquals("0", answer(second, "SELECT COUNT(*) FROM written"));
        assertTrue(answer(second, "SELECT COUNT(*) FROM scratch")
            .startsWith("REFUSED SQL compilation error: | Object 'SCRATCH' does not exist or not authorized."));
    }
}
