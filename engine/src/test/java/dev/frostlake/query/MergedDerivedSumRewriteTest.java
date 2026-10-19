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
 * A SUM over a column of a derived relation the plan merges is rewritten as the SUM over the item the column
 * stands for: an item that qualifies its column with the alias its relation is read under, a CTE's column
 * named by its column list, an unqualified column of a relation beside another in a join, a shift by a
 * session variable, and a shift by a derived column that stands for a constant. Every answer is
 * live-verified.
 */
public class MergedDerivedSumRewriteTest extends MeasuredCellsTestSupport {

    @Test
    public void theShiftsAMergedColumnStandsFor() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (n NUMBER(5,2), n40 NUMBER(4,0), k INT)");
        engine.execute(
            "INSERT INTO t VALUES (1.5, 17, 1), (2.5, 3, 2)");
        engine.execute(
            "CREATE OR REPLACE TABLE d (k INT)");
        engine.execute(
            "INSERT INTO d VALUES (1), (2)");
        engine.execute(
            "CREATE OR REPLACE TABLE rt (n INT, f FLOAT, g VARCHAR(5), n52 NUMBER(5,2), v VARIANT)");
        engine.execute(
            "CREATE OR REPLACE VIEW v2 (x) AS SELECT n + 1 FROM t");
        engine.execute(
            "SET one = 1");
        engine.execute(
            "SET half = 0.5");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT a.n + 1 x FROM t a)");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT a.n + 1 x FROM t AS a WHERE a.k > 0)");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "WITH c (x) AS (SELECT n + 1 FROM t) SELECT SYSTEM$TYPEOF(SUM(x)) FROM c");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 2 x, k FROM t) dd JOIN t ON dd.k = t.k");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 1 x FROM t) CROSS JOIN d");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + $one x FROM t)");
        assertAnswer(
            "NUMBER(24,2)[SB8] ;; NUMBER(24,2)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(n + c)) FROM (SELECT n, 5 c FROM t)");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 1 x FROM t)");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT t.n + 1 x FROM t)");
        assertAnswer(
            "NUMBER(20,0)[SB16] ;; NUMBER(20,0)[SB16]",
            "WITH c AS (SELECT n40 + 1 x FROM t) SELECT SYSTEM$TYPEOF(SUM(x)) FROM c");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM v2");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(dd.x)) FROM (SELECT n + 1 x FROM t) dd CROSS JOIN d");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(a.x)) FROM (SELECT n + 1 x FROM t) a");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT b.n + 1 x FROM t b) a");
        assertAnswer(
            "NUMBER(20,0)[SB16] ;; NUMBER(20,0)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT a.n40 + 1 x FROM t a)");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT 1 + a.n x FROM t a)");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT a.n - 1 x FROM t a)");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(a.n + 1)) FROM t a");
        assertAnswer(
            "NUMBER(17,2)[SB8] ;; NUMBER(17,2)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT a.n x FROM t a)");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(x + 1)) FROM (SELECT a.n x FROM t a)");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "WITH c (x, y) AS (SELECT n + 1, k FROM t) SELECT SYSTEM$TYPEOF(SUM(x)) FROM c");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "WITH c (x) AS (SELECT n FROM t) SELECT SYSTEM$TYPEOF(SUM(x + 1)) FROM c");
        assertAnswer(
            "NUMBER(17,2)[SB8] ;; NUMBER(17,2)[SB8]",
            "WITH c (x) AS (SELECT n FROM t) SELECT SYSTEM$TYPEOF(SUM(x)) FROM c");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "WITH c AS (SELECT n + 1 AS x FROM t) SELECT SYSTEM$TYPEOF(SUM(c.x)) FROM c");
        assertAnswer(
            "NUMBER(20,0)[SB16] ;; NUMBER(20,0)[SB16]",
            "WITH c (x) AS (SELECT n40 + 1 FROM t) SELECT SYSTEM$TYPEOF(SUM(x)) FROM c");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "WITH c (x) AS (SELECT n + 1 FROM t), e AS (SELECT x FROM c) SELECT SYSTEM$TYPEOF(SUM(x)) FROM e");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 2 x, k FROM t) dd JOIN d ON dd.k = d.k");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(n + 1)) FROM t JOIN d ON t.k = d.k");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(t.n + 1)) FROM t CROSS JOIN d");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(n + 1)) FROM t CROSS JOIN d");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM d CROSS JOIN (SELECT n + 1 x FROM t)");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 1 x FROM t), d");
        assertAnswer(
            "NUMBER(20,0)[SB16] ;; NUMBER(20,0)[SB16] ;; NUMBER(20,0)[SB16] ;; NUMBER(20,0)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(n40 + 1)) FROM t, d");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 1 x FROM t) a JOIN (SELECT k FROM d) b ON TRUE");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 1 x, k FROM t) dd LEFT JOIN d ON dd.k = d.k");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 1 x FROM t) NATURAL JOIN d");
        assertAnswer(
            "NUMBER(22,1)[SB16] ;; NUMBER(22,1)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(column1 + 1)) FROM VALUES (1.5), (2.5)");
        assertAnswer(
            "NUMBER(14,1)[SB8] ;; NUMBER(14,1)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT column1 x FROM VALUES (1.5), (2.5))");
        assertAnswer(
            "NUMBER(14,1)[SB8] ;; NUMBER(14,1)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(column1)) FROM VALUES (1.5), (2.5)");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(n + $one)) FROM t");
        assertAnswer(
            "NUMBER(24,2)[SB8] ;; NUMBER(24,2)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(n + $half)) FROM t");
        assertAnswer(
            "NUMBER(24,2)[SB8] ;; NUMBER(24,2)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + $half x FROM t)");
        assertAnswer(
            "NUMBER(20,0)[SB16] ;; NUMBER(20,0)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(n40 + $one)) FROM t");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM($one + n)) FROM t");
        assertAnswer(
            "NUMBER(1,0)[SB1]|NUMBER(2,1)[SB1]|NUMBER(6,2)[SB2]",
            "SELECT SYSTEM$TYPEOF($one), SYSTEM$TYPEOF($half), SYSTEM$TYPEOF(n + $one) FROM t LIMIT 1");
        assertAnswer(
            "NUMBER(24,2)[SB8] ;; NUMBER(24,2)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(c + n)) FROM (SELECT n, 5 c FROM t)");
        assertAnswer(
            "NUMBER(24,2)[SB8] ;; NUMBER(24,2)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(n - c)) FROM (SELECT n, 0.5 c FROM t)");
        assertAnswer(
            "NUMBER(38,2)[SB8] ;; NUMBER(38,2)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(n + c)) FROM (SELECT n, k c FROM t)");
        assertAnswer(
            "NUMBER(24,2)[SB8] ;; NUMBER(24,2)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + c x FROM (SELECT n, 5 c FROM t))");
        assertAnswer(
            "NUMBER(20,0)[SB8] ;; NUMBER(20,0)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(n40 + c)) FROM (SELECT n40, 5 c FROM t)");
        assertAnswer(
            "NUMBER(29,2)[SB8] ;; NUMBER(29,2)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(n + c)) FROM (SELECT n, 5::NUMBER(10,2) c FROM t)");
        assertAnswer(
            "NUMBER(22,2)[SB8] ;; NUMBER(22,2)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(n + c)) FROM (SELECT n, 0.05 c FROM t)");
        assertAnswer(
            "NUMBER(13,0)[SB8] ;; NUMBER(13,0)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(c)) FROM (SELECT 5 c FROM t)");
        assertAnswer(
            "NUMBER(24,2)[SB8] ;; NUMBER(24,2)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(n + 5)) FROM t");
        assertAnswer(
            "NUMBER(6,2)[SB2]|NUMBER(1,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(n + c), SYSTEM$TYPEOF(c) FROM (SELECT n, 5 c FROM t) LIMIT 1");
        assertAnswer(
            "6.00",
            "SELECT (SELECT SUM(n + 1) FROM t)");
        assertAnswer(
            "NUMBER(18,2)[SB16]",
            "SELECT SYSTEM$TYPEOF((SELECT SUM(n + 1) FROM t))");
        assertAnswer(
            "NUMBER(18,0)[SB1]",
            "SELECT (SELECT SYSTEM$TYPEOF(COUNT(*)) FROM t)");
        assertAnswer(
            "NUMBER(5,2)[SB2]",
            "SELECT (SELECT SYSTEM$TYPEOF(MAX(n)) FROM t)");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM (SELECT (SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 1 x FROM t)))");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "SELECT * FROM (SELECT SYSTEM$TYPEOF(SUM(x)) y FROM (SELECT n + 1 x FROM t))");
    }
}
