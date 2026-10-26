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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * A Java function's or procedure's handler an IMPORTS jar holds is judged as the routine is created, as an inline
 * body's is: two public methods of the handler's name and the routine's argument count, none, one of another count
 * and one that is not public are refused in the account's words, a procedure's Session counted among its arguments,
 * and so is an instance handler whose class has no public zero-argument constructor; no routine is left. Once the
 * jar is replaced, a call refuses as the CREATE would refuse it then: the method missing in the CREATE's words, the
 * class as the account's class loader refuses it (all live-verified).
 */
public class JarHandlerMethodTest extends MeasuredCellsTestSupport {

    private static final String SESSION = "com.snowflake.snowpark_java.Session";

    /** The first jar's classes, by name. */
    private static Map<String, String> firstJar() {
        final Map<String, String> classes = new LinkedHashMap<String, String>();
        classes.put("JhOk", "public class JhOk { public static long ok(long x) { return x * 2; } }");
        classes.put("JhOver", "public class JhOver { public static long f(long x) { return x + 1; }"
            + " public static String f(String x) { return x + \"!\"; } }");
        classes.put("JhBoxed", "public class JhBoxed { public static Long b(Long x) { return x + 1; }"
            + " public static Long b(Integer x) { return x + 2L; } }");
        classes.put("JhMiss", "public class JhMiss { public static long g(long x) { return x; } }");
        classes.put("JhArity", "public class JhArity { public static long h(long x, long y) { return x + y; } }");
        classes.put("JhPriv", "public class JhPriv { static long p(long x) { return x; } }");
        classes.put("JhInst", "public class JhInst { public JhInst() { }"
            + " public long inst(long x) { return x * 3; } }");
        classes.put("JhNoCtor", "public class JhNoCtor { private JhNoCtor() { }"
            + " public long m(long x) { return x; } }");
        classes.put("JpOk", "public class JpOk { public String run(" + SESSION + " s, long x)"
            + " { return \"ok \" + x; } }");
        classes.put("JpOver", "public class JpOver { public String run(" + SESSION + " s, long x) { return \"n\"; }"
            + " public String run(" + SESSION + " s, String x) { return \"t\"; } }");
        classes.put("JpMiss", "public class JpMiss { public String other(" + SESSION + " s, long x)"
            + " { return \"o\"; } }");
        classes.put("JpArity", "public class JpArity { public String run(" + SESSION + " s) { return \"a\"; } }");
        classes.put("JpPriv", "public class JpPriv { String run(" + SESSION + " s, long x) { return \"p\"; } }");
        return classes;
    }

    /** The jar that replaces it: JhOk's handler method renamed, and no JpOk. */
    private static Map<String, String> secondJar() {
        final Map<String, String> classes = new LinkedHashMap<String, String>();
        classes.put("JhOk", "public class JhOk { public static long ok2(long x) { return x * 5; } }");
        return classes;
    }

