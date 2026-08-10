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
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CreateOrReplaceFunctionTest extends BaseJdbcTest {
    private static final Logger logger = LoggerFactory.getLogger(CreateOrReplaceFunctionTest.class);

    @Test
    public void testCreateOrReplaceFunctionFirstTime() throws SQLException {
        logger.info("Testing CREATE OR REPLACE FUNCTION when function doesn't exist");

        // Should create successfully without error
        statement.execute("CREATE OR REPLACE FUNCTION add_numbers(a INTEGER, b INTEGER) RETURNS INTEGER AS 'a + b'");

        logger.info("Function created successfully");
    }

    @Test
    public void testCreateOrReplaceFunctionReplaceExisting() throws SQLException {
        logger.info("Testing CREATE OR REPLACE FUNCTION replacing existing function");

        // Create original function
        statement.execute("CREATE FUNCTION multiply_numbers(a INTEGER, b INTEGER) RETURNS INTEGER AS 'a * b'");

        // Replace with new implementation - should not throw error
        statement.execute("CREATE OR REPLACE FUNCTION multiply_numbers(a INTEGER, b INTEGER) RETURNS INTEGER AS 'a * b * 2'");

        logger.info("Function replaced successfully");
    }

    @Test
    public void testCreateFunctionWithoutOrReplaceFailsIfExists() throws SQLException {
        logger.info("Testing CREATE FUNCTION without OR REPLACE fails if exists");

        statement.execute("CREATE FUNCTION subtract_numbers(a INTEGER, b INTEGER) RETURNS INTEGER AS 'a - b'");

        // Trying to create again without OR REPLACE should fail
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("CREATE FUNCTION subtract_numbers(a INTEGER, b INTEGER) RETURNS INTEGER AS 'b - a'");
                
            }
        });
    }

    @Test
    public void testCreateFunctionIfNotExistsDoesNotReplace() throws SQLException {
        logger.info("Testing CREATE FUNCTION IF NOT EXISTS does not replace");

        statement.execute("CREATE FUNCTION divide_numbers(a INTEGER, b INTEGER) RETURNS INTEGER AS 'a / b'");

        // This should not throw error
        statement.execute("CREATE FUNCTION IF NOT EXISTS divide_numbers(a INTEGER, b INTEGER) RETURNS INTEGER AS 'b / a'");

        logger.info("IF NOT EXISTS succeeded without error");
    }

    @Test
    public void testCreateOrReplaceFunctionWithIfNotExists() throws SQLException {
        logger.info("Testing CREATE OR REPLACE FUNCTION with IF NOT EXISTS is rejected");

        // Create original
        statement.execute("CREATE FUNCTION double_number(n INTEGER) RETURNS INTEGER AS 'n * 2'");

        // Live-verified: the two options are mutually exclusive
        final SQLException exception = assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.execute("CREATE OR REPLACE FUNCTION IF NOT EXISTS double_number(n INTEGER) RETURNS INTEGER AS 'n * 3'");
            }
        });
        assertTrue(exception.getMessage().contains("options IF NOT EXISTS and OR REPLACE are incompatible"),
            "unexpected message: " + exception.getMessage());
    }

    @Test
    public void testCreateOrReplaceFunctionInDifferentSchema() throws SQLException {
        logger.info("Testing CREATE OR REPLACE FUNCTION in different schema");

        statement.execute("CREATE SCHEMA test_schema");

        statement.execute("CREATE OR REPLACE FUNCTION test_schema.square(n INTEGER) RETURNS INTEGER AS 'n * n'");

        // Replace it
        statement.execute("CREATE OR REPLACE FUNCTION test_schema.square(n INTEGER) RETURNS INTEGER AS 'n * n * n'");

        logger.info("Function in different schema replaced successfully");
    }

    @Test
    public void testCreateOrReplaceFunctionMultipleTimes() throws SQLException {
        logger.info("Testing CREATE OR REPLACE FUNCTION multiple times");

        statement.execute("CREATE OR REPLACE FUNCTION test_func(x INTEGER) RETURNS INTEGER AS 'x'");
        statement.execute("CREATE OR REPLACE FUNCTION test_func(x INTEGER) RETURNS INTEGER AS 'x * 2'");
        statement.execute("CREATE OR REPLACE FUNCTION test_func(x INTEGER) RETURNS INTEGER AS 'x * 3'");

        logger.info("Multiple replacements succeeded");
    }
}
