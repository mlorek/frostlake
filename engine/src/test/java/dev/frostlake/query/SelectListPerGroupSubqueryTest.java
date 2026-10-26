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
 * A grouped select list's MAX, MIN, SUM, COUNT or ANY_VALUE over a subquery that reads the group's keys, standing
 * under a comparison or an IS NULL, is computed once for the group over its keys, as a HAVING computes it: NOT,
 * AND, OR, CASE, IFF and COALESCE over the test keep three-valued logic, a group of two rows counts the subquery's
 * row once, a subquery reading a column the select does not group by is refused at the column, one with an
 * aggregate of its own is refused as nested aggregates, and the aggregate standing bare or under arithmetic stays
 * refused (all live-verified).
 */
public class SelectListPerGroupSubqueryTest extends MeasuredCellsTestSupport {

    @Test
    public void aTestOverThePerGroupAggregateIsAnswered() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        assertAnswer("5|false ;; 7|null",
            "SELECT id, NOT (MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0) FROM fz GROUP BY id ORDER BY id");
        assertAnswer("5|true ;; 7|null",
            "SELECT id, MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0 FROM fz GROUP BY id ORDER BY id");
        assertAnswer("5|false ;; 7|null",
            "SELECT id, NOT (MAX((SELECT g.v FROM g WHERE g.id = fz.id)) = 50) FROM fz GROUP BY id ORDER BY id");
        assertAnswer("5|false ;; 7|true",
            "SELECT id, MAX((SELECT g.v FROM g WHERE g.id = fz.id)) IS NULL FROM fz GROUP BY id ORDER BY id");
        assertAnswer("5|y ;; 7|n",
            "SELECT id, CASE WHEN MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0 THEN 'y' ELSE 'n' END FROM fz "
                + "GROUP BY id ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 15",
            "SELECT id, MAX((SELECT g.v FROM g WHERE g.id = fz.id)) + 1 FROM fz GROUP BY id ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 15",
            "SELECT id, MAX((SELECT g.v FROM g WHERE g.id = fz.id)) AS m FROM fz GROUP BY id ORDER BY id");
        assertAnswer("5|false ;; 7|null",
            "SELECT id, NOT (MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0) AS x FROM fz GROUP BY id ORDER BY id");
        assertAnswer("5|true ;; 7|null",
            "SELECT id, NOT (MIN((SELECT g.v FROM g WHERE g.id = fz.id)) > 55) FROM fz GROUP BY id ORDER BY id");
        assertAnswer("5|false ;; 7|true",
            "SELECT id, NOT (COUNT((SELECT g.v FROM g WHERE g.id = fz.id)) > 0) FROM fz GROUP BY id ORDER BY id");
        assertAnswer("5|false ;; 7|null",
            "SELECT id, NOT (SUM((SELECT g.v FROM g WHERE g.id = fz.id)) > 0) FROM fz GROUP BY id ORDER BY id");
        assertAnswer("5|true ;; 7|null",
            "SELECT id, (MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0) AND TRUE FROM fz GROUP BY id ORDER BY id");
        assertAnswer("5|false ;; 7|null",
            "SELECT id, NOT (MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0) OR FALSE FROM fz GROUP BY id ORDER BY "
                + "id");
        assertAnswer("5|true ;; 7|null",
            "SELECT id, MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0 AS c FROM fz GROUP BY id ORDER BY id");
        assertAnswer("5|false ;; 7|null",
            "SELECT id, NOT (MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0) FROM fz GROUP BY id HAVING id > 0 "
                + "ORDER BY id");
        assertAnswer("5|false ;; 7|null",
            "SELECT id, NOT (MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0) FROM fz GROUP BY id ORDER BY 2, 1");
        assertAnswer("5|1 ;; 7|0",
            "SELECT id, IFF(MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0, 1, 0) FROM fz GROUP BY id ORDER BY id");
        assertAnswer("5|true ;; 7|false",
            "SELECT id, NOT (MAX((SELECT g.v FROM g WHERE g.id = fz.id)) IS NULL) FROM fz GROUP BY id ORDER BY id");
        assertAnswer("5|true ;; 7|false",
            "SELECT id, COALESCE(MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0, FALSE) FROM fz GROUP BY id ORDER "
                + "BY id");
    }

    @Test
    public void aGroupOfSeveralRowsReadsTheSubqueryOnce() {
        engine.execute("CREATE OR REPLACE TABLE fz2 (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz2 VALUES (5, TRUE), (5, FALSE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        assertAnswer("5|false ;; 7|null",
            "SELECT id, NOT (MAX((SELECT g.v FROM g WHERE g.id = fz2.id)) > 0) FROM fz2 GROUP BY id ORDER BY id");
        assertAnswer("5|false ;; 7|false",
            "SELECT id, COUNT((SELECT g.v FROM g WHERE g.id = fz2.id)) > 1 FROM fz2 GROUP BY id ORDER BY id");
        assertAnswer("5|true ;; 7|false",
            "SELECT id, COUNT((SELECT g.v FROM g WHERE g.id = fz2.id)) = 1 FROM fz2 GROUP BY id ORDER BY id");
        assertAnswer("5|false ;; 7|null",
            "SELECT id, SUM((SELECT g.v FROM g WHERE g.id = fz2.id)) = 100 FROM fz2 GROUP BY id ORDER BY id");
        assertAnswer("5|true ;; 7|null",
            "SELECT id, SUM((SELECT g.v FROM g WHERE g.id = fz2.id)) = 50 FROM fz2 GROUP BY id ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 51 | 'FZ2.ID' in select clause is "
                + "neither an aggregate nor in the group by clause.",
            "SELECT b, NOT (MAX((SELECT g.v FROM g WHERE g.id = fz2.id)) > 0) FROM fz2 GROUP BY b ORDER BY b");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 48 | 'FZ2.ID' in select clause is "
                + "neither an aggregate nor in the group by clause.",
            "SELECT b, COUNT((SELECT g.v FROM g WHERE g.id = fz2.id)) = 2 FROM fz2 GROUP BY b ORDER BY b");
        assertAnswer("",
            "SELECT id FROM fz2 GROUP BY id HAVING COUNT((SELECT g.v FROM g WHERE g.id = fz2.id)) > 1 ORDER BY id");
        assertAnswer("",
            "SELECT id FROM fz2 GROUP BY id HAVING SUM((SELECT g.v FROM g WHERE g.id = fz2.id)) = 100 ORDER BY id");
        assertAnswer("5|few ;; 7|few",
            "SELECT id, CASE WHEN COUNT((SELECT g.v FROM g WHERE g.id = fz2.id)) > 1 THEN 'many' ELSE 'few' END FROM "
                + "fz2 GROUP BY id ORDER BY id");
        assertAnswer("1|null ;; 2|true",
            "SELECT COUNT(*), MAX((SELECT g.v FROM g WHERE g.id = fz2.id)) > 0 FROM fz2 GROUP BY id ORDER BY 1");
        assertAnswer("5|true|false ;; 7|null|true",
            "SELECT id, MAX((SELECT g.v FROM g WHERE g.id = fz2.id)) > 0, MIN((SELECT g.v FROM g WHERE g.id = "
                + "fz2.id)) IS NULL FROM fz2 GROUP BY id ORDER BY id");
        assertAnswer("5|true",
            "SELECT id, MAX((SELECT g.v FROM g WHERE g.id = fz2.id)) > 0 FROM fz2 GROUP BY id HAVING MAX((SELECT g.v "
                + "FROM g WHERE g.id = fz2.id)) > 0 ORDER BY id");
        assertAnswer("5|true ;; 7|true",
            "SELECT id, MAX((SELECT g.v FROM g WHERE g.id = fz2.id AND g.v > 55)) IS NULL FROM fz2 GROUP BY id ORDER "
                + "BY id");
        assertAnswer("5|true ;; 7|null",
            "SELECT id, MAX((SELECT g.v FROM g WHERE g.id = fz2.id) + 1) > 0 FROM fz2 GROUP BY id ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(G.V)] nested in "
                + "[MAX(MAX(G.V))]",
            "SELECT id, MAX((SELECT MAX(g.v) FROM g WHERE g.id = fz2.id)) > 0 FROM fz2 GROUP BY id ORDER BY id");
        assertAnswer("5|true ;; 7|null",
            "SELECT id, ANY_VALUE((SELECT g.v FROM g WHERE g.id = fz2.id)) > 0 FROM fz2 GROUP BY id ORDER BY id");
        assertAnswer("5|true ;; 7|null",
            "SELECT id, MAX((SELECT g.v FROM g WHERE g.id = fz2.id)) > 0 AND MIN((SELECT g.v FROM g WHERE g.id = "
                + "fz2.id)) > 0 FROM fz2 GROUP BY id ORDER BY id");
        assertAnswer("5|true ;; 7|null",
            "SELECT id, (MAX((SELECT g.v FROM g WHERE g.id = fz2.id)) > 0) = TRUE FROM fz2 GROUP BY id ORDER BY id");
    }
}
