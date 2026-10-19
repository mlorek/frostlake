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
package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * What a scalar SQL function's parameter holds of its argument: the value itself, at its full precision and of
 * its own kind, whatever the session's output formats are. A FLOAT parameter is a FLOAT - of a NUMBER, a text
 * or a column alike - and computes as one, NaN and the infinities included; a TIME and a timestamp keep their
 * nine digits and their offset, and a parameter declared with fewer reads that many; a DATE, a TIME and a
 * timestamp are untouched by DATE_OUTPUT_FORMAT, TIME_OUTPUT_FORMAT and TIMESTAMP_NTZ_OUTPUT_FORMAT; a BINARY
 * is a BINARY; a VARIANT, an ARRAY and an OBJECT keep what they hold - a DATE or a BINARY inside stays one, a
 * DOUBLE a DOUBLE; a text keeps its quotes, backslashes and comment marks. A NULL argument is the NULL of the
 * parameter's type. An exact number takes the parameter's scale only when its own is smaller - {@code 1} into
 * a NUMBER(10,2) is {@code 1.00} of that type, {@code 1.005} stays a NUMBER(4,3), {@code 7} into a NUMBER a
 * NUMBER(1,0) - and an argument of another family arrives converted: a text or a FLOAT into a NUMBER is a
 * number of the parameter's type, anything into a VARCHAR a text of no width. A negative argument is one
 * value beside an operator of the body. The session parameters the class sets are unset whatever the
 * assertions make of the statements between. Every answer is live-verified.
 */
