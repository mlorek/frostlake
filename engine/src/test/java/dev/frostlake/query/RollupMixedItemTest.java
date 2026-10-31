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
 * total, while SUM(LENGTH(r)) still reads every row's r (all live-verified). A dimension written as an expression
 * reads as NULL there too, wherever an item computes over it.
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

    @Test
    public void aDimensionWrittenAsAnExpressionReadsAsNullOnASuperGroupRow() {
        engine.execute("CREATE OR REPLACE TABLE rue (r VARCHAR, c INT, v INT)");
        engine.execute("INSERT INTO rue VALUES ('a',1,10),('a',2,20),('bb',1,30)");
        assertAnswer("A|A!|A|30 ;; BB|BB!|BB|30 ;; null|null|ALL|60",
            "SELECT UPPER(r) u, UPPER(r) || '!' x, COALESCE(UPPER(r), 'ALL') lbl, SUM(v) s FROM rue "
                + "GROUP BY ROLLUP (UPPER(r)) ORDER BY s, u");
        assertAnswer("2|20|42 ;; 3|30|23 ;; null|null|null",
            "SELECT c + 1 k, (c + 1) * 10 x, SUM(v) + (c + 1) y FROM rue GROUP BY ROLLUP (c + 1) "
                + "ORDER BY k NULLS LAST");
        assertAnswer("a|2|row|10 ;; a|4|row|20 ;; a|null|sub|30 ;; bb|2|row|30 ;; bb|null|sub|30",
            "SELECT r, c * 2 k, IFF(c * 2 IS NULL, 'sub', 'row') t, SUM(v) s FROM rue "
                + "GROUP BY GROUPING SETS ((r, c * 2), (r)) ORDER BY 1, 2 NULLS LAST");
        assertAnswer("a|false|30 ;; b|false|30 ;; null|true|60",
            "SELECT LEFT(r, 1) k, LEFT(r, 1) IS NULL isn, SUM(v) s FROM rue GROUP BY CUBE (LEFT(r, 1)) "
                + "ORDER BY 1 NULLS LAST");
        // The key written in another case is the same dimension, and an aggregate's argument still reads every row.
        assertAnswer("A|A|2 ;; BB|BB|2 ;; null|ALL|4",
            "SELECT upper(r) u, COALESCE(upper(r), 'ALL') lbl, SUM(LENGTH(UPPER(r))) n FROM rue "
                + "GROUP BY ROLLUP (UPPER(r)) ORDER BY 1 NULLS LAST");
        assertAnswer("a|A|A|30 ;; a|null|-|30 ;; bb|BB|BB|30 ;; bb|null|-|30 ;; null|null|-|60",
            "SELECT r, UPPER(r) u, COALESCE(UPPER(r), '-') l, SUM(v) FROM rue GROUP BY ROLLUP (r, UPPER(r)) "
                + "ORDER BY 1 NULLS LAST, 2 NULLS LAST");
        assertAnswer("-2|-2|20 ;; -1|-1|40 ;; null|0|60",
            "SELECT -c k, COALESCE(-c, 0) z, SUM(v) FROM rue GROUP BY ROLLUP (-c) ORDER BY 1 NULLS LAST");
        assertAnswer("1|1|40 ;; 2|2|20 ;; null|T|60",
            "SELECT c::VARCHAR k, COALESCE(c::VARCHAR, 'T') z, SUM(v) FROM rue GROUP BY ROLLUP (c::VARCHAR) "
                + "ORDER BY 1 NULLS LAST");
        assertAnswer("hi|hi|20 ;; lo|lo|40 ;; null|T|60",
            "SELECT CASE WHEN c > 1 THEN 'hi' ELSE 'lo' END k, COALESCE(CASE WHEN c > 1 THEN 'hi' ELSE 'lo' END, "
                + "'T') z, SUM(v) FROM rue GROUP BY ROLLUP (CASE WHEN c > 1 THEN 'hi' ELSE 'lo' END) "
                + "ORDER BY 1 NULLS LAST");
        assertAnswer("A|0|A:30 ;; BB|0|BB:30 ;; null|1|ALL:60",
            "SELECT UPPER(r) u, GROUPING(UPPER(r)) gr, COALESCE(UPPER(r), 'ALL') || ':' || SUM(v) lbl FROM rue "
                + "GROUP BY ROLLUP (UPPER(r)) ORDER BY 1 NULLS LAST");
    }
}
