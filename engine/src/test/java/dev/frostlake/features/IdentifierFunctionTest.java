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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class IdentifierFunctionTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
        engine.execute("CREATE TABLE employees (id INTEGER, name VARCHAR, salary DOUBLE)");
        engine.execute("INSERT INTO employees VALUES (1, 'Alice', 90000)");
        engine.execute("INSERT INTO employees VALUES (2, 'Bob', 80000)");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) engine.shutdown();
    }

    @Test
    public void testIdentifierAsColumnName() {
        // IDENTIFIER(string) in SELECT resolves string to column name
        ResultSet rs = engine.executeQuery("SELECT IDENTIFIER('name') FROM employees ORDER BY id");
        assertNotNull(rs);
        assertEquals(2, rs.getRowCount());
        assertEquals("Alice", rs.getRows().get(0).getValue(0).toString());
        assertEquals("Bob", rs.getRows().get(1).getValue(0).toString());
    }

    @Test
    public void testIdentifierAsColumnNameWithSessionVar() {
        engine.execute("SET col = 'salary'");
        ResultSet rs = engine.executeQuery("SELECT IDENTIFIER($col) FROM employees ORDER BY id");
        assertNotNull(rs);
        assertEquals(2, rs.getRowCount());
        assertEquals(90000.0, ((Number) rs.getRows().get(0).getValue(0)).doubleValue(), 0.01);
    }

    @Test
    public void testIdentifierAsTableName() {
        engine.execute("SET tbl = 'employees'");
        ResultSet rs = engine.executeQuery("SELECT id, name FROM IDENTIFIER($tbl) ORDER BY id");
        assertNotNull(rs);
        assertEquals(2, rs.getRowCount());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals("Alice", rs.getRows().get(0).getValue(1).toString());
    }

    @Test
    public void testIdentifierAsTableNameLiteral() {
        ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM IDENTIFIER('employees')");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }
}
