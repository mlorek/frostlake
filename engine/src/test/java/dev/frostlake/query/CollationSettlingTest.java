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
 * A column's collation is settled over every relation that feeds it: the arms of a UNION BY NAME by their names, the
 * anchor and the recursive terms of a recursive CTE, and a quantified comparison's subject with its query's column,
 * a COLLATE outranking a column's own and two columns' that differ refused (live-verified).
 */
public class CollationSettlingTest extends BaseDatabaseTest {

    private static final String CS_CI = "SQL compilation error: Incompatible collations: 'en-cs' and 'en-ci'";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE ct (s VARCHAR, c VARCHAR COLLATE 'en-ci', d VARCHAR COLLATE 'en-cs')");
        engine.execute("INSERT INTO ct VALUES ('a', 'a', 'a'), ('A', 'A', 'A'), ('b', 'b', 'b'), ('B', 'B', 'B')");
    }

    @Test
    public void aUnionByNameSettlesEachColumnByItsName() {
        assertCells(engine, new String[][] {
            {"SELECT DISTINCT COLLATION(x) FROM"
                + " (SELECT c AS x FROM ct UNION ALL BY NAME SELECT c COLLATE 'en-cs' AS x FROM ct)", "en-cs"},
            {"SELECT DISTINCT COLLATION(x) FROM (SELECT c AS x FROM ct UNION ALL BY NAME SELECT d AS x FROM ct)",
                CS_CI},
            {"SELECT DISTINCT COLLATION(x) FROM (SELECT c AS x FROM ct UNION BY NAME SELECT s AS x FROM ct)", "en-ci"},
            {"SELECT DISTINCT COLLATION(x) FROM"
                + " (SELECT s AS x, 1 AS y FROM ct UNION ALL BY NAME SELECT 2 AS y, c AS x FROM ct)", "en-ci"},
            {"SELECT COUNT(*) FROM (SELECT c AS x FROM ct UNION BY NAME SELECT c AS x FROM ct)", "2"},
        });
    }

    @Test
    public void aRecursiveCteSettlesItsAnchorWithItsTerms() {
        assertCells(engine, new String[][] {
            {"WITH RECURSIVE r (x, n) AS (SELECT c, 1 FROM ct UNION ALL SELECT x COLLATE 'en-cs', n + 1 FROM r"
                + " WHERE n < 2) SELECT DISTINCT COLLATION(x) FROM r", "en-cs"},
            {"WITH RECURSIVE r (x, n) AS (SELECT s, 1 FROM ct UNION ALL SELECT x COLLATE 'en-ci', n + 1 FROM r"
                + " WHERE n < 2) SELECT DISTINCT COLLATION(x) FROM r", "en-ci"},
            {"WITH RECURSIVE r (x, n) AS (SELECT c, 1 FROM ct UNION ALL SELECT d, n + 1 FROM r JOIN ct ON TRUE"
                + " WHERE n < 2) SELECT COUNT(*) FROM r",
                "SQL compilation error: Incompatible collations: 'en-ci' and 'en-cs'"},
        });
    }

    @Test
    public void aQuantifiedComparisonSettlesWithItsQuery() {
        assertCells(engine, new String[][] {
            {"SELECT COUNT(*) FROM ct WHERE c = ANY (SELECT d FROM ct)", CS_CI},
            {"SELECT COUNT(*) FROM ct WHERE c IN (SELECT d FROM ct)", CS_CI},
            {"SELECT COUNT(*) FROM ct WHERE c IN (SELECT s FROM ct)", "4"},
            {"SELECT COUNT(*) FROM ct WHERE c COLLATE 'en-cs' IN (SELECT d FROM ct)", "4"},
        });
    }
}
