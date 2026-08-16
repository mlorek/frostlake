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
 * The LIMIT / OFFSET value slot's syntax refusals, and the one place live STACKS a second line.
 *
 * <p>★ THE DOUBLING IS CLAUSE-SPECIFIC, WHICH IS WHY THE LISTENER WAS LEFT ALONE. Live prints
 * {@code syntax error line 1 at position 23 unexpected '-'.} TWICE for {@code LIMIT -1}, and the
 * obvious reading — that Frostlake's listener drops stacked lines everywhere — is wrong. Of a
 * twenty-four-cell spread only the LIMIT/OFFSET VALUE slot doubles: an operator there
 * ({@code -1}, {@code +1}, {@code *}) stacks, while {@code LIMIT 'x'} does not, nor does a stray
 * {@code *} in WHERE, a doubled FROM, or an error running to EOF. Changing the listener would have
 * doubled every one of those, so the divergence is RECORDED rather than fixed: these cells assert the
 * FIRST line, which both engines share, and that is all either engine is asked for.
 *
 * <p>★ THE SECOND LINE IS NOT ALWAYS A DUPLICATE. {@code LIMIT (-1)} stacks two DIFFERENT lines, the
 * second pointing at an EARLIER position — {@code unexpected '-'} at 24 then {@code unexpected '('} at
 * 23 — the same backwards shape an unclosed call produces. So live is reporting two failed parse
 * attempts, not printing one error twice, and a rule that merely duplicated a line would be wrong even
 * for the LIMIT slot.
 *
 * <p>Left for its own task: {@code LIMIT NULL} without an intervening clause, {@code OFFSET NULL} and
 * {@code FETCH … NULL …}, all of which live accepts and Frostlake refuses — a parse ambiguity with the
 * bare table alias, not a stacking question.
 */
public class LimitSlotSyntaxTest extends BaseDatabaseTest {

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

    private String unexpectedAt(final int position, final String token) {
        return "SQL compilation error:|syntax error line 1 at position " + position
            + " unexpected '" + token + "'.";
    }

    /** ★ The doubled cell: only its FIRST line is asserted, because live stacks a second copy. */
    @Test
    public void anOperatorInTheLimitSlotIsRefusedAtItsOwnToken() {
        assertTrue(answer("SELECT a FROM ls LIMIT -1").startsWith(unexpectedAt(23, "-")),
            answer("SELECT a FROM ls LIMIT -1"));
        assertTrue(answer("SELECT a FROM ls LIMIT -2").startsWith(unexpectedAt(23, "-")),
            answer("SELECT a FROM ls LIMIT -2"));
        assertTrue(answer("SELECT a FROM ls LIMIT +1").startsWith(unexpectedAt(23, "+")),
            answer("SELECT a FROM ls LIMIT +1"));
        assertTrue(answer("SELECT a FROM ls LIMIT *").startsWith(unexpectedAt(23, "*")),
            answer("SELECT a FROM ls LIMIT *"));
    }

    /** The anchor follows the statement, and a trailing OFFSET does not move it. */
    @Test
    public void theAnchorFollowsTheStatement() {
        assertTrue(answer("SELECT a FROM ls LIMIT -1 OFFSET 1").startsWith(unexpectedAt(23, "-")),
            answer("SELECT a FROM ls LIMIT -1 OFFSET 1"));
        assertTrue(answer("SELECT nosuchfn(a) FROM ls LIMIT -1").startsWith(unexpectedAt(33, "-")),
            "a syntax error outranks the unknown function name");
        assertTrue(answer("SELECT a FROM ls OFFSET -1").startsWith(unexpectedAt(24, "-")),
            answer("SELECT a FROM ls OFFSET -1"));
    }

    /** ★ A parenthesized negative stacks two DIFFERENT lines live, so only the first is asserted. */
    @Test
    public void aparenthesizedNegativeAnchorsOnTheSign() {
        assertTrue(answer("SELECT a FROM ls LIMIT (-1)").startsWith(unexpectedAt(24, "-")),
            answer("SELECT a FROM ls LIMIT (-1)"));
    }

    /** ★ A NON-operator in the same slot does NOT double — the whole message is asserted. */
    @Test
    public void anonOperatorInTheSameSlotIsOneLine() {
        assertEquals(unexpectedAt(23, "'x'"), answer("SELECT a FROM ls LIMIT 'x'"),
            "a string in the LIMIT slot is refused once, in the same clause that doubles for '-'");
    }

