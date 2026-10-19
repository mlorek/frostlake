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
 * The casts of a VARIANT read the arguments they take after it: AS_TIMESTAMP_NTZ, _LTZ and _TZ a scale from 0 to
 * 9 that cuts the fraction and types the result, written as a whole number, and AS_NUMBER and AS_DECIMAL a
 * precision and a scale that type the result, a precision of 0 holding any value; a value outside them is refused
 * in the words of the call's own signature, and the other casts take no argument but the VARIANT
 * (all live-verified).
 */
public class VariantCastArgumentsTest extends MeasuredCellsTestSupport {

    @Test
    public void aCastOfAVariantReadsItsScaleOrItsDigits() {
        answer("SELECT AS_TIMESTAMP_TZ(TO_VARIANT('2024-01-01 00:00:00.123456789 +01:00'::TIMESTAMP_TZ), 1)");
        assertAnswer("TIMESTAMP_TZ(1)[SB8]",
            "SELECT SYSTEM$TYPEOF(AS_TIMESTAMP_TZ(TO_VARIANT('2024-01-01 00:00:00.123456789 +01:00'::TIMESTAMP_TZ), "
                + "1))");
        answer("SELECT AS_TIMESTAMP_TZ(TO_VARIANT('2024-01-01 00:00:00.123456789 +01:00'::TIMESTAMP_TZ), 9)");
        assertAnswer("REFUSED SQL compilation error: | invalid value [10] for parameter 'AS_TIMESTAMP_TZ(?, ?, scale)'",
            "SELECT AS_TIMESTAMP_TZ(TO_VARIANT('2024-01-01 00:00:00.123456789 +01:00'::TIMESTAMP_TZ), 10)");
        assertAnswer("REFUSED SQL compilation error: | invalid value [-1] for parameter 'AS_TIMESTAMP_TZ(?, ?, scale)'",
            "SELECT AS_TIMESTAMP_TZ(TO_VARIANT('2024-01-01 00:00:00.123456789 +01:00'::TIMESTAMP_TZ), -1)");
        answer("SELECT AS_TIMESTAMP_TZ(TO_VARIANT('2024-01-01 00:00:00.123456789 +01:00'::TIMESTAMP_TZ), 1, 2)");
        assertAnswer("null", "SELECT AS_TIMESTAMP_NTZ(TO_VARIANT(1), 1)");
        assertAnswer("2024-01-01T00:00",
            "SELECT AS_TIMESTAMP_NTZ(TO_VARIANT('2024-01-01 00:00:00.987'::TIMESTAMP_NTZ), 0)");
        assertAnswer("TIMESTAMP_NTZ(0)[SB8]",
            "SELECT SYSTEM$TYPEOF(AS_TIMESTAMP_NTZ(TO_VARIANT('2024-01-01 00:00:00.987'::TIMESTAMP_NTZ), 0))");
        assertAnswer("true",
            "SELECT AS_TIMESTAMP_LTZ(TO_VARIANT('2024-01-01 00:00:00.987'::TIMESTAMP_LTZ), 2) IS NOT NULL");
        assertAnswer("TIMESTAMP_LTZ(2)[SB8]",
            "SELECT SYSTEM$TYPEOF(AS_TIMESTAMP_LTZ(TO_VARIANT('2024-01-01 00:00:00.987'::TIMESTAMP_LTZ), 2))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[AS_TIME(CAST(CAST('12:34:56.789' AS TIME(9)) AS VARIANT), 1)] expected 1, got 2",
            "SELECT AS_TIME(TO_VARIANT('12:34:56.789'::TIME), 1)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[AS_DATE(CAST(CAST('2024-01-01' AS DATE) AS VARIANT), 1)] expected 1, got 2",
            "SELECT AS_DATE(TO_VARIANT('2024-01-01'::DATE), 1)");
        assertAnswer("5", "SELECT AS_NUMBER(TO_VARIANT(5), 0)");
        assertAnswer("6", "SELECT AS_NUMBER(TO_VARIANT(5.55), 3)");
        assertAnswer("5.6", "SELECT AS_NUMBER(TO_VARIANT(5.55), 3, 1)");
        assertAnswer("NUMBER(3,1)[SB1]", "SELECT SYSTEM$TYPEOF(AS_NUMBER(TO_VARIANT(5.55), 3, 1))");
        assertAnswer("1.3", "SELECT AS_DECIMAL(TO_VARIANT(1.25), 10, 1)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[AS_DECIMAL(CAST(1.25 AS VARIANT), 10, 1, 1)] expected 3, got 4",
            "SELECT AS_DECIMAL(TO_VARIANT(1.25), 10, 1, 1)");
        assertAnswer("5", "SELECT AS_INTEGER(TO_VARIANT(5), 1)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[AS_DOUBLE(CAST(5 AS VARIANT), 1)] expected 1, got 2",
            "SELECT AS_DOUBLE(TO_VARIANT(5), 1)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[AS_VARCHAR(CAST('x' AS VARIANT), 1)] expected 1, got 2",
            "SELECT AS_VARCHAR(TO_VARIANT('x'), 1)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[AS_BOOLEAN(CAST(TRUE AS VARIANT), 1)] expected 1, got 2",
            "SELECT AS_BOOLEAN(TO_VARIANT(TRUE), 1)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[AS_OBJECT(PARSE_JSON('{}'), 1)] expected 1, got 2",
            "SELECT AS_OBJECT(PARSE_JSON('{}'), 1)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[AS_ARRAY(PARSE_JSON('[]'), 1)] expected 1, got 2",
            "SELECT AS_ARRAY(PARSE_JSON('[]'), 1)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[AS_BINARY(CAST(CAST('AB' AS BINARY(67108864)) AS VARIANT), 1)] expected 1, got 2",
            "SELECT AS_BINARY(TO_VARIANT(TO_BINARY('AB')), 1)");
    }

