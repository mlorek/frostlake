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
 * A TIMESTAMP_TZ remembers the offset it was WRITTEN with.
 *
 * <p>★ THAT IS THE WHOLE DIFFERENCE FROM A TIMESTAMP_LTZ. Both are instants; an LTZ re-renders in
 * whatever zone the session currently has, and a TZ never moves. Frostlake had nowhere to keep the
 * offset — a TZ was normalised to UTC and read back as a bare wall clock — so a value written
 * {@code 10:00:00 +0300} came back {@code 07:00:00}, three hours wrong to look at and missing the
 * offset that says why.
 *
 * <p>★ THE INSTANT WAS ALWAYS RIGHT, which is what made this a presentation job rather than a storage
 * one: ten o'clock at +0300 really is seven o'clock UTC, and the EPOCH, the equality against the same
 * instant spelled differently, and ORDER BY over mixed offsets all agreed with live before and after.
 *
 * <p>★ THE TWO CASTS DISAGREE ON PURPOSE. To a TIMESTAMP_NTZ a TZ TRUNCATES — the digits it was written
 * with survive and the offset is dropped. To a TIMESTAMP_LTZ it CONVERTS — the instant survives and the
 * digits change. One preserves the wall clock, the other the moment, so they cannot share a helper.
 *
 * <p>★ TEXT WITH NO OFFSET TAKES THE SESSION'S, AND THEN FIXES IT. The same literal cast under
 * America/Los_Angeles is {@code -0800} and under UTC is {@code Z} — but once cast, moving the session
 * never moves it again.
 *
 * <p>The offset SPELLING is normalised: {@code +03}, {@code +3}, {@code +03:00} and {@code +0300} all
 * read back {@code +0300}, and a zero offset is {@code Z} however it was written.
 *
 * <p>NOT FIXED HERE: the {@code TZH:TZM} format tokens, which still render an escaped {@code Z}, and
 * SYSTEM$TYPEOF's width and storage tag — each tracked on its own.
 */
public class TimestampTzWrittenOffsetTest extends BaseDatabaseTest {

    private static final String LITERAL = "2020-01-01 10:00:00 +0300";

