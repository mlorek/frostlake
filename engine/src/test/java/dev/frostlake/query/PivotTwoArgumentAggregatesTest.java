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
 * A PIVOT's aggregate that takes two arguments is refused in the call the pivot is rewritten into, once both
 * columns are found (live-verified): {@code not enough arguments for function [CORR(IFF('x' = FL2.S, FL2.N,
 * SYSTEM$NULL_TO_FIXED(null)))], expected 2, got 1}, the relation named as the FROM item names it, the first pivot
 * value met in the FOR column's type, the null typed as the aggregated column. PERCENTILE_CONT's argument is no
 * constant. FIRST_VALUE, LAST_VALUE, NTH_VALUE, LAG and LEAD are syntax errors at the call's parenthesis and at
 * the FOR column, the other window functions are no recognized aggregate, and a FOR column that is not there is
 * refused where it is written.
 */
public class PivotTwoArgumentAggregatesTest extends MeasuredCellsTestSupport {

    @Test
    public void twoArgumentAggregatesAndWindowWords() {
        engine.execute("CREATE OR REPLACE TABLE flags (student VARCHAR, subject VARCHAR, ok BOOLEAN, score INT, arr "
            + "ARRAY, d DATE, f FLOAT)");
        engine.execute("INSERT INTO flags SELECT 'ann', 'Math', TRUE, 90, ARRAY_CONSTRUCT(1), '2024-01-01'::DATE, 1.5");
        engine.execute("INSERT INTO flags SELECT 'bob', 'Art', FALSE, 70, ARRAY_CONSTRUCT(2), '2024-01-02'::DATE, 2.5");
        assertCells(new String[][] {
            {"SELECT * FROM flags PIVOT (OBJECT_AGG(student) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[OBJECT_AGG(IFF('Math' = FLAGS.SUBJECT, FLAGS.STUDENT, SYSTEM$NULL_TO_TEXT(null)))], expected 2, "
                + "got 1"},
            {"SELECT * FROM flags PIVOT (OBJECT_AGG(score) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[OBJECT_AGG(IFF('Math' = FLAGS.SUBJECT, FLAGS.SCORE, SYSTEM$NULL_TO_FIXED(null)))], expected 2, "
                + "got 1"},
            {"SELECT * FROM flags PIVOT (OBJECT_AGG(ok) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[OBJECT_AGG(IFF('Math' = FLAGS.SUBJECT, FLAGS.OK, SYSTEM$NULL_TO_BOOLEAN(null)))], expected 2, "
                + "got 1"},
            {"SELECT * FROM flags PIVOT (OBJECT_AGG(arr) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[OBJECT_AGG(IFF('Math' = FLAGS.SUBJECT, FLAGS.ARR, SYSTEM$NULL_TO_ARRAY(null)))], expected 2, got "
                + "1"},
            {"SELECT * FROM flags PIVOT (OBJECT_AGG(d) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[OBJECT_AGG(IFF('Math' = FLAGS.SUBJECT, FLAGS.D, SYSTEM$NULL_TO_DATE(null)))], expected 2, got 1"},
            {"SELECT * FROM flags PIVOT (OBJECT_AGG(f) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[OBJECT_AGG(IFF('Math' = FLAGS.SUBJECT, FLAGS.F, SYSTEM$NULL_TO_REAL(null)))], expected 2, got 1"},
            {"SELECT * FROM flags fl PIVOT (OBJECT_AGG(student) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[OBJECT_AGG(IFF('Math' = FL.SUBJECT, FL.STUDENT, SYSTEM$NULL_TO_TEXT(null)))], expected 2, got 1"},
            {"SELECT * FROM (SELECT * FROM flags) PIVOT (OBJECT_AGG(student) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[OBJECT_AGG(IFF('Math' = \"values\".SUBJECT, \"values\".STUDENT, SYSTEM$NULL_TO_TEXT(null)))], "
                + "expected 2, got 1"},
            {"SELECT * FROM flags PIVOT (OBJECT_AGG(student) FOR subject IN ('Art', 'Math'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[OBJECT_AGG(IFF('Art' = FLAGS.SUBJECT, FLAGS.STUDENT, SYSTEM$NULL_TO_TEXT(null)))], expected 2, "
                + "got 1"},
            {"SELECT * FROM flags PIVOT (OBJECT_AGG(student) FOR subject IN (ANY))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[OBJECT_AGG(null)], expected 2, got 1"},
            {"SELECT * FROM flags PIVOT (OBJECT_AGG(student) FOR subject IN (1, 2))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[OBJECT_AGG(IFF((CAST(1 AS NUMBER(18,5))) = (TO_NUMBER(FLAGS.SUBJECT, 18, 5)), FLAGS.STUDENT, "
                + "SYSTEM$NULL_TO_TEXT(null)))], expected 2, got 1"},
            {"SELECT * FROM test_schema.flags PIVOT (OBJECT_AGG(student) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[OBJECT_AGG(IFF('Math' = FLAGS.SUBJECT, FLAGS.STUDENT, SYSTEM$NULL_TO_TEXT(null)))], expected 2, "
                + "got 1"},
            {"SELECT * FROM flags PIVOT (CORR(score) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF('Math' = FLAGS.SUBJECT, FLAGS.SCORE, SYSTEM$NULL_TO_FIXED(null)))], expected 2, got 1"},
            {"SELECT * FROM flags PIVOT (COVAR_POP(score) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[COVAR_POP(IFF('Math' = FLAGS.SUBJECT, FLAGS.SCORE, SYSTEM$NULL_TO_FIXED(null)))], expected 2, "
                + "got 1"},
            {"SELECT * FROM flags PIVOT (REGR_SLOPE(score) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[REGR_SLOPE(IFF('Math' = FLAGS.SUBJECT, FLAGS.SCORE, SYSTEM$NULL_TO_FIXED(null)))], expected 2, "
                + "got 1"},
            {"SELECT * FROM flags PIVOT (APPROX_PERCENTILE(score) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[APPROX_PERCENTILE(IFF('Math' = FLAGS.SUBJECT, FLAGS.SCORE, SYSTEM$NULL_TO_FIXED(null)))], "
                + "expected 2, got 1"},
            {"SELECT * FROM flags PIVOT (ARRAY_AGG(student) FOR subject IN ('Math', 'Art'))",
                "true|90|[1]|2024-01-01|1.5|[\"ann\"]|[] ;; false|70|[2]|2024-01-02|2.5|[]|[\"bob\"]"},
            {"SELECT * FROM flags PIVOT (MEDIAN(score) FOR subject IN ('Math', 'Art'))",
                "ann|true|[1]|2024-01-01|1.5|90.000|null ;; bob|false|[2]|2024-01-02|2.5|null|70.000"},
            {"SELECT * FROM flags PIVOT (COUNT_IF(ok) FOR subject IN ('Math', 'Art'))",
                "ann|90|[1]|2024-01-01|1.5|1|0 ;; bob|70|[2]|2024-01-02|2.5|0|0"},
            {"SELECT * FROM flags PIVOT (BITAND_AGG(score) FOR subject IN ('Math', 'Art'))",
                "ann|true|[1]|2024-01-01|1.5|90|null ;; bob|false|[2]|2024-01-02|2.5|null|70"},
            {"SELECT * FROM flags PIVOT (FIRST_VALUE(score) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '('. | syntax error "
                + "line 1 at position 50 unexpected 'subject'."},
            {"SELECT * FROM flags PIVOT (LAST_VALUE(score) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '('. | syntax error "
                + "line 1 at position 49 unexpected 'subject'."},
            {"SELECT * FROM flags PIVOT (NTH_VALUE(score) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '('. | syntax error "
                + "line 1 at position 48 unexpected 'subject'."},
            {"SELECT * FROM flags PIVOT (LAG(score) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '('. | syntax error "
                + "line 1 at position 42 unexpected 'subject'."},
            {"SELECT * FROM flags PIVOT (LEAD(score) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '('. | syntax error "
                + "line 1 at position 43 unexpected 'subject'."},
            {"SELECT * FROM flags PIVOT (ROW_NUMBER(score) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | 'ROW_NUMBER' is not a recognized aggregate function"},
            {"SELECT * FROM flags PIVOT (RANK(score) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | 'RANK' is not a recognized aggregate function"},
            {"SELECT * FROM flags PIVOT (DENSE_RANK(score) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | 'DENSE_RANK' is not a recognized aggregate function"},
            {"SELECT * FROM flags PIVOT (NTILE(score) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | 'NTILE' is not a recognized aggregate function"},
            {"SELECT * FROM flags PIVOT (CUME_DIST(score) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | 'CUME_DIST' is not a recognized aggregate function"},
            {"SELECT * FROM flags PIVOT (PERCENT_RANK(score) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | 'PERCENT_RANK' is not a recognized aggregate function"},
            {"SELECT * FROM flags PIVOT (RATIO_TO_REPORT(score) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | 'RATIO_TO_REPORT' is not a recognized aggregate function"},
            {"SELECT * FROM flags PIVOT (CONDITIONAL_TRUE_EVENT(ok) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | 'CONDITIONAL_TRUE_EVENT' is not a recognized aggregate function"},
            {"SELECT * FROM flags PIVOT (IFF(score) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | 'IFF' is not a recognized aggregate function"},
            {"SELECT * FROM flags PIVOT (COALESCE(score) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | 'COALESCE' is not a recognized aggregate function"},
            {"SELECT * FROM flags PIVOT (LEFT(student) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | 'LEFT' is not a recognized aggregate function"},
            {"SELECT * FROM flags PIVOT (RIGHT(student) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | 'RIGHT' is not a recognized aggregate function"},
            {"SELECT * FROM flags PIVOT (REPLACE(student) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | 'REPLACE' is not a recognized aggregate function"},
        });
    }

