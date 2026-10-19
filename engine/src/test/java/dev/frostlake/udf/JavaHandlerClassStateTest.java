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
 * What an inline Java handler's class keeps from one use to the next. A procedure's class is the CALL's own:
 * a static field and an instance field both start as the source writes them on every CALL. A function's class
 * is the statement's own and, within a statement, the calling place's: a static counter runs over the rows one
 * place reads, starts again for another call of the function in the same statement, and again for the next
 * statement. Two sources that differ only in texts sharing a hash run their own code, as functions and as
 * procedures, and a replaced procedure answers its new body. Every answer is live-verified.
 */
public class JavaHandlerClassStateTest extends MeasuredCellsTestSupport {

    @Test
    public void aCallAndAStatementLoadTheirOwnClass() {
        assertAnswer(
            "Function JP2 successfully created.",
            "CREATE OR REPLACE PROCEDURE jp2(a NUMBER) RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.Session; class H { public long run(Session s, long a) { return a + 1; "
                + "} } $$");
        assertAnswer(
            "2",
            "CALL jp2(1)");
        assertAnswer(
            "3",
            "CALL jp2(2)");
        assertAnswer(
            "4",
            "CALL jp2(3)");
        assertAnswer(
            "Function JPC successfully created.",
            "CREATE OR REPLACE PROCEDURE jpc() RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.Session; class H { static int n = 0; int m = 0; public long "
                + "run(Session s) { n = n + 1; m = m + 1; return n * 10 + m; } } $$");
        assertAnswer(
            "11",
            "CALL jpc()");
        assertAnswer(
            "11",
            "CALL jpc()");
        assertAnswer(
            "11",
            "CALL jpc()");
        assertAnswer(
            "Function JFC successfully created.",
            "CREATE OR REPLACE FUNCTION jfc(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ class H "
                + "{ static int n = 0; public static int f(int x) { n = n + 1; return n; } } $$");
        assertAnswer(
            "1",
            "SELECT jfc(1)");
        assertAnswer(
            "1",
            "SELECT jfc(1)");
        assertAnswer(
            "1|1",
            "SELECT jfc(1), jfc(2)");
    }

    @Test
    public void twoSourcesThatShareAHashRunTheirOwnCode() {
        assertAnswer(
            "Function HC1 successfully created.",
            "CREATE OR REPLACE FUNCTION hc1(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$class H "
                + "{ public static String f(int x) { return \"Aa\"; } }$$");
        assertAnswer(
            "Function HC2 successfully created.",
            "CREATE OR REPLACE FUNCTION hc2(x NUMBER) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$class H "
                + "{ public static String f(int x) { return \"BB\"; } }$$");
        assertAnswer(
            "Aa|BB",
            "SELECT hc1(1), hc2(1)");
        assertAnswer(
            "BB|Aa",
            "SELECT hc2(1), hc1(1)");
        assertAnswer(
            "Function HP1 successfully created.",
            "CREATE OR REPLACE PROCEDURE hp1() RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.Session; class H { public String run(Session s) { return \"Aa\"; } } "
                + "$$");
        assertAnswer(
            "Function HP2 successfully created.",
            "CREATE OR REPLACE PROCEDURE hp2() RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.Session; class H { public String run(Session s) { return \"BB\"; } } "
                + "$$");
        assertAnswer(
            "Aa",
            "CALL hp1()");
        assertAnswer(
            "BB",
            "CALL hp2()");
        assertAnswer(
            "Function JP2 successfully created.",
            "CREATE OR REPLACE PROCEDURE jp2(a NUMBER) RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.Session; class H { public long run(Session s, long a) { return a + 2; "
                + "} } $$");
        assertAnswer(
            "3",
            "CALL jp2(1)");
    }

    @Test
    public void theRowsOfOneCallingPlaceShareTheClass() {
        assertAnswer(
            "Function JFC successfully created.",
            "CREATE OR REPLACE FUNCTION jfc(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ class H "
                + "{ static int n = 0; public static int f(int x) { n = n + 1; return n; } } $$");
        assertAnswer(
            "3|3",
            "SELECT MAX(jfc(v)), COUNT(*) FROM (SELECT 1 AS v UNION ALL SELECT 2 UNION ALL SELECT 3)");
        assertAnswer(
            "2",
            "SELECT jfc(1) + jfc(1)");
        assertAnswer(
            "3|3",
            "SELECT MAX(a), MAX(b) FROM (SELECT jfc(v) AS a, jfc(v + 10) AS b FROM (SELECT 1 AS v UNION ALL "
                + "SELECT 2 UNION ALL SELECT 3))");
    }
}
