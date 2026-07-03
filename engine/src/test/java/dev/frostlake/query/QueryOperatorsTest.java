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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QueryOperatorsTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setup() {
        engine = new DatabaseEngine();

        // Setup test database and table
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("USE SCHEMA test_schema");

        engine.execute("""
            CREATE TABLE employees (
            id INTEGER,
            name VARCHAR,
            department VARCHAR,
            salary INTEGER,
            age INTEGER
            )
            """);

        // Insert test data
        engine.execute("INSERT INTO employees VALUES (1, 'Alice', 'Engineering', 90000, 30)");
        engine.execute("INSERT INTO employees VALUES (2, 'Bob', 'Sales', 70000, 35)");
        engine.execute("INSERT INTO employees VALUES (3, 'Charlie', 'Engineering', 95000, 28)");
        engine.execute("INSERT INTO employees VALUES (4, 'Diana', 'Sales', 75000, 32)");
        engine.execute("INSERT INTO employees VALUES (5, 'Eve', 'Engineering', 85000, 29)");
        engine.execute("INSERT INTO employees VALUES (6, 'Frank', 'HR', 65000, 40)");
    }

    @AfterEach
    public void teardown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testWhereClauseEquals() {
        ResultSet result = engine.executeQuery(
            "SELECT * FROM employees WHERE department = 'Engineering'"
        );

        assertEquals(3, result.getRowCount(), "Should return 3 Engineering employees");
    }

    @Test
    public void testWhereClauseGreaterThan() {
        ResultSet result = engine.executeQuery(
            "SELECT * FROM employees WHERE salary > 80000"
        );

        assertEquals(3, result.getRowCount(), "Should return 3 employees with salary > 80000");
    }

    @Test
    public void testWhereClauseLessThanOrEqual() {
        ResultSet result = engine.executeQuery(
            "SELECT * FROM employees WHERE age <= 30"
        );

        assertEquals(3, result.getRowCount(), "Should return 3 employees with age <= 30");
    }

    @Test
    public void testWhereClauseAnd() {
        ResultSet result = engine.executeQuery(
            "SELECT * FROM employees WHERE department = 'Engineering' AND salary > 85000"
        );

        assertEquals(2, result.getRowCount(), "Should return 2 employees");
    }

    @Test
    public void testWhereClauseOr() {
        ResultSet result = engine.executeQuery(
            "SELECT * FROM employees WHERE department = 'HR' OR age >= 35"
        );

        assertEquals(2, result.getRowCount(), "Should return 2 employees (Frank from HR + Bob with age >= 35)");
    }

    @Test
    public void testWhereClauseNotEqual() {
        ResultSet result = engine.executeQuery(
            "SELECT * FROM employees WHERE department <> 'Engineering'"
        );

        assertEquals(3, result.getRowCount(), "Should return 3 non-Engineering employees");
    }

    @Test
    public void testOrderByAscending() {
        ResultSet result = engine.executeQuery(
            "SELECT * FROM employees ORDER BY salary ASC"
        );

        assertEquals(6, result.getRowCount());

        // Check first and last rows
        List<Row> rows = result.getRows();
        assertEquals(65000L, ((Number) rows.get(0).getValue(3)).longValue(), "First should be lowest salary");
        assertEquals(95000L, ((Number) rows.get(5).getValue(3)).longValue(), "Last should be highest salary");
    }

    @Test
    public void testOrderByDescending() {
        ResultSet result = engine.executeQuery(
            "SELECT * FROM employees ORDER BY age DESC"
        );

        assertEquals(6, result.getRowCount());

        // Check first row has highest age
        List<Row> rows = result.getRows();
        assertEquals(40L, ((Number) rows.get(0).getValue(4)).longValue(), "First should be highest age");
    }

    @Test
    public void testOrderByMultipleColumns() {
        ResultSet result = engine.executeQuery(
            "SELECT * FROM employees ORDER BY department ASC, salary DESC"
        );

        assertEquals(6, result.getRowCount());
        // Engineering employees should be first, ordered by salary DESC
        List<Row> rows = result.getRows();
        assertEquals("Engineering", rows.get(0).getValue(2).toString());
        assertEquals(95000L, ((Number) rows.get(0).getValue(3)).longValue());
    }

    @Test
    public void testGroupByWithCount() {
        ResultSet result = engine.executeQuery(
            "SELECT department, COUNT(*) FROM employees GROUP BY department"
        );

        assertEquals(3, result.getRowCount(), "Should have 3 departments");

        // Check that we have the right groups
        boolean foundEngineering = false;
        for (final Row row : result.getRows()) {
            String dept = row.getValue(0).toString();
            long count = ((Number) row.getValue(1)).longValue();

            if (dept.equals("Engineering")) {
                foundEngineering = true;
                assertEquals(3, count, "Engineering should have 3 employees");
            }
        }
        assertTrue(foundEngineering, "Should find Engineering department");
    }

    @Test
    public void testGroupByWithSum() {
        ResultSet result = engine.executeQuery(
            "SELECT department, SUM(salary) FROM employees GROUP BY department"
        );

        assertEquals(3, result.getRowCount(), "Should have 3 departments");

        for (final Row row : result.getRows()) {
            String dept = row.getValue(0).toString();
            double totalSalary = ((Number) row.getValue(1)).doubleValue();

            if (dept.equals("Engineering")) {
                assertEquals(270000.0, totalSalary, 1.0, "Engineering total salary should be 270000");
            }
        }
    }

    @Test
    public void testGroupByWithAvg() {
        ResultSet result = engine.executeQuery(
            "SELECT department, AVG(salary) FROM employees GROUP BY department"
        );

        assertEquals(3, result.getRowCount(), "Should have 3 departments");

        for (final Row row : result.getRows()) {
            String dept = row.getValue(0).toString();
            double avgSalary = ((Number) row.getValue(1)).doubleValue();

            if (dept.equals("Sales")) {
                assertEquals(72500.0, avgSalary, 1.0, "Sales average salary should be 72500");
            }
        }
    }

    @Test
    public void testGroupByWithMinMax() {
        ResultSet result = engine.executeQuery(
            "SELECT department, MIN(age), MAX(age) FROM employees GROUP BY department"
        );

        assertEquals(3, result.getRowCount(), "Should have 3 departments");
    }

    @Test
    public void testLimitClause() {
        ResultSet result = engine.executeQuery(
            "SELECT * FROM employees LIMIT 3"
        );

        assertEquals(3, result.getRowCount(), "Should return only 3 rows");
    }

    @Test
    public void testWhereWithOrderBy() {
        ResultSet result = engine.executeQuery(
            "SELECT * FROM employees WHERE salary > 70000 ORDER BY salary DESC"
        );

        assertEquals(4, result.getRowCount(), "Should return 4 employees with salary > 70000");

        // Check first row has highest salary
        List<Row> rows = result.getRows();
        assertEquals(95000L, ((Number) rows.get(0).getValue(3)).longValue());
    }

    @Test
    public void testWhereWithGroupBy() {
        ResultSet result = engine.executeQuery(
            "SELECT department, COUNT(*) FROM employees WHERE salary > 70000 GROUP BY department"
        );

        // Should only group employees with salary > 70000
        assertTrue(result.getRowCount() >= 1, "Should have at least 1 department");
    }

    @Test
    public void testGroupByWithOrderBy() {
        ResultSet result = engine.executeQuery(
            "SELECT department, COUNT(*) FROM employees GROUP BY department ORDER BY COUNT(*) DESC"
        );

        assertEquals(3, result.getRowCount(), "Should have 3 departments");

        // Engineering (3 employees) should be first
        List<Row> rows = result.getRows();
        assertEquals("Engineering", rows.get(0).getValue(0).toString());
        assertEquals(3L, ((Number) rows.get(0).getValue(1)).longValue());
    }

    @Test
    public void testComplexQuery() {
        ResultSet result = engine.executeQuery("""
            SELECT department, AVG(salary), COUNT(*)
            FROM employees
            WHERE age < 35
            GROUP BY department
            ORDER BY AVG(salary) DESC
            LIMIT 2
            """);

        assertTrue(result.getRowCount() <= 2, "Should return at most 2 rows");
        assertTrue(result.getColumnCount() == 3, "Should have 3 columns");
    }
}
