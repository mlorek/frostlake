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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How a refusal names the date and time calls it re-prints from the plan. DATEDIFF and its twins are
 * {@code DATE_DIFF<KIND>IN<UNITS>(from, to)}, a subtraction of two dates or timestamps is that
 * difference with its operands swapped, DATE_TRUNC and a temporal TRUNC are {@code TRUNC<KIND>TO<Unit>(x)}
 * — or the value itself where the truncation changes nothing — and DATE converts its argument to what it
 * reads; the operands meet in their common type first. An invalid-type sentence names each conversion by
 * its function. Each expected answer is the account's own.
 */
public class DateVocabularyEchoTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE ft (d DATE, d2 DATE, ts TIMESTAMP_NTZ, ts2 TIMESTAMP_NTZ, n NUMBER(5,0), tm TIME,"
            + " tl TIMESTAMP_LTZ, tz TIMESTAMP_TZ, g VARCHAR(10), ts3 TIMESTAMP_NTZ(3), tl3 TIMESTAMP_LTZ(3))");
        engine.execute("CREATE TABLE fam (g VARCHAR(10), n NUMBER(5,0), d DATE, ts TIMESTAMP_NTZ, v VARIANT)");
    }

    /** The answer as one line: the rows a query returns, or its refusal with each line break as |. */
    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder("ACCEPTED:");
            while (rs.next()) {
                all.append(" ").append(String.valueOf(rs.getValue(0)));
            }
            return all.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** The kind follows the operands and the unit is named in the plural, whatever its spelling. */
    @Test
    public void aDifferenceIsItsDateDiffFunction() {
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFDATEINDAYS(FT.D, FT.D2), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, d, d2), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFDATEINMONTHS(FT.D, FT.D2), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(month, d, d2), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFDATEINYEARS(FT.D, FT.D2), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(year, d, d2), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFDATEINWEEKS(FT.D, FT.D2), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(week, d, d2), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFDATEINQUARTERS(FT.D, FT.D2), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(quarter, d, d2), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFDATEINHOURS(FT.D, FT.D2), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(hour, d, d2), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINDAYS(FT.TS, FT.TS2), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, ts, ts2), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINHOURS(FT.TS, FT.TS2), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(hour, ts, ts2), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINMINUTES(FT.TS, FT.TS2), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(minute, ts, ts2), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINSECONDS(FT.TS, FT.TS2), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(second, ts, ts2), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINMILLISECONDS(FT.TS, FT.TS2), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(millisecond, ts, ts2), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINMICROSECONDS(FT.TS, FT.TS2), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(microsecond, ts, ts2), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINNANOSECONDS(FT.TS, FT.TS2), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(nanosecond, ts, ts2), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINMONTHS(FT.TS, FT.TS2), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(month, ts, ts2), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINWEEKS(FT.TS, FT.TS2), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(week, ts, ts2), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINQUARTERS(FT.TS, FT.TS2), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(quarter, ts, ts2), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINYEARS(FT.TS, FT.TS2), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(year, ts, ts2), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINDAYS(FT.TL, FT.TL), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, tl, tl), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINDAYS(FT.TZ, FT.TZ), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, tz, tz), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMEINHOURS(FT.TM, FT.TM), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(hour, tm, tm), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMEINMINUTES(FT.TM, FT.TM), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(minute, tm, tm), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMEINSECONDS(FT.TM, FT.TM), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(second, tm, tm), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMEINMILLISECONDS(FT.TM, FT.TM), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(millisecond, tm, tm), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMEINNANOSECONDS(FT.TM, FT.TM), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(nanosecond, tm, tm), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFDATEINDAYS(FT.D, FT.D2), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(dd, d, d2), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFDATEINDAYS(FT.D, FT.D2), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF('day', d, d2), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFDATEINDAYS(FT.D, FT.D2), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(days, d, d2), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINMILLISECONDS(FT.TS, FT.TS2), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(ms, ts, ts2), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINMICROSECONDS(FT.TS, FT.TS2), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(us, ts, ts2), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINNANOSECONDS(FT.TS, FT.TS2), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(ns, ts, ts2), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMEINHOURS(FT.TM, FT.TM), 1)] expected 1, got 2",
            answer("SELECT UPPER(TIMEDIFF(hour, tm, tm), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFDATEINDAYS(FT.D, FT.D2), 1)] expected 1, got 2",
            answer("SELECT UPPER(TIMESTAMPDIFF(day, d, d2), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINHOURS(FT.TS, FT.TS2), 1)] expected 1, got 2",
            answer("SELECT UPPER(TIMESTAMPDIFF(hour, ts, ts2), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(null, 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, d, NULL), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(null, 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, NULL, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER((DATE_DIFFDATEINDAYS(FT.D, FT.D2)) + 1, 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, d, d2) + 1, 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFDATEINDAYS(FT.D, DATE_ADDDAYSTODATE(1, FT.D2)), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, d, d2 + 1), 1) FROM ft"));
    }

    /** Operands of different families meet in one type: the widest timestamp flavour, at precision 9. */
    @Test
    public void theOperandsMeetInTheirCommonType() {
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINDAYS(CAST(FT.D AS TIMESTAMP_NTZ(9)), FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, d, ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINDAYS(FT.TS, CAST(FT.D AS TIMESTAMP_NTZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, ts, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINHOURS(CAST(FT.D AS TIMESTAMP_NTZ(9)), FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(hour, d, ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINDAYS(CAST(FT.TS AS TIMESTAMP_LTZ(9)), FT.TL), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, ts, tl), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINDAYS(FT.TL, CAST(FT.TS AS TIMESTAMP_LTZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, tl, ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINDAYS(CAST(FT.TL AS TIMESTAMP_TZ(9)), FT.TZ), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, tl, tz), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINDAYS(FT.TZ, CAST(FT.TL AS TIMESTAMP_TZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, tz, tl), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINDAYS(CAST(FT.TS AS TIMESTAMP_TZ(9)), FT.TZ), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, ts, tz), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINDAYS(FT.TZ, CAST(FT.TS AS TIMESTAMP_TZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, tz, ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINDAYS(CAST(FT.D AS TIMESTAMP_LTZ(9)), FT.TL), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, d, tl), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINDAYS(FT.TZ, CAST(FT.D AS TIMESTAMP_TZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, tz, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINDAYS(CAST(FT.D AS TIMESTAMP_NTZ(9)), CAST(FT.TS3 AS TIMESTAMP_NTZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, d, ts3), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINDAYS(CAST(FT.TS3 AS TIMESTAMP_LTZ(9)), FT.TL), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, ts3, tl), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINDAYS(CAST(FT.TS3 AS TIMESTAMP_NTZ(9)), FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, ts3, ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINDAYS(CAST(FT.TL3 AS TIMESTAMP_LTZ(9)), CAST(FT.TS AS TIMESTAMP_LTZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, tl3, ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFDATEINDAYS(CAST(FT.G AS DATE), FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, g, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFDATEINDAYS(CAST('2020-01-01' AS DATE), FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, '2020-01-01', d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFDATEINDAYS(FT.D, CAST('2020-01-01' AS DATE)), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, d, '2020-01-01'), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINDAYS(CAST(FT.G AS TIMESTAMP_NTZ(9)), CAST(FT.G AS TIMESTAMP_NTZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, g, g), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINDAYS(CAST(FT.G AS TIMESTAMP_NTZ(9)), FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, g, ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINDAYS(CAST(FT.G AS TIMESTAMP_LTZ(9)), FT.TL), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, g, tl), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINDAYS(CAST(FT.G AS TIMESTAMP_NTZ(9)), CAST(FT.TS3 AS TIMESTAMP_NTZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, g, ts3), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINDAYS(CAST('2020-01-01' AS TIMESTAMP_NTZ(9)), CAST('2020-02-01' AS TIMESTAMP_NTZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, '2020-01-01', '2020-02-01'), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFDATEINDAYS(CAST(FAM.V AS DATE), FAM.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, v, d), 1) FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFDATEINDAYS(FAM.D, CAST(FAM.V AS DATE)), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, d, v), 1) FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINDAYS(CAST(FAM.V AS TIMESTAMP_NTZ(9)), CAST(FAM.V AS TIMESTAMP_NTZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, v, v), 1) FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPINDAYS(FAM.TS, CAST(FAM.V AS TIMESTAMP_NTZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEDIFF(day, ts, v), 1) FROM fam"));
    }

    /** A date minus a date, or a timestamp minus a timestamp, is the difference with the operands swapped. */
    @Test
    public void aSubtractionIsTheDifferenceSwapped() {
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFDATEINDAYS(FT.D2, FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(d - d2, 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFDATEINDAYS(FT.D, FT.D2), 1)] expected 1, got 2",
            answer("SELECT UPPER(d2 - d, 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER((DATE_DIFFDATEINDAYS(FT.D, FT.D2)) + 1, 1)] expected 1, got 2",
            answer("SELECT UPPER(d2 - d + 1, 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPTOINTERVAL(FT.TS2, FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(ts - ts2, 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPTOINTERVAL(FT.TL, FT.TL), 1)] expected 1, got 2",
            answer("SELECT UPPER(tl - tl, 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPTOINTERVAL(FT.TS, CAST(FT.TS3 AS TIMESTAMP_NTZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(ts3 - ts, 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_DIFFTIMESTAMPTOINTERVAL(FT.TL, CAST(FT.TS AS TIMESTAMP_LTZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(ts - tl, 1) FROM ft"));
        assertEquals("SQL compilation error:|Can not convert parameter 'DATE_DIFFDATEINDAYS(FT.D2, FT.D)' of type [NUMBER(9,0)] into expected type [BOOLEAN]",
            answer("SELECT CASE WHEN d - d2 THEN 1 END FROM ft"));
        assertEquals("SQL compilation error:|invalid type [CAST(DATE_DIFFDATEINDAYS(FT.D2, FT.D) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (d - d2)::DATE FROM ft"));
    }

    /** An invalid-type sentence names the conversions by their functions; the conversion sentence by the plan. */
    @Test
    public void anInvalidTypeSentenceNamesTheConversionsByTheirFunctions() {
        assertEquals("SQL compilation error:|invalid type [CAST(DATE_DIFFDATEINDAYS(FT.D, FT.D2) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT DATEDIFF(day, d, d2)::DATE FROM ft"));
        assertEquals("SQL compilation error:|invalid type [CAST(DATE_DIFFDATEINDAYS(FT.D, FT.D2) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(DATEDIFF(day, d, d2) AS DATE) FROM ft"));
        assertEquals("SQL compilation error:|invalid type [CAST(DATE_DIFFTIMESTAMPINDAYS(FT.TS, FT.TS2) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT DATEDIFF(day, ts, ts2)::DATE FROM ft"));
        assertEquals("SQL compilation error:|invalid type [CAST(DATE_DIFFTIMESTAMPINDAYS(TO_TIMESTAMP_NTZ(FT.D), FT.TS) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT DATEDIFF(day, d, ts)::DATE FROM ft"));
        assertEquals("SQL compilation error:|invalid type [CAST(DATE_DIFFDATEINDAYS(TO_DATE(FT.G), FT.D) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT DATEDIFF(day, g, d)::DATE FROM ft"));
        assertEquals("SQL compilation error:|invalid type [CAST(DATE_DIFFTIMESTAMPINDAYS(TO_TIMESTAMP_LTZ(FT.TS), FT.TL) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(DATEDIFF(day, ts, tl) AS DATE) FROM ft"));
        assertEquals("SQL compilation error:|invalid type [CAST(DATE_DIFFTIMESTAMPINDAYS(TO_TIMESTAMP_NTZ('2020-01-01'), TO_TIMESTAMP_NTZ('2020-02-01')) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(DATEDIFF(day, '2020-01-01', '2020-02-01') AS DATE)"));
        assertEquals("SQL compilation error:|invalid type [CAST(DATE_DIFFDATEINDAYS(TO_DATE(FAM.V), FAM.D) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(DATEDIFF(day, v, d) AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(DATE_DIFFTIMESTAMPINDAYS(TO_TIMESTAMP_NTZ(FT.D), FT.TS) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(DATEDIFF(day, d, ts) AS DATE) FROM ft"));
        assertEquals("SQL compilation error:|invalid type [CAST(DATE_DIFFTIMESTAMPINDAYS(FT.TS, TO_TIMESTAMP_NTZ(FT.D)) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(DATEDIFF(day, ts, d) AS DATE) FROM ft"));
        assertEquals("SQL compilation error:|Can not convert parameter 'DATE_DIFFDATEINDAYS(FT.D, FT.D2)' of type [NUMBER(9,0)] into expected type [BOOLEAN]",
            answer("SELECT CASE WHEN DATEDIFF(day, d, d2) THEN 1 END FROM ft"));
    }

    /** A truncation is its TRUNC function, in the plan's own capitals, or nothing at all. */
    @Test
    public void aTruncationIsItsTruncFunctionOrTheValueItself() {
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(FT.D, 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('day', d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(FT.D, 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('hour', d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(FT.D, 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('minute', d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(FT.D, 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('second', d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(FT.D, 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('millisecond', d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(FT.D, 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('microsecond', d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(FT.D, 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('nanosecond', d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(FT.D, 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC(day, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(FT.D, 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('dd', d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCDATETOMonth(FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('month', d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCDATETOMonth(FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('mm', d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCDATETOMonth(FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('months', d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCDATETOMonth(FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC(month, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCDATETOMonth(FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('MONTH', d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCDATETOYEAR(FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('year', d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCDATETOYEAR(FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('y', d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCDATETOWeek(FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('week', d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCDATETOWeek(FT.D2), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('week', d2), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCDATETOQUARTER(FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('quarter', d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCTIMESTAMPTODay(FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('day', ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCTIMESTAMPTODay(FT.TS2), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('day', ts2), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCTIMESTAMPTOHOUR(FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('hour', ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCTIMESTAMPTOMINUTE(FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('minute', ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCTIMESTAMPTOSECOND(FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('second', ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCTIMESTAMPTOMILLISECOND(FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('millisecond', ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCTIMESTAMPTOMICROSECOND(FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('microsecond', ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(FT.TS, 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('nanosecond', ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(FT.TS, 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('ns', ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCTIMESTAMPTOMonth(FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('month', ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCTIMESTAMPTOYEAR(FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('year', ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCTIMESTAMPTOWeek(FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('week', ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCTIMESTAMPTOQuarter(FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('quarter', ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCTIMESTAMPTODay(FT.TL), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('day', tl), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCTIMESTAMPTOMonth(FT.TL), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('month', tl), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCTIMESTAMPTOWeek(FT.TL), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('week', tl), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCTIMESTAMPTODay(FT.TZ), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('day', tz), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCTIMESTAMPTOHOUR(FT.TZ), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('hour', tz), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCTIMESTAMPTOMonth(FT.TZ), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('month', tz), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCTIMESTAMPTOQuarter(FT.TZ), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('quarter', tz), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCTIMESTAMPTOYEAR(FT.TZ), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('year', tz), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCTIMESTAMPTOWeek(FT.TZ), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('week', tz), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCTIMESTAMPTOMonth(CAST(FT.TS3 AS TIMESTAMP_NTZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('month', ts3), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCTIMETOHOUR(FT.TM), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('hour', tm), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCTIMETOMINUTE(FT.TM), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('minute', tm), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCTIMETOSECOND(FT.TM), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('second', tm), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCTIMETOMILLISECOND(FT.TM), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('millisecond', tm), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCTIMETOMICROSECOND(FT.TM), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('microsecond', tm), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(FT.TM, 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('nanosecond', tm), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCDATETOMonth(FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(TRUNC(d, 'month'), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCTIMESTAMPTODay(FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(TRUNC(ts, 'day'), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCTIMETOHOUR(FT.TM), 1)] expected 1, got 2",
            answer("SELECT UPPER(TRUNC(tm, 'hour'), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCDATETOMonth(FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('month', DATE_TRUNC('day', d)), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(FT.TS AS DATE), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('day', ts::DATE), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCDATETOMonth(CAST(FT.G AS DATE)), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('month', TO_DATE(g)), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRUNCDATETOMonth(DATE_ADDDAYSTODATE(1, FT.D)), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('month', d + 1), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(null, 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE_TRUNC('month', NULL), 1)"));
        assertEquals("SQL compilation error:|invalid type [CAST(TRUNCDATETOMonth(FT.D) AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            answer("SELECT DATE_TRUNC('month', d)::NUMBER FROM ft"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FT.D) AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            answer("SELECT DATE_TRUNC('day', d)::NUMBER FROM ft"));
        assertEquals("SQL compilation error:|invalid type [CAST(TRUNCTIMESTAMPTOHOUR(FT.TS) AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            answer("SELECT DATE_TRUNC('hour', ts)::NUMBER FROM ft"));
        assertEquals("SQL compilation error:|invalid type [CAST(TRUNCTIMESTAMPTOMonth(FT.TS) AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            answer("SELECT DATE_TRUNC('month', ts)::NUMBER FROM ft"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FT.TS) AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            answer("SELECT DATE_TRUNC('nanosecond', ts)::NUMBER FROM ft"));
        assertEquals("SQL compilation error:|invalid type [CAST(TRUNCTIMETOHOUR(FT.TM) AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            answer("SELECT DATE_TRUNC('hour', tm)::NUMBER FROM ft"));
        assertEquals("SQL compilation error:|Can not convert parameter 'TRUNCDATETOMonth(FT.D)' of type [DATE] into expected type [BOOLEAN]",
            answer("SELECT CASE WHEN DATE_TRUNC('month', d) THEN 1 END FROM ft"));
        assertEquals("SQL compilation error:|Can not convert parameter 'FT.D' of type [DATE] into expected type [BOOLEAN]",
            answer("SELECT CASE WHEN DATE_TRUNC('day', d) THEN 1 END FROM ft"));
    }

    /** DATE converts its argument to what it reads: a DATE to TIMESTAMP_LTZ, beside a format anything to text. */
    @Test
    public void dateConvertsItsArgumentToWhatItReads() {
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE(CAST(FT.D AS TIMESTAMP_LTZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE(d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE(FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE(ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE(CAST(FT.TS3 AS TIMESTAMP_NTZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE(ts3), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE(FT.TL), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE(tl), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE(FT.TZ), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE(tz), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE(FT.G), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE(g), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE('2020-01-01'), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE('2020-01-01'), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE(CAST(FT.N AS VARCHAR(134217728))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE(n), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE(CAST(1.5 AS VARCHAR(134217728))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE(1.5), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE(CAST(FAM.V AS VARCHAR(134217728))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE(v), 1) FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE(SYSTEM$NULL_TO_TIMESTAMP_LTZ(null)), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE(NULL), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE(CAST(CAST('2020-01-01' AS DATE) AS TIMESTAMP_LTZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE('2020-01-01'::DATE), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE(CAST(CAST(FT.G AS DATE) AS TIMESTAMP_LTZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE(TO_DATE(g)), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE(CAST(DATE(CAST(FT.D AS TIMESTAMP_LTZ(9))) AS TIMESTAMP_LTZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE(DATE(d)), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE(CAST(DATE_ADDDAYSTODATE(1, FT.D) AS TIMESTAMP_LTZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE(d + 1), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE(CAST(FT.D AS VARCHAR(134217728)), 'YYYY-MM-DD'), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE(d, 'YYYY-MM-DD'), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE(FT.G, 'YYYY-MM-DD'), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE(g, 'YYYY-MM-DD'), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE(CAST(FT.TL AS VARCHAR(134217728)), 'YYYY'), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE(tl, 'YYYY'), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE(CAST(FT.TS AS VARCHAR(134217728)), 'YYYY'), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE(ts, 'YYYY'), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE(CAST(FAM.V AS VARCHAR(134217728)), 'YYYY'), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE(v, 'YYYY'), 1) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(DATE(TO_TIMESTAMP_LTZ(FT.D)) AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            answer("SELECT DATE(d)::NUMBER FROM ft"));
        assertEquals("SQL compilation error:|invalid type [CAST(DATE(FT.TS) AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            answer("SELECT DATE(ts)::NUMBER FROM ft"));
        assertEquals("SQL compilation error:|invalid type [CAST(DATE(TO_CHAR(FT.N)) AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            answer("SELECT CAST(DATE(n) AS NUMBER) FROM ft"));
        assertEquals("SQL compilation error:|invalid type [CAST(DATE(FT.G) AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            answer("SELECT CAST(DATE(g) AS NUMBER) FROM ft"));
        assertEquals("SQL compilation error:|invalid type [CAST(DATE(TO_CHAR(FAM.V)) AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            answer("SELECT CAST(DATE(v) AS NUMBER) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(DATE(SYSTEM$NULL_TO_TIMESTAMP_LTZ(NULL)) AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            answer("SELECT CAST(DATE(NULL) AS NUMBER)"));
        assertEquals("SQL compilation error:|invalid type [CAST(DATE(TO_CHAR(FT.D), 'YYYY') AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            answer("SELECT CAST(DATE(d, 'YYYY') AS NUMBER) FROM ft"));
        assertEquals("SQL compilation error:|Can not convert parameter 'DATE(CAST(FT.D AS TIMESTAMP_LTZ(9)))' of type [DATE] into expected type [BOOLEAN]",
            answer("SELECT CASE WHEN DATE(d) THEN 1 END FROM ft"));
    }
}
