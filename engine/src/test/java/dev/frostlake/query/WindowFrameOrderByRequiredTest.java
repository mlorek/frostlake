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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A window FRAME written with no ORDER BY beside it. Frostlake answered these — which is the direction
 * that matters most, since it accepted SQL a real account refuses:
 *
 * <pre>
 *   AVG(a) OVER (ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)
 *       was 2.33333333          live  Window frame requires an ORDER BY clause.
 * </pre>
 *
 * <p>THE RULE IS ABOUT THE FRAME, NOT THE FUNCTION. Every frame spelling offends — ROWS and RANGE,
 * BETWEEN and the bare {@code UNBOUNDED PRECEDING} / {@code 2 PRECEDING} / {@code CURRENT ROW} forms —
 * and every function does: AVG, SUM, COUNT(*), MIN, ARRAY_AGG and the ranking family alike. A
 * PARTITION BY does not satisfy it; only an ORDER BY does. A frame BESIDE an ORDER BY is legal on all
 * of them, ranking functions included.
 *
 * <p>IT OUTRANKS EVERYTHING BUT A SYNTAX ERROR. Measured against each neighbour with the other problem
 * written first, it beats the ranking family's own missing-ORDER-BY sentence, an invalid identifier in
 * the PARTITION BY key, an unknown function name, an ungrouped select item — and even
 * {@code Object 'NOSUCHTABLE' does not exist}. Nothing has to be looked up to see it, and live
 * evidently looks at nothing, so Frostlake raises it before the relation is resolved.
 *
 * <p>THE POSITION IS THE OVER KEYWORD'S, not the frame's, though the frame is what offends. That is
 * asserted at eleven different offsets below rather than at one, because a single statement cannot
 * tell an OVER-anchored rule from a call-anchored or clause-anchored one.
 */
public class WindowFrameOrderByRequiredTest extends BaseDatabaseTest {

