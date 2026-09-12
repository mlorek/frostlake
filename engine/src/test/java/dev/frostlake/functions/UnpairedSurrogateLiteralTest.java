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
 * <p>THE POSITION IS THE LITERAL'S OWN START, not the call around it — which is how the refusal
 * announces that it came from the reader. That is asserted at seven different offsets and onto a second
 * line rather than at one, because a single statement cannot tell a literal-anchored rule from a
 * statement- or call-anchored one. The offset had to be RESOLVED against the enclosing fragment: an
 * expression is re-parsed on its own, so the token's raw position is 0 for a literal that begins the
 * fragment, whatever column it really sits in.
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

    private String highRefusal(final int position, final String escape) {
        return "SQL compilation error: error line 1 at position " + position
            + "|Invalid Unicode string literal; high surrogate '" + escape
            + "' must be followed by a low surrogate ('\\uDC00'-'\\uDFFF').";
    }

    private String lowRefusal(final int position, final String escape) {
        return "SQL compilation error: error line 1 at position " + position
            + "|Invalid Unicode string literal; low surrogate '" + escape
            + "' must be preceded by a high surrogate ('\\uD800'-'\\uDBFF').";
    }

    /** Each half has its own sentence. */
    @Test
    public void eachHalfHasItsOwnSentence() {
        assertEquals(highRefusal(7, "\\uD800"), outcome("SELECT " + HIGH));
        assertEquals(lowRefusal(7, "\\uDC00"), outcome("SELECT " + LOW));
        assertEquals(highRefusal(7, "\\uD800"), outcome("SELECT " + HIGH + " FROM pu"));
    }

    /** The FIRST offending unit decides, and the echo carries its own value. */
    @Test
    public void theFirstOffendingUnitDecides() {
        assertEquals(highRefusal(7, "\\uD800"), outcome("SELECT '\\uD800\\uD800'"),
            "two highs — the first one is named");
        assertEquals(lowRefusal(7, "\\uDC00"), outcome("SELECT '\\uDC00\\uD800'"),
            "a LOW first, so the low sentence wins");
        assertEquals(highRefusal(7, "\\uD800"), outcome("SELECT '\\uD800A'"));
        assertEquals(highRefusal(7, "\\uD800"), outcome("SELECT 'A\\uD800'"),
            "and text before it does not move the POSITION, which is the literal's start");
        assertEquals(highRefusal(7, "\\uD8FF"), outcome("SELECT '\\uD8FF'"));
        assertEquals(lowRefusal(7, "\\uDFFF"), outcome("SELECT '\\uDFFF'"));
    }

    /** The echo is UPPER-case hex however the escape was written. */
    @Test
    public void theEchoIsAlwaysUpperCase() {
        assertEquals(highRefusal(7, "\\uD800"), outcome("SELECT '\\ud800'"));
        assertEquals(highRefusal(7, "\\uD8FF"), outcome("SELECT '\\ud8Ff'"));
    }

    /** The position follows the LITERAL wherever it is written, onto a second line included. */
    @Test
    public void thePositionIsTheLiteralsOwnStart() {
        assertEquals(highRefusal(21, "\\uD800"),
            outcome("SELECT VALIDATE_UTF8(" + HIGH + ") FROM pu"));
        assertEquals(highRefusal(25, "\\uD800"),
            outcome("SELECT TRY_VALIDATE_UTF8(" + HIGH + ") FROM pu"),
            "the two spellings differ only by their own name lengths — the call is not the anchor");
        assertEquals(highRefusal(10, "\\uD800"), outcome("SELECT 1, " + HIGH));
        assertEquals(highRefusal(23, "\\uD800"),
            outcome("SELECT i FROM pu WHERE " + HIGH + " = 'x'"));
        assertEquals(highRefusal(15, "\\uD800"), outcome("SELECT 'ab' || " + HIGH));
        assertEquals("SQL compilation error: error line 2 at position 2"
            + "|Invalid Unicode string literal; high surrogate '\\uD800'"
            + " must be followed by a low surrogate ('\\uDC00'-'\\uDFFF').",
            outcome("SELECT 1,\n  " + HIGH), "a second LINE moves the line as well as the column");
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
