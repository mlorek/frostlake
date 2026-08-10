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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class IdentifierFunctionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE employees (id INTEGER, name VARCHAR, salary DOUBLE)");
        engine.execute("INSERT INTO employees VALUES (1, 'Alice', 90000)");
        engine.execute("INSERT INTO employees VALUES (2, 'Bob', 80000)");
    }

    @Test
    public void testIdentifierAsColumnName() {
        // IDENTIFIER(string) in SELECT resolves string to column name
        final ResultSet rs = engine.executeQuery("SELECT IDENTIFIER('name') FROM employees ORDER BY id");
        assertNotNull(rs);
        assertEquals(2, rs.getRowCount());
        assertEquals("Alice", rs.getRows().get(0).getValue(0).toString());
        assertEquals("Bob", rs.getRows().get(1).getValue(0).toString());
    }

    @Test
    public void testIdentifierAsColumnNameWithSessionVar() {
        engine.execute("SET col = 'salary'");
        final ResultSet rs = engine.executeQuery("SELECT IDENTIFIER($col) FROM employees ORDER BY id");
        assertNotNull(rs);
        assertEquals(2, rs.getRowCount());
        assertEquals(90000.0, ((Number) rs.getRows().get(0).getValue(0)).doubleValue(), 0.01);
    }

    @Test
    public void testIdentifierAsTableName() {
        engine.execute("SET tbl = 'employees'");
        final ResultSet rs = engine.executeQuery("SELECT id, name FROM IDENTIFIER($tbl) ORDER BY id");
        assertNotNull(rs);
        assertEquals(2, rs.getRowCount());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals("Alice", rs.getRows().get(0).getValue(1).toString());
    }

    @Test
    public void testIdentifierAsTableNameLiteral() {
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM IDENTIFIER('employees')");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void testIdentifierResolvesView() {
        // FROM IDENTIFIER('<view>') must resolve a view, not only a base table — e.g. a backfill source
        // that is a view is read this way.
        engine.execute("CREATE VIEW hi_earners AS SELECT id, name FROM employees WHERE salary >= 85000");
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM IDENTIFIER('hi_earners')");
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void testIdentifierResolvesStream() {
        // FROM IDENTIFIER('<stream>') must resolve a stream — the loaders read their incremental source
        // (a stream) this way, toggling with the backfill view above via a variable.
        engine.execute("CREATE STREAM emp_stream ON TABLE employees");
        engine.execute("INSERT INTO employees VALUES (3, 'Carol', 70000)");
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM IDENTIFIER('emp_stream')");
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }
}
