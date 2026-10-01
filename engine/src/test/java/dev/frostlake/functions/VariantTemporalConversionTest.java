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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A DATE, a TIME or a timestamp held in a VARIANT keeps its family: it converts to its own family only, a
 * failed conversion out of any VARIANT names the variant by its JSON text, its JSON text is the variant's
 * own, and a DATE enters a VARIANT only within 2^24 days of 1970-01-01. Live-verified.
 */
public class VariantTemporalConversionTest extends BaseDatabaseTest {

    /** The one cell of a single-row query, as text. */
    private String scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount(), sql);
        final Object value = rs.getRows().get(0).getValue(0);
        return value == null ? "NULL" : value.toString();
    }

    /** The message a refused statement carries. */
    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        return refused.getMessage();
    }

    private static String failed(final String json, final String target) {
        return "Failed to cast variant value " + json + " to " + target;
    }

    /** TO_JSON spells a held temporal as the VARIANT holds it; a TIME's fraction is kept, only not spelled. */
    @Test
    public void theJsonTextIsTheVariants() {
        assertEquals("\"10:00:00\"", scalar("SELECT TO_JSON(TO_VARIANT('10:00:00'::TIME))"));
        assertEquals("\"10:30:00\"", scalar("SELECT TO_JSON(TO_VARIANT('10:30:00'::TIME))"));
        assertEquals("\"10:00:00\"", scalar("SELECT TO_JSON(TO_VARIANT('10:00:00.5'::TIME))"));
        assertEquals("\"10:00:00\"", scalar("SELECT TO_JSON(TO_VARIANT('10:00:00.123456789'::TIME))"));
        assertEquals("\"2024-01-15 10:00:00.123\"", scalar("SELECT TO_JSON(TO_VARIANT('2024-01-15 10:00:00.123'::TIMESTAMP_NTZ))"));
        assertEquals("\"2024-01-15 10:00:00.000 +0200\"",
            scalar("SELECT TO_JSON(TO_VARIANT('2024-01-15 10:00:00 +0200'::TIMESTAMP_TZ))"));
        assertEquals("\"20201-01-15\"", scalar("SELECT TO_JSON(TO_VARIANT('20201-01-15'::DATE))"));
        assertEquals("\"-1-01-01\"", scalar("SELECT TO_JSON(TO_VARIANT(DATEADD(year, -2025, '2024-01-01'::DATE)))"));
        assertEquals("10:00:00.500", scalar("SELECT TO_VARCHAR(TO_VARIANT('10:00:00.5'::TIME)::TIME, 'HH24:MI:SS.FF3')"));
        assertEquals("10:00:00.123456789",
            scalar("SELECT TO_VARCHAR(TO_VARIANT('10:00:00.123456789'::TIME)::TIME, 'HH24:MI:SS.FF9')"));
    }

    /** A held temporal converts within its family. */
    @Test
    public void aHeldTemporalConvertsWithinItsFamily() {
        assertEquals("2024-01-15", scalar("SELECT TO_VARCHAR(TO_VARIANT('2024-01-15'::DATE)::DATE)"));
        assertEquals("2024-01-15", scalar("SELECT TO_VARCHAR(TO_DATE(TO_VARIANT('2024-01-15'::DATE)))"));
        assertEquals("10:00:00", scalar("SELECT TO_VARCHAR(TO_VARIANT('10:00:00'::TIME)::TIME)"));
        assertEquals("10:00:00", scalar("SELECT TO_VARCHAR(TIME(TO_VARIANT('10:00:00'::TIME)))"));
        assertEquals("2024-01-15 10:00:00.000",
            scalar("SELECT TO_VARCHAR(TO_VARIANT('2024-01-15 10:00:00'::TIMESTAMP_NTZ)::TIMESTAMP_NTZ)"));
        assertEquals("2024-01-15 10:00:00.000",
            scalar("SELECT TO_VARCHAR(TO_TIMESTAMP_NTZ(TO_VARIANT('2024-01-15 10:00:00'::TIMESTAMP_NTZ)))"));
        assertEquals("2024-01-15 10:00:00.000",
            scalar("SELECT TO_VARCHAR(TO_VARIANT('2024-01-15 10:00:00 +0200'::TIMESTAMP_TZ)::TIMESTAMP_NTZ)"));
        assertEquals("2024-01-15 10:00:00.000 +0200",
            scalar("SELECT TO_VARCHAR(TO_VARIANT('2024-01-15 10:00:00 +0200'::TIMESTAMP_TZ)::TIMESTAMP_TZ)"));
        assertEquals("2024-01-15", scalar("SELECT TO_VARCHAR(TO_VARIANT('2024-01-15'::DATE))"));
        assertEquals("2024-01-15", scalar("SELECT TO_VARCHAR(DATE(TO_VARIANT('2024-01-15 10:00:00'::TIMESTAMP_NTZ)))"));
    }

    /** Out of its family a held temporal, and a BINARY anywhere, fails the variant's cast. */
    @Test
    public void outOfItsFamilyTheVariantsCastFails() {
        assertEquals(failed("\"2024-01-15\"", "TIME"), refusal("SELECT TIME(TO_VARIANT(TO_DATE('2024-01-15')))"));
        assertEquals(failed("\"2024-01-15\"", "TIME"), refusal("SELECT TO_TIME(TO_VARIANT(TO_DATE('2024-01-15')))"));
        assertEquals(failed("\"2024-01-15\"", "TIME"), refusal("SELECT CAST(TO_VARIANT(TO_DATE('2024-01-15')) AS TIME)"));
        assertEquals(failed("\"2024-01-15 10:00:00.000\"", "DATE"),
            refusal("SELECT TO_DATE(TO_VARIANT('2024-01-15 10:00:00'::TIMESTAMP_NTZ))"));
        assertEquals(failed("\"2024-01-15\"", "TIMESTAMP_NTZ"), refusal("SELECT TO_TIMESTAMP(TO_VARIANT('2024-01-15'::DATE))"));
        assertEquals(failed("\"2024-01-15 10:00:00.000\"", "TIME"),
            refusal("SELECT TO_VARIANT('2024-01-15 10:00:00'::TIMESTAMP_NTZ)::TIME"));
        assertEquals(failed("\"10:00:00\"", "DATE"), refusal("SELECT TO_VARIANT('10:00:00'::TIME)::DATE"));
        assertEquals(failed("\"2024-01-15\"", "TIMESTAMP_NTZ"), refusal("SELECT TO_VARIANT('2024-01-15'::DATE)::TIMESTAMP"));
        assertEquals(failed("\"2024-01-15\"", "TIMESTAMP_LTZ"), refusal("SELECT TO_VARIANT('2024-01-15'::DATE)::TIMESTAMP_LTZ"));
        assertEquals(failed("\"2024-01-15\"", "TIMESTAMP_LTZ"),
            refusal("SELECT TO_TIMESTAMP_LTZ(TO_VARIANT('2024-01-15'::DATE))"));
        assertEquals(failed("\"10:00:00\"", "TIMESTAMP_TZ"), refusal("SELECT TO_TIMESTAMP_TZ(TO_VARIANT('10:00:00'::TIME))"));
        assertEquals(failed("\"2024-01-15\"", "FIXED"), refusal("SELECT TO_VARIANT('2024-01-15'::DATE)::NUMBER"));
        assertEquals(failed("\"2024-01-15\"", "REAL"), refusal("SELECT TO_VARIANT('2024-01-15'::DATE)::FLOAT"));
        assertEquals(failed("\"2024-01-15 10:00:00.000\"", "BOOLEAN"),
            refusal("SELECT TO_VARIANT('2024-01-15 10:00:00'::TIMESTAMP_NTZ)::BOOLEAN"));
        assertEquals(failed("\"2024-01-15\"", "TIME"), refusal("SELECT OBJECT_CONSTRUCT('d', '2024-01-15'::DATE):d::TIME"));
        assertEquals(failed("\"AB\"", "DATE"), refusal("SELECT TO_VARIANT(TO_BINARY('AB', 'HEX'))::DATE"));
    }

    /** A VARIANT string a temporal conversion cannot read fails the same way. */
    @Test
    public void anUnreadableVariantStringFailsTheSameWay() {
        assertEquals(failed("\"abc\"", "DATE"), refusal("SELECT PARSE_JSON('\"abc\"')::DATE"));
        assertEquals(failed("\"abc\"", "TIMESTAMP_NTZ"), refusal("SELECT PARSE_JSON('\"abc\"')::TIMESTAMP_NTZ"));
        assertEquals(failed("\"abc\"", "TIME"), refusal("SELECT PARSE_JSON('\"abc\"')::TIME"));
        assertEquals(failed("\"abc\"", "DATE"), refusal("SELECT TO_DATE(PARSE_JSON('\"abc\"'))"));
        assertEquals(failed("\"abc\"", "TIMESTAMP_NTZ"), refusal("SELECT TO_TIMESTAMP(PARSE_JSON('\"abc\"'))"));
        assertEquals(failed("\"abc\"", "TIME"), refusal("SELECT TO_TIME(PARSE_JSON('\"abc\"'))"));
        assertEquals(failed("\"10:00:00\"", "DATE"), refusal("SELECT PARSE_JSON('\"10:00:00\"')::DATE"));
        assertEquals(failed("\"2024-01-15 10:00:00\"", "TIME"), refusal("SELECT PARSE_JSON('\"2024-01-15 10:00:00\"')::TIME"));
        assertEquals("2024-01-15", scalar("SELECT TO_VARCHAR(PARSE_JSON('\"2024-01-15 10:00:00\"')::DATE)"));
        assertEquals("2024-01-15", scalar("SELECT TO_VARCHAR(TO_DATE(PARSE_JSON('\"2024-01-15 10:00:00\"')))"));
    }

    /** A DATE enters a VARIANT only within 2^24 days of 1970-01-01; a timestamp of the same day enters freely. */
    @Test
    public void aDateEntersAVariantWithinItsRange() {
        assertEquals("\"47904-06-16\"", scalar("SELECT TO_JSON(TO_VARIANT(DATEADD(day, 16777215, '1970-01-01'::DATE)))"));
        assertEquals("\"-43965-07-18\"", scalar("SELECT TO_JSON(TO_VARIANT(DATEADD(day, -16777216, '1970-01-01'::DATE)))"));
        assertEquals("Date 16777216 is out of range",
            refusal("SELECT TO_VARIANT(DATEADD(day, 16777216, '1970-01-01'::DATE))"));
        assertEquals("Date -16777217 is out of range",
            refusal("SELECT TO_VARIANT(DATEADD(day, -16777217, '1970-01-01'::DATE))"));
        assertEquals("Date 35804721 is out of range", refusal("SELECT TO_VARIANT('99999-12-31'::DATE)"));
        assertEquals("Date 35804721 is out of range", refusal("SELECT '99999-12-31'::DATE::VARIANT"));
        assertEquals("Date 16777217 is out of range",
            refusal("SELECT ARRAY_CONSTRUCT(DATEADD(day, 16777217, '1970-01-01'::DATE))"));
        assertEquals("Date 16777217 is out of range",
            refusal("SELECT OBJECT_CONSTRUCT('d', DATEADD(day, 16777217, '1970-01-01'::DATE))"));
        assertEquals("\"47904-06-18 00:00:00.000\"",
            scalar("SELECT TO_JSON(TO_VARIANT(DATEADD(day, 16777217, '1970-01-01'::DATE)::TIMESTAMP_NTZ))"));
        assertEquals("47904-06-18", scalar("SELECT DATEADD(day, 16777217, '1970-01-01'::DATE)::VARCHAR"));
    }
}
