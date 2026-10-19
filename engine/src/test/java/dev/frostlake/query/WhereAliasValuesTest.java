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

import static dev.frostlake.query.QueryAnswers.assertCells;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

/**
 * A WHERE computes only the select aliases it reads, and over a single relation the value it tested is the value
 * the row shows: a volatile item is computed once for such a row (live-verified). Over a join, or under a QUALIFY,
 * the account computes the item again for the projection.
 */
public class WhereAliasValuesTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE g AS SELECT SEQ4() AS i FROM TABLE(GENERATOR(ROWCOUNT => 1000))");
    }

    /** Whether some rows passed, and whether every one shows the r = 1 its WHERE tested. */
    private static String tested(final String query) {
        return "SELECT COUNT(*) > 0, COUNT(*) = COUNT_IF(r = 1) FROM (" + query + ")";
    }

    @Test
    public void theTestedValueIsTheValueShown() {
        assertCells(engine, new String[][] {
            {tested("SELECT i, UNIFORM(1, 2, RANDOM()) AS r FROM g WHERE r = 1"), "true, true"},
            {tested("SELECT i, UNIFORM(1, 2, RANDOM()) AS r FROM g WHERE r = 1 LIMIT 100"), "true, true"},
            {tested("SELECT i, UNIFORM(1, 2, RANDOM()) AS r FROM g WHERE r = 1 ORDER BY UNIFORM(1, 9, RANDOM())"),
                "true, true"},
            {tested("SELECT DISTINCT UNIFORM(1, 2, RANDOM()) AS r, i FROM g WHERE r = 1"), "true, true"},
            {tested("SELECT i, UNIFORM(1, 2, RANDOM()) AS r, ROW_NUMBER() OVER (ORDER BY i) AS rn FROM g WHERE r = 1"),
                "true, true"},
            {tested("SELECT i, UNIFORM(1, 2, RANDOM()) AS r FROM g HAVING r = 1"), "true, true"},
            {tested("WITH c AS (SELECT i FROM g) SELECT i, UNIFORM(1, 2, RANDOM()) AS r FROM c WHERE r = 1"),
                "true, true"},
            {"SELECT COUNT(*) > 0, COUNT(*) = COUNT_IF(m = 11), COUNT(*) = COUNT_IF(r = 1) FROM"
                + " (SELECT i, UNIFORM(1, 2, RANDOM()) AS r, r + 10 AS m FROM g WHERE m = 11)", "true, true, true"},
            {"SELECT MIN(n), COUNT(*) FROM (SELECT SEQ4() AS n FROM g WHERE n >= 500)", "500, 500"},
        });
    }

    @Test
    public void overAJoinOrUnderAQualifyTheItemIsComputedAgain() {
        assertCells(engine, new String[][] {
            {tested("SELECT g.i, UNIFORM(1, 2, RANDOM()) AS r FROM g JOIN g AS h ON g.i = h.i WHERE r = 1"),
                "true, false"},
            {tested("SELECT i, UNIFORM(1, 2, RANDOM()) AS r FROM g WHERE r = 1"
                + " QUALIFY ROW_NUMBER() OVER (ORDER BY i) > 0"), "true, false"},
        });
    }

    @Test
    public void anAliasedItemsSubqueryRaisesAheadOfTheRows() {
        assertCells(engine, new String[][] {
            {"SELECT i, (SELECT i FROM g) AS x FROM g WHERE i = 99999",
                "Single-row subquery returns more than one row."},
            {"SELECT i, (SELECT i / 0 FROM g WHERE i = 1) AS x FROM g WHERE i = 99999", "Division by zero"},
            {"SELECT i, (SELECT i FROM g) AS x FROM g WHERE i = 99999 AND i < x",
                "Single-row subquery returns more than one row."},
        });
    }

    @Test
    public void aWhereThatReadsNoAliasComputesNone() {
        assumeFalse(isLiveSnowflake(), "the account's sequence hands its statements values from separate blocks");
        engine.execute("CREATE SEQUENCE s");
        assertCells(engine, new String[][] {
            {"SELECT i, s.NEXTVAL AS n FROM g WHERE i = 8", "8, 1"},
            {"SELECT s.NEXTVAL", "2"},
            {"SELECT i, s.NEXTVAL AS n FROM g WHERE i = 8 LIMIT 5", "8, 3"},
            {"SELECT i, s.NEXTVAL AS n, n + 1 AS m FROM g WHERE i = 8", "8, 4, 5"},
            {"SELECT s.NEXTVAL", "5"},
            {"SELECT i, s.NEXTVAL AS n FROM g WHERE n = 6", "0, 6"},
            {"SELECT s.NEXTVAL", "1006"},
        });
    }
}
