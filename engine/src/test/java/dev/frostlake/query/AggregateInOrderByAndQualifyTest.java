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
 * An aggregate written in ORDER BY or in QUALIFY makes the WHOLE QUERY aggregate, exactly as one in the
 * select list would — and the refusal that follows is about the select list, not about the aggregate:
 *
 * <pre>
 *   SELECT a FROM g ORDER BY SUM(b)       [G.A] is not a valid group by expression
 *   SELECT a FROM g QUALIFY SUM(b) &gt; 0    [G.A] is not a valid group by expression
 *   SELECT 1 FROM g ORDER BY SUM(b)       runs — nothing in the list is ungrouped
 *   SELECT MAX(a) FROM g ORDER BY SUM(b)  runs
 * </pre>
 *
 * <p>Frostlake used to read the aggregate as an unresolvable NAME in ORDER BY ("invalid identifier
 * 'SUM(b)'") and to answer the QUALIFY-without-a-window sentence before looking at the list at all.
 *
 * <p>That QUALIFY sentence is live's own, and it survives — it is simply asked SECOND. A query whose
 * list IS valid still reaches it: {@code SELECT SUM(b) FROM g QUALIFY SUM(b) > 0} is refused for having
 * no window function, where {@code SELECT a FROM g QUALIFY SUM(b) > 0} never gets that far.
 */
public class AggregateInOrderByAndQualifyTest extends BaseDatabaseTest {

    private static final String NOT_GROUPED = "is not a valid group by expression";
    private static final String NO_WINDOW = "found QUALIFY clause but no window function.";

    @Override
    protected void setupTest() {
        // Sums differ per group (30 and 5) so an ordering by them is never a tie.
        engine.execute("CREATE OR REPLACE TABLE g (a INT, b INT)");
        engine.execute("INSERT INTO g VALUES (1, 10), (1, 20), (2, 5)");
    }

    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "accepted";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', ' ');
        }
    }

    private String values(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        while (rs.next()) {
            if (out.length() > 0) {
                out.append(",");
            }
            out.append(String.valueOf(rs.getValue(0)));
        }
        return out.toString();
    }

    private void refusesAsUngrouped(final String sql, final String bracketed) {
        final String answer = refusal(sql);
        assertTrue(answer.contains("[" + bracketed + "] " + NOT_GROUPED), sql + " => " + answer);
    }

    /** An ORDER BY aggregate makes the query aggregate, so the ungrouped list is refused. */
    @Test
    public void anOrderByAggregateGroupsTheQuery() {
        refusesAsUngrouped("SELECT a FROM g ORDER BY SUM(b)", "G.A");
        refusesAsUngrouped("SELECT b FROM g ORDER BY SUM(b)", "G.B");
        refusesAsUngrouped("SELECT a FROM g ORDER BY COUNT(*)", "G.A");
        refusesAsUngrouped("SELECT a FROM g ORDER BY MAX(b) DESC", "G.A");
        refusesAsUngrouped("SELECT a FROM g ORDER BY a + SUM(b)", "G.A");
    }

    /** So does a QUALIFY aggregate — and the list is judged BEFORE the missing-window sentence. */
    @Test
    public void aQualifyAggregateGroupsTheQuery() {
        refusesAsUngrouped("SELECT a FROM g QUALIFY SUM(b) > 0", "G.A");
        refusesAsUngrouped("SELECT a FROM g QUALIFY COUNT(*) > 0", "G.A");
    }

    /** WHERE and DISTINCT do not change it, and HAVING answers the same way. */
    @Test
    public void theOtherClausesDoNotChangeIt() {
        refusesAsUngrouped("SELECT a FROM g WHERE b > 0 ORDER BY SUM(b)", "G.A");
        refusesAsUngrouped("SELECT DISTINCT a FROM g ORDER BY SUM(b)", "G.A");
        refusesAsUngrouped("SELECT a FROM g HAVING SUM(b) > 0", "G.A");
    }

    /** A list with nothing ungrouped in it RUNS — the aggregate ORDER BY is perfectly legal. */
    @Test
    public void anAggregatedListRuns() {
        assertEquals("35", values("SELECT SUM(b) FROM g ORDER BY SUM(a)"));
        assertEquals("3", values("SELECT COUNT(*) FROM g ORDER BY SUM(b)"));
        assertEquals("1", values("SELECT 1 FROM g ORDER BY SUM(b)"));
        assertEquals("2", values("SELECT MAX(a) FROM g ORDER BY SUM(b)"));
    }

    /** An already-grouped query orders by an aggregate quite happily. */
    @Test
    public void aGroupedQueryOrdersByItsAggregate() {
        assertEquals("2,1", values("SELECT a FROM g GROUP BY a ORDER BY SUM(b)"));
        assertEquals("1,2", values("SELECT a FROM g GROUP BY a ORDER BY SUM(b) DESC"));
    }

    /** The QUALIFY-without-a-window sentence survives, for the queries that reach it. */
    @Test
    public void theMissingWindowSentenceSurvives() {
        assertTrue(refusal("SELECT SUM(b) FROM g QUALIFY SUM(b) > 0").contains(NO_WINDOW),
            refusal("SELECT SUM(b) FROM g QUALIFY SUM(b) > 0"));
        assertTrue(refusal("SELECT a, SUM(b) FROM g GROUP BY a QUALIFY SUM(b) > 0").contains(NO_WINDOW),
            refusal("SELECT a, SUM(b) FROM g GROUP BY a QUALIFY SUM(b) > 0"));
        assertTrue(refusal("SELECT a FROM g QUALIFY a > 0").contains(NO_WINDOW),
            refusal("SELECT a FROM g QUALIFY a > 0"));
    }

    /** And a QUALIFY that DOES carry a window still runs. */
    @Test
    public void aWindowedQualifyStillRuns() {
        assertEquals("1", values("SELECT a FROM g QUALIFY ROW_NUMBER() OVER (ORDER BY a) = 1"));
    }
}
