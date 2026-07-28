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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Grammar/executor gaps in Snowflake Scripting stored procedures:
 * <ul>
 *   <li>untyped DECLARE items ({@code v := expr;} — the type is inferred from the expression),</li>
 *   <li>stray empty statements (a lone {@code ;}) between procedure statements,</li>
 *   <li>{@code OBJECT_CONSTRUCT(*)} / {@code OBJECT_CONSTRUCT(* EXCLUDE cols)} star expansion.</li>
 * </ul>
 */
public class ProceduralSqlGapTest {

    private static final Logger logger = LoggerFactory.getLogger(ProceduralSqlGapTest.class);
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
    public void testUntypedDeclareInfersType() {
        logger.info("Testing untyped DECLARE items (type inferred from the initializer)");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p_untyped()
                RETURNS VARCHAR
                LANGUAGE SQL
            AS
            $$
            DECLARE
                cid := 'abc-123';
                n := 7;
            BEGIN
                RETURN :cid || '/' || :n;
            END
            $$
            """);
        final ResultSet rs = engine.executeQuery("CALL p_untyped()");
        assertEquals("abc-123/7", String.valueOf(rs.getRows().get(0).getValue(0)));
    }

    @Test
    public void testStrayEmptyStatementIsNoOp() {
        logger.info("Testing that a lone ';' in a procedure body is a no-op");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p_empty()
                RETURNS INTEGER
                LANGUAGE SQL
            AS
            $$
            DECLARE
                x INTEGER DEFAULT 1;
            BEGIN
                x := 41;
                ;
                x := x + 1;
                ;
                RETURN :x;
            END
            $$
            """);
        final ResultSet rs = engine.executeQuery("CALL p_empty()");
        assertEquals(42L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void testCallResultColumnNamedAfterProcedure() {
        logger.info("Testing that a CALL's result column is named after the procedure (Snowflake behavior)");
        // A caller that reads a proc's result by column name (e.g. fetch("CALL x_test()")[0]["X_TEST"])
        // relies on the CALL result column carrying the procedure's (uppercase, simple) name — not a
        // generic one.
        engine.execute("""
            CREATE OR REPLACE PROCEDURE loader_demo_test()
                RETURNS OBJECT
                LANGUAGE SQL
            AS
            $$
            BEGIN
                RETURN OBJECT_CONSTRUCT('result', 'passed');
            END
            $$
            """);
        final ResultSet rs = engine.executeQuery("CALL loader_demo_test()");
        assertEquals("LOADER_DEMO_TEST", rs.getColumns().get(0).getName());
    }

    @Test
    public void testObjectConstructStarExpandsAllColumns() {
        logger.info("Testing OBJECT_CONSTRUCT(*) expands to all row columns");
        engine.execute("CREATE TABLE t_star (a INTEGER, b INTEGER, src INTEGER)");
        engine.execute("INSERT INTO t_star VALUES (1, 2, 3)");
        final ResultSet rs = engine.executeQuery("SELECT OBJECT_CONSTRUCT(*) AS o FROM t_star");
        final String o = String.valueOf(rs.getRows().get(0).getValue(0));
        assertTrue(o.contains("\"A\":1"), o);
        assertTrue(o.contains("\"B\":2"), o);
        assertTrue(o.contains("\"SRC\":3"), o);
    }

    @Test
    public void testObjectConstructStarExcludeSingle() {
        logger.info("Testing OBJECT_CONSTRUCT(* EXCLUDE col)");
        engine.execute("CREATE TABLE t_excl (a INTEGER, b INTEGER, src INTEGER)");
        engine.execute("INSERT INTO t_excl VALUES (1, 2, 3)");
        final ResultSet rs = engine.executeQuery("SELECT OBJECT_CONSTRUCT(* EXCLUDE src) AS o FROM t_excl");
        final String o = String.valueOf(rs.getRows().get(0).getValue(0));
        assertTrue(o.contains("\"A\":1"), o);
        assertTrue(o.contains("\"B\":2"), o);
        assertFalse(o.contains("SRC"), o);
    }

    @Test
    public void testObjectConstructStarExcludeMultiple() {
        logger.info("Testing OBJECT_CONSTRUCT(* EXCLUDE (a, b))");
        engine.execute("CREATE TABLE t_excl2 (a INTEGER, b INTEGER, src INTEGER)");
        engine.execute("INSERT INTO t_excl2 VALUES (1, 2, 3)");
        final ResultSet rs = engine.executeQuery("SELECT OBJECT_CONSTRUCT(* EXCLUDE (a, b)) AS o FROM t_excl2");
        final String o = String.valueOf(rs.getRows().get(0).getValue(0));
        assertTrue(o.contains("\"SRC\":3"), o);
        assertFalse(o.contains("\"A\":"), o);
        assertFalse(o.contains("\"B\":"), o);
    }
}
