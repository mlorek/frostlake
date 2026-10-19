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
 * A star over two relations joined with USING or NATURAL is echoed as the plan holds the join (live-verified):
 * one view, SYS_VW, whose columns are the keys once, then each side's other columns, a name met again
 * suffixed _0; a qualified star reads its side's columns under their view names, and an EXCLUDE of a name the
 * view holds twice is {@code ambiguous column name 'B'}.
 */
public class UsingJoinStarEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void aUsingJoinIsOneView() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE fy (id INT, c VARCHAR)");
        engine.execute("INSERT INTO fy VALUES (5, 'x')");
        engine.execute("CREATE OR REPLACE TABLE fx (id INT, b BOOLEAN, d INT)");
        engine.execute("INSERT INTO fx VALUES (5, TRUE, 1)");
        assertCells(new String[][] {
            {"SELECT TO_JSON(DISTINCT {*}) FROM fz f JOIN fz g USING (id)",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.B, SYS_VW.B_0))'"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM fz f LEFT JOIN fz g USING (id)",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.B, SYS_VW.B_0))'"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM fz f JOIN fz g USING (id, b)",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.B))'"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM fz f NATURAL JOIN fz g",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.B))'"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM fz f JOIN fy g USING (id)",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.B, SYS_VW.C))'"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM fz f JOIN fx g USING (id)",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.B, SYS_VW.B_0, SYS_VW.D))'"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM fz f JOIN fx g USING (id, b)",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.B, SYS_VW.D))'"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM fz f NATURAL JOIN fx g",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.B, SYS_VW.D))'"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM fz JOIN fy USING (id)",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.B, SYS_VW.C))'"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM fz f RIGHT JOIN fz g USING (id)",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.B, SYS_VW.B_0))'"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM fz f FULL JOIN fz g USING (id)",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.B, SYS_VW.B_0))'"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM fz f JOIN fz g ON f.id = g.id",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(F.ID, F.B, G.ID, G.B))'"},
            {"SELECT HASH(DISTINCT *) FROM fz f JOIN fz g USING (id)", "REFUSED Unsupported feature 'TOK_STAR'."},
            {"SELECT TO_JSON(DISTINCT {f.*}) FROM fz f JOIN fz g USING (id)",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.B))'"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM fz f NATURAL JOIN fy g",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.B, SYS_VW.C))'"},
            {"SELECT TO_JSON(DISTINCT {* EXCLUDE b}) FROM fz f JOIN fz g USING (id)",
                "REFUSED SQL compilation error: | ambiguous column name 'B'"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM (SELECT * FROM fz f JOIN fz g USING (id))",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(\"values\".ID, \"values\".B, \"values\".B))'"},
        });
    }
}
