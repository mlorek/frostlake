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
 * SEQ1, SEQ2, SEQ4 and SEQ8 number the rows a grouping reads in a GROUP BY key and inside an aggregate, where each row
 * reads its place in the scan rather than in its group; an item outside an aggregate numbers the rows the query
 * answers (live-verified).
 */
public class SeqGroupKeysTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE g9 AS SELECT SEQ4() AS i FROM TABLE(GENERATOR(ROWCOUNT => 9))");
    }

    @Test
    public void aKeyNumbersTheRowsItGroups() {
        assertCells(engine, new String[][] {
            {"SELECT SEQ4() % 3 AS b, COUNT(*) AS n FROM g9 GROUP BY b ORDER BY b", "0, 3 | 1, 3 | 2, 3"},
            {"SELECT SEQ4() % 3 AS b, COUNT(*) AS n FROM g9 GROUP BY 1 ORDER BY 1", "0, 3 | 1, 3 | 2, 3"},
            {"SELECT SEQ4() % 3 AS b, COUNT(*) FROM g9 GROUP BY ALL ORDER BY 1", "0, 3 | 1, 3 | 2, 3"},
            {"SELECT SEQ4() % 3 AS b, COUNT(*) AS n FROM g9 GROUP BY ROLLUP (b) ORDER BY b NULLS LAST",
                "0, 3 | 1, 3 | 2, 3 | null, 9"},
            {"SELECT SEQ8() % 2 AS b, COUNT(*) FROM g9 GROUP BY b ORDER BY b", "0, 5 | 1, 4"},
            {"SELECT COUNT(*), COUNT(DISTINCT s), MIN(s), MAX(s) FROM"
                + " (SELECT SEQ4() AS s, COUNT(*) AS n FROM g9 GROUP BY s)", "9, 9, 0, 8"},
            {"SELECT SEQ4() % 2 AS b, COUNT(*) FROM g9 WHERE i > 2 GROUP BY b ORDER BY 1", "0, 3 | 1, 3"},
        });
    }

    @Test
    public void anItemNumbersTheAnsweredRows() {
        assertCells(engine, new String[][] {
            {"SELECT SEQ4() % 3 AS b, COUNT(*) AS n FROM g9 GROUP BY SEQ4() % 3 ORDER BY 2, 1", "0, 3 | 1, 3 | 2, 3"},
            {"SELECT i % 3 AS k, SEQ4() AS s FROM g9 GROUP BY k ORDER BY 1", "0, 0 | 1, 1 | 2, 2"},
        });
    }

    @Test
    public void anAggregateNumbersTheScan() {
        assertCells(engine, new String[][] {
            {"SELECT i % 3 AS k, SUM(SEQ4()) FROM g9 GROUP BY k ORDER BY 1", "0, 9 | 1, 12 | 2, 15"},
            {"SELECT i % 3 AS k, MIN(SEQ4()), MAX(SEQ4()) FROM g9 GROUP BY k ORDER BY 1",
                "0, 0, 6 | 1, 1, 7 | 2, 2, 8"},
            {"SELECT i % 3 AS k, COUNT(*) FROM g9 GROUP BY k HAVING SUM(SEQ4()) > 10 ORDER BY 1", "1, 3 | 2, 3"},
            {"SELECT i % 2 AS k, SUM(SEQ4()) FROM g9 GROUP BY ROLLUP (k) ORDER BY 1 NULLS LAST",
                "0, 20 | 1, 16 | null, 36"},
            {"SELECT i % 3 AS k, SUM(SEQ4()) FROM g9 WHERE i <> 4 GROUP BY k ORDER BY 1", "0, 8 | 1, 7 | 2, 13"},
            {"SELECT i % 3 AS k, SUM(SEQ4()) AS s, (SELECT SUM(SEQ4()) FROM g9 WHERE i > 5) AS t FROM g9"
                + " GROUP BY k ORDER BY 1", "0, 9, 3 | 1, 12, 3 | 2, 15, 3"},
        });
    }
}
