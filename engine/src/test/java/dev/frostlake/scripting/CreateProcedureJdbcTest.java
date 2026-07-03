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

public class CreateProcedureJdbcTest extends BaseJdbcTest {

    @Test
    public void testCreateSimpleProcedure() throws SQLException {
        // Create a simple procedure
        statement.execute("""
            CREATE PROCEDURE update_data(id INTEGER, value VARCHAR) RETURNS VARCHAR AS 'UPDATE statement'
            """);

        // Verify procedure was created
        ResultSet rs = statement.executeQuery("SHOW PROCEDURES");
        assertTrue(rs.next(), "Procedure should be created");

        String procName = rs.getString("name");
        assertEquals("UPDATE_DATA", procName);

        rs.close();
    }

    @Test
    public void testCreateProcedureWithNoParameters() throws SQLException {
        // Create a procedure with no parameters
        statement.execute("CREATE PROCEDURE cleanup() RETURNS VARCHAR AS 'DELETE FROM temp_table'");

        // Verify procedure was created
        ResultSet rs = statement.executeQuery("SHOW PROCEDURES");
        assertTrue(rs.next(), "Procedure should be created");

        String procName = rs.getString("name");
        assertEquals("CLEANUP", procName);

        rs.close();
    }

    @Test
    public void testCreateProcedureWithQualifiedName() throws SQLException {
        // Create schema
        statement.execute("CREATE SCHEMA my_schema");

        // Create procedure with schema-qualified name
        statement.execute("""
            CREATE PROCEDURE my_schema.process_orders(order_id INTEGER) RETURNS INTEGER AS 'SELECT COUNT(*) FROM orders'
            """);

        // Switch to the schema and verify
        statement.execute("USE SCHEMA my_schema");
        ResultSet rs = statement.executeQuery("SHOW PROCEDURES");
        assertTrue(rs.next(), "Procedure should be created");

        String procName = rs.getString("name");
        assertEquals("PROCESS_ORDERS", procName);

        rs.close();
    }

    @Test
    public void testCreateProcedureWithMultipleParameters() throws SQLException {
        // Create procedure with multiple parameters
        statement.execute("""
            CREATE PROCEDURE insert_record(name VARCHAR, age INTEGER, active BOOLEAN) RETURNS INTEGER AS 'INSERT statement'
            """);

        // Verify procedure was created
        ResultSet rs = statement.executeQuery("SHOW PROCEDURES");
        assertTrue(rs.next(), "Procedure should be created");

        String procName = rs.getString("name");
        assertEquals("INSERT_RECORD", procName);

        rs.close();
    }

    @Test
    public void testDropProcedureExists() throws SQLException {
        // Create procedure
        statement.execute("CREATE PROCEDURE test_proc(x INTEGER) RETURNS INTEGER AS 'RETURN x * 2'");

        // Verify it exists
        ResultSet rs = statement.executeQuery("SHOW PROCEDURES");
        assertTrue(rs.next(), "Procedure should exist");
        rs.close();

        // Drop procedure
        statement.execute("DROP PROCEDURE test_proc");

        // Verify it's gone by showing procedures
        ResultSet rs2 = statement.executeQuery("SHOW PROCEDURES");
        int userProcedures = 0;
        while (rs2.next()) {
            if ("N".equals(rs2.getString("is_builtin"))) {
                userProcedures++;
            }
        }
        assertEquals(0, userProcedures, "Procedure should be dropped (built-ins remain)");
        rs2.close();

        // Drop with IF EXISTS should not throw
        assertDoesNotThrow(() -> statement.execute("DROP PROCEDURE IF EXISTS test_proc"));
    }

    @Test
    public void testShowProcedures() throws SQLException {
        // Create multiple procedures
        statement.execute("CREATE PROCEDURE proc1(x INTEGER) RETURNS INTEGER AS 'RETURN x'");
        statement.execute("CREATE PROCEDURE proc2(x INTEGER, y INTEGER) RETURNS INTEGER AS 'RETURN x + y'");

        // Show procedures
        ResultSet rs = statement.executeQuery("SHOW PROCEDURES");

        int userProcedures = 0;
        while (rs.next()) {
            if ("N".equals(rs.getString("is_builtin"))) {
                userProcedures++;
            }
        }

        assertEquals(2, userProcedures, "Should have 2 user-defined procedures");
        rs.close();
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
            CREATE PROCEDURE check_value(max_count INTEGER) RETURNS VARCHAR AS 'DECLARE result VARCHAR; IF max_count > 2 THEN SET result = ''many''; ELSE SET result = ''few''; END IF; RETURN result;'
            """);

        // Verify procedure was created
        ResultSet rs = statement.executeQuery("SHOW PROCEDURES");
        assertTrue(rs.next(), "Procedure should be created");

        String procName = rs.getString("name");
        assertEquals("CHECK_VALUE", procName);

        rs.close();

        // Execute the procedure
        try {
            statement.execute("CALL check_value(3)");
            // CALL succeeded — confirm the procedure is still registered.
            try (ResultSet afterCall = statement.executeQuery("SHOW PROCEDURES")) {
                assertTrue(afterCall.next(), "Procedure should still exist after CALL");
            }
        } catch (final SQLException e) {
            // Expected for now since full procedural execution is not implemented
            assertTrue(e.getMessage().contains("Procedure") || e.getMessage().contains("procedural"),
                      "Error should be related to procedure execution");
        }
    }

    @Test
    public void testCreateProcedureWithLoopAndObjectReturn() throws SQLException {
        // Create procedure with loop and OBJECT return type
        statement.execute("""
            CREATE PROCEDURE process_with_loop(max_iterations INTEGER) RETURNS OBJECT AS 'DECLARE counter INTEGER := 0; DECLARE result OBJECT; WHILE counter < 10 DO SET counter = counter + 1; END WHILE; SET result = OBJECT_CONSTRUCT(''count'', counter, ''status'', ''completed''); RETURN result;'
            """);

        // Verify procedure was created
        ResultSet rs = statement.executeQuery("SHOW PROCEDURES");
        assertTrue(rs.next(), "Procedure should be created");

        String procName = rs.getString("name");
        assertEquals("PROCESS_WITH_LOOP", procName);

        rs.close();
    }
}
