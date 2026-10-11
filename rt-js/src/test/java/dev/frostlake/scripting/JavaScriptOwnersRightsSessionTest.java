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

package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a JavaScript procedure that runs with its owner's rights may do to the session it is called from: nothing.
 * The statements its handler issues are refused where a SQL body's are — SET, UNSET, ALTER SESSION and USE as an
 * unsupported statement type, the session's listings likewise, a SHOW GRANTS that names nothing by a sentence of
 * its own, and a {@code $name} as a session variable an owner's-rights procedure may not use — while GETVARIABLE
 * and a listing of objects answer. The same handler run with its caller's rights does all of it, and what it sets
 * stays set. The refusal's own sentence is live-verified; the words the runtime puts around a failed statement
 * are its own.
 */
public class JavaScriptOwnersRightsSessionTest extends MeasuredCellsTestSupport {

    private static final String STATE = "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || "
        + "NVL(GETVARIABLE('JSW')::VARCHAR, 'none') || ' ' || CURRENT_SCHEMA()";

    private static final String UNSUPPORTED = "Stored procedure execution error: Unsupported statement type '";

    @Override
    protected void setupTest() {
        engine.execute("SET v = 5");
    }

    private void create(final String name, final String sql, final String rights) {
        engine.execute("CREATE OR REPLACE PROCEDURE " + name + "() RETURNS VARCHAR LANGUAGE JAVASCRIPT " + rights
            + "AS $$ snowflake.execute({sqlText: \"" + sql + "\"}); return \"done\"; $$");
    }

    /** The statement is refused for {@code sentence} with its owner's rights, and the session is as it was. */
    private void assertRefused(final String sql, final String sentence) {
        create("js_own", sql, "");
        final String answer = answer("CALL js_own()");
        assertTrue(answer.startsWith("REFUSED ") && answer.contains(sentence), sql + " ==> " + answer);
        assertEquals("5 none TEST_SCHEMA", answer(STATE), sql);
    }

    @Test
    public void anOwnersRightsHandlerChangesNothingOfTheSession() {
        try {
            assertRefused("SET jsw = 9", UNSUPPORTED + "SET'.");
            assertRefused("UNSET v", UNSUPPORTED + "UNSET'.");
            assertRefused("ALTER SESSION SET QUERY_TAG = 'set-by-js'", UNSUPPORTED + "ALTER_SESSION'.");
            assertRefused("USE SCHEMA test_schema", UNSUPPORTED + "USE'.");
        } finally {
            answer("ALTER SESSION UNSET QUERY_TAG");
            answer("UNSET jsw");
            answer("UNSET v");
        }
    }

    @Test
    public void anOwnersRightsHandlerReadsNoneOfTheSessionsListingsOrVariables() {
        try {
            assertRefused("SHOW VARIABLES", UNSUPPORTED + "SHOW VARIABLE'.");
            assertRefused("SHOW PARAMETERS LIKE 'QUERY_TAG'", UNSUPPORTED + "SHOW PARAMETER'.");
            assertRefused("SHOW LOCKS", UNSUPPORTED + "SHOW LOCK'.");
            assertRefused("SHOW TRANSACTIONS", UNSUPPORTED + "SHOW TRANSACTION'.");
            assertRefused("SHOW GRANTS",
                "SHOW GRANTS to the current user is not supported in owner's rights stored procedures.");
            assertRefused("SELECT $v", "Use of session variable '$V' is not allowed in owners rights stored procedure");
            create("js_own", "SHOW TABLES", "");
            assertAnswer("done", "CALL js_own()");
            create("js_own", "SELECT GETVARIABLE('V')", "");
            assertAnswer("done", "CALL js_own()");
        } finally {
            answer("UNSET v");
        }
    }

    @Test
    public void aCallersRightsHandlerChangesTheSession() {
        try {
            create("js_clr", "SET jsw = 9", "EXECUTE AS CALLER ");
            assertAnswer("done", "CALL js_clr()");
            assertEquals("5 9 TEST_SCHEMA", answer(STATE));
            create("js_clr", "UNSET v", "EXECUTE AS CALLER ");
            assertAnswer("done", "CALL js_clr()");
            assertEquals("none 9 TEST_SCHEMA", answer(STATE));
            create("js_clr", "ALTER SESSION SET QUERY_TAG = 'set-by-js'", "EXECUTE AS CALLER ");
            assertAnswer("done", "CALL js_clr()");
            create("js_clr", "USE SCHEMA test_schema", "EXECUTE AS CALLER ");
            assertAnswer("done", "CALL js_clr()");
            assertEquals("none 9 TEST_SCHEMA", answer(STATE));
        } finally {
            answer("ALTER SESSION UNSET QUERY_TAG");
            answer("UNSET jsw");
            answer("UNSET v");
        }
    }
}