    /** ★ And a negative OFFSET after a LIMIT is one line, where a bare OFFSET's is two. */
    /**
     * ★ A GROUP WHOSE OPENING PAREN WAS REFUSED SAYS NOTHING MORE. Live names the '(' and stops,
     * whatever sits inside the brackets — a count, a NULL, a column, an empty pair, a string, a
     * nested pair or nothing at all — and whatever follows them. Frostlake used to name the inner
     * token as a second line.
     */
    @Test
    public void aparenthesisedCountIsOneLine() {
        assertEquals(unexpectedAt(34, "("), answer("SELECT a FROM ls ORDER BY a LIMIT (2)"));
        assertEquals(unexpectedAt(34, "("), answer("SELECT a FROM ls ORDER BY a LIMIT (NULL)"));
        assertEquals(unexpectedAt(34, "("), answer("SELECT a FROM ls ORDER BY a LIMIT (a)"));
        assertEquals(unexpectedAt(34, "("), answer("SELECT a FROM ls ORDER BY a LIMIT ()"));
        assertEquals(unexpectedAt(34, "("), answer("SELECT a FROM ls ORDER BY a LIMIT ('2')"));
        assertEquals(unexpectedAt(34, "("), answer("SELECT a FROM ls ORDER BY a LIMIT ((2))"));
        assertEquals(unexpectedAt(34, "("), answer("SELECT a FROM ls ORDER BY a LIMIT (2"));
        assertEquals(unexpectedAt(43, "("), answer("SELECT a FROM ls ORDER BY a LIMIT 2 OFFSET (1)"));
    }

    /** …and what FOLLOWS the brackets does not bring the second line back either. */
    @Test
    public void whatFollowsTheGroupIsSilentToo() {
        assertEquals(unexpectedAt(34, "("), answer("SELECT a FROM ls ORDER BY a LIMIT (2) OFFSET 1"));
        assertEquals(unexpectedAt(34, "("), answer("SELECT a FROM ls ORDER BY a LIMIT (2) zz"));
        assertEquals(unexpectedAt(34, "("), answer("SELECT a FROM ls ORDER BY a LIMIT (2);"));
        assertEquals(unexpectedAt(34, "("), answer("SELECT a FROM ls ORDER BY a LIMIT (2 OFFSET 1"));
    }

    /**
     * ★ BUT IT IS THE GROUP THAT GOES QUIET, NOT THE STATEMENT. A fault OUTSIDE the refused brackets
     * still speaks: live stacks two lines here, naming the inner '(' and then the ')' left over after
     * that group closes. This is the cell that forbids the blunter "report nothing further" rule.
     */
    @Test
    public void afaultOutsideTheGroupStillSpeaks() {
        assertEquals(unexpectedAt(25, "(") + "|syntax error line 1 at position 29 unexpected ')'.",
            answer("SELECT a FROM ls SAMPLE ((10))"));
    }

    @Test
    public void anegativeOffsetAfterAlimitIsOneLine() {
        assertEquals(unexpectedAt(32, "-"), answer("SELECT a FROM ls LIMIT 1 OFFSET -1"));
        assertEquals(unexpectedAt(29, "-"), answer("SELECT a FROM ls FETCH FIRST -1 ROWS ONLY"),
            "and so is a negative FETCH count");
    }

    /** ★ Syntax errors ELSEWHERE are single lines on both engines — the reason the listener stands. */
    @Test
    public void everyOtherSyntaxErrorIsOneLine() {
        assertEquals(unexpectedAt(23, "*"), answer("SELECT a FROM ls WHERE * = 1"));
        assertEquals(unexpectedAt(14, "FROM"), answer("SELECT a FROM FROM ls"));
        assertEquals(unexpectedAt(22, "<EOF>"), answer("SELECT a FROM ls WHERE"));
    }

    /** TOP is not a keyword before a negative — it reads as a column, and both engines say so. */
    @Test
    public void topBeforeAnegativeIsAcolumn() {
        assertEquals("SQL compilation error: error line 1 at position 7|invalid identifier 'TOP'",
            answer("SELECT TOP -1 a FROM ls"));
    }

    /** The same negative literal where it is LEGAL is untouched. */
    @Test
    public void anegativeLiteralElsewhereIsFine() {
        assertEquals("ACCEPTED: -1", answer("SELECT -1"));
        assertEquals("ACCEPTED: 1 2", answer("SELECT a FROM ls WHERE a > -1 ORDER BY a"));
        assertEquals("ACCEPTED: 1", answer("SELECT a FROM ls ORDER BY a LIMIT 1"));
    }

    /** ★ A bare LIMIT is a table ALIAS on both engines, which is the ambiguity behind the NULL gap. */
    @Test
    public void abareLimitIsAtableAlias() {
        assertEquals("ACCEPTED: 1 2", answer("SELECT a FROM ls LIMIT ORDER BY a"));
    }
}
