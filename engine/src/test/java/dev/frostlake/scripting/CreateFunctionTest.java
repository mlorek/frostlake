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

/**
 * CREATE FUNCTION for SQL UDFs, asserted through SQL — the function is INVOKED and its results
 * checked, and existence is read back through {@code SHOW USER FUNCTIONS LIKE} — never through
 * engine internals, so the same assertions hold against a live account.
 */
public class CreateFunctionTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount(), "expected one row from: " + sql);
        return rs.getRows().get(0).getValue(0);
    }

    private int shown(final String name) {
        return engine.executeQuery("SHOW USER FUNCTIONS LIKE '" + name + "'").getRowCount();
    }

    @Test
    public void testCreateSimpleFunction() {
        engine.execute("CREATE FUNCTION add_numbers(x INTEGER, y INTEGER) RETURNS INTEGER AS 'x + y'");
        assertEquals(1, shown("add_numbers"));
        assertEquals(5L, ((Number) scalar("SELECT add_numbers(2, 3)")).longValue());
    }

    @Test
    public void testCreateFunctionWithNoParameters() {
        engine.execute("CREATE FUNCTION get_pi() RETURNS FLOAT AS '3.14159::FLOAT'");
        assertEquals(1, shown("get_pi"));
        assertEquals(3.14159, ((Number) scalar("SELECT get_pi()")).doubleValue(), 1e-9);
    }

    @Test
    public void testCreateFunctionWithQualifiedName() {
        engine.execute("CREATE SCHEMA my_schema");
        engine.execute("USE SCHEMA test_schema");
        engine.execute("CREATE FUNCTION my_schema.multiply(a INTEGER, b INTEGER) RETURNS INTEGER AS 'a * b'");
        assertEquals(42L, ((Number) scalar("SELECT my_schema.multiply(6, 7)")).longValue());
    }

    @Test
    public void testCreateTableFunction() {
        engine.execute("CREATE FUNCTION get_values() RETURNS TABLE(id INTEGER, name VARCHAR) AS 'SELECT 1, ''test'''");
        final ResultSet rs = engine.executeQuery("SELECT * FROM TABLE(get_values())");
        assertEquals(1, rs.getRowCount());
        // The output columns carry the RETURNS TABLE names, not generated ones.
        assertEquals("ID", rs.getColumns().get(0).getName());
        assertEquals("NAME", rs.getColumns().get(1).getName());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals("test", rs.getRows().get(0).getValue(1));
    }

    @Test
    public void testCreateFunctionWithSingleParameter() {
        engine.execute("CREATE FUNCTION double_val(x INTEGER) RETURNS INTEGER AS 'x * 2'");
        assertEquals(1, shown("double_val"));
        assertEquals(42L, ((Number) scalar("SELECT double_val(21)")).longValue());
    }

    @Test
    public void testDropFunctionExists() {
        engine.execute("CREATE FUNCTION test_func(x INTEGER) RETURNS INTEGER AS 'x * 2'");
        assertEquals(1, shown("test_func"));
        engine.execute("DROP FUNCTION test_func(INTEGER)");
        assertEquals(0, shown("test_func"));
    }

    @Test
    public void testShowFunctions() {
        engine.execute("CREATE FUNCTION func1(x INTEGER) RETURNS INTEGER AS 'x'");
        engine.execute("CREATE FUNCTION func2(x INTEGER, y INTEGER) RETURNS INTEGER AS 'x + y'");
        assertEquals(1, shown("func1"));
        assertEquals(1, shown("func2"));
        assertNotNull(engine.executeQuery("SHOW USER FUNCTIONS"));
    }
}
