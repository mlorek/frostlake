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
 * TO_CHAR, TO_NUMBER and its spellings, TRY_TO_NUMBER, TO_BOOLEAN, TO_DATE, TRY_TO_DATE, TO_TIME and
 * TRY_TO_UUID: what each reads, what it declares, and the sentence a value it cannot read is refused in
 * (live-verified).
 */
public class ConversionFunctionCatalogTest extends MeasuredCellsTestSupport {

    @Test
    public void textAndNumbers() {
        assertAnswer(
            "123|VARCHAR[LOB]",
            "SELECT TO_CHAR(123), SYSTEM$TYPEOF(TO_CHAR(123))");
        assertAnswer(
            " 123.5|VARCHAR[LOB]",
            "SELECT TO_CHAR(123.45, '999.9'), SYSTEM$TYPEOF(TO_CHAR(123.45, '999.9'))");
        assertAnswer(
            "2024/01/02|VARCHAR[LOB]",
            "SELECT TO_CHAR('2024-01-02'::DATE, 'YYYY/MM/DD'), SYSTEM$TYPEOF(TO_CHAR('2024-01-02'::DATE, "
                + "'YYYY/MM/DD'))");
        assertAnswer(
            "true|VARCHAR[LOB]",
            "SELECT TO_CHAR(TRUE), SYSTEM$TYPEOF(TO_CHAR(TRUE))");
        assertAnswer(
            "null|VARCHAR[LOB]",
            "SELECT TO_CHAR(NULL), SYSTEM$TYPEOF(TO_CHAR(NULL))");
        assertAnswer(
            "1.5|VARCHAR[LOB]",
            "SELECT TO_CHAR(1.5::FLOAT), SYSTEM$TYPEOF(TO_CHAR(1.5::FLOAT))");
        assertAnswer(
            "AB|VARCHAR[LOB]",
            "SELECT TO_CHAR(TO_BINARY('AB', 'HEX')), SYSTEM$TYPEOF(TO_CHAR(TO_BINARY('AB', 'HEX')))");
        assertAnswer(
            "x 12|VARCHAR[LOB]",
            "SELECT TO_CHAR(12, '\"x\"99'), SYSTEM$TYPEOF(TO_CHAR(12, '\"x\"99'))");
        assertAnswer(
            "13|NUMBER(38,0)[SB1]",
            "SELECT TO_NUMBER('12.5'), SYSTEM$TYPEOF(TO_NUMBER('12.5'))");
        assertAnswer(
            "12.5|NUMBER(10,1)[SB1]",
            "SELECT TO_NUMBER('12.5', 10, 1), SYSTEM$TYPEOF(TO_NUMBER('12.5', 10, 1))");
        assertAnswer(
            "12.35|NUMBER(10,2)[SB2]",
            "SELECT TO_DECIMAL('12.345', 10, 2), SYSTEM$TYPEOF(TO_DECIMAL('12.345', 10, 2))");
        assertAnswer(
            "REFUSED Numeric value 'a' is not recognized",
            "SELECT TO_NUMBER('a'), SYSTEM$TYPEOF(TO_NUMBER('a'))");
        assertAnswer(
            "null|NUMBER(38,0)[SB1]",
            "SELECT TO_NUMBER(NULL), SYSTEM$TYPEOF(TO_NUMBER(NULL))");
        assertAnswer(
            "13|NUMBER(38,0)[SB1]",
            "SELECT TO_NUMBER(12.5::FLOAT), SYSTEM$TYPEOF(TO_NUMBER(12.5::FLOAT))");
        assertAnswer(
            "1|NUMBER(2,0)[SB1]",
            "SELECT TO_NUMBER(TRUE), SYSTEM$TYPEOF(TO_NUMBER(TRUE))");
        assertAnswer(
            "REFUSED Numeric value '123456' is out of range",
            "SELECT TO_NUMBER('123456', 5, 0), SYSTEM$TYPEOF(TO_NUMBER('123456', 5, 0))");
        assertAnswer(
            "13|NUMBER(38,0)[SB1]",
            "SELECT TRY_TO_NUMBER('12.5'), SYSTEM$TYPEOF(TRY_TO_NUMBER('12.5'))");
        assertAnswer(
            "null|NUMBER(38,0)[SB1]",
            "SELECT TRY_TO_NUMBER('a'), SYSTEM$TYPEOF(TRY_TO_NUMBER('a'))");
        assertAnswer(
            "12.5|NUMBER(10,1)[SB1]",
            "SELECT TRY_TO_NUMBER('12.5', 10, 1), SYSTEM$TYPEOF(TRY_TO_NUMBER('12.5', 10, 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TRY_CAST cannot be used with arguments of types NULL and "
                + "NUMBER(38,0)",
            "SELECT TRY_TO_NUMBER(NULL), SYSTEM$TYPEOF(TRY_TO_NUMBER(NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TRY_CAST cannot be used with arguments of types NUMBER(2,"
                + "0) and NUMBER(38,0)",
            "SELECT TRY_TO_NUMBER(12), SYSTEM$TYPEOF(TRY_TO_NUMBER(12))");
        assertAnswer(
            "null|NUMBER(38,0)[SB1]",
            "SELECT TRY_TO_NUMBER(''), SYSTEM$TYPEOF(TRY_TO_NUMBER(''))");
    }

