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
 * Tests for COMMENT clause on CREATE statements
 */
public class CommentClauseTest {

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
    public void testCreateDatabaseWithComment() {
        engine.execute("CREATE DATABASE test_db COMMENT = 'Test database'");
        Database db = engine.getCatalog().getDatabase("TEST_DB");
        assertNotNull(db);
        assertEquals("Test database", db.getComment());
    }

    @Test
    public void testCreateSchemaWithComment() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE SCHEMA test_schema COMMENT = 'Test schema'");

        Schema schema = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("TEST_SCHEMA");
        assertNotNull(schema);
        assertEquals("Test schema", schema.getComment());
    }

    @Test
    public void testCreateTableWithComment() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR) COMMENT = 'User table'");

        Table table = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("PUBLIC")
            .getTable("USERS");
        assertNotNull(table);
        assertEquals("User table", table.getComment());
    }

    @Test
    public void testCreateTableWithColumnComments() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("""
            CREATE TABLE users (\
            id INTEGER COMMENT = 'User ID', \
            name VARCHAR COMMENT = 'Full name', \
            email VARCHAR COMMENT = 'Email address')\
            """);

        Table table = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("PUBLIC")
            .getTable("USERS");
        assertNotNull(table);

        TableColumn idCol = table.getColumn("ID");
        assertEquals("User ID", idCol.getComment());

        TableColumn nameCol = table.getColumn("NAME");
        assertEquals("Full name", nameCol.getComment());

        TableColumn emailCol = table.getColumn("EMAIL");
        assertEquals("Email address", emailCol.getComment());
    }

    @Test
    public void testCreateTableWithTableAndColumnComments() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("""
            CREATE TABLE products (\
            id INTEGER COMMENT = 'Product ID', \
            name VARCHAR COMMENT = 'Product name') \
            COMMENT = 'Product catalog'\
            """);

        Table table = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("PUBLIC")
            .getTable("PRODUCTS");
        assertNotNull(table);
        assertEquals("Product catalog", table.getComment());

        TableColumn idCol = table.getColumn("ID");
        assertEquals("Product ID", idCol.getComment());
    }

    @Test
    public void testCreateViewWithComment() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("CREATE VIEW active_users AS SELECT * FROM users COMMENT = 'Active users view'");

        View view = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("PUBLIC")
            .getView("ACTIVE_USERS");
        assertNotNull(view);
        assertEquals("Active users view", view.getComment());
    }

    @Test
    public void testCreateStreamWithComment() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STREAM user_stream ON TABLE users COMMENT = 'User change stream'");

        Stream stream = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("PUBLIC")
            .getStream("USER_STREAM");
        assertNotNull(stream);
        assertEquals("User change stream", stream.getComment());
    }

    @Test
    public void testCreateTaskWithComment() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE WAREHOUSE test_wh");
        engine.execute("""
            CREATE TASK daily_task
            WAREHOUSE = 'test_wh'
            SCHEDULE = 'USING CRON 0 9 * * * UTC'
            AS 'SELECT 1'
            COMMENT = 'Daily processing task'
            """);

        Task task = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("PUBLIC")
            .getTask("DAILY_TASK");
        assertNotNull(task);
        assertEquals("Daily processing task", task.getComment());
    }

    @Test
    public void testCreateWarehouseWithComment() {
        engine.execute("CREATE WAREHOUSE test_wh COMMENT = 'Test warehouse'");

        Warehouse warehouse = engine.getCatalog().getWarehouse("TEST_WH");
        assertNotNull(warehouse);
        assertEquals("Test warehouse", warehouse.getComment());
    }

    @Test
    public void testCreateStageWithComment() {
        engine.execute("CREATE STAGE test_stage URL = 's3://bucket/path' COMMENT = 'Test stage'");

        Stage stage = engine.getCatalog().getStage("TEST_STAGE");
        assertNotNull(stage);
        assertEquals("Test stage", stage.getComment());
    }

    @Test
    public void testCreateFunctionWithComment() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("""
            CREATE FUNCTION add_numbers(x INTEGER, y INTEGER)
            RETURNS INTEGER
            COMMENT = 'Adds two numbers'
            AS 'x + y'
            """);

        Function function = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("PUBLIC")
            .getFunction("ADD_NUMBERS");
        assertNotNull(function);
        assertEquals("Adds two numbers", function.getComment());
    }

    @Test
    public void testCreateProcedureWithComment() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("""
            CREATE PROCEDURE test_proc()
            RETURNS VARCHAR
            COMMENT = 'Test procedure'
            AS 'BEGIN RETURN ''done''; END;'
            """);

        Procedure procedure = engine.getCatalog()
            .getDatabase("TEST_DB")
            .getSchema("PUBLIC")
            .getProcedure("TEST_PROC");
        assertNotNull(procedure);
        assertEquals("Test procedure", procedure.getComment());
    }

    @Test
    public void testCreateUserWithComment() {
        engine.execute("CREATE USER test_user PASSWORD = 'secret' COMMENT = 'Test user account'");

        User user = engine.getCatalog().getUser("TEST_USER");
        assertNotNull(user);
        assertEquals("Test user account", user.getComment());
    }

    @Test
    public void testCreateRoleWithComment() {
        engine.execute("CREATE ROLE analyst COMMENT = 'Analyst role'");

        Role role = engine.getCatalog().getRole("ANALYST");
        assertNotNull(role);
        assertEquals("Analyst role", role.getComment());
    }

    @Test
    public void testCreateWithoutComment() {
        // Verify objects without comments work fine
        engine.execute("CREATE DATABASE test_db");
        Database db = engine.getCatalog().getDatabase("TEST_DB");
        assertNotNull(db);
        assertNull(db.getComment());
    }

    @Test
    public void testCommentWithSpecialCharacters() {
        engine.execute("CREATE DATABASE test_db COMMENT = 'Database with special chars: @#$%'");
        Database db = engine.getCatalog().getDatabase("TEST_DB");
        assertNotNull(db);
        assertEquals("Database with special chars: @#$%", db.getComment());
    }

    @Test
    public void testCommentWithQuotes() {
        engine.execute("CREATE DATABASE test_db COMMENT = 'Database with ''quotes'''");
        Database db = engine.getCatalog().getDatabase("TEST_DB");
        assertNotNull(db);
        assertEquals("Database with 'quotes'", db.getComment());
    }

    @Test
    public void testLongComment() {
        engine.execute("""
            CREATE DATABASE test_db COMMENT = 'This is a very long comment that describes the database in great detail. It contains multiple sentences and provides comprehensive documentation about the purpose and usage of this database object.'
            """);
        Database db = engine.getCatalog().getDatabase("TEST_DB");
        assertNotNull(db);
        assertEquals("This is a very long comment that describes the database in great detail. It contains multiple sentences and provides comprehensive documentation about the purpose and usage of this database object.", db.getComment());
    }
}
