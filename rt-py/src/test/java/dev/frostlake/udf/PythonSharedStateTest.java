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

import dev.frostlake.DatabaseEngine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What Python routines share on a thread beside their own names - the built-in names, the environment and the
 * snowpark emulation's modules and classes - is put back once each outermost statement ends, so that a routine one
 * statement ran reaches neither the next statement's routines nor another engine's: a built-in name it added is gone,
 * an environment variable it set is unset, and a snowpark method it replaced is the emulation's own again.
 */
public class PythonSharedStateTest {

    private static final String TAMPERS = """
        CREATE FUNCTION tamper() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' HANDLER = 'h' AS $$
        def h():
            import builtins
            import os
            import snowflake.snowpark as sp
            builtins.LEAK = 'first statement'
            os.environ['FL_LEAK'] = 'first statement'
            sp.Session.sql = lambda self, q: 'hijacked'
            return 'tampered'
        $$""";

    private static final String READS = """
        CREATE FUNCTION peek() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' HANDLER = 'h' AS $$
        def h():
            import builtins
            import os
            import snowflake.snowpark as sp
            hijacked = sp.Session.sql.__name__ == '<lambda>'
            return str(getattr(builtins, 'LEAK', None)) + '|' + str(os.environ.get('FL_LEAK')) + '|' + str(hijacked)
        $$""";

    private static final String RUNS_SQL = """
        CREATE PROCEDURE runs_sql() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12'
        PACKAGES = ('snowflake-snowpark-python') HANDLER = 'main' AS $$
        def main(session):
            return str(session.sql('SELECT 7 AS n').collect()[0][0])
        $$""";

    private static DatabaseEngine engineWith(final String... routines) {
        final DatabaseEngine engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE d");
        engine.execute("USE SCHEMA d.PUBLIC");
        for (final String routine : routines) {
            engine.execute(routine);
        }
        return engine;
    }

    private static String scalar(final DatabaseEngine engine, final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValues().get(0));
    }

    @Test
    public void theNextStatementFindsTheSharedStateAsItWas() {
        final DatabaseEngine engine = engineWith(TAMPERS, READS, RUNS_SQL);
        try {
            assertEquals("tampered", scalar(engine, "SELECT tamper()"));
            assertEquals("None|None|False", scalar(engine, "SELECT peek()"));
            assertEquals("7", scalar(engine, "CALL runs_sql()"));
        } finally {
            engine.shutdown();
        }
    }

    @Test
    public void anotherEngineOnTheThreadFindsItAsItWas() {
        final DatabaseEngine first = engineWith(TAMPERS);
        try {
            assertEquals("tampered", scalar(first, "SELECT tamper()"));
        } finally {
            first.shutdown();
        }
        final DatabaseEngine second = engineWith(READS, RUNS_SQL);
        try {
            assertEquals("None|None|False", scalar(second, "SELECT peek()"));
            assertEquals("7", scalar(second, "CALL runs_sql()"));
        } finally {
            second.shutdown();
        }
    }
}
