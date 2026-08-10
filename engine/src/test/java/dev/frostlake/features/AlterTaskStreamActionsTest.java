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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ALTER TASK … SET / UNSET / MODIFY and ALTER STREAM … UNSET COMMENT — previously the grammar only accepted
 * RESUME/SUSPEND (task) and SET COMMENT (stream), so these valid Snowflake statements were parse errors.
 * The applied options are asserted through the SQL surface — {@code SHOW TASKS} cells,
 * {@code SHOW PARAMETERS IN TASK} rows and {@code SHOW STREAMS} — so every check runs against
 * whichever engine executed the statements, embedded or live.
 */
public class AlterTaskStreamActionsTest extends BaseDatabaseTest {

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

    private String taskCell(final String column) {
        final ResultSet tasks = engine.executeQuery("SHOW TASKS LIKE 'tk'");
        return cell(tasks, soleRowWhere(tasks, "name", "TK"), column);
    }

    private String taskParameter(final String key) {
        final ResultSet params = engine.executeQuery("SHOW PARAMETERS IN TASK tk");
        return cell(params, soleRowWhere(params, "key", key), "value");
    }

    private String streamComment() {
        final ResultSet streams = engine.executeQuery("SHOW STREAMS LIKE 'st'");
        return cell(streams, soleRowWhere(streams, "name", "ST"), "comment");
    }

    @Test
    public void setScheduleAndWarehouse() {
        engine.execute("ALTER TASK tk SET SCHEDULE = '10 MINUTE'");
        engine.execute("ALTER TASK tk SET WAREHOUSE = wh2");
        assertEquals("10 MINUTE", taskCell("schedule"));
        assertEquals("WH2", taskCell("warehouse"));
    }

    @Test
    public void setMultipleOptionsInOneStatement() {
        engine.execute("ALTER TASK tk SET COMMENT = 'hello' USER_TASK_TIMEOUT_MS = 5000");
        assertEquals("hello", taskCell("comment"));
        assertEquals("5000", taskParameter("USER_TASK_TIMEOUT_MS"));
    }

    @Test
    public void setTaskParameters() {
        engine.execute("ALTER TASK tk SET SUSPEND_TASK_AFTER_NUM_FAILURES = 3 "
            + "TASK_AUTO_RETRY_ATTEMPTS = 2 ALLOW_OVERLAPPING_EXECUTION = TRUE");
        assertEquals("3", taskParameter("SUSPEND_TASK_AFTER_NUM_FAILURES"));
        assertEquals("2", taskParameter("TASK_AUTO_RETRY_ATTEMPTS"));
        assertEquals("true", taskCell("allow_overlapping_execution"));
    }

    @Test
    public void unsetResetsToDefault() {
        engine.execute("ALTER TASK tk SET COMMENT = 'hello' USER_TASK_TIMEOUT_MS = 5000");
        engine.execute("ALTER TASK tk UNSET COMMENT, USER_TASK_TIMEOUT_MS");
        // SHOW spells an unset comment as the EMPTY STRING (not NULL); the timeout falls back to
        // the 1-hour default.
        assertEquals("", taskCell("comment"));
        assertEquals("3600000", taskParameter("USER_TASK_TIMEOUT_MS"));
    }

    @Test
    public void modifyAsChangesTheBody() {
        engine.execute("ALTER TASK tk MODIFY AS SELECT 42");
        assertEquals("SELECT 42", taskCell("definition").trim());
    }

    @Test
    public void modifyWhenChangesTheCondition() {
        engine.execute("ALTER TASK tk MODIFY WHEN TRUE");
        assertEquals("TRUE", taskCell("condition").trim());
    }

    @Test
    public void resumeSuspendStillWork() {
        engine.execute("ALTER TASK tk RESUME");
        engine.execute("ALTER TASK tk SUSPEND");
        // No exception ⇒ the pre-existing actions still parse and apply alongside the new ones.
        assertEquals("suspended", taskCell("state"));
    }

    @Test
    public void streamUnsetComment() {
        engine.execute("ALTER STREAM st UNSET COMMENT");
        // SHOW spells an unset comment as the EMPTY STRING (not NULL).
        assertEquals("", streamComment(), "UNSET COMMENT clears the comment CREATE STREAM set");
    }

    @Test
    public void streamSetCommentStillWorks() {
        engine.execute("ALTER STREAM st SET COMMENT = 'updated'");
        assertEquals("updated", streamComment());
    }

    @Test
    public void unsetScheduleClearsIt() {
        engine.execute("ALTER TASK tk UNSET SCHEDULE");
        assertNull(taskCell("schedule"));
    }
}
