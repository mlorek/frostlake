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
 * PERCENTILE_CONT and MEDIAN over a DECFLOAT interpolate as the account does: the place and both weights are
 * doubles, each weight taken to 18 significant digits, and the interpolation is DECFLOAT arithmetic; a descending
 * order runs the same steps over the values reversed, and a grouped or partitioned call that has to interpolate
 * is "DecFloat not supported" (all live-verified).
 */
public class DecfloatPercentileWeightTest extends MeasuredCellsTestSupport {

    @Test
    public void aPercentileInterpolatesWithDoubleWeights() {
        assertAnswer("0.0999999999999999778x",
            "SELECT PERCENTILE_CONT(0.1) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' x UNION "
                + "ALL SELECT DECFLOAT '1')");
        assertAnswer("0.199999999999999956x",
            "SELECT PERCENTILE_CONT(0.2) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' x UNION "
                + "ALL SELECT DECFLOAT '1')");
        assertAnswer("0.300000000000000044x",
            "SELECT PERCENTILE_CONT(0.3) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' x UNION "
                + "ALL SELECT DECFLOAT '1')");
        assertAnswer("0.400000000000000022x",
            "SELECT PERCENTILE_CONT(0.4) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' x UNION "
                + "ALL SELECT DECFLOAT '1')");
        assertAnswer("0.599999999999999978x",
            "SELECT PERCENTILE_CONT(0.6) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' x UNION "
                + "ALL SELECT DECFLOAT '1')");
        assertAnswer("0.699999999999999956x",
            "SELECT PERCENTILE_CONT(0.7) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' x UNION "
                + "ALL SELECT DECFLOAT '1')");
        assertAnswer("0.800000000000000044x",
            "SELECT PERCENTILE_CONT(0.8) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' x UNION "
                + "ALL SELECT DECFLOAT '1')");
        assertAnswer("0.900000000000000022x",
            "SELECT PERCENTILE_CONT(0.9) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' x UNION "
                + "ALL SELECT DECFLOAT '1')");
        assertAnswer("0.12345600000000001x",
            "SELECT PERCENTILE_CONT(0.123456) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' x "
                + "UNION ALL SELECT DECFLOAT '1')");
        assertAnswer("0.998999999999999999x",
            "SELECT PERCENTILE_CONT(0.999) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' x "
                + "UNION ALL SELECT DECFLOAT '1')");
        assertAnswer("0.00100000000000000089x",
            "SELECT PERCENTILE_CONT(0.001) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' x "
                + "UNION ALL SELECT DECFLOAT '1')");
        assertAnswer("0.33333299999999999x",
            "SELECT PERCENTILE_CONT(0.333333) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' x "
                + "UNION ALL SELECT DECFLOAT '1')");
        assertAnswer("0.0500000000000000444x",
            "SELECT PERCENTILE_CONT(0.05) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' x "
                + "UNION ALL SELECT DECFLOAT '1')");
        assertAnswer("0.949999999999999956x",
            "SELECT PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' x "
                + "UNION ALL SELECT DECFLOAT '1')");
        assertAnswer("0.150000000000000022x",
            "SELECT PERCENTILE_CONT(0.15) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' x "
                + "UNION ALL SELECT DECFLOAT '1')");
        assertAnswer("0.449999999999999956x",
            "SELECT PERCENTILE_CONT(0.45) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' x "
                + "UNION ALL SELECT DECFLOAT '1')");
        assertAnswer("1.1999999999999999554x",
            "SELECT PERCENTILE_CONT(0.1) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT '1' x UNION "
                + "ALL SELECT DECFLOAT '3')");
        assertAnswer("-0.8000000000000000442x",
            "SELECT PERCENTILE_CONT(0.1) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT '-1' x "
                + "UNION ALL SELECT DECFLOAT '1')");
        assertAnswer("1.600000000000000088e100x",
            "SELECT PERCENTILE_CONT(0.3) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT '1e100' x "
                + "UNION ALL SELECT DECFLOAT '3e100')");
        assertAnswer("3.3333333333333325933333333333333333333e-2x",
            "SELECT PERCENTILE_CONT(0.1) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' x UNION "
                + "ALL SELECT DECFLOAT '1' / 3)");
        assertAnswer("2.3333333333333331866666666666666666666e-1x",
            "SELECT PERCENTILE_CONT(0.7) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' x UNION "
                + "ALL SELECT DECFLOAT '1' / 3)");
        assertAnswer("2.0987654222098763501222219530122221952e-1x",
            "SELECT PERCENTILE_CONT(0.1) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT "
                + "'0.12345678901234567890123456789012345678' x UNION ALL SELECT DECFLOAT "
                + "'0.98765432109876543210987654321098765432')");
        assertAnswer("9.0123456789012347577666669377766666938e-1x",
            "SELECT PERCENTILE_CONT(0.9) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT "
                + "'0.12345678901234567890123456789012345678' x UNION ALL SELECT DECFLOAT "
                + "'0.98765432109876543210987654321098765432')");
        assertAnswer("1.0000000000000000000000000000000000001x",
            "SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT '1' x UNION "
                + "ALL SELECT DECFLOAT '1.0000000000000000000000000000000000001')");
        assertAnswer("9.99999999999999778e48x",
            "SELECT PERCENTILE_CONT(0.1) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT '1e-50' x "
                + "UNION ALL SELECT DECFLOAT '1e50')");
        assertAnswer("0.300000000000000044x",
            "SELECT PERCENTILE_CONT(0.1) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' x UNION "
                + "ALL SELECT DECFLOAT '1' UNION ALL SELECT DECFLOAT '2' UNION ALL SELECT DECFLOAT '3')");
        assertAnswer("2.0999999999999996441x",
            "SELECT PERCENTILE_CONT(0.7) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' x UNION "
                + "ALL SELECT DECFLOAT '1' UNION ALL SELECT DECFLOAT '2' UNION ALL SELECT DECFLOAT '3')");
        assertAnswer("5.99999999999999978x",
            "SELECT PERCENTILE_CONT(0.3) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' x UNION "
                + "ALL SELECT DECFLOAT '10' UNION ALL SELECT DECFLOAT '30')");
        assertAnswer("20x",
            "SELECT PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' x "
                + "UNION ALL SELECT DECFLOAT '10' UNION ALL SELECT DECFLOAT '30')");
        assertAnswer("0.900000000000000022x",
            "SELECT PERCENTILE_CONT(0.1) WITHIN GROUP (ORDER BY x DESC)::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' x "
                + "UNION ALL SELECT DECFLOAT '1')");
        assertAnswer("6.99999999999999956x",
            "SELECT PERCENTILE_CONT(0.3) WITHIN GROUP (ORDER BY x DESC)::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' x "
                + "UNION ALL SELECT DECFLOAT '10')");
        assertAnswer("0.0999999999999999778x ;; 0.0999999999999999778x",
            "SELECT PERCENTILE_CONT(0.1) WITHIN GROUP (ORDER BY x) OVER ()::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' "
                + "x UNION ALL SELECT DECFLOAT '1')");
        assertAnswer("2.75308799999999998x ;; 2.75308799999999998x",
            "SELECT PERCENTILE_CONT(0.123456) WITHIN GROUP (ORDER BY x DESC) OVER ()::VARCHAR || 'x' FROM (SELECT "
                + "DECFLOAT '1' x UNION ALL SELECT DECFLOAT '3')");
        assertAnswer("0x",
            "SELECT PERCENTILE_CONT(0) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' x UNION "
                + "ALL SELECT DECFLOAT '1')");
        assertAnswer("1x",
            "SELECT PERCENTILE_CONT(1) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' x UNION "
                + "ALL SELECT DECFLOAT '1')");
        assertAnswer("0.0999999999999999778x",
            "SELECT PERCENTILE_CONT(0.1) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' x UNION "
                + "ALL SELECT NULL UNION ALL SELECT DECFLOAT '1')");
        answer("SELECT PERCENTILE_CONT(0.1::FLOAT) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT DECFLOAT "
            + "'0' x UNION ALL SELECT DECFLOAT '1')");
        answer("SELECT PERCENTILE_CONT(DECFLOAT '0.1') WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT "
            + "DECFLOAT '0' x UNION ALL SELECT DECFLOAT '1')");
        assertAnswer("0.1x",
            "SELECT PERCENTILE_CONT(0.1) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT 0::FLOAT x UNION ALL "
                + "SELECT 1::FLOAT)");
        assertAnswer("0.10000x",
            "SELECT PERCENTILE_CONT(0.1) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT 0::NUMBER(10,2) x "
                + "UNION ALL SELECT 1::NUMBER(10,2))");
        assertAnswer("6.6666666666666666666666666666666666667e-1x",
            "SELECT MEDIAN(x)::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' x UNION ALL SELECT DECFLOAT '1' / 3 UNION "
                + "ALL SELECT DECFLOAT '1' UNION ALL SELECT DECFLOAT '2')");
        assertAnswer("1x",
            "SELECT PERCENTILE_CONT(0.1) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT SEQ4()::DECFLOAT x "
                + "FROM TABLE(GENERATOR(ROWCOUNT => 11)))");
        assertAnswer("3x",
            "SELECT PERCENTILE_CONT(0.3) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT SEQ4()::DECFLOAT x "
                + "FROM TABLE(GENERATOR(ROWCOUNT => 11)))");
        assertAnswer("1.5x",
            "SELECT PERCENTILE_CONT(0.15) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT SEQ4()::DECFLOAT x "
                + "FROM TABLE(GENERATOR(ROWCOUNT => 11)))");
        assertAnswer("3.300000000000000266x",
            "SELECT PERCENTILE_CONT(0.33) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT SEQ4()::DECFLOAT x "
                + "FROM TABLE(GENERATOR(ROWCOUNT => 11)))");
        assertAnswer("7.700000000000000178x",
            "SELECT PERCENTILE_CONT(0.77) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT SEQ4()::DECFLOAT x "
                + "FROM TABLE(GENERATOR(ROWCOUNT => 11)))");
        assertAnswer("1.23455999999999988x",
            "SELECT PERCENTILE_CONT(0.123456) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT "
                + "SEQ4()::DECFLOAT x FROM TABLE(GENERATOR(ROWCOUNT => 11)))");
        assertAnswer("9.99000000000000021156x",
            "SELECT PERCENTILE_CONT(0.999) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT SEQ4()::DECFLOAT x "
                + "FROM TABLE(GENERATOR(ROWCOUNT => 11)))");
        assertAnswer("539x",
            "SELECT PERCENTILE_CONT(0.77) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT (SEQ4() * "
                + "7)::DECFLOAT x FROM TABLE(GENERATOR(ROWCOUNT => 101)))");
        assertAnswer("86.419199999999994687x",
            "SELECT PERCENTILE_CONT(0.123456) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT (SEQ4() * "
                + "7)::DECFLOAT x FROM TABLE(GENERATOR(ROWCOUNT => 101)))");
        assertAnswer("0.6999999999999998446x",
            "SELECT PERCENTILE_CONT(0.001) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT (SEQ4() * "
                + "7)::DECFLOAT x FROM TABLE(GENERATOR(ROWCOUNT => 101)))");
        assertAnswer("Table PG successfully created.", "CREATE TABLE pg (g INT, x DECFLOAT)");
        assertAnswer("5",
            "INSERT INTO pg VALUES (1, DECFLOAT '0'), (1, DECFLOAT '1'), (2, DECFLOAT '1e100'), (2, DECFLOAT "
                + "'3e100'), (2, NULL)");
        assertAnswer("REFUSED DecFloat not supported",
            "SELECT g, PERCENTILE_CONT(0.1) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM pg GROUP BY g ORDER BY g");
        assertAnswer("REFUSED DecFloat not supported",
            "SELECT DISTINCT g, PERCENTILE_CONT(0.3) WITHIN GROUP (ORDER BY x) OVER (PARTITION BY g)::VARCHAR || 'x' "
                + "FROM pg ORDER BY g");
    }

