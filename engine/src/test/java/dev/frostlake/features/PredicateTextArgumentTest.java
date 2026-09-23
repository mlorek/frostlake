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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A PREDICATE never becomes text. Handed to a function that reads text, or placed beside {@code ||} or a
 * pattern match, a comparison, an IN, a LIKE, an EXISTS or a type test is refused while the statement
 * compiles, every argument's type listed and the refusal anchored at the call — where a BOOLEAN value in
 * the same place, a literal, a column, a conversion or a conditional, is read as its text. A BOOLEAN of
 * either kind in a text function's numeric slot is refused the same way. Live-verified.
 */
public class PredicateTextArgumentTest extends BaseDatabaseTest {

    private void createTable() {
        engine.execute("CREATE OR REPLACE TABLE pt (g VARCHAR(10), i INT, b BOOLEAN, v VARIANT, a ARRAY)");
        engine.execute("CREATE OR REPLACE TABLE pt0 (g VARCHAR(10), i INT, b BOOLEAN, v VARIANT, a ARRAY)");
        engine.execute("INSERT INTO pt SELECT 'abc', 3, TRUE, PARSE_JSON('2'), ARRAY_CONSTRUCT(1)");
    }

    /** The one cell of a single-row query, as text. */
    private String scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount(), sql);
        final Object value = rs.getRows().get(0).getValue(0);
        return value == null ? "NULL" : value.toString();
    }

    /** The message a refused statement carries. */
    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        return refused.getMessage();
    }

    /** A compile-time argument-type refusal anchored on line 1. */
    private static String at(final int position, final String function, final String types) {
        return "SQL compilation error: error line 1 at position " + position
            + "\nInvalid argument types for function '" + function + "': (" + types + ")";
    }

    /** A predicate handed to a function that reads text is refused, every argument listed. */
    @Test
    public void aPredicateIsNoText() {
        createTable();
        assertEquals(at(7, "POSITION", "VARCHAR(1), BOOLEAN"), refusal("SELECT POSITION('x', 'b' IN (SELECT 'abc'))"));
        assertEquals(at(7, "UPPER", "BOOLEAN"), refusal("SELECT UPPER(1 = 1) FROM pt"));
        assertEquals(at(7, "CONCAT", "VARCHAR(1), BOOLEAN, VARCHAR(1)"), refusal("SELECT CONCAT('a', (1 = 1), 'b')"));
        assertEquals(at(7, "CONCAT", "BOOLEAN, BOOLEAN"), refusal("SELECT CONCAT(b, 1 = 1) FROM pt"));
        assertEquals(at(7, "LENGTH", "BOOLEAN"), refusal("SELECT LENGTH(b = b) FROM pt"));
        assertEquals(at(7, "SUBSTR", "VARCHAR(3), NUMBER(1,0), BOOLEAN"), refusal("SELECT SUBSTR('abc', 1, (1 = 1))"));
        assertEquals(at(7, "TRANSLATE", "VARCHAR(1), BOOLEAN, VARCHAR(1)"), refusal("SELECT TRANSLATE('a', (1 = 1), 'b')"));
        assertEquals(at(7, "SPLIT_PART", "VARCHAR(3), VARCHAR(1), BOOLEAN"),
            refusal("SELECT SPLIT_PART('a,b', ',', (1 = 1))"));
        assertEquals(at(7, "SHA2", "BOOLEAN, NUMBER(3,0)"), refusal("SELECT SHA2((1 = 1), 256)"));
        assertEquals(at(7, "LISTAGG", "BOOLEAN, VARCHAR(1)"), refusal("SELECT LISTAGG((1 = 1), ',') FROM pt"));
        assertEquals(at(7, "ARRAY_TO_STRING", "ARRAY, BOOLEAN"), refusal("SELECT ARRAY_TO_STRING([1], (1 = 1))"));
        assertEquals(at(7, "PARSE_JSON", "BOOLEAN"), refusal("SELECT PARSE_JSON((1 = 1))"));
        assertEquals(at(7, "ILIKE", "VARCHAR(1), BOOLEAN"), refusal("SELECT ILIKE('x', (1 = 1))"));
    }

    /** A BOOLEAN value in the same place is read as the text it spells. */
    @Test
    public void aBooleanValueIsReadAsText() {
        createTable();
        assertEquals("TRUE", scalar("SELECT UPPER(TRUE)"));
        assertEquals("0", scalar("SELECT POSITION('x', TRUE)"));
        assertEquals("TRUE", scalar("SELECT UPPER(b) FROM pt"));
        assertEquals("FALSE", scalar("SELECT UPPER(NOT b) FROM pt"));
        assertEquals("TRUE", scalar("SELECT UPPER(b AND b) FROM pt"));
        assertEquals("TRUE", scalar("SELECT UPPER(IFF(1 = 1, TRUE, FALSE))"));
        assertEquals("FALSE", scalar("SELECT UPPER(CAST(i = 1 AS BOOLEAN)) FROM pt"));
        assertEquals("TRUE", scalar("SELECT UPPER(BOOLAND(1, 1))"));
        assertEquals("TRUE", scalar("SELECT UPPER(MAX(1 = 1)) FROM pt"));
        assertEquals("TRUE", scalar("SELECT UPPER(COALESCE(1 = 1, TRUE))"));
        assertEquals("a", scalar("SELECT CONCAT_WS((1 = 1), 'a')"));
    }

    /** NOT, AND and OR carry a predicate operand through; over BOOLEAN values alone they do not. */
    @Test
    public void aLogicalOperatorCarriesThePredicate() {
        createTable();
        assertEquals(at(7, "UPPER", "BOOLEAN"), refusal("SELECT UPPER(NOT (1 = 1))"));
        assertEquals(at(7, "UPPER", "BOOLEAN"), refusal("SELECT UPPER(b AND 1 = 1) FROM pt"));
        assertEquals(at(7, "UPPER", "BOOLEAN"), refusal("SELECT UPPER(TRUE OR (1 = 1))"));
    }

    /** The type tests, the membership tests and the pattern matches are predicates too. */
    @Test
    public void theTestsArePredicates() {
        createTable();
        final String upper = at(7, "UPPER", "BOOLEAN");
        assertEquals(upper, refusal("SELECT UPPER(IS_ARRAY(v)) FROM pt"));
        assertEquals(upper, refusal("SELECT UPPER(IS_NULL_VALUE(v)) FROM pt"));
        assertEquals(upper, refusal("SELECT UPPER(ARRAY_CONTAINS(1::VARIANT, a)) FROM pt"));
        assertEquals(upper, refusal("SELECT UPPER(CONTAINS(g, 'a')) FROM pt"));
        assertEquals(upper, refusal("SELECT UPPER(EQUAL_NULL(b, b)) FROM pt"));
        assertEquals(upper, refusal("SELECT UPPER(b IS NOT DISTINCT FROM b) FROM pt"));
        assertEquals(upper, refusal("SELECT UPPER('a' RLIKE 'b')"));
        assertEquals(upper, refusal("SELECT UPPER(i BETWEEN 1 AND 2) FROM pt"));
        assertEquals(upper, refusal("SELECT UPPER(g ILIKE ANY ('a')) FROM pt"));
        assertEquals(upper, refusal("SELECT UPPER(NOT EXISTS (SELECT 1))"));
        assertEquals(upper, refusal("SELECT UPPER(i = ANY (SELECT 1)) FROM pt"));
        assertEquals(upper, refusal("SELECT UPPER(b IS NULL) FROM pt"));
    }

    /** Beside {@code ||} or a pattern match the refusal names the operator, at the operator. */
    @Test
    public void anOperatorBesideAPredicate() {
        assertEquals(at(11, "||", "VARCHAR(1), BOOLEAN"), refusal("SELECT 'x' || (1 = 1)"));
        assertEquals(at(15, "||", "BOOLEAN, VARCHAR(1)"), refusal("SELECT (1 = 1) || 'x'"));
        assertEquals(at(15, "LIKE", "BOOLEAN, VARCHAR(1)"), refusal("SELECT (1 = 1) LIKE 'x'"));
        assertEquals(at(15, "ILIKE", "BOOLEAN, VARCHAR(1)"), refusal("SELECT (1 = 1) ILIKE 'x'"));
        assertEquals("SQL compilation error: error line 0 at position -1"
            + "\nInvalid argument types for function 'LIKE': (VARCHAR(1), BOOLEAN)",
            refusal("SELECT 'x' NOT LIKE (1 = 1)"));
    }

    /** A text function's numeric slot refuses a BOOLEAN value as well as a predicate. */
    @Test
    public void aNumericSlotRefusesABoolean() {
        assertEquals(at(7, "CHR", "BOOLEAN"), refusal("SELECT CHR(TRUE)"));
        assertEquals(at(7, "SUBSTR", "VARCHAR(3), BOOLEAN"), refusal("SELECT SUBSTR('abc', TRUE)"));
        assertEquals(at(7, "LPAD", "VARCHAR(1), BOOLEAN"), refusal("SELECT LPAD('x', TRUE)"));
        assertEquals(at(7, "CHARINDEX", "VARCHAR(1), VARCHAR(1), BOOLEAN"), refusal("SELECT CHARINDEX('x', 'y', TRUE)"));
        assertEquals(at(7, "POSITION", "VARCHAR(1), VARCHAR(2), BOOLEAN"), refusal("SELECT POSITION('x', 'xy', TRUE)"));
        assertEquals(at(7, "*", "BOOLEAN, NUMBER(18,0)"), refusal("SELECT REPEAT('x', TRUE)"));
        assertEquals(at(7, "*", "BOOLEAN, NUMBER(18,0)"), refusal("SELECT REPEAT('x', 1 = 1)"));
        assertEquals(at(7, "LPAD", "VARCHAR(1), BOOLEAN, VARCHAR(1)"), refusal("SELECT SPACE(TRUE)"));
        assertEquals(at(7, "LPAD", "VARCHAR(1), BOOLEAN, VARCHAR(1)"), refusal("SELECT SPACE(1 = 1)"));
    }

    /** The refusal is a compile-time one: an empty table and a false filter refuse, and names come first. */
    @Test
    public void theRefusalIsACompileTimeOne() {
        createTable();
        assertEquals(at(7, "CONCAT", "VARCHAR(10), BOOLEAN"), refusal("SELECT CONCAT(g, i IN (1)) FROM pt0"));
        assertEquals(at(7, "UPPER", "BOOLEAN"), refusal("SELECT UPPER(g = 'x') FROM pt WHERE 1 = 0"));
        assertEquals(at(23, "UPPER", "BOOLEAN"), refusal("SELECT 1 FROM pt WHERE UPPER(g = 'x') = 'TRUE'"));
        assertEquals(at(10, "POSITION", "VARCHAR(1), BOOLEAN"), refusal("SELECT 1, POSITION('x', 1 = 1)"));
        assertEquals("SQL compilation error: error line 1 at position 21\ninvalid identifier 'MISSING'",
            refusal("SELECT UPPER(1 = 1), missing FROM pt"));
    }
}
