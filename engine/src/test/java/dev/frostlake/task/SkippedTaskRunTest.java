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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.TaskRunWait;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A run whose WHEN condition is not TRUE is listed in TASK_HISTORY as SKIPPED: "a task run began, but the optional
 * WHEN parameter in the task definition returned a FALSE value", so its statement did not run; and COMPLETE_TASK_GRAPHS
 * lists no graph run whose root run was skipped (documented). The run carries the condition's error, 0040003. Such a
 * run was recorded nowhere.
 */
public class SkippedTaskRunTest extends BaseDatabaseTest {

    private static String cells(final ResultSet rs) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            if (out.length() > 0) {
                out.append(" ;; ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                out.append(i == 0 ? "" : "|").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    @Test
    public void aRunItsConditionSkipsIsListedSkipped() {
        final String suffix = Long.toString(System.nanoTime() % 100000000L);
        final String task = "SK_T_" + suffix;
        engine.execute("CREATE OR REPLACE TABLE sk_src (id INT)");
        engine.execute("CREATE OR REPLACE STREAM sk_st ON TABLE sk_src");
        // A serverless task its condition triggers runs only within a completion interval (TriggeredTaskInterval).
        engine.execute("CREATE TASK " + task + " TARGET_COMPLETION_INTERVAL = '15 MINUTE' "
            + "WHEN SYSTEM$STREAM_HAS_DATA('sk_st') AS SELECT 1");
        engine.execute("EXECUTE TASK " + task);
        final String history = "SELECT state, condition_text, query_id IS NULL, error_code, error_message FROM "
            + "TABLE(INFORMATION_SCHEMA.TASK_HISTORY(TASK_NAME => '" + task + "')) "
            + "WHERE state NOT IN ('SCHEDULED', 'EXECUTING')";
        TaskRunWait.untilRows(engine, history, 1);
        assertEquals("SKIPPED|SYSTEM$STREAM_HAS_DATA('sk_st')|true|0040003|Conditional expression for task evaluated "
            + "to false.", cells(engine.executeQuery(history)));
        assertEquals("0", cells(engine.executeQuery("SELECT COUNT(*) FROM "
            + "TABLE(INFORMATION_SCHEMA.COMPLETE_TASK_GRAPHS(ROOT_TASK_NAME => '" + task + "'))")));
    }
}
