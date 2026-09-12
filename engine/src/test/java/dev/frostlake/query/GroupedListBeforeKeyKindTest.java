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
 * A grouped query with BOTH a select list that is wrong for the grouping and a GROUP BY key that may
 * not be one.
 *
 * <p>★ THE SELECT LIST IS COMPILED AGAINST THE GROUPING FIRST. Live settles whether every projected
 * column is grouped or aggregated before it says anything about what the KEYS are, so a list that is
 * wrong speaks at its own item's offset whatever nonsense the GROUP BY holds. Frostlake reported the
 * key-kind rule instead — a sentence live really does emit, just not first.
 *
 * <p>★ WITH A LIST THAT IS VALID FOR THE GROUPING, the key-kind sentences ARE the answer, and they are
 * asserted here unchanged. That is what makes this an ordering fix rather than a wording one: both
 * sentences were already exactly live's.
 *
 * <p>★ THE POSITION IS THE OFFENDING ITEM'S OWN, not the first item's — {@code SELECT SUM(b), a}
 * anchors on 15, where {@code SELECT a, b} anchors on 7.
 *
 * <p>The same shape as the QUALIFY ordering one level down (a name outranks the clause rule that
 * contains it); here it is the whole select list outranking the GROUP BY's own rules.
 */
public class GroupedListBeforeKeyKindTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE gw (a INT, b INT)");
        engine.execute("INSERT INTO gw VALUES (1, 10), (2, 20), (3, 30)");
    }

    private String outcome(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder("accepted:");
            while (rs.next()) {
                all.append(String.valueOf(rs.getValue(0))).append(";");
            }
            return all.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** Live's select-clause sentence, anchored on the offending item. */
    private static String ungrouped(final int position) {
        return "SQL compilation error: error line 1 at position " + position
            + "|'GW.A' in select clause is neither an aggregate nor in the group by clause.";
    }

    private static final String WINDOW_KEY = "SQL compilation error:|Window function [ROW_NUMBER() OVER "
        + "(ORDER BY GW.A ASC NULLS LAST)] appears outside of SELECT, QUALIFY, and ORDER BY clauses.";
    private static final String AGGREGATE_KEY =
        "SQL compilation error:|[SUM(GW.B)] is not a valid group by expression";

    /** An ungrouped select list outranks both key-kind rules. */
    @Test
    public void theSelectListSpeaksBeforeTheKeyKind() {
        assertEquals(ungrouped(7), outcome("SELECT a FROM gw GROUP BY ROW_NUMBER() OVER (ORDER BY a)"));
        assertEquals(ungrouped(7), outcome("SELECT a FROM gw GROUP BY SUM(b)"));
    }

    /** Including through every super-group spelling. */
    @Test
    public void theSuperGroupSpellingsBehaveTheSame() {
        assertEquals(ungrouped(7),
            outcome("SELECT a FROM gw GROUP BY ROLLUP(ROW_NUMBER() OVER (ORDER BY a))"));
        assertEquals(ungrouped(7), outcome("SELECT a FROM gw GROUP BY CUBE(SUM(b))"));
        assertEquals(ungrouped(7), outcome("SELECT a FROM gw GROUP BY GROUPING SETS ((SUM(b)))"));
    }

    /** ★ With a list that IS valid for the grouping, the key-kind sentences survive unchanged. */
    @Test
    public void aValidListLetsTheKeyKindRulesSpeak() {
        assertEquals(WINDOW_KEY,
            outcome("SELECT SUM(b) FROM gw GROUP BY ROW_NUMBER() OVER (ORDER BY a)"));
        assertEquals(AGGREGATE_KEY, outcome("SELECT SUM(b) FROM gw GROUP BY SUM(b)"));
    }

    /** The position is the offending ITEM's, not the list's first. */
    @Test
    public void thePositionIsTheOffendingItems() {
        assertEquals(ungrouped(7), outcome("SELECT a, b FROM gw GROUP BY SUM(b)"));
        assertEquals(ungrouped(15), outcome("SELECT SUM(b), a FROM gw GROUP BY SUM(b)"));
    }

    /** An unresolvable KEY outranks everything, with a good list or a bad one. */
    @Test
    public void anUnresolvableKeyStillSpeaksFirst() {
        assertEquals("SQL compilation error: error line 1 at position 31|invalid identifier 'NOSUCHCOL'",
            outcome("SELECT SUM(b) FROM gw GROUP BY nosuchcol"));
        assertEquals("SQL compilation error: error line 1 at position 26|invalid identifier 'NOSUCHCOL'",
            outcome("SELECT a FROM gw GROUP BY nosuchcol"));
    }

    /** The placements that share the moved code and must NOT have changed. */
    @Test
    public void theOtherClausePlacementsAreUnmoved() {
        assertEquals(WINDOW_KEY, outcome("SELECT a FROM gw WHERE ROW_NUMBER() OVER (ORDER BY a) = 1"));
        assertEquals("SQL compilation error:|Invalid aggregate function in where clause [SUM(GW.B)]",
            outcome("SELECT a FROM gw WHERE SUM(b) > 1"));
        assertEquals("accepted:1;2;3;", outcome("SELECT a FROM gw GROUP BY a ORDER BY a"));
        assertEquals(ungrouped(7), outcome("SELECT a FROM gw GROUP BY b"));
    }
}
