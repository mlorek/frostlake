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
 * The statistics of pairs either side of which is a DECFLOAT, and the moments of one, are DECFLOATs of 38
 * digits: the sums run in DECFLOAT arithmetic held to 38 digits at every step, the slope and CORR divide by the
 * correctly rounded VAR_POP and STDDEV_POPs, SKEW and KURTOSIS round their small-sample coefficients to twelve
 * fractional digits (and KURTOSIS's subtracted term to six), REGR_R2 is 1 where y does not vary, and a series
 * of no spread divides by zero (all live-verified).
 */
public class DecfloatStatisticsTest extends MeasuredCellsTestSupport {

    @Test
    public void pairsAndMoments() {
        engine.execute("CREATE OR REPLACE TABLE dsa (x DECFLOAT, y DECFLOAT)");
        engine.execute("INSERT INTO dsa VALUES (1, 2), (2, 3), (4, 9)");
        engine.execute("CREATE OR REPLACE TABLE dsb (x DECFLOAT, y DECFLOAT)");
        engine.execute("INSERT INTO dsb VALUES (1, 3), (2, 1), (4, 4), (8, 1)");
        engine.execute("CREATE OR REPLACE TABLE dsc (x DECFLOAT, y DECFLOAT)");
        engine.execute("INSERT INTO dsc VALUES (0.1, 0.3), (0.2, 0.7), (0.35, 0.11), (1.7, 2.9), (3.3, 1.25)");
        engine.execute("CREATE OR REPLACE TABLE dsd (x DECFLOAT, y DECFLOAT)");
        engine.execute("INSERT INTO dsd VALUES (100000000000000000000, 3), (200000000000000000000, 5), "
            + "(500000000000000000000, 4)");
        engine.execute("CREATE OR REPLACE TABLE dse (x DECFLOAT, y DECFLOAT)");
        engine.execute("INSERT INTO dse VALUES (1, NULL), (NULL, 2), (2, 3), (5, 7), (6, 1), (7, 7)");
        engine.execute("CREATE OR REPLACE TABLE dsf (x DECFLOAT, y DECFLOAT)");
        engine.execute("INSERT INTO dsf VALUES (5, 6)");
        engine.execute("CREATE OR REPLACE TABLE dsg (x DECFLOAT, y DECFLOAT)");
        engine.execute("INSERT INTO dsg VALUES (2, 1), (2, 3), (2, 5), (2, 8)");
        engine.execute("CREATE OR REPLACE TABLE dsh (x DECFLOAT, y DECFLOAT)");
        // dsh stays empty: its rows need 39 digits together, which an INSERT refuses.
        engine.execute("CREATE OR REPLACE TABLE dsi (x DECFLOAT, y DECFLOAT)");
        engine.execute("INSERT INTO dsi VALUES (1, 1), (2, 4), (3, 9), (4, 16), (100, 10000)");
        engine.execute("CREATE OR REPLACE TABLE dsj (x DECFLOAT, y DECFLOAT)");
        engine.execute("INSERT INTO dsj VALUES (1, 7), (2, 1), (3, 8), (5, 2), (8, 8), (13, 1), (21, 9), (34, 3), "
            + "(55, 5)");
        engine.execute("CREATE OR REPLACE TABLE dsk (x DECFLOAT, y DECFLOAT)");
        engine.execute("INSERT INTO dsk VALUES (0.5, 1.1), (1.25, 2.2), (2.125, 0.3), (3.0625, 4.4), (5.5, 0.05), "
            + "(7.75, 6.6), (9.875, 0.7), (11.3, 8.8), (13.7, 0.9), (17.1, 10.1), (19.9, 1.1)");
        engine.execute("CREATE OR REPLACE TABLE dsl (x DECFLOAT, y DECFLOAT)");
        engine.execute("INSERT INTO dsl VALUES (1, 2), (3, 5), (4, 4), (6, 7), (7, 1), (9, 9), (10, 3)");
        engine.execute("CREATE OR REPLACE TABLE dsm (x DECFLOAT, n NUMBER(10,2), f FLOAT, i INT)");
        engine.execute("INSERT INTO dsm VALUES (1, 2.5, 2.5, 2), (2, 3.25, 3.25, 3), (4, 9.75, 9.75, 9)");
        assertAnswer("0.97986371009719943856985017511294647251|3.7777777777777777777777777777777777777|"
                + "5.6666666666666666666666666666666666665|2.4285714285714285714285714285714285713|"
                + "-0.9999999999999999999999999999999999996|0.96013289036544850498338870431893687703|"
                + "2.3333333333333333333333333333333333333|4.6666666666666666666666666666666666667|"
                + "4.666666666666666666666666666666666667|28.666666666666666666666666666666666667|"
                + "11.333333333333333333333333333333333333|3",
            "SELECT CORR(x, y), COVAR_POP(x, y), COVAR_SAMP(x, y), REGR_SLOPE(y, x), REGR_INTERCEPT(y, x), REGR_R2(y,"
                + " x), REGR_AVGX(y, x), REGR_AVGY(y, x), REGR_SXX(y, x), REGR_SYY(y, x), REGR_SXY(y, x), "
                + "REGR_COUNT(y, x) FROM dsa");
        assertAnswer("-0.34097520463683815405674209271820286312|-1.1875|-1.5833333333333333333333333333333333333|"
                + "-0.16521739130434782608695652173913043478|2.8695652173913043478260869565217391304|"
                + "0.1162640901771336553945249597423510467|3.75|2.25|28.75|6.75|-4.75|4",
            "SELECT CORR(x, y), COVAR_POP(x, y), COVAR_SAMP(x, y), REGR_SLOPE(y, x), REGR_INTERCEPT(y, x), REGR_R2(y,"
                + " x), REGR_AVGX(y, x), REGR_AVGY(y, x), REGR_SXX(y, x), REGR_SYY(y, x), REGR_SXY(y, x), "
                + "REGR_COUNT(y, x) FROM dsb");
        assertAnswer("1.1376243669582576648487648196737920811|0.757655954629954196597353497164461248|"
                + "0.37037037037055555555555555555555555556|-3.9012345679021944444444444444444444444",
            "SELECT SKEW(x), KURTOSIS(x), SKEW(y), KURTOSIS(y) FROM dsb");
        assertAnswer("0.53799428859451997099987528369576945419|0.66394|0.829925|"
                + "0.43864957716701902748414376321353065539|0.55632597780126849894291754756871035941|"
                + "0.28943785456032364135317109859909954057|1.13|1.052|7.568|5.03108|3.3197|5",
            "SELECT CORR(x, y), COVAR_POP(x, y), COVAR_SAMP(x, y), REGR_SLOPE(y, x), REGR_INTERCEPT(y, x), REGR_R2(y,"
                + " x), REGR_AVGX(y, x), REGR_AVGY(y, x), REGR_SXX(y, x), REGR_SYY(y, x), REGR_SXY(y, x), "
                + "REGR_COUNT(y, x) FROM dsc");
        assertAnswer("1.2859489576750120035652031156575634316|0.5633074081813265155612370323024730813|"
                + "1.4810860441914331022629142368132409983|2.103628601128894799277571752825401866",
            "SELECT SKEW(x), KURTOSIS(x), SKEW(y), KURTOSIS(y) FROM dsc");
        assertAnswer("1.2933427807333960781339719481236260721|null|0|null",
            "SELECT SKEW(x), KURTOSIS(x), SKEW(y), KURTOSIS(y) FROM dsd");
        assertAnswer("0.30860669992418382052218892552799984084|1.5|2|0.42857142857142857142857142857142857143|"
                + "2.3571428571428571428571428571428571428|0.095238095238095238095238095238095238093|5|4.5|14|27|6|4",
            "SELECT CORR(x, y), COVAR_POP(x, y), COVAR_SAMP(x, y), REGR_SLOPE(y, x), REGR_INTERCEPT(y, x), REGR_R2(y,"
                + " x), REGR_AVGX(y, x), REGR_AVGY(y, x), REGR_SXX(y, x), REGR_SYY(y, x), REGR_SXY(y, x), "
                + "REGR_COUNT(y, x) FROM dse");
        assertAnswer("-0.36326900585875416073941453391640654094|-2.4125640454444196925818667854756070394|"
                + "0.33145630368145931710584075005829830382|-2.921875",
            "SELECT SKEW(x), KURTOSIS(x), SKEW(y), KURTOSIS(y) FROM dse");
        assertAnswer("null|0|null|null|null|null|5|6|0|0|0|1",
            "SELECT CORR(x, y), COVAR_POP(x, y), COVAR_SAMP(x, y), REGR_SLOPE(y, x), REGR_INTERCEPT(y, x), REGR_R2(y,"
                + " x), REGR_AVGX(y, x), REGR_AVGY(y, x), REGR_SXX(y, x), REGR_SYY(y, x), REGR_SXY(y, x), "
                + "REGR_COUNT(y, x) FROM dsf");
        assertAnswer("null|null|null|null", "SELECT SKEW(x), KURTOSIS(x), SKEW(y), KURTOSIS(y) FROM dsf");
        assertAnswer("null|0|0|null|null|null|2|4.25|0|26.75|0|4",
            "SELECT CORR(x, y), COVAR_POP(x, y), COVAR_SAMP(x, y), REGR_SLOPE(y, x), REGR_INTERCEPT(y, x), REGR_R2(y,"
                + " x), REGR_AVGX(y, x), REGR_AVGY(y, x), REGR_SXX(y, x), REGR_SYY(y, x), REGR_SXY(y, x), "
                + "REGR_COUNT(y, x) FROM dsg");
        assertAnswer("REFUSED Division by zero", "SELECT SKEW(x), KURTOSIS(x), SKEW(y), KURTOSIS(y) FROM dsg");
        assertAnswer("null|null|null|null|null|null|null|null|null|null|null|0",
            "SELECT CORR(x, y), COVAR_POP(x, y), COVAR_SAMP(x, y), REGR_SLOPE(y, x), REGR_INTERCEPT(y, x), REGR_R2(y,"
                + " x), REGR_AVGX(y, x), REGR_AVGY(y, x), REGR_SXX(y, x), REGR_SYY(y, x), REGR_SXY(y, x), "
                + "REGR_COUNT(y, x) FROM dsh");
        assertAnswer("null|null|null|null", "SELECT SKEW(x), KURTOSIS(x), SKEW(y), KURTOSIS(y) FROM dsh");
        assertAnswer("0.999702688488727983668088920531348215|155888|194860|102.42312746386333771353482260183968463|"
                + "-247.3088042049934296977660972404730619|0.99940546537159070218647222937669018824|22|2006|7610|"
                + "79880174|779440|5",
            "SELECT CORR(x, y), COVAR_POP(x, y), COVAR_SAMP(x, y), REGR_SLOPE(y, x), REGR_INTERCEPT(y, x), REGR_R2(y,"
                + " x), REGR_AVGX(y, x), REGR_AVGY(y, x), REGR_SXX(y, x), REGR_SYY(y, x), REGR_SXY(y, x), "
                + "REGR_COUNT(y, x) FROM dsi");
        assertAnswer("2.2323959116382434660000588334177421362|4.986865957200654094740132027676426861|"
                + "2.2360589512364639960794731466415933289|4.999967694146505632091931423717794493",
            "SELECT SKEW(x), KURTOSIS(x), SKEW(y), KURTOSIS(y) FROM dsi");
        assertAnswer("-0.017531818220070771036469863672606924658|-0.91358024691358024691358024691358024667|"
                + "-1.0277777777777777777777777777777777775|-0.0030985679591324009714429277279959802354|"
                + "4.9377774055774223264383217485972699104|0.00030736465010160545829326628937225541201|"
                + "15.777777777777777777777777777777777778|4.8888888888888888888888888888888888889|"
                + "2653.5555555555555555555555555555555556|82.88888888888888888888888888888888889|"
                + "-8.22222222222222222222222222222222222|9",
            "SELECT CORR(x, y), COVAR_POP(x, y), COVAR_SAMP(x, y), REGR_SLOPE(y, x), REGR_INTERCEPT(y, x), REGR_R2(y,"
                + " x), REGR_AVGX(y, x), REGR_AVGY(y, x), REGR_SXX(y, x), REGR_SYY(y, x), REGR_SXY(y, x), "
                + "REGR_COUNT(y, x) FROM dsj");
        assertAnswer("1.5126813088865113597600106455709550688|1.7712213761541145582432537210846254696|"
                + "-0.045095081132838401211496625255665512858|-1.9947247491673589450557875544758221991",
            "SELECT SKEW(x), KURTOSIS(x), SKEW(y), KURTOSIS(y) FROM dsj");
        assertAnswer("0.32841892577527746618753356386808400006|7.1574741735537190082644628099173553718|"
                + "7.873221590909090909090909090909090909|0.18023338732234758237931244161171027247|"
                + "1.7870239799669432452004134858292657309|0.10785899080738720964512239247211222663|"
                + "8.3693181818181818181818181818181818182|3.2954545454545454545454545454545454545|"
                + "436.83480113636363636363636363636363636|131.56227272727272727272727272727272727|"
                + "78.73221590909090909090909090909090909|11",
            "SELECT CORR(x, y), COVAR_POP(x, y), COVAR_SAMP(x, y), REGR_SLOPE(y, x), REGR_INTERCEPT(y, x), REGR_R2(y,"
                + " x), REGR_AVGX(y, x), REGR_AVGY(y, x), REGR_SXX(y, x), REGR_SYY(y, x), REGR_SXY(y, x), "
                + "REGR_COUNT(y, x) FROM dsk");
        assertAnswer("0.28824266224021964045638010691159470714|2.26530612244897959183673469387755102|"
                + "2.6428571428571428571428571428571428567|0.24999999999999999999999999999999999996|"
                + "3.0000000000000000000000000000000000002|0.083083832335329341317365269461077844282|"
                + "5.7142857142857142857142857142857142857|4.4285714285714285714285714285714285714|"
                + "63.42857142857142857142857142857142857|47.71428571428571428571428571428571429|"
                + "15.85714285714285714285714285714285714|7",
            "SELECT CORR(x, y), COVAR_POP(x, y), COVAR_SAMP(x, y), REGR_SLOPE(y, x), REGR_INTERCEPT(y, x), REGR_R2(y,"
                + " x), REGR_AVGX(y, x), REGR_AVGY(y, x), REGR_SXX(y, x), REGR_SYY(y, x), REGR_SXY(y, x), "
                + "REGR_COUNT(y, x) FROM dsl");
        assertAnswer("DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|"
                + "DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|"
                + "DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|"
                + "DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|NUMBER(18,0)[SB8]|DECFLOAT(38)[DECFLOAT18]|"
                + "DECFLOAT(38)[DECFLOAT18] ;; DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|"
                + "DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|"
                + "DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|"
                + "DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|NUMBER(18,0)[SB8]|"
                + "DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18] ;; DECFLOAT(38)[DECFLOAT18]|"
                + "DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|"
                + "DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|"
                + "DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|"
                + "DECFLOAT(38)[DECFLOAT18]|NUMBER(18,0)[SB8]|DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]",
            "SELECT SYSTEM$TYPEOF(CORR(x, y)), SYSTEM$TYPEOF(COVAR_POP(x, y)), SYSTEM$TYPEOF(COVAR_SAMP(x, y)), "
                + "SYSTEM$TYPEOF(REGR_SLOPE(y, x)), SYSTEM$TYPEOF(REGR_INTERCEPT(y, x)), SYSTEM$TYPEOF(REGR_R2(y, "
                + "x)), SYSTEM$TYPEOF(REGR_AVGX(y, x)), SYSTEM$TYPEOF(REGR_AVGY(y, x)), SYSTEM$TYPEOF(REGR_SXX(y, "
                + "x)), SYSTEM$TYPEOF(REGR_SYY(y, x)), SYSTEM$TYPEOF(REGR_SXY(y, x)), SYSTEM$TYPEOF(REGR_COUNT(y, "
                + "x)), SYSTEM$TYPEOF(SKEW(x)), SYSTEM$TYPEOF(KURTOSIS(x)) FROM dsa");
        assertAnswer("DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|"
                + "DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|"
                + "DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|NUMBER(18,0)[SB8] ;; "
                + "DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|"
                + "DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|"
                + "DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|NUMBER(18,0)[SB8] ;; "
                + "DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|"
                + "DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|"
                + "DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]|NUMBER(18,0)[SB8]",
            "SELECT SYSTEM$TYPEOF(CORR(x, n)), SYSTEM$TYPEOF(CORR(n, x)), SYSTEM$TYPEOF(CORR(x, f)), "
                + "SYSTEM$TYPEOF(CORR(f, x)), SYSTEM$TYPEOF(CORR(x, i)), SYSTEM$TYPEOF(COVAR_POP(x, f)), "
                + "SYSTEM$TYPEOF(REGR_SLOPE(n, x)), SYSTEM$TYPEOF(REGR_SXX(x, n)), SYSTEM$TYPEOF(REGR_AVGX(x, f)), "
                + "SYSTEM$TYPEOF(REGR_COUNT(x, x)) FROM dsm");
        assertAnswer("0.97150951786716784110458785793042121251|0.97150951786716784110458785793042121251|"
                + "3.7777777777777777777777777777777777777|3.7777777777777777777777777777777777777|"
                + "2.5357142857142857142857142857142857141|0.37221494102228047182175622542595019659|"
                + "5.1666666666666666666666666666666666667|31.791666666666666666666666666666666667",
            "SELECT CORR(x, n), CORR(x, f), COVAR_POP(x, i), COVAR_POP(i, x), REGR_SLOPE(n, x), REGR_SLOPE(x, f), "
                + "REGR_AVGX(x, n), REGR_SXX(x, n) FROM dsm");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 38 | Cumulative window frame "
                + "unsupported for function CORR",
            "SELECT x, CORR(x, y) OVER (ORDER BY x ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW), "
                + "SYSTEM$TYPEOF(COVAR_POP(x, y) OVER ()) FROM dsa ORDER BY x");
        assertAnswer("1|0.757655954629954196597353497164461248|1.1376243669582576648487648196737920811|"
                + "DECFLOAT(38)[DECFLOAT18] ;; 2|0.757655954629954196597353497164461248|"
                + "1.1376243669582576648487648196737920811|DECFLOAT(38)[DECFLOAT18] ;; 4|"
                + "0.757655954629954196597353497164461248|1.1376243669582576648487648196737920811|"
                + "DECFLOAT(38)[DECFLOAT18] ;; 8|0.757655954629954196597353497164461248|"
                + "1.1376243669582576648487648196737920811|DECFLOAT(38)[DECFLOAT18]",
            "SELECT x, KURTOSIS(x) OVER (), SKEW(x) OVER (), SYSTEM$TYPEOF(KURTOSIS(x) OVER ()) FROM dsb ORDER BY x");
        assertAnswer("-0.052583048204961887908346813342287055381|-0.285714285715607142857142857142857143",
            "SELECT CORR(a::DECFLOAT, b::DECFLOAT), KURTOSIS(a::DECFLOAT) FROM (VALUES (1, 2), (2, 3), (4, 9), (7, "
                + "1)) t(a, b)");
        assertAnswer("Table DSCT successfully created.",
            "CREATE OR REPLACE TABLE dsct AS SELECT CORR(x, y) AS c, KURTOSIS(x) AS k, REGR_COUNT(y, x) AS r FROM dsa");
        assertAnswer("C|DECFLOAT(38)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; K|DECFLOAT(38)|COLUMN|Y|null|"
                + "N|N|null|null|null|null|null|null ;; R|NUMBER(18,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE dsct");
        assertAnswer("0.97986371009719943856985017511294647251|null|3", "SELECT c, k, r FROM dsct");
    }

    @Test
    public void edgesOfTheSeries() {
        engine.execute("CREATE OR REPLACE TABLE dk (x DECFLOAT, y DECFLOAT)");
        engine.execute("INSERT INTO dk VALUES (1, 5), (2, 5), (4, 5), (8, 5)");
        engine.execute("CREATE OR REPLACE TABLE dc (x DECFLOAT)");
        engine.execute("INSERT INTO dc VALUES (2), (2), (2), (2)");
        assertAnswer("1|0|5|null|0|0",
            "SELECT REGR_R2(y, x), REGR_SLOPE(y, x), REGR_INTERCEPT(y, x), CORR(x, y), COVAR_POP(x, y), REGR_SYY(y, "
                + "x) FROM dk");
        assertAnswer("REFUSED Division by zero", "SELECT KURTOSIS(x) FROM dc");
        assertAnswer("REFUSED Division by zero", "SELECT SKEW(x) FROM dc");
        assertAnswer("REFUSED Division by zero", "SELECT SKEW(y) FROM dk");
        assertAnswer("REFUSED Division by zero", "SELECT KURTOSIS(y) FROM dk");
        assertAnswer("REFUSED Division by zero",
            "SELECT VAR_POP(x), STDDEV(x), SKEW(x::FLOAT), KURTOSIS(x::FLOAT) FROM dc");
        assertAnswer("Table DW successfully created.", "CREATE OR REPLACE TABLE dw (x DECFLOAT, y DECFLOAT)");
        assertAnswer("2",
            "INSERT INTO dw VALUES (0.3333333333333333333333333333333333333, 1.5), (2, "
                + "0.6666666666666666666666666666666666667)");
        assertAnswer("2", "SELECT COUNT(*) FROM dw");
        assertAnswer("1", "INSERT INTO dw VALUES (0.3333333333333333333333333333333333333, 1.5)");
        assertAnswer("1", "INSERT INTO dw VALUES (2, 0.6666666666666666666666666666666666667)");
        assertAnswer("0.3333333333333333333333333333333333333|1.5 ;; 0.3333333333333333333333333333333333333|1.5 ;; 2|"
                + "0.6666666666666666666666666666666666667 ;; 2|0.6666666666666666666666666666666666667",
            "SELECT x, y FROM dw ORDER BY x");
        assertAnswer("0.3333333333333333333333333333333333333 ;; 2.0000000000000000000000000000000000000",
            "SELECT * FROM (VALUES (0.3333333333333333333333333333333333333), (2)) t(v)");
        assertAnswer("NUMBER(38,37)[SB16] ;; NUMBER(38,37)[SB16]",
            "SELECT SYSTEM$TYPEOF(v) FROM (VALUES (0.3333333333333333333333333333333333333), (0.5)) t(v)");
        assertAnswer("null|null|null", "SELECT CORR(x, y), SKEW(x), KURTOSIS(x) FROM dk WHERE x > 100");
        assertAnswer("1|0|1.1376243669582576648487648196737920811 ;; 2|0|1.1376243669582576648487648196737920811 ;; 4|"
                + "0|1.1376243669582576648487648196737920811 ;; 8|0|1.1376243669582576648487648196737920811",
            "SELECT x, COVAR_POP(x, y) OVER (PARTITION BY x > 2), SKEW(x) OVER (PARTITION BY y) FROM dk ORDER BY x");
    }
}
