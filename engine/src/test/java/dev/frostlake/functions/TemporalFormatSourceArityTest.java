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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A temporal conversion's FORMAT argument exists for a TEXT source only. Over a DATE, a TIME, a TIMESTAMP of
 * any flavour or a VARIANT, TO_DATE / TO_TIME / TO_TIMESTAMP* refuse the format at compile time as an ARITY
 * fault - "too many arguments ... expected 1, got 2" - positioned at the call, over an empty table too, and
 * echoing the call as the plan prints it: a cast re-printed as its own conversion. The TIME synonym is refused
 * alike, the DATE synonym answers, and of the TRY_ twins only TRY_TO_DATE over a DATE is this sentence.
 * Every cell was measured on a real account.
 */
public class TemporalFormatSourceArityTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE ft (d DATE, ts TIMESTAMP_NTZ, tz TIMESTAMP_TZ, tl TIMESTAMP_LTZ,"
            + " tm TIME, v VARIANT, s VARCHAR)");
        engine.execute("INSERT INTO ft SELECT '2020-01-15', '2020-01-15 10:00:00', '2020-01-15 10:00:00 +01:00',"
            + " '2020-01-15 10:00:00', '10:00:00', PARSE_JSON('\"2020-01-15\"'), '2020-01-15'");
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        return String.valueOf(refused.getMessage()).replace('\n', '|');
    }

    private String answer(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private static String tooMany(final int at, final String call, final int expected) {
        return "SQL compilation error: error line 1 at position " + at + "|too many arguments for function ["
            + call + "] expected " + expected + ", got " + (expected + 1);
    }

    @Test
    public void aFormatOverATemporalColumnIsTooManyArguments() {
        assertEquals(tooMany(7, "TO_DATE(FT.D, 'YYYY-MM-DD')", 1), refusal("SELECT TO_DATE(d, 'YYYY-MM-DD') FROM ft"));
        assertEquals(tooMany(7, "TO_DATE(FT.TS, 'YYYY-MM-DD')", 1), refusal("SELECT TO_DATE(ts, 'YYYY-MM-DD') FROM ft"));
        assertEquals(tooMany(7, "TO_DATE(FT.TZ, 'YYYY-MM-DD')", 1), refusal("SELECT TO_DATE(tz, 'YYYY-MM-DD') FROM ft"));
        assertEquals(tooMany(7, "TO_TIME(FT.TM, 'HH24:MI:SS')", 1), refusal("SELECT TO_TIME(tm, 'HH24:MI:SS') FROM ft"));
        assertEquals(tooMany(7, "TO_TIME(FT.TS, 'HH24:MI:SS')", 1), refusal("SELECT TO_TIME(ts, 'HH24:MI:SS') FROM ft"));
        assertEquals(tooMany(7, "TO_TIME(FT.TZ, 'HH24:MI:SS')", 1), refusal("SELECT TO_TIME(tz, 'HH24:MI:SS') FROM ft"));
        assertEquals(tooMany(7, "TO_TIMESTAMP(FT.D, 'YYYY-MM-DD')", 1),
            refusal("SELECT TO_TIMESTAMP(d, 'YYYY-MM-DD') FROM ft"));
        assertEquals(tooMany(7, "TO_TIMESTAMP_NTZ(FT.TS, 'YYYY-MM-DD')", 1),
            refusal("SELECT TO_TIMESTAMP_NTZ(ts, 'YYYY-MM-DD') FROM ft"));
        assertEquals(tooMany(7, "TO_TIMESTAMP_LTZ(FT.TL, 'YYYY-MM-DD')", 1),
            refusal("SELECT TO_TIMESTAMP_LTZ(tl, 'YYYY-MM-DD') FROM ft"));
        assertEquals(tooMany(7, "TO_TIMESTAMP_TZ(FT.TZ, 'YYYY-MM-DD')", 1),
            refusal("SELECT TO_TIMESTAMP_TZ(tz, 'YYYY-MM-DD') FROM ft"));
    }

    /** A cast source is echoed as the conversion the plan made of it. */
    @Test
    public void aCastSourceIsEchoedAsItsOwnConversion() {
        assertEquals(tooMany(7, "TO_DATE(TO_DATE('2020-01-15'), 'YYYY-MM-DD')", 1),
            refusal("SELECT TO_DATE('2020-01-15'::DATE, 'YYYY-MM-DD')"));
        assertEquals(tooMany(7, "TO_DATE(TO_TIMESTAMP_NTZ('2020-01-15 10:00:00'), 'YYYY-MM-DD')", 1),
            refusal("SELECT TO_DATE('2020-01-15 10:00:00'::TIMESTAMP, 'YYYY-MM-DD')"));
        assertEquals(tooMany(7, "TO_DATE(TO_TIMESTAMP_NTZ('2020-01-15 10:00:00'), 'YYYY-MM-DD')", 1),
            refusal("SELECT TO_DATE('2020-01-15 10:00:00'::TIMESTAMP_NTZ, 'YYYY-MM-DD')"));
        assertEquals(tooMany(7, "TO_DATE(TO_TIMESTAMP_LTZ('2020-01-15 10:00:00'), 'YYYY-MM-DD')", 1),
            refusal("SELECT TO_DATE('2020-01-15 10:00:00'::TIMESTAMP_LTZ, 'YYYY-MM-DD')"));
        assertEquals(tooMany(7, "TO_DATE(TO_TIMESTAMP_TZ('2020-01-15 10:00:00 +01:00'), 'YYYY-MM-DD')", 1),
            refusal("SELECT TO_DATE('2020-01-15 10:00:00 +01:00'::TIMESTAMP_TZ, 'YYYY-MM-DD')"));
        assertEquals(tooMany(7, "TO_TIME(TO_TIME('10:00:00'), 'HH24:MI:SS')", 1),
            refusal("SELECT TO_TIME('10:00:00'::TIME, 'HH24:MI:SS')"));
        assertEquals(tooMany(7, "TO_TIMESTAMP_TZ(TO_DATE('2020-01-15'), 'YYYY-MM-DD')", 1),
            refusal("SELECT TO_TIMESTAMP_TZ('2020-01-15'::DATE, 'YYYY-MM-DD')"));
    }

    @Test
    public void aVariantSourceIsRefusedAlike() {
        assertEquals(tooMany(7, "TO_DATE(PARSE_JSON('\"2020X01X15\"'), 'YYYY-MM-DD')", 1),
            refusal("SELECT TO_DATE(PARSE_JSON('\"2020X01X15\"'), 'YYYY-MM-DD')"));
        assertEquals(tooMany(7, "TO_TIMESTAMP(PARSE_JSON('\"2020-01-15\"'), 'YYYY-MM-DD')", 1),
            refusal("SELECT TO_TIMESTAMP(PARSE_JSON('\"2020-01-15\"'), 'YYYY-MM-DD')"));
        assertEquals(tooMany(7, "TO_TIME(PARSE_JSON('\"10:00:00\"'), 'HH24:MI:SS')", 1),
            refusal("SELECT TO_TIME(PARSE_JSON('\"10:00:00\"'), 'HH24:MI:SS')"));
        assertEquals(tooMany(7, "TO_TIMESTAMP_LTZ(FT.V, 'YYYY-MM-DD')", 1),
            refusal("SELECT TO_TIMESTAMP_LTZ(v, 'YYYY-MM-DD') FROM ft"));
    }

    /** Positioned at the call, compile-time, a NULL format counted, the TIME synonym and TRY_TO_DATE over a DATE. */
    @Test
    public void theRefusalIsPositionedCompileTimeAndCountsANullFormat() {
        assertEquals(tooMany(10, "TO_DATE(FT.D, 'YYYY-MM-DD')", 1), refusal("SELECT 1, TO_DATE(d, 'YYYY-MM-DD') FROM ft"));
        assertEquals(tooMany(7, "TO_DATE(FT.D, 'YYYY-MM-DD')", 1),
            refusal("SELECT TO_DATE(d, 'YYYY-MM-DD') FROM ft WHERE 1 = 0"));
        assertEquals(tooMany(7, "TO_DATE(FT.D, NULL)", 1), refusal("SELECT TO_DATE(d, NULL) FROM ft"));
        assertEquals(tooMany(7, "TIME(FT.TM, 'HH24:MI:SS')", 1), refusal("SELECT TIME(tm, 'HH24:MI:SS') FROM ft"));
        assertEquals(tooMany(7, "TRY_TO_DATE(FT.D, 'YYYY-MM-DD')", 1),
            refusal("SELECT TRY_TO_DATE(d, 'YYYY-MM-DD') FROM ft"));
    }

    /** A text source takes the format, and so does the DATE synonym over any source; a third argument is plain arity. */
    @Test
    public void aTextSourceAndTheDateSynonymTakeTheFormat() {
        assertEquals("2020-01-15", answer("SELECT TO_DATE(s, 'YYYY-MM-DD') FROM ft"));
        assertEquals("2020-01-15", answer("SELECT TO_DATE(d::VARCHAR, 'YYYY-MM-DD') FROM ft"));
        assertEquals("2020-01-15", answer("SELECT DATE(d, 'YYYY-MM-DD') FROM ft"));
        assertEquals("null", answer("SELECT TO_DATE(NULL, 'YYYY-MM-DD')"));
        assertEquals(tooMany(7, "TO_DATE(FT.D, 'YYYY-MM-DD', 'x')", 2),
            refusal("SELECT TO_DATE(d, 'YYYY-MM-DD', 'x') FROM ft"));
    }
}
