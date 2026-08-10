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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK DDL and execution, asserted through the SQL surface — {@code SHOW TASKS} cells (state is
 * spelled lower-case {@code started}/{@code suspended}; the schedule text distinguishes interval
 * from CRON) and each task body's side effects — so every check runs against whichever engine
 * executed the DDL, embedded or live. Execution RESULTS stay exempt on live: {@code EXECUTE TASK}
 * is asynchronous there, so a shared session cannot observe the side effect deterministically.
 */
public class TasksTest extends BaseDatabaseTest {

    private static final String LIVE_TASKS_ASYNC =
        "live EXECUTE TASK is asynchronous — the shared session cannot await the task body's side "
        + "effects, so execution results are asserted embedded only";

    private String taskCell(final String name, final String column) {
        final ResultSet tasks = engine.executeQuery("SHOW TASKS LIKE '" + name + "'");
        return cell(tasks, soleRowWhere(tasks, "name", name.toUpperCase()), column);
    }

    @Test
    public void testCreateTask() {
        engine.execute("""
            CREATE TASK daily_cleanup
            WAREHOUSE = 'COMPUTE_WH'
            SCHEDULE = '60 MINUTES'
            AS DELETE FROM logs WHERE timestamp < '2024-01-01'
            """);

        assertEquals("60 MINUTES", taskCell("daily_cleanup", "schedule"));
        assertEquals("suspended", taskCell("daily_cleanup", "state"));
    }

    @Test
    public void testCreateTaskWithCronSchedule() {
        engine.execute("""
            CREATE TASK hourly_job
            WAREHOUSE = 'COMPUTE_WH'
            SCHEDULE = 'USING CRON 0 * * * * UTC'
            AS INSERT INTO summary SELECT * FROM staging
            """);

        assertTrue(taskCell("hourly_job", "schedule").startsWith("USING CRON"),
            taskCell("hourly_job", "schedule"));
    }

    @Test
    public void testAlterTaskResume() {
        engine.execute("""
            CREATE TASK test_task
            WAREHOUSE = 'COMPUTE_WH'
            SCHEDULE = '5 MINUTES'
            AS SELECT 1
            """);

        engine.execute("ALTER TASK test_task RESUME");

        assertEquals("started", taskCell("test_task", "state"));
    }

    @Test
    public void testAlterTaskSuspend() {
        engine.execute("""
            CREATE TASK test_task
            WAREHOUSE = 'COMPUTE_WH'
            SCHEDULE = '5 MINUTES'
            AS SELECT 1
            """);

        engine.execute("ALTER TASK test_task RESUME");
        engine.execute("ALTER TASK test_task SUSPEND");

        assertEquals("suspended", taskCell("test_task", "state"));
    }

    @Test
    public void testDropTask() {
        engine.execute("""
            CREATE TASK test_task
            WAREHOUSE = 'COMPUTE_WH'
            SCHEDULE = '5 MINUTES'
            AS SELECT 1
            """);

        engine.execute("DROP TASK test_task");

        assertEquals(0, engine.executeQuery("SHOW TASKS LIKE 'test_task'").getRowCount());
    }

    @Test
    public void testTaskExecution() {
        engine.execute("CREATE TABLE task_log (execution_time VARCHAR, message VARCHAR)");

        engine.execute("""
            CREATE TASK test_task
            WAREHOUSE = 'COMPUTE_WH'
            SCHEDULE = '1 MINUTES'
            AS INSERT INTO task_log VALUES ('2024-01-01', 'Task executed')
            """);

        engine.execute("ALTER TASK test_task RESUME");
        engine.execute("EXECUTE TASK test_task");

        Assumptions.assumeFalse(isLiveSnowflake(), LIVE_TASKS_ASYNC);
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM task_log");
        assertEquals(1, ((Number) rs.getRows().get(0).getValue(0)).intValue(),
            "the task body should have run once");
    }

    @Test
    public void testStreamWithTaskIntegration() {
        engine.execute("CREATE TABLE orders (id INTEGER, amount INTEGER, status VARCHAR)");
        engine.execute("CREATE STREAM order_stream ON TABLE orders");

        engine.execute("CREATE TABLE order_summary (total_orders INTEGER, total_amount INTEGER)");

        engine.execute("INSERT INTO orders VALUES (1, 100, 'pending'), (2, 200, 'completed')");

        final ResultSet before = engine.executeQuery("SELECT COUNT(*) FROM order_stream");
        assertEquals(2L, ((Number) before.getRows().get(0).getValue(0)).longValue());

        engine.execute("""
            CREATE TASK process_orders
            WAREHOUSE = 'COMPUTE_WH'
            SCHEDULE = '5 MINUTES'
            AS INSERT INTO order_summary SELECT COUNT(*), SUM(amount) FROM orders
            """);

        // Creating the task neither consumes the stream nor starts the task.
        final ResultSet after = engine.executeQuery("SELECT COUNT(*) FROM order_stream");
        assertEquals(2L, ((Number) after.getRows().get(0).getValue(0)).longValue());
        assertEquals("suspended", taskCell("process_orders", "state"));
    }

    @Test
    public void testCreateTaskWithCallBodyExecutesProcedure() {
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

        assertTrue(taskCell("refresh_task", "definition").toUpperCase().contains("CALL"),
            "task body should be the CALL statement");

        engine.execute("ALTER TASK refresh_task RESUME");
        engine.execute("EXECUTE TASK refresh_task");

        Assumptions.assumeFalse(isLiveSnowflake(), LIVE_TASKS_ASYNC);
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM ran_marker");
        assertEquals(1, ((Number) rs.getRows().get(0).getValue(0)).intValue(),
            "the CALL body should have executed the procedure");
    }
}
