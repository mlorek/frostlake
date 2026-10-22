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
 * A HAVING aggregate over an expression around a per-group subquery is computed over the rows the subquery
 * answers for the group, a group it answers none for reading as an empty aggregate: COALESCE brings no group back,
 * and COUNT of nothing is 0 (all live-verified).
 */
public class HavingAggregateExpressionTest extends MeasuredCellsTestSupport {

    @Test
    public void expressionArguments() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id) + 1) > 0 ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING MAX(COALESCE((SELECT g.v FROM g WHERE g.id = fz.id), 0)) >= 0 "
                + "ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id) * 2) > 0 ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING SUM((SELECT g.v FROM g WHERE g.id = fz.id) + 1) > 0 ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING MAX(ABS((SELECT g.v FROM g WHERE g.id = fz.id))) > 0 ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING MAX(NVL((SELECT g.v FROM g WHERE g.id = fz.id), 0)) >= 0 ORDER BY "
                + "id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING MAX(IFF((SELECT g.v FROM g WHERE g.id = fz.id) IS NULL, 0, 1)) >= "
                + "0 ORDER BY id");
        assertAnswer("5 ;; 7",
            "SELECT id FROM fz GROUP BY id HAVING COUNT((SELECT g.v FROM g WHERE g.id = fz.id) + 1) >= 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 42",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id) + 1) IS NULL ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING MAX(-(SELECT g.v FROM g WHERE g.id = fz.id)) < 0 ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id)::VARCHAR) > '0' ORDER "
                + "BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 42",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id) + (SELECT g.v FROM g "
                + "WHERE g.id = fz.id)) > 0 ORDER BY id");
        assertAnswer("",
            "SELECT id FROM fz GROUP BY id HAVING MIN(COALESCE((SELECT g.v FROM g WHERE g.id = fz.id), -1)) < 0 "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT b FROM fz GROUP BY b HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id) + 1) > 0 ORDER BY b");
        assertAnswer("7",
            "SELECT id FROM fz GROUP BY id HAVING NOT (MAX((SELECT g.v FROM g WHERE g.id = fz.id) + 1) > 0) ORDER BY "
                + "id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING MAX(COALESCE((SELECT g.v FROM g WHERE g.id = fz.id), 0) + 1) > 0 "
                + "ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id) + 1) > 0 AND id > 0 "
                + "ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING COUNT(COALESCE((SELECT g.v FROM g WHERE g.id = fz.id), 0)) > 0 "
                + "ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT g.v FROM g WHERE g.id = fz.id) / 10) > 0 ORDER BY id");
    }
}
