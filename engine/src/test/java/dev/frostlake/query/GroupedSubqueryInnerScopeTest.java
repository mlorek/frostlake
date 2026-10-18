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
 * A bare name inside a grouped query's scalar subquery binds in the subquery's own FROM first (live-verified):
 * (SELECT MAX(v) FROM u) is u's maximum on every group, in the select list and in HAVING, whatever column of that
 * name the grouped query has; a name qualified by the outer relation is still the outer aggregate.
 */
public class GroupedSubqueryInnerScopeTest extends MeasuredCellsTestSupport {

    @Test
    public void theInnerScopeBindsFirst() {
        engine.execute("CREATE OR REPLACE TABLE t (g INT, v INT)");
        engine.execute("INSERT INTO t VALUES (1, 1), (1, 2), (2, 10), (3, NULL)");
        engine.execute("CREATE OR REPLACE TABLE u (k INT, v INT)");
        engine.execute("INSERT INTO u VALUES (1, 100)");
        assertCells(new String[][] {
            {"SELECT g, (SELECT MAX(v) FROM u) m FROM t GROUP BY g ORDER BY g", "1|100 ;; 2|100 ;; 3|100"},
            {"SELECT g, (SELECT MAX(u.v) FROM u) m FROM t GROUP BY g ORDER BY g", "1|100 ;; 2|100 ;; 3|100"},
            {"SELECT g FROM t GROUP BY g HAVING (SELECT MAX(v) FROM u) = 100 ORDER BY g", "1 ;; 2 ;; 3"},
            {"SELECT g, (SELECT MAX(v) FROM t) m FROM t GROUP BY g ORDER BY g", "1|10 ;; 2|10 ;; 3|10"},
            {"SELECT g, (SELECT MAX(t.v) FROM u) m FROM t GROUP BY g ORDER BY g",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "11"},
        });
    }
}
