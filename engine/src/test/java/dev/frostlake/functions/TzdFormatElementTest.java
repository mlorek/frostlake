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
 * The TZD format element renders a zone abbreviation, and the abbreviation is the STANDARD-TIME one
 * whatever the instant (live-verified across eleven session zones in January and July): an LTZ under
 * America/Los_Angeles is PST in July too — never PDT, never a daylight name of any zone.
 *
 * <p>★ THE ANSWER DEPENDS ON WHAT THE VALUE CARRIES. An LTZ names the SESSION's zone; a TIMESTAMP_TZ
 * has only its written offset and spells it GMT±HH:MM — the zero offset included, GMT+00:00 — and a
 * value with no zone at all (NTZ, DATE) says the bare GMT. A session zone with no English
 * abbreviation (Etc/GMT+5) uses the spelled-offset form as well; UTC says UTC.
 *
 * <p>★ THE ELEMENT DOES NOT DEPEND ON SESSION HISTORY OR VALUE PROVENANCE (measured both ways): a
 * stored column and a literal cast answer alike, before and after ALTER SESSION SET TIMEZONE — to
 * the same zone, to another and back, or UNSET.
 */
public class TzdFormatElementTest extends BaseDatabaseTest {

    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    private void inZone(final String zone) {
        engine.execute("ALTER SESSION SET TIMEZONE = '" + zone + "'");
    }

    @Test
    public void theSessionZoneAnswersItsStandardAbbreviationInBothSeasons() {
        final String[][] cells = {
            {"America/Los_Angeles", "PST"}, {"America/New_York", "EST"},
            {"America/Phoenix", "MST"}, {"Europe/London", "GMT"}, {"Europe/Berlin", "CET"},
            {"Asia/Tokyo", "JST"}, {"Asia/Kolkata", "IST"}, {"Australia/Sydney", "AEST"},
            {"UTC", "UTC"},
        };
        for (final String[] cell : cells) {
            inZone(cell[0]);
            assertEquals(cell[1],
                answer("SELECT TO_VARCHAR('2020-01-15 12:00:00'::TIMESTAMP_LTZ, 'TZD')"), cell[0]);
            assertEquals(cell[1],
                answer("SELECT TO_VARCHAR('2020-07-15 12:00:00'::TIMESTAMP_LTZ, 'TZD')"),
                cell[0] + " in July");
        }
    }

    @Test
    public void aZoneWithNoAbbreviationSpellsItsOffset() {
        inZone("Etc/GMT+5");
        assertEquals("GMT-05:00",
            answer("SELECT TO_VARCHAR('2020-01-15 12:00:00'::TIMESTAMP_LTZ, 'TZD')"));
    }

    @Test
    public void aZoneAliasResolvesBeforeNaming() {
        inZone("US/Pacific");
        assertEquals("PST",
            answer("SELECT TO_VARCHAR('2020-01-15 12:00:00'::TIMESTAMP_LTZ, 'TZD')"));
    }

    @Test
    public void aTimestampTzSpellsItsOwnWrittenOffset() {
        inZone("America/Los_Angeles");
        assertEquals("GMT-08:00",
            answer("SELECT TO_VARCHAR('2020-01-15 12:00:00 -0800'::TIMESTAMP_TZ, 'TZD')"));
        assertEquals("GMT+05:30",
            answer("SELECT TO_VARCHAR('2020-01-15 12:00:00 +0530'::TIMESTAMP_TZ, 'TZD')"));
        assertEquals("GMT+00:00",
            answer("SELECT TO_VARCHAR('2020-01-15 12:00:00 +0000'::TIMESTAMP_TZ, 'TZD')"));
    }

    @Test
    public void aValueWithNoZoneSaysGmt() {
        inZone("America/Los_Angeles");
        assertEquals("GMT",
            answer("SELECT TO_VARCHAR('2020-01-15 12:00:00'::TIMESTAMP_NTZ, 'TZD')"));
        assertEquals("GMT", answer("SELECT TO_VARCHAR('2020-01-15'::DATE, 'TZD')"));
    }

    @Test
    public void theElementIsCaseInsensitiveAndEmbeds() {
        inZone("America/Los_Angeles");
        assertEquals("PST",
            answer("SELECT TO_VARCHAR('2020-01-15 12:00:00'::TIMESTAMP_LTZ, 'tzd')"));
        assertEquals("2020 PST 15",
            answer("SELECT TO_VARCHAR('2020-01-15 12:00:00'::TIMESTAMP_LTZ, 'YYYY TZD DD')"));
        assertEquals("-08:00 PST",
            answer("SELECT TO_VARCHAR('2020-01-15 12:00:00'::TIMESTAMP_LTZ, 'TZH:TZM TZD')"));
    }

    @Test
    public void storedAndLiteralAnswerAlikeAcrossSessionZoneChanges() {
        inZone("America/Los_Angeles");
        engine.execute("CREATE OR REPLACE TABLE tzd_probe (c TIMESTAMP_LTZ)");
        engine.execute("INSERT INTO tzd_probe SELECT '2020-01-15 12:00:00'::TIMESTAMP_LTZ");
        assertEquals("PST", answer("SELECT TO_VARCHAR(c, 'TZD') FROM tzd_probe"));
        inZone("America/Los_Angeles");
        assertEquals("PST", answer("SELECT TO_VARCHAR(c, 'TZD') FROM tzd_probe"));
        inZone("America/New_York");
        assertEquals("EST", answer("SELECT TO_VARCHAR(c, 'TZD') FROM tzd_probe"));
        inZone("America/Los_Angeles");
        assertEquals("PST", answer("SELECT TO_VARCHAR(c, 'TZD') FROM tzd_probe"));
        assertEquals("PST",
            answer("SELECT TO_VARCHAR('2020-01-15 12:00:01'::TIMESTAMP_LTZ, 'TZD')"));
    }
}
