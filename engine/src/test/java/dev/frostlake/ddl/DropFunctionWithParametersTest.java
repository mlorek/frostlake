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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

public class DropFunctionWithParametersTest extends BaseJdbcTest {
    private static final Logger logger = LoggerFactory.getLogger(DropFunctionWithParametersTest.class);

    @Test
    public void testDropFunctionWithoutParameters() throws SQLException {
        logger.info("Testing DROP FUNCTION without parameter types (backward compatible)");

        statement.execute("CREATE FUNCTION add_one(x INTEGER) RETURNS INTEGER LANGUAGE SQL AS 'SELECT x + 1'");

        // Drop without specifying parameters - should work
        statement.execute("DROP FUNCTION add_one");

        // Verify function is dropped
        assertThrows(SQLException.class, () -> {
            statement.executeQuery("SELECT add_one(5)");
        });
    }

    @Test
    public void testDropFunctionWithParameters() throws SQLException {
        logger.info("Testing DROP FUNCTION with parameter types");

        statement.execute("CREATE FUNCTION multiply(a INTEGER, b INTEGER) RETURNS INTEGER LANGUAGE SQL AS 'SELECT a * b'");

        // Drop with parameter types specified
        statement.execute("DROP FUNCTION multiply(INTEGER, INTEGER)");

        // Verify function is dropped
        assertThrows(SQLException.class, () -> {
            statement.executeQuery("SELECT multiply(3, 4)");
        });
    }

    @Test
    public void testDropFunctionWithEmptyParameters() throws SQLException {
        logger.info("Testing DROP FUNCTION with empty parameter list");

        statement.execute("CREATE FUNCTION get_constant() RETURNS INTEGER LANGUAGE SQL AS 'SELECT 42'");

        // Drop with empty parameter list
        statement.execute("DROP FUNCTION get_constant()");

        // Verify function is dropped
        assertThrows(SQLException.class, () -> {
            statement.executeQuery("SELECT get_constant()");
        });
    }

    @Test
    public void testDropFunctionParameterMismatch() throws SQLException {
        logger.info("Testing DROP FUNCTION with wrong parameter types throws error");

        statement.execute("CREATE FUNCTION calc_func(x INTEGER, y INTEGER) RETURNS INTEGER LANGUAGE SQL AS 'BEGIN RETURN x + y; END'");

        // Try to drop with wrong parameter types - should fail
        assertThrows(SQLException.class, () -> {
            statement.execute("DROP FUNCTION calc_func(VARCHAR, VARCHAR)");
        });
    }

    @Test
    public void testDropFunctionParameterCountMismatch() throws SQLException {
        logger.info("Testing DROP FUNCTION with wrong parameter count throws error");

        statement.execute("CREATE FUNCTION sum_func(x INTEGER, y INTEGER) RETURNS INTEGER LANGUAGE SQL AS 'BEGIN RETURN x + y; END'");

        // Try to drop with wrong number of parameters - should fail
        assertThrows(SQLException.class, () -> {
            statement.execute("DROP FUNCTION sum_func(INTEGER)");
        });
    }

    @Test
    public void testDropFunctionWithVarcharParameter() throws SQLException {
        logger.info("Testing DROP FUNCTION with VARCHAR parameter type");

        statement.execute("CREATE FUNCTION greet(name VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS 'SELECT name'");

        // Drop with VARCHAR parameter type
        statement.execute("DROP FUNCTION greet(VARCHAR)");

        // Verify function is dropped
        assertThrows(SQLException.class, () -> {
            statement.executeQuery("SELECT greet('Alice')");
        });
    }

    @Test
    public void testDropFunctionIfExists() throws SQLException {
        logger.info("Testing DROP FUNCTION IF EXISTS with parameters");

        statement.execute("CREATE FUNCTION test_func(x INTEGER) RETURNS INTEGER LANGUAGE SQL AS 'SELECT x'");

        // Drop with IF EXISTS and parameters
        statement.execute("DROP FUNCTION IF EXISTS test_func(INTEGER)");

        // Try to drop again with IF EXISTS - should not fail
        statement.execute("DROP FUNCTION IF EXISTS test_func(INTEGER)");
    }

    @Test
    public void testDropFunctionMixedDataTypes() throws SQLException {
        logger.info("Testing DROP FUNCTION with mixed data types");

        statement.execute("CREATE FUNCTION format_data(id INTEGER, name VARCHAR, active INTEGER) RETURNS VARCHAR LANGUAGE SQL AS 'SELECT name'");

        // Drop with mixed parameter types
        statement.execute("DROP FUNCTION format_data(INTEGER, VARCHAR, INTEGER)");

        // Verify function is dropped
        assertThrows(SQLException.class, () -> {
            statement.executeQuery("SELECT format_data(1, 'test', 1)");
        });
    }

    @Test
    public void testDropFunctionSchemaQualified() throws SQLException {
        logger.info("Testing DROP FUNCTION with schema-qualified name and parameters");

        statement.execute("CREATE SCHEMA test_schema");
        statement.execute("CREATE FUNCTION test_schema.compute(x INTEGER) RETURNS INTEGER LANGUAGE SQL AS 'SELECT x * 2'");

        // Drop schema-qualified function with parameters
        statement.execute("DROP FUNCTION test_schema.compute(INTEGER)");

        // Verify function is dropped
        assertThrows(SQLException.class, () -> {
            statement.executeQuery("SELECT test_schema.compute(5)");
        });
    }

    @Test
    public void testDropNonExistentFunctionWithParameters() throws SQLException {
        logger.info("Testing DROP FUNCTION for non-existent function with parameters");

        // Try to drop non-existent function - should fail
        assertThrows(SQLException.class, () -> {
            statement.execute("DROP FUNCTION non_existent(INTEGER, VARCHAR)");
        });

        // With IF EXISTS - should not fail
        statement.execute("DROP FUNCTION IF EXISTS non_existent(INTEGER, VARCHAR)");
    }
}
