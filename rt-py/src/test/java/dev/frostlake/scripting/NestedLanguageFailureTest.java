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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A procedure in one handler language that fails while a procedure in another is running it reports its own refusal
 * to the caller, every time it is called (live-verified): a Python procedure whose statement is refused under a
 * JavaScript procedure's {@code snowflake.execute}, one raising its own exception there, and a JavaScript procedure
 * whose statement is refused under a Python handler's {@code session.sql}. The words each runtime puts around the
 * refusal are its own; the refusal's sentence, the failing procedure and the place in the JavaScript caller are
 * the account's.
 */
public class NestedLanguageFailureTest extends MeasuredCellsTestSupport {

    private static final String PYTHON = " RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
        + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\n";

    private static final String AT_EXECUTE = "At Snowflake.execute, line 1 position 20";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE PROCEDURE py_fails()" + PYTHON + """
            def run(session):
                session.sql("SELECT 1/0").collect()
                return 'x'
            $$""");
        engine.execute("CREATE OR REPLACE PROCEDURE py_raises()" + PYTHON + """
            def run(session):
                raise ValueError('nothing to answer')
            $$""");
        engine.execute("CREATE OR REPLACE PROCEDURE py_answers()" + PYTHON + """
            def run(session):
                return session.sql("SELECT 'answered'").collect()[0][0]
            $$""");
        js("js_calls_fails", "CALL py_fails()");
        js("js_calls_raises", "CALL py_raises()");
        js("js_calls_answers", "CALL py_answers()");
        engine.execute("CREATE OR REPLACE PROCEDURE js_fails() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ "
            + "snowflake.execute({sqlText: \"SELECT 1/0\"}); return 'x'; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE py_calls_js_fails()" + PYTHON + """
            def run(session):
                return session.sql("CALL js_fails()").collect()[0][0]
            $$""");
    }

    /** A JavaScript procedure answering the first cell of {@code statement}, run through snowflake.execute. */
    private void js(final String name, final String statement) {
        engine.execute("CREATE OR REPLACE PROCEDURE " + name + "() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var rs = "
            + "snowflake.execute({sqlText: \"" + statement + "\"}); rs.next(); return rs.getColumnValue(1); $$");
    }

    /** The CALL is refused, and its refusal holds each of {@code parts}, the first one leading it. */
    private void assertRefusedWith(final String call, final String... parts) {
        final String answer = answer(call);
        boolean holds = answer.startsWith("REFUSED " + parts[0]);
        for (final String part : parts) {
            holds = holds && answer.contains(part);
        }
        assertTrue(holds, call + " ==> " + answer);
    }

    @Test
    public void aPythonRefusalUnderJavaScriptReachesTheCallerEachTime() {
        for (int i = 0; i < 3; i++) {
            assertRefusedWith("CALL js_calls_fails()", "Execution error in store procedure JS_CALLS_FAILS:",
                "Division by zero", AT_EXECUTE);
        }
        assertRefusedWith("CALL py_fails()", "", "Division by zero");
        assertAnswer("answered", "CALL js_calls_answers()");
    }

    @Test
    public void aPythonExceptionUnderJavaScriptReachesTheCallerEachTime() {
        for (int i = 0; i < 2; i++) {
            assertRefusedWith("CALL js_calls_raises()", "Execution error in store procedure JS_CALLS_RAISES:",
                "ValueError: nothing to answer", AT_EXECUTE);
        }
        assertAnswer("answered", "CALL js_calls_answers()");
        assertAnswer("answered", "CALL js_calls_answers()");
    }

    @Test
    public void aJavaScriptRefusalUnderPythonReachesTheCallerEachTime() {
        for (int i = 0; i < 2; i++) {
            assertRefusedWith("CALL py_calls_js_fails()", "", "Execution error in store procedure JS_FAILS:",
                "Division by zero", "At Snowflake.execute, line 1 position 11");
        }
    }
}
