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

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

public class CreateOrReplaceFunctionTest extends BaseJdbcTest {
    private static final Logger logger = LoggerFactory.getLogger(CreateOrReplaceFunctionTest.class);

    @Test
    public void testCreateOrReplaceFunctionFirstTime() throws SQLException {
        logger.info("Testing CREATE OR REPLACE FUNCTION when function doesn't exist");

        // Should create successfully without error
        statement.execute("CREATE OR REPLACE FUNCTION add_numbers(a INTEGER, b INTEGER) RETURNS INTEGER AS 'BEGIN RETURN a + b; END;'");

        logger.info("Function created successfully");
    }

    @Test
    public void testCreateOrReplaceFunctionReplaceExisting() throws SQLException {
        logger.info("Testing CREATE OR REPLACE FUNCTION replacing existing function");

        // Create original function
        statement.execute("CREATE FUNCTION multiply_numbers(a INTEGER, b INTEGER) RETURNS INTEGER AS 'BEGIN RETURN a * b; END;'");

        // Replace with new implementation - should not throw error
        statement.execute("CREATE OR REPLACE FUNCTION multiply_numbers(a INTEGER, b INTEGER) RETURNS INTEGER AS 'BEGIN RETURN a * b * 2; END;'");

        logger.info("Function replaced successfully");
    }

    @Test
    public void testCreateFunctionWithoutOrReplaceFailsIfExists() throws SQLException {
        logger.info("Testing CREATE FUNCTION without OR REPLACE fails if exists");

        statement.execute("CREATE FUNCTION subtract_numbers(a INTEGER, b INTEGER) RETURNS INTEGER AS 'BEGIN RETURN a - b; END;'");

        // Trying to create again without OR REPLACE should fail
        assertThrows(SQLException.class, () -> {
            statement.execute("CREATE FUNCTION subtract_numbers(a INTEGER, b INTEGER) RETURNS INTEGER AS 'BEGIN RETURN b - a; END;'");
        });
    }

    @Test
    public void testCreateFunctionIfNotExistsDoesNotReplace() throws SQLException {
        logger.info("Testing CREATE FUNCTION IF NOT EXISTS does not replace");

        statement.execute("CREATE FUNCTION divide_numbers(a INTEGER, b INTEGER) RETURNS INTEGER AS 'BEGIN RETURN a / b; END;'");

        // This should not throw error
        statement.execute("CREATE FUNCTION IF NOT EXISTS divide_numbers(a INTEGER, b INTEGER) RETURNS INTEGER AS 'BEGIN RETURN b / a; END;'");

        logger.info("IF NOT EXISTS succeeded without error");
    }

    @Test
    public void testCreateOrReplaceFunctionWithIfNotExists() throws SQLException {
        logger.info("Testing CREATE OR REPLACE FUNCTION with IF NOT EXISTS");

        // Create original
        statement.execute("CREATE FUNCTION double_number(n INTEGER) RETURNS INTEGER AS 'BEGIN RETURN n * 2; END;'");

        // This should replace despite IF NOT EXISTS
        statement.execute("CREATE OR REPLACE FUNCTION IF NOT EXISTS double_number(n INTEGER) RETURNS INTEGER AS 'BEGIN RETURN n * 3; END;'");

        logger.info("OR REPLACE with IF NOT EXISTS succeeded");
    }

    @Test
    public void testCreateOrReplaceFunctionInDifferentSchema() throws SQLException {
        logger.info("Testing CREATE OR REPLACE FUNCTION in different schema");

        statement.execute("CREATE SCHEMA test_schema");

        statement.execute("CREATE OR REPLACE FUNCTION test_schema.square(n INTEGER) RETURNS INTEGER AS 'BEGIN RETURN n * n; END;'");

        // Replace it
        statement.execute("CREATE OR REPLACE FUNCTION test_schema.square(n INTEGER) RETURNS INTEGER AS 'BEGIN RETURN n * n * n; END;'");

        logger.info("Function in different schema replaced successfully");
    }

    @Test
    public void testCreateOrReplaceFunctionMultipleTimes() throws SQLException {
        logger.info("Testing CREATE OR REPLACE FUNCTION multiple times");

        statement.execute("CREATE OR REPLACE FUNCTION test_func(x INTEGER) RETURNS INTEGER AS 'BEGIN RETURN x; END;'");
        statement.execute("CREATE OR REPLACE FUNCTION test_func(x INTEGER) RETURNS INTEGER AS 'BEGIN RETURN x * 2; END;'");
        statement.execute("CREATE OR REPLACE FUNCTION test_func(x INTEGER) RETURNS INTEGER AS 'BEGIN RETURN x * 3; END;'");

        logger.info("Multiple replacements succeeded");
    }
}
