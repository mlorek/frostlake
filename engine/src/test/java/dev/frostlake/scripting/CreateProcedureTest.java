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
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.Schema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CreateProcedureTest {

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
    public void testCreateSimpleProcedure() {
        // Setup
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA PUBLIC");

        // Create a simple procedure
        engine.execute("CREATE PROCEDURE update_data(id INTEGER, value VARCHAR) RETURNS VARCHAR AS 'UPDATE statement'");

        // Verify procedure was created
        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Procedure proc = schema.getProcedure("update_data");

        assertNotNull(proc, "Procedure should be created");
        assertEquals("UPDATE_DATA", proc.getName());
        assertEquals(2, proc.getParameters().size());
        assertEquals("ID", proc.getParameters().get(0).getName());
        assertEquals("VALUE", proc.getParameters().get(1).getName());
        assertEquals("UPDATE statement", proc.getBody());
    }

    @Test
    public void testCreateProcedureWithNoParameters() {
        // Setup
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA PUBLIC");

        // Create a procedure with no parameters
        engine.execute("CREATE PROCEDURE cleanup() RETURNS VARCHAR AS 'DELETE FROM temp_table'");

        // Verify procedure was created
        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Procedure proc = schema.getProcedure("cleanup");

        assertNotNull(proc, "Procedure should be created");
        assertEquals("CLEANUP", proc.getName());
        assertEquals(0, proc.getParameters().size());
        assertEquals("DELETE FROM temp_table", proc.getBody());
    }

    @Test
    public void testCreateProcedureWithQualifiedName() {
        // Setup
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE SCHEMA my_schema");

        // Create procedure with schema-qualified name
        engine.execute("""
            CREATE PROCEDURE my_schema.process_orders(order_id INTEGER) RETURNS INTEGER AS 'SELECT COUNT(*) FROM orders'
            """);

        // Verify procedure was created in correct schema
        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("MY_SCHEMA");
        Procedure proc = schema.getProcedure("process_orders");

        assertNotNull(proc, "Procedure should be created");
        assertEquals("PROCESS_ORDERS", proc.getName());
        assertEquals(1, proc.getParameters().size());
        assertEquals("SELECT COUNT(*) FROM orders", proc.getBody());
    }

    @Test
    public void testCreateProcedureWithMultipleParameters() {
        // Setup
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA PUBLIC");

        // Create procedure with multiple parameters
        engine.execute("""
            CREATE PROCEDURE insert_record(name VARCHAR, age INTEGER, active BOOLEAN) RETURNS INTEGER AS 'INSERT statement'
            """);

        // Verify procedure was created
        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Procedure proc = schema.getProcedure("insert_record");

        assertNotNull(proc, "Procedure should be created");
        assertEquals("INSERT_RECORD", proc.getName());
        assertEquals(3, proc.getParameters().size());
        assertEquals("NAME", proc.getParameters().get(0).getName());
        assertEquals("AGE", proc.getParameters().get(1).getName());
        assertEquals("ACTIVE", proc.getParameters().get(2).getName());
        assertEquals("INSERT statement", proc.getBody());
    }

    @Test
    public void testDropProcedureExists() {
        // Setup
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA PUBLIC");

        // Create and then drop procedure
        engine.execute("CREATE PROCEDURE test_proc(x INTEGER) RETURNS INTEGER AS 'RETURN x * 2'");

        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        assertNotNull(schema.getProcedure("test_proc"), "Procedure should exist");

        engine.execute("DROP PROCEDURE test_proc(INTEGER)");

        // Verify procedure was dropped
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            schema.getProcedure("test_proc");
        });
        assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    public void testShowProcedures() {
        // Setup
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA PUBLIC");

        // Create multiple procedures
        engine.execute("CREATE PROCEDURE proc1(x INTEGER) RETURNS INTEGER AS 'RETURN x'");
        engine.execute("CREATE PROCEDURE proc2(x INTEGER, y INTEGER) RETURNS INTEGER AS 'RETURN x + y'");

        // Show procedures
        var result = engine.showProcedures();

        assertNotNull(result);
        assertEquals(2, result.getRowCount());
    }

    @Test
    public void testCreateProcedureWithIntegerReturn() {
        // Setup
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA PUBLIC");

        // Create a test table with data
        engine.execute("CREATE TABLE test_table (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO test_table VALUES (1, 'Alice')");
        engine.execute("INSERT INTO test_table VALUES (2, 'Bob')");
        engine.execute("INSERT INTO test_table VALUES (3, 'Charlie')");

        // Create procedure with IF statement that returns different values based on input
        engine.execute("""
            CREATE PROCEDURE check_value(max_count INTEGER) RETURNS VARCHAR AS 'DECLARE result VARCHAR; IF max_count > 2 THEN SET result = ''many''; ELSE SET result = ''few''; END IF; RETURN result;'
            """);

        // Verify procedure was created
        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Procedure proc = schema.getProcedure("check_value");

        assertNotNull(proc, "Procedure should be created");
        assertEquals("CHECK_VALUE", proc.getName());
        assertEquals(1, proc.getParameters().size());
        assertEquals("MAX_COUNT", proc.getParameters().get(0).getName());
        assertTrue(proc.getBody().contains("IF"), "Body should contain IF statement");
        assertTrue(proc.getBody().contains("ELSE"), "Body should contain ELSE clause");
        assertTrue(proc.getBody().contains("END IF"), "Body should contain END IF");
        assertTrue(proc.getBody().contains("RETURN"), "Body should contain RETURN");

        // CALL now runs the SQL body (rather than logging it). This particular body uses non-standard
        // syntax (SET =, no BEGIN…END); well-formed bodies with real return values are asserted in
        // SqlProcedureCallTest. Here just verify the call runs and the procedure stays registered.
        engine.execute("CALL check_value(3)");
        assertEquals("CHECK_VALUE", schema.getProcedure("check_value").getName());
    }

    @Test
    public void testCreateProcedureWithLoopAndObjectReturn() {
        // Setup
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA PUBLIC");

        // Create procedure with loop and OBJECT return type
        engine.execute("""
            CREATE PROCEDURE process_with_loop(max_iterations INTEGER) RETURNS OBJECT AS 'DECLARE counter INTEGER := 0; DECLARE result OBJECT; WHILE counter < 10 DO SET counter = counter + 1; END WHILE; SET result = OBJECT_CONSTRUCT(''count'', counter, ''status'', ''completed''); RETURN result;'
            """);

        // Verify procedure was created
        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Procedure proc = schema.getProcedure("process_with_loop");

        assertNotNull(proc, "Procedure should be created");
        assertEquals("PROCESS_WITH_LOOP", proc.getName());
        assertEquals(1, proc.getParameters().size());
        assertEquals("MAX_ITERATIONS", proc.getParameters().get(0).getName());

        // Verify return type is OBJECT
        assertNotNull(proc.getReturnType(), "Return type should not be null");
        assertEquals("OBJECT", proc.getReturnType().toString());

        // Verify body contains loop constructs
        String body = proc.getBody();
        assertTrue(body.contains("WHILE"), "Body should contain WHILE loop");
        assertTrue(body.contains("END WHILE"), "Body should contain END WHILE");
        assertTrue(body.contains("OBJECT_CONSTRUCT"), "Body should contain OBJECT_CONSTRUCT");
        assertTrue(body.contains("RETURN"), "Body should contain RETURN statement");
    }
}
