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
import dev.frostlake.metastore.model.ScheduleType;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.TaskExecution;
import dev.frostlake.metastore.model.TaskState;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for TASK feature
 */
public class TasksTest extends BaseDatabaseTest {

    private static final String TASK_MODEL =
        "asserts the parsed task straight off the in-memory catalog (engine.getCatalog()), which live "
        + "Snowflake never populates, and names warehouse COMPUTE_WH which need not exist on the account";

    @Test
    public void testCreateTask() {
        Assumptions.assumeFalse(isLiveSnowflake(), TASK_MODEL);
        engine.execute("""
            CREATE TASK daily_cleanup
            WAREHOUSE = 'COMPUTE_WH'
            SCHEDULE = '60 MINUTES'
            AS DELETE FROM logs WHERE timestamp < '2024-01-01'
            """);

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Task task = schema.getTask("daily_cleanup");

        assertNotNull(task);
        assertEquals("daily_cleanup", task.getName().toLowerCase());
        assertEquals("60 MINUTES", task.getSchedule());
        assertEquals(ScheduleType.MINUTES, task.getScheduleType());
        assertEquals(TaskState.SUSPENDED, task.getState());
    }

    @Test
    public void testCreateTaskWithCronSchedule() {
        Assumptions.assumeFalse(isLiveSnowflake(), TASK_MODEL);
        engine.execute("""
            CREATE TASK hourly_job
            WAREHOUSE = 'COMPUTE_WH'
            SCHEDULE = 'USING CRON 0 * * * * UTC'
            AS INSERT INTO summary SELECT * FROM staging
            """);

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Task task = schema.getTask("hourly_job");

        assertEquals(ScheduleType.CRON, task.getScheduleType());
    }

    @Test
    public void testAlterTaskResume() {
        Assumptions.assumeFalse(isLiveSnowflake(), TASK_MODEL);
        engine.execute("""
            CREATE TASK test_task
            WAREHOUSE = 'COMPUTE_WH'
            SCHEDULE = '5 MINUTES'
            AS SELECT 1
            """);

        engine.execute("ALTER TASK test_task RESUME");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Task task = schema.getTask("test_task");

        assertEquals(TaskState.STARTED, task.getState());
    }

    @Test
    public void testAlterTaskSuspend() {
        Assumptions.assumeFalse(isLiveSnowflake(), TASK_MODEL);
        engine.execute("""
            CREATE TASK test_task
            WAREHOUSE = 'COMPUTE_WH'
            SCHEDULE = '5 MINUTES'
            AS SELECT 1
            """);

        engine.execute("ALTER TASK test_task RESUME");
        engine.execute("ALTER TASK test_task SUSPEND");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Task task = schema.getTask("test_task");

        assertEquals(TaskState.SUSPENDED, task.getState());
    }

    @Test
    public void testDropTask() {
        Assumptions.assumeFalse(isLiveSnowflake(), TASK_MODEL);
        engine.execute("""
            CREATE TASK test_task
            WAREHOUSE = 'COMPUTE_WH'
            SCHEDULE = '5 MINUTES'
            AS SELECT 1
            """);

        engine.execute("DROP TASK test_task");

        final Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                schema.getTask("test_task");
            }
        });
    }

    @Test
    public void testTaskExecution() {
        Assumptions.assumeFalse(isLiveSnowflake(), TASK_MODEL);
        engine.execute("CREATE TABLE task_log (execution_time VARCHAR, message VARCHAR)");

        engine.execute("""
            CREATE TASK test_task
            WAREHOUSE = 'COMPUTE_WH'
            SCHEDULE = '1 MINUTES'
            AS INSERT INTO task_log VALUES ('2024-01-01', 'Task executed')
            """);

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Task task = schema.getTask("test_task");

        String qualifiedName = "test_db.test_schema.test_task";
        engine.getTaskScheduler().executeTaskNow(qualifiedName, task);

        assertEquals(1, task.getExecutionHistory().size());
        TaskExecution execution = task.getExecutionHistory().get(0);
        assertEquals("SUCCEEDED", execution.getState());
    }

    @Test
    public void testStreamWithTaskIntegration() {
        Assumptions.assumeFalse(isLiveSnowflake(), TASK_MODEL);
        engine.execute("CREATE TABLE orders (id INTEGER, amount INTEGER, status VARCHAR)");
        engine.execute("CREATE STREAM order_stream ON TABLE orders");

        engine.execute("CREATE TABLE order_summary (total_orders INTEGER, total_amount INTEGER)");

        engine.execute("INSERT INTO orders VALUES (1, 100, 'pending'), (2, 200, 'completed')");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Stream stream = schema.getStream("order_stream");

        assertEquals(2, stream.getUnconsumedCount());

        engine.execute("""
            CREATE TASK process_orders
            WAREHOUSE = 'COMPUTE_WH'
            SCHEDULE = '5 MINUTES'
            AS INSERT INTO order_summary SELECT COUNT(*), SUM(amount) FROM orders
            """);

        Task task = schema.getTask("process_orders");
        assertNotNull(task);

        assertEquals(2, stream.getUnconsumedCount());
        assertEquals(TaskState.SUSPENDED, task.getState());
    }

    @Test
    public void testCreateTaskWithCallBodyExecutesProcedure() {
        Assumptions.assumeFalse(isLiveSnowflake(), TASK_MODEL);
        // A task whose body is just CALL <procedure>() — a very common Snowflake pattern.
        engine.execute("CREATE TABLE ran_marker (v VARCHAR)");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE refresh_data() RETURNS VARCHAR LANGUAGE SQL AS
            BEGIN INSERT INTO ran_marker VALUES ('ran'); RETURN 'ok'; END
            """);
        engine.execute("""
            CREATE OR REPLACE TASK refresh_task
            SCHEDULE = 'USING CRON 0 0 * * * UTC'
            USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE = 'XSMALL'
            AS
            CALL refresh_data()
            """);

        final Task task = engine.getCatalog().getDatabase("test_db").getSchema("test_schema").getTask("refresh_task");
        assertNotNull(task);
        assertTrue(task.getSqlStatement().toUpperCase().contains("CALL"), "task body should be the CALL statement");

        engine.execute("ALTER TASK refresh_task RESUME");
        engine.execute("EXECUTE TASK refresh_task");

        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM ran_marker");
        assertEquals(1, ((Number) rs.getRows().get(0).getValue(0)).intValue(),
            "the CALL body should have executed the procedure");
    }
}
