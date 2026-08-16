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
 * Tests for nested BEGIN...END blocks with DECLARE sections
 */
public class NestedBeginEndBlockTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(NestedBeginEndBlockTest.class);

    @Test
    public void testSimpleBlockWithDeclare() {
        logger.info("Testing simple BEGIN...END block with DECLARE");

        engine.execute("CREATE TABLE results (value INTEGER)");

        engine.execute("""
            DECLARE x INTEGER := 10;
            BEGIN
                INSERT INTO results VALUES (:x);
            END;
            """);

        final ResultSet rs = engine.executeQuery("SELECT * FROM results");
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
                INSERT INTO results VALUES (:x);

                DECLARE y INTEGER := 10;
                BEGIN
                    INSERT INTO results VALUES (:y);
                    INSERT INTO results VALUES (:x);
                END;

                INSERT INTO results VALUES (:x);
            END;
            """);

        final ResultSet rs = engine.executeQuery("SELECT * FROM results ORDER BY value");
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
                INSERT INTO results VALUES (:x);

                DECLARE x INTEGER := 20;
                BEGIN
                    INSERT INTO results VALUES (:x);
                END;

                INSERT INTO results VALUES (:x);
            END;
            """);

        final ResultSet rs = engine.executeQuery("SELECT * FROM results ORDER BY value");
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
            DECLARE
                x INTEGER := 1;
                y INTEGER := 2;
                z INTEGER := 3;
            BEGIN
                INSERT INTO results VALUES (:x, :y);
                INSERT INTO results VALUES (:y, :z);
                INSERT INTO results VALUES (:z, :x);
            END;
            """);

        final ResultSet rs = engine.executeQuery("SELECT * FROM results ORDER BY id");
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
                INSERT INTO results VALUES (:x);

                DECLARE y INTEGER := 200;
                BEGIN
                    INSERT INTO results VALUES (:y);
                    -- This will cause an error
                    INSERT INTO nonexistent_table VALUES (1);
                EXCEPTION
                    WHEN OTHER THEN
                        INSERT INTO results VALUES (999);
                END;

                INSERT INTO results VALUES (:x);
            END;
            """);

        final ResultSet rs = engine.executeQuery("SELECT * FROM results ORDER BY value");
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

        // Two BEGINs and two ENDs. The block this test means to exercise is the one with no DECLARE
        // section, not an unbalanced one — that shape is refused, and is asserted below.
        engine.execute("""
            BEGIN
            BEGIN
                INSERT INTO results VALUES (42);
            END;
            END;
            """);

        final ResultSet rs = engine.executeQuery("SELECT * FROM results");
        assertEquals(1, rs.getRowCount());
        assertEquals(42L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    /**
     * A block that loses an END runs to the end of its input, and live says so there — at the position
     * one PAST the last character, which for text ending in a newline is column 0 of the next line.
     *
     * <p>★ IT IS THE ACCEPTED-BUT-INVALID DIRECTION that makes this worth refusing: a script missing an
     * END used to run here and fail on a real account. The outer BEGIN was being read as a transaction
     * start — legal on its own, which is why the shape parsed at all.
     *
     * <p>NOT COVERED HERE: a SURPLUS end, an unbalanced DECLARE block and a leading semicolon have their
     * own class (BlockBoundaryRefusalTest); a body given to CREATE PROCEDURE is not compiled at CREATE
     * at all, so an unbalanced one is still accepted there.
     */
    /** One statement's refusal, with its line break shown as a bar — the shape both backends give. */
    private String refusal(final String sql) {
        try {
            engine.execute(sql);
            return "ACCEPTED";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /**
     * A block that loses an END runs to the end of its input, and live says so there — at the position
     * one PAST the last character, which for text ending in a newline is column 0 of the next line.
     *
     * <p>★ IT IS THE ACCEPTED-BUT-INVALID DIRECTION that makes this worth refusing: a script missing an
     * END used to run here and fail on a real account. The outer BEGIN was being read as a transaction
     * start — legal on its own, which is why the shape parsed at all.
     *
     * <p>NOT COVERED HERE: a SURPLUS end and an unbalanced DECLARE block are pinned in
     * BlockBoundaryRefusalTest; a body given to CREATE PROCEDURE is not compiled at CREATE at all, so an
     * unbalanced one is still accepted there.
     */
    @Test
    public void anUnbalancedBlockRunsToTheEndOfItsInput() {
        assertEquals("SQL compilation error:|syntax error line 1 at position 27 unexpected '<EOF>'.",
            refusal("BEGIN BEGIN RETURN 42; END;"));
        assertEquals("SQL compilation error:|syntax error line 4 at position 4 unexpected '<EOF>'.",
            refusal("BEGIN\nBEGIN\n    RETURN 42;\nEND;"));
        // A trailing newline moves the anchor onto the line after the last one written.
        assertEquals("SQL compilation error:|syntax error line 6 at position 0 unexpected '<EOF>'.",
            refusal("BEGIN\nBEGIN\n    RETURN 42;\nEND;\n\n"));
        // Any body, not just a nested block — and however many BEGINs are left open.
        assertEquals("SQL compilation error:|syntax error line 2 at position 13 unexpected '<EOF>'.",
            refusal("BEGIN\n    RETURN 1;"));
        assertEquals("SQL compilation error:|syntax error line 5 at position 4 unexpected '<EOF>'.",
            refusal("BEGIN\nBEGIN\nBEGIN\n    RETURN 1;\nEND;"));
    }

    /**
     * The statements live REQUIRES a semicolon between, and the BEGIN spellings that stay legal without
     * one. A bare BEGIN is a transaction start when it IS the whole statement and a block opener when
     * anything follows it, which is why the two refusals differ.
     */
    @Test
    public void twoStatementsNeedASeparatorBetweenThem() {
        assertEquals("SQL compilation error:|syntax error line 2 at position 0 unexpected 'SELECT'.",
            refusal("SELECT 1\nSELECT 2"));
        assertEquals("SQL compilation error:|syntax error line 2 at position 0 unexpected 'BEGIN'.",
            refusal("BEGIN TRANSACTION\nBEGIN\n    RETURN 1;\nEND;"));

        // Each of these IS the whole statement, so nothing follows to need separating.
        assertEquals("ACCEPTED", refusal("BEGIN"));
        assertEquals("ACCEPTED", refusal("COMMIT"));
        assertEquals("ACCEPTED", refusal("BEGIN TRANSACTION"));
        assertEquals("ACCEPTED", refusal("COMMIT"));
        assertEquals("ACCEPTED", refusal("BEGIN WORK"));
        assertEquals("ACCEPTED", refusal("COMMIT"));
        assertEquals("ACCEPTED", refusal("BEGIN NAME t1"));
        assertEquals("ACCEPTED", refusal("COMMIT"));
    }

    @Test
    public void testDeclareWithCursor() {
        logger.info("Testing DECLARE with cursor in nested block");

        engine.execute("CREATE TABLE source (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO source VALUES (1, 'Alice'), (2, 'Bob')");
        engine.execute("CREATE TABLE results (id INTEGER, name VARCHAR)");

        engine.execute("""
            DECLARE
                cur1 CURSOR FOR SELECT id, name FROM source ORDER BY id;
                rec_id INTEGER;
                rec_name VARCHAR;
            BEGIN
                OPEN cur1;
                FETCH cur1 INTO rec_id, rec_name;

                DECLARE x INTEGER := rec_id;
                BEGIN
                    INSERT INTO results VALUES (:x, :rec_name);
                END;

                CLOSE cur1;
            END;
            """);

        final ResultSet rs = engine.executeQuery("SELECT * FROM results");
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
                INSERT INTO results VALUES (1, :a);

                DECLARE b INTEGER := 2;
                BEGIN
                    INSERT INTO results VALUES (2, :b);

                    DECLARE c INTEGER := 3;
                    BEGIN
                        INSERT INTO results VALUES (3, :c);
                        INSERT INTO results VALUES (3, :a);
                    END;
                END;
            END;
            """);

        final ResultSet rs = engine.executeQuery("SELECT * FROM results ORDER BY level, value");
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

    // ---- RETURN from inside a nested BEGIN...END block ----
    // A RETURN in a nested block used to be swallowed (the nested block handler consumed the return and
    // wrapped it into a result the enclosing block discarded), so the procedure returned nothing and the
    // CALL result had 0 rows ("Index 0 out of bounds for length 0").

    private String callScalar(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void testReturnFromNestedBlock() {
        logger.info("Testing RETURN from a nested BEGIN...END block");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p_nested_ret() RETURNS STRING LANGUAGE SQL AS $$
            BEGIN
                BEGIN
                    RETURN 'inner';
                END;
            END $$""");
        assertEquals("inner", callScalar("CALL p_nested_ret()"));
    }

    @Test
    public void testReturnFromNestedBlockWithDeclare() {
        logger.info("Testing RETURN of a computed value from a nested block with its own DECLARE");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p_nested_decl_ret() RETURNS STRING LANGUAGE SQL AS $$
            BEGIN
                DECLARE a STRING := 'x';
                BEGIN
                    a := a || 'y';
                    RETURN :a;
                END;
            END $$""");
        assertEquals("xy", callScalar("CALL p_nested_decl_ret()"));
    }

    @Test
    public void testReturnFromExceptionHandlerNestedBlock() {
        logger.info("Testing RETURN from a nested block inside an exception handler");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p_handler_ret() RETURNS STRING LANGUAGE SQL AS $$
            DECLARE ex EXCEPTION (-20002, 'boom');
            BEGIN
                RAISE ex;
            EXCEPTION WHEN OTHER THEN
                DECLARE msg STRING := 'caught';
                BEGIN
                    RETURN :msg;
                END;
            END $$""");
        assertEquals("caught", callScalar("CALL p_handler_ret()"));
    }

    @Test
    public void testReturnFromDeeplyNestedBlock() {
        logger.info("Testing RETURN from a 3-level-deep nested block");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p_deep_ret() RETURNS INTEGER LANGUAGE SQL AS $$
            BEGIN
                BEGIN
                    BEGIN
                        RETURN 42;
                    END;
                END;
            END $$""");
        assertEquals(42, ((Number) engine.executeQuery("CALL p_deep_ret()").getRows().get(0).getValue(0)).intValue());
    }

    @Test
    public void testStatementsAfterNestedReturnedBlockDoNotRun() {
        logger.info("Testing that a RETURN in a nested block stops the enclosing block");
        engine.execute("CREATE TABLE after_ret (v INTEGER)");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p_stop() RETURNS STRING LANGUAGE SQL AS $$
            BEGIN
                BEGIN
                    RETURN 'done';
                END;
                INSERT INTO after_ret VALUES (1);
            END $$""");
        assertEquals("done", callScalar("CALL p_stop()"));
        // The INSERT after the nested block must NOT have run — the RETURN stopped the enclosing block.
        assertEquals(0L, ((Number) engine.executeQuery("SELECT COUNT(*) FROM after_ret").getRows().get(0).getValue(0)).longValue());
    }
}
