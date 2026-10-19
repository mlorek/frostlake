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
 * A word of a FROM list the account reads in a way of its own: a clause word or a join word where a join's
 * table belongs, a name after LATERAL, a dot (live-verified).
 */
public class JoinWordLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void aWordWhereTheTableBelongs() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        answer(
            "CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '('. | syntax error "
                + "line 1 at position 29 unexpected ')'.",
            "SELECT 1 FROM t JOIN USING (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '1'. | syntax error "
                + "line 1 at position 29 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN OFFSET 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '.'.",
            "SELECT 1 FROM t JOIN .");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'AS'.",
            "SELECT 1 FROM t JOIN WINDOW w AS ()");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'FOR'.",
            "SELECT 1 FROM t JOIN FOR UPDATE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '<EOF>'. | syntax "
                + "error line 1 at position 26 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN INNER");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '<EOF>'. | syntax "
                + "error line 1 at position 25 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN ASOF");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 28 unexpected ')'.",
            "SELECT (SELECT 1 FROM t JOIN)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'y'. | syntax error "
                + "line 1 at position 31 unexpected 'y'.",
            "SELECT 1 FROM t JOIN LATERAL x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN USING");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '('. | syntax error "
                + "line 1 at position 29 unexpected ')'.",
            "SELECT 1 FROM t JOIN USING (a) WHERE 1 = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '('. | syntax error "
                + "line 1 at position 32 unexpected ')'.",
            "SELECT 1 FROM t JOIN USING (a, b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'a'.",
            "SELECT 1 FROM t JOIN USING a");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'ON'.",
            "SELECT 1 FROM t JOIN ON t.a = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'ON'.",
            "SELECT 1 FROM t JOIN ON (t.a = 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'OFFSET' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE OFFSET.",
            "SELECT 1 FROM t JOIN OFFSET");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '1'. | syntax error "
                + "line 1 at position 34 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN OFFSET 1 ROWS");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'.",
            "SELECT 1 FROM t JOIN LIMIT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '1'.",
            "SELECT 1 FROM t JOIN FETCH FIRST 1 ROWS ONLY");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'WINDOW' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE WINDOW.",
            "SELECT 1 FROM t JOIN WINDOW");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'WINDOW' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE WINDOW.",
            "SELECT 1 FROM t JOIN WINDOW w");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'AS'. | syntax error "
                + "line 1 at position 44 unexpected ')'.",
            "SELECT 1 FROM t JOIN WINDOW w AS (ORDER BY a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'FOR'. | syntax error "
                + "line 1 at position 24 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN FOR");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'FOR'.",
            "SELECT 1 FROM t JOIN FOR UPDATE NOWAIT");
        assertAnswer(
            "REFUSED Unsupported feature 'FOR UPDATE clause'.",
            "SELECT 1 FROM t FOR UPDATE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN LEFT");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'OUTER' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE OUTER.",
            "SELECT 1 FROM t JOIN OUTER");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '<EOF>'. | syntax "
                + "error line 1 at position 26 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN CROSS");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '<EOF>'. | syntax "
                + "error line 1 at position 28 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN NATURAL");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN FULL");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN RIGHT");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'n'. | syntax error "
                + "line 1 at position 27 unexpected 'n'.",
            "SELECT 1 FROM t JOIN INNER n");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'JOIN'.",
            "SELECT 1 FROM t JOIN INNER JOIN n");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'n'. | syntax error "
                + "line 1 at position 26 unexpected 'n'. | syntax error line 1 at position 27 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN ASOF n");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'WHERE'. | syntax "
                + "error line 1 at position 27 unexpected 'WHERE'.",
            "SELECT 1 FROM t JOIN INNER WHERE 1 = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'JOIN'. | syntax "
                + "error line 1 at position 32 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN ASOF JOIN n");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '.'.",
            "SELECT 1 FROM t JOIN ..");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '.'.",
            "SELECT 1 FROM t JOIN .n");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '.'.",
            "SELECT 1 FROM t JOIN . n");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected ','.",
            "SELECT 1 FROM t JOIN ,");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected ')'.",
            "SELECT 1 FROM t JOIN )");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '.'.",
            "SELECT 1 FROM t JOIN . WHERE 1 = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected ';'. | syntax error "
                + "line 1 at position 34 unexpected 'y'.",
            "SELECT 1 FROM t JOIN ; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN (");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 28 unexpected ')'.",
            "SELECT (SELECT 1 FROM t JOIN) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected ')'.",
            "SELECT 1 WHERE EXISTS (SELECT 1 FROM t JOIN)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ')'.",
            "SELECT 1 FROM (SELECT 1 FROM t JOIN) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'SELECT'.",
            "SELECT 1 WHERE 1 IN (SELECT 1 FROM t JOIN)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'TABLESAMPLE'.",
            "SELECT 1 FROM t JOIN TABLESAMPLE (10)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'SAMPLE'.",
            "SELECT 1 FROM t JOIN SAMPLE (10)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'QUALIFY'.",
            "SELECT 1 FROM t JOIN QUALIFY 1 = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'HAVING'.",
            "SELECT 1 FROM t JOIN HAVING 1 = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'GROUP'.",
            "SELECT 1 FROM t JOIN GROUP BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'ORDER'.",
            "SELECT 1 FROM t JOIN ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'WHERE'.",
            "SELECT 1 FROM t JOIN WHERE 1 = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'UNION'.",
            "SELECT 1 FROM t JOIN UNION SELECT 1");
        assertAnswer(
            "",
            "SELECT 1 FROM t JOIN VALUES (1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN TABLE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'AS'.",
            "SELECT 1 FROM t JOIN AS x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN n AS");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'PIVOT' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE PIVOT.",
            "SELECT 1 FROM t JOIN PIVOT");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'MATCH_RECOGNIZE' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE MATCH_RECOGNIZE.",
            "SELECT 1 FROM t JOIN MATCH_RECOGNIZE");
    }

    @Test
    public void aNameAfterLateral() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        answer(
            "CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN LATERAL");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '<EOF>'. | syntax "
                + "error line 1 at position 30 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN LATERAL x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'y'. | syntax error "
                + "line 1 at position 31 unexpected 'y'. | syntax error line 1 at position 33 unexpected 'z'.",
            "SELECT 1 FROM t JOIN LATERAL x y z");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 'y'.",
            "SELECT 1 FROM t JOIN LATERAL (SELECT 1) x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '<EOF>'. | syntax "
                + "error line 1 at position 30 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN LATERAL n");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'm'. | syntax error "
                + "line 1 at position 31 unexpected 'm'.",
            "SELECT 1 FROM t JOIN LATERAL n m");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'ON'. | syntax error "
                + "line 1 at position 31 unexpected 'ON'.",
            "SELECT 1 FROM t JOIN LATERAL n ON TRUE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected 'y'.",
            "SELECT 1 FROM t JOIN LATERAL FLATTEN(ARRAY_CONSTRUCT(1)) x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'y'. | syntax error "
                + "line 1 at position 27 unexpected 'y'.",
            "SELECT 1 FROM t, LATERAL x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '<EOF>'. | syntax "
                + "error line 1 at position 26 unexpected '<EOF>'.",
            "SELECT 1 FROM t, LATERAL x");
    }

    @Test
    public void theSameWordsWider() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        answer(
            "CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '('. | syntax error "
                + "line 1 at position 29 unexpected ')'.",
            "SELECT 1 FROM t JOIN USING (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '('.",
            "SELECT 1 FROM t JOIN USING ()");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '('. | syntax error "
                + "line 1 at position 29 unexpected ')'.",
            "SELECT 1 FROM t JOIN USING (a) ON TRUE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'x'.",
            "SELECT 1 FROM t JOIN USING x.y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '('. | syntax error "
                + "line 1 at position 29 unexpected ')'.",
            "SELECT 1 FROM t JOIN USING (a) (b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '('. | syntax error "
                + "line 1 at position 30 unexpected ')'.",
            "SELECT 1 FROM t JOIN USING ((a))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '1'. | syntax error "
                + "line 1 at position 30 unexpected '2'.",
            "SELECT 1 FROM t JOIN OFFSET 1 2");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'OFFSET' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE OFFSET.",
            "SELECT 1 FROM t JOIN OFFSET x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '1'. | syntax error "
                + "line 1 at position 30 unexpected 'WHERE'.",
            "SELECT 1 FROM t JOIN OFFSET 1 WHERE 1 = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'.",
            "SELECT 1 FROM t JOIN LIMIT 1 OFFSET 2");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'LIMIT' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE LIMIT.",
            "SELECT 1 FROM t JOIN LIMIT");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'LIMIT' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE LIMIT.",
            "SELECT 1 FROM t JOIN LIMIT x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'AS'.",
            "SELECT 1 FROM t JOIN WINDOW w AS (PARTITION BY a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'AS'.",
            "SELECT 1 FROM t JOIN WINDOW w AS x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'AS'.",
            "SELECT 1 FROM t JOIN WINDOW w AS");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'n'. | syntax error "
                + "line 1 at position 27 unexpected 'n'.",
            "SELECT 1 FROM t JOIN INNER n ON TRUE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'n'. | syntax error "
                + "line 1 at position 27 unexpected 'n'.",
            "SELECT 1 FROM t JOIN CROSS n");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'n'. | syntax error "
                + "line 1 at position 29 unexpected 'n'.",
            "SELECT 1 FROM t JOIN NATURAL n");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'JOIN'.",
            "SELECT 1 FROM t JOIN NATURAL JOIN n");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'JOIN'.",
            "SELECT 1 FROM t JOIN CROSS JOIN n");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'n'.",
            "SELECT 1 FROM t JOIN LEFT n");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'JOIN'.",
            "SELECT 1 FROM t JOIN LEFT JOIN n");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'OUTER'. | syntax "
                + "error line 1 at position 31 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN LEFT OUTER");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'OUTER'.",
            "SELECT 1 FROM t JOIN LEFT OUTER JOIN n");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'n'. | syntax error "
                + "line 1 at position 26 unexpected 'n'. | syntax error line 1 at position 29 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN ASOF n m");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'n'. | syntax error "
                + "line 1 at position 27 unexpected 'n'.",
            "SELECT 1 FROM t JOIN INNER n m");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'. | syntax error "
                + "line 1 at position 27 unexpected '1'.",
            "SELECT 1 FROM t JOIN INNER 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '('. | syntax error "
                + "line 1 at position 27 unexpected '('.",
            "SELECT 1 FROM t JOIN INNER (SELECT 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'z'. | syntax error "
                + "line 1 at position 33 unexpected 'z'.",
            "SELECT 1 FROM t JOIN LATERAL x.y z");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'AS'. | syntax error "
                + "line 1 at position 31 unexpected 'AS'.",
            "SELECT 1 FROM t JOIN LATERAL x AS y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'ON'. | syntax error "
                + "line 1 at position 31 unexpected 'ON'.",
            "SELECT 1 FROM t JOIN LATERAL x ON TRUE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'y'. | syntax error "
                + "line 1 at position 24 unexpected 'y'.",
            "SELECT 1 FROM LATERAL x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'y'. | syntax error "
                + "line 1 at position 36 unexpected 'y'.",
            "SELECT 1 FROM t LEFT JOIN LATERAL x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ','. | syntax error "
                + "line 1 at position 33 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN LATERAL x, n");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'WHERE'. | syntax "
                + "error line 1 at position 31 unexpected 'WHERE'.",
            "SELECT 1 FROM t JOIN LATERAL x WHERE 1 = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '1'.",
            "SELECT 1 FROM t JOIN LATERAL 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '.'.",
            "SELECT 1 FROM t JOIN . ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '.'.",
            "SELECT 1 FROM t JOIN . n m");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '.'. | syntax error "
                + "line 1 at position 35 unexpected 'y'.",
            "SELECT 1 FROM t JOIN .; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'FOR'. | syntax error "
                + "line 1 at position 32 unexpected 'x'.",
            "SELECT 1 FROM t JOIN FOR UPDATE x");
    }

    @Test
    public void whatStillJoins() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        answer(
            "CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        answer(
            "INSERT INTO t VALUES (1, 'x', '2024-01-01')");
        assertAnswer(
            "2",
            "SELECT COUNT(*) FROM t, LATERAL FLATTEN(ARRAY_CONSTRUCT(1, 2)) f");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM t JOIN LATERAL (SELECT 1 AS q) s");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM t, LATERAL (SELECT 1 AS q) s");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM t JOIN n ON t.a = n.a");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM t CROSS JOIN n");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM t NATURAL JOIN n");
    }
}
