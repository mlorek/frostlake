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

package dev.frostlake.expressions;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Characterization (golden) baseline for expression evaluation.
 *
 * <p>Pins the evaluated result of representative expressions (via {@code SELECT <expr>}) so
 * regressions in the ANTLR-driven expression AST surface as value changes.
 *
 * <p>Deliberately NOT on the live surface: an own-engine golden pin whose value is catching
 * embedded evaluator drift cheaply — expression semantics themselves are live-verified by the
 * two-sided suites.
 */
public class ExpressionCharacterizationTest {

    private static DatabaseEngine engine;

    @BeforeAll
    public static void setup() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @AfterAll
    public static void teardown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private static Object eval(final String expr) {
        final ResultSet rs = engine.executeQuery("SELECT " + expr);
        return rs.getRows().get(0).getValue(0);
    }

    @Test
    public void testArithmetic() {
        assertEquals(7L, eval("1 + 2 * 3"));
        assertEquals(9L, eval("(1 + 2) * 3"));
        assertEquals(42L, eval("100 - 58"));
    }

    /**
     * {@code 10 - 4 - 2} is now correctly left-associative: {@code (10 - 4) - 2 = 4}.
     * Keyword-free expressions are evaluated via the ANTLR grammar + builder. (The legacy
     * hand-rolled parser returned 8, treating the chain as {@code 10 - (4 - 2)}.)
     */
    @Test
    public void testLeftAssociativityFixed() {
        assertEquals(4L, eval("10 - 4 - 2"));
    }

    @Test
    public void testStringConcat() {
        assertEquals("foobar", eval("'foo' || 'bar'"));
    }

    @Test
    public void testComparisons() {
        assertEquals(true, eval("2 > 1"));
        assertEquals(true, eval("10 = 10"));
        assertEquals(true, eval("5 >= 5"));
    }

    @Test
    public void testLogical() {
        assertEquals(true, eval("(1 = 1) AND (2 = 2)"));
        assertEquals(false, eval("(1 = 1) AND (2 = 3)"));
        assertEquals(true, eval("(1 = 2) OR (5 = 5)"));
        assertEquals(true, eval("NOT (1 = 2)"));
    }

    @Test
    public void testLike() {
        assertEquals(true, eval("'abc' LIKE 'a%'"));
    }

    /**
     * Prefix NOT now binds looser than comparison (grammar fix, live via ANTLR — this
     * expression is not gated): {@code NOT 1 = 2} parses as {@code NOT (1 = 2)} = true.
     * The legacy grammar parsed it as {@code (NOT 1) = 2}.
     */
    @Test
    public void testNotPrecedenceFixed() {
        assertEquals(true, eval("NOT 1 = 2"));
    }

    /**
     * Row-constructor tuple IN — newly supported by the ANTLR builder (the legacy parser did
     * not handle it). The right-hand list is parenthesized ROWS of the left side's width; the
     * flat spelling {@code (1, 2) IN (1, 2)} is a type error (ROW compared against scalars).
     */
    @Test
    public void testTupleIn() {
        assertEquals(true, eval("(1, 2) IN ((1, 2))"));
        assertEquals(true, eval("(1, 2) IN ((3, 4), (1, 2))"));
        assertEquals(false, eval("(1, 2) IN ((3, 4))"));
        assertEquals(true, eval("(1, 2) NOT IN ((3, 4))"));
    }

    /**
     * BETWEEN/LIKE are no longer gated to the legacy parser — they evaluate via the ANTLR grammar
     * now that {@code booleanExpr} wraps {@code expression}. The previously-buggy case was a
     * BETWEEN/LIKE predicate followed by a top-level {@code AND}/{@code OR}: the old grammar let
     * the predicate's right operand absorb the trailing connective (so {@code x BETWEEN a AND b
     * AND c} mis-parsed). It now parses as {@code (x BETWEEN a AND b) AND c}.
     */
    @Test
    public void testBetweenAndLikeUngated() {
        assertEquals(true, eval("5 BETWEEN 1 AND 10"));
        assertEquals(true, eval("5 BETWEEN 1 AND 10 AND 2 > 1"));
        assertEquals(false, eval("5 BETWEEN 1 AND 10 AND 1 = 2"));
        assertEquals(true, eval("5 NOT BETWEEN 1 AND 3 AND 1 = 1"));
        assertEquals(true, eval("'abc' LIKE 'a%' AND 1 = 1"));
        assertEquals(true, eval("'abc' LIKE 'z%' OR 'x' = 'x'"));
    }

    /**
     * A boolean expression projected from a table (no aggregation). The flip makes the item's
     * "value expression" null (it is an AND, not a value), so aggregate detection must treat it as
     * a non-aggregate and route it through the normal projection path rather than NPE.
     */
    @Test
    public void testBooleanProjectionFromTable() {
        engine.execute("CREATE TABLE IF NOT EXISTS bp_tbl (a INT, b INT)");
        engine.execute("INSERT INTO bp_tbl VALUES (1, 1), (1, 0), (0, 0)");
        final ResultSet rs = engine.executeQuery("SELECT a > 0 AND b > 0 AS flag FROM bp_tbl ORDER BY a DESC, b DESC");
        assertEquals(3, rs.getRows().size());
        assertEquals(true, rs.getRows().get(0).getValue(0));
        assertEquals(false, rs.getRows().get(1).getValue(0));
        assertEquals(false, rs.getRows().get(2).getValue(0));
    }
}
