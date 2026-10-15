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
package dev.frostlake.functions;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * COUNT, ANY_VALUE, the standard deviations, SKEW and KURTOSIS, the correlation and regression family, the
 * percentiles, the bitwise aggregates and GROUPING: what each answers over values, over NULLs and over no
 * rows, and the type each declares. A percentile over a descending order reads the group from its top, and a
 * bitwise aggregate rounds a fraction half away from zero (live-verified).
 */
public class AggregateFunctionCatalogTest extends MeasuredCellsTestSupport {

    @Test
    public void countsAndDeviations() {
        answer(
            "CREATE OR REPLACE TABLE ta (g INT, x NUMBER(10,2), y NUMBER(10,2), s VARCHAR, b INT)");
        answer(
            "INSERT INTO ta VALUES (1, 1.0, 2.0, 'a', 12), (1, 2.0, 4.5, 'b', 10), (2, 3.0, 5.0, NULL, 6), (2, "
                + "NULL, 7.0, 'c', NULL), (2, 5.0, NULL, 'a', 3)");
        answer(
            "CREATE OR REPLACE TABLE te (x NUMBER(10,2), y NUMBER(10,2), b INT)");
        assertAnswer(
            "5|NUMBER(18,0)[SB1]",
            "SELECT COUNT(*), SYSTEM$TYPEOF(COUNT(*)) FROM ta");
        assertAnswer(
            "4|NUMBER(18,0)[SB1]",
            "SELECT COUNT(x), SYSTEM$TYPEOF(COUNT(x)) FROM ta");
        assertAnswer(
            "3|NUMBER(18,0)[SB8]",
            "SELECT COUNT(DISTINCT s), SYSTEM$TYPEOF(COUNT(DISTINCT s)) FROM ta");
        assertAnswer(
            "3|NUMBER(18,0)[SB8]",
            "SELECT COUNT(x, y), SYSTEM$TYPEOF(COUNT(x, y)) FROM ta");
        assertAnswer(
            "0|NUMBER(18,0)[SB1]",
            "SELECT COUNT(*), SYSTEM$TYPEOF(COUNT(*)) FROM te");
        assertAnswer(
            "0|NUMBER(18,0)[SB1]",
            "SELECT COUNT(NULL), SYSTEM$TYPEOF(COUNT(NULL)) FROM ta");
        assertAnswer(
            "3|NUMBER(18,0)[SB8]",
            "SELECT COUNT(DISTINCT x, s), SYSTEM$TYPEOF(COUNT(DISTINCT x, s)) FROM ta");
        assertAnswer(
            "1|NUMBER(38,0)[SB1]",
            "SELECT ANY_VALUE(g), SYSTEM$TYPEOF(ANY_VALUE(g)) FROM ta WHERE g = 1");
        assertAnswer(
            "null|NUMBER(10,2)[SB1]",
            "SELECT ANY_VALUE(x), SYSTEM$TYPEOF(ANY_VALUE(x)) FROM te");
        assertAnswer(
            "null|NULL[LOB]",
            "SELECT ANY_VALUE(NULL), SYSTEM$TYPEOF(ANY_VALUE(NULL)) FROM ta");
        assertAnswer(
            "null|FLOAT[DOUBLE]",
            "SELECT STDDEV_SAMP(x), SYSTEM$TYPEOF(STDDEV_SAMP(x)) FROM ta WHERE g = 9");
        assertAnswer(
            "null|FLOAT[DOUBLE]",
            "SELECT STDDEV_SAMP(x), SYSTEM$TYPEOF(STDDEV_SAMP(x)) FROM ta WHERE x = 1");
        assertAnswer(
            "0.0|FLOAT[DOUBLE]",
            "SELECT STDDEV_POP(x), SYSTEM$TYPEOF(STDDEV_POP(x)) FROM ta WHERE x = 1");
        assertAnswer(
            "REFUSED Numeric value 'a' is not recognized",
            "SELECT STDDEV_SAMP(s), SYSTEM$TYPEOF(STDDEV_SAMP(s)) FROM ta");
        assertAnswer(
            "null|FLOAT[DOUBLE]",
            "SELECT SKEW(x), SYSTEM$TYPEOF(SKEW(x)) FROM ta WHERE g = 1");
        assertAnswer(
            "null|NUMBER(38,12)[SB1]",
            "SELECT KURTOSIS(x), SYSTEM$TYPEOF(KURTOSIS(x)) FROM te");
    }

