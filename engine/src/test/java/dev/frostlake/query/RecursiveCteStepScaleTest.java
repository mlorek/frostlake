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
 * A recursive CTE's every step - the anchor's rows too - is held to the CTE's column type, so the next step reads
 * the rounded value and a SELECT and a CTAS of one CTE agree: 1.5 times 1.5 four times is 1.50, 2.25, 3.38, 5.07
 * (all live-verified).
 */
public class RecursiveCteStepScaleTest extends MeasuredCellsTestSupport {

    @Test
    public void eachStepIsHeldToTheColumnsScale() {
        assertAnswer("1.50|NUMBER(38,2)[SB16] ;; 2.25|NUMBER(38,2)[SB16] ;; 3.38|NUMBER(38,2)[SB16] ;; 5.07|NUMBER(38,"
                + "2)[SB16]",
            "WITH r(n) AS (SELECT 1.5 UNION ALL SELECT n*1.5 FROM r WHERE n<5) SELECT n, SYSTEM$TYPEOF(n) t FROM r "
                + "ORDER BY n");
        assertAnswer("12.20", "WITH r(n) AS (SELECT 1.5 UNION ALL SELECT n*1.5 FROM r WHERE n<5) SELECT SUM(n) FROM r");
        assertAnswer("Table RCT successfully created.",
            "CREATE OR REPLACE TABLE rct AS WITH r(n) AS (SELECT 1.5 UNION ALL SELECT n*1.5 FROM r WHERE n<5) SELECT "
                + "n FROM r");
        assertAnswer("1.50 ;; 2.25 ;; 3.38 ;; 5.07", "SELECT n FROM rct ORDER BY n");
        assertAnswer("12.20", "SELECT SUM(n) FROM rct");
        assertAnswer("1|0.5000000|NUMBER(38,7)[SB16] ;; 2|0.1666667|NUMBER(38,7)[SB16] ;; 3|0.0555556|NUMBER(38,"
                + "7)[SB16]",
            "WITH r(n, d) AS (SELECT 1, 0.5 UNION ALL SELECT n+1, d/3 FROM r WHERE n<3) SELECT n, d, "
                + "SYSTEM$TYPEOF(d) FROM r ORDER BY n");
        assertAnswer("",
            "WITH r(n) AS (SELECT 1.5 UNION ALL SELECT n*1.5 FROM r WHERE n<5) SELECT n FROM r WHERE n = 3.375");
        assertAnswer("3.38",
            "WITH r(n) AS (SELECT 1.5 UNION ALL SELECT n*1.5 FROM r WHERE n<5) SELECT n FROM r WHERE n = 3.38");
        assertAnswer("View RVN successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW rvn(n) AS SELECT 1.5 UNION ALL SELECT n*1.5 FROM rvn WHERE n<5");
        assertAnswer("1.50 ;; 2.25 ;; 3.38 ;; 5.07", "SELECT n FROM rvn ORDER BY n");
        assertAnswer("1.250 ;; 1.375 ;; 1.513 ;; 1.664 ;; 1.830 ;; 2.013",
            "WITH RECURSIVE r(n) AS (SELECT 1.25::NUMBER(5,2) UNION ALL SELECT n*1.1 FROM r WHERE n<2) SELECT n FROM "
                + "r ORDER BY n");
        assertAnswer("0.370370|NUMBER(38,6)[SB16] ;; 1.111111|NUMBER(38,6)[SB16] ;; 3.333333|NUMBER(38,6)[SB16] ;; "
                + "10.000000|NUMBER(38,6)[SB16]",
            "WITH RECURSIVE r(n) AS (SELECT 10 UNION ALL SELECT n/3 FROM r WHERE n>1) SELECT n, SYSTEM$TYPEOF(n) "
                + "FROM r ORDER BY n");
    }
}
