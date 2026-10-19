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
 * A correlated subquery whose filter compares two rows is answered as the comparisons of their pairs would be
 * (live-verified): (u.a, u.b) = (t.a, t.b) under EXISTS, NOT EXISTS, a scalar COUNT and an IN, written either way
 * round, mixing the two sides pair by pair, beside a constant, and for every operator; a pair whose operand mixes
 * the inner and the outer row is refused as an unsupported subquery type, as a plain comparison is.
 */
public class CorrelatedRowComparisonTest extends MeasuredCellsTestSupport {

    @Test
    public void aRowComparisonReadsAsItsPairs() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR)");
        engine.execute("INSERT INTO t VALUES (1, 'y'), (2, 'x'), (3, 'x'), (1, 'x'), (NULL, 'z'), (2, NULL)");
        engine.execute("CREATE OR REPLACE TABLE u2 (a INT, b VARCHAR)");
        engine.execute("INSERT INTO u2 VALUES (1, 'x'), (2, 'x'), (9, 'q')");
        assertAnswer("1 ;; 1 ;; 2 ;; 3",
            "SELECT a FROM t WHERE EXISTS (SELECT 1 FROM t u WHERE (u.a, u.b) = (t.a, t.b)) ORDER BY a");
        assertAnswer("1|x ;; 2|x",
            "SELECT a, b FROM t WHERE EXISTS (SELECT 1 FROM u2 WHERE (u2.a, u2.b) = (t.a, t.b)) ORDER BY a, b");
        assertAnswer("1|y ;; 2|null ;; 3|x ;; null|z",
            "SELECT a, b FROM t WHERE NOT EXISTS (SELECT 1 FROM u2 WHERE (u2.a, u2.b) = (t.a, t.b)) ORDER BY a, b");
        assertAnswer("1|x ;; 2|x",
            "SELECT a, b FROM t WHERE EXISTS (SELECT 1 FROM u2 WHERE (t.a, t.b) = (u2.a, u2.b)) ORDER BY a, b");
        assertAnswer("1|x ;; 1|y ;; 2|x ;; 2|null ;; 3|x ;; null|z",
            "SELECT a, b FROM t WHERE EXISTS (SELECT 1 FROM u2 WHERE (u2.a, u2.b) <> (t.a, t.b)) ORDER BY a, b");
        assertAnswer("1|y ;; 2|x ;; 2|null ;; 3|x",
            "SELECT a, b FROM t WHERE EXISTS (SELECT 1 FROM u2 WHERE (u2.a, u2.b) < (t.a, t.b)) ORDER BY a, b");
        assertAnswer("1|x ;; 2|x",
            "SELECT a, b FROM t WHERE EXISTS (SELECT 1 FROM u2 WHERE (u2.a, t.b) = (t.a, u2.b)) ORDER BY a, b");
        assertAnswer("1|x ;; 1|y ;; 2|x ;; 2|null",
            "SELECT a, b FROM t WHERE EXISTS (SELECT 1 FROM u2 WHERE (u2.a, u2.b) = (t.a, 'x')) ORDER BY a, b");
        assertAnswer("2|x",
            "SELECT a, b FROM t WHERE EXISTS (SELECT 1 FROM u2 WHERE (u2.a + 0, u2.b) = (t.a, t.b) AND u2.a > 1) "
                + "ORDER BY a, b");
        assertAnswer("1|1 ;; 1|0 ;; 2|1 ;; 2|0 ;; 3|0 ;; null|0",
            "SELECT a, (SELECT COUNT(*) FROM u2 WHERE (u2.a, u2.b) = (t.a, t.b)) FROM t ORDER BY a, b");
        assertAnswer("1|x ;; 2|x",
            "SELECT a, b FROM t WHERE (a, b) IN (SELECT u2.a, u2.b FROM u2 WHERE (u2.a, u2.b) = (t.a, t.b)) ORDER BY "
                + "a, b");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 25",
            "SELECT a, b FROM t WHERE EXISTS (SELECT 1 FROM u2 WHERE (u2.a, u2.b) = (t.a + u2.a - u2.a, t.b)) ORDER "
                + "BY a, b");
    }
}
