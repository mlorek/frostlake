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
 * A MIN or a MAX is tagged by one end of its argument's interval only where the table's statistics answer
 * it: with no grouping key and no filter. Under a GROUP BY of any form, a HAVING or a WHERE each group's
 * answer may be any value the interval holds, so the whole interval tags it, and a SUM over such a column of
 * a derived table is rewritten as one over a column that varies. Every answer is live-verified.
 */
public class GroupedMinMaxIntervalTest extends MeasuredCellsTestSupport {

    @Test
    public void theIntervalAGroupedExtremeKeeps() {
        engine.execute(
            "CREATE OR REPLACE TABLE g (v NUMBER(6,0), k INT)");
        engine.execute(
            "INSERT INTO g VALUES (-1000, 1), (1, 2)");
        engine.execute(
            "CREATE OR REPLACE TABLE t (n NUMBER(5,2), n40 NUMBER(4,0), k INT)");
        engine.execute(
            "INSERT INTO t VALUES (1.5, 17, 1), (2.5, 3, 2)");
        engine.execute(
            "CREATE OR REPLACE TABLE t847 (n NUMBER(5,2), n10 NUMBER(10,2))");
        engine.execute(
            "INSERT INTO t847 VALUES (1.5, 17.25), (2.5, 3.5)");
        engine.execute(
            "CREATE OR REPLACE TABLE rt (n INT, f FLOAT, g VARCHAR(5), n52 NUMBER(5,2), v VARIANT)");
        engine.execute(
            "CREATE OR REPLACE TABLE src (g VARCHAR(5))");
        engine.execute(
            "INSERT INTO src VALUES ('\u00e9\u00e9\u00e9\u00e9\u00e9')");
        assertAnswer(
            "NUMBER(6,0)[SB2] ;; NUMBER(6,0)[SB2]",
            "SELECT SYSTEM$TYPEOF(MAX(v)) FROM g GROUP BY k");
        assertAnswer(
            "NUMBER(6,0)[SB2] ;; NUMBER(6,0)[SB2]",
            "SELECT SYSTEM$TYPEOF(x) FROM (SELECT MAX(v) x FROM g GROUP BY k)");
        assertAnswer(
            "NUMBER(20,0)[SB16] ;; NUMBER(20,0)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(x + 1)) FROM (SELECT MAX(v) x FROM g GROUP BY k)");
        assertAnswer(
            "NUMBER(20,0)[SB16] ;; NUMBER(20,0)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(x + 2)) FROM (SELECT MAX(v) x FROM g GROUP BY k)");
        assertAnswer(
            "NUMBER(24,2)[SB16] ;; NUMBER(24,2)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(x + 1)) FROM (SELECT MAX(n) x FROM t GROUP BY k)");
        assertAnswer(
            "NUMBER(6,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(MAX(v)) FROM g");
        assertAnswer(
            "NUMBER(6,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(x) FROM (SELECT MAX(v) x FROM g)");
        assertAnswer(
            "NUMBER(6,0)[SB2] ;; NUMBER(6,0)[SB2]",
            "SELECT SYSTEM$TYPEOF(MIN(v)) FROM g GROUP BY k");
        assertAnswer(
            "NUMBER(6,0)[SB2]",
            "SELECT SYSTEM$TYPEOF(MIN(v)) FROM g");
        assertAnswer(
            "1|NUMBER(6,0)[SB2] ;; 2|NUMBER(6,0)[SB2]",
            "SELECT k, SYSTEM$TYPEOF(MAX(v)) FROM g GROUP BY ALL ORDER BY k");
        assertAnswer(
            "NUMBER(6,0)[SB2] ;; NUMBER(6,0)[SB2]",
            "SELECT SYSTEM$TYPEOF(MAX(v)) FROM g GROUP BY k HAVING MAX(v) > -5000");
        assertAnswer(
            "NUMBER(6,0)[SB2] ;; NUMBER(6,0)[SB2]",
            "SELECT SYSTEM$TYPEOF(MAX(v) OVER ()) FROM g");
        assertAnswer(
            "NUMBER(6,0)[SB2] ;; NUMBER(6,0)[SB2]",
            "SELECT SYSTEM$TYPEOF(MAX(v) OVER (PARTITION BY k)) FROM g");
        assertAnswer(
            "NUMBER(6,0)[SB2] ;; NUMBER(6,0)[SB2]",
            "SELECT SYSTEM$TYPEOF(MIN(v) OVER (PARTITION BY k)) FROM g");
        assertAnswer(
            "NUMBER(6,0)[SB2]",
            "SELECT SYSTEM$TYPEOF(MAX(v)) FROM g GROUP BY 'x'");
        assertAnswer(
            "NUMBER(6,0)[SB2]",
            "SELECT SYSTEM$TYPEOF(MAX(v)) FROM g WHERE k = 1 GROUP BY k");
        assertAnswer(
            "NUMBER(6,0)[SB2]",
            "SELECT SYSTEM$TYPEOF(MAX(v)) FROM g WHERE k = 1");
        assertAnswer(
            "NUMBER(6,0)[SB2] ;; NUMBER(6,0)[SB2] ;; NUMBER(6,0)[SB2]",
            "SELECT SYSTEM$TYPEOF(MAX(v)) FROM g GROUP BY ROLLUP(k)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected ')'.",
            "SELECT SYSTEM$TYPEOF(MAX(v)) FROM g GROUP BY ()");
        assertAnswer(
            "NUMBER(6,0)[SB2] ;; NUMBER(6,0)[SB2]",
            "SELECT SYSTEM$TYPEOF(MAX(x)) FROM (SELECT MAX(v) x FROM g GROUP BY k)");
        assertAnswer(
            "NUMBER(7,0)[SB2] ;; NUMBER(7,0)[SB2]",
            "SELECT SYSTEM$TYPEOF(x + 1) FROM (SELECT MAX(v) x FROM g GROUP BY k)");
        assertAnswer(
            "NUMBER(18,0)[SB8] ;; NUMBER(18,0)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT MAX(v) x FROM g GROUP BY k)");
        assertAnswer(
            "NUMBER(18,0)[SB8] ;; NUMBER(18,0)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT MIN(v) x FROM g GROUP BY k)");
        assertAnswer(
            "NUMBER(20,0)[SB16] ;; NUMBER(20,0)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(x + 1)) FROM (SELECT MIN(v) x FROM g GROUP BY k)");
        assertAnswer(
            "NUMBER(19,0)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(x + 1)) FROM (SELECT MAX(v) x FROM g)");
        assertAnswer(
            "NUMBER(7,0)[SB2] ;; NUMBER(7,0)[SB2]",
            "SELECT SYSTEM$TYPEOF(MAX(v + 1)) FROM g GROUP BY k");
        assertAnswer(
            "NUMBER(6,0)[SB2] ;; NUMBER(6,0)[SB2]",
            "SELECT SYSTEM$TYPEOF(MAX(ABS(v))) FROM g GROUP BY k");
        assertAnswer(
            "NUMBER(6,0)[SB2] ;; NUMBER(6,0)[SB2]",
            "SELECT SYSTEM$TYPEOF(ANY_VALUE(v)) FROM g GROUP BY k");
        assertAnswer(
            "NUMBER(5,2)[SB2] ;; NUMBER(5,2)[SB2]",
            "SELECT SYSTEM$TYPEOF(MAX(n)) FROM t GROUP BY k");
        assertAnswer(
            "NUMBER(4,0)[SB1] ;; NUMBER(4,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(MAX(n40)) FROM t GROUP BY k");
        assertAnswer(
            "NUMBER(4,0)[SB1] ;; NUMBER(4,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(MIN(n40)) FROM t GROUP BY k");
        assertAnswer(
            "NUMBER(4,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(MAX(n40)) FROM t");
        assertAnswer(
            "NUMBER(4,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(MIN(n40)) FROM t");
        assertAnswer(
            "NUMBER(6,0)[SB2] ;; NUMBER(6,0)[SB2]",
            "SELECT SYSTEM$TYPEOF(MAX(v)) FROM g GROUP BY k, v");
        assertAnswer(
            "NUMBER(6,0)[SB2] ;; NUMBER(6,0)[SB2]",
            "SELECT SYSTEM$TYPEOF(MAX(v)) FROM g GROUP BY v");
        assertAnswer(
            "NUMBER(38,0)[SB1] ;; NUMBER(38,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(MAX(k)) FROM g GROUP BY v");
        assertAnswer(
            "NUMBER(6,0)[SB2]|NUMBER(6,0)[SB2]|NUMBER(18,0)[SB8]|NUMBER(18,0)[SB8] ;; NUMBER(6,0)[SB2]|NUMBER(6,"
                + "0)[SB2]|NUMBER(18,0)[SB8]|NUMBER(18,0)[SB8]",
            "SELECT SYSTEM$TYPEOF(MAX(v)), SYSTEM$TYPEOF(MIN(v)), SYSTEM$TYPEOF(SUM(v)), SYSTEM$TYPEOF(COUNT(v)) "
                + "FROM g GROUP BY k");
        assertAnswer(
            "NUMBER(6,0)[SB2]",
            "SELECT SYSTEM$TYPEOF(m) FROM (SELECT k, MAX(v) m FROM g GROUP BY k) WHERE k = 1");
        assertAnswer(
            "NUMBER(6,0)[SB2]",
            "SELECT SYSTEM$TYPEOF(MAX(v)) FROM g GROUP BY k ORDER BY k LIMIT 1");
        assertAnswer(
            "NUMBER(6,0)[SB2]",
            "SELECT DISTINCT SYSTEM$TYPEOF(MAX(v)) FROM g GROUP BY k");
    }
}
