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
 * sys.exit() in a Python function fails the function, worded SystemExit and its status, and a procedure that calls
 * the function through session.sql catches that failure and goes on; a procedure's own exit fails its CALL alike
 * (all live-verified).
 */
public class PythonExitUnderProcedureTest extends MeasuredCellsTestSupport {

    @Test
    public void anExitFailsItsOwnCall() {
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE OR REPLACE FUNCTION test_db.test_schema.bad773(x INT) RETURNS INT LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' HANDLER = 'h' AS $$\ndef h(x):\n    return 1/x\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION test_db.test_schema.ex773() RETURNS INT LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' HANDLER = 'h' AS $$\nimport sys\ndef h():\n    sys.exit(3)\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION test_db.test_schema.ex0773() RETURNS INT LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' HANDLER = 'h' AS $$\nimport sys\ndef h():\n    sys.exit()\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE test_db.test_schema.p1773() RETURNS VARCHAR LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'main' AS $$\nTAG = "
            + "'proc'\ndef helper():\n    return 'helper-ok'\ndef main(session):\n    out = []\n    try:\n       "
            + " session.sql('SELECT test_db.test_schema.bad773(0)').collect()\n    except Exception as e:\n      "
            + "  out.append('caught1')\n    out.append(TAG + ':' + helper())\n    try:\n        "
            + "session.sql('SELECT test_db.test_schema.ex773()').collect()\n    except BaseException as e:\n     "
            + "   out.append('caught2 ' + type(e).__name__)\n    out.append(TAG + ':' + helper())\n    return ' "
            + "'.join(out)\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE test_db.test_schema.pe773() RETURNS VARCHAR LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'main' AS $$\nimport "
            + "sys\ndef main(session):\n    sys.exit(4)\n$$");
        assertAnswer("caught1 proc:helper-ok caught2 SnowparkSQLException proc:helper-ok",
            "CALL test_db.test_schema.p1773()");
        assertAnswer("REFUSED Python Interpreter Error: | Traceback (most recent call last): |   File "
                + "\"_udf_code.py\", line 4, in h |     sys.exit(3) | SystemExit: 3 |  in function EX773 with handler "
                + "h",
            "SELECT test_db.test_schema.ex773()");
        assertAnswer("REFUSED Python Interpreter Error: | Traceback (most recent call last): |   File "
                + "\"_udf_code.py\", line 4, in h |     sys.exit() | SystemExit |  in function EX0773 with handler h",
            "SELECT test_db.test_schema.ex0773()");
        assertAnswer("REFUSED Python Interpreter Error: | Traceback (most recent call last): |   File "
                + "\"_udf_code.py\", line 3, in h |     return 1/x |            ~^~ | ZeroDivisionError: division by "
                + "zero |  in function BAD773 with handler h",
            "SELECT test_db.test_schema.bad773(0)");
        assertAnswer("REFUSED Python Interpreter Error: | Traceback (most recent call last): |   File "
                + "\"_udf_code.py\", line 4, in main |     sys.exit(4) | SystemExit: 4 |  in function PE773 with "
                + "handler main",
            "CALL test_db.test_schema.pe773()");
        assertAnswer("1", "SELECT test_db.test_schema.bad773(1)");
    }
}
