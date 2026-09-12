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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * TO_DATE / TO_TIME / TO_TIMESTAMP* with an EXPLICIT format model, and their TRY_ twins, measured
 * cell by cell against a real account and pinned on both engines.
 *
 * <p>★ A MISMATCH IS THE ACCOUNT'S OWN SENTENCE — "Can't parse '&lt;input&gt;' as date|time|timestamp
 * with format '&lt;format&gt;'", input and model echoed verbatim (the input with its spaces, the model
 * with its quotes) — not java.time's "Text … could not be parsed at index 4". The TRY_ spellings
 * answer NULL instead.
 *
 * <p>★ THE MODEL IS APPLIED AS WRITTEN: only the word AUTO (in any case) means "detect", so the empty
 * string is a model that matches nothing, a string of digits is read as the model says
 * ('20200115' under YYYYMMDD is a date, not an epoch), TO_TIME honours its format, a missing field is
 * 1970-01-01 00:00:00, and a value no field can hold — month 13, February 30th, hour 24 — is refused.
 * The account's reader backtracks ('1152020' under MMDDYYYY is November 5th), matches elements and
 * names in any case, reads any number of fraction digits under FF, and ignores a day name under DY.
 *
 * <p>★ A NULL FORMAT IS A NULL ANSWER, whatever the input; a format that is not a STRING — a NUMBER, a
 * BOOLEAN, a DATE, a VARIANT, literal or column, or an untyped NULL under TO_DATE / TO_TIME — is the
 * compile-time "Format argument for function 'TO_DATE' needs to be a string" (the TRY_ twins name the
 * base conversion, the TIMESTAMP spellings their flavour: TO_TIMESTAMP says TO_TIMESTAMP_NTZ), while
 * the TIMESTAMP spellings answer NULL to an untyped NULL format.
 */
public class ExplicitFormatParseTest extends BaseDatabaseTest {

    @BeforeEach
    public void fixtures() {
        engine.execute("CREATE OR REPLACE TABLE tf (s VARCHAR(20), f VARCHAR(30))");
        engine.execute("INSERT INTO tf VALUES ('2020X01X15', 'YYYY\"T\"MM\"T\"DD')");
        engine.execute("CREATE OR REPLACE TABLE tn (s VARCHAR(30), f VARCHAR(30))");
        engine.execute("INSERT INTO tn VALUES ('2020-01-15', NULL)");
        engine.execute("CREATE OR REPLACE TABLE tt (n NUMBER, d DATE, v VARIANT, s VARCHAR)");
        engine.execute("INSERT INTO tt SELECT 1, '2020-01-15'::DATE, PARSE_JSON('\"YYYY-MM-DD\"'), '2020-01-15'");
    }

    private String text(final String sql) {
        final Object value = engine.executeQuery(sql).getRows().get(0).getValue(0);
        return value == null ? null : value.toString();
    }

