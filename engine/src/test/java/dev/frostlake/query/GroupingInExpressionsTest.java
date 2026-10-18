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
 * GROUPING and GROUPING_ID answer a super-group row's grouping set wherever they stand (live-verified): inside
 * arithmetic, IFF and CASE, with several arguments as a bitmask, and for an argument spelled otherwise than the
 * key - GROUPING(s.r) under ROLLUP (r), GROUPING(r) under ROLLUP (s.r).
 */
public class GroupingInExpressionsTest extends MeasuredCellsTestSupport {

    @Test
    public void groupingAnswersInsideAnyExpression() {
        engine.execute("CREATE OR REPLACE TABLE s (r VARCHAR, p VARCHAR, amt INT)");
        engine.execute("INSERT INTO s VALUES ('E', 'a', 1), ('E', 'b', 2), ('W', 'a', 3), ('W', NULL, 4)");
        assertCells(new String[][] {
            {"SELECT r, GROUPING(r) + 0 g, SUM(amt) FROM s GROUP BY ROLLUP (r) ORDER BY 3",
                "E|0|3 ;; W|0|7 ;; null|1|10"},
            {"SELECT IFF(GROUPING(r) = 1, 'ALL', r) rr, SUM(amt) FROM s GROUP BY ROLLUP (r) ORDER BY 2",
                "E|3 ;; W|7 ;; ALL|10"},
            {"SELECT r, CASE GROUPING(r) WHEN 1 THEN 'T' ELSE 'D' END g FROM s GROUP BY ROLLUP (r) ORDER BY 1 NULLS "
                + "LAST",
                "E|D ;; W|D ;; null|T"},
            {"SELECT r, GROUPING(s.r), SUM(amt) FROM s GROUP BY ROLLUP (r) ORDER BY 1 NULLS LAST",
                "E|0|3 ;; W|0|7 ;; null|1|10"},
            {"SELECT r, GROUPING(r), SUM(amt) FROM s GROUP BY ROLLUP (s.r) ORDER BY 1 NULLS LAST",
                "E|0|3 ;; W|0|7 ;; null|1|10"},
            {"SELECT r, p, GROUPING(r, p) * 10 g FROM s GROUP BY ROLLUP (r, p) ORDER BY 1 NULLS LAST, 2 NULLS LAST",
                "E|a|0 ;; E|b|0 ;; E|null|10 ;; W|a|0 ;; W|null|0 ;; W|null|10 ;; null|null|30"},
        });
    }
}
