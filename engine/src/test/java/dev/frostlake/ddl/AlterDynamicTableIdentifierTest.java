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

package dev.frostlake.ddl;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ALTER DYNAMIC TABLE names its table as ALTER TABLE does, by IDENTIFIER() as well, for every action; and a manual
 * refresh whose query reads a relation that is gone is refused as a failed refresh: the table's query expanded as a
 * view's body is, the role the table runs as noted, the moment the refresh was for told (all live-verified).
 */
public class AlterDynamicTableIdentifierTest extends MeasuredCellsTestSupport {

    private static final String DONE = "Statement executed successfully.";

    /** The lag and warehouse a dynamic table is created with here. */
    private String options() {
        final Object warehouse = engine.executeQuery("SELECT CURRENT_WAREHOUSE()").getRows().get(0).getValue(0);
        return " TARGET_LAG = '1 day' WAREHOUSE = " + (warehouse == null ? "COMPUTE_WH" : warehouse)
            + " AS SELECT a FROM ";
    }

    @Test
    public void aDynamicTableIsNamedByIdentifierForEveryAction() {
        engine.execute("CREATE OR REPLACE TABLE pd_src (a INT)");
        engine.execute("INSERT INTO pd_src VALUES (1)");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE pd_dt" + options() + "pd_src");
        assertAnswer(DONE, "ALTER DYNAMIC TABLE IDENTIFIER('pd_dt') RENAME TO pd_dt7");
        assertAnswer(DONE, "ALTER DYNAMIC TABLE IDENTIFIER('pd_dt7') SUSPEND");
        assertAnswer(DONE, "ALTER DYNAMIC TABLE IDENTIFIER('pd_dt7') RESUME");
        assertAnswer(DONE, "ALTER DYNAMIC TABLE IDENTIFIER('pd_dt7') SET COMMENT = 'c'");
        assertAnswer(DONE, "ALTER DYNAMIC TABLE IDENTIFIER('test_db.test_schema.pd_dt7') SUSPEND");
        assertAnswer("REFUSED SQL compilation error: | Dynamic table 'TEST_DB.TEST_SCHEMA.NOSUCH_DT' does not exist or"
            + " not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE"
            + " TEST_DB.TEST_SCHEMA.NOSUCH_DT.", "ALTER DYNAMIC TABLE IDENTIFIER('nosuch_dt') SUSPEND");
        assertAnswer(DONE, "ALTER DYNAMIC TABLE IF EXISTS IDENTIFIER('nosuch_dt') SUSPEND");
    }

    @Test
    public void aManualRefreshOverASourceThatIsGoneIsAFailedRefresh() {
        engine.execute("CREATE OR REPLACE TABLE pd_src (a INT)");
        engine.execute("INSERT INTO pd_src VALUES (1)");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE pd_dt7" + options() + "pd_src");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE pd_dtd" + options() + "pd_dt7");
        assertAnswer(DONE, "ALTER DYNAMIC TABLE pd_dt7 RENAME TO pd_dtu");
        // The moment the refresh was for and the role the table runs as differ from run to run.
        assertEquals("REFUSED SQL compilation error: Failed to refresh dynamic table with refresh_trigger MANUAL at"
                + " data_timestamp <MS> because of the error: SQL compilation error: Target table failed to refresh:"
                + " SQL compilation error: | Failure during expansion of view 'PD_DTD': SQL compilation error: |"
                + " Object 'TEST_DB.TEST_SCHEMA.PD_DT7' does not exist or not authorized. Your primary role <ROLE>"
                + " must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.PD_DT7. (Note: the dynamic"
                + " table runs as owner role '<OWNER>'. Ensure this role has access to all referenced objects. See"
                + " https://docs.snowflake.com/en/user-guide/dynamic-tables/troubleshoot-permissions for details.)",
            answer("ALTER DYNAMIC TABLE pd_dtd REFRESH").replaceAll("data_timestamp \\d+", "data_timestamp <MS>")
                .replaceAll("owner role '[^']+'", "owner role '<OWNER>'"));
        // The table keeps the rows of its last refresh.
        assertAnswer("1", "SELECT * FROM pd_dtd");
        assertAnswer(DONE, "ALTER DYNAMIC TABLE pd_dtu RENAME TO pd_dt7");
        assertEquals("TEST_DB.TEST_SCHEMA.PD_DTD|No new data|0",
            answer("ALTER DYNAMIC TABLE pd_dtd REFRESH").replaceAll("\\|[^|]*$", ""));
    }
}
