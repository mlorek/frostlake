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
 * ALTER DYNAMIC TABLE ... REFRESH refreshes each dynamic table the table reads first, suspended or not, each once
 * (live-verified): refreshed_dt_count counts them all with the table when the table has something new, and none
 * when it has not, and each records what changed in its own rows. A stream on a dynamic table lists
 * METADATA$ROW_ID before METADATA$ACTION, and a query that reads a dynamic table that never refreshed is refused
 * wherever the read stands, a subquery no row reaches and a view included.
 */
public class DynamicTableRefreshUpstreamTest extends MeasuredCellsTestSupport {

    @Test
    public void aRefreshRefreshesWhatItReadsFirst() {
        engine.execute("CREATE OR REPLACE TABLE ds1 (a INT)");
        engine.execute("INSERT INTO ds1 VALUES (1)");
        final Object wh = engine.executeQuery("SELECT CURRENT_WAREHOUSE()").getRows().get(0).getValue(0);
        final String warehouse = wh == null ? "COMPUTE_WH" : String.valueOf(wh);
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE d1 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " AS SELECT a FROM ds1");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE d2 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " AS SELECT a FROM d1");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE d3 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " AS SELECT a FROM d2");
        assertCells(new String[][] {
            {"ALTER DYNAMIC TABLE d3 REFRESH ->> SELECT \"statistics\", \"refreshed_dt_count\" FROM $1",
                "No new data|0"},
            {"INSERT INTO ds1 VALUES (2)", "1"},
            {"ALTER DYNAMIC TABLE d3 REFRESH ->> SELECT \"statistics\", \"refreshed_dt_count\" FROM $1",
                "{\"insertedRows\":1,\"copiedRows\":0,\"deletedRows\":0}|3"},
            {"ALTER DYNAMIC TABLE d3 REFRESH ->> SELECT \"statistics\", \"refreshed_dt_count\" FROM $1",
                "No new data|0"},
            {"INSERT INTO ds1 VALUES (3)", "1"},
            {"ALTER DYNAMIC TABLE d1 REFRESH ->> SELECT \"statistics\", \"refreshed_dt_count\" FROM $1",
                "{\"insertedRows\":1,\"copiedRows\":0,\"deletedRows\":0}|1"},
            {"ALTER DYNAMIC TABLE d3 REFRESH ->> SELECT \"statistics\", \"refreshed_dt_count\" FROM $1",
                "{\"insertedRows\":1,\"copiedRows\":0,\"deletedRows\":0}|3"},
            {"INSERT INTO ds1 VALUES (4)", "1"},
            {"ALTER DYNAMIC TABLE d2 REFRESH ->> SELECT \"statistics\", \"refreshed_dt_count\" FROM $1",
                "{\"insertedRows\":1,\"copiedRows\":0,\"deletedRows\":0}|2"},
            {"ALTER DYNAMIC TABLE d3 REFRESH ->> SELECT \"statistics\", \"refreshed_dt_count\" FROM $1",
                "{\"insertedRows\":1,\"copiedRows\":0,\"deletedRows\":0}|3"},
            {"ALTER DYNAMIC TABLE d1 REFRESH ->> SELECT \"statistics\", \"refreshed_dt_count\" FROM $1",
                "No new data|0"},
            {"CREATE OR REPLACE STREAM sk ON DYNAMIC TABLE d1", "Stream SK successfully created."},
            {"CREATE OR REPLACE STREAM st ON TABLE ds1", "Stream ST successfully created."},
            {"INSERT INTO ds1 VALUES (5)", "1"},
            {"ALTER DYNAMIC TABLE d1 REFRESH ->> SELECT \"refreshed_dt_count\" FROM $1", "1"},
            {"CREATE OR REPLACE TABLE sk_cols AS SELECT * FROM sk WHERE FALSE", "Table SK_COLS successfully created."},
            {"SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'SK_COLS' ORDER BY "
                + "ORDINAL_POSITION",
                "A ;; METADATA$ROW_ID ;; METADATA$ACTION ;; METADATA$ISUPDATE"},
            {"CREATE OR REPLACE TABLE st_cols AS SELECT * FROM st WHERE FALSE", "Table ST_COLS successfully created."},
            {"SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'ST_COLS' ORDER BY "
                + "ORDINAL_POSITION",
                "A ;; METADATA$ACTION ;; METADATA$ISUPDATE ;; METADATA$ROW_ID"},
            {"SELECT a, METADATA$ACTION, METADATA$ISUPDATE FROM sk ORDER BY a", ""},
        });
    }

    @Test
    public void aReadOfAnUninitializedTableIsJudgedAsTheQueryCompiles() {
        engine.execute("CREATE OR REPLACE TABLE ds2 (a INT)");
        engine.execute("INSERT INTO ds2 VALUES (1)");
        final Object wh = engine.executeQuery("SELECT CURRENT_WAREHOUSE()").getRows().get(0).getValue(0);
        final String warehouse = wh == null ? "COMPUTE_WH" : String.valueOf(wh);
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE du TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " INITIALIZE = ON_SCHEDULE AS SELECT a FROM ds2");
        engine.execute("CREATE OR REPLACE VIEW du_v AS SELECT a FROM du");
        assertCells(new String[][] {
            {"SELECT 1 FROM ds2 WHERE FALSE AND EXISTS (SELECT 1 FROM du)",
                "REFUSED Dynamic Table 'TEST_DB.TEST_SCHEMA.DU' is not initialized. Please run a manual refresh or "
                + "wait for a scheduled refresh before querying."},
            {"SELECT 1 FROM ds2 WHERE 1 = 0 AND a IN (SELECT a FROM du)",
                "REFUSED Dynamic Table 'TEST_DB.TEST_SCHEMA.DU' is not initialized. Please run a manual refresh or "
                + "wait for a scheduled refresh before querying."},
            {"SELECT CASE WHEN FALSE THEN (SELECT COUNT(*) FROM du) END",
                "REFUSED Dynamic Table 'TEST_DB.TEST_SCHEMA.DU' is not initialized. Please run a manual refresh or "
                + "wait for a scheduled refresh before querying."},
            {"SELECT 1 FROM ds2 WHERE FALSE AND EXISTS (SELECT 1 FROM du_v)",
                "REFUSED Dynamic Table 'TEST_DB.TEST_SCHEMA.DU' is not initialized. Please run a manual refresh or "
                + "wait for a scheduled refresh before querying."},
            {"SELECT * FROM du WHERE FALSE",
                "REFUSED Dynamic Table 'TEST_DB.TEST_SCHEMA.DU' is not initialized. Please run a manual refresh or "
                + "wait for a scheduled refresh before querying."},
            {"SELECT * FROM du LIMIT 0",
                "REFUSED Dynamic Table 'TEST_DB.TEST_SCHEMA.DU' is not initialized. Please run a manual refresh or "
                + "wait for a scheduled refresh before querying."},
            {"SELECT 1 FROM ds2 LEFT JOIN du ON FALSE",
                "REFUSED Dynamic Table 'TEST_DB.TEST_SCHEMA.DU' is not initialized. Please run a manual refresh or "
                + "wait for a scheduled refresh before querying."},
            {"SELECT (SELECT a FROM du) FROM ds2 WHERE FALSE",
                "REFUSED Dynamic Table 'TEST_DB.TEST_SCHEMA.DU' is not initialized. Please run a manual refresh or "
                + "wait for a scheduled refresh before querying."},
            {"SELECT 1 FROM ds2 WHERE a = 2 AND EXISTS (SELECT 1 FROM du)",
                "REFUSED Dynamic Table 'TEST_DB.TEST_SCHEMA.DU' is not initialized. Please run a manual refresh or "
                + "wait for a scheduled refresh before querying."},
            {"SELECT 1 WHERE FALSE UNION ALL SELECT a FROM du WHERE FALSE",
                "REFUSED Dynamic Table 'TEST_DB.TEST_SCHEMA.DU' is not initialized. Please run a manual refresh or "
                + "wait for a scheduled refresh before querying."},
        });
    }

    @Test
    public void theCountOverManyUpstreamTables() {
        engine.execute("CREATE OR REPLACE TABLE es1 (a INT)");
        engine.execute("CREATE OR REPLACE TABLE es2 (a INT)");
        engine.execute("INSERT INTO es1 VALUES (1)");
        engine.execute("INSERT INTO es2 VALUES (1)");
        final Object wh = engine.executeQuery("SELECT CURRENT_WAREHOUSE()").getRows().get(0).getValue(0);
        final String warehouse = wh == null ? "COMPUTE_WH" : String.valueOf(wh);
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE e1 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " AS SELECT a FROM es1");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE e2 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " AS SELECT a FROM es2");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE e3 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " AS SELECT a FROM e1 UNION ALL SELECT a FROM e2");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE e4 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " AS SELECT a FROM e1 WHERE a > 100");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE e5 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " AS SELECT a FROM e3");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE e6 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " AS SELECT a FROM es1");
        assertCells(new String[][] {
            {"INSERT INTO es1 VALUES (2)", "1"},
            {"ALTER DYNAMIC TABLE e4 REFRESH ->> SELECT \"statistics\", \"refreshed_dt_count\" FROM $1",
                "No new data|0"},
            {"ALTER DYNAMIC TABLE e6 REFRESH ->> SELECT \"statistics\", \"refreshed_dt_count\" FROM $1",
                "{\"insertedRows\":1,\"copiedRows\":0,\"deletedRows\":0}|1"},
            {"ALTER DYNAMIC TABLE e3 REFRESH ->> SELECT \"statistics\", \"refreshed_dt_count\" FROM $1",
                "{\"insertedRows\":1,\"copiedRows\":0,\"deletedRows\":0}|3"},
            {"INSERT INTO es2 VALUES (2)", "1"},
            {"ALTER DYNAMIC TABLE e5 REFRESH ->> SELECT \"statistics\", \"refreshed_dt_count\" FROM $1",
                "{\"insertedRows\":2,\"copiedRows\":0,\"deletedRows\":0}|4"},
            {"INSERT INTO es1 VALUES (3)", "1"},
            {"INSERT INTO es2 VALUES (3)", "1"},
            {"ALTER DYNAMIC TABLE e3 REFRESH ->> SELECT \"statistics\", \"refreshed_dt_count\" FROM $1",
                "{\"insertedRows\":2,\"copiedRows\":0,\"deletedRows\":0}|3"},
            {"ALTER DYNAMIC TABLE e1 REFRESH ->> SELECT \"statistics\", \"refreshed_dt_count\" FROM $1",
                "No new data|0"},
            {"ALTER DYNAMIC TABLE e2 REFRESH ->> SELECT \"statistics\", \"refreshed_dt_count\" FROM $1",
                "No new data|0"},
            {"INSERT INTO es1 VALUES (4)", "1"},
            {"ALTER DYNAMIC TABLE e5 REFRESH ->> SELECT \"statistics\", \"refreshed_dt_count\" FROM $1",
                "{\"insertedRows\":3,\"copiedRows\":0,\"deletedRows\":0}|4"},
            {"SELECT COUNT(*) FROM e1", "4"},
            {"SELECT COUNT(*) FROM e3", "7"},
            {"ALTER DYNAMIC TABLE e1 SUSPEND", "Statement executed successfully."},
            {"INSERT INTO es1 VALUES (5)", "1"},
            {"ALTER DYNAMIC TABLE e3 REFRESH ->> SELECT \"statistics\", \"refreshed_dt_count\" FROM $1",
                "{\"insertedRows\":1,\"copiedRows\":0,\"deletedRows\":0}|3"},
            {"SELECT COUNT(*) FROM e1", "5"},
            {"ALTER DYNAMIC TABLE e1 RESUME", "Statement executed successfully."},
            {"ALTER DYNAMIC TABLE e3 REFRESH ->> SELECT \"statistics\", \"refreshed_dt_count\" FROM $1",
                "No new data|0"},
            {"SELECT COUNT(*) FROM e1", "5"},
            {"SELECT COUNT(*) FROM e3", "8"},
        });
    }
}