    @Test
    public void pairsAndRegressions() {
        answer(
            "CREATE OR REPLACE TABLE ta (g INT, x NUMBER(10,2), y NUMBER(10,2), s VARCHAR, b INT)");
        answer(
            "INSERT INTO ta VALUES (1, 1.0, 2.0, 'a', 12), (1, 2.0, 4.5, 'b', 10), (2, 3.0, 5.0, NULL, 6), (2, "
                + "NULL, 7.0, 'c', NULL), (2, 5.0, NULL, 'a', 3)");
        answer(
            "CREATE OR REPLACE TABLE te (x NUMBER(10,2), y NUMBER(10,2), b INT)");
        assertAnswer(
            "1.0|FLOAT[DOUBLE]",
            "SELECT COVAR_POP(y, x), SYSTEM$TYPEOF(COVAR_POP(y, x)) FROM ta");
        assertAnswer(
            "1.5|FLOAT[DOUBLE]",
            "SELECT COVAR_SAMP(y, x), SYSTEM$TYPEOF(COVAR_SAMP(y, x)) FROM ta");
        assertAnswer(
            "null|FLOAT[DOUBLE]",
            "SELECT CORR(y, x), SYSTEM$TYPEOF(CORR(y, x)) FROM ta WHERE g = 2");
        assertAnswer(
            "1.0|FLOAT[DOUBLE]",
            "SELECT CORR(x, x), SYSTEM$TYPEOF(CORR(x, x)) FROM ta");
        assertAnswer(
            "null|FLOAT[DOUBLE]",
            "SELECT COVAR_SAMP(y, x), SYSTEM$TYPEOF(COVAR_SAMP(y, x)) FROM te");
        assertAnswer(
            "null|FLOAT[DOUBLE]",
            "SELECT CORR(y, 1), SYSTEM$TYPEOF(CORR(y, 1)) FROM ta");
        assertAnswer(
            "1.5|FLOAT[DOUBLE]",
            "SELECT REGR_SLOPE(y, x), SYSTEM$TYPEOF(REGR_SLOPE(y, x)) FROM ta");
        assertAnswer(
            "3|NUMBER(18,0)[SB8]",
            "SELECT REGR_COUNT(y, x), SYSTEM$TYPEOF(REGR_COUNT(y, x)) FROM ta");
        assertAnswer(
            "2.0|FLOAT[DOUBLE]",
            "SELECT REGR_AVGX(y, x), SYSTEM$TYPEOF(REGR_AVGX(y, x)) FROM ta");
        assertAnswer(
            "2.0|FLOAT[DOUBLE]",
            "SELECT REGR_SXX(y, x), SYSTEM$TYPEOF(REGR_SXX(y, x)) FROM ta");
        assertAnswer(
            "3.0|FLOAT[DOUBLE]",
            "SELECT REGR_SXY(y, x), SYSTEM$TYPEOF(REGR_SXY(y, x)) FROM ta");
        assertAnswer(
            "null|FLOAT[DOUBLE]",
            "SELECT REGR_SLOPE(y, x), SYSTEM$TYPEOF(REGR_SLOPE(y, x)) FROM te");
        assertAnswer(
            "0|NUMBER(18,0)[SB1]",
            "SELECT REGR_COUNT(y, x), SYSTEM$TYPEOF(REGR_COUNT(y, x)) FROM te");
        assertAnswer(
            "null|FLOAT[DOUBLE]",
            "SELECT REGR_SLOPE(y, 1), SYSTEM$TYPEOF(REGR_SLOPE(y, 1)) FROM ta");
    }

