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

import org.junit.jupiter.api.Test;

/**
 * Over a single relation an aliased item is computed once for a row, and the WHERE, the grouping and the projection
 * read that one value: a volatile item grouped by its alias, its position, GROUP BY ALL, ROLLUP or GROUPING SETS forms
 * its groups by the values the WHERE tested, so {@code UNIFORM(1, 4, RANDOM()) AS r … WHERE r > 1 GROUP BY r}
 * makes the groups 2, 3 and 4 alone (live-verified).
 */
public class GroupKeyWhereAliasValueTest extends MeasuredCellsTestSupport {

    @Test
    public void aKeyNamingAnAliasTheWhereReadGroupsByTheTestedValue() {
        engine.execute("CREATE OR REPLACE TABLE g AS SELECT SEQ4() AS i FROM TABLE(GENERATOR(ROWCOUNT => 1000))");
        assertCells(new String[][] {
            {"SELECT COUNT(*), MIN(r) FROM (SELECT UNIFORM(1, 4, RANDOM()) AS r, COUNT(*) AS n FROM g WHERE r > "
                + "1 GROUP BY r)",
                "3|2"},
            {"SELECT COUNT(*), MIN(r) FROM (SELECT UNIFORM(1, 4, RANDOM()) AS r, COUNT(*) AS n FROM g WHERE r > "
                + "1 GROUP BY 1)",
                "3|2"},
            {"SELECT MIN(r) FROM (SELECT UNIFORM(1, 4, RANDOM()) AS r FROM g WHERE r > 1 GROUP BY ALL)", "2"},
            {"SELECT COUNT(*), MIN(r) FROM (SELECT DISTINCT UNIFORM(1, 4, RANDOM()) AS r FROM g WHERE r > 1)", "3|2"},
            {"SELECT COUNT(*), MIN(r), SUM(n) > 500 FROM (SELECT UNIFORM(1, 4, RANDOM()) AS r, COUNT(*) AS n "
                + "FROM g WHERE r > 1 GROUP BY r)",
                "3|2|true"},
            {"SELECT COUNT(*), MIN(r) FROM (SELECT UNIFORM(1, 4, RANDOM()) AS r, COUNT(*) AS n FROM g WHERE r > "
                + "1 GROUP BY r HAVING r > 2)",
                "2|3"},
            {"SELECT COUNT(*), MIN(r) FROM (SELECT UNIFORM(1, 4, RANDOM()) + 0 AS r FROM g WHERE r > 1 GROUP BY r)",
                "3|2"},
        });
    }

    @Test
    public void everyGroupingReadsTheTestedValue() {
        engine.execute("CREATE OR REPLACE TABLE g AS SELECT SEQ4() AS i FROM TABLE(GENERATOR(ROWCOUNT => 1000))");
        assertCells(new String[][] {
            {"SELECT COUNT(*), MIN(r) FROM (SELECT UNIFORM(1, 4, RANDOM()) AS r FROM g WHERE r > 1 GROUP BY "
                + "ROLLUP (r))",
                "4|2"},
            {"SELECT COUNT(*), MIN(r) FROM (SELECT UNIFORM(1, 4, RANDOM()) AS r FROM g WHERE r > 1 AND r < 4 "
                + "GROUP BY r)",
                "2|2"},
            {"SELECT COUNT(*), SUM(n) < 1000 FROM (SELECT UNIFORM(1, 4, RANDOM()) AS r, COUNT(*) AS n FROM g "
                + "WHERE r > 1 GROUP BY r)",
                "3|true"},
            {"SELECT COUNT(*), MIN(r) FROM (SELECT UNIFORM(1, 4, RANDOM()) AS r FROM g WHERE r > 1 GROUP BY "
                + "GROUPING SETS ((r)))",
                "3|2"},
            {"SELECT COUNT(*), MIN(r) FROM (SELECT UNIFORM(1, 4, RANDOM()) AS r, MOD(i, 2) AS k FROM g WHERE r "
                + "> 1 GROUP BY k, r)",
                "6|2"},
        });
    }
}
