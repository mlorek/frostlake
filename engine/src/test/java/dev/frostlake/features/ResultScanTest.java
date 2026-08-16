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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for RESULT_SCAN table function and LAST_QUERY_ID() function
 */
public class ResultScanTest extends BaseDatabaseTest {

    @Test
    public void testLastQueryId() {
        // Create and populate table
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice'), (2, 'Bob')");

        // Execute a SELECT query
        final ResultSet result1 = engine.executeQuery("SELECT * FROM users");
        assertEquals(2, result1.getRowCount());

        // Get the last query ID
        final ResultSet queryIdResult = engine.executeQuery("SELECT LAST_QUERY_ID() AS query_id");
        assertTrue(queryIdResult.next());
        final String queryId = (String) queryIdResult.getValue("query_id");

        assertNotNull(queryId, "LAST_QUERY_ID() should return a query ID");
        assertFalse(queryId.isEmpty(), "Query ID should not be empty");

        // Verify it's a UUID format
        assertTrue(queryId.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"),
            "Query ID should be in UUID format");
    }

    @Test
    public void testResultScanWithLastQueryId() {
        // Create and populate table
        engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR, price FLOAT)");
        engine.execute("INSERT INTO products VALUES (1, 'Widget', 19.99), (2, 'Gadget', 29.99), (3, 'Tool', 39.99)");

        // Execute a query
        final ResultSet originalResult = engine.executeQuery("SELECT * FROM products WHERE price > 20");
        assertEquals(2, originalResult.getRowCount());

        // Use RESULT_SCAN with LAST_QUERY_ID() to get the same results
        final ResultSet scannedResult = engine.executeQuery("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertEquals(2, scannedResult.getRowCount());

        // Verify the data matches
        assertTrue(scannedResult.next());
        assertEquals(2L, scannedResult.getValue("id"));
        assertEquals("Gadget", scannedResult.getValue("name"));

        assertTrue(scannedResult.next());
        assertEquals(3L, scannedResult.getValue("id"));
        assertEquals("Tool", scannedResult.getValue("name"));
    }

    @Test
    public void testResultScanWithSpecificQueryId() {
        // Create and populate table
        engine.execute("CREATE TABLE orders (id INTEGER, amount FLOAT)");
        engine.execute("INSERT INTO orders VALUES (1, 100.0), (2, 200.0), (3, 300.0)");

        // Execute a query and capture query ID
        engine.executeQuery("SELECT * FROM orders WHERE amount > 150");

        // Get the query ID
        final ResultSet queryIdResult = engine.executeQuery("SELECT LAST_QUERY_ID() AS qid");
        assertTrue(queryIdResult.next());
        final String queryId = (String) queryIdResult.getValue("qid");

        // Use RESULT_SCAN with the specific query ID (as string literal)
        final ResultSet scannedResult = engine.executeQuery(
            "SELECT * FROM TABLE(RESULT_SCAN('" + queryId + "'))"
        );
        assertEquals(2, scannedResult.getRowCount());

        // Verify the data
        assertTrue(scannedResult.next());
        assertEquals(2L, scannedResult.getValue("id"));
        assertTrue(scannedResult.next());
        assertEquals(3L, scannedResult.getValue("id"));
    }

