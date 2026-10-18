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
 * A Java routine judged as it is created: a class its inline body declares in a package is found by no name, its own
 * included — {@code java.lang.ClassNotFoundException: p.H} with the class loader's frames of the routine's Java level,
 * for a function and a procedure alike — and its PACKAGES must name a package the account offers at one of its
 * versions or at {@code latest}, in any case: a package without a version, an unknown version or package, or a name
 * of more or fewer parts is {@code Package 'com.snowflake:snowpark' is not supported.}, ahead of the handler
 * (live-verified).
 */
public class JavaHandlerCreateCheckTest extends MeasuredCellsTestSupport {

    @Test
    public void aPackagedClassAndThePackagesNamed() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION fnoclass(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'p.H.f' AS "
                + "$$package p; class H { public static int f(int x) { return x; } }$$",
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.ClassNotFoundException: p.H | \tat "
                + "java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:476) | \tat "
                + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:589) | \tat "
                + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:522) |  in function FNOCLASS with "
                + "handler p.H.f"},
            {"CREATE OR REPLACE FUNCTION fnoclass2(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS "
                + "$$package p; class H { public static int f(int x) { return x; } }$$",
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.ClassNotFoundException: H | \tat "
                + "java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:476) | \tat "
                + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:589) | \tat "
                + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:522) |  in function FNOCLASS2 with "
                + "handler H.f"},
            {"CREATE OR REPLACE FUNCTION fnoclass3(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'p.H.f' AS "
                + "$$package p; public class H { public static int f(int x) { return x; } }$$",
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.ClassNotFoundException: p.H | \tat "
                + "java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:476) | \tat "
                + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:589) | \tat "
                + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:522) |  in function FNOCLASS3 with "
                + "handler p.H.f"},
            {"CREATE OR REPLACE FUNCTION fnoclass4(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'p.q.H.f' "
                + "AS $$package p.q; class H { public static int f(int x) { return x; } }$$",
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.ClassNotFoundException: p.q.H | \tat "
                + "java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:476) | \tat "
                + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:589) | \tat "
                + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:522) |  in function FNOCLASS4 with "
                + "handler p.q.H.f"},
            {"CREATE OR REPLACE FUNCTION fnoclass5(x NUMBER) RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = "
                + "'17' HANDLER = 'p.H.f' AS $$package p; class H { public static int f(int x) { return x; } }$$",
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.ClassNotFoundException: p.H | \tat "
                + "java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:445) | \tat "
                + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:592) | \tat "
                + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:525) |  in function FNOCLASS5 with "
                + "handler p.H.f"},
            {"CREATE OR REPLACE FUNCTION fnoclass6(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'p.H.f' AS "
                + "$$class H { public static int f(int x) { return x; } }$$",
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.ClassNotFoundException: p.H | \tat "
                + "java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:476) | \tat "
                + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:589) | \tat "
                + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:522) |  in function FNOCLASS6 with "
                + "handler p.H.f"},
            {"CREATE OR REPLACE PROCEDURE pnoclass() RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'p.H.run' AS $$package p; import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s) { return \"x\"; } }$$",
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.ClassNotFoundException: p.H | \tat "
                + "java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:476) | \tat "
                + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:589) | \tat "
                + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:522) |  in function PNOCLASS with "
                + "handler p.H.run"},
            {"CREATE OR REPLACE FUNCTION fnoclass7(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'p.H.f' AS "
                + "$$/* c */ package p; class H { public static int f(int x) { return x; } }$$",
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.ClassNotFoundException: p.H | \tat "
                + "java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:476) | \tat "
                + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:589) | \tat "
                + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:522) |  in function FNOCLASS7 with "
                + "handler p.H.f"},
            {"CREATE OR REPLACE FUNCTION ftv1(x NUMBER) RETURNS NUMBER LANGUAGE JAVA PACKAGES = "
                + "('com.snowflake:snowpark') HANDLER = 'H.f' AS $$class H { public static int f(int x) { return x; "
                + "} }$$",
                "REFUSED SQL compilation error: Package 'com.snowflake:snowpark' is not supported."},
            {"CREATE OR REPLACE FUNCTION ftv2(x NUMBER) RETURNS NUMBER LANGUAGE JAVA PACKAGES = "
                + "('COM.SNOWFLAKE:SNOWPARK') HANDLER = 'H.f' AS $$class H { public static int f(int x) { return x; "
                + "} }$$",
                "REFUSED SQL compilation error: Package 'COM.SNOWFLAKE:SNOWPARK' is not supported."},
            {"CREATE OR REPLACE FUNCTION ftv3(x NUMBER) RETURNS NUMBER LANGUAGE JAVA PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.f' AS $$class H { public static int f(int x) { "
                + "return x; } }$$",
                "Function FTV3 successfully created."},
            {"CREATE OR REPLACE FUNCTION ftv4(x NUMBER) RETURNS NUMBER LANGUAGE JAVA PACKAGES = "
                + "('com.snowflake:snowpark:1.14.0') HANDLER = 'H.f' AS $$class H { public static int f(int x) { "
                + "return x; } }$$",
                "Function FTV4 successfully created."},
            {"CREATE OR REPLACE FUNCTION ftv5(x NUMBER) RETURNS NUMBER LANGUAGE JAVA PACKAGES = "
                + "('com.snowflake:snowpark:0.0.1') HANDLER = 'H.f' AS $$class H { public static int f(int x) { "
                + "return x; } }$$",
                "REFUSED SQL compilation error: Package 'com.snowflake:snowpark:0.0.1' is not supported."},
            {"CREATE OR REPLACE FUNCTION ftv6(x NUMBER) RETURNS NUMBER LANGUAGE JAVA PACKAGES = "
                + "('com.snowflake:telemetry:latest') HANDLER = 'H.f' AS $$class H { public static int f(int x) { "
                + "return x; } }$$",
                "Function FTV6 successfully created."},
            {"CREATE OR REPLACE FUNCTION ftv7(x NUMBER) RETURNS NUMBER LANGUAGE JAVA PACKAGES = "
                + "('nosuch:pkg:1.0') HANDLER = 'H.f' AS $$class H { public static int f(int x) { return x; } }$$",
                "REFUSED SQL compilation error: Package 'nosuch:pkg:1.0' is not supported."},
            {"CREATE OR REPLACE FUNCTION ftv8(x NUMBER) RETURNS NUMBER LANGUAGE JAVA PACKAGES = ('snowpark') "
                + "HANDLER = 'H.f' AS $$class H { public static int f(int x) { return x; } }$$",
                "REFUSED SQL compilation error: Package 'snowpark' is not supported."},
            {"CREATE OR REPLACE FUNCTION ftv9(x NUMBER) RETURNS NUMBER LANGUAGE JAVA PACKAGES = "
                + "('com.snowflake:snowpark:latest:x') HANDLER = 'H.f' AS $$class H { public static int f(int x) { "
                + "return x; } }$$",
                "REFUSED SQL compilation error: Package 'com.snowflake:snowpark:latest:x' is not supported."},
            {"CREATE OR REPLACE FUNCTION ftv10(x NUMBER) RETURNS NUMBER LANGUAGE JAVA PACKAGES = "
                + "('com.snowflake:snowpark', 'com.snowflake:telemetry:latest') HANDLER = 'H.f' AS $$class H { "
                + "public static int f(int x) { return x; } }$$",
                "REFUSED SQL compilation error: Package 'com.snowflake:snowpark' is not supported."},
            {"CREATE OR REPLACE FUNCTION ftv11(x NUMBER) RETURNS NUMBER LANGUAGE JAVA PACKAGES = "
                + "('com.snowflake:snowpark:') HANDLER = 'H.f' AS $$class H { public static int f(int x) { return x; "
                + "} }$$",
                "REFUSED SQL compilation error: Package 'com.snowflake:snowpark:' is not supported."},
            {"CREATE OR REPLACE FUNCTION ftv12(x NUMBER) RETURNS NUMBER LANGUAGE JAVA PACKAGES = "
                + "('com.snowflake:telemetry') HANDLER = 'H.f' AS $$class H { public static int f(int x) { return x; "
                + "} }$$",
                "REFUSED SQL compilation error: Package 'com.snowflake:telemetry' is not supported."},
            {"CREATE OR REPLACE FUNCTION ftv13(x NUMBER) RETURNS NUMBER LANGUAGE JAVA PACKAGES = "
                + "('com.snowflake:snowpark') HANDLER = 'G.f' AS $$class H { public static int f(int x) { return x; "
                + "} }$$",
                "REFUSED SQL compilation error: Package 'com.snowflake:snowpark' is not supported."},
            {"CREATE OR REPLACE PROCEDURE ptv() RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark') HANDLER = 'H.run' AS $$import com.snowflake.snowpark_java.*; class H { "
                + "public static String run(Session s) { return \"x\"; } }$$",
                "REFUSED SQL compilation error: Package 'com.snowflake:snowpark' is not supported."},
        });
    }

    @Test
    public void aPackageAtItsVersions() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION fpk0(x NUMBER) RETURNS NUMBER LANGUAGE JAVA PACKAGES = "
                + "('com.snowflake:snowpark:1.0.0') HANDLER = 'H.f' AS $$class H { public static int f(int x) { "
                + "return x; } }$$",
                "REFUSED SQL compilation error: Package 'com.snowflake:snowpark:1.0.0' is not supported."},
            {"CREATE OR REPLACE FUNCTION fpk1(x NUMBER) RETURNS NUMBER LANGUAGE JAVA PACKAGES = "
                + "('com.snowflake:snowpark:1.9.0') HANDLER = 'H.f' AS $$class H { public static int f(int x) { "
                + "return x; } }$$",
                "Function FPK1 successfully created."},
            {"CREATE OR REPLACE FUNCTION fpk2(x NUMBER) RETURNS NUMBER LANGUAGE JAVA PACKAGES = "
                + "('com.snowflake:snowpark:1.16.0') HANDLER = 'H.f' AS $$class H { public static int f(int x) { "
                + "return x; } }$$",
                "Function FPK2 successfully created."},
            {"CREATE OR REPLACE FUNCTION fpk3(x NUMBER) RETURNS NUMBER LANGUAGE JAVA PACKAGES = "
                + "('com.snowflake:snowpark:1.99.0') HANDLER = 'H.f' AS $$class H { public static int f(int x) { "
                + "return x; } }$$",
                "REFUSED SQL compilation error: Package 'com.snowflake:snowpark:1.99.0' is not supported."},
            {"CREATE OR REPLACE FUNCTION fpk4(x NUMBER) RETURNS NUMBER LANGUAGE JAVA PACKAGES = "
                + "('com.snowflake:snowpark:0.12.0') HANDLER = 'H.f' AS $$class H { public static int f(int x) { "
                + "return x; } }$$",
                "REFUSED SQL compilation error: Package 'com.snowflake:snowpark:0.12.0' is not supported."},
            {"CREATE OR REPLACE FUNCTION fpk5(x NUMBER) RETURNS NUMBER LANGUAGE JAVA PACKAGES = "
                + "('com.snowflake:snowpark:LATEST') HANDLER = 'H.f' AS $$class H { public static int f(int x) { "
                + "return x; } }$$",
                "Function FPK5 successfully created."},
            {"CREATE OR REPLACE FUNCTION fpk6(x NUMBER) RETURNS NUMBER LANGUAGE JAVA PACKAGES = "
                + "('com.snowflake:telemetry:0.0.1') HANDLER = 'H.f' AS $$class H { public static int f(int x) { "
                + "return x; } }$$",
                "Function FPK6 successfully created."},
            {"CREATE OR REPLACE FUNCTION fpk7(x NUMBER) RETURNS NUMBER LANGUAGE JAVA PACKAGES = "
                + "('com.snowflake:telemetry:0.1.0') HANDLER = 'H.f' AS $$class H { public static int f(int x) { "
                + "return x; } }$$",
                "Function FPK7 successfully created."},
            {"CREATE OR REPLACE FUNCTION fpk8(x NUMBER) RETURNS NUMBER LANGUAGE JAVA PACKAGES = (' "
                + "com.snowflake:snowpark:latest') HANDLER = 'H.f' AS $$class H { public static int f(int x) { "
                + "return x; } }$$",
                "Function FPK8 successfully created."},
            {"CREATE OR REPLACE FUNCTION fpk9(x NUMBER) RETURNS NUMBER LANGUAGE JAVA PACKAGES = "
                + "('com.snowflake:snowpark:latest ') HANDLER = 'H.f' AS $$class H { public static int f(int x) { "
                + "return x; } }$$",
                "Function FPK9 successfully created."},
        });
    }
}
