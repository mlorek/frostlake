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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CREATE PROCEDURE for SQL scripting procedures, asserted through SQL — each procedure is CALLed
 * and its return value and side effects checked, and existence is read back through
 * {@code SHOW PROCEDURES LIKE} — never through engine internals, so the same assertions hold
 * against a live account.
 */
public class CreateProcedureTest extends BaseDatabaseTest {

    private Object call(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount(), "expected one row from: " + sql);
        return rs.getRows().get(0).getValue(0);
    }

    private int shown(final String name) {
        return engine.executeQuery("SHOW PROCEDURES LIKE '" + name + "'").getRowCount();
    }

    @Test
    public void testCreateSimpleProcedure() {
        engine.execute("CREATE TABLE data_rows (id INTEGER, label VARCHAR)");
        engine.execute("INSERT INTO data_rows VALUES (1, 'before'), (2, 'other')");
        engine.execute("CREATE PROCEDURE update_data(id INTEGER, value VARCHAR) RETURNS VARCHAR "
            + "AS 'BEGIN UPDATE data_rows SET label = :value WHERE id = :id; RETURN ''updated''; END'");
        assertEquals(1, shown("update_data"));
        assertEquals("updated", call("CALL update_data(1, 'after')"));
        assertEquals("after", call("SELECT label FROM data_rows WHERE id = 1"));
        assertEquals("other", call("SELECT label FROM data_rows WHERE id = 2"));
    }

    @Test
    public void testCreateProcedureWithNoParameters() {
        engine.execute("CREATE TABLE temp_table (id INTEGER)");
        engine.execute("INSERT INTO temp_table VALUES (1), (2)");
        engine.execute("CREATE PROCEDURE cleanup() RETURNS VARCHAR "
            + "AS 'BEGIN DELETE FROM temp_table; RETURN ''cleaned''; END'");
        assertEquals(1, shown("cleanup"));
        assertEquals("cleaned", call("CALL cleanup()"));
        assertEquals(0L, ((Number) call("SELECT COUNT(*) FROM temp_table")).longValue());
    }

    @Test
    public void testCreateProcedureWithQualifiedName() {
        engine.execute("CREATE SCHEMA my_schema");
        engine.execute("USE SCHEMA test_schema");
        // The body's bare `orders` resolves in the procedure's HOME schema, so the table lives there.
        engine.execute("CREATE TABLE my_schema.orders (id INTEGER)");
        engine.execute("INSERT INTO my_schema.orders VALUES (1), (2), (3)");
        engine.execute("""
            CREATE PROCEDURE my_schema.process_orders(order_id INTEGER) RETURNS INTEGER
            AS 'BEGIN RETURN (SELECT COUNT(*) FROM orders); END'
            """);
        assertEquals(3L, ((Number) call("CALL my_schema.process_orders(1)")).longValue());
    }

    @Test
    public void testCreateProcedureWithMultipleParameters() {
        engine.execute("CREATE TABLE people (name VARCHAR, age INTEGER, active BOOLEAN)");
        engine.execute("""
            CREATE PROCEDURE insert_record(name VARCHAR, age INTEGER, active BOOLEAN) RETURNS INTEGER
            AS 'BEGIN INSERT INTO people (name, age, active) VALUES (:name, :age, :active); RETURN 1; END'
            """);
        assertEquals(1, shown("insert_record"));
        assertEquals(1L, ((Number) call("CALL insert_record('Ada', 36, TRUE)")).longValue());
        final ResultSet rows = engine.executeQuery("SELECT name, age, active FROM people");
        assertEquals(1, rows.getRowCount());
        assertEquals("Ada", rows.getRows().get(0).getValue(0));
        assertEquals(36L, ((Number) rows.getRows().get(0).getValue(1)).longValue());
    }

    @Test
    public void testDropProcedureExists() {
        engine.execute("CREATE PROCEDURE test_proc(x INTEGER) RETURNS INTEGER AS 'BEGIN RETURN x * 2; END'");
        assertEquals(1, shown("test_proc"));
        engine.execute("DROP PROCEDURE test_proc(INTEGER)");
        assertEquals(0, shown("test_proc"));
    }

    @Test
    public void testShowProcedures() {
        engine.execute("CREATE PROCEDURE proc1(x INTEGER) RETURNS INTEGER AS 'BEGIN RETURN x; END'");
        engine.execute(
            "CREATE PROCEDURE proc2(x INTEGER, y INTEGER) RETURNS INTEGER AS 'BEGIN RETURN x + y; END'");
        assertEquals(1, shown("proc1"));
        assertEquals(1, shown("proc2"));
        assertEquals(1L, ((Number) call("CALL proc1(1)")).longValue());
        assertEquals(5L, ((Number) call("CALL proc2(2, 3)")).longValue());
    }

    @Test
    public void testCreateProcedureWithIntegerReturn() {
        engine.execute("CREATE TABLE test_table (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO test_table VALUES (1, 'Alice')");
        engine.execute("INSERT INTO test_table VALUES (2, 'Bob')");
        engine.execute("INSERT INTO test_table VALUES (3, 'Charlie')");
        engine.execute("""
            CREATE PROCEDURE check_value(max_count INTEGER) RETURNS VARCHAR
            AS 'DECLARE outcome VARCHAR; BEGIN IF (max_count > 2) THEN outcome := ''many''; ELSE outcome := ''few''; END IF; RETURN outcome; END'
            """);
        assertEquals(1, shown("check_value"));
        assertEquals("many", call("CALL check_value(3)"));
        assertEquals("few", call("CALL check_value(1)"));
    }

    @Test
    public void testCreateProcedureWithLoopAndObjectReturn() {
        engine.execute("""
            CREATE PROCEDURE process_with_loop(max_iterations INTEGER) RETURNS OBJECT
            AS 'DECLARE counter INTEGER := 0; outcome OBJECT; BEGIN WHILE (counter < 10) DO counter := counter + 1; END WHILE; outcome := OBJECT_CONSTRUCT(''count'', counter, ''status'', ''completed''); RETURN outcome; END'
            """);
        assertEquals(1, shown("process_with_loop"));
        final Object result = call("CALL process_with_loop(5)");
        assertNotNull(result);
        final String text = String.valueOf(result);
        assertTrue(text.contains("\"count\"") && text.contains("10"),
            "loop must have counted to 10: " + text);
        assertTrue(text.contains("completed"), "status must be completed: " + text);
    }
}
