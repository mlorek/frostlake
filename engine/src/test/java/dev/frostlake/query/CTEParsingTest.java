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
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for CTE parsing edge cases
 */
public class CTEParsingTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(CTEParsingTest.class);

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE test_table (id INTEGER, value VARCHAR)");
        engine.execute("INSERT INTO test_table VALUES (1, 'A')");
        engine.execute("INSERT INTO test_table VALUES (2, 'B')");
    }

    @Test
    public void testCTEWithoutColumns() {
        logger.info("Testing CTE without column list");

        final ResultSet result = engine.executeQuery("""
            WITH cte AS (
                SELECT * FROM test_table
            )
            SELECT * FROM cte
            """);

        assertEquals(2, result.getRowCount());
        assertEquals(2, result.getColumnCount());
        assertEquals(1L, ((Number) result.getRows().get(0).getValue(0)).longValue());
        assertEquals("A", result.getRows().get(0).getValue(1));
        assertEquals(2L, ((Number) result.getRows().get(1).getValue(0)).longValue());
        assertEquals("B", result.getRows().get(1).getValue(1));
    }

    @Test
    public void testCTEWithColumnList() {
        logger.info("Testing CTE with explicit column list");

        final ResultSet result = engine.executeQuery("""
            WITH cte (id, val) AS (
                SELECT * FROM test_table
            )
            SELECT * FROM cte
            """);

        assertEquals(2, result.getRowCount());
        assertEquals(2, result.getColumnCount());
        // The CTE column list renames the projected columns to (id, val).
        final int idIndex = result.getColumnIndex("id");
        final int valIndex = result.getColumnIndex("val");
        assertEquals(1L, ((Number) result.getRows().get(0).getValue(idIndex)).longValue());
        assertEquals("A", result.getRows().get(0).getValue(valIndex));
    }

    @Test
    public void testRecursiveCTE() {
        logger.info("Testing recursive CTE");

        final ResultSet result = engine.executeQuery("""
            WITH RECURSIVE cte AS (
                SELECT 1 as n
                UNION ALL
                SELECT n + 1 FROM cte WHERE n < 5
            )
            SELECT * FROM cte ORDER BY n
            """);

        // Recursion produces n = 1, 2, 3, 4, 5 (stops once n reaches 5).
        assertEquals(5, result.getRowCount());
        assertEquals(1, result.getColumnCount());
        for (int i = 0; i < 5; i++) {
            assertEquals((long) (i + 1), ((Number) result.getRows().get(i).getValue(0)).longValue());
        }
    }

    @Test
    public void testCTEInInsert() {
        logger.info("Testing CTE in INSERT");

        engine.execute("""
            CREATE TABLE target (id INTEGER, value VARCHAR)
            """);

        engine.execute("""
            WITH cte AS (
                SELECT * FROM test_table WHERE id = 1
            )
            INSERT INTO target SELECT * FROM cte
            """);

        // The CTE keeps only id=1, so one row is inserted into target.
        final ResultSet result = engine.executeQuery("SELECT * FROM target");
        assertEquals(1, result.getRowCount());
        assertEquals(1L, ((Number) result.getRows().get(0).getValue(0)).longValue());
        assertEquals("A", result.getRows().get(0).getValue(1));
    }

    @Test
    public void testCTEInUpdate() {
        logger.info("Testing CTE in UPDATE");

        engine.execute("""
            CREATE TABLE target (id INTEGER, value VARCHAR)
            """);
        engine.execute("INSERT INTO target VALUES (1, 'X')");

        // A CTE referenced inside the UPDATE's SET-clause subquery now resolves (including the correlated
        // form). The CTE selects test_table id=1 ('A'), so target's value becomes 'A'.
        engine.execute("""
            WITH cte AS (
                SELECT id, value FROM test_table WHERE id = 1
            )
            UPDATE target SET value = (SELECT value FROM cte WHERE cte.id = target.id)
            """);

        final ResultSet rs = engine.executeQuery("SELECT value FROM target WHERE id = 1");
        assertEquals("A", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testCTEInDelete() {
        logger.info("Testing CTE in DELETE");

        engine.execute("""
            CREATE TABLE target (id INTEGER, value VARCHAR)
            """);
        engine.execute("INSERT INTO target VALUES (1, 'X')");
        engine.execute("INSERT INTO target VALUES (2, 'Y')");

        engine.execute("""
            WITH cte AS (
                SELECT id FROM test_table WHERE id = 1
            )
            DELETE FROM target WHERE id IN (SELECT id FROM cte)
            """);

        // The CTE matches id=1, so that row is deleted, leaving id=2 ('Y').
        final ResultSet result = engine.executeQuery("SELECT id, value FROM target ORDER BY id");
        assertEquals(1, result.getRowCount());
        assertEquals(2L, ((Number) result.getRows().get(0).getValue(0)).longValue());
        assertEquals("Y", result.getRows().get(0).getValue(1));
    }
}
