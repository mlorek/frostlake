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
 * MODE, MEDIAN and LISTAGG read their column where it stands after ALTER TABLE DROP COLUMN (live-verified), also
 * when the same session aggregated the table before the drop.
 */
public class AggregateAfterDropColumnTest extends MeasuredCellsTestSupport {

    @Test
    public void anAggregateReadsTheColumnAsItStands() {
        engine.execute("CREATE OR REPLACE TABLE dct (a INT, b INT, c INT)");
        engine.execute("INSERT INTO dct VALUES (1, 10, 1000), (2, 10, 2000), (3, 30, 2000)");
        engine.execute("CREATE OR REPLACE TABLE dct2 (a INT, b INT)");
        engine.execute("INSERT INTO dct2 VALUES (1, 5), (2, 5)");
        assertCells(new String[][] {
            {"SELECT MODE(b), MEDIAN(b) FROM dct", "10|10.000"},
            {"ALTER TABLE dct DROP COLUMN a", "Statement executed successfully."},
            {"SELECT MODE(b), MEDIAN(b), LISTAGG(b, ',') WITHIN GROUP (ORDER BY b) FROM dct", "10|10.000|10,10,30"},
            {"SELECT MODE(b) FROM (SELECT * FROM dct)", "10"},
            {"SELECT MODE(b) FROM dct2", "5"},
            {"ALTER TABLE dct2 DROP COLUMN a", "Statement executed successfully."},
            {"SELECT MODE(b), MEDIAN(b) FROM dct2", "5|5.000"},
        });
    }
}
