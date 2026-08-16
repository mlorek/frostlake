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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for ORDER BY clause
 */
public class OrderByTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("""
            CREATE TABLE employees (
            id INTEGER,
            name VARCHAR,
            department VARCHAR,
            salary INTEGER,
            age INTEGER
            )
            """);

        engine.execute("INSERT INTO employees VALUES (1, 'Alice', 'Engineering', 90000, 30)");
        engine.execute("INSERT INTO employees VALUES (2, 'Bob', 'Sales', 70000, 35)");
        engine.execute("INSERT INTO employees VALUES (3, 'Charlie', 'Engineering', 95000, 28)");
        engine.execute("INSERT INTO employees VALUES (4, 'Diana', 'Sales', 75000, 32)");
        engine.execute("INSERT INTO employees VALUES (5, 'Eve', 'Engineering', 85000, 29)");
        engine.execute("INSERT INTO employees VALUES (6, 'Frank', 'HR', 65000, 40)");
    }

    @Test
    public void testOrderByAscending() {
        final ResultSet result = engine.executeQuery(
            "SELECT * FROM employees ORDER BY salary ASC"
        );

        assertEquals(6, result.getRowCount());

        final List<Row> rows = result.getRows();
        assertEquals(65000L, ((Number) rows.get(0).getValue(3)).longValue());
        assertEquals(95000L, ((Number) rows.get(5).getValue(3)).longValue());
    }

    @Test
    public void testOrderByDescending() {
        final ResultSet result = engine.executeQuery(
            "SELECT * FROM employees ORDER BY age DESC"
        );

        assertEquals(6, result.getRowCount());

        final List<Row> rows = result.getRows();
        assertEquals(40L, ((Number) rows.get(0).getValue(4)).longValue());
    }

    @Test
    public void testOrderByMultipleColumns() {
        final ResultSet result = engine.executeQuery(
            "SELECT * FROM employees ORDER BY department ASC, salary DESC"
        );

        assertEquals(6, result.getRowCount());

        final List<Row> rows = result.getRows();
        assertEquals("Engineering", rows.get(0).getValue(2).toString());
        assertEquals(95000L, ((Number) rows.get(0).getValue(3)).longValue());
    }

    @Test
    public void testOrderByWithWhere() {
        final ResultSet result = engine.executeQuery(
            "SELECT * FROM employees WHERE salary > 70000 ORDER BY salary DESC"
        );

        assertEquals(4, result.getRowCount());

        final List<Row> rows = result.getRows();
        assertEquals(95000L, ((Number) rows.get(0).getValue(3)).longValue());
    }

    @Test
    public void testOrderByWithLimit() {
        final ResultSet result = engine.executeQuery(
            "SELECT * FROM employees ORDER BY salary DESC LIMIT 2"
        );

        assertEquals(2, result.getRowCount());

        final List<Row> rows = result.getRows();
        assertEquals(95000L, ((Number) rows.get(0).getValue(3)).longValue());
        assertEquals(90000L, ((Number) rows.get(1).getValue(3)).longValue());
    }
}
