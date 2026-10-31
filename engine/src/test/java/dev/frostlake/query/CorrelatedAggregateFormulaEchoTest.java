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
 * A misplaced correlated aggregate: REGR_COUNT and REGR_AVGX over a FLOAT echoed as the formulas the plan computes
 * them with, a predicate compared as a value cast to BOOLEAN, and the type refusals a percentile or an average
 * raises before the place is judged (all live-verified).
 */
public class CorrelatedAggregateFormulaEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void formulasCastsAndTypeRefusals() {
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
        assertAnswer("REFUSED SQL compilation error: | incompatible types: [DATE] and [NUMBER(9,0)]",
            "SELECT id FROM fz WHERE EXISTS (SELECT MEDIAN(fz.dt) FROM g)");
        assertAnswer("REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(41,40).",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(DISTINCT fz.n3837) FROM g)");
        assertAnswer("REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(41,40).",
            "SELECT id FROM fz WHERE EXISTS (SELECT MEDIAN(fz.n3837) FROM g)");
        assertAnswer("REFUSED SQL compilation error: | incompatible types: [TIMESTAMP_NTZ(9)] and [NUMBER(9,0)]",
            "SELECT id FROM fz WHERE EXISTS (SELECT MEDIAN(fz.ts) FROM g)");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function PERCENTILE_CONT needs to be constant, "
                + "found 'FZ.F'",
            "SELECT id FROM fz WHERE EXISTS (SELECT PERCENTILE_CONT(fz.f) WITHIN GROUP (ORDER BY fz.id) FROM g)");
        assertAnswer("REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[BITXOR_AGG(TO_NUMBER(FZ.S, 18, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT BITXOR_AGG(fz.s) FROM g)");
        assertAnswer("REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF((CAST(FZ.ID > 5 AS BOOLEAN)) = TRUE, 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.id > 5 = TRUE) FROM g)");
    }
}
