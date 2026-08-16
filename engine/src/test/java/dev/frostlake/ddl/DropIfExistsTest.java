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
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Tests for DROP IF EXISTS statements
 */
public class DropIfExistsTest extends BaseJdbcTest {

    @Test
    public void testDropDatabaseIfExists() throws SQLException {
        // Drop non-existent database should succeed with IF EXISTS
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP DATABASE IF EXISTS nonexistent_db");
            }
        });

        // Create and drop database
        statement.execute("CREATE DATABASE test_drop_db");
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP DATABASE IF EXISTS test_drop_db");
            }
        });

        // Drop again should succeed with IF EXISTS
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP DATABASE IF EXISTS test_drop_db");
            }
        });

        // Drop without IF EXISTS should fail
    }

    @Test
    public void testDropSchemaIfExists() throws SQLException {
        // Drop non-existent schema should succeed with IF EXISTS
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP SCHEMA IF EXISTS nonexistent_schema");
            }
        });

        // Create and drop schema
        statement.execute("CREATE SCHEMA test_drop_schema");
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP SCHEMA IF EXISTS test_drop_schema");
            }
        });

        // Drop again should succeed with IF EXISTS
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP SCHEMA IF EXISTS test_drop_schema");
            }
        });

        // Drop without IF EXISTS should fail
    }

    @Test
    public void testDropTableIfExists() throws SQLException {
        // Drop non-existent table should succeed with IF EXISTS
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP TABLE IF EXISTS nonexistent_table");
            }
        });

        // Create and drop table
        statement.execute("CREATE TABLE test_drop_table (id INTEGER, name VARCHAR)");
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP TABLE IF EXISTS test_drop_table");
            }
        });

        // Drop again should succeed with IF EXISTS
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP TABLE IF EXISTS test_drop_table");
            }
        });

        // Drop without IF EXISTS should fail
    }

    @Test
    public void testDropViewIfExists() throws SQLException {
        // Drop non-existent view should succeed with IF EXISTS
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP VIEW IF EXISTS nonexistent_view");
            }
        });

        // Create table and view
        statement.execute("CREATE TABLE test_table (id INTEGER, name VARCHAR)");
        statement.execute("CREATE VIEW test_drop_view AS SELECT * FROM test_table");
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP VIEW IF EXISTS test_drop_view");
            }
        });

        // Drop again should succeed with IF EXISTS
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP VIEW IF EXISTS test_drop_view");
            }
        });

        // Drop without IF EXISTS should fail
    }

    @Test
    public void testDropFunctionIfExists() throws SQLException {
        // Drop non-existent function should succeed with IF EXISTS
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP FUNCTION IF EXISTS nonexistent_func()");
            }
        });

        // Create and drop function
        statement.execute("CREATE FUNCTION test_drop_func(x INTEGER) RETURNS INTEGER AS 'x + 1'");
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP FUNCTION IF EXISTS test_drop_func(INTEGER)");
            }
        });

        // Drop again should succeed with IF EXISTS
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP FUNCTION IF EXISTS test_drop_func(INTEGER)");
            }
        });

        // Drop without IF EXISTS should fail
    }

    @Test
    public void testDropProcedureIfExists() throws SQLException {
        // Drop non-existent procedure should succeed with IF EXISTS
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP PROCEDURE IF EXISTS nonexistent_proc()");
            }
        });

        // Create and drop procedure
        statement.execute("""
                CREATE PROCEDURE test_drop_proc(x INTEGER) RETURNS INTEGER AS $$\
                BEGIN RETURN x + 1; END;$$
                """);
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP PROCEDURE IF EXISTS test_drop_proc(INTEGER)");
            }
        });

        // Drop again should succeed with IF EXISTS
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP PROCEDURE IF EXISTS test_drop_proc(INTEGER)");
            }
        });

        // Drop without IF EXISTS should fail
    }

    @Test
    public void testDropTaskIfExists() throws SQLException {
        // Drop non-existent task should succeed with IF EXISTS
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP TASK IF EXISTS nonexistent_task");
            }
        });

        // Create and drop task
        statement.execute("""
                CREATE TASK test_drop_task
                WAREHOUSE = 'compute_wh'
                SCHEDULE = '60 MINUTES'
                AS SELECT 1
                """);
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP TASK IF EXISTS test_drop_task");
            }
        });

        // Drop again should succeed with IF EXISTS
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP TASK IF EXISTS test_drop_task");
            }
        });

        // Drop without IF EXISTS should fail
    }

    @Test
    public void testDropStreamIfExists() throws SQLException {
        // Drop non-existent stream should succeed with IF EXISTS
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP STREAM IF EXISTS nonexistent_stream");
            }
        });

        // Create table and stream
        statement.execute("CREATE TABLE test_stream_table (id INTEGER, name VARCHAR)");
        statement.execute("CREATE STREAM test_drop_stream ON TABLE test_stream_table");
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP STREAM IF EXISTS test_drop_stream");
            }
        });

        // Drop again should succeed with IF EXISTS
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP STREAM IF EXISTS test_drop_stream");
            }
        });

        // Drop without IF EXISTS should fail
    }

    @Test
    public void testDropWarehouseIfExists() throws SQLException {
        // Drop non-existent warehouse should succeed with IF EXISTS
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP WAREHOUSE IF EXISTS nonexistent_wh");
            }
        });

        // Create and drop warehouse
        statement.execute("CREATE WAREHOUSE test_drop_wh");
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP WAREHOUSE IF EXISTS test_drop_wh");
            }
        });

        // Drop again should succeed with IF EXISTS
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP WAREHOUSE IF EXISTS test_drop_wh");
            }
        });

        // Drop without IF EXISTS should fail
    }

    @Test
    public void testDropStageIfExists() throws SQLException {
        // Drop non-existent stage should succeed with IF EXISTS
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP STAGE IF EXISTS nonexistent_stage");
            }
        });

        // Create and drop stage
        statement.execute("CREATE STAGE test_drop_stage URL = 's3://bucket/path'");
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP STAGE IF EXISTS test_drop_stage");
            }
        });

        // Drop again should succeed with IF EXISTS
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP STAGE IF EXISTS test_drop_stage");
            }
        });

        // Drop without IF EXISTS should fail
    }

    @Test
    public void testDropUserIfExists() throws SQLException {
        // Drop non-existent user should succeed with IF EXISTS
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP USER IF EXISTS nonexistent_user");
            }
        });

        // Create and drop user
        statement.execute("CREATE USER test_drop_user");
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP USER IF EXISTS test_drop_user");
            }
        });

        // Drop again should succeed with IF EXISTS
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP USER IF EXISTS test_drop_user");
            }
        });

        // Drop without IF EXISTS should fail
    }

    @Test
    public void testDropRoleIfExists() throws SQLException {
        // Drop non-existent role should succeed with IF EXISTS
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP ROLE IF EXISTS nonexistent_role");
            }
        });

        // Create and drop role
        statement.execute("CREATE ROLE test_drop_role");
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP ROLE IF EXISTS test_drop_role");
            }
        });

        // Drop again should succeed with IF EXISTS
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP ROLE IF EXISTS test_drop_role");
            }
        });

        // Drop without IF EXISTS should fail
    }

    @Test
    public void testMultipleDropIfExistsInSequence() throws SQLException {
        // Test multiple DROP IF EXISTS in sequence
        statement.execute("CREATE TABLE t1 (id INTEGER)");
        statement.execute("CREATE TABLE t2 (id INTEGER)");
        statement.execute("CREATE VIEW v1 AS SELECT * FROM t1");

        // Drop tables and view with IF EXISTS
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP TABLE IF EXISTS t1");
            }
        });
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP VIEW IF EXISTS v1");
            }
        });
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP TABLE IF EXISTS t2");
            }
        });

        // Drop again - should all succeed
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP TABLE IF EXISTS t1");
            }
        });
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP VIEW IF EXISTS v1");
            }
        });
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP TABLE IF EXISTS t2");
            }
        });
    }

    @Test
    public void testDropIfExistsWithQualifiedNames() throws SQLException {
        // Test with schema-qualified names
        statement.execute("CREATE SCHEMA test_schema");
        statement.execute("CREATE TABLE test_schema.qualified_table (id INTEGER)");

        // Drop with qualified name
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP TABLE IF EXISTS test_schema.qualified_table");
            }
        });

        // Drop again with IF EXISTS
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP TABLE IF EXISTS test_schema.qualified_table");
            }
        });

        // Drop non-existent with qualified name
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP TABLE IF EXISTS test_schema.nonexistent");
            }
        });
    }
}
