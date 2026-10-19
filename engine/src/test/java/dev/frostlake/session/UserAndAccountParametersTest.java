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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A session parameter can be set on a USER and on the ACCOUNT as well as on the session, and is inherited downwards:
 * a session runs with its own setting, else its user's, else the account's, else the default, and SHOW PARAMETERS
 * names the level the value in force came from. ALTER USER takes the parameters among the user's properties, with
 * or without commas, and written without a name alters the session's own user; ALTER ACCOUNT takes every parameter
 * the account lists. A list is judged whole, so nothing of it applies when a name or a value is refused.
 *
 * <p>The statements here change a user or the account, or would if a refusal ever gave way, so the class runs on an
 * engine of its own and is never sent to an account: what a user's listing reads after a SET, and each refusal's
 * sentence, were measured there once.
 */
public class UserAndAccountParametersTest {

    /** Refusals measured on an account, on statements that name the account or the session's own user. */
    private static final String[][] REFUSED = {
        {"ALTER USER SET QUERY_TAG = 5",
            "REFUSED SQL compilation error: | invalid value [5] for parameter 'QUERY_TAG'"},
        {"ALTER USER SET QUERY_TAG = 5, LOCK_TIMEOUT = 'x'",
            "REFUSED SQL compilation error: | invalid value ['x'] for parameter 'LOCK_TIMEOUT'"},
        {"ALTER USER SET LOCK_TIMEOUT = 'x' QUERY_TAG = 5",
            "REFUSED SQL compilation error: | invalid value ['x'] for parameter 'LOCK_TIMEOUT'"},
        {"ALTER USER SET NOSUCH = 5, QUERY_TAG = 5",
            "REFUSED SQL compilation error: | invalid property 'NOSUCH' for 'USER'"},
        {"ALTER USER SET QUERY_TAG = 5, NOSUCH = 5",
            "REFUSED SQL compilation error: | invalid property 'NOSUCH' for 'USER'"},
        {"ALTER USER IF EXISTS SET QUERY_TAG = 5",
            "REFUSED SQL compilation error: | Cannot specify IF EXISTS when no object name is specified"},
        {"ALTER ACCOUNT SET QUERY_TAG = 5",
            "REFUSED SQL compilation error: | invalid value [5] for parameter 'QUERY_TAG'"},
        {"ALTER ACCOUNT SET QUERY_TAG",
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'QUERY_TAG'."},
        {"ALTER ACCOUNT SET QUERY_TAG =",
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'QUERY_TAG'."},
        {"ALTER ACCOUNT SET AUTOCOMMIT = 1",
            "REFUSED SQL compilation error: | invalid value [1] for parameter 'AUTOCOMMIT'"},
        {"ALTER ACCOUNT SET JSON_INDENT = 'x', QUERY_TAG = 5",
            "REFUSED SQL compilation error: | invalid value ['x'] for parameter 'JSON_INDENT'"},
        {"ALTER ACCOUNT SET JSON_INDENT = 'x' QUERY_TAG = 5",
            "REFUSED SQL compilation error: | invalid value ['x'] for parameter 'JSON_INDENT'"},
        {"ALTER ACCOUNT SET STATEMENT_TIMEOUT_IN_SECONDS = -1",
            "REFUSED SQL compilation error:  | parameter value out of range: -1. Must be between 0 and 604,800."},
        {"ALTER ACCOUNT SET QUERY_TAG = 5, QUERY_TAG = 5",
            "REFUSED SQL compilation error: | duplicate property 'QUERY_TAG';"},
        {"ALTER ACCOUNT SET NOSUCH = 5, QUERY_TAG = 5",
            "REFUSED SQL compilation error: | invalid property 'NOSUCH' for 'ACCOUNT'"},
        {"ALTER ACCOUNT SET QUERY_TAG = 5, NOSUCH = 5",
            "REFUSED SQL compilation error: | invalid property 'NOSUCH' for 'ACCOUNT'"},
        {"ALTER ACCOUNT SET \"QUERY_TAG\" = 5",
            "REFUSED SQL compilation error: | invalid property '\"QUERY_TAG\"' for 'ACCOUNT'"},
        {"ALTER ACCOUNT SET \"query_tag\" = 5",
            "REFUSED SQL compilation error: | invalid property '\"query_tag\"' for 'ACCOUNT'"},
        {"ALTER ACCOUNT SET query_tag = 5",
            "REFUSED SQL compilation error: | invalid value [5] for parameter 'QUERY_TAG'"},
        {"ALTER ACCOUNT SET DATA_RETENTION_TIME_IN_DAYS = 'x'",
            "REFUSED SQL compilation error: | invalid value ['x'] for parameter 'DATA_RETENTION_TIME_IN_DAYS'"},
        {"ALTER ACCOUNT SET PREVENT_UNLOAD_TO_INLINE_URL = 'x'",
            "REFUSED SQL compilation error: | invalid value ['x'] for parameter 'PREVENT_UNLOAD_TO_INLINE_URL'"},
        {"ALTER ACCOUNT SET LOCK_TIMEOUT = 'x'",
            "REFUSED SQL compilation error: | invalid value ['x'] for parameter 'LOCK_TIMEOUT'"},
        {"ALTER ACCOUNT SET QUERY_TAG = 5 NOSUCH",
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'QUERY_TAG'."},
        {"ALTER ACCOUNT IF EXISTS SET QUERY_TAG = 5",
            "REFUSED SQL compilation error: | Cannot specify IF EXISTS when no object name is specified"},
        {"ALTER USER SET QUERY_TAG = 5 NOSUCH",
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'SET'."},
        {"ALTER ACCOUNT SET QUERY_TAG = 5,",
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'QUERY_TAG'."},
        {"ALTER ACCOUNT SET , QUERY_TAG = 5",
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ','."},
    };

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE USER pu");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** A statement's answer: its rows, cells joined by a bar and rows by {@code " ;; "}, or its refusal on one line. */
    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder out = new StringBuilder();
            boolean firstRow = true;
            while (rs.next()) {
                if (!firstRow) {
                    out.append(" ;; ");
                }
                firstRow = false;
                for (int c = 0; c < rs.getColumnCount(); c++) {
                    out.append(c == 0 ? "" : "|").append(String.valueOf(rs.getValue(c)).replace("\n", " | "));
                }
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return "REFUSED " + String.valueOf(refused.getMessage()).replace("\n", " | ");
        }
    }

    /** The value, default and level cells of a parameter's row in a listing. */
    private String row(final String listing) {
        final ResultSet rs = engine.executeQuery(listing);
        assertTrue(rs.next(), listing);
        return rs.getValue(1) + ", " + rs.getValue(2) + ", " + rs.getValue(3);
    }

    @Test
    public void whatAnAccountRefusesOfItsOwnAndItsSessionUsersLists() {
        for (final String[] cell : REFUSED) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
        assertEquals(", , ", row("SHOW PARAMETERS LIKE 'QUERY_TAG' IN ACCOUNT"), "nothing of a refused list applied");
        assertEquals("43200, 43200, ", row("SHOW PARAMETERS LIKE 'LOCK_TIMEOUT' IN USER"));
    }

    @Test
    public void aUserCarriesTheParametersSetOnIt() {
        engine.execute("ALTER USER pu SET QUERY_TAG = 'x'");
        assertEquals("x, , USER", row("SHOW PARAMETERS LIKE 'QUERY_TAG' IN USER pu"));
        engine.execute("ALTER USER pu SET LOCK_TIMEOUT = -1");
        assertEquals("-1, 43200, USER", row("SHOW PARAMETERS LIKE 'LOCK_TIMEOUT' IN USER pu"));
        // Several in one list, with a comma between them or without, and among the user's properties.
        engine.execute("ALTER USER pu SET LOCK_TIMEOUT = 5 QUERY_TAG = 't2'");
        engine.execute("ALTER USER pu SET LOCK_TIMEOUT = 6, QUERY_TAG = 't3'");
        engine.execute("ALTER USER pu SET COMMENT = 'c' QUERY_TAG = 't4'");
        engine.execute("ALTER USER pu SET AUTOCOMMIT = FALSE COMMENT = 'c2'");
        assertEquals("6, 43200, USER", row("SHOW PARAMETERS LIKE 'LOCK_TIMEOUT' IN USER pu"));
        assertEquals("t4, , USER", row("SHOW PARAMETERS LIKE 'QUERY_TAG' IN USER pu"));
        assertEquals("false, true, USER", row("SHOW PARAMETERS LIKE 'AUTOCOMMIT' IN USER pu"));
        assertEquals("c2", engine.executeQuery("SHOW USERS LIKE 'PU'").getRows().get(0).getValue(9));
        // A word is text, upper case as a name is.
        engine.execute("ALTER USER pu SET QUERY_TAG = abc");
        assertEquals("ABC, , USER", row("SHOW PARAMETERS LIKE 'QUERY_TAG' IN USER pu"));
    }

    @Test
    public void aRefusedListAppliesNothing() {
        engine.execute("ALTER USER pu SET LOCK_TIMEOUT = 5 COMMENT = 'kept'");
        assertEquals("REFUSED SQL compilation error: | invalid value [5] for parameter 'QUERY_TAG'",
            answer("ALTER USER pu SET LOCK_TIMEOUT = 7, QUERY_TAG = 5"));
        assertEquals("REFUSED SQL compilation error: | invalid value [5] for parameter 'QUERY_TAG'",
            answer("ALTER USER pu SET COMMENT = 'c3' QUERY_TAG = 5"));
        assertEquals("REFUSED SQL compilation error: | invalid property 'NOSUCH' for 'USER'",
            answer("ALTER USER pu SET LOCK_TIMEOUT = 8 NOSUCH = 1"));
        assertEquals("5, 43200, USER", row("SHOW PARAMETERS LIKE 'LOCK_TIMEOUT' IN USER pu"));
        assertEquals("kept", engine.executeQuery("SHOW USERS LIKE 'PU'").getRows().get(0).getValue(9));
    }

    @Test
    public void aValueItsParameterDoesNotTakeIsRefusedOnceTheUserIsFound() {
        assertEquals("REFUSED SQL compilation error:  | parameter value out of range: -1. Must be between 0 and"
            + " 604,800.", answer("ALTER USER pu SET STATEMENT_TIMEOUT_IN_SECONDS = -1"));
        assertEquals("REFUSED SQL compilation error: | invalid value [Nowhere/Land] for parameter 'TIMEZONE'",
            answer("ALTER USER pu SET TIMEZONE = 'Nowhere/Land'"));
        // A user that is not there is not judged that far: IF EXISTS forgives its absence.
        assertEquals("Statement executed successfully.",
            answer("ALTER USER IF EXISTS nosuch_user SET STATEMENT_TIMEOUT_IN_SECONDS = -1"));
        assertTrue(answer("ALTER USER nosuch_user SET TIMEZONE = 'Nowhere/Land'")
            .startsWith("REFUSED SQL compilation error: | User 'NOSUCH_USER' does not exist or not authorized."));
    }

    @Test
    public void unsetAndNullPutAParameterBackOnWhatTheUserInherits() {
        engine.execute("ALTER USER pu SET QUERY_TAG = 'x', LOCK_TIMEOUT = 5, AUTOCOMMIT = FALSE");
        engine.execute("ALTER USER pu UNSET LOCK_TIMEOUT, AUTOCOMMIT");
        assertEquals("43200, 43200, ", row("SHOW PARAMETERS LIKE 'LOCK_TIMEOUT' IN USER pu"));
        assertEquals("true, true, ", row("SHOW PARAMETERS LIKE 'AUTOCOMMIT' IN USER pu"));
        assertEquals("x, , USER", row("SHOW PARAMETERS LIKE 'QUERY_TAG' IN USER pu"));
        engine.execute("ALTER USER pu SET QUERY_TAG = NULL");
        assertEquals(", , ", row("SHOW PARAMETERS LIKE 'QUERY_TAG' IN USER pu"));
        // A property and a parameter in one UNSET.
        engine.execute("ALTER USER pu SET COMMENT = 'c' QUERY_TAG = 'y'");
        engine.execute("ALTER USER pu UNSET COMMENT, QUERY_TAG");
        assertEquals(", , ", row("SHOW PARAMETERS LIKE 'QUERY_TAG' IN USER pu"));
        assertEquals("", String.valueOf(engine.executeQuery("SHOW USERS LIKE 'PU'").getRows().get(0).getValue(9)));
    }

    @Test
    public void aTextPropertyTakesAWordAndNull() {
        engine.execute("ALTER USER pu SET COMMENT = abc FIRST_NAME = given");
        assertEquals("ABC", engine.executeQuery("SHOW USERS LIKE 'PU'").getRows().get(0).getValue(9));
        assertEquals("GIVEN", engine.executeQuery("SHOW USERS LIKE 'PU'").getRows().get(0).getValue(4));
        engine.execute("ALTER USER pu SET COMMENT = NULL");
        assertEquals("", String.valueOf(engine.executeQuery("SHOW USERS LIKE 'PU'").getRows().get(0).getValue(9)));
    }

    @Test
    public void aSessionRunsWithItsOwnThenItsUsersThenTheAccounts() {
        engine.execute("ALTER ACCOUNT SET QUERY_TAG = 'acct'");
        assertEquals("acct, , ACCOUNT", row("SHOW PARAMETERS LIKE 'QUERY_TAG'"));
        assertEquals("acct, , ACCOUNT", row("SHOW PARAMETERS LIKE 'QUERY_TAG' IN ACCOUNT"));
        assertEquals("acct, , ACCOUNT", row("SHOW PARAMETERS LIKE 'QUERY_TAG' IN USER pu"));
        // Written without a name, ALTER USER alters the session's own user.
        engine.execute("ALTER USER SET QUERY_TAG = 'mine'");
        assertEquals("mine, , USER", row("SHOW PARAMETERS LIKE 'QUERY_TAG'"));
        assertEquals("mine, , USER", row("SHOW PARAMETERS LIKE 'QUERY_TAG' IN USER"));
        assertEquals("acct, , ACCOUNT", row("SHOW PARAMETERS LIKE 'QUERY_TAG' IN USER pu"), "another user's is not");
        engine.execute("ALTER SESSION SET QUERY_TAG = 'sess'");
        assertEquals("sess, , SESSION", row("SHOW PARAMETERS LIKE 'QUERY_TAG'"));
        assertEquals("acct, , ACCOUNT", row("SHOW PARAMETERS LIKE 'QUERY_TAG' IN ACCOUNT"));
        engine.execute("ALTER SESSION UNSET QUERY_TAG");
        engine.execute("ALTER USER UNSET QUERY_TAG");
        assertEquals("acct, , ACCOUNT", row("SHOW PARAMETERS LIKE 'QUERY_TAG'"));
        engine.execute("ALTER ACCOUNT UNSET QUERY_TAG");
        assertEquals(", , ", row("SHOW PARAMETERS LIKE 'QUERY_TAG'"));
    }

    @Test
    public void anInheritedParameterIsInForce() {
        engine.execute("ALTER ACCOUNT SET TIMEZONE = 'Asia/Tokyo'");
        assertEquals("Asia/Tokyo, America/Los_Angeles, ACCOUNT", row("SHOW PARAMETERS LIKE 'TIMEZONE'"));
        assertEquals("+09:00", answer("SELECT TO_CHAR(CURRENT_TIMESTAMP(), 'TZH:TZM')"));
        engine.execute("ALTER USER SET TIMEZONE = 'Asia/Kolkata'");
        assertEquals("+05:30", answer("SELECT TO_CHAR(CURRENT_TIMESTAMP(), 'TZH:TZM')"));
        engine.execute("ALTER SESSION SET TIMEZONE = 'Asia/Dubai'");
        assertEquals("+04:00", answer("SELECT TO_CHAR(CURRENT_TIMESTAMP(), 'TZH:TZM')"));
        engine.execute("ALTER SESSION UNSET TIMEZONE");
        engine.execute("ALTER USER UNSET TIMEZONE");
        engine.execute("ALTER ACCOUNT UNSET TIMEZONE");
        assertEquals("UTC, America/Los_Angeles, ACCOUNT", row("SHOW PARAMETERS LIKE 'TIMEZONE'"));
    }

    @Test
    public void theAccountTakesTheParametersOnlyItCarries() {
        assertEquals("1, 1, ", row("SHOW PARAMETERS LIKE 'DATA_RETENTION_TIME_IN_DAYS' IN ACCOUNT"));
        engine.execute("ALTER ACCOUNT SET DATA_RETENTION_TIME_IN_DAYS = 3, PREVENT_UNLOAD_TO_INLINE_URL = TRUE");
        assertEquals("3, 1, ACCOUNT", row("SHOW PARAMETERS LIKE 'DATA_RETENTION_TIME_IN_DAYS' IN ACCOUNT"));
        assertEquals("true, false, ACCOUNT", row("SHOW PARAMETERS LIKE 'PREVENT_UNLOAD_TO_INLINE_URL' IN ACCOUNT"));
        assertEquals("", answer("SHOW PARAMETERS LIKE 'DATA_RETENTION_TIME_IN_DAYS'"), "a session lists its own");
        engine.execute("ALTER ACCOUNT UNSET DATA_RETENTION_TIME_IN_DAYS, PREVENT_UNLOAD_TO_INLINE_URL");
        assertEquals("false, false, ", row("SHOW PARAMETERS LIKE 'PREVENT_UNLOAD_TO_INLINE_URL' IN ACCOUNT"));
        // NULL puts an account's parameter back too, and a refused list applies nothing.
        engine.execute("ALTER ACCOUNT SET QUERY_TAG = 'q'");
        assertEquals("REFUSED SQL compilation error: | invalid value ['x'] for parameter 'JSON_INDENT'",
            answer("ALTER ACCOUNT SET QUERY_TAG = 'r', JSON_INDENT = 'x'"));
        assertEquals("q, , ACCOUNT", row("SHOW PARAMETERS LIKE 'QUERY_TAG' IN ACCOUNT"));
        engine.execute("ALTER ACCOUNT SET QUERY_TAG = NULL");
        assertEquals(", , ", row("SHOW PARAMETERS LIKE 'QUERY_TAG' IN ACCOUNT"));
    }
}
