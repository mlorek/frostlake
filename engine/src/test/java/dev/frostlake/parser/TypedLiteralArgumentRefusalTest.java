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

package dev.frostlake.parser;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An IDENTIFIER followed by a STRING inside a call — {@code TRIM(BOTH ' ' FROM v)} and its relatives.
 *
 * <p>★ IT IS NOT A TRIM RULE, which is what the measurement changed. The refusal looks like an ANSI
 * TRIM problem and is not: {@code UPPER(FOO ' ')} behaves identically, because live reads the pair as
 * a TYPED LITERAL — the {@code DATE '2020-01-01'} shape with any word in front — consumes both tokens,
 * and reports whatever comes NEXT. So the anchor moves to the FROM, and a pair with nothing after it
 * is refused by NAME instead: "Unsupported data type literal 'BOTH ' ''."
 *
 * <p>Frostlake used to die on the string itself, several characters early.
 *
 * <p>★ THE ALTERNATIVE SITS AFTER THE ORDINARY CALL, and the engine suite proves why: written before
 * it, it also matched {@code HASH(DATE '1970-01-02')} — a REAL typed literal, whose type name is an
 * identifier like any other — and refused it. Behind the ordinary call, only an unrecognised word
 * reaches it. The real-typed-literal cells below are the regression guard.
 *
 * <p>Live STACKS a second syntax error on most of these, which Frostlake does not by the design
 * settled in the task that measured its recovery. These assertions are therefore on the FIRST line,
 * except where live emits only one.
 */
public class TypedLiteralArgumentRefusalTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE rs (v VARCHAR(10), n INT)");
        engine.execute("INSERT INTO rs VALUES ('  x  ', 2)");
    }

    private String refusal(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            return "ACCEPTED: [" + (rs.next() ? String.valueOf(rs.getValue(0)) : "<no rows>") + "]";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** The first line of a refusal, which is all Frostlake emits. */
    private String firstLine(final String sql) {
        final String message = refusal(sql);
        final String[] parts = message.split("\\|");
        return parts.length > 1 ? parts[0] + "|" + parts[1] : message;
    }

    private String unexpectedAt(final int position, final String token) {
        return "SQL compilation error:|syntax error line 1 at position " + position
            + " unexpected '" + token + "'.";
    }

    /** ★ The ANSI TRIM shape anchors on its FROM, not on the trim string. */
    @Test
    public void theAnsiTrimShapeAnchorsOnItsFrom() {
        assertEquals(unexpectedAt(21, "FROM"),
            firstLine("SELECT TRIM(BOTH ' ' FROM v) FROM rs"));
        assertEquals(unexpectedAt(24, "FROM"),
            firstLine("SELECT TRIM(LEADING ' ' FROM v) FROM rs"));
        assertEquals(unexpectedAt(25, "FROM"),
            firstLine("SELECT TRIM(TRAILING ' ' FROM v) FROM rs"));
    }

    /** The modifier slot is ANY identifier — an arbitrary word behaves the same. */
    @Test
    public void theModifierSlotIsAnyIdentifier() {
        assertEquals(unexpectedAt(20, "FROM"),
            firstLine("SELECT TRIM(FOO ' ' FROM v) FROM rs"));
        assertEquals(unexpectedAt(22, "FROM"),
            firstLine("SELECT LTRIM(BOTH ' ' FROM v) FROM rs"), "and any function name");
    }

    /** The anchor follows the statement, not a fixed offset. */
    @Test
    public void theAnchorFollowsTheStatement() {
        assertEquals(unexpectedAt(26, "FROM"),
            firstLine("SELECT      TRIM(BOTH ' ' FROM v) FROM rs"));
    }

    /** With something OTHER than FROM after the pair, the refusal lands on that instead. */
    @Test
    public void whateverFollowsThePairIsWhatIsReported() {
        assertEquals(unexpectedAt(21, "v"), refusal("SELECT TRIM(BOTH ' ' v) FROM rs"),
            "live emits only one line here, so the whole message is asserted");
    }

    /** ★ With NOTHING after the pair it is refused by NAME — and not only for TRIM. */
    @Test
    public void aPairWithNothingAfterItIsRefusedByName() {
        assertEquals("SQL compilation error:|Unsupported data type literal 'BOTH ' ''.",
            refusal("SELECT TRIM(BOTH ' ') FROM rs"));
        assertEquals("SQL compilation error:|Unsupported data type literal 'FOO ' ''.",
            refusal("SELECT TRIM(FOO ' ') FROM rs"));
        assertEquals("SQL compilation error:|Unsupported data type literal 'FOO ' ''.",
            refusal("SELECT UPPER(FOO ' ') FROM rs"),
            "an ordinary function, which is what shows this is not a TRIM rule");
    }

    /** ★ A REAL typed literal still parses — the regression the alternative's position guards. */
    @Test
    public void aRealTypedLiteralStillParses() {
        assertEquals("ACCEPTED: [true]",
            refusal("SELECT HASH(DATE '1970-01-02') = HASH(1) FROM rs"));
        assertEquals("ACCEPTED: [true]",
            refusal("SELECT HASH('00:00:01'::TIME) = HASH(1) FROM rs"));
        assertTrue(refusal("SELECT UPPER(DATE '2020-01-01') FROM rs").startsWith("ACCEPTED"),
            "a date literal is an ordinary argument to an ordinary function");
    }

    /** The TRIM spellings that really work are untouched. */
    @Test
    public void theWorkingTrimSpellingsAreUntouched() {
        assertEquals("ACCEPTED: [x]", refusal("SELECT TRIM(v) FROM rs"));
        assertEquals("ACCEPTED: [x]", refusal("SELECT TRIM(v, ' ') FROM rs"));
    }
}
