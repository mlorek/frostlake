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
 * Tests for LET statement and := assignment operator
 */
public class LetAndAssignmentTest {

    private static final Logger logger = LoggerFactory.getLogger(LetAndAssignmentTest.class);
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
    public void testLetStatement() {
        logger.info("Testing LET statement");

        engine.execute("CREATE TABLE results (value INTEGER)");

        engine.execute("""
            LET x INTEGER := 42;
            BEGIN
                INSERT INTO results VALUES (x);
            END;
            """);

        ResultSet rs = engine.executeQuery("SELECT * FROM results");
        assertEquals(1, rs.getRowCount());
        assertEquals(42L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void testAssignmentOperator() {
        logger.info("Testing := assignment operator");

        engine.execute("CREATE TABLE results (value INTEGER)");

        engine.execute("""
            DECLARE x INTEGER := 10;
            x := 20;
            INSERT INTO results VALUES (x);
            """);

        ResultSet rs = engine.executeQuery("SELECT * FROM results");
        assertEquals(1, rs.getRowCount());
        assertEquals(20L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void testSetWithColonEquals() {
        logger.info("Testing SET with := syntax");

        engine.execute("CREATE TABLE results (value INTEGER)");

        engine.execute("""
            DECLARE x INTEGER := 10;
            SET x := 30;
            INSERT INTO results VALUES (x);
            """);

        ResultSet rs = engine.executeQuery("SELECT * FROM results");
        assertEquals(1, rs.getRowCount());
        assertEquals(30L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void testNestedBlocksWithLetAndAssignment() {
        logger.info("Testing nested blocks with LET and := assignment - expected value 6");

        engine.execute("CREATE TABLE results (value INTEGER)");

        engine.execute("""
            DECLARE i INTEGER := 1;
            BEGIN
                DECLARE j INTEGER := 2;
                BEGIN
                    LET k INTEGER := 3;
                    i := i + j + k;
                    INSERT INTO results VALUES (i);
                END;
            END;
            """);

        ResultSet rs = engine.executeQuery("SELECT * FROM results");
        assertEquals(1, rs.getRowCount());
        assertEquals(6L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void testMultipleAssignments() {
        logger.info("Testing multiple assignments");

        engine.execute("CREATE TABLE results (id INTEGER, value INTEGER)");

        engine.execute("""
            DECLARE x INTEGER := 5;
            DECLARE y INTEGER := 10;
            x := x + 1;
            y := y * 2;
            INSERT INTO results VALUES (x, y);
            """);

        ResultSet rs = engine.executeQuery("SELECT * FROM results");
        assertEquals(1, rs.getRowCount());
        assertEquals(6L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(20L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
    }

    @Test
    public void testLetInNestedScope() {
        logger.info("Testing LET in nested scope");

        engine.execute("CREATE TABLE results (value INTEGER)");

        engine.execute("""
            DECLARE x INTEGER := 1;
            BEGIN
                INSERT INTO results VALUES (x);
            END;
            LET x INTEGER := 100;
            BEGIN
                INSERT INTO results VALUES (x);
            END;
            """);

        ResultSet rs = engine.executeQuery("SELECT * FROM results ORDER BY value");
        assertEquals(2, rs.getRowCount());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(100L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
    }

    @Test
    public void testMixedDeclareLetAndAssignment() {
        logger.info("Testing mixed DECLARE, LET and assignments");

        engine.execute("CREATE TABLE results (value INTEGER)");

        engine.execute("""
            DECLARE a INTEGER := 1;
            BEGIN
                DECLARE b INTEGER := 2;
                BEGIN
                    LET c INTEGER := 3;
                    a := a + b;
                    b := b + c;
                    c := a + b + c;
                    INSERT INTO results VALUES (c);
                END;
            END;
            """);

        ResultSet rs = engine.executeQuery("SELECT * FROM results");
        assertEquals(1, rs.getRowCount());
        // a = 1, b = 2, c = 3
        // a := a + b => a = 3
        // b := b + c => b = 5
        // c := a + b + c => c = 3 + 5 + 3 = 11
        assertEquals(11L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void testComplexExpressionInAssignment() {
        logger.info("Testing complex expression in assignment");

        engine.execute("CREATE TABLE results (value INTEGER)");

        engine.execute("""
            DECLARE x INTEGER := 10;
            DECLARE y INTEGER := 5;
            BEGIN
                LET result INTEGER := 0;
                result := (x + y) * 2 - 10;
                INSERT INTO results VALUES (result);
            END;
            """);

        ResultSet rs = engine.executeQuery("SELECT * FROM results");
        assertEquals(1, rs.getRowCount());
        // (10 + 5) * 2 - 10 = 15 * 2 - 10 = 30 - 10 = 20
        assertEquals(20L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }
}
