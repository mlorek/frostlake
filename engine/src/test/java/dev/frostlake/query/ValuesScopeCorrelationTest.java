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
 * A VALUES list in a subquery's FROM carries its own columns - its alias's column list, or COLUMN1 onwards - so a
 * name it does not carry reads the outer row, and a grouped query's subquery that aggregates the outer row over
 * the list is refused as an unsupported subquery (all live-verified).
 */
public class ValuesScopeCorrelationTest extends MeasuredCellsTestSupport {

    @Test
    public void aValuesListCarriesItsOwnColumns() {
        assertAnswer("Table TV successfully created.", "CREATE TABLE tv (g INT, v INT)");
        assertAnswer("3", "INSERT INTO tv VALUES (1, 100), (1, 200), (2, 300)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 11",
            "SELECT g, (SELECT MAX(v) FROM (VALUES (5), (7)) x) m FROM tv GROUP BY g ORDER BY 1");
        assertAnswer("1|7 ;; 2|7",
            "SELECT g, (SELECT MAX(column1) FROM (VALUES (5), (7)) x) m FROM tv GROUP BY g ORDER BY 1");
        assertAnswer("1|7 ;; 2|7",
            "SELECT g, (SELECT MAX(x.v) FROM (VALUES (5), (7)) x(v)) m FROM tv GROUP BY g ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 11",
            "SELECT g, (SELECT MAX(v) FROM VALUES (5), (7)) m FROM tv GROUP BY g ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 11",
            "SELECT g, (SELECT MAX(v) FROM (VALUES (5), (7) AS y (w))) m FROM tv GROUP BY g ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MAX(TV.V)] "
                + "can only appear in having or select clause",
            "SELECT (SELECT MAX(v) FROM (VALUES (5), (7)) x) FROM tv ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8",
            "SELECT (SELECT v FROM (VALUES (5)) x) FROM tv ORDER BY 1");
        assertAnswer("1|2 ;; 2|2",
            "SELECT g, (SELECT COUNT(*) FROM (VALUES (5), (7)) x WHERE column1 > g) FROM tv GROUP BY g ORDER BY 1");
        assertAnswer("2 ;; 2 ;; 2", "SELECT (SELECT MAX(column2) FROM (VALUES (5, 1), (7, 2)) x) FROM tv ORDER BY 1");
    }
}
