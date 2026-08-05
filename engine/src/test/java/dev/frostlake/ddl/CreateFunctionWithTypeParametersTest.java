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

public class CreateFunctionWithTypeParametersTest extends BaseJdbcTest {
    private static final Logger logger = LoggerFactory.getLogger(CreateFunctionWithTypeParametersTest.class);

    @Test
    public void testCreateFunctionWithVarcharParameter() throws SQLException {
        logger.info("Testing CREATE FUNCTION with VARCHAR(size) parameter");

        statement.execute("CREATE FUNCTION greet(name VARCHAR(100)) RETURNS VARCHAR LANGUAGE SQL AS 'name'");

        // Function should be created successfully
        // Note: We can't easily test function calls with VARCHAR in this simplified engine
        assertFunctionExists("greet");

        statement.execute("DROP FUNCTION greet(VARCHAR)");
    }

    @Test
    public void testCreateFunctionWithDecimalParameter() throws SQLException {
        logger.info("Testing CREATE FUNCTION with DECIMAL(precision, scale) parameter");

        statement.execute("CREATE FUNCTION calculate_tax(amount DECIMAL(10, 2)) RETURNS DECIMAL LANGUAGE SQL AS 'amount'");

        // Verify function was created
        assertFunctionExists("calculate_tax");

        statement.execute("DROP FUNCTION calculate_tax(DECIMAL)");
    }

    @Test
    public void testCreateFunctionWithTimestampParameter() throws SQLException {
        logger.info("Testing CREATE FUNCTION with TIMESTAMP(precision) parameter");

        // The declared return type must match the body's actual one — live-verified:
        // RETURNS VARCHAR over a bare TIMESTAMP body fails "Declared return type 'VARCHAR(134217728)' is
        // incompatible with actual return type 'TIMESTAMP_NTZ(9)'".
        statement.execute(
            "CREATE FUNCTION format_time(ts TIMESTAMP(9)) RETURNS VARCHAR LANGUAGE SQL AS 'ts::VARCHAR'");

        // Verify function was created
        assertFunctionExists("format_time");

        statement.execute("DROP FUNCTION format_time(TIMESTAMP)");
    }

    @Test
    public void testCreateFunctionWithMultipleTypedParameters() throws SQLException {
        logger.info("Testing CREATE FUNCTION with multiple parameters with type parameters");

        statement.execute("CREATE FUNCTION format_record(id INTEGER, name VARCHAR(50), amount DECIMAL(10, 2)) RETURNS VARCHAR LANGUAGE SQL AS 'name'");

        // Verify function was created
        assertFunctionExists("format_record");

        statement.execute("DROP FUNCTION format_record(INTEGER, VARCHAR, DECIMAL)");
    }

    @Test
    public void testCreateFunctionMixedParameters() throws SQLException {
        logger.info("Testing CREATE FUNCTION with mixed typed and untyped parameters");

        statement.execute("CREATE FUNCTION process_data(id INTEGER, description VARCHAR(255), count INTEGER, price DECIMAL(8, 2)) RETURNS VARCHAR LANGUAGE SQL AS 'description'");

        // Verify function was created
        assertFunctionExists("process_data");

        statement.execute("DROP FUNCTION process_data(INTEGER, VARCHAR, INTEGER, DECIMAL)");
    }

    @Test
    public void testCreateFunctionWithCharParameter() throws SQLException {
        logger.info("Testing CREATE FUNCTION with CHAR(size) parameter");

        statement.execute("CREATE FUNCTION validate_code(code CHAR(10)) RETURNS INTEGER LANGUAGE SQL AS '1'");

        // Verify function was created
        assertFunctionExists("validate_code");

        // A routine's signature is stored under its CANONICAL type FAMILY, so the DROP names the
        // family, not the declared alias — live-verified on a real account:
        // DROP FUNCTION f(VARCHAR) drops a CHAR(10) parameter while DROP FUNCTION f(CHAR) does not.
        statement.execute("DROP FUNCTION validate_code(VARCHAR)");
    }

    @Test
    public void testCreateFunctionWithNumberParameter() throws SQLException {
        logger.info("Testing CREATE FUNCTION with NUMBER(precision) parameter");

        statement.execute("CREATE FUNCTION compute_total(value NUMBER(12)) RETURNS NUMBER LANGUAGE SQL AS 'value'");

        // Verify function was created
        assertFunctionExists("compute_total");

        statement.execute("DROP FUNCTION compute_total(NUMBER)");
    }

