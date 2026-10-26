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
 * The digits of a FLOAT frame that moves along its partition, as live computes them in one walk rather than
 * frame by frame: SUM and AVG over the RANGE frame from the current row to the partition's end add its peer
 * groups from the end; the variance family over a ROWS frame, and over RANGE from the partition's start, slides
 * one compensated running sum and sum of squares that rows enter and leave, and over a RANGE frame with an
 * offset slides plain sums (all live-verified).
 */
public class MovingFloatFrameTest extends MeasuredCellsTestSupport {

    @Test
    public void aReverseCumulativeRangeFrameAddsItsPeerGroupsFromTheEnd() {
        assertAnswer("Table WV successfully created.", "CREATE OR REPLACE TABLE wv (p INT, i INT, s VARCHAR, x FLOAT)");
        assertAnswer("37",
            "INSERT INTO wv VALUES (1, 0, 's00', 0.2), (1, 1, 's01', 0.7), (1, 2, 's02', 0.11), (1, 3, 's03', 0.9), "
                + "(1, 4, 's04', 0.6), (1, 5, 's05', 0.35), (1, 6, 's06', 1.1), (1, 7, 's07', 2.2), (1, 8, 's08', "
                + "1.7), (1, 9, 's09', 0.9), (2, 1, 's00', 1.1), (2, 1, 's01', 1.1), (2, 1, 's02', 1.1), (2, 2, "
                + "'s03', 4.4), (2, 3, 's04', 0.13), (2, 4, 's05', 0.9), (2, 4, 's06', 0.9), (2, 5, 's07', 0.7), (2, "
                + "6, 's08', 0.1), (2, 6, 's09', 0.1), (3, 1, 's00', 0.17), (3, 1, 's01', 0.17), (3, 2, 's02', 0.9), "
                + "(3, 2, 's03', 0.9), (3, 3, 's04', 0.1), (3, 3, 's05', 0.1), (3, 4, 's06', 0.6), (3, 5, 's07', "
                + "0.17), (3, 5, 's08', 0.17), (4, 1, 's00', 0.2), (4, 2, 's01', 0.9), (4, 2, 's02', 0.7), (4, 3, "
                + "'s03', 0.13), (4, 3, 's04', 0.1), (4, 4, 's05', 0.2), (4, 4, 's06', 0.01), (4, 4, 's07', 0.6)");
        assertAnswer("Table WN successfully created.", "CREATE OR REPLACE TABLE wn (i INT, n NUMBER(12,2))");
        assertAnswer("7",
            "INSERT INTO wn VALUES (1, 100000000.01), (2, 100000000.02), (3, 100000000.04), (4, 100000000.07), (5, "
                + "100000000.11), (6, 100000000.16), (7, 100000000.22)");
        assertAnswer("3|1|s00|3.28000000000000024869 ;; 3|1|s01|3.28000000000000024869 ;; 3|2|s02|"
                + "2.94000000000000039080 ;; 3|2|s03|2.94000000000000039080 ;; 3|3|s04|1.14000000000000012434 ;; 3|3|"
                + "s05|1.14000000000000012434 ;; 3|4|s06|0.93999999999999994671 ;; 3|5|s07|0.34000000000000002442 ;; "
                + "3|5|s08|0.34000000000000002442 ;; 4|1|s00|2.84000000000000030198 ;; 4|2|s01|2.64000000000000012434 "
                + ";; 4|2|s02|2.64000000000000012434 ;; 4|3|s03|1.04000000000000003553 ;; 4|3|s04|"
                + "1.04000000000000003553 ;; 4|4|s05|0.80999999999999994227 ;; 4|4|s06|0.80999999999999994227 ;; 4|4|"
                + "s07|0.80999999999999994227",
            "SELECT p, i, s, (SUM(x) OVER (PARTITION BY p ORDER BY i RANGE BETWEEN CURRENT ROW AND UNBOUNDED "
                + "FOLLOWING))::NUMBER(38,20) FROM wv WHERE p IN (3, 4) ORDER BY p, i, s");
        assertAnswer("3|1|s00|0.36444444444444445974 ;; 3|1|s01|0.36444444444444445974 ;; 3|2|s02|"
                + "0.42000000000000003997 ;; 3|2|s03|0.42000000000000003997 ;; 3|3|s04|0.22800000000000003597 ;; 3|3|"
                + "s05|0.22800000000000003597 ;; 3|4|s06|0.31333333333333329707 ;; 3|5|s07|0.17000000000000001221 ;; "
                + "3|5|s08|0.17000000000000001221 ;; 4|1|s00|0.35500000000000003775 ;; 4|2|s01|0.37714285714285716855 "
                + ";; 4|2|s02|0.37714285714285716855 ;; 4|3|s03|0.20800000000000001821 ;; 4|3|s04|"
                + "0.20800000000000001821 ;; 4|4|s05|0.26999999999999996225 ;; 4|4|s06|0.26999999999999996225 ;; 4|4|"
                + "s07|0.26999999999999996225",
            "SELECT p, i, s, (AVG(x) OVER (PARTITION BY p ORDER BY i RANGE BETWEEN CURRENT ROW AND UNBOUNDED "
                + "FOLLOWING))::NUMBER(38,20) FROM wv WHERE p IN (3, 4) ORDER BY p, i, s");
        assertAnswer("3|1|s00|0.34000000000000002442 ;; 3|1|s01|0.34000000000000002442 ;; 3|2|s02|"
                + "2.14000000000000012434 ;; 3|2|s03|2.14000000000000012434 ;; 3|3|s04|2.34000000000000030198 ;; 3|3|"
                + "s05|2.34000000000000030198 ;; 3|4|s06|2.94000000000000039080 ;; 3|5|s07|3.28000000000000024869 ;; "
                + "3|5|s08|3.28000000000000024869 ;; 4|1|s00|0.20000000000000001110 ;; 4|2|s01|1.80000000000000004441 "
                + ";; 4|2|s02|1.80000000000000004441 ;; 4|3|s03|2.03000000000000024869 ;; 4|3|s04|"
                + "2.03000000000000024869 ;; 4|4|s05|2.83999999999999985789 ;; 4|4|s06|2.83999999999999985789 ;; 4|4|"
                + "s07|2.83999999999999985789",
            "SELECT p, i, s, (SUM(x) OVER (PARTITION BY p ORDER BY i ))::NUMBER(38,20) FROM wv WHERE p IN (3, 4) "
                + "ORDER BY p, i, s");
        assertAnswer("1|0|s00|0.20000000000000001110 ;; 1|1|s01|0.89999999999999991118 ;; 1|2|s02|"
                + "1.01000000000000000888 ;; 1|3|s03|1.90999999999999992006 ;; 1|4|s04|2.50999999999999978684 ;; 1|5|"
                + "s05|2.85999999999999987566 ;; 1|6|s06|3.95999999999999996447 ;; 1|7|s07|6.16000000000000014211 ;; "
                + "1|8|s08|7.86000000000000031974 ;; 1|9|s09|8.75999999999999978684",
            "SELECT p, i, s, (SUM(x) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT "
                + "ROW))::NUMBER(38,20) FROM wv WHERE p = 1 ORDER BY p, i, s");
        assertAnswer("1|0|s00|0.44999999999999923395 ;; 1|1|s01|0.33666666666666617003 ;; 1|2|s02|"
                + "0.56999999999999950706 ;; 1|3|s03|0.53666666666666629215 ;; 1|4|s04|0.61666666666666625218 ;; 1|5|"
                + "s05|0.68333333333333301507 ;; 1|6|s06|1.21666666666666656305 ;; 1|7|s07|1.66666666666666674068 ;; "
                + "1|8|s08|1.60000000000000031086 ;; 1|9|s09|1.30000000000000004441",
            "SELECT p, i, s, (AVG(x) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 1 PRECEDING AND 1 "
                + "FOLLOWING))::NUMBER(38,20) FROM wv WHERE p = 1 ORDER BY p, i, s");
        assertAnswer("1|0|s00|8.75999999999999978684 ;; 1|1|s01|8.56000000000000049738 ;; 1|2|s02|"
                + "7.86000000000000031974 ;; 1|3|s03|7.75000000000000000000 ;; 1|4|s04|6.85000000000000053291 ;; 1|5|"
                + "s05|6.25000000000000000000 ;; 1|6|s06|5.90000000000000035527 ;; 1|7|s07|4.80000000000000071054 ;; "
                + "1|8|s08|2.60000000000000008882 ;; 1|9|s09|0.90000000000000002220",
            "SELECT p, i, s, (SUM(x) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN CURRENT ROW AND UNBOUNDED "
                + "FOLLOWING))::NUMBER(38,20) FROM wv WHERE p = 1 ORDER BY p, i, s");
        assertAnswer("2|1|s00|10.53000000000000113687 ;; 2|1|s01|10.53000000000000113687 ;; 2|1|s02|"
                + "10.53000000000000113687 ;; 2|2|s03|7.23000000000000042633 ;; 2|3|s04|2.83000000000000007105 ;; 2|4|"
                + "s05|2.70000000000000017764 ;; 2|4|s06|2.70000000000000017764 ;; 2|5|s07|0.89999999999999991118 ;; "
                + "2|6|s08|0.20000000000000001110 ;; 2|6|s09|0.20000000000000001110",
            "SELECT p, i, s, (SUM(x) OVER (PARTITION BY p ORDER BY i RANGE BETWEEN CURRENT ROW AND UNBOUNDED "
                + "FOLLOWING))::NUMBER(38,20) FROM wv WHERE p = 2 ORDER BY p, i, s");
        assertAnswer("2|1|s00|1.10000000000000008882 ;; 2|1|s01|1.10000000000000008882 ;; 2|1|s02|"
                + "1.10000000000000008882 ;; 2|2|s03|1.92500000000000026645 ;; 2|3|s04|1.56600000000000028066 ;; 2|4|"
                + "s05|1.37571428571428588938 ;; 2|4|s06|1.37571428571428588938 ;; 2|5|s07|1.29125000000000023093 ;; "
                + "2|6|s08|1.05300000000000015810 ;; 2|6|s09|1.05300000000000015810",
            "SELECT p, i, s, (AVG(x) OVER (PARTITION BY p ORDER BY i RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT "
                + "ROW))::NUMBER(38,20) FROM wv WHERE p = 2 ORDER BY p, i, s");
    }

