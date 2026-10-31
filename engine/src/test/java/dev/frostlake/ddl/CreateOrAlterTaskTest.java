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
 * CREATE OR ALTER TASK creates a task that is not there and changes one that is to exactly what the statement
 * writes: a schedule, a comment, a predecessor, a condition or a parameter the statement leaves out is unset, while
 * the task keeps its state (live-verified). A warehouse that does not exist is refused and the task left as it was;
 * a root task that runs is not changed; IF NOT EXISTS is no part of the statement.
 */
public class CreateOrAlterTaskTest extends MeasuredCellsTestSupport {

    private static final String D = "DESCRIBE TASK %s ->> SELECT \"name\", \"schedule\", \"definition\", \"state\", "
        + "\"comment\", \"predecessors\", \"condition\", \"warehouse\" FROM $1";
    private static final String P = "SHOW PARAMETERS LIKE 'USER_TASK_TIMEOUT_MS' IN TASK coa_t ->> "
        + "SELECT \"key\", \"value\", \"level\" FROM $1";

    @Test
    public void aTaskIsCreatedThenChangedToWhatEachStatementWrites() {
        engine.execute("CREATE OR REPLACE TABLE coa_tab (a INT)");
        engine.execute("CREATE OR REPLACE STREAM coa_st ON TABLE coa_tab");
        try {
            assertCells(new String[][] {
                {"CREATE OR ALTER TASK coa_t SCHEDULE = '60 MINUTE' AS SELECT 1", "Task COA_T successfully created."},
                {String.format(D, "coa_t"), "COA_T|60 MINUTE|SELECT 1|suspended||[]|null|null"},
                {"CREATE OR ALTER TASK coa_t SCHEDULE = '30 MINUTE' AS SELECT 2", "Statement executed successfully."},
                {String.format(D, "coa_t"), "COA_T|30 MINUTE|SELECT 2|suspended||[]|null|null"},
                {"CREATE OR ALTER TASK coa_t AS SELECT 3", "Statement executed successfully."},
                {String.format(D, "coa_t"), "COA_T|null|SELECT 3|suspended||[]|null|null"},
                {"CREATE OR ALTER TASK coa_t SCHEDULE = '60 MINUTE' COMMENT = 'c1' AS SELECT 4",
                    "Statement executed successfully."},
                {String.format(D, "coa_t"), "COA_T|60 MINUTE|SELECT 4|suspended|c1|[]|null|null"},
                {"CREATE OR ALTER TASK coa_t SCHEDULE = '60 MINUTE' AS SELECT 4", "Statement executed successfully."},
                {String.format(D, "coa_t"), "COA_T|60 MINUTE|SELECT 4|suspended||[]|null|null"},
                {"CREATE OR ALTER TASK coa_b AFTER coa_t AS SELECT 5", "Task COA_B successfully created."},
                {String.format(D, "coa_b"), "COA_B|null|SELECT 5|suspended||[\"TEST_DB.TEST_SCHEMA.COA_T\"]|null|null"},
                {"CREATE OR ALTER TASK coa_b AS SELECT 5", "Statement executed successfully."},
                {String.format(D, "coa_b"), "COA_B|null|SELECT 5|suspended||[]|null|null"},
                {"CREATE OR ALTER TASK coa_b AFTER coa_t WHEN SYSTEM$STREAM_HAS_DATA('coa_st') AS SELECT 6",
                    "Statement executed successfully."},
                {String.format(D, "coa_b"), "COA_B|null|SELECT 6|suspended||[\"TEST_DB.TEST_SCHEMA.COA_T\"]|"
                    + "SYSTEM$STREAM_HAS_DATA('coa_st')|null"},
                {"CREATE OR ALTER TASK coa_t WAREHOUSE = nosuch_coa_wh SCHEDULE = '60 MINUTE' AS SELECT 4",
                    "REFUSED Nonexistent warehouse NOSUCH_COA_WH was specified."},
                {String.format(D, "coa_t"), "COA_T|60 MINUTE|SELECT 4|suspended||[]|null|null"},
                {"CREATE OR ALTER TASK IF NOT EXISTS coa_t AS SELECT 1",
                    "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'NOT'."},
                {"CREATE OR ALTER TASK coa_t SCHEDULE = '60 MINUTE' USER_TASK_TIMEOUT_MS = 50000 AS SELECT 4",
                    "Statement executed successfully."},
                {P, "USER_TASK_TIMEOUT_MS|50000|TASK"},
                {"CREATE OR ALTER TASK coa_t SCHEDULE = '60 MINUTE' AS SELECT 4", "Statement executed successfully."},
                {P, "USER_TASK_TIMEOUT_MS|3600000|"},
                {"ALTER TASK coa_t RESUME", "Statement executed successfully."},
                {"CREATE OR ALTER TASK coa_t SCHEDULE = '30 MINUTE' AS SELECT 4",
                    "REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.COA_T since that root task "
                    + "is not suspended."},
                {String.format(D, "coa_t"), "COA_T|60 MINUTE|SELECT 4|started||[]|null|null"},
                {"ALTER TASK coa_t SUSPEND", "Statement executed successfully."},
                {"CREATE OR ALTER TASK coa_t SCHEDULE = '60 MINUTE' AS SELECT 7", "Statement executed successfully."},
                {String.format(D, "coa_t"), "COA_T|60 MINUTE|SELECT 7|suspended||[]|null|null"},
            });
        } finally {
            engine.execute("ALTER TASK IF EXISTS coa_t SUSPEND");
            engine.execute("DROP TASK IF EXISTS coa_b");
            engine.execute("DROP TASK IF EXISTS coa_t");
        }
    }
}
