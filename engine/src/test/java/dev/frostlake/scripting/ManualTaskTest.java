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

package dev.frostlake.scripting;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.ExecutionResult;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for manual-only tasks (tasks without schedules) and EXECUTE TASK command
 */
public class ManualTaskTest {

    private static final Logger logger = LoggerFactory.getLogger(ManualTaskTest.class);

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("USE SCHEMA test_schema");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testCreateManualOnlyTask() {
        logger.info("Testing CREATE TASK without schedule");

        // Create a table to be populated by the task
        engine.execute("CREATE TABLE task_log (id INTEGER, message VARCHAR)");

        // Create a manual-only task (no schedule)
        engine.execute("""
            CREATE TASK my_manual_task
            WAREHOUSE = 'compute_wh'
            AS
            INSERT INTO task_log VALUES (1, 'Task executed')
            """);

        // Verify task was created
        final Task task = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("TEST_SCHEMA")
            .getTask("MY_MANUAL_TASK");

        assertNotNull(task, "Task should be created");
        assertNull(task.getSchedule(), "Task schedule should be null for manual-only tasks");
        assertNull(task.getScheduleType(), "Task schedule type should be null for manual-only tasks");
        assertEquals("SUSPENDED", task.getState().toString(), "Task should start in SUSPENDED state");
    }

    @Test
    public void testCreateTaskWithSchedule() {
        logger.info("Testing CREATE TASK with schedule");

        engine.execute("CREATE TABLE task_log (id INTEGER, message VARCHAR)");

        // Create a scheduled task
        engine.execute("""
            CREATE TASK my_scheduled_task
            WAREHOUSE = 'compute_wh'
            SCHEDULE = '5 MINUTES'
            AS
            INSERT INTO task_log VALUES (2, 'Scheduled task')
            """);

        // Verify task was created with schedule
        final Task task = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("TEST_SCHEMA")
            .getTask("MY_SCHEDULED_TASK");

        assertNotNull(task, "Task should be created");
        assertNotNull(task.getSchedule(), "Task schedule should not be null");
        assertEquals("5 MINUTES", task.getSchedule());
        assertNotNull(task.getScheduleType(), "Task schedule type should not be null");
    }

    @Test
    public void testExecuteManualTask() {
        logger.info("Testing EXECUTE TASK command");

        // Create table and manual task
        engine.execute("CREATE TABLE task_log (id INTEGER, message VARCHAR)");

        engine.execute("""
            CREATE TASK my_manual_task
            WAREHOUSE = 'compute_wh'
            AS
            INSERT INTO task_log VALUES (1, 'Manually executed')
            """);

        // Execute the task manually
        engine.execute("EXECUTE TASK my_manual_task");

        // Verify the task executed by checking the table
        ExecutionResult result = engine.execute("SELECT COUNT(*) as cnt FROM task_log");
        ResultSet rs = result.getResultSets().get(0);
        assertEquals(1, rs.getRows().size(), "Should have one result row");
        Row row = rs.getRows().get(0);
        assertEquals(1L, row.getValues().get(0), "Task should have inserted one row");

        result = engine.execute("SELECT message FROM task_log");
        rs = result.getResultSets().get(0);
        assertEquals(1, rs.getRows().size());
        row = rs.getRows().get(0);
        assertEquals("Manually executed", row.getValues().get(0));
    }

    @Test
    public void testExecuteManualTaskMultipleTimes() {
        logger.info("Testing EXECUTE TASK multiple times");

        engine.execute("CREATE TABLE task_log (id INTEGER, message VARCHAR)");

        engine.execute("""
            CREATE TASK my_manual_task
            WAREHOUSE = 'compute_wh'
            AS
            INSERT INTO task_log VALUES (1, 'Execution')
            """);

        // Execute the task multiple times
        engine.execute("EXECUTE TASK my_manual_task");
        engine.execute("EXECUTE TASK my_manual_task");
        engine.execute("EXECUTE TASK my_manual_task");

        // Verify three rows were inserted
        final ExecutionResult result = engine.execute("SELECT COUNT(*) as cnt FROM task_log");
        final ResultSet rs = result.getResultSets().get(0);
        assertEquals(1, rs.getRows().size());
        final Row row = rs.getRows().get(0);
        assertEquals(3L, row.getValues().get(0), "Task should have inserted three rows");
    }

    @Test
    public void testExecuteTaskWithQualifiedName() {
        logger.info("Testing EXECUTE TASK with qualified name");

        engine.execute("CREATE TABLE task_log (id INTEGER, message VARCHAR)");

        engine.execute("""
            CREATE TASK my_manual_task
            WAREHOUSE = 'compute_wh'
            AS
            INSERT INTO task_log VALUES (1, 'Qualified execution')
            """);

        // Execute with qualified name
        engine.execute("EXECUTE TASK test_schema.my_manual_task");

        final ExecutionResult result = engine.execute("SELECT COUNT(*) as cnt FROM task_log");
        final ResultSet rs = result.getResultSets().get(0);
        assertEquals(1, rs.getRows().size());
        final Row row = rs.getRows().get(0);
        assertEquals(1L, row.getValues().get(0), "Task should have inserted one row");
    }

