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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * DYNAMIC_TABLE_REFRESH_HISTORY lists the inputs a dynamic table's query names (documented): a view as an input of
 * kind VIEW whose baseTableStatisticsAgg sums the statistics of the tables it reads, beside the tables the query
 * names itself. A refresh is NO_DATA only where no input's data changed: an insert the query filters out is an
 * incremental refresh of no rows that lists the table, though ALTER DYNAMIC TABLE … REFRESH answers it as no new
 * data, its rows unchanged (live-verified for that answer). The account allows no measurement of the history's side,
 * so the embedded engine holds it.
 */
public class DynamicTableRefreshInputsTest extends MeasuredCellsTestSupport {

    private static final String REFRESH = " REFRESH ->> SELECT \"statistics\", \"refreshed_dt_count\" FROM $1";

    @BeforeEach
    public void embeddedOnly() {
        Assumptions.assumeFalse(isLiveSnowflake(), "not measured on the account");
    }

    private String warehouse() {
        final Object wh = engine.executeQuery("SELECT CURRENT_WAREHOUSE()").getRows().get(0).getValue(0);
        return wh == null ? "COMPUTE_WH" : String.valueOf(wh);
    }

    private static String input(final String kind, final String name, final int added, final int registered,
                                final int removed, final int unregistered) {
        return "{\"kind\":\"" + kind + "\",\"name\":\"" + name + "\",\""
            + ("VIEW".equals(kind) ? "baseTableStatisticsAgg" : "statistics") + "\":{\"numAddedPartitions\":"
            + added + ",\"numRegisteredRows\":" + registered + ",\"numRemovedPartitions\":" + removed
            + ",\"numUnregisteredRows\":" + unregistered + "}}";
    }

    @Test
    public void anInsertTheQueryDropsIsAnIncrementalRefreshOfNoRows() {
        engine.execute("CREATE OR REPLACE TABLE f748s (a INT)");
        engine.execute("INSERT INTO f748s VALUES (1)");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE f748a TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse()
            + " AS SELECT a FROM f748s WHERE a < 10");
        assertAnswer("1", "INSERT INTO f748s VALUES (100)");
        assertAnswer("No new data|0", "ALTER DYNAMIC TABLE f748a" + REFRESH);
        assertAnswer("No new data|0", "ALTER DYNAMIC TABLE f748a" + REFRESH);
        assertAnswer("CREATION|INCREMENTAL|1|0|false|[" + input("TABLE", "TEST_DB.TEST_SCHEMA.F748S", 1, 1, 0, 0)
                + "] ;; MANUAL|INCREMENTAL|0|0|false|[" + input("TABLE", "TEST_DB.TEST_SCHEMA.F748S", 1, 2, 1, 1)
                + "] ;; MANUAL|NO_DATA|0|0|true|null",
            "SELECT REFRESH_TRIGGER, REFRESH_ACTION, STATISTICS:numInsertedRows, STATISTICS:numDeletedRows, "
                + "WAREHOUSE IS NULL, INPUTS_WITH_CHANGED_DATA FROM "
                + "TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => 'F748A')) "
                + "ORDER BY REFRESH_START_TIME");
    }

    @Test
    public void aViewIsAnInputOfItsOwnWithItsBaseTablesStatistics() {
        engine.execute("CREATE OR REPLACE TABLE v748s (a INT)");
        engine.execute("INSERT INTO v748s VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE v748t (a INT)");
        engine.execute("INSERT INTO v748t VALUES (2), (3)");
        engine.execute("CREATE OR REPLACE VIEW v748v AS SELECT a FROM v748s UNION ALL SELECT a FROM v748t");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE v748a TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse()
            + " AS SELECT a FROM v748v");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE v748b TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse()
            + " AS SELECT x.a FROM v748s x JOIN v748v y ON x.a = y.a");
        // The joined row is not 4's: the view's change is listed, the table the query names itself is not.
        assertAnswer("1", "INSERT INTO v748t VALUES (4)");
        assertAnswer("No new data|0", "ALTER DYNAMIC TABLE v748b" + REFRESH);
        assertAnswer("V748A|CREATION|INCREMENTAL|[" + input("VIEW", "TEST_DB.TEST_SCHEMA.V748V", 2, 3, 0, 0)
                + "] ;; V748B|CREATION|INCREMENTAL|[" + input("TABLE", "TEST_DB.TEST_SCHEMA.V748S", 1, 1, 0, 0) + ","
                + input("VIEW", "TEST_DB.TEST_SCHEMA.V748V", 2, 3, 0, 0) + "] ;; V748B|MANUAL|INCREMENTAL|["
                + input("VIEW", "TEST_DB.TEST_SCHEMA.V748V", 1, 3, 1, 2) + "]",
            "SELECT NAME, REFRESH_TRIGGER, REFRESH_ACTION, INPUTS_WITH_CHANGED_DATA FROM "
                + "TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME_PREFIX => 'TEST_DB.TEST_SCHEMA.V748')) "
                + "ORDER BY REFRESH_START_TIME, NAME");
    }
}