    @Test
    public void aDescendingOrderAndAGroupAreAsTheAccountTakesThem() {
        assertAnswer("0.900000000000000355x",
            "SELECT PERCENTILE_CONT(0.7) WITHIN GROUP (ORDER BY x DESC)::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' x "
                + "UNION ALL SELECT DECFLOAT '1' UNION ALL SELECT DECFLOAT '2' UNION ALL SELECT DECFLOAT '3')");
        assertAnswer("0.299999999999999822x",
            "SELECT PERCENTILE_CONT(0.9) WITHIN GROUP (ORDER BY x DESC)::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' x "
                + "UNION ALL SELECT DECFLOAT '1' UNION ALL SELECT DECFLOAT '2' UNION ALL SELECT DECFLOAT '3')");
        assertAnswer("6.699999999999999734x",
            "SELECT PERCENTILE_CONT(0.33) WITHIN GROUP (ORDER BY x DESC)::VARCHAR || 'x' FROM (SELECT "
                + "SEQ4()::DECFLOAT x FROM TABLE(GENERATOR(ROWCOUNT => 11)))");
        assertAnswer("8.76544000000000012x",
            "SELECT PERCENTILE_CONT(0.123456) WITHIN GROUP (ORDER BY x DESC)::VARCHAR || 'x' FROM (SELECT "
                + "SEQ4()::DECFLOAT x FROM TABLE(GENERATOR(ROWCOUNT => 11)))");
        assertAnswer("0.900000000000000355x ;; 0.900000000000000355x ;; 0.900000000000000355x ;; 0.900000000000000355x",
            "SELECT PERCENTILE_CONT(0.7) WITHIN GROUP (ORDER BY x DESC) OVER ()::VARCHAR || 'x' FROM (SELECT "
                + "DECFLOAT '0' x UNION ALL SELECT DECFLOAT '1' UNION ALL SELECT DECFLOAT '2' UNION ALL SELECT "
                + "DECFLOAT '3')");
        assertAnswer("Table PG successfully created.", "CREATE TABLE pg (g INT, x DECFLOAT)");
        assertAnswer("5",
            "INSERT INTO pg VALUES (1, DECFLOAT '0'), (1, DECFLOAT '1'), (2, DECFLOAT '1e100'), (2, DECFLOAT "
                + "'3e100'), (2, NULL)");
        assertAnswer("Table PG2 successfully created.", "CREATE TABLE pg2 (g INT, x DECFLOAT)");
        assertAnswer("4",
            "INSERT INTO pg2 VALUES (1, DECFLOAT '0'), (1, DECFLOAT '1'), (2, DECFLOAT '1e100'), (2, DECFLOAT "
                + "'3e100')");
        assertAnswer("Table PGE successfully created.", "CREATE TABLE pge (g INT, x DECFLOAT)");
        assertAnswer("0.300000000000000044x",
            "SELECT PERCENTILE_CONT(0.1) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM pg");
        assertAnswer("5e99x", "SELECT MEDIAN(x)::VARCHAR || 'x' FROM pg");
        assertAnswer("0.5x", "SELECT MEDIAN(x)::VARCHAR || 'x' FROM pg2 WHERE g = 1");
        assertAnswer("REFUSED DecFloat not supported",
            "SELECT g, MEDIAN(x)::VARCHAR || 'x' FROM pg GROUP BY g ORDER BY g");
        assertAnswer("REFUSED DecFloat not supported",
            "SELECT g, MEDIAN(x)::VARCHAR || 'x' FROM pg2 GROUP BY g ORDER BY g");
        assertAnswer("REFUSED DecFloat not supported",
            "SELECT g, PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM pg2 GROUP BY g ORDER BY g");
        assertAnswer("", "SELECT g, MEDIAN(x) FROM pge GROUP BY g");
        assertAnswer("REFUSED DecFloat not supported",
            "SELECT g, PERCENTILE_CONT(0.1) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT 1 g, DECFLOAT '0' "
                + "x UNION ALL SELECT 1, DECFLOAT '1') GROUP BY g");
        assertAnswer("REFUSED DecFloat not supported",
            "SELECT PERCENTILE_CONT(0.1) WITHIN GROUP (ORDER BY x) OVER (PARTITION BY g)::VARCHAR || 'x' FROM "
                + "(SELECT 1 g, DECFLOAT '0' x UNION ALL SELECT 1, DECFLOAT '1')");
        assertAnswer("REFUSED DecFloat not supported",
            "SELECT g, MEDIAN(x)::VARCHAR || 'x' FROM (SELECT g, x FROM pg2) GROUP BY g ORDER BY g");
        assertAnswer("REFUSED DecFloat not supported", "SELECT MEDIAN(x)::VARCHAR || 'x' FROM pg2 GROUP BY 'k'");
        assertAnswer("REFUSED DecFloat not supported",
            "SELECT MEDIAN(x) OVER (PARTITION BY g)::VARCHAR || 'x' FROM pg2 ORDER BY 1");
        assertAnswer("5e99x", "SELECT MEDIAN(x) OVER ()::VARCHAR || 'x' FROM pg2 LIMIT 1");
        assertAnswer("0.300000000000000044x",
            "SELECT PERCENTILE_CONT(0.1) WITHIN GROUP (ORDER BY x) OVER ()::VARCHAR || 'x' FROM pg2 LIMIT 1");
        assertAnswer("1|1|0.5|0|1|2 ;; 2|4e100|2e100|1e100|3e100|2",
            "SELECT g, SUM(x)::VARCHAR, AVG(x)::VARCHAR, MIN(x)::VARCHAR, MAX(x)::VARCHAR, COUNT(x) FROM pg2 GROUP "
                + "BY g ORDER BY g");
        assertAnswer("1|0 ;; 2|1e100",
            "SELECT g, PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY x)::VARCHAR FROM pg2 GROUP BY g ORDER BY g");
        assertAnswer("1|0 ;; 2|1e100", "SELECT g, MODE(x)::VARCHAR FROM pg2 GROUP BY g ORDER BY g");
        assertAnswer("1|7.0710678118654752440084436210484903928e-1x ;; 2|1.4142135623730950488016887242096980786e100x",
            "SELECT g, STDDEV(x)::VARCHAR || 'x' FROM pg2 GROUP BY g ORDER BY g");
        assertAnswer("REFUSED Decfloat out of representable range, operation: TO_NUMBER(1e100, 38, 0)",
            "SELECT g, MEDIAN(x::NUMBER(38,0) / 1e90)::VARCHAR FROM pg2 GROUP BY g ORDER BY g");
    }

