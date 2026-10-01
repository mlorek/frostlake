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
import dev.frostlake.types.DataType;
import dev.frostlake.types.StringType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A character outside the Basic Multilingual Plane — an emoji, a mathematical letter — is ONE character to
 * every string function, never two: it is counted once, a cut never splits it, a position is counted in
 * characters, and a declared {@code VARCHAR(n)} holds n of them. Counting UTF-16 units instead made
 * {@code LENGTH('😀')} 2 and handed back half a character from SUBSTR, LEFT and RIGHT.
 */
public class SupplementaryCharacterTest extends BaseDatabaseTest {

    /** The one-column answer of a FROM-less query, as text. */
    private String answer(final String expr) {
        final ResultSet rs = engine.executeQuery("SELECT " + expr);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** The declared width of a string expression. */
    private int declaredWidth(final String expr) {
        final DataType type = engine.executeQuery("SELECT " + expr + " AS x").getColumns().get(0).getDataType();
        return ((StringType) type).getMaxLength();
    }

    /** A supplementary character counts once. */
    @Test
    public void aSupplementaryCharacterCountsOnce() {
        assertEquals("1", answer("LENGTH('😀')"));
        assertEquals("1", answer("LEN('😀')"));
        assertEquals("3", answer("LENGTH('𝐀bb')"));
        assertEquals("4", answer("LENGTH('a😀b😀')"));
        assertEquals("3", answer("LENGTH(REPEAT('😀', 3))"));
        assertEquals("1", answer("RTRIMMED_LENGTH('😀  ')"));
        assertEquals("4", answer("OCTET_LENGTH('😀')"), "OCTET_LENGTH still counts bytes");
    }

    /** A cut never splits one, and its window is counted in characters. */
    @Test
    public void aCutNeverSplitsOne() {
        assertEquals("ab", answer("SUBSTR('😀ab', 2)"));
        assertEquals("😀", answer("SUBSTR('a😀b', 2, 1)"));
        assertEquals("😀b", answer("SUBSTR('a😀b', -2)"));
        assertEquals("", answer("SUBSTR('😀ab', 4)"));
        assertEquals("😀", answer("LEFT('😀ab', 1)"));
        assertEquals("😀😀", answer("LEFT('😀😀😀', 2)"));
        assertEquals("😀", answer("RIGHT('a😀', 1)"));
        assertEquals("😀Xbc", answer("INSERT('😀abc', 2, 1, 'X')"));
        assertEquals("aXbc", answer("INSERT('a😀bc', 2, 1, 'X')"));
        assertEquals("Y", answer("IFF(SUBSTR('😀ab', 1, 1) = '😀', 'Y', 'N')"));
    }

    /** A position is counted in characters, both the one given and the one answered. */
    @Test
    public void aPositionCountsCharacters() {
        assertEquals("2", answer("CHARINDEX('a', '😀a')"));
        assertEquals("2", answer("POSITION('a' IN '😀a')"));
        assertEquals("4", answer("POSITION('a', '😀a😀a', 3)"));
        assertEquals("3", answer("CHARINDEX('a', '😀😀a', 2)"));
        assertEquals("0", answer("CHARINDEX('a', '😀😀a', 4)"));
        assertEquals("2", answer("REGEXP_INSTR('😀a', 'a')"));
        assertEquals("3", answer("REGEXP_INSTR('😀😀a', 'a', 2)"));
        assertEquals("3", answer("REGEXP_INSTR('😀a😀b', '[a-z]', 1, 1, 1)"));
        assertEquals("😀", answer("REGEXP_SUBSTR('😀😀ab', '.', 2)"));
        assertEquals("😀x", answer("REGEXP_REPLACE('😀😀', '.', 'x', 2)"));
    }

    /** A pad is laid down and cut in characters. */
    @Test
    public void aPadCountsCharacters() {
        assertEquals("xx😀", answer("LPAD('😀', 3, 'x')"));
        assertEquals("😀xx", answer("RPAD('😀', 3, 'x')"));
        assertEquals("😀😀😀ab", answer("LPAD('ab', 5, '😀')"));
        assertEquals("ab😀x😀", answer("RPAD('ab', 5, '😀x')"));
        assertEquals("😀😀", answer("LPAD('😀😀😀', 2)"));
        assertEquals("😀", answer("RPAD('😀a', 1)"));
    }

    /** A character-by-character function sees whole characters. */
    @Test
    public void aCharacterMapSeesWholeCharacters() {
        assertEquals("😀😀", answer("TRANSLATE('a😀b', 'ab', '😀')"));
        assertEquals("aXb", answer("TRANSLATE('a😀b', '😀', 'X')"));
        assertEquals("1", answer("EDITDISTANCE('😀', 'a')"));
        assertEquals("1", answer("EDITDISTANCE('a😀b', 'ab')"));
        assertEquals("😁a😁", answer("TRIM('😁a😁', '😀')"), "a different emoji sharing a first unit stays");
        assertEquals("😁a", answer("LTRIM('😁a', '😀')"));
        assertEquals("a", answer("TRIM('😀a😁', '😀😁')"));
    }

    /** ASCII answers the lead byte of the character's UTF-8 encoding. */
    @Test
    public void asciiAnswersTheLeadByte() {
        assertEquals("97", answer("ASCII('a')"));
        assertEquals("195", answer("ASCII('é')"));
        assertEquals("226", answer("ASCII('€')"));
        assertEquals("240", answer("ASCII('😀')"));
        assertEquals("0", answer("ASCII('')"));
    }

    /** A literal's declared width counts characters, and so does everything derived from it. */
    @Test
    public void aLiteralsWidthCountsCharacters() {
        assertEquals(1, declaredWidth("'😀'"));
        assertEquals(3, declaredWidth("'😀😀a'"));
        assertEquals(3, declaredWidth("SUBSTR('😀ab', 2)"));
        assertEquals(6, declaredWidth("UPPER('😀a')"));
    }

    /** A declared VARCHAR(n) holds n characters, supplementary ones included. */
    @Test
    public void aDeclaredWidthHoldsThatManyCharacters() {
        engine.execute("CREATE OR REPLACE TABLE v2 (s VARCHAR(2))");
        engine.execute("INSERT INTO v2 VALUES ('😀😀')");
        final ResultSet rs = engine.executeQuery("SELECT s, LENGTH(s) FROM v2");
        rs.next();
        assertEquals("😀😀", String.valueOf(rs.getValue(0)));
        assertEquals("2", String.valueOf(rs.getValue(1)));
        assertEquals("😀😀", answer("CAST('😀😀' AS VARCHAR(2))"));
        assertEquals("😀😀", answer("'😀😀'::VARCHAR(2)"));

        final RuntimeException tooLong = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT '😀😀😀'::VARCHAR(2)").next();
            }
        });
        assertTrue(String.valueOf(tooLong.getMessage())
            .contains("String '😀😀😀' is too long and would be truncated"), tooLong.getMessage());
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("INSERT INTO v2 VALUES ('😀😀😀')");
            }
        });
        assertTrue(String.valueOf(refused.getMessage())
            .contains("String '😀😀😀' is too long and would be truncated"), refused.getMessage());
    }
}
