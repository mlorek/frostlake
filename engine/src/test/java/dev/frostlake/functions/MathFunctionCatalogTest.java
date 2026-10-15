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
 * The numeric functions the catalog lists, by what they answer and what they declare: the trigonometric
 * family and its domains, logarithms, roots and powers and the sentences a value outside a domain is refused
 * in, the remainder and the sign, FACTORIAL's range and rounding, HAVERSINE, WIDTH_BUCKET, the bitwise
 * functions and the random ones (live-verified).
 */
public class MathFunctionCatalogTest extends MeasuredCellsTestSupport {

    @Test
    public void trigonometryAndAngles() {
        assertAnswer(
            "1.0471975511965979|FLOAT[DOUBLE]",
            "SELECT ACOS(0.5), SYSTEM$TYPEOF(ACOS(0.5))");
        assertAnswer(
            "1.5707963267948966|FLOAT[DOUBLE]",
            "SELECT ASIN(1), SYSTEM$TYPEOF(ASIN(1))");
        assertAnswer(
            "0.7853981633974483|FLOAT[DOUBLE]",
            "SELECT ATAN(1), SYSTEM$TYPEOF(ATAN(1))");
        assertAnswer(
            "REFUSED Invalid floating point operation: acos(2)",
            "SELECT ACOS(2), SYSTEM$TYPEOF(ACOS(2))");
        assertAnswer(
            "null|FLOAT[DOUBLE]",
            "SELECT ASIN(NULL), SYSTEM$TYPEOF(ASIN(NULL))");
        assertAnswer(
            "0.7853981633974483|FLOAT[DOUBLE]",
            "SELECT ATAN('1'), SYSTEM$TYPEOF(ATAN('1'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ACOS': (BOOLEAN)",
            "SELECT ACOS(TRUE), SYSTEM$TYPEOF(ACOS(TRUE))");
        assertAnswer(
            "0.7853981633974483|FLOAT[DOUBLE]",
            "SELECT ATAN2(1, 1), SYSTEM$TYPEOF(ATAN2(1, 1))");
        assertAnswer(
            "0.0|FLOAT[DOUBLE]",
            "SELECT ATAN2(0, 0), SYSTEM$TYPEOF(ATAN2(0, 0))");
        assertAnswer(
            "null|FLOAT[DOUBLE]",
            "SELECT ATAN2(NULL, 1), SYSTEM$TYPEOF(ATAN2(NULL, 1))");
        assertAnswer(
            "2.356194490192345|FLOAT[DOUBLE]",
            "SELECT ATAN2(1, -1), SYSTEM$TYPEOF(ATAN2(1, -1))");
        assertAnswer(
            "1.0|FLOAT[DOUBLE]",
            "SELECT COS(0), SYSTEM$TYPEOF(COS(0))");
        assertAnswer(
            "0.0|FLOAT[DOUBLE]",
            "SELECT SIN(0), SYSTEM$TYPEOF(SIN(0))");
        assertAnswer(
            "0.0|FLOAT[DOUBLE]",
            "SELECT TAN(0), SYSTEM$TYPEOF(TAN(0))");
        assertAnswer(
            "0.6420926159343306|FLOAT[DOUBLE]",
            "SELECT COT(1), SYSTEM$TYPEOF(COT(1))");
        assertAnswer(
            "Infinity|FLOAT[DOUBLE]",
            "SELECT COT(0), SYSTEM$TYPEOF(COT(0))");
        assertAnswer(
            "null|FLOAT[DOUBLE]",
            "SELECT SIN(NULL), SYSTEM$TYPEOF(SIN(NULL))");
        assertAnswer(
            "1.0|FLOAT[DOUBLE]",
            "SELECT COS('0'), SYSTEM$TYPEOF(COS('0'))");
        assertAnswer(
            "180.0|FLOAT[DOUBLE]",
            "SELECT DEGREES(PI()), SYSTEM$TYPEOF(DEGREES(PI()))");
        assertAnswer(
            "3.141592653589793|FLOAT[DOUBLE]",
            "SELECT RADIANS(180), SYSTEM$TYPEOF(RADIANS(180))");
        assertAnswer(
            "null|FLOAT[DOUBLE]",
            "SELECT DEGREES(NULL), SYSTEM$TYPEOF(DEGREES(NULL))");
        assertAnswer(
            "0.026179938779914945|FLOAT[DOUBLE]",
            "SELECT RADIANS(1.5), SYSTEM$TYPEOF(RADIANS(1.5))");
        assertAnswer(
            "3.141592653589793|FLOAT[DOUBLE]",
            "SELECT PI(), SYSTEM$TYPEOF(PI())");
        assertAnswer(
            "6.283185307179586|FLOAT[DOUBLE]",
            "SELECT PI() * 2, SYSTEM$TYPEOF(PI() * 2)");
        assertAnswer(
            "3.14|FLOAT[DOUBLE]",
            "SELECT ROUND(PI(), 2), SYSTEM$TYPEOF(ROUND(PI(), 2))");
    }

