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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A refusal carries ONE line per problem. Several genuinely independent problems stack, each at its own
 * place and in text order — three stray commas are three lines — while a statement with a single
 * problem stays a single line however many alternatives the parser tried before giving up.
 *
 * <p>What must never stack is the wreckage of recovery, which is what Frostlake's parser produces and
 * live's does not: an end-of-input error following an error already reported at a real token is the
 * parse running off the end of the statement, not a second problem, and the same sentence repeated at
 * the same position is one problem reported twice.
 *
 * <p>Measured but deliberately not asserted here: for a CALL left open with no arguments live adds a
 * second, BACKWARDS line naming the '(' it never closed ({@code SELECT MAX(} is '&lt;EOF&gt;' at 11 then
 * '(' at 10). Frostlake reports the first line only.
 */
public class StackedSyntaxErrorTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE kw (a INT, b INT)");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private String line(final int position, final String token) {
        return "syntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    /** Two independent stray commas are two lines, in text order. */
    @Test
    public void twoIndependentProblemsAreTwoLines() {
        assertEquals("SQL compilation error:\n" + line(26, ",") + "\n" + line(37, ","),
            refusal("SELECT a FROM kw GROUP BY , ORDER BY ,"));
        assertEquals("SQL compilation error:\n" + line(26, ",") + "\n" + line(35, ","),
            refusal("SELECT a FROM kw GROUP BY , HAVING ,"));
    }

    /** Three are three. */
    @Test
    public void threeIndependentProblemsAreThreeLines() {
        assertEquals("SQL compilation error:\n" + line(26, ",") + "\n" + line(37, ",") + "\n"
            + line(45, ","),
            refusal("SELECT a FROM kw GROUP BY , ORDER BY , LIMIT ,"));
    }

    /** One problem stays one line, whatever the parser tried on the way. */
    @Test
    public void oneProblemStaysOneLine() {
        assertEquals("SQL compilation error:\n" + line(14, "<EOF>"), refusal("SELECT MAX((1)"));
        assertEquals("SQL compilation error:\n" + line(8, "<EOF>"), refusal("SELECT ("));
        assertEquals("SQL compilation error:\n" + line(15, "<EOF>"), refusal("SELECT a FROM ("));
        assertEquals("SQL compilation error:\n" + line(17, "<EOF>"), refusal("SELECT MAX(MIN(a)"));
        assertEquals("SQL compilation error:\n" + line(20, "<EOF>"),
            refusal("SELECT COALESCE(1, 2"));
    }

    /**
     * An unterminated literal is the lexer's own sentence, alone, positioned at the end of the input —
     * wherever in the statement the quote was opened.
     *
     * <p>Measured but not asserted: an unterminated quoted IDENTIFIER ({@code SELECT "abc}) is TWO
     * lines on live, the lexer's and then the parser's, where this engine reports the lexer's only.
     * Nothing in a lexer error distinguishes the two cases here, and one line is what the commoner of
     * the pair needs.
     */
    @Test
    public void anUnterminatedLiteralIsALexerLine() {
        assertEquals("SQL compilation error:\nparse error line 1 at position 11 near '<EOF>'.",
            refusal("SELECT 'abc"));
        assertEquals("SQL compilation error:\nparse error line 1 at position 19 near '<EOF>'.",
            refusal("SELECT 'abc' || 'de"));
        assertEquals("SQL compilation error:\nparse error line 1 at position 29 near '<EOF>'.",
            refusal("SELECT a FROM kw WHERE b = 'x"));
    }
}
