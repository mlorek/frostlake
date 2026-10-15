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
 * A handler's answer and the scan of its CALL. A Java handler may answer a text longer than RETURNS VARCHAR(n)
 * allows: the CALL hands it out, and the scan refuses to read it - String 'abc' is too long and would be
 * truncated - wherever a value of it is read, while a count of its rows answers. A SQL body's over-wide answer is
 * refused at the CALL, and a handler's bytes past BINARY(n) too. A CALL of an unqualified name that is a
 * built-in function reaches the built-in, a qualified one the procedure (live-verified).
 */
public class HandlerAnswerScanTest extends MeasuredCellsTestSupport {

    @Test
    public void anAnswerWiderThanItsColumn() {
        try {
            assertAnswer(
                "Function R4 successfully created.",
                "CREATE OR REPLACE PROCEDURE r4() RETURNS VARCHAR(2) LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s) { return "
                    + "\"abc\"; } } $$");
            assertAnswer(
                "abc",
                "CALL r4()");
            assertAnswer(
                "REFUSED String 'abc' is too long and would be truncated",
                "SELECT $1 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "abc",
                "CALL r4()");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "abc",
                "CALL r4()");
            assertAnswer(
                "REFUSED String 'abc' is too long and would be truncated",
                "SELECT LENGTH($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "abc",
                "CALL r4()");
            assertAnswer(
                "VARCHAR(2)[LOB]",
                "SELECT SYSTEM$TYPEOF($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "abc",
                "CALL r4()");
            assertAnswer(
                "Function R5 successfully created.",
                "CREATE OR REPLACE PROCEDURE r5() RETURNS VARCHAR(2) LANGUAGE SQL AS $$ BEGIN RETURN 'abc'; END $$");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : String 'abc' is "
                    + "too long and would be truncated",
                "CALL r5()");
            assertShapedAnswer(
                "REFUSED Query <QID> has no result because it failed",
                "SELECT $1 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : String 'abc' is "
                    + "too long and would be truncated",
                "CALL r5()");
            assertShapedAnswer(
                "REFUSED Query <QID> has no result because it failed",
                "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : String 'abc' is "
                    + "too long and would be truncated",
                "CALL r5()");
            assertShapedAnswer(
                "REFUSED Query <QID> has no result because it failed",
                "SELECT LENGTH($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : String 'abc' is "
                    + "too long and would be truncated",
                "CALL r5()");
            assertShapedAnswer(
                "REFUSED Query <QID> has no result because it failed",
                "SELECT SYSTEM$TYPEOF($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function R6 successfully created.",
                "CREATE OR REPLACE PROCEDURE r6() RETURNS VARCHAR(3) LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s) { return "
                    + "\"abc\"; } } $$");
            assertAnswer(
                "abc",
                "CALL r6()");
            assertAnswer(
                "abc",
                "SELECT $1 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function R7 successfully created.",
                "CREATE OR REPLACE PROCEDURE r7() RETURNS NUMBER(2, 0) LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static int run(Session s) { return 123; } } $$");
            assertAnswer(
                "REFUSED Java value cannot be losslessly represented as a Snowflake NUMBER with a precision of 2, "
                    + "value: 123 in function R7 with handler H.run",
                "CALL r7()");
            assertShapedAnswer(
                "REFUSED Query <QID> has no result because it failed",
                "SELECT $1 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "REFUSED Java value cannot be losslessly represented as a Snowflake NUMBER with a precision of 2, "
                    + "value: 123 in function R7 with handler H.run",
                "CALL r7()");
            assertShapedAnswer(
                "REFUSED Query <QID> has no result because it failed",
                "SELECT SYSTEM$TYPEOF($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function R8 successfully created.",
                "CREATE OR REPLACE PROCEDURE r8() RETURNS VARCHAR(2) LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s) { return \"ab\"; "
                    + "} } $$");
            assertAnswer(
                "ab",
                "CALL r8()");
            assertAnswer(
                "ab",
                "SELECT $1 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS r4()");
            answer(
                "DROP PROCEDURE IF EXISTS r5()");
            answer(
                "DROP PROCEDURE IF EXISTS r6()");
            answer(
                "DROP PROCEDURE IF EXISTS r7()");
            answer(
                "DROP PROCEDURE IF EXISTS r8()");
            answer(
                "DROP TABLE IF EXISTS r4t");
            answer(
                "DROP PROCEDURE IF EXISTS pi()");
            answer(
                "DROP PROCEDURE IF EXISTS abs(INT)");
            answer(
                "DROP PROCEDURE IF EXISTS upper(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS pi(INT)");
            answer(
                "DROP PROCEDURE IF EXISTS coalesce(INT, INT)");
            answer(
                "DROP PROCEDURE IF EXISTS nvl2x()");
            answer(
                "DROP PROCEDURE IF EXISTS round(FLOAT)");
            answer(
                "DROP PROCEDURE IF EXISTS length(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS greatest(INT, INT)");
        }
    }

    @Test
    public void aProcedureNamedAsABuiltIn() {
        try {
            assertAnswer(
                "Function PI successfully created.",
                "CREATE OR REPLACE PROCEDURE pi() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'proc'; END $$");
            assertAnswer(
                "3.141592653589793",
                "CALL pi()");
            assertAnswer(
                "Function ABS successfully created.",
                "CREATE OR REPLACE PROCEDURE abs(a INT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'proc'; "
                    + "END $$");
            assertAnswer(
                "1",
                "CALL abs(-1)");
            assertAnswer(
                "Function UPPER successfully created.",
                "CREATE OR REPLACE PROCEDURE upper(a VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                    + "'proc'; END $$");
            assertAnswer(
                "A",
                "CALL upper('a')");
            assertAnswer(
                "Function PI successfully created.",
                "CREATE OR REPLACE PROCEDURE pi(a INT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'proc'; "
                    + "END $$");
            assertAnswer(
                "REFUSED SQL compilation error: error line 0 at position -1 | too many arguments for function "
                    + "[PI(1)] expected 0, got 1",
                "CALL pi(1)");
            assertAnswer(
                "Function COALESCE successfully created.",
                "CREATE OR REPLACE PROCEDURE coalesce(a INT, b INT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
                    + "RETURN 'proc'; END $$");
            assertAnswer(
                "1",
                "CALL coalesce(1, 2)");
            assertAnswer(
                "Function NVL2X successfully created.",
                "CREATE OR REPLACE PROCEDURE nvl2x() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'proc'; END "
                    + "$$");
            assertAnswer(
                "proc",
                "CALL nvl2x()");
            assertAnswer(
                "Function ROUND successfully created.",
                "CREATE OR REPLACE PROCEDURE round(a FLOAT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                    + "'proc'; END $$");
            assertAnswer(
                "2",
                "CALL round(1.5)");
            assertAnswer(
                "Function LENGTH successfully created.",
                "CREATE OR REPLACE PROCEDURE length(a VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                    + "'proc'; END $$");
            assertAnswer(
                "3",
                "CALL length('abc')");
            assertAnswer(
                "Function GREATEST successfully created.",
                "CREATE OR REPLACE PROCEDURE greatest(a INT, b INT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
                    + "RETURN 'proc'; END $$");
            assertAnswer(
                "2",
                "CALL greatest(1, 2)");
            assertAnswer(
                "proc",
                "CALL test_schema.pi()");
            assertAnswer(
                "REFUSED SQL compilation error: error line 0 at position -1 | too many arguments for function "
                    + "[PI(1, 2)] expected 0, got 2",
                "CALL pi(1, 2)");
            assertAnswer(
                "1.5",
                "CALL abs(-1.5)");
            assertAnswer(
                "REFUSED Numeric value 'x' is not recognized",
                "CALL abs('x')");
            assertShape(
                "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), schema_name:VARCHAR(16777216), "
                    + "is_builtin:VARCHAR(16777216), is_aggregate:VARCHAR(16777216), is_ansi:VARCHAR(16777216), "
                    + "min_num_arguments:NUMBER(9,0), max_num_arguments:NUMBER(9,0), arguments:VARCHAR(16777216), "
                    + "description:VARCHAR(16777216), catalog_name:VARCHAR(16777216), "
                    + "is_table_function:VARCHAR(16777216), valid_for_clustering:VARCHAR(16777216), "
                    + "is_secure:VARCHAR(16777216), secrets:VARCHAR(16777216), "
                    + "external_access_integrations:VARCHAR(16777216)",
                "SHOW PROCEDURES LIKE 'PI'");
            assertAnswer(
                "signature|() ;; returns|VARCHAR ;; language|SQL ;; execute as|OWNER ;; body| BEGIN RETURN "
                    + "'proc'; END ",
                "DESCRIBE PROCEDURE pi()");
            assertAnswer(
                "1",
                "BEGIN CALL pi(); RETURN 1; END;");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS r4()");
            answer(
                "DROP PROCEDURE IF EXISTS r5()");
            answer(
                "DROP PROCEDURE IF EXISTS r6()");
            answer(
                "DROP PROCEDURE IF EXISTS r7()");
            answer(
                "DROP PROCEDURE IF EXISTS r8()");
            answer(
                "DROP TABLE IF EXISTS r4t");
            answer(
                "DROP PROCEDURE IF EXISTS pi()");
            answer(
                "DROP PROCEDURE IF EXISTS abs(INT)");
            answer(
                "DROP PROCEDURE IF EXISTS upper(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS pi(INT)");
            answer(
                "DROP PROCEDURE IF EXISTS coalesce(INT, INT)");
            answer(
                "DROP PROCEDURE IF EXISTS nvl2x()");
            answer(
                "DROP PROCEDURE IF EXISTS round(FLOAT)");
            answer(
                "DROP PROCEDURE IF EXISTS length(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS greatest(INT, INT)");
        }
    }

    @Test
    public void readingTheScan() {
        try {
            answer(
                "CREATE OR REPLACE TABLE rt (v VARCHAR)");
            assertAnswer(
                "Function R4 successfully created.",
                "CREATE OR REPLACE PROCEDURE r4() RETURNS VARCHAR(2) LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s) { return "
                    + "\"abc\"; } } $$");
            assertAnswer(
                "abc",
                "CALL r4()");
            assertAnswer(
                "REFUSED String 'abc' is too long and would be truncated",
                "SELECT $1 IS NULL FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "abc",
                "CALL r4()");
            assertAnswer(
                "REFUSED String 'abc' is too long and would be truncated",
                "SELECT 1 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE $1 = 'x'");
            assertAnswer(
                "abc",
                "CALL r4()");
            assertAnswer(
                "REFUSED String 'abc' is too long and would be truncated",
                "SELECT COUNT($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "abc",
                "CALL r4()");
            assertAnswer(
                "abc",
                "CALL r4()");
            assertAnswer(
                "abc",
                "CALL r4()");
            assertAnswer(
                "1",
                "SELECT 1 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "abc",
                "CALL r4()");
            assertAnswer(
                "REFUSED String 'abc' is too long and would be truncated",
                "SELECT $1::VARCHAR(10) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "abc",
                "CALL r4()");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE FALSE");
            assertAnswer(
                "abc",
                "CALL r4()");
            assertAnswer(
                "R4|VARCHAR(2)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
                "DESCRIBE RESULT LAST_QUERY_ID()");
            assertAnswer(
                "Function R9 successfully created.",
                "CREATE OR REPLACE PROCEDURE r9() RETURNS VARCHAR(2) LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s) { return "
                    + "\"\\u00e9\\u00e9\\u00e9\"; } } $$");
            assertAnswer(
                "\u00e9\u00e9\u00e9",
                "CALL r9()");
            assertAnswer(
                "REFUSED String '\u00e9\u00e9\u00e9' is too long and would be truncated",
                "SELECT $1 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function RB successfully created.",
                "CREATE OR REPLACE PROCEDURE rb() RETURNS BINARY(1) LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES "
                    + "= ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static byte[] run(Session s) { return new "
                    + "byte[] {65, 66}; } } $$");
            assertAnswer(
                "REFUSED System byte length limit (1) exceeded by string 'AB...' in function RB with handler H.run",
                "CALL rb()");
            assertShapedAnswer(
                "REFUSED Query <QID> has no result because it failed",
                "SELECT $1 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function RV successfully created.",
                "CREATE OR REPLACE PROCEDURE rv() RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                    + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s) { return "
                    + "\"abc\"; } } $$");
            assertAnswer(
                "abc",
                "CALL rv()");
            assertAnswer(
                "abc",
                "SELECT $1 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "abc",
                "CALL r4()");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM rt");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS r4()");
            answer(
                "DROP PROCEDURE IF EXISTS r9()");
            answer(
                "DROP PROCEDURE IF EXISTS rb()");
            answer(
                "DROP PROCEDURE IF EXISTS rv()");
        }
    }
}
