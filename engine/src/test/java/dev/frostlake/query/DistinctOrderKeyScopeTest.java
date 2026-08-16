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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With SELECT DISTINCT, an ORDER BY key must be something the SELECT list PRODUCES. The distinct step
 * has already collapsed the rows any other key would be computed from, so live refuses a base column,
 * a GROUP BY key and an aggregate alike — "[GW.B] is not a valid order by expression" — while an
 * ordinal, an alias and an expression BUILT from selected items all resolve as usual.
 *
 * <p>Frostlake answered every one of them, sorting by a value the output no longer holds.
 *
 * <p>The rule is about DISTINCT and not about grouping: {@code SELECT DISTINCT a FROM gw ORDER BY b}
 * is refused with no GROUP BY anywhere in it, which is what fixes where the check belongs. It sits
 * after the name-resolution check, because a key that resolves to NOTHING is that error first
 * (ORDER BY zz is "invalid identifier", not "unselected"), and that ordering is measured, not assumed.
 *
 * <p>The walk stops at any subtree the SELECT list already carries, which is what keeps
 * {@code ORDER BY a + 1} and {@code ORDER BY SUM(b) + 1} legal beside {@code a} and {@code SUM(b)}.
 */
public class DistinctOrderKeyScopeTest extends BaseDatabaseTest {

    private static final String WIN = "ROW_NUMBER() OVER (ORDER BY SUM(b))";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE gw (a INT, b INT)");
        engine.execute("INSERT INTO gw VALUES (1, 30), (2, 10), (3, 50)");
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

    /** The message of the refusal a statement raises, or its answer when there is none. */
    private String outcome(final String sql) {
        try {
            return answer(sql);
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** A plain DISTINCT: the rule needs no grouping and no window to apply. */
    @Test
    public void aPlainDistinctRefusesAnUnselectedColumn() {
        assertEquals("SQL compilation error: [GW.B] is not a valid order by expression",
            outcome("SELECT DISTINCT a FROM gw ORDER BY b"));
        assertEquals("SQL compilation error: [GW.B] is not a valid order by expression",
            outcome("SELECT DISTINCT a FROM gw ORDER BY gw.b"),
            "the qualifier is echoed whether or not it was written");
    }

    /** A GROUPED DISTINCT refuses its own GROUP BY key, which without DISTINCT is a valid key. */
    @Test
    public void aGroupedDistinctRefusesItsGroupKey() {
        assertEquals("SQL compilation error: [GW.A] is not a valid order by expression",
            outcome("SELECT DISTINCT " + WIN + " FROM gw GROUP BY a ORDER BY a"));
        assertEquals("SQL compilation error: [GW.A] is not a valid order by expression",
            outcome("SELECT DISTINCT " + WIN + " FROM gw GROUP BY a ORDER BY gw.a"));
        assertEquals("SQL compilation error: [GW.A] is not a valid order by expression",
            outcome("SELECT DISTINCT SUM(b) FROM gw GROUP BY a ORDER BY a"),
            "with no window in the query at all");
        assertEquals("2,1,3", answer("SELECT " + WIN + " FROM gw GROUP BY a ORDER BY a"),
            "and the same query WITHOUT distinct still answers");
    }

    /** An unselected AGGREGATE is refused as a whole, echoed as the key was written. */
    @Test
    public void anUnselectedAggregateIsRefused() {
        assertEquals("SQL compilation error: [COUNT(*)] is not a valid order by expression",
            outcome("SELECT DISTINCT SUM(b) FROM gw GROUP BY a ORDER BY COUNT(*)"));
    }

    /** What DISTINCT still allows: an ordinal, an alias, and expressions over selected items. */
    @Test
    public void aSelectedKeyIsStillLegal() {
        assertEquals("1,2,3", answer("SELECT DISTINCT a FROM gw ORDER BY a"));
        assertEquals("1,2,3", answer("SELECT DISTINCT a FROM gw ORDER BY a + 1"));
        assertEquals("3,2,1", answer("SELECT DISTINCT a FROM gw ORDER BY 1 DESC"));
        assertEquals("3,2,1", answer("SELECT DISTINCT " + WIN + " FROM gw GROUP BY a ORDER BY 1 DESC"));
        assertEquals("10,30,50", answer("SELECT DISTINCT SUM(b) FROM gw GROUP BY a ORDER BY SUM(b) + 1"));
        assertEquals("10,30,50", answer("SELECT DISTINCT SUM(b) AS s FROM gw GROUP BY a ORDER BY s"));
        assertEquals("1/2,2/1,3/3", answer("SELECT DISTINCT a, " + WIN + " FROM gw GROUP BY a ORDER BY a"));
        assertEquals("2/10,1/30,3/50", answer("SELECT DISTINCT * FROM gw ORDER BY b"),
            "a star projects every column, so nothing a key can name is missing");
    }

    /** A key that resolves to nothing is THAT error first, DISTINCT or not. */
    @Test
    public void anUnresolvableKeyIsNamedFirst() {
        assertEquals("SQL compilation error: error line 1 at position 35 invalid identifier 'ZZ'",
            outcome("SELECT DISTINCT a FROM gw ORDER BY zz"));
    }

    /**
     * An unselected aggregate carrying a COLUMN is refused too. Only the sentence's tail is asserted:
     * live re-prints the key with its table qualifier ({@code [SUM(GW.B)]}) where this echoes it as
     * written, the same canonical-echo difference every refusal naming an expression carries.
     */
    @Test
    public void anAggregateOverAColumnIsRefusedToo() {
        final String refusal = outcome("SELECT DISTINCT " + WIN + " FROM gw GROUP BY a ORDER BY SUM(b)");
        assertTrue(refusal.endsWith("] is not a valid order by expression"), refusal);
        assertTrue(refusal.startsWith("SQL compilation error: [SUM("), refusal);
    }
}
