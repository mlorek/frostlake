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
 * The Java an inline body may be written in is its routine's RUNTIME_VERSION's: 11 unless the routine names
 * 17 or 21. A switch expression, a pattern in instanceof, a text block, a record and a sealed type are
 * refused when a routine of version 11 is created and accepted for 17; a pattern in a switch is refused for
 * both; a method the version's class library lacks is refused as {@code cannot find symbol}. A refused body
 * leaves no function. A version the account does not offer is {@code invalid value '8' for property
 * 'RUNTIME_VERSION'}. Where the text of a refusal is the compiler's own it is asserted only for the faults
 * every compiler words alike. Every answer is live-verified.
 */
public class JavaRuntimeVersionLevelTest extends MeasuredCellsTestSupport {

    @Test
    public void aBodyIsHeldToItsRuntimeVersionsJava() {
        answer(
            "CREATE OR REPLACE FUNCTION jsw11(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "HANDLER = 'H.f' AS $$ class H { public static String f(int x) { return switch (x) { case 1 -> "
                + "\"one\"; default -> \"many\"; }; } } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function JSW11.",
            "SELECT jsw11(1)");
        assertAnswer(
            "Function JSW17 successfully created.",
            "CREATE OR REPLACE FUNCTION jsw17(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '17' "
                + "HANDLER = 'H.f' AS $$ class H { public static String f(int x) { return switch (x) { case 1 -> "
                + "\"one\"; default -> \"many\"; }; } } $$");
        assertAnswer(
            "one",
            "SELECT jsw17(1)");
        answer(
            "CREATE OR REPLACE FUNCTION jpt11(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "HANDLER = 'H.f' AS $$ class H { public static String f(String x) { Object o = x; if (o instanceof "
                + "String s) { return s.strip(); } return \"?\"; } } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function JPT11.",
            "SELECT jpt11(' a ')");
        assertAnswer(
            "Function JPT17 successfully created.",
            "CREATE OR REPLACE FUNCTION jpt17(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '17' "
                + "HANDLER = 'H.f' AS $$ class H { public static String f(String x) { Object o = x; if (o instanceof "
                + "String s) { return s.strip(); } return \"?\"; } } $$");
        assertAnswer(
            "a",
            "SELECT jpt17(' a ')");
        answer(
            "CREATE OR REPLACE FUNCTION jtb11(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "HANDLER = 'H.f' AS $$ class H { public static String f(int x) { return \"\"\"\n   block\"\"\"; } "
                + "} $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function JTB11.",
            "SELECT jtb11(1)");
        assertAnswer(
            "Function JTB17 successfully created.",
            "CREATE OR REPLACE FUNCTION jtb17(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '17' "
                + "HANDLER = 'H.f' AS $$ class H { public static String f(int x) { return \"\"\"\n   block\"\"\"; } "
                + "} $$");
        assertAnswer(
            "block",
            "SELECT jtb17(1)");
        assertAnswer(
            "Function JVA11 successfully created.",
            "CREATE OR REPLACE FUNCTION jva11(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "HANDLER = 'H.f' AS $$ class H { public static String f(int x) { var s = \"var\" + x; return s; } "
                + "} $$");
        assertAnswer(
            "var1",
            "SELECT jva11(1)");
        assertAnswer(
            "Function JVA17 successfully created.",
            "CREATE OR REPLACE FUNCTION jva17(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '17' "
                + "HANDLER = 'H.f' AS $$ class H { public static String f(int x) { var s = \"var\" + x; return s; } "
                + "} $$");
        assertAnswer(
            "var1",
            "SELECT jva17(1)");
        answer(
            "CREATE OR REPLACE FUNCTION jrc11(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "HANDLER = 'H.f' AS $$ class H { record P(int a) { } public static String f(int x) { return "
                + "String.valueOf(new P(x).a()); } } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function JRC11.",
            "SELECT jrc11(1)");
        assertAnswer(
            "Function JRC17 successfully created.",
            "CREATE OR REPLACE FUNCTION jrc17(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '17' "
                + "HANDLER = 'H.f' AS $$ class H { record P(int a) { } public static String f(int x) { return "
                + "String.valueOf(new P(x).a()); } } $$");
        assertAnswer(
            "1",
            "SELECT jrc17(1)");
        answer(
            "CREATE OR REPLACE FUNCTION jse11(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "HANDLER = 'H.f' AS $$ class H { sealed interface S permits A { } record A(int a) implements S { } "
                + "public static String f(int x) { S s = new A(x); return s.toString(); } } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function JSE11.",
            "SELECT jse11(1)");
        assertAnswer(
            "Function JSE17 successfully created.",
            "CREATE OR REPLACE FUNCTION jse17(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '17' "
                + "HANDLER = 'H.f' AS $$ class H { sealed interface S permits A { } record A(int a) implements S { } "
                + "public static String f(int x) { S s = new A(x); return s.toString(); } } $$");
        assertAnswer(
            "A[a=1]",
            "SELECT jse17(1)");
        answer(
            "CREATE OR REPLACE FUNCTION jsp11(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "HANDLER = 'H.f' AS $$ class H { public static String f(int x) { Object o = x; return switch (o) { "
                + "case Integer i when i > 0 -> \"pos\"; default -> \"other\"; }; } } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function JSP11.",
            "SELECT jsp11(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function JSP17.",
            "SELECT jsp17(1)");
        assertAnswer(
            "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol |  class H { "
                + "public static String f(int x) { return \"n=%d\".formatted(x); } }  |                              "
                + "                           ^ |   symbol:   method formatted(int) |   location: class String | 1 "
                + "error |  in function JA511 with handler H.f",
            "CREATE OR REPLACE FUNCTION ja511(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "HANDLER = 'H.f' AS $$ class H { public static String f(int x) { return \"n=%d\".formatted(x); } } "
                + "$$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function JA511.",
            "SELECT ja511(1)");
        assertAnswer(
            "Function JA517 successfully created.",
            "CREATE OR REPLACE FUNCTION ja517(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '17' "
                + "HANDLER = 'H.f' AS $$ class H { public static String f(int x) { return \"n=%d\".formatted(x); } } "
                + "$$");
        assertAnswer(
            "n=1",
            "SELECT ja517(1)");
        assertAnswer(
            "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol |  class H { "
                + "public static String f(int x) { return java.util.stream.Stream.of(x).toList().toString(); } }  |  "
                + "                                                                              ^ |   symbol:   "
                + "method toList() |   location: interface Stream<Integer> | 1 error |  in function JA611 with "
                + "handler H.f",
            "CREATE OR REPLACE FUNCTION ja611(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "HANDLER = 'H.f' AS $$ class H { public static String f(int x) { return "
                + "java.util.stream.Stream.of(x).toList().toString(); } } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function JA611.",
            "SELECT ja611(1)");
        assertAnswer(
            "Function JA617 successfully created.",
            "CREATE OR REPLACE FUNCTION ja617(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '17' "
                + "HANDLER = 'H.f' AS $$ class H { public static String f(int x) { return "
                + "java.util.stream.Stream.of(x).toList().toString(); } } $$");
        assertAnswer(
            "[1]",
            "SELECT ja617(1)");
        assertAnswer(
            "Function JA111 successfully created.",
            "CREATE OR REPLACE FUNCTION ja111(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "HANDLER = 'H.f' AS $$ class H { public static String f(String x) { return x.strip() + "
                + "x.isBlank(); } } $$");
        assertAnswer(
            "afalse",
            "SELECT ja111(' a ')");
        assertAnswer(
            "Function JA117 successfully created.",
            "CREATE OR REPLACE FUNCTION ja117(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '17' "
                + "HANDLER = 'H.f' AS $$ class H { public static String f(String x) { return x.strip() + "
                + "x.isBlank(); } } $$");
        assertAnswer(
            "afalse",
            "SELECT ja117(' a ')");
        assertAnswer(
            "signature|(X NUMBER) ;; returns|VARCHAR ;; language|JAVA ;; null handling|CALLED ON NULL INPUT ;; "
                + "volatility|VOLATILE ;; body| class H { public static String f(int x) { return switch (x) { case 1 "
                + "-> \"one\"; default -> \"many\"; }; } }  ;; imports|[] ;; handler|H.f ;; target_path|null ;; "
                + "runtime_version|17 ;; packages|[]",
            "DESCRIBE FUNCTION jsw17(NUMBER)");
    }

    @Test
    public void theDefaultLevelAndTheVersionsARoutineMayName() {
        answer(
            "CREATE OR REPLACE FUNCTION jsw0(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ class "
                + "H { public static String f(int x) { return switch (x) { case 1 -> \"one\"; default -> \"many\"; "
                + "}; } } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function JSW0.",
            "SELECT jsw0(1)");
        assertAnswer(
            "Function JV21 successfully created.",
            "CREATE OR REPLACE FUNCTION jv21(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '21' "
                + "HANDLER = 'H.f' AS $$ class H { public static String f(int x) { var s = \"var\" + x; return s; } "
                + "} $$");
        assertAnswer(
            "var1",
            "SELECT jv21(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value '8' for property 'RUNTIME_VERSION'",
            "CREATE OR REPLACE FUNCTION jv8(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '8' HANDLER "
                + "= 'H.f' AS $$ class H { public static String f(int x) { var s = \"var\" + x; return s; } } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function JV8.",
            "SELECT jv8(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value '12' for property 'RUNTIME_VERSION'",
            "CREATE OR REPLACE FUNCTION jv12(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '12' "
                + "HANDLER = 'H.f' AS $$ class H { public static String f(int x) { var s = \"var\" + x; return s; } "
                + "} $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function JV12.",
            "SELECT jv12(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value '11.0' for property 'RUNTIME_VERSION'",
            "CREATE OR REPLACE FUNCTION jv110(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11.0' "
                + "HANDLER = 'H.f' AS $$ class H { public static String f(int x) { var s = \"var\" + x; return s; } "
                + "} $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function JV110.",
            "SELECT jv110(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value 'abc' for property 'RUNTIME_VERSION'",
            "CREATE OR REPLACE FUNCTION jvx(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = 'abc' "
                + "HANDLER = 'H.f' AS $$ class H { public static String f(int x) { var s = \"var\" + x; return s; } "
                + "} $$");
        assertAnswer(
            "Function JVN successfully created.",
            "CREATE OR REPLACE FUNCTION jvn(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = 17 HANDLER "
                + "= 'H.f' AS $$ class H { public static String f(int x) { var s = \"var\" + x; return s; } } $$");
        assertAnswer(
            "var1",
            "SELECT jvn(1)");
        assertAnswer(
            "Function JVER11 successfully created.",
            "CREATE OR REPLACE FUNCTION jver11(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "HANDLER = 'H.f' AS $$ class H { public static String f(int x) { return "
                + "String.valueOf(Runtime.version().feature()); } } $$");
        answer(
            "SELECT jver11(1)");
        assertAnswer(
            "Function JVER17 successfully created.",
            "CREATE OR REPLACE FUNCTION jver17(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '17' "
                + "HANDLER = 'H.f' AS $$ class H { public static String f(int x) { return "
                + "String.valueOf(Runtime.version().feature()); } } $$");
        assertAnswer(
            "17",
            "SELECT jver17(1)");
        assertAnswer(
            "Function JVER0 successfully created.",
            "CREATE OR REPLACE FUNCTION jver0(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ class "
                + "H { public static String f(int x) { return String.valueOf(Runtime.version().feature()); } } $$");
        answer(
            "SELECT jver0(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.JSW0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.JSW0.",
            "DESCRIBE FUNCTION jsw0(NUMBER)");
    }

    @Test
    public void aProcedureIsHeldToItsLevelToo() {
        answer(
            "CREATE OR REPLACE PROCEDURE jpsw11() RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.Session; class H { public String run(Session s) { int x = 1; return "
                + "switch (x) { case 1 -> \"one\"; default -> \"many\"; }; } } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function JPSW11.",
            "CALL jpsw11()");
        assertAnswer(
            "Function JPSW17 successfully created.",
            "CREATE OR REPLACE PROCEDURE jpsw17() RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '17' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.Session; class H { public String run(Session s) { int x = 1; return "
                + "switch (x) { case 1 -> \"one\"; default -> \"many\"; }; } } $$");
        assertAnswer(
            "one",
            "CALL jpsw17()");
    }
}
