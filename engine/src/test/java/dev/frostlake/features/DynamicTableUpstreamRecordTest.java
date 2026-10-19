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

package dev.frostlake.features;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A refresh of a dynamic table records the refresh of each dynamic table it reads (live-verified): a read of the
 * upstream table at a moment after the downstream refresh answers the rows the downstream refresh read, a stream
 * on it shows the change, and its own refresh then has no new data.
 */
public class DynamicTableUpstreamRecordTest extends MeasuredCellsTestSupport {

    @Test
    public void anUpstreamTableRecordsItsRefresh() {
        engine.execute("CREATE OR REPLACE TABLE b9 (id INT, g STRING, v INT)");
        engine.execute("INSERT INTO b9 VALUES (1, 'x', 10)");
        final Object wh = engine.executeQuery("SELECT CURRENT_WAREHOUSE()").getRows().get(0).getValue(0);
        final String warehouse = wh == null ? "COMPUTE_WH" : String.valueOf(wh);
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE u1 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " AS SELECT g, SUM(v) s FROM b9 GROUP BY g");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE u2 TARGET_LAG = DOWNSTREAM WAREHOUSE = " + warehouse
            + " AS SELECT g, s * 2 s2 FROM u1");
        engine.execute("CREATE OR REPLACE STREAM su1 ON DYNAMIC TABLE u1");
        assertCells(new String[][] {
            {"INSERT INTO b9 VALUES (3, 'x', 5)", "1"},
            {"ALTER DYNAMIC TABLE u2 REFRESH ->> SELECT \"statistics\", \"refreshed_dt_count\" FROM $1",
                "{\"insertedRows\":1,\"copiedRows\":0,\"deletedRows\":1}|2"},
            {"SELECT SYSTEM$WAIT(1)", "waited 1 seconds"},
            {"SET t9 = CURRENT_TIMESTAMP()", "Statement executed successfully."},
            {"SELECT SYSTEM$WAIT(1)", "waited 1 seconds"},
            {"SELECT g, s FROM u1 AT (TIMESTAMP => $t9::TIMESTAMP_LTZ)", "x|15"},
            {"SELECT g, s2 FROM u2 AT (TIMESTAMP => $t9::TIMESTAMP_LTZ)", "x|30"},
            {"ALTER DYNAMIC TABLE u1 REFRESH ->> SELECT \"statistics\", \"refreshed_dt_count\" FROM $1",
                "No new data|0"},
            {"SELECT g, s FROM u1 BEFORE (TIMESTAMP => $t9::TIMESTAMP_LTZ)", "x|15"},
        });
    }
}