    @Test
    public void testResultScanMultipleQueries() {
        // Create and populate table
        // Using "category" column - previously broken, now fixed!
        engine.execute("CREATE TABLE items (id INTEGER, category VARCHAR)");

        // Insert first set
        engine.execute("INSERT INTO items VALUES (1, 'A'), (2, 'B')");

        // Execute first query and scan it immediately
        final ResultSet result1 = engine.executeQuery("SELECT * FROM items WHERE category = 'A'");
        assertEquals(1, result1.getRowCount());
        final ResultSet scan1 = engine.executeQuery("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertEquals(1, scan1.getRowCount());

        // Insert more rows
        engine.execute("INSERT INTO items VALUES (3, 'A'), (4, 'C')");

        // Execute second query and scan it immediately
        final ResultSet result2 = engine.executeQuery("SELECT * FROM items WHERE category = 'A'");
        assertEquals(2, result2.getRowCount());
        final ResultSet scan2 = engine.executeQuery("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertEquals(2, scan2.getRowCount());
    }

    @Test
    public void testResultScanWithFiltering() {
        // Create and populate table
        engine.execute("CREATE TABLE numbers (id INTEGER, value INTEGER)");
        engine.execute("INSERT INTO numbers VALUES (1, 10), (2, 20), (3, 30), (4, 40)");

        // Execute a query
        engine.executeQuery("SELECT * FROM numbers WHERE value > 15");

        // Use RESULT_SCAN and apply additional filtering
        final ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE value >= 30"
        );
        assertEquals(2, result.getRowCount());

        assertTrue(result.next());
        assertEquals(30L, ((Number) result.getValue("value")).longValue());
        assertTrue(result.next());
        assertEquals(40L, ((Number) result.getValue("value")).longValue());
    }

    @Test
    public void testResultScanWithJoin() {
        // Create and populate tables
        engine.execute("CREATE TABLE customers (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO customers VALUES (1, 'Alice'), (2, 'Bob')");

        // Execute a query
        engine.executeQuery("SELECT * FROM customers");

        // Join RESULT_SCAN with another table (or GENERATOR)
        final ResultSet result = engine.executeQuery(
            "SELECT r.name FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) r, TABLE(GENERATOR(ROWCOUNT => 2)) g"
        );
        assertEquals(4, result.getRowCount()); // 2 customers * 2 generated rows
    }

    @Test
    public void testResultScanWithAggregation() {
        // Create and populate table
        engine.execute("CREATE TABLE sales (product VARCHAR, amount FLOAT)");
        engine.execute("INSERT INTO sales VALUES ('A', 100), ('B', 200), ('A', 150), ('B', 250)");

        // Execute a query
        engine.executeQuery("SELECT * FROM sales WHERE amount > 100");

        // Use RESULT_SCAN with aggregation
        final ResultSet result = engine.executeQuery(
            "SELECT COUNT(*) AS total FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"
        );
        assertTrue(result.next());
        final long total = ((Number) result.getValue("total")).longValue();
        assertEquals(3L, total);
    }

    @Test
    public void testResultScanInvalidQueryId() {
        // Try to use RESULT_SCAN with non-existent query ID
        final RuntimeException exception = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.executeQuery("SELECT * FROM TABLE(RESULT_SCAN('00000000-0000-0000-0000-000000000000'))");
                
            }
        });
        assertTrue(exception.getMessage().toLowerCase().contains("not found") ||
                   exception.getMessage().toLowerCase().contains("not available"));
    }

    @Test
    public void testResultScanInvalidFormat() {
        // Try to use RESULT_SCAN with invalid query ID format
        final RuntimeException exception = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.executeQuery("SELECT * FROM TABLE(RESULT_SCAN('invalid-query-id'))");
                
            }
        });
        assertTrue(exception.getMessage().toLowerCase().contains("invalid"));
    }

    @Test
    public void testResultScanNoArguments() {
        // No-arg RESULT_SCAN() scans the most recent query, exactly like RESULT_SCAN(LAST_QUERY_ID()).
        engine.executeQuery("SELECT 41 AS n");
        final ResultSet rs = engine.executeQuery("SELECT * FROM TABLE(RESULT_SCAN())");
        assertEquals(1, rs.getRowCount());
        assertEquals(41L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void testLastQueryIdAfterDDL() {
        // Execute DDL statements
        engine.execute("CREATE TABLE test_table (id INTEGER)");

        // Execute a SELECT query
        engine.execute("INSERT INTO test_table VALUES (1)");
        final ResultSet result = engine.executeQuery("SELECT * FROM test_table");
        assertEquals(1, result.getRowCount());

        // LAST_QUERY_ID should return the SELECT query ID
        final ResultSet queryIdResult = engine.executeQuery("SELECT LAST_QUERY_ID() AS qid");
        assertTrue(queryIdResult.next());
        final String queryId = (String) queryIdResult.getValue("qid");
        assertNotNull(queryId);

        // Should be able to scan this result
        final ResultSet scanResult = engine.executeQuery("SELECT * FROM TABLE(RESULT_SCAN('" + queryId + "'))");
        assertEquals(1, scanResult.getRowCount());
    }

    @Test
    public void testResultScanWithOrderBy() {
        // Create and populate table
        engine.execute("CREATE TABLE data (id INTEGER, value VARCHAR)");
        engine.execute("INSERT INTO data VALUES (3, 'C'), (1, 'A'), (2, 'B')");

        // Execute query with ORDER BY
        engine.executeQuery("SELECT * FROM data ORDER BY id");

        // Use RESULT_SCAN with different ORDER BY
        final ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY id DESC"
        );

        assertTrue(result.next());
        assertEquals(3L, result.getValue("id"));
        assertTrue(result.next());
        assertEquals(2L, result.getValue("id"));
        assertTrue(result.next());
        assertEquals(1L, result.getValue("id"));
    }

    @Test
    public void testResultScanCacheSizeLimit() {
        // Create table
        engine.execute("CREATE TABLE test (id INTEGER)");
        engine.execute("INSERT INTO test VALUES (1)");

        // Execute many queries (cache limit is 100)
        // Store first query ID
        engine.executeQuery("SELECT * FROM test");
        final ResultSet firstQidResult = engine.executeQuery("SELECT LAST_QUERY_ID() AS qid");
        firstQidResult.next();
        final String firstQueryId = (String) firstQidResult.getValue("qid");

        // Execute additional queries to potentially exceed cache
        for (int i = 0; i < 10; i++) {
            engine.executeQuery("SELECT * FROM test WHERE id = " + i);
        }

        // The first query should still be in cache (10 < 100)
        final ResultSet scanResult = engine.executeQuery(
            "SELECT * FROM TABLE(RESULT_SCAN('" + firstQueryId + "'))"
        );
        assertEquals(1, scanResult.getRowCount());
    }

    /**
     * RESULT_SCAN honours the offset its LAST_QUERY_ID argument carries. The argument used to be
     * special-cased by NAME — any LAST_QUERY_ID call in that position was replaced with the most
     * recent query ID, so RESULT_SCAN(LAST_QUERY_ID(-2)) silently scanned the wrong result. Live
     * returns the three-row query for -2 and the one-row query for -1.
     */
    @Test
    public void resultScanHonoursTheLastQueryIdOffset() {
        engine.execute("CREATE DATABASE offset_db");
        engine.execute("USE DATABASE offset_db");
        engine.execute("USE SCHEMA public");
        engine.execute("CREATE TABLE three (k INTEGER)");
        engine.execute("INSERT INTO three VALUES (1), (2), (3)");
        engine.execute("SELECT * FROM three");
        engine.execute("SELECT 1");

        final ResultSet mostRecent = engine.executeQuery(
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID(-1)))");
        assertEquals(1L, ((Number) mostRecent.getRows().get(0).getValue(0)).longValue());

        // Re-establish the same two source queries: every statement joins the history, including
        // the probe above, so -2 only means "the three-row SELECT" from a matching position.
        engine.execute("SELECT * FROM three");
        engine.execute("SELECT 1");
        final ResultSet oneBefore = engine.executeQuery(
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID(-2)))");
        assertEquals(3L, ((Number) oneBefore.getRows().get(0).getValue(0)).longValue());
    }

    /** RESULT_SCAN over a LAST_QUERY_ID that resolves to nothing keeps its own wording. */
    @Test
    public void resultScanOverAnEmptyHistoryReportsNoPreviousResults() {
        // The shared live session carries a deep query history, so a "nothing N queries back"
        // state cannot be arranged there — the cell is embedded-only.
        Assumptions.assumeFalse(isLiveSnowflake(),
            "an empty query history cannot be arranged on the shared live session");
        engine.execute("CREATE DATABASE empty_hist_db");
        engine.execute("USE DATABASE empty_hist_db");
        engine.execute("USE SCHEMA public");
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID(-99)))");
            }
        });
        assertTrue(error.getMessage().contains("No previous query results available"),
            "unexpected message: " + error.getMessage());
    }
}
