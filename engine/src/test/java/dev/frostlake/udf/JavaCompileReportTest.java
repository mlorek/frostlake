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
 * An inline Java body that does not compile is refused with the compiler's own report: {@code Error while
 * compiling source:}, each fault's place in {@code InlineCode.java} by the body's own line, the line and a
 * mark under the place, the compiler's notes, its count of errors, and the routine and its handler. A public
 * class of any name may be the handler's. Every answer is live-verified.
 */
public class JavaCompileReportTest extends MeasuredCellsTestSupport {

    @Test
    public void aBodyThatDoesNotCompileIsRefusedWithTheCompilersReport() {
        assertAnswer(
            "REFUSED Error while compiling source: /InlineCode.java:1: error: incompatible types: String cannot "
                + "be converted to long |  import com.snowflake.snowpark_java.Session; class H { public long "
                + "run(Session s, long a) { return a + \"x\"; } }  |                                                 "
                + "                                                     ^ | 1 error |  in function JPBAD with "
                + "handler H.run",
            "CREATE OR REPLACE PROCEDURE jpbad(a NUMBER) RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.Session; class H { public long run(Session s, long a) { return a + "
                + "\"x\"; } } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function JPBAD.",
            "CALL jpbad(1)");
        assertAnswer(
            "REFUSED Error while compiling source: /InlineCode.java:1: error: incompatible types: String cannot "
                + "be converted to int |  class H { public static int f(int a) { return a + \"x\"; } }  |            "
                + "                                      ^ | 1 error |  in function JFBAD with handler H.f",
            "CREATE OR REPLACE FUNCTION jfbad(a NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ class "
                + "H { public static int f(int a) { return a + \"x\"; } } $$");
        assertAnswer(
            "REFUSED Error while compiling source: /InlineCode.java:4: error: incompatible types: String cannot "
                + "be converted to int |     int b = \"s\"; |             ^ | /InlineCode.java:5: error: cannot find "
                + "symbol |     return nope(a); |            ^ |   symbol:   method nope(int) |   location: class H "
                + "| 2 errors |  in function JFTWO with handler H.f",
            "CREATE OR REPLACE FUNCTION jftwo(a NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$\nclass "
                + "H {\n  public static int f(int a) {\n    int b = \"s\";\n    return nope(a);\n  }\n}\n$$");
        assertAnswer(
            "Function JFPUB successfully created.",
            "CREATE OR REPLACE FUNCTION jfpub(a NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'Pub.f' AS $$ "
                + "public class Pub { public static int f(int a) { return a + 1; } } $$");
        assertAnswer(
            "2",
            "SELECT jfpub(1)");
        assertAnswer(
            "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol |  class H { "
                + "public static int f(int a) { java.util.List l = new java.util.ArrayList(); l.add(a); return "
                + "nope(l); } }  |                                                                                   "
                + "                     ^ |   symbol:   method nope(List) |   location: class H | Note: "
                + "/InlineCode.java uses unchecked or unsafe operations. | Note: Recompile with -Xlint:unchecked for "
                + "details. | 1 error |  in function JFWARN with handler H.f",
            "CREATE OR REPLACE FUNCTION jfwarn(a NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ class "
                + "H { public static int f(int a) { java.util.List l = new java.util.ArrayList(); l.add(a); return "
                + "nope(l); } } $$");
    }
}