    @Test
    public void logarithmsRootsAndPowers() {
        assertAnswer(
            "2.718281828459045|FLOAT[DOUBLE]",
            "SELECT EXP(1), SYSTEM$TYPEOF(EXP(1))");
        assertAnswer(
            "1.0|FLOAT[DOUBLE]",
            "SELECT EXP(0), SYSTEM$TYPEOF(EXP(0))");
        assertAnswer(
            "0.0|FLOAT[DOUBLE]",
            "SELECT LN(1), SYSTEM$TYPEOF(LN(1))");
        assertAnswer(
            "REFUSED Invalid floating point operation: log(e,0)",
            "SELECT LN(0), SYSTEM$TYPEOF(LN(0))");
        assertAnswer(
            "REFUSED Invalid floating point operation: log(e,-1)",
            "SELECT LN(-1), SYSTEM$TYPEOF(LN(-1))");
        assertAnswer(
            "2.0|FLOAT[DOUBLE]",
            "SELECT LOG(10, 100), SYSTEM$TYPEOF(LOG(10, 100))");
        assertAnswer(
            "3.0|FLOAT[DOUBLE]",
            "SELECT LOG(2, 8), SYSTEM$TYPEOF(LOG(2, 8))");
        assertAnswer(
            "REFUSED Invalid floating point operation: log(1,5)",
            "SELECT LOG(1, 5), SYSTEM$TYPEOF(LOG(1, 5))");
        assertAnswer(
            "REFUSED Invalid floating point operation: log(10,0)",
            "SELECT LOG(10, 0), SYSTEM$TYPEOF(LOG(10, 0))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[LOG(100)], expected 2, got 1",
            "SELECT LOG(100), SYSTEM$TYPEOF(LOG(100))");
        assertAnswer(
            "null|FLOAT[DOUBLE]",
            "SELECT EXP(NULL), SYSTEM$TYPEOF(EXP(NULL))");
        assertAnswer(
            "null|FLOAT[DOUBLE]",
            "SELECT LOG(NULL, 2), SYSTEM$TYPEOF(LOG(NULL, 2))");
        assertAnswer(
            "Infinity|FLOAT[DOUBLE]",
            "SELECT EXP(1000), SYSTEM$TYPEOF(EXP(1000))");
        assertAnswer(
            "4.0|FLOAT[DOUBLE]",
            "SELECT SQRT(16), SYSTEM$TYPEOF(SQRT(16))");
        assertAnswer(
            "1.4142135623730951|FLOAT[DOUBLE]",
            "SELECT SQRT(2), SYSTEM$TYPEOF(SQRT(2))");
        assertAnswer(
            "REFUSED Invalid floating point operation: sqrt(-1)",
            "SELECT SQRT(-1), SYSTEM$TYPEOF(SQRT(-1))");
        assertAnswer(
            "3.0000000000000004|FLOAT[DOUBLE]",
            "SELECT CBRT(27), SYSTEM$TYPEOF(CBRT(27))");
        assertAnswer(
            "-2.0|FLOAT[DOUBLE]",
            "SELECT CBRT(-8), SYSTEM$TYPEOF(CBRT(-8))");
        assertAnswer(
            "9.0|FLOAT[DOUBLE]",
            "SELECT SQUARE(3), SYSTEM$TYPEOF(SQUARE(3))");
        assertAnswer(
            "2.25|FLOAT[DOUBLE]",
            "SELECT SQUARE(1.5), SYSTEM$TYPEOF(SQUARE(1.5))");
        assertAnswer(
            "null|FLOAT[DOUBLE]",
            "SELECT SQRT(NULL), SYSTEM$TYPEOF(SQRT(NULL))");
        assertAnswer(
            "9.0|FLOAT[DOUBLE]",
            "SELECT SQUARE('3'), SYSTEM$TYPEOF(SQUARE('3'))");
        assertAnswer(
            "1024.0|FLOAT[DOUBLE]",
            "SELECT POWER(2, 10), SYSTEM$TYPEOF(POWER(2, 10))");
        assertAnswer(
            "0.5|FLOAT[DOUBLE]",
            "SELECT POWER(2, -1), SYSTEM$TYPEOF(POWER(2, -1))");
        assertAnswer(
            "1.0|FLOAT[DOUBLE]",
            "SELECT POWER(0, 0), SYSTEM$TYPEOF(POWER(0, 0))");
        assertAnswer(
            "REFUSED Invalid floating point operation: pow(-8, 0.333333)",
            "SELECT POWER(-8, 1/3), SYSTEM$TYPEOF(POWER(-8, 1/3))");
        assertAnswer(
            "null|FLOAT[DOUBLE]",
            "SELECT POWER(2, NULL), SYSTEM$TYPEOF(POWER(2, NULL))");
        assertAnswer(
            "Infinity|FLOAT[DOUBLE]",
            "SELECT POWER(10, 400), SYSTEM$TYPEOF(POWER(10, 400))");
        assertAnswer(
            "8.0|FLOAT[DOUBLE]",
            "SELECT POW(2, 3), SYSTEM$TYPEOF(POW(2, 3))");
    }

