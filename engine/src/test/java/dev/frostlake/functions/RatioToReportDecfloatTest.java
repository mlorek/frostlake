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
 * RATIO_TO_REPORT over DECFLOAT values is a DECFLOAT(38): the value divided by the partition's sum as DECFLOAT
 * divides, the sum added up to thirty-eight digits - 3.3333333333333333333333333333333333333e-1, a share of 1e-50
 * beside 1 is 1e-50 - and NULL for a NULL value or a sum of zero, where an exact NUMBER keeps its declared scale
 * (all live-verified).
 */
public class RatioToReportDecfloatTest extends MeasuredCellsTestSupport {

    @Test
    public void decfloatShares() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertAnswer("1|3.3333333333333333333333333333333333333e-1|DECFLOAT(38)[DECFLOAT18]|"
                + "3.3333333333333333333333333333333333333e-1 ;; 2|6.6666666666666666666666666666666666667e-1|"
                + "DECFLOAT(38)[DECFLOAT18]|6.6666666666666666666666666666666666667e-1",
            "SELECT x::VARCHAR, RATIO_TO_REPORT(x) OVER ()::VARCHAR, SYSTEM$TYPEOF(RATIO_TO_REPORT(x) OVER ()), (x / "
                + "SUM(x) OVER ())::VARCHAR FROM (SELECT DECFLOAT '1' x UNION ALL SELECT DECFLOAT '2') ORDER BY 1");
        assertAnswer("1 ;; 1e-50",
            "SELECT RATIO_TO_REPORT(x) OVER ()::VARCHAR FROM (SELECT DECFLOAT '1e-50' x UNION ALL SELECT DECFLOAT "
                + "'1') ORDER BY 1");
        assertAnswer("Table R48 successfully created.", "CREATE OR REPLACE TABLE r48 (g INT, x DECFLOAT)");
        assertAnswer("6", "INSERT INTO r48 VALUES (1, '1'), (1, '3'), (2, '1e100'), (2, NULL), (3, '0'), (3, '0')");
        assertAnswer("1|0.25 ;; 1|0.75 ;; 2|1 ;; 2|null ;; 3|null ;; 3|null",
            "SELECT g, RATIO_TO_REPORT(x) OVER (PARTITION BY g)::VARCHAR FROM r48 ORDER BY g, 2");
        assertAnswer("DECFLOAT(38)[DECFLOAT18]", "SELECT SYSTEM$TYPEOF(RATIO_TO_REPORT(x) OVER ()) FROM r48 LIMIT 1");
        assertAnswer("NUMBER(18,8)[SB8]",
            "SELECT SYSTEM$TYPEOF(RATIO_TO_REPORT(x) OVER ()) FROM (SELECT 1.5::NUMBER(10,2) x)");
        assertAnswer("0.25 ;; 0.75",
            "SELECT RATIO_TO_REPORT(x) OVER ()::VARCHAR FROM (SELECT DECFLOAT '1e300' x UNION ALL SELECT DECFLOAT "
                + "'3e300') ORDER BY 1");
        assertAnswer("DECFLOAT(38)[DECFLOAT18]", "SELECT SYSTEM$TYPEOF(x / SUM(x) OVER ()) FROM r48 LIMIT 1");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
