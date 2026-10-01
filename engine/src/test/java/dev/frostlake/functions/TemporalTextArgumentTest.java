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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A text function reads a DATE, TIME or timestamp argument as its DISPLAY text — the text {@code ||} and
 * {@code ::VARCHAR} give it — never as the engine's internal spelling of the value. A timestamp therefore
 * reads with a space and exactly three fractional digits ({@code 2020-01-01 10:00:00.123}, whatever
 * precision it holds), a zoned one with its {@code ±HHMM} offset, and a TIME with no fraction at all.
 *
 * <p>The same text is what LIKE matches, and a concatenation of such a value — CONCAT, CONCAT_WS or INSERT
 * — is declared VARCHAR(134217728), since nothing bounds a value's text.
 */
public class TemporalTextArgumentTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        engine.execute("CREATE OR REPLACE TABLE tt (n0 TIMESTAMP_NTZ(0), n9 TIMESTAMP_NTZ(9),"
            + " l3 TIMESTAMP_LTZ(3), z3 TIMESTAMP_TZ(3), t0 TIME(0), t9 TIME(9), d DATE, i INT)");
        engine.execute("INSERT INTO tt SELECT '2020-01-01 10:00:00', '2020-01-01 10:00:00.123456789',"
            + " '2020-01-01 10:00:00.5', '2020-01-01 10:00:00.5 +01:00', '10:00:00', '10:00:00.123456789',"
            + " '2020-01-01', 7");
    }

    /** The expression's value over the one row, as text. */
    private String answer(final String expr) {
        final ResultSet rs = engine.executeQuery("SELECT " + expr + " FROM tt");
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** The declared width of the expression's result, which must be a string. */
    private int declaredWidth(final String expr) {
        final DataType type = engine.executeQuery("SELECT " + expr + " AS x FROM tt").getColumns().get(0)
            .getDataType();
        return ((StringType) type).getMaxLength();
    }

    /** A timestamp reads as its display text: a space, three fractional digits, an offset when zoned. */
    @Test
    public void aTimestampReadsAsItsDisplayText() {
        assertEquals("2020-01-01 10:00:00.000", answer("UPPER(n0)"));
        assertEquals("2020-01-01 10:00:00.123", answer("UPPER(n9)"));
        assertEquals("2020-01-01 10:00:00.500 -0800", answer("UPPER(l3)"));
        assertEquals("2020-01-01 10:00:00.500 +0100", answer("LOWER(z3)"));
        assertEquals("2020-01-01", answer("UPPER(d)"));
        assertEquals("23", answer("LENGTH(n0)"));
        assertEquals("23", answer("LENGTH(n9)"));
        assertEquals("29", answer("LENGTH(z3)"));
        assertEquals("321.00:00:01 10-10-0202", answer("REVERSE(n9)"));
        assertEquals("2020-01-01 10:00:00.500 +0100", answer("INITCAP(z3)"));
        assertEquals("2020-01-01 10:00:00.123", answer("TRIM(n9)"));
    }

    /** A TIME reads with no fraction, whatever its precision. */
    @Test
    public void aTimeReadsWithNoFraction() {
        assertEquals("10:00:00", answer("UPPER(t0)"));
        assertEquals("10:00:00", answer("UPPER(t9)"));
        assertEquals("8", answer("LENGTH(t9)"));
        assertEquals("10-00-00", answer("TRANSLATE(t0, ':', '-')"));
        assertEquals("10-00-00", answer("REGEXP_REPLACE(t9, ':', '-')"));
        assertEquals("****10:00:00", answer("LPAD(t9, 12, '*')"));
        assertEquals("0:00:00", answer("LTRIM(t9, '1')"));
        assertEquals("3", answer("EDITDISTANCE(t0, '10:00')"));
    }

    /** Cutting, splitting and searching all work on the display text. */
    @Test
    public void cutsAndSearchesSeeTheDisplayText() {
        assertEquals("10:00:00.123", answer("SUBSTR(n9, 12)"));
        assertEquals("2020-01-01 10", answer("LEFT(n9, 13)"));
        assertEquals("00.123", answer("RIGHT(n9, 6)"));
        assertEquals("10:00:00.123", answer("SPLIT_PART(n9, ' ', 2)"));
        assertEquals("10:00:00.123", answer("STRTOK(n9, ' ', 2)"));
        assertEquals("2020-01-01#10:00:00.123", answer("REPLACE(n9, ' ', '#')"));
        assertEquals("10:00", answer("REGEXP_SUBSTR(n9, '[0-9]{2}:[0-9]{2}')"));
        assertEquals("11", answer("POSITION(' ' IN n9)"));
        assertEquals("N", answer("IFF(CONTAINS(n9, 'T'), 'Y', 'N')"));
        assertEquals("Y", answer("IFF(STARTSWITH(n9, '2020-01-01 '), 'Y', 'N')"));
        assertEquals("Y", answer("IFF(ENDSWITH(n9, '.123'), 'Y', 'N')"));
        assertEquals("Y", answer("IFF(REGEXP_LIKE(n9, '.* .*'), 'Y', 'N')"));
        assertEquals("2000", answer("SOUNDEX(n9)"));
        assertEquals("23", answer("OCTET_LENGTH(n9)"));
    }

    /** LIKE and ILIKE match the same display text. */
    @Test
    public void likeMatchesTheDisplayText() {
        assertEquals("Y", answer("IFF(n9 LIKE '% %', 'Y', 'N')"));
        assertEquals("N", answer("IFF(n9 LIKE '%T%', 'Y', 'N')"));
        assertEquals("Y", answer("IFF(t9 LIKE '10:00:00', 'Y', 'N')"));
        assertEquals("Y", answer("IFF(n9 ILIKE '%:00.123', 'Y', 'N')"));
        assertEquals("Y", answer("IFF(n9 LIKE ANY ('%T%', '% %'), 'Y', 'N')"));
        assertEquals("N", answer("IFF(n9 NOT LIKE '2020-01-01 %', 'Y', 'N')"));
    }

    /** The paths that already read the display text agree with the functions now. */
    @Test
    public void theFunctionsAgreeWithConcatenationAndCasts() {
        assertEquals(answer("'x' || n9"), "x" + answer("UPPER(n9)"));
        assertEquals(answer("n9::VARCHAR"), answer("UPPER(n9)"));
        assertEquals(answer("TO_VARCHAR(t9)"), answer("UPPER(t9)"));
        assertEquals("2020-01-01 10:00:00.123|10:00:00", answer("CONCAT(n9, '|', t9)"));
    }

    /** A concatenation of a value that converts to text is unbounded, whatever else it joins. */
    @Test
    public void aConcatenationOfAConvertedValueIsUnbounded() {
        assertEquals(134217728, declaredWidth("CONCAT(n9, '|', t9)"));
        assertEquals(134217728, declaredWidth("CONCAT(i, 'a')"));
        assertEquals(134217728, declaredWidth("CONCAT(d, 'a')"));
        assertEquals(134217728, declaredWidth("CONCAT_WS('|', n9, t9)"));
        assertEquals(134217728, declaredWidth("CONCAT_WS(d, 'a', 'b')"));
        assertEquals(134217728, declaredWidth("INSERT(t0, 1, 2, 'xx')"));
        assertEquals(134217728, declaredWidth("INSERT('abc', 1, 1, i)"));
        assertEquals(6, declaredWidth("CONCAT('abc', 'def')"), "two strings still add their widths");
    }
}
