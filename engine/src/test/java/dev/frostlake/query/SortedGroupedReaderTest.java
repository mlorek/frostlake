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
 * A grouped query that sorts its groups by COUNT, SUM or another aggregate over their rows tells duplicate rows
 * apart, so the UNION, DISTINCT or GROUP BY of the relation it reads keeps removing them. The reader was taken for one
 * that cannot, from its select list alone, and the ORDER BY counted the duplicates: the wrong group came first.
 */
public class SortedGroupedReaderTest extends MeasuredCellsTestSupport {

    private void setUpRelations() {
        engine.execute("CREATE OR REPLACE TABLE sgr (y VARCHAR, z INT)");
        engine.execute("INSERT INTO sgr VALUES ('p', 2), ('p', 2), ('p', 2), ('q', 1), ('q', 2)");
        engine.execute("CREATE OR REPLACE VIEW sgr_u AS SELECT y, z FROM sgr UNION SELECT y, z FROM sgr");
        engine.execute("CREATE OR REPLACE VIEW sgr_d AS SELECT DISTINCT y, z FROM sgr");
        engine.execute("CREATE OR REPLACE VIEW sgr_g AS SELECT y, z FROM sgr GROUP BY y, z");
    }

    @Test
    public void aSortByAnAggregateCountsEachDistinctRowOnce() {
        setUpRelations();
        assertAnswer("p|1|2 ;; q|2|3", "SELECT y, COUNT(*), SUM(z) FROM sgr_u GROUP BY y ORDER BY y");
        assertAnswer("q", "SELECT y FROM sgr_u GROUP BY y ORDER BY COUNT(*) DESC LIMIT 1");
        assertAnswer("p ;; q", "SELECT y FROM sgr_u GROUP BY y ORDER BY SUM(z), y");
        assertAnswer("q", "WITH c AS (SELECT y, z FROM sgr UNION SELECT y, z FROM sgr) "
            + "SELECT y FROM c GROUP BY y ORDER BY COUNT(*) DESC LIMIT 1");
        assertAnswer("p ;; q", "SELECT y FROM (SELECT y, z FROM sgr UNION SELECT y, z FROM sgr) GROUP BY y "
            + "ORDER BY SUM(z), y");
    }

    @Test
    public void soDoesOneOverADistinctOrGroupedBody() {
        setUpRelations();
        assertAnswer("q", "SELECT y FROM sgr_d GROUP BY y ORDER BY COUNT(*) DESC LIMIT 1");
        assertAnswer("q", "SELECT y FROM sgr_g GROUP BY y ORDER BY COUNT(*) DESC LIMIT 1");
        assertAnswer("q", "SELECT y FROM (SELECT DISTINCT y, z FROM sgr) GROUP BY y ORDER BY COUNT(*) DESC LIMIT 1");
        // A sort by an extreme still reads the groups alone.
        assertAnswer("p ;; q", "SELECT y FROM sgr_u GROUP BY y ORDER BY MAX(z), y");
    }
}
