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
 * MAX_BY and MIN_BY order a NaN key above every number, the infinities at their ends; WIDTH_BUCKET reads a NaN or
 * infinite FLOAT in FLOAT arithmetic and a NaN or infinite bucket count as out of the whole number's range; ZIPF
 * puts every draw of an infinite exponent on rank 1, refuses a negative infinite one, and refuses a NaN or
 * infinite N as out of range (all live-verified).
 */
public class NonFiniteFloatArgumentsTest extends MeasuredCellsTestSupport {

    @Test
    public void nanAndInfinitiesAreReadAsTheAccountReadsThem() {
        assertAnswer("1", "SELECT MAX_BY(k, x) FROM (SELECT 1 k, 'NaN'::FLOAT x UNION ALL SELECT 2, 1.0)");
        assertAnswer("2", "SELECT MIN_BY(k, x) FROM (SELECT 1 k, 'inf'::FLOAT x UNION ALL SELECT 2, 1.0)");
        assertAnswer("2", "SELECT MAX_BY(k, x) FROM (SELECT 1 k, '-inf'::FLOAT x UNION ALL SELECT 2, 1.0)");
        assertAnswer("1", "SELECT MIN_BY(k, x) FROM (SELECT 1 k, '-inf'::FLOAT x UNION ALL SELECT 2, 1.0)");
        assertAnswer("1", "SELECT MAX_BY(k, x) FROM (SELECT 1 k, 'NaN'::FLOAT x UNION ALL SELECT 2, 'inf'::FLOAT)");
        assertAnswer("2", "SELECT MIN_BY(k, x) FROM (SELECT 1 k, 'NaN'::FLOAT x UNION ALL SELECT 2, '-inf'::FLOAT)");
        assertAnswer("[1,3]",
            "SELECT MAX_BY(k, x, 2) FROM (SELECT 1 k, 'NaN'::FLOAT x UNION ALL SELECT 2, 1.0 UNION ALL SELECT 3, "
                + "'inf'::FLOAT)");
        assertAnswer("[3,2]",
            "SELECT MIN_BY(k, x, 2) FROM (SELECT 1 k, 'NaN'::FLOAT x UNION ALL SELECT 2, 1.0 UNION ALL SELECT 3, "
                + "'-inf'::FLOAT)");
        assertAnswer("1", "SELECT ZIPF('inf'::FLOAT, 10, 1)");
        assertAnswer("REFUSED Invalid parameter value: -inf. Reason: alpha must be >= 0.0",
            "SELECT ZIPF('-inf'::FLOAT, 10, 1)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT ZIPF(1, 'inf'::FLOAT, 1)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value nan",
            "SELECT ZIPF(1, 'nan'::FLOAT, 1)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value nan",
            "SELECT ZIPF(1, 10, 'nan'::FLOAT)");
        assertAnswer("1", "SELECT WIDTH_BUCKET('nan'::FLOAT, 0, 10, 5)");
        assertAnswer("6", "SELECT WIDTH_BUCKET('inf'::FLOAT, 0, 10, 5)");
        assertAnswer("0", "SELECT WIDTH_BUCKET('-inf'::FLOAT, 0, 10, 5)");
        assertAnswer("6", "SELECT WIDTH_BUCKET(5, 'nan'::FLOAT, 10, 5)");
        assertAnswer("1", "SELECT WIDTH_BUCKET(5, 0, 'inf'::FLOAT, 5)");
        assertAnswer("1", "SELECT WIDTH_BUCKET(5, '-inf'::FLOAT, 10, 5)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value nan",
            "SELECT WIDTH_BUCKET(5, 0, 10, 'nan'::FLOAT)");
        assertAnswer("1", "SELECT MAX_BY(k, x) FROM (SELECT 1 k, 'NaN'::FLOAT x UNION ALL SELECT 2, 'NaN'::FLOAT)");
    }

    /**
     * A NaN exponent is refused as a negative one is. The account answers it with an internal error, so the call is
     * not made of it.
     */
    @Test
    public void aNanExponentIsRefusedAsANegativeOneIs() {
        if (isLiveSnowflake()) {
            return;
        }
        assertAnswer("REFUSED Invalid parameter value: nan. Reason: alpha must be >= 0.0",
            "SELECT ZIPF('nan'::FLOAT, 10, 1)");
    }
}