    @Test
    public void aMovingVarianceSlidesOneRunningState() {
        assertAnswer("Table WV successfully created.", "CREATE OR REPLACE TABLE wv (p INT, i INT, s VARCHAR, x FLOAT)");
        assertAnswer("37",
            "INSERT INTO wv VALUES (1, 0, 's00', 0.2), (1, 1, 's01', 0.7), (1, 2, 's02', 0.11), (1, 3, 's03', 0.9), "
                + "(1, 4, 's04', 0.6), (1, 5, 's05', 0.35), (1, 6, 's06', 1.1), (1, 7, 's07', 2.2), (1, 8, 's08', "
                + "1.7), (1, 9, 's09', 0.9), (2, 1, 's00', 1.1), (2, 1, 's01', 1.1), (2, 1, 's02', 1.1), (2, 2, "
                + "'s03', 4.4), (2, 3, 's04', 0.13), (2, 4, 's05', 0.9), (2, 4, 's06', 0.9), (2, 5, 's07', 0.7), (2, "
                + "6, 's08', 0.1), (2, 6, 's09', 0.1), (3, 1, 's00', 0.17), (3, 1, 's01', 0.17), (3, 2, 's02', 0.9), "
                + "(3, 2, 's03', 0.9), (3, 3, 's04', 0.1), (3, 3, 's05', 0.1), (3, 4, 's06', 0.6), (3, 5, 's07', "
                + "0.17), (3, 5, 's08', 0.17), (4, 1, 's00', 0.2), (4, 2, 's01', 0.9), (4, 2, 's02', 0.7), (4, 3, "
                + "'s03', 0.13), (4, 3, 's04', 0.1), (4, 4, 's05', 0.2), (4, 4, 's06', 0.01), (4, 4, 's07', 0.6)");
        assertAnswer("Table WN successfully created.", "CREATE OR REPLACE TABLE wn (i INT, n NUMBER(12,2))");
        assertAnswer("7",
            "INSERT INTO wn VALUES (1, 100000000.01), (2, 100000000.02), (3, 100000000.04), (4, 100000000.07), (5, "
                + "100000000.11), (6, 100000000.16), (7, 100000000.22)");
        assertAnswer("1|0|s00|0.12499999999999966693 ;; 1|1|s01|0.10103333333333319732 ;; 1|2|s02|"
                + "0.16869999999999985008 ;; 1|3|s03|0.15903333333333313782 ;; 1|4|s04|0.07583333333333319715 ;; 1|5|"
                + "s05|0.14583333333333303727 ;; 1|6|s06|0.86583333333333312165 ;; 1|7|s07|0.30333333333333339921 ;; "
                + "1|8|s08|0.42999999999999971578 ;; 1|9|s09|0.31999999999999939604",
            "SELECT p, i, s, (VARIANCE(x) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 1 PRECEDING AND 1 "
                + "FOLLOWING))::NUMBER(38,20) FROM wv WHERE p = 1 ORDER BY p, i, s");
        assertAnswer("1|0|s00|null ;; 1|1|s01|0.12500000000000000000 ;; 1|2|s02|0.10103333333333334998 ;; 1|3|s03|"
                + "0.16870000000000007212 ;; 1|4|s04|0.15903333333333349864 ;; 1|5|s05|0.07583333333333341919 ;; 1|6|"
                + "s06|0.14583333333333348136 ;; 1|7|s07|0.86583333333333367676 ;; 1|8|s08|0.30333333333333395432 ;; "
                + "1|9|s09|0.43000000000000088152",
            "SELECT p, i, s, (VARIANCE(x) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 2 PRECEDING AND CURRENT "
                + "ROW))::NUMBER(38,20) FROM wv WHERE p = 1 ORDER BY p, i, s");
        assertAnswer("1|0|s00|0.17404999999999931637 ;; 1|1|s01|0.31204999999999938343 ;; 1|2|s02|"
                + "0.04499999999999904077 ;; 1|3|s03|0.03124999999999933387 ;; 1|4|s04|0.28124999999999933387 ;; 1|5|"
                + "s05|0.60499999999999864997 ;; 1|6|s06|0.12499999999999911182 ;; 1|7|s07|0.31999999999999939604 ;; "
                + "1|8|s08|null ;; 1|9|s09|null",
            "SELECT p, i, s, (VARIANCE(x) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 1 FOLLOWING AND 2 "
                + "FOLLOWING))::NUMBER(38,20) FROM wv WHERE p = 1 ORDER BY p, i, s");
        assertAnswer("1|0|s00|null ;; 1|1|s01|null ;; 1|2|s02|0.12500000000000000000 ;; 1|3|s03|"
                + "0.17405000000000014904 ;; 1|4|s04|0.31205000000000004956 ;; 1|5|s05|0.04500000000000015099 ;; 1|6|"
                + "s06|0.03125000000000005551 ;; 1|7|s07|0.28124999999999977796 ;; 1|8|s08|0.60499999999999953815 ;; "
                + "1|9|s09|0.12499999999999822364",
            "SELECT p, i, s, (VARIANCE(x) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 2 PRECEDING AND 1 "
                + "PRECEDING))::NUMBER(38,20) FROM wv WHERE p = 1 ORDER BY p, i, s");
        assertAnswer("1|0|s00|0.12500000000000000000 ;; 1|1|s01|0.10103333333333334998 ;; 1|2|s02|"
                + "0.14669166666666669241 ;; 1|3|s03|0.11302000000000003710 ;; 1|4|s04|0.09426666666666670691 ;; 1|5|"
                + "s05|0.13406190476190479877 ;; 1|6|s06|0.44877142857142870991 ;; 1|7|s07|0.48877499999999979297 ;; "
                + "1|8|s08|0.43453777777777780056 ;; 1|9|s09|0.43453777777777780056",
            "SELECT p, i, s, (VARIANCE(x) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN UNBOUNDED PRECEDING AND 1 "
                + "FOLLOWING))::NUMBER(38,20) FROM wv WHERE p = 1 ORDER BY p, i, s");
        assertAnswer("1|0|s00|0.43453777777777780056 ;; 1|1|s01|0.42538611111111124252 ;; 1|2|s02|"
                + "0.47602142857142865084 ;; 1|3|s03|0.41035714285714286476 ;; 1|4|s04|0.48241666666666632723 ;; 1|5|"
                + "s05|0.51500000000000023537 ;; 1|6|s06|0.34916666666666645868 ;; 1|7|s07|0.42999999999999971578 ;; "
                + "1|8|s08|0.31999999999999939604 ;; 1|9|s09|null",
            "SELECT p, i, s, (VARIANCE(x) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN CURRENT ROW AND UNBOUNDED "
                + "FOLLOWING))::NUMBER(38,20) FROM wv WHERE p = 1 ORDER BY p, i, s");
        assertAnswer("1|0|s00|0.43453777777777780056 ;; 1|1|s01|0.43453777777777780056 ;; 1|2|s02|"
                + "0.42538611111111124252 ;; 1|3|s03|0.47602142857142865084 ;; 1|4|s04|0.41035714285714286476 ;; 1|5|"
                + "s05|0.48241666666666632723 ;; 1|6|s06|0.51500000000000023537 ;; 1|7|s07|0.34916666666666645868 ;; "
                + "1|8|s08|0.42999999999999971578 ;; 1|9|s09|0.31999999999999939604",
            "SELECT p, i, s, (VARIANCE(x) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 1 PRECEDING AND UNBOUNDED "
                + "FOLLOWING))::NUMBER(38,20) FROM wv WHERE p = 1 ORDER BY p, i, s");
        assertAnswer("1|0|s00|0.06249999999999983347 ;; 1|1|s01|0.06735555555555546026 ;; 1|2|s02|"
                + "0.11246666666666656209 ;; 1|3|s03|0.10602222222222208725 ;; 1|4|s04|0.05055555555555546476 ;; 1|5|"
                + "s05|0.09722222222222202948 ;; 1|6|s06|0.57722222222222208110 ;; 1|7|s07|0.20222222222222224763 ;; "
                + "1|8|s08|0.28666666666666645868 ;; 1|9|s09|0.15999999999999969802",
            "SELECT p, i, s, (VAR_POP(x) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 1 PRECEDING AND 1 "
                + "FOLLOWING))::NUMBER(38,20) FROM wv WHERE p = 1 ORDER BY p, i, s");
        assertAnswer("1|0|s00|0.35355339059327328677 ;; 1|1|s01|0.31785741037976950052 ;; 1|2|s02|"
                + "0.41073105555825684121 ;; 1|3|s03|0.39878983604567097565 ;; 1|4|s04|0.27537852736430484812 ;; 1|5|"
                + "s05|0.38188130791298630085 ;; 1|6|s06|0.93050165681385710315 ;; 1|7|s07|0.55075705472861025136 ;; "
                + "1|8|s08|0.65574385243019983616 ;; 1|9|s09|0.56568542494923745867",
            "SELECT p, i, s, (STDDEV(x) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 1 PRECEDING AND 1 "
                + "FOLLOWING))::NUMBER(38,20) FROM wv WHERE p = 1 ORDER BY p, i, s");
        assertAnswer("1|0|s00|0.24999999999999966693 ;; 1|1|s01|0.25952948879762288703 ;; 1|2|s02|"
                + "0.33536050254415256111 ;; 1|3|s03|0.32561053764001879340 ;; 1|4|s04|0.22484562605386715362 ;; 1|5|"
                + "s05|0.31180478223116148140 ;; 1|6|s06|0.75975142133609863127 ;; 1|7|s07|0.44969125210773475132 ;; "
                + "1|8|s08|0.53541261347363344747 ;; 1|9|s09|0.39999999999999963363",
            "SELECT p, i, s, (STDDEV_POP(x) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 1 PRECEDING AND 1 "
                + "FOLLOWING))::NUMBER(38,20) FROM wv WHERE p = 1 ORDER BY p, i, s");
        assertAnswer("2|1|s00|1.56097888888888891579 ;; 2|1|s01|1.56097888888888891579 ;; 2|1|s02|"
                + "1.56097888888888891579 ;; 2|2|s03|2.33989047619047640936 ;; 2|3|s04|0.16241666666666662588 ;; 2|4|"
                + "s05|0.16799999999999987166 ;; 2|4|s06|0.16799999999999987166 ;; 2|5|s07|0.11999999999999999556 ;; "
                + "2|6|s08|0E-20 ;; 2|6|s09|0E-20",
            "SELECT p, i, s, (VARIANCE(x) OVER (PARTITION BY p ORDER BY i RANGE BETWEEN CURRENT ROW AND UNBOUNDED "
                + "FOLLOWING))::NUMBER(38,20) FROM wv WHERE p = 2 ORDER BY p, i, s");
        assertAnswer("2|1|s00|0E-20 ;; 2|1|s01|0E-20 ;; 2|1|s02|0E-20 ;; 2|2|s03|2.72249999999999969802 ;; 2|3|s04|"
                + "2.68628000000000044523 ;; 2|4|s05|1.89646190476190534824 ;; 2|4|s06|1.89646190476190534824 ;; 2|5|"
                + "s07|1.68261249999999984439 ;; 2|6|s08|1.56097888888888891579 ;; 2|6|s09|1.56097888888888891579",
            "SELECT p, i, s, (VARIANCE(x) OVER (PARTITION BY p ORDER BY i RANGE BETWEEN UNBOUNDED PRECEDING AND "
                + "CURRENT ROW))::NUMBER(38,20) FROM wv WHERE p = 2 ORDER BY p, i, s");
        assertAnswer("2|1|s00|2.72249999999999969802 ;; 2|1|s01|2.72249999999999969802 ;; 2|1|s02|"
                + "2.72249999999999969802 ;; 2|2|s03|2.68627999999999955705 ;; 2|3|s04|3.65989166666666365657 ;; 2|4|"
                + "s05|0.13255833333332991963 ;; 2|4|s06|0.13255833333332991963 ;; 2|5|s07|0.16799999999999734590 ;; "
                + "2|6|s08|0.11999999999999506894 ;; 2|6|s09|0.11999999999999506894",
            "SELECT p, i, s, (VARIANCE(x) OVER (PARTITION BY p ORDER BY i RANGE BETWEEN 1 PRECEDING AND 1 "
                + "FOLLOWING))::NUMBER(38,20) FROM wv WHERE p = 2 ORDER BY p, i, s");
        assertAnswer("2|1|s00|0E-20 ;; 2|1|s01|0E-20 ;; 2|1|s02|0E-20 ;; 2|2|s03|2.72249999999999969802 ;; 2|3|s04|"
                + "9.11644999999999328111 ;; 2|4|s05|0.19763333333332919195 ;; 2|4|s06|0.19763333333332919195 ;; 2|5|"
                + "s07|0.01333333333332845962 ;; 2|6|s08|0.11999999999999573508 ;; 2|6|s09|0.11999999999999573508",
            "SELECT p, i, s, (VARIANCE(x) OVER (PARTITION BY p ORDER BY i RANGE BETWEEN 1 PRECEDING AND CURRENT "
                + "ROW))::NUMBER(38,20) FROM wv WHERE p = 2 ORDER BY p, i, s");
        assertAnswer("2|1|s00|1.18527676092969946886 ;; 2|1|s01|1.18527676092969946886 ;; 2|1|s02|"
                + "1.18527676092969946886 ;; 2|2|s03|1.41619928264466565793 ;; 2|3|s04|0.36789566757740188585 ;; 2|4|"
                + "s05|0.36660605559646708418 ;; 2|4|s06|0.36660605559646708418 ;; 2|5|s07|0.28284271247461900689 ;; "
                + "2|6|s08|0E-20 ;; 2|6|s09|0E-20",
            "SELECT p, i, s, (STDDEV_POP(x) OVER (PARTITION BY p ORDER BY i RANGE BETWEEN CURRENT ROW AND UNBOUNDED "
                + "FOLLOWING))::NUMBER(38,20) FROM wv WHERE p = 2 ORDER BY p, i, s");
    }

