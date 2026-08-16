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
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.TaskExecution;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Streams and tasks side by side, asserted through the SQL surface — {@code SHOW STREAMS} /
 * {@code SHOW TASKS} cells, reads of the stream itself ({@code METADATA$ACTION} /
 * {@code METADATA$ISUPDATE}), and DML consumption — so every check runs against whichever engine
 * executed the statements, embedded or live. Execution RESULTS stay exempt on live:
 * {@code EXECUTE TASK} is asynchronous there, so a shared session cannot observe the side effect
 * deterministically.
 */
public class StreamsAndTasksTest extends BaseDatabaseTest {

    private static final String LIVE_TASKS_ASYNC =
        "live EXECUTE TASK is asynchronous — the shared session cannot await the task body's side "
        + "effects, so execution results are asserted embedded only";

    private String streamCell(final String name, final String column) {
        final ResultSet streams = engine.executeQuery("SHOW STREAMS LIKE '" + name + "'");
        return cell(streams, soleRowWhere(streams, "name", name.toUpperCase()), column);
    }

    private String taskCell(final String name, final String column) {
        final ResultSet tasks = engine.executeQuery("SHOW TASKS LIKE '" + name + "'");
        return cell(tasks, soleRowWhere(tasks, "name", name.toUpperCase()), column);
    }

    // ==================== STREAM TESTS ====================

    @Test
    public void testCreateStream() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, email VARCHAR)");
        engine.execute("CREATE STREAM user_stream ON TABLE users");

        assertTrue(streamCell("user_stream", "table_name").endsWith("USERS"),
            streamCell("user_stream", "table_name"));
        assertEquals("Table", streamCell("user_stream", "source_type"));
        assertEquals("DEFAULT", streamCell("user_stream", "mode"));
    }

    @Test
    public void testCreateAppendOnlyStream() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STREAM user_stream ON TABLE users APPEND_ONLY = TRUE");

        assertEquals("APPEND_ONLY", streamCell("user_stream", "mode"));
    }

    @Test
    public void testStreamTracksInserts() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STREAM user_stream ON TABLE users");

        engine.execute("INSERT INTO users VALUES (1, 'Alice'), (2, 'Bob')");

        final ResultSet delta = engine.executeQuery(
            "SELECT id, METADATA$ACTION FROM user_stream");
        assertEquals(2, delta.getRowCount());
        assertEquals(2, rowsWhere(delta, "METADATA$ACTION", "INSERT").size());
    }

    @Test
    public void testStreamTracksUpdates() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30)");

        engine.execute("CREATE STREAM user_stream ON TABLE users");

        engine.execute("UPDATE users SET age = 31 WHERE id = 1");

        // An UPDATE reads as a DELETE of the old image plus an INSERT of the new one, both flagged
        // METADATA$ISUPDATE.
        final ResultSet delta = engine.executeQuery(
            "SELECT id, METADATA$ACTION, METADATA$ISUPDATE FROM user_stream");
        assertEquals(2, delta.getRowCount());
        final Row deleted = soleRowWhere(delta, "METADATA$ACTION", "DELETE");
        final Row inserted = soleRowWhere(delta, "METADATA$ACTION", "INSERT");
        assertEquals("true", cell(delta, deleted, "METADATA$ISUPDATE"));
        assertEquals("true", cell(delta, inserted, "METADATA$ISUPDATE"));
    }

    @Test
    public void testStreamTracksDeletes() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice'), (2, 'Bob')");

        engine.execute("CREATE STREAM user_stream ON TABLE users");

        engine.execute("DELETE FROM users WHERE id = 1");

        final ResultSet delta = engine.executeQuery(
            "SELECT id, METADATA$ACTION FROM user_stream");
        assertEquals(1, delta.getRowCount());
        soleRowWhere(delta, "METADATA$ACTION", "DELETE");
    }

    @Test
    public void testAppendOnlyStreamOnlyTracksInserts() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STREAM user_stream ON TABLE users APPEND_ONLY = TRUE");

        engine.execute("INSERT INTO users VALUES (1, 'Alice')");
        engine.execute("UPDATE users SET name = 'Alicia' WHERE id = 1");
        engine.execute("DELETE FROM users WHERE id = 1");

        // An append-only stream reports inserts only; the later UPDATE and DELETE leave no rows.
        final ResultSet delta = engine.executeQuery(
            "SELECT id, METADATA$ACTION FROM user_stream");
        assertEquals(1, delta.getRowCount());
        soleRowWhere(delta, "METADATA$ACTION", "INSERT");
    }

    @Test
    public void testStreamConsume() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("CREATE TABLE users_copy (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STREAM user_stream ON TABLE users");

        engine.execute("INSERT INTO users VALUES (1, 'Alice')");

        assertEquals(1, engine.executeQuery("SELECT * FROM user_stream").getRowCount());

        // A stream is consumed by a DML statement that reads it, not by a plain SELECT.
        engine.execute("INSERT INTO users_copy SELECT id, name FROM user_stream");

        assertEquals(0, engine.executeQuery("SELECT * FROM user_stream").getRowCount());
    }

    @Test
    public void testDropStream() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STREAM user_stream ON TABLE users");
        engine.execute("DROP STREAM user_stream");

        assertEquals(0, engine.executeQuery("SHOW STREAMS LIKE 'user_stream'").getRowCount());
    }

    // ==================== TASK TESTS ====================

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
        Assumptions.assumeFalse(isLiveSnowflake(), LIVE_TASKS_ASYNC);

        // Create a table for task to populate
        engine.execute("CREATE TABLE task_log (execution_time VARCHAR, message VARCHAR)");

        engine.execute("""
            CREATE TASK test_task
            WAREHOUSE = 'COMPUTE_WH'
            SCHEDULE = '1 MINUTES'
            AS INSERT INTO task_log VALUES ('2024-01-01', 'Task executed')
            """);

        final Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("TEST_SCHEMA");
        final Task task = schema.getTask("test_task");

        // Execute task manually: the embedded scheduler runs the body synchronously.
        engine.getTaskScheduler().executeTaskNow("test_db.TEST_SCHEMA.test_task", task);

        assertEquals(1, task.getExecutionHistory().size());
        final TaskExecution execution = task.getExecutionHistory().get(0);
        assertEquals("SUCCEEDED", execution.getState());

        assertEquals(1, engine.executeQuery("SELECT * FROM task_log").getRowCount());
    }

    // ==================== COMBINED STREAMS AND TASKS ====================

    @Test
    public void testStreamWithTaskIntegration() {
        // Create source table and stream
        engine.execute("CREATE TABLE orders (id INTEGER, amount INTEGER, status VARCHAR)");
        engine.execute("CREATE STREAM order_stream ON TABLE orders");

        // Create destination table
        engine.execute("CREATE TABLE order_summary (total_orders INTEGER, total_amount INTEGER)");

        // Insert initial data
        engine.execute("INSERT INTO orders VALUES (1, 100, 'pending'), (2, 200, 'completed')");

        assertEquals(2, engine.executeQuery("SELECT * FROM order_stream").getRowCount());

        // Create task to process stream (conceptually)
        engine.execute("""
            CREATE TASK process_orders
            WAREHOUSE = 'COMPUTE_WH'
            SCHEDULE = '5 MINUTES'
            AS INSERT INTO order_summary SELECT COUNT(*), SUM(amount) FROM orders
            """);

        // Task and stream are set up correctly: the read did not consume the stream.
        assertEquals(2, engine.executeQuery("SELECT * FROM order_stream").getRowCount());
        assertEquals("suspended", taskCell("process_orders", "state"));
    }
}
