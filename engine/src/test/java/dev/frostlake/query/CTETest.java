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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for Common Table Expressions (CTEs) using WITH clause
 */
public class CTETest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(CTETest.class);

    @Override
    protected void setupTest() {
        // Create test tables
        engine.execute("CREATE TABLE employees (id INTEGER, name VARCHAR, dept_id INTEGER, salary INTEGER)");
        engine.execute("INSERT INTO employees VALUES (1, 'Alice', 10, 50000)");
        engine.execute("INSERT INTO employees VALUES (2, 'Bob', 20, 60000)");
        engine.execute("INSERT INTO employees VALUES (3, 'Charlie', 10, 55000)");
        engine.execute("INSERT INTO employees VALUES (4, 'Diana', 30, 70000)");

        engine.execute("CREATE TABLE departments (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO departments VALUES (10, 'Engineering')");
        engine.execute("INSERT INTO departments VALUES (20, 'Sales')");
        engine.execute("INSERT INTO departments VALUES (30, 'Marketing')");
    }

    @Test
    public void testSimpleCTE() {
        logger.info("Testing simple CTE");

        ResultSet result = engine.executeQuery("""
            WITH high_earners AS (
                SELECT * FROM employees WHERE salary > 55000
            )
            SELECT * FROM high_earners
            """);

        assertNotNull(result);
        assertEquals(2, result.getRowCount());

        // Should have Bob (60000) and Diana (70000)
        logger.info("Simple CTE returned {} rows", result.getRowCount());
    }

    @Test
    public void testCTEWithJoin() {
        logger.info("Testing CTE with JOIN");

        ResultSet result = engine.executeQuery("""
            WITH eng_employees AS (
                SELECT * FROM employees WHERE dept_id = 10
            )
            SELECT e.name, d.name as dept_name
            FROM eng_employees e
            JOIN departments d ON e.dept_id = d.id
            """);

        assertNotNull(result);
        assertEquals(2, result.getRowCount());

        // Should have Alice and Charlie from Engineering
        logger.info("CTE with JOIN returned {} rows", result.getRowCount());
    }

    @Test
    public void testMultipleCTEs() {
        logger.info("Testing multiple CTEs");

        ResultSet result = engine.executeQuery("""
            WITH
                high_earners AS (
                    SELECT * FROM employees WHERE salary > 55000
                ),
                engineering AS (
                    SELECT * FROM employees WHERE dept_id = 10
                )
            SELECT h.name, h.salary
            FROM high_earners h
            JOIN engineering e ON h.id = e.id
            """);

        assertNotNull(result);
        // No overlap between high earners (Bob 60k, Diana 70k) and engineering (Alice 50k, Charlie 55k)
        assertEquals(0, result.getRowCount());

        logger.info("Multiple CTEs returned {} rows", result.getRowCount());
    }

    @Test
    public void testCTEWithAggregation() {
        logger.info("Testing CTE with aggregation");

        ResultSet result = engine.executeQuery("""
            WITH dept_stats AS (
                SELECT dept_id, COUNT(*) as emp_count, AVG(salary) as avg_salary
                FROM employees
                GROUP BY dept_id
            )
            SELECT * FROM dept_stats WHERE emp_count > 1
            """);

        assertNotNull(result);
        assertEquals(1, result.getRowCount());

        // Only dept 10 has more than 1 employee (Alice and Charlie)
        logger.info("CTE with aggregation returned {} rows", result.getRowCount());
    }

    @Test
    public void testCTEReferencedMultipleTimes() {
        logger.info("Testing CTE referenced multiple times");

        ResultSet result = engine.executeQuery("""
            WITH high_salary AS (
                SELECT * FROM employees WHERE salary >= 60000
            )
            SELECT h1.name as name1, h2.name as name2
            FROM high_salary h1
            CROSS JOIN high_salary h2
            WHERE h1.id < h2.id
            """);

        assertNotNull(result);
        // Bob (60k) and Diana (70k) -> only one pair where id1 < id2
        assertEquals(1, result.getRowCount());

        logger.info("CTE referenced multiple times returned {} rows", result.getRowCount());
    }

    @Test
    public void testCTEWithOrderByLimit() {
        logger.info("Testing CTE with ORDER BY and LIMIT");

        ResultSet result = engine.executeQuery("""
            WITH sorted_employees AS (
                SELECT * FROM employees ORDER BY salary DESC
            )
            SELECT name, salary FROM sorted_employees LIMIT 2
            """);

        assertNotNull(result);
        assertEquals(2, result.getRowCount());

        // Should return Diana (70k) and Bob (60k)
        assertEquals("Diana", result.getRows().get(0).getValue(0));
        assertEquals("Bob", result.getRows().get(1).getValue(0));

        logger.info("CTE with ORDER BY and LIMIT returned {} rows", result.getRowCount());
    }

    @Test
    public void testCTEWithWhereClause() {
        logger.info("Testing CTE with WHERE clause in main query");

        ResultSet result = engine.executeQuery("""
            WITH all_employees AS (
                SELECT * FROM employees
            )
            SELECT name FROM all_employees WHERE dept_id = 20
            """);

        assertNotNull(result);
        assertEquals(1, result.getRowCount());
        assertEquals("Bob", result.getRows().get(0).getValue(0));

        logger.info("CTE with WHERE clause returned {} rows", result.getRowCount());
    }

    @Test
    public void testCTEWithJoinOnCTE() {
        logger.info("Testing CTE used in JOIN");

        ResultSet result = engine.executeQuery("""
            WITH high_earners AS (
                SELECT * FROM employees WHERE salary >= 60000
            )
            SELECT e.name, h.salary
            FROM employees e
            JOIN high_earners h ON e.dept_id = h.dept_id
            WHERE e.id <> h.id
            """);

        assertNotNull(result);
        // This will match employees in the same department as high earners (excluding self)
        logger.info("CTE used in JOIN returned {} rows", result.getRowCount());
    }

    @Test
    public void testNestedCTEQueries() {
        logger.info("Testing nested queries within CTE");

        ResultSet result = engine.executeQuery("""
            WITH dept_employees AS (
                SELECT e.id, e.name, e.dept_id, e.salary, d.name as dept_name
                FROM employees e
                JOIN departments d ON e.dept_id = d.id
            )
            SELECT dept_name, COUNT(*) as emp_count
            FROM dept_employees
            GROUP BY dept_name
            ORDER BY emp_count DESC
            """);

        assertNotNull(result);
        assertEquals(3, result.getRowCount());

        // Engineering should have 2 employees (most)
        assertEquals("Engineering", result.getRows().get(0).getValue(0));

        logger.info("Nested CTE queries returned {} rows", result.getRowCount());
    }

    @Test
    public void testCTEWithUnion() {
        logger.info("Testing CTE with UNION");

        ResultSet result = engine.executeQuery("""
            WITH
                eng_dept AS (SELECT * FROM employees WHERE dept_id = 10),
                sales_dept AS (SELECT * FROM employees WHERE dept_id = 20)
            SELECT name FROM eng_dept
            UNION ALL
            SELECT name FROM sales_dept
            """);

        assertNotNull(result);
        assertEquals(3, result.getRowCount());

        // Alice, Charlie from Engineering + Bob from Sales
        logger.info("CTE with UNION returned {} rows", result.getRowCount());
    }
}
