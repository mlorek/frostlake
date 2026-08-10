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

package dev.frostlake.procedures;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BREAK, CONTINUE, RETURN, RAISE, LET and NULL are SCRIPTING statements: legal inside a BEGIN…END
 * block, and standing alone they are refused as syntax errors.
 *
 * <p>THE POSITION IS THE OFFENDING TOKEN'S OWN, for all six alike — {@code "   BREAK"} reads position
 * 3, {@code "\n  LET v := 1"} reads line 2 position 2 — and the word is echoed exactly as written, so
 * a lower-case {@code null} is quoted in lower case. An earlier reading had five of them pinned at a
 * constant line 1 position 0; that was the LIVE HARNESS trimming each statement's leading whitespace
 * before submitting it, so the account was answering about text it had never been given.
 *
 * <p>AFTER a complete query the same word is a different thing entirely: live parses
 * {@code SELECT x FROM t break} as a query with a bare ALIAS and runs it, and the alias resolves. A
 * SEMICOLON is what makes it a statement again — and that one is refused, at its own place.
 */
public class StrayScriptingStatementTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE st (x NUMBER)");
        engine.execute("INSERT INTO st VALUES (1)");
    }

    /** The refusal a statement raises, flattened, or "accepted" when there was none. */
    private String outcome(final String sql) {
        try {
            engine.execute(sql);
        } catch (final RuntimeException refused) {
            return refused.getMessage().replace('\n', ' ');
        }
        return "accepted";
    }

    private void refusesAsStray(final String sql, final String word) {
        final String got = outcome(sql);
        assertTrue(got.contains("syntax error line 1 at position 0 unexpected '" + word + "'."),
            "[" + sql.replace("\n", "\\n") + "] gave: " + got);
    }

    /**
     * A trailing one after a complete query is a bare table ALIAS, not a stray statement — measured
     * over a raw connection, where `SELECT break.x FROM st break` resolves through it. Frostlake
     * refused these for a while on a measurement the test harness had distorted: it split the input in
     * two and asked the account about a bare `break`, which really is refused.
     */
    @Test
    public void aTrailingScriptingWordIsABareAlias() {
        assertEquals("accepted", outcome("SELECT x FROM st break"));
        assertEquals("accepted", outcome("SELECT x FROM st continue"));
        assertEquals("accepted", outcome("SELECT x FROM st return"));
        assertEquals("accepted", outcome("SELECT x FROM st raise"));
        assertEquals("accepted", outcome("SELECT x FROM st\n  break"));
        assertEquals("accepted", outcome("\n\nSELECT x FROM st break"));
        // and the alias really names the table
        assertEquals("accepted", outcome("SELECT break.x FROM st break"));
        assertEquals("accepted", outcome("SELECT return.x FROM st return"));
    }

    /** A SEMICOLON makes it a second statement again, and that one IS refused — at its own place. */
    @Test
    public void aSemicolonMakesItAStatementAgain() {
        final String got = outcome("SELECT x FROM st; break");
        assertTrue(got.contains("syntax error line 1 at position 18 unexpected 'break'."), got);
    }

    /** NULL is the exception: it is the literal, not a name, so it cannot alias anything. */
    @Test
    public void nullIsNoAlias() {
        assertTrue(String.valueOf(outcome("SELECT x FROM st null")).contains("syntax error"),
            outcome("SELECT x FROM st null"));
    }

    /** Standing alone, outside any block, each is refused and named as written. */
    @Test
    public void aStandaloneScriptingStatementIsRefused() {
        refusesAsStray("BREAK", "BREAK");
        refusesAsStray("CONTINUE", "CONTINUE");
        refusesAsStray("RETURN 1", "RETURN");
        refusesAsStray("RAISE", "RAISE");
        refusesAsStray("LET v := 1", "LET");
        refusesAsStray("NULL", "NULL");
    }

    /** The position follows the LAYOUT, because it is the token's own — for every one of the six. */
    @Test
    public void thePositionIsTheOffendingTokensOwn() {
        assertPositioned("   BREAK", 1, 3, "BREAK");
        assertPositioned("\n  BREAK", 2, 2, "BREAK");
        assertPositioned("\n\n CONTINUE", 3, 1, "CONTINUE");
        assertPositioned("   LET v := 1", 1, 3, "LET");
        assertPositioned("\n  LET v := 1", 2, 2, "LET");
        assertPositioned("\n\n RETURN 1", 3, 1, "RETURN");
        assertPositioned("   RAISE", 1, 3, "RAISE");
    }

    /**
     * NULL is the ONE member of the family whose refusal moves: live reports the word's OWN line and
     * column where the other five report a constant line 1 position 0. On a single-line input the two
     * rules agree by accident — `   NULL` is what tells them apart, at position 3 where an equally
     * indented LET still reads position 0.
     */
    @Test
    public void theNullStatementIsPositionedAtItsOwnToken() {
        assertPositioned("NULL", 1, 0, "NULL");
        assertPositioned("NULL;", 1, 0, "NULL");
        assertPositioned("NULL   ", 1, 0, "NULL");
        assertPositioned(" NULL", 1, 1, "NULL");
        assertPositioned("   NULL", 1, 3, "NULL");
        assertPositioned("       NULL", 1, 7, "NULL");
        assertPositioned("\tNULL", 1, 1, "NULL");
        assertPositioned("\nNULL", 2, 0, "NULL");
        assertPositioned("\n  NULL", 2, 2, "NULL");
        assertPositioned("\n\n   NULL", 3, 3, "NULL");
    }

    /** The word is echoed as WRITTEN, so a lower-case one is refused in lower case. */
    @Test
    public void theNullStatementIsEchoedAsWritten() {
        assertPositioned("null", 1, 0, "null");
    }

    /** Where NULL is legal it stays legal — the semicolon-less form must not open the block one. */
    @Test
    public void nullKeepsItsBlockMeaning() {
        assertEquals("accepted", outcome("BEGIN NULL; END"));
        assertEquals("accepted", outcome("BEGIN SELECT 1; NULL; END"));
        assertEquals("accepted", outcome("BEGIN IF (1 = 1) THEN NULL; ELSE NULL; END IF; END"));
        assertEquals("accepted", outcome("SELECT NULL AS c"));
        // Inside a block the separator is still mandatory — both engines refuse, though they anchor
        // the sentence on different tokens.
        assertTrue(outcome("BEGIN NULL END").contains("syntax error"),
            "BEGIN NULL END gave: " + outcome("BEGIN NULL END"));
    }

    private void assertPositioned(final String sql, final int line, final int position,
            final String word) {
        final String got = outcome(sql);
        assertTrue(got.contains("syntax error line " + line + " at position " + position
                + " unexpected '" + word + "'."),
            "[" + sql.replace("\n", "\\n") + "] gave: " + got);
    }

    /**
     * The words that are NOT scripting statements stay legal in that position — they are bare table
     * aliases, and refusing them would trade one fidelity bug for another.
     */
    @Test
    public void anOrdinaryKeywordAfterAQueryIsStillAnAlias() {
        for (final String word : new String[]{"let", "exception", "while", "loop", "if", "open",
            "fetch"}) {
            assertEquals("accepted", outcome("SELECT x FROM st " + word),
                word + " should still read as a bare alias");
        }
    }

    /** Inside a block they work exactly as before — the refusal is about the top level only. */
    @Test
    public void insideABlockTheyStillWork() {
        assertEquals("accepted", outcome("BEGIN RETURN 42; END"));
        assertEquals("accepted",
            outcome("BEGIN FOR i IN 1 TO 3 DO BREAK; END FOR; RETURN 1; END"));
        assertEquals("accepted",
            outcome("BEGIN FOR i IN 1 TO 3 DO CONTINUE; END FOR; RETURN 1; END"));
    }

    /** And a block that returns a value still returns it. */
    @Test
    public void aBlockStillReturnsItsValue() {
        final ResultSet rs = engine.executeQuery("BEGIN RETURN 42; END");
        rs.next();
        assertEquals("42", String.valueOf(rs.getValue(0)));
    }
}
