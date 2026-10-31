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
import org.junit.jupiter.api.function.Executable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GraalPy runs one venv a JVM: its interpreters are per thread whichever engine calls them, so once one is built an
 * engine whose {@code python.venv} names another venv is refused at its first Python call, in words that say why,
 * instead of importing another venv's packages; the engines that agree keep running.
 */
public class GraalPyVenvPerJvmTest {

    private static final String UDF = """
        CREATE OR REPLACE FUNCTION two() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.12' HANDLER = 'h'
        AS $$
        def h():
            return 2
        $$""";

    private final List<DatabaseEngine> engines = new ArrayList<>();
    private Path venv;

    @AfterEach
    public void tearDown() {
        for (final DatabaseEngine engine : engines) {
            engine.shutdown();
        }
        if (venv != null) {
            new File(venv.toFile(), "pyvenv.cfg").delete();
            venv.toFile().delete();
        }
    }

    private DatabaseEngine engine(final Properties settings) {
        final DatabaseEngine engine = new DatabaseEngine(new EngineConfig(settings));
        engines.add(engine);
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA PUBLIC");
        return engine;
    }

    @Test
    public void anotherVenvIsRefusedOnceTheInterpretersRun() throws IOException {
        final DatabaseEngine plain = engine(new Properties());
        plain.execute(UDF);
        assertEquals(2L, ((Number) plain.executeQuery("SELECT two()").getRows().get(0).getValue(0)).longValue());

        venv = Files.createTempDirectory("other_venv_");
        Files.write(venv.resolve("pyvenv.cfg"), "home = /nowhere\n".getBytes());
        final Properties named = new Properties();
        named.setProperty(EngineConfig.PROP_PYTHON_VENV, venv.toString());
        final DatabaseEngine other = engine(named);
        // CREATE FUNCTION compiles the handler on the interpreter, the engine's first Python call.
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                other.execute(UDF);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains("python.venv '" + venv + "' cannot be used: GraalPy "
            + "runs one venv a JVM, and its interpreters here "), String.valueOf(refused.getMessage()));

        assertEquals(2L, ((Number) plain.executeQuery("SELECT two()").getRows().get(0).getValue(0)).longValue());
    }
}
