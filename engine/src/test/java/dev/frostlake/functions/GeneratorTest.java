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

package dev.frostlake.functions;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GENERATOR table function
 */
public class GeneratorTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setup() {
        engine = new DatabaseEngine();
    }

    @AfterEach
    public void teardown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testGeneratorWithRowCount() {
        // Generate 10 rows
        ResultSet result = engine.executeQuery("SELECT * FROM TABLE(GENERATOR(ROWCOUNT => 10))");

        assertEquals(10, result.getRowCount(), "Should generate 10 rows");

        // Verify SEQ column exists and values are sequential
        int expectedSeq = 0;
        while (result.next()) {
            Object seqValue = result.getValue("SEQ");
            assertNotNull(seqValue, "SEQ value should not be null");
            assertEquals((long) expectedSeq, seqValue, "SEQ should be sequential starting from 0");
            expectedSeq++;
        }
    }

    @Test
    public void testGeneratorWithZeroRows() {
        // Generate 0 rows
        ResultSet result = engine.executeQuery("SELECT * FROM TABLE(GENERATOR(ROWCOUNT => 0))");

        assertEquals(0, result.getRowCount(), "Should generate 0 rows");
    }

    @Test
    public void testGeneratorWithOneRow() {
        // Generate 1 row
        ResultSet result = engine.executeQuery("SELECT * FROM TABLE(GENERATOR(ROWCOUNT => 1))");

        assertEquals(1, result.getRowCount(), "Should generate 1 row");
        assertTrue(result.next());
        assertEquals(0L, result.getValue("SEQ"), "First SEQ should be 0");
    }

    @Test
    public void testGeneratorWithLargeRowCount() {
        // Generate 1000 rows
        ResultSet result = engine.executeQuery("SELECT * FROM TABLE(GENERATOR(ROWCOUNT => 1000))");

        assertEquals(1000, result.getRowCount(), "Should generate 1000 rows");

        // Verify last row has SEQ = 999
        int count = 0;
        long lastSeq = -1;
        while (result.next()) {
            lastSeq = (long) result.getValue("SEQ");
            count++;
        }
        assertEquals(1000, count);
        assertEquals(999L, lastSeq, "Last SEQ should be 999");
    }

    @Test
    public void testGeneratorWithAlias() {
        // Use alias for the table function
        ResultSet result = engine.executeQuery("SELECT g.SEQ FROM TABLE(GENERATOR(ROWCOUNT => 5)) g");

        assertEquals(5, result.getRowCount(), "Should generate 5 rows");

        int count = 0;
        while (result.next()) {
            assertNotNull(result.getValue("SEQ"));
            count++;
        }
        assertEquals(5, count);
    }

    @Test
    public void testGeneratorWithFilter() {
        // Generate rows and filter
        ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(GENERATOR(ROWCOUNT => 10)) WHERE SEQ < 5"
        );

        assertEquals(5, result.getRowCount(), "Should return 5 rows after filter");

        while (result.next()) {
            long seq = (long) result.getValue("SEQ");
            assertTrue(seq < 5, "All SEQ values should be less than 5");
        }
    }

    @Test
    public void testGeneratorWithOrderBy() {
        // Generate rows with explicit ORDER BY (though already ordered)
        ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(GENERATOR(ROWCOUNT => 5)) ORDER BY SEQ DESC"
        );

        assertEquals(5, result.getRowCount());

        // Verify descending order
        long prevSeq = Long.MAX_VALUE;
        while (result.next()) {
            long seq = (long) result.getValue("SEQ");
            assertTrue(seq < prevSeq, "SEQ should be in descending order");
            prevSeq = seq;
        }
    }

    @Test
    public void testGeneratorWithArithmetic() {
        // Use SEQ in arithmetic expressions
        ResultSet result = engine.executeQuery(
            "SELECT SEQ, SEQ * 10 AS scaled FROM TABLE(GENERATOR(ROWCOUNT => 5))"
        );

        assertEquals(5, result.getRowCount());

        while (result.next()) {
            long seq = (long) result.getValue("SEQ");
            Object scaledObj = result.getValue("scaled");
            assertNotNull(scaledObj);

            // Convert to long for comparison
            long scaled = ((Number) scaledObj).longValue();
            assertEquals(seq * 10, scaled, "Scaled value should be SEQ * 10");
        }
    }

    @Test
    public void testGeneratorInJoin() {
        // Create a table and join with GENERATOR
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE TABLE items (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO items VALUES (0, 'Zero'), (1, 'One'), (2, 'Two')");

        ResultSet result = engine.executeQuery("""
            SELECT items.name, g.SEQ
            FROM items
            JOIN TABLE(GENERATOR(ROWCOUNT => 3)) g ON items.id = g.SEQ
            """);

        assertEquals(3, result.getRowCount(), "Should have 3 matching rows");

        // Verify join results
        while (result.next()) {
            String name = (String) result.getValue("name");
            long seq = (long) result.getValue("SEQ");
            assertNotNull(name);
            assertTrue(seq >= 0 && seq <= 2);
        }
    }

    @Test
    public void testGeneratorWithTimeLimit() {
        // Generate rows for 1 second (should generate many rows)
        long startTime = System.currentTimeMillis();
        ResultSet result = engine.executeQuery("SELECT * FROM TABLE(GENERATOR(TIMELIMIT => 1))");
        long endTime = System.currentTimeMillis();

        // Should take approximately 1 second (with some tolerance for CI/CD environments)
        long duration = endTime - startTime;
        assertTrue(duration >= 900 && duration <= 3000,
            "Duration should be approximately 1 second, was " + duration + "ms");

        // Should generate at least some rows
        assertTrue(result.getRowCount() > 0, "Should generate at least some rows");

        // Verify SEQ values start from 0
        if (result.next()) {
            assertEquals(0L, result.getValue("SEQ"), "First SEQ should be 0");
        }
    }

    @Test
    public void testGeneratorWithZeroTimeLimit() {
        // Generate rows for 0 seconds
        ResultSet result = engine.executeQuery("SELECT * FROM TABLE(GENERATOR(TIMELIMIT => 0))");

        // Should generate 0 or very few rows
        assertTrue(result.getRowCount() >= 0, "Should not error with TIMELIMIT => 0");
    }

    @Test
    public void testGeneratorMissingArgument() {
        // Try to call GENERATOR without arguments
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            engine.executeQuery("SELECT * FROM TABLE(GENERATOR())");
        });
        assertTrue(exception.getMessage().toLowerCase().contains("argument") ||
                   exception.getMessage().toLowerCase().contains("rowcount") ||
                   exception.getMessage().toLowerCase().contains("timelimit"));
    }

    @Test
    public void testGeneratorInvalidArgument() {
        // Try to call GENERATOR with invalid argument
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            engine.executeQuery("SELECT * FROM TABLE(GENERATOR(INVALID => 10))");
        });
        assertTrue(exception.getMessage().toLowerCase().contains("invalid") ||
                   exception.getMessage().toLowerCase().contains("argument"));
    }

    @Test
    public void testGeneratorNegativeRowCount() {
        // Try to use negative ROWCOUNT
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            engine.executeQuery("SELECT * FROM TABLE(GENERATOR(ROWCOUNT => -5))");
        });
        assertTrue(exception.getMessage().toLowerCase().contains("negative") ||
                   exception.getMessage().toLowerCase().contains("non-negative"));
    }

    @Test
    public void testGeneratorAsSubquery() {
        // Use GENERATOR in a subquery
        ResultSet result = engine.executeQuery(
            "SELECT COUNT(*) AS total FROM (SELECT * FROM TABLE(GENERATOR(ROWCOUNT => 100)))"
        );

        assertTrue(result.next());
        Object totalObj = result.getValue("total");
        long total = ((Number) totalObj).longValue();
        assertEquals(100L, total, "Subquery should return 100 rows");
    }

    @Test
    public void testGeneratorMultipleReferences() {
        // Use GENERATOR multiple times in one query (CROSS JOIN)
        ResultSet result = engine.executeQuery("""
            SELECT g1.SEQ AS seq1, g2.SEQ AS seq2
            FROM TABLE(GENERATOR(ROWCOUNT => 3)) g1, TABLE(GENERATOR(ROWCOUNT => 2)) g2
            """);

        // 3 * 2 = 6 combinations
        assertEquals(6, result.getRowCount(), "Should have 6 rows from cross join");
    }
}
