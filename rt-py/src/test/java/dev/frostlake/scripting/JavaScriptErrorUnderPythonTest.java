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

/**
 * A JavaScript procedure or function that makes an Error answers as it does on its own when a Python procedure's
 * statement calls it: the caller's language reaches nothing of the handler (live-verified).
 */
public class JavaScriptErrorUnderPythonTest extends MeasuredCellsTestSupport {

    private static final String CAUGHT = "try { throw new Error('%s'); } catch (e) { return 'caught ' + e.message + "
        + "' ' + typeof e.stack; }";

    /** A Python procedure that answers the first cell of {@code statement}. */
    private void python(final String name, final String statement) {
        engine.execute("CREATE OR REPLACE PROCEDURE " + name + "() RETURNS VARCHAR LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.11' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\n"
            + "def run(session):\n    return session.sql(\"" + statement + "\").collect()[0][0]\n$$");
    }

    @Test
    public void aJavaScriptProcedureMakesAnErrorUnderAPythonCaller() {
        engine.execute("CREATE OR REPLACE PROCEDURE jeup_p() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ "
            + String.format(CAUGHT, "x") + " $$");
        python("jeup_pp", "CALL jeup_p()");
        assertCells(new String[][] {
            {"CALL jeup_p()", "caught x string"},
            {"CALL jeup_pp()", "caught x string"},
        });
    }

    @Test
    public void aJavaScriptFunctionMakesAnErrorUnderAPythonCaller() {
        engine.execute("CREATE OR REPLACE FUNCTION jeup_f() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ "
            + String.format(CAUGHT, "y") + " $$");
        python("jeup_fp", "SELECT jeup_f()");
        assertCells(new String[][] {
            {"SELECT jeup_f()", "caught y string"},
            {"CALL jeup_fp()", "caught y string"},
        });
    }
}
