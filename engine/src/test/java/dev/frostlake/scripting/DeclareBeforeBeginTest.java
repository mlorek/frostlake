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

package dev.frostlake.scripting;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests demonstrating that DECLARE section comes BEFORE BEGIN keyword
 * according to proper Snowflake SQL scripting syntax
 */
public class DeclareBeforeBeginTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(DeclareBeforeBeginTest.class);

    @Test
    public void testDeclareBeforeBegin() {
        logger.info("Testing DECLARE before BEGIN");

        engine.execute("CREATE TABLE results (value INTEGER)");

        // Correct syntax: DECLARE before BEGIN
        engine.execute("""
            DECLARE x INTEGER := 100;
            BEGIN
                INSERT INTO results VALUES (:x);
            END;
            """);

        final ResultSet rs = engine.executeQuery("SELECT * FROM results");
        assertEquals(1, rs.getRowCount());
        assertEquals(100L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void testMultipleDeclareBeforeBegin() {
        // Snowflake takes ONE DECLARE keyword followed by every declaration item; repeating the
        // keyword per item is not a spelling it accepts.
        logger.info("Testing multiple declaration items in one DECLARE section before BEGIN");

        engine.execute("CREATE TABLE results (a INTEGER, b INTEGER, c INTEGER)");

        engine.execute("""
            DECLARE
                x INTEGER := 1;
                y INTEGER := 2;
                z INTEGER := 3;
            BEGIN
                INSERT INTO results VALUES (:x, :y, :z);
            END;
            """);

        final ResultSet rs = engine.executeQuery("SELECT * FROM results");
        assertEquals(1, rs.getRowCount());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
        assertEquals(3L, ((Number) rs.getRows().get(0).getValue(2)).longValue());
    }

    @Test
    public void testTwoSeparateDeclareBeginBlocks() {
        logger.info("Testing two separate DECLARE/BEGIN blocks");

        engine.execute("CREATE TABLE results (level INTEGER, value INTEGER)");

        // First block
        engine.execute("""
            DECLARE outer INTEGER := 1;
            BEGIN
                INSERT INTO results VALUES (1, :outer);
            END;
            """);

        // Second block
        engine.execute("""
            DECLARE value2 INTEGER := 2;
            BEGIN
                INSERT INTO results VALUES (2, :value2);
            END;
            """);

        final ResultSet rs = engine.executeQuery("SELECT * FROM results ORDER BY level, value");
        assertEquals(2, rs.getRowCount());
        assertEquals(1, ((Number) rs.getRows().get(0).getValue(0)).intValue());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
        assertEquals(2, ((Number) rs.getRows().get(1).getValue(0)).intValue());
        assertEquals(2L, ((Number) rs.getRows().get(1).getValue(1)).longValue());
    }

    @Test
    public void testDeclareWithCursorBeforeBegin() {
        logger.info("Testing DECLARE with cursor before BEGIN");

        engine.execute("CREATE TABLE source (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO source VALUES (1, 'Alice'), (2, 'Bob')");
        engine.execute("CREATE TABLE results (id INTEGER, name VARCHAR)");

        engine.execute("""
            DECLARE
                cur CURSOR FOR SELECT id, name FROM source ORDER BY id;
                rec_id INTEGER;
                rec_name VARCHAR;
            BEGIN
                OPEN cur;
                FETCH cur INTO rec_id, rec_name;
                INSERT INTO results VALUES (:rec_id, :rec_name);
                CLOSE cur;
            END;
            """);

        final ResultSet rs = engine.executeQuery("SELECT * FROM results");
        assertEquals(1, rs.getRowCount());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals("Alice", rs.getRows().get(0).getValue(1));
    }

    @Test
    public void testRequestedScriptStructure() {
        logger.info("Testing the requested script structure with i + j + k = 6");

        engine.execute("CREATE TABLE results (value INTEGER)");

        // This matches the requested structure from the user
        engine.execute("""
            DECLARE i INTEGER := 1;
            BEGIN
                DECLARE j INTEGER := 2;
                BEGIN
                    LET k INTEGER := 3;
                    i := i + j + k;
                    INSERT INTO results VALUES (:i);
                END;
            END;
            """);

        final ResultSet rs = engine.executeQuery("SELECT * FROM results");
        assertEquals(1, rs.getRowCount());
        assertEquals(6L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void testExceptionHandlingWithDeclareBeforeBegin() {
        logger.info("Testing exception handling with DECLARE before BEGIN");

        engine.execute("CREATE TABLE results (value INTEGER)");

        engine.execute("""
            DECLARE x INTEGER := 42;
            BEGIN
                INSERT INTO results VALUES (:x);
                INSERT INTO nonexistent_table VALUES (1);
            EXCEPTION
                WHEN OTHER THEN
                    INSERT INTO results VALUES (999);
            END;
            """);

        final ResultSet rs = engine.executeQuery("SELECT * FROM results ORDER BY value");
        assertEquals(2, rs.getRowCount());
        assertEquals(42L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(999L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
    }
}
