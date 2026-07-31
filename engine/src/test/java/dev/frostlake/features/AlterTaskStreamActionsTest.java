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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ALTER TASK … SET / UNSET / MODIFY and ALTER STREAM … UNSET COMMENT — previously the grammar only accepted
 * RESUME/SUSPEND (task) and SET COMMENT (stream), so these valid Snowflake statements were parse errors.
 *
 * <p>The statements themselves run on both backends; only the assertions, which read the applied option
 * straight off the in-memory {@code Task} / {@code Stream} model, are embedded-only.
 */
public class AlterTaskStreamActionsTest extends BaseDatabaseTest {

    private static final String MODEL_ASSERTIONS =
        "reads the applied option off the in-memory Task/Stream model (engine.getCatalog()), which live "
        + "Snowflake never populates; the ALTER statements themselves still run against the account";

    @Override
    protected void setupTest() {
        // The tasks below name warehouses explicitly, so the warehouses have to exist: a real account
        // rejects CREATE/ALTER TASK with "Nonexistent warehouse WH was specified."
        engine.execute("CREATE WAREHOUSE IF NOT EXISTS wh WITH WAREHOUSE_SIZE = 'XSMALL' "
            + "AUTO_SUSPEND = 60 INITIALLY_SUSPENDED = TRUE");
        engine.execute("CREATE WAREHOUSE IF NOT EXISTS wh2 WITH WAREHOUSE_SIZE = 'XSMALL' "
            + "AUTO_SUSPEND = 60 INITIALLY_SUSPENDED = TRUE");
        engine.execute("CREATE TABLE t (id INTEGER)");
        engine.execute("CREATE TASK tk WAREHOUSE = wh SCHEDULE = '5 MINUTE' AS SELECT 1");
        engine.execute("CREATE STREAM st ON TABLE t COMMENT = 'orig'");
    }

    @Override
    protected void teardownTest() {
        engine.execute("DROP WAREHOUSE IF EXISTS wh2");
        engine.execute("DROP WAREHOUSE IF EXISTS wh");
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
        Assumptions.assumeFalse(isLiveSnowflake(), MODEL_ASSERTIONS);
        assertEquals("10 MINUTE", task().getSchedule());
        assertEquals("WH2", task().getWarehouse());
    }

    @Test
    public void setMultipleOptionsInOneStatement() {
        engine.execute("ALTER TASK tk SET COMMENT = 'hello' USER_TASK_TIMEOUT_MS = 5000");
        Assumptions.assumeFalse(isLiveSnowflake(), MODEL_ASSERTIONS);
        assertEquals("hello", task().getComment());
        assertEquals(5000L, task().getUserTaskTimeoutMs());
    }

    @Test
    public void setTaskParameters() {
        engine.execute("ALTER TASK tk SET SUSPEND_TASK_AFTER_NUM_FAILURES = 3 "
            + "TASK_AUTO_RETRY_ATTEMPTS = 2 ALLOW_OVERLAPPING_EXECUTION = TRUE");
        Assumptions.assumeFalse(isLiveSnowflake(), MODEL_ASSERTIONS);
        assertEquals(3, task().getSuspendTaskAfterNumFailures());
        assertEquals(2, task().getTaskAutoRetryAttempts());
        assertEquals(true, task().isAllowOverlappingExecution());
    }

    @Test
    public void unsetResetsToDefault() {
        engine.execute("ALTER TASK tk SET COMMENT = 'hello' USER_TASK_TIMEOUT_MS = 5000");
        engine.execute("ALTER TASK tk UNSET COMMENT, USER_TASK_TIMEOUT_MS");
        Assumptions.assumeFalse(isLiveSnowflake(), MODEL_ASSERTIONS);
        assertNull(task().getComment());
        assertEquals(3600000L, task().getUserTaskTimeoutMs());   // back to the 1-hour default
    }

    @Test
    public void modifyAsChangesTheBody() {
        engine.execute("ALTER TASK tk MODIFY AS SELECT 42");
        Assumptions.assumeFalse(isLiveSnowflake(), MODEL_ASSERTIONS);
        assertEquals("SELECT 42", task().getSqlStatement().trim());
    }

    @Test
    public void modifyWhenChangesTheCondition() {
        engine.execute("ALTER TASK tk MODIFY WHEN TRUE");
        Assumptions.assumeFalse(isLiveSnowflake(), MODEL_ASSERTIONS);
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
        engine.execute("ALTER STREAM st UNSET COMMENT");
        Assumptions.assumeFalse(isLiveSnowflake(), MODEL_ASSERTIONS);
        final Stream stream = schema().getStream("ST");
        assertNull(stream.getComment(), "UNSET COMMENT clears the comment CREATE STREAM set");
    }

    @Test
    public void streamSetCommentStillWorks() {
        engine.execute("ALTER STREAM st SET COMMENT = 'updated'");
        Assumptions.assumeFalse(isLiveSnowflake(), MODEL_ASSERTIONS);
        assertEquals("updated", schema().getStream("ST").getComment());
    }

    @Test
    public void unsetScheduleClearsIt() {
        engine.execute("ALTER TASK tk UNSET SCHEDULE");
        Assumptions.assumeFalse(isLiveSnowflake(), MODEL_ASSERTIONS);
        assertNull(task().getSchedule());
        assertFalse(task().getSchedule() != null);
    }
}
