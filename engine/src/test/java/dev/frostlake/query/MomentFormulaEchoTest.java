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
 * The variance family and the moments as the echo of a misplaced correlated aggregate prints them: the formula the plan
 * writes over the value's COUNT and the SUMs of its powers, typed as the plan types arithmetic, so it differs for a
 * whole number, a scaled NUMBER, a FLOAT and a text or a VARIANT read as the FLOAT it converts to (live-verified). Over
 * distinct values every COUNT and SUM of the formula reads DISTINCT, KURTOSIS and SKEW are echoed as written, VARIANCE
 * alone refuses DISTINCT and the regressions refuse it outright.
 */
public class MomentFormulaEchoTest extends MeasuredCellsTestSupport {

    /** Ten aggregates over an INT, a NUMBER(10,2), a FLOAT and a VARCHAR, and over a NUMBER(12,4) and a VARIANT. */
    @Test
    public void theVarianceFamilyAndTheMomentsArePrintedAsFormulas() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR, f FLOAT, d NUMBER(10,2), dt DATE, va "
            + "VARIANT, e NUMBER(12,4))");
        engine.execute("INSERT INTO fz SELECT 5, TRUE, 'a', 1.5, 2.25, '2020-01-01'::DATE, PARSE_JSON('1'), 1.125");
        engine.execute("INSERT INTO fz SELECT 7, FALSE, 'b', 2.5, 3.25, '2020-01-02'::DATE, PARSE_JSON('2'), 2.5");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT, s VARCHAR)");
        engine.execute("INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
        assertCells(new String[][] {
            {"SELECT id FROM fz WHERE EXISTS (SELECT STDDEV(fz.id) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SQRT(CAST(SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.ID)) * (SUM(FZ.ID * FZ.ID))) - ((SUM(FZ.ID)) * "
                + "(SUM(FZ.ID)))) < 0, 0, ((COUNT(FZ.ID)) * (SUM(FZ.ID * FZ.ID))) - ((SUM(FZ.ID)) * (SUM(FZ.ID)))), "
                + "NULLIF((COUNT(FZ.ID)) * ((COUNT(FZ.ID)) - 1), 0)) AS FLOAT))] can only appear in having or select "
                + "clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT STDDEV(fz.d) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SQRT(CAST(SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.D)) * (SUM(FZ.D * FZ.D))) - ((SUM(FZ.D)) * "
                + "(SUM(FZ.D)))) < (CAST(0 AS NUMBER(38,4))), CAST(0 AS NUMBER(38,4)), ((COUNT(FZ.D)) * (SUM(FZ.D * "
                + "FZ.D))) - ((SUM(FZ.D)) * (SUM(FZ.D)))), NULLIF((COUNT(FZ.D)) * ((COUNT(FZ.D)) - 1), 0)) AS FLOAT))] "
                + "can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT STDDEV(fz.f) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SQRT((IFF((((CAST(COUNT(FZ.F) AS FLOAT)) * (SUM(FZ.F * FZ.F))) - ((SUM(FZ.F)) * (SUM(FZ.F)))) < "
                + "(CAST(0 AS FLOAT)), CAST(0 AS FLOAT), ((CAST(COUNT(FZ.F) AS FLOAT)) * (SUM(FZ.F * FZ.F))) - "
                + "((SUM(FZ.F)) * (SUM(FZ.F))))) / (CAST(NULLIF((COUNT(FZ.F)) * ((COUNT(FZ.F)) - 1), 0) AS FLOAT)))] "
                + "can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT STDDEV(fz.s) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SQRT((IFF((((CAST(COUNT(FZ.S) AS FLOAT)) * (SUM((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S AS FLOAT))))) - "
                + "((SUM(CAST(FZ.S AS FLOAT))) * (SUM(CAST(FZ.S AS FLOAT))))) < (CAST(0 AS FLOAT)), CAST(0 AS FLOAT), "
                + "((CAST(COUNT(FZ.S) AS FLOAT)) * (SUM((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S AS FLOAT))))) - "
                + "((SUM(CAST(FZ.S AS FLOAT))) * (SUM(CAST(FZ.S AS FLOAT)))))) / (CAST(NULLIF((COUNT(FZ.S)) * "
                + "((COUNT(FZ.S)) - 1), 0) AS FLOAT)))] can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT STDDEV_SAMP(fz.id) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SQRT(CAST(SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.ID)) * (SUM(FZ.ID * FZ.ID))) - ((SUM(FZ.ID)) * "
                + "(SUM(FZ.ID)))) < 0, 0, ((COUNT(FZ.ID)) * (SUM(FZ.ID * FZ.ID))) - ((SUM(FZ.ID)) * (SUM(FZ.ID)))), "
                + "NULLIF((COUNT(FZ.ID)) * ((COUNT(FZ.ID)) - 1), 0)) AS FLOAT))] can only appear in having or select "
                + "clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT STDDEV_SAMP(fz.d) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SQRT(CAST(SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.D)) * (SUM(FZ.D * FZ.D))) - ((SUM(FZ.D)) * "
                + "(SUM(FZ.D)))) < (CAST(0 AS NUMBER(38,4))), CAST(0 AS NUMBER(38,4)), ((COUNT(FZ.D)) * (SUM(FZ.D * "
                + "FZ.D))) - ((SUM(FZ.D)) * (SUM(FZ.D)))), NULLIF((COUNT(FZ.D)) * ((COUNT(FZ.D)) - 1), 0)) AS FLOAT))] "
                + "can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT STDDEV_SAMP(fz.f) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SQRT((IFF((((CAST(COUNT(FZ.F) AS FLOAT)) * (SUM(FZ.F * FZ.F))) - ((SUM(FZ.F)) * (SUM(FZ.F)))) < "
                + "(CAST(0 AS FLOAT)), CAST(0 AS FLOAT), ((CAST(COUNT(FZ.F) AS FLOAT)) * (SUM(FZ.F * FZ.F))) - "
                + "((SUM(FZ.F)) * (SUM(FZ.F))))) / (CAST(NULLIF((COUNT(FZ.F)) * ((COUNT(FZ.F)) - 1), 0) AS FLOAT)))] "
                + "can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT STDDEV_SAMP(fz.s) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SQRT((IFF((((CAST(COUNT(FZ.S) AS FLOAT)) * (SUM((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S AS FLOAT))))) - "
                + "((SUM(CAST(FZ.S AS FLOAT))) * (SUM(CAST(FZ.S AS FLOAT))))) < (CAST(0 AS FLOAT)), CAST(0 AS FLOAT), "
                + "((CAST(COUNT(FZ.S) AS FLOAT)) * (SUM((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S AS FLOAT))))) - "
                + "((SUM(CAST(FZ.S AS FLOAT))) * (SUM(CAST(FZ.S AS FLOAT)))))) / (CAST(NULLIF((COUNT(FZ.S)) * "
                + "((COUNT(FZ.S)) - 1), 0) AS FLOAT)))] can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT STDDEV_POP(fz.id) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SQRT(CAST(SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.ID)) * (SUM(FZ.ID * FZ.ID))) - ((SUM(FZ.ID)) * "
                + "(SUM(FZ.ID)))) < 0, 0, ((COUNT(FZ.ID)) * (SUM(FZ.ID * FZ.ID))) - ((SUM(FZ.ID)) * (SUM(FZ.ID)))), "
                + "NULLIF((COUNT(FZ.ID)) * (COUNT(FZ.ID)), 0)) AS FLOAT))] can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT STDDEV_POP(fz.d) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SQRT(CAST(SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.D)) * (SUM(FZ.D * FZ.D))) - ((SUM(FZ.D)) * "
                + "(SUM(FZ.D)))) < (CAST(0 AS NUMBER(38,4))), CAST(0 AS NUMBER(38,4)), ((COUNT(FZ.D)) * (SUM(FZ.D * "
                + "FZ.D))) - ((SUM(FZ.D)) * (SUM(FZ.D)))), NULLIF((COUNT(FZ.D)) * (COUNT(FZ.D)), 0)) AS FLOAT))] can "
                + "only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT STDDEV_POP(fz.f) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SQRT((IFF((((CAST(COUNT(FZ.F) AS FLOAT)) * (SUM(FZ.F * FZ.F))) - ((SUM(FZ.F)) * (SUM(FZ.F)))) < "
                + "(CAST(0 AS FLOAT)), CAST(0 AS FLOAT), ((CAST(COUNT(FZ.F) AS FLOAT)) * (SUM(FZ.F * FZ.F))) - "
                + "((SUM(FZ.F)) * (SUM(FZ.F))))) / (CAST(NULLIF((COUNT(FZ.F)) * (COUNT(FZ.F)), 0) AS FLOAT)))] can "
                + "only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT STDDEV_POP(fz.s) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SQRT((IFF((((CAST(COUNT(FZ.S) AS FLOAT)) * (SUM((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S AS FLOAT))))) - "
                + "((SUM(CAST(FZ.S AS FLOAT))) * (SUM(CAST(FZ.S AS FLOAT))))) < (CAST(0 AS FLOAT)), CAST(0 AS FLOAT), "
                + "((CAST(COUNT(FZ.S) AS FLOAT)) * (SUM((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S AS FLOAT))))) - "
                + "((SUM(CAST(FZ.S AS FLOAT))) * (SUM(CAST(FZ.S AS FLOAT)))))) / (CAST(NULLIF((COUNT(FZ.S)) * "
                + "(COUNT(FZ.S)), 0) AS FLOAT)))] can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT VARIANCE(fz.id) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.ID)) * (SUM(FZ.ID * FZ.ID))) - ((SUM(FZ.ID)) * "
                + "(SUM(FZ.ID)))) < 0, 0, ((COUNT(FZ.ID)) * (SUM(FZ.ID * FZ.ID))) - ((SUM(FZ.ID)) * (SUM(FZ.ID)))), "
                + "NULLIF((COUNT(FZ.ID)) * ((COUNT(FZ.ID)) - 1), 0))] can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT VARIANCE(fz.d) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.D)) * (SUM(FZ.D * FZ.D))) - ((SUM(FZ.D)) * (SUM(FZ.D)))) < "
                + "(CAST(0 AS NUMBER(38,4))), CAST(0 AS NUMBER(38,4)), ((COUNT(FZ.D)) * (SUM(FZ.D * FZ.D))) - "
                + "((SUM(FZ.D)) * (SUM(FZ.D)))), NULLIF((COUNT(FZ.D)) * ((COUNT(FZ.D)) - 1), 0))] can only appear in "
                + "having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT VARIANCE(fz.f) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[(IFF((((CAST(COUNT(FZ.F) AS FLOAT)) * (SUM(FZ.F * FZ.F))) - ((SUM(FZ.F)) * (SUM(FZ.F)))) < (CAST(0 "
                + "AS FLOAT)), CAST(0 AS FLOAT), ((CAST(COUNT(FZ.F) AS FLOAT)) * (SUM(FZ.F * FZ.F))) - ((SUM(FZ.F)) * "
                + "(SUM(FZ.F))))) / (CAST(NULLIF((COUNT(FZ.F)) * ((COUNT(FZ.F)) - 1), 0) AS FLOAT))] can only appear "
                + "in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT VARIANCE(fz.s) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[(IFF((((CAST(COUNT(FZ.S) AS FLOAT)) * (SUM((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S AS FLOAT))))) - "
                + "((SUM(CAST(FZ.S AS FLOAT))) * (SUM(CAST(FZ.S AS FLOAT))))) < (CAST(0 AS FLOAT)), CAST(0 AS FLOAT), "
                + "((CAST(COUNT(FZ.S) AS FLOAT)) * (SUM((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S AS FLOAT))))) - "
                + "((SUM(CAST(FZ.S AS FLOAT))) * (SUM(CAST(FZ.S AS FLOAT)))))) / (CAST(NULLIF((COUNT(FZ.S)) * "
                + "((COUNT(FZ.S)) - 1), 0) AS FLOAT))] can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT VARIANCE_SAMP(fz.id) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.ID)) * (SUM(FZ.ID * FZ.ID))) - ((SUM(FZ.ID)) * "
                + "(SUM(FZ.ID)))) < 0, 0, ((COUNT(FZ.ID)) * (SUM(FZ.ID * FZ.ID))) - ((SUM(FZ.ID)) * (SUM(FZ.ID)))), "
                + "NULLIF((COUNT(FZ.ID)) * ((COUNT(FZ.ID)) - 1), 0))] can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT VARIANCE_SAMP(fz.d) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.D)) * (SUM(FZ.D * FZ.D))) - ((SUM(FZ.D)) * (SUM(FZ.D)))) < "
                + "(CAST(0 AS NUMBER(38,4))), CAST(0 AS NUMBER(38,4)), ((COUNT(FZ.D)) * (SUM(FZ.D * FZ.D))) - "
                + "((SUM(FZ.D)) * (SUM(FZ.D)))), NULLIF((COUNT(FZ.D)) * ((COUNT(FZ.D)) - 1), 0))] can only appear in "
                + "having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT VARIANCE_SAMP(fz.f) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[(IFF((((CAST(COUNT(FZ.F) AS FLOAT)) * (SUM(FZ.F * FZ.F))) - ((SUM(FZ.F)) * (SUM(FZ.F)))) < (CAST(0 "
                + "AS FLOAT)), CAST(0 AS FLOAT), ((CAST(COUNT(FZ.F) AS FLOAT)) * (SUM(FZ.F * FZ.F))) - ((SUM(FZ.F)) * "
                + "(SUM(FZ.F))))) / (CAST(NULLIF((COUNT(FZ.F)) * ((COUNT(FZ.F)) - 1), 0) AS FLOAT))] can only appear "
                + "in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT VARIANCE_SAMP(fz.s) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[(IFF((((CAST(COUNT(FZ.S) AS FLOAT)) * (SUM((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S AS FLOAT))))) - "
                + "((SUM(CAST(FZ.S AS FLOAT))) * (SUM(CAST(FZ.S AS FLOAT))))) < (CAST(0 AS FLOAT)), CAST(0 AS FLOAT), "
                + "((CAST(COUNT(FZ.S) AS FLOAT)) * (SUM((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S AS FLOAT))))) - "
                + "((SUM(CAST(FZ.S AS FLOAT))) * (SUM(CAST(FZ.S AS FLOAT)))))) / (CAST(NULLIF((COUNT(FZ.S)) * "
                + "((COUNT(FZ.S)) - 1), 0) AS FLOAT))] can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT VARIANCE_POP(fz.id) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.ID)) * (SUM(FZ.ID * FZ.ID))) - ((SUM(FZ.ID)) * "
                + "(SUM(FZ.ID)))) < 0, 0, ((COUNT(FZ.ID)) * (SUM(FZ.ID * FZ.ID))) - ((SUM(FZ.ID)) * (SUM(FZ.ID)))), "
                + "NULLIF((COUNT(FZ.ID)) * (COUNT(FZ.ID)), 0))] can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT VARIANCE_POP(fz.d) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.D)) * (SUM(FZ.D * FZ.D))) - ((SUM(FZ.D)) * (SUM(FZ.D)))) < "
                + "(CAST(0 AS NUMBER(38,4))), CAST(0 AS NUMBER(38,4)), ((COUNT(FZ.D)) * (SUM(FZ.D * FZ.D))) - "
                + "((SUM(FZ.D)) * (SUM(FZ.D)))), NULLIF((COUNT(FZ.D)) * (COUNT(FZ.D)), 0))] can only appear in having "
                + "or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT VARIANCE_POP(fz.f) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[(IFF((((CAST(COUNT(FZ.F) AS FLOAT)) * (SUM(FZ.F * FZ.F))) - ((SUM(FZ.F)) * (SUM(FZ.F)))) < (CAST(0 "
                + "AS FLOAT)), CAST(0 AS FLOAT), ((CAST(COUNT(FZ.F) AS FLOAT)) * (SUM(FZ.F * FZ.F))) - ((SUM(FZ.F)) * "
                + "(SUM(FZ.F))))) / (CAST(NULLIF((COUNT(FZ.F)) * (COUNT(FZ.F)), 0) AS FLOAT))] can only appear in "
                + "having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT VARIANCE_POP(fz.s) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[(IFF((((CAST(COUNT(FZ.S) AS FLOAT)) * (SUM((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S AS FLOAT))))) - "
                + "((SUM(CAST(FZ.S AS FLOAT))) * (SUM(CAST(FZ.S AS FLOAT))))) < (CAST(0 AS FLOAT)), CAST(0 AS FLOAT), "
                + "((CAST(COUNT(FZ.S) AS FLOAT)) * (SUM((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S AS FLOAT))))) - "
                + "((SUM(CAST(FZ.S AS FLOAT))) * (SUM(CAST(FZ.S AS FLOAT)))))) / (CAST(NULLIF((COUNT(FZ.S)) * "
                + "(COUNT(FZ.S)), 0) AS FLOAT))] can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT VAR_SAMP(fz.id) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.ID)) * (SUM(FZ.ID * FZ.ID))) - ((SUM(FZ.ID)) * "
                + "(SUM(FZ.ID)))) < 0, 0, ((COUNT(FZ.ID)) * (SUM(FZ.ID * FZ.ID))) - ((SUM(FZ.ID)) * (SUM(FZ.ID)))), "
                + "NULLIF((COUNT(FZ.ID)) * ((COUNT(FZ.ID)) - 1), 0))] can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT VAR_SAMP(fz.d) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.D)) * (SUM(FZ.D * FZ.D))) - ((SUM(FZ.D)) * (SUM(FZ.D)))) < "
                + "(CAST(0 AS NUMBER(38,4))), CAST(0 AS NUMBER(38,4)), ((COUNT(FZ.D)) * (SUM(FZ.D * FZ.D))) - "
                + "((SUM(FZ.D)) * (SUM(FZ.D)))), NULLIF((COUNT(FZ.D)) * ((COUNT(FZ.D)) - 1), 0))] can only appear in "
                + "having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT VAR_SAMP(fz.f) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[(IFF((((CAST(COUNT(FZ.F) AS FLOAT)) * (SUM(FZ.F * FZ.F))) - ((SUM(FZ.F)) * (SUM(FZ.F)))) < (CAST(0 "
                + "AS FLOAT)), CAST(0 AS FLOAT), ((CAST(COUNT(FZ.F) AS FLOAT)) * (SUM(FZ.F * FZ.F))) - ((SUM(FZ.F)) * "
                + "(SUM(FZ.F))))) / (CAST(NULLIF((COUNT(FZ.F)) * ((COUNT(FZ.F)) - 1), 0) AS FLOAT))] can only appear "
                + "in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT VAR_SAMP(fz.s) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[(IFF((((CAST(COUNT(FZ.S) AS FLOAT)) * (SUM((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S AS FLOAT))))) - "
                + "((SUM(CAST(FZ.S AS FLOAT))) * (SUM(CAST(FZ.S AS FLOAT))))) < (CAST(0 AS FLOAT)), CAST(0 AS FLOAT), "
                + "((CAST(COUNT(FZ.S) AS FLOAT)) * (SUM((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S AS FLOAT))))) - "
                + "((SUM(CAST(FZ.S AS FLOAT))) * (SUM(CAST(FZ.S AS FLOAT)))))) / (CAST(NULLIF((COUNT(FZ.S)) * "
                + "((COUNT(FZ.S)) - 1), 0) AS FLOAT))] can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT VAR_POP(fz.id) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.ID)) * (SUM(FZ.ID * FZ.ID))) - ((SUM(FZ.ID)) * "
                + "(SUM(FZ.ID)))) < 0, 0, ((COUNT(FZ.ID)) * (SUM(FZ.ID * FZ.ID))) - ((SUM(FZ.ID)) * (SUM(FZ.ID)))), "
                + "NULLIF((COUNT(FZ.ID)) * (COUNT(FZ.ID)), 0))] can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT VAR_POP(fz.d) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.D)) * (SUM(FZ.D * FZ.D))) - ((SUM(FZ.D)) * (SUM(FZ.D)))) < "
                + "(CAST(0 AS NUMBER(38,4))), CAST(0 AS NUMBER(38,4)), ((COUNT(FZ.D)) * (SUM(FZ.D * FZ.D))) - "
                + "((SUM(FZ.D)) * (SUM(FZ.D)))), NULLIF((COUNT(FZ.D)) * (COUNT(FZ.D)), 0))] can only appear in having "
                + "or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT VAR_POP(fz.f) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[(IFF((((CAST(COUNT(FZ.F) AS FLOAT)) * (SUM(FZ.F * FZ.F))) - ((SUM(FZ.F)) * (SUM(FZ.F)))) < (CAST(0 "
                + "AS FLOAT)), CAST(0 AS FLOAT), ((CAST(COUNT(FZ.F) AS FLOAT)) * (SUM(FZ.F * FZ.F))) - ((SUM(FZ.F)) * "
                + "(SUM(FZ.F))))) / (CAST(NULLIF((COUNT(FZ.F)) * (COUNT(FZ.F)), 0) AS FLOAT))] can only appear in "
                + "having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT VAR_POP(fz.s) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[(IFF((((CAST(COUNT(FZ.S) AS FLOAT)) * (SUM((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S AS FLOAT))))) - "
                + "((SUM(CAST(FZ.S AS FLOAT))) * (SUM(CAST(FZ.S AS FLOAT))))) < (CAST(0 AS FLOAT)), CAST(0 AS FLOAT), "
                + "((CAST(COUNT(FZ.S) AS FLOAT)) * (SUM((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S AS FLOAT))))) - "
                + "((SUM(CAST(FZ.S AS FLOAT))) * (SUM(CAST(FZ.S AS FLOAT)))))) / (CAST(NULLIF((COUNT(FZ.S)) * "
                + "(COUNT(FZ.S)), 0) AS FLOAT))] can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT KURTOSIS(fz.id) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[IFF(((((COUNT(FZ.ID)) * ((COUNT(FZ.ID)) - 1)) * ((COUNT(FZ.ID)) - 2)) * ((COUNT(FZ.ID)) - 3)) = 0, "
                + "SYSTEM$NULL_TO_FIXED(null), "
                + "(SCALED_ROUND_INT_DIVIDE(SCALED_DECIMAL_MULTIPLY(SCALED_ROUND_INT_DIVIDE(CAST(((COUNT(FZ.ID)) + 1) "
                + "* (COUNT(FZ.ID)) AS NUMBER(38,10)), (((COUNT(FZ.ID)) - 1) * ((COUNT(FZ.ID)) - 2)) * ((COUNT(FZ.ID)) "
                + "- 3)), (((CAST((CAST(SUM(((FZ.ID * FZ.ID) * FZ.ID) * FZ.ID) AS NUMBER(38,6))) - ((4 * "
                + "(SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), COUNT(FZ.ID)))) * (SUM((FZ.ID * FZ.ID) * FZ.ID))) AS "
                + "NUMBER(38,12))) + (((6 * (SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), COUNT(FZ.ID)))) * "
                + "(SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), COUNT(FZ.ID)))) * (SUM(FZ.ID * FZ.ID)))) - "
                + "((SCALED_DECIMAL_MULTIPLY((4 * (SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), COUNT(FZ.ID)))) * "
                + "(SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), COUNT(FZ.ID))), SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), "
                + "COUNT(FZ.ID)), 6)) * (SUM(FZ.ID)))) + "
                + "(SCALED_DECIMAL_MULTIPLY(SCALED_DECIMAL_MULTIPLY(((COUNT(FZ.ID)) * "
                + "(SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), COUNT(FZ.ID)))) * (SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), "
                + "COUNT(FZ.ID))), SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), COUNT(FZ.ID)), 6), "
                + "SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), COUNT(FZ.ID)), 6)), 12), "
                + "(SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.ID)) * (SUM(FZ.ID * FZ.ID))) - ((SUM(FZ.ID)) * "
                + "(SUM(FZ.ID)))) < 0, 0, ((COUNT(FZ.ID)) * (SUM(FZ.ID * FZ.ID))) - ((SUM(FZ.ID)) * (SUM(FZ.ID)))), "
                + "NULLIF((COUNT(FZ.ID)) * ((COUNT(FZ.ID)) - 1), 0))) * (SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.ID)) "
                + "* (SUM(FZ.ID * FZ.ID))) - ((SUM(FZ.ID)) * (SUM(FZ.ID)))) < 0, 0, ((COUNT(FZ.ID)) * (SUM(FZ.ID * "
                + "FZ.ID))) - ((SUM(FZ.ID)) * (SUM(FZ.ID)))), NULLIF((COUNT(FZ.ID)) * ((COUNT(FZ.ID)) - 1), 0))))) - "
                + "(CAST(SCALED_ROUND_INT_DIVIDE((3 * ((COUNT(FZ.ID)) - 1)) * ((COUNT(FZ.ID)) - 1), ((COUNT(FZ.ID)) - "
                + "2) * ((COUNT(FZ.ID)) - 3)) AS NUMBER(38,12))))] can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT KURTOSIS(fz.d) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[IFF(((((COUNT(FZ.D)) * ((COUNT(FZ.D)) - 1)) * ((COUNT(FZ.D)) - 2)) * ((COUNT(FZ.D)) - 3)) = 0, "
                + "SYSTEM$NULL_TO_FIXED(null), "
                + "(SCALED_ROUND_INT_DIVIDE(SCALED_DECIMAL_MULTIPLY(SCALED_ROUND_INT_DIVIDE(CAST(((COUNT(FZ.D)) + 1) * "
                + "(COUNT(FZ.D)) AS NUMBER(38,10)), (((COUNT(FZ.D)) - 1) * ((COUNT(FZ.D)) - 2)) * ((COUNT(FZ.D)) - "
                + "3)), ((((CAST(SUM(((FZ.D * FZ.D) * FZ.D) * FZ.D) AS NUMBER(38,12))) - (SCALED_DECIMAL_MULTIPLY(4 * "
                + "((CAST(SUM(FZ.D) AS NUMBER(28,8))) / (COUNT(FZ.D))), SUM((FZ.D * FZ.D) * FZ.D), 2))) + "
                + "(SCALED_DECIMAL_MULTIPLY(SCALED_DECIMAL_MULTIPLY(6 * ((CAST(SUM(FZ.D) AS NUMBER(28,8))) / "
                + "(COUNT(FZ.D))), (CAST(SUM(FZ.D) AS NUMBER(28,8))) / (COUNT(FZ.D)), 4), SUM(FZ.D * FZ.D), 4))) - "
                + "(SCALED_DECIMAL_MULTIPLY(SCALED_DECIMAL_MULTIPLY(SCALED_DECIMAL_MULTIPLY(4 * ((CAST(SUM(FZ.D) AS "
                + "NUMBER(28,8))) / (COUNT(FZ.D))), (CAST(SUM(FZ.D) AS NUMBER(28,8))) / (COUNT(FZ.D)), 4), "
                + "(CAST(SUM(FZ.D) AS NUMBER(28,8))) / (COUNT(FZ.D)), 8), SUM(FZ.D), 2))) + "
                + "(SCALED_DECIMAL_MULTIPLY(SCALED_DECIMAL_MULTIPLY(SCALED_DECIMAL_MULTIPLY((COUNT(FZ.D)) * "
                + "((CAST(SUM(FZ.D) AS NUMBER(28,8))) / (COUNT(FZ.D))), (CAST(SUM(FZ.D) AS NUMBER(28,8))) / "
                + "(COUNT(FZ.D)), 4), (CAST(SUM(FZ.D) AS NUMBER(28,8))) / (COUNT(FZ.D)), 8), (CAST(SUM(FZ.D) AS "
                + "NUMBER(28,8))) / (COUNT(FZ.D)), 8)), 12), "
                + "SCALED_DECIMAL_MULTIPLY(SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.D)) * (SUM(FZ.D * FZ.D))) - "
                + "((SUM(FZ.D)) * (SUM(FZ.D)))) < (CAST(0 AS NUMBER(38,4))), CAST(0 AS NUMBER(38,4)), ((COUNT(FZ.D)) * "
                + "(SUM(FZ.D * FZ.D))) - ((SUM(FZ.D)) * (SUM(FZ.D)))), NULLIF((COUNT(FZ.D)) * ((COUNT(FZ.D)) - 1), "
                + "0)), SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.D)) * (SUM(FZ.D * FZ.D))) - ((SUM(FZ.D)) * "
                + "(SUM(FZ.D)))) < (CAST(0 AS NUMBER(38,4))), CAST(0 AS NUMBER(38,4)), ((COUNT(FZ.D)) * (SUM(FZ.D * "
                + "FZ.D))) - ((SUM(FZ.D)) * (SUM(FZ.D)))), NULLIF((COUNT(FZ.D)) * ((COUNT(FZ.D)) - 1), 0)), 8))) - "
                + "(CAST(SCALED_ROUND_INT_DIVIDE((3 * ((COUNT(FZ.D)) - 1)) * ((COUNT(FZ.D)) - 1), ((COUNT(FZ.D)) - 2) "
                + "* ((COUNT(FZ.D)) - 3)) AS NUMBER(38,12))))] can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT KURTOSIS(fz.f) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[IFF(((((COUNT(FZ.F)) * ((COUNT(FZ.F)) - 1)) * ((COUNT(FZ.F)) - 2)) * ((COUNT(FZ.F)) - 3)) = 0, "
                + "SYSTEM$NULL_TO_REAL(null), (((CAST(SCALED_ROUND_INT_DIVIDE(CAST(((COUNT(FZ.F)) + 1) * (COUNT(FZ.F)) "
                + "AS NUMBER(38,10)), (((COUNT(FZ.F)) - 1) * ((COUNT(FZ.F)) - 2)) * ((COUNT(FZ.F)) - 3)) AS FLOAT)) * "
                + "(((((SUM(((FZ.F * FZ.F) * FZ.F) * FZ.F)) - (((CAST(4 AS FLOAT)) * ((SUM(FZ.F)) / (CAST(COUNT(FZ.F) "
                + "AS FLOAT)))) * (SUM((FZ.F * FZ.F) * FZ.F)))) + ((((CAST(6 AS FLOAT)) * ((SUM(FZ.F)) / "
                + "(CAST(COUNT(FZ.F) AS FLOAT)))) * ((SUM(FZ.F)) / (CAST(COUNT(FZ.F) AS FLOAT)))) * (SUM(FZ.F * "
                + "FZ.F)))) - (((((CAST(4 AS FLOAT)) * ((SUM(FZ.F)) / (CAST(COUNT(FZ.F) AS FLOAT)))) * ((SUM(FZ.F)) / "
                + "(CAST(COUNT(FZ.F) AS FLOAT)))) * ((SUM(FZ.F)) / (CAST(COUNT(FZ.F) AS FLOAT)))) * (SUM(FZ.F)))) + "
                + "(((((CAST(COUNT(FZ.F) AS FLOAT)) * ((SUM(FZ.F)) / (CAST(COUNT(FZ.F) AS FLOAT)))) * ((SUM(FZ.F)) / "
                + "(CAST(COUNT(FZ.F) AS FLOAT)))) * ((SUM(FZ.F)) / (CAST(COUNT(FZ.F) AS FLOAT)))) * ((SUM(FZ.F)) / "
                + "(CAST(COUNT(FZ.F) AS FLOAT)))))) / (((IFF((((CAST(COUNT(FZ.F) AS FLOAT)) * (SUM(FZ.F * FZ.F))) - "
                + "((SUM(FZ.F)) * (SUM(FZ.F)))) < (CAST(0 AS FLOAT)), CAST(0 AS FLOAT), ((CAST(COUNT(FZ.F) AS FLOAT)) "
                + "* (SUM(FZ.F * FZ.F))) - ((SUM(FZ.F)) * (SUM(FZ.F))))) / (CAST(NULLIF((COUNT(FZ.F)) * ((COUNT(FZ.F)) "
                + "- 1), 0) AS FLOAT))) * ((IFF((((CAST(COUNT(FZ.F) AS FLOAT)) * (SUM(FZ.F * FZ.F))) - ((SUM(FZ.F)) * "
                + "(SUM(FZ.F)))) < (CAST(0 AS FLOAT)), CAST(0 AS FLOAT), ((CAST(COUNT(FZ.F) AS FLOAT)) * (SUM(FZ.F * "
                + "FZ.F))) - ((SUM(FZ.F)) * (SUM(FZ.F))))) / (CAST(NULLIF((COUNT(FZ.F)) * ((COUNT(FZ.F)) - 1), 0) AS "
                + "FLOAT))))) - (CAST(SCALED_ROUND_INT_DIVIDE((3 * ((COUNT(FZ.F)) - 1)) * ((COUNT(FZ.F)) - 1), "
                + "((COUNT(FZ.F)) - 2) * ((COUNT(FZ.F)) - 3)) AS FLOAT)))] can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT KURTOSIS(fz.s) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[IFF(((((COUNT(FZ.S)) * ((COUNT(FZ.S)) - 1)) * ((COUNT(FZ.S)) - 2)) * ((COUNT(FZ.S)) - 3)) = 0, "
                + "SYSTEM$NULL_TO_REAL(null), (((CAST(SCALED_ROUND_INT_DIVIDE(CAST(((COUNT(FZ.S)) + 1) * (COUNT(FZ.S)) "
                + "AS NUMBER(38,10)), (((COUNT(FZ.S)) - 1) * ((COUNT(FZ.S)) - 2)) * ((COUNT(FZ.S)) - 3)) AS FLOAT)) * "
                + "(((((SUM((((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S AS FLOAT))) * (CAST(FZ.S AS FLOAT))) * (CAST(FZ.S AS "
                + "FLOAT)))) - (((CAST(4 AS FLOAT)) * ((SUM(CAST(FZ.S AS FLOAT))) / (CAST(COUNT(FZ.S) AS FLOAT)))) * "
                + "(SUM(((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S AS FLOAT))) * (CAST(FZ.S AS FLOAT)))))) + ((((CAST(6 AS "
                + "FLOAT)) * ((SUM(CAST(FZ.S AS FLOAT))) / (CAST(COUNT(FZ.S) AS FLOAT)))) * ((SUM(CAST(FZ.S AS "
                + "FLOAT))) / (CAST(COUNT(FZ.S) AS FLOAT)))) * (SUM((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S AS FLOAT)))))) "
                + "- (((((CAST(4 AS FLOAT)) * ((SUM(CAST(FZ.S AS FLOAT))) / (CAST(COUNT(FZ.S) AS FLOAT)))) * "
                + "((SUM(CAST(FZ.S AS FLOAT))) / (CAST(COUNT(FZ.S) AS FLOAT)))) * ((SUM(CAST(FZ.S AS FLOAT))) / "
                + "(CAST(COUNT(FZ.S) AS FLOAT)))) * (SUM(CAST(FZ.S AS FLOAT))))) + (((((CAST(COUNT(FZ.S) AS FLOAT)) * "
                + "((SUM(CAST(FZ.S AS FLOAT))) / (CAST(COUNT(FZ.S) AS FLOAT)))) * ((SUM(CAST(FZ.S AS FLOAT))) / "
                + "(CAST(COUNT(FZ.S) AS FLOAT)))) * ((SUM(CAST(FZ.S AS FLOAT))) / (CAST(COUNT(FZ.S) AS FLOAT)))) * "
                + "((SUM(CAST(FZ.S AS FLOAT))) / (CAST(COUNT(FZ.S) AS FLOAT)))))) / (((IFF((((CAST(COUNT(FZ.S) AS "
                + "FLOAT)) * (SUM((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S AS FLOAT))))) - ((SUM(CAST(FZ.S AS FLOAT))) * "
                + "(SUM(CAST(FZ.S AS FLOAT))))) < (CAST(0 AS FLOAT)), CAST(0 AS FLOAT), ((CAST(COUNT(FZ.S) AS FLOAT)) "
                + "* (SUM((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S AS FLOAT))))) - ((SUM(CAST(FZ.S AS FLOAT))) * "
                + "(SUM(CAST(FZ.S AS FLOAT)))))) / (CAST(NULLIF((COUNT(FZ.S)) * ((COUNT(FZ.S)) - 1), 0) AS FLOAT))) * "
                + "((IFF((((CAST(COUNT(FZ.S) AS FLOAT)) * (SUM((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S AS FLOAT))))) - "
                + "((SUM(CAST(FZ.S AS FLOAT))) * (SUM(CAST(FZ.S AS FLOAT))))) < (CAST(0 AS FLOAT)), CAST(0 AS FLOAT), "
                + "((CAST(COUNT(FZ.S) AS FLOAT)) * (SUM((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S AS FLOAT))))) - "
                + "((SUM(CAST(FZ.S AS FLOAT))) * (SUM(CAST(FZ.S AS FLOAT)))))) / (CAST(NULLIF((COUNT(FZ.S)) * "
                + "((COUNT(FZ.S)) - 1), 0) AS FLOAT))))) - (CAST(SCALED_ROUND_INT_DIVIDE((3 * ((COUNT(FZ.S)) - 1)) * "
                + "((COUNT(FZ.S)) - 1), ((COUNT(FZ.S)) - 2) * ((COUNT(FZ.S)) - 3)) AS FLOAT)))] can only appear in "
                + "having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT SKEW(fz.id) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[IFF((((COUNT(FZ.ID)) * ((COUNT(FZ.ID)) - 1)) * ((COUNT(FZ.ID)) - 2)) = 0, "
                + "SYSTEM$NULL_TO_REAL(null), (CAST(SCALED_DECIMAL_MULTIPLY(SCALED_ROUND_INT_DIVIDE(CAST(COUNT(FZ.ID) "
                + "AS NUMBER(38,10)), ((COUNT(FZ.ID)) - 1) * ((COUNT(FZ.ID)) - 2)), ((CAST((CAST(SUM((FZ.ID * FZ.ID) * "
                + "FZ.ID) AS NUMBER(38,6))) - ((3 * (SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), COUNT(FZ.ID)))) * (SUM(FZ.ID "
                + "* FZ.ID))) AS NUMBER(38,12))) + (((3 * (SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), COUNT(FZ.ID)))) * "
                + "(SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), COUNT(FZ.ID)))) * (SUM(FZ.ID)))) - "
                + "(SCALED_DECIMAL_MULTIPLY(((COUNT(FZ.ID)) * (SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), COUNT(FZ.ID)))) * "
                + "(SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), COUNT(FZ.ID))), SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), "
                + "COUNT(FZ.ID)), 6)), 12) AS FLOAT)) / (((SQRT(CAST(SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.ID)) * "
                + "(SUM(FZ.ID * FZ.ID))) - ((SUM(FZ.ID)) * (SUM(FZ.ID)))) < 0, 0, ((COUNT(FZ.ID)) * (SUM(FZ.ID * "
                + "FZ.ID))) - ((SUM(FZ.ID)) * (SUM(FZ.ID)))), NULLIF((COUNT(FZ.ID)) * ((COUNT(FZ.ID)) - 1), 0)) AS "
                + "FLOAT))) * (SQRT(CAST(SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.ID)) * (SUM(FZ.ID * FZ.ID))) - "
                + "((SUM(FZ.ID)) * (SUM(FZ.ID)))) < 0, 0, ((COUNT(FZ.ID)) * (SUM(FZ.ID * FZ.ID))) - ((SUM(FZ.ID)) * "
                + "(SUM(FZ.ID)))), NULLIF((COUNT(FZ.ID)) * ((COUNT(FZ.ID)) - 1), 0)) AS FLOAT)))) * "
                + "(SQRT(CAST(SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.ID)) * (SUM(FZ.ID * FZ.ID))) - ((SUM(FZ.ID)) * "
                + "(SUM(FZ.ID)))) < 0, 0, ((COUNT(FZ.ID)) * (SUM(FZ.ID * FZ.ID))) - ((SUM(FZ.ID)) * (SUM(FZ.ID)))), "
                + "NULLIF((COUNT(FZ.ID)) * ((COUNT(FZ.ID)) - 1), 0)) AS FLOAT)))))] can only appear in having or "
                + "select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT SKEW(fz.d) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[IFF((((COUNT(FZ.D)) * ((COUNT(FZ.D)) - 1)) * ((COUNT(FZ.D)) - 2)) = 0, SYSTEM$NULL_TO_REAL(null), "
                + "(CAST(SCALED_DECIMAL_MULTIPLY(SCALED_ROUND_INT_DIVIDE(CAST(COUNT(FZ.D) AS NUMBER(38,10)), "
                + "((COUNT(FZ.D)) - 1) * ((COUNT(FZ.D)) - 2)), (((CAST(SUM((FZ.D * FZ.D) * FZ.D) AS NUMBER(38,12))) - "
                + "((3 * ((CAST(SUM(FZ.D) AS NUMBER(28,8))) / (COUNT(FZ.D)))) * (SUM(FZ.D * FZ.D)))) + "
                + "(SCALED_DECIMAL_MULTIPLY(SCALED_DECIMAL_MULTIPLY(3 * ((CAST(SUM(FZ.D) AS NUMBER(28,8))) / "
                + "(COUNT(FZ.D))), (CAST(SUM(FZ.D) AS NUMBER(28,8))) / (COUNT(FZ.D)), 4), SUM(FZ.D), 2))) - "
                + "(SCALED_DECIMAL_MULTIPLY(SCALED_DECIMAL_MULTIPLY((COUNT(FZ.D)) * ((CAST(SUM(FZ.D) AS NUMBER(28,8))) "
                + "/ (COUNT(FZ.D))), (CAST(SUM(FZ.D) AS NUMBER(28,8))) / (COUNT(FZ.D)), 4), (CAST(SUM(FZ.D) AS "
                + "NUMBER(28,8))) / (COUNT(FZ.D)), 8)), 12) AS FLOAT)) / "
                + "(((SQRT(CAST(SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.D)) * (SUM(FZ.D * FZ.D))) - ((SUM(FZ.D)) * "
                + "(SUM(FZ.D)))) < (CAST(0 AS NUMBER(38,4))), CAST(0 AS NUMBER(38,4)), ((COUNT(FZ.D)) * (SUM(FZ.D * "
                + "FZ.D))) - ((SUM(FZ.D)) * (SUM(FZ.D)))), NULLIF((COUNT(FZ.D)) * ((COUNT(FZ.D)) - 1), 0)) AS FLOAT))) "
                + "* (SQRT(CAST(SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.D)) * (SUM(FZ.D * FZ.D))) - ((SUM(FZ.D)) * "
                + "(SUM(FZ.D)))) < (CAST(0 AS NUMBER(38,4))), CAST(0 AS NUMBER(38,4)), ((COUNT(FZ.D)) * (SUM(FZ.D * "
                + "FZ.D))) - ((SUM(FZ.D)) * (SUM(FZ.D)))), NULLIF((COUNT(FZ.D)) * ((COUNT(FZ.D)) - 1), 0)) AS "
                + "FLOAT)))) * (SQRT(CAST(SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.D)) * (SUM(FZ.D * FZ.D))) - "
                + "((SUM(FZ.D)) * (SUM(FZ.D)))) < (CAST(0 AS NUMBER(38,4))), CAST(0 AS NUMBER(38,4)), ((COUNT(FZ.D)) * "
                + "(SUM(FZ.D * FZ.D))) - ((SUM(FZ.D)) * (SUM(FZ.D)))), NULLIF((COUNT(FZ.D)) * ((COUNT(FZ.D)) - 1), 0)) "
                + "AS FLOAT)))))] can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT SKEW(fz.f) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[IFF((((COUNT(FZ.F)) * ((COUNT(FZ.F)) - 1)) * ((COUNT(FZ.F)) - 2)) = 0, SYSTEM$NULL_TO_REAL(null), "
                + "((CAST(SCALED_ROUND_INT_DIVIDE(CAST(COUNT(FZ.F) AS NUMBER(38,10)), ((COUNT(FZ.F)) - 1) * "
                + "((COUNT(FZ.F)) - 2)) AS FLOAT)) * ((((SUM((FZ.F * FZ.F) * FZ.F)) - (((CAST(3 AS FLOAT)) * "
                + "((SUM(FZ.F)) / (CAST(COUNT(FZ.F) AS FLOAT)))) * (SUM(FZ.F * FZ.F)))) + ((((CAST(3 AS FLOAT)) * "
                + "((SUM(FZ.F)) / (CAST(COUNT(FZ.F) AS FLOAT)))) * ((SUM(FZ.F)) / (CAST(COUNT(FZ.F) AS FLOAT)))) * "
                + "(SUM(FZ.F)))) - ((((CAST(COUNT(FZ.F) AS FLOAT)) * ((SUM(FZ.F)) / (CAST(COUNT(FZ.F) AS FLOAT)))) * "
                + "((SUM(FZ.F)) / (CAST(COUNT(FZ.F) AS FLOAT)))) * ((SUM(FZ.F)) / (CAST(COUNT(FZ.F) AS FLOAT)))))) / "
                + "(((SQRT((IFF((((CAST(COUNT(FZ.F) AS FLOAT)) * (SUM(FZ.F * FZ.F))) - ((SUM(FZ.F)) * (SUM(FZ.F)))) < "
                + "(CAST(0 AS FLOAT)), CAST(0 AS FLOAT), ((CAST(COUNT(FZ.F) AS FLOAT)) * (SUM(FZ.F * FZ.F))) - "
                + "((SUM(FZ.F)) * (SUM(FZ.F))))) / (CAST(NULLIF((COUNT(FZ.F)) * ((COUNT(FZ.F)) - 1), 0) AS FLOAT)))) * "
                + "(SQRT((IFF((((CAST(COUNT(FZ.F) AS FLOAT)) * (SUM(FZ.F * FZ.F))) - ((SUM(FZ.F)) * (SUM(FZ.F)))) < "
                + "(CAST(0 AS FLOAT)), CAST(0 AS FLOAT), ((CAST(COUNT(FZ.F) AS FLOAT)) * (SUM(FZ.F * FZ.F))) - "
                + "((SUM(FZ.F)) * (SUM(FZ.F))))) / (CAST(NULLIF((COUNT(FZ.F)) * ((COUNT(FZ.F)) - 1), 0) AS FLOAT))))) "
                + "* (SQRT((IFF((((CAST(COUNT(FZ.F) AS FLOAT)) * (SUM(FZ.F * FZ.F))) - ((SUM(FZ.F)) * (SUM(FZ.F)))) < "
                + "(CAST(0 AS FLOAT)), CAST(0 AS FLOAT), ((CAST(COUNT(FZ.F) AS FLOAT)) * (SUM(FZ.F * FZ.F))) - "
                + "((SUM(FZ.F)) * (SUM(FZ.F))))) / (CAST(NULLIF((COUNT(FZ.F)) * ((COUNT(FZ.F)) - 1), 0) AS "
                + "FLOAT))))))] can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT SKEW(fz.s) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[IFF((((COUNT(FZ.S)) * ((COUNT(FZ.S)) - 1)) * ((COUNT(FZ.S)) - 2)) = 0, SYSTEM$NULL_TO_REAL(null), "
                + "((CAST(SCALED_ROUND_INT_DIVIDE(CAST(COUNT(FZ.S) AS NUMBER(38,10)), ((COUNT(FZ.S)) - 1) * "
                + "((COUNT(FZ.S)) - 2)) AS FLOAT)) * ((((SUM(((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S AS FLOAT))) * "
                + "(CAST(FZ.S AS FLOAT)))) - (((CAST(3 AS FLOAT)) * ((SUM(CAST(FZ.S AS FLOAT))) / (CAST(COUNT(FZ.S) AS "
                + "FLOAT)))) * (SUM((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S AS FLOAT)))))) + ((((CAST(3 AS FLOAT)) * "
                + "((SUM(CAST(FZ.S AS FLOAT))) / (CAST(COUNT(FZ.S) AS FLOAT)))) * ((SUM(CAST(FZ.S AS FLOAT))) / "
                + "(CAST(COUNT(FZ.S) AS FLOAT)))) * (SUM(CAST(FZ.S AS FLOAT))))) - ((((CAST(COUNT(FZ.S) AS FLOAT)) * "
                + "((SUM(CAST(FZ.S AS FLOAT))) / (CAST(COUNT(FZ.S) AS FLOAT)))) * ((SUM(CAST(FZ.S AS FLOAT))) / "
                + "(CAST(COUNT(FZ.S) AS FLOAT)))) * ((SUM(CAST(FZ.S AS FLOAT))) / (CAST(COUNT(FZ.S) AS FLOAT)))))) / "
                + "(((SQRT((IFF((((CAST(COUNT(FZ.S) AS FLOAT)) * (SUM((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S AS FLOAT))))) "
                + "- ((SUM(CAST(FZ.S AS FLOAT))) * (SUM(CAST(FZ.S AS FLOAT))))) < (CAST(0 AS FLOAT)), CAST(0 AS "
                + "FLOAT), ((CAST(COUNT(FZ.S) AS FLOAT)) * (SUM((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S AS FLOAT))))) - "
                + "((SUM(CAST(FZ.S AS FLOAT))) * (SUM(CAST(FZ.S AS FLOAT)))))) / (CAST(NULLIF((COUNT(FZ.S)) * "
                + "((COUNT(FZ.S)) - 1), 0) AS FLOAT)))) * (SQRT((IFF((((CAST(COUNT(FZ.S) AS FLOAT)) * (SUM((CAST(FZ.S "
                + "AS FLOAT)) * (CAST(FZ.S AS FLOAT))))) - ((SUM(CAST(FZ.S AS FLOAT))) * (SUM(CAST(FZ.S AS FLOAT))))) "
                + "< (CAST(0 AS FLOAT)), CAST(0 AS FLOAT), ((CAST(COUNT(FZ.S) AS FLOAT)) * (SUM((CAST(FZ.S AS FLOAT)) "
                + "* (CAST(FZ.S AS FLOAT))))) - ((SUM(CAST(FZ.S AS FLOAT))) * (SUM(CAST(FZ.S AS FLOAT)))))) / "
                + "(CAST(NULLIF((COUNT(FZ.S)) * ((COUNT(FZ.S)) - 1), 0) AS FLOAT))))) * (SQRT((IFF((((CAST(COUNT(FZ.S) "
                + "AS FLOAT)) * (SUM((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S AS FLOAT))))) - ((SUM(CAST(FZ.S AS FLOAT))) * "
                + "(SUM(CAST(FZ.S AS FLOAT))))) < (CAST(0 AS FLOAT)), CAST(0 AS FLOAT), ((CAST(COUNT(FZ.S) AS FLOAT)) "
                + "* (SUM((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S AS FLOAT))))) - ((SUM(CAST(FZ.S AS FLOAT))) * "
                + "(SUM(CAST(FZ.S AS FLOAT)))))) / (CAST(NULLIF((COUNT(FZ.S)) * ((COUNT(FZ.S)) - 1), 0) AS "
                + "FLOAT))))))] can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT STDDEV(DISTINCT fz.id) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SQRT(CAST(SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(DISTINCT FZ.ID)) * (SUM(DISTINCT FZ.ID * FZ.ID))) - "
                + "((SUM(DISTINCT FZ.ID)) * (SUM(DISTINCT FZ.ID)))) < 0, 0, ((COUNT(DISTINCT FZ.ID)) * (SUM(DISTINCT "
                + "FZ.ID * FZ.ID))) - ((SUM(DISTINCT FZ.ID)) * (SUM(DISTINCT FZ.ID)))), NULLIF((COUNT(DISTINCT FZ.ID)) "
                + "* ((COUNT(DISTINCT FZ.ID)) - 1), 0)) AS FLOAT))] can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT VARIANCE(DISTINCT fz.f) FROM g)",
                "REFUSED Unsupported feature 'VARIANCE(DISTINCT)'."},
            {"SELECT id FROM fz WHERE EXISTS (SELECT KURTOSIS(DISTINCT fz.d) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [KURTOSIS(DISTINCT "
                + "FZ.D)] can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT VAR_POP(fz.e) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.E)) * (SUM(FZ.E * FZ.E))) - ((SUM(FZ.E)) * (SUM(FZ.E)))) < "
                + "(CAST(0 AS NUMBER(38,8))), CAST(0 AS NUMBER(38,8)), ((COUNT(FZ.E)) * (SUM(FZ.E * FZ.E))) - "
                + "((SUM(FZ.E)) * (SUM(FZ.E)))), NULLIF((COUNT(FZ.E)) * (COUNT(FZ.E)), 0))] can only appear in having "
                + "or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT SKEW(fz.e) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[IFF((((COUNT(FZ.E)) * ((COUNT(FZ.E)) - 1)) * ((COUNT(FZ.E)) - 2)) = 0, SYSTEM$NULL_TO_REAL(null), "
                + "(CAST(SCALED_DECIMAL_MULTIPLY(SCALED_ROUND_INT_DIVIDE(CAST(COUNT(FZ.E) AS NUMBER(38,10)), "
                + "((COUNT(FZ.E)) - 1) * ((COUNT(FZ.E)) - 2)), (((SUM((FZ.E * FZ.E) * FZ.E)) - "
                + "(SCALED_DECIMAL_MULTIPLY(3 * ((CAST(SUM(FZ.E) AS NUMBER(30,10))) / (COUNT(FZ.E))), SUM(FZ.E * "
                + "FZ.E), 6))) + (SCALED_DECIMAL_MULTIPLY(SCALED_DECIMAL_MULTIPLY(3 * ((CAST(SUM(FZ.E) AS "
                + "NUMBER(30,10))) / (COUNT(FZ.E))), (CAST(SUM(FZ.E) AS NUMBER(30,10))) / (COUNT(FZ.E)), 8), "
                + "SUM(FZ.E), 4))) - (SCALED_DECIMAL_MULTIPLY(SCALED_DECIMAL_MULTIPLY((COUNT(FZ.E)) * ((CAST(SUM(FZ.E) "
                + "AS NUMBER(30,10))) / (COUNT(FZ.E))), (CAST(SUM(FZ.E) AS NUMBER(30,10))) / (COUNT(FZ.E)), 8), "
                + "(CAST(SUM(FZ.E) AS NUMBER(30,10))) / (COUNT(FZ.E)), 10)), 12) AS FLOAT)) / "
                + "(((SQRT(CAST(SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.E)) * (SUM(FZ.E * FZ.E))) - ((SUM(FZ.E)) * "
                + "(SUM(FZ.E)))) < (CAST(0 AS NUMBER(38,8))), CAST(0 AS NUMBER(38,8)), ((COUNT(FZ.E)) * (SUM(FZ.E * "
                + "FZ.E))) - ((SUM(FZ.E)) * (SUM(FZ.E)))), NULLIF((COUNT(FZ.E)) * ((COUNT(FZ.E)) - 1), 0)) AS FLOAT))) "
                + "* (SQRT(CAST(SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.E)) * (SUM(FZ.E * FZ.E))) - ((SUM(FZ.E)) * "
                + "(SUM(FZ.E)))) < (CAST(0 AS NUMBER(38,8))), CAST(0 AS NUMBER(38,8)), ((COUNT(FZ.E)) * (SUM(FZ.E * "
                + "FZ.E))) - ((SUM(FZ.E)) * (SUM(FZ.E)))), NULLIF((COUNT(FZ.E)) * ((COUNT(FZ.E)) - 1), 0)) AS "
                + "FLOAT)))) * (SQRT(CAST(SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.E)) * (SUM(FZ.E * FZ.E))) - "
                + "((SUM(FZ.E)) * (SUM(FZ.E)))) < (CAST(0 AS NUMBER(38,8))), CAST(0 AS NUMBER(38,8)), ((COUNT(FZ.E)) * "
                + "(SUM(FZ.E * FZ.E))) - ((SUM(FZ.E)) * (SUM(FZ.E)))), NULLIF((COUNT(FZ.E)) * ((COUNT(FZ.E)) - 1), 0)) "
                + "AS FLOAT)))))] can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT STDDEV(fz.va) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SQRT((IFF((((CAST(COUNT(FZ.VA) AS FLOAT)) * (SUM((CAST(FZ.VA AS FLOAT)) * (CAST(FZ.VA AS "
                + "FLOAT))))) - ((SUM(CAST(FZ.VA AS FLOAT))) * (SUM(CAST(FZ.VA AS FLOAT))))) < (CAST(0 AS FLOAT)), "
                + "CAST(0 AS FLOAT), ((CAST(COUNT(FZ.VA) AS FLOAT)) * (SUM((CAST(FZ.VA AS FLOAT)) * (CAST(FZ.VA AS "
                + "FLOAT))))) - ((SUM(CAST(FZ.VA AS FLOAT))) * (SUM(CAST(FZ.VA AS FLOAT)))))) / "
                + "(CAST(NULLIF((COUNT(FZ.VA)) * ((COUNT(FZ.VA)) - 1), 0) AS FLOAT)))] can only appear in having or "
                + "select clause"},
        });
    }

    /** DISTINCT calls at the top level and inside the subquery, and expression arguments. */
    @Test
    public void distinctValuesAndExpressionArguments() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR, f FLOAT, d NUMBER(10,2), dt DATE, va "
            + "VARIANT, e NUMBER(12,4))");
        engine.execute("INSERT INTO fz SELECT 5, TRUE, 'a', 1.5, 2.25, '2020-01-01'::DATE, PARSE_JSON('1'), 1.125");
        engine.execute("INSERT INTO fz SELECT 7, FALSE, 'b', 2.5, 3.25, '2020-01-02'::DATE, PARSE_JSON('2'), 2.5");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT, s VARCHAR)");
        engine.execute("INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
        assertCells(new String[][] {
            {"SELECT id FROM fz WHERE EXISTS (SELECT STDDEV(DISTINCT fz.f) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SQRT((IFF((((CAST(COUNT(DISTINCT FZ.F) AS FLOAT)) * (SUM(DISTINCT FZ.F * FZ.F))) - ((SUM(DISTINCT "
                + "FZ.F)) * (SUM(DISTINCT FZ.F)))) < (CAST(0 AS FLOAT)), CAST(0 AS FLOAT), ((CAST(COUNT(DISTINCT FZ.F) "
                + "AS FLOAT)) * (SUM(DISTINCT FZ.F * FZ.F))) - ((SUM(DISTINCT FZ.F)) * (SUM(DISTINCT FZ.F))))) / "
                + "(CAST(NULLIF((COUNT(DISTINCT FZ.F)) * ((COUNT(DISTINCT FZ.F)) - 1), 0) AS FLOAT)))] can only appear "
                + "in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT STDDEV_SAMP(DISTINCT fz.f) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SQRT((IFF((((CAST(COUNT(DISTINCT FZ.F) AS FLOAT)) * (SUM(DISTINCT FZ.F * FZ.F))) - ((SUM(DISTINCT "
                + "FZ.F)) * (SUM(DISTINCT FZ.F)))) < (CAST(0 AS FLOAT)), CAST(0 AS FLOAT), ((CAST(COUNT(DISTINCT FZ.F) "
                + "AS FLOAT)) * (SUM(DISTINCT FZ.F * FZ.F))) - ((SUM(DISTINCT FZ.F)) * (SUM(DISTINCT FZ.F))))) / "
                + "(CAST(NULLIF((COUNT(DISTINCT FZ.F)) * ((COUNT(DISTINCT FZ.F)) - 1), 0) AS FLOAT)))] can only appear "
                + "in having or select clause"},
            {"SELECT STDDEV_POP(DISTINCT id) FROM fz",
                "1.0"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT STDDEV_POP(DISTINCT fz.f) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SQRT((IFF((((CAST(COUNT(DISTINCT FZ.F) AS FLOAT)) * (SUM(DISTINCT FZ.F * FZ.F))) - ((SUM(DISTINCT "
                + "FZ.F)) * (SUM(DISTINCT FZ.F)))) < (CAST(0 AS FLOAT)), CAST(0 AS FLOAT), ((CAST(COUNT(DISTINCT FZ.F) "
                + "AS FLOAT)) * (SUM(DISTINCT FZ.F * FZ.F))) - ((SUM(DISTINCT FZ.F)) * (SUM(DISTINCT FZ.F))))) / "
                + "(CAST(NULLIF((COUNT(DISTINCT FZ.F)) * (COUNT(DISTINCT FZ.F)), 0) AS FLOAT)))] can only appear in "
                + "having or select clause"},
            {"SELECT VARIANCE(DISTINCT id) FROM fz",
                "REFUSED Unsupported feature 'VARIANCE(DISTINCT)'."},
            {"SELECT id FROM fz WHERE EXISTS (SELECT VARIANCE(DISTINCT fz.f) FROM g)",
                "REFUSED Unsupported feature 'VARIANCE(DISTINCT)'."},
            {"SELECT VARIANCE_SAMP(DISTINCT id) FROM fz",
                "2.000000"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT VARIANCE_SAMP(DISTINCT fz.f) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[(IFF((((CAST(COUNT(DISTINCT FZ.F) AS FLOAT)) * (SUM(DISTINCT FZ.F * FZ.F))) - ((SUM(DISTINCT "
                + "FZ.F)) * (SUM(DISTINCT FZ.F)))) < (CAST(0 AS FLOAT)), CAST(0 AS FLOAT), ((CAST(COUNT(DISTINCT FZ.F) "
                + "AS FLOAT)) * (SUM(DISTINCT FZ.F * FZ.F))) - ((SUM(DISTINCT FZ.F)) * (SUM(DISTINCT FZ.F))))) / "
                + "(CAST(NULLIF((COUNT(DISTINCT FZ.F)) * ((COUNT(DISTINCT FZ.F)) - 1), 0) AS FLOAT))] can only appear "
                + "in having or select clause"},
            {"SELECT VARIANCE_POP(DISTINCT id) FROM fz",
                "1.000000"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT VARIANCE_POP(DISTINCT fz.f) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[(IFF((((CAST(COUNT(DISTINCT FZ.F) AS FLOAT)) * (SUM(DISTINCT FZ.F * FZ.F))) - ((SUM(DISTINCT "
                + "FZ.F)) * (SUM(DISTINCT FZ.F)))) < (CAST(0 AS FLOAT)), CAST(0 AS FLOAT), ((CAST(COUNT(DISTINCT FZ.F) "
                + "AS FLOAT)) * (SUM(DISTINCT FZ.F * FZ.F))) - ((SUM(DISTINCT FZ.F)) * (SUM(DISTINCT FZ.F))))) / "
                + "(CAST(NULLIF((COUNT(DISTINCT FZ.F)) * (COUNT(DISTINCT FZ.F)), 0) AS FLOAT))] can only appear in "
                + "having or select clause"},
            {"SELECT VAR_SAMP(DISTINCT id) FROM fz",
                "2.000000"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT VAR_SAMP(DISTINCT fz.f) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[(IFF((((CAST(COUNT(DISTINCT FZ.F) AS FLOAT)) * (SUM(DISTINCT FZ.F * FZ.F))) - ((SUM(DISTINCT "
                + "FZ.F)) * (SUM(DISTINCT FZ.F)))) < (CAST(0 AS FLOAT)), CAST(0 AS FLOAT), ((CAST(COUNT(DISTINCT FZ.F) "
                + "AS FLOAT)) * (SUM(DISTINCT FZ.F * FZ.F))) - ((SUM(DISTINCT FZ.F)) * (SUM(DISTINCT FZ.F))))) / "
                + "(CAST(NULLIF((COUNT(DISTINCT FZ.F)) * ((COUNT(DISTINCT FZ.F)) - 1), 0) AS FLOAT))] can only appear "
                + "in having or select clause"},
            {"SELECT VAR_POP(DISTINCT id) FROM fz",
                "1.000000"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT VAR_POP(DISTINCT fz.f) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[(IFF((((CAST(COUNT(DISTINCT FZ.F) AS FLOAT)) * (SUM(DISTINCT FZ.F * FZ.F))) - ((SUM(DISTINCT "
                + "FZ.F)) * (SUM(DISTINCT FZ.F)))) < (CAST(0 AS FLOAT)), CAST(0 AS FLOAT), ((CAST(COUNT(DISTINCT FZ.F) "
                + "AS FLOAT)) * (SUM(DISTINCT FZ.F * FZ.F))) - ((SUM(DISTINCT FZ.F)) * (SUM(DISTINCT FZ.F))))) / "
                + "(CAST(NULLIF((COUNT(DISTINCT FZ.F)) * (COUNT(DISTINCT FZ.F)), 0) AS FLOAT))] can only appear in "
                + "having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT KURTOSIS(DISTINCT fz.f) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [KURTOSIS(DISTINCT "
                + "FZ.F)] can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT SKEW(DISTINCT fz.f) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [SKEW(DISTINCT "
                + "FZ.F)] can only appear in having or select clause"},
            {"SELECT CORR(DISTINCT id, d) FROM fz",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'CORR(DISTINCT FZ.ID, FZ.D)'"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT CORR(DISTINCT fz.id, fz.d) FROM g)",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'CORR(DISTINCT FZ.ID, FZ.D)'"},
            {"SELECT COVAR_SAMP(DISTINCT id, d) FROM fz",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'COVAR_SAMP(DISTINCT FZ.ID, "
                + "FZ.D)'"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT COVAR_SAMP(DISTINCT fz.id, fz.d) FROM g)",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'COVAR_SAMP(DISTINCT FZ.ID, "
                + "FZ.D)'"},
            {"SELECT COVAR_POP(DISTINCT id, d) FROM fz",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'COVAR_POP(DISTINCT FZ.ID, "
                + "FZ.D)'"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT COVAR_POP(DISTINCT fz.id, fz.d) FROM g)",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'COVAR_POP(DISTINCT FZ.ID, "
                + "FZ.D)'"},
            {"SELECT REGR_SLOPE(DISTINCT id, d) FROM fz",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'REGR_SLOPE(DISTINCT FZ.ID, "
                + "FZ.D)'"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT REGR_SLOPE(DISTINCT fz.id, fz.d) FROM g)",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'REGR_SLOPE(DISTINCT FZ.ID, "
                + "FZ.D)'"},
            {"SELECT REGR_R2(DISTINCT id, d) FROM fz",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'REGR_R2(DISTINCT FZ.ID, "
                + "FZ.D)'"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT REGR_R2(DISTINCT fz.id, fz.d) FROM g)",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'REGR_R2(DISTINCT FZ.ID, "
                + "FZ.D)'"},
            {"SELECT REGR_INTERCEPT(DISTINCT id, d) FROM fz",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'REGR_INTERCEPT(DISTINCT "
                + "FZ.ID, FZ.D)'"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT REGR_INTERCEPT(DISTINCT fz.id, fz.d) FROM g)",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'REGR_INTERCEPT(DISTINCT "
                + "FZ.ID, FZ.D)'"},
            {"SELECT REGR_SYY(DISTINCT id, d) FROM fz",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'REGR_SYY(DISTINCT FZ.ID, "
                + "FZ.D)'"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT REGR_SYY(DISTINCT fz.id, fz.d) FROM g)",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'REGR_SYY(DISTINCT FZ.ID, "
                + "FZ.D)'"},
            {"SELECT REGR_SXY(DISTINCT id, d) FROM fz",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'REGR_SXY(DISTINCT FZ.ID, "
                + "FZ.D)'"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT REGR_SXY(DISTINCT fz.id, fz.d) FROM g)",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'REGR_SXY(DISTINCT FZ.ID, "
                + "FZ.D)'"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT STDDEV(fz.id + 1) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SQRT(CAST(SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.ID + 1)) * (SUM((FZ.ID + 1) * (FZ.ID + 1)))) - "
                + "((SUM(FZ.ID + 1)) * (SUM(FZ.ID + 1)))) < 0, 0, ((COUNT(FZ.ID + 1)) * (SUM((FZ.ID + 1) * (FZ.ID + "
                + "1)))) - ((SUM(FZ.ID + 1)) * (SUM(FZ.ID + 1)))), NULLIF((COUNT(FZ.ID + 1)) * ((COUNT(FZ.ID + 1)) - "
                + "1), 0)) AS FLOAT))] can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT VAR_POP(fz.d * 2) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.D * 2)) * (SUM((FZ.D * 2) * (FZ.D * 2)))) - ((SUM(FZ.D * "
                + "2)) * (SUM(FZ.D * 2)))) < (CAST(0 AS NUMBER(38,4))), CAST(0 AS NUMBER(38,4)), ((COUNT(FZ.D * 2)) * "
                + "(SUM((FZ.D * 2) * (FZ.D * 2)))) - ((SUM(FZ.D * 2)) * (SUM(FZ.D * 2)))), NULLIF((COUNT(FZ.D * 2)) * "
                + "(COUNT(FZ.D * 2)), 0))] can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT KURTOSIS(fz.f + 1) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[IFF(((((COUNT(FZ.F + (CAST(1 AS FLOAT)))) * ((COUNT(FZ.F + (CAST(1 AS FLOAT)))) - 1)) * "
                + "((COUNT(FZ.F + (CAST(1 AS FLOAT)))) - 2)) * ((COUNT(FZ.F + (CAST(1 AS FLOAT)))) - 3)) = 0, "
                + "SYSTEM$NULL_TO_REAL(null), (((CAST(SCALED_ROUND_INT_DIVIDE(CAST(((COUNT(FZ.F + (CAST(1 AS FLOAT)))) "
                + "+ 1) * (COUNT(FZ.F + (CAST(1 AS FLOAT)))) AS NUMBER(38,10)), (((COUNT(FZ.F + (CAST(1 AS FLOAT)))) - "
                + "1) * ((COUNT(FZ.F + (CAST(1 AS FLOAT)))) - 2)) * ((COUNT(FZ.F + (CAST(1 AS FLOAT)))) - 3)) AS "
                + "FLOAT)) * (((((SUM((((FZ.F + (CAST(1 AS FLOAT))) * (FZ.F + (CAST(1 AS FLOAT)))) * (FZ.F + (CAST(1 "
                + "AS FLOAT)))) * (FZ.F + (CAST(1 AS FLOAT))))) - (((CAST(4 AS FLOAT)) * ((SUM(FZ.F + (CAST(1 AS "
                + "FLOAT)))) / (CAST(COUNT(FZ.F + (CAST(1 AS FLOAT))) AS FLOAT)))) * (SUM(((FZ.F + (CAST(1 AS FLOAT))) "
                + "* (FZ.F + (CAST(1 AS FLOAT)))) * (FZ.F + (CAST(1 AS FLOAT))))))) + ((((CAST(6 AS FLOAT)) * "
                + "((SUM(FZ.F + (CAST(1 AS FLOAT)))) / (CAST(COUNT(FZ.F + (CAST(1 AS FLOAT))) AS FLOAT)))) * "
                + "((SUM(FZ.F + (CAST(1 AS FLOAT)))) / (CAST(COUNT(FZ.F + (CAST(1 AS FLOAT))) AS FLOAT)))) * "
                + "(SUM((FZ.F + (CAST(1 AS FLOAT))) * (FZ.F + (CAST(1 AS FLOAT))))))) - (((((CAST(4 AS FLOAT)) * "
                + "((SUM(FZ.F + (CAST(1 AS FLOAT)))) / (CAST(COUNT(FZ.F + (CAST(1 AS FLOAT))) AS FLOAT)))) * "
                + "((SUM(FZ.F + (CAST(1 AS FLOAT)))) / (CAST(COUNT(FZ.F + (CAST(1 AS FLOAT))) AS FLOAT)))) * "
                + "((SUM(FZ.F + (CAST(1 AS FLOAT)))) / (CAST(COUNT(FZ.F + (CAST(1 AS FLOAT))) AS FLOAT)))) * (SUM(FZ.F "
                + "+ (CAST(1 AS FLOAT)))))) + (((((CAST(COUNT(FZ.F + (CAST(1 AS FLOAT))) AS FLOAT)) * ((SUM(FZ.F + "
                + "(CAST(1 AS FLOAT)))) / (CAST(COUNT(FZ.F + (CAST(1 AS FLOAT))) AS FLOAT)))) * ((SUM(FZ.F + (CAST(1 "
                + "AS FLOAT)))) / (CAST(COUNT(FZ.F + (CAST(1 AS FLOAT))) AS FLOAT)))) * ((SUM(FZ.F + (CAST(1 AS "
                + "FLOAT)))) / (CAST(COUNT(FZ.F + (CAST(1 AS FLOAT))) AS FLOAT)))) * ((SUM(FZ.F + (CAST(1 AS FLOAT)))) "
                + "/ (CAST(COUNT(FZ.F + (CAST(1 AS FLOAT))) AS FLOAT)))))) / (((IFF((((CAST(COUNT(FZ.F + (CAST(1 AS "
                + "FLOAT))) AS FLOAT)) * (SUM((FZ.F + (CAST(1 AS FLOAT))) * (FZ.F + (CAST(1 AS FLOAT)))))) - "
                + "((SUM(FZ.F + (CAST(1 AS FLOAT)))) * (SUM(FZ.F + (CAST(1 AS FLOAT)))))) < (CAST(0 AS FLOAT)), CAST(0 "
                + "AS FLOAT), ((CAST(COUNT(FZ.F + (CAST(1 AS FLOAT))) AS FLOAT)) * (SUM((FZ.F + (CAST(1 AS FLOAT))) * "
                + "(FZ.F + (CAST(1 AS FLOAT)))))) - ((SUM(FZ.F + (CAST(1 AS FLOAT)))) * (SUM(FZ.F + (CAST(1 AS "
                + "FLOAT))))))) / (CAST(NULLIF((COUNT(FZ.F + (CAST(1 AS FLOAT)))) * ((COUNT(FZ.F + (CAST(1 AS "
                + "FLOAT)))) - 1), 0) AS FLOAT))) * ((IFF((((CAST(COUNT(FZ.F + (CAST(1 AS FLOAT))) AS FLOAT)) * "
                + "(SUM((FZ.F + (CAST(1 AS FLOAT))) * (FZ.F + (CAST(1 AS FLOAT)))))) - ((SUM(FZ.F + (CAST(1 AS "
                + "FLOAT)))) * (SUM(FZ.F + (CAST(1 AS FLOAT)))))) < (CAST(0 AS FLOAT)), CAST(0 AS FLOAT), "
                + "((CAST(COUNT(FZ.F + (CAST(1 AS FLOAT))) AS FLOAT)) * (SUM((FZ.F + (CAST(1 AS FLOAT))) * (FZ.F + "
                + "(CAST(1 AS FLOAT)))))) - ((SUM(FZ.F + (CAST(1 AS FLOAT)))) * (SUM(FZ.F + (CAST(1 AS FLOAT))))))) / "
                + "(CAST(NULLIF((COUNT(FZ.F + (CAST(1 AS FLOAT)))) * ((COUNT(FZ.F + (CAST(1 AS FLOAT)))) - 1), 0) AS "
                + "FLOAT))))) - (CAST(SCALED_ROUND_INT_DIVIDE((3 * ((COUNT(FZ.F + (CAST(1 AS FLOAT)))) - 1)) * "
                + "((COUNT(FZ.F + (CAST(1 AS FLOAT)))) - 1), ((COUNT(FZ.F + (CAST(1 AS FLOAT)))) - 2) * ((COUNT(FZ.F + "
                + "(CAST(1 AS FLOAT)))) - 3)) AS FLOAT)))] can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT SKEW(-fz.id) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[IFF((((COUNT(NEGATE(FZ.ID))) * ((COUNT(NEGATE(FZ.ID))) - 1)) * ((COUNT(NEGATE(FZ.ID))) - 2)) = 0, "
                + "SYSTEM$NULL_TO_REAL(null), "
                + "(CAST(SCALED_DECIMAL_MULTIPLY(SCALED_ROUND_INT_DIVIDE(CAST(COUNT(NEGATE(FZ.ID)) AS NUMBER(38,10)), "
                + "((COUNT(NEGATE(FZ.ID))) - 1) * ((COUNT(NEGATE(FZ.ID))) - 2)), ((CAST((CAST(SUM(((NEGATE(FZ.ID)) * "
                + "(NEGATE(FZ.ID))) * (NEGATE(FZ.ID))) AS NUMBER(38,6))) - ((3 * "
                + "(SCALED_ROUND_INT_DIVIDE(SUM(NEGATE(FZ.ID)), COUNT(NEGATE(FZ.ID))))) * (SUM((NEGATE(FZ.ID)) * "
                + "(NEGATE(FZ.ID))))) AS NUMBER(38,12))) + (((3 * (SCALED_ROUND_INT_DIVIDE(SUM(NEGATE(FZ.ID)), "
                + "COUNT(NEGATE(FZ.ID))))) * (SCALED_ROUND_INT_DIVIDE(SUM(NEGATE(FZ.ID)), COUNT(NEGATE(FZ.ID))))) * "
                + "(SUM(NEGATE(FZ.ID))))) - (SCALED_DECIMAL_MULTIPLY(((COUNT(NEGATE(FZ.ID))) * "
                + "(SCALED_ROUND_INT_DIVIDE(SUM(NEGATE(FZ.ID)), COUNT(NEGATE(FZ.ID))))) * "
                + "(SCALED_ROUND_INT_DIVIDE(SUM(NEGATE(FZ.ID)), COUNT(NEGATE(FZ.ID)))), "
                + "SCALED_ROUND_INT_DIVIDE(SUM(NEGATE(FZ.ID)), COUNT(NEGATE(FZ.ID))), 6)), 12) AS FLOAT)) / "
                + "(((SQRT(CAST(SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(NEGATE(FZ.ID))) * (SUM((NEGATE(FZ.ID)) * "
                + "(NEGATE(FZ.ID))))) - ((SUM(NEGATE(FZ.ID))) * (SUM(NEGATE(FZ.ID))))) < 0, 0, ((COUNT(NEGATE(FZ.ID))) "
                + "* (SUM((NEGATE(FZ.ID)) * (NEGATE(FZ.ID))))) - ((SUM(NEGATE(FZ.ID))) * (SUM(NEGATE(FZ.ID))))), "
                + "NULLIF((COUNT(NEGATE(FZ.ID))) * ((COUNT(NEGATE(FZ.ID))) - 1), 0)) AS FLOAT))) * "
                + "(SQRT(CAST(SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(NEGATE(FZ.ID))) * (SUM((NEGATE(FZ.ID)) * "
                + "(NEGATE(FZ.ID))))) - ((SUM(NEGATE(FZ.ID))) * (SUM(NEGATE(FZ.ID))))) < 0, 0, ((COUNT(NEGATE(FZ.ID))) "
                + "* (SUM((NEGATE(FZ.ID)) * (NEGATE(FZ.ID))))) - ((SUM(NEGATE(FZ.ID))) * (SUM(NEGATE(FZ.ID))))), "
                + "NULLIF((COUNT(NEGATE(FZ.ID))) * ((COUNT(NEGATE(FZ.ID))) - 1), 0)) AS FLOAT)))) * "
                + "(SQRT(CAST(SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(NEGATE(FZ.ID))) * (SUM((NEGATE(FZ.ID)) * "
                + "(NEGATE(FZ.ID))))) - ((SUM(NEGATE(FZ.ID))) * (SUM(NEGATE(FZ.ID))))) < 0, 0, ((COUNT(NEGATE(FZ.ID))) "
                + "* (SUM((NEGATE(FZ.ID)) * (NEGATE(FZ.ID))))) - ((SUM(NEGATE(FZ.ID))) * (SUM(NEGATE(FZ.ID))))), "
                + "NULLIF((COUNT(NEGATE(FZ.ID))) * ((COUNT(NEGATE(FZ.ID))) - 1), 0)) AS FLOAT)))))] can only appear in "
                + "having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT VARIANCE(fz.id) + 1 FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(IFF((((COUNT(FZ.ID)) * (SUM(FZ.ID * FZ.ID))) - ((SUM(FZ.ID)) * "
                + "(SUM(FZ.ID)))) < 0, 0, ((COUNT(FZ.ID)) * (SUM(FZ.ID * FZ.ID))) - ((SUM(FZ.ID)) * (SUM(FZ.ID)))), "
                + "NULLIF((COUNT(FZ.ID)) * ((COUNT(FZ.ID)) - 1), 0))] can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT CORR(fz.id + 1, fz.f) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [(((SUM((IFF(FZ.ID "
                + "+ 1 IS NULL, SYSTEM$NULL_TO_REAL(null), FZ.F)) * (CAST(IFF(FZ.F IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), FZ.ID + 1) AS FLOAT)))) - (((SUM(IFF(FZ.ID + 1 IS NULL, "
                + "SYSTEM$NULL_TO_REAL(null), FZ.F))) * (SUM(CAST(IFF(FZ.F IS NULL, SYSTEM$NULL_TO_FIXED(null), FZ.ID "
                + "+ 1) AS FLOAT)))) / (CAST(NULLIF(COUNT(IFF(FZ.ID + 1 IS NULL, SYSTEM$NULL_TO_REAL(null), FZ.F)), 0) "
                + "AS FLOAT)))) / (CAST(NULLIF(COUNT(IFF(FZ.ID + 1 IS NULL, SYSTEM$NULL_TO_REAL(null), FZ.F)), 0) AS "
                + "FLOAT))) / (NULLIF((SQRT((IFF((((CAST(COUNT(IFF(FZ.ID + 1 IS NULL, SYSTEM$NULL_TO_REAL(null), "
                + "FZ.F)) AS FLOAT)) * (SUM((IFF(FZ.ID + 1 IS NULL, SYSTEM$NULL_TO_REAL(null), FZ.F)) * (IFF(FZ.ID + 1 "
                + "IS NULL, SYSTEM$NULL_TO_REAL(null), FZ.F))))) - ((SUM(IFF(FZ.ID + 1 IS NULL, "
                + "SYSTEM$NULL_TO_REAL(null), FZ.F))) * (SUM(IFF(FZ.ID + 1 IS NULL, SYSTEM$NULL_TO_REAL(null), "
                + "FZ.F))))) < (CAST(0 AS FLOAT)), CAST(0 AS FLOAT), ((CAST(COUNT(IFF(FZ.ID + 1 IS NULL, "
                + "SYSTEM$NULL_TO_REAL(null), FZ.F)) AS FLOAT)) * (SUM((IFF(FZ.ID + 1 IS NULL, "
                + "SYSTEM$NULL_TO_REAL(null), FZ.F)) * (IFF(FZ.ID + 1 IS NULL, SYSTEM$NULL_TO_REAL(null), FZ.F))))) - "
                + "((SUM(IFF(FZ.ID + 1 IS NULL, SYSTEM$NULL_TO_REAL(null), FZ.F))) * (SUM(IFF(FZ.ID + 1 IS NULL, "
                + "SYSTEM$NULL_TO_REAL(null), FZ.F)))))) / (CAST(NULLIF((COUNT(IFF(FZ.ID + 1 IS NULL, "
                + "SYSTEM$NULL_TO_REAL(null), FZ.F))) * (COUNT(IFF(FZ.ID + 1 IS NULL, SYSTEM$NULL_TO_REAL(null), "
                + "FZ.F))), 0) AS FLOAT)))) * (SQRT((IFF((((CAST(COUNT(CAST(IFF(FZ.F IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), FZ.ID + 1) AS FLOAT)) AS FLOAT)) * (SUM((CAST(IFF(FZ.F IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), FZ.ID + 1) AS FLOAT)) * (CAST(IFF(FZ.F IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), FZ.ID + 1) AS FLOAT))))) - ((SUM(CAST(IFF(FZ.F IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), FZ.ID + 1) AS FLOAT))) * (SUM(CAST(IFF(FZ.F IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), FZ.ID + 1) AS FLOAT))))) < (CAST(0 AS FLOAT)), CAST(0 AS FLOAT), "
                + "((CAST(COUNT(CAST(IFF(FZ.F IS NULL, SYSTEM$NULL_TO_FIXED(null), FZ.ID + 1) AS FLOAT)) AS FLOAT)) * "
                + "(SUM((CAST(IFF(FZ.F IS NULL, SYSTEM$NULL_TO_FIXED(null), FZ.ID + 1) AS FLOAT)) * (CAST(IFF(FZ.F IS "
                + "NULL, SYSTEM$NULL_TO_FIXED(null), FZ.ID + 1) AS FLOAT))))) - ((SUM(CAST(IFF(FZ.F IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), FZ.ID + 1) AS FLOAT))) * (SUM(CAST(IFF(FZ.F IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), FZ.ID + 1) AS FLOAT)))))) / (CAST(NULLIF((COUNT(CAST(IFF(FZ.F IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), FZ.ID + 1) AS FLOAT))) * (COUNT(CAST(IFF(FZ.F IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), FZ.ID + 1) AS FLOAT))), 0) AS FLOAT)))), CAST(0 AS FLOAT)))] can only "
                + "appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT COVAR_SAMP(fz.e, fz.e) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[((SUM((CAST(IFF(FZ.E IS NULL, SYSTEM$NULL_TO_FIXED(null), FZ.E) AS FLOAT)) * (CAST(IFF(FZ.E IS "
                + "NULL, SYSTEM$NULL_TO_FIXED(null), FZ.E) AS FLOAT)))) - (((SUM(CAST(IFF(FZ.E IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), FZ.E) AS FLOAT))) * (SUM(CAST(IFF(FZ.E IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), FZ.E) AS FLOAT)))) / (CAST(NULLIF(COUNT(CAST(IFF(FZ.E IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), FZ.E) AS FLOAT)), 0) AS FLOAT)))) / (CAST(NULLIF((COUNT(CAST(IFF(FZ.E "
                + "IS NULL, SYSTEM$NULL_TO_FIXED(null), FZ.E) AS FLOAT))) - 1, 0) AS FLOAT))] can only appear in "
                + "having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT REGR_SLOPE(fz.e, fz.d) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[(((SUM((CAST(IFF(FZ.D IS NULL, SYSTEM$NULL_TO_FIXED(null), FZ.E) AS FLOAT)) * (CAST(IFF(FZ.E IS "
                + "NULL, SYSTEM$NULL_TO_FIXED(null), FZ.D) AS FLOAT)))) - (((SUM(CAST(IFF(FZ.D IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), FZ.E) AS FLOAT))) * (SUM(CAST(IFF(FZ.E IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), FZ.D) AS FLOAT)))) / (CAST(NULLIF(COUNT(CAST(IFF(FZ.D IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), FZ.E) AS FLOAT)), 0) AS FLOAT)))) / (CAST(NULLIF(COUNT(CAST(IFF(FZ.D IS "
                + "NULL, SYSTEM$NULL_TO_FIXED(null), FZ.E) AS FLOAT)), 0) AS FLOAT))) / "
                + "(NULLIF((IFF((((CAST(COUNT(CAST(IFF(FZ.E IS NULL, SYSTEM$NULL_TO_FIXED(null), FZ.D) AS FLOAT)) AS "
                + "FLOAT)) * (SUM((CAST(IFF(FZ.E IS NULL, SYSTEM$NULL_TO_FIXED(null), FZ.D) AS FLOAT)) * "
                + "(CAST(IFF(FZ.E IS NULL, SYSTEM$NULL_TO_FIXED(null), FZ.D) AS FLOAT))))) - ((SUM(CAST(IFF(FZ.E IS "
                + "NULL, SYSTEM$NULL_TO_FIXED(null), FZ.D) AS FLOAT))) * (SUM(CAST(IFF(FZ.E IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), FZ.D) AS FLOAT))))) < (CAST(0 AS FLOAT)), CAST(0 AS FLOAT), "
                + "((CAST(COUNT(CAST(IFF(FZ.E IS NULL, SYSTEM$NULL_TO_FIXED(null), FZ.D) AS FLOAT)) AS FLOAT)) * "
                + "(SUM((CAST(IFF(FZ.E IS NULL, SYSTEM$NULL_TO_FIXED(null), FZ.D) AS FLOAT)) * (CAST(IFF(FZ.E IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), FZ.D) AS FLOAT))))) - ((SUM(CAST(IFF(FZ.E IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), FZ.D) AS FLOAT))) * (SUM(CAST(IFF(FZ.E IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), FZ.D) AS FLOAT)))))) / (CAST(NULLIF((COUNT(CAST(IFF(FZ.E IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), FZ.D) AS FLOAT))) * (COUNT(CAST(IFF(FZ.E IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), FZ.D) AS FLOAT))), 0) AS FLOAT)), CAST(0 AS FLOAT)))] can only appear "
                + "in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT REGR_INTERCEPT(fz.id, fz.e) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[((SUM(CAST(IFF(FZ.E IS NULL, SYSTEM$NULL_TO_FIXED(null), FZ.ID) AS FLOAT))) / "
                + "(CAST(COUNT(CAST(IFF(FZ.E IS NULL, SYSTEM$NULL_TO_FIXED(null), FZ.ID) AS FLOAT)) AS FLOAT))) - "
                + "(((((SUM((CAST(IFF(FZ.E IS NULL, SYSTEM$NULL_TO_FIXED(null), FZ.ID) AS FLOAT)) * (CAST(IFF(FZ.ID IS "
                + "NULL, SYSTEM$NULL_TO_FIXED(null), FZ.E) AS FLOAT)))) - (((SUM(CAST(IFF(FZ.E IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), FZ.ID) AS FLOAT))) * (SUM(CAST(IFF(FZ.ID IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), FZ.E) AS FLOAT)))) / (CAST(NULLIF(COUNT(CAST(IFF(FZ.E IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), FZ.ID) AS FLOAT)), 0) AS FLOAT)))) / (CAST(NULLIF(COUNT(CAST(IFF(FZ.E "
                + "IS NULL, SYSTEM$NULL_TO_FIXED(null), FZ.ID) AS FLOAT)), 0) AS FLOAT))) / "
                + "(NULLIF((IFF((((CAST(COUNT(CAST(IFF(FZ.ID IS NULL, SYSTEM$NULL_TO_FIXED(null), FZ.E) AS FLOAT)) AS "
                + "FLOAT)) * (SUM((CAST(IFF(FZ.ID IS NULL, SYSTEM$NULL_TO_FIXED(null), FZ.E) AS FLOAT)) * "
                + "(CAST(IFF(FZ.ID IS NULL, SYSTEM$NULL_TO_FIXED(null), FZ.E) AS FLOAT))))) - ((SUM(CAST(IFF(FZ.ID IS "
                + "NULL, SYSTEM$NULL_TO_FIXED(null), FZ.E) AS FLOAT))) * (SUM(CAST(IFF(FZ.ID IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), FZ.E) AS FLOAT))))) < (CAST(0 AS FLOAT)), CAST(0 AS FLOAT), "
                + "((CAST(COUNT(CAST(IFF(FZ.ID IS NULL, SYSTEM$NULL_TO_FIXED(null), FZ.E) AS FLOAT)) AS FLOAT)) * "
                + "(SUM((CAST(IFF(FZ.ID IS NULL, SYSTEM$NULL_TO_FIXED(null), FZ.E) AS FLOAT)) * (CAST(IFF(FZ.ID IS "
                + "NULL, SYSTEM$NULL_TO_FIXED(null), FZ.E) AS FLOAT))))) - ((SUM(CAST(IFF(FZ.ID IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), FZ.E) AS FLOAT))) * (SUM(CAST(IFF(FZ.ID IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), FZ.E) AS FLOAT)))))) / (CAST(NULLIF((COUNT(CAST(IFF(FZ.ID IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), FZ.E) AS FLOAT))) * (COUNT(CAST(IFF(FZ.ID IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), FZ.E) AS FLOAT))), 0) AS FLOAT)), CAST(0 AS FLOAT)))) * "
                + "((SUM(CAST(IFF(FZ.ID IS NULL, SYSTEM$NULL_TO_FIXED(null), FZ.E) AS FLOAT))) / "
                + "(CAST(COUNT(CAST(IFF(FZ.ID IS NULL, SYSTEM$NULL_TO_FIXED(null), FZ.E) AS FLOAT)) AS FLOAT))))] can "
                + "only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT REGR_R2(fz.f, fz.s) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[IFF(((IFF((((CAST(COUNT(FZ.S) AS FLOAT)) * (SUM((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S AS FLOAT))))) - "
                + "((SUM(CAST(FZ.S AS FLOAT))) * (SUM(CAST(FZ.S AS FLOAT))))) < (CAST(0 AS FLOAT)), CAST(0 AS FLOAT), "
                + "((CAST(COUNT(FZ.S) AS FLOAT)) * (SUM((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S AS FLOAT))))) - "
                + "((SUM(CAST(FZ.S AS FLOAT))) * (SUM(CAST(FZ.S AS FLOAT)))))) / (CAST(NULLIF((COUNT(FZ.S)) * "
                + "(COUNT(FZ.S)), 0) AS FLOAT))) = (CAST(0 AS FLOAT)), SYSTEM$NULL_TO_REAL(null), "
                + "IFF((((IFF((((CAST(COUNT(FZ.F) AS FLOAT)) * (SUM(FZ.F * FZ.F))) - ((SUM(FZ.F)) * (SUM(FZ.F)))) < "
                + "(CAST(0 AS FLOAT)), CAST(0 AS FLOAT), ((CAST(COUNT(FZ.F) AS FLOAT)) * (SUM(FZ.F * FZ.F))) - "
                + "((SUM(FZ.F)) * (SUM(FZ.F))))) / (CAST(NULLIF((COUNT(FZ.F)) * (COUNT(FZ.F)), 0) AS FLOAT))) = "
                + "(CAST(0 AS FLOAT))) AND (((IFF((((CAST(COUNT(FZ.S) AS FLOAT)) * (SUM((CAST(FZ.S AS FLOAT)) * "
                + "(CAST(FZ.S AS FLOAT))))) - ((SUM(CAST(FZ.S AS FLOAT))) * (SUM(CAST(FZ.S AS FLOAT))))) < (CAST(0 AS "
                + "FLOAT)), CAST(0 AS FLOAT), ((CAST(COUNT(FZ.S) AS FLOAT)) * (SUM((CAST(FZ.S AS FLOAT)) * (CAST(FZ.S "
                + "AS FLOAT))))) - ((SUM(CAST(FZ.S AS FLOAT))) * (SUM(CAST(FZ.S AS FLOAT)))))) / "
                + "(CAST(NULLIF((COUNT(FZ.S)) * (COUNT(FZ.S)), 0) AS FLOAT))) <> (CAST(0 AS FLOAT))), CAST(1 AS "
                + "FLOAT), SQUARE((((SUM((CAST(IFF(FZ.F IS NULL, SYSTEM$NULL_TO_TEXT(null), FZ.S) AS FLOAT)) * "
                + "(IFF(FZ.S IS NULL, SYSTEM$NULL_TO_REAL(null), FZ.F)))) - (((SUM(CAST(IFF(FZ.F IS NULL, "
                + "SYSTEM$NULL_TO_TEXT(null), FZ.S) AS FLOAT))) * (SUM(IFF(FZ.S IS NULL, SYSTEM$NULL_TO_REAL(null), "
                + "FZ.F)))) / (CAST(NULLIF(COUNT(CAST(IFF(FZ.F IS NULL, SYSTEM$NULL_TO_TEXT(null), FZ.S) AS FLOAT)), "
                + "0) AS FLOAT)))) / (CAST(NULLIF(COUNT(CAST(IFF(FZ.F IS NULL, SYSTEM$NULL_TO_TEXT(null), FZ.S) AS "
                + "FLOAT)), 0) AS FLOAT))) / (NULLIF((SQRT((IFF((((CAST(COUNT(CAST(IFF(FZ.F IS NULL, "
                + "SYSTEM$NULL_TO_TEXT(null), FZ.S) AS FLOAT)) AS FLOAT)) * (SUM((CAST(IFF(FZ.F IS NULL, "
                + "SYSTEM$NULL_TO_TEXT(null), FZ.S) AS FLOAT)) * (CAST(IFF(FZ.F IS NULL, SYSTEM$NULL_TO_TEXT(null), "
                + "FZ.S) AS FLOAT))))) - ((SUM(CAST(IFF(FZ.F IS NULL, SYSTEM$NULL_TO_TEXT(null), FZ.S) AS FLOAT))) * "
                + "(SUM(CAST(IFF(FZ.F IS NULL, SYSTEM$NULL_TO_TEXT(null), FZ.S) AS FLOAT))))) < (CAST(0 AS FLOAT)), "
                + "CAST(0 AS FLOAT), ((CAST(COUNT(CAST(IFF(FZ.F IS NULL, SYSTEM$NULL_TO_TEXT(null), FZ.S) AS FLOAT)) "
                + "AS FLOAT)) * (SUM((CAST(IFF(FZ.F IS NULL, SYSTEM$NULL_TO_TEXT(null), FZ.S) AS FLOAT)) * "
                + "(CAST(IFF(FZ.F IS NULL, SYSTEM$NULL_TO_TEXT(null), FZ.S) AS FLOAT))))) - ((SUM(CAST(IFF(FZ.F IS "
                + "NULL, SYSTEM$NULL_TO_TEXT(null), FZ.S) AS FLOAT))) * (SUM(CAST(IFF(FZ.F IS NULL, "
                + "SYSTEM$NULL_TO_TEXT(null), FZ.S) AS FLOAT)))))) / (CAST(NULLIF((COUNT(CAST(IFF(FZ.F IS NULL, "
                + "SYSTEM$NULL_TO_TEXT(null), FZ.S) AS FLOAT))) * (COUNT(CAST(IFF(FZ.F IS NULL, "
                + "SYSTEM$NULL_TO_TEXT(null), FZ.S) AS FLOAT))), 0) AS FLOAT)))) * (SQRT((IFF((((CAST(COUNT(IFF(FZ.S "
                + "IS NULL, SYSTEM$NULL_TO_REAL(null), FZ.F)) AS FLOAT)) * (SUM((IFF(FZ.S IS NULL, "
                + "SYSTEM$NULL_TO_REAL(null), FZ.F)) * (IFF(FZ.S IS NULL, SYSTEM$NULL_TO_REAL(null), FZ.F))))) - "
                + "((SUM(IFF(FZ.S IS NULL, SYSTEM$NULL_TO_REAL(null), FZ.F))) * (SUM(IFF(FZ.S IS NULL, "
                + "SYSTEM$NULL_TO_REAL(null), FZ.F))))) < (CAST(0 AS FLOAT)), CAST(0 AS FLOAT), ((CAST(COUNT(IFF(FZ.S "
                + "IS NULL, SYSTEM$NULL_TO_REAL(null), FZ.F)) AS FLOAT)) * (SUM((IFF(FZ.S IS NULL, "
                + "SYSTEM$NULL_TO_REAL(null), FZ.F)) * (IFF(FZ.S IS NULL, SYSTEM$NULL_TO_REAL(null), FZ.F))))) - "
                + "((SUM(IFF(FZ.S IS NULL, SYSTEM$NULL_TO_REAL(null), FZ.F))) * (SUM(IFF(FZ.S IS NULL, "
                + "SYSTEM$NULL_TO_REAL(null), FZ.F)))))) / (CAST(NULLIF((COUNT(IFF(FZ.S IS NULL, "
                + "SYSTEM$NULL_TO_REAL(null), FZ.F))) * (COUNT(IFF(FZ.S IS NULL, SYSTEM$NULL_TO_REAL(null), FZ.F))), "
                + "0) AS FLOAT)))), CAST(0 AS FLOAT))))))] can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT REGR_SXY(fz.va, fz.id) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [(SUM((CAST(FZ.ID "
                + "AS FLOAT)) * (CAST(FZ.VA AS FLOAT)))) - (((SUM(CAST(IFF(FZ.VA IS NULL, SYSTEM$NULL_TO_FIXED(null), "
                + "FZ.ID) AS FLOAT))) * (SUM(CAST(IFF(FZ.ID IS NULL, SYSTEM$NULL_TO_VARIANT(null), FZ.VA) AS FLOAT)))) "
                + "/ (CAST(COUNT(CAST(IFF(FZ.VA IS NULL, SYSTEM$NULL_TO_FIXED(null), FZ.ID) AS FLOAT)) AS FLOAT)))] "
                + "can only appear in having or select clause"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT REGR_SYY(fz.s, fz.f) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[(SUM(SQUARE(CAST(IFF(FZ.F IS NULL, SYSTEM$NULL_TO_TEXT(null), FZ.S) AS FLOAT)))) - "
                + "((SQUARE(SUM(CAST(IFF(FZ.F IS NULL, SYSTEM$NULL_TO_TEXT(null), FZ.S) AS FLOAT)))) / "
                + "(CAST(COUNT(IFF(FZ.S IS NULL, SYSTEM$NULL_TO_REAL(null), FZ.F)) AS FLOAT)))] can only appear in "
                + "having or select clause"},
        });
    }
}
