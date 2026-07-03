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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for complex queries combining multiple clauses
 */
public class ComplexQueryTest extends BaseDatabaseTest {

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
    public void testWhereWithOrderByAndLimit() {
        ResultSet result = engine.executeQuery(
            "SELECT * FROM employees WHERE salary > 70000 ORDER BY salary DESC LIMIT 2"
        );

        assertEquals(2, result.getRowCount());
    }

    @Test
    public void testGroupByWithOrderByAndLimit() {
        ResultSet result = engine.executeQuery(
            "SELECT department, COUNT(*) FROM employees GROUP BY department ORDER BY COUNT(*) DESC LIMIT 2"
        );

        assertEquals(2, result.getRowCount());
    }

    @Test
    public void testWhereWithGroupByAndOrderBy() {
        ResultSet result = engine.executeQuery("""
            SELECT department, AVG(salary), COUNT(*)
            FROM employees
            WHERE age < 35
            GROUP BY department
            ORDER BY AVG(salary) DESC
            LIMIT 2
            """);

        assertTrue(result.getRowCount() <= 2);
        assertEquals(3, result.getColumnCount());
    }

    @Test
    public void testMultipleAggregatesWithComplexConditions() {
        ResultSet result = engine.executeQuery("""
            SELECT department, COUNT(*), AVG(salary), MAX(salary), MIN(age)
            FROM employees
            WHERE salary >= 70000
            GROUP BY department
            """);

        assertTrue(result.getRowCount() >= 1);
        assertEquals(5, result.getColumnCount());
    }

    @Test
    public void testComplexWhereClause() {
        ResultSet result = engine.executeQuery("""
            SELECT * FROM employees
            WHERE (department = 'Engineering' AND salary > 85000) OR (department = 'Sales' AND age > 30)
            """);

        assertTrue(result.getRowCount() >= 1);
    }

    @Test
    public void testJoinLikeQueryWithMultipleTables() {
        // Create second table
        engine.execute("CREATE TABLE departments (name VARCHAR, location VARCHAR)");
        engine.execute("INSERT INTO departments VALUES ('Engineering', 'Building A')");
        engine.execute("INSERT INTO departments VALUES ('Sales', 'Building B')");
        engine.execute("INSERT INTO departments VALUES ('HR', 'Building C')");

        // For now just test basic queries on both tables
        ResultSet empResult = engine.executeQuery("SELECT * FROM employees");
        ResultSet deptResult = engine.executeQuery("SELECT * FROM departments");

        assertEquals(6, empResult.getRowCount());
        assertEquals(3, deptResult.getRowCount());
    }
}
