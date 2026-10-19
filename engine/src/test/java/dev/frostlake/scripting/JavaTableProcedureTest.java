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
package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A Java procedure declared RETURNS TABLE answers the rows of the DataFrame its handler returns, to a CALL
 * and in a FROM clause, under the declared columns' names; an empty DataFrame answers no rows. A DataFrame of
 * another shape than the declaration is refused - {@code data type of returned table does not match expected
 * returned table type} - a handler that returns {@code null} is refused, and a handler that returns anything
 * but a DataFrame is refused when the procedure is created. A procedure declared RETURNS TABLE () answers
 * the DataFrame's own columns to a CALL and cannot stand in a FROM clause. Every answer is live-verified.
 */
public class JavaTableProcedureTest extends MeasuredCellsTestSupport {

    @Test
    public void aTableProcedureAnswersItsDataFramesRows() {
        assertAnswer(
            "Function JP9 successfully created.",
            "CREATE OR REPLACE PROCEDURE jp9() RETURNS TABLE(a NUMBER) LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.Session; import com.snowflake.snowpark_java.DataFrame; class H { "
                + "public DataFrame run(Session s) { return s.sql(\"SELECT 1 AS a UNION ALL SELECT 2\"); } } $$");
        assertAnswer(
            "1 ;; 2",
            "CALL jp9()");
        assertAnswer(
            "1 ;; 2",
            "SELECT * FROM TABLE(jp9()) ORDER BY 1");
        assertAnswer(
            "2|3",
            "SELECT COUNT(*), SUM(a) FROM TABLE(jp9())");
        assertAnswer(
            "Function JP10 successfully created.",
            "CREATE OR REPLACE PROCEDURE jp10() RETURNS TABLE() LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.Session; import com.snowflake.snowpark_java.DataFrame; class H { "
                + "public DataFrame run(Session s) { return s.sql(\"SELECT 1 AS a, 'x' AS b UNION ALL SELECT 2, "
                + "'y'\"); } } $$");
        assertAnswer(
            "1|x ;; 2|y",
            "CALL jp10()");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid stored procedure 'JP10' in FROM clause: Return table must "
                + "declare a nonzero number of columns.",
            "SELECT * FROM TABLE(jp10()) ORDER BY 1");
        assertAnswer(
            "Function JP11 successfully created.",
            "CREATE OR REPLACE PROCEDURE jp11() RETURNS TABLE(n NUMBER, t VARCHAR) LANGUAGE JAVA RUNTIME_VERSION "
                + "= '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.Session; import com.snowflake.snowpark_java.DataFrame; class H { "
                + "public DataFrame run(Session s) { return s.sql(\"SELECT 1 AS a, 'x' AS b\"); } } $$");
        assertAnswer(
            "1|x",
            "CALL jp11()");
        assertAnswer(
            "1|x",
            "SELECT n, t FROM TABLE(jp11())");
        assertAnswer(
            "Function JP12 successfully created.",
            "CREATE OR REPLACE PROCEDURE jp12() RETURNS TABLE(a NUMBER) LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.Session; import com.snowflake.snowpark_java.DataFrame; class H { "
                + "public DataFrame run(Session s) { return s.sql(\"SELECT 1 AS a, 2 AS b\"); } } $$");
        assertAnswer(
            "REFUSED Stored procedure execution error: data type of returned table does not match expected "
                + "returned table type",
            "CALL jp12()");
        assertAnswer(
            "Function JP13 successfully created.",
            "CREATE OR REPLACE PROCEDURE jp13() RETURNS TABLE(a VARCHAR) LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.Session; import com.snowflake.snowpark_java.DataFrame; class H { "
                + "public DataFrame run(Session s) { return s.sql(\"SELECT 1.5 AS a\"); } } $$");
        assertAnswer(
            "REFUSED Stored procedure execution error: data type of returned table does not match expected "
                + "returned table type",
            "CALL jp13()");
        assertAnswer(
            "Function JP14 successfully created.",
            "CREATE OR REPLACE PROCEDURE jp14() RETURNS TABLE(a NUMBER) LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.Session; import com.snowflake.snowpark_java.DataFrame; class H { "
                + "public DataFrame run(Session s) { return null; } } $$");
        assertAnswer(
            "REFUSED NULL result in a non-nullable column of type TEXT in function JP14 with handler H.run",
            "CALL jp14()");
        assertAnswer(
            "REFUSED Expected stored procedure to return TABLE if and only if handler method returns DATAFRAME in "
                + "function JP15 with handler H.run",
            "CREATE OR REPLACE PROCEDURE jp15() RETURNS TABLE(a NUMBER) LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.Session; import com.snowflake.snowpark_java.DataFrame; class H { "
                + "public String run(Session s) { return \"x\"; } } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function JP15.",
            "CALL jp15()");
        assertAnswer(
            "Function JP16 successfully created.",
            "CREATE OR REPLACE PROCEDURE jp16(lim NUMBER) RETURNS TABLE(a NUMBER) LANGUAGE JAVA RUNTIME_VERSION = "
                + "'11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.Session; import com.snowflake.snowpark_java.DataFrame; class H { "
                + "public DataFrame run(Session s, long lim) { return s.sql(\"SELECT SEQ4() AS a FROM "
                + "TABLE(GENERATOR(ROWCOUNT => \" + lim + \"))\"); } } $$");
        assertAnswer(
            "0 ;; 1 ;; 2",
            "CALL jp16(3)");
        assertAnswer(
            "4",
            "SELECT COUNT(*) FROM TABLE(jp16(4))");
        assertAnswer(
            "Function JP17 successfully created.",
            "CREATE OR REPLACE PROCEDURE jp17() RETURNS TABLE(a NUMBER) LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.Session; import com.snowflake.snowpark_java.DataFrame; class H { "
                + "public DataFrame run(Session s) { return s.sql(\"SELECT 1 AS a WHERE 1 = 0\"); } } $$");
        assertAnswer(
            "",
            "CALL jp17()");
        assertAnswer(
            "Function JP18 successfully created.",
            "CREATE OR REPLACE PROCEDURE jp18() RETURNS TABLE(a NUMBER) LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.Session; import com.snowflake.snowpark_java.DataFrame; class H { "
                + "public DataFrame run(Session s) { return "
                + "s.table(\"jt\").filter(com.snowflake.snowpark_java.Functions.col(\"a\").gt(com.snowflake.snowpark_java.Functions.lit(1))); } } $$");
        assertAnswer(
            "Table JT successfully created.",
            "CREATE OR REPLACE TABLE jt (a NUMBER)");
        assertAnswer(
            "3",
            "INSERT INTO jt VALUES (1), (2), (3)");
        assertAnswer(
            "2 ;; 3",
            "CALL jp18()");
    }
}