public class SqlFunctionArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void theValueItselfWhateverTheFormats() {
        assertAnswer(
            "Table U7_TF successfully created.",
            "CREATE OR REPLACE TABLE u7_tf (f FLOAT)");
        assertAnswer(
            "1",
            "INSERT INTO u7_tf VALUES (1)");
        assertAnswer(
            "Function U7_FFD successfully created.",
            "CREATE OR REPLACE FUNCTION u7_ffd(x FLOAT) RETURNS FLOAT AS 'x / 3'");
        assertAnswer(
            "Function U7_FFT successfully created.",
            "CREATE OR REPLACE FUNCTION u7_fft(x FLOAT) RETURNS VARCHAR AS 'SYSTEM$TYPEOF(x)'");
        answer(
            "SELECT u7_ffd(f), f / 3, u7_fft(f) FROM u7_tf");
        answer(
            "SELECT u7_ffd(1), u7_fft(1), u7_fft(1.5), u7_fft(1.5::FLOAT), u7_fft('2')");
        shape(
            "SELECT u7_ffd(f) FROM u7_tf");
        assertAnswer(
            "Function U7_FF successfully created.",
            "CREATE OR REPLACE FUNCTION u7_ff(x FLOAT) RETURNS FLOAT AS 'x + 1'");
        assertAnswer(
            "NaN",
            "SELECT u7_ff('NaN'::FLOAT)");
        assertAnswer(
            "-Infinity",
            "SELECT u7_ff('-inf'::FLOAT)");
        assertAnswer(
            "Infinity",
            "SELECT u7_ff('inf'::FLOAT)");
        assertAnswer(
            "null",
            "SELECT u7_ff(NULL)");
        assertAnswer(
            "true",
            "SELECT u7_ff(0.1::FLOAT + 0.2::FLOAT) = 0.1::FLOAT + 0.2::FLOAT + 1");
        assertAnswer(
            "Function U7_FQ successfully created.",
            "CREATE OR REPLACE FUNCTION u7_fq(x FLOAT) RETURNS FLOAT AS 'SELECT x / 3'");
        answer(
            "SELECT u7_fq(f), u7_fq(1) FROM u7_tf");
        assertAnswer(
            "Function U7_FTT successfully created.",
            "CREATE OR REPLACE FUNCTION u7_ftt(t TIME) RETURNS VARCHAR AS 'TO_VARCHAR(t, ''HH24:MI:SS.FF9'')'");
        assertAnswer(
            "12:00:00.123456789",
            "SELECT u7_ftt('12:00:00.123456789'::TIME)");
        assertAnswer(
            "Table U7_TT successfully created.",
            "CREATE OR REPLACE TABLE u7_tt (ts TIMESTAMP_NTZ(9))");
        assertAnswer(
            "1",
            "INSERT INTO u7_tt VALUES ('2024-01-01 00:00:00.999999999')");
        assertAnswer(
            "Function U7_TSEQ successfully created.",
            "CREATE OR REPLACE FUNCTION u7_tseq(a TIMESTAMP_NTZ(9), b TIMESTAMP_NTZ(9)) RETURNS BOOLEAN AS 'a = b'");
        assertAnswer(
            "false|false",
            "SELECT u7_tseq(ts, ts::TIMESTAMP_NTZ(3)), ts = ts::TIMESTAMP_NTZ(3) FROM u7_tt");
        assertAnswer(
            "Function U7_FTS successfully created.",
            "CREATE OR REPLACE FUNCTION u7_fts(t TIMESTAMP_NTZ) RETURNS VARCHAR AS 'REPLACE(REPLACE(TO_VARCHAR(t, "
                + "''YYYY-MM-DD HH24:MI:SS.FF9''), ''-'', ''_''), '':'', '';'')'");
        assertAnswer(
            "2024_01_01 00;00;00.123456000",
            "SELECT u7_fts('2024-01-01 00:00:00.123456'::TIMESTAMP_NTZ)");
        assertAnswer(
            "Function U7_FTZ successfully created.",
            "CREATE OR REPLACE FUNCTION u7_ftz(t TIMESTAMP_TZ) RETURNS VARCHAR AS 'REPLACE(REPLACE(TO_VARCHAR(t, "
                + "''YYYY-MM-DD HH24:MI:SS.FF9 TZHTZM''), ''-'', ''_''), '':'', '';'')'");
        assertAnswer(
            "2024_01_01 10;00;00.123456789 +0530",
            "SELECT u7_ftz('2024-01-01 10:00:00.123456789 +05:30'::TIMESTAMP_TZ)");
        assertAnswer(
            "Function U7_FM successfully created.",
            "CREATE OR REPLACE FUNCTION u7_fm(d DATE) RETURNS NUMBER AS 'MONTH(d)'");
        try {
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET DATE_OUTPUT_FORMAT = 'DD/MM/YYYY'");
            assertAnswer(
                "2|2",
                "SELECT u7_fm('2024-02-01'::DATE), MONTH('2024-02-01'::DATE)");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET DATE_OUTPUT_FORMAT = 'MON DD, YYYY'");
            assertAnswer(
                "2",
                "SELECT u7_fm('2024-02-01'::DATE)");
            assertAnswer(
                "Function U7_FH successfully created.",
                "CREATE OR REPLACE FUNCTION u7_fh(t TIMESTAMP_NTZ) RETURNS NUMBER AS 'HOUR(t)'");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMESTAMP_NTZ_OUTPUT_FORMAT = 'YYYY-MM-DD'");
            assertAnswer(
                "13",
                "SELECT u7_fh('2024-01-01 13:45:00'::TIMESTAMP_NTZ)");
            assertAnswer(
                "Function U7_FTH successfully created.",
                "CREATE OR REPLACE FUNCTION u7_fth(t TIME) RETURNS NUMBER AS 'HOUR(t)'");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIME_OUTPUT_FORMAT = 'HH12:MI AM'");
            assertAnswer(
                "15",
                "SELECT u7_fth('15:30:00'::TIME)");
        } finally {
            answer(
                "ALTER SESSION UNSET DATE_OUTPUT_FORMAT");
            answer(
                "ALTER SESSION UNSET TIMESTAMP_NTZ_OUTPUT_FORMAT");
            answer(
                "ALTER SESSION UNSET TIME_OUTPUT_FORMAT");
        }
        assertAnswer(
            "Function U7_FV successfully created.",
            "CREATE OR REPLACE FUNCTION u7_fv(v VARIANT) RETURNS VARCHAR AS 'TYPEOF(v) || '':'' || v::VARCHAR'");
        assertAnswer(
            "DATE:2024-01-01",
            "SELECT u7_fv(TO_VARIANT('2024-01-01'::DATE))");
        assertAnswer(
            "BINARY:ABCD",
            "SELECT u7_fv(TO_VARIANT(TO_BINARY('ABCD')))");
        assertAnswer(
            "DOUBLE:1.5|INTEGER:2|VARCHAR:abc",
            "SELECT u7_fv(TO_VARIANT(1.5::FLOAT)), u7_fv(TO_VARIANT(2)), u7_fv(TO_VARIANT('abc'))");
        assertAnswer(
            "TIME:10:11:12|null|null",
            "SELECT u7_fv(TO_VARIANT('10:11:12.5'::TIME)), u7_fv(PARSE_JSON('null')), u7_fv(NULL)");
        assertAnswer(
            "Table U7_TV successfully created.",
            "CREATE OR REPLACE TABLE u7_tv (v VARIANT)");
        assertAnswer(
            "1",
            "INSERT INTO u7_tv SELECT TO_VARIANT('2024-01-01'::DATE)");
        assertAnswer(
            "DATE:2024-01-01",
            "SELECT u7_fv(v) FROM u7_tv");
        assertAnswer(
            "Function U7_FA successfully created.",
            "CREATE OR REPLACE FUNCTION u7_fa(a ARRAY) RETURNS VARCHAR AS 'TYPEOF(a[0]) || '' '' || TYPEOF(a[1]) "
                + "|| '' '' || ARRAY_SIZE(a)'");
        assertAnswer(
            "DATE DOUBLE 2",
            "SELECT u7_fa(ARRAY_CONSTRUCT('2024-01-01'::DATE, 1.5::FLOAT))");
        assertAnswer(
            "Function U7_FO successfully created.",
            "CREATE OR REPLACE FUNCTION u7_fo(o OBJECT) RETURNS VARCHAR AS 'TYPEOF(o:d) || '' '' || TYPEOF(o:f)'");
        assertAnswer(
            "DATE DOUBLE",
            "SELECT u7_fo(OBJECT_CONSTRUCT('d', '2024-01-01'::DATE, 'f', 1.5::FLOAT))");
        assertAnswer(
            "Function U7_FS successfully created.",
            "CREATE OR REPLACE FUNCTION u7_fs(s VARCHAR) RETURNS VARCHAR AS 'LENGTH(s) || '':'' || s'");
        assertAnswer(
            "11:C:\\temp\\new",
            "SELECT u7_fs('C:\\\\temp\\\\new')");
        assertAnswer(
            "4:it's",
            "SELECT u7_fs('it''s')");
        assertAnswer(
            "4:a$$b",
            "SELECT u7_fs('a$$b')");
        assertAnswer(
            "0:",
            "SELECT u7_fs('')");
        assertAnswer(
            "null",
            "SELECT u7_fs(NULL)");
        assertAnswer(
            "16:-- not a comment",
            "SELECT u7_fs('-- not a comment')");
        assertAnswer(
            "8:tab\there",
            "SELECT u7_fs('tab\\there')");
        assertAnswer(
            "Function U7_FB successfully created.",
            "CREATE OR REPLACE FUNCTION u7_fb(b BINARY) RETURNS VARCHAR AS 'TO_VARCHAR(b, ''HEX'') || '' '' || "
                + "SYSTEM$TYPEOF(b)'");
        assertAnswer(
            "ABCD BINARY[LOB]",
            "SELECT u7_fb(TO_BINARY('ABCD', 'HEX'))");
        assertAnswer(
            "Function U7_FN successfully created.",
            "CREATE OR REPLACE FUNCTION u7_fn(x NUMBER(10,2)) RETURNS VARCHAR AS 'SYSTEM$TYPEOF(x) || '' '' || x'");
        assertAnswer(
            "NUMBER(4,3)[SB2] 1.234|NUMBER(10,2)[SB1] 1.00|NUMBER(10,2)[SB2] 1.50|NUMBER(10,2)[SB2] 7.56",
            "SELECT u7_fn(1.234), u7_fn(1), u7_fn(1.5::FLOAT), u7_fn('7.555')");
        assertAnswer(
            "Function U7_FBO successfully created.",
            "CREATE OR REPLACE FUNCTION u7_fbo(b BOOLEAN) RETURNS VARCHAR AS 'SYSTEM$TYPEOF(b) || '' '' || IFF(b, "
                + "''yes'', ''no'')'");
        assertAnswer(
            "BOOLEAN[SB1] yes|BOOLEAN[SB1] no|BOOLEAN[SB1] no",
            "SELECT u7_fbo(TRUE), u7_fbo(1 = 2), u7_fbo(NULL)");
        assertAnswer(
            "Function U7_FD successfully created.",
            "CREATE OR REPLACE FUNCTION u7_fd(d DATE) RETURNS VARCHAR AS 'SYSTEM$TYPEOF(d) || '' '' || DAYNAME(d)'");
        assertAnswer(
            "DATE[SB4] Thu|DATE[SB4] Thu|DATE[SB4] Thu",
            "SELECT u7_fd('2024-02-01'::DATE), u7_fd('2024-02-01'), u7_fd('2024-02-01 10:00:00'::TIMESTAMP_NTZ)");
        assertAnswer(
            "Function U7_FTL successfully created.",
            "CREATE OR REPLACE FUNCTION u7_ftl(t TIMESTAMP_LTZ) RETURNS VARCHAR AS 'SYSTEM$TYPEOF(t)'");
        assertAnswer(
            "TIMESTAMP_LTZ(9)[SB16]|TIMESTAMP_LTZ(9)[SB16]",
            "SELECT u7_ftl('2024-01-01 10:00:00'::TIMESTAMP_LTZ), u7_ftl('2024-01-01 10:00:00'::TIMESTAMP_NTZ)");
    }

    @Test
    public void theParametersTypeForEachArgument() {
        assertAnswer(
            "Function U8_N successfully created.",
            "CREATE OR REPLACE FUNCTION u8_n(x NUMBER) RETURNS VARCHAR AS 'SYSTEM$TYPEOF(x) || '' '' || "
                + "NVL(x::VARCHAR, ''null'')'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 91 | Invalid argument types for function "
                + "'U8_N': (BOOLEAN)",
            "SELECT u8_n(7), u8_n(1.5), u8_n(NULL), u8_n(7::NUMBER(10,0)), u8_n(1.5::FLOAT), u8_n('8'), u8_n(TRUE)");
        assertAnswer(
            "Function U8_N2 successfully created.",
            "CREATE OR REPLACE FUNCTION u8_n2(x NUMBER(10,2)) RETURNS VARCHAR AS 'SYSTEM$TYPEOF(x) || '' '' || "
                + "NVL(x::VARCHAR, ''null'')'");
        answer(
            "SELECT u8_n2(1.50), u8_n2(1.5), u8_n2(12345678901.5), u8_n2(NULL), u8_n2(1.005), u8_n2(2::NUMBER(38,"
                + "0))");
        assertAnswer(
            "Function U8_F successfully created.",
            "CREATE OR REPLACE FUNCTION u8_f(x FLOAT) RETURNS VARCHAR AS 'SYSTEM$TYPEOF(x) || '' '' || "
                + "NVL(x::VARCHAR, ''null'')'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 28 | Invalid argument types for function "
                + "'U8_F': (BOOLEAN)",
            "SELECT u8_f(NULL), u8_f(1), u8_f(TRUE), u8_f(1e300::FLOAT), u8_f(0.1::FLOAT + 0.2::FLOAT)");
        assertAnswer(
            "Function U8_T successfully created.",
            "CREATE OR REPLACE FUNCTION u8_t(t TIME) RETURNS VARCHAR AS 'SYSTEM$TYPEOF(t) || '' '' || "
                + "NVL(TO_VARCHAR(t, ''HH24:MI:SS.FF9''), ''null'')'");
        assertAnswer(
            "TIME(9)[SB8] 10:00:00.000000000|TIME(9)[SB8] 10:00:00.500000000|TIME(9)[SB8] null|TIME(9)[SB8] "
                + "10:00:01.000000000|TIME(9)[SB8] 11:12:13.123456789",
            "SELECT u8_t('10:00:00'::TIME), u8_t('10:00:00.5'::TIME(3)), u8_t(NULL), u8_t('10:00:01'), "
                + "u8_t('2024-01-01 11:12:13.123456789'::TIMESTAMP_NTZ)");
        assertAnswer(
            "Function U8_T3 successfully created.",
            "CREATE OR REPLACE FUNCTION u8_t3(t TIMESTAMP_NTZ(3)) RETURNS VARCHAR AS 'SYSTEM$TYPEOF(t) || '' '' "
                + "|| TO_VARCHAR(t, ''YYYY-MM-DD HH24:MI:SS.FF9'')'");
        assertAnswer(
            "TIMESTAMP_NTZ(3)[SB8] 2024_01_01 00;00;00.123000000|TIMESTAMP_NTZ(3)[SB8] 2024_01_01 "
                + "00;00;00.000000000|TIMESTAMP_NTZ(3)[SB8] 2024_01_01 00;00;00.500000000",
            "SELECT REPLACE(REPLACE(u8_t3('2024-01-01 00:00:00.123456789'::TIMESTAMP_NTZ(9)), '-', '_'), ':', "
                + "';'), REPLACE(REPLACE(u8_t3('2024-01-01'::DATE), '-', '_'), ':', ';'), "
                + "REPLACE(REPLACE(u8_t3('2024-01-01 00:00:00.5'), '-', '_'), ':', ';')");
        assertAnswer(
            "Function U8_S successfully created.",
            "CREATE OR REPLACE FUNCTION u8_s(s VARCHAR) RETURNS VARCHAR AS 'SYSTEM$TYPEOF(s) || '':'' || NVL(s, "
                + "''null'')'");
        assertAnswer(
            "VARCHAR(3)[LOB]:abc|VARCHAR[LOB]:12.5|VARCHAR[LOB]:true|VARCHAR[LOB]:2024-01-01|VARCHAR[LOB]:null|VARCHAR[LOB]:1.5",
            "SELECT u8_s('abc'), u8_s(12.50), u8_s(TRUE), u8_s('2024-01-01'::DATE), u8_s(NULL), u8_s(1.5::FLOAT)");
        assertAnswer(
            "Function U8_S2 successfully created.",
            "CREATE OR REPLACE FUNCTION u8_s2(s VARCHAR(2)) RETURNS VARCHAR AS 'SYSTEM$TYPEOF(s) || '':'' || s'");
        assertAnswer(
            "VARCHAR(6)[LOB]:abcdef|VARCHAR(1)[LOB]:a",
            "SELECT u8_s2('abcdef'), u8_s2('a')");
        assertAnswer(
            "Function U8_B successfully created.",
            "CREATE OR REPLACE FUNCTION u8_b(b BINARY) RETURNS VARCHAR AS 'SYSTEM$TYPEOF(b) || '':'' || "
                + "NVL(TO_VARCHAR(b, ''HEX''), ''null'')'");
        assertAnswer(
            "BINARY[LOB]:null|BINARY[LOB]:AB",
            "SELECT u8_b(NULL), u8_b(TO_BINARY('AB', 'HEX'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'U8_B': (VARCHAR(4))",
            "SELECT u8_b('ABCD')");
        assertAnswer(
            "Function U8_V successfully created.",
            "CREATE OR REPLACE FUNCTION u8_v(v VARIANT) RETURNS VARCHAR AS 'NVL(TYPEOF(v), ''sql null'') || '':'' "
                + "|| NVL(v::VARCHAR, ''null'')'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 16 | Invalid argument types for function "
                + "'U8_V': (VARCHAR(3))",
            "SELECT u8_v(5), u8_v('abc'), u8_v('2024-01-01'::DATE), u8_v(ARRAY_CONSTRUCT(1)), u8_v(TRUE), "
                + "u8_v(1.50), u8_v(1.5::FLOAT), u8_v(NULL)");
        assertAnswer(
            "Function U8_A successfully created.",
            "CREATE OR REPLACE FUNCTION u8_a(a ARRAY) RETURNS VARCHAR AS 'TYPEOF(a) || '':'' || TO_JSON(a)'");
        assertAnswer(
            "ARRAY:[1]|ARRAY:[]",
            "SELECT u8_a(PARSE_JSON('[1]')), u8_a(ARRAY_CONSTRUCT())");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'U8_A': (NUMBER(1,0))",
            "SELECT u8_a(1)");
        assertAnswer(
            "Function U8_D successfully created.",
            "CREATE OR REPLACE FUNCTION u8_d(d DATE) RETURNS VARCHAR AS 'SYSTEM$TYPEOF(d) || '' '' || "
                + "NVL(d::VARCHAR, ''null'')'");
        assertAnswer(
            "DATE[SB4] null",
            "SELECT u8_d(NULL)");
        assertAnswer(
            "Function U8_BO successfully created.",
            "CREATE OR REPLACE FUNCTION u8_bo(b BOOLEAN) RETURNS VARCHAR AS 'SYSTEM$TYPEOF(b) || '' '' || "
                + "NVL(b::VARCHAR, ''null'')'");
        assertAnswer(
            "BOOLEAN[SB1] true|BOOLEAN[SB1] false|BOOLEAN[SB1] null",
            "SELECT u8_bo('yes'), u8_bo(0), u8_bo(NULL)");
        assertAnswer(
            "Function U8_FF successfully created.",
            "CREATE OR REPLACE FUNCTION u8_ff(x FLOAT) RETURNS FLOAT AS 'x + 1'");
        assertAnswer(
            "3.5|FLOAT[DOUBLE]",
            "SELECT u8_ff(u8_ff(1.5::FLOAT)), SYSTEM$TYPEOF(u8_ff(1))");
        assertAnswer(
            "Function U8_SUB successfully created.",
            "CREATE OR REPLACE FUNCTION u8_sub(x FLOAT) RETURNS FLOAT AS '(SELECT x / 3)'");
        answer(
            "SELECT u8_sub(1), u8_sub(1::FLOAT)");
        assertAnswer(
            "Function U8_Q successfully created.",
            "CREATE OR REPLACE FUNCTION u8_q(t TIME) RETURNS VARCHAR AS 'SELECT TO_VARCHAR(t, ''HH24:MI:SS.FF9'')'");
        assertAnswer(
            "12:00:00.123456789",
            "SELECT u8_q('12:00:00.123456789'::TIME)");
        assertAnswer(
            "Table U8_TC successfully created.",
            "CREATE OR REPLACE TABLE u8_tc (t TIME(9), i NUMBER(10,0), n NUMBER(10,4))");
        assertAnswer(
            "1",
            "INSERT INTO u8_tc VALUES ('01:02:03.123456789', 3, 1.2345)");
        answer(
            "SELECT u8_t(t), u8_f(i), u8_n2(n), u8_n(n), u8_n2(i) FROM u8_tc");
    }

    @Test
    public void floatsNumbersVariantsAndNegatives() {
        assertAnswer(
            "Table U9_TF successfully created.",
            "CREATE OR REPLACE TABLE u9_tf (f FLOAT)");
        assertAnswer(
            "1",
            "INSERT INTO u9_tf VALUES (1)");
        assertAnswer(
            "Function U9_FFD successfully created.",
            "CREATE OR REPLACE FUNCTION u9_ffd(x FLOAT) RETURNS FLOAT AS 'x / 3'");
        assertAnswer(
            "0.3333333333|0.3333333333|true|true",
            "SELECT u9_ffd(f)::VARCHAR, (f / 3)::VARCHAR, u9_ffd(f) = f / 3, u9_ffd(1) = 1::FLOAT / 3 FROM u9_tf");
        assertAnswer(
            "Function U9_FQ successfully created.",
            "CREATE OR REPLACE FUNCTION u9_fq(x FLOAT) RETURNS FLOAT AS 'SELECT x / 3'");
        assertAnswer(
            "0.3333333333|true",
            "SELECT u9_fq(f)::VARCHAR, u9_fq(1) = 1::FLOAT / 3 FROM u9_tf");
        assertAnswer(
            "Function U9_SUB successfully created.",
            "CREATE OR REPLACE FUNCTION u9_sub(x FLOAT) RETURNS FLOAT AS '(SELECT x / 3)'");
        assertAnswer(
            "0.3333333333|true",
            "SELECT u9_sub(1)::VARCHAR, u9_sub(1::FLOAT) = 1::FLOAT / 3");
        assertAnswer(
            "Function U9_N successfully created.",
            "CREATE OR REPLACE FUNCTION u9_n(x NUMBER) RETURNS VARCHAR AS 'SYSTEM$TYPEOF(x) || '' '' || "
                + "NVL(x::VARCHAR, ''null'')'");
        assertAnswer(
            "NUMBER(1,0)[SB1] 7",
            "SELECT u9_n(7)");
        assertAnswer(
            "NUMBER(2,1)[SB1] 1.5",
            "SELECT u9_n(1.5)");
        assertAnswer(
            "NUMBER(38,0)[SB1] null",
            "SELECT u9_n(NULL)");
        answer(
            "SELECT u9_n(7::NUMBER(10,0))");
        assertAnswer(
            "NUMBER(38,0)[SB1] 2",
            "SELECT u9_n(1.5::FLOAT)");
        assertAnswer(
            "NUMBER(38,0)[SB1] 8",
            "SELECT u9_n('8')");
        assertAnswer(
            "NUMBER(1,0)[SB1] -3",
            "SELECT u9_n(-3)");
        assertAnswer(
            "Function U9_F successfully created.",
            "CREATE OR REPLACE FUNCTION u9_f(x FLOAT) RETURNS VARCHAR AS 'SYSTEM$TYPEOF(x) || '' '' || "
                + "NVL(x::VARCHAR, ''null'')'");
        assertAnswer(
            "FLOAT[DOUBLE] null",
            "SELECT u9_f(NULL)");
        assertAnswer(
            "FLOAT[DOUBLE] 1",
            "SELECT u9_f(1)");
        assertAnswer(
            "FLOAT[DOUBLE] 1e+300",
            "SELECT u9_f(1e300::FLOAT)");
        assertAnswer(
            "FLOAT[DOUBLE] 0.3",
            "SELECT u9_f(0.1::FLOAT + 0.2::FLOAT)");
        assertAnswer(
            "FLOAT[DOUBLE] -1.5",
            "SELECT u9_f(-1.5)");
        assertAnswer(
            "Function U9_V successfully created.",
            "CREATE OR REPLACE FUNCTION u9_v(v VARIANT) RETURNS VARCHAR AS 'NVL(TYPEOF(v), ''sql null'') || '':'' "
                + "|| NVL(v::VARCHAR, ''null'')'");
        assertAnswer(
            "INTEGER:5",
            "SELECT u9_v(5)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'U9_V': (DATE)",
            "SELECT u9_v('2024-01-01'::DATE)");
        assertAnswer(
            "ARRAY:[1]",
            "SELECT u9_v(ARRAY_CONSTRUCT(1))");
        assertAnswer(
            "BOOLEAN:true",
            "SELECT u9_v(TRUE)");
        assertAnswer(
            "DECIMAL:1.5",
            "SELECT u9_v(1.50)");
        assertAnswer(
            "DOUBLE:1.5",
            "SELECT u9_v(1.5::FLOAT)");
        assertAnswer(
            "sql null:null",
            "SELECT u9_v(NULL)");
        assertAnswer(
            "VARCHAR:abc",
            "SELECT u9_v(TO_VARIANT('abc'))");
        assertAnswer(
            "Function U9_NEG successfully created.",
            "CREATE OR REPLACE FUNCTION u9_neg(x NUMBER) RETURNS NUMBER AS '2-x'");
        assertAnswer(
            "7|-3",
            "SELECT u9_neg(-5), u9_neg(5)");
        assertAnswer(
            "Function U9_NEGF successfully created.",
            "CREATE OR REPLACE FUNCTION u9_negf(x FLOAT) RETURNS VARCHAR AS 'x::VARCHAR'");
        assertAnswer(
            "-1.5|-2",
            "SELECT u9_negf(-1.5), u9_negf(-2)");
    }
}
