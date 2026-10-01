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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The quoted-string interval literal's text is a small language of its own — comma-separated parts, each a
 * signed number and an optional unit word — read while the statement compiles. A token out of place is a
 * syntax error positioned inside the TEXT, ahead of any unit it names and ahead of the refusal a standalone
 * literal earns; a unit word the account does not know is refused by name. Every cell is the account's answer.
 */
public class IntervalStringTextTest extends BaseDatabaseTest {

    private static final String DAY = "'2024-03-09'::DATE";

    /** The first row's first cell, or the refusal. */
    private String answer(final String sql) {
        try {
            for (final Row row : engine.executeQuery(sql).getRows()) {
                return String.valueOf(row.getValue(0));
            }
            return "no row";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    private String shifted(final String text) {
        return answer("SELECT TO_VARCHAR(" + DAY + " + INTERVAL '" + text + "')");
    }

    private static String syntaxError(final int line, final int position, final String token) {
        return "SQL compilation error:\nsyntax error line " + line + " at position " + position + " unexpected '"
            + token + "'.";
    }

    /** Two parts with no comma between them: the second part's first token is the one out of place. */
    @Test
    public void aPartWithoutACommaIsASyntaxErrorInsideTheText() {
        assertEquals(syntaxError(1, 6, "2"), answer("SELECT TO_VARCHAR('2024-03-09 12:00:00'::TIMESTAMP_NTZ"
            + " + INTERVAL '1 day 2 hours')"));
        assertEquals(syntaxError(1, 8, "3"), shifted("2 hours 3 minutes"));
        assertEquals(syntaxError(1, 6, "2"), shifted("1 day 2"));
        assertEquals(syntaxError(1, 4, "2"), shifted("1 d 2 h"));
        assertEquals(syntaxError(1, 15, "3"), shifted("1 day, 2 hours 3 minutes"));
        assertEquals(syntaxError(1, 8, "2"), shifted("  1 day 2 hours"));
        assertEquals(syntaxError(1, 5, "2"), shifted("1day 2hours"));
        assertEquals(syntaxError(1, 6, "2"), shifted("1 xyz 2 hours"));
        assertEquals(syntaxError(2, 0, "2"), shifted("1 day\n2 hours"));
        assertEquals(syntaxError(1, 6, "2"), answer("SELECT TO_VARCHAR(" + DAY + " + INTERVAL '1 day 2 hours')"
            + " FROM (SELECT 1) WHERE FALSE"));
    }

    /** Every other token out of place is refused where it stands, the end of the text as {@code <EOF>}. */
    @Test
    public void anyTokenOutOfPlaceIsRefusedWhereItStands() {
        assertEquals(syntaxError(1, 6, "hours"), shifted("1 day hours"));
        assertEquals(syntaxError(1, 6, "-"), shifted("1 day -2 hours"));
        assertEquals(syntaxError(1, 0, "day"), shifted("day"));
        assertEquals(syntaxError(1, 0, "x"), shifted("x"));
        assertEquals(syntaxError(1, 2, "2"), shifted("1 2"));
        assertEquals(syntaxError(1, 7, "<EOF>"), shifted("1 days,"));
        assertEquals(syntaxError(1, 0, ","), shifted(",1 day"));
        assertEquals(syntaxError(1, 0, "<EOF>"), shifted(""));
        assertEquals(syntaxError(1, 6, ","), shifted("1 day,, 2 hours"));
        assertEquals(syntaxError(1, 5, ";"), shifted("1 day;"));
        assertEquals(syntaxError(1, 0, "1.5.5"), shifted("1.5.5 days"));
        assertEquals(syntaxError(1, 6, "2.5"), shifted("1 day 2.5 hours"));
        assertEquals(syntaxError(1, 7, "<EOF>"), shifted("--1 day"));
    }

    /** A unit word the account does not know is refused by name, as written, on one line. */
    @Test
    public void anUnknownUnitIsRefusedByName() {
        assertEquals("SQL compilation error: dayx is not recognized as a date type.", shifted("1 dayx"));
        assertEquals("SQL compilation error: d_ay is not recognized as a date type.", shifted("1 d_ay"));
        assertEquals("SQL compilation error: da1y is not recognized as a date type.", shifted("1 da1y"));
        assertEquals("SQL compilation error: \"day\" is not recognized as a date type.", shifted("1 \"day\""));
        assertEquals("SQL compilation error: xyz is not recognized as a date type.",
            answer("SELECT TO_VARCHAR(" + DAY + " + INTERVAL '1 xyz') FROM (SELECT 1) WHERE FALSE"));
    }

    /** A number may carry a fraction or an exponent, and rounds half away from zero to a whole count. */
    @Test
    public void aFractionalAmountRoundsToAWholeCount() {
        assertEquals("2024-03-11", shifted("1.5 days"));
        assertEquals("2024-03-10", shifted("1.4 days"));
        assertEquals("2024-03-12", shifted("2.5 days"));
        assertEquals("2024-03-07", shifted("-1.5 days"));
        assertEquals("2024-03-10", shifted(".5 days"));
        assertEquals("2024-03-14", shifted("5. days"));
        assertEquals("2024-06-17", shifted("1e2 days"));
        assertEquals("2024-03-09 02:00:00.000", answer("SELECT TO_VARCHAR('2024-03-09 00:00:00'::TIMESTAMP"
            + " + INTERVAL '1.5 hours')"));
        assertEquals("2024-03-09 00:00:02.000", answer("SELECT TO_VARCHAR('2024-03-09 00:00:00'::TIMESTAMP"
            + " + INTERVAL '1.5')"));
    }

    /** Signs, spaces, commas without spaces and bare numbers read as the account reads them. */
    @Test
    public void signsSpacesAndCommasRead() {
        assertEquals("2024-03-10", shifted("+1 day"));
        assertEquals("2024-03-10", shifted("+ 1 day"));
        assertEquals("2024-03-08", shifted("- 1 day"));
        assertEquals("2024-03-10", shifted("1 day "));
        assertEquals("2024-03-12", shifted(" 1 day , 2 days "));
        assertEquals("2024-03-10 02:00:00.000", shifted("1day,2hours"));
        assertEquals("2024-03-09 22:00:00.000", shifted("1 day,-2 hours"));
        assertEquals("2024-03-10 00:00:02.000", shifted("1 day,2"));
        assertEquals("2024-03-09 00:00:03.000", shifted("1,2"));
    }

    private static String unclosed(final int line, final int position) {
        return "parse error line " + line + " at position " + position + " near '<EOF>'.";
    }

    private static String syntaxLine(final int line, final int position, final String token) {
        return "syntax error line " + line + " at position " + position + " unexpected '" + token + "'.";
    }

    /** The text takes the statement's comments; one left open, like a quoted word left open, is refused at its end. */
    @Test
    public void commentsReadAsTheStatementReadsThem() {
        assertEquals("2024-03-10", shifted("1 /* c */ day"));
        assertEquals("2024-03-10", shifted("1 day // c"));
        assertEquals("2024-03-10", shifted("/* c */ 1 day"));
        assertEquals("2024-03-10", shifted("1/*c*/day"));
        assertEquals("2024-03-10", shifted("1 /* multi\nline */ day"));
        assertEquals("2024-03-10", shifted("1 day //"));
        assertEquals("2024-03-10 02:00:00.000", shifted("1 day /* c */, 2 hours"));
        assertEquals("2024-03-10 02:00:00.000", shifted("1 day // c\n, 2 hours"));
        assertEquals("2024-03-10 02:00:00.000", shifted("1 day -- c\n, 2 hours // d"));
        assertEquals(syntaxError(1, 14, "2"), shifted("1 day /* a */ 2 hours"));
        assertEquals(syntaxError(1, 14, "*"), shifted("1 day /* x */ */"));
        assertEquals(syntaxError(1, 11, "/"), shifted("1 day /*c*//"));
        assertEquals(syntaxError(1, 6, "/"), shifted("1 day / 2"));
        assertEquals(syntaxError(1, 2, "<EOF>"), shifted("//"));
        assertEquals(syntaxError(1, 5, "<EOF>"), shifted("/* */"));
        assertEquals("SQL compilation error:\n" + unclosed(1, 17), shifted("1 day /* unclosed"));
        assertEquals("SQL compilation error:\n" + unclosed(2, 11), shifted("1 day\n/* unclosed"));
        assertEquals("SQL compilation error:\n" + unclosed(1, 11), shifted("1 day /*/ c"));
        assertEquals("SQL compilation error:\n" + unclosed(1, 6), shifted("1 \"day"));
        assertEquals("SQL compilation error:\n" + unclosed(1, 11), shifted("1 dayx /* x"));
        assertEquals("SQL compilation error:\n" + unclosed(1, 4) + "\n" + syntaxLine(1, 4, "<EOF>"), shifted("/* x"));
        assertEquals("SQL compilation error:\n" + unclosed(1, 11) + "\n" + syntaxLine(1, 11, "<EOF>"),
            shifted("1 day, /* x"));
        assertEquals("SQL compilation error:\n" + syntaxLine(1, 0, "day") + "\n" + unclosed(1, 8), shifted("day /* x"));
        assertEquals("SQL compilation error:\n" + unclosed(1, 12) + "\n" + syntaxLine(1, 6, "2"),
            shifted("1 day 2 /* x"));
    }

    /** A reserved word of the statement is no unit but a token out of place; every other word names a unit. */
    @Test
    public void aReservedWordIsNoUnit() {
        assertEquals(syntaxError(1, 2, "select"), shifted("1 select"));
        assertEquals(syntaxError(1, 2, "SELECT"), shifted("1 SELECT"));
        assertEquals(syntaxError(1, 2, "Null"), shifted("1 Null"));
        assertEquals(syntaxError(1, 2, "from"), shifted("1 from"));
        assertEquals(syntaxError(1, 2, "to"), shifted("1 to"));
        assertEquals(syntaxError(1, 2, "qualify"), shifted("1 qualify"));
        assertEquals(syntaxError(1, 9, "from"), shifted("1 day, 2 from"));
        assertEquals(syntaxError(1, 0, "select"), shifted("select 1 day"));
        assertEquals("SQL compilation error:\n" + syntaxLine(1, 2, "select") + "\n" + unclosed(1, 13),
            shifted("1 select /* x"));
        assertEquals("SQL compilation error: case is not recognized as a date type.", shifted("1 case"));
        assertEquals("SQL compilation error: true is not recognized as a date type.", shifted("1 true"));
        assertEquals("SQL compilation error: except is not recognized as a date type.", shifted("1 except"));
        assertEquals("SQL compilation error: current_date is not recognized as a date type.", shifted("1 current_date"));
    }

    /** A quote out of place is echoed with each quote doubled, a pair of them as one token. */
    @Test
    public void aQuoteIsEchoedDoubled() {
        assertEquals(syntaxError(1, 2, "''"), shifted("1 ''day''"));
        assertEquals(syntaxError(1, 5, "''"), shifted("1 day''"));
        assertEquals(syntaxError(1, 0, "''"), shifted("''"));
        assertEquals(syntaxError(1, 2, "''''"), shifted("1 ''''"));
        assertEquals(syntaxError(1, 7, "''"), shifted("1 day, ''x''"));
        assertEquals(syntaxError(1, 6, "\""), shifted("1 day \""));
        assertEquals("SQL compilation error: \"\" is not recognized as a date type.", shifted("1 \"\""));
    }

    /**
     * An amount its unit cannot count is refused when a row reads it, signed as the operator applies it: a day, week,
     * month, quarter, year or hour amount stays under a billion, a minute, second or sub-second one under 10^18.
     */
    @Test
    public void anAmountItsUnitCannotCountIsRefusedWhenARowReadsIt() {
        engine.execute("CREATE OR REPLACE TABLE ist_empty (a INT)");
        final String narrow = "Number out of representable range: type FIXED[SB4](9,0){not null}, value ";
        final String wide = "Number out of representable range: type FIXED[SB8](18,0){not null}, value ";
        assertEquals("2739931-03-12", shifted("999999999 day"));
        assertEquals(narrow + "1e+09", shifted("1000000000 day"));
        assertEquals(narrow + "1.23457e+09", shifted("1234567890 day"));
        assertEquals(narrow + "-1e+09", shifted("-1000000000 day"));
        assertEquals(narrow + "1e+09", shifted("999999999.5 days"));
        assertEquals(narrow + "1e+09", shifted("1000000000 hours"));
        assertEquals(narrow + "1e+09", shifted("1000000000 months"));
        assertEquals(narrow + "1.5e+09", shifted("1.5e9 week"));
        assertEquals(narrow + "1.23457e+19", shifted("12345678901234567890 year"));
        assertEquals(narrow + "1e+30", shifted("1e30 day"));
        assertEquals("2055-11-16 01:46:40.000", shifted("1000000000 seconds"));
        assertEquals("5193-01-22 09:46:39.000", shifted("99999999999 second"));
        assertEquals("3925-07-06 10:40:00.000", shifted("1000000000 minutes"));
        assertEquals("2024-03-20 13:46:40.000", shifted("1000000000 ms"));
        assertEquals(wide + "1e+18", shifted("999999999999999999 second"));
        assertEquals(wide + "9.22337e+18", shifted("9223372036854775807 seconds"));
        assertEquals(wide + "1e+20", shifted("99999999999999999999"));
        assertEquals(wide + "1e+30", shifted("1e30 nanosecond"));
        assertEquals(narrow + "1e+30", shifted("1 day, 1e30 days"));
        assertEquals("SQL compilation error: dayx is not recognized as a date type.", shifted("1e30 days, 1 dayx"));
        assertEquals(narrow + "-1e+30",
            answer("SELECT TO_VARCHAR('2024-03-09'::TIMESTAMP_NTZ - INTERVAL '1e30 hours')"));
        assertEquals(narrow + "1e+30", answer("SELECT TO_VARCHAR('2024-03-09'::TIMESTAMP_LTZ + INTERVAL '1e30 days')"));
        assertEquals("no row", answer("SELECT TO_VARCHAR(" + DAY + " + INTERVAL '1e30 days') FROM ist_empty"));
    }

    /** A standalone literal's text is read before the literal is refused as standing alone. */
    @Test
    public void theTextIsReadBeforeAStandaloneLiteralIsRefused() {
        assertEquals(syntaxError(1, 6, "2"), answer("SELECT INTERVAL '1 day 2 hours'"));
        assertEquals("SQL compilation error: error line 0 at position -1\n: interval literal is not supported in"
            + " this form.", answer("SELECT INTERVAL '1 day, 2 hours'"));
    }
}
