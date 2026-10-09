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
 * The SEQ column of FLATTEN, SPLIT_TO_TABLE and STRTOK_SPLIT_TO_TABLE numbers the input record: a call made
 * over the rows of the relations to its left numbers them from 1 in the order it receives them, every row of
 * one call alike. A row whose input yields nothing still takes its number, a call chained after another
 * numbers the rows the first produced, and every query block counts afresh. A condition of the WHERE clause
 * that reads the left relations alone is applied before the call, so the rows it removes take no number and
 * the function is never asked about them. Every answer is live-verified.
 */
public class TableFunctionInputSequenceTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE sn (k INT, c VARCHAR)");
        engine.execute("INSERT INTO sn VALUES (1, 'a.b'), (2, NULL), (3, 'e.f')");
        engine.execute("CREATE OR REPLACE TABLE sq (k INT, c VARCHAR)");
        engine.execute("INSERT INTO sq VALUES (1, 'a.b'), (2, 'c.d'), (3, 'e.f')");
    }

    @Test
    public void theRowsACallReceivesAreNumberedFromOne() {
        assertAnswer(
            "1|1|1|a ;; 1|1|2|b ;; 2|2|1|c ;; 2|2|2|d ;; 3|3|1|e ;; 3|3|2|f",
            "SELECT q.k, s.seq, s.index, s.value FROM sq q, TABLE(STRTOK_SPLIT_TO_TABLE(q.c, '.')) s ORDER BY "
                + "q.k, s.index");
        assertAnswer(
            "1|1|1|a ;; 1|1|2|b ;; 2|2|1|c ;; 2|2|2|d ;; 3|3|1|e ;; 3|3|2|f",
            "SELECT q.k, s.seq, s.index, s.value FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s ORDER BY q.k, "
                + "s.index");
        assertAnswer(
            "1|1|1|a ;; 1|1|2|b ;; 2|2|1|c ;; 2|2|2|d ;; 3|3|1|e ;; 3|3|2|f",
            "SELECT q.k, s.seq, s.index, s.value FROM sq q, LATERAL SPLIT_TO_TABLE(q.c, '.') s ORDER BY q.k, "
                + "s.index");
        assertAnswer(
            "1|1|0|a ;; 1|1|1|b ;; 2|2|0|c ;; 2|2|1|d ;; 3|3|0|e ;; 3|3|1|f",
            "SELECT q.k, f.seq, f.index, f.value FROM sq q, LATERAL FLATTEN(SPLIT(q.c, '.')) f ORDER BY q.k, "
                + "f.index");
        assertAnswer(
            "1|1|0 ;; 1|1|1 ;; 2|2|0 ;; 2|2|1 ;; 3|3|0 ;; 3|3|1",
            "SELECT q.k, f.seq, f.index FROM sq q, TABLE(FLATTEN(INPUT => SPLIT(q.c, '.'))) f ORDER BY q.k, "
                + "f.index");
        assertAnswer(
            "1|1|1 ;; 1|1|2 ;; 2|2|1 ;; 2|2|2",
            "SELECT q.k, s.seq, s.index FROM (SELECT * FROM VALUES (1, 'a.b'), (2, 'c.d') v (k, c)) q, "
                + "TABLE(SPLIT_TO_TABLE(q.c, '.')) s ORDER BY q.k, s.index");
        assertAnswer(
            "1|2|1|1 ;; 1|2|1|2 ;; 2|3|2|1 ;; 2|3|2|2",
            "SELECT a.k, b.k, s.seq, s.index FROM sq a JOIN sq b ON b.k = a.k + 1, TABLE(SPLIT_TO_TABLE(b.c, "
                + "'.')) s ORDER BY a.k, s.index");
        assertAnswer(
            "1|1|a ;; 1|1|b ;; 1|1|null ;; 1|1|null ;; 2|2|a ;; 2|2|b ;; 2|2|null ;; 2|2|null ;; 3|3|a ;; "
                + "3|3|b ;; 3|3|null ;; 3|3|null",
            "SELECT q.k, f.seq, f.key FROM (SELECT k, OBJECT_CONSTRUCT('a', k, 'b', ARRAY_CONSTRUCT(1, 2)) AS "
                + "o FROM sq) q, LATERAL FLATTEN(INPUT => q.o, RECURSIVE => TRUE) f ORDER BY q.k, f.path");
        assertAnswer(
            "1|2 ;; 2|2 ;; 3|2",
            "SELECT s.seq, COUNT(*) FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s GROUP BY 1 ORDER BY 1");
    }

    @Test
    public void aCallOnItsOwnServesOneRecord() {
        assertAnswer(
            "1|1|a ;; 1|2|b",
            "SELECT seq, index, value FROM TABLE(SPLIT_TO_TABLE('a.b', '.')) ORDER BY index");
        assertAnswer(
            "1|1|a ;; 1|2|b",
            "SELECT seq, index, value FROM TABLE(STRTOK_SPLIT_TO_TABLE('a.b', '.')) ORDER BY index");
        assertAnswer(
            "1|0|a ;; 1|1|b",
            "SELECT seq, index, value FROM TABLE(FLATTEN(INPUT => SPLIT('a.b', '.'))) ORDER BY index");
    }

    @Test
    public void aRowThatYieldsNothingTakesItsNumber() {
        assertAnswer(
            "1|1|1 ;; 1|1|2 ;; 3|3|1 ;; 3|3|2",
            "SELECT q.k, s.seq, s.index FROM sn q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s ORDER BY q.k, s.index");
        assertAnswer(
            "1|1|1 ;; 1|1|2 ;; 3|3|1 ;; 3|3|2",
            "SELECT q.k, s.seq, s.index FROM sn q, TABLE(STRTOK_SPLIT_TO_TABLE(q.c, '.')) s ORDER BY q.k, s.index");
        assertAnswer(
            "1|1|0 ;; 1|1|1 ;; 3|3|0 ;; 3|3|1",
            "SELECT q.k, f.seq, f.index FROM sn q, LATERAL FLATTEN(SPLIT(q.c, '.')) f ORDER BY q.k, f.index");
        assertAnswer(
            "1|1|0 ;; 1|1|1 ;; 2|2|null ;; 3|3|0 ;; 3|3|1",
            "SELECT q.k, f.seq, f.index FROM sn q, LATERAL FLATTEN(INPUT => SPLIT(q.c, '.'), OUTER => TRUE) f "
                + "ORDER BY q.k, f.index");
    }

    @Test
    public void aChainedCallNumbersTheRowsBeforeIt() {
        assertAnswer(
            "1|1|1|1|a ;; 1|1|1|2|x ;; 1|1|2|1|b ;; 1|1|2|2|x ;; 2|2|3|1|c ;; 2|2|3|2|x ;; 2|2|4|1|d ;; "
                + "2|2|4|2|x ;; 3|3|5|1|e ;; 3|3|5|2|x ;; 3|3|6|1|f ;; 3|3|6|2|x",
            "SELECT q.k, s.seq, s2.seq, s2.index, s2.value FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s, "
                + "TABLE(SPLIT_TO_TABLE(s.value || '-x', '-')) s2 ORDER BY q.k, s.index, s2.index");
        assertAnswer(
            "1|1|1|0 ;; 1|1|1|1 ;; 1|1|2|0 ;; 1|1|2|1 ;; 2|2|3|0 ;; 2|2|3|1 ;; 2|2|4|0 ;; 2|2|4|1 ;; 3|3|5|0 "
                + ";; 3|3|5|1 ;; 3|3|6|0 ;; 3|3|6|1",
            "SELECT q.k, f.seq, f2.seq, f2.index FROM sq q, LATERAL FLATTEN(SPLIT(q.c, '.')) f, LATERAL "
                + "FLATTEN(SPLIT(f.value || '-x', '-')) f2 ORDER BY q.k, f.index, f2.index");
    }

    @Test
    public void everyQueryBlockCountsAfresh() {
        assertAnswer(
            "1|a ;; 1|b ;; 1|c ;; 1|d",
            "SELECT s.seq, s.value FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k = 1 UNION ALL "
                + "SELECT s.seq, s.value FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k = 2 ORDER BY 2");
        assertAnswer(
            "3|1|3",
            "SELECT COUNT(DISTINCT s.seq), MIN(s.seq), MAX(s.seq) FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s");
        assertAnswer(
            "3|3",
            "SELECT (SELECT MAX(s.seq) FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s), (SELECT MAX(s.seq) FROM "
                + "sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s)");
        assertAnswer(
            "1|1|1 ;; 2|2|2 ;; 3|3|3",
            "WITH w AS (SELECT q.k, s.seq FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s) SELECT a.k, a.seq, "
                + "b.seq FROM w a JOIN w b ON a.k = b.k GROUP BY 1, 2, 3 ORDER BY 1");
    }

    @Test
    public void aConditionOnTheLeftRelationsIsAppliedFirst() {
        assertAnswer(
            "2|1|1 ;; 2|1|2 ;; 3|2|1 ;; 3|2|2",
            "SELECT q.k, s.seq, s.index FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k > 1 ORDER BY "
                + "q.k, s.index");
        assertAnswer(
            "2|1|0 ;; 2|1|1 ;; 3|2|0 ;; 3|2|1",
            "SELECT q.k, f.seq, f.index FROM sq q, LATERAL FLATTEN(SPLIT(q.c, '.')) f WHERE q.k > 1 ORDER BY "
                + "q.k, f.index");
        assertAnswer(
            "2|1|1 ;; 2|1|2 ;; 3|2|1 ;; 3|2|2",
            "SELECT q.k, s.seq, s.index FROM (SELECT * FROM sq WHERE k > 1) q, TABLE(SPLIT_TO_TABLE(q.c, "
                + "'.')) s ORDER BY q.k, s.index");
        assertAnswer(
            "2|1|0 ;; 2|1|1 ;; 3|2|0 ;; 3|2|1",
            "SELECT q.k, f.seq, f.index FROM (SELECT * FROM sq WHERE k > 1) q, LATERAL FLATTEN(SPLIT(q.c, "
                + "'.')) f ORDER BY q.k, f.index");
        assertAnswer(
            "2|1 ;; 2|1 ;; 3|2 ;; 3|2",
            "SELECT q.k, s.seq FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE ABS(q.k) > 1 ORDER BY 1, s.index");
        assertAnswer(
            "2|1 ;; 2|1 ;; 3|2 ;; 3|2",
            "SELECT q.k, s.seq FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k = 3 OR q.k = 2 ORDER BY "
                + "1, s.index");
        assertAnswer(
            "2|1 ;; 3|2",
            "SELECT q.k, s.seq FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k > 1 AND s.index = 1 "
                + "ORDER BY 1");
        assertAnswer(
            "1|1 ;; 2|2 ;; 2|2 ;; 3|3 ;; 3|3",
            "SELECT q.k, s.seq FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE s.value <> 'a' ORDER BY 1, "
                + "s.index");
        assertAnswer(
            "2|1 ;; 2|1 ;; 3|2 ;; 3|2",
            "SELECT q.k, s.seq FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k IN (2, 3) ORDER BY 1, "
                + "s.index");
        assertAnswer(
            "2|1 ;; 2|1 ;; 3|2 ;; 3|2",
            "SELECT q.k, s.seq FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE NOT q.k BETWEEN 1 AND 1 "
                + "ORDER BY 1, s.index");
        assertAnswer(
            "2|1 ;; 2|1 ;; 3|2 ;; 3|2",
            "SELECT q.k, f.seq FROM sq q LEFT JOIN LATERAL FLATTEN(SPLIT(q.c, '.')) f WHERE q.k > 1 ORDER BY "
                + "1, f.index");
    }

    @Test
    public void aRowTheConditionRemovesIsNeverGivenToTheFunction() {
        assertAnswer(
            "2|1 ;; 3|2",
            "SELECT q.k, f.seq FROM sq q, LATERAL FLATTEN(PARSE_JSON(IFF(q.k = 1, '{bad', '[1]'))) f WHERE "
                + "q.k > 1 ORDER BY 1");
        assertAnswer(
            "2|1 ;; 3|2",
            "SELECT q.k, f.seq FROM sq q, LATERAL FLATTEN(PARSE_JSON(IFF(q.k = 1, '{bad', '[1]'))) f WHERE "
                + "q.k + 0 > 1 ORDER BY 1");
    }

    @Test
    public void aJoinPredicateOnATableFunctionIsRefused() {
        assertAnswer(
            "REFUSED Unsupported feature 'lateral table function called with OUTER JOIN syntax or a join "
                + "predicate (ON clause)'.",
            "SELECT q.k, s.seq FROM sq q JOIN TABLE(SPLIT_TO_TABLE(q.c, '.')) s ON q.k > 1 ORDER BY 1, s.index");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated",
            "SELECT q.k, l.seq FROM sq q, LATERAL (SELECT seq FROM TABLE(SPLIT_TO_TABLE(q.c, '.'))) l ORDER "
                + "BY 1, 2");
    }
}
