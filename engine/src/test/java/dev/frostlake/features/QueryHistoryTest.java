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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for query history tracking
 */
public class QueryHistoryTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(QueryHistoryTest.class);

    private static final String SESSION_SCOPED_HISTORY =
        "every test here clears the embedded QueryHistoryTracker (engine.getExecutor(), an internal "
        + "accessor) and then asserts that the statements it just ran are the history; on a real account "
        + "QUERY_HISTORY is account-wide, populated asynchronously and cannot be cleared, and its "
        + "query_type values are finer-grained (CREATE_TABLE, not CREATE)";

    @Test
    public void testQueryHistoryTracking() {
        Assumptions.assumeFalse(isLiveSnowflake(), SESSION_SCOPED_HISTORY);
        logger.info("Testing basic query history tracking");

        // Clear any existing history
        engine.getExecutor().getQueryHistoryTracker().clear();

        // Execute some queries
        engine.execute("CREATE TABLE test_table (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO test_table VALUES (1, 'Alice'), (2, 'Bob')");
        engine.executeQuery("SELECT * FROM test_table");

        // Query history
        ResultSet history = engine.executeQuery("SELECT * FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY())");

        assertNotNull(history);
        assertTrue(history.getRowCount() >= 3); // At least 3 queries: CREATE, INSERT, SELECT

        logger.info("Query history tracked {} queries", history.getRowCount());
    }

    @Test
    public void testQueryHistoryColumns() {
        Assumptions.assumeFalse(isLiveSnowflake(), SESSION_SCOPED_HISTORY);
        logger.info("Testing query history columns");

        engine.getExecutor().getQueryHistoryTracker().clear();

        engine.execute("SELECT 1");

        ResultSet history = engine.executeQuery("SELECT * FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY())");

        // Snowflake's QUERY_HISTORY table function exposes 62 columns (live-captured).
        assertEquals(62, history.getColumns().size());

        // Verify we have at least one row
        assertTrue(history.getRowCount() > 0);

        logger.info("All expected columns present: {} columns", history.getColumns().size());
    }

    @Test
    public void testQueryHistoryQueryTypes() {
        Assumptions.assumeFalse(isLiveSnowflake(), SESSION_SCOPED_HISTORY);
        logger.info("Testing query type classification");

        engine.getExecutor().getQueryHistoryTracker().clear();

        // Execute different types of queries
        engine.execute("CREATE TABLE type_test (id INTEGER)");
        engine.execute("INSERT INTO type_test VALUES (1)");
        engine.execute("UPDATE type_test SET id = 2");
        engine.execute("DELETE FROM type_test");
        engine.executeQuery("SELECT * FROM type_test");
        engine.execute("DROP TABLE type_test");

        ResultSet history = engine.executeQuery("""
            SELECT query_type, COUNT(*) as count
            FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY())
            GROUP BY query_type
            ORDER BY query_type
            """);

        assertTrue(history.getRowCount() > 0);
        logger.info("Query types tracked: {} distinct types", history.getRowCount());
    }

    @Test
    public void testQueryHistoryStatus() {
        Assumptions.assumeFalse(isLiveSnowflake(), SESSION_SCOPED_HISTORY);
        logger.info("Testing query execution status tracking");

        engine.getExecutor().getQueryHistoryTracker().clear();

        // Execute successful query
        engine.execute("SELECT 1");

        // Execute failing query
        try {
            engine.execute("SELECT * FROM non_existent_table");
        } catch (final Exception e) {
            // Expected to fail
        }

        ResultSet history = engine.executeQuery("""
            SELECT execution_status, COUNT(*) as count
            FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY())
            GROUP BY execution_status
            """);

        assertTrue(history.getRowCount() > 0);
        logger.info("Query statuses tracked");
    }

    @Test
    public void testQueryHistoryFilter() {
        Assumptions.assumeFalse(isLiveSnowflake(), SESSION_SCOPED_HISTORY);
        logger.info("Testing query history filtering");

        engine.getExecutor().getQueryHistoryTracker().clear();

        engine.execute("CREATE TABLE filter_test (id INTEGER)");
        engine.executeQuery("SELECT * FROM filter_test");
        engine.execute("DROP TABLE filter_test");

        // Filter by query type
        ResultSet selectQueries = engine.executeQuery("""
            SELECT * FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY())
            WHERE query_type = 'SELECT'
            """);

        assertTrue(selectQueries.getRowCount() >= 1);
        logger.info("Filtered SELECT queries: {}", selectQueries.getRowCount());

        // Filter by status
        ResultSet successQueries = engine.executeQuery("""
            SELECT * FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY())
            WHERE execution_status = 'SUCCESS'
            """);

        assertTrue(successQueries.getRowCount() >= 1);
        logger.info("Filtered SUCCESS queries: {}", successQueries.getRowCount());
    }

    @Test
    public void testQueryHistoryOrderByTime() {
        Assumptions.assumeFalse(isLiveSnowflake(), SESSION_SCOPED_HISTORY);
        logger.info("Testing query history ordering");

        engine.getExecutor().getQueryHistoryTracker().clear();

        engine.execute("SELECT 1");
        engine.execute("SELECT 2");
        engine.execute("SELECT 3");

        ResultSet history = engine.executeQuery("""
            SELECT query_text, start_time
            FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY())
            ORDER BY start_time DESC
            """);

        assertTrue(history.getRowCount() >= 3);
        logger.info("Query history ordered by time");
    }

    @Test
    public void testQueryHistoryExecutionTime() {
        Assumptions.assumeFalse(isLiveSnowflake(), SESSION_SCOPED_HISTORY);
        logger.info("Testing execution time tracking");

        engine.getExecutor().getQueryHistoryTracker().clear();

        engine.execute("CREATE TABLE perf_test (id INTEGER)");
        engine.execute("INSERT INTO perf_test VALUES (1), (2), (3), (4), (5)");

        ResultSet history = engine.executeQuery("""
            SELECT query_type, TOTAL_ELAPSED_TIME
            FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY())
            WHERE query_type IN ('CREATE', 'INSERT')
            """);

        assertTrue(history.getRowCount() >= 2);

        for (int i = 0; i < history.getRowCount(); i++) {
            Object execTime = history.getRows().get(i).getValues().get(1);
            assertNotNull(execTime);
            logger.info("Query execution time: {} ms", execTime);
        }
    }

    @Test
    public void testQueryHistoryRowsProduced() {
        Assumptions.assumeFalse(isLiveSnowflake(), SESSION_SCOPED_HISTORY);
        logger.info("Testing rows produced tracking");

        engine.getExecutor().getQueryHistoryTracker().clear();

        engine.execute("CREATE TABLE rows_test (id INTEGER)");
        engine.execute("INSERT INTO rows_test VALUES (1), (2), (3)");
        engine.executeQuery("SELECT * FROM rows_test");

        ResultSet history = engine.executeQuery("""
            SELECT query_text, rows_produced
            FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY())
            WHERE query_type = 'SELECT'
            ORDER BY start_time DESC
            LIMIT 1
            """);

        assertTrue(history.getRowCount() > 0);
        logger.info("Rows produced tracked for SELECT query");
    }

    @Test
    public void testQueryHistoryWithDDL() {
        Assumptions.assumeFalse(isLiveSnowflake(), SESSION_SCOPED_HISTORY);
        logger.info("Testing query history with DDL operations");

        engine.getExecutor().getQueryHistoryTracker().clear();

        engine.execute("CREATE DATABASE test_hist_db");
        engine.execute("USE DATABASE test_hist_db");
        engine.execute("CREATE SCHEMA test_hist_schema");
        engine.execute("USE SCHEMA test_hist_schema");
        engine.execute("CREATE TABLE ddl_test (id INTEGER)");

        ResultSet history = engine.executeQuery("""
            SELECT query_type, COUNT(*) as count
            FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY())
            WHERE query_type = 'CREATE'
            GROUP BY query_type
            """);

        assertTrue(history.getRowCount() > 0);
        logger.info("DDL operations tracked in history");
    }

    @Test
    public void testQueryHistoryWithTransactions() {
        Assumptions.assumeFalse(isLiveSnowflake(), SESSION_SCOPED_HISTORY);
        logger.info("Testing query history with transactions");

        engine.getExecutor().getQueryHistoryTracker().clear();

        engine.execute("CREATE TABLE txn_test (id INTEGER)");

        // Use try-catch to handle transaction execution
        try {
            engine.execute("BEGIN");
            engine.execute("INSERT INTO txn_test VALUES (1)");
            engine.execute("COMMIT");
        } catch (final Exception e) {
            logger.warn("Transaction execution issue: {}", e.getMessage());
        }

        ResultSet history = engine.executeQuery("""
            SELECT query_type
            FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY())
            WHERE query_type IN ('BEGIN', 'COMMIT', 'CREATE', 'INSERT')
            """);

        assertTrue(history.getRowCount() >= 2);
        logger.info("Statements tracked: {}", history.getRowCount());
    }

    @Test
    public void testQueryHistoryLimit() {
        Assumptions.assumeFalse(isLiveSnowflake(), SESSION_SCOPED_HISTORY);
        logger.info("Testing query history limit");

        engine.getExecutor().getQueryHistoryTracker().clear();

        // Execute 10 queries
        for (int i = 1; i <= 10; i++) {
            engine.execute("SELECT " + i);
        }

        // Verify we have at least 10 queries
        ResultSet allHistory = engine.executeQuery("SELECT * FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY())");
        assertTrue(allHistory.getRowCount() >= 10);

        // Test LIMIT
        ResultSet limitedHistory = engine.executeQuery("""
            SELECT * FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY())
            LIMIT 5
            """);

        assertEquals(5, limitedHistory.getRowCount());
        logger.info("Query history LIMIT works correctly");
    }

    @Test
    public void testQueryHistoryErrorMessages() {
        Assumptions.assumeFalse(isLiveSnowflake(), SESSION_SCOPED_HISTORY);
        logger.info("Testing error message tracking");

        engine.getExecutor().getQueryHistoryTracker().clear();

        // Execute a failing query
        try {
            engine.execute("SELECT * FROM table_that_does_not_exist");
        } catch (final Exception e) {
            // Expected
        }

        ResultSet history = engine.executeQuery("""
            SELECT error_message
            FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY())
            WHERE execution_status = 'FAILED'
            AND error_message IS NOT NULL
            """);

        assertTrue(history.getRowCount() >= 1);
        logger.info("Error messages tracked for failed queries");
    }

    @Test
    public void testQueryHistoryPersistence() {
        Assumptions.assumeFalse(isLiveSnowflake(), SESSION_SCOPED_HISTORY);
        logger.info("Testing query history persistence across multiple queries");

        engine.getExecutor().getQueryHistoryTracker().clear();

        // Execute initial queries
        engine.execute("SELECT 1");
        engine.execute("SELECT 2");

        ResultSet history1 = engine.executeQuery("SELECT COUNT(*) as count FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY())");
        long count1 = ((Number) history1.getRows().get(0).getValues().get(0)).longValue();

        // Execute more queries
        engine.execute("SELECT 3");

        ResultSet history2 = engine.executeQuery("SELECT COUNT(*) as count FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY())");
        long count2 = ((Number) history2.getRows().get(0).getValues().get(0)).longValue();

        assertTrue(count2 > count1);
        logger.info("Query history persists: {} -> {}", count1, count2);
    }
}
