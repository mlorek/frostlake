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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Schema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CreateFunctionTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    @Test
    public void testCreateSimpleFunction() {
        // Setup
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA PUBLIC");

        // Create a simple scalar function
        engine.execute("CREATE FUNCTION add_numbers(x INTEGER, y INTEGER) RETURNS INTEGER AS 'x + y'");

        // Verify function was created
        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Function func = schema.getFunction("add_numbers");

        assertNotNull(func, "Function should be created");
        assertEquals("ADD_NUMBERS", func.getName());
        assertEquals(2, func.getParameters().size());
        assertEquals("X", func.getParameters().get(0).getName());
        assertEquals("Y", func.getParameters().get(1).getName());
        assertFalse(func.isTableFunction(), "Should be scalar function");
        assertEquals("x + y", func.getBody());
    }

    @Test
    public void testCreateFunctionWithNoParameters() {
        // Setup
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA PUBLIC");

        // Create a function with no parameters
        engine.execute("CREATE FUNCTION get_pi() RETURNS FLOAT AS '3.14159'");

        // Verify function was created
        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Function func = schema.getFunction("get_pi");

        assertNotNull(func, "Function should be created");
        assertEquals("GET_PI", func.getName());
        assertEquals(0, func.getParameters().size());
        assertFalse(func.isTableFunction(), "Should be scalar function");
        assertEquals("3.14159", func.getBody());
    }

    @Test
    public void testCreateFunctionWithQualifiedName() {
        // Setup
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE SCHEMA my_schema");

        // Create function with schema-qualified name
        engine.execute("CREATE FUNCTION my_schema.multiply(a INTEGER, b INTEGER) RETURNS INTEGER AS 'a * b'");

        // Verify function was created in correct schema
        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("MY_SCHEMA");
        Function func = schema.getFunction("multiply");

        assertNotNull(func, "Function should be created");
        assertEquals("MULTIPLY", func.getName());
        assertEquals(2, func.getParameters().size());
        assertEquals("a * b", func.getBody());
    }

    @Test
    public void testCreateTableFunction() {
        // Setup
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA PUBLIC");

        // Create a table function
        engine.execute("CREATE FUNCTION get_values() RETURNS TABLE(id INTEGER, name VARCHAR) AS 'SELECT 1, ''test'''");

        // Verify function was created
        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Function func = schema.getFunction("get_values");

        assertNotNull(func, "Function should be created");
        assertEquals("GET_VALUES", func.getName());
        assertTrue(func.isTableFunction(), "Should be table function");
        assertEquals("SELECT 1, 'test'", func.getBody());
    }

    @Test
    public void testCreateFunctionWithVarcharParameter() {
        // Setup
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA PUBLIC");

        // Create function with VARCHAR parameter
        engine.execute("CREATE FUNCTION double_val(x INTEGER) RETURNS INTEGER AS 'x * 2'");

        // Verify function was created
        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Function func = schema.getFunction("double_val");

        assertNotNull(func, "Function should be created");
        assertEquals("DOUBLE_VAL", func.getName());
        assertEquals(1, func.getParameters().size());
        assertEquals("X", func.getParameters().get(0).getName());
        assertEquals("x * 2", func.getBody());
    }

    @Test
    public void testDropFunctionExists() {
        // Setup
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA PUBLIC");

        // Create and then drop function
        engine.execute("CREATE FUNCTION test_func(x INTEGER) RETURNS INTEGER AS 'x * 2'");

        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        assertNotNull(schema.getFunction("test_func"), "Function should exist");

        engine.execute("DROP FUNCTION test_func(INTEGER)");

        // Verify function was dropped
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            schema.getFunction("test_func");
        });
        assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    public void testShowFunctions() {
        // Setup
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA PUBLIC");

        // Create multiple functions
        engine.execute("CREATE FUNCTION func1(x INTEGER) RETURNS INTEGER AS 'x'");
        engine.execute("CREATE FUNCTION func2(x INTEGER, y INTEGER) RETURNS INTEGER AS 'x + y'");

        // Show functions
        var result = engine.showFunctions();

        assertNotNull(result);
        assertEquals(2, result.getRowCount());
    }
}
