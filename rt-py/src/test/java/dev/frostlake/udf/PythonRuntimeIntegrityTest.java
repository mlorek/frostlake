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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a routine does to the objects it raises or the modules it imports reaches no value the Python runtime itself
 * reads or writes, and both backends answer alike. An exception whose {@code str()} fails fails only its statement,
 * as {@code E: <exception str() failed>}, and under a procedure's {@code session.sql} only that query, which the
 * procedure catches as it would any refusal; the cpython worker's reply to it once ended the worker, or under
 * a procedure left the CALL waiting for the 60-second watchdog. A routine that replaces {@code json.dumps},
 * {@code json.loads}, the json module's encoder and decoder or {@code decimal.Decimal} answers as it would without
 * them, and the arguments the routines it calls receive are untouched: the runtime reads and writes them through
 * references of its own, bound before any routine ran.
 */
public class PythonRuntimeIntegrityTest extends MeasuredCellsTestSupport {

    @Test
    public void anExceptionWhoseTextCannotBeFormedFailsOnlyItsStatement() {
        engine.execute("CREATE OR REPLACE FUNCTION bad() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.12' "
            + "HANDLER = 'h' AS $$\nclass E(Exception):\n    def __str__(self):\n        return 1/0\ndef h():\n"
            + "    raise E()\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION nameless() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.12' "
            + "HANDLER = 'h' AS $$\nclass M(type):\n    @property\n    def __name__(cls):\n        raise "
            + "RuntimeError('no name')\n    def __repr__(cls):\n        raise RuntimeError('no repr')\nclass "
            + "E(Exception, metaclass=M):\n    def __str__(self):\n        raise RuntimeError('no str')\ndef h():\n"
            + "    raise E()\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION ok() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.12' "
            + "HANDLER = 'h' AS $$\ndef h():\n    return 7\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' "
            + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'main' AS $$\ndef main(session):\n    out = []\n"
            + "    try:\n        session.sql('SELECT bad()').collect()\n        out.append('no error')\n"
            + "    except BaseException as e:\n        out.append('caught ' + type(e).__name__)\n"
            + "    out.append(str(session.sql('SELECT ok()').collect()))\n    return ' '.join(out)\n$$");
        if (isLiveSnowflake()) {
            // The account answers SELECT bad() with an internal error and an incident number, a defect of its own,
            // recorded and not imitated: both sides refuse the call and go on.
            assertTrue(answer("SELECT bad()").startsWith("REFUSED "));
            assertAnswer("7", "SELECT ok()");
            return;
        }
        assertCells(new String[][] {
            {"SELECT bad()", "REFUSED Python Interpreter Error: | Traceback (most recent call last): |   File "
                + "\"_udf_code.py\", line 6, in h |     raise E() | h_module.E: <exception str() failed> |  in "
                + "function BAD with handler h"},
            {"SELECT nameless()", "REFUSED Python Interpreter Error: | Traceback (most recent call last): |   "
                + "File \"_udf_code.py\", line 12, in h |     raise E() | h_module.E: <exception str() failed> |  "
                + "in function NAMELESS with handler h"},
            {"SELECT ok()", "7"},
            {"CALL pr()", "caught SnowparkSQLException [Row(OK()=7)]"},
            {"SELECT ok()", "7"},
        });
    }

    @Test
    public void aRoutineThatReplacesTheJsonFunctionsChangesNothingTheRuntimeReadsOrWrites() {
        engine.execute("CREATE OR REPLACE FUNCTION patch() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' "
            + "HANDLER = 'h' AS $$\nimport json\ndef h():\n    json.dumps = lambda *a, **k: 'HACKED'\n"
            + "    json.loads = lambda *a, **k: {'re': 1}\n    json.JSONEncoder = None\n    json.JSONDecoder = None\n"
            + "    json._default_encoder = None\n    json._default_decoder = None\n    return 'ok'\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION args(v VARIANT, d NUMBER(5,2)) RETURNS VARCHAR LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' HANDLER = 'h' AS $$\nimport json, decimal\ndef h(v, d):\n"
            + "    json.loads = None\n    decimal.Decimal = float\n    return repr(v) + ' ' + repr(d)\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE calls_args() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.12' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'main' AS $$\nimport json\n"
            + "def main(session):\n    json.loads = lambda *a, **k: 'HACKED'\n"
            + "    return str(session.sql(\"SELECT args(PARSE_JSON('{\\\"a\\\":1}'), 1.25)\").collect()[0][0])\n$$");
        assertCells(new String[][] {
            {"SELECT patch()", "ok"},
            {"SELECT patch() || patch()", "okok"},
            {"SELECT args(PARSE_JSON('{\"x\":[1,2]}'), 1.50) || ' / ' || args(PARSE_JSON('[3]'), 2.25)",
                "{'x': [1, 2]} Decimal('1.50') / [3] Decimal('2.25')"},
        });
        // On the account Snowpark's own connector reads the query's reply with the replaced json.loads, so the CALL
        // fails inside the procedure's sandbox; the runtime here reads it through references of its own.
        if (!isLiveSnowflake()) {
            assertAnswer("{'a': 1} Decimal('1.25')", "CALL calls_args()");
        }
    }
}
