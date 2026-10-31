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

package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * A session's STATEMENT_TIMEOUT_IN_SECONDS ends a statement whatever its handler does meanwhile: a Python function
 * that sleeps, a JavaScript function or procedure that spins, and a Python procedure that catches what its queries
 * raise all fail with the statement's refusal, as SYSTEM$WAIT does (all live-verified).
 */
public class GuestStatementTimeoutTest extends MeasuredCellsTestSupport {

    /** The session's timeout goes back to its default whatever a step answered. */
    @AfterEach
    public void unsetTimeout() {
        engine.execute("ALTER SESSION UNSET STATEMENT_TIMEOUT_IN_SECONDS");
    }

    @Test
    public void aTimeoutEndsEveryHandler() {
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE OR REPLACE FUNCTION test_db.test_schema.slp773(n INT) RETURNS INT LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' HANDLER = 'h' AS $$\nimport time\ndef h(n):\n    time.sleep(n)\n    "
            + "return n\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION test_db.test_schema.jsl773(n FLOAT) RETURNS FLOAT LANGUAGE "
            + "JAVASCRIPT AS $$ var e = Date.now() + N * 1000; while (Date.now() < e) {} return N; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE test_db.test_schema.jsp773(n FLOAT) RETURNS FLOAT LANGUAGE "
            + "JAVASCRIPT AS $$ var e = Date.now() + N * 1000; while (Date.now() < e) {} return N; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE test_db.test_schema.pc773() RETURNS VARCHAR LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'main' AS $$\nimport "
            + "time\ndef main(session):\n    e = time.time() + 8\n    ok = bad = 0\n    while time.time() < e:\n "
            + "       try:\n            session.sql('SELECT 1').collect()\n            ok += 1\n        except "
            + "Exception:\n            bad += 1\n    return 'finished ok=%d refused=%d' % (ok > 0, bad > 0)\n$$");
        engine.execute("SELECT test_db.test_schema.slp773(1)");
        engine.execute("SELECT test_db.test_schema.jsl773(1)");
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET STATEMENT_TIMEOUT_IN_SECONDS = 2");
        assertAnswer("REFUSED Statement reached its statement or warehouse timeout of 2 second(s) and was canceled.",
            "SELECT SYSTEM$WAIT(5)");
        assertAnswer("REFUSED Statement reached its statement or warehouse timeout of 2 second(s) and was canceled.",
            "SELECT test_db.test_schema.slp773(7)");
        assertAnswer("REFUSED Statement reached its statement or warehouse timeout of 2 second(s) and was canceled.",
            "SELECT test_db.test_schema.jsl773(7)");
        assertAnswer("REFUSED Statement reached its statement or warehouse timeout of 2 second(s) and was canceled.",
            "CALL test_db.test_schema.pc773()");
        assertAnswer("REFUSED Statement reached its statement or warehouse timeout of 2 second(s) and was canceled.",
            "CALL test_db.test_schema.jsp773(7)");
        assertAnswer("Statement executed successfully.", "ALTER SESSION UNSET STATEMENT_TIMEOUT_IN_SECONDS");
        assertAnswer("1", "SELECT test_db.test_schema.slp773(1)");
    }
}
