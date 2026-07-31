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

public class DropProcedureWithParametersTest extends BaseJdbcTest {
    private static final Logger logger = LoggerFactory.getLogger(DropProcedureWithParametersTest.class);

    @Test
    public void testDropProcedureRequiresTheSignature() throws SQLException {
        logger.info("Testing DROP PROCEDURE requires the argument-type signature (Snowflake-verified)");

        statement.execute("CREATE PROCEDURE increment_proc(x INTEGER) RETURNS INTEGER LANGUAGE SQL AS 'SELECT x + 1'");

        // Snowflake requires the signature — the bare form is a syntax error.
        assertThrows(SQLException.class, () -> {
            statement.execute("DROP PROCEDURE increment_proc");
        });

        statement.execute("DROP PROCEDURE increment_proc(INTEGER)");
        assertThrows(SQLException.class, () -> {
            statement.execute("CALL increment_proc(5)");
        });
    }

    @Test
    public void testDropProcedureWithParameters() throws SQLException {
        logger.info("Testing DROP PROCEDURE with parameter types");

        statement.execute("CREATE PROCEDURE sum_proc(a INTEGER, b INTEGER) RETURNS INTEGER LANGUAGE SQL AS 'SELECT a + b'");

        // Drop with parameter types specified
        statement.execute("DROP PROCEDURE sum_proc(INTEGER, INTEGER)");

        // Verify procedure is dropped
        assertThrows(SQLException.class, () -> {
            statement.execute("CALL sum_proc(10, 20)");
        });
    }

    @Test
    public void testDropProcedureWithEmptyParameters() throws SQLException {
        logger.info("Testing DROP PROCEDURE with empty parameter list");

        statement.execute("CREATE PROCEDURE no_args_proc() RETURNS INTEGER LANGUAGE SQL AS 'SELECT 100'");

        // Drop with empty parameter list
        statement.execute("DROP PROCEDURE no_args_proc()");

        // Verify procedure is dropped
        assertThrows(SQLException.class, () -> {
            statement.execute("CALL no_args_proc()");
        });
    }

    @Test
    public void testDropProcedureParameterMismatch() throws SQLException {
        logger.info("Testing DROP PROCEDURE with wrong parameter types throws error");

        statement.execute("CREATE PROCEDURE calc_proc(x INTEGER, y INTEGER) RETURNS INTEGER LANGUAGE SQL AS 'BEGIN RETURN 42; END'");

        // Try to drop with wrong parameter types - should fail
        assertThrows(SQLException.class, () -> {
            statement.execute("DROP PROCEDURE calc_proc(VARCHAR, INTEGER)");
        });
    }

    @Test
    public void testDropProcedureParameterCountMismatch() throws SQLException {
        logger.info("Testing DROP PROCEDURE with wrong parameter count throws error");

        statement.execute("CREATE PROCEDURE three_arg_proc(a INTEGER, b INTEGER, c INTEGER) RETURNS INTEGER LANGUAGE SQL AS 'BEGIN RETURN 100; END'");

        // Try to drop with wrong number of parameters - should fail
        assertThrows(SQLException.class, () -> {
            statement.execute("DROP PROCEDURE three_arg_proc(INTEGER, INTEGER)");
        });
    }

    @Test
    public void testDropProcedureWithVarcharParameter() throws SQLException {
        logger.info("Testing DROP PROCEDURE with VARCHAR parameter type");

        statement.execute("CREATE PROCEDURE string_proc(text VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS 'SELECT text'");

        // Drop with VARCHAR parameter type
        statement.execute("DROP PROCEDURE string_proc(VARCHAR)");

        // Verify procedure is dropped
        assertThrows(SQLException.class, () -> {
            statement.execute("CALL string_proc('hello')");
        });
    }

    @Test
    public void testDropProcedureIfExists() throws SQLException {
        logger.info("Testing DROP PROCEDURE IF EXISTS with parameters");

        statement.execute("CREATE PROCEDURE test_proc(x INTEGER) RETURNS INTEGER LANGUAGE SQL AS 'SELECT x * 2'");

        // Drop with IF EXISTS and parameters
        statement.execute("DROP PROCEDURE IF EXISTS test_proc(INTEGER)");

        // Try to drop again with IF EXISTS - should not fail
        statement.execute("DROP PROCEDURE IF EXISTS test_proc(INTEGER)");
    }

    @Test
    public void testDropProcedureMixedDataTypes() throws SQLException {
        logger.info("Testing DROP PROCEDURE with mixed data types");

        statement.execute("CREATE PROCEDURE mixed_proc(id INTEGER, name VARCHAR, count INTEGER) RETURNS VARCHAR LANGUAGE SQL AS 'SELECT name'");

        // Drop with mixed parameter types
        statement.execute("DROP PROCEDURE mixed_proc(INTEGER, VARCHAR, INTEGER)");

        // Verify procedure is dropped
        assertThrows(SQLException.class, () -> {
            statement.execute("CALL mixed_proc(1, 'test', 10)");
        });
    }

    @Test
    public void testDropProcedureSchemaQualified() throws SQLException {
        logger.info("Testing DROP PROCEDURE with schema-qualified name and parameters");

        statement.execute("CREATE SCHEMA proc_schema");
        statement.execute("CREATE PROCEDURE proc_schema.double_value(x INTEGER) RETURNS INTEGER LANGUAGE SQL AS 'SELECT x * 2'");

        // Drop schema-qualified procedure with parameters
        statement.execute("DROP PROCEDURE proc_schema.double_value(INTEGER)");

        // Verify procedure is dropped
        assertThrows(SQLException.class, () -> {
            statement.execute("CALL proc_schema.double_value(7)");
        });
    }

    @Test
    public void testDropNonExistentProcedureWithParameters() throws SQLException {
        logger.info("Testing DROP PROCEDURE for non-existent procedure with parameters");

        // Try to drop non-existent procedure - should fail
        assertThrows(SQLException.class, () -> {
            statement.execute("DROP PROCEDURE missing_proc(INTEGER, VARCHAR)");
        });

        // With IF EXISTS - should not fail
        statement.execute("DROP PROCEDURE IF EXISTS missing_proc(INTEGER, VARCHAR)");
    }
}
