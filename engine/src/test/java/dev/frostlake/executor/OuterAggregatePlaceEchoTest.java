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

package dev.frostlake.executor;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The echo of an outer aggregate refused in a subquery's place: the regressions' planned formulas over exact and
 * FLOAT values, COUNT_IF's condition with a doubled NOT kept, the type refusals that come first, and a grouped
 * ORDER BY key comparing a ROW with a query named by the ROW (all live-verified).
 */
public class OuterAggregatePlaceEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void aggregateEchoes() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR, f FLOAT, d NUMBER(10,2), dt DATE, "
            + "va VARIANT, n5 NUMBER(5,0), n38 NUMBER(38,10), ts TIMESTAMP_NTZ, n20 NUMBER(20,0), n21 NUMBER(21,"
            + "0), n109 NUMBER(10,9), n3837 NUMBER(38,37), n242 NUMBER(24,2), n232 NUMBER(23,2))");
        engine.execute("INSERT INTO fz SELECT 5, TRUE, 'a', 1.5, 2.25, '2020-01-01'::DATE, PARSE_JSON('1'), 5, 1.5, "
            + "'2020-01-01'::TIMESTAMP_NTZ, 5, 5, 1.5, 1.5, 1.5, 1.5");
        engine.execute("INSERT INTO fz SELECT 7, FALSE, 'b', 2.5, 3.25, '2020-01-02'::DATE, PARSE_JSON('2'), 7, 2.5, "
            + "'2020-01-02'::TIMESTAMP_NTZ, 7, 7, 2.5, 2.5, 2.5, 2.5");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT, s VARCHAR)");
        engine.execute("INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
        assertAnswer("REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[COUNT(IFF(FZ.ID IS NULL, SYSTEM$NULL_TO_REAL(null), FZ.F))] can only appear in having or select "
                + "clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT REGR_COUNT(fz.id, fz.f) FROM g)");
        assertAnswer("REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[(SUM(IFF(FZ.ID IS NULL, SYSTEM$NULL_TO_REAL(null), FZ.F))) / (CAST(COUNT(IFF(FZ.ID IS NULL, "
                + "SYSTEM$NULL_TO_REAL(null), FZ.F)) AS FLOAT))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT REGR_AVGX(fz.id, fz.f) FROM g)");
        assertAnswer("REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[(SUM(CAST(IFF(FZ.F IS NULL, SYSTEM$NULL_TO_FIXED(null), FZ.ID) AS FLOAT))) / "
                + "(CAST(COUNT(CAST(IFF(FZ.F IS NULL, SYSTEM$NULL_TO_FIXED(null), FZ.ID) AS FLOAT)) AS FLOAT))] can "
                + "only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT REGR_AVGY(fz.id, fz.f) FROM g)");
        assertAnswer("REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[(SUM(SQUARE(IFF(FZ.ID IS NULL, SYSTEM$NULL_TO_REAL(null), FZ.F)))) - ((SQUARE(SUM(IFF(FZ.ID IS "
                + "NULL, SYSTEM$NULL_TO_REAL(null), FZ.F)))) / (CAST(COUNT(IFF(FZ.ID IS NULL, "
                + "SYSTEM$NULL_TO_REAL(null), FZ.F)) AS FLOAT)))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT REGR_SXX(fz.id, fz.f) FROM g)");
        assertAnswer("REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[COUNT(CAST(IFF(FZ.F IS NULL, SYSTEM$NULL_TO_FIXED(null), FZ.ID) AS FLOAT))] can only appear in "
                + "having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT REGR_COUNT(fz.f, fz.id) FROM g)");
        assertAnswer("REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[(SUM(CAST(IFF(FZ.ID IS NULL, SYSTEM$NULL_TO_FIXED(null), FZ.D) AS FLOAT))) / "
                + "(CAST(COUNT(CAST(IFF(FZ.ID IS NULL, SYSTEM$NULL_TO_FIXED(null), FZ.D) AS FLOAT)) AS FLOAT))] can "
                + "only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT REGR_AVGX(fz.id, fz.d) FROM g)");
        assertAnswer("REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[(SUM(CAST(IFF(FZ.ID IS NULL, SYSTEM$NULL_TO_FIXED(null), FZ.D) AS FLOAT))) / "
                + "(CAST(COUNT(CAST(IFF(FZ.ID IS NULL, SYSTEM$NULL_TO_FIXED(null), FZ.D) AS FLOAT)) AS FLOAT))] can "
                + "only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT REGR_AVGY(fz.d, fz.id) FROM g)");
        assertAnswer("REFUSED SQL compilation error: | incompatible types: [DATE] and [NUMBER(9,0)]",
            "SELECT id FROM fz WHERE EXISTS (SELECT MEDIAN(fz.dt) FROM g)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 39 | Invalid argument types for "
                + "function 'VECTOR_AVG': (VARIANT)",
            "SELECT id FROM fz WHERE EXISTS (SELECT VECTOR_AVG(fz.va) FROM g)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 48 | Invalid argument types for "
                + "function 'BOOLAND': (BOOLEAN, BOOLEAN)",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(BOOLAND(fz.b, fz.b)) FROM g)");
        assertAnswer("REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(41,40).",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(DISTINCT fz.n3837) FROM g)");
        assertAnswer("REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(41,40).",
            "SELECT id FROM fz WHERE EXISTS (SELECT MEDIAN(fz.n3837) FROM g)");
        assertAnswer("REFUSED SQL compilation error: | incompatible types: [TIMESTAMP_NTZ(9)] and [NUMBER(9,0)]",
            "SELECT id FROM fz WHERE EXISTS (SELECT MEDIAN(fz.ts) FROM g)");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function PERCENTILE_CONT needs to be constant, "
                + "found 'FZ.F'",
            "SELECT id FROM fz WHERE EXISTS (SELECT PERCENTILE_CONT(fz.f) WITHIN GROUP (ORDER BY fz.id) FROM g)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 39 | Invalid argument types for "
                + "function 'BITAND_AGG': (BOOLEAN)",
            "SELECT id FROM fz WHERE EXISTS (SELECT BITAND_AGG(fz.b) FROM g)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 39 | Invalid argument types for "
                + "function 'BITAND_AGG': (DATE)",
            "SELECT id FROM fz WHERE EXISTS (SELECT BITAND_AGG(fz.dt) FROM g)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 39 | Invalid argument types for "
                + "function 'BITOR_AGG': (BOOLEAN)",
            "SELECT id FROM fz WHERE EXISTS (SELECT BITOR_AGG(fz.b) FROM g)");
        assertAnswer("REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[BITXOR_AGG(TO_NUMBER(FZ.S, 18, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT BITXOR_AGG(fz.s) FROM g)");
        assertAnswer("REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF(CAST(NOT(NOT(FZ.B)) AS BOOLEAN), 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(NOT (NOT fz.b)) FROM g)");
        assertAnswer("REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF((CAST(FZ.ID > 5 AS BOOLEAN)) = TRUE, 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.id > 5 = TRUE) FROM g)");
        assertAnswer("REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF(CAST(NOT(NOT(FZ.B)) AS BOOLEAN), 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(NOT NOT fz.b) FROM g)");
        assertAnswer("REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF(FZ.B = (CAST(FZ.ID > 5 AS BOOLEAN)), 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.b = (fz.id > 5)) FROM g)");
        assertAnswer("REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF((CAST(FZ.ID > 5 AS BOOLEAN)) <> FALSE, 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF((fz.id > 5) <> FALSE) FROM g)");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }

    @Test
    public void groupedOrderKeys() {
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
        assertAnswer("REFUSED SQL compilation error: | [ROW(ID, ID)] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY (id, id) = (SELECT id, v FROM g LIMIT 1), id");
        assertAnswer("REFUSED SQL compilation error: | [EXISTS(SELECT G.V AS \"V\" FROM G AS G GROUP BY GROUPING SETS "
                + "((), (G.V)))] is not a valid order by expression",
            "SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT g.v FROM g GROUP BY CUBE (g.v)), id");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
