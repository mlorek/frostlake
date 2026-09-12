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
 * A GROUPED query whose SELECT list carries a window may still ORDER BY anything the group offers —
 * a GROUP BY key, an aggregate, an expression over either — none of which the SELECT list projects.
 * Frostlake refused all of them, "invalid identifier 'A'", for a reason that had nothing to do with
 * scope: the window stage doubles as the projection, and it precomputed each unselected sort key by
 * evaluating that key against the row it was handed. Over grouped rows that row is one row per group,
 * already in SELECT-list shape, so a key naming anything else found nothing there.
 *
 * <p>The value exists in one place only — the GROUP the output row came from — which is where the
 * window's own raw aggregates ({@code OVER (ORDER BY SUM(b))}) were already being read from. The same
 * resolver now serves both stages.
 *
 * <p>Two things the measurement settled that the shape does not suggest:
 *
 * <pre>
 *   the trigger is not "names a group key"   ORDER BY SUM(b) was refused identically, so it is
 *                                            "does not match the projected output"
 *   the refusal was DATA-DEPENDENT           over an EMPTY table the per-row precompute never ran,
 *                                            the plan-time check spoke instead, and the same query
 *                                            was refused or answered depending on the rows
 * </pre>
 *
 * <p>That second cell is why the plan-time scope check now runs BEFORE the window stage rather than
 * after it — measured to sit after the window-key walk and the missing-ORDER-BY refusal, both of which
 * live reports first, and before everything this stage would otherwise raise.
 *
 * <p>The fixture is chosen so no two candidate answers collapse: the groups' SUM(b) are 30, 10 and 50,
 * so ordering by the key gives 2,1,3 — a permutation that neither group order nor window order nor
 * the row order of the table can produce by accident. {@code gc} exists for the same reason on the
 * other axis: its groups have three, two and one row, so COUNT(*) is a readable sort key where over
 * {@code gw} every group would count 1 and the sort would be a no-op.
 */
public class GroupedWindowOrderKeyTest extends BaseDatabaseTest {

