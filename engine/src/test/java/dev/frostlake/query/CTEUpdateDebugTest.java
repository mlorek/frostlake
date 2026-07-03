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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CTEUpdateDebugTest {
    private static final Logger logger = LoggerFactory.getLogger(CTEUpdateDebugTest.class);
    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE TABLE test_table (id INTEGER, value VARCHAR)");
        engine.execute("INSERT INTO test_table VALUES (1, 'A')");
        engine.execute("CREATE TABLE target (id INTEGER, value VARCHAR)");
        engine.execute("INSERT INTO target VALUES (1, 'X')");
    }

    @Test
    public void testSimpleUpdate() {
        logger.info("Testing simple UPDATE");
        engine.execute("UPDATE target SET value = 'Y'");
    }

    @Test
    public void testUpdateWithSubquery() {
        logger.info("Testing UPDATE with subquery");
        engine.execute("UPDATE target SET value = (SELECT value FROM test_table WHERE id = 1)");
        assertEquals("A", engine.executeQuery("SELECT value FROM target WHERE id = 1")
            .getRows().get(0).getValue(0).toString());
    }

    @Test
    public void testUpdateWithCTE() {
        logger.info("Testing UPDATE with CTE");
        String sql = """
            WITH cte AS (
                SELECT value FROM test_table WHERE id = 1
            )
            UPDATE target SET value = (SELECT value FROM cte)
            """;
        logger.info("SQL: {}", sql);
        try {
            engine.execute(sql);
            logger.info("Success!");
        } catch (final Exception e) {
            logger.error("Failed: {}", e.getMessage());
        }
    }

    @Test
    public void testUpdateWithCTEAndQualifiedColumn() {
        logger.info("Testing UPDATE with CTE and qualified columns");
        String sql = """
            WITH cte AS (
                SELECT id, value FROM test_table WHERE id = 1
            )
            UPDATE target SET value = (SELECT value FROM cte WHERE cte.id = target.id)
            """;
        logger.info("SQL: {}", sql);
        try {
            engine.execute(sql);
            logger.info("Success!");
        } catch (final Exception e) {
            logger.error("Failed: {}", e.getMessage());
        }
    }
}
