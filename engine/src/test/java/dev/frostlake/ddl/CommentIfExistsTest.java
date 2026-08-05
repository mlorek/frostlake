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

package dev.frostlake.ddl;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.metastore.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for COMMENT IF EXISTS ON command
 */
public class CommentIfExistsTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testCommentIfExistsOnDatabase() {
        // Should not throw error when database doesn't exist
        assertDoesNotThrow(() -> {
            engine.execute("COMMENT IF EXISTS ON DATABASE nonexistent IS 'Comment'");
        });

        // Should set comment when database exists
        engine.execute("CREATE DATABASE test_db");
        engine.execute("COMMENT IF EXISTS ON DATABASE test_db IS 'Updated comment'");

        Database db = engine.getCatalog().getDatabase("TEST_DB");
        assertNotNull(db);
        assertEquals("Updated comment", db.getComment());
    }

    @Test
    public void testCommentIfExistsOnSchema() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");

        // Should not throw error when schema doesn't exist
        assertDoesNotThrow(() -> {
            engine.execute("COMMENT IF EXISTS ON SCHEMA nonexistent IS 'Comment'");
        });

        // Should set comment when schema exists
        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("COMMENT IF EXISTS ON SCHEMA test_schema IS 'Schema comment'");

        Schema schema = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("TEST_SCHEMA");
        assertNotNull(schema);
        assertEquals("Schema comment", schema.getComment());
    }

    @Test
    public void testCommentIfExistsOnTable() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");

        // Should not throw error when table doesn't exist
        assertDoesNotThrow(() -> {
            engine.execute("COMMENT IF EXISTS ON TABLE nonexistent IS 'Comment'");
        });

        // Should set comment when table exists
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("COMMENT IF EXISTS ON TABLE users IS 'User table'");

        Table table = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("PUBLIC")
            .getTable("USERS");
        assertNotNull(table);
        assertEquals("User table", table.getComment());
    }

    @Test
    public void testCommentIfExistsOnColumn() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");

        // Should not throw error when column doesn't exist
        assertDoesNotThrow(() -> {
            engine.execute("COMMENT IF EXISTS ON COLUMN users.nonexistent IS 'Comment'");
        });

        // Should not throw error when table doesn't exist
        assertDoesNotThrow(() -> {
            engine.execute("COMMENT IF EXISTS ON COLUMN nonexistent.name IS 'Comment'");
        });

        // Should set comment when column exists
        engine.execute("COMMENT IF EXISTS ON COLUMN users.name IS 'User name'");

        Table table = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("PUBLIC")
            .getTable("USERS");
        TableColumn nameCol = table.getColumn("NAME");
        assertEquals("User name", nameCol.getComment());
    }

    @Test
    public void testCommentIfExistsOnView() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");

        // Should not throw error when view doesn't exist
        assertDoesNotThrow(() -> {
            engine.execute("COMMENT IF EXISTS ON VIEW nonexistent IS 'Comment'");
        });

        // Should set comment when view exists
        engine.execute("CREATE VIEW active_users AS SELECT * FROM users");
        engine.execute("COMMENT IF EXISTS ON VIEW active_users IS 'Active users'");

        View view = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("PUBLIC")
            .getView("ACTIVE_USERS");
        assertNotNull(view);
        assertEquals("Active users", view.getComment());
    }

    @Test
    public void testCommentIfExistsOnFunction() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");

        // Should not throw error when function doesn't exist
        assertDoesNotThrow(() -> {
            engine.execute("COMMENT IF EXISTS ON FUNCTION nonexistent() IS 'Comment'");
        });

        // Should set comment when function exists
        engine.execute("""
            CREATE FUNCTION add_numbers(x INTEGER, y INTEGER)
            RETURNS INTEGER AS 'x + y'
            """);
        engine.execute("COMMENT IF EXISTS ON FUNCTION add_numbers(INTEGER, INTEGER) IS 'Addition function'");

        Function function = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("PUBLIC")
            .getFunction("ADD_NUMBERS");
        assertNotNull(function);
        assertEquals("Addition function", function.getComment());
    }

    @Test
    public void testCommentIfExistsOnProcedure() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");

        // Should not throw error when procedure doesn't exist
        assertDoesNotThrow(() -> {
            engine.execute("COMMENT IF EXISTS ON PROCEDURE nonexistent() IS 'Comment'");
        });

        // Should set comment when procedure exists
        engine.execute("""
            CREATE PROCEDURE test_proc()
            RETURNS VARCHAR AS 'BEGIN RETURN ''done''; END;'
            """);
        engine.execute("COMMENT IF EXISTS ON PROCEDURE test_proc() IS 'Test procedure'");

        Procedure procedure = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("PUBLIC")
            .getProcedure("TEST_PROC");
        assertNotNull(procedure);
        assertEquals("Test procedure", procedure.getComment());
    }

    @Test
    public void testCommentIfExistsOnStream() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");

        // Should not throw error when stream doesn't exist
        assertDoesNotThrow(() -> {
            engine.execute("COMMENT IF EXISTS ON STREAM nonexistent IS 'Comment'");
        });

        // Should set comment when stream exists
        engine.execute("CREATE STREAM user_stream ON TABLE users");
        engine.execute("COMMENT IF EXISTS ON STREAM user_stream IS 'User stream'");

        Stream stream = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("PUBLIC")
            .getStream("USER_STREAM");
        assertNotNull(stream);
        assertEquals("User stream", stream.getComment());
    }

    @Test
    public void testCommentIfExistsOnTask() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE WAREHOUSE test_wh");

        // Should not throw error when task doesn't exist
        assertDoesNotThrow(() -> {
            engine.execute("COMMENT IF EXISTS ON TASK nonexistent IS 'Comment'");
        });

        // Should set comment when task exists
        engine.execute("""
            CREATE TASK daily_task
            WAREHOUSE = 'test_wh'
            SCHEDULE = 'USING CRON 0 9 * * * UTC'
            AS SELECT 1
            """);
        engine.execute("COMMENT IF EXISTS ON TASK daily_task IS 'Daily task'");

        Task task = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("PUBLIC")
            .getTask("DAILY_TASK");
        assertNotNull(task);
        assertEquals("Daily task", task.getComment());
    }

    @Test
    public void testCommentIfExistsOnWarehouse() {
        // Should not throw error when warehouse doesn't exist
        assertDoesNotThrow(() -> {
            engine.execute("COMMENT IF EXISTS ON WAREHOUSE nonexistent IS 'Comment'");
        });

        // Should set comment when warehouse exists
        engine.execute("CREATE WAREHOUSE test_wh");
        engine.execute("COMMENT IF EXISTS ON WAREHOUSE test_wh IS 'Test warehouse'");

        Warehouse warehouse = engine.getCatalog().getWarehouse("TEST_WH");
        assertNotNull(warehouse);
        assertEquals("Test warehouse", warehouse.getComment());
    }

    @Test
    public void testCommentIfExistsOnStage() {
        // Should not throw error when stage doesn't exist
        assertDoesNotThrow(() -> {
            engine.execute("COMMENT IF EXISTS ON STAGE nonexistent IS 'Comment'");
        });

        // Should set comment when stage exists
        engine.execute("CREATE STAGE test_stage URL = 's3://bucket/path'");
        engine.execute("COMMENT IF EXISTS ON STAGE test_stage IS 'Test stage'");

        Stage stage = engine.getCatalog().getStage("TEST_STAGE");
        assertNotNull(stage);
        assertEquals("Test stage", stage.getComment());
    }

    @Test
    public void testCommentIfExistsOnUser() {
        // Should not throw error when user doesn't exist
        assertDoesNotThrow(() -> {
            engine.execute("COMMENT IF EXISTS ON USER nonexistent IS 'Comment'");
        });

        // Should set comment when user exists
        engine.execute("CREATE USER test_user PASSWORD = 'secret'");
        engine.execute("COMMENT IF EXISTS ON USER test_user IS 'Test user'");

        User user = engine.getCatalog().getUser("TEST_USER");
        assertNotNull(user);
        assertEquals("Test user", user.getComment());
    }

    @Test
    public void testCommentIfExistsOnRole() {
        // Should not throw error when role doesn't exist
        assertDoesNotThrow(() -> {
            engine.execute("COMMENT IF EXISTS ON ROLE nonexistent IS 'Comment'");
        });

        // Should set comment when role exists
        engine.execute("CREATE ROLE analyst");
        engine.execute("COMMENT IF EXISTS ON ROLE analyst IS 'Analyst role'");

        Role role = engine.getCatalog().getRole("ANALYST");
        assertNotNull(role);
        assertEquals("Analyst role", role.getComment());
    }

    @Test
    public void testCommentWithoutIfExistsStillThrowsError() {
        // Verify that without IF EXISTS, errors are still thrown
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            engine.execute("COMMENT ON DATABASE nonexistent IS 'Comment'");
        });
        assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    public void testCommentIfExistsWithQualifiedNames() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("CREATE SCHEMA test_db.test_schema");

        // Should not throw error when qualified schema doesn't exist
        assertDoesNotThrow(() -> {
            engine.execute("COMMENT IF EXISTS ON SCHEMA test_db.nonexistent IS 'Comment'");
        });

        // Should set comment when qualified schema exists
        engine.execute("COMMENT IF EXISTS ON SCHEMA test_db.test_schema IS 'Test schema'");

        Schema schema = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("TEST_SCHEMA");
        assertNotNull(schema);
        assertEquals("Test schema", schema.getComment());
    }
}
