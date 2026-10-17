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
 * The declared types and storage tags the catalog's functions answer: NULLIFZERO over the bare word NULL is that NULL,
 * LPAD and RPAD hand a BINARY back as a BINARY, a decoding is as wide as its text's whole groups decode to, CHECK_JSON
 * and CHECK_XML are a VARCHAR of no width, a fraction counts two whole digits at the least in a bitwise width and
 * BITSHIFTRIGHT counts its shift too, a clock's precision may be a whole-valued decimal, and a conversion read by a
 * format, one that does not fit, an epoch part of a constant timestamp and a conversion of PI() are tagged as the plan
 * holds them (live-verified).
 */
public class CatalogDeclaredTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void declaredTypesAndTagsAreThePlansOwn() {
        assertCells(new String[][] {
            {"SELECT NULLIFZERO(NULL), SYSTEM$TYPEOF(NULLIFZERO(NULL))", "null|NULL[LOB]"},
            {"SELECT NULLIFZERO(NULL) + 1, SYSTEM$TYPEOF(NULLIFZERO(NULL) + 1)", "null|NUMBER(19,0)[SB1]"},
            {"SELECT ZEROIFNULL(NULL), SYSTEM$TYPEOF(ZEROIFNULL(NULL))", "0|NUMBER(2,0)[SB1]"},
            {"SELECT NULLIFZERO(0), SYSTEM$TYPEOF(NULLIFZERO(0))", "null|NUMBER(1,0)[SB1]"},
            {"SELECT LPAD(TO_BINARY('AB', 'HEX'), 3, TO_BINARY('00', 'HEX')), "
                + "SYSTEM$TYPEOF(LPAD(TO_BINARY('AB', 'HEX'), 3, TO_BINARY('00', 'HEX')))",
                "0000AB|BINARY[LOB]"},
            {"SELECT RPAD(TO_BINARY('AB', 'HEX'), 3, TO_BINARY('00', 'HEX')), "
                + "SYSTEM$TYPEOF(RPAD(TO_BINARY('AB', 'HEX'), 3, TO_BINARY('00', 'HEX')))",
                "AB0000|BINARY[LOB]"},
            {"SELECT LPAD(TO_BINARY('AB', 'HEX'), 1, TO_BINARY('00', 'HEX')), "
                + "SYSTEM$TYPEOF(LPAD(TO_BINARY('AB', 'HEX'), 1, TO_BINARY('00', 'HEX')))",
                "AB|BINARY[LOB]"},
            {"SELECT SYSTEM$TYPEOF(LPAD('ab', 3, 'x'))", "VARCHAR(134217728)[LOB]"},
            {"SELECT TRY_BASE64_DECODE_STRING('!!!'), SYSTEM$TYPEOF(TRY_BASE64_DECODE_STRING('!!!'))",
                "null|VARCHAR(0)[LOB]"},
            {"SELECT TRY_BASE64_DECODE_STRING('QQ=='), SYSTEM$TYPEOF(TRY_BASE64_DECODE_STRING('QQ=='))",
                "A|VARCHAR(3)[LOB]"},
            {"SELECT TRY_BASE64_DECODE_STRING('QUJDREVGR0g='), SYSTEM$TYPEOF(TRY_BASE64_DECODE_STRING('QUJDREVGR0g='))",
                "ABCDEFGH|VARCHAR(9)[LOB]"},
            {"SELECT TRY_BASE64_DECODE_STRING(NULL), SYSTEM$TYPEOF(TRY_BASE64_DECODE_STRING(NULL))",
                "null|VARCHAR(100663296)[LOB]"},
            {"SELECT BASE64_DECODE_STRING('QQ=='), SYSTEM$TYPEOF(BASE64_DECODE_STRING('QQ=='))", "A|VARCHAR(3)[LOB]"},
            {"SELECT TRY_HEX_DECODE_STRING(NULL), SYSTEM$TYPEOF(TRY_HEX_DECODE_STRING(NULL))",
                "null|VARCHAR(67108864)[LOB]"},
            {"SELECT TRY_HEX_DECODE_STRING('6'), SYSTEM$TYPEOF(TRY_HEX_DECODE_STRING('6'))", "null|VARCHAR(0)[LOB]"},
            {"SELECT TRY_HEX_DECODE_STRING('41'), SYSTEM$TYPEOF(TRY_HEX_DECODE_STRING('41'))", "A|VARCHAR(1)[LOB]"},
            {"SELECT TRY_HEX_DECODE_STRING('414243'), SYSTEM$TYPEOF(TRY_HEX_DECODE_STRING('414243'))",
                "ABC|VARCHAR(3)[LOB]"},
            {"SELECT TRY_HEX_DECODE_STRING('zz'), SYSTEM$TYPEOF(TRY_HEX_DECODE_STRING('zz'))", "null|VARCHAR(1)[LOB]"},
            {"SELECT HEX_DECODE_STRING('41'), SYSTEM$TYPEOF(HEX_DECODE_STRING('41'))", "A|VARCHAR(1)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(HEX_DECODE_STRING(NULL))", "VARCHAR(67108864)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(BASE64_DECODE_STRING(NULL))", "VARCHAR(100663296)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(TRY_BASE64_DECODE_BINARY('!!!'))", "BINARY(0)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(TRY_HEX_DECODE_BINARY('6'))", "BINARY(0)[LOB]"},
            {"SELECT CHECK_JSON('{\"a\":1}'), SYSTEM$TYPEOF(CHECK_JSON('{\"a\":1}'))", "null|VARCHAR[LOB]"},
            {"SELECT CHECK_JSON('{a'), SYSTEM$TYPEOF(CHECK_JSON('{a'))", "incomplete object value, pos 3|VARCHAR[LOB]"},
            {"SELECT CHECK_JSON(NULL), SYSTEM$TYPEOF(CHECK_JSON(NULL))", "null|VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(CHECK_XML('<a/>'))", "VARCHAR[LOB]"},
            {"SELECT BITSHIFTRIGHT(-1, 200), SYSTEM$TYPEOF(BITSHIFTRIGHT(-1, 200))", "-1|NUMBER(3,0)[SB2]"},
            {"SELECT BITSHIFTRIGHT(1, 200), SYSTEM$TYPEOF(BITSHIFTRIGHT(1, 200))", "0|NUMBER(3,0)[SB2]"},
            {"SELECT BITSHIFTRIGHT(1, 2), SYSTEM$TYPEOF(BITSHIFTRIGHT(1, 2))", "0|NUMBER(2,0)[SB1]"},
            {"SELECT BITSHIFTRIGHT(1, 20), SYSTEM$TYPEOF(BITSHIFTRIGHT(1, 20))", "0|NUMBER(2,0)[SB1]"},
            {"SELECT BITSHIFTRIGHT(100, 1), SYSTEM$TYPEOF(BITSHIFTRIGHT(100, 1))", "50|NUMBER(3,0)[SB2]"},
            {"SELECT BITSHIFTRIGHT(12345, 2), SYSTEM$TYPEOF(BITSHIFTRIGHT(12345, 2))", "3086|NUMBER(5,0)[SB4]"},
            {"SELECT BITSHIFTLEFT(1, 200), SYSTEM$TYPEOF(BITSHIFTLEFT(1, 200))", "0|NUMBER(38,0)[SB16]"},
            {"SELECT BITSHIFTLEFT(1, 2), SYSTEM$TYPEOF(BITSHIFTLEFT(1, 2))", "4|NUMBER(38,0)[SB16]"},
            {"SELECT BITOR(1.5, 2), SYSTEM$TYPEOF(BITOR(1.5, 2))", "2|NUMBER(3,0)[SB2]"},
            {"SELECT BITOR(1, 2), SYSTEM$TYPEOF(BITOR(1, 2))", "3|NUMBER(2,0)[SB1]"},
            {"SELECT BITOR(1, 2.5), SYSTEM$TYPEOF(BITOR(1, 2.5))", "3|NUMBER(3,0)[SB2]"},
            {"SELECT BITOR(10, 200), SYSTEM$TYPEOF(BITOR(10, 200))", "202|NUMBER(4,0)[SB2]"},
            {"SELECT BITAND(1.5, 2), SYSTEM$TYPEOF(BITAND(1.5, 2))", "2|NUMBER(2,0)[SB1]"},
            {"SELECT BITXOR(1.5, 2), SYSTEM$TYPEOF(BITXOR(1.5, 2))", "0|NUMBER(2,0)[SB1]"},
            {"SELECT BITOR(12.5, 2), SYSTEM$TYPEOF(BITOR(12.5, 2))", "15|NUMBER(3,0)[SB2]"},
            {"SELECT BITNOT(1.5), SYSTEM$TYPEOF(BITNOT(1.5))", "-3|NUMBER(2,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(CURRENT_TIME(3.0))", "TIME(3)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(CURRENT_TIME(3))", "TIME(3)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(CURRENT_TIMESTAMP(3.0))", "TIMESTAMP_LTZ(3)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(LOCALTIME(3.0))", "TIME(3)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(LOCALTIMESTAMP(3.0))", "TIMESTAMP_LTZ(3)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS(NULL))", "VARCHAR(1)[LOB]"},
            {"SELECT TO_NUMERIC('1,234', '9,999'), SYSTEM$TYPEOF(TO_NUMERIC('1,234', '9,999'))",
                "1234|NUMBER(38,0)[SB16]"},
            {"SELECT TO_NUMBER('$12.50', '$99.99', 5, 2), SYSTEM$TYPEOF(TO_NUMBER('$12.50', '$99.99', 5, 2))",
                "12.50|NUMBER(5,2)[SB4]"},
            {"SELECT TO_NUMBER('12', 5, 0), SYSTEM$TYPEOF(TO_NUMBER('12', 5, 0))", "12|NUMBER(5,0)[SB1]"},
            {"SELECT TO_NUMBER('12.5', 5, 2), SYSTEM$TYPEOF(TO_NUMBER('12.5', 5, 2))", "12.50|NUMBER(5,2)[SB2]"},
            {"SELECT TRY_TO_NUMBER('123456', 5, 0), SYSTEM$TYPEOF(TRY_TO_NUMBER('123456', 5, 0))",
                "null|NUMBER(5,0)[SB4]"},
            {"SELECT TRY_TO_NUMBER('123', 5, 0), SYSTEM$TYPEOF(TRY_TO_NUMBER('123', 5, 0))", "123|NUMBER(5,0)[SB1]"},
            {"SELECT TRY_TO_NUMBER('12.50', '99.99', 5, 2), SYSTEM$TYPEOF(TRY_TO_NUMBER('12.50', '99.99', 5, 2))",
                "12.50|NUMBER(5,2)[SB4]"},
            {"SELECT TRY_TO_NUMBER('x', 5, 0), SYSTEM$TYPEOF(TRY_TO_NUMBER('x', 5, 0))", "null|NUMBER(5,0)[SB1]"},
            {"SELECT TO_NUMBER('1,234', '9,999'), SYSTEM$TYPEOF(TO_NUMBER('1,234', '9,999'))",
                "1234|NUMBER(38,0)[SB16]"},
            {"SELECT DATE_PART(epoch_second, '2024-03-15 10:20:30.123'::TIMESTAMP_NTZ), "
                + "SYSTEM$TYPEOF(DATE_PART(epoch_second, '2024-03-15 10:20:30.123'::TIMESTAMP_NTZ))",
                "1710498030|NUMBER(18,0)[SB4]"},
            {"SELECT DATE_PART(epoch_millisecond, '2024-03-15 10:20:30.123'::TIMESTAMP_NTZ), "
                + "SYSTEM$TYPEOF(DATE_PART(epoch_millisecond, '2024-03-15 10:20:30.123'::TIMESTAMP_NTZ))",
                "1710498030123|NUMBER(38,0)[SB8]"},
            {"SELECT DATE_PART(epoch_second, '1970-01-01 00:01:00'::TIMESTAMP_NTZ), "
                + "SYSTEM$TYPEOF(DATE_PART(epoch_second, '1970-01-01 00:01:00'::TIMESTAMP_NTZ))",
                "60|NUMBER(18,0)[SB1]"},
            {"SELECT DATE_PART(epoch_second, '2024-03-15'::DATE), SYSTEM$TYPEOF(DATE_PART(epoch_second, "
                + "'2024-03-15'::DATE))",
                "1710460800|NUMBER(18,0)[SB8]"},
            {"SELECT DATE_PART(year, '2024-03-15 10:20:30.123'::TIMESTAMP_NTZ), SYSTEM$TYPEOF(DATE_PART(year, "
                + "'2024-03-15 10:20:30.123'::TIMESTAMP_NTZ))",
                "2024|NUMBER(4,0)[SB2]"},
            {"SELECT EXTRACT(epoch_second FROM '2024-03-15 10:20:30.123'::TIMESTAMP_NTZ), "
                + "SYSTEM$TYPEOF(EXTRACT(epoch_second FROM '2024-03-15 10:20:30.123'::TIMESTAMP_NTZ))",
                "1710498030|NUMBER(18,0)[SB4]"},
            {"SELECT PI()::NUMBER, SYSTEM$TYPEOF(PI()::NUMBER)", "3|NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(PI()::NUMBER(10,2))", "NUMBER(10,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(PI()::INT)", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(3.7::NUMBER)", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF((PI() * 1000)::NUMBER)", "NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SQRT(2)::NUMBER)", "NUMBER(38,0)[SB16]"},
        });
    }

    @Test
    public void fractionsShiftsAndDecodingsAreSizedAsTheyAreHeld() {
        engine.execute("CREATE OR REPLACE TABLE bt (x NUMBER(5,2), y NUMBER(3,1), g VARCHAR(5), h VARCHAR(4), b "
            + "BINARY(5))");
        engine.execute("INSERT INTO bt SELECT 1.25, 2.5, 'QQ==', '41', TO_BINARY('AB', 'HEX')");
        assertCells(new String[][] {
            {"SELECT BITOR(123.5, 2), SYSTEM$TYPEOF(BITOR(123.5, 2))", "126|NUMBER(4,0)[SB2]"},
            {"SELECT BITOR(1.25, 2), SYSTEM$TYPEOF(BITOR(1.25, 2))", "3|NUMBER(3,0)[SB2]"},
            {"SELECT BITOR(0.5, 0), SYSTEM$TYPEOF(BITOR(0.5, 0))", "1|NUMBER(3,0)[SB2]"},
            {"SELECT BITOR(1.5, 200), SYSTEM$TYPEOF(BITOR(1.5, 200))", "202|NUMBER(4,0)[SB2]"},
            {"SELECT BITOR(1.5, 2.5), SYSTEM$TYPEOF(BITOR(1.5, 2.5))", "3|NUMBER(3,0)[SB2]"},
            {"SELECT BITOR(9.5, 2), SYSTEM$TYPEOF(BITOR(9.5, 2))", "10|NUMBER(3,0)[SB2]"},
            {"SELECT BITOR(1.5::NUMBER(10,1), 2), SYSTEM$TYPEOF(BITOR(1.5::NUMBER(10,1), 2))", "2|NUMBER(10,0)[SB8]"},
            {"SELECT BITOR(123.25, 2), SYSTEM$TYPEOF(BITOR(123.25, 2))", "123|NUMBER(4,0)[SB2]"},
            {"SELECT BITAND(12.5, 2), SYSTEM$TYPEOF(BITAND(12.5, 2))", "0|NUMBER(2,0)[SB1]"},
            {"SELECT BITAND(123.5, 2), SYSTEM$TYPEOF(BITAND(123.5, 2))", "0|NUMBER(3,0)[SB2]"},
            {"SELECT BITXOR(123.5, 2), SYSTEM$TYPEOF(BITXOR(123.5, 2))", "126|NUMBER(3,0)[SB2]"},
            {"SELECT BITNOT(123.5), SYSTEM$TYPEOF(BITNOT(123.5))", "-125|NUMBER(3,0)[SB2]"},
            {"SELECT BITNOT(12.5), SYSTEM$TYPEOF(BITNOT(12.5))", "-14|NUMBER(2,0)[SB1]"},
            {"SELECT BITSHIFTRIGHT(12.5, 1), SYSTEM$TYPEOF(BITSHIFTRIGHT(12.5, 1))", "6|NUMBER(2,0)[SB1]"},
            {"SELECT BITSHIFTRIGHT(1, 2.5), SYSTEM$TYPEOF(BITSHIFTRIGHT(1, 2.5))", "0|NUMBER(2,0)[SB1]"},
            {"SELECT BITSHIFTRIGHT(1.5, 200), SYSTEM$TYPEOF(BITSHIFTRIGHT(1.5, 200))", "0|NUMBER(3,0)[SB2]"},
            {"SELECT BITAND(1.5, 200), SYSTEM$TYPEOF(BITAND(1.5, 200))", "0|NUMBER(3,0)[SB2]"},
            {"SELECT BITOR(-1.5, 2), SYSTEM$TYPEOF(BITOR(-1.5, 2))", "-2|NUMBER(3,0)[SB2]"},
            {"SELECT BITOR(1, -2.5), SYSTEM$TYPEOF(BITOR(1, -2.5))", "-3|NUMBER(3,0)[SB2]"},
            {"SELECT BITOR(1234.5, 2), SYSTEM$TYPEOF(BITOR(1234.5, 2))", "1235|NUMBER(5,0)[SB4]"},
            {"SELECT BITOR(1.5, 1234), SYSTEM$TYPEOF(BITOR(1.5, 1234))", "1234|NUMBER(5,0)[SB4]"},
            {"SELECT BITSHIFTRIGHT(1, 2000), SYSTEM$TYPEOF(BITSHIFTRIGHT(1, 2000))", "0|NUMBER(4,0)[SB2]"},
            {"SELECT BITSHIFTRIGHT(1, 2::NUMBER(10,0)), SYSTEM$TYPEOF(BITSHIFTRIGHT(1, 2::NUMBER(10,0)))",
                "0|NUMBER(10,0)[SB8]"},
            {"SELECT BITSHIFTRIGHT(1, '2'), SYSTEM$TYPEOF(BITSHIFTRIGHT(1, '2'))", "0|NUMBER(2,0)[SB1]"},
            {"SELECT BITOR(x, 2), SYSTEM$TYPEOF(BITOR(x, 2)) FROM bt", "3|NUMBER(4,0)[SB2]"},
            {"SELECT BITOR(y, 2), SYSTEM$TYPEOF(BITOR(y, 2)) FROM bt", "3|NUMBER(3,0)[SB2]"},
            {"SELECT BITAND(x, 2), SYSTEM$TYPEOF(BITAND(x, 2)) FROM bt", "0|NUMBER(3,0)[SB2]"},
            {"SELECT BITSHIFTRIGHT(x, 1), SYSTEM$TYPEOF(BITSHIFTRIGHT(x, 1)) FROM bt", "0|NUMBER(3,0)[SB2]"},
            {"SELECT BITSHIFTRIGHT(1, x), SYSTEM$TYPEOF(BITSHIFTRIGHT(1, x)) FROM bt", "0|NUMBER(3,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(BASE64_DECODE_STRING(g)), SYSTEM$TYPEOF(TRY_BASE64_DECODE_STRING(g)) FROM bt",
                "VARCHAR(3)[LOB]|VARCHAR(3)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(HEX_DECODE_STRING(h)), SYSTEM$TYPEOF(TRY_HEX_DECODE_STRING(h)) FROM bt",
                "VARCHAR(2)[LOB]|VARCHAR(2)[LOB]"},
            {"SELECT LPAD(b, 7, TO_BINARY('00', 'HEX')), SYSTEM$TYPEOF(LPAD(b, 7, TO_BINARY('00', 'HEX'))) FROM bt",
                "000000000000AB|BINARY[LOB]"},
            {"SELECT SYSTEM$TYPEOF(RPAD(b, 2, b)) FROM bt", "BINARY[LOB]"},
            {"SELECT TRY_BASE64_DECODE_STRING('QQ'), SYSTEM$TYPEOF(TRY_BASE64_DECODE_STRING('QQ'))",
                "null|VARCHAR(0)[LOB]"},
            {"SELECT TRY_BASE64_DECODE_STRING('QQ='), SYSTEM$TYPEOF(TRY_BASE64_DECODE_STRING('QQ='))",
                "null|VARCHAR(0)[LOB]"},
            {"SELECT TRY_BASE64_DECODE_STRING('QUJD'), SYSTEM$TYPEOF(TRY_BASE64_DECODE_STRING('QUJD'))",
                "ABC|VARCHAR(3)[LOB]"},
            {"SELECT TRY_BASE64_DECODE_STRING('QUJDRA'), SYSTEM$TYPEOF(TRY_BASE64_DECODE_STRING('QUJDRA'))",
                "null|VARCHAR(3)[LOB]"},
            {"SELECT TRY_HEX_DECODE_STRING('414'), SYSTEM$TYPEOF(TRY_HEX_DECODE_STRING('414'))",
                "null|VARCHAR(1)[LOB]"},
            {"SELECT TRY_HEX_DECODE_STRING('4142434'), SYSTEM$TYPEOF(TRY_HEX_DECODE_STRING('4142434'))",
                "null|VARCHAR(3)[LOB]"},
            {"SELECT TRY_BASE64_DECODE_STRING(''), SYSTEM$TYPEOF(TRY_BASE64_DECODE_STRING(''))", "|VARCHAR(0)[LOB]"},
            {"SELECT TRY_HEX_DECODE_STRING(''), SYSTEM$TYPEOF(TRY_HEX_DECODE_STRING(''))", "|VARCHAR(0)[LOB]"},
            {"SELECT TRY_BASE64_DECODE_STRING(NULL::VARCHAR(8)), "
                + "SYSTEM$TYPEOF(TRY_BASE64_DECODE_STRING(NULL::VARCHAR(8)))",
                "null|VARCHAR(6)[LOB]"},
            {"SELECT TRY_HEX_DECODE_STRING(NULL::VARCHAR(8)), SYSTEM$TYPEOF(TRY_HEX_DECODE_STRING(NULL::VARCHAR(8)))",
                "null|VARCHAR(4)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(CHECK_JSON(g)) FROM bt", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(CHECK_JSON(PARSE_JSON('1')))", "VARCHAR[LOB]"},
            {"SELECT CHECK_JSON('{a') || 'x', SYSTEM$TYPEOF(CHECK_JSON('{a') || 'x')",
                "incomplete object value, pos 3x|VARCHAR(134217728)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS(NULL::VARCHAR))", "VARCHAR(1)[LOB]"},
            {"SELECT NULLIFZERO(NULL::INT), SYSTEM$TYPEOF(NULLIFZERO(NULL::INT))", "null|NUMBER(38,0)[SB1]"},
            {"SELECT NULLIFZERO(NULL) || 'a', SYSTEM$TYPEOF(NULLIFZERO(NULL) || 'a')", "null|VARCHAR(134217728)[LOB]"},
            {"SELECT NULLIFZERO(NULL::VARCHAR), SYSTEM$TYPEOF(NULLIFZERO(NULL::VARCHAR))", "null|VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(CURRENT_TIME(0.0))", "TIME(0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(CURRENT_TIME(9.0))", "TIME(9)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(CURRENT_TIME(3.5))",
                "REFUSED SQL compilation error: | Invalid value [CAST(3.5 AS NUMBER(2,0))] for function "
                + "'CURRENT_TIME', parameter 1: must be an integer constant"},
            {"SELECT SYSTEM$TYPEOF(CURRENT_TIMESTAMP(0.0))", "TIMESTAMP_LTZ(0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(CURRENT_TIME(3.00))", "TIME(3)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(SYSDATE())", "TIMESTAMP_NTZ(9)[SB16]"},
            {"SELECT PI()::NUMBER(38,10), SYSTEM$TYPEOF(PI()::NUMBER(38,10))", "3.1415926536|NUMBER(38,10)[SB8]"},
            {"SELECT PI()::NUMBER(5,4), SYSTEM$TYPEOF(PI()::NUMBER(5,4))", "3.1416|NUMBER(5,4)[SB2]"},
            {"SELECT CAST(PI() AS INT), SYSTEM$TYPEOF(CAST(PI() AS INT))", "3|NUMBER(38,0)[SB1]"},
            {"SELECT TO_NUMBER(PI()), SYSTEM$TYPEOF(TO_NUMBER(PI()))", "3|NUMBER(38,0)[SB1]"},
            {"SELECT 3.14e0::NUMBER, SYSTEM$TYPEOF(3.14e0::NUMBER)", "3|NUMBER(38,0)[SB1]"},
            {"SELECT 314.15e0::NUMBER, SYSTEM$TYPEOF(314.15e0::NUMBER)", "314|NUMBER(38,0)[SB2]"},
            {"SELECT 2.5e0::INT, SYSTEM$TYPEOF(2.5e0::INT)", "3|NUMBER(38,0)[SB1]"},
            {"SELECT PI()::FLOAT::NUMBER, SYSTEM$TYPEOF(PI()::FLOAT::NUMBER)", "3|NUMBER(38,0)[SB16]"},
            {"SELECT (-PI())::NUMBER, SYSTEM$TYPEOF((-PI())::NUMBER)", "-3|NUMBER(38,0)[SB16]"},
            {"SELECT TO_DECIMAL(PI(), 10, 2), SYSTEM$TYPEOF(TO_DECIMAL(PI(), 10, 2))", "3.14|NUMBER(10,2)[SB2]"},
            {"SELECT PI()::NUMBER(38,30), SYSTEM$TYPEOF(PI()::NUMBER(38,30))",
                "3.141592653589793115997963468544|NUMBER(38,30)[SB16]"},
            {"SELECT TO_NUMBER('12', '99'), SYSTEM$TYPEOF(TO_NUMBER('12', '99'))", "12|NUMBER(38,0)[SB16]"},
            {"SELECT TO_DECIMAL('1,234', '9,999', 10, 0), SYSTEM$TYPEOF(TO_DECIMAL('1,234', '9,999', 10, 0))",
                "1234|NUMBER(10,0)[SB8]"},
            {"SELECT TRY_TO_DECIMAL('123456', 5, 0), SYSTEM$TYPEOF(TRY_TO_DECIMAL('123456', 5, 0))",
                "null|NUMBER(5,0)[SB4]"},
            {"SELECT TO_NUMBER('99999', 5, 0), SYSTEM$TYPEOF(TO_NUMBER('99999', 5, 0))", "99999|NUMBER(5,0)[SB4]"},
            {"SELECT TRY_TO_NUMBER('12.555', 5, 2), SYSTEM$TYPEOF(TRY_TO_NUMBER('12.555', 5, 2))",
                "12.56|NUMBER(5,2)[SB2]"},
            {"SELECT TRY_TO_NUMBER('1234.5', 5, 2), SYSTEM$TYPEOF(TRY_TO_NUMBER('1234.5', 5, 2))",
                "null|NUMBER(5,2)[SB4]"},
            {"SELECT TO_NUMBER('1234', 5, 0), SYSTEM$TYPEOF(TO_NUMBER('1234', 5, 0))", "1234|NUMBER(5,0)[SB2]"},
            {"SELECT TRY_TO_NUMBER('123456', 5), SYSTEM$TYPEOF(TRY_TO_NUMBER('123456', 5))", "null|NUMBER(5,0)[SB4]"},
            {"SELECT TRY_TO_NUMBER('123456'), SYSTEM$TYPEOF(TRY_TO_NUMBER('123456'))", "123456|NUMBER(38,0)[SB4]"},
            {"SELECT TRY_TO_NUMBER('1e40'), SYSTEM$TYPEOF(TRY_TO_NUMBER('1e40'))", "null|NUMBER(38,0)[SB16]"},
            {"SELECT TRY_TO_NUMBER('12', '99', 5, 0), SYSTEM$TYPEOF(TRY_TO_NUMBER('12', '99', 5, 0))",
                "12|NUMBER(5,0)[SB4]"},
            {"SELECT TRY_TO_NUMBER('12', 'xx', 5, 0), SYSTEM$TYPEOF(TRY_TO_NUMBER('12', 'xx', 5, 0))",
                "18|NUMBER(5,0)[SB1]"},
            {"SELECT TRY_TO_NUMERIC('123456', 5, 0), SYSTEM$TYPEOF(TRY_TO_NUMERIC('123456', 5, 0))",
                "null|NUMBER(5,0)[SB4]"},
            {"SELECT TRY_TO_NUMBER('123456', 38, 0), SYSTEM$TYPEOF(TRY_TO_NUMBER('123456', 38, 0))",
                "123456|NUMBER(38,0)[SB4]"},
            {"SELECT DATE_PART(epoch_second, '2024-03-15 10:20:30 +02:00'::TIMESTAMP_TZ), "
                + "SYSTEM$TYPEOF(DATE_PART(epoch_second, '2024-03-15 10:20:30 +02:00'::TIMESTAMP_TZ))",
                "1710490830|NUMBER(18,0)[SB4]"},
            {"SELECT DATE_PART(epoch_microsecond, '2024-03-15 10:20:30.123'::TIMESTAMP_NTZ), "
                + "SYSTEM$TYPEOF(DATE_PART(epoch_microsecond, '2024-03-15 10:20:30.123'::TIMESTAMP_NTZ))",
                "1710498030123000|NUMBER(38,0)[SB8]"},
        });
        assertShapedAnswer("<ID19>|NUMBER(38,0)[SB8]",
            "SELECT DATE_PART(epoch_nanosecond, '2024-03-15 10:20:30.123'::TIMESTAMP_NTZ), "
                + "SYSTEM$TYPEOF(DATE_PART(epoch_nanosecond, '2024-03-15 10:20:30.123'::TIMESTAMP_NTZ))");
        assertCells(new String[][] {
            {"SELECT DATE_PART(epoch, '2024-03-15 10:20:30.123'::TIMESTAMP_NTZ), SYSTEM$TYPEOF(DATE_PART(epoch, "
                + "'2024-03-15 10:20:30.123'::TIMESTAMP_NTZ))",
                "1710498030|NUMBER(18,0)[SB4]"},
            {"SELECT DATE_PART(epoch_second, '3000-01-01'::TIMESTAMP_NTZ), "
                + "SYSTEM$TYPEOF(DATE_PART(epoch_second, '3000-01-01'::TIMESTAMP_NTZ))",
                "32503680000|NUMBER(18,0)[SB8]"},
            {"SELECT DATE_PART(epoch_second, '1969-12-31 23:59:00'::TIMESTAMP_NTZ), "
                + "SYSTEM$TYPEOF(DATE_PART(epoch_second, '1969-12-31 23:59:00'::TIMESTAMP_NTZ))",
                "-60|NUMBER(18,0)[SB1]"},
            {"SELECT EXTRACT(epoch_millisecond FROM '2024-03-15 10:20:30.123'::TIMESTAMP_NTZ), "
                + "SYSTEM$TYPEOF(EXTRACT(epoch_millisecond FROM '2024-03-15 10:20:30.123'::TIMESTAMP_NTZ))",
                "1710498030123|NUMBER(38,0)[SB8]"},
            {"SELECT DATE_PART(epoch_second, TO_TIMESTAMP('2024-03-15 10:20:30')), "
                + "SYSTEM$TYPEOF(DATE_PART(epoch_second, TO_TIMESTAMP('2024-03-15 10:20:30')))",
                "1710498030|NUMBER(18,0)[SB4]"},
            {"SELECT DATE_PART(epoch_millisecond, '2024-03-15'::DATE), "
                + "SYSTEM$TYPEOF(DATE_PART(epoch_millisecond, '2024-03-15'::DATE))",
                "REFUSED SQL compilation error: | invalid value [EPOCH_MILLISECOND] for parameter 'DATE_PART "
                + "date/time part'"},
            {"SELECT DATE_PART(epoch_second, '1970-01-01 00:01:00'::TIMESTAMP_NTZ(0)), "
                + "SYSTEM$TYPEOF(DATE_PART(epoch_second, '1970-01-01 00:01:00'::TIMESTAMP_NTZ(0)))",
                "60|NUMBER(18,0)[SB1]"},
            {"SELECT DATE_PART(epoch_second, TIMESTAMP '2024-03-15 10:20:30'), "
                + "SYSTEM$TYPEOF(DATE_PART(epoch_second, TIMESTAMP '2024-03-15 10:20:30'))",
                "1710498030|NUMBER(18,0)[SB4]"},
        });
    }
}
