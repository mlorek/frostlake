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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class NegativeNumberTest extends BaseDatabaseTest {

    @Test
    public void testSelectNegativeInteger() {
        final ResultSet rs = engine.executeQuery("SELECT -1");
        assertEquals(-1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void testSelectNegativeFloat() {
        final ResultSet rs = engine.executeQuery("SELECT -2.5");
        assertEquals(-2.5, ((Number) rs.getRows().get(0).getValue(0)).doubleValue(), 0.001);
    }

    @Test
    public void testSelectNegativeInTable() {
        engine.execute("CREATE TABLE test (id INTEGER, value INTEGER)");
        engine.execute("INSERT INTO test VALUES (-1, -100)");

        final ResultSet rs = engine.executeQuery("SELECT * FROM test");
        assertEquals(-1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(-100L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
    }

    @Test
    public void testSelectPositiveWithUnaryPlus() {
        final ResultSet rs = engine.executeQuery("SELECT +5");
        assertEquals(5L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void testSelectNegativeExpression() {
        engine.execute("CREATE TABLE test (value INTEGER)");
        engine.execute("INSERT INTO test VALUES (10)");

        final ResultSet rs = engine.executeQuery("SELECT -value FROM test");
        assertEquals(-10L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    /**
     * Two signs in a row are no expression on a real account: {@code ++n}, {@code +-n}, {@code - -n} and
     * {@code -+1} are refused as an invalid function named by the OUTER sign, spaced or not, in a select
     * list, a WHERE clause and an argument alike (live-verified).
     */
    @Test
    public void twoSignsInARowAreAnInvalidFunction() {
        engine.execute("CREATE TABLE tt (n NUMBER(5,2))");
        engine.execute("INSERT INTO tt VALUES (1.5)");
        assertEquals(invalidFunction("+"), refusal("SELECT ++n FROM tt"));
        assertEquals(invalidFunction("+"), refusal("SELECT +-n FROM tt"));
        assertEquals(invalidFunction("-"), refusal("SELECT -+n FROM tt"));
        assertEquals(invalidFunction("-"), refusal("SELECT - -n FROM tt"));
        assertEquals(invalidFunction("+"), refusal("SELECT + +n FROM tt"));
        assertEquals(invalidFunction("-"), refusal("SELECT -+1"));
        assertEquals(invalidFunction("+"), refusal("SELECT +-+n FROM tt"), "the outermost sign is the one named");
        assertEquals(invalidFunction("-"), refusal("SELECT n FROM tt WHERE - -n > 0"));
        assertEquals(invalidFunction("-"), refusal("SELECT ABS(-+n) FROM tt"));
        assertEquals(invalidFunction("-"), refusal("SELECT 1 - - -n FROM tt"), "a binary minus, then two signs");
    }

    /** Live: a parenthesised inner sign, and one sign after a binary operator, are ordinary. */
    @Test
    public void aParenthesisedOrBinarySeparatedSignIsOrdinary() {
        engine.execute("CREATE TABLE tt (n NUMBER(5,2))");
        engine.execute("INSERT INTO tt VALUES (1.5)");
        assertEquals("1.50", text("SELECT -(-n) FROM tt"));
        assertEquals("-1.50", text("SELECT -(+n) FROM tt"));
        assertEquals("1.50", text("SELECT - (- n) FROM tt"));
        assertEquals("2.50", text("SELECT 1 - -n FROM tt"));
        assertEquals("-0.50", text("SELECT 1 + -n FROM tt"));
        assertEquals("-3.00", text("SELECT 2 * -n FROM tt"));
    }

    private static String invalidFunction(final String sign) {
        return "SQL compilation error:\ninvalid function '" + sign + "'";
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        return refused.getMessage();
    }

    private String text(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }
}