    @Override
    protected void setupTest() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
    }

    /** One scalar, as text. */
    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** A TZ literal rendered back. */
    private String written(final String literal) {
        return answer("SELECT TO_VARCHAR('" + literal + "'::TIMESTAMP_TZ)");
    }

    /** ★ The offset comes back as it was written, normalised to ±HHMM. */
    @Test
    public void thewrittenOffsetComesBack() {
        assertEquals("2020-01-01 10:00:00.000 +0300", written(LITERAL));
        assertEquals("2020-01-01 10:00:00.000 +0300", written("2020-01-01 10:00:00 +03:00"));
        assertEquals("2020-01-01 10:00:00.000 +0300", written("2020-01-01 10:00:00 +03"));
        assertEquals("2020-01-01 10:00:00.000 +0300", written("2020-01-01 10:00:00 +3"),
            "every spelling the reader takes normalises to the same four digits");
        assertEquals("2020-01-01 10:00:00.000 -0800", written("2020-01-01 10:00:00 -0800"));
        assertEquals("2020-01-01 10:00:00.000 +0530", written("2020-01-01 10:00:00 +0530"),
            "a half-hour offset is kept whole");
        assertEquals("2020-01-01 10:00:00.000 -0930", written("2020-01-01 10:00:00 -0930"));
    }

    /** A ZERO offset is spelled Z, however it arrived. */
    @Test
    public void azeroOffsetIsSpelledZed() {
        assertEquals("2020-01-01 10:00:00.000 Z", written("2020-01-01 10:00:00 Z"));
        assertEquals("2020-01-01 10:00:00.000 Z", written("2020-01-01 10:00:00 +0000"));
    }

    /** ★ No offset written takes the SESSION's, at that instant — and then it stops moving. */
    @Test
    public void nooffsetWrittenTakesTheSessions() {
        assertEquals("2020-01-01 10:00:00.000 -0800", written("2020-01-01 10:00:00"));
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        try {
            assertEquals("2020-01-01 10:00:00.000 Z", written("2020-01-01 10:00:00"),
                "the same literal cast under another zone takes that one instead");
            assertEquals("2020-01-01 10:00:00.000 +0300", written(LITERAL),
                "★ but a WRITTEN offset is never moved by the session");
        } finally {
            engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        }
    }

    /** ★ The INSTANT underneath is unchanged — the offset is presentation and only presentation. */
    @Test
    public void theinstantIsUnchanged() {
        assertEquals("1577862000", answer("SELECT DATE_PART(EPOCH_SECOND, '" + LITERAL
            + "'::TIMESTAMP_TZ)"));
        assertEquals("true", answer("SELECT '" + LITERAL + "'::TIMESTAMP_TZ"
            + " = '2020-01-01 07:00:00 Z'::TIMESTAMP_TZ"),
            "two spellings of one moment are one value");
    }

    /** ★ To an NTZ a TZ TRUNCATES; to an LTZ it CONVERTS. */
    @Test
    public void thetwoCastsDisagreeOnPurpose() {
        assertEquals("2020-01-01 10:00:00.000",
            answer("SELECT TO_VARCHAR('" + LITERAL + "'::TIMESTAMP_TZ::TIMESTAMP_NTZ)"),
            "the digits survive and the offset is dropped");
        assertEquals("2019-12-31 23:00:00.000 -0800",
            answer("SELECT TO_VARCHAR('" + LITERAL + "'::TIMESTAMP_TZ::TIMESTAMP_LTZ)"),
            "the moment survives and the digits change");
    }

    /** A DATE and a TIME truncate like the NTZ does — the TZ's OWN wall clock. */
    @Test
    public void adateAndATimeTruncate() {
        assertEquals("2020-01-01", answer("SELECT TO_VARCHAR('" + LITERAL + "'::TIMESTAMP_TZ::DATE)"));
        assertEquals("10:00:00", answer("SELECT TO_VARCHAR('" + LITERAL + "'::TIMESTAMP_TZ::TIME)"));
    }

    /** A cast to VARCHAR is the same rendering, without going through TO_VARCHAR. */
    @Test
    public void acastToVarcharRendersTheSame() {
        assertEquals("2020-01-01 10:00:00.000 +0300", answer("SELECT '" + LITERAL
            + "'::TIMESTAMP_TZ::VARCHAR"));
    }

    /** Coming the OTHER way, a naive timestamp takes the session's offset. */
    @Test
    public void anaiveTimestampBecomingATzTakesTheSessions() {
        assertEquals("2020-01-01 10:00:00.000 -0800",
            answer("SELECT TO_VARCHAR('2020-01-01 10:00:00'::TIMESTAMP_NTZ::TIMESTAMP_TZ)"));
        assertEquals("2020-01-01 10:00:00.000 -0800",
            answer("SELECT TO_VARCHAR('2020-01-01 10:00:00'::TIMESTAMP_LTZ::TIMESTAMP_TZ)"));
    }

    /** Three rows written with three different offsets, and every relation over them. */
    private void mixedOffsetRows() {
        engine.execute("CREATE OR REPLACE TABLE tzw (id INT, t TIMESTAMP_TZ)");
        engine.execute("INSERT INTO tzw SELECT 1, '" + LITERAL + "'::TIMESTAMP_TZ");
        engine.execute("INSERT INTO tzw SELECT 2, '2020-01-01 10:00:00 -0800'::TIMESTAMP_TZ");
        engine.execute("INSERT INTO tzw SELECT 3, '2020-01-01 10:00:00 Z'::TIMESTAMP_TZ");
    }

    /** Every row's rendering, joined. */
    private String rendered(final String relation) {
        final ResultSet rs = engine.executeQuery(
            "SELECT id, TO_VARCHAR(t) FROM " + relation + " ORDER BY id");
        final StringBuilder all = new StringBuilder();
        while (rs.next()) {
            if (all.length() > 0) {
                all.append(",");
            }
            all.append(String.valueOf(rs.getValue(0))).append("/").append(String.valueOf(rs.getValue(1)));
        }
        return all.toString();
    }

    private static final String MIXED = "1/2020-01-01 10:00:00.000 +0300,"
        + "2/2020-01-01 10:00:00.000 -0800,3/2020-01-01 10:00:00.000 Z";

    /** ★ A STORED column keeps each row's own offset. */
    @Test
    public void astoredColumnKeepsEachRowsOffset() {
        mixedOffsetRows();
        assertEquals(MIXED, rendered("tzw"));
    }

    /** And ORDER BY, MIN and MAX read the INSTANT, so the offsets decide nothing. */
    @Test
    public void orderingReadsTheInstant() {
        mixedOffsetRows();
        final ResultSet order = engine.executeQuery("SELECT id FROM tzw ORDER BY t");
        final StringBuilder ids = new StringBuilder();
        while (order.next()) {
            ids.append(String.valueOf(order.getValue(0)));
        }
        assertEquals("132", ids.toString(), "+0300 is the earliest moment and -0800 the latest");
        assertEquals("2020-01-01 10:00:00.000 +0300", answer("SELECT TO_VARCHAR(MIN(t)) FROM tzw"));
        assertEquals("2020-01-01 10:00:00.000 -0800", answer("SELECT TO_VARCHAR(MAX(t)) FROM tzw"));
    }

    /** ★ A CTAS, an INSERT…SELECT and a VIEW each carry the per-row offset across. */
    @Test
    public void copyingRowsKeepsTheirOffsets() {
        mixedOffsetRows();
        engine.execute("CREATE OR REPLACE TABLE tzw2 AS SELECT id, t FROM tzw");
        assertEquals(MIXED, rendered("tzw2"));
        engine.execute("CREATE OR REPLACE TABLE tzw3 (id INT, t TIMESTAMP_TZ)");
        engine.execute("INSERT INTO tzw3 SELECT id, t FROM tzw");
        assertEquals(MIXED, rendered("tzw3"), "the column does not normalise its rows to one offset");
        engine.execute("CREATE OR REPLACE VIEW tzwv AS SELECT id, t FROM tzw");
        assertEquals(MIXED, rendered("tzwv"));
    }

    /** Embedded in a semi-structured value, a TZ carries its offset into the text. */
    @Test
    public void asemiStructuredMemberKeepsIt() {
        assertEquals("2020-01-01 10:00:00.000 +0300",
            answer("SELECT TO_VARCHAR(TO_VARIANT('" + LITERAL + "'::TIMESTAMP_TZ))"));
        assertEquals("{\"k\":\"2020-01-01 10:00:00.000 +0300\"}",
            answer("SELECT OBJECT_CONSTRUCT('k', '" + LITERAL + "'::TIMESTAMP_TZ)"));
        assertEquals("[\"2020-01-01 10:00:00.000 +0300\"]",
            answer("SELECT ARRAY_CONSTRUCT('" + LITERAL + "'::TIMESTAMP_TZ)"));
    }

    /** A computed TZ keeps the flavour it was computed from, offset included. */
    @Test
    public void acomputedTimestampKeepsTheFlavour() {
        assertEquals("2020-01-02 10:00:00.000 +0300",
            answer("SELECT TO_VARCHAR(DATEADD(day, 1, '" + LITERAL + "'::TIMESTAMP_TZ))"));
    }
}
