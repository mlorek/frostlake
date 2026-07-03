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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test quantified comparison operators: ANY, SOME, ALL
 */
public class QuantifiedComparisonTest {

    private static DatabaseEngine engine;

    @BeforeAll
    public static void setup() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @AfterAll
    public static void teardown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @BeforeEach
    public void setupData() {
        // Clean up and recreate tables for each test
        try {
            engine.execute("DROP TABLE IF EXISTS employees");
            engine.execute("DROP TABLE IF EXISTS departments");
        } catch (final Exception e) {
            // Ignore errors
        }

        // Create employees table
        engine.execute("CREATE TABLE employees (id INT, name VARCHAR, salary INT, dept VARCHAR)");
        engine.execute("INSERT INTO employees VALUES (1, 'Alice', 75000, 'Engineering')");
        engine.execute("INSERT INTO employees VALUES (2, 'Bob', 60000, 'Sales')");
        engine.execute("INSERT INTO employees VALUES (3, 'Charlie', 80000, 'Engineering')");
        engine.execute("INSERT INTO employees VALUES (4, 'Diana', 55000, 'Sales')");
        engine.execute("INSERT INTO employees VALUES (5, 'Eve', 90000, 'Engineering')");

        // Create departments table
        engine.execute("CREATE TABLE departments (dept_name VARCHAR, min_salary INT)");
        engine.execute("INSERT INTO departments VALUES ('Engineering', 70000)");
        engine.execute("INSERT INTO departments VALUES ('Sales', 50000)");
        engine.execute("INSERT INTO departments VALUES ('Marketing', 60000)");
    }

    @Test
    public void testAnyComparison() {
        // Find employees earning more than ANY sales employee
        ResultSet result = engine.executeQuery("""
            SELECT name, salary FROM employees
            WHERE salary > ANY (SELECT salary FROM employees WHERE dept = 'Sales')
            ORDER BY name
            """);

        // Should return Alice (75000 > 55000), Bob (60000 > 55000), Charlie (80000 > 55000), Eve (90000 > 55000)
        // Basically everyone earning more than the minimum sales salary (55000)
        assertEquals(4, result.getRows().size());
        assertEquals("Alice", result.getRows().get(0).getValue(0));
        assertEquals("Bob", result.getRows().get(1).getValue(0));
        assertEquals("Charlie", result.getRows().get(2).getValue(0));
        assertEquals("Eve", result.getRows().get(3).getValue(0));
    }

    @Test
    public void testSomeComparison() {
        // SOME is synonymous with ANY
        ResultSet result = engine.executeQuery("""
            SELECT name, salary FROM employees
            WHERE salary > SOME (SELECT salary FROM employees WHERE dept = 'Sales')
            ORDER BY name
            """);

        // Should return same results as ANY
        assertEquals(4, result.getRows().size());
        assertEquals("Alice", result.getRows().get(0).getValue(0));
    }

    @Test
    public void testAllComparison() {
        // Find employees earning more than ALL sales employees
        ResultSet result = engine.executeQuery("""
            SELECT name, salary FROM employees
            WHERE salary > ALL (SELECT salary FROM employees WHERE dept = 'Sales')
            ORDER BY name
            """);

        // Should return only those earning more than the maximum sales salary (60000)
        // That's Alice (75000), Charlie (80000), and Eve (90000)
        assertEquals(3, result.getRows().size());
        assertEquals("Alice", result.getRows().get(0).getValue(0));
        assertEquals("Charlie", result.getRows().get(1).getValue(0));
        assertEquals("Eve", result.getRows().get(2).getValue(0));
    }

    @Test
    public void testAllComparisonLessOrEqual() {
        // Find employees earning <= ALL engineering employees
        ResultSet result = engine.executeQuery("""
            SELECT name, salary FROM employees
            WHERE salary <= ALL (SELECT salary FROM employees WHERE dept = 'Engineering')
            ORDER BY name
            """);

        // Should return only those earning <= minimum engineering salary (75000)
        // That's Alice (75000), Bob (60000), and Diana (55000)
        assertEquals(3, result.getRows().size());
        assertEquals("Alice", result.getRows().get(0).getValue(0));
        assertEquals("Bob", result.getRows().get(1).getValue(0));
        assertEquals("Diana", result.getRows().get(2).getValue(0));
    }

    @Test
    public void testAnyComparisonEquals() {
        // Find employees in departments that exist in departments table
        ResultSet result = engine.executeQuery("""
            SELECT name FROM employees
            WHERE dept = ANY (SELECT dept_name FROM departments)
            ORDER BY name
            """);

        // Should return all employees (all departments exist in departments table)
        assertEquals(5, result.getRows().size());
    }

    @Test
    public void testAllComparisonWithEmptySubquery() {
        // ALL with empty subquery should return all rows (vacuous truth)
        ResultSet result = engine.executeQuery("""
            SELECT name FROM employees
            WHERE salary > ALL (SELECT salary FROM employees WHERE dept = 'NonExistent')
            ORDER BY name
            """);

        // Empty subquery for ALL returns true for all rows
        assertEquals(5, result.getRows().size());
    }

    @Test
    public void testAnyComparisonWithEmptySubquery() {
        // ANY with empty subquery should return no rows
        ResultSet result = engine.executeQuery("""
            SELECT name FROM employees
            WHERE salary > ANY (SELECT salary FROM employees WHERE dept = 'NonExistent')
            ORDER BY name
            """);

        // Empty subquery for ANY returns false for all rows
        assertEquals(0, result.getRows().size());
    }

    @Test
    public void testNestedQuantifiedComparison() {
        // Find departments where min_salary is higher than any employee in Sales
        ResultSet result = engine.executeQuery("""
            SELECT dept_name FROM departments
            WHERE min_salary > ANY (SELECT salary FROM employees WHERE dept = 'Sales')
            ORDER BY dept_name
            """);

        // Engineering (70000 > 55000), Marketing (60000 > 55000)
        assertEquals(2, result.getRows().size());
        assertEquals("Engineering", result.getRows().get(0).getValue(0));
        assertEquals("Marketing", result.getRows().get(1).getValue(0));
    }

    @Test
    public void testComparisonOperators() {
        // Test various comparison operators with ANY

        // >= ANY
        ResultSet result1 = engine.executeQuery("""
            SELECT name FROM employees
            WHERE salary >= ANY (SELECT min_salary FROM departments WHERE dept_name = 'Engineering')
            """);
        assertTrue(result1.getRows().size() > 0);

        // < ANY
        ResultSet result2 = engine.executeQuery("""
            SELECT name FROM employees
            WHERE salary < ANY (SELECT salary FROM employees WHERE dept = 'Engineering')
            ORDER BY name
            """);
        // Should return all employees with salary less than max engineering salary (90000)
        assertEquals(4, result2.getRows().size());

        // != ALL
        ResultSet result3 = engine.executeQuery("""
            SELECT name FROM employees
            WHERE name != ALL (SELECT name FROM employees WHERE dept = 'Sales')
            ORDER BY name
            """);
        // Should return non-Sales employees
        assertEquals(3, result3.getRows().size());
        assertEquals("Alice", result3.getRows().get(0).getValue(0));
        assertEquals("Charlie", result3.getRows().get(1).getValue(0));
        assertEquals("Eve", result3.getRows().get(2).getValue(0));
    }
}
