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
 * MATCH_RECOGNIZE: a window function written with no OVER in MEASURES is read over its match, a window of its
 * own is refused there, and a refusal a measure or a definition earns is placed where the clause writes it
 * (live-verified). The malformed clauses are run for the record of their lines.
 */
public class MatchRecognizeMeasuredWindowsTest extends MeasuredCellsTestSupport {

    @Test
    public void aWindowFunctionIsReadOverItsMatch() {
        answer(
            "CREATE OR REPLACE TABLE s (id INT, v INT)");
        answer(
            "INSERT INTO s VALUES (1, 10), (2, 20), (3, 15), (4, 12), (5, 30), (6, 31), (7, 5), (8, 6)");
        assertAnswer(
            "1|1 ;; 2|1 ;; 3|1",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, ROW_NUMBER() AS rn ONE "
                + "ROW PER MATCH PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "1|10|1|1 ;; 2|20|1|2 ;; 4|12|2|1 ;; 5|30|2|2 ;; 6|31|2|3 ;; 7|5|3|1 ;; 8|6|3|2",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, ROW_NUMBER() AS rn ALL "
                + "ROWS PER MATCH PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY id");
        assertAnswer(
            "1|1 ;; 2|1 ;; 3|1",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, ROW_NUMBER() AS rn "
                + "PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "1|1 ;; 1|1 ;; 1|1",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES RANK() AS r, DENSE_RANK() AS d ONE ROW PER "
                + "MATCH PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "1|10|1|1 ;; 2|20|2|2 ;; 4|12|1|1 ;; 5|30|2|2 ;; 6|31|3|3 ;; 7|5|1|1 ;; 8|6|2|2",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES RANK() AS r, DENSE_RANK() AS d ALL ROWS PER "
                + "MATCH PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY id");
        assertAnswer(
            "1|2 ;; 1|2 ;; 1|3",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES ROW_NUMBER() AS rn, COUNT(*) AS c ONE ROW PER "
                + "MATCH PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "1|10|1 ;; 2|20|2 ;; 4|12|1 ;; 5|30|1 ;; 6|31|2 ;; 7|5|1 ;; 8|6|2",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES NTILE(2) AS nt ALL ROWS PER MATCH PATTERN (strt "
                + "up+) DEFINE up AS v > LAG(v)) ORDER BY id");
        assertAnswer(
            "1|10|null|20 ;; 2|20|10|null ;; 4|12|null|30 ;; 5|30|12|31 ;; 6|31|30|null ;; 7|5|null|6 ;; "
                + "8|6|5|null",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES LAG(v) AS lg, LEAD(v) AS ld ALL ROWS PER MATCH "
                + "PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY id");
        assertAnswer(
            "5|null ;; 10|null ;; 30|null",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES LAG(v) AS lg, LEAD(v) AS ld ONE ROW PER MATCH "
                + "PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "1|10|10|10 ;; 2|20|10|20 ;; 4|12|12|12 ;; 5|30|12|30 ;; 6|31|12|31 ;; 7|5|5|5 ;; 8|6|5|6",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES FIRST_VALUE(v) AS fv, LAST_VALUE(v) AS lv ALL "
                + "ROWS PER MATCH PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY id");
        assertAnswer(
            "5|6 ;; 10|20 ;; 12|31",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES FIRST_VALUE(v) AS fv, LAST_VALUE(v) AS lv ONE "
                + "ROW PER MATCH PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "1|10|null ;; 2|20|20 ;; 4|12|null ;; 5|30|30 ;; 6|31|30 ;; 7|5|null ;; 8|6|6",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES NTH_VALUE(v, 2) AS nv ALL ROWS PER MATCH "
                + "PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY id");
        assertAnswer(
            "11|5|6|2 ;; 30|10|20|2 ;; 73|12|31|3",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES SUM(v) AS sv, MIN(v) AS mi, MAX(v) AS ma, "
                + "COUNT(*) AS c ONE ROW PER MATCH PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 66 | OVER clause not allowed in "
                + "MATCH_RECOGNIZE MEASURES clause.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES ROW_NUMBER() OVER (ORDER BY id) AS rn ONE ROW "
                + "PER MATCH PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 60 | OVER clause not allowed in "
                + "MATCH_RECOGNIZE MEASURES clause.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES SUM(v) OVER () AS sv ONE ROW PER MATCH PATTERN "
                + "(strt up+) DEFINE up AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "1|10|2 ;; 2|20|3 ;; 4|12|2 ;; 5|30|3 ;; 6|31|4 ;; 7|5|2 ;; 8|6|3",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES ROW_NUMBER() + 1 AS rn1 ALL ROWS PER MATCH "
                + "PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY id");
        assertAnswer(
            "1|10|1 ;; 2|20|2 ;; 4|12|1 ;; 5|30|2 ;; 6|31|3 ;; 7|5|1 ;; 8|6|2",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES FINAL ROW_NUMBER() AS rn ALL ROWS PER MATCH "
                + "PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY id");
        assertAnswer(
            "1|10|1 ;; 2|20|2 ;; 4|12|1 ;; 5|30|2 ;; 6|31|3 ;; 7|5|1 ;; 8|6|2",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES RUNNING ROW_NUMBER() AS rn ALL ROWS PER MATCH "
                + "PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY id");
        assertAnswer(
            "1|10|0 ;; 2|20|1 ;; 4|12|0 ;; 5|30|1 ;; 6|31|2 ;; 7|5|0 ;; 8|6|0",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES CONDITIONAL_TRUE_EVENT(v > 15) AS ce ALL ROWS "
                + "PER MATCH PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY id");
        assertAnswer(
            "1|10|0.0 ;; 2|20|1.0 ;; 4|12|0.0 ;; 5|30|0.5 ;; 6|31|1.0 ;; 7|5|0.0 ;; 8|6|1.0",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES PERCENT_RANK() AS pr ALL ROWS PER MATCH PATTERN "
                + "(strt up+) DEFINE up AS v > LAG(v)) ORDER BY id");
        answer(
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES CUME_DIST() AS cd ALL ROWS PER MATCH PATTERN "
                + "(strt up+) DEFINE up AS v > LAG(v)) ORDER BY id");
        assertAnswer(
            "1|10|1|1 ;; 2|20|2|2 ;; 4|12|1|1 ;; 5|30|2|2 ;; 6|31|3|3 ;; 7|5|1|1 ;; 8|6|2|2",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES ROW_NUMBER() AS rn, MATCH_SEQUENCE_NUMBER() AS "
                + "msq ALL ROWS PER MATCH PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY id");
        assertAnswer(
            "1|10|null|-1 ;; 2|20|null|10 ;; 4|12|null|-1 ;; 5|30|null|12 ;; 6|31|12|30 ;; 7|5|null|-1 ;; "
                + "8|6|null|5",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES LAG(v, 2) AS lg2, LAG(v, 1, -1) AS lgd ALL ROWS "
                + "PER MATCH PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY id");
        assertAnswer(
            "1 ;; 1 ;; 1",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES ROW_NUMBER() AS rn ONE ROW PER MATCH PATTERN "
                + "(strt up+) DEFINE up AS v > LAG(v))");
        assertShape(
            "ID:NUMBER(38,0), V:NUMBER(38,0), RN:NUMBER(18,0), R:NUMBER(18,0), LG:NUMBER(38,0), FV:NUMBER(38,0)",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES ROW_NUMBER() AS rn, RANK() AS r, LAG(v) AS lg, "
                + "FIRST_VALUE(v) AS fv ALL ROWS PER MATCH PATTERN (strt up+) DEFINE up AS v > LAG(v))");
    }