    @Test
    public void remainderSignAndFactorial() {
        assertAnswer(
            "1|NUMBER(2,0)[SB1]",
            "SELECT MOD(7, 3), SYSTEM$TYPEOF(MOD(7, 3))");
        assertAnswer(
            "-1|NUMBER(2,0)[SB1]",
            "SELECT MOD(-7, 3), SYSTEM$TYPEOF(MOD(-7, 3))");
        assertAnswer(
            "1|NUMBER(2,0)[SB1]",
            "SELECT MOD(7, -3), SYSTEM$TYPEOF(MOD(7, -3))");
        assertAnswer(
            "1.5|NUMBER(3,1)[SB1]",
            "SELECT MOD(7.5, 2), SYSTEM$TYPEOF(MOD(7.5, 2))");
        assertAnswer(
            "REFUSED Division by zero",
            "SELECT MOD(7, 0), SYSTEM$TYPEOF(MOD(7, 0))");
        assertAnswer(
            "null|NUMBER(2,0)[SB1]",
            "SELECT MOD(NULL, 3), SYSTEM$TYPEOF(MOD(NULL, 3))");
        assertAnswer(
            "2.0|FLOAT[DOUBLE]",
            "SELECT MOD(7, 2.5::FLOAT), SYSTEM$TYPEOF(MOD(7, 2.5::FLOAT))");
        assertAnswer(
            "1|NUMBER(2,0)[SB1]",
            "SELECT MOD('7', 3), SYSTEM$TYPEOF(MOD('7', 3))");
        assertAnswer(
            "-1|NUMBER(2,0)[SB1]",
            "SELECT SIGN(-5), SYSTEM$TYPEOF(SIGN(-5))");
        assertAnswer(
            "0|NUMBER(2,0)[SB1]",
            "SELECT SIGN(0), SYSTEM$TYPEOF(SIGN(0))");
        assertAnswer(
            "1|NUMBER(2,0)[SB1]",
            "SELECT SIGN(2.5), SYSTEM$TYPEOF(SIGN(2.5))");
        assertAnswer(
            "null|NUMBER(2,0)[SB1]",
            "SELECT SIGN(NULL), SYSTEM$TYPEOF(SIGN(NULL))");
        assertAnswer(
            "0.0|FLOAT[DOUBLE]",
            "SELECT SIGN(-0.0::FLOAT), SYSTEM$TYPEOF(SIGN(-0.0::FLOAT))");
        assertAnswer(
            "-1.0|FLOAT[DOUBLE]",
            "SELECT SIGN('-3'), SYSTEM$TYPEOF(SIGN('-3'))");
        assertAnswer(
            "1|NUMBER(37,0)[SB1]",
            "SELECT FACTORIAL(0), SYSTEM$TYPEOF(FACTORIAL(0))");
        assertAnswer(
            "120|NUMBER(37,0)[SB1]",
            "SELECT FACTORIAL(5), SYSTEM$TYPEOF(FACTORIAL(5))");
        assertAnswer(
            "8683317618811886495518194401280000000|NUMBER(37,0)[SB16]",
            "SELECT FACTORIAL(33), SYSTEM$TYPEOF(FACTORIAL(33))");
        assertAnswer(
            "REFUSED Numeric value is out of range, error: 34 is too large for factorial function.",
            "SELECT FACTORIAL(34), SYSTEM$TYPEOF(FACTORIAL(34))");
        assertAnswer(
            "REFUSED Numeric value is out of range, error: Input of factorial function can't be negative. -1 is "
                + "invalid.",
            "SELECT FACTORIAL(-1), SYSTEM$TYPEOF(FACTORIAL(-1))");
        assertAnswer(
            "null|NUMBER(37,0)[SB16]",
            "SELECT FACTORIAL(NULL), SYSTEM$TYPEOF(FACTORIAL(NULL))");
        assertAnswer(
            "6|NUMBER(37,0)[SB16]",
            "SELECT FACTORIAL(2.5), SYSTEM$TYPEOF(FACTORIAL(2.5))");
    }

