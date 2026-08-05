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

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CreateProcedureWithTypeParametersTest extends BaseJdbcTest {
    private static final Logger logger = LoggerFactory.getLogger(CreateProcedureWithTypeParametersTest.class);

    @Test
    public void testCreateProcedureWithVarcharParameter() throws SQLException {
        logger.info("Testing CREATE PROCEDURE with VARCHAR(size) parameter");

        statement.execute("CREATE PROCEDURE greet_proc(name VARCHAR(100)) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN name; END'");

        // Verify procedure was created
        assertProcedureExists("greet_proc");

        statement.execute("DROP PROCEDURE greet_proc(VARCHAR)");
    }

    @Test
    public void testCreateProcedureWithDecimalParameter() throws SQLException {
        logger.info("Testing CREATE PROCEDURE with DECIMAL(precision, scale) parameter");

        statement.execute("CREATE PROCEDURE calc_proc(amount DECIMAL(10, 2)) RETURNS DECIMAL LANGUAGE SQL AS 'BEGIN RETURN amount; END'");

        // Verify procedure was created
        assertProcedureExists("calc_proc");

        statement.execute("DROP PROCEDURE calc_proc(DECIMAL)");
    }

    @Test
    public void testCreateProcedureWithTimestampParameter() throws SQLException {
        logger.info("Testing CREATE PROCEDURE with TIMESTAMP(precision) parameter");

        statement.execute("CREATE PROCEDURE time_proc(ts TIMESTAMP(9)) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN ts; END'");

        // Verify procedure was created
        assertProcedureExists("time_proc");

        statement.execute("DROP PROCEDURE time_proc(TIMESTAMP)");
    }

    @Test
    public void testCreateProcedureWithMultipleTypedParameters() throws SQLException {
        logger.info("Testing CREATE PROCEDURE with multiple parameters with type parameters");

        statement.execute("CREATE PROCEDURE record_proc(id INTEGER, name VARCHAR(50), amount DECIMAL(10, 2)) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN name; END'");

        // Verify procedure was created
        assertProcedureExists("record_proc");

        statement.execute("DROP PROCEDURE record_proc(INTEGER, VARCHAR, DECIMAL)");
    }

    @Test
    public void testCreateProcedureMixedParameters() throws SQLException {
        logger.info("Testing CREATE PROCEDURE with mixed typed and untyped parameters");

        statement.execute("CREATE PROCEDURE data_proc(id INTEGER, description VARCHAR(255), count INTEGER, price DECIMAL(8, 2)) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN description; END'");

        // Verify procedure was created
        assertProcedureExists("data_proc");

        statement.execute("DROP PROCEDURE data_proc(INTEGER, VARCHAR, INTEGER, DECIMAL)");
    }

    @Test
    public void testCreateProcedureWithCharParameter() throws SQLException {
        logger.info("Testing CREATE PROCEDURE with CHAR(size) parameter");

        statement.execute("CREATE PROCEDURE code_proc(code CHAR(10)) RETURNS INTEGER LANGUAGE SQL AS 'BEGIN RETURN 1; END'");

        // Verify procedure was created
        assertProcedureExists("code_proc");

        // A routine's signature is stored under its CANONICAL type FAMILY, so the DROP names the
        // family, not the declared alias — live-verified on a real account:
        // DROP FUNCTION f(VARCHAR) drops a CHAR(10) parameter while DROP FUNCTION f(CHAR) does not.
        statement.execute("DROP PROCEDURE code_proc(VARCHAR)");
    }

    @Test
    public void testCreateProcedureWithNumberParameter() throws SQLException {
        logger.info("Testing CREATE PROCEDURE with NUMBER(precision) parameter");

        statement.execute("CREATE PROCEDURE total_proc(value NUMBER(12)) RETURNS NUMBER LANGUAGE SQL AS 'BEGIN RETURN value; END'");

        // Verify procedure was created
        assertProcedureExists("total_proc");

        statement.execute("DROP PROCEDURE total_proc(NUMBER)");
    }

    @Test
    public void testCreateProcedureWithNumberPrecisionScale() throws SQLException {
        logger.info("Testing CREATE PROCEDURE with NUMBER(precision, scale) parameter");

        statement.execute("CREATE PROCEDURE interest_proc(principal NUMBER(15, 2), rate NUMBER(5, 4)) RETURNS NUMBER LANGUAGE SQL AS 'BEGIN RETURN principal; END'");

        // Verify procedure was created
        assertProcedureExists("interest_proc");

        statement.execute("DROP PROCEDURE interest_proc(NUMBER, NUMBER)");
    }

    @Test
    public void testCreateOrReplaceProcedureWithTypedParameters() throws SQLException {
        logger.info("Testing CREATE OR REPLACE PROCEDURE with typed parameters");

        statement.execute("CREATE PROCEDURE update_proc(id INTEGER, data VARCHAR(200)) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN data; END'");

        // Replace with same signature
        statement.execute("CREATE OR REPLACE PROCEDURE update_proc(id INTEGER, data VARCHAR(200)) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN data; END'");

        // Verify procedure exists
        assertProcedureExists("update_proc");

        statement.execute("DROP PROCEDURE update_proc(INTEGER, VARCHAR)");
    }

    @Test
    public void testCreateProcedureWithAllTypedParameters() throws SQLException {
        logger.info("Testing CREATE PROCEDURE with all parameters having type parameters");

        statement.execute("CREATE PROCEDURE full_proc(str VARCHAR(100), dec DECIMAL(10, 2), num NUMBER(15, 3), ch CHAR(5)) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN str; END'");

        // Verify procedure was created
        assertProcedureExists("full_proc");

        // A routine's signature is stored under its CANONICAL type FAMILY, so the DROP names the
        // family, not the declared alias — live-verified on a real account:
        // DROP FUNCTION f(VARCHAR) drops a CHAR(10) parameter while DROP FUNCTION f(CHAR) does not.
        statement.execute("DROP PROCEDURE full_proc(VARCHAR, NUMBER, NUMBER, VARCHAR)");
    }

    @Test
    public void testDropProcedureWithTypedParametersMatch() throws SQLException {
        logger.info("Testing DROP PROCEDURE matches typed parameters correctly");

        statement.execute("CREATE PROCEDURE typed_proc(amount DECIMAL(10, 2)) RETURNS DECIMAL LANGUAGE SQL AS 'BEGIN RETURN amount; END'");

        // Verify procedure was created
        assertProcedureExists("typed_proc");

        // Drop with matching type (DECIMAL without parameters)
        statement.execute("DROP PROCEDURE typed_proc(DECIMAL)");

        // Verify it was dropped
        assertProcedureNotExists("typed_proc");

        statement.execute("CREATE PROCEDURE typed_proc(amount DECIMAL(10, 2)) RETURNS DECIMAL LANGUAGE SQL AS 'BEGIN RETURN amount; END'");
        assertProcedureExists("typed_proc");
        statement.execute("DROP PROCEDURE typed_proc(DECIMAL)");
    }

    @Test
    public void testCreateProcedureSchemaQualifiedWithTypedParameters() throws SQLException {
        logger.info("Testing CREATE PROCEDURE schema-qualified with typed parameters");

        statement.execute("CREATE SCHEMA proc_schema");
        statement.execute("CREATE PROCEDURE proc_schema.process_proc(name VARCHAR(100), value DECIMAL(8, 2)) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN name; END'");

        // Verify procedure was created in schema
        assertProcedureExistsInSchema("process_proc", "proc_schema");

        statement.execute("DROP PROCEDURE proc_schema.process_proc(VARCHAR, DECIMAL)");
    }

    private boolean procedureExists(final String showSql, final String name) throws SQLException {
        try (ResultSet rs = statement.executeQuery(showSql)) {
            while (rs.next()) {
                if (name.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void assertProcedureExists(final String name) throws SQLException {
        assertTrue(procedureExists("SHOW PROCEDURES", name), "procedure not found: " + name);
    }

    private void assertProcedureExistsInSchema(final String name, final String schema) throws SQLException {
        assertTrue(procedureExists("SHOW PROCEDURES IN SCHEMA " + schema, name),
            "procedure not found: " + schema + "." + name);
    }

    private void assertProcedureNotExists(final String name) throws SQLException {
        assertTrue(!procedureExists("SHOW PROCEDURES", name), "procedure unexpectedly found: " + name);
    }
}
