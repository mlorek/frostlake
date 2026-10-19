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
 * A fault a block's query recovers from by itself, the ones that were left: a closing parenthesis refused
 * where its own bracket wants an operand — OVER (ORDER BY), ABS(1,), IN () — and the statement's semicolon
 * refused where a clause wants one. The statement ends there all the same, so the statements after it are read
 * and a fault of theirs is reported, a handler's too (live-verified).
 */
public class BlockQueryRecoveredFaultLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void aClosingParenthesisOrTheSemicolon() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ')'.",
            "SELECT SUM(a) OVER (ORDER BY) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ')'. | syntax error "
                + "line 1 at position 53 unexpected '1'.",
            "BEGIN SELECT SUM(a) OVER (ORDER BY) FROM t; RETURN 1 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ')'.",
            "BEGIN SELECT SUM(a) OVER (ORDER BY) FROM t; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ')'. | syntax error "
                + "line 1 at position 57 unexpected '1'.",
            "BEGIN SELECT SUM(a) OVER (PARTITION BY) FROM t; RETURN 1 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected ')'. | syntax error "
                + "line 1 at position 68 unexpected '1'.",
            "BEGIN SELECT SUM(a) OVER (PARTITION BY a ORDER BY) FROM t; RETURN 1 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ')'. | syntax error "
                + "line 1 at position 60 unexpected '1'.",
            "BEGIN SELECT SUM(a) OVER (ORDER BY a ROWS) FROM t; RETURN 1 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ';'. | syntax error "
                + "line 1 at position 41 unexpected '1'.",
            "BEGIN SELECT a FROM t ORDER BY; RETURN 1 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ';'. | syntax error "
                + "line 1 at position 41 unexpected '1'.",
            "BEGIN SELECT a FROM t GROUP BY; RETURN 1 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected '1'.",
            "BEGIN SELECT COUNT(DISTINCT) FROM t; RETURN 1 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected ')'. | syntax error "
                + "line 1 at position 65 unexpected '1'.",
            "BEGIN SELECT LISTAGG(a) WITHIN GROUP (ORDER BY) FROM t; RETURN 1 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected ')'. | syntax error "
                + "line 1 at position 59 unexpected '1'.",
            "BEGIN SELECT a FROM (SELECT a FROM t ORDER BY) x; RETURN 1 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '1'.",
            "BEGIN SELECT ABS() FROM t; RETURN 1 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ')'. | syntax error "
                + "line 1 at position 38 unexpected '1'.",
            "BEGIN SELECT ABS(1,) FROM t; RETURN 1 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ')'. | syntax error "
                + "line 1 at position 36 unexpected '1'.",
            "BEGIN SELECT (1 +) FROM t; RETURN 1 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ')'. | syntax error "
                + "line 1 at position 47 unexpected '1'.",
            "BEGIN SELECT a FROM t WHERE a IN () ; RETURN 1 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'FROM'. | syntax "
                + "error line 1 at position 51 unexpected '1'.",
            "BEGIN SELECT CASE WHEN a THEN END FROM t; RETURN 1 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ')'. | syntax error "
                + "line 1 at position 45 unexpected '1'.",
            "BEGIN SELECT a FROM t WHERE (a = ); RETURN 1 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ')'. | syntax error "
                + "line 1 at position 55 unexpected '1'.",
            "BEGIN SELECT SUM(a) OVER (ORDER BY) x FROM t; RETURN 1 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ')'. | syntax error "
                + "line 1 at position 56 unexpected '1'.",
            "BEGIN SELECT SUM(a) OVER (ORDER BY), b FROM t; RETURN 1 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ')'. | syntax error "
                + "line 1 at position 46 unexpected '1'.",
            "BEGIN SELECT SUM(a) OVER (ORDER BY); RETURN 1 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected ')'. | syntax error "
                + "line 1 at position 101 unexpected '2'.",
            "BEGIN LET q := 1; SELECT SUM(a) OVER (ORDER BY) FROM t; RETURN 1; EXCEPTION WHEN OTHER THEN RETURN 2 "
                + "2; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected ')'. | syntax error "
                + "line 1 at position 76 unexpected '1'.",
            "BEGIN IF (TRUE) THEN SELECT SUM(a) OVER (ORDER BY) FROM t; END IF; RETURN 1 1; END");
    }

    @Test
    public void beforeAHandler() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ')'.",
            "BEGIN SELECT SUM(a) OVER (ORDER BY) FROM t; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN "
                + "RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression "
                + "' || SQLCODE || ' ' || SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; "
                + "END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'y'. | syntax error "
                + "line 1 at position 20 unexpected 'RETURN'.",
            "BEGIN SELECT 1 x y; RETURN 'none'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement ' || "
                + "SQLCODE || ' ' || SQLSTATE; WHEN EXPRESSION_ERROR THEN RETURN 'expression ' || SQLCODE || ' ' || "
                + "SQLSTATE; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ')'.",
            "BEGIN SELECT SUM(a) OVER (ORDER BY) FROM t; RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE "
                + "|| ' ' || SQLSTATE || ' ' || SQLERRM; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ')'.",
            "BEGIN SELECT SUM(a) OVER (ORDER BY) FROM t; RETURN 'none'; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ')'.",
            "BEGIN SELECT SUM(a) OVER (ORDER BY) FROM t; EXCEPTION WHEN OTHER THEN RETURN 1; WHEN STATEMENT_ERROR "
                + "THEN RETURN 2; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'y'. | syntax error "
                + "line 1 at position 56 unexpected 'END'.",
            "BEGIN RETURN 1; EXCEPTION WHEN OTHER THEN SELECT 1 x y; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'y'. | syntax error "
                + "line 1 at position 56 unexpected 'WHEN'.",
            "BEGIN RETURN 1; EXCEPTION WHEN OTHER THEN SELECT 1 x y; WHEN STATEMENT_ERROR THEN RETURN 2; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'THEN'.",
            "BEGIN RETURN 1; EXCEPTION WHEN THEN RETURN 2; END");
    }

    @Test
    public void otherClausesAndStatements() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'FROM'. | syntax "
                + "error line 1 at position 17 unexpected ';'. | syntax error line 1 at position 28 unexpected '1'.",
            "BEGIN SELECT FROM; RETURN 1 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ';'. | syntax error "
                + "line 1 at position 27 unexpected '1'.",
            "BEGIN SELECT 1 +; RETURN 1 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ';'. | syntax error "
                + "line 1 at position 38 unexpected '1'.",
            "BEGIN SELECT 1 FROM t WHERE; RETURN 1 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ';'.",
            "BEGIN SELECT 1 FROM t JOIN USING; RETURN 1 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected ')'. | syntax error "
                + "line 1 at position 48 unexpected '1'.",
            "BEGIN SELECT a FROM t WHERE a IN (1,); RETURN 1 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '1'.",
            "BEGIN SELECT a FROM t LIMIT; RETURN 1 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ';'. | syntax error "
                + "line 1 at position 56 unexpected ';'. | syntax error line 1 at position 67 unexpected '1'.",
            "BEGIN SELECT a FROM t ORDER BY; SELECT a FROM t GROUP BY; RETURN 1 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ';'. | syntax error "
                + "line 1 at position 64 unexpected '2'.",
            "BEGIN SELECT a FROM t WHERE; EXCEPTION WHEN OTHER THEN RETURN 2 2; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected ';'. | syntax error "
                + "line 1 at position 53 unexpected 'x'. | syntax error line 1 at position 56 unexpected 'END'.",
            "BEGIN LET x := 1; SELECT a FROM t ORDER BY; RETURN x x; END");
    }
}
