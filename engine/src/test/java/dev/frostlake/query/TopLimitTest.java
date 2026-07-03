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
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for TOP clause and LIMIT with OFFSET
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TopLimitTest {

    private static DatabaseEngine engine;

    @BeforeAll
    public static void setup() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA PUBLIC");

        // Create test table with more data for pagination testing
        engine.execute("CREATE TABLE numbers (id INTEGER, value INTEGER, category VARCHAR)");

        // Insert 20 rows
        for (int i = 1; i <= 20; i++) {
            String category = (i % 2 == 0) ? "even" : "odd";
            engine.execute(String.format("INSERT INTO numbers VALUES (%d, %d, '%s')", i, i * 10, category));
        }
    }

    @AfterAll
    public static void teardown() {
        engine.shutdown();
    }

    @Test
    @Order(1)
    public void testBasicLimit() {
        ResultSet result = engine.executeQuery("SELECT * FROM numbers ORDER BY id LIMIT 5");

        assertEquals(5, result.getRowCount(), "Should return 5 rows");
        result.reset();
        result.next();
        assertEquals(1L, result.getValue("id"), "First row should have id=1");

        // Check last row
        result.reset();
        for (int i = 0; i < result.getRowCount(); i++) {
            result.next();
        }
        assertEquals(5L, result.getValue("id"), "Last row should have id=5");
    }

    @Test
    @Order(2)
    public void testLimitWithOffset() {
        ResultSet result = engine.executeQuery("SELECT * FROM numbers ORDER BY id LIMIT 5 OFFSET 5");

        assertEquals(5, result.getRowCount(), "Should return 5 rows");
        result.reset();
        result.next();
        assertEquals(6L, result.getValue("id"), "First row should have id=6 (skipped first 5)");

        // Check last row
        result.reset();
        for (int i = 0; i < result.getRowCount(); i++) {
            result.next();
        }
        assertEquals(10L, result.getValue("id"), "Last row should have id=10");
    }

    @Test
    @Order(3)
    public void testLimitOffsetPagination() {
        // Page 1: rows 1-5
        ResultSet page1 = engine.executeQuery("SELECT * FROM numbers ORDER BY id LIMIT 5 OFFSET 0");
        assertEquals(5, page1.getRowCount());
        page1.reset();
        page1.next();
        assertEquals(1L, page1.getValue("id"));

        // Page 2: rows 6-10
        ResultSet page2 = engine.executeQuery("SELECT * FROM numbers ORDER BY id LIMIT 5 OFFSET 5");
        assertEquals(5, page2.getRowCount());
        page2.reset();
        page2.next();
        assertEquals(6L, page2.getValue("id"));

        // Page 3: rows 11-15
        ResultSet page3 = engine.executeQuery("SELECT * FROM numbers ORDER BY id LIMIT 5 OFFSET 10");
        assertEquals(5, page3.getRowCount());
        page3.reset();
        page3.next();
        assertEquals(11L, page3.getValue("id"));

        // Page 4: rows 16-20
        ResultSet page4 = engine.executeQuery("SELECT * FROM numbers ORDER BY id LIMIT 5 OFFSET 15");
        assertEquals(5, page4.getRowCount());
        page4.reset();
        page4.next();
        assertEquals(16L, page4.getValue("id"));
    }

    @Test
    @Order(4)
    public void testLimitOffsetBeyondData() {
        // Offset beyond available data
        ResultSet result = engine.executeQuery("SELECT * FROM numbers ORDER BY id LIMIT 5 OFFSET 100");
        assertEquals(0, result.getRowCount(), "Should return 0 rows when offset is beyond data");
    }

    @Test
    @Order(5)
    public void testLimitOffsetPartialPage() {
        // Request 10 rows but only 5 remain after offset
        ResultSet result = engine.executeQuery("SELECT * FROM numbers ORDER BY id LIMIT 10 OFFSET 15");
        assertEquals(5, result.getRowCount(), "Should return only 5 rows (16-20)");
        result.reset();
        result.next();
        assertEquals(16L, result.getValue("id"));
    }

    @Test
    @Order(6)
    public void testTopClause() {
        ResultSet result = engine.executeQuery("SELECT TOP 5 * FROM numbers ORDER BY id");

        assertEquals(5, result.getRowCount(), "TOP 5 should return 5 rows");
        result.reset();
        result.next();
        assertEquals(1L, result.getValue("id"), "First row should have id=1");

        // Check last row
        result.reset();
        for (int i = 0; i < result.getRowCount(); i++) {
            result.next();
        }
        assertEquals(5L, result.getValue("id"), "Last row should have id=5");
    }

    @Test
    @Order(7)
    public void testTopWithWhereClause() {
        ResultSet result = engine.executeQuery("SELECT TOP 3 * FROM numbers WHERE id > 10 ORDER BY id");

        assertEquals(3, result.getRowCount(), "Should return 3 rows where id > 10");
        result.reset();
        result.next();
        assertEquals(11L, result.getValue("id"), "First row is 11");
        result.next();
        assertEquals(12L, result.getValue("id"), "Second row is 12");
        result.next();
        assertEquals(13L, result.getValue("id"), "Third row is 13");
    }

    @Test
    @Order(8)
    public void testTopLargerThanData() {
        ResultSet result = engine.executeQuery("SELECT TOP 100 * FROM numbers");
        assertEquals(20, result.getRowCount(), "Should return all 20 rows when TOP exceeds data");
    }

    @Test
    @Order(9)
    public void testFetchFirstRows() {
        ResultSet result = engine.executeQuery("SELECT * FROM numbers ORDER BY id FETCH FIRST 5 ROWS ONLY");

        assertEquals(5, result.getRowCount(), "FETCH FIRST 5 ROWS should return 5 rows");
        result.reset();
        result.next();
        assertEquals(1L, result.getValue("id"), "First row should have id=1");
    }

    @Test
    @Order(10)
    public void testFetchNextRows() {
        ResultSet result = engine.executeQuery("SELECT * FROM numbers ORDER BY id FETCH NEXT 7 ROWS ONLY");

        assertEquals(7, result.getRowCount(), "FETCH NEXT 7 ROWS should return 7 rows");
        result.reset();
        result.next();
        assertEquals(1L, result.getValue("id"), "First row should have id=1");

        // Check last row
        result.reset();
        for (int i = 0; i < result.getRowCount(); i++) {
            result.next();
        }
        assertEquals(7L, result.getValue("id"), "Last row should have id=7");
    }

    @Test
    @Order(11)
    public void testFetchWithoutRowsKeyword() {
        // FETCH FIRST n ONLY (ROWS is optional)
        ResultSet result = engine.executeQuery("SELECT * FROM numbers ORDER BY id FETCH FIRST 3 ONLY");

        assertEquals(3, result.getRowCount(), "FETCH FIRST 3 ONLY should return 3 rows");
    }

    @Test
    @Order(12)
    public void testLimitWithAggregation() {
        ResultSet result = engine.executeQuery(
            "SELECT category, COUNT(*) as cnt FROM numbers GROUP BY category ORDER BY category LIMIT 1"
        );

        assertEquals(1, result.getRowCount(), "Should return 1 group");
        result.reset();
        result.next();
        assertEquals("even", result.getValue("category"));
    }

    @Test
    @Order(13)
    public void testTopWithAggregation() {
        ResultSet result = engine.executeQuery(
            "SELECT TOP 1 category, COUNT(*) as cnt FROM numbers GROUP BY category ORDER BY category DESC"
        );

        assertEquals(1, result.getRowCount(), "Should return 1 group");
        result.reset();
        result.next();
        assertEquals("odd", result.getValue("category"));
    }

    @Test
    @Order(14)
    public void testLimitZero() {
        ResultSet result = engine.executeQuery("SELECT * FROM numbers LIMIT 0");
        assertEquals(0, result.getRowCount(), "LIMIT 0 should return no rows");
    }

    @Test
    @Order(15)
    public void testOffsetZero() {
        ResultSet result = engine.executeQuery("SELECT * FROM numbers ORDER BY id LIMIT 3 OFFSET 0");
        assertEquals(3, result.getRowCount());
        result.reset();
        result.next();
        assertEquals(1L, result.getValue("id"), "OFFSET 0 should start from first row");
    }

    @Test
    @Order(16)
    public void testTopDescendingOrder() {
        ResultSet result = engine.executeQuery("SELECT TOP 3 * FROM numbers ORDER BY id DESC");

        assertEquals(3, result.getRowCount());
        result.reset();
        result.next();
        assertEquals(20L, result.getValue("id"), "First row should be highest id");
        result.next();
        assertEquals(19L, result.getValue("id"));
        result.next();
        assertEquals(18L, result.getValue("id"));
    }

    @Test
    @Order(17)
    public void testLimitOffsetDescendingOrder() {
        ResultSet result = engine.executeQuery("SELECT * FROM numbers ORDER BY id DESC LIMIT 5 OFFSET 5");

        assertEquals(5, result.getRowCount());
        result.reset();
        result.next();
        assertEquals(15L, result.getValue("id"), "Should skip top 5 (20-16) and return next 5 (15-11)");
    }

    @Test
    @Order(18)
    public void testTopWithOrderByValue() {
        ResultSet result = engine.executeQuery("SELECT TOP 3 * FROM numbers ORDER BY value DESC");

        assertEquals(3, result.getRowCount());
        result.reset();
        result.next();
        assertEquals(200L, result.getValue("value"), "Highest value is 20*10=200");
    }

    @Test
    @Order(19)
    public void testLimitWithMultipleColumns() {
        ResultSet result = engine.executeQuery(
            "SELECT id, value, category FROM numbers ORDER BY id LIMIT 5"
        );

        assertEquals(5, result.getRowCount());
        result.reset();
        result.next();
        assertEquals(1L, result.getValue("id"));
        assertEquals(10L, result.getValue("value"));
    }

    @Test
    @Order(20)
    public void testFetchFirstWithComplexQuery() {
        ResultSet result = engine.executeQuery("""
            SELECT category, SUM(value) as total FROM numbers
            GROUP BY category
            HAVING SUM(value) > 500
            ORDER BY category DESC
            FETCH FIRST 1 ROWS ONLY
            """);

        assertEquals(1, result.getRowCount(), "Should return 1 aggregated row");
    }
}
