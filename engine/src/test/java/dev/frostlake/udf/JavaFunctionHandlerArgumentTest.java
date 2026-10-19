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
 * What an inline Java function's handler receives for each SQL type and Java parameter type. A VARCHAR
 * parameter turns a number, a BOOLEAN, a DATE, a TIME, a timestamp and a VARIANT into text before the handler
 * runs. A {@code String} handler parameter holds the value's text for any SQL type: a NUMBER at its declared
 * scale, a FLOAT as a FLOAT prints, a TIME to the nanosecond with a {@code Z}, a timestamp to the millisecond
 * with its offset, a BINARY as hex, a semi-structured value as its JSON. A {@code byte[]} takes a BINARY, a
 * {@code Map} an OBJECT's members and a {@code String[]} an ARRAY's elements - a JSON null and a SQL NULL
 * element as {@code null} - and a {@code Variant} a VARIANT. A {@code java.sql.Date} holds a DATE as its
 * midnight at UTC, a {@code java.sql.Time} a TIME as the milliseconds of its day and a
 * {@code java.sql.Timestamp} a timestamp without a zone as its wall clock read at UTC, nanoseconds kept,
 * whatever the session's zone; handed back, each is the value it was. An exception the handler raises reaches
 * SQL as {@code User Error Report:}, the exception and its frames in the handler's source. The session's
 * TIMEZONE is set by the tests and unset whatever the assertions make of the statements between. Every
 * answer is live-verified.
 */