    @Test
    public void testCreateFunctionWithNumberPrecisionScale() throws SQLException {
        logger.info("Testing CREATE FUNCTION with NUMBER(precision, scale) parameter");

        statement.execute("CREATE FUNCTION calculate_interest(principal NUMBER(15, 2), rate NUMBER(5, 4)) RETURNS NUMBER LANGUAGE SQL AS 'principal'");

        // Verify function was created
        assertFunctionExists("calculate_interest");

        statement.execute("DROP FUNCTION calculate_interest(NUMBER, NUMBER)");
    }

    @Test
    public void testCreateOrReplaceFunctionWithTypedParameters() throws SQLException {
        logger.info("Testing CREATE OR REPLACE FUNCTION with typed parameters");

        statement.execute("CREATE FUNCTION update_record(id INTEGER, data VARCHAR(200)) RETURNS VARCHAR LANGUAGE SQL AS 'data'");

        // Replace with same signature
        statement.execute("CREATE OR REPLACE FUNCTION update_record(id INTEGER, data VARCHAR(200)) RETURNS VARCHAR LANGUAGE SQL AS 'data'");

        // Verify function exists
        assertFunctionExists("update_record");

        statement.execute("DROP FUNCTION update_record(INTEGER, VARCHAR)");
    }

    @Test
    public void testCreateFunctionWithAllTypedParameters() throws SQLException {
        logger.info("Testing CREATE FUNCTION with all parameters having type parameters");

        statement.execute("CREATE FUNCTION full_typed(str VARCHAR(100), dec DECIMAL(10, 2), num NUMBER(15, 3), ch CHAR(5)) RETURNS VARCHAR LANGUAGE SQL AS 'str'");

        // Verify function was created
        assertFunctionExists("full_typed");

        // A routine's signature is stored under its CANONICAL type FAMILY, so the DROP names the
        // family, not the declared alias — live-verified on a real account:
        // DROP FUNCTION f(VARCHAR) drops a CHAR(10) parameter while DROP FUNCTION f(CHAR) does not.
        statement.execute("DROP FUNCTION full_typed(VARCHAR, NUMBER, NUMBER, VARCHAR)");
    }

    @Test
    public void testDropFunctionWithTypedParametersMatch() throws SQLException {
        logger.info("Testing DROP FUNCTION matches typed parameters correctly");

        statement.execute("CREATE FUNCTION typed_func(amount DECIMAL(10, 2)) RETURNS DECIMAL LANGUAGE SQL AS 'amount'");

        // Verify function was created
        assertFunctionExists("typed_func");

        // Drop with matching type (DECIMAL without parameters)
        statement.execute("DROP FUNCTION typed_func(DECIMAL)");

        // Verify it was dropped
        assertFunctionNotExists("typed_func");

        statement.execute("CREATE FUNCTION typed_func(amount DECIMAL(10, 2)) RETURNS DECIMAL LANGUAGE SQL AS 'amount'");
        assertFunctionExists("typed_func");
        statement.execute("DROP FUNCTION typed_func(DECIMAL)");
    }

    @Test
    public void testCreateFunctionSchemaQualifiedWithTypedParameters() throws SQLException {
        logger.info("Testing CREATE FUNCTION schema-qualified with typed parameters");

        statement.execute("CREATE SCHEMA func_schema");
        statement.execute("CREATE FUNCTION func_schema.process(name VARCHAR(100), value DECIMAL(8, 2)) RETURNS VARCHAR LANGUAGE SQL AS 'name'");

        // Verify function was created in schema
        assertFunctionExistsInSchema("process", "func_schema");

        statement.execute("DROP FUNCTION func_schema.process(VARCHAR, DECIMAL)");
    }

    private boolean functionExists(final String showSql, final String name) throws SQLException {
        try (ResultSet rs = statement.executeQuery(showSql)) {
            while (rs.next()) {
                if (name.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void assertFunctionExists(final String name) throws SQLException {
        assertTrue(functionExists("SHOW FUNCTIONS", name), "function not found: " + name);
    }

    private void assertFunctionExistsInSchema(final String name, final String schema) throws SQLException {
        assertTrue(functionExists("SHOW FUNCTIONS IN SCHEMA " + schema, name),
            "function not found: " + schema + "." + name);
    }

    private void assertFunctionNotExists(final String name) throws SQLException {
        assertTrue(!functionExists("SHOW FUNCTIONS", name), "function unexpectedly found: " + name);
    }
}
