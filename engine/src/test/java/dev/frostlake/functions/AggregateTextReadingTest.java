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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The aggregate readings whose answers a client's transport rounds: SKEW reads a hexadecimal text as a cast to FLOAT
 * reads it, and KURTOSIS and SKEW read a text with spaces around it and answer NULL over too few rows. Engine only:
 * the account's JSON results print a FLOAT to ten decimals.
 */
public class AggregateTextReadingTest extends MeasuredCellsTestSupport {

    @Test
    public void theMomentAggregatesReadTextByTheirFamily() {
        Assumptions.assumeFalse(isLiveSnowflake(), "the account's JSON results print a FLOAT to ten decimals");
        final String skew = answer("SELECT SKEW(v) FROM (SELECT '0x1' v UNION ALL SELECT '2' UNION ALL SELECT '4')");
        assertTrue(skew.startsWith("0.935219529"), skew);
        assertAnswer("null", "SELECT SKEW(v) FROM (SELECT ' 1' v)");
        assertAnswer("null", "SELECT KURTOSIS(v) FROM (SELECT 'inf'::FLOAT v)");
        assertAnswer("REFUSED Numeric value '1d' is not recognized",
            "SELECT KURTOSIS(v) FROM (SELECT '1d' v UNION ALL SELECT '2' UNION ALL SELECT '4' UNION ALL SELECT '8')");
        assertAnswer("REFUSED Numeric value 'x' is not recognized", "SELECT KURTOSIS(v) FROM (SELECT 'x' v)");
    }
}
