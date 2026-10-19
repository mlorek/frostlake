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
 * A materialized view keeps no history to read at a past point: AT and BEFORE of every kind are refused through the
 * view's expansion, Time travel is not supported for materialized views (live-verified).
 */
public class MaterializedViewTimeTravelTest extends MeasuredCellsTestSupport {

    @Test
    public void aMaterializedViewHasNoTimeTravel() {
        engine.execute("CREATE OR REPLACE TABLE mvb (id INT, v INT)");
        engine.execute("INSERT INTO mvb VALUES (1, 10)");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW mvv AS SELECT id, v FROM mvb");
        assertCells(new String[][] {
            {"SELECT id, v FROM mvv", "1|10"},
            {"SELECT id, v FROM mvv AT(OFFSET => -1)",
                "REFUSED SQL compilation error: | Failure during expansion of view 'MVV': SQL compilation error: "
                + "Time travel is not supported for materialized views."},
            {"SELECT id, v FROM mvv BEFORE(STATEMENT => LAST_QUERY_ID())",
                "REFUSED SQL compilation error: | Failure during expansion of view 'MVV': SQL compilation error: "
                + "Time travel is not supported for materialized views."},
            {"SELECT id, v FROM mvv AT(TIMESTAMP => CURRENT_TIMESTAMP())",
                "REFUSED SQL compilation error: | Failure during expansion of view 'MVV': SQL compilation error: "
                + "Time travel is not supported for materialized views."},
        });
    }
}
