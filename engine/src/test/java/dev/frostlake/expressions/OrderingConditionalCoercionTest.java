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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code GREATEST} and {@code LEAST} CONVERT their arguments before ordering them, and answer with the
 * CONVERTED value — they do not hand back the winning argument as written. Frostlake compared the raw
 * values with {@code Comparable.compareTo}, so a mixed pair threw a ClassCastException with Java class
 * names in it, and a view over the same expression was created anyway.
 *
 * <pre>
 *   GREATEST(n, '5')          5            the string joins the number, whichever side it is on
 *   GREATEST('5', n)          5            — NOT the VARCHAR the first argument was
 *   GREATEST(d, '2027-01-01') 2027-01-01   a date the same way, both orders
 *   GREATEST(bo, n)           TRUE         BOOLEAN takes it, whichever side it is on
 *   GREATEST(n, s)            "Numeric value 'abc' is not recognized"   — a ROW-time value error
 *   GREATEST(b, s)            "Can not convert parameter 'QX.S' …"      — a COMPILE-time refusal
 * </pre>
 *
 * <p>The two failures are different in kind, which is the point: a string that cannot be READ as a
 * number fails per row, while a BINARY beside another family cannot be brought together at all and is
 * refused before anything runs — in a view as in a query.
 */
public class OrderingConditionalCoercionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE qx (b BINARY(4), s VARCHAR(10), n NUMBER, d DATE,"
            + " bo BOOLEAN, v VARIANT)");
        engine.execute("INSERT INTO qx SELECT TO_BINARY('4142'), 'abc', 1, '2026-01-01', TRUE,"
            + " TO_VARIANT(1)");
    }

    private String valueOf(final String expression) {
        final ResultSet rs = engine.executeQuery("SELECT " + expression + " AS c FROM qx");
        rs.next();
        return String.valueOf(rs.getValue("c"));
    }

    private String refusalOf(final String sql) {
        try {
            engine.execute(sql);
        } catch (final RuntimeException refused) {
            return refused.getMessage().replace('\n', ' ');
        }
        return "accepted";
    }

    /** A string argument joins the other family, whichever side it is written on. */
    @Test
    public void aStringJoinsTheOtherFamily() {
        assertEquals("5", valueOf("GREATEST(n, '5')"));
        assertEquals("5", valueOf("GREATEST('5', n)"));
        assertEquals("1", valueOf("LEAST(n, '5')"));
        assertEquals("2027-01-01", valueOf("GREATEST(d, '2027-01-01')"));
        assertEquals("2027-01-01", valueOf("GREATEST('2027-01-01', d)"));
    }

    /** A BOOLEAN takes the ordering, and a scalar VARIANT unwraps to its value. */
    @Test
    public void booleanTakesItAndAVariantUnwraps() {
        assertEquals("true", valueOf("GREATEST(bo, n)"));
        assertEquals("true", valueOf("GREATEST(n, bo)"));
        assertEquals("1", valueOf("GREATEST(v, n)"));
        assertEquals("1", valueOf("GREATEST(n, v)"));
    }

    /** Two numbers of different shapes compare by VALUE, and same-family calls are untouched. */
    @Test
    public void theOrdinaryCallsStillAnswer() {
        assertEquals("2.5", valueOf("GREATEST(n, 2.5)"));
        assertEquals("3", valueOf("GREATEST(1, 2, 3)"));
        assertEquals("zzz", valueOf("GREATEST(s, 'zzz')"));
        assertEquals("4142", valueOf("GREATEST(b, TO_BINARY('41'))"));
    }

    /** A string that will not READ as the other family fails per ROW, in the account's words. */
    @Test
    public void anUnreadableStringFailsAtRowTime() {
        assertTrue(refusalOf("SELECT GREATEST(n, s) AS c FROM qx")
            .contains("Numeric value 'abc' is not recognized"),
            refusalOf("SELECT GREATEST(n, s) AS c FROM qx"));
        assertTrue(refusalOf("SELECT GREATEST(d, s) AS c FROM qx")
            .contains("Date 'abc' is not recognized"),
            refusalOf("SELECT GREATEST(d, s) AS c FROM qx"));
    }

    /** A BINARY beside another family is refused at COMPILE time, naming the operand. */
    @Test
    public void aBinaryBesideAnotherFamilyIsRefused() {
        final String[][] cases = {
            {"GREATEST(b, s)", "Can not convert parameter 'QX.S' of type [VARCHAR(10)]"
                + " into expected type [BINARY(4)]"},
            {"LEAST(b, s)", "Can not convert parameter 'QX.S' of type [VARCHAR(10)]"
                + " into expected type [BINARY(4)]"},
            {"GREATEST(s, b)", "Can not convert parameter 'QX.B' of type [BINARY(4)]"
                + " into expected type [VARCHAR(10)]"},
            {"GREATEST(b, n)", "Can not convert parameter 'QX.N' of type [NUMBER(38,0)]"
                + " into expected type [BINARY(4)]"},
            {"GREATEST(b, d)", "Can not convert parameter 'QX.D' of type [DATE]"
                + " into expected type [BINARY(4)]"},
            {"GREATEST(b, bo)", "Can not convert parameter 'QX.BO' of type [BOOLEAN]"
                + " into expected type [BINARY(4)]"},
            {"GREATEST(b, 'abc')", "Can not convert parameter ''abc'' of type [VARCHAR(3)]"
                + " into expected type [BINARY(4)]"},
            {"GREATEST_IGNORE_NULLS(b, s)", "Can not convert parameter 'QX.S' of type [VARCHAR(10)]"
                + " into expected type [BINARY(4)]"},
            {"LEAST_IGNORE_NULLS(b, s)", "Can not convert parameter 'QX.S' of type [VARCHAR(10)]"
                + " into expected type [BINARY(4)]"}};
        for (final String[] pair : cases) {
            final String got = refusalOf("SELECT " + pair[0] + " AS c FROM qx");
            assertTrue(got.contains(pair[1]), pair[0] + "\nexpected: " + pair[1] + "\ngot: " + got);
        }
    }

    /** No Java class name ever reaches the user, whatever the pairing. */
    @Test
    public void noInternalExceptionEscapes() {
        for (final String expression : new String[]{"GREATEST(b, s)", "LEAST(s, b)",
            "GREATEST(b, v)", "GREATEST(b, n)", "LEAST_IGNORE_NULLS(b, d)"}) {
            final String got = refusalOf("SELECT " + expression + " AS c FROM qx");
            assertTrue(got.contains("SQL compilation error"),
                expression + " must refuse as SQL: " + got);
            assertTrue(!got.contains("cannot be cast") && !got.contains("java.lang"),
                expression + " leaked an internal exception: " + got);
        }
    }

    /** And the VIEW over the same body is refused too, rather than created empty. */
    @Test
    public void aViewOverTheSameBodyIsRefused() {
        for (final String expression : new String[]{"GREATEST(b, s)", "LEAST(b, s)",
            "GREATEST(s, b)", "GREATEST(b, n)"}) {
            final String got = refusalOf(
                "CREATE OR REPLACE VIEW qx_v AS SELECT " + expression + " AS c FROM qx");
            assertTrue(got.contains("Can not convert parameter"),
                "view over " + expression + " gave: " + got);
        }
    }
}