    private String refusal(final String sql) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        return e.getMessage();
    }

    private static String cantParse(final String input, final String kind, final String format) {
        return "Can't parse '" + input + "' as " + kind + " with format '" + format + "'";
    }

    @Test
    public void aMismatchIsTheAccountsSentence() {
        assertEquals(cantParse("2020X01X15", "date", "YYYY\"T\"MM\"T\"DD"),
            refusal("SELECT TO_DATE('2020X01X15', 'YYYY\"T\"MM\"T\"DD')"));
        assertEquals(cantParse("2020-01-15", "date", "YYYY/MM/DD"),
            refusal("SELECT TO_DATE('2020-01-15', 'YYYY/MM/DD')"));
        assertEquals(cantParse("2020X01X15", "date", "YYYY\"T\"MM\"T\"DD"),
            refusal("SELECT TO_DATE(s, f) FROM tf"));
        assertEquals("2020-01-15", text("SELECT TO_DATE('2020T01T15', 'YYYY\"T\"MM\"T\"DD')"));
        assertEquals(cantParse("10:00:00", "time", "HH24-MI-SS"),
            refusal("SELECT TO_TIME('10:00:00', 'HH24-MI-SS')"));
        assertEquals(cantParse("abc", "time", "HH24:MI:SS"), refusal("SELECT TO_TIME('abc', 'HH24:MI:SS')"));
        assertEquals(cantParse("2020-01-15", "timestamp", "YYYY/MM/DD HH24:MI:SS"),
            refusal("SELECT TO_TIMESTAMP('2020-01-15', 'YYYY/MM/DD HH24:MI:SS')"));
        assertEquals(cantParse("2020-01-15", "timestamp", "YYYY/MM/DD"),
            refusal("SELECT TO_TIMESTAMP_NTZ('2020-01-15', 'YYYY/MM/DD')"));
        assertEquals(cantParse("2020-01-15", "timestamp", "YYYY/MM/DD"),
            refusal("SELECT TO_TIMESTAMP_LTZ('2020-01-15', 'YYYY/MM/DD')"));
        assertEquals(cantParse("2020-01-15", "timestamp", "YYYY/MM/DD"),
            refusal("SELECT TO_TIMESTAMP_TZ('2020-01-15', 'YYYY/MM/DD')"));
        // An unknown element is literal text, a trailing separator or a trailing space in the model
        // is required, extra input text and short input are refused, and the input is echoed with
        // the spaces it came with.
        assertEquals(cantParse("2020-01-15", "date", "ZZZZ"), refusal("SELECT TO_DATE('2020-01-15', 'ZZZZ')"));
        assertEquals(cantParse("2020-01-15", "date", "YYYY-MM-DD-"),
            refusal("SELECT TO_DATE('2020-01-15', 'YYYY-MM-DD-')"));
        assertEquals(cantParse("2020-01-15", "date", "YYYY-MM-DD "),
            refusal("SELECT TO_DATE('2020-01-15', 'YYYY-MM-DD ')"));
        assertEquals(cantParse("2020-01-15 extra", "date", "YYYY-MM-DD"),
            refusal("SELECT TO_DATE('2020-01-15 extra', 'YYYY-MM-DD')"));
        assertEquals(cantParse("2020-01", "date", "YYYY-MM-DD"), refusal("SELECT TO_DATE('2020-01', 'YYYY-MM-DD')"));
        assertEquals(cantParse(" 2020X01X15 ", "date", "YYYY-MM-DD"),
            refusal("SELECT TO_DATE(' 2020X01X15 ', 'YYYY-MM-DD')"));
        assertEquals(cantParse("", "date", "YYYY-MM-DD"), refusal("SELECT TO_DATE('', 'YYYY-MM-DD')"));
        assertEquals(cantParse("", "timestamp", "YYYY"), refusal("SELECT TO_TIMESTAMP('', 'YYYY')"));
        assertEquals(cantParse("", "time", "HH24"), refusal("SELECT TO_TIME('', 'HH24')"));
        // A value no field can hold.
        assertEquals(cantParse("2020-13-15", "date", "YYYY-MM-DD"),
            refusal("SELECT TO_DATE('2020-13-15', 'YYYY-MM-DD')"));
        assertEquals(cantParse("2020-02-30", "date", "YYYY-MM-DD"),
            refusal("SELECT TO_DATE('2020-02-30', 'YYYY-MM-DD')"));
        assertEquals(cantParse("25:00:00", "time", "HH24:MI:SS"), refusal("SELECT TO_TIME('25:00:00', 'HH24:MI:SS')"));
        assertEquals(cantParse("24:00:00", "time", "HH24:MI:SS"), refusal("SELECT TO_TIME('24:00:00', 'HH24:MI:SS')"));
        assertEquals(cantParse("10:00:60", "time", "HH24:MI:SS"), refusal("SELECT TO_TIME('10:00:60', 'HH24:MI:SS')"));
        assertEquals(cantParse("13:30", "time", "HH12:MI"), refusal("SELECT TO_TIME('13:30', 'HH12:MI')"));
        assertEquals(cantParse("-2020-01-15", "date", "YYYY-MM-DD"),
            refusal("SELECT TO_DATE('-2020-01-15', 'YYYY-MM-DD')"));
        // The AUTO readings keep their own sentences.
        assertEquals("Date '2020X01X15' is not recognized", refusal("SELECT TO_DATE('2020X01X15')"));
        assertEquals("Time '10-00-00' is not recognized", refusal("SELECT TO_TIME('10-00-00')"));
        assertEquals("Timestamp '2020X01X15' is not recognized", refusal("SELECT TO_TIMESTAMP('2020X01X15')"));
    }

    @Test
    public void theTryFormsAnswerNull() {
        assertNull(text("SELECT TRY_TO_DATE('2020X01X15', 'YYYY\"T\"MM\"T\"DD')"));
        assertNull(text("SELECT TRY_TO_TIME('10:00:00', 'HH24-MI-SS')"));
        assertNull(text("SELECT TRY_TO_TIMESTAMP('2020-01-15', 'YYYY/MM/DD HH24:MI:SS')"));
        assertNull(text("SELECT TRY_TO_DATE('2020-01-15', '')"));
        assertNull(text("SELECT TRY_TO_TIMESTAMP('2020-01-15', '')"));
        assertNull(text("SELECT TRY_TO_TIME('10:00:00', '')"));
        assertEquals("10:00:00", text("SELECT TRY_TO_TIME('10-00-00', 'HH24-MI-SS')::VARCHAR"));
        assertEquals("2021-01-15", text("SELECT TRY_TO_DATE('01/15/2021', 'MM/DD/YYYY')"));
    }

    @Test
    public void anEmptyFormatIsAModelThatMatchesNothing() {
        assertEquals(cantParse("2020-01-15", "date", ""), refusal("SELECT TO_DATE('2020-01-15', '')"));
        assertEquals(cantParse("2020X01X15", "date", ""), refusal("SELECT TO_DATE('2020X01X15', '')"));
        assertEquals(cantParse("10:00:00", "time", ""), refusal("SELECT TO_TIME('10:00:00', '')"));
        assertEquals(cantParse("2020-01-15 10:00:00", "timestamp", ""),
            refusal("SELECT TO_TIMESTAMP('2020-01-15 10:00:00', '')"));
        assertEquals(cantParse("2020-01-15 10:00:00", "timestamp", ""),
            refusal("SELECT TO_TIMESTAMP_NTZ('2020-01-15 10:00:00', '')"));
        // … which an empty input, or one that is exactly the model's literal text, does match — and
        // then every field is missing.
        assertEquals("1970-01-01", text("SELECT TO_DATE('', '')"));
        assertEquals("1970-01-01", text("SELECT TO_DATE('abc', '\"abc\"')"));
    }

    @Test
    public void autoIsTheOnlySpellingThatIsNotAModel() {
        assertEquals("2020-01-15", text("SELECT TO_DATE('2020-01-15', 'auto')"));
        assertEquals("2021-09-15", text("SELECT TO_DATE('1631711999', 'AUTO')"));
        assertEquals("2021-09-15", text("SELECT TO_DATE('1631711999', 'Auto')"));
        assertEquals("2020-01-15 10:00:00.000", text("SELECT TO_TIMESTAMP('2020-01-15 10:00:00', 'auto')::VARCHAR"));
        assertEquals("2021-09-15 13:19:59.000", text("SELECT TO_TIMESTAMP('1631711999', 'AUTO')::VARCHAR"));
        assertEquals("10:00:00", text("SELECT TO_TIME('10:00:00', 'AUTO')::VARCHAR"));
        assertEquals("10:00:00", text("SELECT TO_TIME('10:00:00', 'auto')::VARCHAR"));
        assertEquals(cantParse("2020-01-15", "date", " AUTO"), refusal("SELECT TO_DATE('2020-01-15', ' AUTO')"));
    }

    @Test
    public void theModelReadsDigitsAsItSaysAndBacktracks() {
        assertEquals("2020-01-15", text("SELECT TO_DATE('20200115', 'YYYYMMDD')"));
        assertEquals("2020-01-15 00:00:00.000", text("SELECT TO_TIMESTAMP('20200115', 'YYYYMMDD')::VARCHAR"));
        assertEquals(cantParse("1631711999", "date", "YYYYMMDD"), refusal("SELECT TO_DATE('1631711999', 'YYYYMMDD')"));
        assertEquals(cantParse("1631711999", "timestamp", "YYYYMMDD"),
            refusal("SELECT TO_TIMESTAMP('1631711999', 'YYYYMMDD')"));
        // Natural widths first, digits given back when the rest cannot match.
        assertEquals("2020-11-05", text("SELECT TO_DATE('2020115', 'YYYYMMDD')"));
        assertEquals("2020-11-05", text("SELECT TO_DATE('1152020', 'MMDDYYYY')"));
        assertEquals("2020-01-15", text("SELECT TO_DATE('01152020', 'MMDDYYYY')"));
        assertEquals("2020-01-05", text("SELECT TO_DATE('2020-1-5', 'YYYY-MM-DD')"));
        assertEquals("0202-01-15", text("SELECT TO_DATE('202-01-15', 'YYYY-MM-DD')"));
        // A two-digit year is 1970–2069.
        assertEquals("1970-01-15", text("SELECT TO_DATE('70-01-15', 'YY-MM-DD')"));
        assertEquals("2069-01-15", text("SELECT TO_DATE('69-01-15', 'YY-MM-DD')"));
        assertEquals("2000-01-15", text("SELECT TO_DATE('00-01-15', 'YY-MM-DD')"));
        // A repeated element keeps the last value.
        assertEquals("2021-01-15", text("SELECT TO_DATE('2020-2021-01-15', 'YYYY-YYYY-MM-DD')"));
    }

    @Test
    public void toTimeAppliesItsFormat() {
        assertEquals("10:00:00", text("SELECT TO_TIME('10-00-00', 'HH24-MI-SS')::VARCHAR"));
        assertEquals("10:00:00", text("SELECT TO_TIME('10-00-00', 'hh24-mi-ss')::VARCHAR"));
        assertEquals("10:05:00", text("SELECT TO_TIME('10:05', 'HH24:MI')::VARCHAR"));
        assertEquals("22:00:00", text("SELECT TO_TIME('10:00 PM', 'HH12:MI AM')::VARCHAR"));
        assertEquals("22:00:00", text("SELECT TO_TIME('10:00 pm', 'HH12:MI AM')::VARCHAR"));
        assertEquals("10:00:00", text("SELECT TO_TIME('10:00 AM', 'HH12:MI PM')::VARCHAR"));
        assertEquals("00:30:00", text("SELECT TO_TIME('12:30 AM', 'HH12:MI AM')::VARCHAR"));
        assertEquals("12:30:00", text("SELECT TO_TIME('12:30 PM', 'HH12:MI AM')::VARCHAR"));
        // PM lifts an hour below twelve under HH24 too; AM leaves HH24 alone.
        assertEquals("22:00:00", text("SELECT TO_TIME('10:00:00 PM', 'HH24:MI:SS AM')::VARCHAR"));
        assertEquals("12:30:00", text("SELECT TO_TIME('12:30 AM', 'HH24:MI AM')::VARCHAR"));
        assertEquals("13:30:00", text("SELECT TO_TIME('13:30 PM', 'HH24:MI AM')::VARCHAR"));
        assertEquals("10:00:00", text("SELECT TO_TIME('10:00', 'HH12:MI')::VARCHAR"));
        assertEquals("10:00:00.123000000",
            text("SELECT TO_CHAR(TO_TIME('10:00:00.123', 'HH24:MI:SS.FF'), 'HH24:MI:SS.FF')"));
        // Date elements are read and dropped; a model with no clock element is midnight.
        assertEquals("10:00:00", text("SELECT TO_TIME('2020-01-15 10:00:00', 'YYYY-MM-DD HH24:MI:SS')::VARCHAR"));
        assertEquals("00:00:00", text("SELECT TO_TIME('2020-01-15', 'YYYY-MM-DD')::VARCHAR"));
    }

    @Test
    public void aMissingFieldIsTheEpochOrigin() {
        assertEquals("1970-01-15", text("SELECT TO_DATE('15', 'DD')"));
        assertEquals("2020-01-01", text("SELECT TO_DATE('2020', 'YYYY')"));
        assertEquals("1970-01-01", text("SELECT TO_DATE('10:00:00', 'HH24:MI:SS')"));
        assertEquals("1970-01-01 10:00:00.000", text("SELECT TO_TIMESTAMP('10:00:00', 'HH24:MI:SS')::VARCHAR"));
        assertEquals("2020-01-15", text("SELECT TO_DATE('2020-01-15 10:00:00', 'YYYY-MM-DD HH24:MI:SS')"));
        assertEquals("2020-01-15", text("SELECT TO_DATE('2020-01-15 10:00:00.123', 'YYYY-MM-DD HH24:MI:SS.FF')"));
    }

    @Test
    public void elementsNamesAndSpacesMatchLoosely() {
        assertEquals("2020-01-15", text("SELECT TO_DATE('2020-01-15', 'yyyy-mm-dd')"));
        assertEquals("2020-01-15", text("SELECT TO_DATE('2020-01-15', 'Yyyy-Mm-Dd')"));
        assertEquals("2020-01-15 10:00:00.000",
            text("SELECT TO_TIMESTAMP('2020-01-15 10:00:00', 'yyyy-mm-dd hh24:mi:ss')::VARCHAR"));
        assertEquals(cantParse("2020-01-15", "date", "yyyy/mm/dd"), refusal("SELECT TO_DATE('2020-01-15', 'yyyy/mm/dd')"));
        // Month and day names: any case, either length under either element; a day name is ignored.
        assertEquals("2020-01-15", text("SELECT TO_DATE('15 JAN 2020', 'DD MON YYYY')"));
        assertEquals("2020-01-15", text("SELECT TO_DATE('15 jan 2020', 'DD MON YYYY')"));
        assertEquals("2020-01-15", text("SELECT TO_DATE('15 jAN 2020', 'DD MON YYYY')"));
        assertEquals("2020-01-15", text("SELECT TO_DATE('15 January 2020', 'DD MON YYYY')"));
        assertEquals("2020-01-15", text("SELECT TO_DATE('15 Jan 2020', 'DD MMMM YYYY')"));
        assertEquals("2020-01-15", text("SELECT TO_DATE('15 January 2020', 'DD MMMM YYYY')"));
        assertEquals("2020-01-15", text("SELECT TO_DATE('Wed 2020-01-15', 'DY YYYY-MM-DD')"));
        assertEquals("2020-01-15", text("SELECT TO_DATE('Wednesday 2020-01-15', 'DY YYYY-MM-DD')"));
        assertEquals("2020-01-15", text("SELECT TO_DATE('Mon 2020-01-15', 'DY YYYY-MM-DD')"));
        assertEquals(cantParse("15  2020", "date", "DD MON YYYY"), refusal("SELECT TO_DATE('15  2020', 'DD MON YYYY')"));
        // A T separator, quoted or bare.
        assertEquals("2020-01-15 10:00:00.000",
            text("SELECT TO_TIMESTAMP('2020-01-15T10:00:00', 'YYYY-MM-DD\"T\"HH24:MI:SS')::VARCHAR"));
        assertEquals("2020-01-15 10:00:00.000",
            text("SELECT TO_TIMESTAMP('2020-01-15T10:00:00', 'YYYY-MM-DDTHH24:MI:SS')::VARCHAR"));
        // A space in the model is optional in the input except at the very end; the input's own
        // leading and trailing spaces are forgiven, inner ones are not.
        assertEquals("2020-01-15", text("SELECT TO_DATE('2020-01-15', 'YYYY - MM - DD')"));
        assertEquals("2020-01-15", text("SELECT TO_DATE('2020-01-15 ', 'YYYY-MM-DD ')"));
        assertEquals("2020-01-15", text("SELECT TO_DATE(' 2020-01-15', 'YYYY-MM-DD')"));
        assertEquals("2020-01-15", text("SELECT TO_DATE('2020-01-15 ', 'YYYY-MM-DD')"));
        assertEquals(cantParse("2020 - 01 - 15", "date", "YYYY-MM-DD"),
            refusal("SELECT TO_DATE('2020 - 01 - 15', 'YYYY-MM-DD')"));
    }

    @Test
    public void fractionsAreReadAtAnyWidth() {
        assertEquals("2020-01-15 10:00:00.100",
            text("SELECT TO_TIMESTAMP('2020-01-15 10:00:00.1', 'YYYY-MM-DD HH24:MI:SS.FF')::VARCHAR"));
        assertEquals("2020-01-15 10:00:00.123",
            text("SELECT TO_TIMESTAMP('2020-01-15 10:00:00.123456789', 'YYYY-MM-DD HH24:MI:SS.FF')::VARCHAR"));
        assertEquals("2020-01-15 10:00:00.100",
            text("SELECT TO_TIMESTAMP('2020-01-15 10:00:00.1', 'YYYY-MM-DD HH24:MI:SS.FF3')::VARCHAR"));
        assertEquals("2020-01-15 10:00:00.123",
            text("SELECT TO_TIMESTAMP('2020-01-15 10:00:00.123456', 'YYYY-MM-DD HH24:MI:SS.FF3')::VARCHAR"));
        assertEquals("2020-01-15 10:00:00.000",
            text("SELECT TO_TIMESTAMP('2020-01-15 10:00:00.', 'YYYY-MM-DD HH24:MI:SS.FF')::VARCHAR"));
        // The first nine digits are kept, the rest are read past.
        assertEquals("10:00:00.123456789",
            text("SELECT TO_CHAR(TO_TIME('10:00:00.1234567891', 'HH24:MI:SS.FF'), 'HH24:MI:SS.FF')"));
        assertEquals("10:00:00.120000000",
            text("SELECT TO_CHAR(TO_TIME('10:00:00.12', 'HH24:MI:SS.FF'), 'HH24:MI:SS.FF')"));
    }

    @Test
    public void offsetsAndZoneNamesAreRead() {
        assertEquals("2020-01-15 10:00:00.000 +0530",
            text("SELECT TO_TIMESTAMP_TZ('2020-01-15 10:00:00 +0530', 'YYYY-MM-DD HH24:MI:SS TZHTZM')::VARCHAR"));
        assertEquals("2020-01-15 10:00:00.000 +0530",
            text("SELECT TO_TIMESTAMP_TZ('2020-01-15 10:00:00 +05:30', 'YYYY-MM-DD HH24:MI:SS TZH:TZM')::VARCHAR"));
        assertEquals("2020-01-15 10:00:00.000 Z",
            text("SELECT TO_TIMESTAMP_TZ('2020-01-15 10:00:00 Z', 'YYYY-MM-DD HH24:MI:SS TZH:TZM')::VARCHAR"));
        assertEquals("2020-01-15 10:00:00.000 Z",
            text("SELECT TO_TIMESTAMP_TZ('2020-01-15 10:00:00 UTC', 'YYYY-MM-DD HH24:MI:SS TZD')::VARCHAR"));
        assertEquals("2020-01-15 10:00:00.000 -0800",
            text("SELECT TO_TIMESTAMP_TZ('2020-01-15 10:00:00 PST', 'YYYY-MM-DD HH24:MI:SS TZD')::VARCHAR"));
        // An NTZ reads the offset and drops it; a model without an offset element refuses one.
        assertEquals("2020-01-15 10:00:00.000",
            text("SELECT TO_TIMESTAMP_NTZ('2020-01-15 10:00:00 +0530', 'YYYY-MM-DD HH24:MI:SS TZHTZM')::VARCHAR"));
        assertEquals(cantParse("2020-01-15 10:00:00 +0530", "timestamp", "YYYY-MM-DD HH24:MI:SS"),
            refusal("SELECT TO_TIMESTAMP_LTZ('2020-01-15 10:00:00 +0530', 'YYYY-MM-DD HH24:MI:SS')"));
    }

    @Test
    public void aNullFormatIsANullAnswer() {
        assertNull(text("SELECT TO_DATE('2020-01-15', NULL::VARCHAR)"));
        assertNull(text("SELECT TO_TIME('10:00:00', NULL::VARCHAR)"));
        assertNull(text("SELECT TO_DATE(s, f) FROM tn"));
        assertNull(text("SELECT TO_TIMESTAMP(s, f) FROM tn"));
        assertNull(text("SELECT TO_TIME('10:00:00', f) FROM tn"));
        // The TIMESTAMP spellings answer NULL to an untyped NULL as well …
        assertNull(text("SELECT TO_TIMESTAMP('2020-01-15', NULL)"));
        assertNull(text("SELECT TO_TIMESTAMP_NTZ('2020-01-15', NULL)"));
        assertNull(text("SELECT TO_TIMESTAMP_LTZ('2020-01-15', NULL)"));
        assertNull(text("SELECT TO_TIMESTAMP_TZ('2020-01-15', NULL)"));
        assertNull(text("SELECT TRY_TO_TIMESTAMP('2020-01-15', NULL)"));
        // … where TO_DATE and TO_TIME refuse it at compile time.
        final String date = "SQL compilation error:\nFormat argument for function 'TO_DATE' needs to be a string";
        final String time = "SQL compilation error:\nFormat argument for function 'TO_TIME' needs to be a string";
        assertEquals(date, refusal("SELECT TO_DATE('2020-01-15', NULL)"));
        assertEquals(date, refusal("SELECT TO_DATE('2020X01X15', NULL)"));
        assertEquals(date, refusal("SELECT TRY_TO_DATE('2020-01-15', NULL)"));
        assertEquals(time, refusal("SELECT TO_TIME('10:00:00', NULL)"));
        assertEquals(time, refusal("SELECT TRY_TO_TIME('10:00:00', NULL)"));
    }

    @Test
    public void aFormatThatIsNotAStringIsRefusedAtCompileTime() {
        final String date = "SQL compilation error:\nFormat argument for function 'TO_DATE' needs to be a string";
        final String time = "SQL compilation error:\nFormat argument for function 'TO_TIME' needs to be a string";
        final String ntz = "SQL compilation error:\nFormat argument for function 'TO_TIMESTAMP_NTZ' needs to be a string";
        assertEquals(date, refusal("SELECT TO_DATE('2020-01-15', 123)"));
        assertEquals(date, refusal("SELECT TO_DATE('2020-01-15', TRUE)"));
        assertEquals(date, refusal("SELECT TO_DATE('2020-01-15', CURRENT_DATE)"));
        assertEquals(date, refusal("SELECT TO_DATE('2020-01-15', PARSE_JSON('\"YYYY-MM-DD\"'))"));
        assertEquals(date, refusal("SELECT TO_DATE(s, n) FROM tt"));
        assertEquals(date, refusal("SELECT TO_DATE(s, d) FROM tt"));
        assertEquals(date, refusal("SELECT TO_DATE(s, v) FROM tt"));
        assertEquals(date, refusal("SELECT TRY_TO_DATE('2020-01-15', 123)"));
        assertEquals(time, refusal("SELECT TO_TIME('10:00:00', 123)"));
        assertEquals(time, refusal("SELECT TRY_TO_TIME('10:00:00', 123)"));
        assertEquals(ntz, refusal("SELECT TO_TIMESTAMP('2020-01-15', 3)"));
        assertEquals(ntz, refusal("SELECT TO_TIMESTAMP('2020-01-15', TRUE)"));
        assertEquals(ntz, refusal("SELECT TO_TIMESTAMP_NTZ('2020-01-15', 3)"));
        assertEquals(ntz, refusal("SELECT TRY_TO_TIMESTAMP('2020-01-15', 3)"));
        assertEquals(ntz, refusal("SELECT TO_TIMESTAMP(s, n) FROM tt"));
        assertEquals("SQL compilation error:\nFormat argument for function 'TO_TIMESTAMP_LTZ' needs to be a string",
            refusal("SELECT TO_TIMESTAMP_LTZ('2020-01-15', 3)"));
        assertEquals("SQL compilation error:\nFormat argument for function 'TO_TIMESTAMP_TZ' needs to be a string",
            refusal("SELECT TO_TIMESTAMP_TZ('2020-01-15', 3)"));
        // The rule is compile-time: a view over it is refused, not created.
        assertEquals(date, refusal("CREATE OR REPLACE VIEW v_fmt AS SELECT TO_DATE('2020-01-15', 123) AS d"));
        // A numeric SOURCE with a format is the conversion matrix's own sentence, first.
        assertEquals("SQL compilation error:\ninvalid type [TO_DATE(20200115, 'YYYYMMDD')] for parameter 'TO_DATE'",
            refusal("SELECT TO_DATE(20200115, 'YYYYMMDD')"));
    }
}
