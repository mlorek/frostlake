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
 * A VALUES clause carries no statistics: an exact number read from it is tagged by its declared precision
 * alone — (150), (25000) is NUMBER(5,0)[SB4] — and a SUM over a shifted column of it is rewritten whatever its
 * rows hold, one row or the same value twice too; a subquery or a WITH clause over the clause is merged into the
 * query over it, and an alias list renames the clause's columns and nothing else (live-verified).
 */
public class ValuesRelationStatisticsTest extends MeasuredCellsTestSupport {

    @Test
    public void aColumnIsToldByItsType() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        assertAnswer(
            "NUMBER(2,1)[SB1] ;; NUMBER(2,1)[SB1]",
            "SELECT SYSTEM$TYPEOF(column1) FROM VALUES (1.5), (2.5)");
        assertAnswer(
            "NUMBER(3,1)[SB2] ;; NUMBER(3,1)[SB2]",
            "SELECT SYSTEM$TYPEOF(column1 * 2) FROM VALUES (1.5), (2.5)");
        assertAnswer(
            "NUMBER(2,0)[SB1] ;; NUMBER(2,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(column1 + column2) FROM VALUES (1, 2), (3, 4)");
        assertAnswer(
            "NUMBER(4,0)[SB2] ;; NUMBER(4,0)[SB2]",
            "SELECT SYSTEM$TYPEOF(column1 + 100) FROM VALUES (1), (2)");
        assertAnswer(
            "NUMBER(2,1)[SB1] ;; NUMBER(2,1)[SB1]",
            "SELECT SYSTEM$TYPEOF(-column1) FROM VALUES (1.5), (2.5)");
        assertAnswer(
            "NUMBER(6,0)[SB4] ;; NUMBER(6,0)[SB4]",
            "SELECT SYSTEM$TYPEOF(column1 + 1) FROM VALUES (150), (25000)");
        assertAnswer(
            "NUMBER(5,0)[SB4] ;; NUMBER(5,0)[SB4]",
            "SELECT SYSTEM$TYPEOF(column1) FROM VALUES (150), (25000)");
        assertAnswer(
            "NUMBER(4,1)[SB2] ;; NUMBER(4,1)[SB2]",
            "SELECT SYSTEM$TYPEOF(v + 1) FROM (VALUES (1.5), (2.5)) AS vv (v)");
        assertAnswer(
            "NUMBER(4,1)[SB2] ;; NUMBER(4,1)[SB2]",
            "SELECT SYSTEM$TYPEOF(x) FROM (SELECT column1 + 1 x FROM VALUES (1.5), (2.5))");
        assertAnswer(
            "NUMBER(4,1)[SB2]",
            "SELECT SYSTEM$TYPEOF(column1 + 1) FROM VALUES (1.5)");
        assertAnswer(
            "NUMBER(4,1)[SB2] ;; NUMBER(4,1)[SB2]",
            "SELECT SYSTEM$TYPEOF(column1 + 1) FROM VALUES (1.5), (NULL)");
        assertAnswer(
            "NUMBER(1,0)[SB1] ;; NUMBER(1,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(column1) FROM VALUES (1), (NULL)");
        assertAnswer(
            "NUMBER(10,0)[SB8] ;; NUMBER(10,0)[SB8]",
            "SELECT SYSTEM$TYPEOF(column1 * column1) FROM VALUES (150), (25000)");
        assertAnswer(
            "NUMBER(2,0)[SB1] ;; NUMBER(2,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(column1 - 1) FROM VALUES (1), (2)");
        assertAnswer(
            "NUMBER(3,0)[SB2] ;; NUMBER(3,0)[SB2]",
            "SELECT SYSTEM$TYPEOF(column1 + 1) FROM VALUES (99), (1)");
        assertAnswer(
            "NUMBER(2,0)[SB1] ;; NUMBER(2,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(column1 + 1) FROM VALUES (9), (1)");
        assertAnswer(
            "NUMBER(7,6)[SB4] ;; NUMBER(7,6)[SB4]",
            "SELECT SYSTEM$TYPEOF(column1 / 2) FROM VALUES (1), (2)");
        assertAnswer(
            "NUMBER(3,1)[SB2] ;; NUMBER(3,1)[SB2]",
            "SELECT SYSTEM$TYPEOF(ABS(column1)) FROM VALUES (-1.5), (2.5)");
        assertAnswer(
            "NUMBER(4,0)[SB2] ;; NUMBER(4,0)[SB2]",
            "SELECT SYSTEM$TYPEOF(column1 + 1) FROM VALUES (127), (1)");
        assertAnswer(
            "NUMBER(4,0)[SB2] ;; NUMBER(4,0)[SB2]",
            "SELECT SYSTEM$TYPEOF(column1 + 1) FROM VALUES (128), (1)");
        assertAnswer(
            "NUMBER(3,0)[SB2] ;; NUMBER(3,0)[SB2]",
            "SELECT SYSTEM$TYPEOF(column1) FROM VALUES (127), (1)");
        assertAnswer(
            "NUMBER(3,0)[SB2] ;; NUMBER(3,0)[SB2]",
            "SELECT SYSTEM$TYPEOF(column1) FROM VALUES (128), (1)");
        assertAnswer(
            "NUMBER(6,0)[SB4] ;; NUMBER(6,0)[SB4]",
            "SELECT SYSTEM$TYPEOF(column1 + 1) FROM VALUES (32767), (1)");
        assertAnswer(
            "NUMBER(3,1)[SB2] ;; NUMBER(3,1)[SB2]",
            "SELECT SYSTEM$TYPEOF(column1 + column1) FROM VALUES (1.5), (2.5)");
    }

    @Test
    public void aShiftedSumIsRewritten() {
        answer(
            "CREATE OR REPLACE TABLE t (n NUMBER(5,2), n40 NUMBER(4,0), k INT)");
        answer(
            "INSERT INTO t VALUES (1.5, 17, 1), (2.5, 3, 2)");
        answer(
            "CREATE OR REPLACE TABLE d (k INT)");
        answer(
            "INSERT INTO d VALUES (1), (2)");
        assertAnswer(
            "NUMBER(22,1)[SB16] ;; NUMBER(22,1)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT column1 + 1 x FROM VALUES (1.5), (2.5))");
        assertAnswer(
            "NUMBER(20,0)[SB16] ;; NUMBER(20,0)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT column1 + 1 x FROM VALUES (1), (2))");
        assertAnswer(
            "NUMBER(22,1)[SB16] ;; NUMBER(22,1)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(v + 1)) FROM (VALUES (1.5), (2.5)) AS vv (v)");
        assertAnswer(
            "NUMBER(22,1)[SB16] ;; NUMBER(22,1)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(column1 + 1)) FROM VALUES (1.5), (1.5)");
        assertAnswer(
            "NUMBER(22,1)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(column1 + 1)) FROM VALUES (1.5)");
        assertAnswer(
            "NUMBER(21,1)[SB16] ;; NUMBER(21,1)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT column1 + 0.5 x FROM VALUES (1.5), (2.5))");
        assertAnswer(
            "NUMBER(14,1)[SB8] ;; NUMBER(14,1)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(column1)) FROM VALUES (1.5), (2.5)");
        assertAnswer(
            "NUMBER(15,1)[SB8] ;; NUMBER(15,1)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(column1 * 2)) FROM VALUES (1.5), (2.5)");
        assertAnswer(
            "NUMBER(14,0)[SB8] ;; NUMBER(14,0)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(column1 + column2)) FROM VALUES (1, 2), (3, 4)");
        assertAnswer(
            "NUMBER(14,1)[SB8] ;; NUMBER(14,1)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT column1 x FROM VALUES (1.5), (2.5))");
        assertAnswer(
            "NUMBER(22,1)[SB16] ;; NUMBER(22,1)[SB16]",
            "WITH c AS (SELECT column1 + 1 x FROM VALUES (1.5), (2.5)) SELECT SYSTEM$TYPEOF(SUM(x)) FROM c");
        assertAnswer(
            "NUMBER(20,0)[SB16] ;; NUMBER(20,0)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(v + 1)) FROM (VALUES (1), (2)) AS vv (v)");
        assertAnswer(
            "NUMBER(14,1)[SB8] ;; NUMBER(14,1)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(v)) FROM (VALUES (1.5), (2.5)) AS vv (v)");
        assertAnswer(
            "6.0",
            "SELECT SUM(x) FROM (SELECT column1 + 1 x FROM VALUES (1.5), (2.5))");
        assertAnswer(
            "NUMBER(22,1)[SB16] ;; NUMBER(22,1)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(column1 - 1)) FROM VALUES (1.5), (2.5)");
        assertAnswer(
            "NUMBER(22,1)[SB16] ;; NUMBER(22,1)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(1 + column1)) FROM VALUES (1.5), (2.5)");
        assertAnswer(
            "NUMBER(22,7)[SB16] ;; NUMBER(22,7)[SB16]",
            "SELECT SYSTEM$TYPEOF(AVG(column1 + 1)) FROM VALUES (1.5), (2.5)");
        assertAnswer(
            "NUMBER(22,1)[SB16] ;; NUMBER(22,1)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(column1 + 1)) FROM VALUES (1.5), (NULL)");
        assertAnswer(
            "NUMBER(4,1)[SB2]|NUMBER(4,1)[SB2] ;; NUMBER(4,1)[SB2]|NUMBER(4,1)[SB2]",
            "SELECT SYSTEM$TYPEOF(MAX(column1 + 1)), SYSTEM$TYPEOF(MIN(column1 + 1)) FROM VALUES (1.5), (2.5)");
        assertAnswer(
            "NUMBER(4,1)[SB2] ;; NUMBER(4,1)[SB2]",
            "SELECT SYSTEM$TYPEOF(column1 + 1) FROM VALUES (1.5), (2.5)");
        assertAnswer(
            "NUMBER(20,0)[SB16] ;; NUMBER(20,0)[SB16] ;; NUMBER(20,0)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(column1 + 1)) FROM VALUES (1), (2), (3)");
        assertAnswer(
            "NUMBER(22,1)[SB16] ;; NUMBER(22,1)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(column1 + 10)) FROM VALUES (1.5), (2.5)");
        assertAnswer(
            "NUMBER(17,2)[SB8] ;; NUMBER(17,2)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(column1 + 1.25)) FROM VALUES (1.5), (2.5)");
        assertAnswer(
            "NUMBER(22,1)[SB16] ;; NUMBER(22,1)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(x + 1)) FROM (SELECT column1 x FROM VALUES (1.5), (2.5))");
        assertAnswer(
            "NUMBER(22,1)[SB16] ;; NUMBER(22,1)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT v + 1 x FROM (VALUES (1.5), (2.5)) AS vv (v))");
        assertAnswer(
            "NUMBER(22,1)[SB16] ;; NUMBER(22,1)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(column1 + 1)) FROM (VALUES (1.5), (2.5))");
        assertAnswer(
            "NUMBER(22,1)[SB16] ;; NUMBER(22,1)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(column1 + 1)) FROM VALUES (1.5), (2.5) WHERE column1 > 0");
        assertAnswer(
            "NUMBER(22,1)[SB16] ;; NUMBER(22,1)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(a.column1 + 1)) FROM (VALUES (1.5), (2.5)) a");
        assertAnswer(
            "NUMBER(20,0)[SB16] ;; NUMBER(20,0)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(column1 + 1)) FROM VALUES (150), (25000)");
        assertAnswer(
            "FLOAT[DOUBLE] ;; FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(SUM(column1 + 1)) FROM VALUES (1.5::FLOAT), (2.5::FLOAT)");
        assertAnswer(
            "NUMBER(31,5)[SB16] ;; NUMBER(31,5)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(column1 + 1)) FROM VALUES ('1.5'), ('2.5')");
    }

    @Test
    public void throughRelationsOverTheClause() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        assertAnswer(
            "NUMBER(17,0)[SB8] ;; NUMBER(17,0)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(column1)) FROM VALUES (150), (25000)");
        assertAnswer(
            "NUMBER(22,1)[SB16] ;; NUMBER(22,1)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(column1 + 1)) FROM VALUES (1.5), (2.5)");
        assertAnswer(
            "NUMBER(2,1)[SB1] ;; NUMBER(2,1)[SB1]",
            "SELECT SYSTEM$TYPEOF(MAX(column1)) FROM VALUES (1.5), (2.5)");
        assertAnswer(
            "NUMBER(18,0)[SB8] ;; NUMBER(18,0)[SB8]",
            "SELECT SYSTEM$TYPEOF(COUNT(column1)) FROM VALUES (1.5), (2.5)");
        assertAnswer(
            "NUMBER(15,1)[SB8] ;; NUMBER(15,1)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT column1 * 2 x FROM VALUES (1.5), (2.5))");
        assertAnswer(
            "NUMBER(14,0)[SB8] ;; NUMBER(14,0)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT column1 + column2 x FROM VALUES (1, 2), (3, 4))");
        assertAnswer(
            "NUMBER(17,1)[SB8] ;; NUMBER(17,1)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(x + 1)) FROM (SELECT column1 + 1 x FROM VALUES (1.5), (2.5))");
        assertAnswer(
            "NUMBER(22,1)[SB16] ;; NUMBER(22,1)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT column1 + 1 x FROM VALUES (1.5), (2.5)) WHERE x > 0");
        assertAnswer(
            "NUMBER(22,1)[SB16] ;; NUMBER(22,1)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT column1 + 1 x FROM (VALUES (1.5), (2.5)))");
        assertAnswer(
            "NUMBER(22,1)[SB16] ;; NUMBER(22,1)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(a.x)) FROM (SELECT column1 + 1 x FROM VALUES (1.5), (2.5)) a");
        assertAnswer(
            "NUMBER(16,1)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT column1 + 1 x FROM VALUES (1.5), (2.5) LIMIT 1)");
        assertAnswer(
            "NUMBER(16,1)[SB8] ;; NUMBER(16,1)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT DISTINCT column1 + 1 x FROM VALUES (1.5), (2.5))");
        assertAnswer(
            "NUMBER(22,1)[SB16]|NUMBER(20,0)[SB16] ;; NUMBER(22,1)[SB16]|NUMBER(20,0)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(v + 1)), SYSTEM$TYPEOF(SUM(w + 1)) FROM (VALUES (1.5, 2), (2.5, 3)) AS vv "
                + "(v, w)");
        assertAnswer(
            "NUMBER(22,1)[SB16] ;; NUMBER(22,1)[SB16]",
            "WITH c (v) AS (SELECT * FROM VALUES (1.5), (2.5)) SELECT SYSTEM$TYPEOF(SUM(v + 1)) FROM c");
        assertAnswer(
            "NUMBER(22,1)[SB16] ;; NUMBER(22,1)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(column1 + 1)) FROM (SELECT * FROM VALUES (1.5), (2.5))");
        assertAnswer(
            "NUMBER(22,1)[SB16] ;; NUMBER(22,1)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(n + 1)) FROM (SELECT column1 n FROM VALUES (1.5), (2.5))");
    }

    @Test
    public void anAggregatesTypeInAScalarSubquery() {
        answer(
            "CREATE OR REPLACE TABLE t (n NUMBER(5,2), n40 NUMBER(4,0), k INT)");
        answer(
            "INSERT INTO t VALUES (1.5, 17, 1), (2.5, 3, 2)");
        answer(
            "CREATE OR REPLACE TABLE d (k INT)");
        answer(
            "INSERT INTO d VALUES (1), (2)");
        assertAnswer(
            "NUMBER(18,0)[SB1]",
            "SELECT (SELECT SYSTEM$TYPEOF(COUNT(*)) FROM t)");
        assertAnswer(
            "NUMBER(5,2)[SB2]",
            "SELECT (SELECT SYSTEM$TYPEOF(MAX(n)) FROM t)");
        assertAnswer(
            "6.00",
            "SELECT (SELECT SUM(n + 1) FROM t)");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM (SELECT (SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 1 x FROM t)))");
        assertAnswer(
            "NUMBER(17,2)[SB8]",
            "SELECT (SELECT SYSTEM$TYPEOF(SUM(n)) FROM t WHERE k = 1)");
        assertAnswer(
            "REFUSED Single-row subquery returns more than one row.",
            "SELECT (SELECT SYSTEM$TYPEOF(SUM(n)) FROM t GROUP BY k)");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM (SELECT (SELECT SYSTEM$TYPEOF(SUM(n + 1)) FROM t))");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(n + 1)) FROM t");
        assertAnswer(
            "NUMBER(1,0)[SB1]",
            "SELECT (SELECT SYSTEM$TYPEOF(1))");
        assertAnswer(
            "REFUSED Single-row subquery returns more than one row.",
            "SELECT (SELECT SYSTEM$TYPEOF(n) FROM t)");
        assertAnswer(
            "NUMBER(6,2)[SB2]",
            "SELECT (SELECT SYSTEM$TYPEOF(MIN(n + 1)) FROM t)");
        assertAnswer(
            "NUMBER(18,0)[SB1]",
            "SELECT (SELECT SYSTEM$TYPEOF(COUNT(n)) FROM t)");
    }
}
