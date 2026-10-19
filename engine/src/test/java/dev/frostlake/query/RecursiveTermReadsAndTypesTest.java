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
 * A recursive CTE's terms read the CTE in their own FROM: a reference inside a derived table is refused, "'R' is an
 * invalid Recursive CTE", in a view's body too (live-verified). Each step's values take the column's settled type —
 * a DATE column holds the date of the timestamp a term computes, a timestamp column the timestamp of a date, a FLOAT
 * column a double — and a number anchor beside a BOOLEAN term describes BOOLEAN.
 */
public class RecursiveTermReadsAndTypesTest extends MeasuredCellsTestSupport {

    @Test
    public void aTermReadsItsCteDirectlyAndItsValuesTakeTheColumnsType() {
        assertCells(new String[][] {
            {"WITH RECURSIVE r (n) AS (SELECT 1 UNION ALL SELECT m FROM (SELECT n + 1 AS m FROM r WHERE n < 3)) "
                + "SELECT * FROM r ORDER BY 1",
                "REFUSED SQL compilation error: | 'R' is an invalid Recursive CTE"},
            {"CREATE OR REPLACE VIEW rx43 AS WITH RECURSIVE r (n) AS (SELECT 1 UNION ALL SELECT m FROM (SELECT "
                + "n + 1 AS m FROM r WHERE n < 3)) SELECT * FROM r",
                "REFUSED SQL compilation error: | 'R' is an invalid Recursive CTE"},
            {"DESCRIBE VIEW rx43",
                "REFUSED SQL compilation error: | View 'RX43' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE RX43."},
        });
        assertShapedAnswer("REFUSED Query <QID> has no result because it failed",
            "SELECT \"name\", \"type\", \"null?\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertCells(new String[][] {
            {"SELECT n FROM rx43 ORDER BY 1",
                "REFUSED SQL compilation error: | Object 'RX43' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE RX43."},
            {"WITH RECURSIVE r (n) AS (SELECT 1 UNION ALL SELECT m FROM (SELECT n + 1 AS m FROM r) d WHERE m < "
                + "4) SELECT * FROM r ORDER BY 1",
                "REFUSED SQL compilation error: | 'R' is an invalid Recursive CTE"},
            {"WITH RECURSIVE r (n) AS (SELECT 1 UNION ALL SELECT x.m FROM (SELECT n * 2 AS m FROM r WHERE n < "
                + "8) x) SELECT * FROM r ORDER BY 1",
                "REFUSED SQL compilation error: | 'R' is an invalid Recursive CTE"},
            {"CREATE OR REPLACE RECURSIVE VIEW rv1 (d) AS SELECT '2024-01-01'::DATE UNION ALL SELECT "
                + "DATEADD(hour, 30, d::TIMESTAMP_NTZ) FROM rv1 WHERE d < '2024-01-02'",
                "View RV1 successfully created."},
            {"SELECT d FROM rv1 ORDER BY 1", "2024-01-01 ;; 2024-01-02"},
            {"SELECT SYSTEM$TYPEOF(d) FROM rv1 ORDER BY 1 LIMIT 1", "DATE[SB4]"},
            {"CREATE OR REPLACE RECURSIVE VIEW rv2 (d) AS SELECT '2024-01-01'::TIMESTAMP_NTZ UNION ALL SELECT "
                + "DATEADD(day, 1, d)::DATE FROM rv2 WHERE d < '2024-01-02'",
                "View RV2 successfully created."},
            {"SELECT d FROM rv2 ORDER BY 1", "2024-01-01T00:00 ;; 2024-01-02T00:00"},
            {"CREATE OR REPLACE RECURSIVE VIEW rv3 (n) AS SELECT 1::FLOAT UNION ALL SELECT (n + 1)::NUMBER(5,0) "
                + "FROM rv3 WHERE n < 3",
                "View RV3 successfully created."},
            {"SELECT n FROM rv3 ORDER BY 1", "1.0 ;; 2.0 ;; 3.0"},
            {"SELECT n::VARCHAR FROM rv3 ORDER BY 1", "1 ;; 2 ;; 3"},
            {"CREATE OR REPLACE RECURSIVE VIEW rv4 (n) AS SELECT 1 UNION ALL SELECT n > 5 FROM rv4 WHERE n = 1",
                "View RV4 successfully created."},
        });
        engine.execute("DESCRIBE VIEW rv4");
        assertCells(new String[][] {
            {"SELECT \"name\", \"type\", \"null?\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", "N|BOOLEAN|Y"},
            {"SELECT n FROM rv4",
                "REFUSED SQL compilation error: | Type mismatch between anchor and recursive term of column 'N' in "
                + "Recursive CTE 'RV4_RECURSIVE_CTE'"},
            {"WITH RECURSIVE r (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM (SELECT * FROM r) WHERE n < 3) "
                + "SELECT * FROM r ORDER BY 1",
                "REFUSED SQL compilation error: | 'R' is an invalid Recursive CTE"},
        });
    }
}