    @Test
    public void booleansDatesAndTimes() {
        assertAnswer(
            "true|BOOLEAN[SB1]",
            "SELECT TO_BOOLEAN('true'), SYSTEM$TYPEOF(TO_BOOLEAN('true'))");
        assertAnswer(
            "true|BOOLEAN[SB1]",
            "SELECT TO_BOOLEAN('yes'), SYSTEM$TYPEOF(TO_BOOLEAN('yes'))");
        assertAnswer(
            "false|BOOLEAN[SB1]",
            "SELECT TO_BOOLEAN('0'), SYSTEM$TYPEOF(TO_BOOLEAN('0'))");
        assertAnswer(
            "true|BOOLEAN[SB1]",
            "SELECT TO_BOOLEAN(5), SYSTEM$TYPEOF(TO_BOOLEAN(5))");
        assertAnswer(
            "false|BOOLEAN[SB1]",
            "SELECT TO_BOOLEAN(0), SYSTEM$TYPEOF(TO_BOOLEAN(0))");
        assertAnswer(
            "REFUSED Boolean value 'x' is not recognized",
            "SELECT TO_BOOLEAN('x'), SYSTEM$TYPEOF(TO_BOOLEAN('x'))");
        assertAnswer(
            "null|BOOLEAN[SB1]",
            "SELECT TO_BOOLEAN(NULL), SYSTEM$TYPEOF(TO_BOOLEAN(NULL))");
        assertAnswer(
            "true|BOOLEAN[SB1]",
            "SELECT TO_BOOLEAN('T'), SYSTEM$TYPEOF(TO_BOOLEAN('T'))");
        assertAnswer(
            "true|BOOLEAN[SB1]",
            "SELECT TO_BOOLEAN('on'), SYSTEM$TYPEOF(TO_BOOLEAN('on'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_BOOLEAN(TO_DOUBLE(1.5))] for parameter 'TO_BOOLEAN'",
            "SELECT TO_BOOLEAN(1.5::FLOAT), SYSTEM$TYPEOF(TO_BOOLEAN(1.5::FLOAT))");
        assertAnswer(
            "2024-01-02|DATE[SB4]",
            "SELECT TO_DATE('2024-01-02'), SYSTEM$TYPEOF(TO_DATE('2024-01-02'))");
        assertAnswer(
            "2024-01-02|DATE[SB4]",
            "SELECT TO_DATE('02/01/2024', 'DD/MM/YYYY'), SYSTEM$TYPEOF(TO_DATE('02/01/2024', 'DD/MM/YYYY'))");
        assertAnswer(
            "REFUSED Date 'x' is not recognized",
            "SELECT TO_DATE('x'), SYSTEM$TYPEOF(TO_DATE('x'))");
        assertAnswer(
            "null|DATE[SB4]",
            "SELECT TRY_TO_DATE('x'), SYSTEM$TYPEOF(TRY_TO_DATE('x'))");
        assertAnswer(
            "2024-01-02|DATE[SB4]",
            "SELECT TRY_TO_DATE('2024-01-02'), SYSTEM$TYPEOF(TRY_TO_DATE('2024-01-02'))");
        assertAnswer(
            "2024-01-02|DATE[SB4]",
            "SELECT TO_DATE('2024-01-02 10:20:30'::TIMESTAMP_NTZ), SYSTEM$TYPEOF(TO_DATE('2024-01-02 "
                + "10:20:30'::TIMESTAMP_NTZ))");
        assertAnswer(
            "2024-01-02|DATE[SB4]",
            "SELECT TO_DATE('1704153600'), SYSTEM$TYPEOF(TO_DATE('1704153600'))");
        assertAnswer(
            "null|DATE[SB4]",
            "SELECT TO_DATE(NULL), SYSTEM$TYPEOF(TO_DATE(NULL))");
        assertAnswer(
            "null|DATE[SB4]",
            "SELECT TRY_TO_DATE('2024-13-01'), SYSTEM$TYPEOF(TRY_TO_DATE('2024-13-01'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_DATE(1704153600)] for parameter 'TO_DATE'",
            "SELECT TO_DATE(1704153600), SYSTEM$TYPEOF(TO_DATE(1704153600))");
        assertAnswer(
            "10:20:30|TIME(9)[SB8]",
            "SELECT TO_TIME('10:20:30'), SYSTEM$TYPEOF(TO_TIME('10:20:30'))");
        assertAnswer(
            "10:20|TIME(9)[SB8]",
            "SELECT TO_TIME('10.20', 'HH24.MI'), SYSTEM$TYPEOF(TO_TIME('10.20', 'HH24.MI'))");
        assertAnswer(
            "REFUSED Time 'x' is not recognized",
            "SELECT TO_TIME('x'), SYSTEM$TYPEOF(TO_TIME('x'))");
        assertAnswer(
            "10:20:30|TIME(9)[SB8]",
            "SELECT TO_TIME('2024-01-02 10:20:30'::TIMESTAMP_NTZ), SYSTEM$TYPEOF(TO_TIME('2024-01-02 "
                + "10:20:30'::TIMESTAMP_NTZ))");
        assertAnswer(
            "null|TIME(9)[SB8]",
            "SELECT TO_TIME(NULL), SYSTEM$TYPEOF(TO_TIME(NULL))");
        assertAnswer(
            "01:00|TIME(9)[SB8]",
            "SELECT TO_TIME('3600'), SYSTEM$TYPEOF(TO_TIME('3600'))");
        assertAnswer(
            "10:20:30|TIME(9)[SB8]",
            "SELECT TIME('10:20:30'), SYSTEM$TYPEOF(TIME('10:20:30'))");
        assertShapedAnswer(
            "<QID>|UUID[SB16]",
            "SELECT TRY_TO_UUID('123e4567-e89b-12d3-a456-426614174000'), "
                + "SYSTEM$TYPEOF(TRY_TO_UUID('123e4567-e89b-12d3-a456-426614174000'))");
        assertAnswer(
            "null|UUID[SB16]",
            "SELECT TRY_TO_UUID('x'), SYSTEM$TYPEOF(TRY_TO_UUID('x'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TRY_CAST cannot be used with arguments of types NULL and "
                + "UUID",
            "SELECT TRY_TO_UUID(NULL), SYSTEM$TYPEOF(TRY_TO_UUID(NULL))");
        assertAnswer(
            "null|UUID[SB16]",
            "SELECT TRY_TO_UUID('123E4567E89B12D3A456426614174000'), "
                + "SYSTEM$TYPEOF(TRY_TO_UUID('123E4567E89B12D3A456426614174000'))");
    }
}
