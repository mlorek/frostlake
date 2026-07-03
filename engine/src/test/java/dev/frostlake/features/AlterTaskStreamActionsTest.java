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
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.Task;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ALTER TASK … SET / UNSET / MODIFY and ALTER STREAM … UNSET COMMENT — previously the grammar only accepted
 * RESUME/SUSPEND (task) and SET COMMENT (stream), so these valid Snowflake statements were parse errors.
 */
public class AlterTaskStreamActionsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (id INTEGER)");
        engine.execute("CREATE TASK tk WAREHOUSE = wh SCHEDULE = '5 MINUTE' AS SELECT 1");
        engine.execute("CREATE STREAM st ON TABLE t COMMENT = 'orig'");
    }

    private Schema schema() {
        return engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
    }

    private Task task() {
        return schema().getTask("tk");
    }

    @Test
    public void setScheduleAndWarehouse() {
        engine.execute("ALTER TASK tk SET SCHEDULE = '10 MINUTE'");
        engine.execute("ALTER TASK tk SET WAREHOUSE = wh2");
        assertEquals("10 MINUTE", task().getSchedule());
        assertEquals("wh2", task().getWarehouse());
    }

    @Test
    public void setMultipleOptionsInOneStatement() {
        engine.execute("ALTER TASK tk SET COMMENT = 'hello' USER_TASK_TIMEOUT_MS = 5000");
        assertEquals("hello", task().getComment());
        assertEquals(5000L, task().getUserTaskTimeoutMs());
    }

    @Test
    public void setTaskParameters() {
        engine.execute("ALTER TASK tk SET SUSPEND_TASK_AFTER_NUM_FAILURES = 3 "
            + "TASK_AUTO_RETRY_ATTEMPTS = 2 ALLOW_OVERLAPPING_EXECUTION = TRUE");
        assertEquals(3, task().getSuspendTaskAfterNumFailures());
        assertEquals(2, task().getTaskAutoRetryAttempts());
        assertEquals(true, task().isAllowOverlappingExecution());
    }

    @Test
    public void unsetResetsToDefault() {
        engine.execute("ALTER TASK tk SET COMMENT = 'hello' USER_TASK_TIMEOUT_MS = 5000");
        engine.execute("ALTER TASK tk UNSET COMMENT, USER_TASK_TIMEOUT_MS");
        assertNull(task().getComment());
        assertEquals(3600000L, task().getUserTaskTimeoutMs());   // back to the 1-hour default
    }

    @Test
    public void modifyAsChangesTheBody() {
        engine.execute("ALTER TASK tk MODIFY AS SELECT 42");
        assertEquals("SELECT 42", task().getSqlStatement().trim());
    }

    @Test
    public void modifyWhenChangesTheCondition() {
        engine.execute("ALTER TASK tk MODIFY WHEN TRUE");
        assertEquals("TRUE", task().getCondition().trim());
    }

    @Test
    public void resumeSuspendStillWork() {
        engine.execute("ALTER TASK tk RESUME");
        engine.execute("ALTER TASK tk SUSPEND");
        // No exception ⇒ the pre-existing actions still parse and apply alongside the new ones.
    }

    @Test
    public void streamUnsetComment() {
        final Stream stream = schema().getStream("ST");
        assertEquals("orig", stream.getComment());
        engine.execute("ALTER STREAM st UNSET COMMENT");
        assertNull(schema().getStream("ST").getComment());
    }

    @Test
    public void streamSetCommentStillWorks() {
        engine.execute("ALTER STREAM st SET COMMENT = 'updated'");
        assertEquals("updated", schema().getStream("ST").getComment());
    }

    @Test
    public void unsetScheduleClearsIt() {
        engine.execute("ALTER TASK tk UNSET SCHEDULE");
        assertNull(task().getSchedule());
        assertFalse(task().getSchedule() != null);
    }
}
