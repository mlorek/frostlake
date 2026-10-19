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
 * What an inline Java function answers for the value its handler returns, by the declared RETURNS type. A
 * {@code byte[]} is a BINARY, a {@code String[]} an ARRAY of texts with a null element one of its SQL NULLs, a
 * {@code Map} an OBJECT of texts with a null value a JSON null, a {@code java.sql.Timestamp} a timestamp read
 * at UTC with its nanoseconds, a {@code long}, an {@code int} and a {@code short} whole numbers, a
 * {@code double} a FLOAT. A {@code BigDecimal} is rounded half to even to the declared scale - 2.5 is 2 for a
 * NUMBER, 1.25 is 1.2 for a NUMBER(5,1) - and refused when the declared width cannot hold it, as a whole
 * number is. A text is read as the declared type reads one: a number for a NUMBER or a FLOAT, JSON for a
 * VARIANT, and JSON of its kind for an ARRAY or an OBJECT - {@code Error parsing JSON: output JSON is not an
 * array type} otherwise. A text for a declared VARCHAR(2) is answered in full. A text for a BINARY, a DATE, a
 * timestamp or a BOOLEAN is refused when the function is created. The session's TIMEZONE is set by the test
 * and unset whatever the assertions make of the statements between. Every answer is live-verified.
 */