    private static final String FULL = "ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING";
    private static final String SENTENCE = "Window frame requires an ORDER BY clause.";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE aw (n380 NUMBER(38,0), n102 NUMBER(10,2))");
        engine.execute("INSERT INTO aw VALUES (1, 1.00), (2, 2.00), (4, 4.00)");
    }

    /** The refusal, newlines flattened; "accepted" plus the first column when it reads. */
    private String outcome(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            return rs.next() ? "accepted " + String.valueOf(rs.getValue(0)) : "accepted";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** The refusal for a window over the fixture, expected at {@code position}. */
    private void refusedAt(final int position, final String sql) {
        assertEquals("SQL compilation error: error line 1 at position " + position + "|" + SENTENCE,
            outcome(sql), sql);
    }

    /** Every FRAME spelling is refused, ROWS and RANGE alike. */
    @Test
    public void everyFrameSpellingIsRefused() {
        refusedAt(17, "SELECT AVG(n102) OVER (" + FULL + ") FROM aw");
        refusedAt(17, "SELECT AVG(n102) OVER (ROWS UNBOUNDED PRECEDING) FROM aw");
        refusedAt(17, "SELECT AVG(n102) OVER (ROWS 2 PRECEDING) FROM aw");
        refusedAt(17, "SELECT AVG(n102) OVER (ROWS CURRENT ROW) FROM aw");
        refusedAt(17, "SELECT AVG(n102) OVER (ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING) FROM aw");
        refusedAt(17,
            "SELECT AVG(n102) OVER (ROWS BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING) FROM aw");
        refusedAt(17, "SELECT AVG(n102) OVER (RANGE BETWEEN UNBOUNDED PRECEDING"
            + " AND UNBOUNDED FOLLOWING) FROM aw");
        refusedAt(17, "SELECT AVG(n102) OVER (RANGE UNBOUNDED PRECEDING) FROM aw");
        refusedAt(17, "SELECT AVG(n102) OVER (RANGE CURRENT ROW) FROM aw");
    }

    /** A PARTITION BY does not satisfy it — only an ORDER BY does. */
    @Test
    public void aPartitionByDoesNotSatisfyIt() {
        refusedAt(17, "SELECT AVG(n102) OVER (PARTITION BY n380 " + FULL + ") FROM aw");
    }

    /** Whatever function is spelled over the frame, the answer is the same. */
    @Test
    public void everyFunctionIsRefusedAlike() {
        refusedAt(17, "SELECT SUM(n102) OVER (" + FULL + ") FROM aw");
        refusedAt(16, "SELECT COUNT(*) OVER (" + FULL + ") FROM aw");
        refusedAt(17, "SELECT MIN(n102) OVER (" + FULL + ") FROM aw");
        refusedAt(23, "SELECT ARRAY_AGG(n102) OVER (" + FULL + ") FROM aw");
        refusedAt(20, "SELECT ROW_NUMBER() OVER (" + FULL + ") FROM aw");
        refusedAt(25, "SELECT FIRST_VALUE(n102) OVER (" + FULL + ") FROM aw");
    }

    /** That a statement READS, without pinning the value — see the note on the AVG cells below. */
    private void accepted(final String sql) {
        assertEquals("accepted", outcome(sql).replaceAll("^accepted .*", "accepted"), sql);
    }

    /** The legal shapes, which must keep reading. */
    @Test
    public void aFrameBesideAnOrderByIsLegalOnEverything() {
        assertEquals("accepted 1", outcome(
            "SELECT ROW_NUMBER() OVER (ORDER BY n380 " + FULL + ") FROM aw"));
        assertEquals("accepted 1", outcome("SELECT RANK() OVER (ORDER BY n380 " + FULL + ") FROM aw"));
        assertEquals("accepted 1", outcome("SELECT NTILE(2) OVER (ORDER BY n380 " + FULL + ") FROM aw"));
        assertEquals("accepted null", outcome(
            "SELECT LAG(n102) OVER (ORDER BY n380 " + FULL + ") FROM aw"));
        assertEquals("accepted 1.00", outcome(
            "SELECT FIRST_VALUE(n102) OVER (ORDER BY n380 " + FULL + ") FROM aw"));
        accepted("SELECT AVG(n102) OVER (PARTITION BY n380 ORDER BY n102 " + FULL + ") FROM aw");
    }

    /**
     * And a window with NO frame is untouched, whatever else its specification carries.
     *
     * <p>The two AVG cells here assert only that they READ. Their VALUE comes back at eight decimals
     * where live gives five, because a windowed AVG whose OVER carries no bare ORDER BY declares
     * NUMBER(25,5) and the value is not presented at that scale — which is nothing to do with frames
     * and is tracked on its own. The bare-ORDER-BY cell, whose declared scale IS eight, agrees, and it
     * is asserted exactly for that reason: it shows the two engines differ only where the scale does.
     */
    @Test
    public void aFramelessWindowIsUntouched() {
        accepted("SELECT AVG(n102) OVER () FROM aw");
        accepted("SELECT AVG(n102) OVER (PARTITION BY n380) FROM aw");
        assertEquals("accepted 1.00000000", outcome("SELECT AVG(n102) OVER (ORDER BY n380) FROM aw"));
    }

    /**
     * It beats every neighbouring refusal. Each of these carries a SECOND problem, written first, that
     * would otherwise have spoken.
     */
    @Test
    public void itOutranksEveryNeighbour() {
        refusedAt(20, "SELECT ROW_NUMBER() OVER (" + FULL + ") FROM aw",
            "the ranking family's own missing-ORDER-BY sentence");
        refusedAt(17, "SELECT AVG(n102) OVER (PARTITION BY nosuchcol " + FULL + ") FROM aw",
            "an invalid identifier, which outranks nearly everything else there is");
        refusedAt(33, "SELECT nosuchfn(n102), AVG(n102) OVER (" + FULL + ") FROM aw",
            "an unknown function name");
        refusedAt(23, "SELECT n380, AVG(n102) OVER (" + FULL + ") FROM aw GROUP BY n102",
            "an ungrouped select item");
        refusedAt(17, "SELECT AVG(n102) OVER (" + FULL + ") FROM nosuchtable",
            "and a relation that does not exist — nothing is resolved before this is seen");
    }

    /** Overload carrying a note. */
    private void refusedAt(final int position, final String sql, final String note) {
        assertEquals("SQL compilation error: error line 1 at position " + position + "|" + SENTENCE,
            outcome(sql), note);
    }

    /** Only a SYNTAX error still wins, which is the one thing that runs earlier than anything. */
    @Test
    public void onlyASyntaxErrorStillWins() {
        assertEquals("SQL compilation error:|syntax error line 1 at position 94 unexpected '<EOF>'.",
            outcome("SELECT AVG(n102) OVER (" + FULL + ") FROM aw WHERE"));
    }

    /** The position follows the OVER keyword wherever it moves, including onto a second line. */
    @Test
    public void thePositionIsTheOverKeywordsOwn() {
        refusedAt(23, "SELECT n380, AVG(n102) OVER (" + FULL + ") FROM aw");
        refusedAt(17, "SELECT AVG(n102) OVER (" + FULL + ") AS x FROM aw");
        refusedAt(21, "SELECT ABS(AVG(n102) OVER (" + FULL + ")) FROM aw");
        refusedAt(38, "SELECT n380 FROM aw QUALIFY AVG(n102) OVER (" + FULL + ") > 0");
        refusedAt(39, "SELECT n380 FROM aw ORDER BY AVG(n102) OVER (" + FULL + ")");
        refusedAt(32, "SELECT * FROM (SELECT AVG(n102) OVER (" + FULL + ") x FROM aw) s");
        assertEquals("SQL compilation error: error line 2 at position 12|" + SENTENCE,
            outcome("SELECT\n  AVG(n102) OVER (" + FULL + ")\nFROM aw"),
            "a second LINE moves the line number as well as the column");
    }

    /** An ORDER BY on the OUTER query is not the window's own, and does not excuse it. */
    @Test
    public void anOuterOrderByDoesNotCount() {
        refusedAt(17, "SELECT AVG(n102) OVER (" + FULL + ") FROM aw ORDER BY n380");
    }
}
