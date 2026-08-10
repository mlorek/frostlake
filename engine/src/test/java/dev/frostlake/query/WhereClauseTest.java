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
 * Tests for WHERE clause in SELECT queries
 */
public class WhereClauseTest extends BaseDatabaseTest {

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
    public void testWhereEquals() {
        final ResultSet result = engine.executeQuery(
            "SELECT * FROM employees WHERE department = 'Engineering'"
        );
        assertEquals(3, result.getRowCount());
    }

    @Test
    public void testWhereGreaterThan() {
        final ResultSet result = engine.executeQuery(
            "SELECT * FROM employees WHERE salary > 80000"
        );
        assertEquals(3, result.getRowCount());
    }

    @Test
    public void testWhereLessThanOrEqual() {
        final ResultSet result = engine.executeQuery(
            "SELECT * FROM employees WHERE age <= 30"
        );
        assertEquals(3, result.getRowCount());
    }

    @Test
    public void testWhereAnd() {
        final ResultSet result = engine.executeQuery(
            "SELECT * FROM employees WHERE department = 'Engineering' AND salary > 85000"
        );
        assertEquals(2, result.getRowCount());
    }

    @Test
    public void testWhereOr() {
        final ResultSet result = engine.executeQuery(
            "SELECT * FROM employees WHERE department = 'HR' OR age >= 35"
        );
        assertEquals(2, result.getRowCount());
    }

    @Test
    public void testWhereNotEqual() {
        final ResultSet result = engine.executeQuery(
            "SELECT * FROM employees WHERE department <> 'Engineering'"
        );
        assertEquals(3, result.getRowCount());
    }

    @Test
    public void testWhereComplexCondition() {
        final ResultSet result = engine.executeQuery(
            "SELECT * FROM employees WHERE (salary > 70000 AND age < 35) OR department = 'HR'"
        );
        assertTrue(result.getRowCount() >= 1);
    }

    @Test
    public void testWhereWithOrderBy() {
        final ResultSet result = engine.executeQuery(
            "SELECT * FROM employees WHERE salary > 70000 ORDER BY salary DESC"
        );
        assertEquals(4, result.getRowCount());
    }

    @Test
    public void testWhereWithGroupBy() {
        final ResultSet result = engine.executeQuery(
            "SELECT department, COUNT(*) FROM employees WHERE salary > 70000 GROUP BY department"
        );
        assertTrue(result.getRowCount() >= 1);
    }
}
