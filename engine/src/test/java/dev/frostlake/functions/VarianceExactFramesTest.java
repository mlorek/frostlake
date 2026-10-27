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

import java.math.BigDecimal;
import java.math.MathContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The variance family over an EXACT argument is computed exactly and presented at its declared scale, on a moving or
 * a cumulative window frame as over a group, whatever the magnitude of the values: over NUMBER(12,2) values near
 * 10^8 every frame's VARIANCE and VAR_POP reads its exact ten decimals, and STDDEV is the root of that variance (all
 * live-verified).
 */
public class VarianceExactFramesTest extends MeasuredCellsTestSupport {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE wn (i INT, n NUMBER(12,2))");
        engine.execute("INSERT INTO wn VALUES (1, 100000000.01), (2, 100000000.02), (3, 100000000.04), "
            + "(4, 100000000.07), (5, 100000000.11), (6, 100000000.16), (7, 100000000.22)");
    }

    @Test
    public void everyFrameReadsItsExactDecimals() {
        assertAnswer("1|0.0000500000 ;; 2|0.0002333333 ;; 3|0.0006333333 ;; 4|0.0012333333 ;; 5|0.0020333333 ;; "
                + "6|0.0030333333 ;; 7|0.0018000000",
            "SELECT i, VARIANCE(n) OVER (ORDER BY i ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING) FROM wn ORDER BY i");
        assertAnswer("1|0E-10 ;; 2|0.0000250000 ;; 3|0.0001555556 ;; 4|0.0004222222 ;; 5|0.0008222222 ;; "
                + "6|0.0013555556 ;; 7|0.0020222222",
            "SELECT i, VAR_POP(n) OVER (ORDER BY i ROWS BETWEEN 2 PRECEDING AND CURRENT ROW) FROM wn ORDER BY i");
        assertAnswer("1|null ;; 2|0.0000500000 ;; 3|0.0002333333 ;; 4|0.0007000000 ;; 5|0.0016500000 ;; "
                + "6|0.0033366667 ;; 7|0.0060666667",
            "SELECT i, VARIANCE(n) OVER (ORDER BY i ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) FROM wn "
                + "ORDER BY i");
        assertAnswer("1.000000",
            "SELECT VARIANCE(x) FROM (SELECT 100000000 AS x UNION ALL SELECT 100000001 UNION ALL SELECT 100000002)");
    }

    /**
     * STDDEV over the frames is the root of each frame's exact variance at its declared scale, a FLOAT: the account
     * shows it to ten significant digits, the engine's FLOAT keeps every digit of the same value.
     */
    @Test
    public void theDeviationIsTheRootOfTheExactVariance() {
        final String[] shown = {"0.07788880985", "0.07607014329", "0.07176350047", "0.06480740698", "0.05507570517",
            "0.04242640687"};
        final String answered = answer("SELECT STDDEV(n) OVER (ORDER BY i ROWS BETWEEN CURRENT ROW AND UNBOUNDED "
            + "FOLLOWING) FROM wn ORDER BY i");
        final String[] cells = answered.split(" ;; ");
        assertEquals(7, cells.length, answered);
        for (int i = 0; i < shown.length; i++) {
            assertEquals(shown[i], new BigDecimal(cells[i]).round(new MathContext(10)).toPlainString(), answered);
        }
        assertEquals("null", cells[6]);
    }
}
