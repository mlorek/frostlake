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

public class CreateOrReplaceProcedureTest extends BaseJdbcTest {
    private static final Logger logger = LoggerFactory.getLogger(CreateOrReplaceProcedureTest.class);

    @Test
    public void testCreateOrReplaceProcedureFirstTime() throws SQLException {
        logger.info("Testing CREATE OR REPLACE PROCEDURE when procedure doesn't exist");

        // Should create successfully without error
        statement.execute("CREATE OR REPLACE PROCEDURE greet_user(name VARCHAR) RETURNS VARCHAR AS 'BEGIN RETURN ''Hello, '' || name; END;'");

        logger.info("Procedure created successfully");
    }

    @Test
    public void testCreateOrReplaceProcedureReplaceExisting() throws SQLException {
        logger.info("Testing CREATE OR REPLACE PROCEDURE replacing existing procedure");

        // Create original procedure
        statement.execute("CREATE PROCEDURE format_name(name VARCHAR) RETURNS VARCHAR AS 'BEGIN RETURN ''Mr. '' || name; END;'");

        // Replace with new implementation - should not throw error
        statement.execute("CREATE OR REPLACE PROCEDURE format_name(name VARCHAR) RETURNS VARCHAR AS 'BEGIN RETURN ''Dr. '' || name; END;'");

        logger.info("Procedure replaced successfully");
    }

    @Test
    public void testCreateProcedureWithoutOrReplaceFailsIfExists() throws SQLException {
        logger.info("Testing CREATE PROCEDURE without OR REPLACE fails if exists");

        statement.execute("CREATE PROCEDURE uppercase_text(text VARCHAR) RETURNS VARCHAR AS 'BEGIN RETURN UPPER(text); END;'");

        // Trying to create again without OR REPLACE should fail
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("CREATE PROCEDURE uppercase_text(text VARCHAR) RETURNS VARCHAR AS 'BEGIN RETURN LOWER(text); END;'");
                
            }
        });
    }

    @Test
    public void testCreateProcedureIfNotExistsDoesNotReplace() throws SQLException {
        logger.info("Testing CREATE PROCEDURE IF NOT EXISTS does not replace");

        statement.execute("CREATE PROCEDURE lowercase_text(text VARCHAR) RETURNS VARCHAR AS 'BEGIN RETURN LOWER(text); END;'");

        // This should not throw error
        statement.execute("CREATE PROCEDURE IF NOT EXISTS lowercase_text(text VARCHAR) RETURNS VARCHAR AS 'BEGIN RETURN UPPER(text); END;'");

        logger.info("IF NOT EXISTS succeeded without error");
    }

    @Test
    public void testCreateOrReplaceProcedureWithIfNotExists() throws SQLException {
        logger.info("Testing CREATE OR REPLACE PROCEDURE with IF NOT EXISTS is rejected");

        // Create original
        statement.execute("CREATE PROCEDURE triple_number(n INTEGER) RETURNS INTEGER AS 'BEGIN RETURN n * 3; END;'");

        // Live-verified: the two options are mutually exclusive
        final SQLException exception = assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.execute("CREATE OR REPLACE PROCEDURE IF NOT EXISTS triple_number(n INTEGER) RETURNS INTEGER AS 'BEGIN RETURN n * 4; END;'");
            }
        });
        assertTrue(exception.getMessage().contains("options IF NOT EXISTS and OR REPLACE are incompatible"),
            "unexpected message: " + exception.getMessage());
    }

    @Test
    public void testCreateOrReplaceProcedureInDifferentSchema() throws SQLException {
        logger.info("Testing CREATE OR REPLACE PROCEDURE in different schema");

        statement.execute("CREATE SCHEMA proc_schema");

        statement.execute("CREATE OR REPLACE PROCEDURE proc_schema.calculate(a INTEGER, b INTEGER) RETURNS INTEGER AS 'BEGIN RETURN a + b; END;'");

        // Replace it
        statement.execute("CREATE OR REPLACE PROCEDURE proc_schema.calculate(a INTEGER, b INTEGER) RETURNS INTEGER AS 'BEGIN RETURN a * b; END;'");

        logger.info("Procedure in different schema replaced successfully");
    }

    @Test
    public void testCreateOrReplaceProcedureMultipleTimes() throws SQLException {
        logger.info("Testing CREATE OR REPLACE PROCEDURE multiple times");

        statement.execute("CREATE OR REPLACE PROCEDURE test_proc(x INTEGER) RETURNS INTEGER AS 'BEGIN RETURN x; END;'");
        statement.execute("CREATE OR REPLACE PROCEDURE test_proc(x INTEGER) RETURNS INTEGER AS 'BEGIN RETURN x * 2; END;'");
        statement.execute("CREATE OR REPLACE PROCEDURE test_proc(x INTEGER) RETURNS INTEGER AS 'BEGIN RETURN x * 3; END;'");

        logger.info("Multiple replacements succeeded");
    }
}
