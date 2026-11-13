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
 * A scalar subquery whose select list opens with the outer query's aggregate is refused as the statement compiles,
 * whether or not a row reaches it, wherever its item evaluates it whatever the row holds: standing alone, under a
 * COALESCE's or an NVL's first argument, an operator or ZEROIFNULL; over an empty table, under WHERE FALSE and under a
 * WHERE no row passes. One in a branch a CASE, an IFF, an NVL2 or a DECODE chooses is answered per row, a constant
 * guard dropping it, and one whose own aggregate reads the outer row too is no such subquery (all live-verified).
 */
public class OuterAggregateNoRowTest extends MeasuredCellsTestSupport {

    private static final String REFUSED =
        "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8";

    @Test
    public void noRowReachesTheSubquery() {
        engine.execute("CREATE OR REPLACE TABLE o918 (i INT, a ARRAY)");
        engine.execute("INSERT INTO o918 SELECT 0, ARRAY_CONSTRUCT(1)");
        engine.execute("CREATE OR REPLACE TABLE g918 (k INT, v INT)");
        engine.execute("INSERT INTO g918 VALUES (1, 10), (1, 20), (2, 30)");
        engine.execute("CREATE OR REPLACE TABLE e918 (i INT)");
        assertCells(new String[][] {
            {"SELECT (SELECT MAX(e918.i) FROM g918) FROM e918", REFUSED},
            {"SELECT (SELECT MAX(o918.i) FROM g918) FROM o918 WHERE FALSE", REFUSED},
        });
    }

    @Test
    public void aWhereNoRowPassesAndTheGuards() {
        engine.execute("CREATE OR REPLACE TABLE o920 (i INT)");
        engine.execute("INSERT INTO o920 VALUES (0)");
        engine.execute("CREATE OR REPLACE TABLE g920 (k INT, v INT)");
        engine.execute("INSERT INTO g920 VALUES (1, 10), (1, 20), (2, 30)");
        engine.execute("CREATE OR REPLACE TABLE m920 (i INT)");
        engine.execute("INSERT INTO m920 VALUES (0), (1)");
        assertCells(new String[][] {
            {"SELECT (SELECT MAX(m920.i) FROM g920) FROM m920 WHERE FALSE", REFUSED},
            {"SELECT (SELECT MAX(o920.i) FROM g920) FROM o920 WHERE o920.i > 5", REFUSED},
            {"SELECT (SELECT SUM(v + m920.i) FROM g920) FROM m920 WHERE FALSE", ""},
            {"SELECT CASE WHEN o920.i > 5 THEN (SELECT MAX(o920.i) FROM g920) END FROM o920", "null"},
            {"SELECT EXISTS (SELECT MAX(o920.i) FROM g920) FROM o920 WHERE FALSE", ""},
        });
    }

    @Test
    public void aWrapperEvaluatingItWhateverTheRowHolds() {
        engine.execute("CREATE OR REPLACE TABLE e997 (i INT)");
        engine.execute("CREATE OR REPLACE TABLE o997 (i INT)");
        engine.execute("INSERT INTO o997 VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE g997 (v INT)");
        engine.execute("INSERT INTO g997 VALUES (7)");
        final String at = "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position ";
        assertCells(new String[][] {
            {"SELECT COALESCE((SELECT MAX(e997.i) FROM g997), 0) FROM e997", at + "17"},
            {"SELECT NVL((SELECT MAX(e997.i) FROM g997), 0) FROM e997", at + "12"},
            {"SELECT 1 + (SELECT MAX(e997.i) FROM g997) FROM e997", at + "12"},
            {"SELECT ZEROIFNULL((SELECT MAX(e997.i) FROM g997)) FROM e997", at + "18"},
            {"SELECT COALESCE((SELECT MAX(o997.i) FROM g997), 0) FROM o997", at + "17"},
            // A constant guard drops the branch; a guard over the row is judged per row.
            {"SELECT CASE WHEN FALSE THEN (SELECT MAX(e997.i) FROM g997) END FROM e997", ""},
            {"SELECT IFF(FALSE, (SELECT MAX(e997.i) FROM g997), 0) FROM e997", ""},
            {"SELECT CASE WHEN TRUE THEN 1 ELSE (SELECT MAX(e997.i) FROM g997) END FROM e997", ""},
            {"SELECT IFF(o997.i > 5, (SELECT MAX(o997.i) FROM g997), NULL) FROM o997", "null"},
            {"SELECT NVL2(o997.i, 1, (SELECT MAX(o997.i) FROM g997)) FROM o997", "1"},
            {"SELECT DECODE(o997.i, 1, 0, (SELECT MAX(o997.i) FROM g997)) FROM o997", "0"},
            {"SELECT CASE WHEN TRUE THEN 1 ELSE (SELECT MAX(o997.i) FROM g997) END FROM o997", "1"},
        });
    }