    @Test
    public void percentilesBitsAndGrouping() {
        answer(
            "CREATE OR REPLACE TABLE ta (g INT, x NUMBER(10,2), y NUMBER(10,2), s VARCHAR, b INT)");
        answer(
            "INSERT INTO ta VALUES (1, 1.0, 2.0, 'a', 12), (1, 2.0, 4.5, 'b', 10), (2, 3.0, 5.0, NULL, 6), (2, "
                + "NULL, 7.0, 'c', NULL), (2, 5.0, NULL, 'a', 3)");
        answer(
            "CREATE OR REPLACE TABLE te (x NUMBER(10,2), y NUMBER(10,2), b INT)");
        assertAnswer(
            "2.50000|NUMBER(13,5)[SB4]",
            "SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY x), SYSTEM$TYPEOF(PERCENTILE_CONT(0.5) WITHIN "
                + "GROUP (ORDER BY x)) FROM ta");
        assertAnswer(
            "2.00|NUMBER(10,2)[SB2]",
            "SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY x), SYSTEM$TYPEOF(PERCENTILE_DISC(0.5) WITHIN "
                + "GROUP (ORDER BY x)) FROM ta");
        assertAnswer(
            "3.50000|NUMBER(13,5)[SB4]",
            "SELECT PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY x DESC), SYSTEM$TYPEOF(PERCENTILE_CONT(0.25) "
                + "WITHIN GROUP (ORDER BY x DESC)) FROM ta");
        assertAnswer(
            "1.00|NUMBER(10,2)[SB2]",
            "SELECT PERCENTILE_DISC(0) WITHIN GROUP (ORDER BY x), SYSTEM$TYPEOF(PERCENTILE_DISC(0) WITHIN GROUP "
                + "(ORDER BY x)) FROM ta");
        assertAnswer(
            "5.00000|NUMBER(13,5)[SB4]",
            "SELECT PERCENTILE_CONT(1) WITHIN GROUP (ORDER BY x), SYSTEM$TYPEOF(PERCENTILE_CONT(1) WITHIN GROUP "
                + "(ORDER BY x)) FROM ta");
        assertAnswer(
            "null|NUMBER(13,5)[SB1]",
            "SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY x), SYSTEM$TYPEOF(PERCENTILE_CONT(0.5) WITHIN "
                + "GROUP (ORDER BY x)) FROM te");
        assertAnswer(
            "REFUSED Invalid parameter value: 1.5. Reason: percentile must be between 0 and 1",
            "SELECT PERCENTILE_CONT(1.5) WITHIN GROUP (ORDER BY x), SYSTEM$TYPEOF(PERCENTILE_CONT(1.5) WITHIN "
                + "GROUP (ORDER BY x)) FROM ta");
        assertAnswer(
            "6|NUMBER(38,0)[SB1]",
            "SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY b), SYSTEM$TYPEOF(PERCENTILE_DISC(0.5) WITHIN "
                + "GROUP (ORDER BY b)) FROM ta");
        assertAnswer(
            "0|NUMBER(38,0)[SB1]",
            "SELECT BITAND_AGG(b), SYSTEM$TYPEOF(BITAND_AGG(b)) FROM ta");
        assertAnswer(
            "15|NUMBER(38,0)[SB1]",
            "SELECT BITOR_AGG(b), SYSTEM$TYPEOF(BITOR_AGG(b)) FROM ta");
        assertAnswer(
            "3|NUMBER(38,0)[SB1]",
            "SELECT BITXOR_AGG(b), SYSTEM$TYPEOF(BITXOR_AGG(b)) FROM ta");
        assertAnswer(
            "15|NUMBER(38,0)[SB1]",
            "SELECT BITORAGG(b), SYSTEM$TYPEOF(BITORAGG(b)) FROM ta");
        assertAnswer(
            "15|NUMBER(38,0)[SB1]",
            "SELECT BIT_OR_AGG(b), SYSTEM$TYPEOF(BIT_OR_AGG(b)) FROM ta");
        assertAnswer(
            "15|NUMBER(38,0)[SB1]",
            "SELECT BIT_ORAGG(b), SYSTEM$TYPEOF(BIT_ORAGG(b)) FROM ta");
        assertAnswer(
            "3|NUMBER(38,0)[SB1]",
            "SELECT BITXORAGG(b), SYSTEM$TYPEOF(BITXORAGG(b)) FROM ta");
        assertAnswer(
            "3|NUMBER(38,0)[SB1]",
            "SELECT BIT_XOR_AGG(b), SYSTEM$TYPEOF(BIT_XOR_AGG(b)) FROM ta");
        assertAnswer(
            "3|NUMBER(38,0)[SB1]",
            "SELECT BIT_XORAGG(b), SYSTEM$TYPEOF(BIT_XORAGG(b)) FROM ta");
        assertAnswer(
            "null|NUMBER(38,0)[SB1]",
            "SELECT BITAND_AGG(b), SYSTEM$TYPEOF(BITAND_AGG(b)) FROM te");
        assertAnswer(
            "7|NUMBER(8,0)[SB1]",
            "SELECT BITOR_AGG(x), SYSTEM$TYPEOF(BITOR_AGG(x)) FROM ta");
        assertAnswer(
            "0|NUMBER(38,0)[SB1]",
            "SELECT BIT_AND_AGG(b), SYSTEM$TYPEOF(BIT_AND_AGG(b)) FROM ta");
        assertAnswer(
            "0|NUMBER(38,0)[SB1]",
            "SELECT BITANDAGG(b), SYSTEM$TYPEOF(BITANDAGG(b)) FROM ta");
        assertAnswer(
            "1|0|2 ;; 2|0|3 ;; null|1|5",
            "SELECT g, GROUPING(g), COUNT(*) FROM ta GROUP BY ROLLUP (g) ORDER BY 1 NULLS LAST");
        assertAnswer(
            "1|a|0 ;; 1|b|0 ;; 2|a|0 ;; 2|c|0 ;; 2|null|0 ;; 1|null|1 ;; 2|null|1 ;; null|a|2 ;; null|b|2 ;; "
                + "null|c|2 ;; null|null|2 ;; null|null|3",
            "SELECT g, s, GROUPING(g, s) FROM ta GROUP BY CUBE (g, s) ORDER BY 3, 1, 2");
        assertAnswer(
            "NUMBER(18,0)[SB8]",
            "SELECT SYSTEM$TYPEOF(GROUPING(g)) FROM ta GROUP BY ROLLUP (g) LIMIT 1");
        assertAnswer(
            "0 ;; 0",
            "SELECT GROUPING(g) FROM ta GROUP BY g ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 16 | 'TA.X' in select clause is neither an "
                + "aggregate nor in the group by clause.",
            "SELECT GROUPING(x) FROM ta GROUP BY ROLLUP (g)");
    }

