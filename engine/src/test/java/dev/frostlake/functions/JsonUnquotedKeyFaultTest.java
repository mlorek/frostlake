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
 * What a BAD unquoted JSON attribute name is called — five sentences, chosen by the character.
 *
 * <p>★ THE SPLIT IS "COULD THIS CHARACTER BEGIN A VALUE HERE". A sign, a point or whitespace ends the
 * name and leaves the reader wanting a colon, so it reports a MISSING COLON. Everything else — and it
 * is most things — is legal nowhere outside a string and is reported WHERE IT STANDS. That reading
 * was a hypothesis from six characters; it is pinned here across twenty, and it held for every one.
 *
 * <p>★ THREE MORE SENTENCES THE FIRST MEASUREMENT DID NOT SEE, each with its own wording:
 * a name that begins with a DIGIT is not a character complaint at all but "object attribute name
 * cannot be a number"; a {@code [} is "misplaced ["; a {@code ,} is "misplaced comma". A {@code :}
 * is not a fault here at all — it ends the name legally, and the failure moves on to the VALUE.
 *
 * <p>★ AND THE POSITION COUNTS BYTES, NOT CHARACTERS. A two-byte character standing at byte 2 is
 * reported at 3 and a three-byte one at 4 — the character's LAST byte. It is also named by CODE
 * POINT, and that form carries no quotes where a character shown as written does. A backslash is the
 * one ASCII character the sentence escapes.
 */
public class JsonUnquotedKeyFaultTest extends BaseDatabaseTest {

    /** The refusal for a document, or the value when it parses. */
    private String answer(final String document) {
        try {
            final ResultSet rs = engine.executeQuery("SELECT PARSE_JSON('" + document + "')");
            rs.next();
            return String.valueOf(rs.getValue(0));
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String invalid(final String shown, final int pos) {
        return "Error parsing JSON: invalid character outside of a string: " + shown + ", pos " + pos;
    }

    /** ★ Legal nowhere outside a string — reported where it stands, across the whole punctuation set. */
    @Test
    public void acharacterLegalNowhereIsReportedWhereItStands() {
        assertEquals(invalid("'$'", 3), answer("{a$b:1}"));
        assertEquals(invalid("'@'", 3), answer("{a@b:1}"));
        assertEquals(invalid("'/'", 3), answer("{a/b:1}"));
        assertEquals(invalid("';'", 3), answer("{a;b:1}"));
        assertEquals(invalid("'!'", 3), answer("{a!b:1}"));
        assertEquals(invalid("'~'", 3), answer("{a~b:1}"));
        assertEquals(invalid("'%'", 3), answer("{a%b:1}"));
        assertEquals(invalid("'^'", 3), answer("{a^b:1}"));
        assertEquals(invalid("'&'", 3), answer("{a&b:1}"));
        assertEquals(invalid("'*'", 3), answer("{a*b:1}"));
        assertEquals(invalid("'='", 3), answer("{a=b:1}"));
        assertEquals(invalid("'<'", 3), answer("{a<b:1}"));
        assertEquals(invalid("'>'", 3), answer("{a>b:1}"));
        assertEquals(invalid("'|'", 3), answer("{a|b:1}"));
        assertEquals(invalid("'?'", 3), answer("{a?b:1}"));
        assertEquals(invalid("'#'", 3), answer("{a#b:1}"));
        assertEquals(invalid("'('", 3), answer("{a(b:1}"));
        assertEquals(invalid("')'", 3), answer("{a)b:1}"));
    }

    /** ★ The other class: a character that COULD start a value, so the colon is what is missing. */
    @Test
    public void acharacterThatCouldStartAValueLeavesAMissingColon() {
        assertEquals("Error parsing JSON: missing colon, pos 3", answer("{a-b:1}"),
            "a sign could begin a number, so the name simply ended");
        assertEquals("Error parsing JSON: missing colon, pos 3", answer("{a+b:1}"));
        assertEquals("Error parsing JSON: missing colon, pos 3", answer("{a.b:1}"));
        assertEquals("Error parsing JSON: missing colon, pos 4", answer("{a b:1}"),
            "★ whitespace is SKIPPED first, so the report lands on the next real character");
    }

    /** ★ The three sentences that are neither: a number, and the two misplaced tokens. */
    @Test
    public void thenameShapesWithTheirOwnSentences() {
        assertEquals("Error parsing JSON: object attribute name cannot be a number, pos 2",
            answer("{1:1}"),
            "a digit-led name is not a character complaint at all");
        assertEquals("Error parsing JSON: object attribute name cannot be a number, pos 2",
            answer("{1a:1}"),
            "and it is the START that decides — the letters after the digit change nothing");
        assertEquals("Error parsing JSON: misplaced [, pos 3", answer("{a[b:1}"));
        assertEquals("Error parsing JSON: misplaced comma, pos 3", answer("{a,b:1}"));
    }

    /** ★ A colon is no fault here — the name ends, and the VALUE is what fails. */
    @Test
    public void acolonEndsTheNameAndMovesTheFaultToTheValue() {
        assertEquals("Error parsing JSON: unknown keyword \"b\", pos 5", answer("{a:b:1}"),
            "the failure is a bad VALUE, reported past the colon rather than at it");
    }

    /** ★ A non-ASCII character: named by CODE POINT, unquoted, and positioned by its LAST byte. */
    @Test
    public void anonAsciiCharacterIsNamedByCodePointAtItsLastByte() {
        assertEquals(invalid("U+00E4", 3), answer("{\u00e4:1}"),
            "★ two bytes standing at byte 2 report 3 — the position counts bytes, not characters");
        assertEquals(invalid("U+00E4", 4), answer("{a\u00e4:1}"));
        assertEquals(invalid("U+20AC", 4), answer("{\u20ac:1}"),
            "★ and a THREE-byte character at the same place reports 4, so it is the width that decides");
        assertEquals(invalid("U+20AC", 6), answer("{ab\u20ac:1}"));
        assertEquals(invalid("'$'", 5), answer("{abc$:1}"),
            "an ASCII character keeps its quotes and its plain offset");
    }

    /** The one ASCII character the sentence escapes rather than printing as written. */
    @Test
    public void abackslashIsEscapedInTheSentence() {
        assertEquals(invalid("'\\\\'", 3), answer("{a\\\\b:1}"));
    }
}
