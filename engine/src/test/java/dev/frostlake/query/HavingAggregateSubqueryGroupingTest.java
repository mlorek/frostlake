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
 * A query written as a value inside a HAVING aggregate's argument reads the enclosing query's columns against
 * the groups: an ungrouped one is refused as not a valid group by expression, as it is for a query written in
 * HAVING directly. A query with no FROM, one under EXISTS and a condition an OR TRUE settles are not judged; in
 * the select list and in ORDER BY the same subquery is a correlation. Every answer is live-verified.
 */
public class HavingAggregateSubqueryGroupingTest extends MeasuredCellsTestSupport {

    @Test
    public void anUngroupedColumnUnderTheAggregate() {
        engine.execute(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute(
            "INSERT INTO g VALUES (5, 50), (6, 60)");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MAX(fz.id + g.v) FROM g)) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MAX(v) FROM g WHERE g.id < fz.id)) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT MAX(id) FROM fz HAVING MAX((SELECT MAX(fz.id + g.v) FROM g)) > 0");
        assertAnswer(
            "false ;; true",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MAX(g.v) FROM g)) > 0 ORDER BY b");
        assertAnswer(
            "false ;; true",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT fz.id + 1)) > 0 ORDER BY b");
        assertAnswer(
            "false ;; true",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MAX(g.v) FROM g WHERE g.id < fz.id)) > 0 OR TRUE "
                + "ORDER BY b");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 14",
            "SELECT b, MAX((SELECT MAX(fz.id + g.v) FROM g)) FROM fz GROUP BY b");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 41",
            "SELECT b FROM fz GROUP BY b ORDER BY MAX((SELECT MAX(fz.id + g.v) FROM g))");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT b FROM fz GROUP BY b HAVING MIN((SELECT COUNT(*) FROM g WHERE g.id = fz.id)) >= 0");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT b FROM fz GROUP BY b HAVING SUM((SELECT 1 FROM g WHERE g.id = fz.id)) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT b FROM fz GROUP BY b HAVING COUNT((SELECT g.v FROM g WHERE g.id = fz.id)) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT b FROM fz GROUP BY b HAVING MAX(id + (SELECT MAX(g.v) FROM g WHERE g.id = fz.id)) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MAX(g.v) FROM g WHERE g.id < fz.id)) > 0 AND TRUE");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT b FROM fz GROUP BY b HAVING NOT (MAX((SELECT MAX(g.v) FROM g WHERE g.id < fz.id)) > 0)");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT b FROM fz GROUP BY b HAVING (SELECT MAX(g.v) FROM g WHERE g.id < fz.id) > 0");
        assertAnswer(
            "false",
            "SELECT b FROM fz GROUP BY b HAVING (SELECT MAX(g.v) FROM g WHERE g.id < MAX(fz.id)) > 0 ORDER BY b");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT b FROM fz GROUP BY b HAVING EXISTS (SELECT 1 FROM g WHERE g.id = fz.id)");
        assertAnswer(
            "true",
            "SELECT b FROM fz GROUP BY b HAVING MAX(IFF(EXISTS (SELECT 1 FROM g WHERE g.id = fz.id), 1, 0)) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT b FROM fz GROUP BY b HAVING MAX(id) IN (SELECT g.id FROM g WHERE g.v > fz.id)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 28 | found QUALIFY clause but no window "
                + "function.",
            "SELECT b FROM fz GROUP BY b QUALIFY MAX((SELECT MAX(fz.id + g.v) FROM g)) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 81 | invalid identifier 'NOSUCH'",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MAX(fz.id + g.v) FROM g)) > 0 AND nosuch = 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 35 | invalid identifier 'NOSUCH'",
            "SELECT b FROM fz GROUP BY b HAVING nosuch = 1 AND MAX((SELECT MAX(fz.id + g.v) FROM g)) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | [F.ID] is not a valid group by expression",
            "SELECT b FROM fz f GROUP BY b HAVING MAX((SELECT MAX(f.id + g.v) FROM g)) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT fz.id FROM g LIMIT 1)) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MAX(g.v) FROM g WHERE g.id < fz.id)) IS NOT NULL OR b "
                + "ORDER BY b");
    }
}
