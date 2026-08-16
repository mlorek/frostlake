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

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

/**
 * A JSON number's family is decided by its NOTATION and by nothing else — least of all by how many
 * digits it happens to carry. Frostlake used to read a plain fraction of more than fifteen significant
 * digits as a DOUBLE, on the theory that no decimal would be written that wide, and that guess cost the
 * value its digits: {@code 1.2345678901234567890123456789} came back as {@code 1.23456789}.
 *
 * <p>★ DECIMAL ALL THE WAY UP, measured on live at 15, 16, 17, 18, 20, 30 and 38 digits — every one
 * DECIMAL, every one rendering its full text. There is no width at which a plain fraction turns into a
 * double, so there is no threshold to tune.
 *
 * <p>★ THE EXPONENT IS THE WHOLE SIGNAL, and the pair below says so: the same digits written
 * {@code 8.999999761581421e-01} are a DOUBLE and render at ten significant digits, while written plainly
 * they are a DECIMAL and keep all of theirs.
 *
 * <p>The RAW value of a wide fraction is deliberately not asserted: the live harness stringifies a
 * VARIANT member through a double, so it shows seventeen digits where the account's own {@code ::VARCHAR}
 * and TO_JSON show all of them. The two conversions are what this pins.
 */
public class JsonPlainDecimalWidthTest extends BaseDatabaseTest {

    private static final String W15 = "1.23456789012345";
    private static final String W17 = "1.2345678901234567";
    private static final String W20 = "1.234567890123456789";
    private static final String W38 = "1.2345678901234567890123456789012345678";

    private String cellOf(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String typeOfElement(final String json) {
        return cellOf("SELECT TYPEOF(PARSE_JSON('[" + json + "]')[0]) AS a");
    }

    private String textOfElement(final String json) {
        return cellOf("SELECT PARSE_JSON('[" + json + "]')[0]::VARCHAR AS a");
    }

    @Test
    public void aPlainFractionIsDecimalAtEveryWidth() {
        assertEquals("DECIMAL", typeOfElement(W15));
        assertEquals("DECIMAL", typeOfElement("1.234567890123456"));
        assertEquals("DECIMAL", typeOfElement(W17));
        assertEquals("DECIMAL", typeOfElement("1.23456789012345678"));
        assertEquals("DECIMAL", typeOfElement(W20));
        assertEquals("DECIMAL", typeOfElement("1.23456789012345678901234567890"));
        assertEquals("DECIMAL", typeOfElement(W38));
    }

    @Test
    public void andItKeepsEveryDigitThroughTheConversions() {
        assertEquals(W15, textOfElement(W15));
        assertEquals(W17, textOfElement(W17));
        assertEquals(W20, textOfElement(W20));
        assertEquals(W38, textOfElement(W38));
        assertEquals("[" + W38 + "]", cellOf("SELECT TO_JSON(PARSE_JSON('[" + W38 + "]')) AS a"));
    }

    @Test
    public void theExponentFormIsWhatMakesADouble() {
        assertEquals("DOUBLE", typeOfElement("8.999999761581421e-01"));
        // Ten significant digits — the FLOAT-to-VARCHAR rendering, which the DECIMAL above escapes.
        assertEquals("0.8999999762", textOfElement("8.999999761581421e-01"));
        assertEquals("DECIMAL", cellOf("SELECT TYPEOF(PARSE_JSON('1.234567890123456')) AS a"));
        assertEquals("DOUBLE", cellOf("SELECT TYPEOF(PARSE_JSON('8.999999761581421e-01')) AS a"));
    }

    @Test
    public void aStoredValueAndAFlattenedOneReadTheSameWay() {
        engine.execute("CREATE TABLE jw (v VARIANT)");
        engine.execute("INSERT INTO jw SELECT PARSE_JSON('[1.234567890123456, 8.999999761581421e-01]')");
        assertEquals("DECIMAL", cellOf("SELECT TYPEOF(v[0]) AS a FROM jw"));
        assertEquals("DOUBLE", cellOf("SELECT TYPEOF(v[1]) AS a FROM jw"));
        assertEquals("1.234567890123456", cellOf("SELECT v[0]::VARCHAR AS a FROM jw"));
        assertEquals("DECIMAL",
            cellOf("SELECT TYPEOF(f.value) AS a FROM jw, LATERAL FLATTEN(input => v) f WHERE f.index = 0"));
        assertEquals("DOUBLE",
            cellOf("SELECT TYPEOF(f.value) AS a FROM jw, LATERAL FLATTEN(input => v) f WHERE f.index = 1"));
    }

    @Test
    public void anIntegerPastThirtyEightDigitsIsStillAnInteger() {
        assertEquals("INTEGER", typeOfElement("123456789012345678901234567890123456789"));
        assertEquals("123456789012345678901234567890123456789",
            cellOf("SELECT PARSE_JSON('[123456789012345678901234567890123456789]')[0] AS a"));
    }
}
