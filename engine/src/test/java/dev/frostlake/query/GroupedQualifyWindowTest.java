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
 * A QUALIFY whose window is written INLINE, over grouped rows. Two defects, one root:
 *
 * <pre>
 *   SELECT a, ROW_NUMBER() OVER (ORDER BY SUM(b)) … GROUP BY a QUALIFY &lt;the same window&gt; &gt; 1
 *       was  2/1, 3/3     — the wrong ROWS survived, each carrying its own correct number
 *       live 1/2, 3/3
 *   … QUALIFY RANK() OVER (ORDER BY SUM(b)) &gt; 1
 *       was  EMPTY
 *   SELECT a … QUALIFY LAG(SUM(b)) OVER (ORDER BY SUM(b)) IS NOT NULL
 *       was  REFUSED, "invalid identifier 'B'"
 * </pre>
 *
 * <p>THE SAME MISSING RESOLVER, A THIRD TIME. A window's raw aggregate is computed over each row's
 * SOURCE GROUP, and QUALIFY ran with no group resolver installed and over rows the window projection
 * had already REBUILT — so the identity-keyed group map no longer answered for them either. The key
 * {@code SUM(b)} therefore evaluated to NULL on every row, and a ranking over all-equal keys numbers
 * rows in the order it received them, which is GROUP order.
 *
 * <p>That is why the wrong answer looked like an answer. Row numbers 1..n were all present and each
 * surviving row carried its own correct value; only the PAIRING was off, because the predicate had
 * judged position i by the value that belongs to the i-th row in WINDOW order. RANK is the tell: its
 * ties are all 1, so {@code &gt; 1} filtered everything out and the query answered nothing at all.
 *
 * <p>The refusal is the same base-vs-projected confusion one call site over: an inline window's
 * ARGUMENTS were scoped against the SELECT-list shape, where a raw aggregate's column does not exist.
 *
 * <p>THE FIXTURE, AND WHAT IT IS NOT. Both tables hold groups summing to 30, 10 and 50 — {@code gw}
 * one row per group, {@code gc} three, two and one. They answer IDENTICALLY on both engines, before
 * and after, so group CARDINALITY is not what this turns on. What discriminates is that the sums do
 * not ASCEND in group order: group 2 sorts first. Had they ascended, group order and window order
 * would coincide and every cell here would have agreed while the bug sat untouched.
 */
public class GroupedQualifyWindowTest extends BaseDatabaseTest {

