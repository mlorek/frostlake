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

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * A Java function whose handler class an IMPORTS jar holds runs in a class loaded for the statement and the place
 * that calls it, as an inline body's: a static counter counts the statement's rows at one call, starts over in the
 * next statement and at a second call in the same statement (all live-verified).
 */
public class JavaImportedHandlerStateTest extends MeasuredCellsTestSupport {

    @Test
    public void anImportedClassStartsOverInEachStatementAndCall() throws IOException {
        final Path work = Files.createTempDirectory("imported_handler_state_");
        try {
            final Path jar = counterJar(work);
            engine.execute("CREATE OR REPLACE STAGE ihs");
            engine.execute("PUT file://" + jar.toAbsolutePath() + " @ihs AUTO_COMPRESS=FALSE");
            engine.execute("CREATE OR REPLACE FUNCTION jf() RETURNS INT LANGUAGE JAVA "
                + "IMPORTS = ('@ihs/counter.jar') HANDLER = 'Counter.next'");
            engine.execute("CREATE OR REPLACE FUNCTION ji() RETURNS INT LANGUAGE JAVA HANDLER = 'C.next' AS "
                + "'class C { static int n; public static int next() { return ++n; } }'");
            assertCells(new String[][] {
                {"SELECT jf()", "1"},
                {"SELECT jf()", "1"},
                {"SELECT jf(), jf()", "1|1"},
                {"SELECT jf() FROM VALUES (1), (2), (3) ORDER BY 1", "1 ;; 2 ;; 3"},
                {"SELECT jf(), ji()", "1|1"},
            });
        } finally {
            deleteRecursively(work.toFile());
        }
    }

    /** A jar holding one class whose static method counts its calls. */
    private static Path counterJar(final Path work) throws IOException {
        final Path classes = Files.createDirectories(work.resolve("classes"));
        Files.writeString(classes.resolve("Counter.java"),
            "public class Counter { static int n; public static int next() { return ++n; } }");
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler (run on a JDK)");
        }
        final StandardJavaFileManager files = compiler.getStandardFileManager(null, null, null);
        final List<String> options = Arrays.asList("--release", "11", "-d", classes.toString());
        final boolean compiled = compiler.getTask(null, files, null, options, null,
            files.getJavaFileObjects(classes.resolve("Counter.java").toFile())).call();
        files.close();
        if (!compiled) {
            throw new IllegalStateException("javac failed building the handler jar");
        }
        final Path jar = work.resolve("counter.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("Counter.class"));
            Files.copy(classes.resolve("Counter.class"), out);
            out.closeEntry();
        }
        return jar;
    }

    private static void deleteRecursively(final File file) {
        if (file == null || !file.exists()) {
            return;
        }
        final File[] children = file.isDirectory() ? file.listFiles() : null;
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
