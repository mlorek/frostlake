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
 * A bare name inside a grouped query's scalar subquery binds in the subquery's own FROM first whatever the FROM
 * reads (live-verified): over a derived table, a common table expression - the subquery's own or the statement's -
 * or a view, (SELECT MAX(v) FROM ...) is the subquery's aggregate over its own rows on every group, in the select
 * list and in HAVING, as it is over a table; a name the source does not carry - a derived table renamed by its
 * column list, a view's listed names - still reaches the outer column, and its aggregate is refused there.
 */
public class GroupedSubqueryDerivedSourceTest extends MeasuredCellsTestSupport {

    @Test
    public void aDerivedTableACteOrAViewBindsTheNameFirst() {
        engine.execute("CREATE OR REPLACE TABLE gst (g INT, v INT)");
        engine.execute("INSERT INTO gst VALUES (1, 1), (1, 2), (2, 10), (3, NULL)");
        engine.execute("CREATE OR REPLACE TABLE gsu (k INT, v INT)");
        engine.execute("INSERT INTO gsu VALUES (1, 100)");
        engine.execute("CREATE OR REPLACE VIEW gsuv AS SELECT v FROM gsu");
        assertAnswer("1|100 ;; 2|100 ;; 3|100",
            "SELECT g, (SELECT MAX(v) FROM (SELECT v FROM gsu)) m FROM gst GROUP BY g ORDER BY g");
        assertAnswer("1|100 ;; 2|100 ;; 3|100",
            "SELECT g, (WITH c AS (SELECT v FROM gsu) SELECT MAX(v) FROM c) m FROM gst GROUP BY g ORDER BY g");
        assertAnswer("1|100 ;; 2|100 ;; 3|100", "SELECT g, (SELECT MAX(v) FROM gsuv) m FROM gst GROUP BY g ORDER BY g");
        assertAnswer("1 ;; 2 ;; 3",
            "SELECT g FROM gst GROUP BY g HAVING (SELECT MAX(v) FROM (SELECT v FROM gsu)) = 100 ORDER BY g");
        assertAnswer("1|100 ;; 2|100 ;; 3|100",
            "SELECT g, (SELECT MAX(d.v) FROM (SELECT v FROM gsu) d) m FROM gst GROUP BY g ORDER BY g");
        assertAnswer("1|100 ;; 2|100 ;; 3|100", "SELECT g, (SELECT MAX(v) FROM gsu) m FROM gst GROUP BY g ORDER BY g");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 11",
            "SELECT g, (SELECT MAX(gst.v) FROM (SELECT v FROM gsu)) m FROM gst GROUP BY g ORDER BY g");
        assertAnswer("1|1 ;; 2|1 ;; 3|1",
            "SELECT g, (SELECT COUNT(v) FROM (SELECT v FROM gsu)) m FROM gst GROUP BY g ORDER BY g");
        assertAnswer("1|200 ;; 2|200 ;; 3|200",
            "SELECT g, (SELECT MAX(v) + MIN(v) FROM gsuv) m FROM gst GROUP BY g ORDER BY g");
        assertAnswer("100 ;; 100 ;; 100 ;; 100", "SELECT (SELECT MAX(v) FROM (SELECT v FROM gsu)) m FROM gst");
    }

    @Test
    public void aStarAnAliasAColumnListOrASetOperationCarriesItsNames() {
        engine.execute("CREATE OR REPLACE TABLE gxt (g INT, v INT)");
        engine.execute("INSERT INTO gxt VALUES (1, 1), (1, 2), (2, 10), (3, NULL)");
        engine.execute("CREATE OR REPLACE TABLE gxu (k INT, v INT)");
        engine.execute("INSERT INTO gxu VALUES (1, 100)");
        engine.execute("CREATE OR REPLACE VIEW gxv (v) AS SELECT k FROM gxu");
        assertAnswer("1|100 ;; 2|100 ;; 3|100",
            "SELECT g, (SELECT MAX(v) FROM (SELECT * FROM gxu)) m FROM gxt GROUP BY g ORDER BY g");
        assertAnswer("1|1 ;; 2|1 ;; 3|1",
            "SELECT g, (SELECT MAX(v) FROM (SELECT k AS v FROM gxu)) m FROM gxt GROUP BY g ORDER BY g");
        assertAnswer("1|1 ;; 2|1 ;; 3|1",
            "SELECT g, (SELECT MAX(v) FROM (SELECT k FROM gxu) d(v)) m FROM gxt GROUP BY g ORDER BY g");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 11",
            "SELECT g, (SELECT MAX(v) FROM (SELECT k FROM gxu)) m FROM gxt GROUP BY g ORDER BY g");
        assertAnswer("1|100 ;; 2|100 ;; 3|100",
            "WITH c AS (SELECT v FROM gxu) SELECT g, (SELECT MAX(v) FROM c) m FROM gxt GROUP BY g ORDER BY g");
        assertAnswer("1|1 ;; 2|1 ;; 3|1",
            "SELECT g, (WITH c(v) AS (SELECT k FROM gxu) SELECT MAX(v) FROM c) m FROM gxt GROUP BY g ORDER BY g");
        assertAnswer("1|100 ;; 2|100 ;; 3|100",
            "SELECT g, (SELECT MAX(v) FROM gxu JOIN (SELECT 1 one) ON TRUE) m FROM gxt GROUP BY g ORDER BY g");
        assertAnswer("1|100 ;; 2|100 ;; 3|100",
            "SELECT g, (SELECT MAX(v) FROM (SELECT gxu.* FROM gxu)) m FROM gxt GROUP BY g ORDER BY g");
        assertAnswer("1|200 ;; 2|200 ;; 3|200",
            "SELECT g, (SELECT SUM(v) FROM (SELECT v FROM gxu UNION ALL SELECT v FROM gxu)) m FROM gxt GROUP BY g "
                + "ORDER BY g");
        assertAnswer("1|1 ;; 2|1 ;; 3|1", "SELECT g, (SELECT MAX(v) FROM gxv) m FROM gxt GROUP BY g ORDER BY g");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 11",
            "SELECT g, (SELECT MAX(v) FROM (SELECT k FROM gxu) d(w)) m FROM gxt GROUP BY g ORDER BY g");
    }
}
