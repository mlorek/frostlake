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

package dev.frostlake.rt.py;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A routine that replaces a builtin - {@code builtins.len}, {@code builtins.isinstance} or {@code builtins.str} -
 * changes it for routines, never for the runtime that carries the routine's answer back: the call answers its value,
 * and the next statement runs as before, on either backend. On CPython the worker and the standard library it
 * uses read the builtins a routine changes, so the call failed with the worker's own traceback. CPython needs its
 * interpreter ({@code python.executable}, else {@code python3} on the PATH) and is skipped without one.
 */
public class PythonBuiltinReplacementTest {

    private static final String EXECUTABLE = System.getProperty(PythonInterpreter.PROP_EXECUTABLE, "python3");

    private final List<DatabaseEngine> engines = new ArrayList<>();

    @AfterEach
    public void tearDown() {
        for (final DatabaseEngine engine : engines) {
            engine.shutdown();
        }
    }

    private DatabaseEngine engine(final String runtime) {
        final Properties settings = new Properties();
        settings.setProperty(PythonInterpreter.PROP_RUNTIME, runtime);
        settings.setProperty(PythonInterpreter.PROP_EXECUTABLE, EXECUTABLE);
        final DatabaseEngine engine = new DatabaseEngine(new EngineConfig(settings));
        engines.add(engine);
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA PUBLIC");
        for (final String name : new String[] {"len", "str", "isinstance"}) {
            engine.execute("CREATE OR REPLACE FUNCTION swap_" + name + "() RETURNS VARCHAR LANGUAGE PYTHON "
                + "RUNTIME_VERSION = '3.12' HANDLER = 'h' AS $$\nimport builtins\ndef h():\n    builtins." + name
                + " = lambda *a, **k: 42\n    return 'ok'\n$$");
        }
        engine.execute("CREATE OR REPLACE FUNCTION plain() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.12' "
            + "HANDLER = 'h' AS $$\ndef h():\n    return len('abc') + 4\n$$");
        return engine;
    }

    private static String cell(final DatabaseEngine engine, final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private static boolean cpythonRuns() {
        try {
            final Process check = new ProcessBuilder(EXECUTABLE, "-c", "pass").start();
            return check.waitFor(60, TimeUnit.SECONDS) && check.exitValue() == 0;
        } catch (final IOException missing) {
            return false;
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void eachReplacementAnswersItsValue(final String runtime, final String... names) {
        final DatabaseEngine engine = engine(runtime);
        for (final String name : names) {
            assertEquals("ok", cell(engine, "SELECT swap_" + name + "()"), name);
            assertEquals("7", cell(engine, "SELECT plain()"), "the statement after the one replacing " + name);
        }
    }

    @Test
    public void graalPyKeepsItsRuntimeAndAnswers() {
        eachReplacementAnswersItsValue("graalpy", "len", "str", "isinstance");
    }

    @Test
    public void cpythonKeepsItsRuntimeAndAnswers() {
        assumeTrue(cpythonRuns(), "needs the CPython interpreter " + EXECUTABLE);
        eachReplacementAnswersItsValue("cpython", "len", "str", "isinstance");
    }
}
