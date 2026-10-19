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

package dev.frostlake.functions;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * RANK and DENSE_RANK beside a ROWS frame rank the current row among the frame's rows: one plus the frame's rows
 * that sort before it, DENSE_RANK counting the distinct keys among them, and the partition's rank where the frame
 * does not hold the current row, under PARTITION BY, a descending order and several keys alike (all
 * live-verified).
 */
public class FramedRankingTest extends MeasuredCellsTestSupport {

    @Test
    public void aRankingFunctionRanksWithinItsRowsFrame() {
        engine.execute("CREATE OR REPLACE TABLE w (g VARCHAR, v NUMBER(5,1))");
        engine.execute("INSERT INTO w VALUES ('a', 1), ('a', 2), ('b', 2), ('b', 4), ('a', 10), ('b', NULL)");
        engine.execute("CREATE OR REPLACE TABLE w2 (v INT)");
        engine.execute("INSERT INTO w2 VALUES (1), (1), (1), (2), (2), (3)");
        assertAnswer("1.0|1|1 ;; 2.0|1|1 ;; 2.0|2|2 ;; 4.0|2|2 ;; 10.0|2|2 ;; null|2|2",
            "SELECT v, RANK() OVER (ORDER BY v ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) AS r, DENSE_RANK() OVER "
                + "(ORDER BY v ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) AS d FROM w ORDER BY v NULLS LAST, r, d");
        assertAnswer("1.0|1|1 ;; 2.0|1|1 ;; 2.0|2|2 ;; 4.0|2|2 ;; 10.0|2|2 ;; null|2|2",
            "SELECT v, RANK() OVER (ORDER BY v ROWS 1 PRECEDING) AS r, DENSE_RANK() OVER (ORDER BY v ROWS 1 "
                + "PRECEDING) AS d FROM w ORDER BY v NULLS LAST, r, d");
        assertAnswer("1.0|1|1 ;; 2.0|1|1 ;; 2.0|1|1 ;; 4.0|1|1 ;; 10.0|1|1 ;; null|1|1",
            "SELECT v, RANK() OVER (ORDER BY v ROWS BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING) AS r, DENSE_RANK() "
                + "OVER (ORDER BY v ROWS BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING) AS d FROM w ORDER BY v NULLS "
                + "LAST, r, d");
        assertAnswer("1.0|1|1 ;; 2.0|1|1 ;; 2.0|1|1 ;; 4.0|1|1 ;; 10.0|1|1 ;; null|1|1",
            "SELECT v, RANK() OVER (ORDER BY v ROWS CURRENT ROW) AS r, DENSE_RANK() OVER (ORDER BY v ROWS CURRENT "
                + "ROW) AS d FROM w ORDER BY v NULLS LAST, r, d");
        assertAnswer("1.0|1|1 ;; 2.0|1|1 ;; 2.0|1|1 ;; 4.0|1|1 ;; 10.0|1|1 ;; null|1|1",
            "SELECT v, RANK() OVER (ORDER BY v ROWS BETWEEN 0 PRECEDING AND CURRENT ROW) AS r, DENSE_RANK() OVER "
                + "(ORDER BY v ROWS BETWEEN 0 PRECEDING AND CURRENT ROW) AS d FROM w ORDER BY v NULLS LAST, r, d");
        assertAnswer("1.0|1|1 ;; 2.0|1|1 ;; 2.0|2|2 ;; 4.0|2|2 ;; 10.0|2|2 ;; null|2|2",
            "SELECT v, RANK() OVER (ORDER BY v ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING) AS r, DENSE_RANK() OVER "
                + "(ORDER BY v ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING) AS d FROM w ORDER BY v NULLS LAST, r, d");
        assertAnswer("1.0|1|1 ;; 2.0|1|1 ;; 2.0|2|2 ;; 4.0|2|2 ;; 10.0|2|2 ;; null|2|2",
            "SELECT v, RANK() OVER (ORDER BY v ROWS BETWEEN 1 PRECEDING AND UNBOUNDED FOLLOWING) AS r, DENSE_RANK() "
                + "OVER (ORDER BY v ROWS BETWEEN 1 PRECEDING AND UNBOUNDED FOLLOWING) AS d FROM w ORDER BY v NULLS "
                + "LAST, r, d");
        assertAnswer("1.0|1|1 ;; 2.0|2|2 ;; 2.0|2|2 ;; 4.0|4|3 ;; 10.0|5|4 ;; null|6|5",
            "SELECT v, RANK() OVER (ORDER BY v ROWS BETWEEN 1 FOLLOWING AND 2 FOLLOWING) AS r, DENSE_RANK() OVER "
                + "(ORDER BY v ROWS BETWEEN 1 FOLLOWING AND 2 FOLLOWING) AS d FROM w ORDER BY v NULLS LAST, r, d");
        assertAnswer("1.0|1|1 ;; 2.0|2|2 ;; 2.0|2|2 ;; 4.0|4|3 ;; 10.0|5|4 ;; null|6|5",
            "SELECT v, RANK() OVER (ORDER BY v ROWS BETWEEN 2 PRECEDING AND 1 PRECEDING) AS r, DENSE_RANK() OVER "
                + "(ORDER BY v ROWS BETWEEN 2 PRECEDING AND 1 PRECEDING) AS d FROM w ORDER BY v NULLS LAST, r, d");
        assertAnswer("1.0|1|1 ;; 2.0|2|2 ;; 2.0|2|2 ;; 4.0|4|3 ;; 10.0|5|4 ;; null|6|5",
            "SELECT v, RANK() OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND 1 FOLLOWING) AS r, DENSE_RANK() "
                + "OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND 1 FOLLOWING) AS d FROM w ORDER BY v NULLS "
                + "LAST, r, d");
        assertAnswer("1.0|1|1 ;; 2.0|2|2 ;; 2.0|2|2 ;; 4.0|4|3 ;; 10.0|5|4 ;; null|6|5",
            "SELECT v, RANK() OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS r, DENSE_RANK() "
                + "OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS d FROM w ORDER BY v NULLS "
                + "LAST, r, d");
        assertAnswer("1.0|1|1 ;; 2.0|2|2 ;; 2.0|2|2 ;; 4.0|4|3 ;; 10.0|5|4 ;; null|6|5",
            "SELECT v, RANK() OVER (ORDER BY v ROWS UNBOUNDED PRECEDING) AS r, DENSE_RANK() OVER (ORDER BY v ROWS "
                + "UNBOUNDED PRECEDING) AS d FROM w ORDER BY v NULLS LAST, r, d");
        assertAnswer("1.0|1|1 ;; 2.0|2|2 ;; 2.0|2|2 ;; 4.0|3|2 ;; 10.0|3|3 ;; null|3|3",
            "SELECT v, RANK() OVER (ORDER BY v ROWS BETWEEN 2 PRECEDING AND CURRENT ROW) AS r, DENSE_RANK() OVER "
                + "(ORDER BY v ROWS BETWEEN 2 PRECEDING AND CURRENT ROW) AS d FROM w ORDER BY v NULLS LAST, r, d");
        assertAnswer("1.0|1|1 ;; 2.0|2|2 ;; 2.0|2|2 ;; 4.0|4|3 ;; 10.0|4|3 ;; null|4|4",
            "SELECT v, RANK() OVER (ORDER BY v ROWS BETWEEN 3 PRECEDING AND 1 FOLLOWING) AS r, DENSE_RANK() OVER "
                + "(ORDER BY v ROWS BETWEEN 3 PRECEDING AND 1 FOLLOWING) AS d FROM w ORDER BY v NULLS LAST, r, d");
        assertAnswer("1|1|1 ;; 1|1|1 ;; 1|1|1 ;; 2|2|2 ;; 2|3|2 ;; 3|3|2",
            "SELECT v, RANK() OVER (ORDER BY v ROWS 2 PRECEDING) AS r, DENSE_RANK() OVER (ORDER BY v ROWS 2 "
                + "PRECEDING) AS d FROM w2 ORDER BY v, r, d");
        assertAnswer("1|1|1 ;; 1|1|1 ;; 1|1|1 ;; 2|2|2 ;; 2|3|2 ;; 3|3|2",
            "SELECT v, RANK() OVER (ORDER BY v ROWS BETWEEN 2 PRECEDING AND 2 FOLLOWING) AS r, DENSE_RANK() OVER "
                + "(ORDER BY v ROWS BETWEEN 2 PRECEDING AND 2 FOLLOWING) AS d FROM w2 ORDER BY v, r, d");
        assertAnswer("a|1.0|1 ;; a|2.0|2 ;; a|10.0|2 ;; b|2.0|1 ;; b|4.0|2 ;; b|null|2",
            "SELECT g, v, RANK() OVER (PARTITION BY g ORDER BY v ROWS 1 PRECEDING) AS r FROM w ORDER BY g, v NULLS "
                + "LAST, r");
        assertAnswer("null|1|1 ;; 10.0|2|2 ;; 4.0|2|2 ;; 2.0|1|1 ;; 2.0|2|2 ;; 1.0|2|2",
            "SELECT v, RANK() OVER (ORDER BY v DESC ROWS 1 PRECEDING) AS r, DENSE_RANK() OVER (ORDER BY v DESC ROWS "
                + "1 PRECEDING) AS d FROM w ORDER BY v DESC NULLS FIRST, r, d");
        assertAnswer("null|1 ;; 1.0|2 ;; 2.0|1 ;; 2.0|2 ;; 4.0|2 ;; 10.0|2",
            "SELECT v, RANK() OVER (ORDER BY v NULLS FIRST ROWS 1 PRECEDING) AS r FROM w ORDER BY v NULLS FIRST, r");
        assertAnswer("a|1.0|1 ;; a|2.0|2 ;; a|10.0|2 ;; b|2.0|2 ;; b|4.0|2 ;; b|null|2",
            "SELECT g, v, RANK() OVER (ORDER BY g, v ROWS 1 PRECEDING) AS r FROM w ORDER BY g, v NULLS LAST, r");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 34 | RANGE BETWEEN 1 PRECEDING AND "
                + "CURRENT ROW window frame unsupported for function RANK",
            "SELECT v, RANK() OVER (ORDER BY v RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS r FROM w ORDER BY v");
        assertAnswer("1.0|1 ;; 2.0|2 ;; 2.0|2 ;; 4.0|4 ;; 10.0|5 ;; null|6",
            "SELECT v, RANK() OVER (ORDER BY v RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS r FROM w ORDER "
                + "BY v NULLS LAST, r");
        assertAnswer("NUMBER(18,0)[SB8]",
            "SELECT SYSTEM$TYPEOF(RANK() OVER (ORDER BY v ROWS 1 PRECEDING)) FROM w LIMIT 1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 42 | Sliding window frame unsupported "
                + "for function PERCENT_RANK",
            "SELECT v, PERCENT_RANK() OVER (ORDER BY v ROWS 1 PRECEDING) AS p FROM w ORDER BY v NULLS LAST, p");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 39 | Sliding window frame unsupported "
                + "for function CUME_DIST",
            "SELECT v, CUME_DIST() OVER (ORDER BY v ROWS 1 PRECEDING) AS c FROM w ORDER BY v NULLS LAST, c");
    }
}
