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
 * Tests for COMMENT ON command
 */
public class CommentCommandTest {

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
    public void testCommentOnDatabase() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("COMMENT ON DATABASE test_db IS 'Updated comment'");

        Database db = engine.getCatalog().getDatabase("TEST_DB");
        assertNotNull(db);
        assertEquals("Updated comment", db.getComment());
    }

    @Test
    public void testCommentOnSchema() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("COMMENT ON SCHEMA test_schema IS 'Schema comment'");

        Schema schema = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("TEST_SCHEMA");
        assertNotNull(schema);
        assertEquals("Schema comment", schema.getComment());
    }

    @Test
    public void testCommentOnTable() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("COMMENT ON TABLE users IS 'User table comment'");

        Table table = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("PUBLIC")
            .getTable("USERS");
        assertNotNull(table);
        assertEquals("User table comment", table.getComment());
    }

    @Test
    public void testCommentOnColumn() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, email VARCHAR)");
        engine.execute("COMMENT ON COLUMN users.name IS 'User full name'");
        engine.execute("COMMENT ON COLUMN users.email IS 'User email address'");

        Table table = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("PUBLIC")
            .getTable("USERS");
        assertNotNull(table);

        TableColumn nameCol = table.getColumn("NAME");
        assertEquals("User full name", nameCol.getComment());

        TableColumn emailCol = table.getColumn("EMAIL");
        assertEquals("User email address", emailCol.getComment());
    }

    @Test
    public void testCommentOnView() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("CREATE VIEW active_users AS SELECT * FROM users");
        engine.execute("COMMENT ON VIEW active_users IS 'Active users only'");

        View view = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("PUBLIC")
            .getView("ACTIVE_USERS");
        assertNotNull(view);
        assertEquals("Active users only", view.getComment());
    }

    @Test
    public void testCommentOnStream() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STREAM user_stream ON TABLE users");
        engine.execute("COMMENT ON STREAM user_stream IS 'Stream comment'");

        Stream stream = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("PUBLIC")
            .getStream("USER_STREAM");
        assertNotNull(stream);
        assertEquals("Stream comment", stream.getComment());
    }

    @Test
    public void testCommentOnTask() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE WAREHOUSE test_wh");
        engine.execute("""
            CREATE TASK daily_task
            WAREHOUSE = 'test_wh'
            SCHEDULE = 'USING CRON 0 9 * * * UTC'
            AS 'SELECT 1'
            """);
        engine.execute("COMMENT ON TASK daily_task IS 'Daily task comment'");

        Task task = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("PUBLIC")
            .getTask("DAILY_TASK");
        assertNotNull(task);
        assertEquals("Daily task comment", task.getComment());
    }

    @Test
    public void testCommentOnWarehouse() {
        engine.execute("CREATE WAREHOUSE test_wh");
        engine.execute("COMMENT ON WAREHOUSE test_wh IS 'Warehouse comment'");

        Warehouse warehouse = engine.getCatalog().getWarehouse("TEST_WH");
        assertNotNull(warehouse);
        assertEquals("Warehouse comment", warehouse.getComment());
    }

    @Test
    public void testCommentOnStage() {
        engine.execute("CREATE STAGE test_stage URL = 's3://bucket/path'");
        engine.execute("COMMENT ON STAGE test_stage IS 'Stage comment'");

        Stage stage = engine.getCatalog().getStage("TEST_STAGE");
        assertNotNull(stage);
        assertEquals("Stage comment", stage.getComment());
    }

    @Test
    public void testCommentOnFunction() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("""
            CREATE FUNCTION add_numbers(x INTEGER, y INTEGER)
            RETURNS INTEGER AS 'x + y'
            """);
        engine.execute("COMMENT ON FUNCTION add_numbers(INTEGER, INTEGER) IS 'Function comment'");

        Function function = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("PUBLIC")
            .getFunction("ADD_NUMBERS");
        assertNotNull(function);
        assertEquals("Function comment", function.getComment());
    }

    @Test
    public void testCommentOnProcedure() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("""
            CREATE PROCEDURE test_proc()
            RETURNS VARCHAR AS 'BEGIN RETURN ''done''; END;'
            """);
        engine.execute("COMMENT ON PROCEDURE test_proc() IS 'Procedure comment'");

        Procedure procedure = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("PUBLIC")
            .getProcedure("TEST_PROC");
        assertNotNull(procedure);
        assertEquals("Procedure comment", procedure.getComment());
    }

    @Test
    public void testCommentOnUser() {
        engine.execute("CREATE USER test_user PASSWORD = 'secret'");
        engine.execute("COMMENT ON USER test_user IS 'User comment'");

        User user = engine.getCatalog().getUser("TEST_USER");
        assertNotNull(user);
        assertEquals("User comment", user.getComment());
    }

    @Test
    public void testCommentOnRole() {
        engine.execute("CREATE ROLE analyst");
        engine.execute("COMMENT ON ROLE analyst IS 'Role comment'");

        Role role = engine.getCatalog().getRole("ANALYST");
        assertNotNull(role);
        assertEquals("Role comment", role.getComment());
    }

    @Test
    public void testUpdateExistingComment() {
        engine.execute("CREATE DATABASE test_db COMMENT = 'Initial comment'");
        Database db = engine.getCatalog().getDatabase("TEST_DB");
        assertEquals("Initial comment", db.getComment());

        engine.execute("COMMENT ON DATABASE test_db IS 'Updated comment'");
        assertEquals("Updated comment", db.getComment());
    }

    @Test
    public void testCommentOnQualifiedSchema() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("CREATE SCHEMA test_db.test_schema");
        engine.execute("COMMENT ON SCHEMA test_db.test_schema IS 'Qualified schema comment'");

        Schema schema = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("TEST_SCHEMA");
        assertNotNull(schema);
        assertEquals("Qualified schema comment", schema.getComment());
    }

    @Test
    public void testCommentOnQualifiedTable() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("CREATE TABLE test_db.public.users (id INTEGER)");
        engine.execute("COMMENT ON TABLE test_db.public.users IS 'Qualified table comment'");

        Table table = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("PUBLIC")
            .getTable("USERS");
        assertNotNull(table);
        assertEquals("Qualified table comment", table.getComment());
    }

    @Test
    public void testCommentOnQualifiedColumn() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("CREATE TABLE test_db.public.users (id INTEGER, name VARCHAR)");
        engine.execute("COMMENT ON COLUMN test_db.public.users.name IS 'Qualified column comment'");

        Table table = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("PUBLIC")
            .getTable("USERS");
        assertNotNull(table);

        TableColumn nameCol = table.getColumn("NAME");
        assertEquals("Qualified column comment", nameCol.getComment());
    }

    @Test
    public void testCommentWithSpecialCharacters() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("COMMENT ON DATABASE test_db IS 'Special chars: @#$%^&*()'");

        Database db = engine.getCatalog().getDatabase("TEST_DB");
        assertEquals("Special chars: @#$%^&*()", db.getComment());
    }

    @Test
    public void testCommentWithQuotes() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("COMMENT ON DATABASE test_db IS 'Comment with ''quotes'''");

        Database db = engine.getCatalog().getDatabase("TEST_DB");
        assertEquals("Comment with 'quotes'", db.getComment());
    }

    @Test
    public void testCommentOnNonExistentObject() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            engine.execute("COMMENT ON DATABASE nonexistent IS 'Comment'");
        });
        assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    public void testCommentOnNonExistentColumn() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE TABLE users (id INTEGER)");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            engine.execute("COMMENT ON COLUMN users.nonexistent IS 'Comment'");
        });
        assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    public void testCommentOnFunctionWithVarcharArguments() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("""
            CREATE FUNCTION concat_strings(a VARCHAR, b VARCHAR, c VARCHAR)
            RETURNS VARCHAR AS 'a || b || c'
            """);
        engine.execute("COMMENT ON FUNCTION concat_strings(VARCHAR, VARCHAR, VARCHAR) IS 'Concatenates three strings'");

        Function function = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("PUBLIC")
            .getFunction("CONCAT_STRINGS");
        assertNotNull(function);
        assertEquals("Concatenates three strings", function.getComment());
    }

    @Test
    public void testCommentOnFunctionWithMixedArguments() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("""
            CREATE FUNCTION format_data(name VARCHAR, age INTEGER, salary DECIMAL)
            RETURNS VARCHAR AS 'name || age || salary'
            """);
        engine.execute("COMMENT ON FUNCTION format_data(VARCHAR, INTEGER, DECIMAL) IS 'Formats user data'");

        Function function = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("PUBLIC")
            .getFunction("FORMAT_DATA");
        assertNotNull(function);
        assertEquals("Formats user data", function.getComment());
    }

    @Test
    public void testCommentOnFunctionWithNoArguments() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("""
            CREATE FUNCTION get_timestamp()
            RETURNS VARCHAR AS 'CURRENT_TIMESTAMP'
            """);
        engine.execute("COMMENT ON FUNCTION get_timestamp() IS 'Returns current timestamp'");

        Function function = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("PUBLIC")
            .getFunction("GET_TIMESTAMP");
        assertNotNull(function);
        assertEquals("Returns current timestamp", function.getComment());
    }

    @Test
    public void testCommentOnProcedureWithArguments() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("""
            CREATE PROCEDURE update_user(user_id INTEGER, new_name VARCHAR)
            RETURNS VARCHAR AS 'BEGIN RETURN ''done''; END;'
            """);
        engine.execute("COMMENT ON PROCEDURE update_user(INTEGER, VARCHAR) IS 'Updates user name'");

        Procedure procedure = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("PUBLIC")
            .getProcedure("UPDATE_USER");
        assertNotNull(procedure);
        assertEquals("Updates user name", procedure.getComment());
    }

    @Test
    public void testCommentOnFunctionWithWrongSignature() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("""
            CREATE FUNCTION add_numbers(x INTEGER, y INTEGER)
            RETURNS INTEGER AS 'x + y'
            """);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            engine.execute("COMMENT ON FUNCTION add_numbers(VARCHAR, VARCHAR) IS 'Wrong signature'");
        });
        assertTrue(exception.getMessage().contains("type mismatch") ||
                   exception.getMessage().contains("does not have"));
    }

    @Test
    public void testCommentOnFunctionWithWrongArgumentCount() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("""
            CREATE FUNCTION add_numbers(x INTEGER, y INTEGER)
            RETURNS INTEGER AS 'x + y'
            """);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            engine.execute("COMMENT ON FUNCTION add_numbers(INTEGER) IS 'Wrong count'");
        });
        assertTrue(exception.getMessage().contains("does not have") ||
                   exception.getMessage().contains("parameters"));
    }
}
