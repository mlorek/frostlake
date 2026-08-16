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
 * A window function may be written in the SELECT list, in QUALIFY and in ORDER BY, and nowhere else.
 * Frostlake used to EVALUATE one in HAVING, in a GROUP BY key and in a join's ON condition — the
 * GROUP BY case even returned a plausible-looking answer — where live refuses all three at compile
 * time.
 *
 * <p>The sentence depends on the clause, which is itself informative: HAVING gets the GROUPED
 * validation wording, so live evidently sees the window there as an expression the grouping does not
 * carry rather than as a misplaced window.
 *
 * <pre>
 *   WHERE / GROUP BY / ON   Window function [CALL] appears outside of SELECT, QUALIFY, and ORDER BY
 *                           clauses.
 *   HAVING                  [CALL] is not a valid group by expression
 * </pre>
 *
 * <p>The bracketed call is re-printed canonicalised on both engines
 * ({@code ROW_NUMBER() OVER (ORDER BY G.A ASC NULLS LAST)}) — that form is asserted by
 * {@code CanonicalRefusalEchoTest}; these cases assert the sentence.
 */
public class WindowClauseRestrictionTest extends BaseDatabaseTest {

    private static final String OUTSIDE = "appears outside of SELECT, QUALIFY, and ORDER BY clauses.";
    private static final String NOT_GROUPED = "is not a valid group by expression";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE g (a INT, b INT)");
        engine.execute("INSERT INTO g VALUES (3, 10), (1, 20), (2, 30)");
    }

    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "accepted";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', ' ');
        }
    }

    private String order(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder text = new StringBuilder();
        while (rs.next()) {
            if (text.length() > 0) {
                text.append(",");
            }
            text.append(String.valueOf(rs.getValue(0)));
        }
        return text.toString();
    }

    /** WHERE cannot carry one. */
    @Test
    public void whereCannotCarryAWindow() {
        assertTrue(refusal("SELECT a FROM g WHERE ROW_NUMBER() OVER (ORDER BY a) = 1").contains(OUTSIDE),
            refusal("SELECT a FROM g WHERE ROW_NUMBER() OVER (ORDER BY a) = 1"));
    }

    /** Nor a GROUP BY key, alone or inside a larger key. */
    @Test
    public void aGroupByKeyCannotCarryAWindow() {
        assertTrue(refusal("SELECT COUNT(*) FROM g GROUP BY ROW_NUMBER() OVER (ORDER BY a)")
            .contains(OUTSIDE),
            refusal("SELECT COUNT(*) FROM g GROUP BY ROW_NUMBER() OVER (ORDER BY a)"));
        assertTrue(refusal("SELECT COUNT(*) FROM g GROUP BY ROW_NUMBER() OVER (ORDER BY a) + 1")
            .contains(OUTSIDE),
            refusal("SELECT COUNT(*) FROM g GROUP BY ROW_NUMBER() OVER (ORDER BY a) + 1"));
    }

    /** Nor a join's ON condition. */
    @Test
    public void aJoinConditionCannotCarryAWindow() {
        assertTrue(refusal("SELECT g.a FROM g JOIN g g2 ON ROW_NUMBER() OVER (ORDER BY g.a) = g2.a")
            .contains(OUTSIDE),
            refusal("SELECT g.a FROM g JOIN g g2 ON ROW_NUMBER() OVER (ORDER BY g.a) = g2.a"));
    }

    /** HAVING refuses it with the GROUPED wording — with a GROUP BY clause and without one. */
    @Test
    public void havingRefusesItAsAnUngroupedExpression() {
        assertTrue(refusal("SELECT COUNT(*) FROM g GROUP BY a HAVING ROW_NUMBER() OVER (ORDER BY a) = 1")
            .contains(NOT_GROUPED),
            refusal("SELECT COUNT(*) FROM g GROUP BY a HAVING ROW_NUMBER() OVER (ORDER BY a) = 1"));
        assertTrue(refusal("SELECT COUNT(*) FROM g HAVING ROW_NUMBER() OVER (ORDER BY 1) = 1")
            .contains(NOT_GROUPED),
            refusal("SELECT COUNT(*) FROM g HAVING ROW_NUMBER() OVER (ORDER BY 1) = 1"));
        assertTrue(refusal(
            "SELECT COUNT(*) FROM g GROUP BY a HAVING ROW_NUMBER() OVER (ORDER BY a) + 1 > 1")
            .contains(NOT_GROUPED),
            refusal("SELECT COUNT(*) FROM g GROUP BY a HAVING ROW_NUMBER() OVER (ORDER BY a) + 1 > 1"));
    }

    /** The clauses that MAY carry one still do, and an aggregate in HAVING is untouched. */
    @Test
    public void theAllowedClausesStillCarryOne() {
        assertEquals("1", order("SELECT a FROM g QUALIFY ROW_NUMBER() OVER (ORDER BY a) = 1"));
        assertEquals("1,1,1", order("SELECT COUNT(*) FROM g GROUP BY a HAVING SUM(b) > 0"));
        assertEquals("3,1,2", order("SELECT a FROM g ORDER BY ROW_NUMBER() OVER (ORDER BY b)"));
    }

    /**
     * And a window belonging to a NESTED query is untouched wherever that query sits — its own SELECT
     * list is one of the three places a window belongs.
     */
    @Test
    public void aNestedQuerysWindowIsUntouched() {
        assertEquals("3,1,2",
            order("SELECT a FROM g WHERE a IN (SELECT ROW_NUMBER() OVER (ORDER BY b) FROM g)"));
        assertEquals("1,2,3",
            order("SELECT x.r FROM (SELECT ROW_NUMBER() OVER (ORDER BY b) r FROM g) x ORDER BY 1"));
        assertEquals("1,2,3", order(
            "WITH c AS (SELECT ROW_NUMBER() OVER (ORDER BY b) r FROM g) SELECT r FROM c ORDER BY 1"));
    }
}
