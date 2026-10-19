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
 * The Java classes a handler's parameter may take. byte, Byte, char, Character, Object and the java.time classes
 * are refused as the procedure is created; a list is paired as a map is - an OBJECT's alone - and refused for
 * any other type in the words the account has for the map; a Variant takes a VARIANT, a String[] an ARRAY whose
 * NULL elements, nested ones included, read null, and a map an OBJECT. Each CALL reaches the overload the
 * account reaches (live-verified).
 */
public class JavaHandlerArgumentClassTest extends MeasuredCellsTestSupport {

    @Test
    public void classesTheAccountTakesAndRefuses() {
        try {
            assertAnswer(
                "REFUSED Unsupported argument type: byte in function JA with handler H.run",
                "CREATE OR REPLACE PROCEDURE ja(a INT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, byte a) { return "
                    + "\"v=\" + (a); } } $$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function JA.",
                "CALL ja(5)");
            assertAnswer(
                "REFUSED Unsupported argument type: byte in function JA with handler H.run",
                "CREATE OR REPLACE PROCEDURE ja(a INT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, byte a) { return "
                    + "\"v=\" + (a); } } $$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function JA.",
                "CALL ja(300)");
            assertAnswer(
                "REFUSED Unsupported argument type: java.lang.Byte in function JA with handler H.run",
                "CREATE OR REPLACE PROCEDURE ja(a INT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, Byte a) { return "
                    + "\"v=\" + (a); } } $$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function JA.",
                "CALL ja(NULL)");
            assertAnswer(
                "REFUSED Unsupported argument type: char in function JA with handler H.run",
                "CREATE OR REPLACE PROCEDURE ja(a VARCHAR) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, char a) { return "
                    + "\"v=\" + (a); } } $$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function JA.",
                "CALL ja('x')");
            assertAnswer(
                "REFUSED Unsupported argument type: java.lang.Character in function JA with handler H.run",
                "CREATE OR REPLACE PROCEDURE ja(a VARCHAR) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, Character a) { "
                    + "return \"v=\" + (a); } } $$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function JA.",
                "CALL ja('xy')");
            assertAnswer(
                "REFUSED Unsupported argument type: java.time.LocalDate in function JA with handler H.run",
                "CREATE OR REPLACE PROCEDURE ja(a DATE) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, "
                    + "java.time.LocalDate a) { return \"v=\" + (a); } } $$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function JA.",
                "CALL ja('2024-01-31'::DATE)");
            assertAnswer(
                "REFUSED Unsupported argument type: java.time.LocalDateTime in function JA with handler H.run",
                "CREATE OR REPLACE PROCEDURE ja(a TIMESTAMP_NTZ) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = "
                    + "'11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, "
                    + "java.time.LocalDateTime a) { return \"v=\" + (a); } } $$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function JA.",
                "CALL ja('2024-01-31 10:11:12'::TIMESTAMP_NTZ)");
            assertAnswer(
                "REFUSED Unsupported argument type: java.time.LocalTime in function JA with handler H.run",
                "CREATE OR REPLACE PROCEDURE ja(a TIME) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, "
                    + "java.time.LocalTime a) { return \"v=\" + (a); } } $$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function JA.",
                "CALL ja('10:11:12'::TIME)");
            assertAnswer(
                "Function JA successfully created.",
                "CREATE OR REPLACE PROCEDURE ja(a VARIANT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, "
                    + "com.snowflake.snowpark_java.types.Variant a) { return \"v=\" + (a.asJsonString()); } } $$");
            assertAnswer(
                "v={\"k\":1}",
                "CALL ja(PARSE_JSON('{\"k\":1}'))");
            assertAnswer(
                "Function JA successfully created.",
                "CREATE OR REPLACE PROCEDURE ja(a VARIANT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, "
                    + "com.snowflake.snowpark_java.types.Variant a) { return \"v=\" + (a == null); } } $$");
            assertAnswer(
                "v=true",
                "CALL ja(NULL)");
            assertAnswer(
                "REFUSED Snowflake type ARRAY[LOB](38,0){nullable} is not supported as input to argument at index "
                    + "0 with Java type java.util.Map<String, String> in function JA with handler H.run",
                "CREATE OR REPLACE PROCEDURE ja(a ARRAY) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, "
                    + "java.util.List<String> a) { return \"v=\" + (a); } } $$");
            assertAnswer(
                "v=false",
                "CALL ja(ARRAY_CONSTRUCT('p', 'q'))");
            assertAnswer(
                "REFUSED Snowflake type VARIANT[LOB](38,0){nullable} is not supported as input to argument at "
                    + "index 0 with Java type java.util.Map<String, String> in function JA with handler H.run",
                "CREATE OR REPLACE PROCEDURE ja(a VARIANT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, "
                    + "java.util.Map<String, String> a) { return \"v=\" + (a); } } $$");
            assertAnswer(
                "v=false",
                "CALL ja(PARSE_JSON('{\"k\":1}'))");
            assertAnswer(
                "REFUSED Snowflake type VARIANT[LOB](38,0){nullable} is not supported as input to argument at "
                    + "index 0 with Java type int in function JA with handler H.run",
                "CREATE OR REPLACE PROCEDURE ja(a VARIANT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, int a) { return "
                    + "\"v=\" + (a); } } $$");
            assertAnswer(
                "v=false",
                "CALL ja(PARSE_JSON('7'))");
            assertAnswer(
                "Function JA successfully created.",
                "CREATE OR REPLACE PROCEDURE ja(a TIMESTAMP_TZ) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = "
                    + "'11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, "
                    + "java.sql.Timestamp a) { return \"v=\" + (a.getTime()); } } $$");
            assertAnswer(
                "v=1706688672000",
                "CALL ja('2024-01-31 10:11:12 +02:00'::TIMESTAMP_TZ)");
            assertAnswer(
                "REFUSED Snowflake type REAL[DOUBLE](38,0){nullable} is not supported as input to argument at "
                    + "index 0 with Java type int in function JA with handler H.run",
                "CREATE OR REPLACE PROCEDURE ja(a FLOAT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, int a) { return "
                    + "\"v=\" + (a); } } $$");
            assertAnswer(
                "v=false",
                "CALL ja('NaN'::FLOAT)");
            assertAnswer(
                "REFUSED Snowflake type REAL[DOUBLE](38,0){nullable} is not supported as input to argument at "
                    + "index 0 with Java type int in function JA with handler H.run",
                "CREATE OR REPLACE PROCEDURE ja(a FLOAT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, int a) { return "
                    + "\"v=\" + (a); } } $$");
            assertAnswer(
                "v=false",
                "CALL ja('inf'::FLOAT)");
            assertAnswer(
                "REFUSED Snowflake type REAL[DOUBLE](38,0){nullable} is not supported as input to argument at "
                    + "index 0 with Java type long in function JA with handler H.run",
                "CREATE OR REPLACE PROCEDURE ja(a FLOAT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, long a) { return "
                    + "\"v=\" + (a); } } $$");
            assertAnswer(
                "v=false",
                "CALL ja(1.9::FLOAT)");
            assertAnswer(
                "Function JA successfully created.",
                "CREATE OR REPLACE PROCEDURE ja(a OBJECT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, "
                    + "java.util.Map<String, String> a) { return \"v=\" + (a); } } $$");
            assertAnswer(
                "v={k=null, m=1}",
                "CALL ja(OBJECT_CONSTRUCT_KEEP_NULL('k', NULL, 'm', 1))");
            assertAnswer(
                "Function JA successfully created.",
                "CREATE OR REPLACE PROCEDURE ja(a ARRAY) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, String[] a) { "
                    + "return \"v=\" + (java.util.Arrays.toString(a)); } } $$");
            assertAnswer(
                "v=[p, null, [1,null]]",
                "CALL ja(ARRAY_CONSTRUCT('p', NULL, ARRAY_CONSTRUCT(1, NULL)))");
            assertAnswer(
                "Function JA successfully created.",
                "CREATE OR REPLACE PROCEDURE ja(a VARIANT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, String a) { "
                    + "return \"v=\" + (a); } } $$");
            assertAnswer(
                "v=[1,null]",
                "CALL ja(PARSE_JSON('[1, null]'))");
            assertAnswer(
                "Function JA successfully created.",
                "CREATE OR REPLACE PROCEDURE ja(a VARIANT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, String a) { "
                    + "return \"v=\" + (a); } } $$");
            assertAnswer(
                "v=null",
                "CALL ja(PARSE_JSON('null'))");
            assertAnswer(
                "Function JA successfully created.",
                "CREATE OR REPLACE PROCEDURE ja(a NUMBER(38, 0)) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = "
                    + "'11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, "
                    + "java.math.BigInteger a) { return \"v=\" + (a); } } $$");
            assertAnswer(
                "v=12345678901234567890",
                "CALL ja(12345678901234567890)");
            assertAnswer(
                "Function JA successfully created.",
                "CREATE OR REPLACE PROCEDURE ja(a BOOLEAN) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, Boolean a) { "
                    + "return \"v=\" + (a); } } $$");
            assertAnswer(
                "v=null",
                "CALL ja(NULL)");
            assertAnswer(
                "Function JA successfully created.",
                "CREATE OR REPLACE PROCEDURE ja(a BINARY) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, byte[] a) { "
                    + "return \"v=\" + (a.length); } } $$");
            assertAnswer(
                "v=2",
                "CALL ja(TO_BINARY('ABCD'))");
            assertAnswer(
                "REFUSED Unsupported argument type: java.lang.Object in function JA with handler H.run",
                "CREATE OR REPLACE PROCEDURE ja(a VARCHAR) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, Object a) { "
                    + "return \"v=\" + (a); } } $$");
            assertAnswer(
                "REFUSED Boolean value 'x' is not recognized",
                "CALL ja('x')");
            assertAnswer(
                "Function JA successfully created.",
                "CREATE OR REPLACE PROCEDURE ja(a GEOGRAPHY) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, String a) { "
                    + "return \"v=\" + (a); } } $$");
            assertAnswer(
                "REFUSED Boolean value 'POINT(1 2)' is not recognized",
                "CALL ja('POINT(1 2)')");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS ja(ARRAY)");
            answer(
                "DROP PROCEDURE IF EXISTS ja(BINARY)");
            answer(
                "DROP PROCEDURE IF EXISTS ja(BOOLEAN)");
            answer(
                "DROP PROCEDURE IF EXISTS ja(DATE)");
            answer(
                "DROP PROCEDURE IF EXISTS ja(FLOAT)");
            answer(
                "DROP PROCEDURE IF EXISTS ja(GEOGRAPHY)");
            answer(
                "DROP PROCEDURE IF EXISTS ja(INT)");
            answer(
                "DROP PROCEDURE IF EXISTS ja(NUMBER(38, 0))");
            answer(
                "DROP PROCEDURE IF EXISTS ja(OBJECT)");
            answer(
                "DROP PROCEDURE IF EXISTS ja(TIME)");
            answer(
                "DROP PROCEDURE IF EXISTS ja(TIMESTAMP_NTZ)");
            answer(
                "DROP PROCEDURE IF EXISTS ja(TIMESTAMP_TZ)");
            answer(
                "DROP PROCEDURE IF EXISTS ja(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS ja(VARIANT)");
        }
    }

