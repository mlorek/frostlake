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
 * A GREATEST or a LEAST one of whose arguments is a constant that folds to NULL — NULL, a cast of it, a CASE with no
 * branch taken — is NULL, and so is a BETWEEN or a NOT BETWEEN whose subject is: the plan folds them, and a correlated
 * subquery among the other operands is neither judged nor run. A NULL bound, GREATEST_IGNORE_NULLS and a GREATEST
 * over values leave the subquery to be refused (all live-verified).
 */
public class GreatestBetweenNullFoldTest extends MeasuredCellsTestSupport {

    @Test
    public void aNullOperandFoldsTheCallAndTheBetween() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute("CREATE OR REPLACE TABLE tt (id INT, value VARCHAR)");
        engine.execute("INSERT INTO tt VALUES (1, 'A'), (2, 'B')");
        engine.execute("CREATE OR REPLACE TABLE one (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO one VALUES (5, TRUE)");
        assertCells(new String[][] {
            {"SELECT id, NULL BETWEEN 1 AND (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz ORDER BY id",
                "5|null ;; 7|null"},
            {"SELECT id, GREATEST(NULL, (SELECT v FROM g WHERE g.id = fz.id)) AS x FROM fz ORDER BY id",
                "5|null ;; 7|null"},
            {"SELECT id, LEAST((SELECT v FROM g WHERE g.id = fz.id), NULL) AS x FROM fz ORDER BY id",
                "5|null ;; 7|null"},
            {"SELECT id, GREATEST(1, NULL, (SELECT v FROM g WHERE g.id = fz.id)) AS x FROM fz ORDER BY id",
                "5|null ;; 7|null"},
            {"SELECT id, NULL NOT BETWEEN 1 AND (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz ORDER BY id",
                "5|null ;; 7|null"},
            {"SELECT id, 1 BETWEEN NULL AND (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) BETWEEN NULL AND 1 AS x FROM fz ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, GREATEST_IGNORE_NULLS(NULL, (SELECT v FROM g WHERE g.id = fz.id)) AS x FROM fz ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 40"},
            {"SELECT id, GREATEST(1, (SELECT v FROM g WHERE g.id = fz.id)) AS x FROM fz ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 24"},
            {"SELECT id, GREATEST(NULL::INT, (SELECT v FROM g WHERE g.id = fz.id)) AS x FROM fz ORDER BY id",
                "5|null ;; 7|null"},
            {"SELECT id, LEAST(CASE WHEN FALSE THEN 1 END, (SELECT v FROM g WHERE g.id = fz.id)) AS x FROM fz "
                + "ORDER BY id",
                "5|null ;; 7|null"},
            {"SELECT id FROM fz WHERE GREATEST(NULL, (SELECT v FROM g WHERE g.id = fz.id)) IS NULL ORDER BY id",
                "5 ;; 7"},
        });
    }
}
