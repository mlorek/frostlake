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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test for nested correlated subqueries with EXISTS
 */
public class NestedCorrelatedQueryTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(NestedCorrelatedQueryTest.class);

    @BeforeEach
    public void setupTables() {
        engine.execute("CREATE TABLE t1 (i INTEGER)");
        engine.execute("CREATE TABLE t2 (i INTEGER)");
        engine.execute("CREATE TABLE t3 (i INTEGER)");

        engine.execute("INSERT INTO t1 VALUES (1), (2), (3), (4)");
        engine.execute("INSERT INTO t2 VALUES (1), (2), (3)");
        engine.execute("INSERT INTO t3 VALUES (1), (2)");
    }

    @Test
    public void testNestedCorrelatedExists() {
        logger.info("Testing nested correlated EXISTS subqueries");

        String query = """
            SELECT *
            FROM t1
            WHERE EXISTS (
                SELECT *
                FROM t2
                WHERE t1.i = t2.i
                    AND EXISTS (
                        SELECT *
                        FROM t3
                        WHERE t2.i = t3.i
                    )
            )
            """;

        ResultSet result = engine.executeQuery(query);

        // Expected: rows from t1 where i is in (1, 2)
        // Because:
        // - t1 has: 1, 2, 3, 4
        // - t2 has: 1, 2, 3
        // - t3 has: 1, 2
        // For each row in t1:
        //   - i=1: EXISTS in t2 where t2.i=1? YES. EXISTS in t3 where t3.i=1? YES -> INCLUDE
        //   - i=2: EXISTS in t2 where t2.i=2? YES. EXISTS in t3 where t3.i=2? YES -> INCLUDE
        //   - i=3: EXISTS in t2 where t2.i=3? YES. EXISTS in t3 where t3.i=3? NO -> EXCLUDE
        //   - i=4: EXISTS in t2 where t2.i=4? NO -> EXCLUDE

        assertEquals(2, result.getRowCount(), "Should return 2 rows (i=1 and i=2)");

        long val1 = ((Number) result.getRows().get(0).getValues().get(0)).longValue();
        long val2 = ((Number) result.getRows().get(1).getValues().get(0)).longValue();

        assertEquals(1L, val1, "First row should be i=1");
        assertEquals(2L, val2, "Second row should be i=2");

        logger.info("Nested correlated EXISTS query returned correct results");
    }

    @Test
    public void testSimpleCorrelatedExists() {
        logger.info("Testing simple correlated EXISTS subquery");

        String query = """
            SELECT *
            FROM t1
            WHERE EXISTS (
                SELECT *
                FROM t2
                WHERE t1.i = t2.i
            )
            """;

        ResultSet result = engine.executeQuery(query);

        // Expected: rows from t1 where i is in (1, 2, 3)
        assertEquals(3, result.getRowCount(), "Should return 3 rows");

        logger.info("Simple correlated EXISTS query works correctly");
    }

    @Test
    public void testDoubleNestedCorrelatedExists() {
        logger.info("Testing double nested correlated EXISTS");

        engine.execute("CREATE TABLE t4 (i INTEGER)");
        engine.execute("INSERT INTO t4 VALUES (1)");

        String query = """
            SELECT *
            FROM t1
            WHERE EXISTS (
                SELECT *
                FROM t2
                WHERE t1.i = t2.i
                    AND EXISTS (
                        SELECT *
                        FROM t3
                        WHERE t2.i = t3.i
                            AND EXISTS (
                                SELECT *
                                FROM t4
                                WHERE t3.i = t4.i
                            )
                    )
            )
            """;

        ResultSet result = engine.executeQuery(query);

        // Expected: only i=1
        // t1: 1,2,3,4 -> t2: 1,2,3 -> t3: 1,2 -> t4: 1
        assertEquals(1, result.getRowCount(), "Should return 1 row (i=1)");

        long val = ((Number) result.getRows().get(0).getValues().get(0)).longValue();
        assertEquals(1L, val, "Should be i=1");

        logger.info("Double nested correlated EXISTS works correctly");
    }

    @Test
    public void testNestedExistsDebug() {
        logger.info("Testing nested EXISTS with debug output");

        String query = """
            SELECT *
            FROM t1
            WHERE EXISTS (
                SELECT *
                FROM t2
                WHERE t1.i = t2.i
                    AND EXISTS (
                        SELECT *
                        FROM t3
                        WHERE t2.i = t3.i
                    )
            )
            """;

        ResultSet result = engine.executeQuery(query);

        logger.info("Result row count: {}", result.getRowCount());
        for (int i = 0; i < result.getRowCount(); i++) {
            logger.info("Row {}: {}", i, result.getRows().get(i).getValues());
        }

        // Manual verification
        // t1: {1, 2, 3, 4}
        // t2: {1, 2, 3}
        // t3: {1, 2}
        //
        // For t1.i = 1:
        //   EXISTS(SELECT * FROM t2 WHERE t2.i = 1 AND EXISTS(SELECT * FROM t3 WHERE t3.i = 1))
        //   t2.i = 1 exists, t3.i = 1 exists -> TRUE
        //
        // For t1.i = 2:
        //   EXISTS(SELECT * FROM t2 WHERE t2.i = 2 AND EXISTS(SELECT * FROM t3 WHERE t3.i = 2))
        //   t2.i = 2 exists, t3.i = 2 exists -> TRUE
        //
        // For t1.i = 3:
        //   EXISTS(SELECT * FROM t2 WHERE t2.i = 3 AND EXISTS(SELECT * FROM t3 WHERE t3.i = 3))
        //   t2.i = 3 exists, but t3.i = 3 does NOT exist -> FALSE
        //
        // For t1.i = 4:
        //   EXISTS(SELECT * FROM t2 WHERE t2.i = 4 ...)
        //   t2.i = 4 does NOT exist -> FALSE
        //
        // Expected result: {1, 2}

        assertEquals(2, result.getRowCount(), "Expected 2 rows");
    }

    @Test
    public void testNestedExistsManualCheck() {
        logger.info("Manual check of nested EXISTS logic");

        // Check what's in each table
        ResultSet t1Data = engine.executeQuery("SELECT * FROM t1 ORDER BY i");
        logger.info("t1 has {} rows", t1Data.getRowCount());
        for (int i = 0; i < t1Data.getRowCount(); i++) {
            logger.info("t1[{}] = {}", i, t1Data.getRows().get(i).getValues().get(0));
        }

        ResultSet t2Data = engine.executeQuery("SELECT * FROM t2 ORDER BY i");
        logger.info("t2 has {} rows", t2Data.getRowCount());
        for (int i = 0; i < t2Data.getRowCount(); i++) {
            logger.info("t2[{}] = {}", i, t2Data.getRows().get(i).getValues().get(0));
        }

        ResultSet t3Data = engine.executeQuery("SELECT * FROM t3 ORDER BY i");
        logger.info("t3 has {} rows", t3Data.getRowCount());
        for (int i = 0; i < t3Data.getRowCount(); i++) {
            logger.info("t3[{}] = {}", i, t3Data.getRows().get(i).getValues().get(0));
        }

        // Now test the nested query
        String query = """
            SELECT *
            FROM t1
            WHERE EXISTS (
                SELECT *
                FROM t2
                WHERE t1.i = t2.i
                    AND EXISTS (
                        SELECT *
                        FROM t3
                        WHERE t2.i = t3.i
                    )
            )
            """;

        ResultSet result = engine.executeQuery(query);
        logger.info("Query result has {} rows", result.getRowCount());
        for (int i = 0; i < result.getRowCount(); i++) {
            logger.info("result[{}] = {}", i, result.getRows().get(i).getValues().get(0));
        }

        assertEquals(2, result.getRowCount());
    }
}