    private static final String WIN = "ROW_NUMBER() OVER (ORDER BY SUM(b))";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE gw (a INT, b INT)");
        engine.execute("INSERT INTO gw VALUES (1, 30), (2, 10), (3, 50)");
        engine.execute("CREATE OR REPLACE TABLE gc (a INT, b INT)");
        engine.execute("INSERT INTO gc VALUES (1, 10), (1, 10), (1, 10), (2, 5), (2, 5), (3, 50)");
    }

    /** Every row's every column, joined. */
    private String answer(final String sql) {
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
    }

    /** The same answer from both fixtures — the cardinality control. */
    private void bothFixtures(final String expected, final String sqlWithTable) {
        assertEquals(expected, answer(sqlWithTable.replace("<t>", "gw")), "one row per group");
        assertEquals(expected, answer(sqlWithTable.replace("<t>", "gc")), "three, two and one");
    }

    /** The window's own values, unfiltered — what every QUALIFY below is filtering. */
    @Test
    public void theWindowValuesThemselves() {
        bothFixtures("1/2,2/1,3/3",
            "SELECT a, " + WIN + " FROM <t> GROUP BY a ORDER BY a");
    }

    /** The surviving ROWS, which is what was wrong: 2/1 and 3/3 kept the wrong pair. */
    @Test
    public void theRightRowsSurvive() {
        bothFixtures("1/2,3/3",
            "SELECT a, " + WIN + " FROM <t> GROUP BY a QUALIFY " + WIN + " > 1 ORDER BY a");
        bothFixtures("2,3",
            "SELECT " + WIN + " FROM <t> GROUP BY a QUALIFY " + WIN + " > 1",
            "the numbers alone — a select list carrying nothing BUT the window");
        bothFixtures("2,3",
            "SELECT " + WIN + " FROM <t> GROUP BY a QUALIFY " + WIN + " > 1 ORDER BY a");
        bothFixtures("30,50",
            "SELECT SUM(b) FROM <t> GROUP BY a QUALIFY " + WIN + " > 1 ORDER BY a",
            "and a select list carrying neither the window nor the group key");
    }

    /** Overload carrying a note, so a cell can say what it is for. */
    private void bothFixtures(final String expected, final String sqlWithTable, final String note) {
        assertEquals(expected, answer(sqlWithTable.replace("<t>", "gw")), note);
        assertEquals(expected, answer(sqlWithTable.replace("<t>", "gc")), note);
    }

    /** Each ranking, and each comparison — RANK is the one that answered NOTHING. */
    @Test
    public void everyRankingAndEveryComparison() {
        bothFixtures("1/2,3/3", "SELECT a, RANK() OVER (ORDER BY SUM(b)) FROM <t> GROUP BY a"
            + " QUALIFY RANK() OVER (ORDER BY SUM(b)) > 1 ORDER BY a",
            "every key was NULL, so every rank tied at 1 and > 1 kept no row at all");
        bothFixtures("1/2,3/3", "SELECT a, DENSE_RANK() OVER (ORDER BY SUM(b)) FROM <t> GROUP BY a"
            + " QUALIFY DENSE_RANK() OVER (ORDER BY SUM(b)) > 1 ORDER BY a");
        bothFixtures("1", "SELECT a FROM <t> GROUP BY a"
            + " QUALIFY NTILE(3) OVER (ORDER BY SUM(b)) = 2 ORDER BY a");
        bothFixtures("3/3",
            "SELECT a, " + WIN + " FROM <t> GROUP BY a QUALIFY " + WIN + " > 2 ORDER BY a");
        bothFixtures("2/1",
            "SELECT a, " + WIN + " FROM <t> GROUP BY a QUALIFY " + WIN + " = 1 ORDER BY a",
            "= 1 is the cell that kept the FIRST group where live keeps the smallest sum");
        bothFixtures("1/2,2/3", "SELECT a, ROW_NUMBER() OVER (ORDER BY SUM(b) DESC) FROM <t>"
            + " GROUP BY a QUALIFY ROW_NUMBER() OVER (ORDER BY SUM(b) DESC) > 1 ORDER BY a",
            "DESC reverses which rows survive, so it cannot agree by coincidence");
    }

    /** A raw aggregate as the window's ARGUMENT — refused outright before, at the column's offset. */
    @Test
    public void aRawAggregateArgumentIsInScope() {
        bothFixtures("1,3", "SELECT a FROM <t> GROUP BY a"
            + " QUALIFY LAG(SUM(b)) OVER (ORDER BY SUM(b)) IS NOT NULL ORDER BY a");
        bothFixtures("1,2,3", "SELECT a FROM <t> GROUP BY a"
            + " QUALIFY SUM(SUM(b)) OVER () = 90 ORDER BY a");
        bothFixtures("1,2,3", "SELECT a FROM <t> GROUP BY a"
            + " QUALIFY ROW_NUMBER() OVER (PARTITION BY MIN(b) ORDER BY SUM(b)) = 1 ORDER BY a",
            "and a raw aggregate as the PARTITION key, which each group answers alone");
    }

    /** A super-group's rows filter by the same rule, subtotal included. */
    @Test
    public void superGroupRowsQualifyToo() {
        assertEquals("1/2,3/3,null/4", answer(
            "SELECT a, " + WIN + " FROM gw GROUP BY ROLLUP(a) QUALIFY " + WIN + " > 1 ORDER BY a"),
            "the grand total sorts last on 90 and survives");
        bothFixtures("1,3", "SELECT a FROM <t> GROUP BY GROUPING SETS ((a))"
            + " QUALIFY " + WIN + " > 1 ORDER BY a");
    }

    /** The predicate's other halves: a plain column, a second window, HAVING, an alias key. */
    @Test
    public void thePredicateComposes() {
        bothFixtures("1", "SELECT a FROM <t> GROUP BY a"
            + " QUALIFY " + WIN + " > 1 AND a < 3 ORDER BY a");
        bothFixtures("1", "SELECT a FROM <t> GROUP BY a QUALIFY " + WIN + " > 1"
            + " AND RANK() OVER (ORDER BY SUM(b) DESC) > 1 ORDER BY a");
        bothFixtures("3", "SELECT a FROM <t> GROUP BY a HAVING SUM(b) > 10"
            + " QUALIFY " + WIN + " > 1 ORDER BY a",
            "the window runs over what HAVING left, so only two rows are numbered");
        bothFixtures("1/30,3/50", "SELECT a, SUM(b) AS s FROM <t> GROUP BY a"
            + " QUALIFY ROW_NUMBER() OVER (ORDER BY s) > 1 ORDER BY a",
            "the key may name the aggregate's ALIAS instead of repeating it");
        bothFixtures("90", "SELECT SUM(b) FROM <t>"
            + " QUALIFY ROW_NUMBER() OVER (ORDER BY SUM(b)) = 1",
            "and implicit aggregation, whose single row is its own group");
    }

    /** What already worked and must not move — including the two shapes that hid the bug. */
    @Test
    public void theUnaffectedShapesAreUnchanged() {
        bothFixtures("1/30/2,3/50/3", "SELECT a, SUM(b), " + WIN + " FROM <t> GROUP BY a"
            + " QUALIFY " + WIN + " > 1 ORDER BY a",
            "projecting SUM(b) made the key resolve POSITIONALLY, so this cell was always right");
        bothFixtures("1/30,3/50", "SELECT a, SUM(b) FROM <t> GROUP BY a"
            + " QUALIFY " + WIN + " > 1 ORDER BY a",
            "and so was this one, for the same reason");
        assertEquals("1/30,3/50", answer("SELECT a, b FROM gw"
            + " QUALIFY ROW_NUMBER() OVER (ORDER BY b) > 1 ORDER BY a, b"),
            "an UNGROUPED QUALIFY never had a group to resolve against");
        assertEquals("1/10,1/10,1/10,2/5,3/50", answer("SELECT a, b FROM gc"
            + " QUALIFY ROW_NUMBER() OVER (ORDER BY b) > 1 ORDER BY a, b"));
    }
}
