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

import static org.junit.jupiter.api.Assumptions.assumeFalse;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * The variance family over an exact argument and a moving frame slides one exact state along the partition, rows
 * entering and leaving it, and answers what each frame's rows read afresh answer: a frame before, around and after its
 * row, a NULL among the values, a frame of one value, and a partition boundary.
 */
public class ExactMovingVarianceTest extends MeasuredCellsTestSupport {

    @Test
    public void eachFrameAnswersAsItsRowsReadAfresh() {
        assumeFalse(isLiveSnowflake(), "pins the engine's sliding exact sums to its frame-by-frame answers");
        engine.execute("CREATE OR REPLACE TABLE ve (g INT, o INT, n NUMBER(12,2), i INT)");
        engine.execute("INSERT INTO ve VALUES (1, 1, 1.50, 100000000), (1, 2, 2.25, 100000001), "
            + "(1, 3, NULL, 100000002), (1, 4, 10.75, 100000003), (1, 5, 7.00, 100000004), (1, 6, 3.33, 100000005), "
            + "(2, 1, 5.00, 7), (2, 2, 5.50, 8), (2, 3, 6.10, 9)");
        assertCells(new String[][] {
            {"SELECT g, o, VARIANCE(n) OVER (PARTITION BY g ORDER BY o ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING), "
                + "VAR_POP(i) OVER (PARTITION BY g ORDER BY o ROWS BETWEEN 2 PRECEDING AND CURRENT ROW), "
                + "STDDEV_SAMP(n) OVER (PARTITION BY g ORDER BY o ROWS BETWEEN CURRENT ROW AND 2 FOLLOWING) "
                + "FROM ve ORDER BY g, o",
                "1|1|0.2812500000|0.000000|0.5303300858899106 ;; 1|2|0.2812500000|0.250000|6.010407640085654 ;; "
                + "1|3|36.1250000000|0.666667|2.6516504294495533 ;; 1|4|7.0312500000|0.666667|3.7100718771069654 ;; "
                + "1|5|13.7646333333|0.666667|2.5950818869546293 ;; 1|6|6.7344500000|0.666667|null ;; "
                + "2|1|0.1250000000|0.000000|0.5507570546983488 ;; 2|2|0.3033333333|0.250000|0.4242640687119285 ;; "
                + "2|3|0.1800000000|0.666667|null"},
        });
    }
}
