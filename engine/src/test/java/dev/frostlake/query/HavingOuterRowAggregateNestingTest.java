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
 * A HAVING aggregate over a subquery whose own aggregate reads only the outer group's column is a nesting of
 * aggregates: under a MAX, a MIN, a COUNT or a SUM it is refused as the aggregate CORRELATION nests in the
 * outer one, beside a literal it stands beside, an AVG of whole numbers spelled as its SUM over its COUNT, and an
 * outer AVG is an unsupported subquery; one whose WHERE also reads the outer row is not a valid group by
 * expression, and an OR beside such a subquery settles nothing (all live-verified).
 */
public class HavingOuterRowAggregateNestingTest extends MeasuredCellsTestSupport {

    @Test
    public void outerRowAggregates() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute("CREATE OR REPLACE TABLE fz3 (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz3 VALUES (5, TRUE), (7, FALSE), (100, FALSE)");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ.ID)] nested in "
                + "[MAX(CORRELATION(MAX(FZ.ID)))]",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MAX(fz.id) FROM g WHERE g.id > 100)) IS NULL ORDER BY b");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ.ID)] nested in "
                + "[MAX(CORRELATION(MAX(FZ.ID)))]",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MAX(fz.id) FROM g WHERE g.id = 5)) = 7 ORDER BY b");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ.ID AS "
                + "NUMBER(38,0)))] nested in [MAX(CORRELATION(SUM(CAST(FZ.ID AS NUMBER(38,0)))))]",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT SUM(fz.id) FROM g)) = 12 ORDER BY b");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(SCALED_ROUND_INT_DIVIDE(CAST(SUM(CAST(FZ.ID AS "
                + "NUMBER(38,0))) AS NUMBER(38,0)), CAST(COUNT(FZ.ID) AS NUMBER(18,0)))))]",
            "SELECT b FROM fz GROUP BY b HAVING SUM((SELECT AVG(fz.id) FROM g WHERE g.id = 5)) = 12 ORDER BY b");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ.ID)] nested in "
                + "[COUNT(CORRELATION(MAX(FZ.ID)))]",
            "SELECT b FROM fz GROUP BY b HAVING COUNT((SELECT MAX(fz.id) FROM g WHERE g.id = 5)) = 1 ORDER BY b");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ.ID)] nested in "
                + "[MAX(CORRELATION(MAX(FZ.ID)) + 1)]",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MAX(fz.id) + 1 FROM g WHERE g.id = 5)) = 8 ORDER BY b");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 39",
            "SELECT b FROM fz GROUP BY b HAVING AVG((SELECT MIN(fz.id) FROM g WHERE g.id = 5)) = 5 ORDER BY b");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [COUNT(FZ.ID)] nested "
                + "in [MAX(CORRELATION(COUNT(FZ.ID)))]",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT COUNT(fz.id) FROM g WHERE g.id = 5)) = 1 ORDER BY b");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ.ID * 2)] nested "
                + "in [MAX(CORRELATION(MAX(FZ.ID * 2)))]",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MAX(fz.id * 2) FROM g WHERE g.id = 5)) = 14 ORDER BY b");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 14",
            "SELECT b, MAX((SELECT MAX(fz.id) FROM g WHERE g.id = 5)) FROM fz GROUP BY b ORDER BY b");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ.ID)] nested in "
                + "[MAX(CORRELATION(MAX(FZ.ID)))]",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MAX(fz.id) FROM g WHERE g.id = 5)) = 7 OR b ORDER BY b");
    }

    @Test
    public void outerRowAggregatesUnderAnOuterReadingWhere() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute("CREATE OR REPLACE TABLE fz3 (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz3 VALUES (5, TRUE), (7, FALSE), (100, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE fz4 (id INT)");
        engine.execute("INSERT INTO fz4 VALUES (-1), (5), (7)");
        assertAnswer("REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT b FROM fz GROUP BY b HAVING MIN((SELECT MAX(fz.id) FROM g WHERE g.v > fz.id * 10)) = 5 ORDER BY b");
        assertAnswer("REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT b FROM fz GROUP BY b HAVING MIN((SELECT MAX(fz.id) FROM g WHERE g.v > fz.id * 10)) IS NULL ORDER "
                + "BY b");
        assertAnswer("REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT b FROM fz GROUP BY b HAVING COUNT((SELECT MAX(fz.id) FROM g WHERE g.v > fz.id * 10)) = 1 ORDER "
                + "BY b");
        assertAnswer("REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT COUNT(fz.id) FROM g WHERE g.v > fz.id * 10)) = 1 ORDER "
                + "BY b");
        assertAnswer("REFUSED SQL compilation error: | [FZ3.ID] is not a valid group by expression",
            "SELECT b FROM fz3 GROUP BY b HAVING MAX((SELECT MAX(fz3.id) FROM g WHERE g.v > fz3.id)) = 100 ORDER BY b");
        assertAnswer("REFUSED SQL compilation error: | [FZ3.ID] is not a valid group by expression",
            "SELECT b FROM fz3 GROUP BY b HAVING MAX((SELECT MAX(fz3.id) FROM g WHERE g.v > fz3.id)) = 7 ORDER BY b");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[MAX(MAX(G.V))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(v) FROM g WHERE g.id < fz.id)) > 0 OR id > 6 ORDER "
                + "BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[MAX(MAX(G.V))]",
            "SELECT id FROM fz4 GROUP BY id HAVING MAX((SELECT MAX(v) FROM g WHERE g.id < fz4.id)) > 0 OR id > 0 "
                + "ORDER BY id");
    }
}
