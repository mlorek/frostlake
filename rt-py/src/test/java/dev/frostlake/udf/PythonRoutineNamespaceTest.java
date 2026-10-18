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

import org.junit.jupiter.api.Test;

/**
 * Each Python routine runs in a module namespace of its own (live-verified): a function a procedure's query calls
 * leaves the procedure's own {@code fmt} as it was, and a name one function's body defines is not one another
 * function's body sees.
 */
public class PythonRoutineNamespaceTest extends MeasuredCellsTestSupport {

    @Test
    public void eachRoutineHasItsOwnModule() {
        engine.execute("CREATE OR REPLACE FUNCTION u(x INT) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' "
            + "HANDLER = 'h' AS $$\ndef fmt(v):\n    return 'udf-' + str(v)\ndef h(x):\n    return fmt(x)\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE p() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' "
            + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'main' AS $$\ndef fmt(v):\n    return 'proc-' "
            + "+ str(v)\ndef main(session):\n    before = fmt(1)\n    inner = session.sql(\"SELECT "
            + "u(2)\").collect()[0][0]\n    after = fmt(3)\n    return before + ' ' + inner + ' ' + after\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION g1() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' "
            + "HANDLER = 'h' AS $$\nSECRET = 'one'\ndef h():\n    return SECRET\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION g2() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' "
            + "HANDLER = 'h' AS $$\ndef h():\n    return str(globals().get('SECRET'))\n$$");
        assertCells(new String[][] {
            {"CALL p()", "proc-1 udf-2 proc-3"},
            {"SELECT g1(), g2()", "one|None"},
            {"SELECT g2()", "None"},
        });
    }
}
