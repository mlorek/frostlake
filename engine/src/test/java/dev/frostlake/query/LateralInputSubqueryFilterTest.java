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
 * A WHERE condition on the relations left of a table function is applied before the function when it holds a
 * subquery too: the rows the function is called for are numbered from 1. A subquery that reads the function's
 * own rows, or a later relation's, is judged after the call (live-verified).
 */
public class LateralInputSubqueryFilterTest extends MeasuredCellsTestSupport {

    @Test
    public void aConditionHoldingASubquery() {
        answer(
            "CREATE OR REPLACE TABLE sq (k INT, c VARCHAR)");
        answer(
            "INSERT INTO sq VALUES (1, 'a.b'), (2, 'c.d'), (3, 'e.f')");
        assertAnswer(
            "2|1|1 ;; 2|1|2 ;; 3|2|1 ;; 3|2|2",
            "SELECT q.k, s.seq, s.index FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k > (SELECT MIN(k) "
                + "FROM sq) ORDER BY 1, s.index");
        assertAnswer(
            "2|1|1 ;; 2|1|2 ;; 3|2|1 ;; 3|2|2",
            "SELECT q.k, s.seq, s.index FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k > 1 ORDER BY 1, "
                + "s.index");
        assertAnswer(
            "2|1|1 ;; 2|1|2 ;; 3|2|1 ;; 3|2|2",
            "SELECT q.k, s.seq, s.index FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k IN (SELECT k FROM "
                + "sq WHERE k > 1) ORDER BY 1, s.index");
        assertAnswer(
            "3|1|1 ;; 3|1|2",
            "SELECT q.k, s.seq, s.index FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k = (SELECT MAX(k) "
                + "FROM sq) ORDER BY 1, s.index");
        assertAnswer(
            "2|1|1 ;; 2|1|2 ;; 3|2|1 ;; 3|2|2",
            "SELECT q.k, s.seq, s.index FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE EXISTS (SELECT 1 FROM "
                + "sq x WHERE x.k = q.k AND x.k > 1) ORDER BY 1, s.index");
        assertAnswer(
            "2|1|1 ;; 3|2|1",
            "SELECT q.k, s.seq, s.index FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k > (SELECT MIN(k) "
                + "FROM sq) AND s.index = 1 ORDER BY 1, s.index");
        assertAnswer(
            "1|1|1 ;; 1|1|2 ;; 2|2|1 ;; 2|2|2 ;; 3|3|1 ;; 3|3|2",
            "SELECT q.k, s.seq, s.index FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k > (SELECT MIN(k) "
                + "FROM sq) OR q.k = 1 ORDER BY 1, s.index");
        assertAnswer(
            "2|1|1 ;; 2|1|2 ;; 3|2|1 ;; 3|2|2",
            "SELECT q.k, s.seq, s.index FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE NOT q.k <= (SELECT "
                + "MIN(k) FROM sq) ORDER BY 1, s.index");
        assertAnswer(
            "2|1|1 ;; 2|1|2 ;; 3|2|1 ;; 3|2|2",
            "SELECT q.k, s.seq, s.index FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k NOT IN (SELECT "
                + "MIN(k) FROM sq) ORDER BY 1, s.index");
        assertAnswer(
            "2|1|1 ;; 2|1|2",
            "SELECT q.k, s.seq, s.index FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k > (SELECT MIN(k) "
                + "FROM sq) AND q.k < 3 ORDER BY 1, s.index");
        assertAnswer(
            "2|1|1 ;; 2|1|2 ;; 3|2|1 ;; 3|2|2",
            "SELECT q.k, s.seq, s.index FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k + (SELECT 0) > 1 "
                + "ORDER BY 1, s.index");
        assertAnswer(
            "2|1|0 ;; 2|1|1 ;; 3|2|0 ;; 3|2|1",
            "SELECT q.k, f.seq, f.index FROM sq q, LATERAL FLATTEN(SPLIT(q.c, '.')) f WHERE q.k > (SELECT MIN(k) "
                + "FROM sq) ORDER BY 1, f.index");
        assertAnswer(
            "REFUSED Unsupported feature 'lateral table function called with OUTER JOIN syntax or a join "
                + "predicate (ON clause)'.",
            "SELECT q.k, s.seq, s.index FROM sq q JOIN TABLE(SPLIT_TO_TABLE(q.c, '.')) s ON TRUE WHERE q.k > "
                + "(SELECT MIN(k) FROM sq) ORDER BY 1, s.index");
        assertAnswer(
            "2|2|1 ;; 2|2|2 ;; 3|3|1 ;; 3|3|2",
            "SELECT q.k, s.seq, s.index FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE s.value > (SELECT 'b') "
                + "ORDER BY 1, s.index");
        assertAnswer(
            "2|1|2 ;; 3|2|1 ;; 3|2|2",
            "SELECT q.k, s.seq, s.index FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k > (SELECT MIN(k) "
                + "FROM sq) QUALIFY ROW_NUMBER() OVER (ORDER BY q.k, s.index) > 1 ORDER BY 1, s.index");
        assertAnswer(
            "1|6",
            "SELECT COUNT(DISTINCT s.seq), COUNT(*) FROM sq q, TABLE(SPLIT_TO_TABLE('x.y', '.')) s");
        assertAnswer(
            "1|6",
            "SELECT COUNT(DISTINCT f.seq), COUNT(*) FROM sq q, LATERAL FLATTEN(SPLIT('x.y', '.')) f");
        assertAnswer(
            "2|1|1 ;; 2|1|2 ;; 3|2|1 ;; 3|2|2",
            "SELECT q.k, s.seq, s.index FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k > 1 AND q.k > "
                + "(SELECT MIN(k) FROM sq) ORDER BY 1, s.index");
        assertAnswer(
            "2|1|1 ;; 2|1|2",
            "SELECT q.k, s.seq, s.index FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.c LIKE (SELECT "
                + "'%.d') ORDER BY 1, s.index");
    }