    @Test
    public void bitsOfOtherWidths() {
        answer(
            "CREATE OR REPLACE TABLE ta (g INT, x NUMBER(10,2), y NUMBER(10,2), s VARCHAR, b INT)");
        answer(
            "INSERT INTO ta VALUES (1, 1.0, 2.0, 'a', 12), (1, 2.0, 4.5, 'b', 10), (2, 3.0, 5.0, NULL, 6), (2, "
                + "NULL, 7.0, 'c', NULL), (2, 5.0, NULL, 'a', 3)");
        answer(
            "CREATE OR REPLACE TABLE te (x NUMBER(10,2), y NUMBER(10,2), b INT)");
        answer(
            "CREATE OR REPLACE TABLE tb (n NUMBER(5,0), w NUMBER(20,0), f FLOAT, neg INT, d NUMBER(6,1))");
        answer(
            "INSERT INTO tb VALUES (300, 70000, 5.5, -5, 100.5), (17, 1, 2.0, 5, 3.4), (4, 4294967296, 9.0, -128, "
                + "0.5)");
        assertAnswer(
            "3|NUMBER(38,0)[SB1]",
            "SELECT BITOR_AGG(g), SYSTEM$TYPEOF(BITOR_AGG(g)) FROM ta");
        assertAnswer(
            "0|NUMBER(8,0)[SB1]",
            "SELECT BITAND_AGG(x), SYSTEM$TYPEOF(BITAND_AGG(x)) FROM ta");
        assertAnswer(
            "5|NUMBER(8,0)[SB1]",
            "SELECT BITXOR_AGG(y), SYSTEM$TYPEOF(BITXOR_AGG(y)) FROM ta");
        assertAnswer(
            "317|NUMBER(5,0)[SB2]",
            "SELECT BITOR_AGG(n), SYSTEM$TYPEOF(BITOR_AGG(n)) FROM tb");
        assertAnswer(
            "0|NUMBER(5,0)[SB2]",
            "SELECT BITAND_AGG(n), SYSTEM$TYPEOF(BITAND_AGG(n)) FROM tb");
        assertAnswer(
            "4295037297|NUMBER(20,0)[SB8]",
            "SELECT BITXOR_AGG(w), SYSTEM$TYPEOF(BITXOR_AGG(w)) FROM tb");
        assertAnswer(
            "4295037297|NUMBER(20,0)[SB8]",
            "SELECT BITOR_AGG(w), SYSTEM$TYPEOF(BITOR_AGG(w)) FROM tb");
        assertAnswer(
            "15|NUMBER(2,0)[SB8]",
            "SELECT BITOR_AGG(f), SYSTEM$TYPEOF(BITOR_AGG(f)) FROM tb");
        assertAnswer(
            "0|NUMBER(38,0)[SB16]",
            "SELECT BITAND_AGG(neg), SYSTEM$TYPEOF(BITAND_AGG(neg)) FROM tb");
        assertAnswer(
            "-1|NUMBER(38,0)[SB16]",
            "SELECT BITOR_AGG(neg), SYSTEM$TYPEOF(BITOR_AGG(neg)) FROM tb");
        assertAnswer(
            "126|NUMBER(38,0)[SB16]",
            "SELECT BITXOR_AGG(neg), SYSTEM$TYPEOF(BITXOR_AGG(neg)) FROM tb");
        assertAnswer(
            "103|NUMBER(5,0)[SB1]",
            "SELECT BITOR_AGG(d), SYSTEM$TYPEOF(BITOR_AGG(d)) FROM tb");
        assertAnswer(
            "319|NUMBER(6,0)[SB2]",
            "SELECT BITOR_AGG(n + 1), SYSTEM$TYPEOF(BITOR_AGG(n + 1)) FROM tb");
        assertAnswer(
            "REFUSED Numeric value 'a' is not recognized",
            "SELECT BITOR_AGG(s), SYSTEM$TYPEOF(BITOR_AGG(s)) FROM ta");
        assertAnswer(
            "1|14|NUMBER(38,0)[SB1] ;; 2|7|NUMBER(38,0)[SB1]",
            "SELECT g, BITOR_AGG(b), SYSTEM$TYPEOF(BITOR_AGG(b)) FROM ta GROUP BY g ORDER BY g");
        assertAnswer(
            "15|NUMBER(38,0)[SB1]",
            "SELECT BITOR_AGG(DISTINCT b), SYSTEM$TYPEOF(BITOR_AGG(DISTINCT b)) FROM ta");
        assertAnswer(
            "13|NUMBER(38,0)[SB16] ;; 11|NUMBER(38,0)[SB16] ;; 6|NUMBER(38,0)[SB16] ;; 3|NUMBER(38,0)[SB16] ;; "
                + "null|NUMBER(38,0)[SB16]",
            "SELECT BITOR(g, b), SYSTEM$TYPEOF(BITOR(g, b)) FROM ta ORDER BY x");
        assertAnswer(
            "-13|NUMBER(38,0)[SB16] ;; -11|NUMBER(38,0)[SB16] ;; -7|NUMBER(38,0)[SB16] ;; -4|NUMBER(38,0)[SB16] "
                + ";; null|NUMBER(38,0)[SB16]",
            "SELECT BITNOT(b), SYSTEM$TYPEOF(BITNOT(b)) FROM ta ORDER BY x");
        assertAnswer(
            "13|NUMBER(38,0)[SB16] ;; 11|NUMBER(38,0)[SB16] ;; 6|NUMBER(38,0)[SB16] ;; 3|NUMBER(38,0)[SB16] ;; "
                + "null|NUMBER(38,0)[SB16]",
            "SELECT BIT_OR(g, b), SYSTEM$TYPEOF(BIT_OR(g, b)) FROM ta ORDER BY x");
    }

