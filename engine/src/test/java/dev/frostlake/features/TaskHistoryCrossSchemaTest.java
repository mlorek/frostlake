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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * INFORMATION_SCHEMA.TASK_HISTORY spans every schema, as in Snowflake: a manually executed task in
 * another schema must be visible from the current one, filterable by database_name / schema_name —
 * the exact polling shape deployment harnesses use to wait for a task's terminal state.
 */
public class TaskHistoryCrossSchemaTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(TaskHistoryCrossSchemaTest.class);

    @Test
    public void manualRunInAnotherSchemaIsVisibleFromCurrentSchema() {
        engine.execute("CREATE SCHEMA task_home");
        engine.execute("CREATE TASK task_home.history_probe SCHEDULE = '1 MINUTE' AS SELECT 1");
        engine.execute("EXECUTE TASK task_home.history_probe");

        // Current schema is still test_schema; the history row must surface anyway, stamped with
        // the task's own database and schema.
        final ResultSet rs = engine.executeQuery("""
            SELECT state, error_code, error_message
            FROM TABLE(information_schema.task_history(task_name=>'HISTORY_PROBE'))
            WHERE database_name = CURRENT_DATABASE()
            AND state NOT IN ('SCHEDULED', 'EXECUTING', 'SKIPPED')
            AND schema_name = 'TASK_HOME'
            ORDER BY query_start_time DESC
            LIMIT 1
            """);
        assertEquals(1, rs.getRows().size(), "the manual run must appear in cross-schema history");
        assertEquals("SUCCEEDED", rs.getRows().get(0).getValue(0));
        logger.info("Cross-schema task history row: {}", rs.getRows().get(0).getValues());
    }

    @Test
    public void failedRunSurfacesItsState() {
        engine.execute("CREATE SCHEMA task_home2");
        engine.execute("CREATE TASK task_home2.broken_probe SCHEDULE = '1 MINUTE' AS INSERT INTO no_such_table VALUES (1)");
        engine.execute("EXECUTE TASK task_home2.broken_probe");

        final ResultSet rs = engine.executeQuery("""
            SELECT state FROM TABLE(information_schema.task_history(task_name=>'BROKEN_PROBE'))
            WHERE schema_name = 'TASK_HOME2'
            """);
        assertTrue(rs.getRows().size() >= 1);
        assertEquals("FAILED", rs.getRows().get(0).getValue(0));
    }
}
