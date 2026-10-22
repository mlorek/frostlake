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
 * A grouped ORDER BY key holding a query, named as its plan prints it: a ROW tested by IN as that ROW, a NATURAL
 * or USING join over several shared columns as SYS_VW with its renamed columns, GROUP BY ALL over a constant and
 * a ROLLUP as the grouping sets it stands for (all live-verified).
 */
public class GroupedOrderKeyPlanPrintTest extends MeasuredCellsTestSupport {

    @Test
    public void keysAsThePlanPrintsThem() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR, f FLOAT, d NUMBER(10,2), dt DATE, "
            + "va VARIANT, n5 NUMBER(5,0), n38 NUMBER(38,10), ts TIMESTAMP_NTZ, n20 NUMBER(20,0), n21 NUMBER(21,"
            + "0), n109 NUMBER(10,9), n3837 NUMBER(38,37), n242 NUMBER(24,2), n232 NUMBER(23,2))");
        engine.execute("INSERT INTO fz SELECT 5, TRUE, 'a', 1.5, 2.25, '2020-01-01'::DATE, PARSE_JSON('1'), 5, 1.5, "
            + "'2020-01-01'::TIMESTAMP_NTZ, 5, 5, 1.5, 1.5, 1.5, 1.5");
        engine.execute("INSERT INTO fz SELECT 7, FALSE, 'b', 2.5, 3.25, '2020-01-02'::DATE, PARSE_JSON('2'), 7, 2.5, "
            + "'2020-01-02'::TIMESTAMP_NTZ, 7, 7, 2.5, 2.5, 2.5, 2.5");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT, s VARCHAR)");
        engine.execute("INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
        assertAnswer("REFUSED SQL compilation error: | [ROW(ID, ID)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY (id, id) IN (SELECT id, v FROM g), id");
        assertAnswer("REFUSED SQL compilation error: | [EXISTS(SELECT 1 AS \"1\" FROM (SELECT X.ID AS \"ID\", X.V AS "
                + "\"V\", X.S AS \"S\" FROM G AS X INNER JOIN G AS Y ON ((X.ID = Y.ID) AND (X.V = Y.V) AND (X.S = "
                + "Y.S))) SYS_VW)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT 1 FROM g x NATURAL JOIN g y), id");
        assertAnswer("REFUSED SQL compilation error: | [EXISTS(SELECT 1 AS \"1\" FROM (SELECT X.ID AS \"ID\", X.V AS "
                + "\"V\", X.S AS \"S\", Y.V AS \"V_0\", Y.S AS \"S_0\" FROM G AS X INNER JOIN G AS Y ON (X.ID = "
                + "Y.ID)) SYS_VW)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT 1 FROM g x JOIN g y USING (id)), id");
        assertAnswer("REFUSED SQL compilation error: | [EXISTS(SELECT 1 AS \"1\" FROM G AS G GROUP BY 1)] is not a "
                + "valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT 1 FROM g GROUP BY ALL), id");
        assertAnswer("REFUSED SQL compilation error: | [EXISTS(SELECT G.V AS \"V\", COUNT(*) AS \"C\" FROM G AS G "
                + "GROUP BY GROUPING SETS ((), (G.V)))] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT g.v, COUNT(*) c FROM g GROUP BY ROLLUP (g.v)), id");
    }
}
