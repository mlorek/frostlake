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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for GROUP BY with aggregate functions
 */
public class GroupByTest extends BaseDatabaseTest {

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
    public void testGroupByWithCount() {
        final ResultSet result = engine.executeQuery(
            "SELECT department, COUNT(*) FROM employees GROUP BY department"
        );

        assertEquals(3, result.getRowCount());

        boolean foundEngineering = false;
        for (final Row row : result.getRows()) {
            final String dept = row.getValue(0).toString();
            final long count = ((Number) row.getValue(1)).longValue();

            if (dept.equals("Engineering")) {
                foundEngineering = true;
                assertEquals(3, count);
            }
        }
        assertTrue(foundEngineering);
    }

    @Test
    public void testGroupByWithSum() {
        final ResultSet result = engine.executeQuery(
            "SELECT department, SUM(salary) FROM employees GROUP BY department"
        );

        assertEquals(3, result.getRowCount());

        for (final Row row : result.getRows()) {
            final String dept = row.getValue(0).toString();
            final double totalSalary = ((Number) row.getValue(1)).doubleValue();

            if (dept.equals("Engineering")) {
                assertEquals(270000.0, totalSalary, 1.0);
            }
        }
    }

    @Test
    public void testGroupByWithAvg() {
        final ResultSet result = engine.executeQuery(
            "SELECT department, AVG(salary) FROM employees GROUP BY department"
        );

        assertEquals(3, result.getRowCount());

        for (final Row row : result.getRows()) {
            final String dept = row.getValue(0).toString();
            final double avgSalary = ((Number) row.getValue(1)).doubleValue();

            if (dept.equals("Sales")) {
                assertEquals(72500.0, avgSalary, 1.0);
            }
        }
    }

    @Test
    public void testGroupByWithMinMax() {
        final ResultSet result = engine.executeQuery(
            "SELECT department, MIN(age), MAX(age) FROM employees GROUP BY department"
        );

        assertEquals(3, result.getRowCount());
    }

    @Test
    public void testGroupByWithMultipleAggregates() {
        final ResultSet result = engine.executeQuery(
            "SELECT department, COUNT(*), AVG(salary), MAX(salary) FROM employees GROUP BY department"
        );

        assertEquals(3, result.getRowCount());
        assertEquals(4, result.getColumnCount());
    }

    @Test
    public void testGroupByWithOrderBy() {
        final ResultSet result = engine.executeQuery(
            "SELECT department, COUNT(*) FROM employees GROUP BY department ORDER BY COUNT(*) DESC"
        );

        assertEquals(3, result.getRowCount());

        // Engineering (3 employees) should be first
        assertEquals("Engineering", result.getRows().get(0).getValue(0).toString());
        assertEquals(3L, ((Number) result.getRows().get(0).getValue(1)).longValue());
    }

    @Test
    public void testGroupByWithWhere() {
        final ResultSet result = engine.executeQuery(
            "SELECT department, COUNT(*) FROM employees WHERE salary > 70000 GROUP BY department"
        );

        assertTrue(result.getRowCount() >= 1);
    }
}
