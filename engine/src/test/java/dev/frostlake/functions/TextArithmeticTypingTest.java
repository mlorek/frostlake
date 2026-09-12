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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A text in exact arithmetic reads as NUMBER(18,5) — a literal as NUMBER(18, its own scale) — and a
 * text beside a text, a FLOAT, a VARIANT or a bare NULL, a VARIANT beside anything, and the
 * one-argument numeric functions over a text are FLOAT: the declared types, the values at the
 * reading's scale, the row-time refusals, and the function forms (MOD, DIV0, the rounding family with
 * a scale, the conditionals). Every expectation is live-verified.
 */
public class TextArithmeticTypingTest extends BaseDatabaseTest {

    @BeforeEach
    public void createRelations() {
        engine.execute("CREATE TABLE ta (t VARCHAR(10), g VARCHAR(10), n NUMBER(10,2), v VARIANT, vs VARIANT, f FLOAT, "
            + "i INTEGER, w VARCHAR)");
        engine.execute("INSERT INTO ta SELECT '5', '3', 2.50, TO_VARIANT(5), PARSE_JSON('\"2.5\"'), 2.5, 3, '1.123456'");
        engine.execute("CREATE TABLE tb (t VARCHAR(3), u VARCHAR, e VARCHAR(20))");
        engine.execute("INSERT INTO tb SELECT '5', '7', '5e2'");
    }

    private Row row(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        assertEquals(1, result.getRowCount(), sql);
        return result.getRows().get(0);
    }

    private String text(final String sql) {
        return String.valueOf(row(sql).getValue(0));
    }

    /** A cell's text: a BigDecimal's digits in place (a zero at scale 11 is 0.00000000000, not 0E-11). */
    private static String cell(final Row row, final int index) {
        final Object value = row.getValue(index);
        return value instanceof BigDecimal ? ((BigDecimal) value).toPlainString() : String.valueOf(value);
    }

    private static void assertDouble(final double expected, final Object value, final String what) {
        assertTrue(value instanceof Double, what + " should be a double, was " + (value == null ? "null" : value.getClass()));
        assertEquals(expected, ((Double) value).doubleValue(), 1e-9, what);
    }