    @Test
    public void listsMapsAndOverloads() {
        try {
            assertAnswer(
                "REFUSED Snowflake type VARIANT[LOB](38,0){nullable} is not supported as input to argument at "
                    + "index 0 with Java type java.util.Map<String, String> in function JB with handler H.run",
                "CREATE OR REPLACE PROCEDURE jb(a VARIANT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, "
                    + "java.util.List<String> a) { return \"v=\" + (a); } } $$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function JB.",
                "CALL jb(PARSE_JSON('[1]'))");
            assertAnswer(
                "Function JC successfully created.",
                "CREATE OR REPLACE PROCEDURE jc(a OBJECT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, "
                    + "java.util.List<String> a) { return \"v=\" + (a); } } $$");
            assertAnswer(
                "REFUSED Snowflake type TEXT[LOB](134217728){nullable} is not supported as input to argument at "
                    + "index 0 with Java type java.util.Map<String, String> in function JD with handler H.run",
                "CREATE OR REPLACE PROCEDURE jd(a VARCHAR) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, "
                    + "java.util.List<String> a) { return \"v=\" + (a); } } $$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function JD.",
                "CALL jd('x')");
            assertAnswer(
                "REFUSED Unsupported argument type: java.util.ArrayList in function JE with handler H.run",
                "CREATE OR REPLACE PROCEDURE je(a ARRAY) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, "
                    + "java.util.ArrayList<String> a) { return \"v=\" + (a); } } $$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function JE.",
                "CALL je(ARRAY_CONSTRUCT('p'))");
            assertAnswer(
                "REFUSED Unsupported argument type: java.util.Collection in function JF with handler H.run",
                "CREATE OR REPLACE PROCEDURE jf(a ARRAY) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, "
                    + "java.util.Collection<String> a) { return \"v=\" + (a); } } $$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function JF.",
                "CALL jf(ARRAY_CONSTRUCT('p'))");
            assertAnswer(
                "Function JG successfully created.",
                "CREATE OR REPLACE PROCEDURE jg(a OBJECT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, "
                    + "java.util.Map<String, String> a) { return \"v=\" + (a); } } $$");
            assertAnswer(
                "v={k=[1,null], m={\"n\":null}}",
                "CALL jg(OBJECT_CONSTRUCT('k', ARRAY_CONSTRUCT(1, NULL), 'm', OBJECT_CONSTRUCT_KEEP_NULL('n', "
                    + "NULL)))");
            assertAnswer(
                "Function JH successfully created.",
                "CREATE OR REPLACE PROCEDURE jh(a ARRAY) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, String[] a) { "
                    + "return \"v=\" + (java.util.Arrays.toString(a)); } } $$");
            assertAnswer(
                "v=[{\"n\":null}, 1.5, true, x]",
                "CALL jh(ARRAY_CONSTRUCT(OBJECT_CONSTRUCT_KEEP_NULL('n', NULL), 1.5, TRUE, 'x'))");
            assertAnswer(
                "Function JO successfully created.",
                "CREATE OR REPLACE PROCEDURE jo(a VARIANT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, "
                    + "com.snowflake.snowpark_java.types.Variant a) { return \"v=\" + (\"V:\" + a.asJsonString()); } "
                    + "} $$");
            assertAnswer(
                "v=V:1",
                "CALL jo(PARSE_JSON('1'))");
            assertAnswer(
                "Function JO successfully created.",
                "CREATE OR REPLACE PROCEDURE jo(a TIMESTAMP_TZ) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = "
                    + "'11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, "
                    + "java.sql.Timestamp a) { return \"v=\" + (\"T:\" + a.getTime()); } } $$");
            assertAnswer(
                "v=T:1706688672000",
                "CALL jo('2024-01-31 10:11:12 +02:00'::TIMESTAMP_TZ)");
            assertAnswer(
                "Function JO successfully created.",
                "CREATE OR REPLACE PROCEDURE jo(a VARCHAR) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, String a) { "
                    + "return \"v=\" + (\"S:\" + a); } } $$");
            assertAnswer(
                "v=S:x",
                "CALL jo('x')");
            assertAnswer(
                "v=V:\"NaN\"",
                "CALL jo('NaN'::FLOAT)");
            assertAnswer(
                "v=V:1.5",
                "CALL jo(1.5::FLOAT)");
            assertAnswer(
                "v=V:1.5",
                "CALL jo(1.5)");
            assertAnswer(
                "v=S:true",
                "CALL jo(TRUE)");
            assertAnswer(
                "v=V:[\"p\"]",
                "CALL jo(ARRAY_CONSTRUCT('p'))");
            assertAnswer(
                "v=S:null",
                "CALL jo(NULL)");
            assertAnswer(
                "v=V:{\"k\":1}",
                "CALL jo(OBJECT_CONSTRUCT('k', 1))");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS jb(VARIANT)");
            answer(
                "DROP PROCEDURE IF EXISTS jc(OBJECT)");
            answer(
                "DROP PROCEDURE IF EXISTS jd(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS je(ARRAY)");
            answer(
                "DROP PROCEDURE IF EXISTS jf(ARRAY)");
            answer(
                "DROP PROCEDURE IF EXISTS jg(OBJECT)");
            answer(
                "DROP PROCEDURE IF EXISTS jh(ARRAY)");
            answer(
                "DROP PROCEDURE IF EXISTS jo(TIMESTAMP_TZ)");
            answer(
                "DROP PROCEDURE IF EXISTS jo(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS jo(VARIANT)");
        }
    }
}
