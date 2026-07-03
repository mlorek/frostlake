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

package dev.frostlake.executor.udf;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies UDFs and procedures whose handler lives in a pre-compiled JAR (the {@code IMPORTS = ('…jar')} +
 * {@code HANDLER = 'Class.method'} form, no inline {@code AS} body) execute for both Java and Scala. The
 * JARs are built in a temp dir during the run — Java via {@code javac} (ToolProvider) and Scala via the
 * in-process {@link ScalaCompiler} — then loaded through {@link JarHandlerLoader}. See docs/functions.md.
 */
public class JarHandlerTest {

    private DatabaseEngine engine;
    private Path workDir;
    private Path javaJar;
    private Path scalaJar;

    @BeforeEach
    public void setUp() throws Exception {
        workDir = Files.createTempDirectory("jar_handler_test_");
        javaJar = buildJavaJar();
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
    public void javaUdfFromJar() {
        engine.execute("CREATE FUNCTION j_greet(n VARCHAR) RETURNS VARCHAR LANGUAGE JAVA "
            + "IMPORTS = ('" + javaJar + "') HANDLER = 'JUdf.greet'");
        final ResultSet rs = engine.executeQuery("SELECT j_greet('World')");
        assertEquals("Hi World", rs.getRows().get(0).getValue(0).toString());
    }

    @Test
    public void javaProcedureFromJar() {
        engine.execute("CREATE PROCEDURE j_proc() RETURNS VARCHAR LANGUAGE JAVA "
            + "IMPORTS = ('" + javaJar + "') HANDLER = 'JProc.run'");
        final ResultSet rs = engine.executeQuery("CALL j_proc()");
        assertEquals("jproc", rs.getRows().get(0).getValue(0).toString());
    }

    @Test
    public void scalaUdfFromJar() {
        engine.execute("CREATE FUNCTION s_double(x INTEGER) RETURNS INTEGER LANGUAGE SCALA "
            + "IMPORTS = ('" + scalaJar + "') HANDLER = 'ScUdf.doubleIt'");
        final ResultSet rs = engine.executeQuery("SELECT s_double(21)");
        assertEquals("42", rs.getRows().get(0).getValue(0).toString());
    }

    @Test
    public void scalaProcedureFromJar() {
        engine.execute("CREATE PROCEDURE s_proc() RETURNS VARCHAR LANGUAGE SCALA "
            + "IMPORTS = ('" + scalaJar + "') HANDLER = 'ScProc.run'");
        final ResultSet rs = engine.executeQuery("CALL s_proc()");
        assertEquals("scproc", rs.getRows().get(0).getValue(0).toString());
    }

    // ── JAR building ────────────────────────────────────────────────────────

    /** javac a UDF (static) + a procedure (instance, takes a Snowpark Session) class, then jar them. */
    private Path buildJavaJar() throws IOException {
        final Path out = Files.createDirectories(workDir.resolve("javaOut"));
        writeSource(out, "JUdf.java",
            "public class JUdf { public static String greet(String n) { return \"Hi \" + n; } }");
        writeSource(out, "JProc.java",
            "public class JProc { public String run(com.snowflake.snowpark_java.Session s) { return \"jproc\"; } }");

        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler (run on a JDK)");
        }
        final StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null);
        final List<String> opts = Arrays.asList(
            "-classpath", System.getProperty("java.class.path"), "-d", out.toString());
        final boolean ok = compiler.getTask(null, fm, null, opts, null,
            fm.getJavaFileObjects(out.resolve("JUdf.java").toFile(), out.resolve("JProc.java").toFile())).call();
        fm.close();
        if (!ok) {
            throw new IllegalStateException("javac failed building the Java handler jar");
        }
        return jarClassesIn(out, workDir.resolve("handlers-java.jar"));
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

    private static void writeSource(final Path dir, final String name, final String src) throws IOException {
        Files.writeString(dir.resolve(name), src);
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
