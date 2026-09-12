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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which token a syntax error blames when the statement's FIRST word is misspelled.
 *
 * <p>★ THE RULE IS "THE FIRST TOKEN IT CANNOT USE", and this is the cell that shows it. Live answers
 * {@code SELCT 1} with "unexpected 'SELCT'" at position 0; Frostlake used to get past the misspelling
 * and blame the {@code 1} after it. The reason it got past is worth keeping: a bare identifier legally
 * OPENS a statement — the assignment {@code x := 1} — so the alternative stays viable until the token
 * after the name.
 *
 * <p>★ WHICH IS ALSO THE TEST, and why this needs no list of legal opening words: an identifier that is
 * NOT followed by {@code :=} could never have begun a statement, so naming it costs nothing that
 * parses. Every keyword-led statement is untouched, because the lexer already tells a keyword from a
 * name.
 *
 * <p>A LEADING COMMENT does not move the position, on either engine — the same rebase every other
 * syntax error uses.
 *
 * <p>Left for its own task: the same rule MID-statement. {@code SELECT i FROM rt WHER i = 1} reads
 * WHER as a table alias on both engines, and then live blames the {@code i} that cannot follow while
 * Frostlake runs on to the {@code =}. Fixing that means making the parser report the first unusable
 * token in general, not just at the start.
 */
public class LeadingKeywordAnchorTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE lk (i INT, n NUMBER(10,2))");
        engine.execute("INSERT INTO lk VALUES (1, 1.00)");
    }

    private String refusal(final String sql) {
        try {
            engine.execute(sql);
            return "ACCEPTED";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String unexpectedAt(final int position, final String token) {
        return "SQL compilation error:|syntax error line 1 at position " + position
            + " unexpected '" + token + "'.";
    }

    /** ★ A misspelled leading keyword is named where it stands. */
    @Test
    public void amisspelledLeadingKeywordIsNamedWhereItStands() {
        assertEquals(unexpectedAt(0, "SELCT"), refusal("SELCT 1"));
        assertEquals(unexpectedAt(0, "SLECT"), refusal("SLECT 1"));
        assertEquals(unexpectedAt(0, "SELCT"), refusal("SELCT i FROM lk"),
            "whatever follows it");
    }

    /** Every statement family, not only SELECT. */
    @Test
    public void everyStatementFamilyIsAnchoredAlike() {
        assertEquals(unexpectedAt(0, "CREAT"), refusal("CREAT TABLE zz (a INT)"));
        assertEquals(unexpectedAt(0, "INSRT"), refusal("INSRT INTO lk VALUES (2, 2.00)"));
        assertEquals(unexpectedAt(0, "UPDTE"), refusal("UPDTE lk SET i = 2"));
        assertEquals(unexpectedAt(0, "DELTE"), refusal("DELTE FROM lk"));
    }

    /** A LEADING COMMENT does not move the position. */
    @Test
    public void aleadingCommentDoesNotMoveIt() {
        assertEquals(unexpectedAt(0, "SELCT"), refusal("/* c */ SELCT 1"));
    }

    /** ★ The ASSIGNMENT shape still opens with a bare name — the exception the rule turns on. */
    @Test
    public void anAssignmentStillOpensWithAbareName() {
        assertEquals("ACCEPTED",
            refusal("BEGIN LET x INT := 1; x := 2; RETURN x; END;"));
    }

    /** A well-formed statement is untouched, and so are the syntax errors that already agreed. */
    @Test
    public void thewellFormedAndAlreadyAgreeingCellsAreUntouched() {
        assertEquals("ACCEPTED", refusal("SELECT i FROM lk"));
        assertEquals(unexpectedAt(14, "lk"), refusal("SELECT 1 FORM lk"),
            "FORM reads as an alias on both engines, so the name after it is what is blamed");
        assertEquals(unexpectedAt(22, "<EOF>"), refusal("SELECT i FROM lk WHERE"));
        assertEquals(unexpectedAt(23, "*"), refusal("SELECT i FROM lk WHERE * = 1"));
    }
}
