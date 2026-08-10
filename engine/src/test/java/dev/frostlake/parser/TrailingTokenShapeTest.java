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
 * Where a statement ends in a token that cannot finish it, the refusal names either {@code '<EOF>'} or
 * the token itself, and which one is decided by a single question: can the grammar CONSUME that token
 * there? A consumable one is taken, and the input then runs out — {@code '<EOF>'} at the text's end. An
 * unconsumable one is named where it stands.
 *
 * <pre>
 *   SELECT a FROM kw LEFT JOIN     '&lt;EOF&gt;' — JOIN opens a clause that wants a relation
 *   SELECT a FROM kw .             '&lt;EOF&gt;' — the dot opens a qualified name
 *   SELECT a FROM kw )             ')'    — nothing can consume it
 *   SELECT a FROM kw THEN          'THEN' — nor it
 * </pre>
 *
 * <p>Both engines answer identically for every case below. They part company where Frostlake's grammar
 * gives up on a token live's would have taken — a trailing comma in GROUP BY / ORDER BY, a trailing
 * {@code JOIN} / {@code LEFT} / {@code IN} / {@code OFFSET}, and {@code ON}, where it is Frostlake that
 * consumes and live that names. Those cells are measured and deliberately not asserted here: matching
 * them means giving this grammar live's exact follow sets at every list and clause boundary, which
 * changes acceptance nowhere and risks a great deal.
 */
public class TrailingTokenShapeTest extends BaseDatabaseTest {

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

    private String eofAt(final int position) {
        return "SQL compilation error:\nsyntax error line 1 at position " + position
            + " unexpected '<EOF>'.";
    }

    private String tokenAt(final int position, final String token) {
        return "SQL compilation error:\nsyntax error line 1 at position " + position
            + " unexpected '" + token + "'.";
    }

    /** A clause keyword that opens something is consumed, so the refusal lands on end of input. */
    @Test
    public void anOpeningKeywordRunsToEndOfInput() {
        assertEquals(eofAt(26), refusal("SELECT a FROM kw LEFT JOIN"));
        assertEquals(eofAt(27), refusal("SELECT a FROM kw CROSS JOIN"));
        assertEquals(eofAt(22), refusal("SELECT a FROM kw INNER"));
        assertEquals(eofAt(22), refusal("SELECT a FROM kw UNION"));
        assertEquals(eofAt(24), refusal("SELECT a FROM kw QUALIFY"));
    }

    /** So is an operator or a clause left mid-expression. */
    @Test
    public void anUnfinishedExpressionRunsToEndOfInput() {
        assertEquals(eofAt(32), refusal("SELECT a FROM kw WHERE b = 1 AND"));
        assertEquals(eofAt(32), refusal("SELECT a FROM kw HAVING MAX(a) >"));
        assertEquals(eofAt(38), refusal("SELECT ROW_NUMBER() OVER (PARTITION BY"));
        assertEquals(eofAt(41), refusal("SELECT ROW_NUMBER() OVER (ORDER BY a ROWS"));
        // A dot opens a qualified name, so it is consumed like any other opener.
        assertEquals(eofAt(18), refusal("SELECT a FROM kw ."));
    }

    /** A token nothing can consume is named where it stands, never at end of input. */
    @Test
    public void anUnconsumableTokenIsNamedWhereItStands() {
        assertEquals(tokenAt(17, ")"), refusal("SELECT a FROM kw )"));
        assertEquals(tokenAt(17, "]"), refusal("SELECT a FROM kw ]"));
        assertEquals(tokenAt(17, "="), refusal("SELECT a FROM kw ="));
        assertEquals(tokenAt(17, "THEN"), refusal("SELECT a FROM kw THEN"));
        assertEquals(tokenAt(17, "ELSE"), refusal("SELECT a FROM kw ELSE"));
        assertEquals(tokenAt(17, "*"), refusal("SELECT a FROM kw *"));
    }

    /** A trailing comma INSIDE parentheses is refused at the closing paren, which follows it. */
    @Test
    public void aTrailingCommaInsideParenthesesNamesTheCloser() {
        assertEquals(tokenAt(13, ")"), refusal("SELECT MAX(a,) FROM kw"));
        assertEquals(tokenAt(41, ")"),
            refusal("SELECT ROW_NUMBER() OVER (PARTITION BY a,) FROM kw"));
    }

    /** And the two lists that TOLERATE a trailing comma still do. */
    @Test
    public void theTolerantListsStayTolerant() {
        assertEquals(0, engine.executeQuery("SELECT a FROM kw,").getRowCount());
        assertEquals(0, engine.executeQuery("SELECT a, FROM kw").getRowCount());
    }
}
