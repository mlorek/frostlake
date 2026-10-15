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
 * ORDER BY ALL orders a query by every column it answers, left to right, under one direction and one NULLS
 * placement: a star's columns, a grouped or windowed list, a set operation's, a subquery's, a view's and a
 * block's SELECT INTO. It stands alone - a second key either side of it is a syntax error at the comma or at the
 * ALL - and a window's OVER and a WITHIN GROUP take none, refused at the ALL (live-verified).
 */
public class OrderByAllTest extends MeasuredCellsTestSupport {

    @Test
    public void ordersByEveryColumn() {
        try {
            assertAnswer(
                "Table T successfully created.",
                "CREATE OR REPLACE TABLE t (g VARCHAR, x INT)");
            assertAnswer(
                "4",
                "INSERT INTO t VALUES ('b', 1), ('a', 2), ('a', 1), (NULL, 3)");
            assertAnswer(
                "a|1 ;; a|2 ;; b|1 ;; null|3",
                "SELECT g, x FROM t ORDER BY ALL");
            assertAnswer(
                "3|null ;; 2|a ;; 1|b ;; 1|a",
                "SELECT x, g FROM t ORDER BY ALL DESC NULLS LAST");
            assertAnswer(
                "1|a ;; 1|b ;; 2|a ;; 3|null",
                "SELECT x, g FROM t ORDER BY ALL ASC");
            assertAnswer(
                "null|3 ;; a|1 ;; a|2 ;; b|1",
                "SELECT g, x FROM t ORDER BY ALL NULLS FIRST");
            assertAnswer(
                "null ;; b ;; a ;; a",
                "SELECT g FROM t ORDER BY ALL DESC");
            assertAnswer(
                "a|2 ;; b|1 ;; null|1",
                "SELECT g, COUNT(*) FROM t GROUP BY ALL ORDER BY ALL");
            assertAnswer(
                "a|1 ;; a|2 ;; b|1 ;; null|3",
                "SELECT * FROM t ORDER BY ALL");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ','.",
                "SELECT g, x FROM t ORDER BY ALL, x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'ALL'.",
                "SELECT g, x FROM t ORDER BY x, ALL");
            assertAnswer(
                "a|1 ;; a|2",
                "SELECT g, x FROM t ORDER BY ALL LIMIT 2");
            assertAnswer(
                "a|1 ;; a|2 ;; b|1 ;; null|3",
                "SELECT g AS all1, x FROM t ORDER BY ALL");
            assertAnswer(
                "1 ;; 1 ;; 2 ;; 3 ;; 5",
                "SELECT x FROM t UNION ALL SELECT 5 ORDER BY ALL");
            assertAnswer(
                "1",
                "SELECT \"ALL\" FROM (SELECT 1 AS \"ALL\") ORDER BY \"ALL\"");
        } finally {
            answer(
                "DROP TABLE IF EXISTS t");
        }
    }

    @Test
    public void standsOnlyAtAQuerysOwnOrderBy() {
        try {
            assertAnswer(
                "Table T successfully created.",
                "CREATE OR REPLACE TABLE t (g VARCHAR, x INT)");
            assertAnswer(
                "4",
                "INSERT INTO t VALUES ('b', 1), ('a', 2), ('a', 1), (NULL, 3)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'ALL'.",
                "SELECT x, SUM(x) OVER (ORDER BY ALL) FROM t");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'ALL'.",
                "SELECT LISTAGG(g) WITHIN GROUP (ORDER BY ALL) FROM t");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'ALL'.",
                "SELECT x, SUM(x) OVER (PARTITION BY g ORDER BY ALL) FROM t");
            assertAnswer(
                "a|1 ;; a|2 ;; b|1 ;; null|3",
                "SELECT * FROM (SELECT g, x FROM t ORDER BY ALL) ORDER BY ALL");
            assertAnswer(
                "a|1 ;; a|2 ;; b|1 ;; null|3",
                "SELECT g, x FROM t order by all asc nulls last");
            assertAnswer(
                "null|3 ;; b|1 ;; a|2",
                "SELECT g, x FROM t ORDER BY ALL DESC NULLS FIRST LIMIT 3");
            assertAnswer(
                "1",
                "EXECUTE IMMEDIATE $$DECLARE v INT; BEGIN SELECT x INTO :v FROM t ORDER BY ALL LIMIT 1; RETURN v; "
                    + "END;$$");
            assertAnswer(
                "View TV successfully created.",
                "CREATE OR REPLACE VIEW tv AS SELECT g, x FROM t ORDER BY ALL");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'ALL'.",
                "SELECT x, ROW_NUMBER() OVER (ORDER BY ALL) FROM t");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'ALL'.",
                "SELECT ARRAY_AGG(x) WITHIN GROUP (ORDER BY ALL DESC) FROM t");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'ALL'.",
                "SELECT g, x FROM t ORDER BY ALL ALL");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'x'.",
                "SELECT g, x FROM t ORDER BY ALL x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'ALL'.",
                "SELECT g, x FROM t ORDER BY (ALL)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '<EOF>'.",
                "SELECT g, x FROM t ORDER BY ALL NULLS");
        } finally {
            answer(
                "DROP VIEW IF EXISTS tv");
            answer(
                "DROP TABLE IF EXISTS t");
        }
    }

    @Test
    public void everyShapeOfQuery() {
        try {
            assertAnswer(
                "Table T successfully created.",
                "CREATE OR REPLACE TABLE t (g VARCHAR, x INT)");
            assertAnswer(
                "5",
                "INSERT INTO t VALUES ('b', 1), ('a', 2), ('a', 1), (NULL, 3), ('B', 0)");
            assertAnswer(
                "Table U successfully created.",
                "CREATE OR REPLACE TABLE u (k INT, y VARCHAR)");
            assertAnswer(
                "2",
                "INSERT INTO u VALUES (1, 'q'), (0, 'p')");
            assertAnswer(
                "a|1 ;; a|2 ;; B|0 ;; b|1 ;; null|3",
                "SELECT g COLLATE 'en-ci' AS g2, x FROM t ORDER BY ALL");
            assertAnswer(
                "0|7 ;; 1|7 ;; 1|7 ;; 2|7 ;; 3|7",
                "SELECT x, SUM(x) OVER () AS s FROM t ORDER BY ALL");
            assertAnswer(
                "B ;; a ;; b ;; null",
                "SELECT DISTINCT g FROM t ORDER BY ALL");
            assertAnswer(
                "Table C successfully created.",
                "CREATE OR REPLACE TABLE c AS SELECT g, x FROM t ORDER BY ALL DESC");
            assertAnswer(
                "B|0|0|p ;; a|1|1|q ;; b|1|1|q",
                "SELECT * FROM t, u WHERE t.x = u.k ORDER BY ALL");
            assertAnswer(
                "0|p|1 ;; 1|q|1",
                "SELECT u.*, 1 AS one FROM u ORDER BY ALL");
            assertAnswer(
                "1 ;; 1",
                "SELECT x FROM t ORDER BY ALL OFFSET 1 ROWS FETCH NEXT 2 ROWS ONLY");
            assertAnswer(
                "2|1",
                "SELECT 2 AS a, 1 AS b ORDER BY ALL");
            assertAnswer(
                "null|1 ;; b|1 ;; a|2 ;; B|1",
                "SELECT g, COUNT(*) AS n FROM t GROUP BY g HAVING COUNT(*) > 0 ORDER BY ALL DESC");
            assertAnswer(
                "a",
                "SELECT g FROM t INTERSECT SELECT 'a' ORDER BY ALL DESC");
            assertAnswer(
                "3",
                "SELECT (SELECT x FROM t ORDER BY ALL DESC LIMIT 1) AS top1");
            assertAnswer(
                "0|B ;; 1|a ;; 1|b ;; 2|a ;; 3|null",
                "SELECT x, g FROM t ORDER BY ALL NULLS FIRST");
            assertAnswer(
                "1 ;; 2 ;; a",
                "SELECT PARSE_JSON(v) AS j FROM (SELECT '2' AS v UNION ALL SELECT '\"a\"' UNION ALL SELECT '1') "
                    + "ORDER BY ALL");
            assertAnswer(
                "B|0 ;; a|1 ;; b|1 ;; null|3",
                "SELECT g, x FROM t QUALIFY ROW_NUMBER() OVER (PARTITION BY g ORDER BY x) = 1 ORDER BY ALL");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected ','.",
                "SELECT x FROM t ORDER BY ALL ASC NULLS LAST, x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ','.",
                "SELECT x FROM t ORDER BY ALL DESC, ALL");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'ALL'.",
                "SELECT x FROM t ORDER BY 1, ALL DESC");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected 'LAST'.",
                "SELECT x FROM t ORDER BY ALL DESC NULLS FIRST LAST");
            assertAnswer(
                "Table T successfully created.",
                "CREATE OR REPLACE TABLE t (g VARCHAR, x INT)");
            assertAnswer(
                "3",
                "INSERT INTO t VALUES ('b', 1), ('a', 2), ('a', 1)");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM t ORDER BY ALL");
            assertAnswer(
                "4|b",
                "SELECT SUM(x), MAX(g) FROM t ORDER BY ALL");
            assertAnswer(
                "a|3 ;; b|1",
                "SELECT g, SUM(x) FROM t GROUP BY g ORDER BY ALL");
            assertAnswer(
                "3",
                "SELECT COUNT(*) AS n FROM t GROUP BY ALL ORDER BY ALL");
            assertAnswer(
                "1|3 ;; 1|3 ;; 2|3",
                "SELECT x, COUNT(*) OVER () FROM t ORDER BY ALL");
            assertAnswer(
                "4",
                "SELECT SUM(x) FROM t HAVING SUM(x) > 0 ORDER BY ALL DESC");
        } finally {
            answer(
                "DROP TABLE IF EXISTS c");
            answer(
                "DROP TABLE IF EXISTS u");
            answer(
                "DROP TABLE IF EXISTS t");
        }
    }
}
