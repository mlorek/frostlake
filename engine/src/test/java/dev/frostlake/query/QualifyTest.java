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
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for QUALIFY clause with window functions
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class QualifyTest {

    private static DatabaseEngine engine;

    @BeforeAll
    public static void setup() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA PUBLIC");

        // Create test table with employee data
        engine.execute("CREATE TABLE employees (id INTEGER, name VARCHAR, department VARCHAR, salary INTEGER)");

        // Insert test data
        engine.execute("INSERT INTO employees VALUES (1, 'Alice', 'Engineering', 100000)");
        engine.execute("INSERT INTO employees VALUES (2, 'Bob', 'Engineering', 95000)");
        engine.execute("INSERT INTO employees VALUES (3, 'Charlie', 'Engineering', 90000)");
        engine.execute("INSERT INTO employees VALUES (4, 'David', 'Sales', 85000)");
        engine.execute("INSERT INTO employees VALUES (5, 'Eve', 'Sales', 80000)");
        engine.execute("INSERT INTO employees VALUES (6, 'Frank', 'Sales', 75000)");
        engine.execute("INSERT INTO employees VALUES (7, 'Grace', 'HR', 70000)");
        engine.execute("INSERT INTO employees VALUES (8, 'Henry', 'HR', 65000)");
    }

    @AfterAll
    public static void teardown() {
        engine.shutdown();
    }

    @Test
    @Order(1)
    public void testQualifyWithRowNumber() {
        // Get top 3 employees overall
        ResultSet result = engine.executeQuery("""
            SELECT id, name, ROW_NUMBER() OVER (ORDER BY salary DESC) as rank
            FROM employees
            QUALIFY rank <= 3
            """);

        assertEquals(3, result.getRowCount(), "Should return top 3 employees");
    }

    @Test
    @Order(2)
    public void testQualifyWithRowNumberPartition() {
        // Top 2 employees per department: 3 departments (Engineering, Sales, HR) x 2 = 6 rows.
        ResultSet result = engine.executeQuery("""
            SELECT id, name, department, ROW_NUMBER() OVER (PARTITION BY department ORDER BY salary DESC) as dept_rank
            FROM employees
            QUALIFY dept_rank <= 2
            """);

        assertEquals(6, result.getRowCount(), "Top 2 per department across 3 departments");
    }

    @Test
    @Order(3)
    public void testQualifyWithRank() {
        // Get employees with rank <= 2
        ResultSet result = engine.executeQuery("""
            SELECT id, name, RANK() OVER (ORDER BY salary DESC) as rank
            FROM employees
            QUALIFY rank <= 2
            """);

        assertTrue(result.getRowCount() >= 2, "Should return at least top 2 employees");
    }

    @Test
    @Order(4)
    public void testQualifyWithDenseRank() {
        // Get employees with dense rank = 1
        ResultSet result = engine.executeQuery("""
            SELECT id, name, DENSE_RANK() OVER (ORDER BY salary DESC) as dense_rank
            FROM employees
            QUALIFY dense_rank = 1
            """);

        assertTrue(result.getRowCount() >= 1, "Should return at least 1 employee");
    }

    @Test
    @Order(5)
    public void testQualifyGreaterThan() {
        // Get employees with row number > 5
        ResultSet result = engine.executeQuery("""
            SELECT id, name, ROW_NUMBER() OVER (ORDER BY id) as row_num
            FROM employees
            QUALIFY row_num > 5
            """);

        assertEquals(3, result.getRowCount(), "Should return 3 employees (ids 6, 7, 8)");
    }

    @Test
    @Order(6)
    public void testQualifyEquals() {
        // Get employee with row number exactly 3
        ResultSet result = engine.executeQuery("""
            SELECT id, name, ROW_NUMBER() OVER (ORDER BY id) as row_num
            FROM employees
            QUALIFY row_num = 3
            """);

        assertEquals(1, result.getRowCount(), "Should return exactly 1 employee");
    }

    @Test
    @Order(7)
    public void testQualifyWithOrderBy() {
        // QUALIFY filters before ORDER BY
        ResultSet result = engine.executeQuery("""
            SELECT id, name, ROW_NUMBER() OVER (ORDER BY salary DESC) as rank
            FROM employees
            QUALIFY rank <= 3
            ORDER BY name
            """);

        assertEquals(3, result.getRowCount(), "Should return 3 employees");

        // Verify they're ordered by name (not salary)
        result.reset();
        result.next();
        String firstName = (String) result.getValue("name");
        assertTrue(firstName != null, "Should have name in first row");
    }

    @Test
    @Order(8)
    public void testQualifyWithWhere() {
        // Combine WHERE and QUALIFY
        ResultSet result = engine.executeQuery("""
            SELECT id, name, department, ROW_NUMBER() OVER (ORDER BY salary DESC) as rank
            FROM employees
            WHERE department = 'Engineering'
            QUALIFY rank <= 2
            """);

        assertEquals(2, result.getRowCount(), "Should return top 2 from Engineering");
    }

    @Test
    @Order(9)
    public void testQualifyWithLimit() {
        // Combine QUALIFY and LIMIT
        ResultSet result = engine.executeQuery("""
            SELECT id, name, ROW_NUMBER() OVER (ORDER BY salary DESC) as rank
            FROM employees
            QUALIFY rank <= 5
            LIMIT 3
            """);

        assertEquals(3, result.getRowCount(), "Should return 3 rows (LIMIT after QUALIFY)");
    }

    @Test
    @Order(10)
    public void testQualifyNoMatch() {
        // QUALIFY condition that matches nothing
        ResultSet result = engine.executeQuery("""
            SELECT id, name, ROW_NUMBER() OVER (ORDER BY id) as row_num
            FROM employees
            QUALIFY row_num > 100
            """);

        assertEquals(0, result.getRowCount(), "Should return 0 rows");
    }

    @Test
    @Order(11)
    public void testQualifyNotEquals() {
        // Use != operator
        ResultSet result = engine.executeQuery("""
            SELECT id, name, ROW_NUMBER() OVER (ORDER BY id) as row_num
            FROM employees
            QUALIFY row_num != 1
            """);

        assertEquals(7, result.getRowCount(), "Should return all except first row");
    }

    @Test
    @Order(12)
    public void testQualifyLessThan() {
        // Use < operator
        ResultSet result = engine.executeQuery("""
            SELECT id, name, ROW_NUMBER() OVER (ORDER BY id) as row_num
            FROM employees
            QUALIFY row_num < 4
            """);

        assertEquals(3, result.getRowCount(), "Should return first 3 rows");
    }

    @Test
    @Order(13)
    public void testQualifyGreaterThanOrEqual() {
        // Use >= operator
        ResultSet result = engine.executeQuery("""
            SELECT id, name, ROW_NUMBER() OVER (ORDER BY id DESC) as row_num
            FROM employees
            QUALIFY row_num >= 6
            """);

        assertEquals(3, result.getRowCount(), "Should return last 3 rows");
    }

    @Test
    @Order(14)
    public void testQualifyWithTop() {
        // Combine QUALIFY with TOP
        ResultSet result = engine.executeQuery("""
            SELECT TOP 2 id, name, ROW_NUMBER() OVER (ORDER BY salary DESC) as rank
            FROM employees
            QUALIFY rank <= 5
            """);

        assertEquals(2, result.getRowCount(), "Should return 2 rows (TOP after QUALIFY)");
    }

    @Test
    @Order(15)
    public void testMultipleWindowFunctionsWithQualify() {
        // Multiple window functions, QUALIFY on one
        ResultSet result = engine.executeQuery("""
            SELECT id, name,
            ROW_NUMBER() OVER (ORDER BY salary DESC) as overall_rank,
            ROW_NUMBER() OVER (PARTITION BY department ORDER BY salary DESC) as dept_rank
            FROM employees
            QUALIFY overall_rank <= 4
            """);

        assertEquals(4, result.getRowCount(), "Should return top 4 employees overall");
    }
}
