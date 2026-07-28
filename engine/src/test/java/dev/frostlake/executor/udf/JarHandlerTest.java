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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies UDFs and procedures whose handler lives in a pre-compiled JAR (the {@code IMPORTS = ('…jar')} +
 * {@code HANDLER = 'Class.method'} form, no inline {@code AS} body) execute. The JARs are built in a temp
 * dir during the run via {@code javac} (ToolProvider), then loaded through {@link JarHandlerLoader}. The
 * Scala variant lives in the rt-scala module's {@code ScalaJarHandlerTest}. See docs/functions.md.
 */
public class JarHandlerTest {

    private DatabaseEngine engine;
    private Path workDir;
    private Path javaJar;

    @BeforeEach
    public void setUp() throws Exception {
        workDir = Files.createTempDirectory("jar_handler_test_");
        javaJar = buildJavaJar();

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
    public void stageImportPutsSiblingJarsOnTheClasspath() throws Exception {
        // Real handler JARs are thin — on Snowflake their dependencies arrive via the PACKAGES
        // transitive closure. The stage directory doubles as the dependency set: the handler jar names
        // a class from a SEPARATE dep jar sitting beside it, and only the handler jar is IMPORTed.
        final Path stageDir = Files.createDirectories(workDir.resolve("stage_jars"));
        final Path depOut = Files.createDirectories(workDir.resolve("depOut"));
        writeSource(depOut, "DepHelper.java",
            "public class DepHelper { public static String tag() { return \"from-dep\"; } }");
        compileAll(depOut, System.getProperty("java.class.path"), "DepHelper.java");
        jarClassesIn(depOut, stageDir.resolve("dep-helper.jar"));

        final Path handlerOut = Files.createDirectories(workDir.resolve("handlerOut"));
        writeSource(handlerOut, "SibUdf.java",
            "public class SibUdf { public static String tagOf(String n) { return DepHelper.tag() + \":\" + n; } }");
        compileAll(handlerOut, System.getProperty("java.class.path")
            + File.pathSeparator + depOut, "SibUdf.java");
        jarClassesIn(handlerOut, stageDir.resolve("sib-handler.jar"));

        engine.execute("CREATE STAGE dep_stage URL='file://" + stageDir + "'");
        engine.execute("CREATE FUNCTION sib_tag(n VARCHAR) RETURNS VARCHAR LANGUAGE JAVA "
            + "IMPORTS = ('@dep_stage/sib-handler.jar') HANDLER = 'SibUdf.tagOf'");
        final ResultSet rs = engine.executeQuery("SELECT sib_tag('x')");
        assertEquals("from-dep:x", rs.getRows().get(0).getValue(0).toString());
    }

    @Test
    public void childFirstLoadingPrefersTheJarsOwnDependencyVersion() throws Exception {
        // A handler jar (or a stage-sibling dep jar) may carry a DIFFERENT version of a library that
        // also sits on the engine's classpath — like the vendor plugin-output parsers built against
        // ANTLR 4.11 while the engine ships 4.13. The jar's version must win (Snowflake runs a UDF
        // against its PACKAGES dependency set, not the warehouse's internals). The probe shadows a
        // class the engine really has, and calls a METHOD so javac cannot constant-inline the answer.
        final Path out = Files.createDirectories(workDir.resolve("shadowOut"));
        writeSource(out, "RuntimeMetaData.java",
            "package org.antlr.v4.runtime; "
            + "public class RuntimeMetaData { public static String getRuntimeVersion() { return \"fake-4.11\"; } }");
        writeSource(out, "ShadowUdf.java",
            "public class ShadowUdf { public static String probe() { "
            + "return org.antlr.v4.runtime.RuntimeMetaData.getRuntimeVersion(); } }");
        compileAll(out, System.getProperty("java.class.path"), "RuntimeMetaData.java", "ShadowUdf.java");
        final Path jar = jarClassesIn(out, workDir.resolve("shadow-handler.jar"));

        engine.execute("CREATE FUNCTION shadow_probe() RETURNS VARCHAR LANGUAGE JAVA "
            + "IMPORTS = ('" + jar + "') HANDLER = 'ShadowUdf.probe'");
        final ResultSet rs = engine.executeQuery("SELECT shadow_probe()");
        assertEquals("fake-4.11", rs.getRows().get(0).getValue(0).toString());
    }

    @Test
    public void handlerClassIsLoadedOnceAndReusedAcrossCalls() throws Exception {
        // The handler used to be re-loaded through a fresh classloader on EVERY invocation — one
        // class-load per ROW for jar UDFs. The loaded class is now cached (keyed by class name +
        // jar size/mtime), which a static counter observes: it keeps incrementing across calls
        // instead of resetting with a fresh class each time.
        final Path out = Files.createDirectories(workDir.resolve("counterOut"));
        writeSource(out, "CounterUdf.java",
            "public class CounterUdf { static int n = 0; public static int bump(int ignored) { return ++n; } }");
        compileAll(out, System.getProperty("java.class.path"), "CounterUdf.java");
        final Path jar = jarClassesIn(out, workDir.resolve("counter-handler.jar"));
        engine.execute("CREATE FUNCTION bump(x INTEGER) RETURNS INTEGER LANGUAGE JAVA "
            + "IMPORTS = ('" + jar + "') HANDLER = 'CounterUdf.bump'");

        engine.executeQuery("SELECT bump(0)");
        final ResultSet rs = engine.executeQuery("SELECT bump(0)");
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void handlerConsoleOutputIsCapturedNotPrinted() throws Exception {
        // Snowflake never surfaces a UDF's System.out/System.err to the client (the vendor ANTLR
        // parsers print recovered-parse noise on every call). The engine must route it to the log,
        // and non-UDF writes must keep passing through to whatever streams the embedder installed.
        final Path out = Files.createDirectories(workDir.resolve("noisyOut"));
        writeSource(out, "NoisyUdf.java",
            "public class NoisyUdf { public static String shout(String n) { "
            + "System.out.println(\"udf-stdout-noise\"); "
            + "System.err.println(\"udf-stderr-noise\"); return n; } }");
        compileAll(out, System.getProperty("java.class.path"), "NoisyUdf.java");
        final Path jar = jarClassesIn(out, workDir.resolve("noisy-handler.jar"));
        engine.execute("CREATE FUNCTION noisy(n VARCHAR) RETURNS VARCHAR LANGUAGE JAVA "
            + "IMPORTS = ('" + jar + "') HANDLER = 'NoisyUdf.shout'");

        final PrintStream priorOut = System.out;
        final PrintStream priorErr = System.err;
        final ByteArrayOutputStream consoleOut = new ByteArrayOutputStream();
        final ByteArrayOutputStream consoleErr = new ByteArrayOutputStream();
        System.setOut(new PrintStream(consoleOut, true));
        System.setErr(new PrintStream(consoleErr, true));
        try {
            final ResultSet rs = engine.executeQuery("SELECT noisy('ok')");
            assertEquals("ok", rs.getRows().get(0).getValue(0).toString());
            System.out.println("outside-udf-write");
        } finally {
            System.setOut(priorOut);
            System.setErr(priorErr);
        }
        assertFalse(consoleOut.toString().contains("udf-stdout-noise"),
            "UDF stdout leaked to the console: " + consoleOut);
        assertFalse(consoleErr.toString().contains("udf-stderr-noise"),
            "UDF stderr leaked to the console: " + consoleErr);
        assertTrue(consoleOut.toString().contains("outside-udf-write"),
            "non-UDF write should pass through: " + consoleOut);
    }

    @Test
    public void arrayParameterMarshalsToStringArrayAndDefaultsApply() throws Exception {
        // Snowflake's Java UDF type mapping passes an ARRAY as String[]; a trailing DEFAULT NULL
        // parameter may be omitted and must arrive as a null reference (not fail method resolution).
        final Path out = Files.createDirectories(workDir.resolve("arrOut"));
        writeSource(out, "ArrUdf.java",
            "public class ArrUdf { public static String join(String sep, String[] parts) { "
            + "if (parts == null) return \"none\"; "
            + "StringBuilder b = new StringBuilder(); "
            + "for (int i = 0; i < parts.length; i++) { if (i > 0) b.append(sep); b.append(parts[i]); } "
            + "return b.toString(); } }");
        compileAll(out, System.getProperty("java.class.path"), "ArrUdf.java");
        final Path jar = jarClassesIn(out, workDir.resolve("arr-handler.jar"));

        engine.execute("CREATE FUNCTION arr_join(sep VARCHAR, parts ARRAY DEFAULT NULL) "
            + "RETURNS VARCHAR LANGUAGE JAVA "
            + "IMPORTS = ('" + jar + "') HANDLER = 'ArrUdf.join'");
        assertEquals("a-b",
            engine.executeQuery("SELECT arr_join('-', ARRAY_CONSTRUCT('a', 'b'))").getRows().get(0).getValue(0).toString());
        assertEquals("none",
            engine.executeQuery("SELECT arr_join('-')").getRows().get(0).getValue(0).toString());
    }

    private static void compileAll(final Path srcDir, final String classpath, final String... files) throws IOException {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler (run on a JDK)");
        }
        final StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null);
        final File[] sources = new File[files.length];
        for (int i = 0; i < files.length; i++) {
            sources[i] = srcDir.resolve(files[i]).toFile();
        }
        final List<String> opts = Arrays.asList("-classpath", classpath, "-d", srcDir.toString());
        final boolean ok = compiler.getTask(null, fm, null, opts, null, fm.getJavaFileObjects(sources)).call();
        fm.close();
        if (!ok) {
            throw new IllegalStateException("javac failed for " + Arrays.toString(files));
        }
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
