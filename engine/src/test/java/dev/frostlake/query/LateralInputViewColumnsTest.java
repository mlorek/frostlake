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
 * A subquery in the WHERE of a lateral table function's statement that reads a view or a common table expression is
 * applied before the function when its bare names are the view's or the CTE's own columns, as for a table: the
 * function is called for the rows the condition keeps, and numbers them so (live-verified).
 */
public class LateralInputViewColumnsTest extends MeasuredCellsTestSupport {

    @Test
    public void aViewsAndACtesColumnsAreTheSubquerysOwn() {
        engine.execute("CREATE OR REPLACE TABLE sq (k INT, c VARCHAR)");
        engine.execute("INSERT INTO sq VALUES (1, 'a.b'), (2, 'c.d'), (3, 'e.f')");
        engine.execute("CREATE OR REPLACE VIEW sqv AS SELECT k AS vk FROM sq");
        engine.execute("CREATE OR REPLACE VIEW sqv2 AS SELECT k FROM sq");
        assertCells(new String[][] {
            {"SELECT q.k, s.seq, s.index FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k > (SELECT "
                + "MIN(vk) FROM sqv) ORDER BY 1, s.index",
                "2|1|1 ;; 2|1|2 ;; 3|2|1 ;; 3|2|2"},
            {"SELECT q.k, s.seq, s.index FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k > (WITH m AS "
                + "(SELECT MIN(k) AS mk FROM sq) SELECT mk FROM m) ORDER BY 1, s.index",
                "2|1|1 ;; 2|1|2 ;; 3|2|1 ;; 3|2|2"},
            {"SELECT q.k, s.seq, s.index FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k > (SELECT "
                + "MIN(v.vk) FROM sqv v) ORDER BY 1, s.index",
                "2|1|1 ;; 2|1|2 ;; 3|2|1 ;; 3|2|2"},
            {"SELECT q.k, s.seq, s.index FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k > (SELECT "
                + "MIN(k) FROM (SELECT k FROM sq)) ORDER BY 1, s.index",
                "2|1|1 ;; 2|1|2 ;; 3|2|1 ;; 3|2|2"},
            {"SELECT q.k, s.seq, s.index FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k > (SELECT "
                + "MIN(k) FROM sqv2) ORDER BY 1, s.index",
                "2|1|1 ;; 2|1|2 ;; 3|2|1 ;; 3|2|2"},
            {"WITH m AS (SELECT MIN(k) AS mk FROM sq) SELECT q.k, s.seq, s.index FROM sq q, "
                + "TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k > (SELECT mk FROM m) ORDER BY 1, s.index",
                "2|1|1 ;; 2|1|2 ;; 3|2|1 ;; 3|2|2"},
            {"SELECT q.k, s.seq, s.index FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k IN (SELECT vk "
                + "FROM sqv WHERE vk > 1) ORDER BY 1, s.index",
                "2|1|1 ;; 2|1|2 ;; 3|2|1 ;; 3|2|2"},
            {"SELECT q.k, s.seq, s.index FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE EXISTS (SELECT 1 "
                + "FROM sqv WHERE vk = 3) AND q.k > 1 ORDER BY 1, s.index",
                "2|1|1 ;; 2|1|2 ;; 3|2|1 ;; 3|2|2"},
            {"SELECT q.k, s.seq, s.index FROM sq q, TABLE(SPLIT_TO_TABLE(q.c, '.')) s WHERE q.k > (WITH m AS "
                + "(SELECT k AS mk FROM sq) SELECT MIN(mk) FROM m) ORDER BY 1, s.index",
                "2|1|1 ;; 2|1|2 ;; 3|2|1 ;; 3|2|2"},
        });
    }
}
