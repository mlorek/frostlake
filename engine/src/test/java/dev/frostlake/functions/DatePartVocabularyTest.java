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
 * The date/time component vocabulary EXTRACT and DATE_PART read — which is ONE vocabulary, not two.
 * That is what the measurement says: the whole abbreviation ladder was walked through both
 * functions, eighty-odd words a side, and every rung agrees. What differs is only the sentence
 * each gives when a word is refused.
 *
 * <pre>
 *   EXTRACT(ss FROM ts)      invalid value [ss] for parameter 'EXTRACT date/time part'
 *   DATE_PART(ss, ts)        invalid value [SS] for parameter 'DATE_PART date/time part'
 *   DATE_PART('ss', ts)      invalid value [ss] for parameter 'DATE_PART date/time part'
 * </pre>
 *
 * <p>Each names ITSELF as the parameter, and the word is quoted exactly as it arrived — so the only
 * reason DATE_PART's bareword comes out upper-cased is that its argument is a unit SLOT, which
 * upper-cases the name before the function sees it. EXTRACT's part is not a slot in that sense and
 * keeps the case it was written in.
 *
 * <p>Frostlake was wrong in both directions. It ACCEPTED four words live refuses — {@code ss},
 * {@code wks}, {@code isoweek}, {@code isodow} — three of them manufactured by a blind plural strip
 * that turned SS into S and WKS into WK. And it REFUSED seven live takes: {@code yyy}, {@code w},
 * {@code wy}, {@code yearofweek}, {@code yearofweekiso}, {@code timezone_hour} and
 * {@code timezone_minute}.
 */
public class DatePartVocabularyTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE ex (ts TIMESTAMP_NTZ)");
        engine.execute("INSERT INTO ex SELECT '2020-03-04 10:20:30.123456789'::TIMESTAMP_NTZ");
    }

    /** The answer, or the refusal's detail with the compilation prefix and its line break removed. */
    private String outcome(final String expr) {
        try {
            final ResultSet rs = engine.executeQuery("SELECT " + expr + " FROM ex");
            return rs.next() ? String.valueOf(rs.getValue(0)) : "<no rows>";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage())
                .replace("SQL compilation error:\n", "").replace("SQL compilation error: ", "")
                .replace("\n", " ").trim();
        }
    }

    /** Both functions read {@code word} as the same component, with the same answer. */
    private void bothRead(final String word, final String answer) {
        assertEquals(answer, outcome("EXTRACT(" + word + " FROM ts)"), "EXTRACT(" + word + ")");
        assertEquals(answer, outcome("DATE_PART(" + word + ", ts)"), "DATE_PART(" + word + ")");
    }

    /** Both refuse {@code word}, each naming itself and quoting the word as it arrived. */
    private void bothRefuse(final String word) {
        assertEquals("invalid value [" + word + "] for parameter 'EXTRACT date/time part'",
            outcome("EXTRACT(" + word + " FROM ts)"));
        assertEquals("invalid value [" + word.toUpperCase(java.util.Locale.ROOT)
            + "] for parameter 'DATE_PART date/time part'", outcome("DATE_PART(" + word + ", ts)"));
    }

    /** The ladder, rung by rung — one vocabulary for the two functions. */
    @Test
    public void theTwoFunctionsShareOneVocabulary() {
        bothRead("year", "2020");
        bothRead("yyyy", "2020");
        bothRead("yr", "2020");
        bothRead("quarter", "1");
        bothRead("qtr", "1");
        bothRead("month", "3");
        bothRead("mon", "3");
        bothRead("week", "10");
        bothRead("woy", "10");
        bothRead("weekiso", "10");
        bothRead("day", "4");
        bothRead("dayofmonth", "4");
        bothRead("dow", "3");
        bothRead("dayofweekiso", "3");
        bothRead("doy", "64");
        bothRead("hour", "10");
        bothRead("hh", "10");
        bothRead("minute", "20");
        bothRead("mi", "20");
        bothRead("second", "30");
        bothRead("sec", "30");
        bothRead("s", "30");
    }

    /** The seven words Frostlake used to refuse. */
    @Test
    public void theSevenWordsThatWereMissing() {
        bothRead("yyy", "2020");
        bothRead("w", "10");
        bothRead("wy", "10");
        bothRead("yearofweek", "2020");
        bothRead("yearofweekiso", "2020");
        bothRead("timezone_hour", "0");
        bothRead("timezone_minute", "0");
    }

    /** The four Frostlake used to accept — the direction that matters most. */
    @Test
    public void theFourWordsThatWereNotUnits() {
        bothRefuse("ss");
        bothRefuse("wks");
        bothRefuse("isoweek");
        bothRefuse("isodow");
    }

    /** A NANOSECOND reads; the millisecond and microsecond families do not, in any spelling. */
    @Test
    public void nanosecondsReadWhereMillisecondsDoNot() {
        bothRead("nanosecond", "123456789");
        bothRead("ns", "123456789");
        bothRead("nsec", "123456789");
        bothRead("nsecs", "123456789");
        bothRead("nanosecs", "123456789");
        bothRefuse("millisecond");
        bothRefuse("ms");
        bothRefuse("msec");
        bothRefuse("msecs");
        bothRefuse("microsecond");
        bothRefuse("us");
        bothRefuse("usec");
        bothRefuse("usecs");
    }

    /** The EPOCH family, which is where the sub-second precision does survive. */
    @Test
    public void theEpochFamilyReadsEveryPrecision() {
        bothRead("epoch", "1583317230");
        bothRead("epoch_second", "1583317230");
        bothRead("epoch_millisecond", "1583317230123");
        bothRead("epoch_microsecond", "1583317230123456");
        bothRead("epoch_nanosecond", "1583317230123456789");
    }

    /** The word is quoted as it ARRIVED, which is why the two functions read differently. */
    @Test
    public void theWordIsQuotedAsItArrived() {
        assertEquals("invalid value [zz] for parameter 'EXTRACT date/time part'",
            outcome("EXTRACT(zz FROM ts)"), "EXTRACT keeps the case that was written");
        assertEquals("invalid value [ZZ] for parameter 'EXTRACT date/time part'",
            outcome("EXTRACT(ZZ FROM ts)"));
        assertEquals("invalid value [ZZ] for parameter 'DATE_PART date/time part'",
            outcome("DATE_PART(zz, ts)"), "DATE_PART's SLOT upper-cases a bareword first");
        assertEquals("invalid value [zz] for parameter 'DATE_PART date/time part'",
            outcome("DATE_PART('zz', ts)"), "but a string literal arrives as written");
        assertEquals("invalid value [ss] for parameter 'DATE_PART date/time part'",
            outcome("DATE_PART('ss', ts)"));
    }
}
