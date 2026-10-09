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
 * What a symbol's definition reads. A definition is judged for a row over the clause's whole partition, before
 * any match: LAG, LEAD and NEXT read the rows around it, FIRST_VALUE the partition's first row, LAST and
 * LAST_VALUE the row itself, and an aggregate needs a frame of its own with no PARTITION BY or ORDER BY. A
 * number that is not zero reads as true, a NULL as false. PREV, FIRST, CLASSIFIER, MATCH_NUMBER, FINAL and a
 * qualified name are refused there. Every answer is live-verified.
 */
public class MatchRecognizeDefinitionsTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE s (id INT, v INT)");
        engine.execute("INSERT INTO s VALUES (1, 10), (2, 20), (3, 15), (4, 12), (5, 30), (6, 31), (7, 5), (8, 6)");
        engine.execute("CREATE OR REPLACE TABLE t (g VARCHAR, id INT, v INT, w FLOAT, d DATE, x VARCHAR)");
        engine.execute("""
            INSERT INTO t VALUES ('a', 1, 1, 1.5, '2024-01-01', 'p'), ('a', 2, 2, 2.5, '2024-01-02', 'q'),
            ('a', 3, NULL, NULL, NULL, NULL), ('a', 4, 4, 0.5, '2024-01-04', 'r'), ('b', 1, 9, 9.0, '2024-02-01', 's'),
            ('b', 2, 8, 8.0, '2024-02-02', 't'), (NULL, 1, 7, 7.0, '2024-03-01', 'u')""");
    }

    @Test
    public void navigationOverThePartition() {
        assertAnswer(
            "1|1|3 ;; 2|5|6",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, FIRST(id) AS f, "
                + "LAST(id) AS l PATTERN (a+) DEFINE a AS v > LAG(v, 2, 0)) ORDER BY 1");
        assertAnswer(
            "1|1|1 ;; 2|4|5 ;; 3|7|7",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, FIRST(id) AS f, "
                + "LAST(id) AS l PATTERN (a+) DEFINE a AS v < LEAD(v)) ORDER BY 1");
        assertAnswer(
            "1|1|1 ;; 2|4|5 ;; 3|7|7",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, FIRST(id) AS f, "
                + "LAST(id) AS l PATTERN (a+) DEFINE a AS v < NEXT(v)) ORDER BY 1");
        assertAnswer(
            "1|1|6",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, FIRST(id) AS f, "
                + "LAST(id) AS l PATTERN (a+) DEFINE a AS v >= FIRST_VALUE(v)) ORDER BY 1");
        assertAnswer(
            "1|1|8",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, FIRST(id) AS f, "
                + "LAST(id) AS l PATTERN (a+) DEFINE a AS v = LAST(v)) ORDER BY 1");
        assertAnswer(
            "1|8",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, COUNT(*) AS c PATTERN "
                + "(a+) DEFINE a AS v >= LAST_VALUE(v)) ORDER BY 1, 2");
        assertAnswer(
            "1|1|2 ;; 2|5|6 ;; 3|8|8",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, FIRST(id) AS f, "
                + "LAST(id) AS l PATTERN (a+) DEFINE a AS v >= AVG(v) OVER (ROWS BETWEEN 1 PRECEDING AND CURRENT "
                + "ROW)) ORDER BY 1");
        assertAnswer(
            "1|3",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, COUNT(*) AS c PATTERN "
                + "(a+) DEFINE a AS ROW_NUMBER() OVER (ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) "
                + "< 4) ORDER BY 1, 2");
        assertAnswer(
            "1|6 ;; 2|1",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, COUNT(*) AS c PATTERN "
                + "(a+) DEFINE a AS v > (SELECT MIN(v) FROM s)) ORDER BY 1");
        assertAnswer(
            "1|8",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, COUNT(*) AS c PATTERN "
                + "(a+) DEFINE a AS v >= RUNNING LAST(v)) ORDER BY 1, 2");
    }

    @Test
    public void datePartsAndValues() {
        assertAnswer(
            "a|1|2 ;; a|2|1 ;; b|1|2 ;; null|1|1",
            "SELECT * FROM t MATCH_RECOGNIZE(PARTITION BY g ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "COUNT(*) AS c PATTERN (a+) DEFINE a AS DATEDIFF(day, LAG(d), d) <= 1 OR LAG(d) IS NULL) ORDER "
                + "BY 1, 2");
        assertAnswer(
            "a|1|2 ;; a|2|1 ;; b|1|2 ;; null|1|1",
            "SELECT * FROM t MATCH_RECOGNIZE(PARTITION BY g ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "COUNT(*) AS c PATTERN (a+) DEFINE a AS DATEDIFF('day', LAG(d), d) <= 1 OR LAG(d) IS NULL) "
                + "ORDER BY 1, 2");
        assertAnswer(
            "a|1|2 ;; a|2|1",
            "SELECT * FROM t MATCH_RECOGNIZE(PARTITION BY g ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "COUNT(*) AS c PATTERN (a+) DEFINE a AS DATE_TRUNC(month, d) = '2024-01-01'::DATE) ORDER BY 1, 2");
        assertAnswer(
            "a|1|2 ;; a|2|1",
            "SELECT * FROM t MATCH_RECOGNIZE(PARTITION BY g ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "COUNT(*) AS c PATTERN (a+) DEFINE a AS EXTRACT(month FROM d) = 1) ORDER BY 1, 2");
        assertAnswer(
            "a|1|2 ;; a|2|1 ;; b|1|2",
            "SELECT * FROM t MATCH_RECOGNIZE(PARTITION BY g ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "COUNT(*) AS c PATTERN (a+) DEFINE a AS YEAR(d) = 2024 AND MONTH(d) < 3) ORDER BY 1, 2");
        assertAnswer(
            "a|1|1 ;; b|1|1",
            "SELECT * FROM t MATCH_RECOGNIZE(PARTITION BY g ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "COUNT(*) AS c PATTERN (a+) DEFINE a AS DATEADD(day, 1, LAG(d)) = d) ORDER BY 1, 2");
        assertAnswer(
            "2024-01-01|3 ;; 2024-02-01|2 ;; 2024-03-01|1 ;; null|1",
            "SELECT * FROM t MATCH_RECOGNIZE(PARTITION BY DATE_TRUNC(month, d) ORDER BY DATEDIFF(day, "
                + "'2024-01-01'::DATE, d) MEASURES COUNT(*) AS c PATTERN (a+) DEFINE a AS TRUE) ORDER BY 1");
        assertAnswer(
            "1",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn PATTERN (a+) DEFINE a "
                + "AS v) ORDER BY 1");
        assertAnswer(
            "1|7",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, COUNT(*) AS c PATTERN "
                + "(a+) DEFINE a AS v - 10) ORDER BY 1, 2");
        assertAnswer(
            "",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, COUNT(*) AS c PATTERN "
                + "(a+) DEFINE a AS NULL) ORDER BY 1");
        assertAnswer(
            "",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, COUNT(*) AS c PATTERN "
                + "(a+) DEFINE a AS 'x') ORDER BY 1, 2");
    }

    @Test
    public void refusals() {
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function PREV.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, FIRST(id) AS f, "
                + "LAST(id) AS l PATTERN (a+) DEFINE a AS v > PREV(v)) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FIRST.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, FIRST(id) AS f, "
                + "LAST(id) AS l PATTERN (a+) DEFINE a AS v >= FIRST(v)) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 135 | This function requires an explicit "
                + "window frame definition (OVER clause without PARTITION BY and ORDER BY).",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, FIRST(id) AS f, "
                + "LAST(id) AS l PATTERN (a+) DEFINE a AS v >= AVG(v)) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 130 | This function requires an explicit "
                + "window frame definition (OVER clause without PARTITION BY and ORDER BY).",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, FIRST(id) AS f, "
                + "LAST(id) AS l PATTERN (a+) DEFINE a AS COUNT(*) <= 3) ORDER BY 1");
        assertAnswer(
            "REFUSED Unsupported feature 'Correlated pattern definitions'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, FIRST(id) AS f, "
                + "LAST(id) AS l PATTERN (a b+) DEFINE b AS v > a.v) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function CLASSIFIER.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn PATTERN (a+) DEFINE a "
                + "AS CLASSIFIER() = 'A') ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function MATCH_NUMBER.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn PATTERN (a+) DEFINE a "
                + "AS MATCH_NUMBER() = 1) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL execution error: | Duplicate parameter specification - A.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn PATTERN (a+) DEFINE a "
                + "AS v > 0, a AS v < 0) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 99 | invalid identifier 'NOPE'",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn PATTERN (a+) DEFINE a "
                + "AS nope > 0) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 131 | ORDER BY clause not allowed within "
                + "MATCH_RECOGNIZE DEFINE clause.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, COUNT(*) AS c PATTERN "
                + "(a+) DEFINE a AS v > LAG(v) OVER (ORDER BY id)) ORDER BY 1, 2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 144 | PARTITION BY clause not allowed "
                + "within MATCH_RECOGNIZE DEFINE clause.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, COUNT(*) AS c PATTERN "
                + "(a+) DEFINE a AS v > SUM(v) OVER (PARTITION BY 1 ROWS BETWEEN 1 PRECEDING AND 1 PRECEDING)) "
                + "ORDER BY 1, 2");
        assertAnswer(
            "REFUSED SQL compilation error: | FINAL semantics not allowed in MATCH_RECOGNIZE DEFINE clause.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, COUNT(*) AS c PATTERN "
                + "(a+) DEFINE a AS v > FINAL LAST(v)) ORDER BY 1, 2");
        assertAnswer(
            "REFUSED Unsupported feature 'Correlated pattern definitions'.",
            "SELECT c FROM s AS x MATCH_RECOGNIZE(ORDER BY id MEASURES COUNT(*) AS c PATTERN (a+) DEFINE a AS "
                + "x.v > 0)");
    }
}
