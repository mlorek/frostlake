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
 * The variance family over large exact values answers exactly (live-verified): over seven values near 10^8 with two
 * decimals VARIANCE, VAR_SAMP and VARIANCE_SAMP are 0.0060666667 and VAR_POP and VARIANCE_POP 0.0052000000, over the
 * whole table and per group, STDDEV and STDDEV_POP their square roots; a RANGE frame from the partition's start, one
 * to its end, one of the current row's peers alone and one of the rows two before reads each frame's own variance.
 */
public class VarianceLargeExactValuesTest extends MeasuredCellsTestSupport {

    @Test
    public void theGroupedFamilyIsExact() {
        engine.execute("CREATE OR REPLACE TABLE wn922 (i INT, n NUMBER(12,2))");
        engine.execute("INSERT INTO wn922 VALUES (1, 100000000.01), (2, 100000000.02), (3, 100000000.04), (4, "
            + "100000000.07), (5, 100000000.11), (6, 100000000.16), (7, 100000000.22)");
        assertCells(new String[][] {
            {"SELECT VARIANCE(n), VAR_POP(n) FROM wn922", "0.0060666667|0.0052000000"},
            {"SELECT VAR_SAMP(n), VARIANCE_POP(n), VARIANCE_SAMP(n) FROM wn922",
                "0.0060666667|0.0052000000|0.0060666667"},
            {"SELECT STDDEV(n)::NUMBER(20,12), STDDEV_POP(n)::NUMBER(20,12) FROM wn922",
                "0.077888809851|0.072111025509"},
            {"SELECT i % 2, VARIANCE(n), VAR_POP(n) FROM wn922 GROUP BY 1 ORDER BY 1",
                "0|0.0050333333|0.0033555556 ;; 1|0.0087000000|0.0065250000"},
        });
    }

    @Test
    public void rangeFramesReadEachFramesOwnVariance() {
        engine.execute("CREATE OR REPLACE TABLE wn916 (i INT, n NUMBER(12,2))");
        engine.execute("INSERT INTO wn916 VALUES (1, 100000000.01), (2, 100000000.02), (3, 100000000.04), (4, "
            + "100000000.07), (5, 100000000.11), (6, 100000000.16), (7, 100000000.22)");
        assertCells(new String[][] {
            {"SELECT i, VARIANCE(n) OVER (ORDER BY i RANGE BETWEEN UNBOUNDED PRECEDING AND 1 FOLLOWING) FROM "
                + "wn916 ORDER BY i",
                "1|0.0000500000 ;; 2|0.0002333333 ;; 3|0.0007000000 ;; 4|0.0016500000 ;; 5|0.0033366667 ;; "
                + "6|0.0060666667 ;; 7|0.0060666667"},
            {"SELECT i, VARIANCE(n) OVER (ORDER BY i RANGE BETWEEN 1 PRECEDING AND UNBOUNDED FOLLOWING) FROM "
                + "wn916 ORDER BY i",
                "1|0.0060666667 ;; 2|0.0060666667 ;; 3|0.0057866667 ;; 4|0.0051500000 ;; 5|0.0042000000 ;; "
                + "6|0.0030333333 ;; 7|0.0018000000"},
            {"SELECT i, VARIANCE(n) OVER (ORDER BY i RANGE BETWEEN CURRENT ROW AND CURRENT ROW) FROM wn916 ORDER BY i",
                "1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null ;; 6|null ;; 7|null"},
            {"SELECT i, VAR_POP(n) OVER (ORDER BY i RANGE BETWEEN 2 PRECEDING AND CURRENT ROW) FROM wn916 ORDER BY i",
                "1|0E-10 ;; 2|0.0000250000 ;; 3|0.0001555556 ;; 4|0.0004222222 ;; 5|0.0008222222 ;; 6|0.0013555556 "
                + ";; 7|0.0020222222"},
        });
    }
}
