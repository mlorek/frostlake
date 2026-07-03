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
 * Test set operators: UNION, INTERSECT, EXCEPT (and their ALL variants)
 */
public class SetOperatorsTest {

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
            engine.execute("DROP TABLE IF EXISTS set1");
            engine.execute("DROP TABLE IF EXISTS set2");
        } catch (final Exception e) {
            // Ignore errors
        }

        // Create test tables with some overlapping data
        engine.execute("CREATE TABLE set1 (id INT, value VARCHAR)");
        engine.execute("INSERT INTO set1 VALUES (1, 'A')");
        engine.execute("INSERT INTO set1 VALUES (2, 'B')");
        engine.execute("INSERT INTO set1 VALUES (3, 'C')");
        engine.execute("INSERT INTO set1 VALUES (3, 'C')"); // Duplicate
        engine.execute("INSERT INTO set1 VALUES (4, 'D')");

        engine.execute("CREATE TABLE set2 (id INT, value VARCHAR)");
        engine.execute("INSERT INTO set2 VALUES (3, 'C')");
        engine.execute("INSERT INTO set2 VALUES (4, 'D')");
        engine.execute("INSERT INTO set2 VALUES (5, 'E')");
        engine.execute("INSERT INTO set2 VALUES (6, 'F')");
    }

    // UNION tests
    @Test
    public void testUnion() {
        ResultSet result = engine.executeQuery("""
            SELECT id, value FROM set1
            UNION
            SELECT id, value FROM set2
            ORDER BY id
            """);

        // Should have 6 unique rows: (1,A), (2,B), (3,C), (4,D), (5,E), (6,F)
        assertEquals(6, result.getRows().size());
        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals(6L, result.getRows().get(5).getValue(0));
    }

    @Test
    public void testUnionAll() {
        ResultSet result = engine.executeQuery("""
            SELECT id, value FROM set1
            UNION ALL
            SELECT id, value FROM set2
            """);

        // Should have 9 rows total (5 from set1 + 4 from set2)
        assertEquals(9, result.getRows().size());
    }

    // INTERSECT tests
    @Test
    public void testIntersect() {
        ResultSet result = engine.executeQuery("""
            SELECT id, value FROM set1
            INTERSECT
            SELECT id, value FROM set2
            ORDER BY id
            """);

        // Should have 2 unique rows that appear in both: (3,C), (4,D)
        assertEquals(2, result.getRows().size());
        assertEquals(3L, result.getRows().get(0).getValue(0));
        assertEquals("C", result.getRows().get(0).getValue(1));
        assertEquals(4L, result.getRows().get(1).getValue(0));
        assertEquals("D", result.getRows().get(1).getValue(1));
    }

    @Test
    public void testIntersectAll() {
        ResultSet result = engine.executeQuery("""
            SELECT id, value FROM set1
            INTERSECT ALL
            SELECT id, value FROM set2
            ORDER BY id
            """);

        // set1 has: (3,C) twice, (4,D) once
        // set2 has: (3,C) once, (4,D) once
        // Result should have: (3,C) once (min of 2,1), (4,D) once (min of 1,1)
        assertEquals(2, result.getRows().size());
        assertEquals(3L, result.getRows().get(0).getValue(0));
        assertEquals(4L, result.getRows().get(1).getValue(0));
    }

    @Test
    public void testIntersectEmpty() {
        // No common rows
        ResultSet result = engine.executeQuery("""
            SELECT id, value FROM set1 WHERE id < 3
            INTERSECT
            SELECT id, value FROM set2 WHERE id > 4
            """);

        assertEquals(0, result.getRows().size());
    }

    // EXCEPT tests
    @Test
    public void testExcept() {
        ResultSet result = engine.executeQuery("""
            SELECT id, value FROM set1
            EXCEPT
            SELECT id, value FROM set2
            ORDER BY id
            """);

        // Should have rows from set1 that don't appear in set2: (1,A), (2,B)
        assertEquals(2, result.getRows().size());
        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals("A", result.getRows().get(0).getValue(1));
        assertEquals(2L, result.getRows().get(1).getValue(0));
        assertEquals("B", result.getRows().get(1).getValue(1));
    }

    @Test
    public void testExceptAll() {
        ResultSet result = engine.executeQuery("""
            SELECT id, value FROM set1
            EXCEPT ALL
            SELECT id, value FROM set2
            ORDER BY id
            """);

        // set1 has: (1,A), (2,B), (3,C) twice, (4,D)
        // set2 has: (3,C), (4,D)
        // Result: (1,A), (2,B), (3,C) once (2-1=1)
        assertEquals(3, result.getRows().size());
        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals(2L, result.getRows().get(1).getValue(0));
        assertEquals(3L, result.getRows().get(2).getValue(0));
    }

    @Test
    public void testExceptReversed() {
        ResultSet result = engine.executeQuery("""
            SELECT id, value FROM set2
            EXCEPT
            SELECT id, value FROM set1
            ORDER BY id
            """);

        // Should have rows from set2 that don't appear in set1: (5,E), (6,F)
        assertEquals(2, result.getRows().size());
        assertEquals(5L, result.getRows().get(0).getValue(0));
        assertEquals("E", result.getRows().get(0).getValue(1));
        assertEquals(6L, result.getRows().get(1).getValue(0));
        assertEquals("F", result.getRows().get(1).getValue(1));
    }

    // Complex queries with multiple set operators
    @Test
    public void testMultipleSetOperators() {
        // Create a third table
        engine.execute("CREATE TABLE set3 (id INT, value VARCHAR)");
        engine.execute("INSERT INTO set3 VALUES (3, 'C')");
        engine.execute("INSERT INTO set3 VALUES (7, 'G')");

        // INTERSECT binds tighter than UNION (Snowflake precedence), so this is
        // set1 UNION (set2 INTERSECT set3), NOT (set1 UNION set2) INTERSECT set3.
        ResultSet result = engine.executeQuery("""
            SELECT id, value FROM set1
            UNION
            SELECT id, value FROM set2
            INTERSECT
            SELECT id, value FROM set3
            ORDER BY id
            """);

        // set2 INTERSECT set3 = {(3,C)};  set1 UNION {(3,C)} = {(1,A), (2,B), (3,C), (4,D)}
        assertEquals(4, result.getRows().size());
        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals(2L, result.getRows().get(1).getValue(0));
        assertEquals(3L, result.getRows().get(2).getValue(0));
        assertEquals("C", result.getRows().get(2).getValue(1));
        assertEquals(4L, result.getRows().get(3).getValue(0));
    }

    @Test
    public void testSetOperatorsWithWhereClause() {
        ResultSet result = engine.executeQuery("""
            SELECT id, value FROM set1 WHERE id <= 3
            EXCEPT
            SELECT id, value FROM set2 WHERE id >= 3
            ORDER BY id
            """);

        // set1 WHERE id <= 3: (1,A), (2,B), (3,C) twice
        // set2 WHERE id >= 3: (3,C), (4,D), (5,E), (6,F)
        // Result: (1,A), (2,B)
        assertEquals(2, result.getRows().size());
        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals(2L, result.getRows().get(1).getValue(0));
    }

    @Test
    public void testIntersectWithLiterals() {
        ResultSet result = engine.executeQuery("""
            SELECT 1 AS num, 'A' AS letter
            UNION
            SELECT 2, 'B'
            INTERSECT
            SELECT 1, 'A'
            UNION
            SELECT 3, 'C'
            """);

        // First UNION: {(1,A), (2,B)}
        // INTERSECT with {(1,A)}: {(1,A)}
        // UNION with {(3,C)}: {(1,A), (3,C)}
        assertEquals(2, result.getRows().size());
    }

    @Test
    public void testExceptWithOrderByAndLimit() {
        ResultSet result = engine.executeQuery("""
            SELECT id, value FROM set1
            EXCEPT
            SELECT id, value FROM set2
            ORDER BY id DESC
            LIMIT 1
            """);

        // set1 EXCEPT set2: (1,A), (2,B)
        // ORDER BY id DESC: (2,B), (1,A)
        // LIMIT 1: (2,B)
        assertEquals(1, result.getRows().size());
        assertEquals(2L, result.getRows().get(0).getValue(0));
        assertEquals("B", result.getRows().get(0).getValue(1));
    }

    @Test
    public void testIntersectAllWithMultipleDuplicates() {
        // Create tables with more duplicates
        engine.execute("DROP TABLE IF EXISTS dupes1");
        engine.execute("DROP TABLE IF EXISTS dupes2");

        engine.execute("CREATE TABLE dupes1 (val INT)");
        engine.execute("INSERT INTO dupes1 VALUES (1)");
        engine.execute("INSERT INTO dupes1 VALUES (1)");
        engine.execute("INSERT INTO dupes1 VALUES (1)");
        engine.execute("INSERT INTO dupes1 VALUES (2)");

        engine.execute("CREATE TABLE dupes2 (val INT)");
        engine.execute("INSERT INTO dupes2 VALUES (1)");
        engine.execute("INSERT INTO dupes2 VALUES (1)");
        engine.execute("INSERT INTO dupes2 VALUES (3)");

        ResultSet result = engine.executeQuery("""
            SELECT val FROM dupes1
            INTERSECT ALL
            SELECT val FROM dupes2
            """);

        // dupes1 has 1 three times, dupes2 has 1 twice
        // Result should have 1 twice (min of 3,2)
        assertEquals(2, result.getRows().size());
        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals(1L, result.getRows().get(1).getValue(0));
    }

    @Test
    public void testExceptAllWithMultipleDuplicates() {
        // Create tables with more duplicates
        engine.execute("DROP TABLE IF EXISTS dupes1");
        engine.execute("DROP TABLE IF EXISTS dupes2");

        engine.execute("CREATE TABLE dupes1 (val INT)");
        engine.execute("INSERT INTO dupes1 VALUES (1)");
        engine.execute("INSERT INTO dupes1 VALUES (1)");
        engine.execute("INSERT INTO dupes1 VALUES (1)");
        engine.execute("INSERT INTO dupes1 VALUES (2)");

        engine.execute("CREATE TABLE dupes2 (val INT)");
        engine.execute("INSERT INTO dupes2 VALUES (1)");
        engine.execute("INSERT INTO dupes2 VALUES (1)");
        engine.execute("INSERT INTO dupes2 VALUES (3)");

        ResultSet result = engine.executeQuery("""
            SELECT val FROM dupes1
            EXCEPT ALL
            SELECT val FROM dupes2
            ORDER BY val
            """);

        // dupes1 has 1 three times and 2 once
        // dupes2 has 1 twice and 3 once
        // Result should have 1 once (3-2=1) and 2 once (1-0=1)
        assertEquals(2, result.getRows().size());
        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals(2L, result.getRows().get(1).getValue(0));
    }
}