    @Test
    public void anAggregateOverAMovingFrameAnswersEveryFrame() {
        assertAnswer("Table WV successfully created.", "CREATE OR REPLACE TABLE wv (p INT, i INT, s VARCHAR, x FLOAT)");
        assertAnswer("37",
            "INSERT INTO wv VALUES (1, 0, 's00', 0.2), (1, 1, 's01', 0.7), (1, 2, 's02', 0.11), (1, 3, 's03', 0.9), "
                + "(1, 4, 's04', 0.6), (1, 5, 's05', 0.35), (1, 6, 's06', 1.1), (1, 7, 's07', 2.2), (1, 8, 's08', "
                + "1.7), (1, 9, 's09', 0.9), (2, 1, 's00', 1.1), (2, 1, 's01', 1.1), (2, 1, 's02', 1.1), (2, 2, "
                + "'s03', 4.4), (2, 3, 's04', 0.13), (2, 4, 's05', 0.9), (2, 4, 's06', 0.9), (2, 5, 's07', 0.7), (2, "
                + "6, 's08', 0.1), (2, 6, 's09', 0.1), (3, 1, 's00', 0.17), (3, 1, 's01', 0.17), (3, 2, 's02', 0.9), "
                + "(3, 2, 's03', 0.9), (3, 3, 's04', 0.1), (3, 3, 's05', 0.1), (3, 4, 's06', 0.6), (3, 5, 's07', "
                + "0.17), (3, 5, 's08', 0.17), (4, 1, 's00', 0.2), (4, 2, 's01', 0.9), (4, 2, 's02', 0.7), (4, 3, "
                + "'s03', 0.13), (4, 3, 's04', 0.1), (4, 4, 's05', 0.2), (4, 4, 's06', 0.01), (4, 4, 's07', 0.6)");
        assertAnswer("Table WN successfully created.", "CREATE OR REPLACE TABLE wn (i INT, n NUMBER(12,2))");
        assertAnswer("7",
            "INSERT INTO wn VALUES (1, 100000000.01), (2, 100000000.02), (3, 100000000.04), (4, 100000000.07), (5, "
                + "100000000.11), (6, 100000000.16), (7, 100000000.22)");
        assertAnswer("1|0|s00|1 ;; 1|1|s01|1 ;; 1|2|s02|2 ;; 1|3|s03|2 ;; 1|4|s04|2 ;; 1|5|s05|2 ;; 1|6|s06|2 ;; 1|7|"
                + "s07|3 ;; 1|8|s08|3 ;; 1|9|s09|2",
            "SELECT p, i, s, (COUNT_IF(x > 0.5) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 1 PRECEDING AND 1 "
                + "FOLLOWING)) FROM wv WHERE p = 1 ORDER BY p, i, s");
        assertAnswer("1|0|s00|[\"s00\",\"s01\"] ;; 1|1|s01|[\"s00\",\"s01\",\"s02\"] ;; 1|2|s02|[\"s01\",\"s02\","
                + "\"s03\"] ;; 1|3|s03|[\"s02\",\"s03\",\"s04\"] ;; 1|4|s04|[\"s03\",\"s04\",\"s05\"] ;; 1|5|s05|"
                + "[\"s04\",\"s05\",\"s06\"] ;; 1|6|s06|[\"s05\",\"s06\",\"s07\"] ;; 1|7|s07|[\"s06\",\"s07\","
                + "\"s08\"] ;; 1|8|s08|[\"s07\",\"s08\",\"s09\"] ;; 1|9|s09|[\"s08\",\"s09\"]",
            "SELECT p, i, s, (ARRAY_AGG(s) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 1 PRECEDING AND 1 "
                + "FOLLOWING)) FROM wv WHERE p = 1 ORDER BY p, i, s");
        assertAnswer("1|0|s00|2 ;; 1|1|s01|3 ;; 1|2|s02|3 ;; 1|3|s03|3 ;; 1|4|s04|3 ;; 1|5|s05|3 ;; 1|6|s06|3 ;; 1|7|"
                + "s07|3 ;; 1|8|s08|3 ;; 1|9|s09|2",
            "SELECT p, i, s, (COUNT(DISTINCT x) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 1 PRECEDING AND 1 "
                + "FOLLOWING)) FROM wv WHERE p = 1 ORDER BY p, i, s");
        assertAnswer("2|1|s00|1 ;; 2|1|s01|1 ;; 2|1|s02|1 ;; 2|2|s03|1 ;; 2|3|s04|1 ;; 2|4|s05|1 ;; 2|4|s06|1 ;; 2|5|"
                + "s07|1 ;; 2|6|s08|1 ;; 2|6|s09|1",
            "SELECT p, i, s, (COUNT(DISTINCT x) OVER (PARTITION BY p ORDER BY i RANGE BETWEEN CURRENT ROW AND "
                + "CURRENT ROW)) FROM wv WHERE p = 2 ORDER BY p, i, s");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 56 | WITHIN GROUP clause is not "
                + "supported when the OVER clause contains an ORDER BY clause.",
            "SELECT p, i, s, (ARRAY_AGG(s) WITHIN GROUP (ORDER BY s) OVER (PARTITION BY p ORDER BY i RANGE BETWEEN "
                + "UNBOUNDED PRECEDING AND CURRENT ROW)) FROM wv WHERE p = 2 ORDER BY p, i, s");
        assertAnswer("2|1|s00|[\"s00\",\"s01\",\"s02\"] ;; 2|1|s01|[\"s00\",\"s01\",\"s02\"] ;; 2|1|s02|[\"s00\","
                + "\"s01\",\"s02\"] ;; 2|2|s03|[\"s00\",\"s01\",\"s02\",\"s03\"] ;; 2|3|s04|[\"s03\",\"s04\"] ;; 2|4|"
                + "s05|[\"s04\",\"s05\",\"s06\"] ;; 2|4|s06|[\"s04\",\"s05\",\"s06\"] ;; 2|5|s07|[\"s05\",\"s06\","
                + "\"s07\"] ;; 2|6|s08|[\"s07\",\"s08\",\"s09\"] ;; 2|6|s09|[\"s07\",\"s08\",\"s09\"]",
            "SELECT p, i, s, (ARRAY_AGG(s) OVER (PARTITION BY p ORDER BY i RANGE BETWEEN 1 PRECEDING AND CURRENT "
                + "ROW)) FROM wv WHERE p = 2 ORDER BY p, i, s");
        assertAnswer("2|1|s00|7 ;; 2|1|s01|7 ;; 2|1|s02|7 ;; 2|2|s03|4 ;; 2|3|s04|3 ;; 2|4|s05|3 ;; 2|4|s06|3 ;; 2|5|"
                + "s07|1 ;; 2|6|s08|0 ;; 2|6|s09|0",
            "SELECT p, i, s, (COUNT_IF(x > 0.5) OVER (PARTITION BY p ORDER BY i RANGE BETWEEN CURRENT ROW AND "
                + "UNBOUNDED FOLLOWING)) FROM wv WHERE p = 2 ORDER BY p, i, s");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 38 | distinct cannot be used with a "
                + "window frame or an order.",
            "SELECT p, i, s, (VARIANCE(DISTINCT x) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 1 PRECEDING AND 1 "
                + "FOLLOWING))::NUMBER(38,20) FROM wv WHERE p = 1 ORDER BY p, i, s");
    }

    @Test
    public void aFrameOverLargeAndSmallValuesKeepsLivesDigits() {
        assertAnswer("Table WF successfully created.",
            "CREATE OR REPLACE TABLE wf (p INT, i INT, x FLOAT, y FLOAT, s VARCHAR)");
        assertAnswer("12",
            "INSERT INTO wf VALUES (1, 1, 0.3, 1.5, 'a'), (1, 2, 0.7, 2.5, 'b'), (1, 2, 0.1, 0.5, 'c'), (1, 3, 0.5, "
                + "4.5, 'd'), (1, 4, 0.9, 0.25, 'e'), (1, 4, 0.11, 1.0, 'f'), (1, 5, 0.13, 2.0, 'g'), (1, 6, 0.2, "
                + "3.0, 'h'), (2, 1, 1.1, 1.0, 'i'), (2, 1, 2.2, 2.0, 'j'), (2, 2, 3.3, 3.0, 'k'), (2, 3, NULL, 1.0, "
                + "'l')");
        assertAnswer("Table WZ successfully created.", "CREATE OR REPLACE TABLE wz (i INT, z FLOAT)");
        assertAnswer("7",
            "INSERT INTO wz VALUES (1, 1e16), (2, 1.0), (2, 2.5), (3, -1e16), (4, 3.25), (5, 1e-3), (5, 7.0)");
        assertAnswer("1|1|a|1.71899999999999986144 ;; 1|2|b|1.62900000000000000355 ;; 1|2|c|1.62900000000000000355 ;; "
                + "1|3|d|1.12900000000000000355 ;; 1|4|e|0.87900000000000000355 ;; 1|4|f|0.87900000000000000355 ;; 1|"
                + "5|g|0.05690000000000000613 ;; 1|6|h|0.04000000000000000777 ;; 2|1|i|16.93999999999999772626 ;; 2|1|"
                + "j|16.93999999999999772626 ;; 2|2|k|10.88999999999999879208 ;; 2|3|l|null",
            "SELECT p, i, s, (SUM(x * x) OVER (PARTITION BY p ORDER BY i RANGE BETWEEN CURRENT ROW AND UNBOUNDED "
                + "FOLLOWING))::NUMBER(38,20) FROM wf ORDER BY p, i, s");
        assertAnswer("1|1|a|0.21487499999999998268 ;; 1|2|b|0.23271428571428570686 ;; 1|2|c|0.23271428571428570686 ;; "
                + "1|3|d|0.22580000000000000071 ;; 1|4|e|0.21975000000000000089 ;; 1|4|f|0.21975000000000000089 ;; 1|"
                + "5|g|0.02845000000000000306 ;; 1|6|h|0.04000000000000000777 ;; 2|1|i|5.64666666666666561269 ;; 2|1|"
                + "j|5.64666666666666561269 ;; 2|2|k|10.88999999999999879208 ;; 2|3|l|null",
            "SELECT p, i, s, (AVG(x * x) OVER (PARTITION BY p ORDER BY i RANGE BETWEEN CURRENT ROW AND UNBOUNDED "
                + "FOLLOWING))::NUMBER(38,20) FROM wf ORDER BY p, i, s");
        assertAnswer("1|1|a|0.08999999999999999667 ;; 1|2|b|0.58999999999999996891 ;; 1|2|c|0.58999999999999996891 ;; "
                + "1|3|d|0.83999999999999985789 ;; 1|4|e|1.66209999999999991083 ;; 1|4|f|1.66209999999999991083 ;; 1|"
                + "5|g|1.67899999999999982592 ;; 1|6|h|1.71899999999999986144 ;; 2|1|i|6.05000000000000071054 ;; 2|1|"
                + "j|6.05000000000000071054 ;; 2|2|k|16.93999999999999772626 ;; 2|3|l|16.93999999999999772626",
            "SELECT p, i, s, (SUM(x * x) OVER (PARTITION BY p ORDER BY i RANGE BETWEEN UNBOUNDED PRECEDING AND "
                + "CURRENT ROW))::NUMBER(38,20) FROM wf ORDER BY p, i, s");
        assertAnswer("1|1|a|1.71899999999999986144 ;; 1|2|b|1.71899999999999986144 ;; 1|2|c|1.71899999999999986144 ;; "
                + "1|3|d|1.71899999999999986144 ;; 1|4|e|1.71899999999999986144 ;; 1|4|f|1.71899999999999986144 ;; 1|"
                + "5|g|1.71899999999999986144 ;; 1|6|h|1.71899999999999986144 ;; 2|1|i|16.93999999999999772626 ;; 2|1|"
                + "j|16.93999999999999772626 ;; 2|2|k|16.93999999999999772626 ;; 2|3|l|16.93999999999999772626",
            "SELECT p, i, s, (SUM(x * x) OVER (PARTITION BY p ORDER BY i RANGE BETWEEN UNBOUNDED PRECEDING AND "
                + "UNBOUNDED FOLLOWING))::NUMBER(38,20) FROM wf ORDER BY p, i, s");
        assertAnswer("1|1|a|0.08999999999999999667 ;; 1|2|b|0.57999999999999996003 ;; 1|2|c|0.58999999999999985789 ;; "
                + "1|3|d|0.83999999999999985789 ;; 1|4|e|1.64999999999999991118 ;; 1|4|f|1.66209999999999991083 ;; 1|"
                + "5|g|1.67899999999999982592 ;; 1|6|h|1.71899999999999986144 ;; 2|1|i|1.21000000000000018652 ;; 2|1|"
                + "j|6.05000000000000071054 ;; 2|2|k|16.93999999999999772626 ;; 2|3|l|16.93999999999999772626",
            "SELECT p, i, s, (SUM(x * x) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN UNBOUNDED PRECEDING AND "
                + "CURRENT ROW))::NUMBER(38,20) FROM wf ORDER BY p, i, s");
        assertAnswer("1|1|a|0.08999999999999999667 ;; 1|2|b|0.28999999999999998002 ;; 1|2|c|0.19666666666666665630 ;; "
                + "1|3|d|0.20999999999999999223 ;; 1|4|e|0.32999999999999996003 ;; 1|4|f|0.27701666666666663330 ;; 1|"
                + "5|g|0.23985714285714282434 ;; 1|6|h|0.21487499999999998268 ;; 2|1|i|1.21000000000000018652 ;; 2|1|"
                + "j|3.02500000000000035527 ;; 2|2|k|5.64666666666666561269 ;; 2|3|l|5.64666666666666561269",
            "SELECT p, i, s, (AVG(x * x) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN UNBOUNDED PRECEDING AND "
                + "CURRENT ROW))::NUMBER(38,20) FROM wf ORDER BY p, i, s");
        assertAnswer("1|1|a|1.71899999999999986144 ;; 1|2|b|1.62900000000000000355 ;; 1|2|c|1.13900000000000001243 ;; "
                + "1|3|d|1.12900000000000000355 ;; 1|4|e|0.87900000000000000355 ;; 1|4|f|0.06900000000000000577 ;; 1|"
                + "5|g|0.05690000000000000613 ;; 1|6|h|0.04000000000000000777 ;; 2|1|i|16.94000000000000127898 ;; 2|1|"
                + "j|15.73000000000000042633 ;; 2|2|k|10.88999999999999879208 ;; 2|3|l|null",
            "SELECT p, i, s, (SUM(x * x) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN CURRENT ROW AND UNBOUNDED "
                + "FOLLOWING))::NUMBER(38,20) FROM wf ORDER BY p, i, s");
        assertAnswer("1|1.0E16|14.0000 ;; 2|1.0|-9999999999999986.0000 ;; 2|2.5|-9999999999999986.0000 ;; 3|-1.0E16|"
                + "-9999999999999990.0000 ;; 4|3.25|10.2510 ;; 5|0.001|7.0010 ;; 5|7.0|7.0010",
            "SELECT i, z, (SUM(z) OVER (ORDER BY i RANGE BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING))::NUMBER(38,4) "
                + "FROM wz ORDER BY i, z");
        assertAnswer("1|1.0E16|10000000000000000.0000 ;; 2|1.0|10000000000000004.0000 ;; 2|2.5|10000000000000004.0000 "
                + ";; 3|-1.0E16|4.0000 ;; 4|3.25|7.2500 ;; 5|0.001|14.2510 ;; 5|7.0|14.2510",
            "SELECT i, z, (SUM(z) OVER (ORDER BY i RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW))::NUMBER(38,4) "
                + "FROM wz ORDER BY i, z");
        assertAnswer("1|1.0E16|2.0000 ;; 2|1.0|-1666666666666664.2500 ;; 2|2.5|-1666666666666664.2500 ;; 3|-1.0E16|"
                + "-2499999999999997.5000 ;; 4|3.25|3.4170 ;; 5|0.001|3.5005 ;; 5|7.0|3.5005",
            "SELECT i, z, (AVG(z) OVER (ORDER BY i RANGE BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING))::NUMBER(38,4) "
                + "FROM wz ORDER BY i, z");
    }

    @Test
    public void aMovingDeviationKeepsLivesDigits() {
        assertAnswer("Table WF successfully created.",
            "CREATE OR REPLACE TABLE wf (p INT, i INT, x FLOAT, y FLOAT, s VARCHAR)");
        assertAnswer("12",
            "INSERT INTO wf VALUES (1, 1, 0.3, 1.5, 'a'), (1, 2, 0.7, 2.5, 'b'), (1, 2, 0.1, 0.5, 'c'), (1, 3, 0.5, "
                + "4.5, 'd'), (1, 4, 0.9, 0.25, 'e'), (1, 4, 0.11, 1.0, 'f'), (1, 5, 0.13, 2.0, 'g'), (1, 6, 0.2, "
                + "3.0, 'h'), (2, 1, 1.1, 1.0, 'i'), (2, 1, 2.2, 2.0, 'j'), (2, 2, 3.3, 3.0, 'k'), (2, 3, NULL, 1.0, "
                + "'l')");
        assertAnswer("Table WZ successfully created.", "CREATE OR REPLACE TABLE wz (i INT, z FLOAT)");
        assertAnswer("7",
            "INSERT INTO wz VALUES (1, 1e16), (2, 1.0), (2, 2.5), (3, -1e16), (4, 3.25), (5, 1e-3), (5, 7.0)");
        assertAnswer("1|1|a|0.282842712474619 ;; 1|2|b|0.305505046330389 ;; 1|2|c|0.305505046330389 ;; 1|3|d|"
                + "0.400000000000000 ;; 1|4|e|0.395010548382361 ;; 1|4|f|0.450444225182208 ;; 1|5|g|0.047258156262526 "
                + ";; 1|6|h|0.049497474683058 ;; 2|1|i|0.777817459305202 ;; 2|1|j|1.100000000000001 ;; 2|2|k|"
                + "0.777817459305203 ;; 2|3|l|null",
            "SELECT p, i, s, (STDDEV(x) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 1 PRECEDING AND 1 "
                + "FOLLOWING))::NUMBER(38,15) FROM wf ORDER BY p, i, s");
        assertAnswer("1|1|a|0.080000000000000 ;; 1|2|b|0.093333333333333 ;; 1|2|c|0.093333333333333 ;; 1|3|d|"
                + "0.160000000000000 ;; 1|4|e|0.156033333333333 ;; 1|4|f|0.202900000000000 ;; 1|5|g|0.002233333333333 "
                + ";; 1|6|h|0.002450000000000 ;; 2|1|i|0.605000000000000 ;; 2|1|j|1.210000000000002 ;; 2|2|k|"
                + "0.605000000000000 ;; 2|3|l|null",
            "SELECT p, i, s, (VARIANCE(x) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 1 PRECEDING AND 1 "
                + "FOLLOWING))::NUMBER(38,15) FROM wf ORDER BY p, i, s");
        assertAnswer("1|1|a|0.040000000000000 ;; 1|2|b|0.062222222222222 ;; 1|2|c|0.062222222222222 ;; 1|3|d|"
                + "0.106666666666667 ;; 1|4|e|0.104022222222222 ;; 1|4|f|0.135266666666667 ;; 1|5|g|0.001488888888889 "
                + ";; 1|6|h|0.001225000000000 ;; 2|1|i|0.302500000000000 ;; 2|1|j|0.806666666666668 ;; 2|2|k|"
                + "0.302500000000000 ;; 2|3|l|0E-15",
            "SELECT p, i, s, (VAR_POP(x) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 1 PRECEDING AND 1 "
                + "FOLLOWING))::NUMBER(38,15) FROM wf ORDER BY p, i, s");
        assertAnswer("1|1|a|0.200000000000000 ;; 1|2|b|0.249443825784929 ;; 1|2|c|0.249443825784929 ;; 1|3|d|"
                + "0.326598632371090 ;; 1|4|e|0.322524762184584 ;; 1|4|f|0.367786169759912 ;; 1|5|g|0.038586123009301 "
                + ";; 1|6|h|0.035000000000000 ;; 2|1|i|0.550000000000000 ;; 2|1|j|0.898146239020499 ;; 2|2|k|"
                + "0.550000000000000 ;; 2|3|l|0E-15",
            "SELECT p, i, s, (STDDEV_POP(x) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 1 PRECEDING AND 1 "
                + "FOLLOWING))::NUMBER(38,15) FROM wf ORDER BY p, i, s");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 69 | Sliding window frame unsupported "
                + "for function COVAR_SAMP",
            "SELECT p, i, s, (COVAR_SAMP(x, y) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 1 PRECEDING AND 1 "
                + "FOLLOWING))::NUMBER(38,15) FROM wf ORDER BY p, i, s");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 63 | Sliding window frame unsupported "
                + "for function CORR",
            "SELECT p, i, s, (CORR(x, y) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 1 PRECEDING AND 1 "
                + "FOLLOWING))::NUMBER(38,15) FROM wf ORDER BY p, i, s");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 69 | Sliding window frame unsupported "
                + "for function REGR_SLOPE",
            "SELECT p, i, s, (REGR_SLOPE(y, x) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 1 PRECEDING AND 1 "
                + "FOLLOWING))::NUMBER(38,15) FROM wf ORDER BY p, i, s");
        assertAnswer("1|1|a|2 ;; 1|2|b|3 ;; 1|2|c|3 ;; 1|3|d|3 ;; 1|4|e|3 ;; 1|4|f|3 ;; 1|5|g|3 ;; 1|6|h|2 ;; 2|1|i|2 "
                + ";; 2|1|j|3 ;; 2|2|k|2 ;; 2|3|l|1",
            "SELECT p, i, s, COUNT(x) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING) "
                + "FROM wf ORDER BY p, i, s");
        assertAnswer("1|1|a|1 ;; 1|2|b|1 ;; 1|2|c|2 ;; 1|3|d|2 ;; 1|4|e|2 ;; 1|4|f|1 ;; 1|5|g|0 ;; 1|6|h|0 ;; 2|1|i|2 "
                + ";; 2|1|j|3 ;; 2|2|k|2 ;; 2|3|l|1",
            "SELECT p, i, s, COUNT_IF(x > 0.3) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 1 PRECEDING AND 1 "
                + "FOLLOWING) FROM wf ORDER BY p, i, s");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 61 | Sliding window frame unsupported "
                + "for function MEDIAN",
            "SELECT p, i, s, MEDIAN(x) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING) "
                + "FROM wf ORDER BY p, i, s");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 67 | Sliding window frame unsupported "
                + "for function LISTAGG",
            "SELECT p, i, s, LISTAGG(s, ',') OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 1 PRECEDING AND 1 "
                + "FOLLOWING) FROM wf ORDER BY p, i, s");
        assertAnswer("1|1|a|[\"a\",\"b\"] ;; 1|2|b|[\"a\",\"b\",\"c\"] ;; 1|2|c|[\"b\",\"c\",\"d\"] ;; 1|3|d|[\"c\","
                + "\"d\",\"e\"] ;; 1|4|e|[\"d\",\"e\",\"f\"] ;; 1|4|f|[\"e\",\"f\",\"g\"] ;; 1|5|g|[\"f\",\"g\","
                + "\"h\"] ;; 1|6|h|[\"g\",\"h\"] ;; 2|1|i|[\"i\",\"j\"] ;; 2|1|j|[\"i\",\"j\",\"k\"] ;; 2|2|k|[\"j\","
                + "\"k\",\"l\"] ;; 2|3|l|[\"k\",\"l\"]",
            "SELECT p, i, s, ARRAY_AGG(s) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 1 PRECEDING AND 1 "
                + "FOLLOWING) FROM wf ORDER BY p, i, s");
        assertAnswer("1|1|a|2 ;; 1|2|b|3 ;; 1|2|c|3 ;; 1|3|d|3 ;; 1|4|e|3 ;; 1|4|f|3 ;; 1|5|g|3 ;; 1|6|h|2 ;; 2|1|i|2 "
                + ";; 2|1|j|3 ;; 2|2|k|3 ;; 2|3|l|2",
            "SELECT p, i, s, COUNT(DISTINCT s) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 1 PRECEDING AND 1 "
                + "FOLLOWING) FROM wf ORDER BY p, i, s");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 59 | Sliding window frame unsupported "
                + "for function MODE",
            "SELECT p, i, s, MODE(s) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING) "
                + "FROM wf ORDER BY p, i, s");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 64 | Sliding window frame unsupported "
                + "for function KURTOSIS",
            "SELECT p, i, s, (KURTOSIS(x) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 1 PRECEDING AND 1 "
                + "FOLLOWING))::NUMBER(38,12) FROM wf ORDER BY p, i, s");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 60 | Sliding window frame unsupported "
                + "for function SKEW",
            "SELECT p, i, s, (SKEW(x) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 1 PRECEDING AND 1 "
                + "FOLLOWING))::NUMBER(38,12) FROM wf ORDER BY p, i, s");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 72 | Sliding window frame unsupported "
                + "for function BOOLAND_AGG",
            "SELECT p, i, s, BOOLAND_AGG(x > 0.1) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 1 PRECEDING AND 1 "
                + "FOLLOWING) FROM wf ORDER BY p, i, s");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 76 | Sliding window frame unsupported "
                + "for function APPROX_COUNT_DISTINCT",
            "SELECT p, i, s, APPROX_COUNT_DISTINCT(s) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 1 PRECEDING "
                + "AND 1 FOLLOWING) FROM wf ORDER BY p, i, s");
        assertAnswer("1|1|a|null ;; 1|2|b|0.305505046330389 ;; 1|2|c|0.305505046330389 ;; 1|3|d|0.258198889747161 ;; "
                + "1|4|e|0.324576647342350 ;; 1|4|f|0.324576647342350 ;; 1|5|g|0.317932307859633 ;; 1|6|h|"
                + "0.302028853872322 ;; 2|1|i|0.777817459305202 ;; 2|1|j|0.777817459305202 ;; 2|2|k|1.100000000000000 "
                + ";; 2|3|l|1.100000000000000",
            "SELECT p, i, s, (STDDEV(x) OVER (PARTITION BY p ORDER BY i RANGE BETWEEN UNBOUNDED PRECEDING AND "
                + "CURRENT ROW))::NUMBER(38,15) FROM wf ORDER BY p, i, s");
        assertAnswer("1|1|a|0.091221428571429 ;; 1|2|b|0.105557142857143 ;; 1|2|c|0.105557142857143 ;; 1|3|d|"
                + "0.112970000000000 ;; 1|4|e|0.143366666666667 ;; 1|4|f|0.143366666666667 ;; 1|5|g|0.002450000000000 "
                + ";; 1|6|h|null ;; 2|1|i|1.210000000000000 ;; 2|1|j|1.210000000000000 ;; 2|2|k|null ;; 2|3|l|null",
            "SELECT p, i, s, (VARIANCE(x) OVER (PARTITION BY p ORDER BY i RANGE BETWEEN CURRENT ROW AND UNBOUNDED "
                + "FOLLOWING))::NUMBER(38,15) FROM wf ORDER BY p, i, s");
        assertAnswer("1|1|a|null ;; 1|2|b|0.282842712474619 ;; 1|2|c|0.305505046330389 ;; 1|3|d|0.305505046330389 ;; "
                + "1|4|e|0.400000000000000 ;; 1|4|f|0.395010548382361 ;; 1|5|g|0.450444225182208 ;; 1|6|h|"
                + "0.047258156262526 ;; 2|1|i|null ;; 2|1|j|0.777817459305202 ;; 2|2|k|1.100000000000000 ;; 2|3|l|"
                + "0.777817459305201",
            "SELECT p, i, s, (STDDEV(x) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN 2 PRECEDING AND CURRENT "
                + "ROW))::NUMBER(38,15) FROM wf ORDER BY p, i, s");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 63 | Cumulative window frame "
                + "unsupported for function CORR",
            "SELECT p, i, s, (CORR(x, y) OVER (PARTITION BY p ORDER BY i, s ROWS BETWEEN UNBOUNDED PRECEDING AND "
                + "CURRENT ROW))::NUMBER(38,15) FROM wf ORDER BY p, i, s");
    }
}
