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

/**
 * ALTER EVENT TABLE is no statement: its line names the TABLE after the EVENT, and an event table is renamed by
 * ALTER TABLE. A materialized view's new name, when bare, is placed in the session's schema, as a created name is,
 * so the view moves there; ALTER VIEW does not reach a materialized view (all live-verified).
 */
public class EventTableAndViewRenameTest extends MeasuredCellsTestSupport {

    @Test
    public void theRenamesReadTheirNamesAsTheAccountDoes() {
        engine.execute("CREATE OR REPLACE SCHEMA test_db.crw_o");
        engine.execute("CREATE OR REPLACE EVENT TABLE test_db.crw_o.et");
        engine.execute("CREATE OR REPLACE TABLE test_db.crw_o.bt (x INT)");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW test_db.crw_o.mv AS SELECT x FROM test_db.crw_o.bt");
        engine.execute("USE SCHEMA test_db.test_schema");
        assertCells(new String[][] {
            {"ALTER EVENT TABLE test_db.crw_o.et RENAME TO test_db.crw_o.et_r",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'TABLE'."},
            {"ALTER EVENT TABLE et RENAME TO et_r",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'TABLE'."},
            {"ALTER TABLE test_db.crw_o.et RENAME TO test_db.crw_o.et_r", "Statement executed successfully."},
            {"ALTER MATERIALIZED VIEW test_db.crw_o.mv RENAME TO mv_r", "Statement executed successfully."},
        });
        engine.execute("SHOW MATERIALIZED VIEWS IN DATABASE test_db");
        assertCells(new String[][] {
            {"SELECT \"schema_name\", \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 2",
                "TEST_SCHEMA|MV_R"},
            {"ALTER VIEW test_db.crw_o.mv_r RENAME TO mv_s",
                "REFUSED SQL compilation error: | View 'TEST_DB.CRW_O.MV_R' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.CRW_O.MV_R."},
        });
        engine.execute("SHOW MATERIALIZED VIEWS IN DATABASE test_db");
        assertCells(new String[][] {
            {"SELECT \"schema_name\", \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 2",
                "TEST_SCHEMA|MV_R"},
        });
    }
}
