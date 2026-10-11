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
package dev.frostlake.procedural;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * What a CALL of a Java procedure answers: one row in a column named after the procedure and typed by its
 * RETURNS - a NULL when the handler hands back none - holding the handler's value as that type. A
 * {@code BigDecimal} is rounded half to even to the declared scale and refused when the declared width cannot
 * hold it; a {@code long} and an {@code int} are refused likewise; a text returned for a NUMBER or a FLOAT is read
 * as the cast reads it. A {@code java.sql.Date} is the date of its instant at UTC, a {@code java.sql.Time} the
 * time of day it counts and a {@code java.sql.Timestamp} its wall clock at UTC; a {@code String[]} is an ARRAY of
 * texts, a null element one of its SQL NULLs, a {@code Map} an OBJECT and a {@code byte[]} a BINARY; a text
 * returned for a VARIANT is read as PARSE_JSON reads one - a number written with an exponent a DOUBLE, the
 * text {@code null} a JSON null where a Java {@code null} is a SQL NULL. The column keeps a declared
 * VARCHAR(n) and BINARY(n), and bytes past a declared width are refused. The classes a declared type does not
 * take are refused when the procedure is created. DESCRIBE RESULT of the CALL and a table created over its
 * RESULT_SCAN carry the declared type too. Every answer is live-verified.
 */
public class JavaHandlerResultTest extends MeasuredCellsTestSupport {