    @Test
    public void testExecuteNonExistentTask() {
        logger.info("Testing EXECUTE TASK on non-existent task");

        final Exception exception = assertThrows(Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("EXECUTE TASK non_existent_task");
            }
        });

        String message = exception.getMessage();
        if (message == null && exception.getCause() != null) {
            message = exception.getCause().getMessage();
        }

        // Live: EXECUTE TASK nosuch answers exactly
        // "SQL compilation error:\nTask '<db>.<schema>.NOSUCH' does not exist or not authorized." —
        // no preamble naming the statement.
        assertTrue(message != null
                && message.contains("Task 'TEST_DB.TEST_SCHEMA.NON_EXISTENT_TASK'"
                    + " does not exist or not authorized."),
                   "Should report the task as missing the way Snowflake does, but got: " + message);
    }

    @Test
    public void testExecuteTaskWithComplexSQL() {
        logger.info("Testing EXECUTE TASK with complex SQL");

        engine.execute("CREATE TABLE source_data (id INTEGER, value VARCHAR)");
        engine.execute("CREATE TABLE summary (total INTEGER, distinct_count INTEGER)");

        // Insert test data
        engine.execute("INSERT INTO source_data VALUES (1, 'A'), (2, 'B'), (3, 'A')");

        // Create task with aggregation query
        engine.execute("""
            CREATE TASK summarize_task
            WAREHOUSE = 'compute_wh'
            AS
            INSERT INTO summary
            SELECT COUNT(*) as total, COUNT(DISTINCT value) as distinct_count
            FROM source_data
            """);

        // Execute the task
        engine.execute("EXECUTE TASK summarize_task");

        // Verify results
        final ExecutionResult result = engine.execute("SELECT total, distinct_count FROM summary");
        final ResultSet rs = result.getResultSets().get(0);
        assertEquals(1, rs.getRows().size());
        final Row row = rs.getRows().get(0);
        assertEquals(3L, row.getValues().get(0), "Should count all rows");
        assertEquals(2L, row.getValues().get(1), "Should count distinct values");
    }

    @Test
    public void testManualTaskExecutionHistory() {
        logger.info("Testing manual task execution history");

        engine.execute("CREATE TABLE task_log (id INTEGER, message VARCHAR)");

        engine.execute("""
            CREATE TASK my_manual_task
            WAREHOUSE = 'compute_wh'
            AS
            INSERT INTO task_log VALUES (1, 'Execution')
            """);

        // Execute task multiple times
        engine.execute("EXECUTE TASK my_manual_task");
        engine.execute("EXECUTE TASK my_manual_task");

        // Check task execution history
        final Task task = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("TEST_SCHEMA")
            .getTask("MY_MANUAL_TASK");

        assertEquals(2, task.getExecutionHistory().size(), "Should have 2 executions in history");
        assertEquals(2, task.getSuccessCount(), "Should have 2 successful executions");
        assertEquals(0, task.getFailureCount(), "Should have 0 failed executions");
    }

    @Test
    public void testExecuteTaskWithFailure() {
        logger.info("Testing EXECUTE TASK with failing SQL");

        // Create task with invalid SQL
        engine.execute("""
            CREATE TASK failing_task
            WAREHOUSE = 'compute_wh'
            AS
            INSERT INTO non_existent_table VALUES (1)
            """);

        // Execute task - TaskScheduler catches exceptions and records them in execution history
        engine.execute("EXECUTE TASK failing_task");

        // Task should still exist and have recorded the failure
        final Task task = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("TEST_SCHEMA")
            .getTask("FAILING_TASK");

        assertNotNull(task, "Task should still exist");
        assertEquals(1, task.getExecutionHistory().size(), "Should have 1 execution in history");
        assertEquals("FAILED", task.getExecutionHistory().get(0).getState(), "Execution should be marked as FAILED");
        assertEquals(1, task.getFailureCount(), "Should have 1 failed execution");
    }

    @Test
    public void testMixedManualAndScheduledTasks() {
        logger.info("Testing both manual and scheduled tasks");

        engine.execute("CREATE TABLE task_log (id INTEGER, message VARCHAR)");

        // Create manual task
        engine.execute("""
            CREATE TASK manual_task
            WAREHOUSE = 'compute_wh'
            AS
            INSERT INTO task_log VALUES (1, 'Manual')
            """);

        // Create scheduled task
        engine.execute("""
            CREATE TASK scheduled_task
            WAREHOUSE = 'compute_wh'
            SCHEDULE = '10 MINUTES'
            AS
            INSERT INTO task_log VALUES (2, 'Scheduled')
            """);

        // Execute manual task
        engine.execute("EXECUTE TASK manual_task");

        // Verify only manual task executed
        ExecutionResult result = engine.execute("SELECT COUNT(*) as cnt FROM task_log");
        ResultSet rs = result.getResultSets().get(0);
        assertEquals(1, rs.getRows().size());
        Row row = rs.getRows().get(0);
        assertEquals(1L, row.getValues().get(0), "Only manual task should have executed");

        result = engine.execute("SELECT message FROM task_log");
        rs = result.getResultSets().get(0);
        assertEquals(1, rs.getRows().size());
        row = rs.getRows().get(0);
        assertEquals("Manual", row.getValues().get(0));
    }
}
