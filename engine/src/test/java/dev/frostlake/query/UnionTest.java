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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test UNION and UNION ALL operators
 */
public class UnionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
    }

    @BeforeEach
    public void setupData() {
        // Clean up and recreate tables for each test
        try {
            engine.execute("DROP TABLE IF EXISTS table1");
            engine.execute("DROP TABLE IF EXISTS table2");
        } catch (final Exception e) {
            // Ignore errors
        }

        // Create test tables
        engine.execute("CREATE TABLE table1 (id INT, name VARCHAR)");
        engine.execute("INSERT INTO table1 VALUES (1, 'Alice')");
        engine.execute("INSERT INTO table1 VALUES (2, 'Bob')");
        engine.execute("INSERT INTO table1 VALUES (3, 'Charlie')");

        engine.execute("CREATE TABLE table2 (id INT, name VARCHAR)");
        engine.execute("INSERT INTO table2 VALUES (3, 'Charlie')");
        engine.execute("INSERT INTO table2 VALUES (4, 'Diana')");
        engine.execute("INSERT INTO table2 VALUES (5, 'Eve')");
    }

    @Test
    public void testUnionAll() {
        // UNION ALL keeps all rows including duplicates
        final ResultSet result = engine.executeQuery("""
            SELECT id, name FROM table1
            UNION ALL
            SELECT id, name FROM table2
            """);

        // Should have 6 rows (3 from table1 + 3 from table2)
        assertEquals(6, result.getRows().size());
    }

    @Test
    public void testUnion() {
        // UNION removes duplicates
        final ResultSet result = engine.executeQuery("""
            SELECT id, name FROM table1
            UNION
            SELECT id, name FROM table2
            """);

        // Should have 5 unique rows (Charlie appears in both but counted once)
        assertEquals(5, result.getRows().size());
    }

    @Test
    public void testUnionWithOrderBy() {
        // UNION with ORDER BY at statement level
        final ResultSet result = engine.executeQuery("""
            SELECT id, name FROM table1
            UNION
            SELECT id, name FROM table2
            ORDER BY id
            """);

        assertEquals(5, result.getRows().size());

        // Check ordering: id should be 1, 2, 3, 4, 5
        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals(2L, result.getRows().get(1).getValue(0));
        assertEquals(3L, result.getRows().get(2).getValue(0));
        assertEquals(4L, result.getRows().get(3).getValue(0));
        assertEquals(5L, result.getRows().get(4).getValue(0));
    }

    @Test
    public void testUnionAllWithOrderBy() {
        // UNION ALL with ORDER BY
        final ResultSet result = engine.executeQuery("""
            SELECT id, name FROM table1
            UNION ALL
            SELECT id, name FROM table2
            ORDER BY id
            """);

        assertEquals(6, result.getRows().size());

        // Check ordering
        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals(2L, result.getRows().get(1).getValue(0));
        // Charlie appears twice (id=3)
        assertEquals(3L, result.getRows().get(2).getValue(0));
        assertEquals(3L, result.getRows().get(3).getValue(0));
        assertEquals(4L, result.getRows().get(4).getValue(0));
        assertEquals(5L, result.getRows().get(5).getValue(0));
    }

    @Test
    public void testUnionWithLimit() {
        // UNION with LIMIT at statement level
        final ResultSet result = engine.executeQuery("""
            SELECT id, name FROM table1
            UNION
            SELECT id, name FROM table2
            LIMIT 3
            """);

        // Should have only 3 rows
        assertEquals(3, result.getRows().size());
    }

    @Test
    public void testMultipleUnions() {
        // Create a third table
        engine.execute("CREATE TABLE table3 (id INT, name VARCHAR)");
        engine.execute("INSERT INTO table3 VALUES (6, 'Frank')");
        engine.execute("INSERT INTO table3 VALUES (7, 'Grace')");

        // Multiple UNIONs
        final ResultSet result = engine.executeQuery("""
            SELECT id, name FROM table1
            UNION ALL
            SELECT id, name FROM table2
            UNION ALL
            SELECT id, name FROM table3
            """);

        // Should have 8 rows (3 + 3 + 2)
        assertEquals(8, result.getRows().size());
    }

    @Test
    public void testUnionWithWhereClause() {
        // Each SELECT can have its own WHERE clause
        final ResultSet result = engine.executeQuery("""
            SELECT id, name FROM table1 WHERE id < 3
            UNION
            SELECT id, name FROM table2 WHERE id > 3
            ORDER BY id
            """);

        // table1 WHERE id < 3: Alice (1), Bob (2)
        // table2 WHERE id > 3: Diana (4), Eve (5)
        // Total: 4 rows
        assertEquals(4, result.getRows().size());
        assertEquals("Alice", result.getRows().get(0).getValue(1));
        assertEquals("Bob", result.getRows().get(1).getValue(1));
        assertEquals("Diana", result.getRows().get(2).getValue(1));
        assertEquals("Eve", result.getRows().get(3).getValue(1));
    }

    @Test
    public void testUnionWithDifferentLiterals() {
        // UNION of literal values
        final ResultSet result = engine.executeQuery("""
            SELECT 1 AS num, 'First' AS label
            UNION
            SELECT 2 AS num, 'Second' AS label
            UNION
            SELECT 1 AS num, 'First' AS label
            ORDER BY num
            """);

        // Should have 2 unique rows (duplicate removed)
        assertEquals(2, result.getRows().size());
        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals("First", result.getRows().get(0).getValue(1));
        assertEquals(2L, result.getRows().get(1).getValue(0));
        assertEquals("Second", result.getRows().get(1).getValue(1));
    }
}
