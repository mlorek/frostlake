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
 * A Java table function whose handler class an IMPORTS jar holds has the class judged as the function is created, as
 * an inline one's is: two process methods of the function's argument count leave the call undecided, the function is
 * refused, and none is left (live-verified).
 */
public class JarTableFunctionOverloadTest extends MeasuredCellsTestSupport {

    /** A table function's handler with two process methods of one argument each. */
    private static final String TWO = """
        import java.util.stream.Stream;

        public class Two {
            public static Class getOutputClass() {
                return TwoRow.class;
            }

            public Stream<TwoRow> process(int k) {
                return Stream.of(new TwoRow(k));
            }

            public Stream<TwoRow> process(String k) {
                return Stream.of(new TwoRow(-1));
            }
        }
        """;

    /** The row the handler writes: its public field is the function's column. */
    private static final String TWO_ROW = """
        public class TwoRow {
            public int n;

            public TwoRow(int n) {
                this.n = n;
            }
        }
        """;

    @Test
    public void aJarHandlersOverloadsAreRefusedAtCreate() throws IOException {
        final Path work = Files.createTempDirectory("jar_table_function_overload_");
        try {
            final Path jar = twoJar(work);
            engine.execute("CREATE OR REPLACE STAGE jtfo_stage");
            engine.execute("PUT file://" + jar.toAbsolutePath() + " @jtfo_stage AUTO_COMPRESS=FALSE");
            assertCells(new String[][] {
                {"CREATE OR REPLACE FUNCTION jtfo(k INT) RETURNS TABLE (n INT) LANGUAGE JAVA "
                    + "IMPORTS = ('@jtfo_stage/two.jar') HANDLER = 'Two'",
                    "REFUSED Cannot determine which implementation of handler \"process\" to invoke since there are "
                        + "multiple definitions with 1 arguments in function JTFO with handler Two"},
                {"SELECT * FROM TABLE(jtfo(1))", "REFUSED SQL compilation error: | Unknown table function JTFO"},
            });
        } finally {
            deleteRecursively(work.toFile());
        }
    }

    /** A jar holding the handler and its output row class. */
    private static Path twoJar(final Path work) throws IOException {
        final Path classes = Files.createDirectories(work.resolve("classes"));
        Files.writeString(classes.resolve("Two.java"), TWO);
        Files.writeString(classes.resolve("TwoRow.java"), TWO_ROW);
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler (run on a JDK)");
        }
        final StandardJavaFileManager files = compiler.getStandardFileManager(null, null, null);
        final List<String> options = Arrays.asList("--release", "11", "-d", classes.toString());
        final boolean compiled = compiler.getTask(null, files, null, options, null,
            files.getJavaFileObjects(classes.resolve("Two.java").toFile(),
                classes.resolve("TwoRow.java").toFile())).call();
        files.close();
        if (!compiled) {
            throw new IllegalStateException("javac failed building the handler jar");
        }
        final Path jar = work.resolve("two.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (final String entry : new String[] {"Two.class", "TwoRow.class"}) {
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
