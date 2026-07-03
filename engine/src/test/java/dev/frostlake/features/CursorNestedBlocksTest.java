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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for cursor operations inside nested BEGIN...END blocks.
 */
public class CursorNestedBlocksTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) engine.shutdown();
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private String exec(final String block) {
        ResultSet rs = engine.executeQuery("EXECUTE IMMEDIATE $$\n" + block + "\n$$");
        assertNotNull(rs);
        assertFalse(rs.getRows().isEmpty(), "Expected a RETURN value but got no rows");
        Object val = rs.getRows().get(0).getValue(0);
        return val == null ? "null" : val.toString();
    }

    private int execInt(final String block) {
        return (int) Double.parseDouble(exec(block));
    }

    // ── 1. Cursor declared and fully iterated inside an inner block ───────────

    @Test
    public void testCursorDeclaredAndIteratedInInnerBlock() {
        engine.execute("CREATE TABLE nums (v INT)");
        engine.execute("INSERT INTO nums VALUES (1),(2),(3)");

        assertEquals(6, execInt(
            "DECLARE total INT DEFAULT 0;\n" +
            "BEGIN\n" +
            "    BEGIN\n" +
            "        LET cur CURSOR FOR SELECT v FROM test_db.public.nums;\n" +
            "        FOR rec IN cur DO\n" +
            "            total := :total + rec.V;\n" +
            "        END FOR;\n" +
            "        CLOSE cur;\n" +
            "    END;\n" +
            "    RETURN :total;\n" +
            "END"
        ));
    }

    // ── 2. Outer variable accumulated across inner-block cursor loop ──────────

    @Test
    public void testOuterVariableAccumulatedByInnerCursorLoop() {
        engine.execute("CREATE TABLE words (w VARCHAR)");
        engine.execute("INSERT INTO words VALUES ('hello'),('world')");

        assertEquals("hello,world,", exec(
            "DECLARE result VARCHAR DEFAULT '';\n" +
            "BEGIN\n" +
            "    BEGIN\n" +
            "        LET cur CURSOR FOR SELECT w FROM test_db.public.words ORDER BY w;\n" +
            "        FOR rec IN cur DO\n" +
            "            result := :result || rec.W || ',';\n" +
            "        END FOR;\n" +
            "        CLOSE cur;\n" +
            "    END;\n" +
            "    RETURN :result;\n" +
            "END"
        ));
    }

    // ── 3. Sequential cursors in sequential inner blocks ─────────────────────

    @Test
    public void testSequentialCursorsInSequentialBlocks() {
        engine.execute("CREATE TABLE a_vals (x INT)");
        engine.execute("CREATE TABLE b_vals (x INT)");
        engine.execute("INSERT INTO a_vals VALUES (10)");
        engine.execute("INSERT INTO b_vals VALUES (20)");

        assertEquals(30, execInt(
            "DECLARE total INT DEFAULT 0;\n" +
            "BEGIN\n" +
            "    BEGIN\n" +
            "        LET c1 CURSOR FOR SELECT x FROM test_db.public.a_vals;\n" +
            "        FOR rec IN c1 DO\n" +
            "            total := :total + rec.X;\n" +
            "        END FOR;\n" +
            "        CLOSE c1;\n" +
            "    END;\n" +
            "    BEGIN\n" +
            "        LET c2 CURSOR FOR SELECT x FROM test_db.public.b_vals;\n" +
            "        FOR rec IN c2 DO\n" +
            "            total := :total + rec.X;\n" +
            "        END FOR;\n" +
            "        CLOSE c2;\n" +
            "    END;\n" +
            "    RETURN :total;\n" +
            "END"
        ));
    }

    // ── 4. Cursor inside IF body (conditional iteration) ─────────────────────

    @Test
    public void testCursorInsideIfBodyExecutesWhenTrue() {
        engine.execute("CREATE TABLE cond_data (v INT)");
        engine.execute("INSERT INTO cond_data VALUES (5)");

        assertEquals(5, execInt(
            "DECLARE result INT DEFAULT 0;\n" +
            "BEGIN\n" +
            "    IF (1 = 1) THEN\n" +
            "        LET cur CURSOR FOR SELECT v FROM test_db.public.cond_data;\n" +
            "        FOR rec IN cur DO\n" +
            "            result := rec.V;\n" +
            "        END FOR;\n" +
            "        CLOSE cur;\n" +
            "    END IF;\n" +
            "    RETURN :result;\n" +
            "END"
        ));
    }

    @Test
    public void testCursorInsideIfBodySkippedWhenFalse() {
        engine.execute("CREATE TABLE cond_data2 (v INT)");
        engine.execute("INSERT INTO cond_data2 VALUES (5)");

        assertEquals(0, execInt(
            "DECLARE result INT DEFAULT 0;\n" +
            "BEGIN\n" +
            "    IF (1 = 2) THEN\n" +
            "        LET cur CURSOR FOR SELECT v FROM test_db.public.cond_data2;\n" +
            "        FOR rec IN cur DO\n" +
            "            result := rec.V;\n" +
            "        END FOR;\n" +
            "        CLOSE cur;\n" +
            "    END IF;\n" +
            "    RETURN :result;\n" +
            "END"
        ));
    }

    // ── 5. Multiple cursors in same block ─────────────────────────────────────

    @Test
    public void testMultipleCursorsInSameBlock() {
        engine.execute("CREATE TABLE mc_a (n INT)");
        engine.execute("CREATE TABLE mc_b (n INT)");
        engine.execute("INSERT INTO mc_a VALUES (3)");
        engine.execute("INSERT INTO mc_b VALUES (4)");

        // Sum approach: 3 + 4 = 7, avoids String-to-Number cast in multiplication
        assertEquals(7, execInt(
            "DECLARE total INT DEFAULT 0;\n" +
            "BEGIN\n" +
            "    LET c1 CURSOR FOR SELECT n FROM test_db.public.mc_a;\n" +
            "    LET c2 CURSOR FOR SELECT n FROM test_db.public.mc_b;\n" +
            "    FOR r1 IN c1 DO\n" +
            "        total := :total + r1.N;\n" +
            "    END FOR;\n" +
            "    CLOSE c1;\n" +
            "    FOR r2 IN c2 DO\n" +
            "        total := :total + r2.N;\n" +
            "    END FOR;\n" +
            "    CLOSE c2;\n" +
            "    RETURN :total;\n" +
            "END"
        ));
    }

    // ── 6. Cursor declared in outer, iterated in inner block ─────────────────

    @Test
    public void testCursorDeclaredOuterIteratedInner() {
        engine.execute("CREATE TABLE outer_cur (v INT)");
        engine.execute("INSERT INTO outer_cur VALUES (7),(8)");

        assertEquals(15, execInt(
            "DECLARE total INT DEFAULT 0;\n" +
            "BEGIN\n" +
            "    LET cur CURSOR FOR SELECT v FROM test_db.public.outer_cur;\n" +
            "    BEGIN\n" +
            "        FOR rec IN cur DO\n" +
            "            total := :total + rec.V;\n" +
            "        END FOR;\n" +
            "        CLOSE cur;\n" +
            "    END;\n" +
            "    RETURN :total;\n" +
            "END"
        ));
    }

    // ── 7. Nested FOR loops — inner cursor re-opened each outer iteration ──────

    @Test
    public void testNestedForLoopsWithIndependentCursors() {
        engine.execute("CREATE TABLE nfl_labels (label VARCHAR)");
        engine.execute("CREATE TABLE nfl_single (cnt INT)");
        engine.execute("INSERT INTO nfl_labels VALUES ('A'),('B')");
        engine.execute("INSERT INTO nfl_single VALUES (1)");

        // Single-row inner table: each outer iteration visits cy once → "1,1,"
        assertEquals("1,1,", exec(
            "DECLARE result VARCHAR DEFAULT '';\n" +
            "BEGIN\n" +
            "    LET cx CURSOR FOR SELECT label FROM test_db.public.nfl_labels ORDER BY label;\n" +
            "    FOR rx IN cx DO\n" +
            "        BEGIN\n" +
            "            LET cy CURSOR FOR SELECT cnt FROM test_db.public.nfl_single;\n" +
            "            FOR ry IN cy DO\n" +
            "                result := :result || ry.CNT || ',';\n" +
            "            END FOR;\n" +
            "            CLOSE cy;\n" +
            "        END;\n" +
            "    END FOR;\n" +
            "    CLOSE cx;\n" +
            "    RETURN :result;\n" +
            "END"
        ));
    }

    // ── 8. Cursor accumulation through triple-nested blocks ───────────────────

    @Test
    public void testCursorAccumulationThroughTripleNesting() {
        engine.execute("CREATE TABLE deep_vals (v INT)");
        engine.execute("INSERT INTO deep_vals VALUES (100)");

        assertEquals(100, execInt(
            "DECLARE result INT DEFAULT 0;\n" +
            "BEGIN\n" +
            "    BEGIN\n" +
            "        BEGIN\n" +
            "            LET cur CURSOR FOR SELECT v FROM test_db.public.deep_vals;\n" +
            "            FOR rec IN cur DO\n" +
            "                result := rec.V;\n" +
            "            END FOR;\n" +
            "            CLOSE cur;\n" +
            "        END;\n" +
            "    END;\n" +
            "    RETURN :result;\n" +
            "END"
        ));
    }

    // ── 9. Empty cursor result leaves variable unchanged ──────────────────────

    @Test
    public void testEmptyCursorLeavesVariableUnchanged() {
        engine.execute("CREATE TABLE empty_tbl (v INT)");

        assertEquals("default", exec(
            "DECLARE result VARCHAR DEFAULT 'default';\n" +
            "BEGIN\n" +
            "    BEGIN\n" +
            "        LET cur CURSOR FOR SELECT v FROM test_db.public.empty_tbl;\n" +
            "        FOR rec IN cur DO\n" +
            "            result := 'changed';\n" +
            "        END FOR;\n" +
            "        CLOSE cur;\n" +
            "    END;\n" +
            "    RETURN :result;\n" +
            "END"
        ));
    }

    // ── 10. Cursor name re-used across sequential blocks (no leakage) ─────────

    @Test
    public void testCursorNameReusedAcrossSequentialBlocks() {
        engine.execute("CREATE TABLE seq1 (v INT)");
        engine.execute("CREATE TABLE seq2 (v INT)");
        engine.execute("INSERT INTO seq1 VALUES (5)");
        engine.execute("INSERT INTO seq2 VALUES (15)");

        // Both blocks declare 'cur' — they must not see each other's cursor
        assertEquals(20, execInt(
            "DECLARE total INT DEFAULT 0;\n" +
            "BEGIN\n" +
            "    BEGIN\n" +
            "        LET cur CURSOR FOR SELECT v FROM test_db.public.seq1;\n" +
            "        FOR rec IN cur DO total := :total + rec.V; END FOR;\n" +
            "        CLOSE cur;\n" +
            "    END;\n" +
            "    BEGIN\n" +
            "        LET cur CURSOR FOR SELECT v FROM test_db.public.seq2;\n" +
            "        FOR rec IN cur DO total := :total + rec.V; END FOR;\n" +
            "        CLOSE cur;\n" +
            "    END;\n" +
            "    RETURN :total;\n" +
            "END"
        ));
    }

    // ── 11. Cursor with WHERE filter in inner block ───────────────────────────

    @Test
    public void testCursorWithWhereFilterInInnerBlock() {
        engine.execute("CREATE TABLE filter_data (id INT, active BOOLEAN)");
        engine.execute("INSERT INTO filter_data VALUES (1, TRUE),(2, FALSE),(3, TRUE)");

        assertEquals(2, execInt(
            "DECLARE cnt INT DEFAULT 0;\n" +
            "BEGIN\n" +
            "    BEGIN\n" +
            "        LET cur CURSOR FOR SELECT id FROM test_db.public.filter_data WHERE active = TRUE;\n" +
            "        FOR rec IN cur DO\n" +
            "            cnt := :cnt + 1;\n" +
            "        END FOR;\n" +
            "        CLOSE cur;\n" +
            "    END;\n" +
            "    RETURN :cnt;\n" +
            "END"
        ));
    }

    // ── 12. Cursor in ELSE branch only ────────────────────────────────────────

    @Test
    public void testCursorInElseBranchOnly() {
        engine.execute("CREATE TABLE else_data (v VARCHAR)");
        engine.execute("INSERT INTO else_data VALUES ('found')");

        assertEquals("found", exec(
            "DECLARE result VARCHAR DEFAULT 'nothing';\n" +
            "BEGIN\n" +
            "    IF (1 = 2) THEN\n" +
            "        result := 'wrong';\n" +
            "    ELSE\n" +
            "        LET cur CURSOR FOR SELECT v FROM test_db.public.else_data;\n" +
            "        FOR rec IN cur DO\n" +
            "            result := rec.V;\n" +
            "        END FOR;\n" +
            "        CLOSE cur;\n" +
            "    END IF;\n" +
            "    RETURN :result;\n" +
            "END"
        ));
    }
}
