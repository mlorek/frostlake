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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * What a measure reads. Under ONE ROW PER MATCH a measure is FINAL — read from the match's last row over the
 * whole match — and under ALL ROWS PER MATCH it is RUNNING unless FINAL is written. FIRST and LAST read the
 * match's first and last row, PREV and NEXT a row so many places away inside the match, and an aggregate the
 * rows of the match; a column predicated by a symbol reads the rows the match read as that symbol alone.
 * What the account refuses is refused in its words: RUNNING under ONE ROW PER MATCH, a symbol the pattern does
 * not hold, two symbols in one call, a symbol where the semantics take none, a name that is no column — the
 * word of a date part among them — and a cumulative frame an aggregate has none of. Every answer is
 * live-verified.
 */
public class MatchRecognizeMeasuresTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE n (id INT, k VARCHAR, v INT)");
        engine.execute("INSERT INTO n VALUES (1, 'x', NULL), (2, 'x', 3), (3, 'y', 3), (NULL, 'y', 4), (5, 'x', NULL)");
        engine.execute("CREATE OR REPLACE TABLE s (id INT, v INT)");
        engine.execute("INSERT INTO s VALUES (1, 10), (2, 20), (3, 15), (4, 12), (5, 30), (6, 31), (7, 5), (8, 6)");
        engine.execute("CREATE OR REPLACE TABLE t (g VARCHAR, id INT, v INT, w FLOAT, d DATE, x VARCHAR)");
        engine.execute("""
            INSERT INTO t VALUES ('a', 1, 1, 1.5, '2024-01-01', 'p'), ('a', 2, 2, 2.5, '2024-01-02', 'q'),
            ('a', 3, NULL, NULL, NULL, NULL), ('a', 4, 4, 0.5, '2024-01-04', 'r'), ('b', 1, 9, 9.0, '2024-02-01', 's'),
            ('b', 2, 8, 8.0, '2024-02-02', 't'), (NULL, 1, 7, 7.0, '2024-03-01', 'u')""");
    }

    @Test
    public void navigation() {
        assertAnswer(
            "1|10|20|10|null ;; 2|12|31|30|null ;; 3|5|6|5|null",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, FIRST(v) AS f, "
                + "LAST(v) AS l, PREV(v) AS p, NEXT(v) AS nx ONE ROW PER MATCH PATTERN (a b+) DEFINE b AS v > "
                + "LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "1|10|1|10|10|null|20 ;; 2|20|2|10|20|10|null ;; 4|12|4|12|12|null|30 ;; 5|30|5|12|30|12|31 ;; "
                + "6|31|6|12|31|30|null ;; 7|5|7|5|5|null|6 ;; 8|6|8|5|6|5|null",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES id AS i, FIRST(v) AS f, LAST(v) AS l, "
                + "PREV(v) AS p, NEXT(v) AS nx ALL ROWS PER MATCH PATTERN (a b+) DEFINE b AS v > LAG(v)) ORDER "
                + "BY 1, 2");
        assertAnswer(
            "1|10|1|null|null|-1|-1 ;; 2|20|2|null|null|-1|-1 ;; 4|12|4|null|31|-1|-1 ;; "
                + "5|30|5|null|null|-1|-1 ;; 6|31|6|12|null|12|-1 ;; 7|5|7|null|null|-1|-1 ;; 8|6|8|null|null|-1|-1",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES id AS i, PREV(v, 2) AS p2, NEXT(v, 2) AS "
                + "n2, PREV(v, 2, -1) AS p2d, NEXT(v, 9, -1) AS n9d ALL ROWS PER MATCH PATTERN (a b+) DEFINE b "
                + "AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "1|10|1|null|20|-1 ;; 2|20|2|10|null|-1 ;; 4|12|4|null|30|-1 ;; 5|30|5|12|31|-1 ;; "
                + "6|31|6|30|null|12 ;; 7|5|7|null|6|-1 ;; 8|6|8|5|null|-1",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES id AS i, LAG(v) AS lg, LEAD(v) AS ld, "
                + "LAG(v, 2, -1) AS lg2 ALL ROWS PER MATCH PATTERN (a b+) DEFINE b AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "1|10|1|10|10 ;; 2|20|2|10|20 ;; 4|12|4|12|12 ;; 5|30|5|12|30 ;; 6|31|6|12|31 ;; 7|5|7|5|5 ;; "
                + "8|6|8|5|6",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES id AS i, FIRST_VALUE(v) AS fv, "
                + "LAST_VALUE(v) AS lv ALL ROWS PER MATCH PATTERN (a b+) DEFINE b AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "1|10|1|10|10|10 ;; 2|20|1|20|20|20 ;; 4|12|2|12|12|12 ;; 5|30|2|30|30|30 ;; 6|31|2|31|31|31 ;; "
                + "7|5|3|5|5|5 ;; 8|6|3|6|6|6",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, PREV(v, 0) AS p0, "
                + "NEXT(v, 0) AS n0, LAG(v, 0) AS l0 ALL ROWS PER MATCH PATTERN (strt up+) DEFINE up AS v > "
                + "LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "1|10|1|20 ;; 2|20|1|null ;; 4|12|2|30 ;; 5|30|2|31 ;; 6|31|2|null ;; 7|5|3|6 ;; 8|6|3|null",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, PREV(v, -1) AS p ALL "
                + "ROWS PER MATCH PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "1|10|1|10|null|10 ;; 2|20|1|10|null|10 ;; 4|12|2|30|null|30 ;; 5|30|2|30|null|30 ;; "
                + "6|31|2|30|null|30 ;; 7|5|3|5|null|5 ;; 8|6|3|5|null|5",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, FINAL PREV(v) AS p, "
                + "FINAL NEXT(v) AS n, FINAL LAG(v) AS lg ALL ROWS PER MATCH PATTERN (strt up+) DEFINE up AS v > "
                + "LAG(v)) ORDER BY 1, 3, 4");
        assertAnswer(
            "1|10|1|null|-1|-1 ;; 2|20|1|null|-1|-1 ;; 4|12|2|12|12|-1 ;; 5|30|2|12|12|-1 ;; 6|31|2|12|12|-1 "
                + ";; 7|5|3|null|-1|-1 ;; 8|6|3|null|-1|-1",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, FINAL PREV(v, 2) AS "
                + "p2, FINAL PREV(v, 2, -1) AS p2d, FINAL NEXT(v, 1, -1) AS n1d ALL ROWS PER MATCH PATTERN (strt "
                + "up+) DEFINE up AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "1|10|1|10|20|2 ;; 2|20|1|10|20|2 ;; 4|12|2|12|31|3 ;; 5|30|2|12|31|3 ;; 6|31|2|12|31|3 ;; "
                + "7|5|3|5|6|2 ;; 8|6|3|5|6|2",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, FINAL FIRST(v) AS fv, "
                + "FINAL LAST(v) AS lv, FINAL COUNT(*) AS c ALL ROWS PER MATCH PATTERN (strt up+) DEFINE up AS v "
                + "> LAG(v)) ORDER BY 1, 2");
    }

    @Test
    public void aggregates() {
        assertAnswer(
            "1|2|30|10|20|15.000 ;; 2|3|73|12|31|24.333 ;; 3|2|11|5|6|5.500",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, COUNT(*) AS c, SUM(v) "
                + "AS sm, MIN(v) AS mi, MAX(v) AS mx, AVG(v) AS av ONE ROW PER MATCH PATTERN (a b+) DEFINE b AS "
                + "v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "1|10|1|1|10|10|10 ;; 2|20|2|2|30|10|20 ;; 4|12|4|1|12|12|12 ;; 5|30|5|2|42|12|30 ;; "
                + "6|31|6|3|73|12|31 ;; 7|5|7|1|5|5|5 ;; 8|6|8|2|11|5|6",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES id AS i, COUNT(*) AS c, SUM(v) AS sm, "
                + "MIN(v) AS mi, MAX(v) AS mx ALL ROWS PER MATCH PATTERN (a b+) DEFINE b AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "1|10|1|2|30|10 ;; 2|20|2|2|30|30 ;; 4|12|4|3|73|12 ;; 5|30|5|3|73|42 ;; 6|31|6|3|73|73 ;; "
                + "7|5|7|2|11|5 ;; 8|6|8|2|11|11",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES id AS i, FINAL COUNT(*) AS c, FINAL SUM(v) "
                + "AS sm, RUNNING SUM(v) AS rs ALL ROWS PER MATCH PATTERN (a b+) DEFINE b AS v > LAG(v)) ORDER "
                + "BY 1, 2");
        assertAnswer(
            "1|10,20|[10,20] ;; 2|12,30,31|[12,30,31] ;; 3|5,6|[5,6]",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, LISTAGG(v, ',') AS c, "
                + "ARRAY_AGG(v) AS a ONE ROW PER MATCH PATTERN (a b+) DEFINE b AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "1|1 ;; 2|2 ;; 3|2",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, COUNT(DISTINCT v % 2) "
                + "AS c PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "1|2|20|10 ;; 2|3|31|12 ;; 3|2|6|5",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, FINAL COUNT(*) AS c, "
                + "FINAL LAST(v) AS lv, FINAL FIRST(v) AS fv ONE ROW PER MATCH PATTERN (strt up+) DEFINE up AS v "
                + "> LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "1|STRT,UP|2 ;; 2|STRT,UP,UP|3 ;; 3|STRT,UP|2",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, LISTAGG(CLASSIFIER(), "
                + "',') AS cls, COUNT(CLASSIFIER()) AS cc ONE ROW PER MATCH PATTERN (strt up+) DEFINE up AS v > "
                + "LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 75 | Cumulative window frame unsupported "
                + "for function LISTAGG",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, LISTAGG(v, ',') AS "
                + "la, FINAL LISTAGG(v, ',') AS fl, ARRAY_AGG(v) AS aa ALL ROWS PER MATCH PATTERN (strt up+) "
                + "DEFINE up AS v > LAG(v)) ORDER BY 1, 3, 4");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 137 | Cumulative window frame "
                + "unsupported for function MEDIAN",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, AVG(v) AS av, FINAL "
                + "AVG(v) AS fav, MAX(v) - MIN(v) AS spread, MEDIAN(v) AS md ALL ROWS PER MATCH PATTERN (strt "
                + "up+) DEFINE up AS v > LAG(v)) ORDER BY 1, 3, 4");
    }

    @Test
    public void symbolPredicates() {
        assertAnswer(
            "1|10|20|20|20|20|1|1 ;; 2|12|31|30|31|61|2|1 ;; 3|5|6|6|6|6|1|1",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, a.v AS av, b.v AS bv, "
                + "FIRST(b.v) AS fb, LAST(b.v) AS lb, SUM(b.v) AS sb, COUNT(b.v) AS cb, COUNT(a.*) AS ca ONE ROW "
                + "PER MATCH PATTERN (a b+) DEFINE b AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "1|10|1|20|20|20 ;; 2|20|2|20|20|20 ;; 4|12|4|31|30|61 ;; 5|30|5|31|30|61 ;; 6|31|6|31|30|61 ;; "
                + "7|5|7|6|6|6 ;; 8|6|8|6|6|6",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES id AS i, FINAL LAST(b.v) AS lb, FINAL "
                + "FIRST(b.v) AS fb, FINAL SUM(b.v) AS sb ALL ROWS PER MATCH PATTERN (a b+) DEFINE b AS v > "
                + "LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "1|10|null|0|1 ;; 2|20|20|1|1 ;; 4|12|null|0|1 ;; 5|30|30|1|1 ;; 6|31|61|2|1 ;; 7|5|null|0|1 ;; "
                + "8|6|6|1|1",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES SUM(up.v) AS p, COUNT(up.*) AS c, "
                + "COUNT(strt.id) AS cs ALL ROWS PER MATCH PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY "
                + "1, 3, 4");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 111 | Pattern variable 'up' does not "
                + "occur in the pattern.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, COUNT(UP.*) AS c1, "
                + "SUM(Up.v) AS c2, \"up\".v AS c3 PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY 1");
    }

    @Test
    public void expressions() {
        assertAnswer(
            "1|10|20|3|B! ;; 2|19|31|7|B! ;; 3|1|6|9|B!",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, LAST(v) - FIRST(v) AS "
                + "d, v AS bare, id + 1 AS e, CLASSIFIER() || '!' AS c ONE ROW PER MATCH PATTERN (a b+) DEFINE b "
                + "AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "1|10|31 ;; 2|19|74 ;; 3|1|12",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, FINAL LAST(v) - "
                + "FIRST(v) AS d, 1 + FINAL SUM(v) AS e ONE ROW PER MATCH PATTERN (a b+) DEFINE b AS v > LAG(v)) "
                + "ORDER BY 1, 2");
        assertAnswer(
            "1|2 ;; 2|3 ;; 3|2",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() mn, COUNT(*) c ONE ROW PER "
                + "MATCH PATTERN (a b+) DEFINE b AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "1|10|2|false|strt ;; 2|20|2|true|up ;; 4|12|3|false|strt ;; 5|30|3|true|up ;; 6|31|3|true|up ;; "
                + "7|5|4|false|strt ;; 8|6|4|true|up",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() + 1 AS mn1, CLASSIFIER() = "
                + "'UP' AS isup, LOWER(CLASSIFIER()) AS lc ALL ROWS PER MATCH PATTERN (strt up+) DEFINE up AS v "
                + "> LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "1|15 ;; 2|26 ;; 3|1",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, LAST(v) - (SELECT "
                + "MIN(v) FROM s) AS d PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY 1");
        assertAnswer(
            "1|20 ;; 2|31 ;; 3|6",
            "SELECT \"Mn\", \"last v\" FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS \"Mn\", "
                + "LAST(v) AS \"last v\" PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY 1");
        assertAnswer(
            "a|0 ;; a|1 ;; b|1 ;; null|0",
            "SELECT * FROM t MATCH_RECOGNIZE(PARTITION BY g ORDER BY id MEASURES DATEDIFF('day', FIRST(d), "
                + "LAST(d)) AS dd PATTERN (a+) DEFINE a AS d IS NOT NULL) ORDER BY 1, 2");
        assertAnswer(
            "a|2024-01-01 ;; a|2024-01-01 ;; b|2024-02-01 ;; null|2024-03-01",
            "SELECT * FROM t MATCH_RECOGNIZE(PARTITION BY g ORDER BY id MEASURES DATE_TRUNC('month', LAST(d)) "
                + "AS m PATTERN (a+) DEFINE a AS d IS NOT NULL) ORDER BY 1, 2");
        assertAnswer(
            "a|2024|2024 ;; a|2024|2024 ;; b|2024|2024 ;; null|2024|2024",
            "SELECT * FROM t MATCH_RECOGNIZE(PARTITION BY g ORDER BY id MEASURES EXTRACT(year FROM LAST(d)) "
                + "AS y, YEAR(FIRST(d)) AS y2 PATTERN (a+) DEFINE a AS d IS NOT NULL) ORDER BY 1, 2");
        assertAnswer(
            "a|2024-01-02|many|up|2.0 ;; a|2024-01-04|one|flat|0.5 ;; b|2024-02-02|many|flat|8.5 ;; "
                + "null|2024-03-01|one|flat|7.0",
            "SELECT * FROM t MATCH_RECOGNIZE(PARTITION BY g ORDER BY id MEASURES LAST(d)::VARCHAR AS dv, "
                + "IFF(COUNT(*) > 1, 'many', 'one') AS size, CASE WHEN LAST(v) > FIRST(v) THEN 'up' ELSE 'flat' "
                + "END AS dir, ROUND(AVG(w), 1) AS aw PATTERN (a+) DEFINE a AS d IS NOT NULL) ORDER BY 1, 2");
    }

    @Test
    public void refusals() {
        assertAnswer(
            "REFUSED SQL compilation error: | RUNNING semantics not allowed in MATCH_RECOGNIZE MEASURES "
                + "clause when using ONE ROW PER MATCH.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, RUNNING SUM(v) AS rs, "
                + "FINAL SUM(v) AS fs, RUNNING LAST(v) AS rl ONE ROW PER MATCH PATTERN (a b+) DEFINE b AS v > "
                + "LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "REFUSED Unsupported feature 'pattern variable predicate for a column with RUNNING semantic'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES id AS i, a.v AS av, b.v AS bv, SUM(b.v) AS "
                + "sb, COUNT(b.*) AS cb ALL ROWS PER MATCH PATTERN (a b+) DEFINE b AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 75 | Pattern variable 'ZZ' does not "
                + "occur in the pattern.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, zz.v AS c ONE ROW PER "
                + "MATCH PATTERN (a b+) DEFINE b AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 86 | invalid identifier 'B'",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, CLASSIFIER(b) AS c "
                + "ONE ROW PER MATCH PATTERN (a b+) DEFINE b AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "REFUSED Unsupported feature 'this window function and semantic combination does currently not "
                + "support pattern variable predicates'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, PREV(up.v) AS p "
                + "PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "REFUSED Unsupported feature 'this window function and semantic combination does currently not "
                + "support pattern variable predicates'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, NEXT(strt.v) AS p "
                + "PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "REFUSED Unsupported feature 'this window function and semantic combination does currently not "
                + "support pattern variable predicates'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES FIRST(up.v) AS p ALL ROWS PER MATCH PATTERN "
                + "(strt up+) DEFINE up AS v > LAG(v)) ORDER BY 1, 3, 4");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 86 | Illegal use of multiple pattern "
                + "variables in window function.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, SUM(up.v - strt.v) AS "
                + "d PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 86 | Illegal use of multiple pattern "
                + "variables in window function.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, SUM(up.v + v) AS d "
                + "PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 77 | invalid identifier 'DAY'",
            "SELECT * FROM t MATCH_RECOGNIZE(PARTITION BY g ORDER BY id MEASURES DATEDIFF(day, FIRST(d), "
                + "LAST(d)) AS dd PATTERN (a+) DEFINE a AS d IS NOT NULL) ORDER BY 1, 2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 77 | invalid identifier 'DAY'",
            "SELECT * FROM t MATCH_RECOGNIZE(PARTITION BY g ORDER BY id MEASURES DATEDIFF(day, d, d) AS dd "
                + "PATTERN (a+) DEFINE a AS d IS NOT NULL) ORDER BY 1, 2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 79 | invalid identifier 'MONTH'",
            "SELECT * FROM t MATCH_RECOGNIZE(PARTITION BY g ORDER BY id MEASURES DATE_TRUNC(month, LAST(d)) "
                + "AS m PATTERN (a+) DEFINE a AS d IS NOT NULL) ORDER BY 1, 2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 76 | invalid identifier 'DAY'",
            "SELECT * FROM t MATCH_RECOGNIZE(PARTITION BY g ORDER BY id MEASURES DATEADD(day, 1, LAST(d)) AS "
                + "nx PATTERN (a+) DEFINE a AS d IS NOT NULL) ORDER BY 1, 2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 62 | Pattern variable 'X' does not occur "
                + "in the pattern.",
            "SELECT c FROM s AS x MATCH_RECOGNIZE(ORDER BY id MEASURES SUM(x.v) AS c PATTERN (a+) DEFINE a AS "
                + "TRUE)");
    }
}
