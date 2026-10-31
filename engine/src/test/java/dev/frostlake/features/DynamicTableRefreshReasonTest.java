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
 * Why a dynamic table created with REFRESH_MODE = AUTO refreshes in FULL: the CREATE's status and SHOW DYNAMIC
 * TABLES' refresh_mode and refresh_mode_reason, for a subquery, a view's row generator, a window without
 * PARTITION BY, a non-deterministic function and a LIMIT, and INCREMENTAL for every other query (all
 * live-verified).
 */
public class DynamicTableRefreshReasonTest extends MeasuredCellsTestSupport {

    @Test
    public void refreshReasons() {
        engine.execute("CREATE OR REPLACE TABLE kt (a INT)");
        engine.execute("INSERT INTO kt VALUES (1)");
        engine.execute("CREATE OR REPLACE VIEW kv AS SELECT 1 AS a");
        assertAnswer("Dynamic table KD2047_1 successfully created. FULL refresh mode was selected because: Change "
                + "tracking is not supported on queries with subquery expressions.",
            "CREATE OR REPLACE DYNAMIC TABLE kd2047_1 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT 1 AS a "
                + "WHERE EXISTS (SELECT 1 FROM kt)");
        assertAnswer("FULL|Change tracking is not supported on queries with subquery expressions.",
            "SHOW DYNAMIC TABLES LIKE 'KD2047_1' ->> SELECT \"refresh_mode\", \"refresh_mode_reason\" FROM $1");
        assertAnswer("KD2047_1 successfully dropped.", "DROP DYNAMIC TABLE IF EXISTS kd2047_1");
        assertAnswer("Dynamic table KD2047_2 successfully created. FULL refresh mode was selected because: Change "
                + "tracking is not supported on queries with subquery expressions.",
            "CREATE OR REPLACE DYNAMIC TABLE kd2047_2 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT (SELECT "
                + "MAX(a) FROM kt) AS m");
        assertAnswer("FULL|Change tracking is not supported on queries with subquery expressions.",
            "SHOW DYNAMIC TABLES LIKE 'KD2047_2' ->> SELECT \"refresh_mode\", \"refresh_mode_reason\" FROM $1");
        assertAnswer("KD2047_2 successfully dropped.", "DROP DYNAMIC TABLE IF EXISTS kd2047_2");
        assertAnswer("Dynamic table KD2047_3 successfully created. FULL refresh mode was selected because: Change "
                + "tracking is not supported on queries with subquery expressions.",
            "CREATE OR REPLACE DYNAMIC TABLE kd2047_3 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT a FROM "
                + "kt WHERE a IN (SELECT 1)");
        assertAnswer("FULL|Change tracking is not supported on queries with subquery expressions.",
            "SHOW DYNAMIC TABLES LIKE 'KD2047_3' ->> SELECT \"refresh_mode\", \"refresh_mode_reason\" FROM $1");
        assertAnswer("KD2047_3 successfully dropped.", "DROP DYNAMIC TABLE IF EXISTS kd2047_3");
        assertAnswer("Dynamic table KD2047_4 successfully created. FULL refresh mode was selected because: Change "
                + "tracking is not supported on queries with row generators.",
            "CREATE OR REPLACE DYNAMIC TABLE kd2047_4 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT a FROM "
                + "kv");
        assertAnswer("FULL|Change tracking is not supported on queries with row generators.",
            "SHOW DYNAMIC TABLES LIKE 'KD2047_4' ->> SELECT \"refresh_mode\", \"refresh_mode_reason\" FROM $1");
        assertAnswer("KD2047_4 successfully dropped.", "DROP DYNAMIC TABLE IF EXISTS kd2047_4");
        assertAnswer("Dynamic table KD2047_5 successfully created.",
            "CREATE OR REPLACE DYNAMIC TABLE kd2047_5 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT a FROM "
                + "kt");
        assertAnswer("INCREMENTAL|null",
            "SHOW DYNAMIC TABLES LIKE 'KD2047_5' ->> SELECT \"refresh_mode\", \"refresh_mode_reason\" FROM $1");
        assertAnswer("KD2047_5 successfully dropped.", "DROP DYNAMIC TABLE IF EXISTS kd2047_5");
        assertAnswer("Dynamic table KD2047_6 successfully created.",
            "CREATE OR REPLACE DYNAMIC TABLE kd2047_6 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT a, "
                + "COUNT(*) AS c FROM kt GROUP BY a");
        assertAnswer("INCREMENTAL|null",
            "SHOW DYNAMIC TABLES LIKE 'KD2047_6' ->> SELECT \"refresh_mode\", \"refresh_mode_reason\" FROM $1");
        assertAnswer("KD2047_6 successfully dropped.", "DROP DYNAMIC TABLE IF EXISTS kd2047_6");
        assertAnswer("Dynamic table KD2047_7 successfully created.",
            "CREATE OR REPLACE DYNAMIC TABLE kd2047_7 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT k1.a "
                + "FROM kt k1 JOIN kt k2 ON k1.a = k2.a");
        assertAnswer("INCREMENTAL|null",
            "SHOW DYNAMIC TABLES LIKE 'KD2047_7' ->> SELECT \"refresh_mode\", \"refresh_mode_reason\" FROM $1");
        assertAnswer("KD2047_7 successfully dropped.", "DROP DYNAMIC TABLE IF EXISTS kd2047_7");
        assertAnswer("REFUSED SQL compilation error: Dynamic Tables must have at least one base table.",
            "CREATE OR REPLACE DYNAMIC TABLE kd2047_8 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT 1 AS a");
        assertAnswer("",
            "SHOW DYNAMIC TABLES LIKE 'KD2047_8' ->> SELECT \"refresh_mode\", \"refresh_mode_reason\" FROM $1");
        assertAnswer("Drop statement executed successfully (KD2047_8 already dropped).",
            "DROP DYNAMIC TABLE IF EXISTS kd2047_8");
        assertAnswer("Dynamic table KD2047_9 successfully created.",
            "CREATE OR REPLACE DYNAMIC TABLE kd2047_9 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT a FROM "
                + "kt UNION SELECT a FROM kt");
        assertAnswer("INCREMENTAL|null",
            "SHOW DYNAMIC TABLES LIKE 'KD2047_9' ->> SELECT \"refresh_mode\", \"refresh_mode_reason\" FROM $1");
        assertAnswer("KD2047_9 successfully dropped.", "DROP DYNAMIC TABLE IF EXISTS kd2047_9");
        assertAnswer("Dynamic table KD2047_10 successfully created. FULL refresh mode was selected because: Change "
                + "tracking is not supported on queries with LIMIT.",
            "CREATE OR REPLACE DYNAMIC TABLE kd2047_10 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT a FROM "
                + "kt LIMIT 1");
        assertAnswer("FULL|Change tracking is not supported on queries with LIMIT.",
            "SHOW DYNAMIC TABLES LIKE 'KD2047_10' ->> SELECT \"refresh_mode\", \"refresh_mode_reason\" FROM $1");
        assertAnswer("KD2047_10 successfully dropped.", "DROP DYNAMIC TABLE IF EXISTS kd2047_10");
        assertAnswer("Dynamic table KD2047_11 successfully created. FULL refresh mode was selected because: This "
                + "dynamic table contains a complex query. Refresh mode has been automatically set to FULL for more "
                + "predictable performance. To use INCREMENTAL, re-create the dynamic table with "
                + "REFRESH_MODE=INCREMENTAL. See "
                + "https://docs.snowflake.com/user-guide/dynamic-table-performance-guide for details.",
            "CREATE OR REPLACE DYNAMIC TABLE kd2047_11 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT a, "
                + "ROW_NUMBER() OVER (ORDER BY a) AS r FROM kt");
        assertAnswer("FULL|This dynamic table contains a complex query. Refresh mode has been automatically set to "
                + "FULL for more predictable performance. To use INCREMENTAL, re-create the dynamic table with "
                + "REFRESH_MODE=INCREMENTAL. See "
                + "https://docs.snowflake.com/user-guide/dynamic-table-performance-guide for details.",
            "SHOW DYNAMIC TABLES LIKE 'KD2047_11' ->> SELECT \"refresh_mode\", \"refresh_mode_reason\" FROM $1");
        assertAnswer("KD2047_11 successfully dropped.", "DROP DYNAMIC TABLE IF EXISTS kd2047_11");
        assertAnswer("Dynamic table KD2047_12 successfully created. FULL refresh mode was selected because: Query "
                + "contains the function 'CURRENT_TIMESTAMP', but change tracking is not supported on queries with "
                + "non-deterministic functions. For more information, see "
                + "https://docs.snowflake.com/user-guide/dynamic-tables-supported-queries"
                + "#supported-non-deterministic-functions.",
            "CREATE OR REPLACE DYNAMIC TABLE kd2047_12 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT "
                + "CURRENT_TIMESTAMP() AS t, a FROM kt");
        assertAnswer("FULL|Query contains the function 'CURRENT_TIMESTAMP', but change tracking is not supported on "
                + "queries with non-deterministic functions. For more information, see "
                + "https://docs.snowflake.com/user-guide/dynamic-tables-supported-queries"
                + "#supported-non-deterministic-functions.",
            "SHOW DYNAMIC TABLES LIKE 'KD2047_12' ->> SELECT \"refresh_mode\", \"refresh_mode_reason\" FROM $1");
        assertAnswer("KD2047_12 successfully dropped.", "DROP DYNAMIC TABLE IF EXISTS kd2047_12");
        assertAnswer("Dynamic table KD2047_13 successfully created.",
            "CREATE OR REPLACE DYNAMIC TABLE kd2047_13 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT "
                + "f.value AS v FROM kt, LATERAL FLATTEN(INPUT => ARRAY_CONSTRUCT(a)) f");
        assertAnswer("INCREMENTAL|null",
            "SHOW DYNAMIC TABLES LIKE 'KD2047_13' ->> SELECT \"refresh_mode\", \"refresh_mode_reason\" FROM $1");
        assertAnswer("KD2047_13 successfully dropped.", "DROP DYNAMIC TABLE IF EXISTS kd2047_13");
        assertAnswer("Dynamic table KD2047_14 successfully created. FULL refresh mode was selected because: Query "
                + "contains the function 'RANDOM', but change tracking is not supported on queries with "
                + "non-deterministic functions. For more information, see "
                + "https://docs.snowflake.com/user-guide/dynamic-tables-supported-queries"
                + "#supported-non-deterministic-functions.",
            "CREATE OR REPLACE DYNAMIC TABLE kd2047_14 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT "
                + "RANDOM() AS r FROM kt");
        assertAnswer("FULL|Query contains the function 'RANDOM', but change tracking is not supported on queries with "
                + "non-deterministic functions. For more information, see "
                + "https://docs.snowflake.com/user-guide/dynamic-tables-supported-queries"
                + "#supported-non-deterministic-functions.",
            "SHOW DYNAMIC TABLES LIKE 'KD2047_14' ->> SELECT \"refresh_mode\", \"refresh_mode_reason\" FROM $1");
        assertAnswer("KD2047_14 successfully dropped.", "DROP DYNAMIC TABLE IF EXISTS kd2047_14");
        assertAnswer("Dynamic table KD2047_15 successfully created.",
            "CREATE OR REPLACE DYNAMIC TABLE kd2047_15 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT a FROM "
                + "kt UNION ALL SELECT a FROM kt");
        assertAnswer("INCREMENTAL|null",
            "SHOW DYNAMIC TABLES LIKE 'KD2047_15' ->> SELECT \"refresh_mode\", \"refresh_mode_reason\" FROM $1");
        assertAnswer("KD2047_15 successfully dropped.", "DROP DYNAMIC TABLE IF EXISTS kd2047_15");
    }
}
