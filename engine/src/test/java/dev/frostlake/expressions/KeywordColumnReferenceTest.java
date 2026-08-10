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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Of the five words a NAME position admits, only THREE are refused by the parser in an expression.
 * CONSTRAINT and DEFAULT get past it and are answered by the name RESOLVER instead — and the two do
 * not behave alike once they are there:
 *
 * <pre>
 *   SELECT constraint FROM d   d HAS that column   reads it
 *   SELECT constraint FROM kw  it does not         invalid identifier 'CONSTRAINT'
 *   SELECT default FROM d      d HAS that column   invalid identifier 'DEFAULT'   — still refused
 *   SELECT "DEFAULT" FROM d    the quoted spelling reads it
 *   SELECT case FROM kw                            syntax error — the construct owns the position
 * </pre>
 *
 * <p>So an UNQUOTED DEFAULT is a name that can never be found: the word is the DML marker, and outside
 * the two places that marker stands it resolves to nothing, whatever the table holds.
 */
public class KeywordColumnReferenceTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE kw (a INT)");
        engine.execute("INSERT INTO kw VALUES (1)");
        engine.execute("CREATE TABLE d (\"DEFAULT\" INT, \"CONSTRAINT\" INT, b INT)");
        engine.execute("INSERT INTO d SELECT 5, 6, 7");
    }

    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return null;
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', ' ');
        }
    }

    private int value(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return ((Number) rs.getValue(0)).intValue();
    }

    /** CONSTRAINT is an ordinary column reference: it reads the column when there is one. */
    @Test
    public void constraintReadsItsColumn() {
        assertEquals(6, value("SELECT constraint FROM d"));
        assertEquals(6, value("SELECT b - 1 FROM d WHERE constraint = 6"));
    }

    /** And is refused BY NAME, not by the parser, when there is none. */
    @Test
    public void constraintIsRefusedByName() {
        assertTrue(String.valueOf(refusal("SELECT constraint FROM kw"))
            .contains("error line 1 at position 7 invalid identifier 'CONSTRAINT'"),
            refusal("SELECT constraint FROM kw"));
    }

    /** An unquoted DEFAULT never resolves — not even to a column of that name. */
    @Test
    public void unquotedDefaultNeverResolves() {
        assertTrue(String.valueOf(refusal("SELECT default FROM d"))
            .contains("error line 1 at position 7 invalid identifier 'DEFAULT'"),
            refusal("SELECT default FROM d"));
        assertTrue(String.valueOf(refusal("SELECT default FROM kw"))
            .contains("error line 1 at position 7 invalid identifier 'DEFAULT'"),
            refusal("SELECT default FROM kw"));
        assertTrue(String.valueOf(refusal("SELECT b FROM d WHERE default = 5"))
            .contains("error line 1 at position 22 invalid identifier 'DEFAULT'"),
            refusal("SELECT b FROM d WHERE default = 5"));
    }

    /** The QUOTED spelling is a different name and reads the column. */
    @Test
    public void theQuotedSpellingReadsIt() {
        assertEquals(5, value("SELECT \"DEFAULT\" FROM d"));
    }

    /** In a compound expression the word is a name too, and fails the same way. */
    @Test
    public void defaultInAnExpressionIsAName() {
        assertTrue(String.valueOf(refusal("SELECT default + 1 FROM d"))
            .contains("invalid identifier 'DEFAULT'"), refusal("SELECT default + 1 FROM d"));
    }

    /** The other three keep the PARSER's refusal — the construct owns that position. */
    @Test
    public void theOtherThreeStayParserRefusals() {
        for (final String word : new String[]{"case", "cast", "when"}) {
            assertTrue(String.valueOf(refusal("SELECT " + word + " FROM kw")).contains("syntax error"),
                word + " must be a syntax error: " + refusal("SELECT " + word + " FROM kw"));
        }
    }
}