    @Test
    public void theCastsArgumentsAreJudgedAsWritten() {
        answer("SELECT SYSTEM$TYPEOF(AS_NUMBER(TO_VARIANT(5), 0))");
        assertAnswer("REFUSED Number out of representable range: type FIXED, value 123",
            "SELECT AS_NUMBER(TO_VARIANT(123), 2)");
        assertAnswer("5", "SELECT AS_DECIMAL(TO_VARIANT(5), 0, 0)");
        answer("SELECT SYSTEM$TYPEOF(AS_DECIMAL(TO_VARIANT(5), 0, 0))");
        assertAnswer("REFUSED SQL compilation error: | invalid value [39] for parameter 'AS_NUMBER(?, precision...)'",
            "SELECT AS_NUMBER(TO_VARIANT(5), 39)");
        assertAnswer("REFUSED SQL compilation error: | invalid value [11] for parameter 'AS_NUMBER(?, ?, scale)'",
            "SELECT AS_NUMBER(TO_VARIANT(5), 10, 11)");
        assertAnswer("5.3", "SELECT AS_NUMBER(TO_VARIANT(5.25), 10, 1)");
        assertAnswer("NUMBER(10,0)[SB1]", "SELECT SYSTEM$TYPEOF(AS_NUMBER(TO_VARIANT(5.25), 10))");
        assertAnswer("980000000",
            "SELECT DATE_PART(NANOSECOND, AS_TIMESTAMP_NTZ(TO_VARIANT('2024-01-01 "
                + "00:00:00.987654321'::TIMESTAMP_NTZ), 2))");
        assertAnswer("0",
            "SELECT DATE_PART(NANOSECOND, AS_TIMESTAMP_NTZ(TO_VARIANT('2024-01-01 "
                + "00:00:00.987654321'::TIMESTAMP_NTZ), 0))");
        assertAnswer("987654321",
            "SELECT DATE_PART(NANOSECOND, AS_TIMESTAMP_NTZ(TO_VARIANT('2024-01-01 "
                + "00:00:00.987654321'::TIMESTAMP_NTZ)))");
        assertAnswer("987600000",
            "SELECT DATE_PART(NANOSECOND, AS_TIMESTAMP_TZ(TO_VARIANT('2024-01-01 00:00:00.987654321 "
                + "+01:00'::TIMESTAMP_TZ), 4))");
        assertAnswer("987650000",
            "SELECT DATE_PART(NANOSECOND, AS_TIMESTAMP_LTZ(TO_VARIANT('2024-01-01 "
                + "00:00:00.987654321'::TIMESTAMP_LTZ), 5))");
        assertAnswer("REFUSED SQL compilation error: | argument 2 to function AS_TIMESTAMP_NTZ needs to be an integer,"
                + " found: 'null'",
            "SELECT AS_TIMESTAMP_NTZ(TO_VARIANT('2024-01-01 00:00:00.987654321'::TIMESTAMP_NTZ), NULL)");
        assertAnswer("REFUSED SQL compilation error: | argument 2 to function AS_TIMESTAMP_NTZ needs to be an integer,"
                + " found: '1.5'",
            "SELECT AS_TIMESTAMP_NTZ(TO_VARIANT('2024-01-01 00:00:00.987654321'::TIMESTAMP_NTZ), 1.5)");
        assertAnswer("REFUSED SQL compilation error: | argument 2 to function AS_TIMESTAMP_NTZ needs to be an integer,"
                + " found: ''2''",
            "SELECT AS_TIMESTAMP_NTZ(TO_VARIANT('2024-01-01 00:00:00.987654321'::TIMESTAMP_NTZ), '2')");
        assertAnswer("REFUSED SQL compilation error: | argument 2 to function AS_TIMESTAMP_NTZ needs to be an integer,"
                + " found: ''2''",
            "SELECT SYSTEM$TYPEOF(AS_TIMESTAMP_NTZ(TO_VARIANT('2024-01-01 00:00:00.987654321'::TIMESTAMP_NTZ), '2'))");
        assertAnswer("REFUSED SQL compilation error: | invalid value [10] for parameter 'AS_TIMESTAMP_NTZ(?, ?, "
                + "scale)'",
            "SELECT AS_TIMESTAMP_NTZ(TO_VARIANT(1), 10)");
    }
}
