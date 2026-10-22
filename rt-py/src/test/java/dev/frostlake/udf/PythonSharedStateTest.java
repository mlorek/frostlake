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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What Python routines share on a thread beside their own names - the built-in names, the environment and the
 * snowpark emulation's modules and classes - is put back once each outermost statement ends, so that a routine one
 * statement ran reaches neither the next statement's routines nor another engine's: a built-in name it added is gone,
 * an environment variable it set is unset, and a snowpark method it replaced is the emulation's own again. So for a
 * member it added to an emulation class or module, a class only a closure of the emulation holds, and a function's
 * code or closure cell, whether the statement succeeded or failed. So too for the standard library's modules
 * imported when the interpreter was made: a member a routine replaced or deleted is back, as is the module on
 * {@code sys.modules}, a module a routine made itself or anything else it put there is gone while one it imported
 * stays, and the working directory and the decimal context are as they were.
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

    /**
     * Hijacks the snowpark emulation through what the first restore missed: a member added to a guarded class
     * ({@code Session.__getattribute__}), classes no module holds ({@code GroupedFrame}, {@code DataFrameWriter}), a
     * function's code and a closure's cell, and a member added to an emulation module; then fails when asked to.
     */
    private static final String HIJACKS = """
        CREATE PROCEDURE hijack(fail BOOLEAN) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12'
        PACKAGES = ('snowflake-snowpark-python') HANDLER = 'main' AS $$
        import snowflake.snowpark as sp
        import snowflake.snowpark.functions as F
        def main(session, fail):
            def ga(self, name):
                if name == 'sql':
                    return lambda q, params=None: 'hijacked:' + q
                return object.__getattribute__(self, name)
            df = session.sql('SELECT 1 AS a')
            grouped = type(df.group_by('a'))
            grouped.count = lambda self: 'grouped-hijacked'
            writer = type(df.write)
            writer.save_as_table = lambda self, *a, **k: 'writer-hijacked'
            def make():
                held = None
                def lit(*a, **k):
                    return (held, 'lit-hijacked')[1]
                return lit
            F.lit.__code__ = make().__code__
            for cell in type(df).group_by.__closure__ or ():
                if cell.cell_contents is grouped:
                    cell.cell_contents = lambda *a, **k: 'closure-hijacked'
            sp.Session.__getattribute__ = ga
            sp.ADDED = 'added'
            hijacked = session.sql('SELECT 1') + ' ' + str(F.lit(1))
            if fail:
                raise ValueError('after hijacking')
            return grouped.__name__ + ' ' + writer.__name__ + ' ' + hijacked
        $$""";

    /** What a hijack would change: session.sql, a grouped count, F.lit and the emulation module's members. */
    private static final String VICTIM = """
        CREATE PROCEDURE victim() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12'
        PACKAGES = ('snowflake-snowpark-python') HANDLER = 'main' AS $$
        import snowflake.snowpark as sp
        import snowflake.snowpark.functions as F
        def main(session):
            df = session.sql('SELECT 1 AS a')
            grouped = df.group_by('a')
            return ' '.join([type(df).__name__, type(grouped).__name__, str(grouped.count().collect()[0][1]),
                             type(df.write).save_as_table.__name__, type(F.lit(1)).__name__,
                             str(hasattr(sp, 'ADDED')), str(hasattr(sp.Session, '__getattribute__')
                                                           and '__getattribute__' in vars(sp.Session))])
        $$""";

    private static final String UNTOUCHED = "DataFrame GroupedFrame 1 save_as_table Column False False";

    /**
     * Changes the standard library: replaces {@code json.dumps} and deletes {@code json.JSONDecoder}, puts a module of
     * its own, a non-module and a stand-in for {@code decimal} on {@code sys.modules} and takes {@code os} off it,
     * imports {@code email.mime}, moves the working directory and narrows the decimal context. Answers the working
     * directory it started in.
     */
    private static final String CHANGES_STDLIB = """
        CREATE FUNCTION change_stdlib() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' HANDLER = 'h' AS $$
        import decimal, json, os, sys, tempfile, types
        def h():
            started = os.getcwd()
            json.dumps = lambda *a, **k: 'HACKED'
            del json.JSONDecoder
            made = types.ModuleType('fl_made'); made.v = 'leaked'
            sys.modules['fl_made'] = made
            sys.modules['fl_number'] = 42
            sys.modules['decimal'] = types.ModuleType('decimal')
            del sys.modules['os']
            import email.mime
            os.chdir(tempfile.gettempdir())
            decimal.getcontext().prec = 3
            return started
        $$""";

    /** What the change would show: json, sys.modules, the working directory, 1/7, whether email.mime is imported. */
    private static final String READS_STDLIB = """
        CREATE FUNCTION read_stdlib() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' HANDLER = 'h' AS $$
        import sys
        def h():
            imported = 'email.mime' in sys.modules
            import decimal, json, os
            return '|'.join([json.dumps([1]), str(hasattr(json, 'JSONDecoder')), str(sys.modules.get('fl_made')),
                             str(sys.modules.get('fl_number')), str(sys.modules['decimal'] is decimal
                                                                    and hasattr(decimal, 'Decimal')),
                             str(sys.modules.get('os') is os), os.getcwd(),
                             str(decimal.Decimal(1) / decimal.Decimal(7)), str(imported)])
        $$""";

    private static final String PRISTINE_STDLIB = "[1]|True|None|None|True|True|%s|0.1428571428571428571428571429|";

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

    @Test
    public void whatARoutineChangesInTheStandardLibraryIsPutBackInTheNextStatement() {
        final DatabaseEngine engine = engineWith(CHANGES_STDLIB, READS_STDLIB);
        try {
            final String before = scalar(engine, "SELECT read_stdlib()");
            final String started = scalar(engine, "SELECT change_stdlib()");
            assertEquals(String.format(PRISTINE_STDLIB, started) + "True", scalar(engine, "SELECT read_stdlib()"));
            // Whether email.mime was imported before depends on what ran on the thread before this test.
            assertTrue(before.startsWith(String.format(PRISTINE_STDLIB, started)), before);
        } finally {
            engine.shutdown();
        }
    }

    @Test
    public void anotherEngineFindsTheStandardLibraryAsItWas() {
        final DatabaseEngine first = engineWith(CHANGES_STDLIB);
        final String started;
        try {
            started = scalar(first, "SELECT change_stdlib()");
        } finally {
            first.shutdown();
        }
        final DatabaseEngine second = engineWith(READS_STDLIB);
        try {
            assertEquals(String.format(PRISTINE_STDLIB, started) + "True", scalar(second, "SELECT read_stdlib()"));
        } finally {
            second.shutdown();
        }
    }

    @Test
    public void whatARoutineAddsToOrChangesInTheSnowparkEmulationIsGoneInTheNextStatement() {
        final DatabaseEngine engine = engineWith(HIJACKS, VICTIM);
        try {
            assertEquals(UNTOUCHED, scalar(engine, "CALL victim()"));
            assertEquals("GroupedFrame DataFrameWriter hijacked:SELECT 1 lit-hijacked",
                scalar(engine, "CALL hijack(FALSE)"));
            assertEquals(UNTOUCHED, scalar(engine, "CALL victim()"));
        } finally {
            engine.shutdown();
        }
    }

    @Test
    public void aHijackThatFailsItsStatementIsGoneAsWellAndReachesNoOtherEngine() {
        final DatabaseEngine first = engineWith(HIJACKS, VICTIM);
        try {
            assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    first.executeQuery("CALL hijack(TRUE)");
                }
            });
            assertEquals(UNTOUCHED, scalar(first, "CALL victim()"));
            assertEquals("GroupedFrame DataFrameWriter hijacked:SELECT 1 lit-hijacked",
                scalar(first, "CALL hijack(FALSE)"));
        } finally {
            first.shutdown();
        }
        final DatabaseEngine second = engineWith(VICTIM);
        try {
            assertEquals(UNTOUCHED, scalar(second, "CALL victim()"));
        } finally {
            second.shutdown();
        }
    }
}