public class JavaFunctionHandlerArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void anArgumentBecomesTheHandlersJavaType() {
        try {
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function FS successfully created.",
                "CREATE OR REPLACE FUNCTION fs(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(String x) { return \"s:\" + x; } } $$");
            assertAnswer(
                "s:42",
                "SELECT fs(42)");
            assertAnswer(
                "s:1.5",
                "SELECT fs(1.50)");
            assertAnswer(
                "s:1.5",
                "SELECT fs(1.5::FLOAT)");
            assertAnswer(
                "s:true",
                "SELECT fs(TRUE)");
            assertAnswer(
                "s:2024-03-05",
                "SELECT fs('2024-03-05'::DATE)");
            assertAnswer(
                "s:10:11:12",
                "SELECT fs('10:11:12'::TIME)");
            answer(
                "SELECT fs('2024-03-05 10:11:12.345'::TIMESTAMP_NTZ)");
            assertAnswer(
                "s:null",
                "SELECT fs(NULL)");
            assertAnswer(
                "s:{\"a\":1}",
                "SELECT fs(PARSE_JSON('{\"a\":1}'))");
            assertAnswer(
                "s:q",
                "SELECT fs(TO_VARIANT('q'))");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'FS': (ARRAY)",
                "SELECT fs(ARRAY_CONSTRUCT(1))");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'FS': (BINARY(67108864))",
                "SELECT fs(TO_BINARY('0A', 'HEX'))");
            assertAnswer(
                "Table IDS successfully created.",
                "CREATE OR REPLACE TABLE ids (id NUMBER, d DATE, f FLOAT, b BOOLEAN)");
            assertAnswer(
                "1",
                "INSERT INTO ids VALUES (7, '2024-03-05', 2.5, TRUE)");
            assertAnswer(
                "s:7|s:2024-03-05|s:2.5|s:true",
                "SELECT fs(id), fs(d), fs(f), fs(b) FROM ids");
            assertAnswer(
                "Function BL successfully created.",
                "CREATE OR REPLACE FUNCTION bl(x BOOLEAN) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(String x) { return \"[\" + x + \"]\"; } } $$");
            assertAnswer(
                "[true]",
                "SELECT bl(TRUE)");
            assertAnswer(
                "[null]",
                "SELECT bl(NULL)");
            assertAnswer(
                "Function BLB successfully created.",
                "CREATE OR REPLACE FUNCTION blb(x BOOLEAN) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(boolean x) { return \"[\" + x + \"]\"; } } $$");
            assertAnswer(
                "[true]",
                "SELECT blb(TRUE)");
            assertAnswer(
                "REFUSED Cannot pass a NULL value as input to a Java type of boolean in function BLB with handler "
                    + "H.f",
                "SELECT blb(NULL)");
            assertAnswer(
                "[true]",
                "SELECT blb('yes')");
            assertAnswer(
                "[false]",
                "SELECT blb(0)");
            assertAnswer(
                "Function BLB successfully created.",
                "CREATE OR REPLACE FUNCTION blB(x BOOLEAN) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(Boolean x) { return \"[\" + x + \"]\"; } } $$");
            assertAnswer(
                "[null]",
                "SELECT blB(NULL)");
            assertAnswer(
                "Function NS successfully created.",
                "CREATE OR REPLACE FUNCTION ns(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(String x) { return \"[\" + x + \"]\"; } } $$");
            assertAnswer(
                "[7]",
                "SELECT ns(7)");
            assertAnswer(
                "[2]",
                "SELECT ns(1.5)");
            assertAnswer(
                "[12345678901234567890123456789012345678]",
                "SELECT ns(12345678901234567890123456789012345678)");
            assertAnswer(
                "Function N2S successfully created.",
                "CREATE OR REPLACE FUNCTION n2s(x NUMBER(10,2)) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$ class H { public static String f(String x) { return \"[\" + x + \"]\"; } } $$");
            assertAnswer(
                "[7.00]",
                "SELECT n2s(7)");
            assertAnswer(
                "[1.50]",
                "SELECT n2s(1.5)");
            assertAnswer(
                "Function FLS successfully created.",
                "CREATE OR REPLACE FUNCTION fls(x FLOAT) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(String x) { return \"[\" + x + \"]\"; } } $$");
            assertAnswer(
                "[1.5]",
                "SELECT fls(1.5)");
            assertAnswer(
                "[1]",
                "SELECT fls(1)");
            assertAnswer(
                "[1e+30]",
                "SELECT fls(1e30::FLOAT)");
            assertAnswer(
                "[NaN]",
                "SELECT fls('NaN'::FLOAT)");
            assertAnswer(
                "Function FBIN successfully created.",
                "CREATE OR REPLACE FUNCTION fbin(x BINARY) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(byte[] x) { return \"len=\" + x.length + \":\" + x[0]; } } $$");
            assertAnswer(
                "len=2:10",
                "SELECT fbin(TO_BINARY('0AFF', 'HEX'))");
            assertAnswer(
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.NullPointerException | \tat "
                    + "function_handler_0//H.f(InlineCode.java:1) |  in function FBIN with handler H.f",
                "SELECT fbin(NULL)");
            assertAnswer(
                "Function FBINS successfully created.",
                "CREATE OR REPLACE FUNCTION fbins(x BINARY) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(String x) { return \"[\" + x + \"]\"; } } $$");
            assertAnswer(
                "[0AFF]",
                "SELECT fbins(TO_BINARY('0AFF', 'HEX'))");
            assertAnswer(
                "Function FDATE successfully created.",
                "CREATE OR REPLACE FUNCTION fdate(x DATE) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(java.sql.Date x) { return \"d=\" + x; } } $$");
            assertAnswer(
                "Function FDATES successfully created.",
                "CREATE OR REPLACE FUNCTION fdates(x DATE) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(String x) { return \"[\" + x + \"]\"; } } $$");
            assertAnswer(
                "[2024-03-05]",
                "SELECT fdates('2024-03-05'::DATE)");
            assertAnswer(
                "Function FTS successfully created.",
                "CREATE OR REPLACE FUNCTION fts(x TIMESTAMP_NTZ) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$ class H { public static String f(java.sql.Timestamp x) { return \"t=\" + x + \"/\" + "
                    + "x.getTime(); } } $$");
            assertAnswer(
                "Function TSN successfully created.",
                "CREATE OR REPLACE FUNCTION tsn(x TIMESTAMP_NTZ) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$ class H { public static String f(String x) { return \"[\" + x + \"]\"; } } $$");
            answer(
                "SELECT tsn('2024-03-05 10:11:12.345'::TIMESTAMP_NTZ)");
            assertAnswer(
                "Function FTL successfully created.",
                "CREATE OR REPLACE FUNCTION ftl(x TIMESTAMP_LTZ) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$ class H { public static String f(java.sql.Timestamp x) { return \"t=\" + x.getTime(); } } "
                    + "$$");
            assertAnswer(
                "t=1709629872345",
                "SELECT ftl('2024-03-05 10:11:12.345 +01:00'::TIMESTAMP_LTZ)");
            assertAnswer(
                "Function TSL successfully created.",
                "CREATE OR REPLACE FUNCTION tsl(x TIMESTAMP_LTZ) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$ class H { public static String f(String x) { return \"[\" + x + \"]\"; } } $$");
            answer(
                "SELECT tsl('2024-03-05 10:11:12.345 +01:00'::TIMESTAMP_LTZ)");
            assertAnswer(
                "Function FTZ successfully created.",
                "CREATE OR REPLACE FUNCTION ftz(x TIMESTAMP_TZ) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$ class H { public static String f(java.sql.Timestamp x) { return \"t=\" + x.getTime(); } } "
                    + "$$");
            assertAnswer(
                "t=1709629872345",
                "SELECT ftz('2024-03-05 10:11:12.345 +01:00'::TIMESTAMP_TZ)");
            assertAnswer(
                "Function TSZ successfully created.",
                "CREATE OR REPLACE FUNCTION tsz(x TIMESTAMP_TZ) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$ class H { public static String f(String x) { return \"[\" + x + \"]\"; } } $$");
            answer(
                "SELECT tsz('2024-03-05 10:11:12.345 +01:00'::TIMESTAMP_TZ)");
            assertAnswer(
                "Function FTIME successfully created.",
                "CREATE OR REPLACE FUNCTION ftime(x TIME) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(java.sql.Time x) { return \"t=\" + x; } } $$");
            assertAnswer(
                "Function FTIMES successfully created.",
                "CREATE OR REPLACE FUNCTION ftimes(x TIME) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(String x) { return \"[\" + x + \"]\"; } } $$");
            assertAnswer(
                "[10:11:12.500000000Z]",
                "SELECT ftimes('10:11:12.5'::TIME)");
            assertAnswer(
                "Function FMAP successfully created.",
                "CREATE OR REPLACE FUNCTION fmap(x OBJECT) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(java.util.Map<String, String> x) { return \"keys=\" + new "
                    + "java.util.TreeMap<String, String>(x); } } $$");
            assertAnswer(
                "keys={a=1, b=x}",
                "SELECT fmap(OBJECT_CONSTRUCT('a', 1, 'b', 'x'))");
            assertAnswer(
                "keys={a=[1,2], n=null}",
                "SELECT fmap(OBJECT_CONSTRUCT('a', ARRAY_CONSTRUCT(1, 2), 'n', PARSE_JSON('null')))");
            answer(
                "SELECT fmap(NULL)");
            assertAnswer(
                "Function FOBJS successfully created.",
                "CREATE OR REPLACE FUNCTION fobjs(x OBJECT) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(String x) { return \"[\" + x + \"]\"; } } $$");
            assertAnswer(
                "[{\"a\":1}]",
                "SELECT fobjs(OBJECT_CONSTRUCT('a', 1))");
            assertAnswer(
                "Function FARR successfully created.",
                "CREATE OR REPLACE FUNCTION farr(x ARRAY) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(String[] x) { return x.length + \":\" + String.join(\"|\", "
                    + "x); } } $$");
            assertAnswer(
                "3:1|x|1.5",
                "SELECT farr(ARRAY_CONSTRUCT(1, 'x', 1.5))");
            assertAnswer(
                "3:a|null|b",
                "SELECT farr(ARRAY_CONSTRUCT('a', NULL, 'b'))");
            assertAnswer(
                "0:",
                "SELECT farr(ARRAY_CONSTRUCT())");
            assertAnswer(
                "Function FARRS successfully created.",
                "CREATE OR REPLACE FUNCTION farrs(x ARRAY) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(String x) { return \"[\" + x + \"]\"; } } $$");
            assertAnswer(
                "[[1,\"x\"]]",
                "SELECT farrs(ARRAY_CONSTRUCT(1, 'x'))");
            answer(
                "CREATE OR REPLACE FUNCTION fvar2(x VARIANT) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "import com.snowflake.snowpark_java.types.Variant; class H { public static String f(Variant x) "
                    + "{ return \"v=\" + x; } } $$");
            answer(
                "SELECT fvar2(PARSE_JSON('{\"a\": \"x\"}'))");
            answer(
                "SELECT fvar2(1.5)");
            answer(
                "SELECT fvar2(TO_VARIANT('s'))");
            answer(
                "SELECT fvar2(NULL)");
            assertAnswer(
                "Function FVARS successfully created.",
                "CREATE OR REPLACE FUNCTION fvars(x VARIANT) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(String x) { return \"[\" + x + \"]\"; } } $$");
            assertAnswer(
                "[{\"a\":\"x\"}]",
                "SELECT fvars(PARSE_JSON('{\"a\": \"x\"}'))");
            assertAnswer(
                "[\"s\"]",
                "SELECT fvars(TO_VARIANT('s'))");
            assertAnswer(
                "[1.5]",
                "SELECT fvars(1.5)");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }

    @Test
    public void timestampTextsMembersAndTheHandlersOwnExceptions() {
        try {
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function TSN successfully created.",
                "CREATE OR REPLACE FUNCTION tsn(x TIMESTAMP_NTZ) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$ class H { public static String f(String x) { return \"[\" + (x == null ? \"NULL\" : "
                    + "x.replace('-', '~').replace(':', '.')) + \"]\"; } } $$");
            assertAnswer(
                "[2024~03~05 10.11.12.345]",
                "SELECT tsn('2024-03-05 10:11:12.345'::TIMESTAMP_NTZ)");
            assertAnswer(
                "[2024~03~05 10.11.12.000]",
                "SELECT tsn('2024-03-05 10:11:12'::TIMESTAMP_NTZ)");
            assertAnswer(
                "[2024~03~05 10.11.12.345]",
                "SELECT tsn('2024-03-05 10:11:12.345678912'::TIMESTAMP_NTZ)");
            assertAnswer(
                "[2024~03~05 00.00.00.000]",
                "SELECT tsn('2024-03-05'::DATE)");
            assertAnswer(
                "[NULL]",
                "SELECT tsn(NULL)");
            assertAnswer(
                "Function TSL successfully created.",
                "CREATE OR REPLACE FUNCTION tsl(x TIMESTAMP_LTZ) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$ class H { public static String f(String x) { return \"[\" + (x == null ? \"NULL\" : "
                    + "x.replace('-', '~').replace(':', '.')) + \"]\"; } } $$");
            assertAnswer(
                "[2024~03~05 09.11.12.345 Z]",
                "SELECT tsl('2024-03-05 10:11:12.345 +01:00'::TIMESTAMP_LTZ)");
            assertAnswer(
                "Function TSZ successfully created.",
                "CREATE OR REPLACE FUNCTION tsz(x TIMESTAMP_TZ) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$ class H { public static String f(String x) { return \"[\" + (x == null ? \"NULL\" : "
                    + "x.replace('-', '~').replace(':', '.')) + \"]\"; } } $$");
            assertAnswer(
                "[2024~03~05 10.11.12.345 +0100]",
                "SELECT tsz('2024-03-05 10:11:12.345 +01:00'::TIMESTAMP_TZ)");
            assertAnswer(
                "Function TSS successfully created.",
                "CREATE OR REPLACE FUNCTION tss(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(String x) { return \"[\" + (x == null ? \"NULL\" : "
                    + "x.replace('-', '~').replace(':', '.')) + \"]\"; } } $$");
            assertAnswer(
                "[2024~03~05 10.11.12.345]",
                "SELECT tss('2024-03-05 10:11:12.345'::TIMESTAMP_NTZ)");
            assertAnswer(
                "[2024~03~05 10.11.12.345 +0100]",
                "SELECT tss('2024-03-05 10:11:12.345 +01:00'::TIMESTAMP_TZ)");
            assertAnswer(
                "[1.5]",
                "SELECT tss(PARSE_JSON('1.5'))");
            assertAnswer(
                "[2024~03~05]",
                "SELECT tss(TO_VARIANT('2024-03-05'::DATE))");
            assertAnswer(
                "Function FTSX successfully created.",
                "CREATE OR REPLACE FUNCTION ftsx(x TIMESTAMP_NTZ) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' "
                    + "AS $$ class H { public static String f(java.sql.Timestamp x) { return x.getTime() + \"/\" + "
                    + "x.getNanos() + \"/\" + x.toString().replace('-', '~').replace(':', '.'); } } $$");
            assertAnswer(
                "Function FDX successfully created.",
                "CREATE OR REPLACE FUNCTION fdx(x DATE) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ class "
                    + "H { public static String f(java.sql.Date x) { return x.getTime() + \"/\" + "
                    + "x.toString().replace('-', '~'); } } $$");
            assertAnswer(
                "Function FTMX successfully created.",
                "CREATE OR REPLACE FUNCTION ftmx(x TIME) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(java.sql.Time x) { return x.getTime() + \"/\" + "
                    + "x.toString().replace(':', '.'); } } $$");
            assertAnswer(
                "Function FMAPN successfully created.",
                "CREATE OR REPLACE FUNCTION fmapn(x OBJECT) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(java.util.Map<String, String> x) { return (x.get(\"n\") == "
                    + "null) + \"/\" + x.containsKey(\"n\") + \"/\" + x.size() + \"/\" + "
                    + "x.getClass().getSimpleName().length() + \"/\" + x.get(\"a\") + \"/\" + x.get(\"o\"); } } $$");
            assertAnswer(
                "true/true/3/7/text/{\"k\":1.5}",
                "SELECT fmapn(OBJECT_CONSTRUCT('a', 'text', 'n', PARSE_JSON('null'), 'o', OBJECT_CONSTRUCT('k', "
                    + "1.5)))");
            assertAnswer(
                "Function FARRN successfully created.",
                "CREATE OR REPLACE FUNCTION farrn(x ARRAY) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(String[] x) { return (x[1] == null) + \"/\" + x.length + "
                    + "\"/\" + x[0] + \"/\" + x[2]; } } $$");
            assertAnswer(
                "true/3/a/{\"k\":1}",
                "SELECT farrn(ARRAY_CONSTRUCT('a', NULL, OBJECT_CONSTRUCT('k', 1)))");
            assertAnswer(
                "true/3/1.5/true",
                "SELECT farrn(ARRAY_CONSTRUCT(1.50, PARSE_JSON('null'), TRUE))");
            assertAnswer(
                "Function FVAR2 successfully created.",
                "CREATE OR REPLACE FUNCTION fvar2(x VARIANT) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') AS $$ import "
                    + "com.snowflake.snowpark_java.types.Variant; class H { public static String f(Variant x) { "
                    + "return \"v=\" + x; } } $$");
            assertAnswer(
                "v={\"a\":\"x\"}",
                "SELECT fvar2(PARSE_JSON('{\"a\": \"x\"}'))");
            assertAnswer(
                "v=1.5",
                "SELECT fvar2(1.5)");
            answer(
                "SELECT fvar2(TO_VARIANT('s'))");
            assertAnswer(
                "v=null",
                "SELECT fvar2(NULL)");
            assertAnswer(
                "v=null",
                "SELECT fvar2(PARSE_JSON('null'))");
            answer(
                "SELECT fvar2(ARRAY_CONSTRUCT(1, NULL))");
            assertAnswer(
                "Function FTHROW successfully created.",
                "CREATE OR REPLACE FUNCTION fthrow(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(int x) { if (x > 0) { throw new "
                    + "IllegalArgumentException(\"bad \" + x); } return \"ok\"; } } $$");
            assertAnswer(
                "ok",
                "SELECT fthrow(0)");
            assertAnswer(
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.IllegalArgumentException: bad 1 | "
                    + "\tat function_handler_0//H.f(InlineCode.java:1) |  in function FTHROW with handler H.f",
                "SELECT fthrow(1)");
            assertAnswer(
                "Function FTHROW2 successfully created.",
                "CREATE OR REPLACE FUNCTION fthrow2(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H {\n  static String deep(int x) {\n    throw new IllegalStateException(\"deep \" + "
                    + "x);\n  }\n  public static String f(int x) {\n    return deep(x);\n  }\n} $$");
            assertAnswer(
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.IllegalStateException: deep 1 | \tat "
                    + "function_handler_0//H.deep(InlineCode.java:3) | \tat "
                    + "function_handler_0//H.f(InlineCode.java:6) |  in function FTHROW2 with handler H.f",
                "SELECT fthrow2(1)");
            assertAnswer(
                "Function FTHROW3 successfully created.",
                "CREATE OR REPLACE FUNCTION fthrow3(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(int x) { try { Integer.parseInt(\"q\"); } catch "
                    + "(NumberFormatException e) { throw new RuntimeException(\"wrapped\", e); } return \"ok\"; } } "
                    + "$$");
            answer(
                "SELECT fthrow3(1)");
            assertAnswer(
                "Function FTHROW4 successfully created.",
                "CREATE OR REPLACE FUNCTION fthrow4(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(int x) { throw new RuntimeException(); } } $$");
            assertAnswer(
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.RuntimeException | \tat "
                    + "function_handler_0//H.f(InlineCode.java:1) |  in function FTHROW4 with handler H.f",
                "SELECT fthrow4(1)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 99 unexpected 'NULL'. | syntax "
                    + "error line 1 at position 104 unexpected 'ON'.",
                "CREATE OR REPLACE FUNCTION fstrict(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' "
                    + "RETURNS NULL ON NULL INPUT AS $$ class H { public static String f(int x) { return \"got \" + "
                    + "x; } } $$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function FSTRICT.",
                "SELECT fstrict(NULL) IS NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function FSTRICT.",
                "SELECT fstrict(2)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 98 unexpected 'ON'. | syntax "
                    + "error line 1 at position 112 unexpected 'AS'.",
                "CREATE OR REPLACE FUNCTION fcalled(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' "
                    + "CALLED ON NULL INPUT AS $$ class H { public static String f(Integer x) { return \"got \" + x; "
                    + "} } $$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function FCALLED.",
                "SELECT fcalled(NULL)");
            assertAnswer(
                "Function FTWO successfully created.",
                "CREATE OR REPLACE FUNCTION ftwo(a NUMBER, b VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = "
                    + "'H.f' AS $$ class H { public static String f(long a, String b) { return a + \":\" + b; } } $$");
            assertAnswer(
                "2:42",
                "SELECT ftwo(1.6, 42)");
            assertAnswer(
                "3:null",
                "SELECT ftwo('3', NULL)");
            assertAnswer(
                "2:x",
                "SELECT ftwo(b => 'x', a => 2)");
            assertAnswer(
                "Function FRET successfully created.",
                "CREATE OR REPLACE FUNCTION fret(x VARCHAR) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(String x) { return x; } } $$");
            assertAnswer(
                "12",
                "SELECT fret('12')");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }

    @Test
    public void theInstantsDatesTimesAndTimestampsHold() {
        try {
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function GD successfully created.",
                "CREATE OR REPLACE FUNCTION gd(x DATE) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ class "
                    + "H { public static String f(java.sql.Date x) { return String.valueOf(x.getTime()); } } $$");
            assertAnswer(
                "1709596800000",
                "SELECT gd('2024-03-05'::DATE)");
            assertAnswer(
                "-86400000",
                "SELECT gd('1969-12-31'::DATE)");
            assertAnswer(
                "1719792000000",
                "SELECT gd('2024-07-01'::DATE)");
            assertAnswer(
                "Function GT successfully created.",
                "CREATE OR REPLACE FUNCTION gt(x TIME) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ class "
                    + "H { public static String f(java.sql.Time x) { return String.valueOf(x.getTime()); } } $$");
            assertAnswer(
                "36672500",
                "SELECT gt('10:11:12.5'::TIME)");
            assertAnswer(
                "0",
                "SELECT gt('00:00:00'::TIME)");
            assertAnswer(
                "86399999",
                "SELECT gt('23:59:59.999999999'::TIME)");
            assertAnswer(
                "Function GN successfully created.",
                "CREATE OR REPLACE FUNCTION gn(x TIMESTAMP_NTZ) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$ class H { public static String f(java.sql.Timestamp x) { return x.getTime() + \"/\" + "
                    + "x.getNanos(); } } $$");
            assertAnswer(
                "1709633472345/345678912",
                "SELECT gn('2024-03-05 10:11:12.345678912'::TIMESTAMP_NTZ)");
            assertAnswer(
                "1719828672000/0",
                "SELECT gn('2024-07-01 10:11:12'::TIMESTAMP_NTZ)");
            assertAnswer(
                "Function GL successfully created.",
                "CREATE OR REPLACE FUNCTION gl(x TIMESTAMP_LTZ) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$ class H { public static String f(java.sql.Timestamp x) { return x.getTime() + \"/\" + "
                    + "x.getNanos(); } } $$");
            assertAnswer(
                "1719821472500/500000000",
                "SELECT gl('2024-07-01 10:11:12.5 +02:00'::TIMESTAMP_LTZ)");
            assertAnswer(
                "Function GZ successfully created.",
                "CREATE OR REPLACE FUNCTION gz(x TIMESTAMP_TZ) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$ class H { public static String f(java.sql.Timestamp x) { return x.getTime() + \"/\" + "
                    + "x.getNanos(); } } $$");
            assertAnswer(
                "1719821472500/500000000",
                "SELECT gz('2024-07-01 10:11:12.5 +02:00'::TIMESTAMP_TZ)");
            assertAnswer(
                "Function RD successfully created.",
                "CREATE OR REPLACE FUNCTION rd(x NUMBER) RETURNS DATE LANGUAGE JAVA HANDLER = 'H.f' AS $$ class H "
                    + "{ public static java.sql.Date f(long x) { return new java.sql.Date(x); } } $$");
            assertAnswer(
                "1970-01-01|1970-01-02|1970-01-01|1969-12-31|2024-07-01",
                "SELECT rd(0)::VARCHAR, rd(86400000)::VARCHAR, rd(86399999)::VARCHAR, rd(-1)::VARCHAR, "
                    + "rd(1719792000000)::VARCHAR");
            assertAnswer(
                "Function RT successfully created.",
                "CREATE OR REPLACE FUNCTION rt(x NUMBER) RETURNS TIME LANGUAGE JAVA HANDLER = 'H.f' AS $$ class H "
                    + "{ public static java.sql.Time f(long x) { return new java.sql.Time(x); } } $$");
            assertAnswer(
                "REFUSED Time value out of range: 86401000 ms in function RT with handler H.f",
                "SELECT TO_VARCHAR(rt(36672500), 'HH24.MI.SS.FF3'), TO_VARCHAR(rt(0), 'HH24.MI.SS.FF3'), "
                    + "TO_VARCHAR(rt(86400000 + 1000), 'HH24.MI.SS.FF3')");
            assertAnswer(
                "Function RN successfully created.",
                "CREATE OR REPLACE FUNCTION rn(x NUMBER) RETURNS TIMESTAMP_NTZ LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$ class H { public static java.sql.Timestamp f(long x) { java.sql.Timestamp t = new "
                    + "java.sql.Timestamp(x); t.setNanos(123456789); return t; } } $$");
            assertAnswer(
                "1970/01/01 00.00.00.123456789|2024/07/01 10.11.12.123456789",
                "SELECT TO_VARCHAR(rn(0), 'YYYY/MM/DD HH24.MI.SS.FF9'), TO_VARCHAR(rn(1719828672000), 'YYYY/MM/DD "
                    + "HH24.MI.SS.FF9')");
            assertAnswer(
                "Function RL successfully created.",
                "CREATE OR REPLACE FUNCTION rl(x NUMBER) RETURNS TIMESTAMP_LTZ LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$ class H { public static java.sql.Timestamp f(long x) { return new java.sql.Timestamp(x); } "
                    + "} $$");
            assertAnswer(
                "1719828672500",
                "SELECT DATE_PART(epoch_millisecond, rl(1719828672500))");
            assertAnswer(
                "Function RR successfully created.",
                "CREATE OR REPLACE FUNCTION rr(x TIMESTAMP_NTZ) RETURNS TIMESTAMP_NTZ LANGUAGE JAVA HANDLER = "
                    + "'H.f' AS $$ class H { public static java.sql.Timestamp f(java.sql.Timestamp x) { return x; } "
                    + "} $$");
            assertAnswer(
                "true",
                "SELECT rr('2024-07-01 10:11:12.345678912'::TIMESTAMP_NTZ) = '2024-07-01 "
                    + "10:11:12.345678912'::TIMESTAMP_NTZ");
            assertAnswer(
                "Function RRD successfully created.",
                "CREATE OR REPLACE FUNCTION rrd(x DATE) RETURNS DATE LANGUAGE JAVA HANDLER = 'H.f' AS $$ class H "
                    + "{ public static java.sql.Date f(java.sql.Date x) { return x; } } $$");
            assertAnswer(
                "true|true",
                "SELECT rrd('2024-07-01'::DATE) = '2024-07-01'::DATE, rrd('1969-12-31'::DATE) = '1969-12-31'::DATE");
            assertAnswer(
                "Function RRT successfully created.",
                "CREATE OR REPLACE FUNCTION rrt(x TIME) RETURNS TIME LANGUAGE JAVA HANDLER = 'H.f' AS $$ class H "
                    + "{ public static java.sql.Time f(java.sql.Time x) { return x; } } $$");
            assertAnswer(
                "true|true",
                "SELECT rrt('10:11:12'::TIME) = '10:11:12'::TIME, rrt('10:11:12.5'::TIME) = '10:11:12.5'::TIME");
            assertAnswer(
                "10:11:12|10:11:12|10:11:12",
                "SELECT '10:11:12.5'::TIME::VARCHAR, TO_VARCHAR('10:11:12.5'::TIME), '10:11:12'::TIME::VARCHAR");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
            assertAnswer(
                "1709596800000",
                "SELECT gd('2024-03-05'::DATE)");
            assertAnswer(
                "1709633472345/345678912",
                "SELECT gn('2024-03-05 10:11:12.345678912'::TIMESTAMP_NTZ)");
            assertAnswer(
                "36672500",
                "SELECT gt('10:11:12.5'::TIME)");
            assertAnswer(
                "1719821472500/500000000",
                "SELECT gl('2024-07-01 10:11:12.5 +02:00'::TIMESTAMP_LTZ)");
            assertAnswer(
                "1970-01-01|1970-01-02",
                "SELECT rd(0)::VARCHAR, rd(86400000)::VARCHAR");
            assertAnswer(
                "1970/01/01 00.00.00.123456789",
                "SELECT TO_VARCHAR(rn(0), 'YYYY/MM/DD HH24.MI.SS.FF9')");
            assertAnswer(
                "true",
                "SELECT rr('2024-07-01 10:11:12.345678912'::TIMESTAMP_NTZ) = '2024-07-01 "
                    + "10:11:12.345678912'::TIMESTAMP_NTZ");
            assertAnswer(
                "true",
                "SELECT rrd('2024-07-01'::DATE) = '2024-07-01'::DATE");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }
}
