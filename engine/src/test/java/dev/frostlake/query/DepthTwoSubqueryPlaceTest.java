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
 * Which of several correlated subqueries a statement is refused for: a WHERE's subquery whose own subquery reads
 * the outer row, given no place alone, is placed beside any other subquery of the statement (all live-verified).
 */
public class DepthTwoSubqueryPlaceTest extends MeasuredCellsTestSupport {

    @Test
    public void severalSubqueries() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute("CREATE OR REPLACE FUNCTION f1(x INT) RETURNS INT AS 'x + 1'");
        engine.execute("CREATE OR REPLACE FUNCTION f2(x INT) RETURNS INT AS 'x + 2'");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8",
            "SELECT (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5), (SELECT (SELECT "
                + "g2.id FROM g g2 WHERE g2.v = fz.id) FROM g WHERE g.id = 6) FROM fz");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8",
            "SELECT (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5), (SELECT v FROM g "
                + "WHERE g.id > fz.id LIMIT 1), (SELECT (SELECT g2.id FROM g g2 WHERE g2.v = fz.id) FROM g WHERE "
                + "g.id = 6) FROM fz");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 25",
            "SELECT id FROM fz WHERE (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5) > 0 "
                + "AND (SELECT (SELECT g2.id FROM g g2 WHERE g2.v = fz.id) FROM g WHERE g.id = 6) > 0");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1((SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5)), f2((SELECT "
                + "(SELECT g2.id FROM g g2 WHERE g2.v = fz.id) FROM g WHERE g.id = 6)) FROM fz");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 67",
            "SELECT (SELECT v FROM g WHERE g.id > fz.id LIMIT 1) FROM fz WHERE (SELECT (SELECT g2.v FROM g g2 WHERE "
                + "g2.id = fz.id) FROM g WHERE g.id = 5) > 0");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 25",
            "SELECT id FROM fz WHERE (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5) > 0 "
                + "ORDER BY (SELECT v FROM g WHERE g.id > fz.id LIMIT 1)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 25",
            "SELECT id FROM fz WHERE (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5) > 0 "
                + "AND (SELECT v FROM g WHERE g.id > fz.id LIMIT 1) > 0");
    }
}
