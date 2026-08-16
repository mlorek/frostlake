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
 * A TIMESTAMP_LTZ is an INSTANT, shown in the SESSION's zone with that zone's offset.
 *
 * <p>Frostlake used to hold one as a naive wall clock, so it rendered exactly like a TIMESTAMP_NTZ —
 * a column that said LTZ and showed no offset. The fix is a representation change, not a formatting
 * one, and the proof that it had to be is here in two places: the same stored value RE-RENDERS when
 * the session zone changes (digits and date included, not just the suffix), and the comparison cells
 * below only come out right if both sides are read as instants.
 *
 * <p>Every test pins TIMEZONE first, because the answer depends on it and the two engines default
 * differently. America/Los_Angeles is used precisely because it has DST: a January value and a June
 * value in the same column carry DIFFERENT offsets, which is what proves the offset is the zone's at
 * that instant rather than a fixed suffix.
 *
 * <p>TIMESTAMP_TZ is deliberately absent — it keeps the offset it was WRITTEN with rather than the
 * session's, which is a different rule and a different carrier.
 */
public class TimestampLtzOffsetTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        engine.execute("CREATE OR REPLACE TABLE lz (tn TIMESTAMP_NTZ, tl TIMESTAMP_LTZ,"
            + " jun TIMESTAMP_LTZ)");
        engine.execute("INSERT INTO lz SELECT '2020-01-01 10:00:00'::TIMESTAMP_NTZ,"
            + " '2020-01-01 10:00:00'::TIMESTAMP_LTZ, '2020-06-01 10:00:00'::TIMESTAMP_LTZ");
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

    /** The offset is the zone's AT THAT INSTANT, so DST moves it. */
    @Test
    public void theOffsetFollowsTheInstantThroughDst() {
        assertEquals("2020-01-01 10:00:00.000 -0800", answer("SELECT TO_VARCHAR(tl) FROM lz"));
        assertEquals("2020-06-01 10:00:00.000 -0700", answer("SELECT TO_VARCHAR(jun) FROM lz"),
            "June is daylight time in the same zone — a fixed suffix could not do this");
        assertEquals("2020-01-01 10:00:00.000",
            answer("SELECT TO_VARCHAR(tn) FROM lz"), "and an NTZ beside it carries none");
    }

    /** A literal cast to LTZ names a wall clock in the session's zone. */
    @Test
    public void aLiteralNamesAWallClockInTheSessionZone() {
        assertEquals("2020-01-01 10:00:00.000 -0800",
            answer("SELECT TO_VARCHAR('2020-01-01 10:00:00'::TIMESTAMP_LTZ)"));
        assertEquals("2020-06-01 10:00:00.000 -0700",
            answer("SELECT TO_VARCHAR('2020-06-01 10:00:00'::TIMESTAMP_LTZ)"));
    }

    /** A literal that CARRIES an offset already fixes the instant, and is merely re-expressed. */
    @Test
    public void aWrittenOffsetFixesTheInstant() {
        assertEquals("2019-12-31 23:00:00.000 -0800",
            answer("SELECT TO_VARCHAR('2020-01-01 10:00:00 +0300'::TIMESTAMP_LTZ)"),
            "ten o'clock at +0300 is the previous day in Los Angeles");
    }

    /** ★ The same stored value re-renders when the session zone changes — the proof it is an instant. */
    @Test
    public void theStoredValueReRendersInANewZone() {
        assertEquals("2020-01-01 10:00:00.000 -0800", answer("SELECT TO_VARCHAR(tl) FROM lz"));
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        assertEquals("2020-01-01 18:00:00.000 Z", answer("SELECT TO_VARCHAR(tl) FROM lz"),
            "the DIGITS move, not just the suffix — and a zero offset is spelled Z");
        assertEquals("2020-06-01 17:00:00.000 Z", answer("SELECT TO_VARCHAR(jun) FROM lz"));
        assertEquals("2020-01-01 10:00:00.000", answer("SELECT TO_VARCHAR(tn) FROM lz"),
            "while the NTZ beside it does not move at all");
        engine.execute("ALTER SESSION SET TIMEZONE = 'Asia/Tokyo'");
        assertEquals("2020-01-02 03:00:00.000 +0900", answer("SELECT TO_VARCHAR(tl) FROM lz"),
            "far enough east to move the DATE as well");
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        assertEquals("2020-01-01 10:00:00.000 -0800", answer("SELECT TO_VARCHAR(tl) FROM lz"),
            "and it comes back");
    }

    /** A new literal reads in whatever zone is current when it is written. */
    @Test
    public void aNewLiteralUsesTheCurrentZone() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        assertEquals("2020-01-01 10:00:00.000 Z",
            answer("SELECT TO_VARCHAR('2020-01-01 10:00:00'::TIMESTAMP_LTZ)"));
        engine.execute("ALTER SESSION SET TIMEZONE = 'Asia/Tokyo'");
        assertEquals("2020-01-01 10:00:00.000 +0900",
            answer("SELECT TO_VARCHAR('2020-01-01 10:00:00'::TIMESTAMP_LTZ)"));
    }

    /** ★ Comparisons read BOTH sides as instants — these are the cells a naive compare gets backwards. */
    @Test
    public void comparisonsReadBothSidesAsInstants() {
        assertEquals("true", answer("SELECT tl = tn FROM lz"),
            "the same wall-clock digits in the session's zone ARE the same instant");
        assertEquals("false", answer("SELECT tl = '2020-01-01 18:00:00'::TIMESTAMP_NTZ FROM lz"),
            "though the LTZ's own UTC digits are NOT — read in the session zone they are a different one");
        assertEquals("true", answer("SELECT tl < jun FROM lz"));
    }

    /** The flavour survives everything that passes a value through. */
    @Test
    public void theFlavourSurvivesBeingPassedThrough() {
        assertEquals("2020-01-01 10:00:00.000 -0800",
            answer("SELECT TO_VARCHAR(GREATEST(tl, tl)) FROM lz"));
        assertEquals("2020-01-01 10:00:00.000 -0800",
            answer("SELECT TO_VARCHAR(COALESCE(tl, tl)) FROM lz"));
        assertEquals("2020-01-01 10:00:00.000 -0800 | 2020-06-01 10:00:00.000 -0700",
            answer("SELECT TO_VARCHAR(MIN(tl)), TO_VARCHAR(MAX(jun)) FROM lz"));
        assertEquals("2020-01-01 10:00:00.000 -0800",
            answer("SELECT TO_VARCHAR(tl) FROM lz ORDER BY tl"));
        assertEquals("{\"k\":\"2020-01-01 10:00:00.000 -0800\"}",
            answer("SELECT OBJECT_CONSTRUCT('k', tl) FROM lz"), "including inside a VARIANT");
    }

    /** A computed timestamp keeps the flavour it was computed FROM. */
    @Test
    public void aComputedTimestampKeepsTheFlavour() {
        assertEquals("2020-01-02 10:00:00.000 -0800",
            answer("SELECT TO_VARCHAR(DATEADD(day, 1, tl)) FROM lz"));
        assertEquals("2020-01-01 00:00:00.000 -0800",
            answer("SELECT TO_VARCHAR(DATE_TRUNC(day, tl)) FROM lz"));
        assertEquals("10", answer("SELECT EXTRACT(HOUR FROM tl) FROM lz"),
            "and the parts read in the session's zone");
    }

    /** Casting in and out: the digits stay, the flavour changes. */
    @Test
    public void castingBetweenTheFlavours() {
        assertEquals("2020-01-01 10:00:00.000 -0800",
            answer("SELECT TO_VARCHAR(tn::TIMESTAMP_LTZ) FROM lz"),
            "an NTZ reinterpreted in the session's zone keeps its digits");
        assertEquals("2020-01-01 10:00:00.000", answer("SELECT TO_VARCHAR(tl::TIMESTAMP_NTZ) FROM lz"),
            "and back the other way it is the session-local wall clock");
        assertEquals("2020-01-01", answer("SELECT TO_VARCHAR(tl::DATE) FROM lz"));
    }

    /** A view and a CTAS both carry it, and the CTAS column is still declared LTZ. */
    @Test
    public void aViewAndACtasBothCarryIt() {
        engine.execute("CREATE OR REPLACE VIEW vlz AS SELECT tl FROM lz");
        assertEquals("2020-01-01 10:00:00.000 -0800", answer("SELECT TO_VARCHAR(tl) FROM vlz"));
        engine.execute("CREATE OR REPLACE TABLE clz AS SELECT tl FROM lz");
        assertEquals("2020-01-01 10:00:00.000 -0800", answer("SELECT TO_VARCHAR(tl) FROM clz"));
        assertEquals("TL | TIMESTAMP_LTZ(9) | COLUMN | Y | null | N | N | null | null | null"
            + " | null | null | null", answer("DESCRIBE TABLE clz"));
    }
}
