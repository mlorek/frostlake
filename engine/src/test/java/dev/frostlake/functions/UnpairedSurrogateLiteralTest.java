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
 * A {@code \}u escape that builds HALF a character. {@code '\}u{@code D800'} is a high surrogate with
 * nothing after it, and Frostlake used to hand it back — a lone surrogate that renders as a replacement
 * character. Live refuses it in the string-literal READER:
 *
 * <pre>
 *   '\uD800'   Invalid Unicode string literal; high surrogate '\uD800' must be followed by a low
 *              surrogate ('\uDC00'-'\uDFFF').
 *   '\uDC00'   Invalid Unicode string literal; low surrogate '\uDC00' must be preceded by a high
 *              surrogate ('\uD800'-'\uDBFF').
 * </pre>
 *
 * <p>THE SENTENCE STANDS ALONE: no compilation-error heading and no place, wherever the literal sits — at seven
 * different offsets and onto a second line alike.
 *
 * <p>THE FIRST offending code unit decides, scanning left to right — {@code '\uDC00\uD800'} is two
 * unpaired halves and reports the LOW one. The echo is always four UPPER-case hex digits whatever case
 * was written, and it carries the offending unit's own value.
 *
 * <p>WHAT MUST KEEP WORKING: a correctly PAIRED surrogate, which is how an emoji is written; every
 * ordinary escape; and a DOLLAR-QUOTED string, whose reader processes no escapes at all and so keeps
 * the backslash and the u as text.
 *
 * <p>This is also the only route measured that could feed invalid UTF-8 into VALIDATE_UTF8 /
 * TRY_VALIDATE_UTF8, and closing it is why those two cannot be told apart on any reachable input — see
 * {@code functions/ValidateUtf8Test}.
 */
public class UnpairedSurrogateLiteralTest extends BaseDatabaseTest {

    private static final String HIGH = "'\\uD800'";
    private static final String LOW = "'\\uDC00'";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE pu (i INT)");
        engine.execute("INSERT INTO pu VALUES (1)");
    }

    /** The answer's characters as hex code units, or the refusal. */
    private String outcome(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            if (!rs.next()) {
                return "<no rows>";
            }
            final String text = String.valueOf(rs.getValue(0));
            final StringBuilder codes = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                if (i > 0) {
                    codes.append(" ");
                }
                codes.append(Integer.toHexString(text.charAt(i)));
            }
            return codes.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String highRefusal(final String escape) {
        return "Invalid Unicode string literal; high surrogate '" + escape
            + "' must be followed by a low surrogate ('\\uDC00'-'\\uDFFF').";
    }

    private String lowRefusal(final String escape) {
        return "Invalid Unicode string literal; low surrogate '" + escape
            + "' must be preceded by a high surrogate ('\\uD800'-'\\uDBFF').";
    }

    /** Each half has its own sentence. */
    @Test
    public void eachHalfHasItsOwnSentence() {
        assertEquals(highRefusal("\\uD800"), outcome("SELECT " + HIGH));
        assertEquals(lowRefusal("\\uDC00"), outcome("SELECT " + LOW));
        assertEquals(highRefusal("\\uD800"), outcome("SELECT " + HIGH + " FROM pu"));
    }

    /** The FIRST offending unit decides, and the echo carries its own value. */
    @Test
    public void theFirstOffendingUnitDecides() {
        assertEquals(highRefusal("\\uD800"), outcome("SELECT '\\uD800\\uD800'"),
            "two highs — the first one is named");
        assertEquals(lowRefusal("\\uDC00"), outcome("SELECT '\\uDC00\\uD800'"),
            "a LOW first, so the low sentence wins");
        assertEquals(highRefusal("\\uD800"), outcome("SELECT '\\uD800A'"));
        assertEquals(highRefusal("\\uD800"), outcome("SELECT 'A\\uD800'"),
            "and text before it changes nothing");
        assertEquals(highRefusal("\\uD8FF"), outcome("SELECT '\\uD8FF'"));
        assertEquals(lowRefusal("\\uDFFF"), outcome("SELECT '\\uDFFF'"));
    }

    /** The echo is UPPER-case hex however the escape was written. */
    @Test
    public void theEchoIsAlwaysUpperCase() {
        assertEquals(highRefusal("\\uD800"), outcome("SELECT '\\ud800'"));
        assertEquals(highRefusal("\\uD8FF"), outcome("SELECT '\\ud8Ff'"));
    }

    /** No place is named wherever the literal is written, onto a second line included. */
    @Test
    public void theSentenceNamesNoPlace() {
        assertEquals(highRefusal("\\uD800"),
            outcome("SELECT VALIDATE_UTF8(" + HIGH + ") FROM pu"));
        assertEquals(highRefusal("\\uD800"),
            outcome("SELECT TRY_VALIDATE_UTF8(" + HIGH + ") FROM pu"),
            "the call around it names no place either");
        assertEquals(highRefusal("\\uD800"), outcome("SELECT 1, " + HIGH));
        assertEquals(highRefusal("\\uD800"),
            outcome("SELECT i FROM pu WHERE " + HIGH + " = 'x'"));
        assertEquals(highRefusal("\\uD800"), outcome("SELECT 'ab' || " + HIGH));
        assertEquals(highRefusal("\\uD800"), outcome("SELECT 1,\n  " + HIGH), "nor does a second line");
    }

    /** A correctly PAIRED surrogate is untouched — this is how an emoji is written. */
    @Test
    public void aPairedSurrogateStillReads() {
        assertEquals("d83d de00", outcome("SELECT '\\uD83D\\uDE00'"));
        assertEquals("61 d83d de00 62", outcome("SELECT 'a\\uD83D\\uDE00b'"));
    }

    /** Every ordinary escape is untouched. */
    @Test
    public void theOrdinaryEscapesAreUntouched() {
        assertEquals("41", outcome("SELECT '\\u0041'"));
        assertEquals("a", outcome("SELECT '\\n'"));
        assertEquals("5c", outcome("SELECT '\\\\'"));
        assertEquals("61 62 63", outcome("SELECT 'abc'"));
    }

    /** A DOLLAR-QUOTED string processes no escapes, so the same text is just text there. */
    @Test
    public void aDollarQuotedStringKeepsTheEscapeAsText() {
        assertEquals("5c 75 44 38 30 30", outcome("SELECT $$\\uD800$$"));
        assertEquals("5c 75 44 38 33 44 5c 75 44 45 30 30", outcome("SELECT $$\\uD83D\\uDE00$$"));
    }
}
