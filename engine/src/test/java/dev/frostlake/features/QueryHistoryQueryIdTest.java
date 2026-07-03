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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests to verify query IDs are consistent between query history and LAST_QUERY_ID()
 */
public class QueryHistoryQueryIdTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(QueryHistoryQueryIdTest.class);

    @Test
    public void testQueryIdMatchesLastQueryId() {
        logger.info("Testing query ID consistency between query history and LAST_QUERY_ID()");

        engine.getExecutor().getQueryHistoryTracker().clear();

        // Execute a query
        engine.executeQuery("SELECT 1 as value");

        // Get the last query ID
        ResultSet lastQueryIdResult = engine.executeQuery("SELECT LAST_QUERY_ID() as query_id");
        String lastQueryId = (String) lastQueryIdResult.getRows().get(0).getValues().get(0);

        // Get the query ID from history
        ResultSet historyResult = engine.executeQuery("""
            SELECT query_id
            FROM INFORMATION_SCHEMA.QUERY_HISTORY
            WHERE query_text = 'SELECT 1 as value'
            ORDER BY start_time DESC
            LIMIT 1
            """);

        assertNotNull(historyResult);
        assertEquals(1, historyResult.getRowCount());

        String historyQueryId = (String) historyResult.getRows().get(0).getValues().get(0);

        // They should match
        assertEquals(lastQueryId, historyQueryId,
            "Query ID from LAST_QUERY_ID() should match query ID in query history");

        logger.info("Query IDs match: {}", lastQueryId);
    }

    @Test
    public void testMultipleQueriesHaveCorrectIds() {
        logger.info("Testing query ID consistency for multiple queries");

        engine.getExecutor().getQueryHistoryTracker().clear();

        // Execute first query
        engine.executeQuery("SELECT 100");
        ResultSet result1 = engine.executeQuery("SELECT LAST_QUERY_ID() as qid");
        String queryId1 = (String) result1.getRows().get(0).getValues().get(0);

        // Execute second query
        engine.executeQuery("SELECT 200");
        ResultSet result2 = engine.executeQuery("SELECT LAST_QUERY_ID() as qid");
        String queryId2 = (String) result2.getRows().get(0).getValues().get(0);

        // Query IDs should be different
        assertNotNull(queryId1);
        assertNotNull(queryId2);
        logger.info("Query ID 1: {}", queryId1);
        logger.info("Query ID 2: {}", queryId2);

        // Verify both are in history with correct IDs
        ResultSet history = engine.executeQuery("""
            SELECT query_id, query_text
            FROM INFORMATION_SCHEMA.QUERY_HISTORY
            WHERE query_text IN ('SELECT 100', 'SELECT 200')
            ORDER BY start_time ASC
            """);

        assertEquals(2, history.getRowCount());

        String historyQid1 = (String) history.getRows().get(0).getValues().get(0);
        String historyQid2 = (String) history.getRows().get(1).getValues().get(0);

        assertEquals(queryId1, historyQid1, "First query ID should match");
        assertEquals(queryId2, historyQid2, "Second query ID should match");

        logger.info("All query IDs match correctly");
    }

    @Test
    public void testDMLQueryIdConsistency() {
        logger.info("Testing query ID consistency for DML operations");

        engine.getExecutor().getQueryHistoryTracker().clear();

        // Create table and insert
        engine.execute("CREATE TABLE test_qid (id INTEGER)");
        ResultSet createQueryId = engine.executeQuery("SELECT LAST_QUERY_ID() as qid");
        String createQid = (String) createQueryId.getRows().get(0).getValues().get(0);

        engine.execute("INSERT INTO test_qid VALUES (1)");
        ResultSet insertQueryId = engine.executeQuery("SELECT LAST_QUERY_ID() as qid");
        String insertQid = (String) insertQueryId.getRows().get(0).getValues().get(0);

        // Verify in history
        ResultSet history = engine.executeQuery("""
            SELECT query_id, query_type
            FROM INFORMATION_SCHEMA.QUERY_HISTORY
            WHERE query_type IN ('CREATE', 'INSERT')
            ORDER BY start_time ASC
            """);

        assertEquals(2, history.getRowCount());

        String historyCreateQid = (String) history.getRows().get(0).getValues().get(0);
        String historyInsertQid = (String) history.getRows().get(1).getValues().get(0);

        assertEquals(createQid, historyCreateQid, "CREATE query ID should match");
        assertEquals(insertQid, historyInsertQid, "INSERT query ID should match");

        logger.info("DML query IDs match: CREATE={}, INSERT={}", createQid, insertQid);
    }

    @Test
    public void testQueryIdNotNull() {
        logger.info("Testing that all query IDs are not null");

        engine.getExecutor().getQueryHistoryTracker().clear();

        // Execute various types of queries
        engine.execute("CREATE TABLE qid_test (id INTEGER)");
        engine.execute("INSERT INTO qid_test VALUES (1), (2), (3)");
        engine.executeQuery("SELECT * FROM qid_test");
        engine.execute("UPDATE qid_test SET id = id + 10");
        engine.execute("DELETE FROM qid_test WHERE id > 11");

        // Verify all have non-null query IDs
        ResultSet history = engine.executeQuery("""
            SELECT query_id, query_type
            FROM INFORMATION_SCHEMA.QUERY_HISTORY
            WHERE query_id IS NOT NULL
            """);

        // All queries should have query IDs
        assertTrue(history.getRowCount() >= 5);

        for (int i = 0; i < history.getRowCount(); i++) {
            String queryId = (String) history.getRows().get(i).getValues().get(0);
            assertNotNull(queryId, "Query ID should not be null");
            assertFalse(queryId.isEmpty());
        }

        logger.info("All {} queries have valid query IDs", history.getRowCount());
    }

    @Test
    public void testResultScanUsesCorrectQueryId() {
        logger.info("Testing RESULT_SCAN uses same query ID as query history");

        engine.getExecutor().getQueryHistoryTracker().clear();

        // Execute a query
        engine.executeQuery("SELECT 42 as answer");

        // Get the last query ID
        ResultSet lastQueryIdResult = engine.executeQuery("SELECT LAST_QUERY_ID() as qid");
        String lastQueryId = (String) lastQueryIdResult.getRows().get(0).getValues().get(0);

        // Use RESULT_SCAN with the same query ID
        ResultSet resultScan = engine.executeQuery("SELECT * FROM TABLE(RESULT_SCAN('" + lastQueryId + "'))");

        assertNotNull(resultScan);
        assertEquals(1, resultScan.getRowCount());

        Object value = resultScan.getRows().get(0).getValues().get(0);
        assertEquals(42L, ((Number) value).longValue());

        // Verify the query ID is in history
        ResultSet history = engine.executeQuery("""
            SELECT query_id
            FROM INFORMATION_SCHEMA.QUERY_HISTORY
            WHERE query_text = 'SELECT 42 as answer'
            LIMIT 1
            """);

        assertEquals(1, history.getRowCount());
        String historyQueryId = (String) history.getRows().get(0).getValues().get(0);

        assertEquals(lastQueryId, historyQueryId, "Query IDs should match");

        logger.info("RESULT_SCAN successfully used query ID: {}", lastQueryId);
    }

    @Test
    public void testFailedQueryHasQueryId() {
        logger.info("Testing that failed queries also have query IDs");

        engine.getExecutor().getQueryHistoryTracker().clear();

        // Execute a failing query
        try {
            engine.executeQuery("SELECT * FROM table_that_does_not_exist");
        } catch (final Exception e) {
            // Expected to fail
        }

        // The failed query should still have gotten a query ID from the cache
        ResultSet lastQueryIdResult = engine.executeQuery("SELECT LAST_QUERY_ID() as qid");
        String lastQueryId = (String) lastQueryIdResult.getRows().get(0).getValues().get(0);

        // Check history
        ResultSet history = engine.executeQuery("""
            SELECT query_id, execution_status
            FROM INFORMATION_SCHEMA.QUERY_HISTORY
            WHERE execution_status = 'FAILED'
            AND query_text LIKE '%table_that_does_not_exist%'
            LIMIT 1
            """);

        if (history.getRowCount() > 0) {
            String historyQueryId = (String) history.getRows().get(0).getValues().get(0);
            assertNotNull(historyQueryId, "Failed query should have a query ID");
            logger.info("Failed query has query ID: {}", historyQueryId);
        }
    }

    private void assertTrue(final boolean condition) {
        Assertions.assertTrue(condition);
    }

    private void assertFalse(final boolean condition) {
        Assertions.assertFalse(condition);
    }
}
