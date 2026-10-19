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
package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * A routine written without a body. A procedure asks for its AS property in every language that takes its code
 * inline, and so does a JavaScript function; a Python, Java or Scala routine asks for a body or an imported
 * file, a SQL table function for its query, and a SQL scalar function alone is created — it answers NULL and
 * describes a NULL body. The refusal follows the routine's other properties and an existing overload, and
 * precedes the signature's names and IF NOT EXISTS; a SQL procedure's empty body is refused as an empty
 * statement. Every answer is live-verified.
 */
public class RoutineWithoutBodyTest extends MeasuredCellsTestSupport {

    @Test
    public void aProcedureAsksForItsBody() {
        assertAnswer(
            "REFUSED Property 'as' must be specified",
            "CREATE OR REPLACE PROCEDURE rp_q1() RETURNS VARCHAR");
        assertAnswer(
            "REFUSED Property 'as' must be specified",
            "CREATE OR REPLACE PROCEDURE rp_q2() RETURNS VARCHAR LANGUAGE SQL");
        assertAnswer(
            "REFUSED Property 'as' must be specified",
            "CREATE OR REPLACE PROCEDURE rp_q3() RETURNS VARCHAR LANGUAGE JAVASCRIPT");
        assertAnswer(
            "REFUSED Property 'as' must be specified",
            "CREATE OR REPLACE PROCEDURE rp_q4() RETURNS TABLE (a INT) LANGUAGE SQL");
        assertAnswer(
            "REFUSED Property 'as' must be specified",
            "CREATE OR REPLACE PROCEDURE rp_q5() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER");
        assertAnswer(
            "REFUSED Property 'as' must be specified",
            "CREATE OR REPLACE PROCEDURE rp_q6() RETURNS VARCHAR COMMENT = 'x'");
        assertAnswer(
            "Function RP_F1 successfully created.",
            "CREATE OR REPLACE FUNCTION rp_f1() RETURNS VARCHAR");
        assertAnswer(
            "Function RP_F2 successfully created.",
            "CREATE OR REPLACE FUNCTION rp_f2() RETURNS VARCHAR LANGUAGE SQL");
        assertAnswer(
            "REFUSED Property 'as' must be specified",
            "CREATE OR REPLACE FUNCTION rp_f3() RETURNS VARCHAR LANGUAGE JAVASCRIPT");
        assertAnswer(
            "null",
            "SELECT rp_f1()");
        assertAnswer(
            "Function RP_F4 successfully created.",
            "CREATE OR REPLACE FUNCTION rp_f4(a INT) RETURNS INT");
        assertAnswer(
            "",
            "SHOW PROCEDURES LIKE 'RP_Q%'");
        engine.execute("SHOW USER FUNCTIONS LIKE 'RP_F%'");
        assertAnswer(
            "RP_F1|RP_F1() RETURN VARCHAR ;; RP_F2|RP_F2() RETURN VARCHAR ;; RP_F4|RP_F4(NUMBER) RETURN NUMBER",
            "SELECT \"name\", \"arguments\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
        assertAnswer(
            "REFUSED Property 'as' must be specified",
            "CREATE PROCEDURE IF NOT EXISTS rp_q7() RETURNS VARCHAR");
        assertAnswer(
            "REFUSED Property 'as' must be specified",
            "CREATE OR REPLACE SECURE PROCEDURE rp_q8() RETURNS VARCHAR");
        assertAnswer(
            "REFUSED SQL compilation error: | Empty SQL statement.",
            "CREATE OR REPLACE PROCEDURE rp_q9() RETURNS VARCHAR LANGUAGE SQL AS ''");
    }

    @Test
    public void eachLanguageAsksInItsOwnWordsAndInItsOwnTurn() {
        engine.execute("CREATE OR REPLACE PROCEDURE rp_e() RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        assertAnswer(
            "REFUSED Property 'runtime_version' must be specified",
            "CREATE OR REPLACE PROCEDURE rp_p1() RETURNS VARCHAR LANGUAGE PYTHON");
        assertAnswer(
            "REFUSED SQL compilation error: Either a function BODY or a ZIP or PY file in the IMPORTS clause "
                + "is required.",
            "CREATE OR REPLACE PROCEDURE rp_p2() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run'");
        assertAnswer(
            "REFUSED SQL compilation error: Either a function BODY or a JAR file in the IMPORTS clause is "
                + "required.",
            "CREATE OR REPLACE PROCEDURE rp_p3() RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'A.run'");
        assertAnswer(
            "REFUSED SQL compilation error: Either a function BODY or a function body or imports file in the "
                + "IMPORTS clause is required.",
            "CREATE OR REPLACE PROCEDURE rp_p4() RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'A.run'");
        assertAnswer(
            "REFUSED SQL compilation error: Either a function BODY or a ZIP or PY file in the IMPORTS clause "
                + "is required.",
            "CREATE OR REPLACE FUNCTION rp_g1() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "HANDLER = 'run'");
        assertAnswer(
            "REFUSED SQL compilation error: Either a function BODY or a JAR file in the IMPORTS clause is "
                + "required.",
            "CREATE OR REPLACE FUNCTION rp_g2() RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'A.run'");
        assertAnswer(
            "REFUSED Table function definition expected a subquery, found 'null'.",
            "CREATE OR REPLACE FUNCTION rp_g3() RETURNS TABLE (a INT)");
        assertAnswer(
            "REFUSED Table function definition expected a subquery, found 'null'.",
            "CREATE OR REPLACE FUNCTION rp_g5() RETURNS TABLE (a INT) LANGUAGE SQL");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'RP_E' already exists.",
            "CREATE PROCEDURE rp_e() RETURNS VARCHAR");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NO_SUCH_SCHEMA' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NO_SUCH_SCHEMA.",
            "CREATE PROCEDURE no_such_schema.rp_m() RETURNS VARCHAR");
        assertAnswer(
            "REFUSED SQL compilation error: | Empty SQL statement.",
            "CREATE OR REPLACE PROCEDURE rp_p7() RETURNS VARCHAR LANGUAGE SQL AS '   '");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION rp_g4() RETURNS VARCHAR AS ''");
        assertAnswer(
            "Function RP_G6 successfully created.",
            "CREATE OR REPLACE FUNCTION rp_g6() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS ''");
        assertAnswer(
            "Function RP_P5 successfully created.",
            "CREATE OR REPLACE PROCEDURE rp_p5() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS ''");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE OR REPLACE PROCEDURE rp_p6(a NOSUCHTYPE) RETURNS VARCHAR");
        assertAnswer(
            "REFUSED Property 'as' must be specified",
            "CREATE OR REPLACE PROCEDURE rp_p8(a INT, a INT) RETURNS VARCHAR");
        assertAnswer(
            "REFUSED Property 'as' must be specified",
            "CREATE PROCEDURE IF NOT EXISTS rp_e() RETURNS VARCHAR");
        engine.execute("CREATE OR REPLACE FUNCTION rp_f1() RETURNS VARCHAR");
        assertAnswer(
            "signature|() ;; returns|VARCHAR ;; language|SQL ;; body|null",
            "DESCRIBE FUNCTION rp_f1()");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"RP_F1\"() | RETURNS VARCHAR | LANGUAGE SQL | ;",
            "SELECT GET_DDL('function', 'rp_f1()')");
        assertAnswer(
            "true",
            "SELECT rp_f1() IS NULL");
        engine.execute("CREATE OR REPLACE FUNCTION rp_f9(a INT) RETURNS INT LANGUAGE SQL");
        assertAnswer(
            "null",
            "SELECT rp_f9(5)");
    }
}
