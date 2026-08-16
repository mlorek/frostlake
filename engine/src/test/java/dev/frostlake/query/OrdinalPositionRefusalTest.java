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
 * The POSITIONAL slot — {@code ORDER BY 2}, {@code GROUP BY 1} — and what happens when the position
 * names nothing.
 *
 * <p>★ THE SLOT TAKES ANY NUMERIC LITERAL, TRUNCATED. {@code ORDER BY 1.5} and {@code ORDER BY 1.9}
 * both sort by the FIRST select item and {@code 2.5} by the second, so the position is the literal
 * truncated toward zero — which also means {@code 0.5} truncates to 0 and is out of range. The
 * exponent spelling counts ({@code 1e0} is position 1) and so does a parenthesized literal, but
 * anything COMPUTED does not: {@code ORDER BY 1 + 0} is a constant that leaves the rows alone. This
 * is the half that had to be measured rather than assumed — Frostlake read only bare digits.
 *
 * <p>★ THE REFUSAL IS THE ORDER-BY-EXPRESSION SENTENCE, echoing the literal AS WRITTEN — {@code [9.5]},
 * not the 9 it truncates to. Frostlake had invented "ORDER BY position 9 is not in select list", a
 * wording live has nowhere. GROUP BY has the same sentence with its own noun.
 *
 * <p>★ A STAR IS ITS COLUMNS, and this was the gap behind two defects at once: an ordinal past a star
 * list was never range-checked (accepted where live refuses) AND an ordinal INSIDE one never resolved,
 * so {@code SELECT * FROM t ORDER BY 2} silently did not sort. Both are the same missing expansion.
 *
 * <p>Left for their own tasks, and deliberately not asserted here: a literal too large for the type
 * is refused by the literal READER live, with a different, positioned sentence; and an unresolvable
 * COLUMN in the select list outranks the ordinal live, where Frostlake reports the ordinal.
 */
public class OrdinalPositionRefusalTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        // Insertion order is NOT sorted order, so a cell that sorts is visibly different from one that
        // leaves the rows alone — the only way to tell a resolved position from an ignored constant.
        engine.execute("CREATE OR REPLACE TABLE ord (a INT, b INT)");
        engine.execute("INSERT INTO ord VALUES (2, 20), (1, 10), (3, 30)");
    }

    /** Every row's every column, joined. */
    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder();
            while (rs.next()) {
                if (all.length() > 0) {
                    all.append(",");
                }
                for (int c = 0; c < rs.getColumns().size(); c++) {
                    if (c > 0) {
                        all.append("/");
                    }
                    all.append(String.valueOf(rs.getValue(c)));
                }
            }
            return all.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String outOfRangeOrderBy(final String literal) {
        return "SQL compilation error:|[" + literal + "] is not a valid order by expression";
    }

    private String outOfRangeGroupBy(final String literal) {
        return "SQL compilation error:|[" + literal + "] is not a valid group by expression";
    }

    /** ★ The out-of-range sentence, and its bracketed echo. */
    @Test
    public void aPositionPastTheSelectListIsRefusedByItsLiteral() {
        assertEquals(outOfRangeOrderBy("9"), answer("SELECT a FROM ord ORDER BY 9"));
        assertEquals(outOfRangeOrderBy("3"), answer("SELECT a, b FROM ord ORDER BY 3"),
            "one past the end is as out of range as nine");
        assertEquals(outOfRangeOrderBy("0"), answer("SELECT a, b FROM ord ORDER BY 0"),
            "the slot is 1-based, so zero names nothing");
    }

    /** A NEGATIVE position is refused, where Frostlake used to sort by the constant and answer. */
    @Test
    public void aNegativePositionIsRefused() {
        assertEquals(outOfRangeOrderBy("-1"), answer("SELECT a, b FROM ord ORDER BY -1"));
        assertEquals(outOfRangeOrderBy("-1"), answer("SELECT a, b FROM ord ORDER BY -1 DESC"),
            "the direction does not rescue it");
        assertEquals(outOfRangeOrderBy("-2147483649"),
            answer("SELECT a FROM ord ORDER BY -2147483649"),
            "and one past an int, which used to throw a raw number-format error");
    }

    /** A position past an INT is still a position, not a parse accident. */
    @Test
    public void aPositionPastAnIntIsStillAPosition() {
        assertEquals(outOfRangeOrderBy("2147483648"), answer("SELECT a FROM ord ORDER BY 2147483648"));
    }

    /** ★ Any numeric literal fills the slot, and the position is the literal TRUNCATED. */
    @Test
    public void theSlotTakesAnyNumericLiteralTruncated() {
        assertEquals("1/10,2/20,3/30", answer("SELECT a, b FROM ord ORDER BY 1"));
        assertEquals("1/10,2/20,3/30", answer("SELECT a, b FROM ord ORDER BY 1.0"));
        assertEquals("1/10,2/20,3/30", answer("SELECT a, b FROM ord ORDER BY 1.5"),
            "1.5 truncates to the FIRST item, not the second");
        assertEquals("1/10,2/20,3/30", answer("SELECT a, b FROM ord ORDER BY 1.9"));
        assertEquals("3/30,2/20,1/10", answer("SELECT a, b FROM ord ORDER BY 2.5 DESC"),
            "and 2.5 to the second");
        assertEquals("1/10,2/20,3/30", answer("SELECT a, b FROM ord ORDER BY 1e0"),
            "the exponent spelling is a literal like any other");
        assertEquals("1/10,2/20,3/30", answer("SELECT a, b FROM ord ORDER BY (1)"),
            "and parentheses do not take it out of the slot");
    }

    /** A truncating literal can truncate itself out of range. */
    @Test
    public void aFractionCanTruncateOutOfRange() {
        assertEquals(outOfRangeOrderBy("0.5"), answer("SELECT a, b FROM ord ORDER BY 0.5"));
        assertEquals(outOfRangeOrderBy("9.5"), answer("SELECT a, b FROM ord ORDER BY 9.5"));
    }

    /** ★ Anything COMPUTED is a constant, not a position — the rows come back untouched. */
    @Test
    public void aComputedKeyIsAConstant() {
        assertEquals("2/20,1/10,3/30", answer("SELECT a, b FROM ord ORDER BY 1 + 0"),
            "insertion order, because a constant orders nothing");
    }

    /** ★ A star counts as its columns, both for the range and for what the position resolves to. */
    @Test
    public void aStarCountsAsItsColumns() {
        assertEquals("1/10,2/20,3/30", answer("SELECT * FROM ord ORDER BY 2"),
            "position 2 is the star's SECOND column, which used to sort nothing at all");
        assertEquals(outOfRangeOrderBy("3"), answer("SELECT * FROM ord ORDER BY 3"),
            "and the two-column star ends at 2");
        assertEquals(outOfRangeOrderBy("3"), answer("SELECT ord.* FROM ord ORDER BY 3"),
            "a qualified star counts the same way");
    }

    /** A star BESIDE an item counts both. */
    @Test
    public void aStarBesideAnItemCountsBoth() {
        assertEquals("1/10/1,2/20/2,3/30/3", answer("SELECT *, a FROM ord ORDER BY 3"));
        assertEquals(outOfRangeOrderBy("4"), answer("SELECT *, a FROM ord ORDER BY 4"));
    }

    /** The refusal does not depend on the query's shape, or on there being rows to sort. */
    @Test
    public void everyShapeOfQueryIsRefusedAlike() {
        assertEquals(outOfRangeOrderBy("9"), answer("SELECT a FROM ord ORDER BY 9 DESC"));
        assertEquals(outOfRangeOrderBy("9"), answer("SELECT a FROM ord ORDER BY 9 NULLS FIRST"));
        assertEquals(outOfRangeOrderBy("9"), answer("SELECT a, b FROM ord ORDER BY 1, 9"),
            "a later key is judged too");
        assertEquals(outOfRangeOrderBy("9"), answer("SELECT DISTINCT a FROM ord ORDER BY 9"));
        assertEquals(outOfRangeOrderBy("9"),
            answer("SELECT a, SUM(b) FROM ord GROUP BY a ORDER BY 9"),
            "a GROUPED query, which used to skip the range check and answer");
        assertEquals(outOfRangeOrderBy("9"),
            answer("SELECT a FROM (SELECT a FROM ord WHERE 1=0) ORDER BY 9"),
            "and an empty input, because the refusal is a compile-time one");
    }

    /** A SET OPERATION's ORDER BY is refused in the same words, over the arms' shared width. */
    @Test
    public void aSetOperationIsRefusedInTheSameWords() {
        assertEquals(outOfRangeOrderBy("9"),
            answer("SELECT a FROM ord UNION ALL SELECT a FROM ord ORDER BY 9"));
        assertEquals(outOfRangeOrderBy("2"),
            answer("SELECT a FROM ord UNION SELECT a FROM ord ORDER BY 2"),
            "one arm column, so position 2 is past the end");
        assertEquals(outOfRangeOrderBy("-1"),
            answer("SELECT a FROM ord UNION ALL SELECT a FROM ord ORDER BY -1"));
        assertEquals("1,1,2,2,3,3",
            answer("SELECT a FROM ord UNION ALL SELECT a FROM ord ORDER BY 1"),
            "and a position that IS in range still sorts");
    }

    /** An unknown FUNCTION does NOT outrank the ordinal — the position speaks first. */
    @Test
    public void theOrdinalOutranksAnUnknownFunction() {
        assertEquals(outOfRangeOrderBy("9"), answer("SELECT nosuchfn(a) FROM ord ORDER BY 9"));
    }

    /** ★ GROUP BY has the same sentence with its own noun. */
    @Test
    public void groupByRefusesItsOwnPositionTheSameWay() {
        assertEquals(outOfRangeGroupBy("9"), answer("SELECT a, SUM(b) FROM ord GROUP BY 9"));
        assertEquals(outOfRangeGroupBy("0"), answer("SELECT a, SUM(b) FROM ord GROUP BY 0"));
        assertEquals(outOfRangeGroupBy("-1"), answer("SELECT a, SUM(b) FROM ord GROUP BY -1"));
        assertEquals(outOfRangeGroupBy("0.5"), answer("SELECT a, SUM(b) FROM ord GROUP BY 0.5"));
        assertEquals(outOfRangeGroupBy("9.5"), answer("SELECT a, SUM(b) FROM ord GROUP BY 9.5"));
        assertEquals(outOfRangeGroupBy("9"), answer("SELECT SUM(b) FROM ord GROUP BY 9"),
            "an aggregate-only select list is one column wide, and used to skip the check entirely");
    }

    /** A SUPER-GROUP's member is a position like any other. */
    @Test
    public void aSuperGroupMemberIsAPositionToo() {
        assertEquals(outOfRangeGroupBy("9"), answer("SELECT a, SUM(b) FROM ord GROUP BY ROLLUP(9)"));
        assertEquals(outOfRangeGroupBy("9"),
            answer("SELECT a, SUM(b) FROM ord GROUP BY GROUPING SETS ((9))"));
    }

    /** A GROUP BY position that IS in range groups by that item, fraction and all. */
    @Test
    public void anInRangeGroupByPositionGroups() {
        assertEquals("1/10,2/20,3/30",
            answer("SELECT a, SUM(b) FROM ord GROUP BY 1 ORDER BY 1"));
        assertEquals("1/10,2/20,3/30",
            answer("SELECT a, SUM(b) FROM ord GROUP BY 1.5 ORDER BY 1"),
            "1.5 groups by the first item, exactly as 1 does");
    }

    /**
     * Inside an OVER clause a number is a CONSTANT, not a position — neither engine range-checks it,
     * and 9 sits there quite happily beside a two-column select list.
     *
     * <p>Only the shapes whose answer does NOT depend on the constant key are asserted: an unpartitioned
     * {@code OVER (ORDER BY 9)} numbers the rows in whatever order they arrive, which nothing fixes.
     */
    @Test
    public void aNumberInsideOverIsNotAPosition() {
        assertEquals("1/1,2/2,3/3",
            answer("SELECT a, ROW_NUMBER() OVER (PARTITION BY 9 ORDER BY a) r FROM ord ORDER BY 1"),
            "one constant partition, ordered by a real column");
        assertEquals("1/1,2/1,3/1",
            answer("SELECT a, ROW_NUMBER() OVER (PARTITION BY a ORDER BY 9) r FROM ord ORDER BY 1"),
            "and a partition per row, where the constant key has nothing left to decide");
    }
}
