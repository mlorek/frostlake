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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for nested BEGIN...END blocks with DECLARE sections
 */
public class NestedBeginEndBlockTest {

    private static final Logger logger = LoggerFactory.getLogger(NestedBeginEndBlockTest.class);
    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testSimpleBlockWithDeclare() {
        logger.info("Testing simple BEGIN...END block with DECLARE");

        engine.execute("CREATE TABLE results (value INTEGER)");

        engine.execute("""
            DECLARE x INTEGER := 10;
            BEGIN
                INSERT INTO results VALUES (x);
            END;
            """);

        ResultSet rs = engine.executeQuery("SELECT * FROM results");
        assertEquals(1, rs.getRowCount());
        assertEquals(10L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void testNestedBlocksWithDeclare() {
        logger.info("Testing nested BEGIN...END blocks with DECLARE");

        engine.execute("CREATE TABLE results (value INTEGER)");

        engine.execute("""
            DECLARE x INTEGER := 5;
            BEGIN
                INSERT INTO results VALUES (x);

                DECLARE y INTEGER := 10;
                BEGIN
                    INSERT INTO results VALUES (y);
                    INSERT INTO results VALUES (x);
                END;

                INSERT INTO results VALUES (x);
            END;
            """);

        ResultSet rs = engine.executeQuery("SELECT * FROM results ORDER BY value");
        assertEquals(4, rs.getRowCount());
        assertEquals(5L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(5L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
        assertEquals(5L, ((Number) rs.getRows().get(2).getValue(0)).longValue());
        assertEquals(10L, ((Number) rs.getRows().get(3).getValue(0)).longValue());
    }

    @Test
    public void testVariableShadowing() {
        logger.info("Testing variable shadowing in nested blocks");

        engine.execute("CREATE TABLE results (value INTEGER)");

        engine.execute("""
            DECLARE x INTEGER := 5;
            BEGIN
                INSERT INTO results VALUES (x);

                DECLARE x INTEGER := 20;
                BEGIN
                    INSERT INTO results VALUES (x);
                END;

                INSERT INTO results VALUES (x);
            END;
            """);

        ResultSet rs = engine.executeQuery("SELECT * FROM results ORDER BY value");
        logger.info("Row count: {}", rs.getRowCount());
        for (int i = 0; i < rs.getRowCount(); i++) {
            logger.info("Row {}: {}", i, rs.getRows().get(i).getValue(0));
        }
        assertEquals(3, rs.getRowCount());
        // `DECLARE x := 20; BEGIN … END;` is a NESTED block (Snowflake semantics): x:=20 is scoped to
        // it, so the third INSERT — which is AFTER that block, back in the outer scope — sees x = 5.
        // Inserts are 5, 20, 5 → sorted [5, 5, 20].
        assertEquals(5L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(5L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
        assertEquals(20L, ((Number) rs.getRows().get(2).getValue(0)).longValue());
    }

    @Test
    public void testMultipleVariablesInDeclareSection() {
        logger.info("Testing multiple variables in DECLARE section");

        engine.execute("CREATE TABLE results (id INTEGER, value INTEGER)");

        engine.execute("""
            DECLARE x INTEGER := 1;
            DECLARE y INTEGER := 2;
            DECLARE z INTEGER := 3;
            BEGIN
                INSERT INTO results VALUES (x, y);
                INSERT INTO results VALUES (y, z);
                INSERT INTO results VALUES (z, x);
            END;
            """);

        ResultSet rs = engine.executeQuery("SELECT * FROM results ORDER BY id");
        assertEquals(3, rs.getRowCount());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
        assertEquals(2L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
        assertEquals(3L, ((Number) rs.getRows().get(1).getValue(1)).longValue());
        assertEquals(3L, ((Number) rs.getRows().get(2).getValue(0)).longValue());
        assertEquals(1L, ((Number) rs.getRows().get(2).getValue(1)).longValue());
    }

    @Test
    public void testNestedBlocksWithException() {
        logger.info("Testing nested blocks with exception handling");

        engine.execute("CREATE TABLE results (value INTEGER)");

        engine.execute("""
            DECLARE x INTEGER := 100;
            BEGIN
                INSERT INTO results VALUES (x);

                DECLARE y INTEGER := 200;
                BEGIN
                    INSERT INTO results VALUES (y);
                    -- This will cause an error
                    INSERT INTO nonexistent_table VALUES (1);
                EXCEPTION
                    WHEN OTHER THEN
                        INSERT INTO results VALUES (999);
                END;

                INSERT INTO results VALUES (x);
            END;
            """);

        ResultSet rs = engine.executeQuery("SELECT * FROM results ORDER BY value");
        assertEquals(4, rs.getRowCount());
        assertEquals(100L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(100L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
        assertEquals(200L, ((Number) rs.getRows().get(2).getValue(0)).longValue());
        assertEquals(999L, ((Number) rs.getRows().get(3).getValue(0)).longValue());
    }

    @Test
    public void testBlockWithoutDeclare() {
        logger.info("Testing BEGIN...END block without DECLARE section");

        engine.execute("CREATE TABLE results (value INTEGER)");

        engine.execute("""
            BEGIN
            BEGIN
                INSERT INTO results VALUES (42);
            END;
            """);

        ResultSet rs = engine.executeQuery("SELECT * FROM results");
        assertEquals(1, rs.getRowCount());
        assertEquals(42L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void testDeclareWithCursor() {
        logger.info("Testing DECLARE with cursor in nested block");

        engine.execute("CREATE TABLE source (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO source VALUES (1, 'Alice'), (2, 'Bob')");
        engine.execute("CREATE TABLE results (id INTEGER, name VARCHAR)");

        engine.execute("""
            DECLARE cur1 CURSOR FOR SELECT id, name FROM source;
            DECLARE rec_id INTEGER;
            DECLARE rec_name VARCHAR;
            BEGIN
                OPEN cur1;
                FETCH cur1 INTO rec_id, rec_name;

                DECLARE x INTEGER := rec_id;
                BEGIN
                    INSERT INTO results VALUES (x, rec_name);
                END;

                CLOSE cur1;
            END;
            """);

        ResultSet rs = engine.executeQuery("SELECT * FROM results");
        assertEquals(1, rs.getRowCount());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals("Alice", rs.getRows().get(0).getValue(1));
    }

    @Test
    public void testThreeLevelNesting() {
        logger.info("Testing three-level nested blocks");

        engine.execute("CREATE TABLE results (level INTEGER, value INTEGER)");

        engine.execute("""
            DECLARE a INTEGER := 1;
            BEGIN
                INSERT INTO results VALUES (1, a);

                DECLARE b INTEGER := 2;
                BEGIN
                    INSERT INTO results VALUES (2, b);

                    DECLARE c INTEGER := 3;
                    BEGIN
                        INSERT INTO results VALUES (3, c);
                        INSERT INTO results VALUES (3, a);
                    END;
                END;
            END;
            """);

        ResultSet rs = engine.executeQuery("SELECT * FROM results ORDER BY level, value");
        assertEquals(4, rs.getRowCount());
        assertEquals(1, ((Number) rs.getRows().get(0).getValue(0)).intValue());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
        assertEquals(2, ((Number) rs.getRows().get(1).getValue(0)).intValue());
        assertEquals(2L, ((Number) rs.getRows().get(1).getValue(1)).longValue());
        assertEquals(3, ((Number) rs.getRows().get(2).getValue(0)).intValue());
        assertEquals(1L, ((Number) rs.getRows().get(2).getValue(1)).longValue());
        assertEquals(3, ((Number) rs.getRows().get(3).getValue(0)).intValue());
        assertEquals(3L, ((Number) rs.getRows().get(3).getValue(1)).longValue());
    }
}