    @Test
    public void emptyGroupsAndDescendingPercentiles() {
        answer(
            "CREATE OR REPLACE TABLE ta (g INT, x NUMBER(10,2), y NUMBER(10,2), s VARCHAR, b INT)");
        answer(
            "INSERT INTO ta VALUES (1, 1.0, 2.0, 'a', 12), (1, 2.0, 4.5, 'b', 10), (2, 3.0, 5.0, NULL, 6), (2, "
                + "NULL, 7.0, 'c', NULL), (2, 5.0, NULL, 'a', 3)");
        answer(
            "CREATE OR REPLACE TABLE te (x NUMBER(10,2), y NUMBER(10,2), b INT)");
        answer(
            "CREATE OR REPLACE TABLE tb (n NUMBER(5,0), w NUMBER(20,0), f FLOAT, neg INT, d NUMBER(6,1))");
        answer(
            "INSERT INTO tb VALUES (300, 70000, 5.5, -5, 100.5), (17, 1, 2.0, 5, 3.4), (4, 4294967296, 9.0, -128, "
                + "0.5)");
        assertAnswer(
            "0|NUMBER(18,0)[SB8]",
            "SELECT REGR_COUNT(y, x), SYSTEM$TYPEOF(REGR_COUNT(y, x)) FROM ta WHERE g = 9");
        assertAnswer(
            "0|NUMBER(18,0)[SB1]",
            "SELECT REGR_COUNT(y, NULL), SYSTEM$TYPEOF(REGR_COUNT(y, NULL)) FROM ta");
        assertAnswer(
            "null|NUMBER(38,12)[SB16]",
            "SELECT KURTOSIS(x), SYSTEM$TYPEOF(KURTOSIS(x)) FROM ta WHERE g = 1");
        assertAnswer(
            "null|NUMBER(38,12)[SB1]",
            "SELECT KURTOSIS(NULL), SYSTEM$TYPEOF(KURTOSIS(NULL)) FROM ta");
        assertAnswer(
            "null|FLOAT[DOUBLE]",
            "SELECT SKEW(x), SYSTEM$TYPEOF(SKEW(x)) FROM te");
        assertAnswer(
            "null|NUMBER(38,12)[SB1]",
            "SELECT KURTOSIS(n), SYSTEM$TYPEOF(KURTOSIS(n)) FROM tb");
        assertAnswer(
            "5.00",
            "SELECT PERCENTILE_DISC(0.25) WITHIN GROUP (ORDER BY x DESC) FROM ta");
        assertAnswer(
            "3.50000",
            "SELECT PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY x DESC) FROM ta");
        assertAnswer(
            "3.00",
            "SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY x DESC) FROM ta");
        assertAnswer(
            "5.00",
            "SELECT PERCENTILE_DISC(0) WITHIN GROUP (ORDER BY x DESC) FROM ta");
        assertAnswer(
            "1.00",
            "SELECT PERCENTILE_DISC(1) WITHIN GROUP (ORDER BY x DESC) FROM ta");
        assertAnswer(
            "6.40000",
            "SELECT PERCENTILE_CONT(0.1) WITHIN GROUP (ORDER BY y DESC) FROM ta");
        assertAnswer(
            "6",
            "SELECT PERCENTILE_DISC(0.75) WITHIN GROUP (ORDER BY b DESC NULLS FIRST) FROM ta");
        assertAnswer(
            "1|4.50 ;; 2|7.00",
            "SELECT g, PERCENTILE_DISC(0.3) WITHIN GROUP (ORDER BY y DESC) FROM ta GROUP BY g ORDER BY g");
        assertAnswer(
            "1.00",
            "SELECT PERCENTILE_DISC(0.25) WITHIN GROUP (ORDER BY x ASC) FROM ta");
    }
}
