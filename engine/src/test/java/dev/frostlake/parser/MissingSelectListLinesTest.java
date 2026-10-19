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
 * A SELECT with no select list, and a FROM with no table before its WHERE: the token where the part is missing
 * is refused and the statement read on as if it stood there, so what the rest lacks is reported too — in a
 * statement, a view's or a table's query, a subquery, a script and a block (live-verified).
 */
public class MissingSelectListLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void theStatementIsReadOn() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'FROM'. | syntax error "
                + "line 1 at position 12 unexpected 'WHERE'. | syntax error line 1 at position 17 unexpected '<EOF>'.",
            "SELECT FROM WHERE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'FROM'. | syntax error "
                + "line 1 at position 11 unexpected '<EOF>'.",
            "SELECT FROM");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'FROM'.",
            "SELECT FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'WHERE'. | syntax "
                + "error line 1 at position 12 unexpected '<EOF>'.",
            "SELECT WHERE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'FROM'. | syntax error "
                + "line 1 at position 12 unexpected 'WHERE'.",
            "SELECT FROM WHERE a = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'FROM'. | syntax error "
                + "line 1 at position 19 unexpected '<EOF>'.",
            "SELECT FROM t WHERE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected ','.",
            "SELECT , FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'FROM'. | syntax error "
                + "line 1 at position 22 unexpected '<EOF>'.",
            "SELECT FROM t GROUP BY");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'FROM'. | syntax error "
                + "line 1 at position 12 unexpected 'FROM'.",
            "SELECT FROM FROM");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'FROM'. | syntax "
                + "error line 1 at position 28 unexpected '<EOF>'.",
            "CREATE VIEW v AS SELECT FROM");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'FROM'. | syntax "
                + "error line 1 at position 40 unexpected 'WHERE'. | syntax error line 1 at position 45 unexpected "
                + "'<EOF>'.",
            "CREATE OR REPLACE VIEW v AS SELECT FROM WHERE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'FROM'. | syntax error "
                + "line 1 at position 11 unexpected ';'. | syntax error line 1 at position 24 unexpected 'y'.",
            "SELECT FROM; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'FROM'. | syntax "
                + "error line 1 at position 17 unexpected ';'.",
            "BEGIN SELECT FROM; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'.",
            "SELECT (SELECT FROM)");
    }

    @Test
    public void otherClausesAndQueries() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'WHERE'. | syntax "
                + "error line 1 at position 19 unexpected '<EOF>'.",
            "SELECT 1 FROM WHERE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "SELECT 1 FROM");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'FROM'.",
            "SELECT 1 FROM FROM");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '<EOF>'.",
            "SELECT 1 WHERE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'FROM'. | syntax error "
                + "line 1 at position 16 unexpected 'y'.",
            "SELECT FROM t x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'FROM'.",
            "SELECT FROM t,");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'FROM'.",
            "SELECT DISTINCT FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'FROM'. | syntax "
                + "error line 1 at position 20 unexpected '<EOF>'.",
            "SELECT DISTINCT FROM");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'FROM'.",
            "SELECT TOP 1 FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'FROM'. | syntax "
                + "error line 1 at position 15 unexpected '<EOF>'.",
            "SELECT ALL FROM");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'FROM'. | syntax error "
                + "line 1 at position 22 unexpected '<EOF>'.",
            "SELECT FROM t ORDER BY");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'FROM'.",
            "SELECT FROM t LIMIT");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'FROM'. | syntax error "
                + "line 1 at position 18 unexpected '<EOF>'.",
            "SELECT FROM t JOIN");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'FROM'. | syntax error "
                + "line 1 at position 20 unexpected 'FROM'. | syntax error line 1 at position 24 unexpected ')'.",
            "SELECT FROM (SELECT FROM)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'FROM'. | syntax error "
                + "line 1 at position 22 unexpected 'FROM'. | syntax error line 1 at position 26 unexpected '<EOF>'.",
            "SELECT FROM t; SELECT FROM");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'GROUP'. | syntax "
                + "error line 1 at position 15 unexpected '<EOF>'.",
            "SELECT GROUP BY");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'ORDER'.",
            "SELECT ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '1'.",
            "SELECT LIMIT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'HAVING'. | syntax "
                + "error line 1 at position 13 unexpected '<EOF>'.",
            "SELECT HAVING");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'QUALIFY'. | syntax "
                + "error line 1 at position 14 unexpected '<EOF>'.",
            "SELECT QUALIFY");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected ';'.",
            "SELECT ;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected '<EOF>'.",
            "SELECT");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'INTO'. | syntax error "
                + "line 1 at position 11 unexpected '<EOF>'.",
            "SELECT INTO");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'UNION'.",
            "SELECT UNION SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'FROM'. | syntax error "
                + "line 1 at position 27 unexpected 'FROM'.",
            "SELECT FROM t UNION SELECT FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'FROM'. | syntax "
                + "error line 1 at position 25 unexpected '<EOF>'.",
            "INSERT INTO t SELECT FROM");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'FROM'. | syntax "
                + "error line 1 at position 30 unexpected '<EOF>'.",
            "CREATE TABLE tz AS SELECT FROM");
    }

    @Test
    public void subqueriesBlocksAndScripts() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 21 unexpected ')'.",
            "SELECT (SELECT FROM t)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'.",
            "SELECT 1 + (SELECT FROM)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'FROM'. | syntax "
                + "error line 1 at position 34 unexpected ')'.",
            "SELECT 1 WHERE EXISTS (SELECT FROM)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'FROM'. | syntax "
                + "error line 1 at position 26 unexpected ')'.",
            "SELECT * FROM (SELECT FROM)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'FROM'. | syntax "
                + "error line 1 at position 22 unexpected ')'.",
            "WITH c AS (SELECT FROM) SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'FROM'. | syntax "
                + "error line 1 at position 17 unexpected ';'.",
            "BEGIN SELECT FROM; RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'FROM'. | syntax "
                + "error line 1 at position 18 unexpected 'WHERE'. | syntax error line 1 at position 23 unexpected "
                + "';'.",
            "BEGIN SELECT FROM WHERE; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'FROM'. | syntax error "
                + "line 1 at position 12 unexpected 'WHERE'. | syntax error line 1 at position 17 unexpected ';'. | "
                + "syntax error line 1 at position 30 unexpected 'y'.",
            "SELECT FROM WHERE; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'FROM'. | syntax error "
                + "line 1 at position 22 unexpected 'b'.",
            "SELECT FROM t WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'FROM'. | syntax error "
                + "line 1 at position 16 unexpected 'y'. | syntax error line 1 at position 30 unexpected 'y'.",
            "SELECT FROM t x y; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'AS'.",
            "SELECT AS x FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected ','.",
            "SELECT , 1 FROM t");
        assertAnswer(
            "",
            "SELECT 1, FROM t");
    }
}
