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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test JOIN operations and LATERAL keyword functionality
 */
public class JoinAndLateralTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("USE SCHEMA PUBLIC");
        engine.execute("CREATE TABLE employees (id INT, name VARCHAR, dept_id INT)");
        engine.execute("INSERT INTO employees VALUES (1, 'Alice', 10)");
        engine.execute("INSERT INTO employees VALUES (2, 'Bob', 20)");
        engine.execute("INSERT INTO employees VALUES (3, 'Charlie', 10)");
        engine.execute("CREATE TABLE departments (dept_id INT, dept_name VARCHAR)");
        engine.execute("INSERT INTO departments VALUES (10, 'Engineering')");
        engine.execute("INSERT INTO departments VALUES (20, 'Sales')");
        engine.execute("INSERT INTO departments VALUES (30, 'Marketing')");
    }

    @Test
    public void testCrossJoin() {
        final ResultSet rs = engine.executeQuery(
            "SELECT * FROM employees CROSS JOIN departments"
        );

        // 3 employees x 3 departments = 9 rows
        assertEquals(9, rs.getRowCount(), "CROSS JOIN should produce cartesian product");
        assertEquals(5, rs.getColumnCount(), "Result should have 5 columns (3 from employees + 2 from departments)");
    }

    @Test
    public void testInnerJoin() {
        final ResultSet rs = engine.executeQuery(
            "SELECT * FROM employees JOIN departments ON employees.dept_id = departments.dept_id"
                + " ORDER BY employees.id"
        );

        // Only employees with matching departments
        assertEquals(3, rs.getRowCount(), "INNER JOIN should return matching rows");

        // Verify the join matched correctly
        rs.next();
        assertEquals("Alice", rs.getValue("name"));
        assertEquals("Engineering", rs.getValue("dept_name"));
    }

    @Test
    public void testInnerJoinExplicit() {
        final ResultSet rs = engine.executeQuery(
            "SELECT * FROM employees INNER JOIN departments ON employees.dept_id = departments.dept_id"
        );

        assertEquals(3, rs.getRowCount(), "INNER JOIN should return matching rows");
    }

    @Test
    public void testLeftJoin() {
        // Add an employee with no department
        engine.execute("INSERT INTO employees VALUES (4, 'David', 99)");

        final ResultSet rs = engine.executeQuery(
            "SELECT * FROM employees LEFT JOIN departments ON employees.dept_id = departments.dept_id"
        );

        // All 4 employees, even one without matching department
        assertEquals(4, rs.getRowCount(), "LEFT JOIN should return all left rows");

        // Find David's row (should have NULL department)
        rs.reset();
        while (rs.next()) {
            if ("David".equals(rs.getValue("name"))) {
                assertNull(rs.getValue("dept_name"), "David should have NULL dept_name");
                break;
            }
        }
    }

    @Test
    public void testLeftOuterJoin() {
        // Add an employee with no department
        engine.execute("INSERT INTO employees VALUES (4, 'David', 99)");

        final ResultSet rs = engine.executeQuery(
            "SELECT * FROM employees LEFT OUTER JOIN departments ON employees.dept_id = departments.dept_id"
        );

        assertEquals(4, rs.getRowCount(), "LEFT OUTER JOIN should return all left rows");
    }

    @Test
    public void testLateralWithSubquery() {
        // LATERAL allows the subquery to reference columns from the left table
        final ResultSet rs = engine.executeQuery(
            "SELECT * FROM employees e, LATERAL (SELECT dept_name FROM departments d WHERE d.dept_id = e.dept_id) dept"
                + " ORDER BY e.id"
        );

        assertEquals(3, rs.getRowCount(), "LATERAL subquery should execute for each employee row");

        // Verify correct correlation
        rs.next();
        assertEquals("Alice", rs.getValue("name"));
        assertEquals("Engineering", rs.getValue("dept_name"));

        rs.next();
        assertEquals("Bob", rs.getValue("name"));
        assertEquals("Sales", rs.getValue("dept_name"));
    }

    @Test
    public void testLateralWithCrossJoin() {
        final ResultSet rs = engine.executeQuery("""
            SELECT * FROM employees e CROSS JOIN LATERAL (SELECT dept_name FROM departments d WHERE d.dept_id = e.dept_id) dept
            """);

        assertEquals(3, rs.getRowCount(), "LATERAL CROSS JOIN should work correctly");
    }

    @Test
    public void testMultipleJoins() {
        // Create a third table
        engine.execute("CREATE TABLE projects (proj_id INT, proj_name VARCHAR, emp_id INT)");
        engine.execute("INSERT INTO projects VALUES (100, 'Project A', 1)");
        engine.execute("INSERT INTO projects VALUES (200, 'Project B', 2)");

        final ResultSet rs = engine.executeQuery("""
            SELECT * FROM employees e
            JOIN departments d ON e.dept_id = d.dept_id
            JOIN projects p ON p.emp_id = e.id
            """);

        assertEquals(2, rs.getRowCount(), "Multi-join should return correct rows");
        assertEquals(8, rs.getColumnCount(), "Result should have columns from all tables");
    }

    @Test
    public void testJoinWithTableAliases() {
        final ResultSet rs = engine.executeQuery(
            "SELECT * FROM employees e JOIN departments d ON e.dept_id = d.dept_id"
        );

        assertEquals(3, rs.getRowCount(), "JOIN with aliases should work");
    }

    @Test
    public void testSubqueryInFrom() {
        final ResultSet rs = engine.executeQuery(
            "SELECT * FROM (SELECT * FROM employees WHERE dept_id = 10) e"
        );

        assertEquals(2, rs.getRowCount(), "Subquery in FROM should work");
    }

    @Test
    public void testJoinWithSubquery() {
        final ResultSet rs = engine.executeQuery(
            "SELECT * FROM employees e JOIN (SELECT * FROM departments WHERE dept_id < 30) d ON e.dept_id = d.dept_id"
        );

        assertEquals(3, rs.getRowCount(), "JOIN with subquery should work");
    }

    @Test
    public void testLateralWithFilter() {
        final ResultSet rs = engine.executeQuery("""
            SELECT * FROM employees e, LATERAL (SELECT dept_name FROM departments d WHERE d.dept_id = e.dept_id AND d.dept_id = 10) dept
            """);

        // Only employees in department 10 will have matches
        assertEquals(2, rs.getRowCount(), "LATERAL with additional filter should work");
    }

    @Test
    public void testJoinWithWhereClause() {
        final ResultSet rs = engine.executeQuery(
            "SELECT * FROM employees e JOIN departments d ON e.dept_id = d.dept_id WHERE e.name = 'Alice'"
        );

        assertEquals(1, rs.getRowCount(), "JOIN with WHERE should filter correctly");
        rs.next();
        assertEquals("Alice", rs.getValue("name"));
        assertEquals("Engineering", rs.getValue("dept_name"));
    }

    @Test
    public void testCrossJoinWithoutKeyword() {
        // Traditional comma-separated table syntax (equivalent to CROSS JOIN)
        final ResultSet rs = engine.executeQuery(
            "SELECT * FROM employees, departments"
        );

        assertEquals(9, rs.getRowCount(), "Comma syntax should produce cartesian product");
    }

    @Test
    public void testRightJoin() {
        // Add a department with no employees
        engine.execute("INSERT INTO departments VALUES (40, 'HR')");

        final ResultSet rs = engine.executeQuery(
            "SELECT * FROM employees e RIGHT JOIN departments d ON e.dept_id = d.dept_id"
        );

        // 3 matching rows (Alice+Eng, Bob+Sales, Charlie+Eng) + 2 unmatched depts (Marketing, HR) = 5 rows
        assertEquals(5, rs.getRowCount(), "RIGHT JOIN should return all matches plus unmatched right rows");

        // Find HR department row (should have NULL employee)
        rs.reset();
        boolean foundHR = false;
        while (rs.next()) {
            if ("HR".equals(rs.getValue("dept_name"))) {
                assertNull(rs.getValue("name"), "HR should have NULL employee name");
                foundHR = true;
                break;
            }
        }
        assertTrue(foundHR, "Should find HR department");
    }

    @Test
    public void testFullOuterJoin() {
        // Add employee with no department and department with no employees
        engine.execute("INSERT INTO employees VALUES (4, 'David', 99)");
        engine.execute("INSERT INTO departments VALUES (40, 'HR')");

        final ResultSet rs = engine.executeQuery(
            "SELECT * FROM employees e FULL OUTER JOIN departments d ON e.dept_id = d.dept_id"
        );

        // 3 matching (Alice+Eng, Bob+Sales, Charlie+Eng) + 1 unmatched employee (David) + 2 unmatched depts (Marketing, HR) = 6 rows
        assertEquals(6, rs.getRowCount(), "FULL OUTER JOIN should return all rows");
    }

    @Test
    public void testComplexJoinCondition() {
        final ResultSet rs = engine.executeQuery(
            "SELECT * FROM employees e JOIN departments d ON e.dept_id = d.dept_id AND d.dept_id >= 10"
        );

        assertEquals(3, rs.getRowCount(), "Complex join condition with AND should work");
    }

    @Test
    public void testJoinWithMultipleConditions() {
        final ResultSet rs = engine.executeQuery(
            "SELECT * FROM employees e JOIN departments d ON e.dept_id = d.dept_id AND e.dept_id < 30"
        );

        assertEquals(3, rs.getRowCount(), "JOIN with multiple conditions should work");
    }
}
