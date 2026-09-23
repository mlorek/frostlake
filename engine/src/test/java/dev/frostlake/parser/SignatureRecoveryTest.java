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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

/**
 * The lines the account reports for a DROP's or a DESCRIBE's signature and what follows it, read the way it reads
 * them: a token it cannot match is dropped when the next one is the expected one, supplied when the one at hand may
 * follow it, and otherwise named, with the reading skipping to a token that may follow what it is still inside — for a
 * DESCRIBE that includes the name of a property, so the rest of the text is read as properties, while a DROP skips to
 * its end. A keyword is no item there, a type takes its parameters only when they can begin, and a word value followed
 * by a number is read again as a property's name. A stage reference is one token, up to a delimiter, and only a
 * property's value; a text with an unclosed quote or comment keeps the parse's own lines (all live-verified).
 */
public class SignatureRecoveryTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t1 (x INT)");
    }

    /** Every row's first cell, a bar between rows, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            final StringBuilder out = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                out.append(out.length() > 0 ? " | " : "").append(row.getValue(0));
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** The refusal naming each (position, token) pair of line 1 in turn. */
    private static String lines(final String... placed) {
        final StringBuilder out = new StringBuilder("SQL compilation error:");
        for (int i = 0; i < placed.length; i += 2) {
            out.append("|syntax error line 1 at position ").append(placed[i]).append(" unexpected '")
                .append(placed[i + 1]).append("'.");
        }
        return out.toString();
    }

    @Test
    public void aDescribeReadsTheRestAsPropertiesOnceItsSignatureIsAbandoned() {
        assertEquals(lines("19", "(", "21", ")", "25", ")"), answer("DESCRIBE TABLE t1 ((a), b)"));
        assertEquals(lines("19", "(", "21", ")", "24", ")"), answer("DESCRIBE TABLE t1 ((a) b)"));
        assertEquals(lines("19", "(", "22", "b", "23", ")"), answer("DESCRIBE TABLE t1 ((a b))"));
        assertEquals(lines("19", "(", "21", ")", "25", "<EOF>"), answer("DESCRIBE TABLE t1 ((a)) x"));
        assertEquals(lines("19", "TRUE", "23", "(", "26", ")"), answer("DESCRIBE TABLE t1 (TRUE(1))"));
        assertEquals(lines("19", "TYPE", "23", ")"), answer("DESCRIBE TABLE t1 (TYPE)"));
        assertEquals(lines("25", "d", "26", ".", "28", ")"), answer("DESCRIBE TABLE t1 (a.b.c.d.e)"));
        assertEquals(lines("15", "(", "17", ")", "21", ")"), answer("DESC TABLE t1 ((a), b)"));
        assertEquals(lines("18", "(", "20", ")", "23", ")"), answer("DESCRIBE VIEW t1 ((a) b)"));
    }

    @Test
    public void aDropSkipsToItsEndUnlessItsOptionFollows() {
        assertEquals(lines("15", "("), answer("DROP TABLE t1 ((a)) x"));
        assertEquals(lines("17", "b"), answer("DROP TABLE t1 (a b) x"));
        assertEquals(lines("17", "b", "28", "x"), answer("DROP TABLE t1 (a b) CASCADE x"));
        assertEquals(lines("19", "c", "23", "x"), answer("DROP TABLE t1 (a(b c)) x"));
        assertEquals(lines("18", "(", "21", ")"), answer("DROP TABLE t1 (a, (b)) x"));
        assertEquals(lines("16", "b", "27", "x"), answer("DROP VIEW t9 (a b) CASCADE x"));
        assertEquals("1", answer("SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'T1'"));
    }

    @Test
    public void aFaultInsideAParameterListResumesThere() {
        assertEquals(lines("21", "TRUE", "27", "1", "29", ")"), answer("DESCRIBE TABLE t1 (a(TRUE, 1))"));
        assertEquals(lines("22", "("), answer("DESCRIBE TABLE t1 (a, (b))"));
        assertEquals(lines("25", "d"), answer("DESCRIBE TABLE t1 (a(b.c.d))"));
        assertEquals(lines("28", "(", "32", ")"), answer("DESCRIBE TABLE t1 (a(VARCHAR((1))))"));
        assertEquals(lines("28", "(", "35", ")"), answer("DROP TABLE IDENTIFIER(UPPER(('t1')))"));
        assertEquals(lines("22", "("), answer("DROP TABLE IDENTIFIER((UPPER('t1')))"));
        assertEquals(lines("28", "'t1'", "35", "x"), answer("DROP TABLE IDENTIFIER(UPPER('t1')) x"));
        assertEquals(lines("32", "'t1'", "40", "<EOF>"), answer("DESCRIBE TABLE IDENTIFIER(UPPER('t1')) x"));
    }

    @Test
    public void aTypeTakesItsParametersOnlyWhenTheyCanBegin() {
        assertEquals(lines("26", "("), answer("DESCRIBE TABLE t1 (VARCHAR((1)))"));
        assertEquals(lines("26", "(", "28", ")"), answer("DESCRIBE TABLE t1 (VARCHAR(a))"));
        assertEquals(lines("28", ",", "30", "2", "32", ")"), answer("DESCRIBE TABLE t1 (VARCHAR(1, 2))"));
        assertEquals(lines("30", ",", "32", "3", "34", ")"), answer("DESCRIBE TABLE t1 (NUMBER(1, 2, 3))"));
        assertEquals(lines("22", "("), answer("DESCRIBE TABLE t1 (INT(1))"));
        assertEquals("X", answer("DESCRIBE TABLE t1 (NUMBER(-1))"));
    }

    @Test
    public void aWordValueBeforeANumberIsReadAgainAsAProperty() {
        assertEquals(lines("28", "1", "28", "1", "31", "<EOF>"), answer("DESCRIBE TABLE t1 (a) x = y 1 a"));
    }

    @Test
    public void afterTheWordIdentifierTheStatementJudgesTheFirstTokenAlone() {
        assertEquals(lines("22", "TRUE"), answer("DROP TABLE IDENTIFIER(TRUE) CASCADE x"));
        assertEquals(lines("26", "("), answer("DESCRIBE TABLE IDENTIFIER((a), b) x"));
        assertEquals(lines("28", "b", "32", "<EOF>"), answer("DESCRIBE TABLE IDENTIFIER(a b) x"));
    }

    @Test
    public void onlyWhatTheAccountTakesIsTaken() {
        assertEquals(lines("19", "d", "20", ")"), answer("DESCRIBE TABLE t1 (d)"));
        assertEquals(lines("22", "("), answer("DESCRIBE TABLE t1 (a.b(1))"));
        assertEquals(lines("15", "VIEW"), answer("DROP TABLE t1 (VIEW)"));
        assertEquals("X", answer("DESCRIBE TABLE t1 (a(b.c.e.f))"));
        assertEquals("X", answer("DESCRIBE TABLE t1 (CURRENT_DATE)"));
        assertTrue(answer("DROP TABLE t9 (a(b.c.e))").contains("does not exist or not authorized"));
        assertEquals("1", answer("SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'T1'"));
    }

    @Test
    public void aStageReferenceIsOneTokenAndOnlyAValue() {
        assertEquals(lines("19", "@s"), answer("DESCRIBE TABLE t1 (@s)"));
        assertEquals(lines("19", "@s.t/x"), answer("DESCRIBE TABLE t1 (@s.t/x)"));
        assertEquals(lines("19", "@~s"), answer("DESCRIBE TABLE t1 (@~s)"));
        assertEquals(lines("19", "@s", "23", ")"), answer("DESCRIBE TABLE t1 (@s~x)"));
        assertEquals(lines("19", "@", "22", ")"), answer("DESCRIBE TABLE t1 (@ s)"));
        assertEquals(lines("24", "@s"), answer("DESCRIBE TABLE t1 (a, b(@s, 1))"));
        assertEquals(lines("19", "(", "21", ")", "25", ")"), answer("DESCRIBE TABLE t1 ((a), b) @~ = 1"));
        assertEquals(lines("21", "b", "32", "@s"), answer("DESCRIBE TABLE t1 (a b) x = (1, @s)"));
        assertEquals(lines("21", "b", "31", "="), answer("DESCRIBE TABLE t1 (a b) x = @s = @t"));
        assertEquals(lines("21", "b", "30", "'q'"), answer("DESCRIBE TABLE t1 (a b) x = @s'q'"));
        assertEquals(lines("21", "b", "33", "@s", "33", "@s"), answer("DESCRIBE TABLE t1 (a b) x = TRUE @s"));
        assertEquals(lines("21", "b", "32", "@s", "29", ".", "32", "@s"), answer("DESCRIBE TABLE t1 (a b) x = a.b @s"));
        assertEquals(lines("18", "@s"), answer("DROP TABLE t9 (a) @s CASCADE"));
        assertEquals(lines("26", "@s"), answer("DESCRIBE TABLE IDENTIFIER(@s) x"));
    }

    @Test
    public void aTextThisLexerCannotReadAsTheAccountDoesKeepsTheParsesLines() {
        final String quote = answer("DESCRIBE TABLE t1 ((a), b) x = 'abc");
        assertTrue(quote.startsWith(lines("19", "(", "21", ")")), quote);
        assertFalse(quote.contains("unexpected '<EOF>'"), quote);
        final String comment = answer("DESCRIBE TABLE t1 ((a), b) /* x");
        assertTrue(comment.startsWith(lines("19", "(", "21", ")")), comment);
        assertFalse(comment.contains("unexpected '<EOF>'"), comment);
        final String dollars = answer("DESCRIBE TABLE t1 (a b) x = $$y");
        assertTrue(dollars.contains("parse error line 1 at position 31 near '<EOF>'."), dollars);
        assertFalse(dollars.contains("unexpected '<EOF>'"), dollars);
    }

    @Test
    public void linesKeepTheirOwnLineAndAnOpeningCommentIsSkipped() {
        assertEquals("SQL compilation error:|syntax error line 2 at position 0 unexpected '('.|syntax error line 2 at "
            + "position 2 unexpected ')'.|syntax error line 3 at position 3 unexpected '<EOF>'.",
            answer("DESCRIBE TABLE t1 (\n(a)\n) x"));
        assertEquals(lines("19", "(", "21", ")", "24", ")"), answer("/* c */ DESCRIBE TABLE t1 ((a) b)"));
    }
}
