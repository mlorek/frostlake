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

/**
 * A Java routine's handler is judged as the routine is created: a handler that names no class is an invalid
 * function name, a class the body does not hold is reported with the class loader's frames as the routine's
 * Java runtime numbers them, for a function, a procedure and a table function alike, and an instance handler's
 * class needs a constructor that is neither private nor protected (live-verified).
 */
public class JavaHandlerClassAtCreateTest extends MeasuredCellsTestSupport {

    @Test
    public void aMissingClassAndAnInstancesConstructor() {
        try {
            assertAnswer(
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.ClassNotFoundException: G | \tat "
                    + "java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:476) | \tat "
                    + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:589) | \tat "
                    + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:522) |  in function FNOCLASS with "
                    + "handler G.f",
                "CREATE OR REPLACE FUNCTION fnoclass(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'G.f' AS "
                    + "$$class H { public static int f(int x) { return x; } }$$");
            assertAnswer(
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.ClassNotFoundException: p.G | \tat "
                    + "java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:476) | \tat "
                    + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:589) | \tat "
                    + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:522) |  in function FNOCLASS with "
                    + "handler p.G.f",
                "CREATE OR REPLACE FUNCTION fnoclass(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'p.G.f' AS "
                    + "$$class H { public static int f(int x) { return x; } }$$");
            assertAnswer(
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.ClassNotFoundException: h | \tat "
                    + "java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:476) | \tat "
                    + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:589) | \tat "
                    + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:522) |  in function FNOCLASS with "
                    + "handler h.f",
                "CREATE OR REPLACE FUNCTION fnoclass(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'h.f' AS "
                    + "$$class H { public static int f(int x) { return x; } }$$");
            assertAnswer(
                "REFUSED Failed to find a public method named \"g\" with 1 arguments in function FNOCLASS with "
                    + "handler H.g",
                "CREATE OR REPLACE FUNCTION fnoclass(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.g' AS "
                    + "$$class H { public static int f(int x) { return x; } }$$");
            assertAnswer(
                "REFUSED Invalid function name f in function FNOCLASS with handler f",
                "CREATE OR REPLACE FUNCTION fnoclass(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'f' AS "
                    + "$$class H { public static int f(int x) { return x; } }$$");
            assertAnswer(
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.ClassNotFoundException: G | \tat "
                    + "java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:445) | \tat "
                    + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:592) | \tat "
                    + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:525) |  in function FNOCLASS with "
                    + "handler G.f",
                "CREATE OR REPLACE FUNCTION fnoclass(x NUMBER) RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = "
                    + "'17' HANDLER = 'G.f' AS $$class H { public static int f(int x) { return x; } }$$");
            assertAnswer(
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.ClassNotFoundException: G | \tat "
                    + "java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:476) | \tat "
                    + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:589) | \tat "
                    + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:522) |  in function PNOCLASS with "
                    + "handler G.run",
                "CREATE OR REPLACE PROCEDURE pnoclass() RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'G.run' AS $$class H { public static "
                    + "String run(com.snowflake.snowpark_java.Session s) { return \"x\"; } }$$");
            assertAnswer(
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.ClassNotFoundException: G | \tat "
                    + "java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:476) | \tat "
                    + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:589) | \tat "
                    + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:522) |  in function FNOCLASS with "
                    + "handler G.f",
                "CREATE OR REPLACE FUNCTION fnoclass(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'G.f' AS "
                    + "$$class H { public static int f(int x) { return x; } }\nclass K { }$$");
            assertAnswer(
                "REFUSED The provided method is non-static, but the enclosing class does not have a public "
                    + "zero-argument constructor in function FPROT with handler H.f",
                "CREATE OR REPLACE FUNCTION fprot(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$class H { protected H() { } public int f(int x) { return x + 1; } }$$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function FPROT.",
                "SELECT fprot(1)");
            assertAnswer(
                "Function FNPE successfully created.",
                "CREATE OR REPLACE FUNCTION fnpe(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$class H { public static int f(int x) { int[] a = null; return a.length; } }$$");
            assertAnswer(
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.NullPointerException | \tat "
                    + "function_handler_0//H.f(InlineCode.java:1) |  in function FNPE with handler H.f",
                "SELECT fnpe(1)");
        } finally {
            answer(
                "DROP FUNCTION IF EXISTS fsv(VARCHAR)");
            answer(
                "DROP FUNCTION IF EXISTS fsv(VARIANT)");
            answer(
                "DROP PROCEDURE IF EXISTS pnp()");
            answer(
                "DROP FUNCTION IF EXISTS fnoclass(NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS pnoclass()");
            answer(
                "DROP FUNCTION IF EXISTS fprot(NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS fnpe(NUMBER)");
        }
    }

    @Test
    public void handlerNamesRuntimesAndConstructors() {
        try {
            assertAnswer(
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.ClassNotFoundException: G | \tat "
                    + "java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:445) | \tat "
                    + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:593) | \tat "
                    + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:526) |  in function FNOCLASS with "
                    + "handler G.f",
                "CREATE OR REPLACE FUNCTION fnoclass(x NUMBER) RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = "
                    + "'21' HANDLER = 'G.f' AS $$class H { public static int f(int x) { return x; } }$$");
            assertAnswer(
                "REFUSED Invalid function name f in function FNOCLASS with handler f",
                "CREATE OR REPLACE FUNCTION fnoclass(x NUMBER) RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = "
                    + "'17' HANDLER = 'f' AS $$class H { public static int f(int x) { return x; } }$$");
            assertAnswer(
                "REFUSED Failed to find a public method named \"\" with 1 arguments in function FNOCLASS with "
                    + "handler H.",
                "CREATE OR REPLACE FUNCTION fnoclass(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.' AS "
                    + "$$class H { public static int f(int x) { return x; } }$$");
            assertAnswer(
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.ClassNotFoundException:  | \tat "
                    + "java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:476) | \tat "
                    + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:589) | \tat "
                    + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:522) |  in function FNOCLASS with "
                    + "handler .f",
                "CREATE OR REPLACE FUNCTION fnoclass(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = '.f' AS "
                    + "$$class H { public static int f(int x) { return x; } }$$");
            assertAnswer(
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.ClassNotFoundException: G | \tat "
                    + "java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:476) | \tat "
                    + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:589) | \tat "
                    + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:522) |  in function FNOCLASS with "
                    + "handler G.f",
                "CREATE OR REPLACE FUNCTION fnoclass(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'G.f' AS "
                    + "$$package p; class H { public static int f(int x) { return x; } }$$");
            assertAnswer(
                "REFUSED Invalid function name run in function PNOCLASS with handler run",
                "CREATE OR REPLACE PROCEDURE pnoclass() RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'run' AS $$class H { public static "
                    + "String run(com.snowflake.snowpark_java.Session s) { return \"x\"; } }$$");
            assertAnswer(
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.ClassNotFoundException: G | \tat "
                    + "java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:476) | \tat "
                    + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:589) | \tat "
                    + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:522) |  in function TNOCLASS with "
                    + "handler G",
                "CREATE OR REPLACE FUNCTION tnoclass(x NUMBER) RETURNS TABLE (y NUMBER) LANGUAGE JAVA HANDLER = "
                    + "'G' AS $$class H { }$$");
            assertAnswer(
                "Function FCTOR successfully created.",
                "CREATE OR REPLACE FUNCTION fctor(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$public class H { H() { } public int f(int x) { return x + 1; } }$$");
            assertAnswer(
                "2",
                "SELECT fctor(1)");
            assertAnswer(
                "Function FCTOR successfully created.",
                "CREATE OR REPLACE FUNCTION fctor(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$public class H { public H() { } public int f(int x) { return x + 1; } }$$");
            assertAnswer(
                "2",
                "SELECT fctor(1)");
            assertAnswer(
                "REFUSED The provided method is non-static, but the enclosing class does not have a public "
                    + "zero-argument constructor in function FCTOR with handler H.f",
                "CREATE OR REPLACE FUNCTION fctor(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$public class H { protected H() { } public int f(int x) { return x + 1; } }$$");
            assertAnswer(
                "Function FCTOR successfully created.",
                "CREATE OR REPLACE FUNCTION fctor(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$class H { H() { } public int f(int x) { return x + 1; } }$$");
            assertAnswer(
                "Function FCTOR successfully created.",
                "CREATE OR REPLACE FUNCTION fctor(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$class H { public int f(int x) { return x + 1; } }$$");
            assertAnswer(
                "2",
                "SELECT fctor(1)");
            assertAnswer(
                "Function FCTOR successfully created.",
                "CREATE OR REPLACE FUNCTION fctor(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$class H { protected H() { } public static int f(int x) { return x + 1; } }$$");
            assertAnswer(
                "2",
                "SELECT fctor(1)");
        } finally {
            answer(
                "DROP FUNCTION IF EXISTS fnoclass(NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS pnoclass()");
            answer(
                "DROP FUNCTION IF EXISTS tnoclass(NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS fctor(NUMBER)");
        }
    }
}
