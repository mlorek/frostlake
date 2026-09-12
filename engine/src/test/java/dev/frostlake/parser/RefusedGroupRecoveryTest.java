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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What live reports AFTER a refused bracket group, measured shape by shape — the one question the
 * refused-group rule could not answer from two statements alone:
 *
 * <ul>
 *   <li>a refused '(' in the FETCH count slot is the LAST line of its statement: {@code FETCH FIRST
 *       (2) ROWS ONLY}, {@code ROW ONLY}, a bare {@code ROWS}, nothing at all, junk, a stray ')', a
 *       WHERE, a newline before the ROWS, a second statement after a ';' — every one is the '(' line
 *       alone, because live resyncs past the group to the clause's own keywords and reads the rest
 *       as written;</li>
 *   <li>a stray ')' that closes the last open group is the last line too: {@code SAMPLE ((10))} names
 *       the inner '(' and the leftover ')', and a LIMIT 1, a WHERE, an ORDER BY or junk after that
 *       add nothing;</li>
 *   <li>an error INSIDE a bracketed LIMIT or OFFSET count resyncs at the next OFFSET — {@code LIMIT
 *       (2) OFFSET (1)} still names the second '(' — and consumes a ROWS or a ')' silently.</li>
 * </ul>
 *
 * <p>Frostlake used to name the ROWS after a refused FETCH group, the LIMIT after SAMPLE's stray ')',
 * and the ROWS or ')' after a LIMIT group.
 *
 * <p>NOT COVERED: the BACKWARDS second line live stacks for a bracketed LIMIT count with no ORDER BY —
 * {@code LIMIT (2)} is "unexpected '2'" at 24 and then "unexpected '('" at 23, a second failed parse
 * attempt this listener does not produce (LimitSlotSyntaxTest records it); those cells assert their
 * first line and the absence of the wrong second one.
 */
public class RefusedGroupRecoveryTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE ls (a INT, b INT)");
        engine.execute("INSERT INTO ls VALUES (1, 10), (2, 20)");
    }

    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder("ACCEPTED:");
            while (rs.next()) {
                all.append(" ").append(String.valueOf(rs.getValue(0)));
            }
            return all.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String unexpectedAt(final int position, final String token) {
        return "SQL compilation error:|syntax error line 1 at position " + position
            + " unexpected '" + token + "'.";
    }

    /** ★ The FETCH count slot: one line, whatever follows the group. */
    @Test
    public void aRefusedFetchCountGroupIsTheLastLine() {
        assertEquals(unexpectedAt(40, "("), answer("SELECT a FROM ls ORDER BY a FETCH FIRST (2) ROWS ONLY"));
        assertEquals(unexpectedAt(40, "("), answer("SELECT a FROM ls ORDER BY a FETCH FIRST (2)"));
        assertEquals(unexpectedAt(34, "("), answer("SELECT a FROM ls ORDER BY a FETCH (2) ROWS ONLY"));
        assertEquals(unexpectedAt(40, "("), answer("SELECT a FROM ls ORDER BY a FETCH FIRST (2) ROW ONLY"));
        assertEquals(unexpectedAt(53, "("), answer("SELECT a FROM ls ORDER BY a OFFSET 1 ROWS FETCH NEXT (2) ROWS ONLY"));
        assertEquals(unexpectedAt(40, "("), answer("SELECT a FROM ls ORDER BY a FETCH FIRST (2) ROWS"));
        assertEquals(unexpectedAt(40, "("), answer("SELECT a FROM ls ORDER BY a FETCH FIRST (2) zz"));
        assertEquals(unexpectedAt(40, "("), answer("SELECT a FROM ls ORDER BY a FETCH FIRST (2) ONLY"));
        assertEquals(unexpectedAt(40, "("), answer("SELECT a FROM ls ORDER BY a FETCH FIRST (2) )"), "a stray ')' too");
        assertEquals(unexpectedAt(40, "("), answer("SELECT a FROM ls ORDER BY a FETCH FIRST (2) WHERE a = 1"));
        assertEquals(unexpectedAt(40, "("), answer("SELECT a FROM ls ORDER BY a FETCH FIRST (2) ROWS ONLY; SELECT 1"),
            "and the statement after the separator adds nothing");
        assertEquals(unexpectedAt(40, "("), answer("SELECT a FROM ls ORDER BY a FETCH FIRST (2)\nROWS ONLY"));
        assertEquals(unexpectedAt(40, "("), answer("SELECT a FROM ls ORDER BY a FETCH FIRST (-2) ROWS ONLY"));
        assertEquals(unexpectedAt(40, "-"), answer("SELECT a FROM ls ORDER BY a FETCH FIRST -2 ROWS ONLY"),
            "an unbracketed operator is named itself");
        assertEquals("ACCEPTED: 1 2", answer("SELECT a FROM ls ORDER BY a FETCH FIRST 2 ROWS ONLY"));
    }

    /** ★ SAMPLE ((10)): the inner '(' and the leftover ')', and nothing after that. */
    @Test
    public void aStrayClosingParenIsTheLastLine() {
        final String twoLines = unexpectedAt(25, "(") + "|syntax error line 1 at position 29 unexpected ')'.";
        assertEquals(twoLines, answer("SELECT a FROM ls SAMPLE ((10))"));
        assertEquals(twoLines, answer("SELECT a FROM ls SAMPLE ((10)) zz"));
        assertEquals(twoLines, answer("SELECT a FROM ls SAMPLE ((10)) WHERE a = 1"));
        assertEquals(twoLines, answer("SELECT a FROM ls SAMPLE ((10)) ROWS"));
        assertEquals(twoLines, answer("SELECT a FROM ls SAMPLE ((10)) ORDER BY a"));
        assertEquals(twoLines, answer("SELECT a FROM ls SAMPLE ((10)) ) zz"));
        assertEquals(twoLines, answer("SELECT a FROM ls SAMPLE ((10)) LIMIT 1"), "the LIMIT's count is not named");
        assertEquals(unexpectedAt(32, "("), answer("SELECT a FROM ls LIMIT 2 OFFSET (1)"));
        assertEquals(unexpectedAt(32, "("), answer("SELECT a FROM ls LIMIT 2 OFFSET (1) zz"));
    }

    /** The bracketed LIMIT count with no ORDER BY: the first line, and no line for what follows the group. */
    @Test
    public void anErrorInsideALimitGroupConsumesWhatFollows() {
        for (final String tail : new String[] {"", " OFFSET 1", " zz", " ROWS", " )"}) {
            final String refusal = answer("SELECT a FROM ls LIMIT (2)" + tail);
            assertTrue(refusal.startsWith(unexpectedAt(24, "2")), refusal);
            assertFalse(refusal.contains("position 27"), "nothing after the group is named: " + refusal);
        }
        final String offsetGroup = answer("SELECT a FROM ls LIMIT (2) OFFSET (1)");
        assertTrue(offsetGroup.startsWith(unexpectedAt(24, "2")), offsetGroup);
        assertTrue(offsetGroup.contains("position 34 unexpected '('"), "the OFFSET's own group is still named: " + offsetGroup);
        assertTrue(answer("SELECT a FROM ls LIMIT (-1)").startsWith(unexpectedAt(24, "-")));
        assertEquals("SQL compilation error:|Unknown function TOP.", answer("SELECT TOP (2) a FROM ls"),
            "a bracketed TOP is not a syntax error at all");
    }
}
