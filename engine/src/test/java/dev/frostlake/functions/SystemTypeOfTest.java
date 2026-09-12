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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What {@code SYSTEM$TYPEOF} answers, which is a DECLARED type name and a physical storage tag drawn
 * from two different places. Frostlake read the evaluated VALUE for both, so a DATE answered
 * TIMESTAMP_NTZ, a TIME and a BINARY answered VARCHAR, every NUMBER answered FLOAT or INTEGER, no width
 * ever appeared, and any expression that happened to evaluate to NULL answered NULL however well typed.
 *
 * <p>★ THE NAME IS THE DECLARATION, THE TAG IS THE VALUE — and the pair that proves it shares a
 * declaration while differing only in what it holds:
 *
 * <pre>
 *   CAST(1.00    AS NUMBER(10,2))   NUMBER(10,2)[SB1]
 *   CAST(1000.00 AS NUMBER(10,2))   NUMBER(10,2)[SB4]
 * </pre>
 *
 * <p>★ SBn IS THE SMALLEST SIGNED-INTEGER WIDTH HOLDING THE UNSCALED VALUE, pinned at the byte
 * boundaries rather than guessed from a shape: 127 is SB1 and 128 is SB2, 32767 is SB2 and 32768 is
 * SB4. A NUMBER(38,0) holding 1 is SB1, so the DECLARED precision does not enter into the tag at all.
 *
 * <p>★ EVERY OTHER FAMILY'S TAG IS FIXED, and [LOB] is not the universal tag the string cells suggest:
 * BOOLEAN SB1, DATE SB4, TIME SB8, all three TIMESTAMP flavours SB16, FLOAT DOUBLE, and the
 * variable-length families LOB.
 *
 * <p>★ A NULL VALUE KEEPS ITS DECLARED TYPE and takes the family's smallest tag. Only an argument with
 * no type at all — a bare NULL — answers NULL[LOB].
 */
