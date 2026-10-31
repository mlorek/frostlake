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
 * A star over a chain of USING joins is one view to the plan echo, SYS_VW: the keys once, then each relation's
 * other columns, a name met again suffixed; a USING column two relations to its left both show is ambiguous (all
 * live-verified).
 */
public class UsingJoinChainEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void twoRelations() {
        engine.execute("CREATE OR REPLACE TABLE t1 (c VARCHAR)");
        engine.execute("INSERT INTO t1 VALUES ('a')");
        engine.execute("CREATE OR REPLACE PROCEDURE qa(c VARCHAR) RETURNS TABLE (x VARCHAR) LANGUAGE SQL AS $$ "
            + "DECLARE r RESULTSET DEFAULT (SELECT :c AS x); BEGIN RETURN TABLE(r); END; $$");
        engine.execute("CREATE OR REPLACE TABLE tsrc (n VARCHAR, i INT)");
        engine.execute("INSERT INTO tsrc VALUES ('x', 1)");
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b INT)");
        engine.execute("INSERT INTO fz VALUES (1, 2)");
        assertAnswer("REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.B, SYS_VW.B_0))'",
            "SELECT TO_JSON(DISTINCT {*}) FROM fz f JOIN fz g USING (id)");
    }

    @Test
    public void chains() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b INT)");
        engine.execute("INSERT INTO fz VALUES (1, 2)");
        engine.execute("CREATE OR REPLACE TABLE fz3 (id INT, b INT, c INT)");
        engine.execute("INSERT INTO fz3 VALUES (1, 2, 3)");
        engine.execute("CREATE OR REPLACE TABLE fa (id INT, a INT)");
        engine.execute("CREATE OR REPLACE TABLE fb (id INT, b INT)");
        engine.execute("CREATE OR REPLACE TABLE fc (id INT, c INT)");
        engine.execute("CREATE OR REPLACE TABLE fd (id INT, d INT)");
        assertAnswer("REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.A, SYS_VW.B, SYS_VW.C))'",
            "SELECT TO_JSON(DISTINCT {*}) FROM fa JOIN fb USING (id) JOIN fc USING (id)");
        assertAnswer("REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.A, SYS_VW.B, SYS_VW.C, SYS_VW.D))'",
            "SELECT TO_JSON(DISTINCT {*}) FROM fa JOIN fb USING (id) JOIN fc USING (id) JOIN fd USING (id)");
        assertAnswer("REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.B, SYS_VW.B_0))'",
            "SELECT TO_JSON(DISTINCT {*}) FROM fz f JOIN fz g USING (id, b) JOIN fz h USING (id)");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'B'",
            "SELECT TO_JSON(DISTINCT {*}) FROM fz f JOIN fz g ON f.id = g.id JOIN fz h USING (b)");
        assertShape("ID:NUMBER(38,0), B:NUMBER(38,0), B:NUMBER(38,0)", "SELECT * FROM fz f JOIN fz g USING (id)");
        assertAnswer("REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.B, SYS_VW.C))'",
            "SELECT TO_JSON(DISTINCT {*}) FROM fz f NATURAL JOIN fz3 g NATURAL JOIN fz h");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'B'",
            "SELECT TO_JSON(DISTINCT {*}) FROM fz f JOIN fz g USING (id) JOIN fz h USING (b)");
        assertAnswer("REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(SYS_VW.ID, SYS_VW.A, SYS_VW.B, SYS_VW.B_0, SYS_VW.C))'",
            "SELECT TO_JSON(DISTINCT {*}) FROM fa JOIN fz USING (id) JOIN fz3 USING (id)");
    }
}
