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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for basic SELECT queries
 */
public class SelectBasicTest extends BaseDatabaseTest {

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

        // Insert test data
        engine.execute("INSERT INTO employees VALUES (1, 'Alice', 'Engineering', 90000, 30)");
        engine.execute("INSERT INTO employees VALUES (2, 'Bob', 'Sales', 70000, 35)");
        engine.execute("INSERT INTO employees VALUES (3, 'Charlie', 'Engineering', 95000, 28)");
        engine.execute("INSERT INTO employees VALUES (4, 'Diana', 'Sales', 75000, 32)");
        engine.execute("INSERT INTO employees VALUES (5, 'Eve', 'Engineering', 85000, 29)");
        engine.execute("INSERT INTO employees VALUES (6, 'Frank', 'HR', 65000, 40)");
    }

    @Test
    public void testSelectAll() {
        ResultSet result = engine.executeQuery("SELECT * FROM employees");
        assertEquals(6, result.getRowCount());
        assertEquals(5, result.getColumnCount());
    }

    @Test
    public void testSelectSpecificColumns() {
        ResultSet result = engine.executeQuery("SELECT name, department FROM employees");
        assertEquals(6, result.getRowCount());
        assertEquals(2, result.getColumnCount());
    }

    @Test
    public void testSelectWithLimit() {
        ResultSet result = engine.executeQuery("SELECT * FROM employees LIMIT 3");
        assertEquals(3, result.getRowCount());
    }

    @Test
    public void testSelectCount() {
        ResultSet result = engine.executeQuery("SELECT COUNT(*) FROM employees");
        assertEquals(1, result.getRowCount());
    }

    @Test
    public void testSelectFromEmptyTable() {
        engine.execute("CREATE TABLE empty_table (id INTEGER, name VARCHAR)");
        ResultSet result = engine.executeQuery("SELECT * FROM empty_table");
        assertEquals(0, result.getRowCount());
    }

    @Test
    public void testSelectWithTrailingComma() {
        ResultSet result = engine.executeQuery("SELECT id, name, department, FROM employees");
        assertEquals(6, result.getRowCount());
        assertEquals(3, result.getColumnCount());
    }

    @Test
    public void testSelectWithTrailingCommaAndWhere() {
        ResultSet result = engine.executeQuery("SELECT name, department, FROM employees WHERE age > 30");
        assertEquals(3, result.getRowCount());
        assertEquals(2, result.getColumnCount());
    }

    @Test
    public void testSelectWithTrailingCommaMultipleColumns() {
        ResultSet result = engine.executeQuery("""
            SELECT
                id,
                name,
                department,
                salary,
            FROM employees
            """);
        assertEquals(6, result.getRowCount());
        assertEquals(4, result.getColumnCount());
    }

    @Test
    public void testSelectWithTrailingCommaAndOrderBy() {
        ResultSet result = engine.executeQuery("""
            SELECT
                name,
                salary,
            FROM employees
            ORDER BY salary DESC
            """);
        assertEquals(6, result.getRowCount());
        assertEquals(2, result.getColumnCount());
    }

    @Test
    public void testSelectWithTrailingCommaAndGroupBy() {
        ResultSet result = engine.executeQuery("""
            SELECT
                department,
                COUNT(*),
            FROM employees
            GROUP BY department
            """);
        assertEquals(3, result.getRowCount());
        assertEquals(2, result.getColumnCount());
    }

    @Test
    public void testSelectWithTrailingCommaAndJoin() {
        engine.execute("CREATE TABLE departments (name VARCHAR, budget INTEGER)");
        engine.execute("INSERT INTO departments VALUES ('Engineering', 1000000)");
        engine.execute("INSERT INTO departments VALUES ('Sales', 500000)");
        engine.execute("INSERT INTO departments VALUES ('HR', 300000)");

        ResultSet result = engine.executeQuery("""
            SELECT
                e.name,
                e.department,
                d.budget,
            FROM employees e
            JOIN departments d ON e.department = d.name
            """);
        assertEquals(6, result.getRowCount());
        assertEquals(3, result.getColumnCount());
    }

    @Test
    public void testSelectWithTrailingCommaInSubquery() {
        ResultSet result = engine.executeQuery("""
            SELECT * FROM (
                SELECT
                    id,
                    name,
                    department,
                FROM employees
            )
            """);
        assertEquals(6, result.getRowCount());
        assertEquals(3, result.getColumnCount());
    }

    @Test
    public void testSelectSingleColumnWithTrailingComma() {
        ResultSet result = engine.executeQuery("SELECT name, FROM employees");
        assertEquals(6, result.getRowCount());
        assertEquals(1, result.getColumnCount());
    }

    @Test
    public void testSelectStarNotAffectedByTrailingComma() {
        ResultSet result = engine.executeQuery("SELECT * FROM employees");
        assertEquals(6, result.getRowCount());
        assertEquals(5, result.getColumnCount());
    }

    @Test
    public void testSelectWithTrailingCommaAndAlias() {
        ResultSet result = engine.executeQuery("""
            SELECT
                id AS emp_id,
                name AS emp_name,
                department AS dept,
            FROM employees
            """);
        assertEquals(6, result.getRowCount());
        assertEquals(3, result.getColumnCount());
    }

    @Test
    public void testSelectWithTrailingCommaAndExpressions() {
        ResultSet result = engine.executeQuery("""
            SELECT
                name,
                salary * 12 AS annual_salary,
                age + 1 AS next_year_age,
            FROM employees
            """);
        assertEquals(6, result.getRowCount());
        assertEquals(3, result.getColumnCount());
    }
}