    @Test
    public void theRewriteByTypeAndRelation() {
        engine.execute("CREATE OR REPLACE TABLE fl2 (s VARCHAR, n INT, dc NUMBER(10,2), f FLOAT, b BOOLEAN, d DATE, "
            + "tm TIME, tn TIMESTAMP_NTZ, tl TIMESTAMP_LTZ, tz TIMESTAMP_TZ, bi BINARY, v VARIANT, o OBJECT, a "
            + "ARRAY, v5 VARCHAR(5), \"lc\" INT)");
        engine.execute("INSERT INTO fl2 (s, n) VALUES ('x', 1)");
        engine.execute("CREATE OR REPLACE TABLE \"lt2\" (s VARCHAR, n INT)");
        assertCells(new String[][] {
            {"SELECT * FROM fl2 PIVOT (CORR(dc) FOR s IN ('x'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF('x' = FL2.S, FL2.DC, SYSTEM$NULL_TO_FIXED(null)))], expected 2, got 1"},
            {"SELECT * FROM fl2 PIVOT (CORR(f) FOR s IN ('x'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF('x' = FL2.S, FL2.F, SYSTEM$NULL_TO_REAL(null)))], expected 2, got 1"},
            {"SELECT * FROM fl2 PIVOT (CORR(b) FOR s IN ('x'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF('x' = FL2.S, FL2.B, SYSTEM$NULL_TO_BOOLEAN(null)))], expected 2, got 1"},
            {"SELECT * FROM fl2 PIVOT (CORR(d) FOR s IN ('x'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF('x' = FL2.S, FL2.D, SYSTEM$NULL_TO_DATE(null)))], expected 2, got 1"},
            {"SELECT * FROM fl2 PIVOT (CORR(tm) FOR s IN ('x'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF('x' = FL2.S, FL2.TM, SYSTEM$NULL_TO_TIME(null)))], expected 2, got 1"},
            {"SELECT * FROM fl2 PIVOT (CORR(tn) FOR s IN ('x'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF('x' = FL2.S, FL2.TN, SYSTEM$NULL_TO_TIMESTAMP_NTZ(null)))], expected 2, got 1"},
            {"SELECT * FROM fl2 PIVOT (CORR(tl) FOR s IN ('x'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF('x' = FL2.S, FL2.TL, SYSTEM$NULL_TO_TIMESTAMP_LTZ(null)))], expected 2, got 1"},
            {"SELECT * FROM fl2 PIVOT (CORR(tz) FOR s IN ('x'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF('x' = FL2.S, FL2.TZ, SYSTEM$NULL_TO_TIMESTAMP_TZ(null)))], expected 2, got 1"},
            {"SELECT * FROM fl2 PIVOT (CORR(bi) FOR s IN ('x'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF('x' = FL2.S, FL2.BI, SYSTEM$NULL_TO_BINARY(null)))], expected 2, got 1"},
            {"SELECT * FROM fl2 PIVOT (CORR(v) FOR s IN ('x'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF('x' = FL2.S, FL2.V, SYSTEM$NULL_TO_VARIANT(null)))], expected 2, got 1"},
            {"SELECT * FROM fl2 PIVOT (CORR(o) FOR s IN ('x'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF('x' = FL2.S, FL2.O, SYSTEM$NULL_TO_OBJECT(null)))], expected 2, got 1"},
            {"SELECT * FROM fl2 PIVOT (CORR(a) FOR s IN ('x'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF('x' = FL2.S, FL2.A, SYSTEM$NULL_TO_ARRAY(null)))], expected 2, got 1"},
            {"SELECT * FROM fl2 PIVOT (CORR(v5) FOR s IN ('x'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF('x' = FL2.S, FL2.V5, SYSTEM$NULL_TO_TEXT(null)))], expected 2, got 1"},
            {"SELECT * FROM fl2 PIVOT (CORR(\"lc\") FOR s IN ('x'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF('x' = FL2.S, FL2.\"lc\", SYSTEM$NULL_TO_FIXED(null)))], expected 2, got 1"},
            {"SELECT * FROM fl2 PIVOT (CORR(n) FOR n IN (1, 2))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF(1 = FL2.N, FL2.N, SYSTEM$NULL_TO_FIXED(null)))], expected 2, got 1"},
            {"SELECT * FROM fl2 PIVOT (CORR(n) FOR n IN ('1', '2'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF((TO_NUMBER('1')) = FL2.N, FL2.N, SYSTEM$NULL_TO_FIXED(null)))], expected 2, got 1"},
            {"SELECT * FROM fl2 PIVOT (CORR(n) FOR n IN (1.5))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF(1.5 = (CAST(FL2.N AS NUMBER(38,1))), FL2.N, SYSTEM$NULL_TO_FIXED(null)))], expected 2, "
                + "got 1"},
            {"SELECT * FROM fl2 PIVOT (CORR(n) FOR dc IN (1))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF((CAST(1 AS NUMBER(10,2))) = FL2.DC, FL2.N, SYSTEM$NULL_TO_FIXED(null)))], expected 2, "
                + "got 1"},
            {"SELECT * FROM fl2 PIVOT (CORR(n) FOR d IN ('2024-01-01'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF((CAST('2024-01-01' AS DATE)) = FL2.D, FL2.N, SYSTEM$NULL_TO_FIXED(null)))], expected 2, "
                + "got 1"},
            {"SELECT * FROM fl2 PIVOT (CORR(n) FOR b IN (TRUE))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF(TRUE = FL2.B, FL2.N, SYSTEM$NULL_TO_FIXED(null)))], expected 2, got 1"},
            {"SELECT * FROM fl2 PIVOT (CORR(n) FOR s IN (NULL))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF(EQUAL_NULL(SYSTEM$NULL_TO_TEXT(null), FL2.S), FL2.N, SYSTEM$NULL_TO_FIXED(null)))], "
                + "expected 2, got 1"},
            {"SELECT * FROM fl2 PIVOT (CORR(n) FOR s IN ('x' AS ax))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF('x' = FL2.S, FL2.N, SYSTEM$NULL_TO_FIXED(null)))], expected 2, got 1"},
            {"SELECT * FROM fl2 PIVOT (CORR(n) FOR s IN (SELECT 'x'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(null)], expected 2, got 1"},
            {"SELECT * FROM fl2 PIVOT (CORR(n) FOR s IN ('x', 'y'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF('x' = FL2.S, FL2.N, SYSTEM$NULL_TO_FIXED(null)))], expected 2, got 1"},
            {"SELECT * FROM fl2 PIVOT (CORR(n) FOR s IN (1.5))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF((CAST(1.5 AS NUMBER(18,5))) = (TO_NUMBER(FL2.S, 18, 5)), FL2.N, "
                + "SYSTEM$NULL_TO_FIXED(null)))], expected 2, got 1"},
            {"SELECT * FROM fl2 PIVOT (CORR(n) FOR f IN (1))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF((CAST(1 AS FLOAT)) = FL2.F, FL2.N, SYSTEM$NULL_TO_FIXED(null)))], expected 2, got 1"},
            {"WITH c AS (SELECT * FROM fl2) SELECT * FROM c PIVOT (CORR(n) FOR s IN ('x'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF('x' = C.S, C.N, SYSTEM$NULL_TO_FIXED(null)))], expected 2, got 1"},
            {"SELECT * FROM \"lt2\" PIVOT (CORR(n) FOR s IN ('x'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF('x' = \"lt2\".S, \"lt2\".N, SYSTEM$NULL_TO_FIXED(null)))], expected 2, got 1"},
            {"SELECT * FROM fl2 AS \"q\" PIVOT (CORR(n) FOR s IN ('x'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF('x' = \"q\".S, \"q\".N, SYSTEM$NULL_TO_FIXED(null)))], expected 2, got 1"},
            {"SELECT * FROM fl2 PIVOT (CORR(n) FOR s IN ('x')) AS p",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF('x' = FL2.S, FL2.N, SYSTEM$NULL_TO_FIXED(null)))], expected 2, got 1"},
            {"SELECT * FROM fl2 PIVOT (CORR(n) FOR s IN ('x') DEFAULT ON NULL (0))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF('x' = FL2.S, FL2.N, SYSTEM$NULL_TO_FIXED(null)))], expected 2, got 1"},
            {"SELECT * FROM fl2 PIVOT (CORR(n) AS c FOR s IN ('x'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF('x' = FL2.S, FL2.N, SYSTEM$NULL_TO_FIXED(null)))], expected 2, got 1"},
            {"SELECT * FROM fl2 PIVOT (corr(n) FOR s IN ('x'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[CORR(IFF('x' = FL2.S, FL2.N, SYSTEM$NULL_TO_FIXED(null)))], expected 2, got 1"},
            {"SELECT * FROM fl2 PIVOT (COVAR_SAMP(n) FOR s IN ('x'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[COVAR_SAMP(IFF('x' = FL2.S, FL2.N, SYSTEM$NULL_TO_FIXED(null)))], expected 2, got 1"},
            {"SELECT * FROM fl2 PIVOT (REGR_R2(n) FOR s IN ('x'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[REGR_R2(IFF('x' = FL2.S, FL2.N, SYSTEM$NULL_TO_FIXED(null)))], expected 2, got 1"},
            {"SELECT * FROM fl2 PIVOT (REGR_COUNT(n) FOR s IN ('x'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[REGR_COUNT(IFF('x' = FL2.S, FL2.N, SYSTEM$NULL_TO_FIXED(null)))], expected 2, got 1"},
            {"SELECT * FROM fl2 PIVOT (APPROX_TOP_K(n) FOR s IN ('x'))",
                "null|null|null|null|null|null|null|null|null|null|null|null|null|null|[[1,1]]"},
            {"SELECT * FROM fl2 PIVOT (PERCENTILE_CONT(n) FOR s IN ('x'))",
                "REFUSED SQL compilation error: | argument 1 to function PERCENTILE_CONT needs to be constant, found "
                + "'IFF('x' = FL2.S, FL2.N, SYSTEM$NULL_TO_FIXED(null))'"},
            {"SELECT * FROM fl2 PIVOT (APPROX_PERCENTILE_ACCUMULATE(n) FOR s IN ('x'))",
                "null|null|null|null|null|null|null|null|null|null|null|null|null|null|"
                + "{\"state\":[1.000000000000000e+00,1.000000000000000e+00],\"type\":\"tdigest\",\"version\":1}"},
            {"SELECT * FROM fl2 PIVOT (WIDTH_BUCKET(n) FOR s IN ('x'))",
                "REFUSED SQL compilation error: | 'WIDTH_BUCKET' is not a recognized aggregate function"},
            {"SELECT * FROM fl2 PIVOT (ARRAY_UNION_AGG(a) FOR s IN ('x'))",
                "1|null|null|null|null|null|null|null|null|null|null|null|null|null|[]"},
            {"SELECT * FROM fl2 PIVOT (OBJECT_AGG(nosuch) FOR s IN ('x'))",
                "REFUSED SQL compilation error: error line 1 at position 36 | invalid identifier 'NOSUCH'"},
            {"SELECT * FROM fl2 PIVOT (CORR(n) FOR nosuch IN ('x'))",
                "REFUSED SQL compilation error: error line 1 at position 37 | invalid identifier 'NOSUCH'"},
            {"SELECT * FROM fl2 PIVOT (FIRST_VALUE(nosuch) FOR s IN ('x'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '('. | syntax error "
                + "line 1 at position 49 unexpected 's'."},
            {"SELECT * FROM fl2 PIVOT (LAG(n) FOR s IN (ANY ORDER BY s))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '('. | syntax error "
                + "line 1 at position 36 unexpected 's'."},
            {"SELECT * FROM fl2 PIVOT (first_value(n) FOR s IN ('x'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '('. | syntax error "
                + "line 1 at position 44 unexpected 's'."},
            {"SELECT * FROM fl2 PIVOT (\"FIRST_VALUE\"(n) FOR s IN ('x'))",
                "REFUSED SQL compilation error: | invalid function '\"FIRST_VALUE\"'"},
            {"SELECT * FROM fl2 PIVOT (CORR(DISTINCT n) FOR s IN ('x'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'DISTINCT'."},
        });
    }
}
