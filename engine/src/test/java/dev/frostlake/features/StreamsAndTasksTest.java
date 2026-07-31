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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.metastore.*;
import dev.frostlake.metastore.model.*;
import dev.frostlake.metastore.model.ChangeType;
import dev.frostlake.metastore.model.ScheduleType;
import dev.frostlake.metastore.model.StreamType;
import dev.frostlake.metastore.model.TaskState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StreamsAndTasksTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setup() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA PUBLIC");
    }

    @AfterEach
    public void teardown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    // ==================== STREAM TESTS ====================

    @Test
    public void testCreateStream() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, email VARCHAR)");
        engine.execute("CREATE STREAM user_stream ON TABLE users");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("PUBLIC");
        Stream stream = schema.getStream("user_stream");

        assertNotNull(stream);
        assertEquals("USER_STREAM", stream.getName().toUpperCase());
        assertEquals("USERS", stream.getSourceTableName().toUpperCase());
        assertEquals(StreamType.STANDARD, stream.getStreamType());
    }

    @Test
    public void testCreateAppendOnlyStream() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STREAM user_stream ON TABLE users APPEND_ONLY = TRUE");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("PUBLIC");
        Stream stream = schema.getStream("user_stream");

        assertEquals(StreamType.APPEND_ONLY, stream.getStreamType());
    }

    @Test
    public void testStreamTracksInserts() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STREAM user_stream ON TABLE users");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("PUBLIC");
        Stream stream = schema.getStream("user_stream");

        // Insert data
        engine.execute("INSERT INTO users VALUES (1, 'Alice'), (2, 'Bob')");

        // Check stream has records
        List<StreamRecord> records = stream.getUnconsumedRecords();
        assertEquals(2, records.size());
        assertEquals(ChangeType.INSERT, records.get(0).getChangeType());
    }

    @Test
    public void testStreamTracksUpdates() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30)");

        engine.execute("CREATE STREAM user_stream ON TABLE users");

        // Update data
        engine.execute("UPDATE users SET age = 31 WHERE id = 1");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("PUBLIC");
        Stream stream = schema.getStream("user_stream");

        // Updates generate DELETE + INSERT
        List<StreamRecord> records = stream.getUnconsumedRecords();
        assertEquals(2, records.size());
        assertEquals(ChangeType.DELETE, records.get(0).getChangeType());
        assertEquals(ChangeType.INSERT, records.get(1).getChangeType());
        assertTrue(records.get(0).isUpdate());
        assertTrue(records.get(1).isUpdate());
    }

    @Test
    public void testStreamTracksDeletes() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice'), (2, 'Bob')");

        engine.execute("CREATE STREAM user_stream ON TABLE users");

        // Delete data
        engine.execute("DELETE FROM users WHERE id = 1");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("PUBLIC");
        Stream stream = schema.getStream("user_stream");

        List<StreamRecord> records = stream.getUnconsumedRecords();
        assertEquals(1, records.size());
        assertEquals(ChangeType.DELETE, records.get(0).getChangeType());
    }

    @Test
    public void testAppendOnlyStreamOnlyTracksInserts() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STREAM user_stream ON TABLE users APPEND_ONLY = TRUE");

        engine.execute("INSERT INTO users VALUES (1, 'Alice')");
        engine.execute("UPDATE users SET name = 'Alicia' WHERE id = 1");
        engine.execute("DELETE FROM users WHERE id = 1");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("PUBLIC");
        Stream stream = schema.getStream("user_stream");

        // Append-only stream should only have the INSERT
        List<StreamRecord> records = stream.getUnconsumedRecords();
        assertEquals(1, records.size());
        assertEquals(ChangeType.INSERT, records.get(0).getChangeType());
    }

    @Test
    public void testStreamConsume() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STREAM user_stream ON TABLE users");

        engine.execute("INSERT INTO users VALUES (1, 'Alice')");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("PUBLIC");
        Stream stream = schema.getStream("user_stream");

        assertEquals(1, stream.getUnconsumedCount());

        // Consume the stream
        stream.consume();

        assertEquals(0, stream.getUnconsumedCount());
    }

    @Test
    public void testDropStream() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STREAM user_stream ON TABLE users");
        engine.execute("DROP STREAM user_stream");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("PUBLIC");

        assertThrows(RuntimeException.class, () -> {
            schema.getStream("user_stream");
        });
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

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("PUBLIC");
        Task task = schema.getTask("daily_cleanup");

        assertNotNull(task);
        assertEquals("daily_cleanup", task.getName().toLowerCase());
        assertEquals("60 MINUTES", task.getSchedule());
        assertEquals(ScheduleType.MINUTES, task.getScheduleType());
        assertEquals(TaskState.SUSPENDED, task.getState());
    }

    @Test
    public void testCreateTaskWithCronSchedule() {
        engine.execute("""
            CREATE TASK hourly_job
            WAREHOUSE = 'COMPUTE_WH'
            SCHEDULE = 'USING CRON 0 * * * * UTC'
            AS INSERT INTO summary SELECT * FROM staging
            """);

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("PUBLIC");
        Task task = schema.getTask("hourly_job");

        assertEquals(ScheduleType.CRON, task.getScheduleType());
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

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("PUBLIC");
        Task task = schema.getTask("test_task");

        assertEquals(TaskState.STARTED, task.getState());
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

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("PUBLIC");
        Task task = schema.getTask("test_task");

        assertEquals(TaskState.SUSPENDED, task.getState());
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

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("PUBLIC");

        assertThrows(RuntimeException.class, () -> {
            schema.getTask("test_task");
        });
    }

    @Test
    public void testTaskExecution() {
        // Create a table for task to populate
        engine.execute("CREATE TABLE task_log (execution_time VARCHAR, message VARCHAR)");

        engine.execute("""
            CREATE TASK test_task
            WAREHOUSE = 'COMPUTE_WH'
            SCHEDULE = '1 MINUTES'
            AS INSERT INTO task_log VALUES ('2024-01-01', 'Task executed')
            """);

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("PUBLIC");
        Task task = schema.getTask("test_task");

        // Execute task manually for testing
        String qualifiedName = "test_db.PUBLIC.test_task";
        engine.getTaskScheduler().executeTaskNow(qualifiedName, task);

        // Verify task execution was recorded
        assertEquals(1, task.getExecutionHistory().size());
        TaskExecution execution = task.getExecutionHistory().get(0);
        assertEquals("SUCCEEDED", execution.getState());
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

        // Verify stream captured changes
        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("PUBLIC");
        Stream stream = schema.getStream("order_stream");

        assertEquals(2, stream.getUnconsumedCount());

        // Create task to process stream (conceptually)
        engine.execute("""
            CREATE TASK process_orders
            WAREHOUSE = 'COMPUTE_WH'
            SCHEDULE = '5 MINUTES'
            AS INSERT INTO order_summary SELECT COUNT(*), SUM(amount) FROM orders
            """);

        Task task = schema.getTask("process_orders");
        assertNotNull(task);

        // Task and stream are set up correctly
        assertEquals(2, stream.getUnconsumedCount());
        assertEquals(TaskState.SUSPENDED, task.getState());
    }
}
