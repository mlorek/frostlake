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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A cast to a sized string holds EVERY source to the width, on the text the source converts to: a
 * number, a FLOAT, a boolean, a date, a time, a timestamp, a BINARY and a VARIANT are refused exactly as
 * a text is, with {@code String '<text>' is too long and would be truncated}. The refusal is per row,
 * so a row that fits, or no row at all, answers. Only two constants escape it: a BINARY constant and a
 * number or boolean constant wrapped straight into a VARIANT ({@code TO_VARIANT(123)::VARCHAR(1)} is
 * {@code 123}). The account answers a column holding a single value the way it answers a constant, so
 * the row-time cells read columns with two distinct values. Every cell is live-verified.
 */
public class SizedStringCastTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE sc (i INT, n NUMBER, f FLOAT, b BOOLEAN, bin BINARY, v VARIANT)");
        engine.execute("INSERT INTO sc SELECT 1, 123, 1.5, FALSE, X'ABCDEF', TO_VARIANT('abc')"
            + " UNION ALL SELECT 2, 7, 2, TRUE, X'AB', TO_VARIANT('x')");
    }

    private Object value(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    private String cell(final String sql) {
        return String.valueOf(value(sql));
    }

    private List<String> column(final String sql) {
        final List<String> texts = new ArrayList<>();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            texts.add(String.valueOf(row.getValue(0)));
        }
        return texts;
    }

    private void assertTooLong(final String sql, final String text) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains("String '" + text + "' is too long and would be truncated"),
            sql + " -> " + refused.getMessage());
    }

    @Test
    public void aNonTextSourceIsHeldToTheWidthOnItsConvertedText() {
        assertTooLong("SELECT 123::VARCHAR(2)", "123");
        assertTooLong("SELECT CAST(123 AS VARCHAR(2))", "123");
        assertTooLong("SELECT 1.5::VARCHAR(2)", "1.5");
        assertTooLong("SELECT 0.5::VARCHAR(2)", "0.5");
        assertTooLong("SELECT (-12)::VARCHAR(2)", "-12");
        assertTooLong("SELECT 1.5::FLOAT::VARCHAR(2)", "1.5");
        assertTooLong("SELECT 1e10::FLOAT::VARCHAR(5)", "10000000000");
        assertTooLong("SELECT TRUE::VARCHAR(2)", "true");
        assertTooLong("SELECT '2024-01-01'::DATE::VARCHAR(5)", "2024-01-01");
        assertTooLong("SELECT TIME '12:34:56'::VARCHAR(5)", "12:34:56");
        assertTooLong("SELECT '2024-01-01 00:00:00'::TIMESTAMP_NTZ::VARCHAR(5)", "2024-01-01 00:00:00.000");
    }

    @Test
    public void everySizedStringSpellingChecks() {
        assertTooLong("SELECT 123::STRING(2)", "123");
        assertTooLong("SELECT 123::TEXT(2)", "123");
        assertTooLong("SELECT 123::CHAR(2)", "123");
    }

    @Test
    public void aVariantHoldingTextAContainerOrADateChecks() {
        assertTooLong("SELECT PARSE_JSON('\"abc\"')::VARCHAR(2)", "abc");
        assertTooLong("SELECT TO_VARIANT('ab')::VARCHAR(1)", "ab");
        assertTooLong("SELECT PARSE_JSON('[1,2]')::VARCHAR(2)", "[1,2]");
        assertTooLong("SELECT ARRAY_CONSTRUCT(1)::VARCHAR(2)", "[1]");
        assertTooLong("SELECT OBJECT_CONSTRUCT('a', 1)::VARCHAR(2)", "{\"a\":1}");
        assertTooLong("SELECT '2024-01-01'::DATE::VARIANT::VARCHAR(2)", "2024-01-01");
        // A number, a boolean or a double parsed from JSON text is no wrap, and neither is a computed value.
        assertTooLong("SELECT PARSE_JSON('12345')::VARCHAR(2)", "12345");
        assertTooLong("SELECT PARSE_JSON('true')::VARCHAR(2)", "true");
        assertTooLong("SELECT PARSE_JSON('1.5e0')::VARCHAR(2)", "1.5");
        assertTooLong("SELECT TO_VARIANT(SQRT(2))::VARCHAR(3)", "1.414213562");
        // A number read out through a path is a number like any other.
        assertTooLong("SELECT PARSE_JSON('{\"a\":123}'):a::VARCHAR(1)", "123");
        // The wrapped number is not checked, but the text it becomes is, by the next cast.
        assertTooLong("SELECT TO_VARIANT(123)::VARCHAR(2)::VARCHAR(1)", "123");
    }

    @Test
    public void whatFitsIsAnswered() {
        assertEquals("true", cell("SELECT TRUE::VARCHAR(4)"));
        assertEquals("123", cell("SELECT 123::VARCHAR(3)"));
        assertEquals("12", cell("SELECT 12::VARCHAR(2)"));
        assertEquals("1.5", cell("SELECT 1.50::VARCHAR(3)"));
        assertNull(value("SELECT NULL::VARCHAR(2)"));
        assertNull(value("SELECT PARSE_JSON('null')::VARCHAR(2)"));
        // The cast binds tighter than the unary minus: the width applies to 12, and the minus then reads
        // the text back as a number.
        assertEquals(-12.0, ((Number) value("SELECT -12::VARCHAR(2)")).doubleValue());
    }

    @Test
    public void aBinaryConstantOrAWrappedNumberConstantIsNotChecked() {
        assertEquals("ABCD", cell("SELECT X'ABCD'::VARCHAR(2)"));
        assertEquals("ABCDEF", cell("SELECT TO_BINARY('ABCDEF')::VARCHAR(2)"));
        assertEquals("123", cell("SELECT TO_VARIANT(123)::VARCHAR(1)"));
        assertEquals("123", cell("SELECT 123::VARIANT::VARCHAR(1)"));
        assertEquals("123", cell("SELECT CAST(CAST(123 AS VARIANT) AS VARCHAR(1))"));
        assertEquals("123", cell("SELECT TO_VARIANT(TO_VARIANT(123))::VARCHAR(1)"));
        assertEquals("123", cell("SELECT TO_VARIANT(123)::CHAR(1)"));
        assertEquals("-5", cell("SELECT TO_VARIANT(-5)::VARCHAR(1)"));
        assertEquals("12345678901234567890", cell("SELECT TO_VARIANT(12345678901234567890)::VARCHAR(2)"));
        assertEquals("true", cell("SELECT TO_VARIANT(TRUE)::VARCHAR(1)"));
        assertEquals("1.5", cell("SELECT TO_VARIANT(1.5::FLOAT)::VARCHAR(1)"));
        assertEquals("1.5", cell("SELECT TO_VARIANT(1.5)::VARCHAR(2)"));
        assertEquals(List.of("123", "123"), column("SELECT TO_VARIANT(123)::VARCHAR(1) FROM sc"));
        assertEquals(List.of("ABCDEF", "ABCDEF"), column("SELECT X'ABCDEF'::VARCHAR(1) FROM sc"));
    }

    @Test
    public void theRefusalIsPerRow() {
        assertTooLong("SELECT n::VARCHAR(2) FROM sc ORDER BY i", "123");
        assertEquals(List.of("7"), column("SELECT n::VARCHAR(2) FROM sc WHERE i = 2"));
        assertEquals(List.of(), column("SELECT n::VARCHAR(2) FROM sc WHERE 1 = 0"));
        assertTooLong("SELECT b::VARCHAR(4) FROM sc ORDER BY i", "false");
        assertTooLong("SELECT f::VARCHAR(2) FROM sc ORDER BY i", "1.5");
        assertTooLong("SELECT v::VARCHAR(2) FROM sc ORDER BY i", "abc");
        // Per row, a BINARY and a wrapped number are checked like any other source.
        assertTooLong("SELECT bin::VARCHAR(1) FROM sc ORDER BY i", "ABCDEF");
        assertTooLong("SELECT bin::VARCHAR(1) FROM sc WHERE i = 1", "ABCDEF");
        assertTooLong("SELECT TO_VARIANT(n)::VARCHAR(1) FROM sc ORDER BY i", "123");
        assertTooLong("SELECT TO_VARIANT(n)::VARCHAR(1) FROM sc WHERE i = 1", "123");
        assertTooLong("SELECT n::VARIANT::VARCHAR(1) FROM sc ORDER BY i", "123");
        assertTooLong("SELECT TO_VARIANT(b)::VARCHAR(1) FROM sc ORDER BY i", "false");
        assertTooLong("SELECT TO_VARIANT(f)::VARCHAR(1) FROM sc ORDER BY i", "1.5");
        assertTooLong("WITH c AS (SELECT TO_VARIANT(n) AS w, i FROM sc) SELECT w::VARCHAR(1) FROM c ORDER BY i", "123");
        assertTooLong("SELECT IFF(i = 1, TO_VARIANT(n), NULL)::VARCHAR(1) FROM sc ORDER BY i", "123");
        // IFF evaluates only the branch it takes, so the row that would not fit is never cast.
        assertEquals(List.of("x", "7"), column("SELECT IFF(n > 100, 'x', n::VARCHAR(2)) FROM sc ORDER BY i"));
    }
}
