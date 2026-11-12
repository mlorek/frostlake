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
 * A PIVOT or UNPIVOT applies to the FROM item it follows, whichever item that is: the item is reshaped before a join
 * or another item reads it, and its alias names the reshaped relation, so an ON clause reads the pivot's output. The
 * clause was applied once per FROM clause, to the first item after every join, and dropped on any later item.
 */
public class PivotOnItsFromItemTest extends MeasuredCellsTestSupport {

    private static final String PIVOT = "PIVOT (SUM(amt) FOR m IN ('JAN', 'FEB'))";

    private void setUpTables() {
        engine.execute("CREATE OR REPLACE TABLE pvs (emp INT, m VARCHAR, amt NUMBER(10,2))");
        engine.execute("INSERT INTO pvs VALUES (1, 'JAN', 10), (1, 'FEB', 20), (2, 'JAN', 30)");
        engine.execute("CREATE OR REPLACE TABLE pvq (k INT, v NUMBER(10,2))");
        engine.execute("INSERT INTO pvq VALUES (1, 10), (1, 10)");
        engine.execute("CREATE OR REPLACE TABLE pvq1 (k INT)");
        engine.execute("INSERT INTO pvq1 VALUES (1)");
    }

    @Test
    public void aPivotBeforeAJoinIsTheItemsOwn() {
        setUpTables();
        final String joined = "1|10.00|20.00|1|10.00 ;; 1|10.00|20.00|1|10.00";
        assertAnswer(joined, "SELECT * FROM pvs " + PIVOT + " p JOIN pvq ON emp = k ORDER BY 1");
        assertAnswer(joined, "SELECT * FROM (SELECT * FROM pvs " + PIVOT + ") p JOIN pvq ON emp = k ORDER BY 1");
        assertAnswer("1|10.00 ;; 1|10.00", "SELECT p.emp, q.v FROM pvs " + PIVOT + " p JOIN pvq q ON p.emp = q.k");
        assertAnswer(joined, "SELECT * FROM pvs " + PIVOT + " p JOIN pvq ON \"'JAN'\" = v ORDER BY 1");
    }

    @Test
    public void aPivotOrUnpivotOnALaterItemIsApplied() {
        setUpTables();
        assertAnswer("1|1|10.00|20.00 ;; 1|2|30.00|null", "SELECT * FROM pvq1, pvs " + PIVOT + " p ORDER BY emp");
        assertAnswer("1|1|10.00|20.00", "SELECT * FROM pvq1 JOIN pvs " + PIVOT + " p ON emp = k");
        assertAnswer("1|1|FEB|AMT|20.00 ;; 1|1|JAN|AMT|10.00 ;; 1|2|JAN|AMT|30.00",
            "SELECT * FROM pvq1, pvs UNPIVOT (val FOR col IN (amt)) ORDER BY emp, m");
    }
}
