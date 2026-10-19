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
 * How the aggregate of a query around a subquery is named where the subquery may not stand. The refusal
 * echoes the call as the plan computes it, so with the rewrites the plan has made by then: an AVG is the sum
 * over the count at the width its argument gives it - {@code SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), COUNT(FZ.ID))}
 * over an INTEGER, a cast SUM over the COUNT for a narrow NUMBER, a FLOAT division for a FLOAT or a text - a
 * MEDIAN is its 0.5 percentile over the argument cast three decimals wider, a COUNT_IF the SUM of an IFF. A
 * condition counted by keeps its predicates as they are and casts only the values beside them: a BOOLEAN
 * column alone is {@code CAST(FZ.B AS BOOLEAN)}, {@code b OR n > 5} is {@code (CAST(FZ.B AS BOOLEAN)) OR
 * (FZ.N > 5)}. A distinct count and an approximate percentile are named by both of their steps, the boolean
 * aggregates by the extreme or the sum they are. An argument of a type the call does not take is refused for
 * that, at the call, before the subquery's place is judged. Every answer is live-verified.
 */
public class CorrelatedAggregatePlanEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void anAverageIsTheSumOverTheCount() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR, f FLOAT, d NUMBER(10,2), dt DATE, va "
                + "VARIANT, n5 NUMBER(5,0), n38 NUMBER(38,10), ts TIMESTAMP_NTZ, n20 NUMBER(20,0), n21 NUMBER(21,0), "
                + "n109 NUMBER(10,9), n3837 NUMBER(38,37), n242 NUMBER(24,2), n232 NUMBER(23,2))");
        answer(
            "INSERT INTO fz SELECT 5, TRUE, 'a', 1.5, 2.25, '2020-01-01'::DATE, PARSE_JSON('1'), 5, 1.5, "
                + "'2020-01-01'::TIMESTAMP_NTZ, 5, 5, 1.5, 1.5, 1.5, 1.5");
        answer(
            "INSERT INTO fz SELECT 7, FALSE, 'b', 2.5, 3.25, '2020-01-02'::DATE, PARSE_JSON('2'), 7, 2.5, "
                + "'2020-01-02'::TIMESTAMP_NTZ, 7, 7, 2.5, 2.5, 2.5, 2.5");
        answer(
            "CREATE OR REPLACE TABLE g (id INT, v INT, s VARCHAR)");
        answer(
            "INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), COUNT(FZ.ID))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [(SUM(FZ.F)) / "
                + "(CAST(COUNT(FZ.F) AS FLOAT))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(fz.f) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [(CAST(SUM(FZ.D) "
                + "AS NUMBER(28,8))) / (COUNT(FZ.D))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(fz.d) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [(SUM(CAST(FZ.S "
                + "AS FLOAT))) / (CAST(COUNT(FZ.S) AS FLOAT))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(fz.s) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [(SUM(CAST(FZ.VA "
                + "AS FLOAT))) / (CAST(COUNT(FZ.VA) AS FLOAT))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(fz.va) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [(CAST(SUM(FZ.N5) "
                + "AS NUMBER(23,6))) / (COUNT(FZ.N5))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(fz.n5) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(SUM(FZ.N38), COUNT(FZ.N38))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(fz.n38) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 39 | Invalid argument types for function "
                + "'SUM': (BOOLEAN)",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [AVG(DISTINCT "
                + "CAST(FZ.ID AS NUMBER(38,3)))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(DISTINCT fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID + 1), COUNT(FZ.ID + 1))] can only appear in having or select "
                + "clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(fz.id + 1) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), COUNT(FZ.ID))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(fz.id) + 1 FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [(SUM(CAST(FZ.ID "
                + "AS FLOAT))) / (CAST(COUNT(CAST(FZ.ID AS FLOAT)) AS FLOAT))] can only appear in having or select "
                + "clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(fz.id::FLOAT) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(SUM(SCALED_ROUND_INT_DIVIDE(FZ.ID, 2)), "
                + "COUNT(SCALED_ROUND_INT_DIVIDE(FZ.ID, 2)))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(fz.id / 2) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [(CAST(SUM(FZ.D * "
                + "2) AS NUMBER(29,8))) / (COUNT(FZ.D * 2))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(fz.d * 2) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), COUNT(FZ.ID))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT avg( fz.id ) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), COUNT(FZ.ID))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(ALL fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(SUM((CAST(FZ.ID AS NUMBER(38,2))) + FZ.D), COUNT((CAST(FZ.ID AS "
                + "NUMBER(38,2))) + FZ.D))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(fz.id + fz.d) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(SUM(NEGATE(FZ.ID)), COUNT(NEGATE(FZ.ID)))] can only appear in having or "
                + "select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(-fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(SUM(ABS(FZ.ID)), COUNT(ABS(FZ.ID)))] can only appear in having or select "
                + "clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(ABS(fz.id)) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 39 | Invalid argument types for function "
                + "'SUM': (DATE)",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(fz.dt) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [AVG(DISTINCT "
                + "CAST(FZ.D AS NUMBER(25,5)))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(DISTINCT fz.d) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [AVG(DISTINCT "
                + "FZ.F)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(DISTINCT fz.f) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [AVG(DISTINCT "
                + "CAST(FZ.N5 AS NUMBER(20,3)))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(DISTINCT fz.n5) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [AVG(DISTINCT "
                + "CAST(FZ.N38 AS NUMBER(38,13)))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(DISTINCT fz.n38) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [AVG(DISTINCT "
                + "CAST(DISTINCT FZ.S AS FLOAT))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(DISTINCT fz.s) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [AVG(DISTINCT "
                + "CAST(DISTINCT FZ.VA AS FLOAT))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(DISTINCT fz.va) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [AVG(DISTINCT "
                + "CAST(FZ.N20 AS NUMBER(35,3)))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(DISTINCT fz.n20) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [AVG(DISTINCT "
                + "CAST(FZ.N21 AS NUMBER(36,3)))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(DISTINCT fz.n21) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [AVG(DISTINCT "
                + "CAST(FZ.N109 AS NUMBER(25,12)))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(DISTINCT fz.n109) FROM g)");
        answer(
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(DISTINCT fz.n3837) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [AVG(DISTINCT "
                + "CAST(FZ.N242 AS NUMBER(38,5)))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(DISTINCT fz.n242) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [AVG(DISTINCT "
                + "CAST(FZ.N232 AS NUMBER(38,5)))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(DISTINCT fz.n232) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [AVG(DISTINCT "
                + "CAST(FZ.ID + 1 AS NUMBER(38,3)))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(DISTINCT fz.id + 1) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 39 | Invalid argument types for function "
                + "'AVG': (BOOLEAN)",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(DISTINCT fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[(CAST(SUM(FZ.N20) AS NUMBER(38,6))) / (COUNT(FZ.N20))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(fz.n20) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(SUM(FZ.N21), COUNT(FZ.N21))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(fz.n21) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[(CAST(SUM(FZ.N109) AS NUMBER(25,12))) / (COUNT(FZ.N109))] can only appear in having or select "
                + "clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(fz.n109) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[(CAST(SUM(FZ.N3837) AS NUMBER(38,37))) / (COUNT(FZ.N3837))] can only appear in having or select "
                + "clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(fz.n3837) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(SUM(FZ.N242), COUNT(FZ.N242))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(fz.n242) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(SUM(FZ.N232), COUNT(FZ.N232))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(fz.n232) FROM g)");
    }

    @Test
    public void aMedianIsItsPercentile() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR, f FLOAT, d NUMBER(10,2), dt DATE, va "
                + "VARIANT, n5 NUMBER(5,0), n38 NUMBER(38,10), ts TIMESTAMP_NTZ, n20 NUMBER(20,0), n21 NUMBER(21,0), "
                + "n109 NUMBER(10,9), n3837 NUMBER(38,37), n242 NUMBER(24,2), n232 NUMBER(23,2))");
        answer(
            "INSERT INTO fz SELECT 5, TRUE, 'a', 1.5, 2.25, '2020-01-01'::DATE, PARSE_JSON('1'), 5, 1.5, "
                + "'2020-01-01'::TIMESTAMP_NTZ, 5, 5, 1.5, 1.5, 1.5, 1.5");
        answer(
            "INSERT INTO fz SELECT 7, FALSE, 'b', 2.5, 3.25, '2020-01-02'::DATE, PARSE_JSON('2'), 7, 2.5, "
                + "'2020-01-02'::TIMESTAMP_NTZ, 7, 7, 2.5, 2.5, 2.5, 2.5");
        answer(
            "CREATE OR REPLACE TABLE g (id INT, v INT, s VARCHAR)");
        answer(
            "INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[PERCENTILE_CONT(CAST(FZ.ID AS NUMBER(38,3)), 0.5)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT MEDIAN(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[PERCENTILE_CONT(FZ.F, 0.5)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT MEDIAN(fz.f) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[PERCENTILE_CONT(CAST(FZ.D AS NUMBER(13,5)), 0.5)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT MEDIAN(fz.d) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[PERCENTILE_CONT(CAST(TO_NUMBER(FZ.S, 9, 0) AS NUMBER(12,3)), 0.5)] can only appear in having or "
                + "select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT MEDIAN(fz.s) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[PERCENTILE_CONT(CAST(FZ.N5 AS NUMBER(8,3)), 0.5)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT MEDIAN(fz.n5) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[PERCENTILE_CONT(CAST(FZ.N38 AS NUMBER(38,13)), 0.5)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT MEDIAN(fz.n38) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[PERCENTILE_CONT(CAST(FZ.ID + 1 AS NUMBER(38,3)), 0.5)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT MEDIAN(fz.id + 1) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[PERCENTILE_CONT(CAST(TO_NUMBER(FZ.VA, 9, 0) AS NUMBER(12,3)), 0.5)] can only appear in having or "
                + "select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT MEDIAN(fz.va) FROM g)");
        answer(
            "SELECT id FROM fz WHERE EXISTS (SELECT MEDIAN(fz.dt) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[PERCENTILE_CONT(CAST(CAST(FZ.B AS NUMBER(9,0)) AS NUMBER(12,3)), 0.5)] can only appear in having "
                + "or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT MEDIAN(fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[PERCENTILE_CONT(CAST(FZ.ID AS NUMBER(38,3)), 0.5)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT median(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[PERCENTILE_CONT(CAST(FZ.ID AS NUMBER(38,3)), 0.5)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[PERCENTILE_DISC(FZ.ID, 0.5)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[PERCENTILE_CONT(FZ.F, 0.25)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY fz.f DESC) FROM "
                + "g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[PERCENTILE_CONT(CAST(FZ.D AS NUMBER(13,5)), 0.5)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY fz.d) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[PERCENTILE_CONT(CAST(FZ.N20 AS NUMBER(23,3)), 0.5)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT MEDIAN(fz.n20) FROM g)");
        answer(
            "SELECT id FROM fz WHERE EXISTS (SELECT MEDIAN(fz.n3837) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[PERCENTILE_CONT(CAST(FZ.N109 AS NUMBER(13,12)), 0.5)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT MEDIAN(fz.n109) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'MEDIAN(DISTINCT FZ.ID)'",
            "SELECT id FROM fz WHERE EXISTS (SELECT MEDIAN(DISTINCT fz.id) FROM g)");
        answer(
            "SELECT id FROM fz WHERE EXISTS (SELECT MEDIAN(fz.ts) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[PERCENTILE_CONT(CAST(CAST(FZ.B AS NUMBER(9,0)) AS NUMBER(12,3)), 0.5)] can only appear in having "
                + "or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[PERCENTILE_DISC(TO_NUMBER(FZ.S, 9, 0), 0.5)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY fz.s) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[PERCENTILE_DISC(CAST(FZ.B AS NUMBER(9,0)), 0.5)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[PERCENTILE_DISC(FZ.D, 0.5)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY fz.d DESC) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[PERCENTILE_CONT(CAST(TO_NUMBER(FZ.S, 9, 0) AS NUMBER(12,3)), 0.5)] can only appear in having or "
                + "select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY fz.s) FROM g)");
        answer(
            "SELECT id FROM fz WHERE EXISTS (SELECT PERCENTILE_CONT(fz.f) WITHIN GROUP (ORDER BY fz.id) FROM g)");
    }

    @Test
    public void aCountedConditionKeepsItsPredicates() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR, f FLOAT, d NUMBER(10,2), dt DATE, va "
                + "VARIANT, n5 NUMBER(5,0), n38 NUMBER(38,10), ts TIMESTAMP_NTZ, n20 NUMBER(20,0), n21 NUMBER(21,0), "
                + "n109 NUMBER(10,9), n3837 NUMBER(38,37), n242 NUMBER(24,2), n232 NUMBER(23,2))");
        answer(
            "INSERT INTO fz SELECT 5, TRUE, 'a', 1.5, 2.25, '2020-01-01'::DATE, PARSE_JSON('1'), 5, 1.5, "
                + "'2020-01-01'::TIMESTAMP_NTZ, 5, 5, 1.5, 1.5, 1.5, 1.5");
        answer(
            "INSERT INTO fz SELECT 7, FALSE, 'b', 2.5, 3.25, '2020-01-02'::DATE, PARSE_JSON('2'), 7, 2.5, "
                + "'2020-01-02'::TIMESTAMP_NTZ, 7, 7, 2.5, 2.5, 2.5, 2.5");
        answer(
            "CREATE OR REPLACE TABLE g (id INT, v INT, s VARCHAR)");
        answer(
            "INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF(CAST(FZ.B AS BOOLEAN), 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [SUM(IFF(FZ.ID > "
                + "5, 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.id > 5) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 39 | Invalid argument types for function "
                + "'IFF': (NUMBER(38,0), NUMBER(1,0), NUMBER(1,0))",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF(CAST(FZ.B AND FZ.B AS BOOLEAN), 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.b AND fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF(CAST(NOT(FZ.B) AS BOOLEAN), 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(NOT fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [SUM(IFF(FZ.S = "
                + "'a', 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.s = 'a') FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [SUM(IFF(FZ.ID IS "
                + "NULL, 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.id IS NULL) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 39 | Invalid argument types for function "
                + "'IFF': (VARCHAR(16777216), NUMBER(1,0), NUMBER(1,0))",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.s) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 39 | Invalid argument types for function "
                + "'IFF': (VARIANT, NUMBER(1,0), NUMBER(1,0))",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.va) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF((CAST(FZ.B AS BOOLEAN)) OR (FZ.ID > 5), 1, 0))] can only appear in having or select "
                + "clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.b OR fz.id > 5) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [SUM(IFF(FZ.ID IN "
                + "(5, 6), 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.id IN (5, 6)) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [SUM(IFF((FZ.ID "
                + ">= 1) AND (FZ.ID <= 6), 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.id BETWEEN 1 AND 6) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [SUM(IFF(FZ.S "
                + "LIKE 'a%', 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.s LIKE 'a%') FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 39 | Invalid argument types for function "
                + "'IFF': (FLOAT, NUMBER(1,0), NUMBER(1,0))",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.f) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF((CAST(FZ.B AS BOOLEAN)) AND (FZ.ID > 5), 1, 0))] can only appear in having or select "
                + "clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.b AND fz.id > 5) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [SUM(IFF((FZ.ID > "
                + "5) AND (CAST(FZ.B AS BOOLEAN)), 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.id > 5 AND fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF(CAST(FZ.B OR TRUE AS BOOLEAN), 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.b OR TRUE) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF(NOT(FZ.ID > 5), 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(NOT (fz.id > 5)) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF((CAST(FZ.B AND FZ.B AS BOOLEAN)) OR (FZ.ID > 5), 1, 0))] can only appear in having or "
                + "select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF((fz.b AND fz.b) OR fz.id > 5) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [SUM(IFF(FZ.B = "
                + "TRUE, 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.b = TRUE) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [SUM(IFF(FZ.B IS "
                + "NOT NULL, 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.b IS NOT NULL) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF(STARTSWITH(FZ.S, 'a'), 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(STARTSWITH(fz.s, 'a')) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF(CAST(CAST(FZ.VA AS BOOLEAN) AS BOOLEAN), 1, 0))] can only appear in having or select "
                + "clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.va::BOOLEAN) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF(CAST(CASE_FLATTENED(FZ.ID > 5, TRUE, SYSTEM$NULL_TO_BOOLEAN(null)) AS BOOLEAN), 1, 0))] "
                + "can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(CASE WHEN fz.id > 5 THEN TRUE END) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF(CAST(IFF(CAST(FZ.B AS BOOLEAN), TRUE, FALSE) AS BOOLEAN), 1, 0))] can only appear in "
                + "having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(IFF(fz.b, TRUE, FALSE)) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF(CAST((NOT(FZ.B)) AND (NOT(FZ.B)) AS BOOLEAN), 1, 0))] can only appear in having or "
                + "select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(NOT fz.b AND NOT fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF(EQUAL_NULL(FZ.ID, 5), 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(EQUAL_NULL(fz.id, 5)) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF(NOT(EQUAL_NULL(FZ.ID, 5)), 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.id IS DISTINCT FROM 5) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF(NOT((CAST(FZ.B AS BOOLEAN)) AND (FZ.ID > 5)), 1, 0))] can only appear in having or "
                + "select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(NOT (fz.b AND fz.id > 5)) FROM g)");
        answer(
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(NOT (NOT fz.b)) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF((CAST(FZ.B AS BOOLEAN)) AND (CAST(FZ.B AS BOOLEAN)) AND (FZ.ID > 5), 1, 0))] can only "
                + "appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.b AND fz.b AND fz.id > 5) FROM g)");
        answer(
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.b OR fz.b OR fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF(CAST(FZ.B AS BOOLEAN), 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF((fz.b)) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [SUM(IFF((FZ.ID > "
                + "5) OR (FZ.ID < 3), 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.id > 5 OR fz.id < 3) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF((CAST(FZ.B AS BOOLEAN)) AND ((CAST(FZ.B AS BOOLEAN)) OR (FZ.ID > 5)), 1, 0))] can only "
                + "appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.b AND (fz.b OR fz.id > 5)) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF(CAST(CAST(FZ.S AS BOOLEAN) AS BOOLEAN), 1, 0))] can only appear in having or select "
                + "clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(TO_BOOLEAN(fz.s)) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF(CAST(FZ.B AND TRUE AS BOOLEAN), 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.b AND TRUE) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF(CAST(TRUE AND FZ.B AS BOOLEAN), 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(TRUE AND fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [SUM(IFF(FZ.B <> "
                + "FALSE, 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.b <> FALSE) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF(NOT(FZ.ID IN (5, 6)), 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.id NOT IN (5, 6)) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [SUM(IFF(NOT(FZ.S "
                + "LIKE 'a%'), 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.s NOT LIKE 'a%') FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF(NOT((FZ.ID >= 1) AND (FZ.ID <= 6)), 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.id NOT BETWEEN 1 AND 6) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF((CONTAINS(FZ.S, 'a')) AND (CAST(FZ.B AS BOOLEAN)), 1, 0))] can only appear in having or "
                + "select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(CONTAINS(fz.s, 'a') AND fz.b) FROM g)");
        answer(
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(BOOLAND(fz.b, fz.b)) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'COUNT_IF(DISTINCT FZ.B)'",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(DISTINCT fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 39 | too many arguments for function "
                + "[COUNT_IF(FZ.B, FZ.B)] expected 1, got 2",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.b, fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF(CAST((SYSTEM$NULL_TO_BOOLEAN(null)) AND FZ.B AS BOOLEAN), 1, 0))] can only appear in "
                + "having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(NULL AND fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [SUM(IFF((FZ.B IS "
                + "NULL) OR (CAST(FZ.B AS BOOLEAN)), 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.b IS NULL OR fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF(CAST(NVL(FZ.B, FALSE) AS BOOLEAN), 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(NVL(fz.b, FALSE)) FROM g)");
        answer(
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.id > 5 = TRUE) FROM g)");
    }

    @Test
    public void everyOtherAggregateAsThePlanHoldsIt() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR, f FLOAT, d NUMBER(10,2), dt DATE, va "
                + "VARIANT, n5 NUMBER(5,0), n38 NUMBER(38,10), ts TIMESTAMP_NTZ, n20 NUMBER(20,0), n21 NUMBER(21,0), "
                + "n109 NUMBER(10,9), n3837 NUMBER(38,37), n242 NUMBER(24,2), n232 NUMBER(23,2))");
        answer(
            "INSERT INTO fz SELECT 5, TRUE, 'a', 1.5, 2.25, '2020-01-01'::DATE, PARSE_JSON('1'), 5, 1.5, "
                + "'2020-01-01'::TIMESTAMP_NTZ, 5, 5, 1.5, 1.5, 1.5, 1.5");
        answer(
            "INSERT INTO fz SELECT 7, FALSE, 'b', 2.5, 3.25, '2020-01-02'::DATE, PARSE_JSON('2'), 7, 2.5, "
                + "'2020-01-02'::TIMESTAMP_NTZ, 7, 7, 2.5, 2.5, 2.5, 2.5");
        answer(
            "CREATE OR REPLACE TABLE g (id INT, v INT, s VARCHAR)");
        answer(
            "INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [SUM(FZ.ID)] can "
                + "only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT SUM(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [SUM(FZ.F)] can "
                + "only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT SUM(fz.f) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [SUM(CAST(FZ.S AS "
                + "FLOAT))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT SUM(fz.s) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [SUM(DISTINCT "
                + "FZ.ID)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT SUM(DISTINCT fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [SUM(FZ.D)] can "
                + "only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT SUM(fz.d) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [SUM(CAST(FZ.VA "
                + "AS FLOAT))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT SUM(fz.va) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 39 | Invalid argument types for function "
                + "'SUM': (BOOLEAN)",
            "SELECT id FROM fz WHERE EXISTS (SELECT SUM(fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [COUNT(FZ.ID)] "
                + "can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [COUNT(DISTINCT "
                + "FZ.ID)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT(DISTINCT fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [COUNT(FZ.ID, "
                + "FZ.B)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT(fz.id, fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [COUNT(DISTINCT "
                + "FZ.ID, FZ.B)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT(DISTINCT fz.id, fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'FZ' does not exist or not authorized.",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT(fz.*) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MIN(FZ.S)] can "
                + "only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT MIN(fz.s) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MAX(FZ.S)] can "
                + "only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT MAX(fz.s) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MIN(FZ.DT)] can "
                + "only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT MIN(fz.dt) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[ANY_VALUE(FZ.ID)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT ANY_VALUE(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MODE(FZ.ID)] can "
                + "only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT MODE(fz.id) FROM g)");
        answer(
            "SELECT id FROM fz WHERE EXISTS (SELECT KURTOSIS(fz.id) FROM g)");
        answer(
            "SELECT id FROM fz WHERE EXISTS (SELECT SKEW(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [LISTAGG(FZ.S)] "
                + "can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT LISTAGG(fz.s) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [LISTAGG(FZ.S, ',"
                + "')] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT LISTAGG(fz.s, ',') FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [LISTAGG(DISTINCT "
                + "FZ.S, ',')] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT LISTAGG(DISTINCT fz.s, ',') WITHIN GROUP (ORDER BY fz.s) FROM "
                + "g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[ARRAY_AGG(FZ.ID)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT ARRAY_AGG(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[ARRAY_AGG(DISTINCT FZ.ID)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT ARRAY_AGG(DISTINCT fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[ARRAY_AGG(FZ.ID)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT ARRAY_AGG(fz.id) WITHIN GROUP (ORDER BY fz.id DESC) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[ARRAY_UNIQUE_AGG(FZ.ID)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT ARRAY_UNIQUE_AGG(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [OBJECT_AGG(FZ.S, "
                + "FZ.VA)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT OBJECT_AGG(fz.s, fz.va) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [ARRAYAGG(FZ.ID)] "
                + "can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT ARRAYAGG(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MIN(FZ.B)] can "
                + "only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT BOOLAND_AGG(fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MAX(FZ.B)] can "
                + "only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT BOOLOR_AGG(fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [(SUM(IFF((FZ.B) "
                + "= TRUE, 1, IFF(FZ.B IS NULL, SYSTEM$NULL_TO_FIXED(null), 0)))) = 1] can only appear in having or "
                + "select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT BOOLXOR_AGG(fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MIN(CAST(FZ.ID "
                + "<> 0 AS BOOLEAN))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT BOOLAND_AGG(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[BITAND_AGG(FZ.ID)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT BITAND_AGG(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[BITOR_AGG(FZ.ID)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT BITOR_AGG(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[BITXOR_AGG(FZ.ID)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT BITXOR_AGG(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[BIT_AND_AGG(FZ.ID)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT BIT_AND_AGG(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[HLL_ESTIMATE(HLL_ACCUMULATE(FZ.ID))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT APPROX_COUNT_DISTINCT(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[HLL_ESTIMATE(HLL_ACCUMULATE(FZ.ID))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT HLL(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[APPROX_PERCENTILE_ESTIMATE(APPROX_PERCENTILE_ACCUMULATE(CAST(FZ.ID AS FLOAT)), CAST(0.5 AS "
                + "FLOAT))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT APPROX_PERCENTILE(fz.id, 0.5) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[APPROX_TOP_K(FZ.ID)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT APPROX_TOP_K(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[APPROX_TOP_K(FZ.ID, 2)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT APPROX_TOP_K(fz.id, 2) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[HLL_ACCUMULATE(FZ.ID)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT HLL_ACCUMULATE(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MAX_BY(FZ.ID, "
                + "FZ.F)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT MAX_BY(fz.id, fz.f) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MIN_BY(FZ.ID, "
                + "FZ.F)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT MIN_BY(fz.id, fz.f) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MAX_BY(FZ.ID, "
                + "FZ.F, 2)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT MAX_BY(fz.id, fz.f, 2) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [HASH_AGG(FZ.ID)] "
                + "can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT HASH_AGG(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'FZ' does not exist or not authorized.",
            "SELECT id FROM fz WHERE EXISTS (SELECT HASH_AGG(fz.*) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), COUNT(FZ.ID))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT ZEROIFNULL(AVG(fz.id)) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MAX(FZ.ID)] can "
                + "only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT MAX(fz.id), AVG(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), COUNT(FZ.ID))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(fz.id), MAX(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [SUM(FZ.ID)] can "
                + "only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT SUM(fz.id) / COUNT(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[PERCENTILE_CONT(CAST(FZ.ID AS NUMBER(38,3)), 0.5)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT NVL(MEDIAN(fz.id), 0) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF(CAST(FZ.B AS BOOLEAN), 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.b) + COUNT(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [COUNT(FZ.ID)] "
                + "can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT(fz.id) + COUNT_IF(fz.b) FROM g)");
        answer(
            "SELECT id FROM fz WHERE EXISTS (SELECT MINHASH(5, fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | GROUPING function without a GROUP BY.",
            "SELECT id FROM fz WHERE EXISTS (SELECT GROUPING(fz.id) FROM g)");
        assertAnswer(
            "5 ;; 7",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT(*) FROM g)");
        assertAnswer(
            "5 ;; 7",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(g.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 24",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(fz.id + g.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Window function [SUM(FZ.ID) OVER ()] contains a correlation.",
            "SELECT id FROM fz WHERE EXISTS (SELECT SUM(fz.id) OVER () FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Window function [AVG(FZ.ID) OVER ()] contains a correlation.",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(fz.id) OVER () FROM g)");
        answer(
            "SELECT id FROM fz WHERE EXISTS (SELECT ST_COLLECT(TO_GEOGRAPHY(fz.s)) FROM g)");
        answer(
            "SELECT id FROM fz WHERE EXISTS (SELECT VECTOR_AVG(fz.va) FROM g)");
        assertAnswer(
            "5 ;; 7",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT(1) FROM g)");
        assertAnswer(
            "5 ;; 7",
            "SELECT id FROM fz WHERE EXISTS (SELECT SUM(1) FROM g)");
        assertAnswer(
            "5 ;; 7",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(1) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected '('.",
            "SELECT id FROM fz WHERE EXISTS (SELECT MAX(fz.id) FILTER (WHERE fz.b) FROM g)");
    }

    @Test
    public void theApproximateAndTheBooleanAggregates() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR, f FLOAT, d NUMBER(10,2), dt DATE, va "
                + "VARIANT, n5 NUMBER(5,0), n38 NUMBER(38,10), ts TIMESTAMP_NTZ, n20 NUMBER(20,0), n21 NUMBER(21,0), "
                + "n109 NUMBER(10,9), n3837 NUMBER(38,37), n242 NUMBER(24,2), n232 NUMBER(23,2))");
        answer(
            "INSERT INTO fz SELECT 5, TRUE, 'a', 1.5, 2.25, '2020-01-01'::DATE, PARSE_JSON('1'), 5, 1.5, "
                + "'2020-01-01'::TIMESTAMP_NTZ, 5, 5, 1.5, 1.5, 1.5, 1.5");
        answer(
            "INSERT INTO fz SELECT 7, FALSE, 'b', 2.5, 3.25, '2020-01-02'::DATE, PARSE_JSON('2'), 7, 2.5, "
                + "'2020-01-02'::TIMESTAMP_NTZ, 7, 7, 2.5, 2.5, 2.5, 2.5");
        answer(
            "CREATE OR REPLACE TABLE g (id INT, v INT, s VARCHAR)");
        answer(
            "INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[APPROX_PERCENTILE_ESTIMATE(APPROX_PERCENTILE_ACCUMULATE(FZ.F), CAST(0.5 AS FLOAT))] can only "
                + "appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT APPROX_PERCENTILE(fz.f, 0.5) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[APPROX_PERCENTILE_ESTIMATE(APPROX_PERCENTILE_ACCUMULATE(CAST(FZ.D AS FLOAT)), CAST(0.25 AS "
                + "FLOAT))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT APPROX_PERCENTILE(fz.d, 0.25) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[APPROX_PERCENTILE_ESTIMATE(APPROX_PERCENTILE_ACCUMULATE(CAST(FZ.S AS FLOAT)), CAST(0.5 AS "
                + "FLOAT))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT APPROX_PERCENTILE(fz.s, 0.5) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[APPROX_PERCENTILE_ESTIMATE(APPROX_PERCENTILE_ACCUMULATE(CAST(FZ.ID AS FLOAT)), CAST(1 AS "
                + "FLOAT))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT APPROX_PERCENTILE(fz.id, 1) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [CAST(0.5 AS FLOAT)] for function 'APPROX_PERCENTILE',"
                + " parameter 2: Percentile must be a constant numberic value between 0 and 1 inclusive.",
            "SELECT id FROM fz WHERE EXISTS (SELECT APPROX_PERCENTILE(fz.id, 0.5::FLOAT) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[HLL_ESTIMATE(HLL_ACCUMULATE(FZ.S))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT HLL(fz.s) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [HLL(FZ.ID, "
                + "FZ.S)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT HLL(fz.id, fz.s) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[APPROX_COUNT_DISTINCT(FZ.ID, FZ.S)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT APPROX_COUNT_DISTINCT(fz.id, fz.s) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[HLL_ESTIMATE(HLL_ACCUMULATE(FZ.ID))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT APPROX_COUNT_DISTINCT(DISTINCT fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[HLL_ESTIMATE(HLL_ACCUMULATE(FZ.ID))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT HLL(DISTINCT fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'FZ' does not exist or not authorized.",
            "SELECT id FROM fz WHERE EXISTS (SELECT APPROX_COUNT_DISTINCT(fz.*) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[HLL_COMBINE(HLL_ACCUMULATE(FZ.ID))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT HLL_COMBINE(HLL_ACCUMULATE(fz.id)) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[APPROX_TOP_K(FZ.S, 3, 10)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT APPROX_TOP_K(fz.s, 3, 10) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[APPROX_PERCENTILE_ACCUMULATE(CAST(FZ.ID AS FLOAT))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT APPROX_PERCENTILE_ACCUMULATE(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [COUNT(DISTINCT "
                + "FZ.S)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT(DISTINCT fz.s) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[ARRAY_UNION_AGG(CAST(FZ.VA AS ARRAY))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT ARRAY_UNION_AGG(fz.va) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[(SUM(IFF((CAST(FZ.ID <> 0 AS BOOLEAN)) = TRUE, 1, IFF(FZ.ID IS NULL, SYSTEM$NULL_TO_FIXED(null), "
                + "0)))) = 1] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT BOOLXOR_AGG(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MAX(CAST(FZ.ID "
                + "<> 0 AS BOOLEAN))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT BOOLOR_AGG(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MAX(CAST(FZ.S AS "
                + "BOOLEAN))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT BOOLOR_AGG(fz.s) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MIN(CAST(FZ.D <> "
                + "(CAST(0 AS NUMBER(10,2))) AS BOOLEAN))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT BOOLAND_AGG(fz.d) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MIN(CAST(FZ.ID > "
                + "5 AS BOOLEAN))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT BOOLAND_AGG(fz.id > 5) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[(SUM(IFF((CAST(FZ.ID > 5 AS BOOLEAN)) = TRUE, 1, IFF(CAST(FZ.ID > 5 AS BOOLEAN) IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), 0)))) = 1] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT BOOLXOR_AGG(fz.id > 5) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[(SUM(IFF((CAST(FZ.S AS BOOLEAN)) = TRUE, 1, IFF(FZ.S IS NULL, SYSTEM$NULL_TO_FIXED(null), 0)))) "
                + "= 1] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT BOOLXOR_AGG(fz.s) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[(SUM(IFF((CAST(FZ.D <> (CAST(0 AS NUMBER(10,2))) AS BOOLEAN)) = TRUE, 1, IFF(FZ.D IS NULL, "
                + "SYSTEM$NULL_TO_FIXED(null), 0)))) = 1] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT BOOLXOR_AGG(fz.d) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_BOOLEAN(FZ.F)] for parameter 'TO_BOOLEAN'",
            "SELECT id FROM fz WHERE EXISTS (SELECT BOOLAND_AGG(fz.f) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MAX(CAST(FZ.VA "
                + "AS BOOLEAN))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT BOOLOR_AGG(fz.va) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MAX(NOT(FZ.B))] "
                + "can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT BOOLOR_AGG(NOT fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MIN(FZ.B AND "
                + "FZ.B)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT BOOLAND_AGG(fz.b AND fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'BOOLAND_AGG(DISTINCT FZ.B)'",
            "SELECT id FROM fz WHERE EXISTS (SELECT BOOLAND_AGG(DISTINCT fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MIN(CAST(FZ.ID > "
                + "5 AS BOOLEAN))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT MIN(fz.id > 5) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MAX(FZ.B AND "
                + "FZ.B)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT MAX(fz.b AND fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MAX(NOT(FZ.B))] "
                + "can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT MAX(NOT fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MAX(CAST(FZ.ID "
                + "IS NULL AS BOOLEAN))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT MAX(fz.id IS NULL) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[MIN(CAST((CAST(FZ.B AS BOOLEAN)) OR (FZ.ID > 5) AS BOOLEAN))] can only appear in having or "
                + "select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT MIN(fz.b OR fz.id > 5) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[BITAND_AGG(CAST(FZ.D AS NUMBER(8,0)))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT BITAND_AGG(fz.d) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[BITOR_AGG(CAST(FZ.F AS NUMBER(18,0)))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT BITOR_AGG(fz.f) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[BITXOR_AGG(TO_NUMBER(FZ.S, 18, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT BITXOR_AGG(fz.s) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[BITOR_AGG(FZ.ID)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT BITOR_AGG(DISTINCT fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[BITXOR_AGG(DISTINCT CAST(DISTINCT FZ.D AS NUMBER(8,0)))] can only appear in having or select "
                + "clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT BITXOR_AGG(DISTINCT fz.d) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[LISTAGG(CAST(FZ.ID AS VARCHAR(134217728)), ',')] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT LISTAGG(fz.id, ',') FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [LISTAGG(DISTINCT "
                + "CAST(DISTINCT FZ.ID AS VARCHAR(134217728)), ',')] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT LISTAGG(DISTINCT fz.id, ',') FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[LISTAGG(CAST(FZ.B AS VARCHAR(134217728)))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT LISTAGG(fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[LISTAGG(CAST(FZ.DT AS VARCHAR(134217728)))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT LISTAGG(fz.dt) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[LISTAGG(CAST(FZ.VA AS VARCHAR(134217728)))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT LISTAGG(fz.va) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [OBJECT_AGG(FZ.S, "
                + "CAST(FZ.ID AS VARIANT))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT OBJECT_AGG(fz.s, fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[OBJECT_AGG(CAST(FZ.ID AS VARCHAR(134217728)), FZ.VA)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT OBJECT_AGG(fz.id, fz.va) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [OBJECT_AGG(FZ.S, "
                + "CAST(FZ.B AS VARIANT))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT OBJECT_AGG(fz.s, fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [SUM(DISTINCT "
                + "CAST(DISTINCT FZ.S AS FLOAT))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT SUM(DISTINCT fz.s) FROM g)");
    }

    @Test
    public void aTypeTheCallDoesNotTakeIsRefusedFirst() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR, f FLOAT, d NUMBER(10,2), dt DATE, va "
                + "VARIANT, n5 NUMBER(5,0), n38 NUMBER(38,10), ts TIMESTAMP_NTZ, n20 NUMBER(20,0), n21 NUMBER(21,0), "
                + "n109 NUMBER(10,9), n3837 NUMBER(38,37), n242 NUMBER(24,2), n232 NUMBER(23,2))");
        answer(
            "INSERT INTO fz SELECT 5, TRUE, 'a', 1.5, 2.25, '2020-01-01'::DATE, PARSE_JSON('1'), 5, 1.5, "
                + "'2020-01-01'::TIMESTAMP_NTZ, 5, 5, 1.5, 1.5, 1.5, 1.5");
        answer(
            "INSERT INTO fz SELECT 7, FALSE, 'b', 2.5, 3.25, '2020-01-02'::DATE, PARSE_JSON('2'), 7, 2.5, "
                + "'2020-01-02'::TIMESTAMP_NTZ, 7, 7, 2.5, 2.5, 2.5, 2.5");
        answer(
            "CREATE OR REPLACE TABLE g (id INT, v INT, s VARCHAR)");
        answer(
            "INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 51 | Invalid argument types for function "
                + "'SUM': (BOOLEAN)",
            "SELECT id FROM fz WHERE EXISTS (SELECT MAX(fz.id), AVG(fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 39 | Invalid argument types for function "
                + "'SUM': (BOOLEAN)",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(fz.b), MAX(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 51 | Invalid argument types for function "
                + "'IFF': (NUMBER(38,0), NUMBER(1,0), NUMBER(1,0))",
            "SELECT id FROM fz WHERE EXISTS (SELECT MAX(fz.id), COUNT_IF(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 51 | Invalid argument types for function "
                + "'SUM': (BOOLEAN)",
            "SELECT id FROM fz WHERE EXISTS (SELECT MAX(fz.id), SUM(fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 39 | Invalid argument types for function "
                + "'IFF': (NUMBER(38,0), NUMBER(1,0), NUMBER(1,0))",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(fz.id), AVG(fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 43 | invalid identifier 'NOSUCH'",
            "SELECT id FROM fz WHERE EXISTS (SELECT MAX(nosuch), AVG(fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 54 | invalid identifier 'NOSUCH'",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(fz.b), MAX(nosuch) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 39 | Invalid argument types for function "
                + "'*': (BOOLEAN, BOOLEAN)",
            "SELECT id FROM fz WHERE EXISTS (SELECT STDDEV(fz.b) FROM g)");
        answer(
            "SELECT id FROM fz WHERE EXISTS (SELECT VARIANCE(fz.s) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 39 | Invalid argument types for function "
                + "'*': (DATE, DATE)",
            "SELECT id FROM fz WHERE EXISTS (SELECT VAR_POP(fz.dt) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 39 | Invalid argument types for function "
                + "'*': (BOOLEAN, BOOLEAN)",
            "SELECT id FROM fz WHERE EXISTS (SELECT KURTOSIS(fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_DOUBLE(IFF(FZ.ID IS NULL, "
                + "SYSTEM$NULL_TO_BOOLEAN(NULL), FZ.B))] for parameter 'TO_DOUBLE'",
            "SELECT id FROM fz WHERE EXISTS (SELECT CORR(fz.b, fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 39 | Invalid argument types for function "
                + "'SUM': (DATE)",
            "SELECT id FROM fz WHERE EXISTS (SELECT SUM(fz.dt) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 39 | Invalid argument types for function "
                + "'SUM': (TIMESTAMP_NTZ(9))",
            "SELECT id FROM fz WHERE EXISTS (SELECT SUM(fz.ts) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 39 | Invalid argument types for function "
                + "'SUM': (TIMESTAMP_NTZ(9))",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(fz.ts) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MAX(FZ.VA)] can "
                + "only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT MAX(fz.va) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MIN(FZ.B)] can "
                + "only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT MIN(fz.b) FROM g)");
        answer(
            "SELECT id FROM fz WHERE EXISTS (SELECT BITAND_AGG(fz.b) FROM g)");
        answer(
            "SELECT id FROM fz WHERE EXISTS (SELECT BITAND_AGG(fz.dt) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MODE(FZ.B)] can "
                + "only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT MODE(fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[ANY_VALUE(FZ.VA)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT ANY_VALUE(fz.va) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MAX_BY(FZ.B, "
                + "FZ.S)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT MAX_BY(fz.b, fz.s) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [ARRAY_AGG(FZ.B)] "
                + "can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT ARRAY_AGG(fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [HASH_AGG(FZ.B, "
                + "FZ.S)] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT HASH_AGG(fz.b, fz.s) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SUM': (BOOLEAN)",
            "SELECT AVG(b) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IFF': (NUMBER(38,0), NUMBER(1,0), NUMBER(1,0))",
            "SELECT COUNT_IF(id) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SUM': (BOOLEAN)",
            "SELECT SUM(b) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | incompatible types: [DATE] and [NUMBER(9,0)]",
            "SELECT MEDIAN(dt) FROM fz");
        answer(
            "SELECT VECTOR_AVG(va) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SUM': (DATE)",
            "SELECT AVG(dt) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IFF': (VARCHAR(16777216), NUMBER(1,0), NUMBER(1,0))",
            "SELECT COUNT_IF(s) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IFF': (FLOAT, NUMBER(1,0), NUMBER(1,0))",
            "SELECT COUNT_IF(f) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IFF': (VARIANT, NUMBER(1,0), NUMBER(1,0))",
            "SELECT COUNT_IF(va) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SUM': (DATE)",
            "SELECT SUM(dt) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function '*': "
                + "(BOOLEAN, BOOLEAN)",
            "SELECT STDDEV(b) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SUM': (TIMESTAMP_NTZ(9))",
            "SELECT AVG(ts) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 10 | Invalid argument types for function "
                + "'SUM': (BOOLEAN)",
            "SELECT 1, AVG(b) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 37 | Invalid argument types for function "
                + "'SUM': (BOOLEAN)",
            "SELECT id FROM fz WHERE id > (SELECT AVG(b) FROM fz)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 61 | invalid identifier 'NOSUCH'",
            "SELECT id FROM fz WHERE EXISTS (SELECT AVG(fz.b) FROM g) AND nosuch = 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 24 | invalid identifier 'NOSUCH'",
            "SELECT id FROM fz WHERE nosuch = 1 AND EXISTS (SELECT AVG(fz.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 75 | Invalid argument types for function "
                + "'SUM': (BOOLEAN)",
            "SELECT id FROM fz WHERE EXISTS (SELECT MAX(fz.id) FROM g) AND id > (SELECT AVG(b) FROM fz)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AVG': (BOOLEAN)",
            "SELECT AVG(b) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 37 | Invalid argument types for function "
                + "'SUM': (BOOLEAN)",
            "SELECT id FROM fz GROUP BY id HAVING AVG(b) > 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 | Invalid argument types for function "
                + "'SUM': (BOOLEAN)",
            "SELECT id, (SELECT AVG(fz.b) FROM g) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 | Invalid argument types for function "
                + "'IFF': (NUMBER(38,0), NUMBER(1,0), NUMBER(1,0))",
            "SELECT id, (SELECT COUNT_IF(fz.id) FROM g) FROM fz");
    }

    @Test
    public void whereverTheSubqueryStands() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR, f FLOAT, d NUMBER(10,2), dt DATE, va "
                + "VARIANT, n5 NUMBER(5,0), n38 NUMBER(38,10), ts TIMESTAMP_NTZ, n20 NUMBER(20,0), n21 NUMBER(21,0), "
                + "n109 NUMBER(10,9), n3837 NUMBER(38,37), n242 NUMBER(24,2), n232 NUMBER(23,2))");
        answer(
            "INSERT INTO fz SELECT 5, TRUE, 'a', 1.5, 2.25, '2020-01-01'::DATE, PARSE_JSON('1'), 5, 1.5, "
                + "'2020-01-01'::TIMESTAMP_NTZ, 5, 5, 1.5, 1.5, 1.5, 1.5");
        answer(
            "INSERT INTO fz SELECT 7, FALSE, 'b', 2.5, 3.25, '2020-01-02'::DATE, PARSE_JSON('2'), 7, 2.5, "
                + "'2020-01-02'::TIMESTAMP_NTZ, 7, 7, 2.5, 2.5, 2.5, 2.5");
        answer(
            "CREATE OR REPLACE TABLE g (id INT, v INT, s VARCHAR)");
        answer(
            "INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(SUM(F.ID), COUNT(F.ID))] can only appear in having or select clause",
            "SELECT id FROM fz f WHERE EXISTS (SELECT AVG(f.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[PERCENTILE_CONT(CAST(F.ID AS NUMBER(38,3)), 0.5)] can only appear in having or select clause",
            "SELECT id FROM fz AS f WHERE EXISTS (SELECT MEDIAN(f.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [SUM(IFF(CAST(F.B "
                + "AS BOOLEAN), 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz f WHERE EXISTS (SELECT COUNT_IF(f.b) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'ID'",
            "SELECT id FROM (SELECT id AS id2 FROM fz) q WHERE EXISTS (SELECT AVG(id2) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), COUNT(FZ.ID))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE id IN (SELECT AVG(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), COUNT(FZ.ID))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE (SELECT AVG(fz.id) FROM g) > 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), COUNT(FZ.ID))] can only appear in having or select clause",
            "SELECT id FROM fz ORDER BY (SELECT AVG(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[PERCENTILE_CONT(CAST(FZ.ID AS NUMBER(38,3)), 0.5)] can only appear in having or select clause",
            "SELECT COUNT(*) FROM fz GROUP BY (SELECT MEDIAN(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'ID'",
            "SELECT id FROM fz JOIN g ON EXISTS (SELECT COUNT_IF(fz.b))");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), COUNT(FZ.ID))] can only appear in having or select clause",
            "UPDATE fz SET id = 1 WHERE EXISTS (SELECT AVG(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[PERCENTILE_CONT(CAST(FZ.ID AS NUMBER(38,3)), 0.5)] can only appear in having or select clause",
            "DELETE FROM fz WHERE EXISTS (SELECT MEDIAN(fz.id) FROM g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), COUNT(FZ.ID))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE EXISTS (SELECT AVG(fz.id)))");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), COUNT(FZ.ID))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g HAVING AVG(fz.id) > 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SUM(IFF(CAST(FZ.B AS BOOLEAN), 1, 0))] can only appear in having or select clause",
            "SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE g.id > 1 ORDER BY COUNT_IF(fz.b))");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), COUNT(FZ.ID))] can only appear in having or select clause",
            "SELECT id, (SELECT AVG(fz.id) FROM g) FROM fz ORDER BY 2");
        assertAnswer(
            "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID), COUNT(FZ.ID))] can only appear in having or select clause",
            "SELECT id, (SELECT AVG(fz.id) FROM g) AS e FROM fz ORDER BY e");
    }
}