    @Test
    public void refusalsOfAMeasure() {
        answer(
            "CREATE OR REPLACE TABLE s (id INT, v INT)");
        answer(
            "INSERT INTO s VALUES (1, 10), (2, 20), (3, 15), (4, 12), (5, 30), (6, 31), (7, 5), (8, 6)");
        answer(
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES FIRST(v, 1) AS f1, LAST(v, 1) AS l1 ONE ROW PER "
                + "MATCH PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY 1, 2");
        answer(
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES LAST(v - PREV(v)) AS d ONE ROW PER MATCH "
                + "PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY 1, 2");
        answer(
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES FINAL v AS f ALL ROWS PER MATCH PATTERN (strt "
                + "up+) DEFINE up AS v > LAG(v)) ORDER BY id");
        assertAnswer(
            "1|10|20 ;; 2|20|20 ;; 4|12|31 ;; 5|30|31 ;; 6|31|31 ;; 7|5|6 ;; 8|6|6",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES FINAL LAST(v) AS f ALL ROWS PER MATCH PATTERN "
                + "(strt up+) DEFINE up AS v > LAG(v)) ORDER BY id");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 55 | Invalid argument types for function "
                + "'+': (NUMBER(38,0), BOOLEAN)",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES v + TRUE AS x ONE ROW PER MATCH PATTERN (strt "
                + "up+) DEFINE up AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 108 | Invalid argument types for function "
                + "'+': (NUMBER(38,0), BOOLEAN)",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn PATTERN (strt up+) DEFINE "
                + "up AS v + TRUE > 1)");
        answer(
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES v::DATE AS d ONE ROW PER MATCH PATTERN (strt "
                + "up+) DEFINE up AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "REFUSED SQL compilation error: | [2] is not a valid order by expression",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES ABS('a') AS a ONE ROW PER MATCH PATTERN (strt "
                + "up+) DEFINE up AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn PATTERN (strt up+) DEFINE "
                + "up AS v > TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 53 | invalid identifier 'NOSUCH'",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES nosuch AS n ONE ROW PER MATCH PATTERN (strt "
                + "up+) DEFINE up AS v > LAG(v)) ORDER BY 1, 2");
        answer(
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES UPPER(v, 1) AS u ONE ROW PER MATCH PATTERN "
                + "(strt up+) DEFINE up AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 106 | invalid identifier 'NOSUCH'",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn PATTERN (strt up+) DEFINE "
                + "up AS nosuch > 1)");
    }

    @Test
    public void malformedClauses() {
        answer(
            "CREATE OR REPLACE TABLE s (id INT, v INT)");
        answer(
            "INSERT INTO s VALUES (1, 10), (2, 20), (3, 15), (4, 12), (5, 30), (6, 31), (7, 5), (8, 6)");
        answer(
            "SELECT * FROM s  MATCH_RECOGNIZE(ORDER BY id MEASURES 1 AS m DEFINE a AS TRUE)");
        answer(
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (^ any+ $) DEFINE "
                + "lo AS v < 13, hi AS v >= 13)");
        answer(
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (PERMUTE(lo, hi, "
                + "any)) DEFINE lo AS v < 13, hi AS v >= 13)");
        answer(
            "SELECT id, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (lo () hi) DEFINE "
                + "lo AS v < 13, hi AS v >= 13)");
        answer(
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER(), COUNT(*) ONE ROW PER MATCH "
                + "PATTERN (a b+) DEFINE b AS v > LAG(v))");
        answer(
            "SELECT * FROM s MATCH_RECOGNIZE(PARTITION BY id % 2 AS odd ORDER BY id MEASURES COUNT(*) AS c "
                + "PATTERN (a+) DEFINE a AS TRUE)");
        answer(
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn ONE ROW PER MATCH SHOW "
                + "EMPTY MATCHES PATTERN (a+) DEFINE a AS TRUE)");
        answer(
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn AFTER MATCH SKIP TO NEXT "
                + "ROW ONE ROW PER MATCH PATTERN (a a) DEFINE a AS TRUE)");
        answer(
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id ONE ROW PER MATCH MEASURES MATCH_NUMBER() AS mn PATTERN "
                + "(a a) DEFINE a AS TRUE)");
        answer(
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PARTITION BY v MEASURES MATCH_NUMBER() AS mn PATTERN (a) "
                + "DEFINE a AS TRUE)");
        answer(
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn DEFINE a AS TRUE PATTERN "
                + "(a))");
        answer(
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn PATTERN (a) PATTERN (a) "
                + "DEFINE a AS TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 85 unexpected ')'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn PATTERN (a))");
        answer(
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id PATTERN (a) DEFINE a AS TRUE MEASURES MATCH_NUMBER() AS "
                + "mn)");
        answer(
            "SELECT * FROM s MATCH_RECOGNIZE(MEASURES MATCH_NUMBER() AS mn ORDER BY id PATTERN (a) DEFINE a AS "
                + "TRUE)");
        answer(
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn PATTERN a DEFINE a AS TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 95 unexpected 'TRUE'.",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn PATTERN (a) DEFINE a TRUE)");
        answer(
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() mn PATTERN (a) DEFINE a AS TRUE)");
    }
}
