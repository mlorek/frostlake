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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * What decides where a Python import looks is put back once each outermost statement ends, changed in place or not:
 * the contents of {@code sys.path}, {@code sys.meta_path} and {@code sys.path_hooks}, the importer cache and the
 * recursion limit. A routine that put a directory first on {@code sys.path} made every later statement's import, and
 * another engine's on the same thread, find the module it planted there. On both backends; CPython needs its
 * interpreter ({@code python.executable}, else {@code python3} on the PATH) and is skipped without one.
 */
public class PythonImportPathIsolationTest {

    private static final String EXECUTABLE = System.getProperty(PythonInterpreter.PROP_EXECUTABLE, "python3");

    private static final String PLANTS = """
        CREATE OR REPLACE FUNCTION plant(d VARCHAR) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12'
        HANDLER = 'h' AS $$
        import os
        import sys
        class Planted:
            FL_PLANTED = True
            def find_spec(self, name, path=None, target=None):
                return None
        def h(d):
            with open(os.path.join(d, 'fl_planted_module.py'), 'w') as f:
                f.write("VALUE = 'HIJACKED'\\n")
            sys.path.insert(0, d)
            sys.meta_path.append(Planted())
            sys.setrecursionlimit(200)
            return 'planted'
        $$""";

    private static final String LOOKS = """
        CREATE OR REPLACE FUNCTION look(d VARCHAR) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12'
        HANDLER = 'h' AS $$
        import sys
        def h(d):
            try:
                import fl_planted_module
                found = fl_planted_module.VALUE
            except ImportError:
                found = 'absent'
            planted = any(getattr(f, 'FL_PLANTED', False) for f in sys.meta_path)
            return '|'.join([found, str(d in sys.path), str(planted), str(sys.getrecursionlimit())])
        $$""";

    @TempDir
    Path planted;

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
        engine.execute(PLANTS);
        engine.execute(LOOKS);
        return engine;
    }

    private String cell(final DatabaseEngine engine, final String sql) {
        return String.valueOf(engine.executeQuery(sql.replace("<d>", planted.toString())).getRows().get(0)
            .getValue(0));
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

    private void plantedPathStaysInItsStatement(final String runtime) {
        final DatabaseEngine first = engine(runtime);
        final String unplanted = cell(first, "SELECT look('<d>')");
        assertEquals("absent|False|False|", unplanted.substring(0, unplanted.lastIndexOf('|') + 1));
        assertEquals("planted", cell(first, "SELECT plant('<d>')"));
        assertEquals(unplanted, cell(first, "SELECT look('<d>')"), "the next statement");
        final DatabaseEngine second = engine(runtime);
        assertEquals(unplanted, cell(second, "SELECT look('<d>')"), "another engine on the thread");
    }

    @Test
    public void graalPyPutsTheImportSystemBack() {
        plantedPathStaysInItsStatement("graalpy");
    }

    @Test
    public void cpythonPutsTheImportSystemBack() {
        assumeTrue(cpythonRuns(), "needs the CPython interpreter " + EXECUTABLE);
        plantedPathStaysInItsStatement("cpython");
    }
}
