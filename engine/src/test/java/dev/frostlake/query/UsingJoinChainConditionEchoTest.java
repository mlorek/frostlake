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
 * A star over a chain of USING or NATURAL joins that a condition, a cross join or a comma continues echoes the chain as
 * its one view, SYS_VW, ahead of the relations joined to it, which keep their own names wherever the FROM writes them
 * (live-verified).
 */
public class UsingJoinChainConditionEchoTest extends MeasuredCellsTestSupport {

    /** Chains followed by ON, LEFT, CROSS and comma joins, qualified stars and EXCLUDE. */
    @Test
    public void chainThenCondition() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b INT)");
        engine.execute("CREATE OR REPLACE TABLE fa (id INT, a INT)");
        engine.execute("CREATE OR REPLACE TABLE fb (id INT, b INT)");
        engine.execute("CREATE OR REPLACE TABLE fc (id INT, c INT)");
        engine.execute("CREATE OR REPLACE TABLE fd (id INT, d INT)");
        assertCells(new String[][] {
            {"SELECT TO_JSON(DISTINCT {*}) FROM fz f JOIN fz g USING (id) JOIN fz h ON f.id = h.id",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.B, SYS_VW.B_0, H.ID, H.B))'"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM fa JOIN fb USING (id) JOIN fc ON fa.id = fc.id",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.A, SYS_VW.B, FC.ID, FC.C))'"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM fa JOIN fb USING (id) JOIN fc USING (id) JOIN fd ON fa.id = fd.id",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.A, SYS_VW.B, SYS_VW.C, FD.ID, FD.D))'"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM fa JOIN fb ON fa.id = fb.id JOIN fc USING (id)",
                "REFUSED SQL compilation error: | ambiguous column name 'ID'"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM fa x JOIN fb y USING (id) JOIN fc z ON x.id = z.id",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.A, SYS_VW.B, Z.ID, Z.C))'"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM fa JOIN fb USING (id) LEFT JOIN fc ON fa.id = fc.id",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.A, SYS_VW.B, FC.ID, FC.C))'"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM fa JOIN fb USING (id) CROSS JOIN fc",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.A, SYS_VW.B, FC.ID, FC.C))'"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM fa JOIN fb USING (id), fc",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.A, SYS_VW.B, FC.ID, FC.C))'"},
            {"SELECT TO_JSON(DISTINCT {fc.*}) FROM fa JOIN fb USING (id) JOIN fc ON fa.id = fc.id",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(FC.ID, FC.C))'"},
            {"SELECT TO_JSON(DISTINCT {fa.*}) FROM fa JOIN fb USING (id) JOIN fc ON fa.id = fc.id",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.A))'"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM fa JOIN fb USING (id) JOIN fc ON fa.id = fc.id JOIN fd USING (id)",
                "REFUSED SQL compilation error: | ambiguous column name 'ID'"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM fa NATURAL JOIN fb JOIN fc ON fa.id = fc.id",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.A, SYS_VW.B, FC.ID, FC.C))'"},
            {"SELECT TO_JSON(DISTINCT {* EXCLUDE c}) FROM fa JOIN fb USING (id) JOIN fc ON fa.id = fc.id",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.A, SYS_VW.B, FC.ID))'"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM fa JOIN fb USING (id) JOIN fc ON fa.id = fc.id JOIN fd ON fa.id = "
                + "fd.id",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.A, SYS_VW.B, FC.ID, FC.C, FD.ID, FD.D))'"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM fa JOIN fb ON fa.id = fb.id JOIN fc ON fa.id = fc.id",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(FA.ID, FA.A, FB.ID, FB.B, FC.ID, FC.C))'"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM fz f JOIN fz g USING (id) JOIN fz h ON f.id = h.id JOIN fz k ON f.id = "
                + "k.id",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.B, SYS_VW.B_0, H.ID, H.B, K.ID, K.B))'"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM (fa JOIN fb USING (id)) JOIN fc ON fa.id = fc.id",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.A, SYS_VW.B, FC.ID, FC.C))'"},
            {"SELECT TO_JSON(DISTINCT {*}) FROM fc JOIN (fa JOIN fb USING (id)) ON fa.id = fc.id",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.A, SYS_VW.B, FC.ID, FC.C))'"},
        });
    }
}
