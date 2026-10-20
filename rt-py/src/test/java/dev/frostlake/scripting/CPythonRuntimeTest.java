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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.rt.py.PythonInterpreter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * What the CPython backend ({@code python.runtime=cpython}) changes from the embedded GraalPy runtime: a handler
 * receives the Python types Snowflake hands it and its return keeps its SQL type, a procedure's {@code session.sql}
 * reaches a Python UDF in the same worker, a worker that dies is replaced, and printing cannot disturb the worker's
 * protocol. Runs on the CPython backend only ({@code mvn -pl rt-py test -Pcpython}); skipped on GraalPy.
 */
public class CPythonRuntimeTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        assumeTrue(PythonInterpreter.description().endsWith("(CPython)"), "runs on python.runtime=cpython only");
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA PUBLIC");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private Object cell(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void argumentsArriveAsThePythonTypesSnowflakePasses() {
        engine.execute("CREATE FUNCTION arg_types(a NUMBER(38,0), b NUMBER(10,2), c FLOAT, d DATE, e TIMESTAMP_NTZ,"
            + " f TIMESTAMP_TZ, g VARIANT, h VARCHAR, i BOOLEAN, j BINARY, k ARRAY, l OBJECT, m TIME)"
            + " RETURNS STRING LANGUAGE PYTHON RUNTIME_VERSION = '3.11' HANDLER = 'h' AS $$\n"
            + "def h(a, b, c, d, e, f, g, h, i, j, k, l, m):\n"
            + "    names = ' '.join(type(x).__name__ for x in (a, b, c, d, e, f, g, h, i, j, k, l, m))\n"
            + "    return names + ' | ' + ' '.join(str(x) for x in (a, b, c, d, e, f, m)) + ' | ' + j.hex()\n"
            + "$$");

        assertEquals("int Decimal float date datetime datetime dict str bool bytes list dict time"
                + " | 42 12.34 1.5 2024-01-02 2024-01-02 03:04:05.123456 2024-01-02 03:04:05+02:00 10:11:12 | abcd",
            cell("SELECT arg_types(42, 12.34, 1.5, '2024-01-02'::DATE, '2024-01-02 03:04:05.123456'::TIMESTAMP_NTZ,"
                + " '2024-01-02 03:04:05 +02:00'::TIMESTAMP_TZ, PARSE_JSON('{\"k\": [1, 2.5]}'), 'txt', TRUE,"
                + " TO_BINARY('ABCD', 'HEX'), ARRAY_CONSTRUCT(1, 'x'), OBJECT_CONSTRUCT('a', 1), '10:11:12'::TIME)"));
    }

    @Test
    public void returnsKeepTheirSqlTypes() {
        engine.execute("CREATE FUNCTION r_dec() RETURNS NUMBER(10,2) LANGUAGE PYTHON RUNTIME_VERSION = '3.11'"
            + " HANDLER = 'h' AS $$\nimport decimal\ndef h():\n    return decimal.Decimal('3.14159')\n$$");
        engine.execute("CREATE FUNCTION r_bin() RETURNS BINARY LANGUAGE PYTHON RUNTIME_VERSION = '3.11'"
            + " HANDLER = 'h' AS $$\ndef h():\n    return b'AB'\n$$");
        engine.execute("CREATE FUNCTION r_tz() RETURNS TIMESTAMP_TZ LANGUAGE PYTHON RUNTIME_VERSION = '3.11'"
            + " HANDLER = 'h' AS $$\nimport datetime\ndef h():\n"
            + "    return datetime.datetime(2024, 1, 2, 3, 4, 5, tzinfo=datetime.timezone(datetime.timedelta(hours=5)))\n$$");
        engine.execute("CREATE FUNCTION r_var() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.11'"
            + " HANDLER = 'h' AS $$\nimport decimal\ndef h():\n    return {'dec': decimal.Decimal('1.25'), 'ok': True}\n$$");

        assertEquals("3.14", cell("SELECT r_dec()::VARCHAR"));
        assertEquals("4142", cell("SELECT TO_VARCHAR(r_bin(), 'HEX')"));
        assertEquals("2024-01-02 03:04:05 +05:00", cell("SELECT TO_VARCHAR(r_tz(), 'YYYY-MM-DD HH24:MI:SS TZH:TZM')"));
        // Inside a VARIANT a Decimal is its str() (live-verified, see PythonFunctionAnswerTypesTest), on both backends.
        assertEquals("{\"dec\":\"1.25\",\"ok\":true}", cell("SELECT r_var()::VARCHAR"));
    }

    @Test
    public void aProcedureReachesAPythonUdfThroughSessionSql() {
        engine.execute("CREATE FUNCTION add_one(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = '3.11'"
            + " HANDLER = 'f' AS $$\ndef f(x):\n    return x + 1\n$$");
        engine.execute("CREATE PROCEDURE call_add_one() RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = '3.11'"
            + " PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\n"
            + "def run(session):\n"
            + "    return session.sql('SELECT add_one(41) AS v').collect()[0][0]\n$$");

        assertEquals("42", String.valueOf(cell("CALL call_add_one()")));
    }

    @Test
    public void aWorkerThatDiesIsReplaced() {
        engine.execute("CREATE FUNCTION die() RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = '3.11'"
            + " HANDLER = 'f' AS $$\nimport os\ndef f():\n    os._exit(3)\n$$");
        engine.execute("CREATE FUNCTION twice(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = '3.11'"
            + " HANDLER = 'f' AS $$\ndef f(x):\n    return x * 2\n$$");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT die()");
            }
        });
        assertEquals("42", String.valueOf(cell("SELECT twice(21)")));
    }

    @Test
    public void printingDoesNotDisturbTheResult() {
        engine.execute("CREATE FUNCTION noisy(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = '3.11'"
            + " HANDLER = 'f' AS $$\nimport sys\ndef f(x):\n"
            + "    print('{\"re\": 1, \"ok\": true}')\n"
            + "    sys.stdout.write('more noise\\n')\n"
            + "    return x * 2\n$$");

        assertEquals("42", String.valueOf(cell("SELECT noisy(21)")));
    }

    /**
     * #2383: the worker runs isolated from a directory only the engine's user may enter, so a module someone left in
     * the shared temporary directory is not importable, let alone imported in place of the standard library's, and
     * what a routine prints still reaches the log as UTF-8 although isolation ignores PYTHONIOENCODING.
     */
    @Test
    public void aModuleLeftInTheTemporaryDirectoryIsNotImported() throws IOException {
        final String name = "fl_planted_" + UUID.randomUUID().toString().replace("-", "");
        final Path planted = Paths.get(System.getProperty("java.io.tmpdir"), name + ".py");
        Files.write(planted, "PLANTED = True\n".getBytes(StandardCharsets.UTF_8));
        try {
            engine.execute("CREATE FUNCTION isolation(m VARCHAR) RETURNS VARCHAR LANGUAGE PYTHON"
                + " RUNTIME_VERSION = '3.12' HANDLER = 'f' AS $$\n"
                + "import importlib, os, stat, sys\n"
                + "def f(m):\n"
                + "    print('za\u017c\u00f3\u0142\u0107')\n"
                + "    try:\n"
                + "        importlib.import_module(m)\n"
                + "        found = 'imported'\n"
                + "    except ImportError:\n"
                + "        found = 'not found'\n"
                + "    home = os.path.dirname(os.path.abspath(sys.argv[0]))\n"
                + "    on_path = [p for p in sys.path if p in ('', home, os.getcwd())]\n"
                + "    return ' '.join([found, str(sys.flags.isolated), oct(stat.S_IMODE(os.stat(home).st_mode)),"
                + " str(on_path)])\n"
                + "$$");

            assertEquals("not found 1 0o700 []", String.valueOf(cell("SELECT isolation('" + name + "')")));
        } finally {
            Files.deleteIfExists(planted);
        }
    }

    /**
     * #2386: the worker's own module and classes - what encodes a result, what calls the engine - are guarded as the
     * runtime's helper modules are, so a routine that replaces them through {@code sys.modules['__main__']} or
     * {@code object.__subclasses__()} reaches no later statement.
     */
    @Test
    public void whatARoutineChangesInTheWorkerItselfIsGoneInTheNextStatement() {
        engine.execute("CREATE FUNCTION tamper_worker() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12'"
            + " HANDLER = 'f' AS $$\n"
            + "import sys\n"
            + "def f():\n"
            + "    worker = sys.modules['__main__']\n"
            + "    worker.__dict__['_decode'] = lambda value: 'decoded-hijacked'\n"
            + "    worker.__dict__['_ADDED'] = 'added'\n"
            + "    for kind in object.__subclasses__():\n"
            + "        if kind.__name__ == '_JavaObject':\n"
            + "            kind.__getattr__ = lambda self, name: (lambda *a: 'host-hijacked')\n"
            + "    return 'tampered'\n"
            + "$$");
        engine.execute("CREATE FUNCTION add_two(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = '3.12'"
            + " HANDLER = 'f' AS $$\nimport sys\ndef f(x):\n"
            + "    return x + (1 if hasattr(sys.modules['__main__'], '_ADDED') else 2)\n$$");
        engine.execute("CREATE PROCEDURE seven() RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = '3.12'"
            + " PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\n"
            + "def run(session):\n"
            + "    return session.sql('SELECT 7 AS v').collect()[0][0]\n$$");

        assertEquals("tampered", String.valueOf(cell("SELECT tamper_worker()")));
        assertEquals("42", String.valueOf(cell("SELECT add_two(40)")));
        assertEquals("7", String.valueOf(cell("CALL seven()")));
    }
}
