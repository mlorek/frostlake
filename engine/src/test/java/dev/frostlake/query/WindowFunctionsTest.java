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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for window functions ROW_NUMBER, RANK, and DENSE_RANK
 */
public class WindowFunctionsTest {

    private static DatabaseEngine engine;

    @BeforeAll
    public static void setup() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA PUBLIC");

        // Create test table with some ties in salary
        engine.execute("CREATE TABLE employees (id INTEGER, name VARCHAR, salary INTEGER)");

        // Insert test data with ties
        engine.execute("INSERT INTO employees VALUES (1, 'Alice', 100000)");
        engine.execute("INSERT INTO employees VALUES (2, 'Bob', 95000)");
        engine.execute("INSERT INTO employees VALUES (3, 'Charlie', 95000)"); // Tie with Bob
        engine.execute("INSERT INTO employees VALUES (4, 'David', 90000)");
        engine.execute("INSERT INTO employees VALUES (5, 'Eve', 85000)");
    }

    @AfterAll
    public static void teardown() {
        engine.shutdown();
    }

    @Test
    public void denseRankMultiKeyOrderByQualifiedAndCast() {
        // A multi-key window ORDER BY whose keys are QUALIFIED (employees.salary) and a CAST
        // (employees.name::VARCHAR): every key must participate in the ordering and tie-break, not just
        // the first bare column. Bob and Charlie tie on salary (95000); the name key separates them.
        ResultSet result = engine.executeQuery("""
            SELECT name,
                   DENSE_RANK() OVER (ORDER BY employees.salary DESC, employees.name::VARCHAR) AS rnk
            FROM employees
            ORDER BY rnk
            """);

        assertEquals(5, result.getRowCount());
        assertEquals("Alice", result.getRows().get(0).getValue(0));
        assertEquals(1L, ((Number) result.getRows().get(0).getValue(1)).longValue());
        assertEquals("Bob", result.getRows().get(1).getValue(0));
        assertEquals(2L, ((Number) result.getRows().get(1).getValue(1)).longValue());
        assertEquals("Charlie", result.getRows().get(2).getValue(0));
        assertEquals(3L, ((Number) result.getRows().get(2).getValue(1)).longValue());
        assertEquals("David", result.getRows().get(3).getValue(0));
        assertEquals(4L, ((Number) result.getRows().get(3).getValue(1)).longValue());
        assertEquals("Eve", result.getRows().get(4).getValue(0));
        assertEquals(5L, ((Number) result.getRows().get(4).getValue(1)).longValue());
    }

    @Test
    public void testRowNumber() {
        // ROW_NUMBER assigns unique sequential numbers even with ties
        ResultSet result = engine.executeQuery("""
            SELECT name, salary, ROW_NUMBER() OVER (ORDER BY salary DESC) as row_num
            FROM employees
            """);

        assertEquals(5, result.getRowCount());

        // Check that row numbers are sequential: 1, 2, 3, 4, 5
        Row row1 = result.getRows().get(0);
        assertEquals("Alice", row1.getValue(0));
        assertEquals(100000L, row1.getValue(1));
        assertEquals(1L, row1.getValue(2), "Alice should be row 1");

        // Bob and Charlie have same salary, but should get different row numbers (2 and 3)
        Row row2 = result.getRows().get(1);
        long rowNum2 = (Long) row2.getValue(2);
        assertTrue(rowNum2 == 2L || rowNum2 == 3L, "Bob should be row 2 or 3");

        Row row3 = result.getRows().get(2);
        long rowNum3 = (Long) row3.getValue(2);
        assertTrue(rowNum3 == 2L || rowNum3 == 3L, "Charlie should be row 2 or 3");
        assertNotEquals(rowNum2, rowNum3, "Bob and Charlie should have different row numbers");

        Row row4 = result.getRows().get(3);
        assertEquals("David", row4.getValue(0));
        assertEquals(4L, row4.getValue(2), "David should be row 4");

        Row row5 = result.getRows().get(4);
        assertEquals("Eve", row5.getValue(0));
        assertEquals(5L, row5.getValue(2), "Eve should be row 5");
    }

    @Test
    public void testRank() {
        // RANK assigns same rank to tied values, with gaps
        ResultSet result = engine.executeQuery("""
            SELECT name, salary, RANK() OVER (ORDER BY salary DESC) as rank
            FROM employees
            """);

        assertEquals(5, result.getRowCount());

        // Expected ranks: Alice=1, Bob=2, Charlie=2, David=4, Eve=5
        Row row1 = result.getRows().get(0);
        assertEquals("Alice", row1.getValue(0));
        assertEquals(1L, row1.getValue(2), "Alice should have rank 1");

        // Bob and Charlie should both have rank 2
        Row row2 = result.getRows().get(1);
        Row row3 = result.getRows().get(2);

        // Either could be first, but both should have rank 2
        long rank2 = (Long) row2.getValue(2);
        long rank3 = (Long) row3.getValue(2);
        assertEquals(2L, rank2, "Second row should have rank 2");
        assertEquals(2L, rank3, "Third row should have rank 2");

        // David should have rank 4 (skipping 3 because of the tie)
        Row row4 = result.getRows().get(3);
        assertEquals("David", row4.getValue(0));
        assertEquals(4L, row4.getValue(2), "David should have rank 4 (gap after tie)");

        // Eve should have rank 5
        Row row5 = result.getRows().get(4);
        assertEquals("Eve", row5.getValue(0));
        assertEquals(5L, row5.getValue(2), "Eve should have rank 5");
    }

    @Test
    public void testDenseRank() {
        // DENSE_RANK assigns same rank to tied values, without gaps
        ResultSet result = engine.executeQuery("""
            SELECT name, salary, DENSE_RANK() OVER (ORDER BY salary DESC) as dense_rank
            FROM employees
            """);

        assertEquals(5, result.getRowCount());

        // Expected dense ranks: Alice=1, Bob=2, Charlie=2, David=3, Eve=4
        Row row1 = result.getRows().get(0);
        assertEquals("Alice", row1.getValue(0));
        assertEquals(1L, row1.getValue(2), "Alice should have dense_rank 1");

        // Bob and Charlie should both have dense_rank 2
        Row row2 = result.getRows().get(1);
        Row row3 = result.getRows().get(2);

        long denseRank2 = (Long) row2.getValue(2);
        long denseRank3 = (Long) row3.getValue(2);
        assertEquals(2L, denseRank2, "Second row should have dense_rank 2");
        assertEquals(2L, denseRank3, "Third row should have dense_rank 2");

        // David should have dense_rank 3 (no gap)
        Row row4 = result.getRows().get(3);
        assertEquals("David", row4.getValue(0));
        assertEquals(3L, row4.getValue(2), "David should have dense_rank 3 (no gap)");

        // Eve should have dense_rank 4
        Row row5 = result.getRows().get(4);
        assertEquals("Eve", row5.getValue(0));
        assertEquals(4L, row5.getValue(2), "Eve should have dense_rank 4");
    }

    @Test
    public void testAllThreeWindowFunctions() {
        // Compare all three window functions side by side
        ResultSet result = engine.executeQuery("""
            SELECT name, salary,
            ROW_NUMBER() OVER (ORDER BY salary DESC) as row_num,
            RANK() OVER (ORDER BY salary DESC) as rank,
            DENSE_RANK() OVER (ORDER BY salary DESC) as dense_rank
            FROM employees
            """);

        assertEquals(5, result.getRowCount());

        // Alice: row_num=1, rank=1, dense_rank=1
        Row alice = result.getRows().get(0);
        assertEquals("Alice", alice.getValue(0));
        assertEquals(1L, alice.getValue(2), "Alice row_num");
        assertEquals(1L, alice.getValue(3), "Alice rank");
        assertEquals(1L, alice.getValue(4), "Alice dense_rank");

        // Check that the tied rows (Bob and Charlie) have correct values
        // They should have: row_num=2,3 (different), rank=2,2 (same), dense_rank=2,2 (same)
        Row tied1 = result.getRows().get(1);
        Row tied2 = result.getRows().get(2);

        // Check ranks are both 2
        assertEquals(2L, tied1.getValue(3), "First tied row rank");
        assertEquals(2L, tied2.getValue(3), "Second tied row rank");

        // Check dense ranks are both 2
        assertEquals(2L, tied1.getValue(4), "First tied row dense_rank");
        assertEquals(2L, tied2.getValue(4), "Second tied row dense_rank");

        // Check row numbers are different (2 and 3)
        long rowNum1 = (Long) tied1.getValue(2);
        long rowNum2 = (Long) tied2.getValue(2);
        assertTrue((rowNum1 == 2L && rowNum2 == 3L) || (rowNum1 == 3L && rowNum2 == 2L),
            "Tied rows should have different row numbers");

        // David: row_num=4, rank=4 (gap), dense_rank=3 (no gap)
        Row david = result.getRows().get(3);
        assertEquals("David", david.getValue(0));
        assertEquals(4L, david.getValue(2), "David row_num");
        assertEquals(4L, david.getValue(3), "David rank (with gap)");
        assertEquals(3L, david.getValue(4), "David dense_rank (no gap)");
    }
}