public class JavaFunctionReturnTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void anAnswerBecomesTheDeclaredType() {
        try {
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function FBINR successfully created.",
                "CREATE OR REPLACE FUNCTION fbinr(x VARCHAR) RETURNS BINARY LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static byte[] f(String x) { return x.getBytes(); } } $$");
            assertAnswer(
                "4142|BINARY",
                "SELECT HEX_ENCODE(fbinr('AB')), TYPEOF(fbinr('AB')::VARIANT)");
            assertAnswer(
                "Function FBINN successfully created.",
                "CREATE OR REPLACE FUNCTION fbinn(x VARCHAR) RETURNS BINARY LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static byte[] f(String x) { return null; } } $$");
            assertAnswer(
                "true",
                "SELECT fbinn('x') IS NULL");
            assertAnswer(
                "Function FBIN1 successfully created.",
                "CREATE OR REPLACE FUNCTION fbin1(x VARCHAR) RETURNS BINARY(1) LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$ class H { public static byte[] f(String x) { return x.getBytes(); } } $$");
            assertAnswer(
                "REFUSED System byte length limit (1) exceeded by string 'AB...' in function FBIN1 with handler "
                    + "H.f",
                "SELECT HEX_ENCODE(fbin1('AB'))");
            assertAnswer(
                "REFUSED Snowflake type BINARY[LOB](67108864){nullable} is not supported for Java return type "
                    + "String in function FBINS with handler H.f",
                "CREATE OR REPLACE FUNCTION fbins(x VARCHAR) RETURNS BINARY LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(String x) { return x; } } $$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function FBINS.",
                "SELECT HEX_ENCODE(fbins('4142'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function FBINS.",
                "SELECT HEX_ENCODE(fbins('zz'))");
            assertAnswer(
                "Function FARRR successfully created.",
                "CREATE OR REPLACE FUNCTION farrr(x VARCHAR) RETURNS ARRAY LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String[] f(String x) { return new String[] {x, x}; } } $$");
            assertAnswer(
                "[\"q\",\"q\"]|2|VARCHAR",
                "SELECT farrr('q'), ARRAY_SIZE(farrr('q')), TYPEOF(farrr('q')[0])");
            assertAnswer(
                "Function FARRN successfully created.",
                "CREATE OR REPLACE FUNCTION farrn(x VARCHAR) RETURNS ARRAY LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String[] f(String x) { return new String[] {x, null, \"[1,2]\", \"7\","
                    + " \"{\\\"a\\\":1}\"}; } } $$");
            assertAnswer(
                "[\"q\",undefined,\"[1,2]\",\"7\",\"{\\\"a\\\":1}\"]",
                "SELECT farrn('q')");
            assertAnswer(
                "null|true|VARCHAR|VARCHAR|VARCHAR",
                "SELECT TYPEOF(farrn('q')[1]), farrn('q')[1] IS NULL, TYPEOF(farrn('q')[2]), "
                    + "TYPEOF(farrn('q')[3]), TYPEOF(farrn('q')[4])");
            assertAnswer(
                "Function FARRE successfully created.",
                "CREATE OR REPLACE FUNCTION farre(x VARCHAR) RETURNS ARRAY LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String[] f(String x) { return new String[0]; } } $$");
            assertAnswer(
                "[]|0",
                "SELECT farre('q'), ARRAY_SIZE(farre('q'))");
            assertAnswer(
                "Function FARRNULL successfully created.",
                "CREATE OR REPLACE FUNCTION farrnull(x VARCHAR) RETURNS ARRAY LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String[] f(String x) { return null; } } $$");
            assertAnswer(
                "true",
                "SELECT farrnull('q') IS NULL");
            assertAnswer(
                "Function FMAPR successfully created.",
                "CREATE OR REPLACE FUNCTION fmapr(x VARCHAR) RETURNS OBJECT LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static java.util.Map<String, String> f(String x) { java.util.Map<String, "
                    + "String> m = new java.util.HashMap<>(); m.put(\"k\", x); m.put(\"b\", \"2\"); m.put(\"j\", "
                    + "\"[1,2]\"); m.put(\"n\", null); return m; } } $$");
            assertAnswer(
                "{\"b\":\"2\",\"j\":\"[1,2]\",\"k\":\"v\",\"n\":null}",
                "SELECT fmapr('v')");
            assertAnswer(
                "v|VARCHAR|VARCHAR|VARCHAR|NULL_VALUE|false",
                "SELECT fmapr('v'):k, TYPEOF(fmapr('v'):k), TYPEOF(fmapr('v'):b), TYPEOF(fmapr('v'):j), "
                    + "TYPEOF(fmapr('v'):n), fmapr('v'):n IS NULL");
            assertAnswer(
                "Function FMAPN successfully created.",
                "CREATE OR REPLACE FUNCTION fmapn(x VARCHAR) RETURNS OBJECT LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static java.util.Map<String, String> f(String x) { return null; } } $$");
            assertAnswer(
                "true",
                "SELECT fmapn('v') IS NULL");
            assertAnswer(
                "Function FTSR successfully created.",
                "CREATE OR REPLACE FUNCTION ftsr(x VARCHAR) RETURNS TIMESTAMP_NTZ LANGUAGE JAVA HANDLER = 'H.f' "
                    + "AS $$ class H { public static java.sql.Timestamp f(String x) { return new "
                    + "java.sql.Timestamp(0L); } } $$");
            assertAnswer(
                "1970/01/01 00.00.00.000",
                "SELECT TO_VARCHAR(ftsr('x'), 'YYYY/MM/DD HH24.MI.SS.FF3')");
            assertAnswer(
                "Function FTSL successfully created.",
                "CREATE OR REPLACE FUNCTION ftsl(x VARCHAR) RETURNS TIMESTAMP_LTZ LANGUAGE JAVA HANDLER = 'H.f' "
                    + "AS $$ class H { public static java.sql.Timestamp f(String x) { return new "
                    + "java.sql.Timestamp(86400000L); } } $$");
            assertAnswer(
                "1970/01/02 00.00.00.000 Z|86400",
                "SELECT TO_VARCHAR(ftsl('x'), 'YYYY/MM/DD HH24.MI.SS.FF3 TZHTZM'), DATE_PART(epoch_second, "
                    + "ftsl('x'))");
            assertAnswer(
                "REFUSED Snowflake type TIMESTAMP_NTZ[SB16](0,9){nullable} is not supported for Java return type "
                    + "String in function FTSS with handler H.f",
                "CREATE OR REPLACE FUNCTION ftss(x VARCHAR) RETURNS TIMESTAMP_NTZ LANGUAGE JAVA HANDLER = 'H.f' "
                    + "AS $$ class H { public static String f(String x) { return x; } } $$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function FTSS.",
                "SELECT TO_VARCHAR(ftss('2024-03-05 10:11:12.5'), 'YYYY/MM/DD HH24.MI.SS.FF3')");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function FTSS.",
                "SELECT ftss('nope')");
            assertAnswer(
                "REFUSED Snowflake type DATE[SB4](38,0){nullable} is not supported for Java return type String in "
                    + "function FDATES with handler H.f",
                "CREATE OR REPLACE FUNCTION fdates(x VARCHAR) RETURNS DATE LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(String x) { return x; } } $$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function FDATES.",
                "SELECT fdates('2024-03-05')::VARCHAR");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function FDATES.",
                "SELECT fdates('nope')");
            assertAnswer(
                "Function FTIMES successfully created.",
                "CREATE OR REPLACE FUNCTION ftimes(x VARCHAR) RETURNS TIME LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(String x) { return x; } } $$");
            assertAnswer(
                "10:11:12",
                "SELECT ftimes('10:11:12.5')::VARCHAR");
            assertAnswer(
                "Function JAS successfully created.",
                "CREATE OR REPLACE FUNCTION jas(x VARCHAR) RETURNS ARRAY LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(String x) { return x; } } $$");
            assertAnswer(
                "{\"k\":[1,2]}|ARRAY|2",
                "SELECT OBJECT_CONSTRUCT('k', jas('[1, 2]')), TYPEOF(jas('[1, 2]')), ARRAY_SIZE(jas('[1, 2]'))");
            assertAnswer(
                "REFUSED Error parsing JSON: Document is not a valid JSON object: nope in function JAS with "
                    + "handler H.f",
                "SELECT jas('nope')");
            assertAnswer(
                "REFUSED Error parsing JSON: output JSON is not an array type: {\"a\":1} in function JAS with "
                    + "handler H.f",
                "SELECT jas('{\"a\":1}')");
            assertAnswer(
                "REFUSED Error parsing JSON: output JSON is not an array type: 7 in function JAS with handler H.f",
                "SELECT jas('7')");
            assertAnswer(
                "Function JOS successfully created.",
                "CREATE OR REPLACE FUNCTION jos(x VARCHAR) RETURNS OBJECT LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(String x) { return x; } } $$");
            assertAnswer(
                "{\"a\":[1,2],\"b\":1}|OBJECT",
                "SELECT jos('{\"b\":1,\"a\":[1,2]}'), TYPEOF(jos('{\"b\":1}'))");
            assertAnswer(
                "REFUSED Error parsing JSON: output JSON is not an object type: [1] in function JOS with handler "
                    + "H.f",
                "SELECT jos('[1]')");
            assertAnswer(
                "REFUSED Error parsing JSON: Document is not a valid JSON object: nope in function JOS with "
                    + "handler H.f",
                "SELECT jos('nope')");
            assertAnswer(
                "Function JVS successfully created.",
                "CREATE OR REPLACE FUNCTION jvs(x VARCHAR) RETURNS VARIANT LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(String x) { return x; } } $$");
            assertAnswer(
                "{\"a\":1}|OBJECT",
                "SELECT jvs('{\"a\":1}'), TYPEOF(jvs('{\"a\":1}'))");
            assertAnswer(
                "REFUSED Error parsing JSON: Document is not a valid JSON object: abc in function JVS with "
                    + "handler H.f",
                "SELECT jvs('abc'), TYPEOF(jvs('abc'))");
            assertAnswer(
                "7|INTEGER",
                "SELECT jvs('7'), TYPEOF(jvs('7'))");
            assertAnswer(
                "false|NULL_VALUE",
                "SELECT jvs('null') IS NULL, TYPEOF(jvs('null'))");
            assertAnswer(
                "1.5|DECIMAL",
                "SELECT jvs('1.5'), TYPEOF(jvs('1.5'))");
            assertAnswer(
                "q|VARCHAR",
                "SELECT jvs('\"q\"'), TYPEOF(jvs('\"q\"'))");
            assertAnswer(
                "Function JNS successfully created.",
                "CREATE OR REPLACE FUNCTION jns(x VARCHAR) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(String x) { return x; } } $$");
            assertAnswer(
                "12|INTEGER",
                "SELECT jns('12'), TYPEOF(jns('12')::VARIANT)");
            assertAnswer(
                "13",
                "SELECT jns('12.6')");
            assertAnswer(
                "REFUSED Numeric value 'abc' is not recognized in function JNS with handler H.f",
                "SELECT jns('abc')");
            assertAnswer(
                "Function JN2S successfully created.",
                "CREATE OR REPLACE FUNCTION jn2s(x VARCHAR) RETURNS NUMBER(10,2) LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$ class H { public static String f(String x) { return x; } } $$");
            assertAnswer(
                "12.35",
                "SELECT jn2s('12.345')::VARCHAR");
            assertAnswer(
                "7.00",
                "SELECT jn2s('7')::VARCHAR");
            assertAnswer(
                "Function JFS successfully created.",
                "CREATE OR REPLACE FUNCTION jfs(x VARCHAR) RETURNS FLOAT LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(String x) { return x; } } $$");
            assertAnswer(
                "1.5",
                "SELECT jfs('1.5')::VARCHAR");
            assertAnswer(
                "REFUSED Numeric value 'abc' is not recognized in function JFS with handler H.f",
                "SELECT jfs('abc')");
            assertAnswer(
                "REFUSED Snowflake type BOOLEAN[SB1](38,0){nullable} is not supported for Java return type String "
                    + "in function JBS with handler H.f",
                "CREATE OR REPLACE FUNCTION jbs(x VARCHAR) RETURNS BOOLEAN LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(String x) { return x; } } $$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function JBS.",
                "SELECT jbs('true')");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function JBS.",
                "SELECT jbs('maybe')");
            assertAnswer(
                "Function FDEC successfully created.",
                "CREATE OR REPLACE FUNCTION fdec(x NUMBER(10,2)) RETURNS NUMBER(10,2) LANGUAGE JAVA HANDLER = "
                    + "'H.f' AS $$ class H { public static java.math.BigDecimal f(java.math.BigDecimal x) { return "
                    + "x.multiply(new java.math.BigDecimal(\"1.111\")); } } $$");
            assertAnswer(
                "1.39|true",
                "SELECT fdec(1.25)::VARCHAR, fdec(1.25) = 1.39");
            assertAnswer(
                "Function FBD0 successfully created.",
                "CREATE OR REPLACE FUNCTION fbd0(x VARCHAR) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static java.math.BigDecimal f(String x) { return new "
                    + "java.math.BigDecimal(x); } } $$");
            assertAnswer(
                "2",
                "SELECT fbd0('1.5')::VARCHAR");
            assertAnswer(
                "2",
                "SELECT fbd0('2.5')::VARCHAR");
            assertAnswer(
                "-2",
                "SELECT fbd0('-1.5')::VARCHAR");
            assertAnswer(
                "Function FBD5 successfully created.",
                "CREATE OR REPLACE FUNCTION fbd5(x VARCHAR) RETURNS NUMBER(5,1) LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$ class H { public static java.math.BigDecimal f(String x) { return new "
                    + "java.math.BigDecimal(x); } } $$");
            assertAnswer(
                "1.2",
                "SELECT fbd5('1.25')::VARCHAR");
            assertAnswer(
                "REFUSED BigDecimal value cannot be losslessly represented as a Snowflake NUMBER with a precision "
                    + "of 5 and a scale of 1 in function FBD5 with handler H.f",
                "SELECT fbd5('123456.7')");
            assertAnswer(
                "Function FLONG successfully created.",
                "CREATE OR REPLACE FUNCTION flong(x VARCHAR) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static long f(String x) { return Long.parseLong(x); } } $$");
            assertShapedAnswer(
                "<ID19>",
                "SELECT flong('9223372036854775807')");
            assertAnswer(
                "INTEGER",
                "SELECT TYPEOF(flong('5')::VARIANT)");
            assertAnswer(
                "Function FINT successfully created.",
                "CREATE OR REPLACE FUNCTION fint(x VARCHAR) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static int f(String x) { return Integer.parseInt(x); } } $$");
            assertAnswer(
                "5|INTEGER",
                "SELECT fint('5'), TYPEOF(fint('5')::VARIANT)");
            assertAnswer(
                "Function FSHORT successfully created.",
                "CREATE OR REPLACE FUNCTION fshort(x VARCHAR) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static short f(String x) { return Short.parseShort(x); } } $$");
            assertAnswer(
                "5|INTEGER",
                "SELECT fshort('5'), TYPEOF(fshort('5')::VARIANT)");
            assertAnswer(
                "Function FN3 successfully created.",
                "CREATE OR REPLACE FUNCTION fn3(x VARCHAR) RETURNS NUMBER(3,0) LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$ class H { public static int f(String x) { return Integer.parseInt(x); } } $$");
            assertAnswer(
                "999",
                "SELECT fn3('999')");
            assertAnswer(
                "REFUSED Java value cannot be losslessly represented as a Snowflake NUMBER with a precision of 3, "
                    + "value: 1000 in function FN3 with handler H.f",
                "SELECT fn3('1000')");
            assertAnswer(
                "Function FDBL successfully created.",
                "CREATE OR REPLACE FUNCTION fdbl(x VARCHAR) RETURNS FLOAT LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static double f(String x) { return Double.parseDouble(x); } } $$");
            assertAnswer(
                "1.5|DOUBLE",
                "SELECT fdbl('1.5')::VARCHAR, TYPEOF(fdbl('1.5')::VARIANT)");
            assertAnswer(
                "NaN",
                "SELECT fdbl('NaN')::VARCHAR");
            assertAnswer(
                "Function FFLT successfully created.",
                "CREATE OR REPLACE FUNCTION fflt(x VARCHAR) RETURNS FLOAT LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static float f(String x) { return Float.parseFloat(x); } } $$");
            assertAnswer(
                "1.100000024",
                "SELECT fflt('1.1')::VARCHAR");
            assertAnswer(
                "Function FBOOL successfully created.",
                "CREATE OR REPLACE FUNCTION fbool(x VARCHAR) RETURNS BOOLEAN LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static boolean f(String x) { return x.length() > 1; } } $$");
            assertAnswer(
                "true|false",
                "SELECT fbool('ab'), fbool('a')");
            assertAnswer(
                "Function FBOOLB successfully created.",
                "CREATE OR REPLACE FUNCTION fboolb(x VARCHAR) RETURNS BOOLEAN LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static Boolean f(String x) { return null; } } $$");
            assertAnswer(
                "true",
                "SELECT fboolb('a') IS NULL");
            assertAnswer(
                "Function FSTR2 successfully created.",
                "CREATE OR REPLACE FUNCTION fstr2(x VARCHAR) RETURNS VARCHAR(2) LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$ class H { public static String f(String x) { return x; } } $$");
            assertAnswer(
                "ab",
                "SELECT fstr2('ab')");
            assertAnswer(
                "abcdef",
                "SELECT fstr2('abcdef')");
            assertAnswer(
                "Function FSTRN successfully created.",
                "CREATE OR REPLACE FUNCTION fstrn(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { public static String f(String x) { return null; } } $$");
            assertAnswer(
                "true",
                "SELECT fstrn('a') IS NULL");
            assertAnswer(
                "Function FVARR successfully created.",
                "CREATE OR REPLACE FUNCTION fvarr(x VARCHAR) RETURNS VARIANT LANGUAGE JAVA HANDLER = 'H.f' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') AS $$ class H { public static "
                    + "com.snowflake.snowpark_java.types.Variant f(String x) { return new "
                    + "com.snowflake.snowpark_java.types.Variant(x); } } $$");
            assertAnswer(
                "{\"a\":[1,2]}|OBJECT",
                "SELECT fvarr('{\"a\":[1,2]}'), TYPEOF(fvarr('{\"a\":[1,2]}'))");
            answer(
                "SELECT fvarr('abc'), TYPEOF(fvarr('abc'))");
            answer(
                "SELECT fvarr('7'), TYPEOF(fvarr('7'))");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }
}
