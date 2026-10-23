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

package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * Second lines after an operand: an AS in a select item's quantified or IN list names the list's ')', and stands
 * alone in any other clause; an EXISTS after a quantifier names what its parenthesis opens with, and its ')' after a
 * query that reads a FROM; a method's name with no parenthesis after it is read as if one opened before the next
 * token, in a select list, a clause and a SQL function's body (all live-verified).
 */
public class OperandSecondLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void operandSecondLines() {
        engine.execute("CREATE OR REPLACE TABLE rt (n INT)");
        engine.execute("INSERT INTO rt VALUES (1), (2)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'AS'. | syntax "
                + "error line 1 at position 22 unexpected ')'.",
            "SELECT 1 = ANY (1 AS x)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'EXISTS'. | "
                + "syntax error line 1 at position 23 unexpected 'SELECT'.",
            "SELECT 1 = ANY EXISTS (SELECT 1)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'AS'. | syntax "
                + "error line 1 at position 22 unexpected ')'.",
            "SELECT 1 = ALL (1 AS x)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '2'.",
            "SELECT 1 = ANY (1 2)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'AS'. | syntax "
                + "error line 1 at position 25 unexpected ')'.",
            "SELECT 1 = ANY (1, 2 AS x)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'NOT'.",
            "SELECT 1 = ANY NOT (SELECT 1)");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "7 unexpected ')'. | syntax error line 1 at position 8 unexpected '<EOF>'.",
            "CREATE OR REPLACE FUNCTION nf_b(x INT) RETURNS INT AS 'x OVER'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "6 unexpected ')'. | syntax error line 1 at position 7 unexpected '<EOF>'.",
            "CREATE OR REPLACE FUNCTION nf_b(x INT) RETURNS INT AS 'x ! y'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "4 unexpected ')'. | syntax error line 1 at position 5 unexpected '<EOF>'.",
            "CREATE OR REPLACE FUNCTION nf_b(x INT) RETURNS INT AS 'x!y'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "5 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION nf_b(x INT) RETURNS INT AS '(x !'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "4 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION nf_b(x INT) RETURNS INT AS '1 !'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "3 unexpected 'y'.",
            "CREATE OR REPLACE FUNCTION nf_b(x INT) RETURNS INT AS 'x y'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "4 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION nf_b(x INT) RETURNS INT AS 'x +'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "4 unexpected '<EOF>'.",
            "CREATE OR REPLACE FUNCTION nf_b(x INT) RETURNS INT AS '(x'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "4 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION nf_b(x INT) RETURNS INT AS 'x )'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "4 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION nf_b(x INT) RETURNS INT AS 'x !'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "7 unexpected 'z'. | syntax error line 1 at position 9 unexpected '<EOF>'.",
            "CREATE OR REPLACE FUNCTION nf_b(x INT) RETURNS INT AS 'x ! y z'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'z'. | syntax "
                + "error line 1 at position 13 unexpected 'FROM'.",
            "SELECT x!y z FROM pg");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'z'. | syntax "
                + "error line 1 at position 12 unexpected '<EOF>'.",
            "SELECT x!y z");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'z'. | syntax "
                + "error line 1 at position 15 unexpected 'FROM'.",
            "SELECT x ! y z FROM pg");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'z'. | syntax "
                + "error line 1 at position 13 unexpected 'FROM'.",
            "SELECT n!y z FROM rt");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }

    @Test
    public void operandSecondLineShapes() {
        engine.execute("CREATE OR REPLACE TABLE rt (n INT)");
        engine.execute("INSERT INTO rt VALUES (1), (2)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'AS'. | syntax "
                + "error line 1 at position 23 unexpected ')'.",
            "SELECT 1 = SOME (1 AS x)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'AS'. | syntax "
                + "error line 1 at position 25 unexpected ')'.",
            "SELECT 1 = ANY (1 AS x, 2)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'AS'. | syntax "
                + "error line 1 at position 28 unexpected ')'.",
            "SELECT 1 = ANY (1, 2 AS x, 3)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'AS'. | syntax "
                + "error line 1 at position 24 unexpected ')'.",
            "SELECT 1 = ANY ((1) AS x)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'AS'. | syntax "
                + "error line 1 at position 19 unexpected ')'.",
            "SELECT 1 IN (1 AS x)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'EXISTS'. | "
                + "syntax error line 1 at position 23 unexpected 'SELECT'. | syntax error line 1 at position 39 "
                + "unexpected ')'.",
            "SELECT 1 = ANY EXISTS (SELECT 1 FROM rt)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'EXISTS'. | "
                + "syntax error line 1 at position 23 unexpected 'SELECT'.",
            "SELECT 1 = ALL EXISTS (SELECT 1)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'EXISTS'. | "
                + "syntax error line 1 at position 23 unexpected '('.",
            "SELECT 1 = ANY EXISTS ((SELECT 1))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'AS'.",
            "SELECT * FROM rt WHERE n = ANY (1 AS x)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'z'. | syntax "
                + "error line 1 at position 16 unexpected 'FROM'.",
            "SELECT x!y z, 1 FROM pg");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'z'. | syntax "
                + "error line 1 at position 17 unexpected 'FROM'.",
            "SELECT x!y z + 1 FROM pg");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '1'. | syntax "
                + "error line 1 at position 13 unexpected 'FROM'.",
            "SELECT x!y 1 FROM pg");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'z'. | syntax "
                + "error line 1 at position 15 unexpected 'FROM'.",
            "SELECT (x!y z) FROM pg");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'z'. | syntax "
                + "error line 1 at position 28 unexpected '<EOF>'.",
            "SELECT x FROM pg WHERE x!y z");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "7 unexpected 'z'. | syntax error line 1 at position 9 unexpected 'w'. | syntax error line 1 at "
                + "position 11 unexpected '<EOF>'.",
            "CREATE OR REPLACE FUNCTION nf_c(x INT) RETURNS INT AS 'x ! y z w'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "7 unexpected '1'. | syntax error line 1 at position 9 unexpected '<EOF>'.",
            "CREATE OR REPLACE FUNCTION nf_c(x INT) RETURNS INT AS 'x ! y 1'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "5 unexpected 'z'. | syntax error line 1 at position 11 unexpected '<EOF>'.",
            "CREATE OR REPLACE FUNCTION nf_c(x INT) RETURNS INT AS 'x!y z + 1'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "7 unexpected ')'. | syntax error line 1 at position 8 unexpected '<EOF>'.",
            "CREATE OR REPLACE FUNCTION nf_c(x INT) RETURNS INT AS '(x ! y'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "6 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION nf_c(x INT) RETURNS INT AS '((x !'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "5 unexpected 'y'.",
            "CREATE OR REPLACE FUNCTION nf_c(x INT) RETURNS INT AS '1 ! y'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "5 unexpected '1'.",
            "CREATE OR REPLACE FUNCTION nf_c(x INT) RETURNS INT AS 'x ! 1'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "5 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION nf_c(x INT) RETURNS INT AS '(1 !'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "8 unexpected 'y'.",
            "CREATE OR REPLACE FUNCTION nf_c(x INT) RETURNS INT AS 'x OVER y'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "8 unexpected ')'. | syntax error line 1 at position 9 unexpected '<EOF>'.",
            "CREATE OR REPLACE FUNCTION nf_c(x INT) RETURNS INT AS '(x OVER'");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
