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
        engine.execute("CREATE PROCEDURE update_data(id INTEGER, value VARCHAR) RETURNS VARCHAR "
            + "AS 'BEGIN UPDATE data_rows SET label = :value WHERE id = :id; RETURN ''updated''; END'");

        // Verify procedure was created
        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Procedure proc = schema.getProcedure("update_data");

        assertNotNull(proc, "Procedure should be created");
        assertEquals("UPDATE_DATA", proc.getName());
        assertEquals(2, proc.getParameters().size());
        assertEquals("ID", proc.getParameters().get(0).getName());
        assertEquals("VALUE", proc.getParameters().get(1).getName());
        assertEquals("BEGIN UPDATE data_rows SET label = :value WHERE id = :id; RETURN 'updated'; END",
            proc.getBody());
    }

    @Test
    public void testCreateProcedureWithNoParameters() {
        // Setup
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA PUBLIC");

        // Create a procedure with no parameters
        engine.execute("CREATE PROCEDURE cleanup() RETURNS VARCHAR "
            + "AS 'BEGIN DELETE FROM temp_table; RETURN ''cleaned''; END'");

        // Verify procedure was created
        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Procedure proc = schema.getProcedure("cleanup");

        assertNotNull(proc, "Procedure should be created");
        assertEquals("CLEANUP", proc.getName());
        assertEquals(0, proc.getParameters().size());
        assertEquals("BEGIN DELETE FROM temp_table; RETURN 'cleaned'; END", proc.getBody());
    }

    @Test
    public void testCreateProcedureWithQualifiedName() {
        // Setup
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE SCHEMA my_schema");

        // Create procedure with schema-qualified name
        engine.execute("""
            CREATE PROCEDURE my_schema.process_orders(order_id INTEGER) RETURNS INTEGER
            AS 'BEGIN RETURN (SELECT COUNT(*) FROM orders); END'
            """);

        // Verify procedure was created in correct schema
        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("MY_SCHEMA");
        Procedure proc = schema.getProcedure("process_orders");

        assertNotNull(proc, "Procedure should be created");
        assertEquals("PROCESS_ORDERS", proc.getName());
        assertEquals(1, proc.getParameters().size());
        assertEquals("BEGIN RETURN (SELECT COUNT(*) FROM orders); END", proc.getBody());
    }

    @Test
    public void testCreateProcedureWithMultipleParameters() {
        // Setup
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA PUBLIC");

        // Create procedure with multiple parameters
        engine.execute("""
            CREATE PROCEDURE insert_record(name VARCHAR, age INTEGER, active BOOLEAN) RETURNS INTEGER
            AS 'BEGIN INSERT INTO people (name, age, active) VALUES (:name, :age, :active); RETURN 1; END'
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
        assertEquals("BEGIN INSERT INTO people (name, age, active) VALUES (:name, :age, :active); "
            + "RETURN 1; END", proc.getBody());
    }

    @Test
    public void testDropProcedureExists() {
        // Setup
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA PUBLIC");

        // Create and then drop procedure
        engine.execute("CREATE PROCEDURE test_proc(x INTEGER) RETURNS INTEGER AS 'BEGIN RETURN x * 2; END'");

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
        engine.execute("CREATE PROCEDURE proc1(x INTEGER) RETURNS INTEGER AS 'BEGIN RETURN x; END'");
        engine.execute(
            "CREATE PROCEDURE proc2(x INTEGER, y INTEGER) RETURNS INTEGER AS 'BEGIN RETURN x + y; END'");

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
            CREATE PROCEDURE check_value(max_count INTEGER) RETURNS VARCHAR
            AS 'DECLARE outcome VARCHAR; BEGIN IF (max_count > 2) THEN outcome := ''many''; ELSE outcome := ''few''; END IF; RETURN outcome; END'
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

        // CALL runs the SQL body; the body is a well-formed scripting block, so it really returns 'many'.
        final var callResult = engine.executeQuery("CALL check_value(3)");
        assertEquals("many", callResult.getRows().get(0).getValue(0));
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
            CREATE PROCEDURE process_with_loop(max_iterations INTEGER) RETURNS OBJECT
            AS 'DECLARE counter INTEGER := 0; outcome OBJECT; BEGIN WHILE (counter < 10) DO counter := counter + 1; END WHILE; outcome := OBJECT_CONSTRUCT(''count'', counter, ''status'', ''completed''); RETURN outcome; END'
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