    @Test
    public void theAnswerIsTypedByReturns() {
        assertAnswer(
            "Function V00 successfully created.",
            "CREATE OR REPLACE PROCEDURE v00() RETURNS NUMBER(10,2) LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES "
                + "= ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static java.math.BigDecimal run(Session s) { return new "
                + "java.math.BigDecimal(\"1.38875\"); } } $$");
        assertAnswer(
            "1.39",
            "CALL v00()");
        assertShape(
            "V00:NUMBER(10,2)",
            "CALL v00()");
        assertAnswer(
            "NUMBER(10,2)[SB8] / 1.39",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function V01 successfully created.",
            "CREATE OR REPLACE PROCEDURE v01() RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static java.math.BigDecimal run(Session s) { return new "
                + "java.math.BigDecimal(\"2.5\"); } } $$");
        assertAnswer(
            "2",
            "CALL v01()");
        assertShape(
            "V01:NUMBER(38,0)",
            "CALL v01()");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 2",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function V02 successfully created.",
            "CREATE OR REPLACE PROCEDURE v02() RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static long run(Session s) { return 7L; } } $$");
        assertAnswer(
            "7",
            "CALL v02()");
        assertShape(
            "V02:NUMBER(38,0)",
            "CALL v02()");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 7",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function V03 successfully created.",
            "CREATE OR REPLACE PROCEDURE v03() RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static Long run(Session s) { return null; } } $$");
        assertAnswer(
            "null",
            "CALL v03()");
        assertShape(
            "V03:NUMBER(38,0)",
            "CALL v03()");
        assertAnswer(
            "NUMBER(38,0)[SB16] / null",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function V04 successfully created.",
            "CREATE OR REPLACE PROCEDURE v04() RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static int run(Session s) { return 7; } } $$");
        assertAnswer(
            "7",
            "CALL v04()");
        assertShape(
            "V04:NUMBER(38,0)",
            "CALL v04()");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 7",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function V05 successfully created.",
            "CREATE OR REPLACE PROCEDURE v05() RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static String run(Session s) { return \"7\"; } } $$");
        assertAnswer(
            "7",
            "CALL v05()");
        assertShape(
            "V05:NUMBER(38,0)",
            "CALL v05()");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 7",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function V06 successfully created.",
            "CREATE OR REPLACE PROCEDURE v06() RETURNS NUMBER(3,0) LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES "
                + "= ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static long run(Session s) { return 1000L; } } $$");
        assertAnswer(
            "REFUSED Java value cannot be losslessly represented as a Snowflake NUMBER with a precision of 3, "
                + "value: 1000 in function V06 with handler H.run",
            "CALL v06()");
        assertShape(
            "REFUSED Java value cannot be losslessly represented as a Snowflake NUMBER with a precision of 3, "
                + "value: 1000 in function V06 with handler H.run",
            "CALL v06()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function V07 successfully created.",
            "CREATE OR REPLACE PROCEDURE v07() RETURNS FLOAT LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static double run(Session s) { return 1.5; } } $$");
        assertAnswer(
            "1.5",
            "CALL v07()");
        shape(
            "CALL v07()");
        assertAnswer(
            "FLOAT[DOUBLE] / 1.5",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function V08 successfully created.",
            "CREATE OR REPLACE PROCEDURE v08() RETURNS FLOAT LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static Double run(Session s) { return null; } } $$");
        assertAnswer(
            "null",
            "CALL v08()");
        shape(
            "CALL v08()");
        assertAnswer(
            "FLOAT[DOUBLE] / null",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function V09 successfully created.",
            "CREATE OR REPLACE PROCEDURE v09() RETURNS FLOAT LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static float run(Session s) { return 1.5f; } } $$");
        assertAnswer(
            "1.5",
            "CALL v09()");
        shape(
            "CALL v09()");
        assertAnswer(
            "FLOAT[DOUBLE] / 1.5",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function V10 successfully created.",
            "CREATE OR REPLACE PROCEDURE v10() RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static String run(Session s) { return \"abc\"; } } $$");
        assertAnswer(
            "abc",
            "CALL v10()");
        assertShape(
            "V10:VARCHAR(134217728)",
            "CALL v10()");
        assertAnswer(
            "VARCHAR(134217728)[LOB] / abc",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function V11 successfully created.",
            "CREATE OR REPLACE PROCEDURE v11() RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static String run(Session s) { return null; } } $$");
        assertAnswer(
            "null",
            "CALL v11()");
        assertShape(
            "V11:VARCHAR(134217728)",
            "CALL v11()");
        assertAnswer(
            "VARCHAR(134217728)[LOB] / null",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "REFUSED Snowflake type TEXT[LOB](134217728){nullable} is not supported for Java return type int in "
                + "function V12 with handler H.run",
            "CREATE OR REPLACE PROCEDURE v12() RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static int run(Session s) { return 7; } } $$");
        assertAnswer(
            "Function V13 successfully created.",
            "CREATE OR REPLACE PROCEDURE v13() RETURNS VARCHAR(2) LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static String run(Session s) { return \"abc\"; } } $$");
        assertAnswer(
            "abc",
            "CALL v13()");
        assertShape(
            "V13:VARCHAR(2)",
            "CALL v13()");
        answer(
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function V14 successfully created.",
            "CREATE OR REPLACE PROCEDURE v14() RETURNS BOOLEAN LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static boolean run(Session s) { return true; } } $$");
        assertAnswer(
            "true",
            "CALL v14()");
        assertShape(
            "V14:BOOLEAN",
            "CALL v14()");
        assertAnswer(
            "BOOLEAN[SB1] / true",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function V15 successfully created.",
            "CREATE OR REPLACE PROCEDURE v15() RETURNS BOOLEAN LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static Boolean run(Session s) { return null; } } $$");
        assertAnswer(
            "null",
            "CALL v15()");
        assertShape(
            "V15:BOOLEAN",
            "CALL v15()");
        assertAnswer(
            "BOOLEAN[SB1] / null",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function V16 successfully created.",
            "CREATE OR REPLACE PROCEDURE v16() RETURNS DATE LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static java.sql.Date run(Session s) { return new java.sql.Date(86400000L); } } $$");
        assertAnswer(
            "1970-01-02",
            "CALL v16()");
        assertShape(
            "V16:DATE",
            "CALL v16()");
        assertAnswer(
            "DATE[SB4] / 1970-01-02",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function V17 successfully created.",
            "CREATE OR REPLACE PROCEDURE v17() RETURNS DATE LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static java.sql.Date run(Session s) { return null; } } $$");
        assertAnswer(
            "null",
            "CALL v17()");
        assertShape(
            "V17:DATE",
            "CALL v17()");
        assertAnswer(
            "DATE[SB4] / null",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "REFUSED Snowflake type DATE[SB4](38,0){nullable} is not supported for Java return type String in "
                + "function V18 with handler H.run",
            "CREATE OR REPLACE PROCEDURE v18() RETURNS DATE LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static String run(Session s) { return \"2024-01-02\"; } } $$");
        assertAnswer(
            "Function V19 successfully created.",
            "CREATE OR REPLACE PROCEDURE v19() RETURNS TIME LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static java.sql.Time run(Session s) { return new java.sql.Time(36672000L); } } $$");
        assertAnswer(
            "10:11:12",
            "CALL v19()");
        assertShape(
            "V19:TIME",
            "CALL v19()");
        assertAnswer(
            "TIME(9)[SB8] / 10:11:12",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function V20 successfully created.",
            "CREATE OR REPLACE PROCEDURE v20() RETURNS TIMESTAMP_NTZ LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static java.sql.Timestamp run(Session s) { return "
                + "new java.sql.Timestamp(0L); } } $$");
        assertAnswer(
            "1970-01-01T00:00",
            "CALL v20()");
        assertShape(
            "V20:TIMESTAMP_NTZ",
            "CALL v20()");
        answer(
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function V21 successfully created.",
            "CREATE OR REPLACE PROCEDURE v21() RETURNS TIMESTAMP_NTZ LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static java.sql.Timestamp run(Session s) { return "
                + "new java.sql.Timestamp(1706695872123L); } } $$");
        answer(
            "CALL v21()");
        assertShape(
            "V21:TIMESTAMP_NTZ",
            "CALL v21()");
        answer(
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function V22 successfully created.",
            "CREATE OR REPLACE PROCEDURE v22() RETURNS TIMESTAMP_LTZ LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static java.sql.Timestamp run(Session s) { return "
                + "new java.sql.Timestamp(1706695872000L); } } $$");
        answer(
            "CALL v22()");
        assertShape(
            "V22:TIMESTAMP_LTZ",
            "CALL v22()");
        answer(
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function V23 successfully created.",
            "CREATE OR REPLACE PROCEDURE v23() RETURNS ARRAY LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static String[] run(Session s) { return new String[] {\"a\", \"b\"}; } } $$");
        assertAnswer(
            "[\"a\",\"b\"]",
            "CALL v23()");
        assertShape(
            "V23:ARRAY",
            "CALL v23()");
        assertAnswer(
            "ARRAY[LOB] / [\"a\",\"b\"]",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function V24 successfully created.",
            "CREATE OR REPLACE PROCEDURE v24() RETURNS ARRAY LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static String[] run(Session s) { return null; } } $$");
        assertAnswer(
            "null",
            "CALL v24()");
        assertShape(
            "V24:ARRAY",
            "CALL v24()");
        assertAnswer(
            "ARRAY[LOB] / null",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function V25 successfully created.",
            "CREATE OR REPLACE PROCEDURE v25() RETURNS ARRAY LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static String[] run(Session s) { return new String[] {\"1\", null}; } } $$");
        assertAnswer(
            "[\"1\",undefined]",
            "CALL v25()");
        assertShape(
            "V25:ARRAY",
            "CALL v25()");
        assertAnswer(
            "ARRAY[LOB] / [\"1\",undefined]",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function V26 successfully created.",
            "CREATE OR REPLACE PROCEDURE v26() RETURNS OBJECT LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static java.util.Map<String, String> run(Session s) { return "
                + "java.util.Map.of(\"a\", \"1\"); } } $$");
        assertAnswer(
            "{\"a\":\"1\"}",
            "CALL v26()");
        assertShape(
            "V26:OBJECT",
            "CALL v26()");
        assertAnswer(
            "OBJECT[LOB] / {\"a\":\"1\"}",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function V27 successfully created.",
            "CREATE OR REPLACE PROCEDURE v27() RETURNS BINARY LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static byte[] run(Session s) { return new byte[] {(byte) 171, (byte) 205}; } } $$");
        assertAnswer(
            "ABCD",
            "CALL v27()");
        assertShape(
            "V27:BINARY",
            "CALL v27()");
        assertAnswer(
            "BINARY(67108864)[LOB] / ABCD",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function V28 successfully created.",
            "CREATE OR REPLACE PROCEDURE v28() RETURNS VARIANT LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static String run(Session s) { return \"{\\\"a\\\":1}\"; } } $$");
        assertAnswer(
            "{\"a\":1}",
            "CALL v28()");
        assertShape(
            "V28:VARIANT",
            "CALL v28()");
        assertAnswer(
            "VARIANT[LOB] / {\"a\":1}",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function V29 successfully created.",
            "CREATE OR REPLACE PROCEDURE v29() RETURNS VARIANT LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static String run(Session s) { return \"abc\"; } } $$");
        assertAnswer(
            "REFUSED Error parsing JSON: Document is not a valid JSON object: abc in function V29 with handler "
                + "H.run",
            "CALL v29()");
        assertShape(
            "REFUSED Error parsing JSON: Document is not a valid JSON object: abc in function V29 with handler "
                + "H.run",
            "CALL v29()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
    }

    @Test
    public void roundingRangeAndTextsReadAsNumbers() {
        assertAnswer(
            "Function XJ00 successfully created.",
            "CREATE OR REPLACE PROCEDURE xj00() RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static java.math.BigDecimal run(Session s) { return new "
                + "java.math.BigDecimal(\"3.5\"); } } $$");
        assertAnswer(
            "4",
            "CALL xj00()");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 4",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XJ01 successfully created.",
            "CREATE OR REPLACE PROCEDURE xj01() RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static java.math.BigDecimal run(Session s) { return new "
                + "java.math.BigDecimal(\"2.7\"); } } $$");
        assertAnswer(
            "3",
            "CALL xj01()");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 3",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XJ02 successfully created.",
            "CREATE OR REPLACE PROCEDURE xj02() RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static java.math.BigDecimal run(Session s) { return new "
                + "java.math.BigDecimal(\"-2.5\"); } } $$");
        assertAnswer(
            "-2",
            "CALL xj02()");
        assertAnswer(
            "NUMBER(38,0)[SB16] / -2",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XJ03 successfully created.",
            "CREATE OR REPLACE PROCEDURE xj03() RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static java.math.BigDecimal run(Session s) { return new "
                + "java.math.BigDecimal(\"-2.7\"); } } $$");
        assertAnswer(
            "-3",
            "CALL xj03()");
        assertAnswer(
            "NUMBER(38,0)[SB16] / -3",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XJ04 successfully created.",
            "CREATE OR REPLACE PROCEDURE xj04() RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static java.math.BigDecimal run(Session s) { return new "
                + "java.math.BigDecimal(\"0.5\"); } } $$");
        assertAnswer(
            "0",
            "CALL xj04()");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 0",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XJ05 successfully created.",
            "CREATE OR REPLACE PROCEDURE xj05() RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static java.math.BigDecimal run(Session s) { return new "
                + "java.math.BigDecimal(\"1.5\"); } } $$");
        assertAnswer(
            "2",
            "CALL xj05()");
        assertAnswer(
            "NUMBER(38,0)[SB16] / 2",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XJ06 successfully created.",
            "CREATE OR REPLACE PROCEDURE xj06() RETURNS NUMBER(10,2) LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static java.math.BigDecimal run(Session s) { "
                + "return new java.math.BigDecimal(\"1.385\"); } } $$");
        assertAnswer(
            "1.38",
            "CALL xj06()");
        assertAnswer(
            "NUMBER(10,2)[SB8] / 1.38",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XJ07 successfully created.",
            "CREATE OR REPLACE PROCEDURE xj07() RETURNS NUMBER(10,2) LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static java.math.BigDecimal run(Session s) { "
                + "return new java.math.BigDecimal(\"1.375\"); } } $$");
        assertAnswer(
            "1.38",
            "CALL xj07()");
        assertAnswer(
            "NUMBER(10,2)[SB8] / 1.38",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XJ08 successfully created.",
            "CREATE OR REPLACE PROCEDURE xj08() RETURNS NUMBER(10,2) LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static java.math.BigDecimal run(Session s) { "
                + "return new java.math.BigDecimal(\"1.365\"); } } $$");
        assertAnswer(
            "1.36",
            "CALL xj08()");
        assertAnswer(
            "NUMBER(10,2)[SB8] / 1.36",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XJ09 successfully created.",
            "CREATE OR REPLACE PROCEDURE xj09() RETURNS NUMBER(10,2) LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static java.math.BigDecimal run(Session s) { "
                + "return new java.math.BigDecimal(\"-1.385\"); } } $$");
        assertAnswer(
            "-1.38",
            "CALL xj09()");
        assertAnswer(
            "NUMBER(10,2)[SB8] / -1.38",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XJ10 successfully created.",
            "CREATE OR REPLACE PROCEDURE xj10() RETURNS NUMBER(10,2) LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static java.math.BigDecimal run(Session s) { "
                + "return new java.math.BigDecimal(\"123456789.12\"); } } $$");
        assertAnswer(
            "REFUSED BigDecimal value cannot be losslessly represented as a Snowflake NUMBER with a precision of "
                + "10 and a scale of 2 in function XJ10 with handler H.run",
            "CALL xj10()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "REFUSED Snowflake type FIXED[SB16](38,0){nullable} is not supported for Java return type double in "
                + "function XD0 with handler H.run",
            "CREATE OR REPLACE PROCEDURE xd0() RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static double run(Session s) { return 2.7; } } $$");
        assertAnswer(
            "REFUSED Snowflake type FIXED[SB16](10,2){nullable} is not supported for Java return type double in "
                + "function XD1 with handler H.run",
            "CREATE OR REPLACE PROCEDURE xd1() RETURNS NUMBER(10,2) LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES "
                + "= ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static double run(Session s) { return 1.385; } } $$");
        assertAnswer(
            "REFUSED Cannot encode number with a non-zero scale as a long in function XL0 with handler H.run",
            "CREATE OR REPLACE PROCEDURE xl0() RETURNS NUMBER(10,2) LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES "
                + "= ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static long run(Session s) { return 7L; } } $$");
        assertAnswer(
            "Function XS0 successfully created.",
            "CREATE OR REPLACE PROCEDURE xs0() RETURNS NUMBER(10,2) LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES "
                + "= ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static String run(Session s) { return \"1.385\"; } } $$");
        assertAnswer(
            "1.39",
            "CALL xs0()");
        assertAnswer(
            "NUMBER(10,2)[SB8] / 1.39",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XS1 successfully created.",
            "CREATE OR REPLACE PROCEDURE xs1() RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static String run(Session s) { return \"x\"; } } $$");
        assertAnswer(
            "REFUSED Numeric value 'x' is not recognized in function XS1 with handler H.run",
            "CALL xs1()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XS2 successfully created.",
            "CREATE OR REPLACE PROCEDURE xs2() RETURNS FLOAT LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static String run(Session s) { return \"1.5\"; } } $$");
        assertAnswer(
            "1.5",
            "CALL xs2()");
        assertAnswer(
            "FLOAT[DOUBLE] / 1.5",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "REFUSED Snowflake type BOOLEAN[SB1](38,0){nullable} is not supported for Java return type String in "
                + "function XS3 with handler H.run",
            "CREATE OR REPLACE PROCEDURE xs3() RETURNS BOOLEAN LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static String run(Session s) { return \"true\"; } } $$");
        assertAnswer(
            "REFUSED Snowflake type REAL[DOUBLE](38,0){nullable} is not supported for Java return type int in "
                + "function XI0 with handler H.run",
            "CREATE OR REPLACE PROCEDURE xi0() RETURNS FLOAT LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static int run(Session s) { return 7; } } $$");
        assertAnswer(
            "Function XN8 successfully created.",
            "CREATE OR REPLACE PROCEDURE xn8() RETURNS VARIANT LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static String run(Session s) { return null; } } $$");
        assertAnswer(
            "null",
            "CALL xn8()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 81 | Invalid argument types for function "
                + "'||': (BOOLEAN, VARCHAR(3), VARCHAR(134217728), VARCHAR(3), VARCHAR(134217728))",
            "SELECT ($1 IS NULL) || ' / ' || NVL(IS_NULL_VALUE($1)::VARCHAR, 'null') || ' / ' || NVL(TYPEOF($1), "
                + "'null') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XN9 successfully created.",
            "CREATE OR REPLACE PROCEDURE xn9() RETURNS VARIANT LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static String run(Session s) { return \"null\"; } } $$");
        assertAnswer(
            "null",
            "CALL xn9()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 81 | Invalid argument types for function "
                + "'||': (BOOLEAN, VARCHAR(3), VARCHAR(134217728), VARCHAR(3), VARCHAR(134217728))",
            "SELECT ($1 IS NULL) || ' / ' || NVL(IS_NULL_VALUE($1)::VARCHAR, 'null') || ' / ' || NVL(TYPEOF($1), "
                + "'null') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
    }

    @Test
    public void aTextReadAsJsonAndADeclaredWidth() {
        assertAnswer(
            "Function N9 successfully created.",
            "CREATE OR REPLACE PROCEDURE n9() RETURNS VARIANT LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static String run(Session s) { return null; } } $$");
        assertAnswer(
            "null",
            "CALL n9()");
        assertAnswer(
            "sql-null / no-type",
            "SELECT IFF($1 IS NULL, 'sql-null', 'value') || ' / ' || NVL(TYPEOF($1), 'no-type') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function NA successfully created.",
            "CREATE OR REPLACE PROCEDURE na() RETURNS VARIANT LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static String run(Session s) { return \"null\"; } } $$");
        assertAnswer(
            "null",
            "CALL na()");
        assertAnswer(
            "value / NULL_VALUE",
            "SELECT IFF($1 IS NULL, 'sql-null', 'value') || ' / ' || NVL(TYPEOF($1), 'no-type') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function DJ successfully created.",
            "CREATE OR REPLACE PROCEDURE dj() RETURNS VARIANT LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static String run(Session s) { return \"1.5\"; } } $$");
        assertAnswer(
            "1.5",
            "CALL dj()");
        assertAnswer(
            "VARIANT[LOB] / 1.5",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function DK successfully created.",
            "CREATE OR REPLACE PROCEDURE dk() RETURNS VARIANT LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static String run(Session s) { return \"{\\\"f\\\": 1.5e0, \\\"i\\\": 2}\"; } } "
                + "$$");
        assertAnswer(
            "{\"f\":1.500000000000000e+00,\"i\":2}",
            "CALL dk()");
        assertAnswer(
            "VARIANT[LOB] / {\"f\":1.500000000000000e+00,\"i\":2}",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function W5 successfully created.",
            "CREATE OR REPLACE PROCEDURE w5() RETURNS VARCHAR(2) LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static String run(Session s) { return \"ab\"; } } $$");
        assertAnswer(
            "ab",
            "CALL w5()");
        assertShape(
            "W5:VARCHAR(2)",
            "CALL w5()");
        assertAnswer(
            "VARCHAR(2)[LOB] / ab",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function V0 successfully created.",
            "CREATE OR REPLACE PROCEDURE v0() RETURNS VARIANT LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static String run(Session s) { return \"1.5e0\"; } } $$");
        assertAnswer(
            "1.5",
            "CALL v0()");
        assertAnswer(
            "VARIANT[LOB] / 1.5",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function V1 successfully created.",
            "CREATE OR REPLACE PROCEDURE v1() RETURNS VARIANT LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static String run(Session s) { return \"[1.0, 2, 1e2, 1.50]\"; } } $$");
        assertAnswer(
            "[1,2,1.000000000000000e+02,1.5]",
            "CALL v1()");
        assertAnswer(
            "VARIANT[LOB] / [1,2,1.000000000000000e+02,1.5]",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function V2 successfully created.",
            "CREATE OR REPLACE PROCEDURE v2() RETURNS BINARY(2) LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static byte[] run(Session s) { return new byte[] {1, 2, 3}; } } $$");
        assertAnswer(
            "REFUSED System byte length limit (2) exceeded by string '\u0001\u0002\u0003...' in function V2 with "
                + "handler H.run",
            "CALL v2()");
        assertShape(
            "REFUSED System byte length limit (2) exceeded by string '\u0001\u0002\u0003...' in function V2 with "
                + "handler H.run",
            "CALL v2()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function V3 successfully created.",
            "CREATE OR REPLACE PROCEDURE v3() RETURNS VARIANT LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static String run(Session s) { return \"NaN\"; } } $$");
        assertAnswer(
            "NaN",
            "CALL v3()");
        assertAnswer(
            "VARIANT[LOB] / NaN",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function V4 successfully created.",
            "CREATE OR REPLACE PROCEDURE v4() RETURNS VARIANT LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static String run(Session s) { return \"[undefined, null]\"; } } $$");
        assertAnswer(
            "[undefined,null]",
            "CALL v4()");
        assertAnswer(
            "VARIANT[LOB] / [undefined,null]",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
    }

    @Test
    public void describeResultAndATableOverTheScan() {
        assertAnswer(
            "Function JAVA_NUM successfully created.",
            "CREATE OR REPLACE PROCEDURE java_num() RETURNS NUMBER(10,2) LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static java.math.BigDecimal run(Session s) { "
                + "return new java.math.BigDecimal(\"1.38875\"); } } $$");
        assertAnswer(
            "1.39",
            "CALL java_num()");
        assertAnswer(
            "JAVA_NUM|NUMBER(10,2)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE RESULT LAST_QUERY_ID()");
        assertAnswer(
            "JAVA_NUM / NUMBER(10,2) / Y",
            "SELECT \"name\" || ' / ' || \"type\" || ' / ' || \"null?\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "1.39",
            "CALL java_num()");
        assertAnswer(
            "Table CT_JAVA_NUM successfully created.",
            "CREATE OR REPLACE TABLE ct_java_num AS SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "JAVA_NUM|NUMBER(10,2)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE ct_java_num");
        assertAnswer(
            "JAVA_NUM / NUMBER(10,2) / Y",
            "SELECT \"name\" || ' / ' || \"type\" || ' / ' || \"null?\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function JAVA_TS successfully created.",
            "CREATE OR REPLACE PROCEDURE java_ts() RETURNS TIMESTAMP_NTZ LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static java.sql.Timestamp run(Session s) { return "
                + "new java.sql.Timestamp(0L); } } $$");
        assertAnswer(
            "1970-01-01T00:00",
            "CALL java_ts()");
        assertAnswer(
            "JAVA_TS|TIMESTAMP_NTZ(9)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE RESULT LAST_QUERY_ID()");
        assertAnswer(
            "JAVA_TS / TIMESTAMP_NTZ(9) / Y",
            "SELECT \"name\" || ' / ' || \"type\" || ' / ' || \"null?\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "1970-01-01T00:00",
            "CALL java_ts()");
        assertAnswer(
            "Table CT_JAVA_TS successfully created.",
            "CREATE OR REPLACE TABLE ct_java_ts AS SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "JAVA_TS|TIMESTAMP_NTZ(9)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE ct_java_ts");
        assertAnswer(
            "JAVA_TS / TIMESTAMP_NTZ(9) / Y",
            "SELECT \"name\" || ' / ' || \"type\" || ' / ' || \"null?\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function JAVA_ARR successfully created.",
            "CREATE OR REPLACE PROCEDURE java_arr() RETURNS ARRAY LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static String[] run(Session s) { return new String[] {\"a\", \"b\"}; } } $$");
        assertAnswer(
            "[\"a\",\"b\"]",
            "CALL java_arr()");
        assertAnswer(
            "JAVA_ARR|ARRAY|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE RESULT LAST_QUERY_ID()");
        assertAnswer(
            "JAVA_ARR / ARRAY / Y",
            "SELECT \"name\" || ' / ' || \"type\" || ' / ' || \"null?\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "[\"a\",\"b\"]",
            "CALL java_arr()");
        assertAnswer(
            "Table CT_JAVA_ARR successfully created.",
            "CREATE OR REPLACE TABLE ct_java_arr AS SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "JAVA_ARR|ARRAY|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE ct_java_arr");
        assertAnswer(
            "JAVA_ARR / ARRAY / Y",
            "SELECT \"name\" || ' / ' || \"type\" || ' / ' || \"null?\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function JAVA_TXT successfully created.",
            "CREATE OR REPLACE PROCEDURE java_txt() RETURNS VARCHAR(2) LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.*; class H { public static String run(Session s) { return \"ab\"; } } "
                + "$$");
        assertAnswer(
            "ab",
            "CALL java_txt()");
        assertAnswer(
            "JAVA_TXT|VARCHAR(2)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE RESULT LAST_QUERY_ID()");
        assertAnswer(
            "JAVA_TXT / VARCHAR(2) / Y",
            "SELECT \"name\" || ' / ' || \"type\" || ' / ' || \"null?\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "ab",
            "CALL java_txt()");
        assertAnswer(
            "Table CT_JAVA_TXT successfully created.",
            "CREATE OR REPLACE TABLE ct_java_txt AS SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "JAVA_TXT|VARCHAR(2)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE ct_java_txt");
        assertAnswer(
            "JAVA_TXT / VARCHAR(2) / Y",
            "SELECT \"name\" || ' / ' || \"type\" || ' / ' || \"null?\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function JAVA_NUL successfully created.",
            "CREATE OR REPLACE PROCEDURE java_nul() RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES "
                + "= ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static Long run(Session s) { return null; } } $$");
        assertAnswer(
            "null",
            "CALL java_nul()");
        assertAnswer(
            "JAVA_NUL|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE RESULT LAST_QUERY_ID()");
        assertAnswer(
            "JAVA_NUL / NUMBER(38,0) / Y",
            "SELECT \"name\" || ' / ' || \"type\" || ' / ' || \"null?\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "null",
            "CALL java_nul()");
        assertAnswer(
            "Table CT_JAVA_NUL successfully created.",
            "CREATE OR REPLACE TABLE ct_java_nul AS SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "JAVA_NUL|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE ct_java_nul");
        assertAnswer(
            "JAVA_NUL / NUMBER(38,0) / Y",
            "SELECT \"name\" || ' / ' || \"type\" || ' / ' || \"null?\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function JAVA_BIN successfully created.",
            "CREATE OR REPLACE PROCEDURE java_bin() RETURNS BINARY LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES "
                + "= ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static byte[] run(Session s) { return new byte[] {1, 2}; } } $$");
        assertAnswer(
            "0102",
            "CALL java_bin()");
        answer(
            "DESCRIBE RESULT LAST_QUERY_ID()");
        answer(
            "SELECT \"name\" || ' / ' || \"type\" || ' / ' || \"null?\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "0102",
            "CALL java_bin()");
        assertAnswer(
            "Table CT_JAVA_BIN successfully created.",
            "CREATE OR REPLACE TABLE ct_java_bin AS SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "JAVA_BIN|BINARY(8388608)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE ct_java_bin");
        assertAnswer(
            "JAVA_BIN / BINARY(8388608) / Y",
            "SELECT \"name\" || ' / ' || \"type\" || ' / ' || \"null?\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function SQL_FLOAT successfully created.",
            "CREATE OR REPLACE PROCEDURE sql_float() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN 1.5; END; $$");
        assertAnswer(
            "1.5",
            "CALL sql_float()");
        assertAnswer(
            "SQL_FLOAT|FLOAT|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE RESULT LAST_QUERY_ID()");
        assertAnswer(
            "SQL_FLOAT / FLOAT / Y",
            "SELECT \"name\" || ' / ' || \"type\" || ' / ' || \"null?\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
    }
}
