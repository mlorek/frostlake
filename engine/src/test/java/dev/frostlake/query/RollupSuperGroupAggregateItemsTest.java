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
 * On a ROLLUP, CUBE or GROUPING SETS super-group row, an item that computes an aggregate reads a dimension the
 * row rolls up as NULL, while the aggregate still counts the group's rows: in the select list, beside GROUPING,
 * in a window's key, in HAVING and in ORDER BY (all live-verified).
 */
public class RollupSuperGroupAggregateItemsTest extends MeasuredCellsTestSupport {

    @Test
    public void aggregateItemsOverRolledUpDimensions() {
        engine.execute("CREATE OR REPLACE TABLE s753 (r VARCHAR, c VARCHAR, amt INT)");
        engine.execute("INSERT INTO s753 VALUES ('E', 'x', 1), ('E', 'y', 2), ('W', 'x', 3)");
        assertAnswer("ALL:6 ;; E:3 ;; W:3",
            "SELECT COALESCE(r, 'ALL') || ':' || SUM(amt) x FROM s753 GROUP BY ROLLUP(r) ORDER BY 1");
        assertAnswer("--6 ;; -x4 ;; -y2 ;; E-3 ;; Ex1 ;; Ey2 ;; W-3 ;; Wx3",
            "SELECT COALESCE(r, '-') || COALESCE(c, '-') || SUM(amt) x FROM s753 GROUP BY CUBE(r, c) ORDER BY 1");
        assertAnswer("-/-=3 ;; -/x=2 ;; -/y=1 ;; E/-=2 ;; W/-=1",
            "SELECT COALESCE(r, '-') || '/' || COALESCE(c, '-') || '=' || COUNT(*) x FROM s753 GROUP BY GROUPING "
                + "SETS ((r), (c), ()) ORDER BY 1");
        assertAnswer("E3 ;; T6 ;; W3",
            "SELECT CASE WHEN r IS NULL THEN 'T' ELSE r END || SUM(amt) x FROM s753 GROUP BY ROLLUP(r) ORDER BY 1");
        assertAnswer("-3 ;; -3 ;; 6",
            "SELECT IFF(r IS NULL, SUM(amt), -SUM(amt)) x FROM s753 GROUP BY ROLLUP(r) ORDER BY 1");
        assertAnswer("E|2|E2 ;; W|1|W1 ;; null|3|T3",
            "SELECT r, SUM(LENGTH(r)) n, COALESCE(r, 'T') || SUM(LENGTH(r)) x FROM s753 GROUP BY ROLLUP(r) ORDER BY "
                + "1 NULLS LAST");
        assertAnswer("0:E3 ;; 0:W3 ;; 1:all6",
            "SELECT GROUPING(r) || ':' || COALESCE(r, 'all') || SUM(amt) x FROM s753 GROUP BY ROLLUP(r) ORDER BY 1");
        assertAnswer("E|3|1 ;; W|3|2 ;; null|6|3",
            "SELECT r, SUM(amt) s, RANK() OVER (ORDER BY COALESCE(r, 'ZZ') || SUM(amt)) rk FROM s753 GROUP BY "
                + "ROLLUP(r) ORDER BY 1 NULLS LAST");
        assertAnswer("E|3 ;; W|3 ;; null|6",
            "SELECT r, SUM(amt) s FROM s753 GROUP BY ROLLUP(r) HAVING NOT (COALESCE(r, 'ALL') = 'E' AND SUM(amt) = "
                + "6) ORDER BY 1 NULLS LAST");
        assertAnswer("null|6 ;; E|3 ;; W|3",
            "SELECT r, SUM(amt) s FROM s753 GROUP BY ROLLUP(r) ORDER BY COALESCE(r, 'A') || SUM(amt)");
        assertAnswer("ALL:6|*3 ;; E:1|x1 ;; E:2|y2 ;; E:3|*2 ;; W:3|*3 ;; W:3|x3",
            "SELECT COALESCE(r, 'ALL') || ':' || SUM(amt) x, COALESCE(c, '*') || MAX(amt) y FROM s753 GROUP BY "
                + "ROLLUP(r, c) ORDER BY 1, 2");
        assertAnswer("ALL:36 ;; E:33 ;; W:33",
            "SELECT COALESCE(r, 'ALL') || ':' || (SELECT MAX(amt) FROM s753) || SUM(amt) x FROM s753 GROUP BY "
                + "ROLLUP(r) ORDER BY 1");
        assertAnswer("false|1 ;; false|2 ;; true|3",
            "SELECT r IS NULL AND SUM(amt) > 5 b, COUNT(*) n FROM s753 GROUP BY ROLLUP(r) ORDER BY 1, 2");
    }
}
