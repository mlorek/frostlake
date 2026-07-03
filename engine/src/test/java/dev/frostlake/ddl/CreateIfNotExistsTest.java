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

import dev.frostlake.BaseJdbcTest;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CREATE IF NOT EXISTS statements
 */
public class CreateIfNotExistsTest extends BaseJdbcTest {

    @Test
    public void testCreateDatabaseIfNotExists() throws SQLException {
        // Create database
        assertDoesNotThrow(() -> statement.execute("CREATE DATABASE IF NOT EXISTS test_create_db"));

        // Create again should succeed with IF NOT EXISTS
        assertDoesNotThrow(() -> statement.execute("CREATE DATABASE IF NOT EXISTS test_create_db"));

        // Create without IF NOT EXISTS should fail

        // Cleanup
        statement.execute("DROP DATABASE test_create_db");
    }

    @Test
    public void testCreateSchemaIfNotExists() throws SQLException {
        // Create schema
        assertDoesNotThrow(() -> statement.execute("CREATE SCHEMA IF NOT EXISTS test_create_schema"));

        // Create again should succeed with IF NOT EXISTS
        assertDoesNotThrow(() -> statement.execute("CREATE SCHEMA IF NOT EXISTS test_create_schema"));

        // Create without IF NOT EXISTS should fail

        // Cleanup
        statement.execute("DROP SCHEMA test_create_schema");
    }

    @Test
    public void testCreateTableIfNotExists() throws SQLException {
        // Create table
        assertDoesNotThrow(() -> statement.execute(
                "CREATE TABLE IF NOT EXISTS test_create_table (id INTEGER, name VARCHAR)"
        ));

        // Create again should succeed with IF NOT EXISTS
        assertDoesNotThrow(() -> statement.execute(
                "CREATE TABLE IF NOT EXISTS test_create_table (id INTEGER, name VARCHAR)"
        ));

        // Create without IF NOT EXISTS should fail
        assertThrows(SQLException.class, () -> statement.execute(
                "CREATE TABLE test_create_table (id INTEGER, name VARCHAR)"
        ));

        // Cleanup
        statement.execute("DROP TABLE test_create_table");
    }

    @Test
    public void testCreateViewIfNotExists() throws SQLException {
        // Create table first
        statement.execute("CREATE TABLE test_table (id INTEGER, name VARCHAR)");

        // Create view
        assertDoesNotThrow(() -> statement.execute(
                "CREATE VIEW IF NOT EXISTS test_create_view AS SELECT * FROM test_table"
        ));

        // Create again should succeed with IF NOT EXISTS
        assertDoesNotThrow(() -> statement.execute(
                "CREATE VIEW IF NOT EXISTS test_create_view AS SELECT * FROM test_table"
        ));

        // Create without IF NOT EXISTS should fail
        assertThrows(SQLException.class, () -> statement.execute(
                "CREATE VIEW test_create_view AS SELECT * FROM test_table"
        ));

        // Cleanup
        statement.execute("DROP VIEW test_create_view");
    }

    @Test
    public void testCreateFunctionIfNotExists() throws SQLException {
        // Create function
        assertDoesNotThrow(() -> statement.execute(
                "CREATE FUNCTION IF NOT EXISTS test_create_func(x INTEGER) RETURNS INTEGER AS 'x + 1'"
        ));

        // Create again should succeed with IF NOT EXISTS
        assertDoesNotThrow(() -> statement.execute(
                "CREATE FUNCTION IF NOT EXISTS test_create_func(x INTEGER) RETURNS INTEGER AS 'x + 1'"
        ));

        // Create without IF NOT EXISTS should fail
        assertThrows(SQLException.class, () -> statement.execute(
                "CREATE FUNCTION test_create_func(x INTEGER) RETURNS INTEGER AS 'x + 1'"
        ));

        // Cleanup
        statement.execute("DROP FUNCTION test_create_func");
    }

    @Test
    public void testCreateProcedureIfNotExists() throws SQLException {
        // Create procedure
        assertDoesNotThrow(() -> statement.execute("""
                CREATE PROCEDURE IF NOT EXISTS test_create_proc(x INTEGER) RETURNS INTEGER AS $$\
                BEGIN RETURN x + 1; END;$$
                """));

        // Create again should succeed with IF NOT EXISTS
        assertDoesNotThrow(() -> statement.execute("""
                CREATE PROCEDURE IF NOT EXISTS test_create_proc(x INTEGER) RETURNS INTEGER AS $$\
                BEGIN RETURN x + 1; END;$$
                """));

        // Create without IF NOT EXISTS should fail
        assertThrows(SQLException.class, () -> statement.execute("""
                CREATE PROCEDURE test_create_proc(x INTEGER) RETURNS INTEGER AS $$\
                BEGIN RETURN x + 1; END;$$
                """));

        // Cleanup
        statement.execute("DROP PROCEDURE test_create_proc");
    }

    @Test
    public void testCreateTaskIfNotExists() throws SQLException {
        // Create task
        assertDoesNotThrow(() -> statement.execute("""
                CREATE TASK IF NOT EXISTS test_create_task
                WAREHOUSE = 'compute_wh'
                SCHEDULE = '60 MINUTES'
                AS SELECT 1
                """));

        // Create again should succeed with IF NOT EXISTS
        assertDoesNotThrow(() -> statement.execute("""
                CREATE TASK IF NOT EXISTS test_create_task
                WAREHOUSE = 'compute_wh'
                SCHEDULE = '60 MINUTES'
                AS SELECT 1
                """));

        // Create without IF NOT EXISTS should fail
        assertThrows(SQLException.class, () -> statement.execute("""
                CREATE TASK test_create_task
                WAREHOUSE = 'compute_wh'
                SCHEDULE = '60 MINUTES'
                AS SELECT 1
                """));

        // Cleanup
        statement.execute("DROP TASK test_create_task");
    }

