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

public class CreateProcedureJdbcTest extends BaseJdbcTest {

    /**
     * Whether {@code SHOW PROCEDURES} lists a procedure with this name. The rows are scanned rather than
     * read positionally: both backends list the whole built-in library alongside the user-defined
     * procedures, so the procedure just created is not necessarily the first row.
     */
    private boolean procedureListed(final String name) throws SQLException {
        try (final ResultSet rs = statement.executeQuery("SHOW PROCEDURES")) {
            while (rs.next()) {
                if (name.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** How many of the listed procedures are user-defined (is_builtin = 'N'). */
    private int userProcedureCount() throws SQLException {
        int count = 0;
        try (final ResultSet rs = statement.executeQuery("SHOW PROCEDURES")) {
            while (rs.next()) {
                if ("N".equals(rs.getString("is_builtin"))) {
                    count++;
                }
            }
        }
        return count;
    }

    @Test
    public void testCreateSimpleProcedure() throws SQLException {
        // Create a simple procedure
        statement.execute("""
            CREATE PROCEDURE update_data(id INTEGER, value VARCHAR) RETURNS VARCHAR
            AS 'BEGIN UPDATE data_rows SET label = :value WHERE id = :id; RETURN ''updated''; END'
            """);

        // Verify procedure was created
        assertTrue(procedureListed("UPDATE_DATA"), "UPDATE_DATA should be listed by SHOW PROCEDURES");
    }

    @Test
    public void testCreateProcedureWithNoParameters() throws SQLException {
        // Create a procedure with no parameters
        statement.execute("CREATE PROCEDURE cleanup() RETURNS VARCHAR "
            + "AS 'BEGIN DELETE FROM temp_table; RETURN ''cleaned''; END'");

        // Verify procedure was created
        assertTrue(procedureListed("CLEANUP"), "CLEANUP should be listed by SHOW PROCEDURES");
    }

    @Test
    public void testCreateProcedureWithQualifiedName() throws SQLException {
        // Create schema
        statement.execute("CREATE SCHEMA my_schema");

        // Create procedure with schema-qualified name
        statement.execute("""
            CREATE PROCEDURE my_schema.process_orders(order_id INTEGER) RETURNS INTEGER
            AS 'BEGIN RETURN (SELECT COUNT(*) FROM orders); END'
            """);

        // Switch to the schema and verify
        statement.execute("USE SCHEMA my_schema");
        assertTrue(procedureListed("PROCESS_ORDERS"), "PROCESS_ORDERS should be listed by SHOW PROCEDURES");
    }

    @Test
    public void testCreateProcedureWithMultipleParameters() throws SQLException {
        // Create procedure with multiple parameters
        statement.execute("""
            CREATE PROCEDURE insert_record(name VARCHAR, age INTEGER, active BOOLEAN) RETURNS INTEGER
            AS 'BEGIN INSERT INTO people (name, age, active) VALUES (:name, :age, :active); RETURN 1; END'
            """);

        // Verify procedure was created
        assertTrue(procedureListed("INSERT_RECORD"), "INSERT_RECORD should be listed by SHOW PROCEDURES");
    }

    @Test
    public void testDropProcedureExists() throws SQLException {
        // Create procedure
        statement.execute("CREATE PROCEDURE test_proc(x INTEGER) RETURNS INTEGER AS 'BEGIN RETURN x * 2; END'");

        // Verify it exists
        assertTrue(procedureListed("TEST_PROC"), "TEST_PROC should be listed by SHOW PROCEDURES");

        // Drop procedure
        statement.execute("DROP PROCEDURE test_proc(INTEGER)");

        // Verify it's gone by showing procedures
        assertEquals(0, userProcedureCount(), "Procedure should be dropped (built-ins remain)");

        // Drop with IF EXISTS should not throw
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.execute("DROP PROCEDURE IF EXISTS test_proc(INTEGER)");
            }
        });
    }

    @Test
    public void testShowProcedures() throws SQLException {
        // Create multiple procedures
        statement.execute("CREATE PROCEDURE proc1(x INTEGER) RETURNS INTEGER AS 'BEGIN RETURN x; END'");
        statement.execute(
            "CREATE PROCEDURE proc2(x INTEGER, y INTEGER) RETURNS INTEGER AS 'BEGIN RETURN x + y; END'");

        assertEquals(2, userProcedureCount(), "Should have 2 user-defined procedures");
    }

    @Test
    public void testCreateProcedureWithIfStatement() throws SQLException {
        // Create a test table with data
        statement.execute("CREATE TABLE test_table (id INTEGER, name VARCHAR)");
        statement.execute("INSERT INTO test_table VALUES (1, 'Alice')");
        statement.execute("INSERT INTO test_table VALUES (2, 'Bob')");
        statement.execute("INSERT INTO test_table VALUES (3, 'Charlie')");

        // Create procedure with IF statement
        statement.execute("""
            CREATE PROCEDURE check_value(max_count INTEGER) RETURNS VARCHAR
            AS 'DECLARE outcome VARCHAR; BEGIN IF (max_count > 2) THEN outcome := ''many''; ELSE outcome := ''few''; END IF; RETURN outcome; END'
            """);

        // Verify procedure was created
        assertTrue(procedureListed("CHECK_VALUE"), "CHECK_VALUE should be listed by SHOW PROCEDURES");

        // The body is a well-formed scripting block, so the CALL really runs the IF and returns 'many'.
        try (final ResultSet call = statement.executeQuery("CALL check_value(3)")) {
            assertTrue(call.next(), "CALL should produce a row");
            assertEquals("many", call.getString(1));
        }
    }

    @Test
    public void testCreateProcedureWithLoopAndObjectReturn() throws SQLException {
        // Create procedure with loop and OBJECT return type
        statement.execute("""
            CREATE PROCEDURE process_with_loop(max_iterations INTEGER) RETURNS OBJECT
            AS 'DECLARE counter INTEGER := 0; outcome OBJECT; BEGIN WHILE (counter < 10) DO counter := counter + 1; END WHILE; outcome := OBJECT_CONSTRUCT(''count'', counter, ''status'', ''completed''); RETURN outcome; END'
            """);

        // Verify procedure was created
        assertTrue(procedureListed("PROCESS_WITH_LOOP"), "PROCESS_WITH_LOOP should be listed by SHOW PROCEDURES");
    }
}
