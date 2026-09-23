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

package dev.frostlake.ddl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

/**
 * A word live's lexer keeps as a keyword, written where SHOW or DROP takes a class name, is refused where it
 * stands. After DROP live names one more token — the one after the word, when it could be a name or is a
 * parenthesis — and nothing more of the statement; after SHOW the word stands alone. The plural words live lists
 * kinds by are such keywords after DROP, and a word opening a two-word kind is refused at the word after it.
 * Every cell is live-verified.
 */
public class KeywordClassNameLinesTest extends BaseDatabaseTest {

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

    private static String lines(final String... placed) {
        final StringBuilder out = new StringBuilder("SQL compilation error:");
        for (int i = 0; i < placed.length; i += 2) {
            out.append("|syntax error line 1 at position ").append(placed[i]).append(" unexpected '")
                .append(placed[i + 1]).append("'.");
        }
        return out.toString();
    }

    @Test
    public void afterDropTheNextNameOrParenthesisIsNamedToo() {
        assertEquals(lines("5", "T", "7", "y"), answer("DROP T y"));
        assertEquals(lines("5", "T", "7", "y"), answer("DROP T y;"));
        assertEquals(lines("5", "T", "7", "y"), answer("DROP T y z w"));
        assertEquals(lines("5", "T", "7", "y"), answer("DROP T y CASCADE"));
        assertEquals(lines("5", "T", "7", "y"), answer("DROP T y.z"));
        assertEquals(lines("5", "T", "7", "IF"), answer("DROP T IF EXISTS y"));
        assertEquals(lines("5", "T", "7", "\"y\""), answer("DROP T \"y\""));
        assertEquals(lines("5", "T", "7", "("), answer("DROP T ("));
        assertEquals(lines("5", "T", "7", ")"), answer("DROP T )"));
        assertEquals(lines("5", "T", "7", "TRUE"), answer("DROP T TRUE"));
        assertEquals(lines("5", "T", "7", "IDENTIFIER"), answer("DROP T IDENTIFIER('y')"));
        assertEquals(lines("5", "TS", "8", "y"), answer("DROP TS y"));
        assertEquals(lines("5", "D", "7", "y"), answer("DROP D y"));
    }

    @Test
    public void anythingElseAfterTheWordIsNotNamed() {
        assertEquals(lines("5", "T"), answer("DROP T"));
        assertEquals(lines("5", "T"), answer("DROP T ;"));
        assertEquals(lines("5", "T"), answer("DROP T 1"));
        assertEquals(lines("5", "T"), answer("DROP T 'x'"));
        assertEquals(lines("5", "T"), answer("DROP T NULL"));
        assertEquals(lines("5", "T"), answer("DROP T SELECT"));
        assertEquals(lines("5", "T"), answer("DROP T TABLE"));
        assertEquals(lines("5", "T"), answer("DROP T *"));
        assertEquals(lines("5", "T"), answer("DROP T $v"));
        // A dot after the word makes it a qualified class name.
        assertEquals(lines("8", "<EOF>"), answer("DROP T ."));
    }

    @Test
    public void afterShowTheWordStandsAlone() {
        assertEquals(lines("5", "T"), answer("SHOW T"));
        assertEquals(lines("5", "T"), answer("SHOW T y"));
        assertEquals(lines("5", "T"), answer("SHOW T IN ACCOUNT"));
        assertEquals(lines("5", "T"), answer("SHOW T LIKE 'x'"));
        assertEquals("SQL compilation error: Object type or Class 'FOO' does not exist or not authorized.",
            answer("SHOW foo"));
        assertEquals("SQL compilation error: Object type or Class 'FOO' does not exist or not authorized.",
            answer("DROP foo y"));
    }

    @Test
    public void thePluralListingWordsAreKeywordsAfterDrop() {
        assertEquals(lines("5", "ALERTS", "12", "a"), answer("DROP ALERTS a"));
        assertEquals(lines("5", "NOTEBOOKS", "15", "n"), answer("DROP NOTEBOOKS n m"));
        assertEquals(lines("5", "SECRETS", "13", "s"), answer("DROP SECRETS s"));
        assertEquals(lines("5", "MODELS", "12", "m"), answer("DROP MODELS m"));
        assertEquals(lines("5", "LISTINGS", "14", "l"), answer("DROP LISTINGS l"));
        assertEquals(lines("5", "STREAMLITS", "16", "s"), answer("DROP STREAMLITS s"));
        assertEquals(lines("5", "ALERTS"), answer("DROP ALERTS"));
    }

    @Test
    public void aTwoWordKindsFirstWordIsRefusedAtTheWordAfterIt() {
        assertEquals(lines("14", "y"), answer("DROP EXTERNAL y"));
        assertEquals(lines("14", "y"), answer("DROP FAILOVER y"));
        assertEquals(lines("17", "y"), answer("DROP REPLICATION y"));
        assertEquals(lines("13", "<EOF>"), answer("DROP EXTERNAL"));
    }
}
