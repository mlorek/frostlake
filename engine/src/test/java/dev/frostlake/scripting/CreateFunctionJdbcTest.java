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

import dev.frostlake.BaseJdbcTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CreateFunctionJdbcTest extends BaseJdbcTest {

    /**
     * Whether {@code SHOW FUNCTIONS} lists a function with this name. The rows are scanned rather than read
     * positionally: both backends list the whole built-in library alongside the user-defined functions, so
     * the function just created is not necessarily the first row.
     */
    private boolean functionListed(final String name) throws SQLException {
        try (final ResultSet rs = statement.executeQuery("SHOW FUNCTIONS")) {
            while (rs.next()) {
                if (name.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** How many of the listed functions are user-defined (is_builtin = 'N'). */
    private int userFunctionCount() throws SQLException {
        int count = 0;
        try (final ResultSet rs = statement.executeQuery("SHOW FUNCTIONS")) {
            while (rs.next()) {
                if ("N".equals(rs.getString("is_builtin"))) {
                    count++;
                }
            }
        }
        return count;
    }

    @Test
    public void testCreateSimpleFunction() throws SQLException {
        // Create a simple scalar function
        statement.execute("CREATE FUNCTION add_numbers(x INTEGER, y INTEGER) RETURNS INTEGER AS 'x + y'");

        // Verify function was created by showing functions
        assertTrue(functionListed("ADD_NUMBERS"), "ADD_NUMBERS should be listed by SHOW FUNCTIONS");
    }

    @Test
    public void testCreateFunctionWithNoParameters() throws SQLException {
        // Create a function with no parameters
        // Snowflake checks a SQL UDF's DECLARED return type against the body's ACTUAL one at CREATE
        // (RETURNS FLOAT over a decimal literal fails "Declared return type
        // 'FLOAT' is incompatible with actual return type 'NUMBER(6,5)'"), so the body casts.
        statement.execute("CREATE FUNCTION get_pi() RETURNS FLOAT AS '3.14159::FLOAT'");

        // Verify function was created
        assertTrue(functionListed("GET_PI"), "GET_PI should be listed by SHOW FUNCTIONS");
    }

    @Test
    public void testCreateFunctionWithQualifiedName() throws SQLException {
        // Create schema
        statement.execute("CREATE SCHEMA my_schema");

        // Create function with schema-qualified name
        statement.execute("CREATE FUNCTION my_schema.multiply(a INTEGER, b INTEGER) RETURNS INTEGER AS 'a * b'");

        // Switch to the schema and verify
        statement.execute("USE SCHEMA my_schema");
        assertTrue(functionListed("MULTIPLY"), "MULTIPLY should be listed by SHOW FUNCTIONS");
    }

    @Test
    public void testCreateTableFunction() throws SQLException {
        // Create a table function
        statement.execute("""
            CREATE FUNCTION get_values() RETURNS TABLE(id INTEGER, name VARCHAR) AS 'SELECT 1, ''test'''
            """);

        // Verify function was created
        assertTrue(functionListed("GET_VALUES"), "GET_VALUES should be listed by SHOW FUNCTIONS");
    }

    @Test
    public void testDropFunctionExists() throws SQLException {
        // Create function
        statement.execute("CREATE FUNCTION test_func(x INTEGER) RETURNS INTEGER AS 'x * 2'");

        // Verify it exists
        assertTrue(functionListed("TEST_FUNC"), "TEST_FUNC should be listed by SHOW FUNCTIONS");

        // Drop function
        statement.execute("DROP FUNCTION test_func(INTEGER)");

        // Verify it's gone by showing functions
        assertEquals(0, userFunctionCount(), "Function should be dropped (built-ins remain)");

        // Drop with IF EXISTS should not throw
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.execute("DROP FUNCTION IF EXISTS test_func(INTEGER)");
            }
        });
    }

    @Test
    public void testShowFunctions() throws SQLException {
        // Create multiple functions
        statement.execute("CREATE FUNCTION func1(x INTEGER) RETURNS INTEGER AS 'x'");
        statement.execute("CREATE FUNCTION func2(x INTEGER, y INTEGER) RETURNS INTEGER AS 'x + y'");

        assertEquals(2, userFunctionCount(), "Should have 2 user-defined functions");
    }

    @Test
    public void testCreateFunctionWithIntegerParameter() throws SQLException {
        // Create function with INTEGER parameter
        statement.execute("CREATE FUNCTION double_val(x INTEGER) RETURNS INTEGER AS 'x * 2'");

        // Verify function was created
        assertTrue(functionListed("DOUBLE_VAL"), "DOUBLE_VAL should be listed by SHOW FUNCTIONS");
    }
}
