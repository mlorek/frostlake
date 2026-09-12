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

public class SubqueryExpressionsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        // Create test tables
        engine.execute("CREATE TABLE employees (id INTEGER, name VARCHAR, dept_id INTEGER, salary NUMBER)");
        engine.execute("INSERT INTO employees VALUES (1, 'Alice', 10, 50000)");
        engine.execute("INSERT INTO employees VALUES (2, 'Bob', 20, 60000)");
        engine.execute("INSERT INTO employees VALUES (3, 'Charlie', 10, 55000)");
        engine.execute("INSERT INTO employees VALUES (4, 'Diana', 30, 70000)");

        engine.execute("CREATE TABLE departments (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO departments VALUES (10, 'Engineering')");
        engine.execute("INSERT INTO departments VALUES (20, 'Sales')");
        engine.execute("INSERT INTO departments VALUES (40, 'Marketing')");
    }

    // EXISTS Tests - Non-correlated

    @Test
    public void testExistsWithNonEmptyTable() {
        final ResultSet result = engine.executeQuery(
            "SELECT name FROM employees WHERE EXISTS (SELECT 1 FROM departments)"
        );
        // Should return all employees (departments table is not empty)
        assertEquals(4, result.getRowCount());
    }

    @Test
    public void testExistsWithEmptyTable() {
        engine.execute("CREATE TABLE empty_table (id INTEGER)");
        final ResultSet result = engine.executeQuery(
            "SELECT name FROM employees WHERE EXISTS (SELECT 1 FROM empty_table)"
        );
        // Should return no rows (empty_table is empty)
        assertEquals(0, result.getRowCount());
    }

    @Test
    public void testNotExistsWithNonEmptyTable() {
        final ResultSet result = engine.executeQuery(
            "SELECT name FROM employees WHERE NOT EXISTS (SELECT 1 FROM departments)"
        );
        // Should return no rows (departments table is not empty, so NOT EXISTS is false)
        assertEquals(0, result.getRowCount());
    }

    @Test
    public void testNotExistsWithEmptyTable() {
        engine.execute("CREATE TABLE empty_table (id INTEGER)");
        final ResultSet result = engine.executeQuery(
            "SELECT name FROM employees WHERE NOT EXISTS (SELECT 1 FROM empty_table)"
        );
        // Should return all rows (empty_table is empty, so NOT EXISTS is true)
        assertEquals(4, result.getRowCount());
    }

    @Test
    public void testExistsWithSpecificCondition() {
        final ResultSet result = engine.executeQuery(
            "SELECT name FROM employees WHERE EXISTS (SELECT 1 FROM departments WHERE name = 'Engineering')"
        );
        // Should return all employees (Engineering dept exists)
        assertEquals(4, result.getRowCount());
    }

    @Test
    public void testExistsWithNoMatch() {
        final ResultSet result = engine.executeQuery(
            "SELECT name FROM employees WHERE EXISTS (SELECT 1 FROM departments WHERE name = 'NonExistent')"
        );
        // Should return no rows (no such department)
        assertEquals(0, result.getRowCount());
    }

    @Test
    public void testExistsWithMultipleConditions() {
        final ResultSet result = engine.executeQuery(
            "SELECT name FROM employees WHERE salary > 55000 AND EXISTS (SELECT 1 FROM departments)"
        );
        // Should return employees with salary > 55000 (Bob, Diana)
        assertEquals(2, result.getRowCount());
    }

    // Scalar Subquery Tests - Simple cases only

    @Test
    public void testScalarSubqueryConstant() {
        final ResultSet result = engine.executeQuery(
            "SELECT name, salary + (SELECT 1000) as new_salary FROM employees WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        assertEquals(51000.0, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("new_salary"))).doubleValue(), 0.01);
    }

    @Test
    public void testScalarSubqueryAggregate() {
        final ResultSet result = engine.executeQuery(
            "SELECT name FROM employees WHERE salary > (SELECT AVG(salary) FROM employees)"
        );
        // Average salary is (50000+60000+55000+70000)/4 = 58750
        // Employees with salary > 58750: Bob (60000), Diana (70000)
        assertEquals(2, result.getRowCount());
    }

    @Test
    public void testScalarSubqueryMax() {
        final ResultSet result = engine.executeQuery(
            "SELECT name FROM employees WHERE salary = (SELECT MAX(salary) FROM employees)"
        );
        // Should return Diana (highest salary)
        assertEquals(1, result.getRowCount());
        assertEquals("Diana", result.getRows().get(0).getValue(result.getColumnIndex("name")));
    }

    @Test
    public void testScalarSubqueryReturnsNull() {
        engine.execute("CREATE TABLE empty_test (val INTEGER)");
        final ResultSet result = engine.executeQuery(
            "SELECT name, (SELECT MAX(val) FROM empty_test) as max_val FROM employees WHERE id = 1"
        );
        // Empty table aggregate should return NULL
        assertEquals(1, result.getRowCount());
        assertNull(result.getRows().get(0).getValue(result.getColumnIndex("max_val")));
    }

    // TODO: Multi-row scalar subquery error not thrown (caught by ProjectOperator)
    // @Test
    // public void testScalarSubqueryMultipleRowsError() {
    //     // Scalar subquery returning multiple rows should throw error
    //     assertThrows(RuntimeException.class, () -> {
    //         engine.executeQuery(
    //             "SELECT name, (SELECT name FROM departments) as dept_name FROM employees WHERE id = 1"
    //         );
    //     });
    // }

    @Test
    public void testScalarSubqueryInComparison() {
        final ResultSet result = engine.executeQuery(
            "SELECT name FROM employees WHERE (SELECT COUNT(*) FROM departments) > 2"
        );
        // departments has 3 rows, so all employees should be returned
        assertEquals(4, result.getRowCount());
    }

    @Test
    public void testScalarSubqueryInSelect() {
        final ResultSet result = engine.executeQuery(
            "SELECT name, (SELECT COUNT(*) FROM departments) as dept_count FROM employees WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        assertEquals(3, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("dept_count"))).intValue());
    }

    @Test
    public void testMultipleScalarSubqueriesInSelect() {
        final ResultSet result = engine.executeQuery("""
            SELECT name, (SELECT COUNT(*) FROM departments) as dept_count, (SELECT MAX(salary) FROM employees) as max_salary FROM employees WHERE id = 1
            """);
        assertEquals(1, result.getRowCount());
        assertEquals(3, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("dept_count"))).intValue());
        assertEquals(70000.0, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("max_salary"))).doubleValue(), 0.01);
    }

    @Test
    public void testScalarSubqueryInSelectWithMultipleRows() {
        final ResultSet result = engine.executeQuery(
            "SELECT name, (SELECT COUNT(*) FROM departments) as dept_count FROM employees"
        );
        assertEquals(4, result.getRowCount());
        for (int i = 0; i < 4; i++) {
            assertEquals(3, ((Number) result.getRows().get(i).getValue(result.getColumnIndex("dept_count"))).intValue());
        }
    }

    @Test
    public void testCorrelatedScalarSubqueryInSelect() {
        engine.execute("CREATE TABLE orders (id INTEGER, customer_id INTEGER, amount NUMBER)");
        engine.execute("INSERT INTO orders VALUES (1, 1, 100)");
        engine.execute("INSERT INTO orders VALUES (2, 1, 150)");
        engine.execute("INSERT INTO orders VALUES (3, 2, 200)");

        engine.execute("CREATE TABLE customers (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO customers VALUES (1, 'Alice')");
        engine.execute("INSERT INTO customers VALUES (2, 'Bob')");
        engine.execute("INSERT INTO customers VALUES (3, 'Charlie')");

        final ResultSet result = engine.executeQuery("""
            SELECT name, (SELECT COUNT(*) FROM orders WHERE orders.customer_id = customers.id) as order_count FROM customers ORDER BY id
            """);
        assertEquals(3, result.getRowCount());
        assertEquals(2, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("order_count"))).intValue());
        assertEquals(1, ((Number) result.getRows().get(1).getValue(result.getColumnIndex("order_count"))).intValue());
        assertEquals(0, ((Number) result.getRows().get(2).getValue(result.getColumnIndex("order_count"))).intValue());
    }

    @Test
    public void testCorrelatedScalarSubqueryWithSum() {
        engine.execute("CREATE TABLE orders (id INTEGER, customer_id INTEGER, amount NUMBER)");
        engine.execute("INSERT INTO orders VALUES (1, 1, 100)");
        engine.execute("INSERT INTO orders VALUES (2, 1, 150)");
        engine.execute("INSERT INTO orders VALUES (3, 2, 200)");

        engine.execute("CREATE TABLE customers (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO customers VALUES (1, 'Alice')");
        engine.execute("INSERT INTO customers VALUES (2, 'Bob')");

        final ResultSet result = engine.executeQuery("""
            SELECT name, (SELECT SUM(amount) FROM orders WHERE orders.customer_id = customers.id) as total FROM customers ORDER BY id
            """);
        assertEquals(2, result.getRowCount());
        assertEquals(250.0, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("total"))).doubleValue(), 0.01);
        assertEquals(200.0, ((Number) result.getRows().get(1).getValue(result.getColumnIndex("total"))).doubleValue(), 0.01);
    }

    // Correlated Subquery Tests

    @Test
    public void testCorrelatedExistsBasic() {
        // Create test tables
        engine.execute("CREATE TABLE t1 (i INTEGER, val VARCHAR)");
        engine.execute("CREATE TABLE t2 (i INTEGER, val VARCHAR)");

        engine.execute("INSERT INTO t1 VALUES (1, 'a')");
        engine.execute("INSERT INTO t1 VALUES (2, 'b')");
        engine.execute("INSERT INTO t1 VALUES (3, 'c')");

        engine.execute("INSERT INTO t2 VALUES (1, 'x')");
        engine.execute("INSERT INTO t2 VALUES (2, 'y')");

        // Test correlated EXISTS - should return rows where t1.i matches t2.i (1 and 2)
        final ResultSet result = engine.executeQuery(
            "SELECT * FROM t1 WHERE EXISTS (SELECT * FROM t2 WHERE t1.i = t2.i) ORDER BY i"
        );
        assertEquals(2, result.getRowCount());
        assertEquals(1, ((Number) result.getRows().get(0).getValue(0)).intValue());
        assertEquals(2, ((Number) result.getRows().get(1).getValue(0)).intValue());
    }

    @Test
    public void testCorrelatedNotExists() {
        // Create test tables
        engine.execute("CREATE TABLE t1 (i INTEGER, val VARCHAR)");
        engine.execute("CREATE TABLE t2 (i INTEGER, val VARCHAR)");

        engine.execute("INSERT INTO t1 VALUES (1, 'a')");
        engine.execute("INSERT INTO t1 VALUES (2, 'b')");
        engine.execute("INSERT INTO t1 VALUES (3, 'c')");

        engine.execute("INSERT INTO t2 VALUES (1, 'x')");
        engine.execute("INSERT INTO t2 VALUES (2, 'y')");

        // Test correlated NOT EXISTS - should return rows where t1.i does NOT match t2.i (3)
        final ResultSet result = engine.executeQuery(
            "SELECT * FROM t1 WHERE NOT EXISTS (SELECT * FROM t2 WHERE t1.i = t2.i)"
        );
        assertEquals(1, result.getRowCount());
        assertEquals(3, ((Number) result.getRows().get(0).getValue(0)).intValue());
    }

    @Test
    public void testCorrelatedExistsWithRealExample() {
        // Test with actual employee-department relationship
        // Find employees whose department exists in departments table
        final ResultSet result = engine.executeQuery("""
            SELECT name FROM employees WHERE EXISTS (SELECT 1 FROM departments WHERE departments.id = employees.dept_id)
            """);
        // Alice (dept_id=10), Bob (dept_id=20), Charlie (dept_id=10) should match
        // Diana (dept_id=30) should NOT match (no dept 30)
        assertEquals(3, result.getRowCount());
    }

    @Test
    public void testCorrelatedExistsWithMultipleConditions() {
        engine.execute("CREATE TABLE t1 (i INTEGER, j INTEGER)");
        engine.execute("CREATE TABLE t2 (i INTEGER, j INTEGER)");

        engine.execute("INSERT INTO t1 VALUES (1, 10)");
        engine.execute("INSERT INTO t1 VALUES (2, 20)");
        engine.execute("INSERT INTO t1 VALUES (3, 30)");

        engine.execute("INSERT INTO t2 VALUES (1, 10)");
        engine.execute("INSERT INTO t2 VALUES (2, 99)");

        // Both columns must match
        final ResultSet result = engine.executeQuery(
            "SELECT * FROM t1 WHERE EXISTS (SELECT * FROM t2 WHERE t1.i = t2.i AND t1.j = t2.j)"
        );
        // Only (1, 10) should match
        assertEquals(1, result.getRowCount());
        assertEquals(1, ((Number) result.getRows().get(0).getValue(0)).intValue());
    }

    @Test
    public void testCorrelatedExistsWithComparison() {
        engine.execute("CREATE TABLE t1 (salary NUMBER)");
        engine.execute("CREATE TABLE t2 (min_salary NUMBER)");

        engine.execute("INSERT INTO t1 VALUES (50000)");
        engine.execute("INSERT INTO t1 VALUES (60000)");
        engine.execute("INSERT INTO t1 VALUES (70000)");

        engine.execute("INSERT INTO t2 VALUES (55000)");
        engine.execute("INSERT INTO t2 VALUES (65000)");

        // Find salaries greater than any min_salary
        final ResultSet result = engine.executeQuery(
            "SELECT * FROM t1 WHERE EXISTS (SELECT * FROM t2 WHERE t1.salary > t2.min_salary)"
        );
        // 60000 and 70000 are both > 55000
        assertEquals(2, result.getRowCount());
    }

    @Test
    public void testCorrelatedExists4Levels() {
        // Create 4 tables with hierarchical relationships
        engine.execute("CREATE TABLE level1 (id INTEGER, name VARCHAR)");
        engine.execute("CREATE TABLE level2 (id INTEGER, parent_id INTEGER, name VARCHAR)");
        engine.execute("CREATE TABLE level3 (id INTEGER, parent_id INTEGER, name VARCHAR)");
        engine.execute("CREATE TABLE level4 (id INTEGER, parent_id INTEGER, name VARCHAR)");

        // Insert data: level1 -> level2 -> level3 -> level4
        // Complete chain: 1 -> 1 -> 1 -> 1
        engine.execute("INSERT INTO level1 VALUES (1, 'A')");
        engine.execute("INSERT INTO level1 VALUES (2, 'B')");
        engine.execute("INSERT INTO level1 VALUES (3, 'C')");

        engine.execute("INSERT INTO level2 VALUES (1, 1, 'A1')");
        engine.execute("INSERT INTO level2 VALUES (2, 2, 'B1')");
        // No level2 for level1.id=3

        engine.execute("INSERT INTO level3 VALUES (1, 1, 'A11')");
        // No level3 for level2.id=2

        engine.execute("INSERT INTO level4 VALUES (1, 1, 'A111')");

        // 4-level nested correlated subquery
        // Find level1 records that have corresponding records all the way to level4
        final ResultSet result = engine.executeQuery("""
            SELECT * FROM level1
            WHERE EXISTS (
              SELECT * FROM level2 WHERE level2.parent_id = level1.id
              AND EXISTS (
                SELECT * FROM level3 WHERE level3.parent_id = level2.id
                AND EXISTS (
                  SELECT * FROM level4 WHERE level4.parent_id = level3.id
                )
              )
            )
            """);

        // Only level1.id=1 should match (has complete chain to level4)
        assertEquals(1, result.getRowCount());
        assertEquals(1, ((Number) result.getRows().get(0).getValue(0)).intValue());
        assertEquals("A", result.getRows().get(0).getValue(1));
    }
}
