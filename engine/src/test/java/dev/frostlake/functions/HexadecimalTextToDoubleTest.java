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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A text spelling a hexadecimal number reads as a DOUBLE wherever a text converts to FLOAT, its binary
 * exponent optional ({@code '0x10'} is 16, {@code '0x1.8'} 1.5, {@code '0x10f'} 271): the FLOAT cast,
 * TO_DOUBLE, a VARIANT's cast, a write into a FLOAT column, a comparison with or arithmetic on a FLOAT or
 * another text, a sign, GREATEST and LEAST beside a FLOAT, and a set operation beside a FLOAT arm. A sign
 * reads there, but TRY_TO_DOUBLE and TRY_CAST answer NULL for a signed hexadecimal number with no
 * exponent. Beside a NUMBER, and in TO_NUMBER or TO_DECFLOAT, a hexadecimal text is refused. Every cell is
 * live-verified.
 */
public class HexadecimalTextToDoubleTest extends BaseDatabaseTest {

    /** The first row's cells, numbers as doubles, a comma between cells and a bar between rows, or the refusal. */
    private String answer(final String sql) {
        try {
            final StringBuilder out = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                out.append(out.length() > 0 ? " | " : "");
                for (int i = 0; i < row.getValues().size(); i++) {
                    final Object value = row.getValue(i);
                    out.append(i > 0 ? ", " : "")
                        .append(value instanceof Number ? String.valueOf(((Number) value).doubleValue()) : String.valueOf(value));
                }
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private void assertCells(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    @Test
    public void aCastReadsAHexadecimalText() {
        assertCells(new String[][] {
            {"SELECT '0x10'::DOUBLE, '0X1A'::FLOAT, '0x10'::REAL, CAST('0x10' AS DOUBLE PRECISION)", "16.0, 26.0, 16.0, 16.0"},
            {"SELECT '-0x10'::DOUBLE, '+0x10'::DOUBLE, '  0x10  '::DOUBLE", "-16.0, 16.0, 16.0"},
            {"SELECT '0x1.8'::DOUBLE, '0x.8'::DOUBLE, '-0x1.8'::DOUBLE", "1.5, 0.5, -1.5"},
            {"SELECT '0x1e3'::DOUBLE, '0x10f'::DOUBLE, '0x10d'::DOUBLE", "483.0, 271.0, 269.0"},
            {"SELECT '0x1p3'::DOUBLE, '0x10.8p1'::DOUBLE, '0x1P-1'::DOUBLE", "8.0, 33.0, 0.5"},
            {"SELECT '0xFFFFFFFF'::DOUBLE, '0x7FFFFFFFFFFF'::DOUBLE", "4.294967295E9, 1.40737488355327E14"},
            {"SELECT TO_DOUBLE('0x10'), TO_DOUBLE('-0x10'), TO_DOUBLE(' +0x1p3 ')", "16.0, -16.0, 8.0"},
            {"SELECT TRY_TO_DOUBLE('0x10'), TRY_TO_DOUBLE('-0x10'), TRY_TO_DOUBLE('+0x10'), TRY_TO_DOUBLE('-0x1p0')",
                "16.0, null, null, -1.0"},
            {"SELECT TRY_TO_DOUBLE('0xZZ'), TRY_TO_DOUBLE('0x10', '999')", "null, null"},
            {"SELECT TRY_CAST('0x10' AS DOUBLE), TRY_CAST('-0x10' AS DOUBLE), TRY_CAST('+0x10' AS DOUBLE)", "16.0, null, null"},
            {"SELECT PARSE_JSON('\"0x10\"')::DOUBLE, PARSE_JSON('\"-0x10\"')::DOUBLE, TO_DOUBLE(PARSE_JSON('\"-0x10\"'))",
                "16.0, -16.0, -16.0"},
            {"SELECT TO_VARIANT('0x10')::DOUBLE, '0x10'::VARIANT::DOUBLE, AS_DOUBLE(PARSE_JSON('\"0x10\"'))", "16.0, 16.0, null"},
        });
    }

    @Test
    public void aTextSpellingNoHexadecimalNumberIsRefused() {
        final String[] unreadable = {"0x", "0xg", "00x10", "0x_10", "0b101", "0x10p"};
        for (final String text : unreadable) {
            assertEquals("Numeric value '" + text + "' is not recognized", answer("SELECT '" + text + "'::DOUBLE"), text);
        }
        assertCells(new String[][] {
            {"SELECT '0x10'::NUMBER", "Numeric value '0x10' is not recognized"},
            {"SELECT TO_NUMBER('0x10')", "Numeric value '0x10' is not recognized"},
            {"SELECT TO_DECFLOAT('0x10')", "Numeric value '0x10' is not recognized"},
            {"SELECT '0x10' = 16", "Numeric value '0x10' is not recognized"},
            {"SELECT '0x10' + 1", "Numeric value '0x10' is not recognized"},
            {"SELECT GREATEST(1, '0x20')", "Numeric value '0x20' is not recognized"},
            {"SELECT 1 UNION ALL SELECT '0x10'", "Numeric value '0x10' is not recognized"},
            {"SELECT TO_DOUBLE('0x10', '999')", "Can't parse '0x10' as number with format '999'"},
        });
    }

    @Test
    public void aFloatBesideAHexadecimalTextReadsIt() {
        engine.execute("CREATE OR REPLACE TABLE hex_float (f FLOAT, n NUMBER(10,2))");
        engine.execute("INSERT INTO hex_float VALUES ('0x10', 16)");
        assertEquals("16.0", answer("SELECT f FROM hex_float"));
        assertCells(new String[][] {
            {"SELECT f = '0x10', f > '0x0', f <> '0x10', f = '-0x10', f = '+0x10' FROM hex_float",
                "true, true, false, false, true"},
            {"SELECT f IN ('0x10'), f BETWEEN '0x1' AND '0x20', '0x10' = f FROM hex_float", "true, true, true"},
            {"SELECT f * '0x2', f - '0x2', f / '0x2' FROM hex_float", "32.0, 14.0, 8.0"},
            {"SELECT '0x10' + '0x1', -'0x10', '0x10' + 1.5::FLOAT, '0x10' + TO_DOUBLE(1)", "17.0, -16.0, 17.5, 17.0"},
            {"SELECT GREATEST(f, '0x20'), LEAST('0x20', f), GREATEST(f, '0x20', '0x8') FROM hex_float", "32.0, 16.0, 32.0"},
            {"SELECT n = '0x10' FROM hex_float", "Numeric value '0x10' is not recognized"},
            {"SELECT 1.5::FLOAT AS x UNION ALL SELECT '0x10' ORDER BY 1", "1.5 | 16.0"},
            {"SELECT '0x10' UNION ALL SELECT 1.5::FLOAT ORDER BY 1", "1.5 | 16.0"},
            {"SELECT f FROM hex_float UNION SELECT '-0x10' ORDER BY 1", "-16.0 | 16.0"},
            {"SELECT f FROM hex_float INTERSECT SELECT '0x10'", "16.0"},
        });
        engine.execute("INSERT INTO hex_float (f) SELECT '0x20'");
        engine.execute("INSERT INTO hex_float (f) VALUES ('+0x10')");
        assertEquals("16.0 | 16.0 | 32.0", answer("SELECT f FROM hex_float ORDER BY f"));
        engine.execute("UPDATE hex_float SET f = '-0x2'");
        assertEquals("-2.0", answer("SELECT DISTINCT f FROM hex_float"));
    }
}
