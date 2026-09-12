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
 * How a refusal names an operand it re-prints from the plan, where the plan's vocabulary differs from
 * what was written. The arity sentences, the conversion sentence and the nesting brackets print the
 * RESOLVED plan: an interval shift is its {@code DATE_ADD<UNITS>TO<KIND>} function with the amount
 * first, a one-argument conversion is the cast it stands for, a cast that changes nothing is dropped,
 * a literal minus belongs to its number and an array literal is its constructor call. An invalid-type
 * sentence prints the conversions still as the functions they were planned as — {@code TO_DATE(x)},
 * {@code identity(x)}, and an operand's implicit rescale as
 * {@code FIXED_TO_FIXED(x AS NUMBER(p,s)[UNKNOWN])}. Each expected answer is the account's own.
 */
public class PlanVocabularyEchoTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE re (a NUMBER(10,2), i NUMBER(10,0), f FLOAT)");
        engine.execute("CREATE TABLE ft (d DATE, ts TIMESTAMP_NTZ, n NUMBER(5,0), tm TIME, tl TIMESTAMP_LTZ, tz TIMESTAMP_TZ, g VARCHAR(10))");
        engine.execute("CREATE TABLE mix (d DATE, a NUMBER(10,2), f FLOAT, n NUMBER(5,0), g VARCHAR(10), ts TIMESTAMP_NTZ)");
        engine.execute("CREATE TABLE rt (g VARCHAR(10), n NUMBER(5,0), v VARIANT, b BOOLEAN)");
        engine.execute("CREATE TABLE fam (g VARCHAR(10), n NUMBER(5,0), a NUMBER(10,2), f FLOAT, b BOOLEAN,"
            + " d DATE, tm TIME, ts TIMESTAMP_NTZ, tl TIMESTAMP_LTZ, tz TIMESTAMP_TZ, bn BINARY, b10 BINARY(10),"
            + " v VARIANT, o OBJECT, ar ARRAY, vec VECTOR(FLOAT,3))");
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

    /** An operand's rescale is FIXED_TO_FIXED and its move to FLOAT is TO_DOUBLE in an invalid-type sentence. */
    @Test
    public void anInvalidTypeSentenceSpellsTheOperandConversionsAsPlanFunctions() {
        assertEquals("SQL compilation error:|invalid type [CAST(RE.A + (FIXED_TO_FIXED(1 AS NUMBER(10,2)[UNKNOWN])) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (a + 1)::DATE FROM re"));
        assertEquals("SQL compilation error:|invalid type [CAST(RE.A + (FIXED_TO_FIXED(1.5 AS NUMBER(10,2)[UNKNOWN])) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (a + 1.5)::DATE FROM re"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(RE.I AS NUMBER(11,1)[UNKNOWN])) + 1.5 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (i + 1.5)::DATE FROM re"));
        assertEquals("SQL compilation error:|invalid type [CAST(RE.A + (FIXED_TO_FIXED(RE.I AS NUMBER(12,2)[UNKNOWN])) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (a + i)::DATE FROM re"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(1 AS NUMBER(10,2)[UNKNOWN])) + RE.A AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (1 + a)::DATE FROM re"));
        assertEquals("SQL compilation error:|invalid type [CAST(RE.A - (FIXED_TO_FIXED(1 AS NUMBER(10,2)[UNKNOWN])) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (a - 1)::DATE FROM re"));
        assertEquals("SQL compilation error:|invalid type [CAST(RE.A % (FIXED_TO_FIXED(1 AS NUMBER(10,2)[UNKNOWN])) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (a % 1)::DATE FROM re"));
        assertEquals("SQL compilation error:|invalid type [CAST(RE.A > (FIXED_TO_FIXED(1 AS NUMBER(10,2)[UNKNOWN])) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (a > 1)::DATE FROM re"));
        assertEquals("SQL compilation error:|invalid type [CAST(RE.A = (FIXED_TO_FIXED(1 AS NUMBER(10,2)[UNKNOWN])) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (a = 1)::DATE FROM re"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(RE.A AS NUMBER(16,8)[UNKNOWN])) / 1 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (a / 1)::DATE FROM re"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(RE.I AS NUMBER(16,6)[UNKNOWN])) / 3 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (i / 3)::DATE FROM re"));
        assertEquals("SQL compilation error:|invalid type [CAST((RE.A + (FIXED_TO_FIXED(1 AS NUMBER(10,2)[UNKNOWN]))) + (FIXED_TO_FIXED(1 AS NUMBER(11,2)[UNKNOWN])) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (a + 1 + 1)::DATE FROM re"));
        assertEquals("SQL compilation error:|invalid type [CAST((RE.A + (FIXED_TO_FIXED(1 AS NUMBER(10,2)[UNKNOWN]))) * 2 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT ((a + 1) * 2)::DATE FROM re"));
        assertEquals("SQL compilation error:|invalid type [CAST((ABS(RE.A)) + (FIXED_TO_FIXED(1 AS NUMBER(10,2)[UNKNOWN])) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (ABS(a) + 1)::DATE FROM re"));
        assertEquals("SQL compilation error:|invalid type [CAST((NEGATE(RE.A)) + (FIXED_TO_FIXED(1 AS NUMBER(10,2)[UNKNOWN])) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (-a + 1)::DATE FROM re"));
        assertEquals("SQL compilation error:|invalid type [CAST((TO_DOUBLE(RE.A)) + RE.F AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (a + f)::DATE FROM re"));
        assertEquals("SQL compilation error:|invalid type [CAST((TO_DOUBLE(RE.I)) + RE.F AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (i + f)::DATE FROM re"));
        assertEquals("SQL compilation error:|invalid type [CAST(RE.F + (TO_DOUBLE(1)) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (f + 1)::DATE FROM re"));
        assertEquals("SQL compilation error:|invalid type [CAST(RE.A + (FIXED_TO_FIXED(1 AS NUMBER(10,2)[UNKNOWN])) AS TIME(9))] for parameter 'TO_TIME'",
            answer("SELECT (a + 1)::TIME FROM re"));
        assertEquals("SQL compilation error:|invalid type [CAST(RE.A + (FIXED_TO_FIXED(1 AS NUMBER(10,2)[UNKNOWN])) AS BINARY(67108864))] for parameter 'TO_BINARY'",
            answer("SELECT (a + 1)::BINARY FROM re"));
        assertEquals("SQL compilation error:|invalid type [CAST(RE.A + (FIXED_TO_FIXED(1 AS NUMBER(10,2)[UNKNOWN])) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(a + 1 AS DATE) FROM re"));
        assertEquals("SQL compilation error:|invalid type [TRY_CAST(RE.A + (FIXED_TO_FIXED(1 AS NUMBER(10,2)[UNKNOWN])))] for parameter 'TO_DATE'",
            answer("SELECT TRY_CAST(a + 1 AS DATE) FROM re"));
        assertEquals("SQL compilation error:|invalid type [TO_DATE(RE.A + (FIXED_TO_FIXED(1 AS NUMBER(10,2)[UNKNOWN])))] for parameter 'TO_DATE'",
            answer("SELECT TO_DATE(a + 1) FROM re"));
        assertEquals("SQL compilation error:|invalid type [CAST(RE.I + 1 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (i + 1)::DATE FROM re"));
        assertEquals("SQL compilation error:|invalid type [CAST(RE.A * 1 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (a * 1)::DATE FROM re"));
    }

    /** The same rescale is a CAST on the arity and conversion surfaces. */
    @Test
    public void theArityAndConversionSentencesKeepTheCastSpelling() {
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(RE.A + (CAST(1 AS NUMBER(10,2))), 1)] expected 1, got 2",
            answer("SELECT UPPER(a + 1, 1) FROM re"));
        assertEquals("SQL compilation error:|Can not convert parameter 'RE.A + (CAST(1 AS NUMBER(10,2)))' of type [NUMBER(11,2)] into expected type [BOOLEAN]",
            answer("SELECT CASE WHEN a + 1 THEN 1 END FROM re"));
    }

    /** DATEADD, TIMEADD, TIMESTAMPADD and a DATE plus or minus a number are DATE_ADD functions. */
    @Test
    public void anIntervalShiftIsItsDateAddFunction() {
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_DATE(DATE_ADDDAYSTODATE(1, FT.D), 'YYYY-MM-DD', 1)] expected 2, got 3",
            answer("SELECT TO_DATE(DATEADD(day, 1, d), 'YYYY-MM-DD', 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(1, FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, 1, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDMONTHSTODATE(1, FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(month, 1, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDYEARSTODATE(1, FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(year, 1, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDWEEKSTODATE(1, FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(week, 1, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDQUARTERSTODATE(1, FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(quarter, 1, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDHOURSTOTIMESTAMP(1, CAST(FT.D AS TIMESTAMP_NTZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(hour, 1, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDMINUTESTOTIMESTAMP(1, CAST(FT.D AS TIMESTAMP_NTZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(minute, 1, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDSECONDSTOTIMESTAMP(1, CAST(FT.D AS TIMESTAMP_NTZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(second, 1, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDNANOSTOTIMESTAMP(1, CAST(FT.D AS TIMESTAMP_NTZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(nanosecond, 1, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTOTIMESTAMP(1, FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, 1, ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDMONTHSTOTIMESTAMP(1, FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(month, 1, ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDYEARSTOTIMESTAMP(1, FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(year, 1, ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDWEEKSTOTIMESTAMP(1, FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(week, 1, ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDQUARTERSTOTIMESTAMP(1, FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(quarter, 1, ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDMINUTESTOTIMESTAMP(1, FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(minute, 1, ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDSECONDSTOTIMESTAMP(1, FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(second, 1, ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDMILLISTOTIMESTAMP(1, FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(millisecond, 1, ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDMICROSTOTIMESTAMP(1, FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(microsecond, 1, ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDNANOSTOTIMESTAMP(1, FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(nanosecond, 1, ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTOTIMESTAMP(1, FT.TL), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, 1, tl), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDHOURSTOTIMESTAMP(1, FT.TL), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(hour, 1, tl), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTOTIMESTAMP(1, FT.TZ), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, 1, tz), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDHOURSTOTIMESTAMP(1, FT.TZ), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(hour, 1, tz), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDHOURSTOTIME(1, FT.TM), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(hour, 1, tm), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDMINUTESTOTIME(1, FT.TM), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(minute, 1, tm), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDSECONDSTOTIME(1, FT.TM), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(second, 1, tm), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDMILLISTOTIME(1, FT.TM), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(millisecond, 1, tm), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTOTIMESTAMP(1, CAST(FT.G AS TIMESTAMP_NTZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, 1, g), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDMONTHSTOTIMESTAMP(1, CAST(FT.G AS TIMESTAMP_NTZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(month, 1, g), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDHOURSTOTIMESTAMP(1, CAST(FT.G AS TIMESTAMP_NTZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(hour, 1, g), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTOTIMESTAMP(1, CAST(RT.V AS TIMESTAMP_NTZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, 1, v), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTOTIMESTAMP(1, CAST('2020-01-01' AS TIMESTAMP_NTZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, 1, '2020-01-01'), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(1, CAST('2020-01-01' AS DATE)), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, 1, '2020-01-01'::DATE), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(1, CAST('2020-01-01' AS DATE)), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, 1, TO_DATE('2020-01-01')), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTOTIMESTAMP(1, CAST('2020-01-01' AS TIMESTAMP_NTZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, 1, TO_TIMESTAMP('2020-01-01')), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(1, CURRENT_DATE()), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, 1, CURRENT_DATE()), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(1, CAST(FT.TS AS DATE)), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, 1, ts::DATE), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTOTIMESTAMP(1, CAST(FT.D AS TIMESTAMP_LTZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, 1, d::TIMESTAMP_LTZ), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTOTIMESTAMP(1, CAST(FT.D AS TIMESTAMP_TZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, 1, d::TIMESTAMP_TZ), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTOTIMESTAMP(1, CAST(CAST(FT.D AS VARCHAR(134217728)) AS TIMESTAMP_NTZ(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, 1, d::VARCHAR), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDHOURSTOTIME(1, CAST('10:00:00' AS TIME(9))), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(hour, 1, '10:00:00'::TIME), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(1, FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(TIMESTAMPADD(day, 1, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDHOURSTOTIMESTAMP(1, FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(TIMESTAMPADD(hour, 1, ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(1, FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(TIMEADD(day, 1, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(1, FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(d + 1, 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(1, FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(1 + d, 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(1, FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(d + (1), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(FT.N, FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(d + n, 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(FT.N, FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(n + d, 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(NEGATE(1), FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(d - 1, 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(NEGATE(FT.N), FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(d - n, 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(NEGATE(-1), FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(d - (-1), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(-1, FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(d + -1, 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(CAST(1.5 AS NUMBER(9,0)), FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(d + 1.5, 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(CAST(NEGATE(1.5) AS NUMBER(9,0)), FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(d - 1.5, 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(CAST(MIX.A AS NUMBER(9,0)), MIX.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(d + a, 1) FROM mix"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(CAST(NEGATE(MIX.A) AS NUMBER(9,0)), MIX.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(d - a, 1) FROM mix"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(1, DATE_ADDDAYSTODATE(1, FT.D)), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, 1, d) + 1, 1) FROM ft"));
    }

    /** Every spelling of a unit names the same DATE_ADD function. */
    @Test
    public void anIntervalUnitIsNamedInThePluralWhateverItsSpelling() {
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(1, FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(dd, 1, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(1, FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD('day', 1, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(1, FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(days, 1, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDYEARSTODATE(1, FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(y, 1, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDYEARSTODATE(1, FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(yyyy, 1, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDMONTHSTODATE(1, FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(mon, 1, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDQUARTERSTODATE(1, FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(q, 1, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDHOURSTOTIMESTAMP(1, FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(h, 1, ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDMINUTESTOTIMESTAMP(1, FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(mi, 1, ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDSECONDSTOTIMESTAMP(1, FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(s, 1, ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDMILLISTOTIMESTAMP(1, FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(ms, 1, ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDMICROSTOTIMESTAMP(1, FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(us, 1, ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDNANOSTOTIMESTAMP(1, FT.TS), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(ns, 1, ts), 1) FROM ft"));
    }

    /** The amount is bare when it is a whole number, and converted to NUMBER(9,0) otherwise. */
    @Test
    public void anIntervalAmountIsPlannedAsAWholeNumber() {
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(FT.N, FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, n, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(-1, FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, -1, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(NEGATE(FT.N), FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, -n, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(1 + 1, FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, 1 + 1, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(12345678901, FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, 12345678901, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(1, FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, 1.0, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(2, FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, 2.0, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(1, FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, 1e0, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(CAST(1.5 AS NUMBER(9,0)), FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, 1.5, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(CAST(1.5 AS NUMBER(9,0)), FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, 1.50, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(CAST(2.5 AS NUMBER(9,0)), FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, 2.5e0, d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(CAST(MIX.A AS NUMBER(9,0)), MIX.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, a, d), 1) FROM mix"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(CAST(NEGATE(MIX.A) AS NUMBER(9,0)), MIX.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, -a, d), 1) FROM mix"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(CAST(MIX.F AS NUMBER(9,0)), MIX.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, f, d), 1) FROM mix"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(CAST(CAST(FT.N AS NUMBER(10,2)) AS NUMBER(9,0)), FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, n::NUMBER(10,2), d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(TO_NUMBER('1', 9, 0), FT.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, '1', d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE_ADDDAYSTODATE(TO_NUMBER(MIX.G, 9, 0), MIX.D), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, g, d), 1) FROM mix"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(null, 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, 1, NULL), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(null, 1)] expected 1, got 2",
            answer("SELECT UPPER(DATEADD(day, NULL, d), 1) FROM ft"));
    }

    /** TO_DATE and its kin are casts in the plan, TRY_ forms TRY_CASTs. */
    @Test
    public void aOneArgumentConversionIsTheCastItStandsFor() {
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST('2020-01-01' AS DATE), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_DATE('2020-01-01'), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(FT.G AS DATE), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_DATE(g), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(FT.TS AS DATE), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_DATE(ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(RT.V AS DATE), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_DATE(v), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST('10:00:00' AS TIME(9)), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_TIME('10:00:00'), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(FT.G AS TIME(9)), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_TIME(g), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(FT.TS AS TIME(9)), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_TIME(ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(FT.TM AS TIME(9)), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_TIME(tm), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(RT.V AS TIME(9)), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_TIME(v), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST('2020-01-01' AS TIMESTAMP_NTZ(9)), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_TIMESTAMP('2020-01-01'), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(FT.D AS TIMESTAMP_NTZ(9)), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_TIMESTAMP(d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(FT.TS AS TIMESTAMP_NTZ(9)), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_TIMESTAMP(ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(RT.V AS TIMESTAMP_NTZ(9)), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_TIMESTAMP(v), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(1 AS TIMESTAMP_NTZ(0)), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_TIMESTAMP(1), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(1.5 AS TIMESTAMP_NTZ(1)), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_TIMESTAMP(1.5), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(FT.N AS TIMESTAMP_NTZ(0)), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_TIMESTAMP(n), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST('2020-01-01' AS TIMESTAMP_NTZ(9)), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_TIMESTAMP_NTZ('2020-01-01'), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(FT.D AS TIMESTAMP_NTZ(9)), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_TIMESTAMP_NTZ(d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST('2020-01-01' AS TIMESTAMP_LTZ(9)), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_TIMESTAMP_LTZ('2020-01-01'), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(FT.TS AS TIMESTAMP_LTZ(9)), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_TIMESTAMP_LTZ(ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST('2020-01-01' AS TIMESTAMP_TZ(9)), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_TIMESTAMP_TZ('2020-01-01'), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST('1.5' AS FLOAT), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_DOUBLE('1.5'), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(1 AS FLOAT), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_DOUBLE(1), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(FT.G AS FLOAT), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_DOUBLE(g), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(FT.N AS FLOAT), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_DOUBLE(n), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(RT.V AS FLOAT), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_DOUBLE(v), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST('true' AS BOOLEAN), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_BOOLEAN('true'), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(FT.G AS BOOLEAN), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_BOOLEAN(g), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(RT.V AS BOOLEAN), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_BOOLEAN(v), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(1 <> 0 AS BOOLEAN), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_BOOLEAN(1), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(FT.N <> 0 AS BOOLEAN), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_BOOLEAN(n), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(RE.A <> (CAST(0 AS NUMBER(10,2))) AS BOOLEAN), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_BOOLEAN(a), 1) FROM re"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(RT.N AS VARCHAR(134217728)), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_VARCHAR(n), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(1.5 AS VARCHAR(134217728)), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_VARCHAR(1.5), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(FT.TS AS VARCHAR(134217728)), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_VARCHAR(ts), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(RT.B AS VARCHAR(134217728)), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_VARCHAR(b), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(RT.V AS VARCHAR(134217728)), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_VARCHAR(v), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(FT.D AS VARCHAR(134217728)), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_CHAR(d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST('ab' AS BINARY(67108864)), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_BINARY('ab'), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(FT.G AS BINARY(67108864)), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_BINARY(g), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(1 AS ARRAY), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_ARRAY(1), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(FT.G AS ARRAY), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_ARRAY(g), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(PARSE_JSON('{}') AS OBJECT), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_OBJECT(PARSE_JSON('{}')), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(RT.V AS OBJECT), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_OBJECT(v), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(1 AS VARIANT), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_VARIANT(1), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(TRUE AS VARIANT), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_VARIANT(TRUE), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRY_CAST('2020-01-01' AS DATE), 1)] expected 1, got 2",
            answer("SELECT UPPER(TRY_TO_DATE('2020-01-01'), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRY_CAST(FT.G AS DATE), 1)] expected 1, got 2",
            answer("SELECT UPPER(TRY_TO_DATE(g), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRY_CAST('10:00:00' AS TIME(9)), 1)] expected 1, got 2",
            answer("SELECT UPPER(TRY_TO_TIME('10:00:00'), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRY_CAST('2020-01-01' AS TIMESTAMP_NTZ(9)), 1)] expected 1, got 2",
            answer("SELECT UPPER(TRY_TO_TIMESTAMP('2020-01-01'), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRY_CAST('1.5' AS FLOAT), 1)] expected 1, got 2",
            answer("SELECT UPPER(TRY_TO_DOUBLE('1.5'), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRY_CAST('true' AS BOOLEAN), 1)] expected 1, got 2",
            answer("SELECT UPPER(TRY_TO_BOOLEAN('true'), 1)"));
    }

    /** A conversion or cast onto the type its operand already has leaves only the operand. */
    @Test
    public void aConversionThatChangesNothingIsDropped() {
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(FT.D, 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_DATE(d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST('2020-01-01' AS DATE), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_DATE(TO_DATE('2020-01-01')), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(FT.G AS DATE), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_DATE(TO_DATE(g)), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(FT.G, 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_VARCHAR(g), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER('x', 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_VARCHAR('x'), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(RT.N AS VARCHAR(134217728)), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_VARCHAR(TO_VARCHAR(n)), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(RE.F, 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_DOUBLE(f), 1) FROM re"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(RT.B, 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_BOOLEAN(b), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(FT.D, 1)] expected 1, got 2",
            answer("SELECT UPPER(TRY_TO_DATE(d), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(FT.D, 1)] expected 1, got 2",
            answer("SELECT UPPER(d::DATE, 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(FT.G, 1)] expected 1, got 2",
            answer("SELECT UPPER(g::VARCHAR, 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(FT.G, 1)] expected 1, got 2",
            answer("SELECT UPPER(g::VARCHAR(10), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(FT.G, 1)] expected 1, got 2",
            answer("SELECT UPPER(g::TEXT, 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER('x', 1)] expected 1, got 2",
            answer("SELECT UPPER('x'::VARCHAR, 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(FT.N, 1)] expected 1, got 2",
            answer("SELECT UPPER(n::NUMBER(5,0), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(RE.A, 1)] expected 1, got 2",
            answer("SELECT UPPER(a::NUMBER(10,2), 1) FROM re"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(RE.I, 1)] expected 1, got 2",
            answer("SELECT UPPER(i::NUMBER(10,0), 1) FROM re"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(RE.F, 1)] expected 1, got 2",
            answer("SELECT UPPER(f::FLOAT, 1) FROM re"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(RE.F, 1)] expected 1, got 2",
            answer("SELECT UPPER(f::DOUBLE, 1) FROM re"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(RT.B, 1)] expected 1, got 2",
            answer("SELECT UPPER(b::BOOLEAN, 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(RT.V, 1)] expected 1, got 2",
            answer("SELECT UPPER(v::VARIANT, 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(FT.G, 1)] expected 1, got 2",
            answer("SELECT UPPER(TRY_CAST(g AS VARCHAR), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(FT.G, 1)] expected 1, got 2",
            answer("SELECT UPPER(TRY_CAST(g AS VARCHAR(10)), 1) FROM ft"));
    }

    /** Any other cast is CAST or TRY_CAST, the target named in full. */
    @Test
    public void aCastThatChangesSomethingIsSpelledCast() {
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST('2020-01-01' AS DATE), 1)] expected 1, got 2",
            answer("SELECT UPPER('2020-01-01'::DATE, 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(RT.N AS VARCHAR(134217728)), 1)] expected 1, got 2",
            answer("SELECT UPPER(n::VARCHAR, 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(RT.N AS VARCHAR(5)), 1)] expected 1, got 2",
            answer("SELECT UPPER(CAST(n AS VARCHAR(5)), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(FT.N AS VARCHAR(3)), 1)] expected 1, got 2",
            answer("SELECT UPPER(n::VARCHAR(3), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(FT.G AS VARCHAR(5)), 1)] expected 1, got 2",
            answer("SELECT UPPER(g::VARCHAR(5), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(FT.N AS NUMBER(38,0)), 1)] expected 1, got 2",
            answer("SELECT UPPER(n::INT, 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(1 AS NUMBER(38,0)), 1)] expected 1, got 2",
            answer("SELECT UPPER(1::NUMBER, 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(1 AS NUMBER(38,0)), 1)] expected 1, got 2",
            answer("SELECT UPPER(1::INT, 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(1.5 AS FLOAT), 1)] expected 1, got 2",
            answer("SELECT UPPER(1.5::FLOAT, 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(RE.A AS NUMBER(12,2)), 1)] expected 1, got 2",
            answer("SELECT UPPER(a::NUMBER(12,2), 1) FROM re"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(RE.A AS NUMBER(10,1)), 1)] expected 1, got 2",
            answer("SELECT UPPER(a::NUMBER(10,1), 1) FROM re"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(FT.D AS TIMESTAMP_LTZ(9)), 1)] expected 1, got 2",
            answer("SELECT UPPER(d::TIMESTAMP_LTZ, 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(FT.TS AS TIMESTAMP_NTZ(9)), 1)] expected 1, got 2",
            answer("SELECT UPPER(ts::TIMESTAMP_NTZ, 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(CAST(FT.TM AS TIME(9)), 1)] expected 1, got 2",
            answer("SELECT UPPER(tm::TIME, 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRY_CAST(FT.G AS NUMBER(38,0)), 1)] expected 1, got 2",
            answer("SELECT UPPER(TRY_CAST(g AS NUMBER), 1) FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TRY_CAST(FT.G AS DATE), 1)] expected 1, got 2",
            answer("SELECT UPPER(TRY_CAST(g AS DATE), 1) FROM ft"));
    }

    /** A cast or conversion of the bare word NULL is SYSTEM$NULL_TO_ its family. */
    @Test
    public void aTypedNullIsThePlansNullOfThatFamily() {
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(SYSTEM$NULL_TO_DATE(null), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_DATE(NULL), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(SYSTEM$NULL_TO_DATE(null), 1)] expected 1, got 2",
            answer("SELECT UPPER(NULL::DATE, 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(SYSTEM$NULL_TO_TEXT(null), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_VARCHAR(NULL), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(SYSTEM$NULL_TO_TEXT(null), 1)] expected 1, got 2",
            answer("SELECT UPPER(CAST(NULL AS VARCHAR), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(SYSTEM$NULL_TO_REAL(null), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_DOUBLE(NULL), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(SYSTEM$NULL_TO_FIXED(null), 1)] expected 1, got 2",
            answer("SELECT UPPER(NULL::NUMBER, 1)"));
    }

    /** COALESCE, LEFT, paths, the aggregates and the synonyms as the plan names them; the rest as written. */
    @Test
    public void theOtherScalarsThePlanRenamesOrKeeps() {
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(IFNULL(RT.G, 'a'), 1)] expected 1, got 2",
            answer("SELECT UPPER(COALESCE(g, 'a'), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(SUBSTR(RT.G, 1, 2), 1)] expected 1, got 2",
            answer("SELECT UPPER(LEFT(g, 2), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(GET(RT.V, 'a'), 1)] expected 1, got 2",
            answer("SELECT UPPER(v:a, 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER((CAST(SUM(RT.N) AS NUMBER(23,6))) / (COUNT(RT.N)), 1)] expected 1, got 2",
            answer("SELECT UPPER(AVG(n), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(PERCENTILE_CONT(CAST(RT.N AS NUMBER(8,3)), 0.5), 1)] expected 1, got 2",
            answer("SELECT UPPER(MEDIAN(n), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(SUM(IFF(CAST(RT.B AS BOOLEAN), 1, 0)), 1)] expected 1, got 2",
            answer("SELECT UPPER(COUNT_IF(b), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER((SUM(RT.N)) + 1, 1)] expected 1, got 2",
            answer("SELECT UPPER(SUM(n) + 1, 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TO_NUMBER('1'), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_DECIMAL('1'), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TO_NUMBER('1'), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_NUMERIC('1'), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TO_TIMESTAMP_NTZ('2020-01-01', 'YYYY-MM-DD'), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_TIMESTAMP('2020-01-01', 'YYYY-MM-DD'), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TO_NUMBER('12'), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_NUMBER('12'), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(DATE('2020-01-01'), 1)] expected 1, got 2",
            answer("SELECT UPPER(DATE('2020-01-01'), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TIME('10:00:00'), 1)] expected 1, got 2",
            answer("SELECT UPPER(TIME('10:00:00'), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TO_DATE('2020-01-01', 'YYYY-MM-DD'), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_DATE('2020-01-01', 'YYYY-MM-DD'), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(TO_CHAR(FT.D, 'YYYY'), 1)] expected 1, got 2",
            answer("SELECT UPPER(TO_CHAR(d, 'YYYY'), 1) FROM ft"));
    }

    /** A minus before a number belongs to it; an array or object literal is its constructor call. */
    @Test
    public void aLiteralIsPrintedAsThePlanHoldsIt() {
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(-1, 1)] expected 1, got 2",
            answer("SELECT UPPER(-1, 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(-1.5, 1)] expected 1, got 2",
            answer("SELECT UPPER(-1.5, 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(-1, 1)] expected 1, got 2",
            answer("SELECT UPPER(-(1), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(-1, 1)] expected 1, got 2",
            answer("SELECT UPPER(- 1, 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(NEGATE(RE.A), 1)] expected 1, got 2",
            answer("SELECT UPPER(-a, 1) FROM re"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(ARRAY_CONSTRUCT(1, 2, 3), 1)] expected 1, got 2",
            answer("SELECT UPPER([1,2,3], 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(OBJECT_CONSTRUCT('a', 1), 1)] expected 1, got 2",
            answer("SELECT UPPER({'a': 1}, 1)"));
        assertEquals("SQL compilation error:|Can not convert parameter '-1' of type [NUMBER(1,0)] into expected type [BOOLEAN]",
            answer("SELECT CASE WHEN -1 THEN 1 END"));
        assertEquals("SQL compilation error:|Can not convert parameter 'ARRAY_CONSTRUCT(1, 2)' of type [ARRAY] into expected type [BOOLEAN]",
            answer("SELECT CASE WHEN [1,2] THEN 1 END"));
    }

    /** NOT IN over a list is NOT of the IN. */
    @Test
    public void aNotInListIsTheNotOfTheMembershipTest() {
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER('b' IN ('abc'), 1)] expected 1, got 2",
            answer("SELECT UPPER('b' IN ('abc'), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER('b' IN ('a', 'b'), 1)] expected 1, got 2",
            answer("SELECT UPPER('b' IN ('a', 'b'), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(NOT('b' IN ('abc')), 1)] expected 1, got 2",
            answer("SELECT UPPER('b' NOT IN ('abc'), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(NOT('b' IN ('a', 'b')), 1)] expected 1, got 2",
            answer("SELECT UPPER('b' NOT IN ('a', 'b'), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX(NOT('b' IN ('abc')))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' NOT IN ('abc'))"));
    }

    /** The CASE-condition conversion sentence uses the resolved vocabulary too. */
    @Test
    public void theConversionSentenceNamesItsParameterFromTheResolvedPlan() {
        assertEquals("SQL compilation error:|Can not convert parameter 'CAST('2020-01-01' AS DATE)' of type [DATE] into expected type [BOOLEAN]",
            answer("SELECT CASE WHEN TO_DATE('2020-01-01') THEN 1 END"));
        assertEquals("SQL compilation error:|Can not convert parameter 'DATE_ADDDAYSTODATE(1, FT.D)' of type [DATE] into expected type [BOOLEAN]",
            answer("SELECT CASE WHEN DATEADD(day, 1, d) THEN 1 END FROM ft"));
        assertEquals("SQL compilation error:|Can not convert parameter 'DATE_ADDDAYSTODATE(1, FT.D)' of type [DATE] into expected type [BOOLEAN]",
            answer("SELECT CASE WHEN d + 1 THEN 1 END FROM ft"));
        assertEquals("SQL compilation error:|Can not convert parameter 'FT.D' of type [DATE] into expected type [BOOLEAN]",
            answer("SELECT CASE WHEN d::DATE THEN 1 END FROM ft"));
        assertEquals("SQL compilation error:|Can not convert parameter 'FT.D' of type [DATE] into expected type [BOOLEAN]",
            answer("SELECT CASE WHEN TO_DATE(d) THEN 1 END FROM ft"));
        assertEquals("SQL compilation error:|Can not convert parameter 'CAST(RT.N AS VARCHAR(134217728))' of type [VARCHAR(134217728)] into expected type [BOOLEAN]",
            answer("SELECT CASE WHEN n::VARCHAR THEN 1 END FROM rt"));
        assertEquals("SQL compilation error:|Can not convert parameter 'CAST(RT.N AS VARCHAR(134217728))' of type [VARCHAR(134217728)] into expected type [BOOLEAN]",
            answer("SELECT CASE WHEN TO_VARCHAR(n) THEN 1 END FROM rt"));
        assertEquals("SQL compilation error:|Can not convert parameter 'CAST(RT.N AS FLOAT)' of type [FLOAT] into expected type [BOOLEAN]",
            answer("SELECT CASE WHEN TO_DOUBLE(n) THEN 1 END FROM rt"));
    }

    /** The two calls a nesting message brackets print the same resolved plan. */
    @Test
    public void theNestingBracketsUseTheResolvedPlanToo() {
        assertEquals("SQL compilation error: |Aggregate functions cannot be nested: [MAX(FT.D)] nested in [MAX(MAX(FT.D))]",
            answer("SELECT MAX(MAX(d::DATE)) FROM ft"));
        assertEquals("SQL compilation error: |Aggregate functions cannot be nested: [SUM(CAST(RT.N AS NUMBER(38,0)))] nested in [SUM(SUM(CAST(RT.N AS NUMBER(38,0))))]",
            answer("SELECT SUM(SUM(n::INT)) FROM rt"));
        assertEquals("SQL compilation error: |Aggregate functions cannot be nested: [SUM(RT.N)] nested in [SUM(SUM(RT.N))]",
            answer("SELECT SUM(SUM(n::NUMBER(5,0))) FROM rt"));
        assertEquals("SQL compilation error: |Aggregate functions cannot be nested: [SUM(CAST(RT.N AS FLOAT))] nested in [SUM(SUM(CAST(RT.N AS FLOAT)))]",
            answer("SELECT SUM(SUM(TO_DOUBLE(n))) FROM rt"));
        assertEquals("SQL compilation error: |Aggregate functions cannot be nested: [MAX(DATE_ADDDAYSTODATE(1, FT.D))] nested in [MAX(MAX(DATE_ADDDAYSTODATE(1, FT.D)))]",
            answer("SELECT MAX(MAX(DATEADD(day, 1, d))) FROM ft"));
    }

    /** Inside an invalid-type sentence a cast is still the conversion it was planned as. */
    @Test
    public void anInvalidTypeSentenceNamesACastByItsConversionFunction() {
        assertEquals("SQL compilation error:|invalid type [CAST(TO_BOOLEAN(FT.G) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(g::BOOLEAN AS DATE) FROM ft"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_TIME(FT.TS) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(ts::TIME AS DATE) FROM ft"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_NUMBER(FT.G) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(g::NUMBER AS DATE) FROM ft"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_TIMESTAMP(FT.D) AS BOOLEAN)] for parameter 'TO_BOOLEAN'",
            answer("SELECT CAST(d::TIMESTAMP_NTZ AS BOOLEAN) FROM ft"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_DOUBLE(FT.N) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(n::FLOAT AS DATE) FROM ft"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FT.D) AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            answer("SELECT CAST(d::DATE AS NUMBER) FROM ft"));
        assertEquals("SQL compilation error:|invalid type [CAST(SYSTEM$NULL_TO_FIXED(NULL) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(NULL::NUMBER AS DATE)"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_DATE(TO_CHAR(FT.N)) AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            answer("SELECT CAST(TO_VARCHAR(n)::DATE AS NUMBER) FROM ft"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_DATE(TO_CHAR(FT.N)) AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            answer("SELECT CAST(n::VARCHAR::DATE AS NUMBER) FROM ft"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_TIMESTAMP(TO_DATE(FT.G)) AS BOOLEAN)] for parameter 'TO_BOOLEAN'",
            answer("SELECT CAST(g::DATE::TIMESTAMP_NTZ AS BOOLEAN) FROM ft"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_DATE('2020-01-01') AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            answer("SELECT ('2020-01-01'::DATE)::NUMBER"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_DATE('2020-01-01') AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            answer("SELECT (TO_DATE('2020-01-01'))::NUMBER"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_DATE(FT.G) AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            answer("SELECT CAST(TO_DATE(g) AS NUMBER) FROM ft"));
        assertEquals("SQL compilation error:|invalid type [CAST(IFNULL(FT.N, 1) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(COALESCE(n, 1) AS DATE) FROM ft"));
        assertEquals("SQL compilation error:|invalid type [CAST(IFNULL(FT.D, FT.D) AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            answer("SELECT (COALESCE(d, d))::NUMBER FROM ft"));
        assertEquals("SQL compilation error:|invalid type [CAST(DATE_ADDDAYSTODATE(1, FT.D) AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            answer("SELECT (DATEADD(day, 1, d))::NUMBER FROM ft"));
        assertEquals("SQL compilation error:|invalid type [CAST(DATE_ADDDAYSTODATE(1, FT.D) AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            answer("SELECT CAST((d + 1) AS NUMBER) FROM ft"));
        assertEquals("SQL compilation error:|invalid type [CAST(NEGATE(RE.A) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(-a AS DATE) FROM re"));
        assertEquals("SQL compilation error:|invalid type [CAST(-1 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(-1 AS DATE)"));
        assertEquals("SQL compilation error:|invalid type [CAST(ARRAY_CONSTRUCT(1, 2) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST([1,2] AS DATE)"));
    }

    /** TO_DATE's format beside a DATE echoes its argument as an invalid-type sentence does. */
    @Test
    public void aFormatOverANonTextSourceEchoesTheUnresolvedConversion() {
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_DATE(DATE_ADDDAYSTODATE(1, FT.D), 'YYYY-MM-DD')] expected 1, got 2",
            answer("SELECT TO_DATE(DATEADD(day, 1, d), 'YYYY-MM-DD') FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_DATE(IFNULL(FT.D, FT.D), 'YYYY-MM-DD')] expected 1, got 2",
            answer("SELECT TO_DATE(COALESCE(d, d), 'YYYY-MM-DD') FROM ft"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_DATE(TO_DATE('2020-01-15'), 'YYYY-MM-DD')] expected 1, got 2",
            answer("SELECT TO_DATE('2020-01-15'::DATE, 'YYYY-MM-DD')"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_DATE(TO_DATE('2020-01-15'), 'YYYY-MM-DD')] expected 1, got 2",
            answer("SELECT TO_DATE(TO_DATE('2020-01-15'), 'YYYY-MM-DD')"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_DATE(CAST('2020-01-15' AS DATE), 'YYYY-MM-DD', 1)] expected 2, got 3",
            answer("SELECT TO_DATE(TO_DATE('2020-01-15'), 'YYYY-MM-DD', 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_DATE(CAST('2020-01-15' AS DATE), 'YYYY-MM-DD', 1)] expected 2, got 3",
            answer("SELECT TO_DATE('2020-01-15'::DATE, 'YYYY-MM-DD', 1)"));
    }

    /** A cast onto its operand's own type is identity(x) in an invalid-type sentence, in every family but TIME and TIMESTAMP. */
    @Test
    public void aCastThatChangesNothingIsIdentityInAnInvalidTypeSentence() {
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FAM.D) AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            answer("SELECT CAST(d::DATE AS NUMBER) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FAM.D) IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(d::DATE IS NULL AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [TRY_CAST(identity(FAM.D))] for parameter 'TO_TIME'",
            answer("SELECT TRY_CAST(d::DATE AS TIME) FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_DATE(identity(FAM.D), 'YYYY-MM-DD')] expected 1, got 2",
            answer("SELECT TO_DATE(d::DATE, 'YYYY-MM-DD') FROM fam"));
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(identity(FAM.VEC))] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR(vec::VECTOR(FLOAT,3)) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FAM.VEC) IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(vec::VECTOR(FLOAT,3) IS NULL AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FAM.G) IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(g::VARCHAR IS NULL AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FAM.G) IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(g::VARCHAR(10) IS NULL AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FAM.G) IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(g::TEXT IS NULL AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FAM.G) IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(g::STRING IS NULL AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity('x') IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST('x'::VARCHAR IS NULL AS DATE)"));
        assertEquals("SQL compilation error:|invalid type [CAST(LENGTH(identity(FAM.G)) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(LENGTH(g::VARCHAR) AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST((identity(FAM.G)) = 'x' AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(g::VARCHAR = 'x' AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FAM.A) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(a::NUMBER(10,2) AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FAM.N) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(n::NUMBER(5,0) AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FAM.A) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(a::DECIMAL(10,2) AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FAM.A) IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(a::NUMBER(10,2) IS NULL AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [TO_DATE(identity(FAM.A))] for parameter 'TO_DATE'",
            answer("SELECT TO_DATE(a::NUMBER(10,2)) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [TRY_CAST(identity(FAM.A))] for parameter 'TO_DATE'",
            answer("SELECT TRY_CAST(a::NUMBER(10,2) AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FAM.F) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(f::FLOAT AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FAM.F) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(f::DOUBLE AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FAM.F) IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(f::FLOAT IS NULL AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [TO_DATE(identity(FAM.F))] for parameter 'TO_DATE'",
            answer("SELECT TO_DATE(f::FLOAT) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FAM.B) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(b::BOOLEAN AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FAM.B) IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(b::BOOLEAN IS NULL AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(TRUE) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(TRUE::BOOLEAN AS DATE)"));
        assertEquals("SQL compilation error:|invalid type [TO_DATE(identity(FAM.B))] for parameter 'TO_DATE'",
            answer("SELECT TO_DATE(b::BOOLEAN) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FAM.BN) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(bn::BINARY AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FAM.BN) AS BOOLEAN)] for parameter 'TO_BOOLEAN'",
            answer("SELECT CAST(bn::BINARY AS BOOLEAN) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FAM.BN) IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(bn::BINARY IS NULL AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FAM.B10) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(b10::BINARY(10) AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FAM.B10) IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(b10::BINARY(10) IS NULL AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FAM.B10) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(b10::BINARY AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FAM.V) IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(v::VARIANT IS NULL AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(TO_VECTOR(identity(FAM.V)))] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR(v::VARIANT::VECTOR(FLOAT,3)) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FAM.O) AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            answer("SELECT CAST(o::OBJECT AS NUMBER) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FAM.O) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(o::OBJECT AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FAM.O) IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(o::OBJECT IS NULL AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FAM.AR) AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            answer("SELECT CAST(ar::ARRAY AS NUMBER) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FAM.AR) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(ar::ARRAY AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(identity(FAM.AR) IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(ar::ARRAY IS NULL AS DATE) FROM fam"));
    }

    /** A TIME or TIMESTAMP cast onto its own type keeps its conversion function there. */
    @Test
    public void aTimeOrTimestampCastOntoItsOwnTypeKeepsItsConversionFunction() {
        assertEquals("SQL compilation error:|invalid type [CAST(TO_TIME(FAM.TM) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(tm::TIME AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_TIME(FAM.TM) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(tm::TIME(9) AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_TIME(FAM.TM) IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(tm::TIME IS NULL AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [TRY_CAST(TO_TIME(FAM.TM))] for parameter 'TO_DATE'",
            answer("SELECT TRY_CAST(tm::TIME AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_TIMESTAMP(FAM.TS) AS BOOLEAN)] for parameter 'TO_BOOLEAN'",
            answer("SELECT CAST(ts::TIMESTAMP_NTZ AS BOOLEAN) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_TIMESTAMP(FAM.TS) AS BOOLEAN)] for parameter 'TO_BOOLEAN'",
            answer("SELECT CAST(ts::TIMESTAMP_NTZ(9) AS BOOLEAN) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_TIMESTAMP(FAM.TS) AS BOOLEAN)] for parameter 'TO_BOOLEAN'",
            answer("SELECT CAST(ts::TIMESTAMP AS BOOLEAN) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_TIMESTAMP(FAM.TS) IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(ts::TIMESTAMP_NTZ IS NULL AS DATE) FROM fam"));
    }

    /**
     * A cast that changes a width or a TIMESTAMP flavour is named by its plan conversion there: a
     * narrower text is TEXT_TO_TEXT, another NUMBER precision or scale FIXED_TO_FIXED, and a TIMESTAMP
     * TO_TIMESTAMP over a DATE or a TIMESTAMP but its flavour's own conversion over a text.
     */
    @Test
    public void aWidthOrTimestampFlavourChangeIsNamedByItsPlanConversion() {
        assertEquals("SQL compilation error:|invalid type [CAST(TO_TIMESTAMP(FAM.TL) AS BOOLEAN)] for parameter 'TO_BOOLEAN'",
            answer("SELECT CAST(tl::TIMESTAMP_LTZ AS BOOLEAN) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_TIMESTAMP(FAM.TL) IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(tl::TIMESTAMP_LTZ IS NULL AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_TIMESTAMP(FAM.TZ) AS BOOLEAN)] for parameter 'TO_BOOLEAN'",
            answer("SELECT CAST(tz::TIMESTAMP_TZ AS BOOLEAN) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_TIMESTAMP(FAM.TZ) IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(tz::TIMESTAMP_TZ IS NULL AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(TEXT_TO_TEXT(FAM.G) IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(g::VARCHAR(5) IS NULL AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(FIXED_TO_FIXED(FAM.N AS NUMBER(38,0)[UNKNOWN]) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(n::INT AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_TIMESTAMP(FAM.D) AS BOOLEAN)] for parameter 'TO_BOOLEAN'",
            answer("SELECT CAST(d::TIMESTAMP_LTZ AS BOOLEAN) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_TIMESTAMP(FAM.D) AS BOOLEAN)] for parameter 'TO_BOOLEAN'",
            answer("SELECT CAST(d::TIMESTAMP_TZ AS BOOLEAN) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_TIMESTAMP(FAM.TS) AS BOOLEAN)] for parameter 'TO_BOOLEAN'",
            answer("SELECT CAST(ts::TIMESTAMP_LTZ AS BOOLEAN) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_TIMESTAMP(FAM.TS) AS BOOLEAN)] for parameter 'TO_BOOLEAN'",
            answer("SELECT CAST(ts::TIMESTAMP_TZ AS BOOLEAN) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_TIMESTAMP(FAM.TL) AS BOOLEAN)] for parameter 'TO_BOOLEAN'",
            answer("SELECT CAST(tl::TIMESTAMP_TZ AS BOOLEAN) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_TIMESTAMP(FAM.TZ) AS BOOLEAN)] for parameter 'TO_BOOLEAN'",
            answer("SELECT CAST(tz::TIMESTAMP_LTZ AS BOOLEAN) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_TIMESTAMP_LTZ(FAM.G) AS BOOLEAN)] for parameter 'TO_BOOLEAN'",
            answer("SELECT CAST(g::TIMESTAMP_LTZ AS BOOLEAN) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_TIMESTAMP_TZ(FAM.G) AS BOOLEAN)] for parameter 'TO_BOOLEAN'",
            answer("SELECT CAST(g::TIMESTAMP_TZ AS BOOLEAN) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_TIMESTAMP_LTZ('2020-01-01') AS BOOLEAN)] for parameter 'TO_BOOLEAN'",
            answer("SELECT CAST('2020-01-01'::TIMESTAMP_LTZ AS BOOLEAN)"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_TIMESTAMP(FAM.TL) AS BOOLEAN)] for parameter 'TO_BOOLEAN'",
            answer("SELECT CAST(tl::TIMESTAMP_LTZ(3) AS BOOLEAN) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_TIMESTAMP(FAM.TS) AS BOOLEAN)] for parameter 'TO_BOOLEAN'",
            answer("SELECT CAST(ts::TIMESTAMP_NTZ(3) AS BOOLEAN) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_TIMESTAMP_NTZ(FAM.G) AS BOOLEAN)] for parameter 'TO_BOOLEAN'",
            answer("SELECT CAST(g::TIMESTAMP_NTZ AS BOOLEAN) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(FIXED_TO_FIXED(FAM.N AS NUMBER(38,0)[UNKNOWN]) IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(n::INT IS NULL AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [TO_DATE(FIXED_TO_FIXED(FAM.N AS NUMBER(38,0)[UNKNOWN]))] for parameter 'TO_DATE'",
            answer("SELECT TO_DATE(n::INT) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(FIXED_TO_FIXED(FAM.N AS NUMBER(10,2)[UNKNOWN]) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(n::NUMBER(10,2) AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(FIXED_TO_FIXED(FAM.A AS NUMBER(12,2)[UNKNOWN]) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(a::NUMBER(12,2) AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(FIXED_TO_FIXED(FAM.A AS NUMBER(10,1)[UNKNOWN]) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(a::NUMBER(10,1) AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(FIXED_TO_FIXED(FAM.A AS NUMBER(38,0)[UNKNOWN]) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(a::INT AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(FIXED_TO_FIXED(1 AS NUMBER(38,0)[UNKNOWN]) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(1::INT AS DATE)"));
        assertEquals("SQL compilation error:|invalid type [CAST(FIXED_TO_FIXED(1.5 AS NUMBER(5,1)[UNKNOWN]) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(1.5::NUMBER(5,1) AS DATE)"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_NUMBER(FAM.F) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(f::NUMBER AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(LENGTH(TEXT_TO_TEXT(FAM.G)) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(LENGTH(g::VARCHAR(5)) AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST((TEXT_TO_TEXT(FAM.G)) = 'x' AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(g::VARCHAR(5) = 'x' AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(TEXT_TO_TEXT('abc') IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST('abc'::VARCHAR(2) IS NULL AS DATE)"));
        assertEquals("SQL compilation error:|invalid type [CAST(TEXT_TO_TEXT(FAM.G) IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(g::CHAR(5) IS NULL AS DATE) FROM fam"));
    }

    /**
     * Beside a format, TO_DATE's and TO_TIME's echo names a TIMESTAMP cast by its source: TO_TIMESTAMP over a
     * DATE or a TIMESTAMP, whatever the target's flavour, and the flavour's own conversion over a text or a
     * VARIANT.
     */
    @Test
    public void aTimestampCastBesideAFormatIsNamedByItsSource() {
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_DATE(TO_TIMESTAMP(FAM.TS), 'YYYY-MM-DD')] expected 1, got 2",
            answer("SELECT TO_DATE(ts::TIMESTAMP_NTZ, 'YYYY-MM-DD') FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_DATE(TO_TIMESTAMP(FAM.TS), 'YYYY-MM-DD')] expected 1, got 2",
            answer("SELECT TO_DATE(ts::TIMESTAMP_NTZ(3), 'YYYY-MM-DD') FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_DATE(TO_TIMESTAMP(FAM.TS), 'YYYY-MM-DD')] expected 1, got 2",
            answer("SELECT TO_DATE(ts::TIMESTAMP, 'YYYY-MM-DD') FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_DATE(TO_TIMESTAMP(FAM.TS), 'YYYY-MM-DD')] expected 1, got 2",
            answer("SELECT TO_DATE(ts::TIMESTAMP_LTZ, 'YYYY-MM-DD') FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_DATE(TO_TIMESTAMP(FAM.TL), 'YYYY-MM-DD')] expected 1, got 2",
            answer("SELECT TO_DATE(tl::TIMESTAMP_LTZ, 'YYYY-MM-DD') FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_DATE(TO_TIMESTAMP(FAM.TL), 'YYYY-MM-DD')] expected 1, got 2",
            answer("SELECT TO_DATE(tl::TIMESTAMP_TZ, 'YYYY-MM-DD') FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_DATE(TO_TIMESTAMP(FAM.TZ), 'YYYY-MM-DD')] expected 1, got 2",
            answer("SELECT TO_DATE(tz::TIMESTAMP_TZ, 'YYYY-MM-DD') FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_DATE(TO_TIMESTAMP(FAM.D), 'YYYY-MM-DD')] expected 1, got 2",
            answer("SELECT TO_DATE(d::TIMESTAMP_NTZ, 'YYYY-MM-DD') FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_DATE(TO_TIMESTAMP(FAM.D), 'YYYY-MM-DD')] expected 1, got 2",
            answer("SELECT TO_DATE(d::TIMESTAMP_LTZ, 'YYYY-MM-DD') FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_DATE(TO_TIMESTAMP(FAM.D), 'YYYY-MM-DD')] expected 1, got 2",
            answer("SELECT TO_DATE(d::TIMESTAMP_TZ, 'YYYY-MM-DD') FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_DATE(TO_TIMESTAMP_NTZ(FAM.G), 'YYYY-MM-DD')] expected 1, got 2",
            answer("SELECT TO_DATE(g::TIMESTAMP_NTZ, 'YYYY-MM-DD') FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_DATE(TO_TIMESTAMP_LTZ(FAM.G), 'YYYY-MM-DD')] expected 1, got 2",
            answer("SELECT TO_DATE(g::TIMESTAMP_LTZ, 'YYYY-MM-DD') FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_DATE(TO_TIMESTAMP_TZ(FAM.G), 'YYYY-MM-DD')] expected 1, got 2",
            answer("SELECT TO_DATE(g::TIMESTAMP_TZ, 'YYYY-MM-DD') FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_DATE(TO_TIMESTAMP_NTZ('2020-01-15 10:00:00'), 'YYYY-MM-DD')] expected 1, got 2",
            answer("SELECT TO_DATE('2020-01-15 10:00:00'::TIMESTAMP, 'YYYY-MM-DD')"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_DATE(TO_TIMESTAMP_NTZ('2020-01-15 10:00:00'), 'YYYY-MM-DD')] expected 1, got 2",
            answer("SELECT TO_DATE(CAST('2020-01-15 10:00:00' AS TIMESTAMP_NTZ), 'YYYY-MM-DD')"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_DATE(TO_TIMESTAMP_NTZ(FAM.V), 'YYYY-MM-DD')] expected 1, got 2",
            answer("SELECT TO_DATE(v::TIMESTAMP_NTZ, 'YYYY-MM-DD') FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_TIME(TO_TIMESTAMP(FAM.TS), 'HH24:MI:SS')] expected 1, got 2",
            answer("SELECT TO_TIME(ts::TIMESTAMP_NTZ, 'HH24:MI:SS') FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_TIME(TO_TIMESTAMP(FAM.D), 'HH24:MI:SS')] expected 1, got 2",
            answer("SELECT TO_TIME(d::TIMESTAMP_NTZ, 'HH24:MI:SS') FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_DATE(TO_DATE(FAM.TS), 'YYYY-MM-DD')] expected 1, got 2",
            answer("SELECT TO_DATE(ts::DATE, 'YYYY-MM-DD') FROM fam"));
    }
}