    /**
     * A branch the statistics settle against is dropped: over a column holding 1 and 10, {@code m.i = 5} is left open
     * and {@code m.i > 50} settled; an empty input, or WHERE FALSE, settles nothing but a constant, which IFF(TRUE, …)
     * and CASE WHEN TRUE … are. A subquery reading no table evaluates its item over the outer row, the subqueries in
     * it refused at their places; a COALESCE's or an NVL's later argument is judged per row (all live-verified).
     */
    @Test
    public void aGuardTheStatisticsSettleOrANestedItem() {
        engine.execute("CREATE OR REPLACE TABLE e1001 (i INT)");
        engine.execute("CREATE OR REPLACE TABLE o1001 (i INT)");
        engine.execute("INSERT INTO o1001 VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE m1001 (i INT)");
        engine.execute("INSERT INTO m1001 VALUES (1), (10)");
        engine.execute("CREATE OR REPLACE TABLE g1001 (v INT)");
        engine.execute("INSERT INTO g1001 VALUES (7)");
        assertCells(new String[][] {
            {"SELECT IFF(m1001.i = 5, (SELECT MAX(m1001.i) FROM g1001), NULL) FROM m1001",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 25"},
            {"SELECT IFF(m1001.i > 50, (SELECT MAX(m1001.i) FROM g1001), NULL) FROM m1001",
                "null ;; null"},
            {"SELECT IFF(o1001.i > 5, (SELECT MAX(o1001.i) FROM g1001), NULL) FROM o1001 WHERE FALSE",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 25"},
            {"SELECT IFF(o1001.i < 5, (SELECT MAX(o1001.i) FROM g1001), NULL) FROM o1001 WHERE FALSE",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 25"},
            {"SELECT (SELECT (SELECT MAX(o1001.i) FROM g1001)) FROM o1001",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 16"},
            {"SELECT (SELECT 1 + (SELECT MAX(e1001.i) FROM g1001)) FROM e1001",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 20"},
            {"SELECT (SELECT IFF(FALSE, (SELECT MAX(e1001.i) FROM g1001), 0)) FROM e1001",
                ""},
            {"SELECT COALESCE(1, (SELECT MAX(e1001.i) FROM g1001)) FROM e1001",
                ""},
            {"SELECT NVL(e1001.i, (SELECT MAX(e1001.i) FROM g1001)) FROM e1001",
                ""},
            {"SELECT COALESCE(o1001.i, (SELECT MAX(o1001.i) FROM g1001)) FROM o1001",
                "1"},
            {"SELECT CASE WHEN e1001.i > 5 THEN 1 ELSE (SELECT MAX(e1001.i) FROM g1001) END FROM e1001",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 42"},
            {"SELECT IFF(e1001.i > 5, 1, (SELECT MAX(e1001.i) FROM g1001)) FROM e1001",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 28"},
            {"SELECT IFF(TRUE, (SELECT MAX(e1001.i) FROM g1001), 0) FROM e1001",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT CASE WHEN TRUE THEN (SELECT MAX(e1001.i) FROM g1001) END FROM e1001",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 28"},
        });
    }
}
