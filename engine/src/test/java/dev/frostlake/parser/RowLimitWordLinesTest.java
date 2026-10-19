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
 * The token after LIMIT, OFFSET or FETCH where the word may be a table's alias: the account reads the word
 * both ways and reports the token once for each — twice, or the first token inside a bracket and then the
 * bracket — while after a table that has its alias, an ORDER BY or any other clause the token is reported
 * once (live-verified).
 */
public class RowLimitWordLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void theTokenIsToldTwice() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'x'. | syntax error "
                + "line 1 at position 22 unexpected 'x'.",
            "SELECT a FROM t LIMIT x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '-'. | syntax error "
                + "line 1 at position 22 unexpected '-'.",
            "SELECT a FROM t LIMIT -1 x");
        assertAnswer(
            "1 ;; 3",
            "SELECT a FROM t LIMIT");
        assertAnswer(
            "1 ;; 3",
            "SELECT a FROM t LIMIT ,");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '*'. | syntax error "
                + "line 1 at position 22 unexpected '*'.",
            "SELECT a FROM t LIMIT *");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'a'. | syntax error "
                + "line 1 at position 22 unexpected 'a'.",
            "SELECT a FROM t LIMIT a.b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'TRUE'. | syntax "
                + "error line 1 at position 22 unexpected 'TRUE'.",
            "SELECT a FROM t LIMIT TRUE");
        assertAnswer(
            "1 ;; 3",
            "SELECT a FROM t LIMIT NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ')'. | syntax error "
                + "line 1 at position 22 unexpected '('.",
            "SELECT a FROM t LIMIT ()");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '('. | syntax error "
                + "line 1 at position 22 unexpected '('.",
            "SELECT a FROM t LIMIT ((1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'A'",
            "SELECT a FROM t LIMIT (x)");
        answer(
            "SELECT a FROM t LIMIT (SELECT 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '1'. | syntax error "
                + "line 1 at position 22 unexpected '('.",
            "SELECT a FROM t LIMIT (1) OFFSET 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'x'.",
            "SELECT a FROM t LIMIT 1 OFFSET x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '('.",
            "SELECT a FROM t LIMIT 1 OFFSET (1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '<EOF>'.",
            "SELECT a FROM t LIMIT 1 OFFSET");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '+'.",
            "SELECT a FROM t LIMIT 1 OFFSET +1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'x'.",
            "SELECT a FROM t FETCH FIRST x ROWS ONLY");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '('.",
            "SELECT a FROM t FETCH FIRST (1) ROWS ONLY");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'x'. | syntax error "
                + "line 1 at position 22 unexpected 'x'.",
            "SELECT a FROM t FETCH x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '-'. | syntax error "
                + "line 1 at position 23 unexpected '-'.",
            "SELECT a FROM t OFFSET -1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x'. | syntax error "
                + "line 1 at position 23 unexpected 'x'.",
            "SELECT a FROM t OFFSET x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '-'. | syntax error "
                + "line 1 at position 32 unexpected '-'.",
            "SELECT 1; SELECT a FROM t LIMIT -1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '-'. | syntax error "
                + "line 1 at position 22 unexpected '-'.",
            "SELECT a FROM t LIMIT -1");
    }

    @Test
    public void afterAnotherClauseItIsToldOnce() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '-'.",
            "SELECT a FROM t WHERE a > 0 LIMIT -1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '-'.",
            "SELECT a FROM t GROUP BY a LIMIT -1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected '-'.",
            "SELECT a FROM t GROUP BY a HAVING a > 0 LIMIT -1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected '-'.",
            "SELECT a FROM t QUALIFY ROW_NUMBER() OVER (ORDER BY a) = 1 LIMIT -1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'x'.",
            "SELECT a FROM t ORDER BY a LIMIT x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '('.",
            "SELECT a FROM t ORDER BY a LIMIT (1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '+'.",
            "SELECT a FROM t ORDER BY a LIMIT +1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '<EOF>'.",
            "SELECT a FROM t ORDER BY a LIMIT");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '-'.",
            "SELECT a FROM t ORDER BY a OFFSET -1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected '-'. | syntax error "
                + "line 1 at position 48 unexpected '-'.",
            "SELECT a FROM t UNION ALL SELECT a FROM t LIMIT -1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected '-'.",
            "SELECT a FROM t UNION ALL SELECT a FROM t ORDER BY a LIMIT -1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '-'.",
            "(SELECT a FROM t) LIMIT -1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '-'. | syntax error "
                + "line 1 at position 23 unexpected '-'.",
            "(SELECT a FROM t LIMIT -1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 0 unexpected 'VALUES'.",
            "VALUES (1) LIMIT -1");
        answer(
            "TABLE t LIMIT -1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '-'.",
            "SELECT 1 LIMIT -1");
    }

    @Test
    public void scriptsBlocksAndOtherStatements() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        answer(
            "SELECT a FROM t LIMIT -1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'y'.",
            "SELECT 1 x y; SELECT a FROM t LIMIT -1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '-'. | syntax error "
                + "line 1 at position 28 unexpected '-'.",
            "BEGIN SELECT a FROM t LIMIT -1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '-'. | syntax error "
                + "line 1 at position 28 unexpected '-'.",
            "BEGIN SELECT a FROM t LIMIT -1; RETURN 1; END;");
        answer(
            "SELECT a FROM t LIMIT -1 OFFSET -1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '-'. | syntax error "
                + "line 1 at position 22 unexpected '-'.",
            "SELECT a FROM t LIMIT - -1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '-'. | syntax error "
                + "line 1 at position 22 unexpected '-'.",
            "SELECT a FROM t LIMIT -x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'.",
            "SELECT a FROM t LIMIT 1 x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '1'.",
            "SELECT a FROM t LIMIT 1 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ','.",
            "SELECT a FROM t LIMIT 1, 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '$1'. | syntax error "
                + "line 1 at position 22 unexpected '$1'.",
            "SELECT a FROM t LIMIT $1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 22 | Bind variable :x not set.",
            "SELECT a FROM t LIMIT :x");
        answer(
            "SELECT a FROM t LIMIT ?");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'a'.",
            "SELECT TOP x a FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function TOP.",
            "SELECT TOP (1) a FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '-'.",
            "SELECT a FROM t t2 LIMIT -1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'x'.",
            "SELECT a FROM t AS t2 LIMIT x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '-'.",
            "SELECT t.a FROM t, t t2 LIMIT -1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected '-'.",
            "SELECT t.a FROM t JOIN t t2 ON t.a = t2.a LIMIT -1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '-'. | syntax error "
                + "line 1 at position 38 unexpected '-'.",
            "SELECT a FROM (SELECT a FROM t) LIMIT -1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '-'.",
            "SELECT a FROM t SAMPLE (10) LIMIT -1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected '-'. | syntax error "
                + "line 1 at position 39 unexpected '-'.",
            "INSERT INTO t SELECT a, b FROM t LIMIT -1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected '-'. | syntax error "
                + "line 1 at position 51 unexpected '-'.",
            "CREATE OR REPLACE VIEW vx AS SELECT a FROM t LIMIT -1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'x'.",
            "SELECT a FROM t LIMIT 1 OFFSET 1 x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'x'. | syntax error "
                + "line 1 at position 22 unexpected 'x'.",
            "SELECT a FROM t LIMIT x OFFSET 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '-'. | syntax error "
                + "line 1 at position 22 unexpected '-'.",
            "SELECT a FROM t LIMIT -1 ROWS");
    }

    @Test
    public void otherOperands() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '-'.",
            "SELECT a FROM t LIMIT 1 OFFSET -1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected '-'.",
            "SELECT 1; SELECT a FROM t LIMIT 1 OFFSET -1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '-'.",
            "SELECT a FROM t FETCH FIRST -1 ROWS ONLY");
        assertAnswer(
            "1",
            "SELECT a FROM t LIMIT 1.5");
        assertAnswer(
            "1",
            "SELECT 1; SELECT a FROM t LIMIT 1.5");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'x'. | syntax error "
                + "line 1 at position 22 unexpected 'x'.",
            "SELECT a FROM t LIMIT x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '1'. | syntax error "
                + "line 1 at position 22 unexpected '('.",
            "SELECT a FROM t LIMIT (1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '+'.",
            "SELECT a FROM t LIMIT 1 + 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '-'. | syntax error "
                + "line 1 at position 22 unexpected '-'.",
            "SELECT a FROM t LIMIT - 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '+'. | syntax error "
                + "line 1 at position 22 unexpected '+'.",
            "SELECT a FROM t LIMIT +1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '-'. | syntax error "
                + "line 1 at position 22 unexpected '-'.",
            "SELECT a FROM t LIMIT -1 OFFSET 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 30 unexpected '-'. | syntax error line 1 at position 30 unexpected '-'.",
            "SELECT (SELECT a FROM t LIMIT -1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 40 unexpected '-'. | syntax error line 1 at position 40 unexpected '-'.",
            "SELECT 1; SELECT (SELECT a FROM t LIMIT -1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '-'. | syntax error "
                + "line 1 at position 37 unexpected '-'.",
            "SELECT a FROM (SELECT a FROM t LIMIT -1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '-'.",
            "SELECT a FROM t ORDER BY a LIMIT -1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'TOP'",
            "SELECT TOP -1 a FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected ''x''.",
            "SELECT TOP 'x' a FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected ''x''.",
            "SELECT 1; SELECT TOP 'x' a FROM t");
    }
}
