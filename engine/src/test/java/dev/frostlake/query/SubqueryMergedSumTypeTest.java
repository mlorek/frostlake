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
 * A derived relation that reads nothing of the row around it is merged into a scalar subquery as it is into a query
 * of its own: {@code SUM(x)} over {@code (SELECT n + 1 x FROM t)} inside one is typed as {@code SUM(n + 1)} is,
 * NUMBER(24,2)[SB16] over a NUMBER(5,2), and so is the subquery's value (live-verified).
 */
public class SubqueryMergedSumTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void aMergedColumnKeepsItsRewrittenSum() {
        engine.execute("CREATE OR REPLACE TABLE t (n NUMBER(5,2), n40 NUMBER(4,0), k INT)");
        engine.execute("INSERT INTO t VALUES (1.5, 17, 1), (2.5, 3, 2)");
        engine.execute("CREATE OR REPLACE TABLE d (k INT)");
        engine.execute("INSERT INTO d VALUES (1), (2)");
        assertCells(new String[][] {
            {"SELECT (SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 1 x FROM t) LIMIT 1)", "NUMBER(24,2)[SB16]"},
            {"SELECT (SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n40 + 1 x FROM t) LIMIT 1)", "NUMBER(20,0)[SB16]"},
            {"SELECT (SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n x FROM t) LIMIT 1)", "NUMBER(17,2)[SB8]"},
            {"SELECT (SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT k + 1 x FROM t) LIMIT 1)", "NUMBER(38,0)[SB16]"},
            {"SELECT (SELECT SYSTEM$TYPEOF(MAX(x)) FROM (SELECT n + 1 x FROM t) LIMIT 1)", "NUMBER(6,2)[SB2]"},
            {"SELECT (SELECT SYSTEM$TYPEOF(AVG(x)) FROM (SELECT n + 1 x FROM t) LIMIT 1)", "NUMBER(24,8)[SB16]"},
            {"SELECT (SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 1 x FROM t WHERE k > 0) LIMIT 1)",
                "NUMBER(24,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 1 x FROM t)", "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n40 + 1 x FROM t)", "NUMBER(20,0)[SB16] ;; NUMBER(20,0)[SB16]"},
            {"SELECT (SELECT SUM(x) FROM (SELECT n + 1 x FROM t))", "6.00"},
            {"SELECT k, (SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 1 x FROM t) LIMIT 1) FROM d ORDER BY k",
                "1|NUMBER(24,2)[SB16] ;; 2|NUMBER(24,2)[SB16]"},
            {"SELECT k FROM d WHERE (SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 1 x FROM t) LIMIT 1) = "
                + "'NUMBER(24,2)[SB16]' ORDER BY k",
                "1 ;; 2"},
            {"SELECT SYSTEM$TYPEOF((SELECT SUM(x) FROM (SELECT n + 1 x FROM t)))", "NUMBER(18,2)[SB16]"},
            {"SELECT (SELECT SYSTEM$TYPEOF(s) FROM (SELECT SUM(x) s FROM (SELECT n + 1 x FROM t)))",
                "NUMBER(18,2)[SB16]"},
            {"SELECT x FROM (SELECT (SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 1 x FROM t) LIMIT 1) x)",
                "NUMBER(24,2)[SB16]"},
            {"WITH c AS (SELECT n + 1 x FROM t) SELECT (SELECT SYSTEM$TYPEOF(SUM(x)) FROM c LIMIT 1)",
                "NUMBER(24,2)[SB16]"},
        });
    }

    @Test
    public void theSubquerysValueIsTaggedSo() {
        engine.execute("CREATE OR REPLACE TABLE t (n NUMBER(5,2), n40 NUMBER(4,0), k INT)");
        engine.execute("INSERT INTO t VALUES (1.5, 17, 1), (2.5, 3, 2)");
        assertCells(new String[][] {
            {"SELECT (SELECT SYSTEM$TYPEOF(SUM(n + 1)) FROM t LIMIT 1)", "NUMBER(24,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n + 1)) FROM t", "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]"},
            {"SELECT (SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n * 2 x FROM t) LIMIT 1)", "NUMBER(18,2)[SB8]"},
            {"SELECT (SELECT SYSTEM$TYPEOF(MIN(x)) FROM (SELECT n + 1 x FROM t) LIMIT 1)", "NUMBER(6,2)[SB2]"},
            {"SELECT (SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 1 x FROM t) WHERE x > 0 LIMIT 1)",
                "NUMBER(24,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT k + 1 x FROM t)", "NUMBER(38,0)[SB16] ;; NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF((SELECT SUM(n) FROM t))", "NUMBER(17,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF((SELECT SUM(n + 1) FROM t))", "NUMBER(18,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF((SELECT MAX(n) FROM t))", "NUMBER(5,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF((SELECT 1))", "NUMBER(1,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF((SELECT n FROM t ORDER BY k LIMIT 1))", "NUMBER(5,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF((SELECT SUM(k) FROM t))", "NUMBER(38,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF((SELECT COUNT(*) FROM t))", "NUMBER(18,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF((SELECT MAX(k) FROM t))", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF((SELECT SUM(x) FROM (SELECT k + 1 x FROM t)))", "NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(k + 1)) FROM t", "NUMBER(38,0)[SB16] ;; NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF((SELECT SUM(n) FROM t)) FROM t", "NUMBER(17,2)[SB8] ;; NUMBER(17,2)[SB8]"},
            {"SELECT (SELECT SYSTEM$TYPEOF(SUM(k)) FROM t LIMIT 1)", "NUMBER(38,0)[SB8]"},
            {"SELECT (SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT k x FROM t) LIMIT 1)", "NUMBER(38,0)[SB8]"},
            {"SELECT (SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n40 x FROM t) LIMIT 1)", "NUMBER(16,0)[SB8]"},
            {"SELECT (SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n40 + 1 x FROM t WHERE k = 1) LIMIT 1)",
                "NUMBER(20,0)[SB16]"},
            {"SELECT (SELECT SUM(x) FROM (SELECT n + 1 x FROM t) LIMIT 1)", "6.00"},
            {"SELECT 1 WHERE EXISTS (SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 1 x FROM t))", "1"},
            {"SELECT ARRAY_AGG(s) FROM (SELECT (SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 1 x FROM t) LIMIT 1) s)",
                "[\"NUMBER(24,2)[SB16]\"]"},
        });
    }
}
