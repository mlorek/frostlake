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
 * COUNT_IF counts the rows whose condition holds, and answers NULL over no rows at all — an empty input, an empty
 * listing read back through RESULT_SCAN, an empty window frame — where COUNT answers 0; any row, its condition NULL or
 * FALSE, makes it 0 (live-verified).
 */
public class CountIfOverNoRowsTest extends MeasuredCellsTestSupport {

    @Test
    public void noRowsAreNullAndAnyRowCounts() {
        assertCells(new String[][] {
            {"SELECT COUNT_IF(a = 1), COUNT(*) FROM (SELECT 1 a WHERE FALSE)", "null|0"},
            {"SELECT COUNT_IF(a = 1), SUM(a), COUNT(a), COUNT_IF(TRUE) FROM (SELECT 1 a WHERE FALSE)",
                "null|null|0|null"},
            {"SELECT COUNT_IF(a = 1) FROM (SELECT NULL::INT a)", "0"},
            {"SELECT COUNT_IF(a = 1) FROM (SELECT 2 a)", "0"},
            {"SELECT g, COUNT_IF(a = 1) FROM (SELECT 1 g, 2 a) GROUP BY g", "1|0"},
            {"SELECT COUNT_IF(a = 1) OVER (ORDER BY a ROWS BETWEEN 1 FOLLOWING AND 2 FOLLOWING) FROM (SELECT 1 a)",
                "null"},
        });
    }

    @Test
    public void anEmptyListingReadBackCountsNull() {
        engine.executeQuery("SHOW TABLES LIKE 'no_such_count_if_table'");
        assertAnswer("null|0", "SELECT COUNT_IF(\"name\" = 'X'), COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
    }
}
