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
 * On a ROLLUP, CUBE or GROUPING SETS super-group row a rolled-up dimension is NULL wherever an item reads it outside
 * an aggregate's argument: SUM(v) + LENGTH(r) and MAX(r) || r are NULL there, IFF(r IS NULL, SUM(v), 0) is the
 * total, while SUM(LENGTH(r)) still reads every row's r (all live-verified).
 */
public class RollupMixedItemTest extends MeasuredCellsTestSupport {

    @Test
    public void aSuperGroupItemReadsTheRolledUpDimensionAsNull() {
        engine.execute("CREATE OR REPLACE TABLE rus (r VARCHAR, p VARCHAR, v INT)");
        engine.execute("INSERT INTO rus VALUES ('E','a',1),('E','b',2),('W','a',3)");
        assertAnswer("E|3|4 ;; W|3|4 ;; null|6|null",
            "SELECT r, SUM(v), SUM(v) + LENGTH(r) x FROM rus GROUP BY ROLLUP (r) ORDER BY r");
        assertAnswer("E|E|EE ;; W|W|WW ;; null|W|null",
            "SELECT r, MAX(r) mr, MAX(r) || r z FROM rus GROUP BY ROLLUP (r) ORDER BY r");
        assertAnswer("E|E ;; W|W ;; null|ALL",
            "SELECT r, COALESCE(r, 'ALL') rr FROM rus GROUP BY ROLLUP (r) ORDER BY r");
        assertAnswer("E|a|2 ;; E|b|3 ;; E|null|null ;; W|a|4 ;; W|null|null ;; null|null|null",
            "SELECT r, p, SUM(v) + LENGTH(p) x FROM rus GROUP BY ROLLUP (r, p) ORDER BY r, p");
        assertAnswer("E|a|1Ea ;; E|b|1Eb ;; E|null|null ;; W|a|1Wa ;; W|null|null ;; null|a|null ;; null|b|null ;; "
                + "null|null|null",
            "SELECT r, p, COUNT(*) || r || p y FROM rus GROUP BY CUBE (r, p) ORDER BY r, p");
        assertAnswer("E|0 ;; W|0 ;; null|6",
            "SELECT r, SUM(v) * GROUPING(r) g FROM rus GROUP BY ROLLUP (r) ORDER BY r");
        assertAnswer("E|0 ;; W|0 ;; null|6",
            "SELECT r, IFF(r IS NULL, SUM(v), 0) f FROM rus GROUP BY ROLLUP (r) ORDER BY r");
        assertAnswer("E|null|null ;; W|null|null ;; null|a|null ;; null|b|null ;; null|null|null",
            "SELECT r, p, MAX(p) || r || p q FROM rus GROUP BY GROUPING SETS ((r), (p), ()) ORDER BY r, p");
        assertAnswer("E|2 ;; W|1 ;; null|3", "SELECT r, SUM(LENGTH(r)) s FROM rus GROUP BY ROLLUP (r) ORDER BY r");
        assertAnswer("E|3 ;; W|-3 ;; null|-6",
            "SELECT r, CASE WHEN r = 'E' THEN SUM(v) ELSE -SUM(v) END c FROM rus GROUP BY ROLLUP (r) ORDER BY r");
    }
}