    @Test
    public void distanceBucketsAndZeros() {
        assertAnswer(
            "0.0|FLOAT[DOUBLE]",
            "SELECT HAVERSINE(0, 0, 0, 0), SYSTEM$TYPEOF(HAVERSINE(0, 0, 0, 0))");
        assertAnswer(
            "3936.3850963892937|FLOAT[DOUBLE]",
            "SELECT HAVERSINE(40.7127, -74.0059, 34.0500, -118.2500), SYSTEM$TYPEOF(HAVERSINE(40.7127, -74.0059, "
                + "34.0500, -118.2500))");
        assertAnswer(
            "null|FLOAT[DOUBLE]",
            "SELECT HAVERSINE(0, 0, NULL, 0), SYSTEM$TYPEOF(HAVERSINE(0, 0, NULL, 0))");
        assertAnswer(
            "20015.086796020572|FLOAT[DOUBLE]",
            "SELECT HAVERSINE(0, 0, 0, 180), SYSTEM$TYPEOF(HAVERSINE(0, 0, 0, 180))");
        assertAnswer(
            "3|NUMBER(2,0)[SB1]",
            "SELECT WIDTH_BUCKET(5, 0, 10, 5), SYSTEM$TYPEOF(WIDTH_BUCKET(5, 0, 10, 5))");
        assertAnswer(
            "0|NUMBER(2,0)[SB1]",
            "SELECT WIDTH_BUCKET(-1, 0, 10, 5), SYSTEM$TYPEOF(WIDTH_BUCKET(-1, 0, 10, 5))");
        assertAnswer(
            "6|NUMBER(2,0)[SB1]",
            "SELECT WIDTH_BUCKET(10, 0, 10, 5), SYSTEM$TYPEOF(WIDTH_BUCKET(10, 0, 10, 5))");
        assertAnswer(
            "6|NUMBER(2,0)[SB1]",
            "SELECT WIDTH_BUCKET(11, 0, 10, 5), SYSTEM$TYPEOF(WIDTH_BUCKET(11, 0, 10, 5))");
        assertAnswer(
            "3|NUMBER(2,0)[SB1]",
            "SELECT WIDTH_BUCKET(5, 10, 0, 5), SYSTEM$TYPEOF(WIDTH_BUCKET(5, 10, 0, 5))");
        assertAnswer(
            "REFUSED Invalid argument for width bucket function, want 0 (num_buckets) > 0 and 0 (min_value) != 10 "
                + "(max_value)",
            "SELECT WIDTH_BUCKET(5, 0, 10, 0), SYSTEM$TYPEOF(WIDTH_BUCKET(5, 0, 10, 0))");
        assertAnswer(
            "null|NUMBER(2,0)[SB1]",
            "SELECT WIDTH_BUCKET(NULL, 0, 10, 5), SYSTEM$TYPEOF(WIDTH_BUCKET(NULL, 0, 10, 5))");
        assertAnswer(
            "REFUSED Invalid argument for width bucket function, want 5 (num_buckets) > 0 and 0 (min_value) != 0 "
                + "(max_value)",
            "SELECT WIDTH_BUCKET(5, 0, 0, 5), SYSTEM$TYPEOF(WIDTH_BUCKET(5, 0, 0, 5))");
        assertAnswer(
            "0|NUMBER(2,0)[SB1]",
            "SELECT ZEROIFNULL(NULL), SYSTEM$TYPEOF(ZEROIFNULL(NULL))");
        assertAnswer(
            "5|NUMBER(2,0)[SB1]",
            "SELECT ZEROIFNULL(5), SYSTEM$TYPEOF(ZEROIFNULL(5))");
        assertAnswer(
            "1.5|NUMBER(3,1)[SB1]",
            "SELECT ZEROIFNULL(1.50), SYSTEM$TYPEOF(ZEROIFNULL(1.50))");
        assertAnswer(
            "null|NUMBER(1,0)[SB1]",
            "SELECT NULLIFZERO(0), SYSTEM$TYPEOF(NULLIFZERO(0))");
        assertAnswer(
            "5|NUMBER(1,0)[SB1]",
            "SELECT NULLIFZERO(5), SYSTEM$TYPEOF(NULLIFZERO(5))");
        assertAnswer(
            "null|NUMBER(1,0)[SB1]",
            "SELECT NULLIFZERO(0.0), SYSTEM$TYPEOF(NULLIFZERO(0.0))");
        assertAnswer(
            "7.0|FLOAT[DOUBLE]",
            "SELECT ZEROIFNULL('7'), SYSTEM$TYPEOF(ZEROIFNULL('7'))");
    }

