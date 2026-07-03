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
 * Golden / characterization corpus for expression evaluation — the safety net for the
 * string-evaluator migration. Each assertion captures the CURRENT evaluated behavior of the
 * live engine (whatever path produces it: ANTLR builder or the gated legacy parser). Later
 * migration steps must keep these values unchanged.
 *
 * <p>Conventions: constant expressions via {@code SELECT <expr>}; predicates via
 * {@code COUNT(*) ... WHERE}; column expressions via the fixture row {@code id = 1}. Numeric
 * results whose boxed type is ambiguous (division, decimals, some functions) are asserted as
 * {@code <expr> = <value>} → true to avoid Long/Double/BigDecimal coupling.
 */
public class ExpressionGoldenCorpusTest {

    private static DatabaseEngine engine;

    @BeforeAll
    public static void setup() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
        engine.execute("CREATE TABLE g (id INT, name VARCHAR, qty INT, descr VARCHAR, data VARCHAR)");
        engine.execute("INSERT INTO g VALUES (1, 'Alice', 10, 'hello world', '{\"a\": 1, \"b\": \"x\"}')");
        engine.execute("INSERT INTO g VALUES (2, 'Bob', 20, 'foo bar', '{\"a\": 2, \"b\": \"y\"}')");
        engine.execute("INSERT INTO g VALUES (3, 'Carol', NULL, NULL, '{\"a\": 3}')");
    }

    @AfterAll
    public static void teardown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private static Object eval(final String expr) {
        ResultSet rs = engine.executeQuery("SELECT " + expr);
        return rs.getRows().get(0).getValue(0);
    }

    private static long countWhere(final String predicate) {
        ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM g WHERE " + predicate);
        return ((Number) rs.getRows().get(0).getValue(0)).longValue();
    }

    private static Object evalCol(final String expr) {
        ResultSet rs = engine.executeQuery("SELECT " + expr + " FROM g WHERE id = 1");
        return rs.getRows().get(0).getValue(0);
    }

    // ---- Constants: literals & arithmetic ----

    @Test
    public void testLiterals() {
        assertEquals(42L, eval("42"));
        assertEquals("hello", eval("'hello'"));
        assertEquals("it's", eval("'it''s'"));
        assertEquals(true, eval("true"));
        assertEquals(false, eval("false"));
    }

    @Test
    public void testArithmetic() {
        assertEquals(7L, eval("1 + 2 * 3"));
        assertEquals(9L, eval("(1 + 2) * 3"));
        assertEquals(42L, eval("100 - 58"));
        assertEquals(4L, eval("10 - 4 - 2"));        // left-associative (ANTLR-fixed)
        assertEquals(20L, eval("2 * 5 + 5 * 2"));
        assertEquals(true, eval("10 / 4 = 2.5"));
        assertEquals(true, eval("1.5 + 1.5 = 3.0"));
        assertEquals(-5L, eval("-5"));
        assertEquals(5L, eval("-(-5)"));
    }

    // ---- Constants: comparison & logical ----

    @Test
    public void testComparisonsAndLogical() {
        assertEquals(true, eval("2 > 1"));
        assertEquals(true, eval("5 = 5"));
        assertEquals(true, eval("5 <> 6"));
        assertEquals(true, eval("5 >= 5"));
        assertEquals(true, eval("4 <= 5"));
        assertEquals(true, eval("1 = 1 AND 2 = 2"));
        assertEquals(false, eval("1 = 1 AND 2 = 3"));
        assertEquals(true, eval("1 = 2 OR 3 = 3"));
        assertEquals(true, eval("NOT 1 = 2"));
        assertEquals(true, eval("1 = 1 AND 2 = 2 OR 1 = 2"));
    }

    // ---- Constants: strings, NULL, CASE, CAST ----

    @Test
    public void testStringsAndNull() {
        assertEquals("abc", eval("'a' || 'b' || 'c'"));
        assertEquals(null, eval("'foo' || NULL"));
        assertEquals(true, eval("NULL IS NULL"));
        assertEquals(true, eval("5 IS NOT NULL"));
        assertEquals(true, eval("UPPER('abc') = 'ABC'"));
        assertEquals(true, eval("LENGTH('hello') = 5"));
        assertEquals(true, eval("COALESCE(NULL, 5) = 5"));
        assertEquals(true, eval("ABS(-7) = 7"));
    }

    @Test
    public void testCaseAndCast() {
        assertEquals("yes", eval("CASE WHEN 1 = 1 THEN 'yes' ELSE 'no' END"));
        assertEquals("b", eval("CASE 2 WHEN 1 THEN 'a' WHEN 2 THEN 'b' END"));
        assertEquals("42", eval("CAST(42 AS VARCHAR)"));
        assertEquals(17L, eval("CAST('17' AS INTEGER)"));
        assertEquals(17L, eval("'17' :: INTEGER"));
    }

    // ---- Constants: IN / BETWEEN / LIKE / tuple IN ----

    @Test
    public void testMembershipConstants() {
        assertEquals(true, eval("3 IN (1, 2, 3)"));
        assertEquals(true, eval("4 NOT IN (1, 2, 3)"));
        assertEquals(true, eval("5 BETWEEN 1 AND 10"));
        assertEquals(true, eval("15 NOT BETWEEN 1 AND 10"));
        assertEquals(true, eval("'abc' LIKE 'a%'"));
        assertEquals(true, eval("'abc' NOT LIKE 'z%'"));
        assertEquals(true, eval("(1, 2) IN (3, 4, 1, 2)"));
        assertEquals(false, eval("(1, 2) IN (3, 4)"));
    }

    // ---- Predicates over the fixture table ----

    @Test
    public void testTablePredicates() {
        assertEquals(1L, countWhere("qty BETWEEN 5 AND 15"));
        assertEquals(2L, countWhere("qty IN (10, 20)"));
        assertEquals(1L, countWhere("qty IS NULL"));
        assertEquals(2L, countWhere("qty IS NOT NULL"));
        assertEquals(1L, countWhere("qty > 5 AND qty < 15"));
        assertEquals(2L, countWhere("name = 'Alice' OR name = 'Bob'"));
        assertEquals(1L, countWhere("name LIKE 'A%'"));
        assertEquals(1L, countWhere("name ILIKE 'a%'"));
        assertEquals(2L, countWhere("name NOT LIKE 'A%'"));
        assertEquals(1L, countWhere("(id, qty) IN (1, 10)"));
    }

    // ---- Column expressions over the fixture row id = 1 ----

    @Test
    public void testColumnExpressions() {
        assertEquals("Alice", evalCol("name"));
        assertEquals(20L, evalCol("qty * 2"));
        assertEquals(11L, evalCol("qty + id"));
        assertEquals("ALICE", evalCol("UPPER(name)"));
        assertEquals("Alice!", evalCol("name || '!'"));
        assertEquals("big", evalCol("CASE WHEN qty > 5 THEN 'big' ELSE 'small' END"));
        assertEquals(true, evalCol("qty > 5"));
    }

    // ---- JSON path access and subqueries ----

    @Test
    public void testJsonAccess() {
        // data = {"a": 1, "b": "x"} on row id = 1; comparison keeps the assertion type-robust.
        assertEquals(true, evalCol("data:a = 1"));
        assertEquals(true, evalCol("data:b = 'x'"));
    }

    @Test
    public void testSubqueries() {
        assertEquals(20L, ((Number) eval("(SELECT MAX(qty) FROM g)")).longValue());
        assertEquals(1L, countWhere("qty IN (SELECT qty FROM g WHERE id = 2)"));
        assertEquals(3L, countWhere("EXISTS (SELECT 1 FROM g WHERE qty > 15)"));
        assertEquals(0L, countWhere("NOT EXISTS (SELECT 1 FROM g WHERE qty > 15)"));
    }
}
