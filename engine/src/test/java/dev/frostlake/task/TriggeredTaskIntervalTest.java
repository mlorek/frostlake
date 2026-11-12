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
package dev.frostlake.task;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A serverless task its condition triggers is resumed or run on demand only once it has a TARGET_COMPLETION_INTERVAL;
 * a task with a warehouse or a schedule, and one given the interval, runs (all live-verified).
 */
public class TriggeredTaskIntervalTest extends MeasuredCellsTestSupport {

    private static final String REFUSED = "REFUSED TARGET_COMPLETION_INTERVAL must be set for serverless triggered "
        + "task TEST_DB.TEST_SCHEMA.TTI_T to resume or execute.";

    @Test
    public void aTriggeredServerlessTaskNeedsItsInterval() {
        engine.execute("CREATE OR REPLACE TABLE tti_src (id INT)");
        engine.execute("CREATE OR REPLACE STREAM tti_st ON TABLE tti_src");
        try {
            assertCells(new String[][] {
                {"CREATE OR REPLACE TASK tti_t WHEN SYSTEM$STREAM_HAS_DATA('tti_st') AS SELECT 1",
                    "Task TTI_T successfully created."},
                {"EXECUTE TASK tti_t", REFUSED},
                {"ALTER TASK tti_t RESUME", REFUSED},
                {"SHOW TASKS LIKE 'TTI_T' ->> SELECT \"state\" FROM $1", "suspended"},
                {"ALTER TASK tti_t SET TARGET_COMPLETION_INTERVAL = '15 MINUTE'", "Statement executed successfully."},
                {"EXECUTE TASK tti_t", "Task TTI_T is scheduled to run immediately."},
                {"CREATE OR REPLACE TASK tti_s SCHEDULE = '60 MINUTE' WHEN SYSTEM$STREAM_HAS_DATA('tti_st') AS "
                    + "SELECT 1", "Task TTI_S successfully created."},
                {"EXECUTE TASK tti_s", "Task TTI_S is scheduled to run immediately."},
                {"CREATE OR REPLACE TASK tti_p AS SELECT 1", "Task TTI_P successfully created."},
                {"EXECUTE TASK tti_p", "Task TTI_P is scheduled to run immediately."},
            });
        } finally {
            engine.execute("DROP TASK IF EXISTS tti_t");
            engine.execute("DROP TASK IF EXISTS tti_s");
            engine.execute("DROP TASK IF EXISTS tti_p");
        }
    }
}
