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

/**
 * Tests for new expression features:
 * - IS NULL / IS NOT NULL
 * - LIKE / ILIKE pattern matching
 * - NOT operator
 * - || concatenation operator
 */
public class ExpressionFeaturesTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE employees (id INTEGER, name VARCHAR, email VARCHAR, salary INTEGER)");
        engine.execute("INSERT INTO employees VALUES (1, 'John Doe', 'john@example.com', 50000)");
        engine.execute("INSERT INTO employees VALUES (2, 'Jane Smith', NULL, 60000)");
        engine.execute("INSERT INTO employees VALUES (3, 'Bob Wilson', 'bob@test.org', NULL)");
        engine.execute("INSERT INTO employees VALUES (4, 'Alice Brown', NULL, NULL)");
    }

    @Test
    public void testIsNull() {
        final ResultSet rs = engine.executeQuery("SELECT name FROM employees WHERE email IS NULL ORDER BY id");
        assertEquals(2, rs.getRowCount());
        assertEquals("Jane Smith", rs.getRows().get(0).getValue(0));
        assertEquals("Alice Brown", rs.getRows().get(1).getValue(0));
    }

    @Test
    public void testIsNotNull() {
        final ResultSet rs = engine.executeQuery("SELECT name FROM employees WHERE email IS NOT NULL ORDER BY id");
        assertEquals(2, rs.getRowCount());
        assertEquals("John Doe", rs.getRows().get(0).getValue(0));
        assertEquals("Bob Wilson", rs.getRows().get(1).getValue(0));
    }

    @Test
    public void testIsNullInSelect() {
        final ResultSet rs = engine.executeQuery("SELECT name, email IS NULL as has_no_email FROM employees ORDER BY id");
        assertEquals(4, rs.getRowCount());
        assertEquals(false, rs.getRows().get(0).getValue(1));
        assertEquals(true, rs.getRows().get(1).getValue(1));
        assertEquals(false, rs.getRows().get(2).getValue(1));
        assertEquals(true, rs.getRows().get(3).getValue(1));
    }

    @Test
    public void testLikeSimplePattern() {
        final ResultSet rs = engine.executeQuery("SELECT name FROM employees WHERE email LIKE '%@example.com'");
        assertEquals(1, rs.getRowCount());
        assertEquals("John Doe", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testLikeWildcards() {
        final ResultSet rs = engine.executeQuery("SELECT name FROM employees WHERE name LIKE 'J___ ___'");
        assertEquals(1, rs.getRowCount());
        assertEquals("John Doe", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testLikeMultipleWildcards() {
        final ResultSet rs = engine.executeQuery("SELECT name FROM employees WHERE email LIKE '%@%'");
        assertEquals(2, rs.getRowCount());
    }

    @Test
    public void testNotLike() {
        final ResultSet rs = engine.executeQuery("""
            SELECT name FROM employees WHERE email NOT LIKE '%@example.com' AND email IS NOT NULL
            """);
        assertEquals(1, rs.getRowCount());
        assertEquals("Bob Wilson", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testILikeCaseInsensitive() {
        final ResultSet rs = engine.executeQuery("SELECT name FROM employees WHERE name ILIKE 'JOHN%'");
        assertEquals(1, rs.getRowCount());
        assertEquals("John Doe", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testILikeVsLike() {
        // ILIKE should match case-insensitively
        final ResultSet rs1 = engine.executeQuery("SELECT name FROM employees WHERE name ILIKE 'ALICE%'");
        assertEquals(1, rs1.getRowCount());

        // LIKE should not match due to case
        final ResultSet rs2 = engine.executeQuery("SELECT name FROM employees WHERE name LIKE 'ALICE%'");
        assertEquals(0, rs2.getRowCount());
    }

    @Test
    public void testNotILike() {
        final ResultSet rs = engine.executeQuery("SELECT name FROM employees WHERE name NOT ILIKE 'john%'");
        assertEquals(3, rs.getRowCount());
    }

    @Test
    public void testConcatenationOperator() {
        final ResultSet rs = engine.executeQuery("""
            SELECT name || ' - ' || 'Employee' as full_name FROM employees WHERE id = 1
            """);
        assertEquals(1, rs.getRowCount());
        assertEquals("John Doe - Employee", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testConcatenationMultiple() {
        final ResultSet rs = engine.executeQuery("""
            SELECT 'ID: ' || id || ', Name: ' || name as info FROM employees WHERE id = 1
            """);
        assertEquals(1, rs.getRowCount());
        assertEquals("ID: 1, Name: John Doe", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testConcatenationWithNull() {
        final ResultSet rs = engine.executeQuery("SELECT name || email as concat FROM employees WHERE id = 2");
        assertEquals(1, rs.getRowCount());
        assertNull(rs.getRows().get(0).getValue(0)); // NULL || anything = NULL in Snowflake
    }

    @Test
    public void testNotOperator() {
        // NOT (salary > 55000) under Snowflake three-valued logic:
        // - John (50000):  NOT (50000 > 55000) = NOT false   = true    -> included
        // - Jane (60000):  NOT (60000 > 55000) = NOT true    = false   -> excluded
        // - Bob (NULL):    NOT (NULL  > 55000) = NOT UNKNOWN = UNKNOWN -> excluded
        // - Alice (NULL):  NOT (NULL  > 55000) = NOT UNKNOWN = UNKNOWN -> excluded
        final ResultSet rs = engine.executeQuery("SELECT name FROM employees WHERE NOT (salary > 55000) ORDER BY id");
        assertEquals(1, rs.getRowCount());
        assertEquals("John Doe", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testNotWithIsNull() {
        final ResultSet rs = engine.executeQuery("SELECT name FROM employees WHERE NOT (email IS NULL)");
        assertEquals(2, rs.getRowCount());
    }

    @Test
    public void testNotWithComparison() {
        final ResultSet rs = engine.executeQuery("SELECT name FROM employees WHERE NOT id = 1 AND NOT id = 2");
        assertEquals(2, rs.getRowCount());
    }

    @Test
    public void testCombinedExpressions() {
        final ResultSet rs = engine.executeQuery("""
            SELECT name || ' <' || email || '>' as contact
            FROM employees
            WHERE email IS NOT NULL AND email LIKE '%@example.com'
            """);
        assertEquals(1, rs.getRowCount());
        assertEquals("John Doe <john@example.com>", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testLikeEscapeClause() {
        engine.execute("CREATE TABLE patterns (id INTEGER, text VARCHAR)");
        engine.execute("INSERT INTO patterns VALUES (1, '50% off')");
        engine.execute("INSERT INTO patterns VALUES (2, '50 dollars')");
        engine.execute("INSERT INTO patterns VALUES (3, '100% guaranteed')");

        // Match literal % using ESCAPE. The string-literal decode consumes single backslashes
        // ('\%' -> '%'), so the escape must be written doubled: SQL '%\\%%' ESCAPE '\\' — the
        // decoded pattern is %\%% with escape \.
        final ResultSet rs = engine.executeQuery("SELECT text FROM patterns WHERE text LIKE '%\\\\%%' ESCAPE '\\\\'");
        assertEquals(2, rs.getRowCount());
    }

    @Test
    public void testComplexWhereClause() {
        final ResultSet rs = engine.executeQuery("""
            SELECT name FROM employees
            WHERE (salary IS NULL OR salary > 55000)
            AND name NOT LIKE '%Doe'
            """);
        assertEquals(3, rs.getRowCount());
    }

    @Test
    public void testIsNullWithAnd() {
        final ResultSet rs = engine.executeQuery(
            "SELECT name FROM employees WHERE email IS NULL AND salary IS NULL"
        );
        assertEquals(1, rs.getRowCount());
        assertEquals("Alice Brown", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testLikeInSelectExpression() {
        final ResultSet rs = engine.executeQuery(
            "SELECT name, name LIKE 'J%' as starts_with_j FROM employees ORDER BY id"
        );
        assertEquals(4, rs.getRowCount());
        assertEquals(true, rs.getRows().get(0).getValue(1));
        assertEquals(true, rs.getRows().get(1).getValue(1));
        assertEquals(false, rs.getRows().get(2).getValue(1));
        assertEquals(false, rs.getRows().get(3).getValue(1));
    }
}
