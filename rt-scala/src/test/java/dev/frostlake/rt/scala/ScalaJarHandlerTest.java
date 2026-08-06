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

package dev.frostlake.rt.scala;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies Scala UDFs and procedures whose handler lives in a pre-compiled JAR (the
 * {@code IMPORTS = ('…jar')} + {@code HANDLER = 'Class.method'} form, no inline {@code AS} body)
 * execute — a Scala {@code object} loads via its {@code ClassName$} singleton, a plain class like a
 * Java one. The JAR is built in a temp dir during the run via the in-process {@link ScalaCompiler}.
 * The Java variant of this test lives in the engine's {@code JarHandlerTest}.
 */
public class ScalaJarHandlerTest {

    private DatabaseEngine engine;
    private Path workDir;
    private Path scalaJar;

    @BeforeEach
    public void setUp() throws Exception {
        workDir = Files.createTempDirectory("scala_jar_handler_test_");
        scalaJar = buildScalaJar();

        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE db");
        engine.execute("USE DATABASE db");
        engine.execute("CREATE SCHEMA s");
        engine.execute("USE SCHEMA s");
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (engine != null) {
            engine.shutdown();
        }
        deleteRecursively(workDir.toFile());
    }

    @Test
    public void scalaUdfFromJar() {
        engine.execute("CREATE FUNCTION s_double(x INTEGER) RETURNS INTEGER LANGUAGE SCALA "
            + "RUNTIME_VERSION = '2.12' "
            + "IMPORTS = ('" + scalaJar + "') HANDLER = 'ScUdf.doubleIt'");
        final ResultSet rs = engine.executeQuery("SELECT s_double(21)");
        assertEquals("42", rs.getRows().get(0).getValue(0).toString());
    }

    @Test
    public void scalaProcedureFromJar() {
        engine.execute("CREATE PROCEDURE s_proc() RETURNS VARCHAR LANGUAGE SCALA "
            + "RUNTIME_VERSION = '2.12' "
            + "IMPORTS = ('" + scalaJar + "') HANDLER = 'ScProc.run'");
        final ResultSet rs = engine.executeQuery("CALL s_proc()");
        assertEquals("scproc", rs.getRows().get(0).getValue(0).toString());
    }

    /** Compile a Scala UDF object + a procedure class in-process, then jar the resulting .class files. */
    private Path buildScalaJar() throws Exception {
        final Path out = Files.createDirectories(workDir.resolve("scalaOut"));
        // One source, two top-level definitions; ScalaCompiler emits a .class for each into `out`.
        ScalaCompiler.compile(
            "object ScUdf { def doubleIt(x: Int): Int = x * 2 }\n"
            + "class ScProc { def run(session: com.snowflake.snowpark_java.Session): String = \"scproc\" }\n",
            "ScUdf", out);
        return jarClassesIn(out, workDir.resolve("handlers-scala.jar"));
    }

    private static Path jarClassesIn(final Path classDir, final Path jarFile) throws IOException {
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarFile))) {
            addClasses(jos, classDir.toFile(), classDir.toFile());
        }
        return jarFile;
    }

    private static void addClasses(final JarOutputStream jos, final File root, final File f) throws IOException {
        if (f.isDirectory()) {
            final File[] children = f.listFiles();
            if (children != null) {
                for (final File child : children) {
                    addClasses(jos, root, child);
                }
            }
        } else if (f.getName().endsWith(".class")) {
            final String entry = root.toURI().relativize(f.toURI()).getPath();
            jos.putNextEntry(new JarEntry(entry));
            Files.copy(f.toPath(), jos);
            jos.closeEntry();
        }
    }

    private static void deleteRecursively(final File f) {
        if (f == null || !f.exists()) {
            return;
        }
        if (f.isDirectory()) {
            final File[] children = f.listFiles();
            if (children != null) {
                for (final File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        f.delete();
    }
}