    @Test
    public void bitsAndRandomNumbers() {
        assertAnswer(
            "8|NUMBER(2,0)[SB1]",
            "SELECT BITAND(12, 10), SYSTEM$TYPEOF(BITAND(12, 10))");
        assertAnswer(
            "14|NUMBER(3,0)[SB1]",
            "SELECT BITOR(12, 10), SYSTEM$TYPEOF(BITOR(12, 10))");
        assertAnswer(
            "6|NUMBER(2,0)[SB1]",
            "SELECT BITXOR(12, 10), SYSTEM$TYPEOF(BITXOR(12, 10))");
        assertAnswer(
            "-13|NUMBER(2,0)[SB1]",
            "SELECT BITNOT(12), SYSTEM$TYPEOF(BITNOT(12))");
        assertAnswer(
            "255|NUMBER(3,0)[SB2]",
            "SELECT BITAND(-1, 255), SYSTEM$TYPEOF(BITAND(-1, 255))");
        assertAnswer(
            "null|NUMBER(2,0)[SB1]",
            "SELECT BITAND(NULL, 1), SYSTEM$TYPEOF(BITAND(NULL, 1))");
        assertAnswer(
            "0|NUMBER(2,0)[SB1]",
            "SELECT BITAND(1.5, 1), SYSTEM$TYPEOF(BITAND(1.5, 1))");
        assertAnswer(
            "14|NUMBER(19,0)[SB1]",
            "SELECT BITOR('12', 10), SYSTEM$TYPEOF(BITOR('12', 10))");
        assertAnswer(
            "null|NUMBER(2,0)[SB1]",
            "SELECT BITNOT(NULL), SYSTEM$TYPEOF(BITNOT(NULL))");
        assertAnswer(
            "8|NUMBER(2,0)[SB1]",
            "SELECT BIT_AND(12, 10), SYSTEM$TYPEOF(BIT_AND(12, 10))");
        assertAnswer(
            "16|NUMBER(38,0)[SB16]",
            "SELECT BITSHIFTLEFT(1, 4), SYSTEM$TYPEOF(BITSHIFTLEFT(1, 4))");
        assertAnswer(
            "1|NUMBER(2,0)[SB1]",
            "SELECT BITSHIFTRIGHT(16, 4), SYSTEM$TYPEOF(BITSHIFTRIGHT(16, 4))");
        assertAnswer(
            "-170141183460469231731687303715884105728|NUMBER(38,0)[SB16]",
            "SELECT BITSHIFTLEFT(1, 127), SYSTEM$TYPEOF(BITSHIFTLEFT(1, 127))");
        assertAnswer(
            "0|NUMBER(38,0)[SB16]",
            "SELECT BITSHIFTLEFT(1, 128), SYSTEM$TYPEOF(BITSHIFTLEFT(1, 128))");
        assertAnswer(
            "-4|NUMBER(2,0)[SB1]",
            "SELECT BITSHIFTRIGHT(-16, 2), SYSTEM$TYPEOF(BITSHIFTRIGHT(-16, 2))");
        assertAnswer(
            "null|NUMBER(38,0)[SB1]",
            "SELECT BITSHIFTLEFT(NULL, 1), SYSTEM$TYPEOF(BITSHIFTLEFT(NULL, 1))");
        assertAnswer(
            "REFUSED Invalid parameter value: -1. Reason: shift parameter must be greater or equal to zero",
            "SELECT BITSHIFTLEFT(1, -1), SYSTEM$TYPEOF(BITSHIFTLEFT(1, -1))");
        assertAnswer(
            "NUMBER(19,0)[SB8]",
            "SELECT SYSTEM$TYPEOF(RANDOM())");
        assertAnswer(
            "true|BOOLEAN[ROWINDEX]",
            "SELECT RANDOM(1) = RANDOM(1), SYSTEM$TYPEOF(RANDOM(1) = RANDOM(1))");
        assertAnswer(
            "false|BOOLEAN[ROWINDEX]",
            "SELECT RANDOM(1) = RANDOM(2), SYSTEM$TYPEOF(RANDOM(1) = RANDOM(2))");
        assertAnswer(
            "1|NUMBER(2,0)[SB1]",
            "SELECT UNIFORM(1, 1, RANDOM()), SYSTEM$TYPEOF(UNIFORM(1, 1, RANDOM()))");
        assertAnswer(
            "true|BOOLEAN[ROWINDEX]",
            "SELECT UNIFORM(1, 10, 5) BETWEEN 1 AND 10, SYSTEM$TYPEOF(UNIFORM(1, 10, 5) BETWEEN 1 AND 10)");
        assertAnswer(
            "true|BOOLEAN[ROWINDEX]",
            "SELECT UNIFORM(1.5::FLOAT, 2.5::FLOAT, 5) BETWEEN 1.5 AND 2.5, SYSTEM$TYPEOF(UNIFORM(1.5::FLOAT, "
                + "2.5::FLOAT, 5) BETWEEN 1.5 AND 2.5)");
        assertAnswer(
            "NUMBER(2,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(UNIFORM(1, 10, RANDOM()))");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(UNIFORM(1::FLOAT, 10, RANDOM()))");
    }

