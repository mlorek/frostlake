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
 * TO_TIMESTAMP_LTZ and TO_TIMESTAMP_TZ, their TRY_ twins, the bare spelling under a zoned mapping and the
 * casts beside them each hand back their own flavour: an LTZ is the instant the input names, shown in the
 * session's zone, and a TZ is that instant at an offset it keeps. What the input IS decides both:
 *
 * <pre>
 *   input                   TIMESTAMP_LTZ                        TIMESTAMP_TZ
 *   a number (any scale)    the epoch, in the session's zone     the epoch, at the session's offset
 *   a string of digits      the epoch, in the session's zone     the epoch, at UTC
 *   text with no offset     a wall clock in the session's zone   the same, at the session's offset
 *   text with an offset     re-expressed in the session's zone   kept as written
 * </pre>
 *
 * <p>Every test pins TIMEZONE, because the answers depend on it and the two engines default differently,
 * and unsets what it changed. Every cell was measured on a real account.
 */
public class ZonedTimestampConversionTest extends BaseDatabaseTest {

    private static final String EPOCH_IN_LOS_ANGELES = "2020-01-14 16:00:00.000 -0800";
    private static final String EPOCH_AT_UTC = "2020-01-15 00:00:00.000 Z";

    @Override
    protected void setupTest() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
    }

    @Override
    protected void teardownTest() {
        engine.execute("ALTER SESSION UNSET TIMESTAMP_TYPE_MAPPING");
        engine.execute("ALTER SESSION UNSET TIMEZONE");
    }

    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            if (!rs.next()) {
                return "<no rows>";
            }
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < rs.getColumnCount(); i++) {
                if (i > 0) {
                    sb.append(" | ");
                }
                sb.append(String.valueOf(rs.getValue(i)));
            }
            return sb.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** Text with no offset names a wall clock in the session's zone, at that instant's offset. */
    @Test
    public void theLtzSpellingIsAnInstantInTheSessionZone() {
        assertEquals("2020-01-15 10:00:00.000 -0800", answer("SELECT TO_TIMESTAMP_LTZ('2020-01-15 10:00:00')::VARCHAR"));
        assertEquals("2020-06-15 10:00:00.000 -0700", answer("SELECT TO_TIMESTAMP_LTZ('2020-06-15 10:00:00')::VARCHAR"),
            "June is daylight time in the same zone");
        assertEquals("2020-01-15 00:00:00.000 -0800", answer("SELECT TO_TIMESTAMP_LTZ('2020-01-15')::VARCHAR"));
        assertEquals("2020-01-15 10:00:00.000 -0800",
            answer("SELECT TO_TIMESTAMP_LTZ('2020-01-15 10:00:00', 'AUTO')::VARCHAR"));
        assertEquals("2020-01-15 00:00:00.000 -0800",
            answer("SELECT TO_TIMESTAMP_LTZ('2020-01-15 10:00:00 +02:00')::VARCHAR"),
            "a written offset fixes the instant, which the session's zone re-expresses");
        assertEquals("1579111200 | 1579111200", answer("SELECT DATE_PART(EPOCH_SECOND, TO_TIMESTAMP_LTZ('2020-01-15 10:00:00')),"
            + " DATE_PART(EPOCH_SECOND, '2020-01-15 10:00:00'::TIMESTAMP_LTZ)"), "the instant the cast names too");
        assertEquals("true", answer("SELECT TO_TIMESTAMP_LTZ('2020-01-15 10:00:00') = '2020-01-15 10:00:00'::TIMESTAMP_LTZ"));
    }

    /** A number, a scaled number and a string of digits are all the same instant. */
    @Test
    public void anEpochIsAnInstantWhateverItsSpelling() {
        assertEquals(EPOCH_IN_LOS_ANGELES, answer("SELECT TO_TIMESTAMP_LTZ(1579046400)::VARCHAR"));
        assertEquals(EPOCH_IN_LOS_ANGELES, answer("SELECT TO_TIMESTAMP_LTZ(1579046400000, 3)::VARCHAR"));
        assertEquals("2020-01-14 16:00:00.500 -0800", answer("SELECT TO_TIMESTAMP_LTZ(1579046400.5)::VARCHAR"));
        assertEquals(EPOCH_IN_LOS_ANGELES, answer("SELECT TO_TIMESTAMP_LTZ('1579046400')::VARCHAR"),
            "a string of digits is an epoch too");
        assertEquals(EPOCH_IN_LOS_ANGELES, answer("SELECT TO_TIMESTAMP_LTZ('1579046400000')::VARCHAR"),
            "whose unit its magnitude picks");
        assertEquals(EPOCH_IN_LOS_ANGELES, answer("SELECT TO_TIMESTAMP_LTZ('+1579046400')::VARCHAR"));
        assertEquals("1579046400", answer("SELECT DATE_PART(EPOCH_SECOND, TO_TIMESTAMP_LTZ(1579046400))"));
        assertEquals("Timestamp '1579046400.5' is not recognized",
            answer("SELECT TO_TIMESTAMP_LTZ('1579046400.5')::VARCHAR"), "though a fraction written as text is no epoch");
    }

    /** A NUMBER takes the session's offset, the same epoch written as text stays at UTC, and text keeps its own. */
    @Test
    public void theTzSpellingKeepsTheOffsetItIsGiven() {
        assertEquals(EPOCH_IN_LOS_ANGELES, answer("SELECT TO_TIMESTAMP_TZ(1579046400)::VARCHAR"));
        assertEquals(EPOCH_IN_LOS_ANGELES, answer("SELECT TO_TIMESTAMP_TZ(1579046400000, 3)::VARCHAR"));
        assertEquals("2020-01-14 16:00:00.500 -0800", answer("SELECT TO_TIMESTAMP_TZ(1579046400.5)::VARCHAR"));
        assertEquals(EPOCH_AT_UTC, answer("SELECT TO_TIMESTAMP_TZ('1579046400')::VARCHAR"),
            "one instant, two offsets: the text stays at UTC");
        assertEquals(EPOCH_AT_UTC, answer("SELECT TO_TIMESTAMP_TZ('1579046400000')::VARCHAR"));
        assertEquals(EPOCH_AT_UTC, answer("SELECT TO_TIMESTAMP_TZ('  1579046400  ')::VARCHAR"));
        assertEquals(EPOCH_AT_UTC, answer("SELECT TO_TIMESTAMP_TZ('1579046400', 'AUTO')::VARCHAR"));
        assertEquals("1969-12-31 23:59:59.000 Z", answer("SELECT TO_TIMESTAMP_TZ('-1')::VARCHAR"));
        assertEquals("1970-08-22 19:08:35.000 Z", answer("SELECT TO_TIMESTAMP_TZ('20200115')::VARCHAR"),
            "eight digits are seconds, not a date");
        assertEquals("2020-01-15 10:00:00.000 -0800", answer("SELECT TO_TIMESTAMP_TZ('2020-01-15 10:00:00')::VARCHAR"));
        assertEquals("2020-01-15 10:00:00.000 +0200",
            answer("SELECT TO_TIMESTAMP_TZ('2020-01-15 10:00:00 +02:00', 'AUTO')::VARCHAR"),
            "text keeps the offset it was written with");
    }

    /** A DATE or a timestamp of another flavour becomes the call's flavour. */
    @Test
    public void aTemporalSourceTakesTheCallsFlavour() {
        assertEquals("2020-01-15 00:00:00.000 -0800", answer("SELECT TO_TIMESTAMP_LTZ('2020-01-15'::DATE)::VARCHAR"));
        assertEquals("2020-01-15 00:00:00.000 -0800", answer("SELECT TO_TIMESTAMP_TZ('2020-01-15'::DATE)::VARCHAR"));
        assertEquals("2020-01-15 10:00:00.000 -0800",
            answer("SELECT TO_TIMESTAMP_LTZ('2020-01-15 10:00:00'::TIMESTAMP_NTZ)::VARCHAR"), "an NTZ keeps its digits");
        assertEquals("2020-01-15 00:00:00.000 -0800",
            answer("SELECT TO_TIMESTAMP_LTZ('2020-01-15 10:00:00 +02:00'::TIMESTAMP_TZ)::VARCHAR"),
            "a TZ becomes an LTZ by its instant");
        assertEquals("2020-01-15 10:00:00.000 +0200",
            answer("SELECT TO_TIMESTAMP_TZ('2020-01-15 10:00:00 +02:00'::TIMESTAMP_TZ)::VARCHAR"));
        assertEquals("2020-01-15 10:00:00.000 -0800",
            answer("SELECT TO_TIMESTAMP_TZ('2020-01-15 10:00:00'::TIMESTAMP_LTZ)::VARCHAR"));
    }

    /** A VARIANT is read by what it holds: a number is a number, however large, and a string is text. */
    @Test
    public void aVariantIsReadByWhatItHolds() {
        assertEquals("2020-01-15 10:00:00.000 -0800",
            answer("SELECT TO_TIMESTAMP_LTZ(PARSE_JSON('\"2020-01-15 10:00:00\"'))::VARCHAR"));
        assertEquals(EPOCH_IN_LOS_ANGELES, answer("SELECT TO_TIMESTAMP_TZ(PARSE_JSON('1579046400'))::VARCHAR"),
            "a number takes the session's offset");
        assertEquals(EPOCH_AT_UTC, answer("SELECT TO_TIMESTAMP_TZ(PARSE_JSON('\"1579046400\"'))::VARCHAR"),
            "and a string of digits stays at UTC");
        assertEquals("2020-01-15 10:00:00.000 +0200",
            answer("SELECT TO_TIMESTAMP_TZ(PARSE_JSON('\"2020-01-15 10:00:00 +02:00\"'))::VARCHAR"));
        assertEquals("2020-01-14 16:00:00.500 -0800", answer("SELECT TO_TIMESTAMP_LTZ(PARSE_JSON('1579046400.5'))::VARCHAR"));
        assertEquals("5138 | 1973", answer("SELECT YEAR(TO_TIMESTAMP_LTZ(PARSE_JSON('100000000000'))),"
            + " YEAR(TO_TIMESTAMP_LTZ(PARSE_JSON('\"100000000000\"')))"), "only text picks its unit by magnitude");
        assertEquals("null", answer("SELECT TO_TIMESTAMP_LTZ(PARSE_JSON('null'))"), "the JSON null is NULL");
        assertEquals("Failed to cast variant value true to TIMESTAMP_LTZ",
            answer("SELECT TO_TIMESTAMP_LTZ(PARSE_JSON('true'))::VARCHAR"));
        assertEquals("Failed to cast variant value true to TIMESTAMP_TZ",
            answer("SELECT TO_TIMESTAMP_TZ(PARSE_JSON('true'))::VARCHAR"));
        assertEquals("Failed to cast variant value {\"a\":1} to TIMESTAMP_TZ",
            answer("SELECT TO_TIMESTAMP_TZ(PARSE_JSON('{\"a\":1}'))::VARCHAR"));
        assertEquals("Failed to cast variant value [1] to TIMESTAMP_LTZ",
            answer("SELECT TO_TIMESTAMP_LTZ(PARSE_JSON('[1]'))::VARCHAR"));
    }

    @Test
    public void theTryTwinsConvertAlike() {
        assertEquals("2020-01-15 10:00:00.000 -0800", answer("SELECT TRY_TO_TIMESTAMP_LTZ('2020-01-15 10:00:00')::VARCHAR"));
        assertEquals(EPOCH_IN_LOS_ANGELES, answer("SELECT TRY_TO_TIMESTAMP_LTZ('1579046400')::VARCHAR"));
        assertEquals("2020-01-15 00:00:00.000 -0800",
            answer("SELECT TRY_TO_TIMESTAMP_LTZ('2020-01-15 10:00:00 +02:00')::VARCHAR"));
        assertEquals("2020-01-15 10:00:00.000 -0800",
            answer("SELECT TRY_TO_TIMESTAMP_LTZ('2020-01-15 10:00:00', 'YYYY-MM-DD HH24:MI:SS')::VARCHAR"));
        assertEquals("2020-01-15 10:00:00.000 -0800", answer("SELECT TRY_TO_TIMESTAMP_TZ('2020-01-15 10:00:00')::VARCHAR"));
        assertEquals("2020-01-15 10:00:00.000 +0200",
            answer("SELECT TRY_TO_TIMESTAMP_TZ('2020-01-15 10:00:00 +02:00')::VARCHAR"));
        assertEquals(EPOCH_AT_UTC, answer("SELECT TRY_TO_TIMESTAMP_TZ('1579046400')::VARCHAR"));
        assertEquals(EPOCH_AT_UTC, answer("SELECT TRY_TO_TIMESTAMP_TZ('1579046400000')::VARCHAR"));
        assertEquals("null", answer("SELECT TRY_TO_TIMESTAMP_LTZ('garbage')"));
        assertEquals("TIMESTAMP_LTZ(9)[SB16]", answer("SELECT SYSTEM$TYPEOF(TRY_TO_TIMESTAMP_LTZ('2020-01-15 10:00:00'))"));
    }

    /** Every cast reads a string of digits as an epoch, a DATE included. */
    @Test
    public void aDigitStringIsAnEpochToEveryCast() {
        assertEquals("2020-01-15 00:00:00.000", answer("SELECT '1579046400'::TIMESTAMP_NTZ::VARCHAR"));
        assertEquals(EPOCH_IN_LOS_ANGELES, answer("SELECT '1579046400'::TIMESTAMP_LTZ::VARCHAR"));
        assertEquals(EPOCH_IN_LOS_ANGELES, answer("SELECT '1579046400000'::TIMESTAMP_LTZ::VARCHAR"));
        assertEquals(EPOCH_AT_UTC, answer("SELECT '1579046400'::TIMESTAMP_TZ::VARCHAR"));
        assertEquals(EPOCH_IN_LOS_ANGELES, answer("SELECT TRY_CAST('1579046400' AS TIMESTAMP_LTZ)::VARCHAR"));
        assertEquals("1970-08-22 19:08:35.000", answer("SELECT '20200115'::TIMESTAMP_NTZ::VARCHAR"),
            "eight digits are seconds, not a compact date");
        assertEquals("1970-08-22 12:08:35.000 -0700", answer("SELECT '20200115'::TIMESTAMP_LTZ::VARCHAR"));
        assertEquals("1970-08-22", answer("SELECT '20200115'::DATE::VARCHAR"));
        assertEquals("true", answer("SELECT '1579046400'::TIMESTAMP_NTZ = TO_TIMESTAMP_NTZ(1579046400)"));
        assertEquals("2020", answer("SELECT DATE_PART(YEAR, '1579046400'::TIMESTAMP_NTZ)"));
        assertEquals("Timestamp '1579046400.5' is not recognized",
            answer("SELECT TO_TIMESTAMP_NTZ('1579046400.5')::VARCHAR"));
    }

    @Test
    public void anInsertReadsADigitStringAsAnEpoch() {
        engine.execute("CREATE OR REPLACE TABLE dz (n TIMESTAMP_NTZ, l TIMESTAMP_LTZ, z TIMESTAMP_TZ)");
        engine.execute("INSERT INTO dz VALUES ('1579046400', '1579046400', '1579046400')");
        assertEquals("2020-01-15 00:00:00.000 | " + EPOCH_IN_LOS_ANGELES + " | " + EPOCH_AT_UTC,
            answer("SELECT n::VARCHAR, l::VARCHAR, z::VARCHAR FROM dz"));
    }

    /** A converted value is an instant: stored, it re-renders when the session's zone moves. */
    @Test
    public void aConvertedValueReRendersInANewZone() {
        engine.execute("CREATE OR REPLACE TABLE lt (v TIMESTAMP_LTZ, w TIMESTAMP_LTZ)");
        engine.execute("INSERT INTO lt SELECT TO_TIMESTAMP_LTZ('2020-01-15 10:00:00'),"
            + " TRY_TO_TIMESTAMP_LTZ('2020-01-15 10:00:00')");
        assertEquals("2020-01-15 10:00:00.000 -0800 | 2020-01-15 10:00:00.000 -0800",
            answer("SELECT v::VARCHAR, w::VARCHAR FROM lt"));
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        assertEquals("2020-01-15 18:00:00.000 Z | 2020-01-15 18:00:00.000 Z",
            answer("SELECT v::VARCHAR, w::VARCHAR FROM lt"), "the digits move with the zone");
        assertEquals("2020-01-15 10:00:00.000 Z", answer("SELECT TO_TIMESTAMP_LTZ('2020-01-15 10:00:00')::VARCHAR"));
        assertEquals(EPOCH_AT_UTC, answer("SELECT TO_TIMESTAMP_LTZ(1579046400)::VARCHAR"));
        assertEquals(EPOCH_AT_UTC, answer("SELECT TO_TIMESTAMP_TZ(1579046400)::VARCHAR"));
        assertEquals(EPOCH_AT_UTC, answer("SELECT TRY_TO_TIMESTAMP_LTZ('1579046400')::VARCHAR"));
        assertEquals("1579082400 | 1579082400", answer("SELECT DATE_PART(EPOCH_SECOND, TO_TIMESTAMP_LTZ('2020-01-15 10:00:00')),"
            + " DATE_PART(EPOCH_SECOND, '2020-01-15 10:00:00'::TIMESTAMP_LTZ)"));
        engine.execute("ALTER SESSION SET TIMEZONE = 'Asia/Tokyo'");
        assertEquals("2020-01-15 10:00:00.000 +0900", answer("SELECT TO_TIMESTAMP_LTZ('2020-01-15 10:00:00')::VARCHAR"));
        assertEquals("1579050000", answer("SELECT DATE_PART(EPOCH_SECOND, TO_TIMESTAMP_LTZ('2020-01-15 10:00:00'))"));
    }

    /** The bare spelling, its TRY_ twin and the bare cast take the flavour the mapping names, value and type. */
    @Test
    public void theBareSpellingFollowsTheMapping() {
        engine.execute("ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = 'TIMESTAMP_LTZ'");
        assertEquals(EPOCH_IN_LOS_ANGELES, answer("SELECT TO_TIMESTAMP('1579046400')::VARCHAR"));
        assertEquals(EPOCH_IN_LOS_ANGELES, answer("SELECT TO_TIMESTAMP(1579046400)::VARCHAR"));
        assertEquals(EPOCH_IN_LOS_ANGELES, answer("SELECT TO_TIMESTAMP(1579046400000, 3)::VARCHAR"));
        assertEquals(EPOCH_IN_LOS_ANGELES, answer("SELECT TO_TIMESTAMP(PARSE_JSON('1579046400'))::VARCHAR"));
        assertEquals("2020-01-15 00:00:00.000 -0800", answer("SELECT TO_TIMESTAMP('2020-01-15 10:00:00 +02:00')::VARCHAR"));
        assertEquals("2020-01-15 10:00:00.000 -0800", answer("SELECT TRY_TO_TIMESTAMP('2020-01-15 10:00:00')::VARCHAR"));
        assertEquals(EPOCH_IN_LOS_ANGELES, answer("SELECT TRY_TO_TIMESTAMP('1579046400')::VARCHAR"));
        assertEquals("2020-01-15 00:00:00.000 -0800",
            answer("SELECT TRY_TO_TIMESTAMP('2020-01-15 10:00:00 +02:00')::VARCHAR"));
        assertEquals("TIMESTAMP_LTZ(9)[SB16]", answer("SELECT SYSTEM$TYPEOF(TRY_TO_TIMESTAMP('2020-01-15 10:00:00'))"));
        assertEquals(EPOCH_IN_LOS_ANGELES, answer("SELECT '1579046400'::TIMESTAMP::VARCHAR"));

        engine.execute("ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = 'TIMESTAMP_TZ'");
        assertEquals(EPOCH_AT_UTC, answer("SELECT TO_TIMESTAMP('1579046400')::VARCHAR"));
        assertEquals(EPOCH_IN_LOS_ANGELES, answer("SELECT TO_TIMESTAMP(1579046400)::VARCHAR"));
        assertEquals(EPOCH_IN_LOS_ANGELES, answer("SELECT TO_TIMESTAMP(1579046400000, 3)::VARCHAR"));
        assertEquals("2020-01-15 10:00:00.000 +0200", answer("SELECT TO_TIMESTAMP('2020-01-15 10:00:00 +02:00')::VARCHAR"));
        assertEquals("2020-01-15 10:00:00.000 -0800", answer("SELECT TO_TIMESTAMP('2020-01-15 10:00:00')::VARCHAR"));
        assertEquals("2020-01-15 10:00:00.000 +0200",
            answer("SELECT TRY_TO_TIMESTAMP('2020-01-15 10:00:00 +02:00')::VARCHAR"));
        assertEquals(EPOCH_AT_UTC, answer("SELECT TRY_TO_TIMESTAMP('1579046400')::VARCHAR"));
        assertEquals("TIMESTAMP_TZ(9)[SB16] | TIMESTAMP_TZ(9)[SB16]",
            answer("SELECT SYSTEM$TYPEOF(TRY_TO_TIMESTAMP('2020-01-15 10:00:00')),"
                + " SYSTEM$TYPEOF(TO_TIMESTAMP('2020-01-15 10:00:00'))"));
        assertEquals("2020-01-15 10:00:00.000 +0200", answer("SELECT '2020-01-15 10:00:00 +02:00'::TIMESTAMP::VARCHAR"));
        assertEquals(EPOCH_AT_UTC, answer("SELECT '1579046400'::TIMESTAMP::VARCHAR"));
        assertEquals(EPOCH_IN_LOS_ANGELES, answer("SELECT 1579046400::TIMESTAMP::VARCHAR"));

        engine.execute("ALTER SESSION UNSET TIMESTAMP_TYPE_MAPPING");
        assertEquals("TIMESTAMP_NTZ(9)[SB16]", answer("SELECT SYSTEM$TYPEOF(TRY_TO_TIMESTAMP('2020-01-15 10:00:00'))"));
    }
}
