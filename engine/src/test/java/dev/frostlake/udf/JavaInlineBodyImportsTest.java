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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;

/**
 * An inline Java body compiles with the jars its IMPORTS name on the class path and runs beside them (live-verified):
 * a scalar function, a procedure and a table function read a jar's class, a jar of a newer Java runs at the runtime
 * version that reads it, two jars may be imported, a jar's class is the one a body runs where the body declares a
 * class of the same name, and the jar's static fields start anew for each statement and carry across its rows. A jar
 * may hold the handler's class where the body declares none, the body being compiled all the same.
 */
public class JavaInlineBodyImportsTest extends MeasuredCellsTestSupport {

    @TempDir
    Path work;

    /** Functions, a procedure and a table function whose inline bodies read imported jars. */
    @Test
    public void inlineBodiesReadImportedJars() throws IOException {
        put("h813.jar", "Helper813", "public class Helper813 { private static int calls; public static int "
            + "twice(int x) { calls++; return 2 * x; } public static int calls() { return calls; } }", "11");
        put("h813v.jar", "Helper813v", "public class Helper813v { public static int thrice(int x) { return 3 * x; } }",
            "17");
        assertCells(new String[][] {
            {"LIST @st813 ->> SELECT \"name\" FROM $1 ORDER BY 1",
                "st813/h813.jar ;; st813/h813v.jar"},
            {"CREATE OR REPLACE FUNCTION j813(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' IMPORTS = "
                + "('@st813/h813.jar') HANDLER = 'J813.run' AS 'class J813 { public static int run(int x) { return "
                + "Helper813.twice(x); } }'",
                "Function J813 successfully created."},
            {"SELECT j813(21)",
                "42"},
            {"CREATE OR REPLACE FUNCTION j813b(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11'  HANDLER = "
                + "'J813.run' AS 'class J813 { public static int run(int x) { return Helper813.twice(x); } }'",
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | class J813 { "
                + "public static int run(int x) { return Helper813.twice(x); } } |                                     "
                + "               ^ |   symbol:   variable Helper813 |   location: class J813 | 1 error |  in function "
                + "J813B with handler J813.run"},
            {"CREATE OR REPLACE FUNCTION j813d(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '17' IMPORTS = "
                + "('@st813/h813v.jar') HANDLER = 'J813.run' AS 'class J813 { public static int run(int x) { return "
                + "Helper813v.thrice(x); } }'",
                "Function J813D successfully created."},
            {"SELECT j813d(5)",
                "15"},
            {"CREATE OR REPLACE FUNCTION j813e(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' IMPORTS = "
                + "('@st813/nosuch813.jar') HANDLER = 'J813.run' AS 'class J813 { public static int run(int x) { "
                + "return Helper813.twice(x); } }'",
                "REFUSED Remote file 'nosuch813.jar' was not found. If you are running a copy command, please make "
                + "sure files are not deleted when they are being loaded or files are not being loaded into two "
                + "different tables concurrently with auto purge option."},
            {"CREATE OR REPLACE FUNCTION j813f(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' IMPORTS = "
                + "('@st813/h813.jar', '@st813/h813v.jar') HANDLER = 'J813.run' AS 'class J813 { public static int "
                + "run(int x) { return Helper813.twice(x); } }'",
                "Function J813F successfully created."},
            {"SELECT j813f(4)",
                "8"},
            {"CREATE OR REPLACE FUNCTION j813g() RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' IMPORTS = "
                + "('@st813/h813.jar') HANDLER = 'J813G.run' AS 'class J813G { public static int run() { "
                + "Helper813.twice(1); return Helper813.calls(); } }'",
                "Function J813G successfully created."},
            {"SELECT j813g()",
                "1"},
            {"SELECT j813g()",
                "1"},
            {"SELECT MAX(j813g()) FROM (SELECT 1 AS q UNION ALL SELECT 2 UNION ALL SELECT 3)",
                "3"},
            {"CREATE OR REPLACE PROCEDURE jp813(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') IMPORTS = ('@st813/h813.jar') HANDLER = 'JP813.run' AS 'import "
                + "com.snowflake.snowpark_java.*; class JP813 { public static int run(Session s, int x) { return "
                + "Helper813.twice(x); } }'",
                "Function JP813 successfully created."},
            {"CALL jp813(4)",
                "8"},
            {"CREATE OR REPLACE FUNCTION jt813(x INT) RETURNS TABLE (y INT) LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "IMPORTS = ('@st813/h813.jar') HANDLER = 'JT813' AS 'import java.util.stream.Stream; class OutputRow "
                + "{ public int y; public OutputRow(int y) { this.y = y; } } class JT813 { public static Class "
                + "getOutputClass() { return OutputRow.class; } public Stream<OutputRow> process(int x) { return "
                + "Stream.of(new OutputRow(Helper813.twice(x))); } }'",
                "Function JT813 successfully created."},
            {"SELECT y FROM TABLE(jt813(5))",
                "10"},
            {"CREATE OR REPLACE FUNCTION j813h(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' IMPORTS = "
                + "('@st813/h813.jar') HANDLER = 'J813.run' AS 'class Helper813 { public static int twice(int x) { "
                + "return 7; } } class J813 { public static int run(int x) { return Helper813.twice(x); } }'",
                "Function J813H successfully created."},
            {"SELECT j813h(1)",
                "2"},
            {"CREATE OR REPLACE FUNCTION j813i(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' IMPORTS = "
                + "('@st813/h813.jar') HANDLER = 'Helper813.twice'",
                "Function J813I successfully created."},
            {"SELECT j813i(6)",
                "12"},
        });
    }

    /** A handler class an imported jar holds, beside a body that declares another class, the same name or nothing. */
    @Test
    public void aJarHoldsTheHandlerTheBodyLacks() throws IOException {
        put("h813.jar", "Helper813", "public class Helper813 { public static int twice(int x) { return 2 * x; } }",
            "11");
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION j814b(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' IMPORTS = "
                + "('@st813/h813.jar') HANDLER = 'Helper813.twice' AS 'class Other814 { }'",
                "Function J814B successfully created."},
            {"SELECT j814b(3)",
                "6"},
            {"CREATE OR REPLACE FUNCTION j814c(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' IMPORTS = "
                + "('@st813/h813.jar') HANDLER = 'Helper813.twice' AS 'class Helper813 { }'",
                "Function J814C successfully created."},
            {"SELECT j814c(3)",
                "6"},
            {"CREATE OR REPLACE FUNCTION j814d(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' IMPORTS = "
                + "('@st813/h813.jar') HANDLER = 'J814.run' AS 'class J814 { public static int run(int x) { return "
                + "Helper813.twice(x) } }'",
                "REFUSED Error while compiling source: /InlineCode.java:1: error: ';' expected | class J814 { public "
                + "static int run(int x) { return Helper813.twice(x) } } |                                             "
                + "                         ^ | 1 error |  in function J814D with handler J814.run"},
            {"CREATE OR REPLACE FUNCTION j814g(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' IMPORTS = "
                + "('@st813/h813.jar') HANDLER = 'Helper813.twice' AS ''",
                "Function J814G successfully created."},
            {"SELECT j814g(3)",
                "6"},
        });
    }

    /** Compiles one class at a Java release into a jar of its own and puts the jar on the stage st813. */
    private void put(final String jar, final String className, final String source, final String release)
            throws IOException {
        final Path written = work.resolve(className + ".java");
        Files.write(written, source.getBytes(StandardCharsets.UTF_8));
        ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", release, "-d", work.toString(),
            written.toString());
        try (OutputStream file = Files.newOutputStream(work.resolve(jar));
             JarOutputStream packed = new JarOutputStream(file)) {
            packed.putNextEntry(new JarEntry(className + ".class"));
            packed.write(Files.readAllBytes(work.resolve(className + ".class")));
            packed.closeEntry();
        }
        engine.execute("CREATE STAGE IF NOT EXISTS st813");
        engine.execute("PUT 'file://" + work.resolve(jar) + "' @st813 AUTO_COMPRESS = FALSE OVERWRITE = TRUE");
    }
}
