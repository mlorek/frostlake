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

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

public class CreateFunctionJdbcTest extends BaseJdbcTest {

    @Test
    public void testCreateSimpleFunction() throws SQLException {
        // Create a simple scalar function
        statement.execute("CREATE FUNCTION add_numbers(x INTEGER, y INTEGER) RETURNS INTEGER AS 'x + y'");

        // Verify function was created by showing functions
        ResultSet rs = statement.executeQuery("SHOW FUNCTIONS");
        assertTrue(rs.next(), "Should have at least one function");

        String functionName = rs.getString("name");
        assertEquals("ADD_NUMBERS", functionName);

        rs.close();
    }

    @Test
    public void testCreateFunctionWithNoParameters() throws SQLException {
        // Create a function with no parameters
        statement.execute("CREATE FUNCTION get_pi() RETURNS FLOAT AS '3.14159'");

        // Verify function was created
        ResultSet rs = statement.executeQuery("SHOW FUNCTIONS");
        assertTrue(rs.next(), "Function should be created");

        String functionName = rs.getString("name");
        assertEquals("GET_PI", functionName);

        rs.close();
    }

    @Test
    public void testCreateFunctionWithQualifiedName() throws SQLException {
        // Create schema
        statement.execute("CREATE SCHEMA my_schema");

        // Create function with schema-qualified name
        statement.execute("CREATE FUNCTION my_schema.multiply(a INTEGER, b INTEGER) RETURNS INTEGER AS 'a * b'");

        // Switch to the schema and verify
        statement.execute("USE SCHEMA my_schema");
        ResultSet rs = statement.executeQuery("SHOW FUNCTIONS");
        assertTrue(rs.next(), "Function should be created");

        String functionName = rs.getString("name");
        assertEquals("MULTIPLY", functionName);

        rs.close();
    }

    @Test
    public void testCreateTableFunction() throws SQLException {
        // Create a table function
        statement.execute("""
            CREATE FUNCTION get_values() RETURNS TABLE(id INTEGER, name VARCHAR) AS 'SELECT 1, ''test'''
            """);

        // Verify function was created
        ResultSet rs = statement.executeQuery("SHOW FUNCTIONS");
        assertTrue(rs.next(), "Function should be created");

        String functionName = rs.getString("name");
        assertEquals("GET_VALUES", functionName);

        rs.close();
    }

    @Test
    public void testDropFunctionExists() throws SQLException {
        // Create function
        statement.execute("CREATE FUNCTION test_func(x INTEGER) RETURNS INTEGER AS 'x * 2'");

        // Verify it exists
        ResultSet rs = statement.executeQuery("SHOW FUNCTIONS");
        assertTrue(rs.next(), "Function should exist");
        rs.close();

        // Drop function
        statement.execute("DROP FUNCTION test_func");

        // Verify it's gone by showing functions
        ResultSet rs2 = statement.executeQuery("SHOW FUNCTIONS");
        int userFunctions = 0;
        while (rs2.next()) {
            if ("N".equals(rs2.getString("is_builtin"))) {
                userFunctions++;
            }
        }
        assertEquals(0, userFunctions, "Function should be dropped (built-ins remain)");
        rs2.close();

        // Drop with IF EXISTS should not throw
        assertDoesNotThrow(() -> statement.execute("DROP FUNCTION IF EXISTS test_func"));
    }

    @Test
    public void testShowFunctions() throws SQLException {
        // Create multiple functions
        statement.execute("CREATE FUNCTION func1(x INTEGER) RETURNS INTEGER AS 'x'");
        statement.execute("CREATE FUNCTION func2(x INTEGER, y INTEGER) RETURNS INTEGER AS 'x + y'");

        // Show functions
        ResultSet rs = statement.executeQuery("SHOW FUNCTIONS");

        int userFunctions = 0;
        while (rs.next()) {
            if ("N".equals(rs.getString("is_builtin"))) {
                userFunctions++;
            }
        }

        assertEquals(2, userFunctions, "Should have 2 user-defined functions");
        rs.close();
    }

    @Test
    public void testCreateFunctionWithIntegerParameter() throws SQLException {
        // Create function with INTEGER parameter
        statement.execute("CREATE FUNCTION double_val(x INTEGER) RETURNS INTEGER AS 'x * 2'");

        // Verify function was created
        ResultSet rs = statement.executeQuery("SHOW FUNCTIONS");
        assertTrue(rs.next(), "Function should be created");

        String functionName = rs.getString("name");
        assertEquals("DOUBLE_VAL", functionName);

        rs.close();
    }
}