    @Test
    public void testCreateStreamIfNotExists() throws SQLException {
        // Create table first
        statement.execute("CREATE TABLE test_stream_table (id INTEGER, name VARCHAR)");

        // Create stream
        assertDoesNotThrow(() -> statement.execute(
                "CREATE STREAM IF NOT EXISTS test_create_stream ON TABLE test_stream_table"
        ));

        // Create again should succeed with IF NOT EXISTS
        assertDoesNotThrow(() -> statement.execute(
                "CREATE STREAM IF NOT EXISTS test_create_stream ON TABLE test_stream_table"
        ));

        // Create without IF NOT EXISTS should fail
        assertThrows(SQLException.class, () -> statement.execute(
                "CREATE STREAM test_create_stream ON TABLE test_stream_table"
        ));

        // Cleanup
        statement.execute("DROP STREAM test_create_stream");
    }

    @Test
    public void testCreateWarehouseIfNotExists() throws SQLException {
        // Create warehouse
        assertDoesNotThrow(() -> statement.execute("CREATE WAREHOUSE IF NOT EXISTS test_create_wh"));

        // Create again should succeed with IF NOT EXISTS
        assertDoesNotThrow(() -> statement.execute("CREATE WAREHOUSE IF NOT EXISTS test_create_wh"));

        // Create without IF NOT EXISTS should fail

        // Cleanup
        statement.execute("DROP WAREHOUSE test_create_wh");
    }

    @Test
    public void testCreateStageIfNotExists() throws SQLException {
        // Create stage
        assertDoesNotThrow(() -> statement.execute(
                "CREATE STAGE IF NOT EXISTS test_create_stage URL = 's3://bucket/path'"
        ));

        // Create again should succeed with IF NOT EXISTS
        assertDoesNotThrow(() -> statement.execute(
                "CREATE STAGE IF NOT EXISTS test_create_stage URL = 's3://bucket/path'"
        ));

        // Create without IF NOT EXISTS should fail
        assertThrows(SQLException.class, () -> statement.execute(
                "CREATE STAGE test_create_stage URL = 's3://bucket/path'"
        ));

        // Cleanup
        statement.execute("DROP STAGE test_create_stage");
    }

    @Test
    public void testCreateUserIfNotExists() throws SQLException {
        // Create user
        assertDoesNotThrow(() -> statement.execute("CREATE USER IF NOT EXISTS test_create_user"));

        // Create again should succeed with IF NOT EXISTS
        assertDoesNotThrow(() -> statement.execute("CREATE USER IF NOT EXISTS test_create_user"));

        // Create without IF NOT EXISTS should fail

        // Cleanup
        statement.execute("DROP USER test_create_user");
    }

    @Test
    public void testCreateRoleIfNotExists() throws SQLException {
        // Create role
        assertDoesNotThrow(() -> statement.execute("CREATE ROLE IF NOT EXISTS test_create_role"));

        // Create again should succeed with IF NOT EXISTS
        assertDoesNotThrow(() -> statement.execute("CREATE ROLE IF NOT EXISTS test_create_role"));

        // Create without IF NOT EXISTS should fail

        // Cleanup
        statement.execute("DROP ROLE test_create_role");
    }

    @Test
    public void testIdempotentSetup() throws SQLException {
        // Test that setup scripts can be run multiple times
        String setupScript = """
                CREATE DATABASE IF NOT EXISTS app_db;
                CREATE SCHEMA IF NOT EXISTS app_db.app_schema;
                CREATE TABLE IF NOT EXISTS users (id INTEGER, name VARCHAR);
                CREATE VIEW IF NOT EXISTS active_users AS SELECT * FROM users;
                """;

        // Run setup script first time
        for (final String sqlLine : setupScript.split(";")) {
            String sql = sqlLine.trim();
            if (!sql.isEmpty()) {
                assertDoesNotThrow(() -> statement.execute(sql));
            }
        }

        // Run setup script second time - should succeed
        for (final String sqlLine : setupScript.split(";")) {
            String sql = sqlLine.trim();
            if (!sql.isEmpty()) {
                assertDoesNotThrow(() -> statement.execute(sql));
            }
        }

        // Cleanup
        statement.execute("DROP VIEW active_users");
        statement.execute("DROP TABLE users");
        statement.execute("DROP SCHEMA app_db.app_schema");
        statement.execute("DROP DATABASE app_db");
    }

    @Test
    public void testCreateAndDropWithIfClauses() throws SQLException {
        // Test combination of IF NOT EXISTS and IF EXISTS

        // Initial create
        statement.execute("CREATE TABLE IF NOT EXISTS test_table (id INTEGER)");

        // Drop and recreate idempotently
        statement.execute("DROP TABLE IF EXISTS test_table");
        statement.execute("CREATE TABLE IF NOT EXISTS test_table (id INTEGER)");

        // Verify table exists
        ResultSet rs = statement.executeQuery("SELECT COUNT(*) as cnt FROM test_table");
        assertTrue(rs.next());
        assertEquals(0, rs.getInt("cnt"));
        rs.close();

        // Drop multiple times
        statement.execute("DROP TABLE IF EXISTS test_table");
        statement.execute("DROP TABLE IF EXISTS test_table");

        // Recreate multiple times
        statement.execute("CREATE TABLE IF NOT EXISTS test_table (id INTEGER)");
        statement.execute("CREATE TABLE IF NOT EXISTS test_table (id INTEGER)");

        // Final cleanup
        statement.execute("DROP TABLE test_table");
    }
}
