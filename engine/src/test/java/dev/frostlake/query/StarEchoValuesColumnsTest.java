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
 * A star argument over a VALUES clause given an alias list echoes the columns by the names the clause gives them,
 * {@code W.COLUMN1, W.COLUMN2}, under the alias; and a USING join matches a VALUES clause's column by that name too, so
 * {@code (VALUES (1)) AS w (id) JOIN fz USING (id)} is {@code Invalid identifier ID} (live-verified).
 */
public class StarEchoValuesColumnsTest extends MeasuredCellsTestSupport {

    @Test
    public void aValuesClauseKeepsItsColumnNames() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE lo (o OBJECT, a ARRAY, v VARIANT, bn BINARY)");
        assertCells(new String[][] {
            {"SELECT TO_JSON(DISTINCT {*}) FROM (VALUES (1, 2)) AS w (m, n)",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(W.COLUMN1, W.COLUMN2))'"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM (VALUES (1, 2)) AS w",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(W.COLUMN1, W.COLUMN2))'"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM fz f JOIN fz g ON f.id = g.id",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(F.ID, F.B, G.ID, G.B))'"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM (VALUES (1, 2), (3, 4)) w (m, n)",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(W.COLUMN1, W.COLUMN2))'"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM (VALUES (1, 2)) w (m, n) JOIN fz USING (id)",
                "REFUSED SQL compilation error: | Invalid identifier ID"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM (VALUES (1)) AS w (id) JOIN fz USING (id)",
                "REFUSED SQL compilation error: | Invalid identifier ID"},
        });
    }
}
