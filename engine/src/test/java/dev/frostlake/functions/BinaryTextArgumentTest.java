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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The string functions that read their argument AS TEXT refuse a BINARY while the statement compiles, by
 * the argument types, and list every argument's type: UPPER, LOWER, INITCAP, the TRIM family, SOUNDEX, ASCII,
 * UNICODE, SPLIT, the REGEXP family, CONTAINS, STARTSWITH, ENDSWITH, EDITDISTANCE, LIKE, the PAD pair,
 * REPLACE, TRANSLATE, CHARINDEX and POSITION. The ones that take bytes as bytes answer: REVERSE, LENGTH,
 * SUBSTR, LEFT, RIGHT, CONCAT and the ENCODE pair. Every cell is live-verified.
 */
public class BinaryTextArgumentTest extends BaseDatabaseTest {

    private static final String B = "BINARY(8388608)";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE bt (i NUMBER(5,0), bn BINARY)");
        engine.execute("INSERT INTO bt SELECT 1, TO_BINARY('6162') UNION ALL SELECT 2, TO_BINARY('63')");
    }

    private void assertRefused(final String call, final String name, final String types) {
        final String sql = "SELECT " + call + " FROM bt";
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        final String expected = "error line 1 at position 7\nInvalid argument types for function '" + name + "': (" + types + ")";
        assertTrue(String.valueOf(refused.getMessage()).contains(expected), sql + " -> " + refused.getMessage());
    }

    @Test
    public void aTextFunctionRefusesABinaryByItsArgumentTypes() {
        for (final String name : new String[] {"UPPER", "LOWER", "INITCAP", "TRIM", "LTRIM", "RTRIM", "SOUNDEX", "ASCII", "UNICODE"}) {
            assertRefused(name + "(bn)", name, B);
        }
        for (final String name : new String[] {"SPLIT", "REGEXP_LIKE", "REGEXP_REPLACE", "REGEXP_SUBSTR", "REGEXP_INSTR",
                "REGEXP_COUNT", "CONTAINS", "STARTSWITH", "ENDSWITH", "EDITDISTANCE", "LIKE"}) {
            assertRefused(name + "(bn, 'a')", name, B + ", VARCHAR(1)");
        }
        assertRefused("LPAD(bn, 3)", "LPAD", B + ", NUMBER(1,0)");
        assertRefused("RPAD(bn, 3)", "RPAD", B + ", NUMBER(1,0)");
        assertRefused("REPLACE(bn, 'a', 'b')", "REPLACE", B + ", VARCHAR(1), VARCHAR(1)");
        assertRefused("TRANSLATE(bn, 'a', 'b')", "TRANSLATE", B + ", VARCHAR(1), VARCHAR(1)");
        assertRefused("CHARINDEX('a', bn)", "CHARINDEX", "VARCHAR(1), " + B);
        assertRefused("POSITION('a', bn)", "POSITION", "VARCHAR(1), " + B);
        assertRefused("UPPER(TO_BINARY('ab'))", "UPPER", "BINARY(67108864)");
        // All-binary is refused too for the functions that only ever read text.
        for (final String name : new String[] {"SPLIT", "REGEXP_LIKE", "REGEXP_COUNT", "REGEXP_INSTR", "REGEXP_SUBSTR",
                "REGEXP_REPLACE", "EDITDISTANCE", "LIKE", "TRIM", "LTRIM", "RTRIM"}) {
            assertRefused(name + "(bn, bn)", name, B + ", " + B);
        }
        assertRefused("REPLACE(bn, bn, bn)", "REPLACE", B + ", " + B + ", " + B);
        assertRefused("TRANSLATE(bn, bn, bn)", "TRANSLATE", B + ", " + B + ", " + B);
        for (final String name : new String[] {"CONTAINS", "STARTSWITH", "EDITDISTANCE", "SPLIT", "REGEXP_LIKE", "TRIM", "LIKE"}) {
            assertRefused(name + "('a', bn)", name, "VARCHAR(1), " + B);
        }
        assertRefused("LPAD('a', 3, bn)", "LPAD", "VARCHAR(1), NUMBER(1,0), " + B);
        assertRefused("REPLACE('a', bn, 'b')", "REPLACE", "VARCHAR(1), " + B + ", VARCHAR(1)");
    }

    @Test
    public void aByteCapableFunctionTakesBinariesThroughout() {
        assertEquals("y, y, y, 1, 1, 616162, 616261", row("""
            SELECT IFF(CONTAINS(bn, bn), 'y', 'n'), IFF(STARTSWITH(bn, bn), 'y', 'n'), IFF(ENDSWITH(bn, bn), 'y', 'n'),
                CHARINDEX(bn, bn), POSITION(bn, bn), HEX_ENCODE(LPAD(bn, 3, bn)), HEX_ENCODE(RPAD(bn, 3, bn))
            FROM bt ORDER BY i"""));
    }

    private String row(final String sql) {
        final StringBuilder out = new StringBuilder();
        final List<Object> values = engine.executeQuery(sql).getRows().get(0).getValues();
        for (int c = 0; c < values.size(); c++) {
            if (c > 0) {
                out.append(", ");
            }
            out.append(values.get(c));
        }
        return out.toString();
    }

    @Test
    public void aByteFunctionAnswers() {
        assertEquals("6261", engine.executeQuery("SELECT HEX_ENCODE(REVERSE(bn)) FROM bt ORDER BY i").getRows().get(0).getValue(0));
        assertEquals("2", String.valueOf(engine.executeQuery("SELECT LENGTH(bn) FROM bt ORDER BY i").getRows().get(0).getValue(0)));
        assertEquals("61", engine.executeQuery("SELECT HEX_ENCODE(SUBSTR(bn, 1, 1)) FROM bt ORDER BY i").getRows().get(0).getValue(0));
        assertEquals("61", engine.executeQuery("SELECT HEX_ENCODE(LEFT(bn, 1)) FROM bt ORDER BY i").getRows().get(0).getValue(0));
        assertEquals("62", engine.executeQuery("SELECT HEX_ENCODE(RIGHT(bn, 1)) FROM bt ORDER BY i").getRows().get(0).getValue(0));
        assertEquals("61626162", engine.executeQuery("SELECT HEX_ENCODE(CONCAT(bn, bn)) FROM bt ORDER BY i").getRows().get(0).getValue(0));
        assertEquals("YWI=", engine.executeQuery("SELECT BASE64_ENCODE(bn) FROM bt ORDER BY i").getRows().get(0).getValue(0));
    }
}
