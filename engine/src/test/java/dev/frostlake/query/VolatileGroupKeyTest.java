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

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

/**
 * A select item that a GROUP BY key names — by its alias or its position, or as GROUP BY ALL groups by it — shows
 * the value its group was formed by, even when the key answers anew each time it is computed; a key that writes
 * the expression out again is a call of its own (live-verified).
 */
public class VolatileGroupKeyTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE g AS SELECT SEQ4() AS i FROM TABLE(GENERATOR(ROWCOUNT => 1000))");
    }

    /** The groups, the distinct keys they show, and the rows they count, grouped by {@code grouping}. */
    private static String keys(final String grouping) {
        return "SELECT COUNT(*), COUNT(DISTINCT r), SUM(n) FROM"
            + " (SELECT UNIFORM(1, 50, RANDOM()) AS r, COUNT(*) AS n FROM g GROUP BY " + grouping + ")";
    }

    @Test
    public void anItemTheKeyNamesShowsItsGroupsKey() {
        assertCells(engine, new String[][] {
            {keys("r"), "50, 50, 1000"},
            {keys("1"), "50, 50, 1000"},
            {keys("ALL"), "50, 50, 1000"},
            {keys("ROLLUP (r)"), "51, 50, 2000"},
            {keys("ROLLUP (1)"), "51, 50, 2000"},
            {keys("CUBE (r)"), "51, 50, 2000"},
            {keys("GROUPING SETS ((r), ())"), "51, 50, 2000"},
            {"SELECT COUNT(*), COUNT(DISTINCT p || '-' || r), SUM(n) FROM"
                + " (SELECT i % 2 AS p, UNIFORM(1, 3, RANDOM()) AS r, COUNT(*) AS n FROM g GROUP BY p, r)",
                "6, 6, 1000"},
            {"SELECT COUNT(*), COUNT(DISTINCT u) FROM"
                + " (SELECT LEFT(UUID_STRING(), 1) AS u, COUNT(*) AS n FROM g GROUP BY u)", "16, 16"},
            {"SELECT COUNT(*), COUNT(DISTINCT r2) FROM"
                + " (SELECT UNIFORM(1, 50, RANDOM()) AS r, r * 2 AS r2, COUNT(*) AS n FROM g GROUP BY r)", "50, 50"},
            {"SELECT COUNT(*), MIN(r), MAX(r) FROM"
                + " (SELECT UNIFORM(1, 4, RANDOM()) AS r, COUNT(*) AS n FROM g GROUP BY r HAVING r = 2)", "1, 2, 2"},
        });
    }

    @Test
    public void aKeyWrittenOutAgainIsACallOfItsOwn() {
        assertCells(engine, new String[][] {
            {"SELECT COUNT(*), COUNT(DISTINCT r) < 50, SUM(n) FROM (SELECT UNIFORM(1, 50, RANDOM()) AS r,"
                + " COUNT(*) AS n FROM g GROUP BY UNIFORM(1, 50, RANDOM()))", "50, true, 1000"},
        });
    }
}
