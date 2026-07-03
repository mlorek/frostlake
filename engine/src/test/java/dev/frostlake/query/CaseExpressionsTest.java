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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for CASE expressions (both simple and searched forms)
 */
public class CaseExpressionsTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("""
            CREATE TABLE employees (id INTEGER, name VARCHAR, dept VARCHAR, salary INTEGER, status VARCHAR)
            """);
        engine.execute("INSERT INTO employees VALUES (1, 'John Doe', 'Sales', 50000, 'active')");
        engine.execute("INSERT INTO employees VALUES (2, 'Jane Smith', 'IT', 60000, 'active')");
        engine.execute("INSERT INTO employees VALUES (3, 'Bob Wilson', 'Sales', 45000, 'inactive')");
        engine.execute("INSERT INTO employees VALUES (4, 'Alice Brown', 'IT', 75000, 'active')");
        engine.execute("INSERT INTO employees VALUES (5, 'Charlie Davis', 'HR', NULL, 'active')");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testSimpleCaseExpression() {
        ResultSet rs = engine.executeQuery("""
            SELECT name,
            CASE dept
              WHEN 'Sales' THEN 'Revenue'
              WHEN 'IT' THEN 'Technology'
              WHEN 'HR' THEN 'People'
              ELSE 'Other'
            END as dept_category
            FROM employees ORDER BY id
            """);

        assertEquals(5, rs.getRowCount());
        assertEquals("Revenue", rs.getRows().get(0).getValue(1));
        assertEquals("Technology", rs.getRows().get(1).getValue(1));
        assertEquals("Revenue", rs.getRows().get(2).getValue(1));
        assertEquals("Technology", rs.getRows().get(3).getValue(1));
        assertEquals("People", rs.getRows().get(4).getValue(1));
    }

    @Test
    public void testSimpleCaseWithoutElse() {
        ResultSet rs = engine.executeQuery("""
            SELECT name,
            CASE dept
              WHEN 'Sales' THEN 'Revenue'
              WHEN 'IT' THEN 'Technology'
            END as dept_category
            FROM employees WHERE id = 5
            """);

        assertEquals(1, rs.getRowCount());
        assertNull(rs.getRows().get(0).getValue(1)); // HR doesn't match, no ELSE, so NULL
    }

    @Test
    public void testSearchedCaseExpression() {
        ResultSet rs = engine.executeQuery("""
            SELECT name,
            CASE
              WHEN salary >= 70000 THEN 'High'
              WHEN salary >= 50000 THEN 'Medium'
              WHEN salary < 50000 THEN 'Low'
              ELSE 'Unknown'
            END as salary_level
            FROM employees ORDER BY id
            """);

        assertEquals(5, rs.getRowCount());
        assertEquals("Medium", rs.getRows().get(0).getValue(1)); // 50000
        assertEquals("Medium", rs.getRows().get(1).getValue(1)); // 60000
        assertEquals("Low", rs.getRows().get(2).getValue(1));    // 45000
        assertEquals("High", rs.getRows().get(3).getValue(1));   // 75000
        assertEquals("Unknown", rs.getRows().get(4).getValue(1)); // NULL
    }

    @Test
    public void testSearchedCaseWithComplexConditions() {
        ResultSet rs = engine.executeQuery("""
            SELECT name,
            CASE
              WHEN dept = 'IT' AND salary > 65000 THEN 'Senior Tech'
              WHEN dept = 'IT' THEN 'Tech'
              WHEN dept = 'Sales' AND status = 'active' THEN 'Active Sales'
              ELSE 'Other'
            END as employee_type
            FROM employees ORDER BY id
            """);

        assertEquals(5, rs.getRowCount());
        assertEquals("Active Sales", rs.getRows().get(0).getValue(1));
        assertEquals("Tech", rs.getRows().get(1).getValue(1));
        assertEquals("Other", rs.getRows().get(2).getValue(1)); // Sales but inactive
        assertEquals("Senior Tech", rs.getRows().get(3).getValue(1));
        assertEquals("Other", rs.getRows().get(4).getValue(1));
    }

    @Test
    public void testCaseInWhereClause() {
        ResultSet rs = engine.executeQuery("""
            SELECT name FROM employees
            WHERE CASE dept
              WHEN 'IT' THEN 1
              WHEN 'Sales' THEN 1
              ELSE 0
            END = 1
            ORDER BY id
            """);

        assertEquals(4, rs.getRowCount());
        assertEquals("John Doe", rs.getRows().get(0).getValue(0));
        assertEquals("Jane Smith", rs.getRows().get(1).getValue(0));
        assertEquals("Bob Wilson", rs.getRows().get(2).getValue(0));
        assertEquals("Alice Brown", rs.getRows().get(3).getValue(0));
    }

    @Test
    public void testNestedCaseExpressions() {
        ResultSet rs = engine.executeQuery("""
            SELECT name,
            CASE dept
              WHEN 'IT' THEN
                CASE
                  WHEN salary > 65000 THEN 'Senior IT'
                  ELSE 'Junior IT'
                END
              WHEN 'Sales' THEN 'Sales Team'
              ELSE 'Other'
            END as classification
            FROM employees ORDER BY id
            """);

        assertEquals(5, rs.getRowCount());
        assertEquals("Sales Team", rs.getRows().get(0).getValue(1));
        assertEquals("Junior IT", rs.getRows().get(1).getValue(1));
        assertEquals("Sales Team", rs.getRows().get(2).getValue(1));
        assertEquals("Senior IT", rs.getRows().get(3).getValue(1));
        assertEquals("Other", rs.getRows().get(4).getValue(1));
    }

    @Test
    public void testCaseWithNullHandling() {
        ResultSet rs = engine.executeQuery("""
            SELECT name,
            CASE
              WHEN salary IS NULL THEN 'No Salary'
              WHEN salary > 50000 THEN 'Good Salary'
              ELSE 'Standard Salary'
            END as salary_status
            FROM employees WHERE id IN (4, 5) ORDER BY id
            """);

        assertEquals(2, rs.getRowCount());
        assertEquals("Good Salary", rs.getRows().get(0).getValue(1));
        assertEquals("No Salary", rs.getRows().get(1).getValue(1));
    }

    @Test
    public void testCaseWithArithmetic() {
        ResultSet rs = engine.executeQuery("""
            SELECT name,
            CASE
              WHEN salary >= 60000 THEN salary * 1.1
              WHEN salary >= 50000 THEN salary * 1.05
              ELSE salary
            END as adjusted_salary
            FROM employees WHERE id <= 4 ORDER BY id
            """);

        assertEquals(4, rs.getRowCount());
        assertEquals(52500.0, ((Number) rs.getRows().get(0).getValue(1)).doubleValue(), 0.01);
        assertEquals(66000.0, ((Number) rs.getRows().get(1).getValue(1)).doubleValue(), 0.01);
        assertEquals(45000.0, ((Number) rs.getRows().get(2).getValue(1)).doubleValue(), 0.01);
        assertEquals(82500.0, ((Number) rs.getRows().get(3).getValue(1)).doubleValue(), 0.01);
    }

    @Test
    public void testCaseWithStringConcatenation() {
        ResultSet rs = engine.executeQuery("""
            SELECT
            CASE status
              WHEN 'active' THEN name || ' (Active)'
              WHEN 'inactive' THEN name || ' (Inactive)'
              ELSE name
            END as display_name
            FROM employees WHERE id <= 3 ORDER BY id
            """);

        assertEquals(3, rs.getRowCount());
        assertEquals("John Doe (Active)", rs.getRows().get(0).getValue(0));
        assertEquals("Jane Smith (Active)", rs.getRows().get(1).getValue(0));
        assertEquals("Bob Wilson (Inactive)", rs.getRows().get(2).getValue(0));
    }

    @Test
    public void testCaseWithLikeOperator() {
        ResultSet rs = engine.executeQuery("""
            SELECT name,
            CASE
              WHEN name LIKE 'J%' THEN 'Starts with J'
              WHEN name LIKE '%son' THEN 'Ends with son'
              ELSE 'Other'
            END as name_pattern
            FROM employees ORDER BY id
            """);

        assertEquals(5, rs.getRowCount());
        assertEquals("Starts with J", rs.getRows().get(0).getValue(1));
        assertEquals("Starts with J", rs.getRows().get(1).getValue(1));
        assertEquals("Ends with son", rs.getRows().get(2).getValue(1));
        assertEquals("Other", rs.getRows().get(3).getValue(1));
        assertEquals("Other", rs.getRows().get(4).getValue(1));
    }

    @Test
    public void testMultipleCaseExpressionsInSelect() {
        ResultSet rs = engine.executeQuery("""
            SELECT name,
            CASE dept WHEN 'IT' THEN 'Tech' ELSE 'Non-Tech' END as tech_status,
            CASE WHEN salary > 50000 THEN 'High' ELSE 'Standard' END as pay_level
            FROM employees WHERE id = 2
            """);

        assertEquals(1, rs.getRowCount());
        assertEquals("Tech", rs.getRows().get(0).getValue(1));
        assertEquals("High", rs.getRows().get(0).getValue(2));
    }

    @Test
    public void testCaseWithBetweenOperator() {
        ResultSet rs = engine.executeQuery("""
            SELECT name,
            CASE
              WHEN salary BETWEEN 40000 AND 50000 THEN 'Entry Level'
              WHEN salary BETWEEN 50001 AND 65000 THEN 'Mid Level'
              WHEN salary > 65000 THEN 'Senior Level'
              ELSE 'Unknown'
            END as level
            FROM employees ORDER BY id
            """);

        assertEquals(5, rs.getRowCount());
        assertEquals("Entry Level", rs.getRows().get(0).getValue(1));
        assertEquals("Mid Level", rs.getRows().get(1).getValue(1));
        assertEquals("Entry Level", rs.getRows().get(2).getValue(1));
        assertEquals("Senior Level", rs.getRows().get(3).getValue(1));
        assertEquals("Unknown", rs.getRows().get(4).getValue(1));
    }

    // TODO: ORDER BY with CASE expressions needs special handling in QueryExecutor
    // @Test
    // public void testCaseInOrderBy() {
    //     ResultSet rs = engine.executeQuery(
    //         "SELECT name FROM employees " +
    //         "ORDER BY CASE dept " +
    //         "  WHEN 'IT' THEN 1 " +
    //         "  WHEN 'Sales' THEN 2 " +
    //         "  WHEN 'HR' THEN 3 " +
    //         "  ELSE 4 " +
    //         "END, name"
    //     );
    //
    //     assertEquals(5, rs.getRowCount());
    //     // IT first (alphabetically: Alice, Jane), then Sales (Bob, John), then HR (Charlie)
    //     assertEquals("Alice Brown", rs.getRows().get(0).getValue(0));
    //     assertEquals("Jane Smith", rs.getRows().get(1).getValue(0));
    //     assertEquals("Bob Wilson", rs.getRows().get(2).getValue(0));
    //     assertEquals("John Doe", rs.getRows().get(3).getValue(0));
    //     assertEquals("Charlie Davis", rs.getRows().get(4).getValue(0));
    // }
}