    @Test
    public void cubeRootsAndDomains() {
        assertAnswer(
            "4.0|FLOAT[DOUBLE]",
            "SELECT CBRT(64), SYSTEM$TYPEOF(CBRT(64))");
        assertAnswer(
            "5.0|FLOAT[DOUBLE]",
            "SELECT CBRT(125), SYSTEM$TYPEOF(CBRT(125))");
        assertAnswer(
            "10.0|FLOAT[DOUBLE]",
            "SELECT CBRT(1000), SYSTEM$TYPEOF(CBRT(1000))");
        assertAnswer(
            "1.2599210498948734|FLOAT[DOUBLE]",
            "SELECT CBRT(2), SYSTEM$TYPEOF(CBRT(2))");
        assertAnswer(
            "0.0|FLOAT[DOUBLE]",
            "SELECT CBRT(0), SYSTEM$TYPEOF(CBRT(0))");
        assertAnswer(
            "-3.0000000000000004|FLOAT[DOUBLE]",
            "SELECT CBRT(-27), SYSTEM$TYPEOF(CBRT(-27))");
        assertAnswer(
            "0.1|FLOAT[DOUBLE]",
            "SELECT CBRT(0.001), SYSTEM$TYPEOF(CBRT(0.001))");
        assertAnswer(
            "2.0|FLOAT[DOUBLE]",
            "SELECT CBRT(8), SYSTEM$TYPEOF(CBRT(8))");
        assertAnswer(
            "1.0|FLOAT[DOUBLE]",
            "SELECT CBRT(1), SYSTEM$TYPEOF(CBRT(1))");
        assertAnswer(
            "-1.0|FLOAT[DOUBLE]",
            "SELECT CBRT(-1), SYSTEM$TYPEOF(CBRT(-1))");
        assertAnswer(
            "7.0|FLOAT[DOUBLE]",
            "SELECT CBRT(343), SYSTEM$TYPEOF(CBRT(343))");
        assertAnswer(
            "9.999999999999998E9|FLOAT[DOUBLE]",
            "SELECT CBRT(1e30), SYSTEM$TYPEOF(CBRT(1e30))");
        assertAnswer(
            "6.000000000000001|FLOAT[DOUBLE]",
            "SELECT CBRT(216), SYSTEM$TYPEOF(CBRT(216))");
        assertAnswer(
            "-0.7937005259840998|FLOAT[DOUBLE]",
            "SELECT CBRT(-0.5), SYSTEM$TYPEOF(CBRT(-0.5))");
        assertAnswer(
            "REFUSED Invalid floating point operation: asin(2)",
            "SELECT ASIN(2), SYSTEM$TYPEOF(ASIN(2))");
        assertAnswer(
            "REFUSED Invalid floating point operation: asin(-1.5)",
            "SELECT ASIN(-1.5), SYSTEM$TYPEOF(ASIN(-1.5))");
        assertAnswer(
            "REFUSED Invalid floating point operation: acos(-2)",
            "SELECT ACOS(-2), SYSTEM$TYPEOF(ACOS(-2))");
        assertAnswer(
            "1.5707963267948966|FLOAT[DOUBLE]",
            "SELECT ASIN(1), SYSTEM$TYPEOF(ASIN(1))");
        assertAnswer(
            "Infinity|FLOAT[DOUBLE]",
            "SELECT COT(0), SYSTEM$TYPEOF(COT(0))");
        assertAnswer(
            "REFUSED Invalid floating point operation: log(e,-5)",
            "SELECT LN(-5), SYSTEM$TYPEOF(LN(-5))");
        assertAnswer(
            "REFUSED Invalid floating point operation: sqrt(-0.5)",
            "SELECT SQRT(-0.5), SYSTEM$TYPEOF(SQRT(-0.5))");
        assertAnswer(
            "REFUSED Invalid floating point operation: pow(-2, 0.5)",
            "SELECT POWER(-2, 0.5), SYSTEM$TYPEOF(POWER(-2, 0.5))");
        assertAnswer(
            "-512.0|FLOAT[DOUBLE]",
            "SELECT POWER(-8, 3), SYSTEM$TYPEOF(POWER(-8, 3))");
        assertAnswer(
            "Infinity|FLOAT[DOUBLE]",
            "SELECT POWER(0, -1), SYSTEM$TYPEOF(POWER(0, -1))");
        assertAnswer(
            "REFUSED Invalid floating point operation: log(-2,8)",
            "SELECT LOG(-2, 8), SYSTEM$TYPEOF(LOG(-2, 8))");
        assertAnswer(
            "REFUSED Invalid floating point operation: log(2,-8)",
            "SELECT LOG(2, -8), SYSTEM$TYPEOF(LOG(2, -8))");
        assertAnswer(
            "REFUSED Invalid floating point operation: log(0,8)",
            "SELECT LOG(0, 8), SYSTEM$TYPEOF(LOG(0, 8))");
    }

