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
 * A recursive CTE holds every value, the anchor's included, to its column's type, and each step reads the value so
 * held (live-verified): over a NUMBER(38,2) column n * 1.5 from 1.5 is 1.50, 2.25, 3.38, 5.07, an anchor's 1 in a
 * NUMBER(38,1) column is 1.0, and a CTAS of the query stores the rows a SELECT of it answers.
 */
public class RecursiveCteColumnScaleTest extends MeasuredCellsTestSupport {

    @Test
    public void valuesTakeTheColumnsScale() {
        engine.execute("ALTER SESSION SET STATEMENT_TIMEOUT_IN_SECONDS = 20");
        assertCells(new String[][] {
            {"WITH r(n) AS (SELECT 1 UNION ALL SELECT n + 0.5 FROM r WHERE n < 2) SELECT n, SYSTEM$TYPEOF(n) FROM r "
                + "ORDER BY 1",
                "1.0|NUMBER(38,1)[SB16] ;; 1.5|NUMBER(38,1)[SB16] ;; 2.0|NUMBER(38,1)[SB16]"},
            {"WITH r(n, d) AS (SELECT 1, 0.5 UNION ALL SELECT n + 1, d / 3 FROM r WHERE n < 3) SELECT n, d, "
                + "SYSTEM$TYPEOF(d) FROM r ORDER BY 1",
                "1|0.5000000|NUMBER(38,7)[SB16] ;; 2|0.1666667|NUMBER(38,7)[SB16] ;; 3|0.0555556|NUMBER(38,7)[SB16]"},
            {"WITH r(n, d) AS (SELECT 1, 0.5 UNION ALL SELECT n + 1, d / 3 FROM r WHERE n < 3) SELECT n, d * 1000000 "
                + "FROM r ORDER BY 1",
                "1|500000.0000000 ;; 2|166666.7000000 ;; 3|55555.6000000"},
            {"WITH r(n, d) AS (SELECT 1, 0.5 UNION ALL SELECT n + 1, d / 3 FROM r WHERE n < 3) SELECT n, d::VARCHAR "
                + "FROM r ORDER BY 1",
                "1|0.5000000 ;; 2|0.1666667 ;; 3|0.0555556"},
            {"WITH r(n, d) AS (SELECT 1, 0.5 UNION ALL SELECT n + 1, d / 3 FROM r WHERE n < 4) SELECT n, d FROM r "
                + "ORDER BY 1",
                "1|0.5000000 ;; 2|0.1666667 ;; 3|0.0555556 ;; 4|0.0185185"},
            {"WITH r(n, d) AS (SELECT 1, 1.25 UNION ALL SELECT n + 1, d * 2 FROM r WHERE n < 3) SELECT n, d, "
                + "SYSTEM$TYPEOF(d) FROM r ORDER BY 1",
                "1|1.25|NUMBER(38,2)[SB16] ;; 2|2.50|NUMBER(38,2)[SB16] ;; 3|5.00|NUMBER(38,2)[SB16]"},
            {"WITH r(n, d) AS (SELECT 1, 1 UNION ALL SELECT n + 1, d + 0.25 FROM r WHERE n < 3) SELECT n, d, "
                + "SYSTEM$TYPEOF(d) FROM r ORDER BY 1",
                "1|1.00|NUMBER(38,2)[SB16] ;; 2|1.25|NUMBER(38,2)[SB16] ;; 3|1.50|NUMBER(38,2)[SB16]"},
            {"WITH r(n) AS (SELECT 1.5 UNION ALL SELECT n * 1.5 FROM r WHERE n < 5) SELECT n, SYSTEM$TYPEOF(n) FROM r "
                + "ORDER BY 1",
                "1.50|NUMBER(38,2)[SB16] ;; 2.25|NUMBER(38,2)[SB16] ;; 3.38|NUMBER(38,2)[SB16] ;; 5.07|NUMBER(38,"
                + "2)[SB16]"},
        });
    }

    @Test
    public void aCtasStoresWhatTheQueryAnswers() {
        engine.execute("ALTER SESSION SET STATEMENT_TIMEOUT_IN_SECONDS = 20");
        engine.execute("CREATE OR REPLACE TEMPORARY TABLE tt AS WITH r(n) AS (SELECT 1.5 UNION ALL SELECT n * 1.5 "
            + "FROM r WHERE n < 5) SELECT n FROM r");
        assertCells(new String[][] {
            {"WITH r(n) AS (SELECT 1.5 UNION ALL SELECT n * 1.5 FROM r WHERE n < 5) SELECT n FROM r WHERE n = 3.375",
                ""},
            {"SELECT n FROM tt ORDER BY 1", "1.50 ;; 2.25 ;; 3.38 ;; 5.07"},
            {"WITH r(n) AS (SELECT 1.5 UNION ALL SELECT n * 1.5 FROM r WHERE n < 5) SELECT n FROM r EXCEPT SELECT n "
                + "FROM tt",
                ""},
            {"SELECT column_name, data_type, numeric_precision, numeric_scale FROM information_schema.columns WHERE "
                + "table_name = 'TT'",
                "N|NUMBER|38|2"},
        });
    }
}