    @Test
    public void anImportedHandlerIsJudgedAtCreateAndAgainOnceItsJarIsReplaced() throws IOException {
        final Path work = Files.createTempDirectory("jar_handler_method_");
        try {
            final Path jar = buildJar(Files.createDirectories(work.resolve("first")), firstJar());
            final Path replaced = buildJar(Files.createDirectories(work.resolve("second")), secondJar());
            engine.execute("CREATE OR REPLACE STAGE jhm_st");
            engine.execute("PUT file://" + jar.toAbsolutePath() + " @jhm_st AUTO_COMPRESS = FALSE OVERWRITE = TRUE");
            assertCells(new String[][] {
                {"CREATE OR REPLACE FUNCTION jh_ok(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = "
                    + "('@jhm_st/jh.jar') HANDLER = 'JhOk.ok'",
                    "Function JH_OK successfully created."},
                {"SELECT jh_ok(21)",
                    "42"},
                {"CREATE OR REPLACE FUNCTION jh_over_num(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = "
                    + "('@jhm_st/jh.jar') HANDLER = 'JhOver.f'",
                    "REFUSED Cannot determine which implementation of handler \"f\" to invoke since there "
                        + "are multiple definitions with 1 arguments in function JH_OVER_NUM with handler JhOver.f"},
                {"CREATE OR REPLACE FUNCTION jh_over_txt(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA IMPORTS "
                    + "= ('@jhm_st/jh.jar') HANDLER = 'JhOver.f'",
                    "REFUSED Cannot determine which implementation of handler \"f\" to invoke since there "
                        + "are multiple definitions with 1 arguments in function JH_OVER_TXT with handler JhOver.f"},
                {"CREATE OR REPLACE FUNCTION jh_boxed(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = "
                    + "('@jhm_st/jh.jar') HANDLER = 'JhBoxed.b'",
                    "REFUSED Cannot determine which implementation of handler \"b\" to invoke since there "
                        + "are multiple definitions with 1 arguments in function JH_BOXED with handler JhBoxed.b"},
                {"CREATE OR REPLACE FUNCTION jh_miss(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = "
                    + "('@jhm_st/jh.jar') HANDLER = 'JhMiss.nope'",
                    "REFUSED Failed to find a public method named \"nope\" with 1 arguments in function "
                        + "JH_MISS with handler JhMiss.nope"},
                {"CREATE OR REPLACE FUNCTION jh_arity(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = "
                    + "('@jhm_st/jh.jar') HANDLER = 'JhArity.h'",
                    "REFUSED Failed to find a public method named \"h\" with 1 arguments in function "
                        + "JH_ARITY with handler JhArity.h"},
                {"CREATE OR REPLACE FUNCTION jh_priv(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = "
                    + "('@jhm_st/jh.jar') HANDLER = 'JhPriv.p'",
                    "REFUSED Failed to find a public method named \"p\" with 1 arguments in function JH_PRIV "
                        + "with handler JhPriv.p"},
                {"CREATE OR REPLACE FUNCTION jh_noclass(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = "
                    + "('@jhm_st/jh.jar') HANDLER = 'JhNoSuch.f'",
                    "REFUSED User Error Report:  | Java Stack Trace: | java.lang.ClassNotFoundException: "
                        + "JhNoSuch | \tat java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:476) | "
                        + "\tat java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:589) | \tat "
                        + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:522) |  in function "
                        + "JH_NOCLASS with handler JhNoSuch.f"},
                {"CREATE OR REPLACE FUNCTION jh_inst(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = "
                    + "('@jhm_st/jh.jar') HANDLER = 'JhInst.inst'",
                    "Function JH_INST successfully created."},
                {"SELECT jh_inst(2)",
                    "6"},
                {"CREATE OR REPLACE FUNCTION jh_noctor(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = "
                    + "('@jhm_st/jh.jar') HANDLER = 'JhNoCtor.m'",
                    "REFUSED The provided method is non-static, but the enclosing class does not have a "
                        + "public zero-argument constructor in function JH_NOCTOR with handler JhNoCtor.m"},
                {"SELECT jh_noctor(2)",
                    "REFUSED SQL compilation error: | Unknown function JH_NOCTOR."},
                {"CREATE OR REPLACE FUNCTION jh_nodot(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = "
                    + "('@jhm_st/jh.jar') HANDLER = 'JhOk'",
                    "REFUSED Invalid function name JhOk in function JH_NODOT with handler JhOk"},
                {"CREATE OR REPLACE FUNCTION jh_pkg(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = "
                    + "('@jhm_st/jh.jar') HANDLER = 'no.such.pkg.JhOk.ok'",
                    "REFUSED User Error Report:  | Java Stack Trace: | java.lang.ClassNotFoundException: "
                        + "no.such.pkg.JhOk | \tat "
                        + "java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:476) | \tat "
                        + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:589) | \tat "
                        + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:522) |  in function JH_PKG "
                        + "with handler no.such.pkg.JhOk.ok"},
                {"CREATE OR REPLACE PROCEDURE jp_ok(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA "
                    + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') IMPORTS = "
                    + "('@jhm_st/jh.jar') HANDLER = 'JpOk.run'",
                    "Function JP_OK successfully created."},
                {"CALL jp_ok(1)",
                    "ok 1"},
                {"CREATE OR REPLACE PROCEDURE jp_over(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA "
                    + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') IMPORTS = "
                    + "('@jhm_st/jh.jar') HANDLER = 'JpOver.run'",
                    "REFUSED Cannot determine which implementation of handler \"run\" to invoke since there "
                        + "are multiple definitions with 2 arguments in function JP_OVER with handler JpOver.run"},
                {"CREATE OR REPLACE PROCEDURE jp_miss(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA "
                    + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') IMPORTS = "
                    + "('@jhm_st/jh.jar') HANDLER = 'JpMiss.run'",
                    "REFUSED Failed to find a public method named \"run\" with 2 arguments in function "
                        + "JP_MISS with handler JpMiss.run"},
                {"CREATE OR REPLACE PROCEDURE jp_arity(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA "
                    + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') IMPORTS = "
                    + "('@jhm_st/jh.jar') HANDLER = 'JpArity.run'",
                    "REFUSED Failed to find a public method named \"run\" with 2 arguments in function "
                        + "JP_ARITY with handler JpArity.run"},
                {"CREATE OR REPLACE PROCEDURE jp_priv(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA "
                    + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') IMPORTS = "
                    + "('@jhm_st/jh.jar') HANDLER = 'JpPriv.run'",
                    "REFUSED Failed to find a public method named \"run\" with 2 arguments in function "
                        + "JP_PRIV with handler JpPriv.run"},
                {"CREATE OR REPLACE PROCEDURE jp_noclass(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA "
                    + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') IMPORTS = "
                    + "('@jhm_st/jh.jar') HANDLER = 'JpNoSuch.run'",
                    "REFUSED User Error Report:  | Java Stack Trace: | java.lang.ClassNotFoundException: "
                        + "JpNoSuch | \tat java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:476) | "
                        + "\tat java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:589) | \tat "
                        + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:522) |  in function "
                        + "JP_NOCLASS with handler JpNoSuch.run"},
            });
            engine.execute("PUT file://" + replaced.toAbsolutePath()
                + " @jhm_st AUTO_COMPRESS = FALSE OVERWRITE = TRUE");
            assertCells(new String[][] {
                {"SELECT jh_ok(21)",
                    "REFUSED Failed to find a public method named \"ok\" with 1 arguments in function JH_OK "
                        + "with handler JhOk.ok"},
                {"CREATE OR REPLACE FUNCTION jh_ok_again(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = "
                    + "('@jhm_st/jh.jar') HANDLER = 'JhOk.ok'",
                    "REFUSED Failed to find a public method named \"ok\" with 1 arguments in function "
                        + "JH_OK_AGAIN with handler JhOk.ok"},
                {"CALL jp_ok(1)",
                    "REFUSED User Error Report:  | Java Stack Trace: | java.lang.ClassNotFoundException: "
                        + "JpOk | \tat java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:476) | \tat "
                        + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:589) | \tat "
                        + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:522) |  in function JP_OK "
                        + "with handler JpOk.run"},
            });
        } finally {
            for (final String function : new String[] {"jh_ok", "jh_inst", "jh_ok_again"}) {
                engine.execute("DROP FUNCTION IF EXISTS " + function + "(NUMBER)");
            }
            engine.execute("DROP PROCEDURE IF EXISTS jp_ok(NUMBER)");
            engine.execute("DROP STAGE IF EXISTS jhm_st");
            deleteRecursively(work.toFile());
        }
    }

    /** A jar named jh.jar in a directory, holding the given classes compiled for Java 11. */
    private static Path buildJar(final Path dir, final Map<String, String> classes) throws IOException {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler (run on a JDK)");
        }
        final List<File> sources = new ArrayList<File>();
        for (final Map.Entry<String, String> source : classes.entrySet()) {
            final Path file = dir.resolve(source.getKey() + ".java");
            Files.writeString(file, source.getValue());
            sources.add(file.toFile());
        }
        final StandardJavaFileManager files = compiler.getStandardFileManager(null, null, null);
        final List<String> options = new ArrayList<String>();
        options.add("--release");
        options.add("11");
        options.add("-cp");
        options.add(System.getProperty("java.class.path"));
        options.add("-d");
        options.add(dir.toString());
        final boolean compiled = compiler.getTask(null, files, null, options, null,
            files.getJavaFileObjectsFromFiles(sources)).call();
        files.close();
        if (!compiled) {
            throw new IllegalStateException("javac failed building the handler jar");
        }
        final Path jar = dir.resolve("jh.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (final String name : classes.keySet()) {
                out.putNextEntry(new JarEntry(name + ".class"));
                Files.copy(dir.resolve(name + ".class"), out);
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