    @Test
    public void aWindowCountsADescendingOrderFromTheGreatestValue() {
        assertAnswer("2.250",
            "SELECT PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY x DESC) OVER ()::VARCHAR FROM (SELECT 0 x UNION ALL "
                + "SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) LIMIT 1");
        assertAnswer("3",
            "SELECT PERCENTILE_DISC(0.25) WITHIN GROUP (ORDER BY x DESC) OVER ()::VARCHAR FROM (SELECT 0 x UNION ALL "
                + "SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) LIMIT 1");
        assertAnswer("2.250",
            "SELECT PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY x DESC)::VARCHAR FROM (SELECT 0 x UNION ALL SELECT "
                + "1 UNION ALL SELECT 2 UNION ALL SELECT 3)");
        assertAnswer("1.2500 ;; 1.2500 ;; 7.0000",
            "SELECT PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY x DESC) OVER (PARTITION BY g)::VARCHAR FROM (SELECT "
                + "1 g, 0.5 x UNION ALL SELECT 1, 1.5 UNION ALL SELECT 2, 7) ORDER BY 1");
        assertAnswer("2.25",
            "SELECT PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY x DESC) OVER ()::VARCHAR FROM (SELECT 0::FLOAT x "
                + "UNION ALL SELECT 1::FLOAT UNION ALL SELECT 2::FLOAT UNION ALL SELECT 3::FLOAT) LIMIT 1");
        assertAnswer("1.6666666666666666666666666666666666667e-1x",
            "SELECT MEDIAN(x) OVER ()::VARCHAR || 'x' FROM (SELECT DECFLOAT '0' x UNION ALL SELECT DECFLOAT '1' / 3) "
                + "LIMIT 1");
        assertAnswer("", "SELECT g, MEDIAN(x)::VARCHAR FROM (SELECT 1 g, DECFLOAT '1' x WHERE FALSE) GROUP BY g");
        assertAnswer("1", "SELECT g FROM (SELECT 1 g, DECFLOAT '1' x) GROUP BY g HAVING MEDIAN(x) > 0");
        assertAnswer("1|null", "SELECT g, MEDIAN(x)::VARCHAR FROM (SELECT 1 g, NULL::DECFLOAT x) GROUP BY g");
    }