    @Test
    public void factorialsOfFractionsAndTexts() {
        assertAnswer(
            "2|NUMBER(37,0)[SB16]",
            "SELECT FACTORIAL(2.4), SYSTEM$TYPEOF(FACTORIAL(2.4))");
        assertAnswer(
            "24|NUMBER(37,0)[SB16]",
            "SELECT FACTORIAL(3.5), SYSTEM$TYPEOF(FACTORIAL(3.5))");
        assertAnswer(
            "8683317618811886495518194401280000000|NUMBER(37,0)[SB16]",
            "SELECT FACTORIAL(33.4), SYSTEM$TYPEOF(FACTORIAL(33.4))");
        assertAnswer(
            "REFUSED Numeric value is out of range, error: 34 is too large for factorial function.",
            "SELECT FACTORIAL(33.5), SYSTEM$TYPEOF(FACTORIAL(33.5))");
        assertAnswer(
            "1|NUMBER(37,0)[SB16]",
            "SELECT FACTORIAL(-0.4), SYSTEM$TYPEOF(FACTORIAL(-0.4))");
        assertAnswer(
            "REFUSED Numeric value is out of range, error: Input of factorial function can't be negative. -1 is "
                + "invalid.",
            "SELECT FACTORIAL(-0.5), SYSTEM$TYPEOF(FACTORIAL(-0.5))");
        assertAnswer(
            "3628800|NUMBER(37,0)[SB4]",
            "SELECT FACTORIAL(10), SYSTEM$TYPEOF(FACTORIAL(10))");
        assertShapedAnswer(
            "<ID19>|NUMBER(37,0)[SB8]",
            "SELECT FACTORIAL(20), SYSTEM$TYPEOF(FACTORIAL(20))");
        assertAnswer(
            "120|NUMBER(37,0)[SB16]",
            "SELECT FACTORIAL('5'), SYSTEM$TYPEOF(FACTORIAL('5'))");
        assertAnswer(
            "120|NUMBER(37,0)[SB1]",
            "SELECT FACTORIAL(5.0), SYSTEM$TYPEOF(FACTORIAL(5.0))");
        assertAnswer(
            "120|NUMBER(37,0)[SB16]",
            "SELECT FACTORIAL(1 + 4), SYSTEM$TYPEOF(FACTORIAL(1 + 4))");
        assertAnswer(
            "479001600|NUMBER(37,0)[SB4]",
            "SELECT FACTORIAL(12), SYSTEM$TYPEOF(FACTORIAL(12))");
        assertAnswer(
            "6227020800|NUMBER(37,0)[SB8]",
            "SELECT FACTORIAL(13), SYSTEM$TYPEOF(FACTORIAL(13))");
        assertAnswer(
            "40320|NUMBER(37,0)[SB4]",
            "SELECT FACTORIAL(8), SYSTEM$TYPEOF(FACTORIAL(8))");
    }
}
