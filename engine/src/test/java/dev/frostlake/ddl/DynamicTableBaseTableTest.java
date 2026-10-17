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
 * A dynamic table's query must read a base table (live-verified): a name another kind holds, an existing dynamic
 * table, a missing warehouse and a body that does not compile are each refused first; a query reading no relation,
 * or only common table expressions, is then refused, and one reading a table anywhere, a subquery included, is not.
 */
public class DynamicTableBaseTableTest extends MeasuredCellsTestSupport {

    @Test
    public void aQueryReadingNoTableIsRefused() {
        engine.execute("CREATE OR REPLACE TABLE kt (a INT)");
        engine.execute("INSERT INTO kt VALUES (1)");
        engine.execute("CREATE OR REPLACE VIEW kv AS SELECT 1 AS a");
        assertCells(new String[][] {
            {"CREATE DYNAMIC TABLE kt TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT 1 AS a",
                "REFUSED SQL compilation error: | Object 'KT' already exists as TABLE"},
            {"CREATE OR REPLACE DYNAMIC TABLE kt TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT 1 AS a",
                "REFUSED SQL compilation error: | Object 'KT' already exists as TABLE"},
            {"CREATE DYNAMIC TABLE IF NOT EXISTS kt TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT 1 AS a",
                "REFUSED SQL compilation error: | Object 'KT' already exists as TABLE"},
            {"CREATE DYNAMIC TABLE kd1 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT a FROM (SELECT 1 AS a)",
                "REFUSED SQL compilation error: Dynamic Tables must have at least one base table."},
            {"CREATE DYNAMIC TABLE kd2 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT * FROM VALUES (1)",
                "REFUSED SQL compilation error: Dynamic Tables must have at least one base table."},
            {"CREATE DYNAMIC TABLE kd3 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT SEQ4() AS s FROM "
                + "TABLE(GENERATOR(ROWCOUNT => 3))",
                "REFUSED SQL compilation error: Dynamic Tables must have at least one base table."},
            {"CREATE DYNAMIC TABLE kd4 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS WITH c AS (SELECT 1 AS "
                + "a) SELECT a FROM c",
                "REFUSED SQL compilation error: Dynamic Tables must have at least one base table."},
            {"CREATE DYNAMIC TABLE kd7 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT 1 AS a FROM nosuch_t",
                "REFUSED SQL compilation error: | Object 'NOSUCH_T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T."},
            {"CREATE DYNAMIC TABLE kd9 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT a FROM kt UNION "
                + "ALL SELECT 1",
                "Dynamic table KD9 successfully created."},
        });
    }

    @Test
    public void theExistingTableTheWarehouseAndTheBodyComeFirst() {
        engine.execute("CREATE OR REPLACE TABLE kt (a INT)");
        engine.execute("INSERT INTO kt VALUES (1)");
        engine.execute("CREATE OR REPLACE VIEW kv AS SELECT 1 AS a");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE kd6 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT "
            + "a FROM kt");
        assertCells(new String[][] {
            {"CREATE DYNAMIC TABLE kd10 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS WITH c AS (SELECT a "
                + "FROM kt) SELECT a FROM c",
                "Dynamic table KD10 successfully created."},
            {"CREATE DYNAMIC TABLE kd11 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS WITH kt AS (SELECT 1 AS "
                + "a) SELECT a FROM kt",
                "REFUSED SQL compilation error: Dynamic Tables must have at least one base table."},
            {"CREATE DYNAMIC TABLE IF NOT EXISTS kd6 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT 1 AS a",
                "KD6 already exists, statement succeeded."},
            {"CREATE DYNAMIC TABLE kd6 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT 1 AS a",
                "REFUSED SQL compilation error: | Object 'KD6' already exists."},
            {"CREATE DYNAMIC TABLE kd12 TARGET_LAG = '1 hour' WAREHOUSE = nosuch_wh AS SELECT 1 AS a",
                "REFUSED Warehouse 'NOSUCH_WH' does not exist."},
            {"CREATE DYNAMIC TABLE kd15 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT nosuch_col AS a",
                "REFUSED SQL compilation error: error line 1 at position 81 | invalid identifier 'NOSUCH_COL'"},
        });
    }
}
