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
import dev.frostlake.config.EngineConfig;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies a UDF whose handler JAR is referenced through an S3-backed stage (or a bare {@code s3://} URL)
 * loads by resolving the S3 path to a local file via the {@code stage.s3.localMappings} engine setting —
 * the local/CI workflow where a JAR lives in S3 in production but on local disk for testing. The JAR is
 * built in a temp dir, an {@code s3://test-bucket/lib} prefix is mapped to that dir, and a stage at that
 * S3 URL is created; IMPORTS then resolve to the local JAR. See {@link dev.frostlake.config.S3PathResolver}.
 */
public class StageJarImportTest {

    private DatabaseEngine engine;
    private Path workDir;
    private Path jarDir;

    @BeforeEach
    public void setUp() throws Exception {
        workDir = Files.createTempDirectory("stage_jar_import_");
        jarDir = Files.createDirectories(workDir.resolve("jars"));
        buildJavaJar(jarDir.resolve("handlers.jar"));

        // Map the S3 prefix used by the stage / direct s3:// IMPORTS to the local JAR directory.
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_STAGE_S3_LOCAL_MAPPINGS, "s3://test-bucket/lib=" + jarDir);

        engine = new DatabaseEngine(config);
        engine.execute("CREATE DATABASE db");
        engine.execute("USE DATABASE db");
        engine.execute("CREATE SCHEMA s");
        engine.execute("USE SCHEMA s");
        engine.execute("CREATE STAGE jar_stage URL='s3://test-bucket/lib'");
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (engine != null) {
            engine.shutdown();
        }
        deleteRecursively(workDir.toFile());
    }

    @Test
    public void javaUdfFromS3BackedStageImport() {
        // IMPORTS names the JAR via the stage; @jar_stage -> s3://test-bucket/lib -> local jarDir.
        engine.execute("CREATE FUNCTION j_greet(n VARCHAR) RETURNS VARCHAR LANGUAGE JAVA "
            + "IMPORTS = ('@jar_stage/handlers.jar') HANDLER = 'JUdf.greet'");
        final ResultSet rs = engine.executeQuery("SELECT j_greet('World')");
        assertEquals("Hi World", rs.getRows().get(0).getValue(0).toString());
    }

    @Test
    public void javaUdfFromDirectS3UrlImport() {
        // IMPORTS names the JAR by its s3:// URL directly; resolved by the same mapping.
        engine.execute("CREATE FUNCTION j_greet2(n VARCHAR) RETURNS VARCHAR LANGUAGE JAVA "
            + "IMPORTS = ('s3://test-bucket/lib/handlers.jar') HANDLER = 'JUdf.greet'");
        final ResultSet rs = engine.executeQuery("SELECT j_greet2('Stage')");
        assertEquals("Hi Stage", rs.getRows().get(0).getValue(0).toString());
    }

    @Test
    public void listHonorsS3MappingAndSeesStagedJar() {
        // The JAR was written into the mapped local dir in setUp; LIST resolves the S3-backed stage's
        // local path through the same stage.s3.localMappings setting, so it sees the file there.
        final ResultSet rs = engine.executeQuery("LIST @jar_stage");
        boolean found = false;
        for (final Row row : rs.getRows()) {
            if ("handlers.jar".equals(String.valueOf(row.getValue(0)))) {
                found = true;
            }
        }
        assertTrue(found, "LIST @jar_stage should see handlers.jar in the mapped local directory");
    }

    // ── JAR building (javac a single static-method handler, then jar the class) ──────────────────

    private static void buildJavaJar(final Path jarFile) throws IOException {
        final Path out = Files.createDirectories(jarFile.getParent().resolve("classes"));
        Files.writeString(out.resolve("JUdf.java"),
            "public class JUdf { public static String greet(String n) { return \"Hi \" + n; } }");

        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler (run on a JDK)");
        }
        final StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null);
        final List<String> opts = Arrays.asList(
            "-classpath", System.getProperty("java.class.path"), "-d", out.toString());
        final boolean ok = compiler.getTask(null, fm, null, opts, null,
            fm.getJavaFileObjects(out.resolve("JUdf.java").toFile())).call();
        fm.close();
        if (!ok) {
            throw new IllegalStateException("javac failed building the handler jar");
        }

        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarFile))) {
            addClasses(jos, out.toFile(), out.toFile());
        }
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