    @Test
    public void aSubqueryThatReadsTheFunctionsRowsOrALaterRelation() {
        answer(
            "CREATE OR REPLACE TABLE sq (k INT, c VARCHAR)");
        answer(
            "INSERT INTO sq VALUES (1, 'a.b'), (2, 'c.d'), (3, 'e.f')");
        answer(
            "CREATE OR REPLACE TABLE su (uk INT)");
        answer(
            "INSERT INTO su VALUES (2), (3)");
        answer(
            "CREATE OR REPLACE VIEW sqv AS SELECT k AS vk FROM sq");
        assertAnswer(
            "1|b ;; 2|d ;; 3|f",
            "SELECT q.k, s.value FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE NOT EXISTS (SELECT 1 FROM sq "
                + "x WHERE x.k = q.k AND x.c LIKE s.value || '%') ORDER BY 1, 2");
        assertAnswer(
            "1|a ;; 2|c ;; 3|e",
            "SELECT q.k, s.value FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE EXISTS (SELECT 1 FROM sq x "
                + "WHERE x.k = q.k AND x.c LIKE s.value || '%') ORDER BY 1, 2");
        assertAnswer(
            "1|b ;; 2|d ;; 3|f",
            "SELECT q.k, f.value::VARCHAR FROM sq q, TABLE(FLATTEN(SPLIT(q.c, '.'), outer => TRUE)) AS f WHERE "
                + "NOT EXISTS (SELECT 1 FROM sq x WHERE x.k = q.k AND x.c LIKE f.value::VARCHAR || '%') ORDER BY 1, 2");
        assertAnswer(
            "2|c ;; 3|e",
            "SELECT q.k, f.value::VARCHAR FROM sq q, LATERAL FLATTEN(SPLIT(q.c, '.')) f WHERE q.k > 1 AND EXISTS "
                + "(SELECT 1 FROM sq x WHERE x.k = q.k AND x.c LIKE f.value::VARCHAR || '%') ORDER BY 1, 2");
        assertAnswer(
            "1|b ;; 2|d ;; 3|f",
            "SELECT q.k, s.value FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k IN (SELECT x.k FROM sq x "
                + "WHERE x.c LIKE '%' || s.value) ORDER BY 1, 2");
        assertAnswer(
            "1|b ;; 2|d ;; 3|f",
            "SELECT q.k, s.value FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k IN (SELECT x.k FROM sq x "
                + "WHERE x.c LIKE '%' || value) ORDER BY 1, 2");
        assertAnswer(
            "1|b ;; 2|d ;; 3|f",
            "SELECT q.k, s.value FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k = (SELECT MAX(x.k) FROM "
                + "sq x WHERE x.c LIKE '%' || s.value) ORDER BY 1, 2");
        assertAnswer(
            "1|a|1 ;; 1|b|1 ;; 2|c|2 ;; 2|d|2 ;; 3|e|3 ;; 3|f|3",
            "SELECT q.k, s.value, u.k FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s, sq u WHERE q.k IN (SELECT x.k "
                + "FROM sq x WHERE x.k = u.k) ORDER BY 1, 2, 3");
        assertAnswer(
            "2|c|2 ;; 2|d|2 ;; 3|e|3 ;; 3|f|3",
            "SELECT q.k, s.value, uk FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s, su WHERE q.k IN (SELECT x.k "
                + "FROM sq x WHERE x.k = uk) ORDER BY 1, 2, 3");
        assertAnswer(
            "2|c|2 ;; 2|d|2 ;; 3|e|3 ;; 3|f|3",
            "SELECT q.k, s.value, uk FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s, su WHERE EXISTS (SELECT 1 FROM "
                + "sq x WHERE x.k = q.k AND x.k = uk) ORDER BY 1, 2, 3");
        assertAnswer(
            "2|1|1 ;; 2|1|2 ;; 3|2|1 ;; 3|2|2",
            "SELECT q.k, s.seq, s.index FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k > (SELECT "
                + "MIN(v.vk) FROM sqv v) ORDER BY 1, s.index");
        assertAnswer(
            "2|1|1 ;; 2|1|2 ;; 3|2|1 ;; 3|2|2",
            "SELECT q.k, s.seq, s.index FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k > (SELECT MIN(d.k) "
                + "FROM (SELECT k FROM sq) d) ORDER BY 1, s.index");
        assertAnswer(
            "2|1|1 ;; 2|1|2 ;; 3|2|1 ;; 3|2|2",
            "SELECT q.k, s.seq, s.index FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k > (SELECT MIN(k) "
                + "FROM (SELECT k FROM sq)) ORDER BY 1, s.index");
        assertAnswer(
            "2|1|1 ;; 2|1|2 ;; 3|2|1 ;; 3|2|2",
            "SELECT q.k, s.seq, s.index FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE EXISTS (SELECT 1 FROM "
                + "sqv v WHERE v.vk = q.k AND v.vk > 1) ORDER BY 1, s.index");
        assertAnswer(
            "2|1|1 ;; 2|1|2 ;; 3|2|1 ;; 3|2|2",
            "SELECT q.k, s.seq, s.index FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE EXISTS (SELECT 1 FROM "
                + "su WHERE uk = k) ORDER BY 1, s.index");
        assertAnswer(
            "2|1|1 ;; 2|1|2 ;; 3|2|1 ;; 3|2|2",
            "SELECT q.k, s.seq, s.index FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k IN (SELECT uk FROM "
                + "su) ORDER BY 1, s.index");
    }
}