    /** The window whose own key is a raw aggregate the SELECT list never projects. */
    private static final String WIN = "ROW_NUMBER() OVER (ORDER BY SUM(b))";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE gw (a INT, b INT)");
        engine.execute("INSERT INTO gw VALUES (1, 30), (2, 10), (3, 50)");
        engine.execute("CREATE OR REPLACE TABLE gx (a INT, c INT)");
        engine.execute("INSERT INTO gx VALUES (1, 7), (2, 8), (3, 9)");
        // Group SIZES differ — 3, 2, 1 — so COUNT(*) is a key that can actually be read.
        engine.execute("CREATE OR REPLACE TABLE gc (a INT, b INT)");
        engine.execute("INSERT INTO gc VALUES (1, 1), (1, 2), (1, 3), (2, 10), (2, 20), (3, 100)");
        engine.execute("CREATE OR REPLACE TABLE gwe (a INT, b INT)");
    }

    /** Every row's every column, joined, so a wrong ORDER is as visible as a wrong VALUE. */
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

    /** The message of the refusal a statement raises, or its answer when there is none. */
    private String outcome(final String sql) {
        try {
            return answer(sql);
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** The reported defect: a GROUP BY key, ordered both ways and written both ways. */
    @Test
    public void aGroupKeyIsAValidSortKey() {
        assertEquals("2,1,3", answer("SELECT " + WIN + " FROM gw GROUP BY a ORDER BY a"));
        assertEquals("3,1,2", answer("SELECT " + WIN + " FROM gw GROUP BY a ORDER BY a DESC"));
        assertEquals("2,1,3", answer("SELECT " + WIN + " FROM gw GROUP BY a ORDER BY gw.a"),
            "written with its table qualifier");
        assertEquals("2,1,3", answer("SELECT " + WIN + " FROM gw GROUP BY a ORDER BY a NULLS FIRST"));
    }

    /** And an AGGREGATE, which is the cell that proved the trigger was not about group keys. */
    @Test
    public void anAggregateIsAValidSortKeyToo() {
        assertEquals("1,2,3", answer("SELECT " + WIN + " FROM gw GROUP BY a ORDER BY SUM(b)"));
        assertEquals("1,2,3", answer("SELECT " + WIN + " FROM gw GROUP BY a ORDER BY MAX(b)"),
            "an aggregate the query never mentions elsewhere");
        assertEquals("1,2,3", answer("SELECT " + WIN + " FROM gw GROUP BY a ORDER BY MIN(b) + MAX(b)"));
        assertEquals("3,2,1", answer("SELECT " + WIN + " FROM gw GROUP BY a ORDER BY SUM(b) * -1"));
        assertEquals("3,2,1", answer("SELECT " + WIN + " FROM gc GROUP BY a ORDER BY COUNT(*)"),
            "COUNT(*) over groups of three, two and one row");
        assertEquals("1,2,3", answer("SELECT " + WIN + " FROM gc GROUP BY a ORDER BY COUNT(*) DESC"));
    }

    /** Expressions over a key, and a key reached through a function, resolve the same way. */
    @Test
    public void anExpressionOverTheKeyResolves() {
        assertEquals("2,1,3", answer("SELECT " + WIN + " FROM gw GROUP BY a ORDER BY a + 1"));
        assertEquals("2,1,3", answer("SELECT " + WIN + " FROM gw GROUP BY a ORDER BY COALESCE(a, 0)"));
        assertEquals("2,1,3", answer("SELECT " + WIN + " FROM gw GROUP BY a ORDER BY TO_VARCHAR(a)"));
        assertEquals("2,1,3", answer("SELECT " + WIN + " FROM gw GROUP BY a + 0 ORDER BY a + 0"),
            "a query grouped by an expression, ordered by the same expression");
    }

    /** The shapes the window and the clauses around it can take. */
    @Test
    public void theSurroundingClausesAreUnaffected() {
        assertEquals("2,1,3", answer("SELECT RANK() OVER (ORDER BY SUM(b)) FROM gw GROUP BY a ORDER BY a"));
        assertEquals("2,1,3", answer("SELECT " + WIN + " AS rn FROM gw GROUP BY a ORDER BY a"));
        assertEquals("2/2,1/1,3/3",
            answer("SELECT " + WIN + ", RANK() OVER (ORDER BY SUM(b)) FROM gw GROUP BY a ORDER BY a"));
        assertEquals("1,1,1",
            answer("SELECT ROW_NUMBER() OVER (PARTITION BY a ORDER BY SUM(b)) FROM gw GROUP BY a"
                + " ORDER BY a"));
        assertEquals("1,2", answer("SELECT " + WIN + " FROM gw GROUP BY a HAVING SUM(b) > 10 ORDER BY a"));
        assertEquals("2,1", answer("SELECT " + WIN + " FROM gw GROUP BY a ORDER BY a LIMIT 2"));
        assertEquals("2,1,3",
            answer("WITH t AS (SELECT " + WIN + " AS rn FROM gw GROUP BY a ORDER BY a) SELECT rn FROM t"));
        assertEquals("2,1,3", answer("SELECT ROW_NUMBER() OVER (ORDER BY SUM(gw.b))"
            + " FROM gw JOIN gx ON gw.a = gx.a GROUP BY gw.a ORDER BY gw.a"),
            "over a join, where the key needs its alias context");
        assertEquals("1", answer("SELECT " + WIN + " FROM gw ORDER BY SUM(b)"),
            "implicit aggregation — one row, and it must still compute the key");
    }

    /** A key the SELECT list DOES carry keeps resolving off the output, ordinals included. */
    @Test
    public void aSelectedKeyIsUnchanged() {
        assertEquals("1,2,3", answer("SELECT " + WIN + " FROM gw GROUP BY a ORDER BY 1"));
        assertEquals("1/2,2/1,3/3", answer("SELECT a, " + WIN + " FROM gw GROUP BY a ORDER BY a"));
        assertEquals("1,1,1", answer("SELECT COUNT(*) FROM gw GROUP BY a ORDER BY a"));
        assertEquals("1,2,3", answer("SELECT " + WIN + " AS rn FROM gw GROUP BY a ORDER BY rn"));
        assertEquals("2,3", answer("SELECT " + WIN + " FROM gc GROUP BY a QUALIFY " + WIN + " > 1"
            + " ORDER BY a"));
    }

    /**
     * The keys that are NOT legal are refused in live's own words, at COMPILE time — an empty input
     * refuses identically, which is the half of this the row-time path could never do.
     */
    @Test
    public void anIllegalKeyIsStillRefused() {
        assertEquals("SQL compilation error: [GW.B] is not a valid order by expression",
            outcome("SELECT " + WIN + " FROM gw GROUP BY a ORDER BY b"),
            "a column that is neither grouped nor aggregated");
        assertEquals("SQL compilation error: [GWE.B] is not a valid order by expression",
            outcome("SELECT " + WIN + " FROM gwe GROUP BY a ORDER BY b"),
            "and the same over an empty table");
        assertEquals("SQL compilation error: error line 1 at position 71 invalid identifier 'ZZ'",
            outcome("SELECT " + WIN + " FROM gw GROUP BY a ORDER BY zz"));
        assertEquals("SQL compilation error: error line 1 at position 71 invalid identifier 'ZZ.A'",
            outcome("SELECT " + WIN + " FROM gw GROUP BY a ORDER BY zz.a"));
        assertEquals("", answer("SELECT " + WIN + " FROM gwe GROUP BY a ORDER BY a"),
            "a legal key over an empty table answers no rows rather than refusing");
    }

    /**
     * The refusals live reports BEFORE this one, which is what fixes where the check may sit. Each
     * names something other than the ORDER BY though the ORDER BY key is invalid too.
     */
    @Test
    public void theEarlierRefusalsStillWin() {
        assertEquals("SQL compilation error: error line 1 at position 35 invalid identifier 'NOSUCH'",
            outcome("SELECT ROW_NUMBER() OVER (ORDER BY nosuch) FROM gw GROUP BY a ORDER BY b"),
            "an unresolvable window key");
        assertEquals("SQL compilation error: Window function type [ROW_NUMBER] requires ORDER BY"
            + " in window specification.",
            outcome("SELECT ROW_NUMBER() OVER () FROM gw GROUP BY a ORDER BY b"));
        assertEquals("SQL compilation error: error line 1 at position 69 invalid identifier 'NOSUCH'",
            outcome("SELECT " + WIN + " FROM gw GROUP BY a HAVING nosuch > 1 ORDER BY b"));
        assertEquals("SQL compilation error: error line 1 at position 70 invalid identifier 'NOSUCH'",
            outcome("SELECT " + WIN + " FROM gw GROUP BY a QUALIFY nosuch > 1 ORDER BY b"));
        assertEquals("SQL compilation error: error line 1 at position 7 'GW.B' in select clause is"
            + " neither an aggregate nor in the group by clause.",
            outcome("SELECT b, " + WIN + " FROM gw GROUP BY a ORDER BY b"));
    }
}
