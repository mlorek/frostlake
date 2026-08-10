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

package dev.frostlake.dml;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for MERGE statement DELETE operation
 */
public class MergeDeleteTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(MergeDeleteTest.class);

    @Test
    public void testMergeWithDeleteNoCondition() {
        logger.info("Testing MERGE with DELETE without additional condition");

        engine.execute("CREATE TABLE target (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO target VALUES (1, 'Alice')");
        engine.execute("INSERT INTO target VALUES (2, 'Bob')");
        engine.execute("INSERT INTO target VALUES (3, 'Charlie')");

        engine.execute("CREATE TABLE source (id INTEGER)");
        engine.execute("INSERT INTO source VALUES (1)");
        engine.execute("INSERT INTO source VALUES (2)");

        // Delete all matched rows
        engine.execute("""
            MERGE INTO target
            USING source
            ON target.id = source.id
            WHEN MATCHED THEN DELETE
            """);

        final ResultSet rs = engine.executeQuery("SELECT * FROM target ORDER BY id");
        assertEquals(1, rs.getRowCount());
        assertEquals(3L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals("Charlie", rs.getRows().get(0).getValue(1));
    }

    @Test
    public void testMergeWithMultipleDeletes() {
        logger.info("Testing MERGE with multiple rows to delete");

        engine.execute("CREATE TABLE target (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO target VALUES (1, 'Alice')");
        engine.execute("INSERT INTO target VALUES (2, 'Bob')");
        engine.execute("INSERT INTO target VALUES (3, 'Charlie')");
        engine.execute("INSERT INTO target VALUES (4, 'Diana')");
        engine.execute("INSERT INTO target VALUES (5, 'Eve')");

        engine.execute("CREATE TABLE source (id INTEGER)");
        engine.execute("INSERT INTO source VALUES (1)");
        engine.execute("INSERT INTO source VALUES (3)");
        engine.execute("INSERT INTO source VALUES (5)");

        engine.execute("""
            MERGE INTO target
            USING source
            ON target.id = source.id
            WHEN MATCHED THEN DELETE
            """);

        final ResultSet rs = engine.executeQuery("SELECT * FROM target ORDER BY id");
        assertEquals(2, rs.getRowCount());
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals("Bob", rs.getRows().get(0).getValue(1));
        assertEquals(4L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
        assertEquals("Diana", rs.getRows().get(1).getValue(1));
    }
}
