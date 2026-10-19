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
 * A user's table function and a table procedure called in a FROM clause take their arguments one way or the
 * other: a call that writes some by name and some by position, in either order, is refused where it names the
 * function, {@code illegal mixing of named and positional arguments for function TEST_SCHEMA.TOV}, the name as
 * the call writes it - before its names and its types are judged, and after a table of the FROM clause that is
 * not there. A CALL is held to the same rule. Every answer is live-verified.
 */
public class TableCallArgumentMixTest extends MeasuredCellsTestSupport {

    @Test
    public void aTableFunctionsArgumentsAreNamedOrPositional() {
        answer(
            "CREATE OR REPLACE FUNCTION n1(a INT, b INT) RETURNS INT AS 'a + b'");
        answer(
            "CREATE OR REPLACE FUNCTION f2() RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION pick(x INT) RETURNS INT AS 'x'");
        answer(
            "CREATE OR REPLACE FUNCTION pick(x VARCHAR) RETURNS VARCHAR AS 'x'");
        answer(
            "CREATE OR REPLACE FUNCTION tov(a INT, b VARCHAR) RETURNS TABLE (v INT) AS 'SELECT a'");
        answer(
            "CREATE OR REPLACE TABLE t1 (c INT, s VARCHAR, v VARIANT)");
        answer(
            "INSERT INTO t1 SELECT 1, 'x', PARSE_JSON('[1,2]')");
        answer(
            "CREATE OR REPLACE PROCEDURE pa(a INT, b VARCHAR) RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END;'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | illegal mixing of named and positional "
                + "arguments for function TOV",
            "SELECT v FROM TABLE(tov(a => 1, 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | illegal mixing of named and positional "
                + "arguments for function TOV",
            "SELECT v FROM TABLE(tov(1, b => 'x'))");
        assertAnswer(
            "1",
            "SELECT v FROM TABLE(tov(a => 1, b => 'x'))");
        assertAnswer(
            "1",
            "SELECT v FROM TABLE(tov(1, 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | illegal mixing of named and positional "
                + "arguments for function TEST_SCHEMA.TOV",
            "SELECT v FROM TABLE(test_schema.tov(a => 1, 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | illegal mixing of named and positional "
                + "arguments for function TOV",
            "SELECT v FROM TABLE(\"TOV\"(a => 1, 'x'))");
        answer(
            "SELECT * FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[1]'), 'x'))");
        assertAnswer(
            "1|null|[0]|0|1|[1]",
            "SELECT * FROM TABLE(FLATTEN(PARSE_JSON('[1]'), PATH => ''))");
        answer(
            "SELECT * FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[1]'), 'x', OUTER => TRUE))");
        answer(
            "SELECT * FROM TABLE(SPLIT_TO_TABLE(x => 'a', ','))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | invalid argument for function "
                + "[SPLIT_TO_TABLE] unexpected argument [Y] at position 2,",
            "SELECT * FROM TABLE(SPLIT_TO_TABLE('a', y => ','))");
        answer(
            "SELECT * FROM t1, LATERAL FLATTEN(INPUT => t1.v, 'x')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 24 | illegal mixing of named and positional "
                + "arguments for function TOV",
            "SELECT * FROM t1, TABLE(tov(a => t1.c, 'x'))");
        answer(
            "SELECT * FROM TABLE(nosuchtf(a => 1, 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 5 | illegal mixing of named and positional "
                + "arguments for function PA",
            "CALL pa(a => 1, 'y')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 5 | illegal mixing of named and positional "
                + "arguments for function PA",
            "CALL pa(1, b => 'y')");
        assertAnswer(
            "1",
            "CALL pa(a => 1, b => 'y')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | illegal mixing of named and positional "
                + "arguments for function TOV",
            "SELECT * FROM TABLE(tov(a => 1, 'x')) WHERE nosuchcol = 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | illegal mixing of named and positional "
                + "arguments for function TOV",
            "SELECT * FROM TABLE(tov(a => 1, 'x', 3))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 28 | illegal mixing of named and positional "
                + "arguments for function TOV",
            "SELECT nosuchcol FROM TABLE(tov(a => 1, 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'NOSUCH_TABLE' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_TABLE.",
            "SELECT * FROM TABLE(tov(a => 1, 'x')), nosuch_table");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'NOSUCH_TABLE' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_TABLE.",
            "SELECT * FROM nosuch_table, TABLE(tov(a => 1, 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'FROM'.",
            "SELECT * FROM TABLE(tov(a => 1, 'x')) FROM");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | illegal mixing of named and positional "
                + "arguments for function TOV",
            "SELECT * FROM TABLE(tov(a => 1,\n 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, 2) FROM TABLE(tov(a => 1, 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 40 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT * FROM TABLE(tov(a => n1(a => 1, 2), b => 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | illegal mixing of named and positional "
                + "arguments for function TOV",
            "SELECT * FROM TABLE(tov(a => 1, 'x', b => 'y'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | illegal mixing of named and positional "
                + "arguments for function TOV",
            "SELECT * FROM TABLE(tov(1, b => 'x', 'y'))");
    }

    @Test
    public void theMixIsRefusedInEitherOrderAndAfterLateral() {
        answer(
            "CREATE OR REPLACE FUNCTION n1(a INT, b INT) RETURNS INT AS 'a + b'");
        answer(
            "CREATE OR REPLACE FUNCTION f2() RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION jsf() RETURNS FLOAT LANGUAGE JAVASCRIPT AS 'return 1;'");
        answer(
            "CREATE OR REPLACE FUNCTION tov(a INT, b VARCHAR) RETURNS TABLE (v INT) AS 'SELECT a'");
        answer(
            "CREATE OR REPLACE TABLE t1 (c INT, s VARCHAR, v VARIANT, b BOOLEAN)");
        answer(
            "INSERT INTO t1 SELECT 1, 'x', PARSE_JSON('[1,2]'), TRUE");
        answer(
            "SELECT COUNT(*) FROM TABLE(FLATTEN(PATH => '', PARSE_JSON('[1]')))");
        answer(
            "SELECT COUNT(*) FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[1]'), ''))");
        answer(
            "SELECT COUNT(*) FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[1,2]}'), 'a'))");
        answer(
            "SELECT COUNT(*) FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[1,2]}'), 'a', TRUE))");
        answer(
            "SELECT COUNT(*) FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[]}'), 'a', TRUE))");
        answer(
            "SELECT COUNT(*) FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[]}'), 'a', FALSE))");
        answer(
            "SELECT COUNT(*) FROM TABLE(FLATTEN(PARSE_JSON('{\"a\":[1,2]}'), PATH => 'a', TRUE))");
        answer(
            "SELECT COUNT(*) FROM TABLE(FLATTEN(PARSE_JSON('{\"a\":[1,2]}'), OUTER => TRUE, 'a'))");
        answer(
            "SELECT COUNT(*) FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[1,2]}'), PATH => 'a', 'b'))");
        answer(
            "SELECT COUNT(*) FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":[1,2]}'), 'a', PATH => 'a'))");
        answer(
            "SELECT COUNT(*) FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[1]'), 'x', TRUE, TRUE, 'ARRAY', 6))");
        answer(
            "SELECT COUNT(*) FROM t1, LATERAL FLATTEN(INPUT => t1.v, '')");
        answer(
            "SELECT COUNT(*) FROM t1, LATERAL FLATTEN(INPUT => t1.v, '', TRUE)");
        assertAnswer(
            "2",
            "SELECT COUNT(*) FROM TABLE(SPLIT_TO_TABLE('a,b', ','))");
        answer(
            "SELECT COUNT(*) FROM TABLE(STRTOK_SPLIT_TO_TABLE(x => 'a b', ' '))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 27 | illegal mixing of named and positional "
                + "arguments for function TOV",
            "SELECT COUNT(*) FROM TABLE(tov(a => 1, 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 27 | illegal mixing of named and positional "
                + "arguments for function TOV",
            "SELECT COUNT(*) FROM TABLE(tov(1, b => 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 33 | illegal mixing of named and positional "
                + "arguments for function TOV",
            "SELECT COUNT(*) FROM t1, LATERAL tov(a => t1.c, 'x')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 31 | illegal mixing of named and positional "
                + "arguments for function TOV",
            "SELECT COUNT(*) FROM t1, TABLE(tov(t1.c, b => 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 27 | illegal mixing of named and positional "
                + "arguments for function TOV",
            "SELECT COUNT(*) FROM TABLE(tov(a => 1, 'x') OVER (PARTITION BY 1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 27 | illegal mixing of named and positional "
                + "arguments for function TOV",
            "SELECT COUNT(*) FROM TABLE(tov(nosuch => 1, 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 27 | illegal mixing of named and positional "
                + "arguments for function TOV",
            "SELECT COUNT(*) FROM TABLE(tov(a => 1, 'x', 'y'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 27 | Invalid argument types for function "
                + "'TOV': (NUMBER(1,0))",
            "SELECT COUNT(*) FROM TABLE(tov(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 27 | Invalid argument types for function "
                + "'TOV': (VARCHAR(1), VARCHAR(1))",
            "SELECT COUNT(*) FROM TABLE(tov('1', 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 27 | Invalid argument types for function "
                + "'TOV': (NUMBER(2,1), VARCHAR(1))",
            "SELECT COUNT(*) FROM TABLE(tov(1.5, 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 27 | Invalid argument types for function "
                + "'TOV': (NULL, VARCHAR(1))",
            "SELECT COUNT(*) FROM TABLE(tov(NULL, 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 27 | Invalid argument types for function "
                + "'TOV': (NUMBER(1,0), NUMBER(1,0))",
            "SELECT COUNT(*) FROM TABLE(tov(1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 27 | Invalid argument types for function "
                + "'TOV': (BOOLEAN, VARCHAR(1))",
            "SELECT COUNT(*) FROM TABLE(tov(TRUE, 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 27 | Invalid argument types for function "
                + "'TOV': (NUMBER(1,0), NULL)",
            "SELECT COUNT(*) FROM TABLE(tov(1, NULL))");
    }
}
