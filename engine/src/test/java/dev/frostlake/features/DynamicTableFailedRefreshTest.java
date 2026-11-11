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

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A dynamic table's refresh that fails is refused in the refresh's own sentence and recorded in the table's refresh
 * history: FAILED, the failure's code in six digits and its message, and ERROR_ONLY lists those rows alone. A base
 * table replaced since the last refresh asks for a reinitialization, which fails while the new table's change
 * tracking is off - REINITIALIZE, no inputs, "Base table ... was replaced." and then "Reinitialization requested by
 * previous refresh attempt." - and succeeds once it is on, replacing every row. A create whose first refresh fails is
 * recorded under the name the table would have had, and no table is made. Every answer is live-verified.
 */
public class DynamicTableFailedRefreshTest extends MeasuredCellsTestSupport {

    private static final String HISTORY = "SELECT NAME, STATE, STATE_CODE, STATE_MESSAGE, REFRESH_TRIGGER, "
        + "REFRESH_ACTION, REINIT_REASON, INPUTS_WITH_CHANGED_DATA IS NULL FROM "
        + "TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME_PREFIX => 'TEST_DB.TEST_SCHEMA.%s'%s, "
        + "RESULT_LIMIT => 10000)) WHERE REFRESH_START_TIME >= $t962 ORDER BY REFRESH_START_TIME, NAME";

    private static final String FAILED = "REFUSED SQL compilation error: Failed to refresh dynamic table with "
        + "refresh_trigger %s at data_timestamp <MS> because of the error: SQL compilation error: Target table "
        + "failed to refresh: %s";

    private static final String UNTRACKED = "SQL compilation error: Change tracking is not enabled or has been "
        + "missing for the time range requested on table 'TEST_DB.TEST_SCHEMA.FB962'.";

    private static final String CREATED = "FD962|SUCCEEDED|SUCCESS|null|CREATION|INCREMENTAL|null|false";
    private static final String DIVIDED = "FD962|FAILED|100051|Division by zero|MANUAL|INCREMENTAL|null|false";
    private static final String REPLACED = "FD962|FAILED|091930|" + UNTRACKED + "|MANUAL|REINITIALIZE|Base table "
        + "'TEST_DB.TEST_SCHEMA.FB962' was replaced.|true";
    private static final String REQUESTED = "FD962|FAILED|091930|" + UNTRACKED + "|MANUAL|REINITIALIZE|"
        + "Reinitialization requested by previous refresh attempt.|true";

    private String warehouse() {
        final Object wh = engine.executeQuery("SELECT CURRENT_WAREHOUSE()").getRows().get(0).getValue(0);
        return wh == null ? "COMPUTE_WH" : String.valueOf(wh);
    }

    private static String history(final String table, final boolean errorsOnly) {
        return String.format(HISTORY, table, errorsOnly ? ", ERROR_ONLY => TRUE" : "");
    }

    /** A refresh's refusal, its data timestamp masked: each refresh dates its data anew. */
    private void assertRefused(final String trigger, final String message, final String sql) {
        assertEquals(String.format(FAILED, trigger, message),
            answer(sql).replaceAll("data_timestamp [0-9]+", "data_timestamp <MS>"), sql);
    }

    private static String rows(final String... rows) {
        return String.join(" ;; ", rows);
    }

    @Test
    public void aFailedRefreshIsRecordedAndAReplacedBaseTableReinitializesTheTable() {
        engine.execute("SET t962 = CURRENT_TIMESTAMP()");
        engine.execute("CREATE OR REPLACE TABLE fb962 (a INT, b INT)");
        engine.execute("INSERT INTO fb962 VALUES (10, 2)");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE fd962 TARGET_LAG = '1 day' WAREHOUSE = " + warehouse()
            + " AS SELECT a / b AS q FROM fb962");
        assertAnswer("1", "INSERT INTO fb962 VALUES (1, 0)");
        assertRefused("MANUAL", "Division by zero", "ALTER DYNAMIC TABLE fd962 REFRESH");
        assertAnswer(rows(CREATED, DIVIDED), history("FD962", false));
        assertAnswer(DIVIDED, history("FD962", true));
        assertRefused("MANUAL", "Division by zero", "ALTER DYNAMIC TABLE fd962 REFRESH");
        assertAnswer(rows(CREATED, DIVIDED, DIVIDED), history("FD962", false));
        assertAnswer("1", "DELETE FROM fb962 WHERE b = 0");
        assertAnswer("No new data|0",
            "ALTER DYNAMIC TABLE fd962 REFRESH ->> SELECT \"statistics\", \"refreshed_dt_count\" FROM $1");
        final String refreshed = "FD962|SUCCEEDED|SUCCESS|null|MANUAL|INCREMENTAL|null|false";
        assertAnswer(rows(CREATED, DIVIDED, DIVIDED, refreshed), history("FD962", false));
        assertAnswer("Table FB962 successfully created.", "CREATE OR REPLACE TABLE fb962 (a INT, b INT)");
        assertAnswer("1", "INSERT INTO fb962 VALUES (20, 4)");
        assertRefused("MANUAL", UNTRACKED, "ALTER DYNAMIC TABLE fd962 REFRESH");
        assertRefused("MANUAL", UNTRACKED, "ALTER DYNAMIC TABLE fd962 REFRESH");
        assertAnswer(rows(CREATED, DIVIDED, DIVIDED, refreshed, REPLACED, REQUESTED), history("FD962", false));
        assertAnswer("Statement executed successfully.", "ALTER TABLE fb962 SET CHANGE_TRACKING = TRUE");
        assertAnswer("{\"insertedRows\":1,\"copiedRows\":0,\"deletedRows\":1}|1",
            "ALTER DYNAMIC TABLE fd962 REFRESH ->> SELECT \"statistics\", \"refreshed_dt_count\" FROM $1");
        assertAnswer("5.000000", "SELECT q FROM fd962 ORDER BY q");
        assertAnswer(rows(CREATED, DIVIDED, DIVIDED, refreshed, REPLACED, REQUESTED,
            "FD962|SUCCEEDED|SUCCESS|null|MANUAL|REINITIALIZE|Reinitialization requested by previous refresh "
                + "attempt.|false"), history("FD962", false));
        assertAnswer(rows(DIVIDED, DIVIDED, REPLACED, REQUESTED), history("FD962", true));
    }

    @Test
    public void aCreateWhoseFirstRefreshFailsIsRecordedAndMakesNoTable() {
        engine.execute("SET t962 = CURRENT_TIMESTAMP()");
        engine.execute("CREATE OR REPLACE TABLE fb962 (a INT, b INT)");
        engine.execute("INSERT INTO fb962 VALUES (2, 0)");
        assertRefused("INITIAL", "Division by zero", "CREATE OR REPLACE DYNAMIC TABLE fz962 TARGET_LAG = '1 day' "
            + "WAREHOUSE = " + warehouse() + " AS SELECT a / b AS q FROM fb962");
        assertAnswer("FZ962|FAILED|100051|Division by zero|CREATION|INCREMENTAL|null|false", history("FZ962", false));
        engine.execute("SHOW DYNAMIC TABLES LIKE 'FZ962'");
        assertAnswer("0", "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
    }
}
