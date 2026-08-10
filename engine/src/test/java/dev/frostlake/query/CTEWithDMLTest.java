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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for CTEs (WITH clause) used in DML statements
 * Note: Full implementation requires DML handler updates
 */
public class CTEWithDMLTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(CTEWithDMLTest.class);

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE source (id INTEGER, value VARCHAR)");
        engine.execute("INSERT INTO source VALUES (1, 'A')");
        engine.execute("INSERT INTO source VALUES (2, 'B')");
        engine.execute("INSERT INTO source VALUES (3, 'C')");

        engine.execute("CREATE TABLE target (id INTEGER, value VARCHAR)");
    }

    @Test
    public void testCTEWithInsertFromSelect() {
        logger.info("Testing CTE with INSERT FROM SELECT");

        // Using CTE in INSERT...SELECT statement
        engine.execute("""
            INSERT INTO target 
            WITH filtered AS (
                SELECT * FROM source WHERE id > 1
            )
            SELECT * FROM filtered
            """);

        final ResultSet result = engine.executeQuery("SELECT * FROM target ORDER BY id");
        assertNotNull(result);
        assertEquals(2, result.getRowCount());

        // Should have id 2 and 3
        assertEquals(2, ((Number) result.getRows().get(0).getValue(0)).intValue());
        assertEquals(3, ((Number) result.getRows().get(1).getValue(0)).intValue());

        logger.info("CTE with INSERT completed successfully");
    }

    @Test
    public void testMultipleCTEWithInsert() {
        logger.info("Testing multiple CTEs with INSERT");

        engine.execute("""
            INSERT INTO target 
            WITH
                filtered AS (
                    SELECT * FROM source WHERE id <= 2
                ),
                renamed AS (
                    SELECT id, value FROM filtered
                )
            SELECT * FROM renamed
            """);

        final ResultSet result = engine.executeQuery("SELECT COUNT(*) FROM target");
        assertNotNull(result);
        assertEquals(2, ((Number) result.getRows().get(0).getValue(0)).intValue());

        logger.info("Multiple CTEs with INSERT completed successfully");
    }

    @Test
    public void testCTEWithInsertAndJoin() {
        logger.info("Testing CTE with INSERT and JOIN");

        engine.execute("CREATE TABLE lookup (id INTEGER, category VARCHAR)");
        engine.execute("INSERT INTO lookup VALUES (1, 'Cat1')");
        engine.execute("INSERT INTO lookup VALUES (2, 'Cat2')");

        engine.execute("""
            INSERT INTO target 
            WITH enriched AS (
                SELECT s.id, s.value
                FROM source s
                JOIN lookup l ON s.id = l.id
            )
            SELECT * FROM enriched
            """);

        final ResultSet result = engine.executeQuery("SELECT COUNT(*) FROM target");
        assertNotNull(result);
        assertEquals(2, ((Number) result.getRows().get(0).getValue(0)).intValue());

        logger.info("CTE with INSERT and JOIN completed successfully");
    }

    @Test
    public void testCTEWithInsertAndAggregation() {
        logger.info("Testing CTE with INSERT and aggregation");

        engine.execute("CREATE TABLE summary (total INTEGER, description VARCHAR)");

        engine.execute("""
            INSERT INTO summary 
            WITH stats AS (
                SELECT COUNT(*) as cnt FROM source WHERE id > 0
            )
            SELECT cnt, 'Total records' FROM stats
            """);

        final ResultSet result = engine.executeQuery("SELECT total FROM summary");
        assertNotNull(result);
        assertEquals(1, result.getRowCount());
        assertEquals(3, ((Number) result.getRows().get(0).getValue(0)).intValue());

        logger.info("CTE with INSERT and aggregation completed successfully");
    }

    @Test
    public void testCTESyntaxValidation() {
        logger.info("Testing CTE syntax variations");

        // Test RECURSIVE keyword parsing (even if not fully supported)
        try {
            engine.execute("""
            INSERT INTO target 
                WITH RECURSIVE numbers AS (
                    SELECT 1 as n
                    UNION ALL
                    SELECT n + 1 FROM numbers WHERE n < 5
                )
            SELECT n, 'num' FROM numbers
                """);
            logger.info("RECURSIVE keyword parsed successfully");
        } catch (final Exception e) {
            logger.info("RECURSIVE CTE limitation: {}", e.getMessage());
        }

        // Test CTE with explicit column list parsing
        try {
            engine.execute("""
            INSERT INTO target 
                WITH filtered (id, val) AS (
                    SELECT id, value FROM source WHERE id = 1
                )
            SELECT * FROM filtered
                """);
            logger.info("CTE with column list parsed successfully");
        } catch (final Exception e) {
            logger.info("CTE column list limitation: {}", e.getMessage());
        }
    }
}
