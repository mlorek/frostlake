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
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Each engine runs its Python on the interpreter its own configuration chose ({@code python.runtime}), whatever an
 * engine built after it chose, and a CPython worker does not outlive the thread it served. Needs a CPython
 * interpreter ({@code python.executable}, else {@code python3} on the PATH); skipped without one.
 */
public class PythonBackendPerEngineTest {

    private static final String EXECUTABLE = System.getProperty(PythonInterpreter.PROP_EXECUTABLE, "python3");

    private static final String IMPLEMENTATION = """
        CREATE OR REPLACE FUNCTION impl() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' HANDLER = 'h'
        AS $$
        import platform
        def h():
            return platform.python_implementation()
        $$""";

    private final List<DatabaseEngine> engines = new ArrayList<>();

    @AfterEach
    public void tearDown() {
        for (final DatabaseEngine engine : engines) {
            engine.shutdown();
        }
    }

    private static EngineConfig config(final String runtime) {
        final Properties settings = new Properties();
        settings.setProperty(PythonInterpreter.PROP_RUNTIME, runtime);
        settings.setProperty(PythonInterpreter.PROP_EXECUTABLE, EXECUTABLE);
        return new EngineConfig(settings);
    }

    private DatabaseEngine engine(final EngineConfig config) {
        final DatabaseEngine engine = new DatabaseEngine(config);
        engines.add(engine);
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA PUBLIC");
        return engine;
    }

    private static Object cell(final DatabaseEngine engine, final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
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

    @Test
    public void eachEngineKeepsTheInterpreterItsConfigurationChose() {
        assumeTrue(cpythonRuns(), "needs the CPython interpreter " + EXECUTABLE);
        final DatabaseEngine cpython = engine(config("cpython"));
        cpython.execute(IMPLEMENTATION);
        assertEquals("CPython", cell(cpython, "SELECT impl()"));

        final DatabaseEngine graalpy = engine(config("graalpy"));
        graalpy.execute(IMPLEMENTATION);
        assertEquals("GraalVM", cell(graalpy, "SELECT impl()"));

        assertEquals("CPython", cell(cpython, "SELECT impl()"));
        assertEquals("GraalVM", cell(graalpy, "SELECT impl()"));
    }

    @Test
    public void aWorkerEndsWithTheThreadItServed() throws InterruptedException {
        assumeTrue(cpythonRuns(), "needs the CPython interpreter " + EXECUTABLE);
        final EngineConfig config = config("cpython");
        final DatabaseEngine engine = engine(config);
        engine.execute("""
            CREATE OR REPLACE FUNCTION pf(x INT) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.12' HANDLER = 'h'
            AS $$
            def h(x):
                return x + 1
            $$""");
        assertEquals("2", String.valueOf(cell(engine, "SELECT pf(1)")));
        final CPythonBackend backend = (CPythonBackend) PythonInterpreter.backendOf(config);
        final int before = backend.runningWorkers();

        final List<Object> answers = Collections.synchronizedList(new ArrayList<Object>());
        for (int i = 0; i < 20; i++) {
            final Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    answers.add(cell(engine, "SELECT pf(1)"));
                }
            });
            thread.start();
            thread.join();
        }

        assertEquals(20, answers.size());
        for (final Object answer : answers) {
            assertEquals("2", String.valueOf(answer));
        }
        // Each thread's worker is stopped once the thread has ended: the last one's when the next worker starts or
        // the sweep runs, every earlier one's by now.
        final int after = backend.runningWorkers();
        assertTrue(after <= before + 1, "workers running: " + before + " before the threads, " + after + " after");
    }
}