    private void assertTypes(final String sql, final String... expected) {
        final Row typed = row(sql);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], cell(typed, i), sql + " column " + i);
        }
    }

    private void assertRefusal(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        assertTrue(refused.getMessage().contains(fragment), sql + " -> " + refused.getMessage());
    }

    @Test
    public void aTextColumnReadsAsNumber18And5() {
        assertTypes("SELECT SYSTEM$TYPEOF(t + 0), SYSTEM$TYPEOF(t - 0), SYSTEM$TYPEOF(t * 1), SYSTEM$TYPEOF(t / 1), SYSTEM$TYPEOF(-t), "
            + "SYSTEM$TYPEOF(t + 0.5), SYSTEM$TYPEOF(t + n), SYSTEM$TYPEOF(t + i), SYSTEM$TYPEOF(t + f), SYSTEM$TYPEOF(t % 2), "
            + "SYSTEM$TYPEOF(t + '1'), SYSTEM$TYPEOF(+t), SYSTEM$TYPEOF(t + 1e0), SYSTEM$TYPEOF(t + 1.5e0) FROM ta",
            "NUMBER(19,5)[SB16]", "NUMBER(19,5)[SB16]", "NUMBER(19,5)[SB16]", "NUMBER(24,11)[SB16]", "FLOAT[DOUBLE]",
            "NUMBER(19,5)[SB16]", "NUMBER(19,5)[SB16]", "NUMBER(38,5)[SB16]", "FLOAT[DOUBLE]", "NUMBER(18,5)[SB8]",
            "FLOAT[DOUBLE]", "FLOAT[DOUBLE]", "NUMBER(19,5)[SB16]", "NUMBER(19,5)[SB16]");
        assertTypes("SELECT SYSTEM$TYPEOF(t * n), SYSTEM$TYPEOF(t / n), SYSTEM$TYPEOF(t % n), SYSTEM$TYPEOF(n + t), SYSTEM$TYPEOF(n * t), "
            + "SYSTEM$TYPEOF(t * i), SYSTEM$TYPEOF(t / i), SYSTEM$TYPEOF(t * f), SYSTEM$TYPEOF(t + 1.5), SYSTEM$TYPEOF(t * 1.5), "
            + "SYSTEM$TYPEOF(t + 12345678901234567890) FROM ta",
            "NUMBER(28,7)[SB16]", "NUMBER(26,11)[SB16]", "NUMBER(18,5)[SB8]", "NUMBER(19,5)[SB16]", "NUMBER(28,7)[SB16]",
            "NUMBER(38,5)[SB16]", "NUMBER(24,11)[SB16]", "FLOAT[DOUBLE]", "NUMBER(19,5)[SB16]", "NUMBER(20,6)[SB16]",
            "NUMBER(26,5)[SB16]");
        // The declared length is immaterial, and the reading nests.
        assertTypes("SELECT SYSTEM$TYPEOF(t + 0), SYSTEM$TYPEOF(u + 0), SYSTEM$TYPEOF(u * u), SYSTEM$TYPEOF(t * 1.0), "
            + "SYSTEM$TYPEOF(t + 1::NUMBER(5,3)), SYSTEM$TYPEOF(t * 1::NUMBER(5,3)), SYSTEM$TYPEOF(t / 1::NUMBER(5,3)), "
            + "SYSTEM$TYPEOF(t % 1::NUMBER(5,3)), SYSTEM$TYPEOF(u + 0 + 0), SYSTEM$TYPEOF((u + 0) * 1), SYSTEM$TYPEOF(u + u + 0), "
            + "SYSTEM$TYPEOF(-u + 0), SYSTEM$TYPEOF(ABS(u) + 0), SYSTEM$TYPEOF(u + 0::FLOAT), SYSTEM$TYPEOF(-(t + 0)) FROM tb",
            "NUMBER(19,5)[SB16]", "NUMBER(19,5)[SB16]", "FLOAT[DOUBLE]", "NUMBER(19,5)[SB16]", "NUMBER(19,5)[SB16]",
            "NUMBER(23,8)[SB16]", "NUMBER(27,11)[SB16]", "NUMBER(18,5)[SB8]", "NUMBER(20,5)[SB16]", "NUMBER(20,5)[SB16]",
            "FLOAT[DOUBLE]", "FLOAT[DOUBLE]", "FLOAT[DOUBLE]", "FLOAT[DOUBLE]", "NUMBER(19,5)[SB16]");
        assertTypes("SELECT SYSTEM$TYPEOF(ta.t + g), SYSTEM$TYPEOF(ta.t * g), SYSTEM$TYPEOF(ta.t / g), SYSTEM$TYPEOF(ta.t - g), "
            + "SYSTEM$TYPEOF(u + NULL), SYSTEM$TYPEOF(u + NULL::NUMBER(3,1)), SYSTEM$TYPEOF(ta.t::NUMBER(18,5) + 0), "
            + "SYSTEM$TYPEOF(ta.t::NUMBER(18,5) / 1), SYSTEM$TYPEOF(ta.t::NUMBER(18,5) % 2) FROM ta, tb",
            "FLOAT[DOUBLE]", "FLOAT[DOUBLE]", "FLOAT[DOUBLE]", "FLOAT[DOUBLE]", "FLOAT[DOUBLE]", "NUMBER(19,5)[SB1]",
            "NUMBER(19,5)[SB16]", "NUMBER(24,11)[SB16]", "NUMBER(18,5)[SB8]");
        assertTypes("SELECT SYSTEM$TYPEOF(SUM(u + 0)), SYSTEM$TYPEOF(AVG(u + 0)), SYSTEM$TYPEOF(MAX(u + 0)), SYSTEM$TYPEOF(SUM(u)), "
            + "SYSTEM$TYPEOF(MAX(u)) FROM tb",
            "NUMBER(31,5)[SB16]", "NUMBER(37,11)[SB16]", "NUMBER(19,5)[SB16]", "FLOAT[DOUBLE]", "VARCHAR(16777216)[LOB]");
    }

    @Test
    public void aTextLiteralReadsAtItsOwnScale() {
        final Row folded = row("SELECT '5' + 1, '5' * 2, '5' / 2, MOD('5', 2), '2.5' + 1, SYSTEM$TYPEOF('5' + 1), SYSTEM$TYPEOF('5' / 2), "
            + "SYSTEM$TYPEOF(MOD('5', 2)), SYSTEM$TYPEOF('2.5' + 1), SYSTEM$TYPEOF(-'5'), SYSTEM$TYPEOF('5' + '1'), "
            + "DIV0('5', 2), SYSTEM$TYPEOF(DIV0('5', 2)), SYSTEM$TYPEOF(MOD('2.5', 2)), SYSTEM$TYPEOF(MOD('12345', 2)), "
            + "SYSTEM$TYPEOF('5' % 2), SYSTEM$TYPEOF('12345' % 2), SYSTEM$TYPEOF(MOD(2, '5')), SYSTEM$TYPEOF(MOD('5', 12345)), "
            + "SYSTEM$TYPEOF(DIV0('2.5', 2)), SYSTEM$TYPEOF('12345' * 2), SYSTEM$TYPEOF('12345' - 1)");
        assertEquals("6", cell(folded, 0));
        assertEquals("10", cell(folded, 1));
        assertEquals("2.500000", cell(folded, 2));
        assertEquals("1", cell(folded, 3));
        assertEquals("3.5", cell(folded, 4));
        assertEquals("NUMBER(19,0)[SB1]", cell(folded, 5));
        assertEquals("NUMBER(24,6)[SB4]", cell(folded, 6));
        assertEquals("NUMBER(2,0)[SB1]", cell(folded, 7));
        assertEquals("NUMBER(19,1)[SB1]", cell(folded, 8));
        assertEquals("FLOAT[DOUBLE]", cell(folded, 9));
        assertEquals("FLOAT[DOUBLE]", cell(folded, 10));
        assertEquals("2.500000", cell(folded, 11));
        assertEquals("NUMBER(24,6)[SB4]", cell(folded, 12));
        assertEquals("NUMBER(3,1)[SB1]", cell(folded, 13));
        assertEquals("NUMBER(5,0)[SB1]", cell(folded, 14));
        assertEquals("NUMBER(2,0)[SB1]", cell(folded, 15));
        assertEquals("NUMBER(5,0)[SB1]", cell(folded, 16));
        assertEquals("NUMBER(2,0)[SB1]", cell(folded, 17));
        assertEquals("NUMBER(5,0)[SB1]", cell(folded, 18));
        assertEquals("NUMBER(24,7)[SB4]", cell(folded, 19));
        assertEquals("NUMBER(19,0)[SB2]", cell(folded, 20));
        assertEquals("NUMBER(19,0)[SB2]", cell(folded, 21));
        assertTypes("SELECT SYSTEM$TYPEOF(MOD(5, 2)), SYSTEM$TYPEOF(MOD(2.5, 2)), SYSTEM$TYPEOF(MOD(12345, 2)), "
            + "SYSTEM$TYPEOF(MOD(i, 2)), SYSTEM$TYPEOF(MOD(t, n)), SYSTEM$TYPEOF(MOD(n, t)), SYSTEM$TYPEOF(t % n), "
            + "SYSTEM$TYPEOF(DIV0(5, 2)) FROM ta",
            "NUMBER(2,0)[SB1]", "NUMBER(3,1)[SB1]", "NUMBER(5,0)[SB1]", "NUMBER(38,0)[SB1]",
            "NUMBER(18,5)[SB8]", "NUMBER(18,5)[SB8]", "NUMBER(18,5)[SB8]", "NUMBER(7,6)[SB4]");
        // The declared type only: a remainder's storage tag over a column beside a constant is its
        // own open measurement (the dividend's interval on the two-row instrument, the divisor's on
        // a one-row table), so these two pin the width and not the tag.
        final Row remainders = row("SELECT SYSTEM$TYPEOF(MOD(n, 7)), SYSTEM$TYPEOF(MOD('5', n)) FROM ta");
        assertEquals("NUMBER(10,2)", cell(remainders, 0).substring(0, cell(remainders, 0).indexOf('[')));
        assertEquals("NUMBER(10,2)", cell(remainders, 1).substring(0, cell(remainders, 1).indexOf('[')));
    }

    @Test
    public void valuesCarryTheReadingsScale() {
        final Row exact = row("SELECT t + 0, t - 0, t * 1, t / 1, -t, t + 0.5, t + n, t + i, t + f, t % 2, t + '1', +t, t + 1e0 FROM ta");
        assertEquals("5.00000", cell(exact, 0));
        assertEquals("5.00000", cell(exact, 1));
        assertEquals("5.00000", cell(exact, 2));
        assertEquals("5.00000000000", cell(exact, 3));
        assertDouble(-5.0, exact.getValue(4), "-t");
        assertEquals("5.50000", cell(exact, 5));
        assertEquals("7.50000", cell(exact, 6));
        assertEquals("8.00000", cell(exact, 7));
        assertDouble(7.5, exact.getValue(8), "t + f");
        assertEquals("1.00000", cell(exact, 9));
        assertDouble(6.0, exact.getValue(10), "t + '1'");
        assertDouble(5.0, exact.getValue(11), "+t");
        assertEquals("6.00000", cell(exact, 12));
        final Row more = row("SELECT t * n, t / n, t % n, n + t, n * t, t * i, t / i, t * f, t / f, t + n * 2, -(t + 0) FROM ta");
        assertEquals("12.5000000", cell(more, 0));
        assertEquals("2.00000000000", cell(more, 1));
        assertEquals("0.00000", cell(more, 2));
        assertEquals("7.50000", cell(more, 3));
        assertEquals("12.5000000", cell(more, 4));
        assertEquals("15.00000", cell(more, 5));
        assertEquals("1.66666666667", cell(more, 6));
        assertDouble(12.5, more.getValue(7), "t * f");
        assertDouble(2.0, more.getValue(8), "t / f");
        assertEquals("10.00000", cell(more, 9));
        assertEquals("-5.00000", cell(more, 10));
        // Rounded to five decimals BEFORE the operation; a text beside a text keeps its digits.
        final Row rounded = row("SELECT w + 0, w * 1, w / 1, -w, w + n, e + 0, u * 1000000000000000, u + 0.123456 FROM ta, tb");
        assertEquals("1.12346", cell(rounded, 0));
        assertEquals("1.12346", cell(rounded, 1));
        assertEquals("1.12346000000", cell(rounded, 2));
        assertDouble(-1.123456, rounded.getValue(3), "-w");
        assertEquals("3.62346", cell(rounded, 4));
        assertEquals("500.00000", cell(rounded, 5));
        assertEquals("7000000000000000.00000", cell(rounded, 6));
        assertEquals("7.123456", cell(rounded, 7));
        final Row spellings = row("SELECT '1e5'::VARCHAR + 0, '1234567890123'::VARCHAR + 0, '1234567890123.12345'::VARCHAR + 0, "
            + "'1234567890123.123456'::VARCHAR + 0, '.5'::VARCHAR + 0, '5.'::VARCHAR + 0, '+5'::VARCHAR + 0, "
            + "'-0.000004'::VARCHAR + 0, '0.000005'::VARCHAR + 0, ' 5 '::VARCHAR + 0, '0000000000000005'::VARCHAR + 0, "
            + "'5.000000000000000000001'::VARCHAR + 0 FROM tb");
        final String[] expected = {"100000.00000", "1234567890123.00000", "1234567890123.12345", "1234567890123.12346",
            "0.50000", "5.00000", "5.00000", "0.00000", "0.00001", "5.00000", "5.00000", "5.00000"};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], cell(spellings, i), "spelling " + i);
        }
        final Row derived = row("SELECT t + 0 AS x, t * 1 AS y FROM ta");
        assertEquals("5.00000", cell(derived, 0));
        assertEquals("5.00000", cell(derived, 1));
        engine.execute("CREATE TABLE ct AS SELECT t + 0 AS x, t / 1 AS y, -t AS z, MOD(t, 2) AS m FROM ta");
        final Row stored = row("SELECT SYSTEM$TYPEOF(x), SYSTEM$TYPEOF(y), SYSTEM$TYPEOF(z), SYSTEM$TYPEOF(m), x, y, z, m FROM ct");
        assertEquals("NUMBER(19,5)[SB4]", cell(stored, 0));
        assertEquals("NUMBER(24,11)[SB8]", cell(stored, 1));
        assertEquals("FLOAT[DOUBLE]", cell(stored, 2));
        assertEquals("NUMBER(18,5)[SB4]", cell(stored, 3));
        assertEquals("5.00000", cell(stored, 4));
        assertEquals("5.00000000000", cell(stored, 5));
        assertDouble(-5.0, stored.getValue(6), "stored -t");
        assertEquals("1.00000", cell(stored, 7));
    }

    @Test
    public void theReadingRefusesWhatTheAccountRefuses() {
        assertRefusal("SELECT '1e30'::VARCHAR + 0 FROM ta", "Numeric value '1e30' is not recognized");
        assertRefusal("SELECT 'x'::VARCHAR + 0 FROM ta", "Numeric value 'x' is not recognized");
        assertRefusal("SELECT ''::VARCHAR + 0 FROM ta", "Numeric value '' is not recognized");
        assertRefusal("SELECT '123456789012345678'::VARCHAR + 0 FROM ta", "Numeric value '123456789012345678' is not recognized");
        assertRefusal("SELECT '1234567890123456789'::VARCHAR + 0 FROM ta", "Numeric value '1234567890123456789' is not recognized");
        assertRefusal("SELECT '12345678901234.56789'::VARCHAR + 0 FROM ta", "Numeric value '12345678901234.56789' is out of range");
        assertRefusal("SELECT '12345678901234'::VARCHAR + 0 FROM tb", "Numeric value '12345678901234' is out of range");
        assertRefusal("SELECT '123456789012345'::VARCHAR + 0 FROM tb", "Numeric value '123456789012345' is not recognized");
        assertRefusal("SELECT '1,000'::VARCHAR + 0 FROM tb", "Numeric value '1,000' is not recognized");
        assertRefusal("SELECT '0x10'::VARCHAR + 0 FROM tb", "Numeric value '0x10' is not recognized");
        assertRefusal("SELECT '12345678901234567'::VARCHAR + 0 FROM tb", "Numeric value '12345678901234567' is not recognized");
        assertRefusal("SELECT '1234567890123456'::VARCHAR + 0 FROM tb", "Numeric value '1234567890123456' is not recognized");
        assertRefusal("SELECT '12345678901234.5'::VARCHAR + 0 FROM tb", "Numeric value '12345678901234.5' is out of range");
        assertRefusal("SELECT '1e14'::VARCHAR + 0 FROM tb", "Numeric value '1e14' is not recognized");
        assertRefusal("SELECT '99999999999999'::VARCHAR + 0 FROM tb", "Numeric value '99999999999999' is not recognized");
        assertRefusal("SELECT '-12345678901234'::VARCHAR + 0 FROM tb", "Numeric value '-12345678901234' is out of range");
        assertRefusal("SELECT '9999999999999.999995'::VARCHAR + 0 FROM tb", "Numeric value '9999999999999.999995' is out of range");
        assertRefusal("SELECT '1e13'::VARCHAR + 0 FROM tb", "Numeric value '1e13' is out of range");
    }

    @Test
    public void theFunctionFormsReadTheSameWay() {
        assertTypes("SELECT SYSTEM$TYPEOF(ABS(t)), SYSTEM$TYPEOF(ROUND(t)), SYSTEM$TYPEOF(FLOOR(t)), SYSTEM$TYPEOF(CEIL(t)), "
            + "SYSTEM$TYPEOF(TRUNC(t)), SYSTEM$TYPEOF(SIGN(t)), SYSTEM$TYPEOF(SQRT(t)), SYSTEM$TYPEOF(POWER(t, 2)), "
            + "SYSTEM$TYPEOF(MOD(t, 2)), SYSTEM$TYPEOF(DIV0(t, 2)), SYSTEM$TYPEOF(DIV0NULL(t, 2)), SYSTEM$TYPEOF(ROUND(t, 1)), "
            + "SYSTEM$TYPEOF(TRUNC(t, 1)), SYSTEM$TYPEOF(CEIL(t, 1)), SYSTEM$TYPEOF(FLOOR(t, 1)) FROM ta",
            "FLOAT[DOUBLE]", "FLOAT[DOUBLE]", "FLOAT[DOUBLE]", "FLOAT[DOUBLE]", "FLOAT[DOUBLE]", "FLOAT[DOUBLE]", "FLOAT[DOUBLE]",
            "FLOAT[DOUBLE]", "NUMBER(18,5)[SB8]", "NUMBER(24,11)[SB16]", "NUMBER(24,11)[SB16]", "NUMBER(19,1)[SB16]",
            "NUMBER(19,1)[SB16]", "NUMBER(19,1)[SB16]", "NUMBER(19,1)[SB16]");
        final Row values = row("SELECT ABS(t), ROUND(t), SQRT(t), MOD(t, 2), DIV0(t, 2), DIV0NULL(t, 2), ROUND(t, 1), TRUNC(t, 1), "
            + "ROUND(t, 7), TRUNC(t, 9), CEIL(t, 6), ROUND(t, 5), SYSTEM$TYPEOF(ROUND(t, 5)), MOD(t, 2) + 0, "
            + "SYSTEM$TYPEOF(MOD(t, 2) + 0) FROM ta");
        assertDouble(5.0, values.getValue(0), "ABS(t)");
        assertDouble(5.0, values.getValue(1), "ROUND(t)");
        assertDouble(Math.sqrt(5.0), values.getValue(2), "SQRT(t)");
        assertEquals("1.00000", cell(values, 3));
        assertEquals("2.50000000000", cell(values, 4));
        assertEquals("2.50000000000", cell(values, 5));
        assertEquals("5.0", cell(values, 6));
        assertEquals("5.0", cell(values, 7));
        assertEquals("5.00000", cell(values, 8));
        assertEquals("5.00000", cell(values, 9));
        assertEquals("5.00000", cell(values, 10));
        assertEquals("5.00000", cell(values, 11));
        assertEquals("NUMBER(18,5)[SB8]", cell(values, 12));
        assertEquals("1.00000", cell(values, 13));
        assertEquals("NUMBER(19,5)[SB16]", cell(values, 14));
        final Row family = row("SELECT MOD(u, 2), SYSTEM$TYPEOF(MOD(u, 2)), MOD(u, 2.5), SYSTEM$TYPEOF(MOD(u, 2.5)), MOD(2, u), "
            + "SYSTEM$TYPEOF(MOD(2, u)), DIV0(u, 3), SYSTEM$TYPEOF(DIV0(u, 3)), DIV0(3, u), SYSTEM$TYPEOF(DIV0(3, u)), "
            + "DIV0NULL(u, 0), SYSTEM$TYPEOF(DIV0NULL(u, 0)), ROUND(u, 2), SYSTEM$TYPEOF(ROUND(u, 2)), TRUNC(u, 0), "
            + "SYSTEM$TYPEOF(TRUNC(u, 0)), ROUND(u, -1), SYSTEM$TYPEOF(ROUND(u, -1)), ROUND(u, 7), SYSTEM$TYPEOF(ROUND(u, 7)) FROM tb");
        final String[] expected = {"1.00000", "NUMBER(18,5)[SB8]", "2.00000", "NUMBER(18,5)[SB8]", "2.00000", "NUMBER(18,5)[SB8]",
            "2.33333333333", "NUMBER(24,11)[SB16]", "0.428571", "NUMBER(12,6)[SB8]", "0.00000000000", "NUMBER(24,11)[SB16]",
            "7.00", "NUMBER(19,2)[SB16]", "7", "NUMBER(19,0)[SB16]", "10", "NUMBER(19,0)[SB16]", "7.00000", "NUMBER(18,5)[SB8]"};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], cell(family, i), "column " + i);
        }
        assertTypes("SELECT SYSTEM$TYPEOF(IFF(TRUE, ta.t, 0)), SYSTEM$TYPEOF(COALESCE(ta.t, 0)), SYSTEM$TYPEOF(GREATEST(ta.t, 0)), "
            + "SYSTEM$TYPEOF(IFF(TRUE, u, 1::NUMBER(10,2))), SYSTEM$TYPEOF(COALESCE(u, 1::NUMBER(10,2))), "
            + "SYSTEM$TYPEOF(GREATEST(u, 1::NUMBER(10,2))), IFF(TRUE, u, 0), COALESCE(u, 0.5), GREATEST(u, 1::NUMBER(10,2)), "
            + "LEAST(u, 10) FROM ta, tb",
            "NUMBER(18,5)[SB8]", "NUMBER(18,5)[SB8]", "NUMBER(18,5)[SB8]", "NUMBER(18,5)[SB8]", "NUMBER(18,5)[SB8]",
            "NUMBER(18,5)[SB8]", "7.00000", "7.00000", "7.00000", "7.00000");
    }

    @Test
    public void aVariantInArithmeticIsAFloat() {
        assertTypes("SELECT SYSTEM$TYPEOF(v + 0), SYSTEM$TYPEOF(v * 1), SYSTEM$TYPEOF(v / 1), SYSTEM$TYPEOF(-v), SYSTEM$TYPEOF(v + 0.5), "
            + "SYSTEM$TYPEOF(v + n), SYSTEM$TYPEOF(ABS(v)), SYSTEM$TYPEOF(ROUND(v)), SYSTEM$TYPEOF(MOD(v, 2)), SYSTEM$TYPEOF(v % 2), "
            + "SYSTEM$TYPEOF(v + t), SYSTEM$TYPEOF(v + f), SYSTEM$TYPEOF(vs + 0), SYSTEM$TYPEOF(-vs), SYSTEM$TYPEOF(ABS(vs)) FROM ta",
            "FLOAT[DOUBLE]", "FLOAT[DOUBLE]", "FLOAT[DOUBLE]", "FLOAT[DOUBLE]", "FLOAT[DOUBLE]", "FLOAT[DOUBLE]", "FLOAT[DOUBLE]",
            "FLOAT[DOUBLE]", "FLOAT[DOUBLE]", "FLOAT[DOUBLE]", "FLOAT[DOUBLE]", "FLOAT[DOUBLE]", "FLOAT[DOUBLE]", "FLOAT[DOUBLE]",
            "FLOAT[DOUBLE]");
        final Row values = row("SELECT v + 0, v * 1, v / 1, -v, v + 0.5, v + n, ABS(v), ROUND(v), MOD(v, 2), v % 2, v + t, v + f, "
            + "vs + 0, vs * 2, -vs, ABS(vs) FROM ta");
        final double[] expected = {5, 5, 5, -5, 5.5, 7.5, 5, 5, 1, 1, 10, 7.5, 2.5, 5, -2.5, 2.5};
        for (int i = 0; i < expected.length; i++) {
            assertDouble(expected[i], values.getValue(i), "column " + i);
        }
    }
}