    @Test
    public void aGroupIsRefusedOnlyAnInterpolation() {
        assertAnswer("1|1x",
            "SELECT g, MEDIAN(x)::VARCHAR || 'x' FROM (SELECT 1 g, DECFLOAT '1' x) GROUP BY g ORDER BY g");
        assertAnswer("REFUSED DecFloat not supported",
            "SELECT g, MEDIAN(x)::VARCHAR || 'x' FROM (SELECT 1 g, DECFLOAT '1' x UNION ALL SELECT 1, DECFLOAT '3') "
                + "GROUP BY g ORDER BY g");
        assertAnswer("1|2x",
            "SELECT g, MEDIAN(x)::VARCHAR || 'x' FROM (SELECT 1 g, DECFLOAT '1' x UNION ALL SELECT 1, DECFLOAT '2' "
                + "UNION ALL SELECT 1, DECFLOAT '3') GROUP BY g ORDER BY g");
        assertAnswer("REFUSED DecFloat not supported",
            "SELECT g, MEDIAN(x)::VARCHAR || 'x' FROM (SELECT 1 g, DECFLOAT '2' x UNION ALL SELECT 1, DECFLOAT '2') "
                + "GROUP BY g ORDER BY g");
        assertAnswer("1|1x",
            "SELECT g, PERCENTILE_CONT(0) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT 1 g, DECFLOAT '1' x "
                + "UNION ALL SELECT 1, DECFLOAT '3') GROUP BY g ORDER BY g");
        assertAnswer("1|3x",
            "SELECT g, PERCENTILE_CONT(1) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT 1 g, DECFLOAT '1' x "
                + "UNION ALL SELECT 1, DECFLOAT '3') GROUP BY g ORDER BY g");
        assertAnswer("1|2x",
            "SELECT g, PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT 1 g, DECFLOAT '1' "
                + "x UNION ALL SELECT 1, DECFLOAT '2' UNION ALL SELECT 1, DECFLOAT '3') GROUP BY g ORDER BY g");
        assertAnswer("REFUSED DecFloat not supported",
            "SELECT g, PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT 1 g, DECFLOAT "
                + "'1' x UNION ALL SELECT 1, DECFLOAT '2' UNION ALL SELECT 1, DECFLOAT '3') GROUP BY g ORDER BY g");
        assertAnswer("REFUSED DecFloat not supported",
            "SELECT g, MEDIAN(x)::VARCHAR || 'x' FROM (SELECT 1 g, DECFLOAT '1' x UNION ALL SELECT 2, DECFLOAT '1' "
                + "UNION ALL SELECT 2, DECFLOAT '3') GROUP BY g ORDER BY g");
        assertAnswer("1|1x",
            "SELECT g, MEDIAN(x)::VARCHAR || 'x' FROM (SELECT 1 g, DECFLOAT '1' x UNION ALL SELECT 1, "
                + "NULL::DECFLOAT) GROUP BY g ORDER BY g");
        assertAnswer("REFUSED DecFloat not supported",
            "SELECT g, MEDIAN(x)::VARCHAR || 'x' FROM (SELECT 1 g, DECFLOAT '1' x UNION ALL SELECT 1, NULL::DECFLOAT "
                + "UNION ALL SELECT 1, DECFLOAT '5') GROUP BY g ORDER BY g");
        assertAnswer("REFUSED DecFloat not supported",
            "SELECT g, PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY x)::VARCHAR || 'x' FROM (SELECT 1 g, DECFLOAT '1' "
                + "x UNION ALL SELECT 1, DECFLOAT '3') GROUP BY g ORDER BY g");
        assertAnswer("REFUSED DecFloat not supported",
            "SELECT g FROM (SELECT 1 g, DECFLOAT '1' x UNION ALL SELECT 1, DECFLOAT '3') GROUP BY g HAVING MEDIAN(x) "
                + "> 0");
        assertAnswer("1x", "SELECT MEDIAN(x)::VARCHAR || 'x' FROM (SELECT 1 g, DECFLOAT '1' x) GROUP BY 'k'");
        assertAnswer("REFUSED DecFloat not supported",
            "SELECT MEDIAN(x)::VARCHAR || 'x' FROM (SELECT 1 g, DECFLOAT '1' x UNION ALL SELECT 1, DECFLOAT '3') "
                + "GROUP BY 'k'");
        assertAnswer("1|1x ;; 2|5x",
            "SELECT g, MEDIAN(x) OVER (PARTITION BY g)::VARCHAR || 'x' FROM (SELECT 1 g, DECFLOAT '1' x UNION ALL "
                + "SELECT 2, DECFLOAT '5') ORDER BY g");
        assertAnswer("REFUSED DecFloat not supported",
            "SELECT g, MEDIAN(x) OVER (PARTITION BY g)::VARCHAR || 'x' FROM (SELECT 1 g, DECFLOAT '1' x UNION ALL "
                + "SELECT 1, DECFLOAT '3') ORDER BY g");
        assertAnswer("1|1x ;; 1|1x",
            "SELECT g, PERCENTILE_CONT(0) WITHIN GROUP (ORDER BY x) OVER (PARTITION BY g)::VARCHAR || 'x' FROM "
                + "(SELECT 1 g, DECFLOAT '1' x UNION ALL SELECT 1, DECFLOAT '3') ORDER BY g");
        assertAnswer("1|2x ;; 1|2x ;; 1|2x",
            "SELECT g, MEDIAN(x) OVER (PARTITION BY g)::VARCHAR || 'x' FROM (SELECT 1 g, DECFLOAT '1' x UNION ALL "
                + "SELECT 1, DECFLOAT '2' UNION ALL SELECT 1, DECFLOAT '3') ORDER BY g");
        assertAnswer("Table PG3 successfully created.", "CREATE TABLE pg3 (g INT, x DECFLOAT)");
        assertAnswer("3", "INSERT INTO pg3 VALUES (1, DECFLOAT '1'), (2, DECFLOAT '2'), (2, NULL)");
        assertAnswer("1|1x ;; 2|2x", "SELECT g, MEDIAN(x)::VARCHAR || 'x' FROM pg3 GROUP BY g ORDER BY g");
        assertAnswer("2", "INSERT INTO pg3 VALUES (3, DECFLOAT '7'), (3, DECFLOAT '7')");
        assertAnswer("REFUSED DecFloat not supported",
            "SELECT g, MEDIAN(x)::VARCHAR || 'x' FROM pg3 GROUP BY g ORDER BY g");
        assertAnswer("1|1x ;; 2|2x", "SELECT g, MEDIAN(x)::VARCHAR || 'x' FROM pg3 WHERE g < 3 GROUP BY g ORDER BY g");
    }
}
