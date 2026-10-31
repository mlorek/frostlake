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
 * A NOT over a HAVING aggregate of a per-group subquery is the anti join it decorrelates into: a group the
 * subquery finds nothing for passes it (all live-verified).
 */
public class HavingNotPerGroupSubqueryTest extends MeasuredCellsTestSupport {

    @Test
    public void negations() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        assertAnswer("7",
            "SELECT id FROM fz GROUP BY id HAVING NOT (MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0) ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0 ORDER BY id");
        assertAnswer("7",
            "SELECT id FROM fz GROUP BY id HAVING NOT (MAX((SELECT g.v FROM g WHERE g.id = fz.id)) = 50) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 46",
            "SELECT id FROM fz GROUP BY id HAVING NOT (MAX((SELECT g.v FROM g WHERE g.id = fz.id)) IS NULL) ORDER BY "
                + "id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 41",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id)) IS NULL ORDER BY id");
        assertAnswer("7",
            "SELECT id FROM fz GROUP BY id HAVING NOT (MAX((SELECT g.v FROM g WHERE g.id = fz.id)) BETWEEN 0 AND "
                + "100) ORDER BY id");
        assertAnswer("",
            "SELECT id FROM fz GROUP BY id HAVING NOT (MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0 OR id = 7) "
                + "ORDER BY id");
        assertAnswer("7",
            "SELECT id FROM fz GROUP BY id HAVING NOT (MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0 AND id = 5) "
                + "ORDER BY id");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz GROUP BY id HAVING NOT (MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 100) ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING NOT NOT (MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0) ORDER BY "
                + "id");
        assertAnswer("7",
            "SELECT id FROM fz GROUP BY id HAVING NOT (MIN((SELECT g.v FROM g WHERE g.id = fz.id)) > 0) ORDER BY id");
        assertAnswer("7",
            "SELECT id FROM fz GROUP BY id HAVING NOT (SUM((SELECT g.v FROM g WHERE g.id = fz.id)) > 0) ORDER BY id");
        assertAnswer("7",
            "SELECT id FROM fz GROUP BY id HAVING NOT (COUNT((SELECT g.v FROM g WHERE g.id = fz.id)) > 0) ORDER BY id");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz GROUP BY id HAVING NOT (MAX((SELECT g.v FROM g WHERE g.id = fz.id)) < 0) ORDER BY id");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz GROUP BY id HAVING NOT (MAX((SELECT g.v FROM g WHERE g.id = fz.id)) <> 50) ORDER BY id");
    }
}