public class SystemTypeOfTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE tf (n102 NUMBER(10,2), i INT, f FLOAT, s VARCHAR(5),"
            + " d DATE, t TIME, ntz TIMESTAMP_NTZ, ltz TIMESTAMP_LTZ, tz TIMESTAMP_TZ,"
            + " b BOOLEAN, bin BINARY(10), arr ARRAY, obj OBJECT, v VARIANT)");
        engine.execute("INSERT INTO tf SELECT 1.00, 1, 1.5, 'abc', '2020-01-01', '01:02:03',"
            + " '2020-01-01 00:00:00', '2020-01-01 00:00:00', '2020-01-01 00:00:00 +0100',"
            + " TRUE, TO_BINARY('AB','HEX'), PARSE_JSON('[1]'), PARSE_JSON('{\"k\":1}'),"
            + " PARSE_JSON('1')");
    }

    private String typeOf(final String expr) {
        final ResultSet rs = engine.executeQuery("SELECT SYSTEM$TYPEOF(" + expr + ") FROM tf");
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** Each family read off a COLUMN, with its declared width and its own tag. */
    @Test
    public void eachFamilyKeepsItsDeclaredTypeAndTag() {
        assertEquals("NUMBER(10,2)[SB1]", typeOf("n102"));
        assertEquals("NUMBER(38,0)[SB1]", typeOf("i"));
        assertEquals("FLOAT[DOUBLE]", typeOf("f"));
        assertEquals("VARCHAR(5)[LOB]", typeOf("s"));
        assertEquals("BOOLEAN[SB1]", typeOf("b"));
        assertEquals("BINARY(10)[LOB]", typeOf("bin"));
        assertEquals("ARRAY[LOB]", typeOf("arr"));
        assertEquals("OBJECT[LOB]", typeOf("obj"));
        assertEquals("VARIANT[LOB]", typeOf("v"));
    }

    /** ★ The temporals, whose tags are all different — and whose zoned flavours are not NTZ. */
    @Test
    public void thetemporalsKeepTheirFlavourAndTheirOwnTags() {
        assertEquals("DATE[SB4]", typeOf("d"));
        assertEquals("TIME(9)[SB8]", typeOf("t"));
        assertEquals("TIMESTAMP_NTZ(9)[SB16]", typeOf("ntz"));
        assertEquals("TIMESTAMP_LTZ(9)[SB16]", typeOf("ltz"));
        assertEquals("TIMESTAMP_TZ(9)[SB16]", typeOf("tz"));
    }

    /** ★ THE TAG FOLLOWS THE VALUE: one declaration, two values, two tags. */
    @Test
    public void thetagFollowsTheValueNotTheDeclaration() {
        assertEquals("NUMBER(10,2)[SB1]", typeOf("CAST(1.00 AS NUMBER(10,2))"));
        assertEquals("NUMBER(10,2)[SB4]", typeOf("CAST(1000.00 AS NUMBER(10,2))"));
    }

    /** ★ The byte boundaries themselves, which a shape-based rule would miss. */
    @Test
    public void thetagIsTheSignedWidthOfTheUnscaledValue() {
        assertEquals("NUMBER(38,0)[SB1]", typeOf("CAST(127 AS NUMBER(38,0))"));
        assertEquals("NUMBER(38,0)[SB2]", typeOf("CAST(128 AS NUMBER(38,0))"));
        assertEquals("NUMBER(38,0)[SB2]", typeOf("CAST(32767 AS NUMBER(38,0))"));
        assertEquals("NUMBER(38,0)[SB4]", typeOf("CAST(32768 AS NUMBER(38,0))"));
        assertEquals("NUMBER(38,0)[SB8]", typeOf("CAST(99999999999 AS NUMBER(38,0))"));
        assertEquals("NUMBER(38,0)[SB16]",
            typeOf("CAST(99999999999999999999999999 AS NUMBER(38,0))"));
    }

    /** ★ A NULL VALUE keeps its declaration; only an untyped NULL is NULL[LOB]. */
    @Test
    public void anullValueKeepsItsDeclaredType() {
        assertEquals("NUMBER(10,2)[SB1]", typeOf("CAST(NULL AS NUMBER(10,2))"));
        assertEquals("DATE[SB4]", typeOf("CAST(NULL AS DATE)"));
        assertEquals("BOOLEAN[SB1]", typeOf("CAST(NULL AS BOOLEAN)"));
        assertEquals("VARCHAR(7)[LOB]", typeOf("CAST(NULL AS VARCHAR(7))"));
        assertEquals("FLOAT[DOUBLE]", typeOf("CAST(NULL AS FLOAT)"));
        assertEquals("TIMESTAMP_NTZ(9)[SB16]", typeOf("CAST(NULL AS TIMESTAMP_NTZ)"));
        assertEquals("NULL[LOB]", typeOf("NULL"), "an argument with no type at all");
    }

    /** An expression's width is computed, not the column's — and the arithmetic widens. */
    @Test
    public void anexpressionCarriesItsOwnComputedWidth() {
        assertEquals("VARCHAR(15)[LOB]", typeOf("UPPER(s)"), "UPPER can triple a length");
        assertEquals("VARCHAR(5)[LOB]", typeOf("SUBSTR(s, 1, 2)"));
        assertEquals("VARCHAR(10)[LOB]", typeOf("s || s"));
        assertEquals("NUMBER(11,2)[SB2]", typeOf("n102 + 1"));
    }

    /** A CAST states its own width, and the tag still follows the value. */
    @Test
    public void acastStatesItsOwnWidth() {
        assertEquals("VARCHAR(20)[LOB]", typeOf("s::VARCHAR(20)"));
        assertEquals("NUMBER(12,3)[SB2]", typeOf("i::NUMBER(12,3)"));
    }

    /** Literals of each family. */
    @Test
    public void aliteralIsTypedFromItsText() {
        assertEquals("NUMBER(1,0)[SB1]", typeOf("1"));
        assertEquals("VARCHAR(3)[LOB]", typeOf("'abc'"));
        assertEquals("BOOLEAN[SB1]", typeOf("TRUE"));
        assertEquals("DATE[SB4]", typeOf("DATE '2020-01-01'"));
    }
}
