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
 * A Java table function whose handler class an IMPORTS jar holds runs in a class loaded for the statement and the
 * place that calls it: a static counter starts over in each statement and at a second call in the same statement, and
 * carries from one row of a LATERAL call to the next (all live-verified).
 */
public class JarTableFunctionClassTest extends MeasuredCellsTestSupport {

    /** A table function's handler whose static field counts the rows it is called for. */
    private static final String COUNTED = """
        import java.util.stream.Stream;

        public class Counted {
            static int calls;

            public static Class getOutputClass() {
                return CountedRow.class;
            }

            public Stream<CountedRow> process(int k) {
                calls++;
                return Stream.of(new CountedRow(calls * 10 + k));
            }
        }
        """;

    /** The row the handler writes: its public field is the function's column. */
    private static final String COUNTED_ROW = """
        public class CountedRow {
            public int n;

            public CountedRow(int n) {
                this.n = n;
            }
        }
        """;

    @Test
    public void aJarTableFunctionsClassLastsTheStatementsCall() throws IOException {
        final Path work = Files.createTempDirectory("jar_table_function_class_");
        try {
            final Path jar = countedJar(work);
            engine.execute("CREATE OR REPLACE STAGE jtfc_stage");
            engine.execute("PUT file://" + jar.toAbsolutePath() + " @jtfc_stage AUTO_COMPRESS=FALSE");
            engine.execute("CREATE OR REPLACE FUNCTION jtfc(k INT) RETURNS TABLE (n INT) LANGUAGE JAVA "
                + "IMPORTS = ('@jtfc_stage/counted.jar') HANDLER = 'Counted'");
            assertCells(new String[][] {
                {"SELECT * FROM TABLE(jtfc(1))", "11"},
                {"SELECT * FROM TABLE(jtfc(1))", "11"},
                {"SELECT * FROM TABLE(jtfc(1)) a, TABLE(jtfc(2)) b", "11|12"},
                {"SELECT t.n FROM (SELECT 1 AS k UNION ALL SELECT 2) v, TABLE(jtfc(v.k)) t ORDER BY 1", "11 ;; 22"},
            });
        } finally {
            deleteRecursively(work.toFile());
        }
    }

    /** A jar holding the counting handler and its output row class. */
    private static Path countedJar(final Path work) throws IOException {
        final Path classes = Files.createDirectories(work.resolve("classes"));
        Files.writeString(classes.resolve("Counted.java"), COUNTED);
        Files.writeString(classes.resolve("CountedRow.java"), COUNTED_ROW);
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler (run on a JDK)");
        }
        final StandardJavaFileManager files = compiler.getStandardFileManager(null, null, null);
        final List<String> options = Arrays.asList("--release", "11", "-d", classes.toString());
        final boolean compiled = compiler.getTask(null, files, null, options, null,
            files.getJavaFileObjects(classes.resolve("Counted.java").toFile(),
                classes.resolve("CountedRow.java").toFile())).call();
        files.close();
        if (!compiled) {
            throw new IllegalStateException("javac failed building the handler jar");
        }
        final Path jar = work.resolve("counted.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (final String entry : new String[] {"Counted.class", "CountedRow.class"}) {
                out.putNextEntry(new JarEntry(entry));
                Files.copy(classes.resolve(entry), out);
                out.closeEntry();
            }
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
