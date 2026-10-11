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
 * What a SQL table function's parameter holds of its argument, called directly and laterally: the value
 * itself, of its own type - a DATE, a TIME and a timestamp with all their digits, a VARIANT, an ARRAY and an
 * OBJECT with what they hold, a BINARY, a FLOAT with NaN and the infinities, a text with its backslashes and
 * quotes. A table function converts nothing: an argument of any other type than its parameter's - a NUMBER of
 * another scale, a number for a FLOAT, a TIME or a timestamp of other digits, a text for a DATE, a NULL - is
 * {@code Invalid argument types for function}. Every answer is live-verified.
 */
public class SqlTableFunctionArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void theValueItself() {
        assertAnswer(
            "Function U7_TD successfully created.",
            "CREATE OR REPLACE FUNCTION u7_td(d DATE) RETURNS TABLE (x DATE) AS 'SELECT d'");
        assertAnswer(
            "2024-01-15|DATE[SB4]",
            "SELECT x, SYSTEM$TYPEOF(x) FROM TABLE(u7_td('2024-01-15'::DATE))");
        assertAnswer(
            "Function U7_TD2 successfully created.",
            "CREATE OR REPLACE FUNCTION u7_td2(d DATE) RETURNS TABLE (y NUMBER) AS 'SELECT YEAR(d)'");
        assertAnswer(
            "2024",
            "SELECT * FROM TABLE(u7_td2('2024-01-15'::DATE))");
        assertAnswer(
            "Function U7_TTS successfully created.",
            "CREATE OR REPLACE FUNCTION u7_tts(t TIMESTAMP_NTZ) RETURNS TABLE (x VARCHAR) AS 'SELECT "
                + "REPLACE(REPLACE(TO_VARCHAR(t, ''YYYY-MM-DD HH24:MI:SS.FF9''), ''-'', ''_''), '':'', '';'')'");
        assertAnswer(
            "2024_01_15 10;30;00.123456789",
            "SELECT * FROM TABLE(u7_tts('2024-01-15 10:30:00.123456789'::TIMESTAMP_NTZ))");
        assertAnswer(
            "Function U7_TTM successfully created.",
            "CREATE OR REPLACE FUNCTION u7_ttm(t TIME) RETURNS TABLE (x VARCHAR) AS 'SELECT TO_VARCHAR(t, "
                + "''HH24:MI:SS.FF9'')'");
        assertAnswer(
            "10:30:00.123456789",
            "SELECT * FROM TABLE(u7_ttm('10:30:00.123456789'::TIME))");
        assertAnswer(
            "Function U7_TVV successfully created.",
            "CREATE OR REPLACE FUNCTION u7_tvv(v VARIANT) RETURNS TABLE (x VARIANT, t VARCHAR) AS 'SELECT v, "
                + "TYPEOF(v)'");
        assertAnswer(
            "{\"a\":1}|OBJECT",
            "SELECT * FROM TABLE(u7_tvv(PARSE_JSON('{\"a\":1}')))");
        assertAnswer(
            "2024-01-15|DATE",
            "SELECT * FROM TABLE(u7_tvv(TO_VARIANT('2024-01-15'::DATE)))");
        assertAnswer(
            "1.5|DOUBLE",
            "SELECT * FROM TABLE(u7_tvv(TO_VARIANT(1.5::FLOAT)))");
        assertAnswer(
            "Function U7_TA successfully created.",
            "CREATE OR REPLACE FUNCTION u7_ta(a ARRAY) RETURNS TABLE (x ARRAY, n NUMBER) AS 'SELECT a, "
                + "ARRAY_SIZE(a)'");
        assertAnswer(
            "[\"p\",\"q\"]|2",
            "SELECT * FROM TABLE(u7_ta(ARRAY_CONSTRUCT('p', 'q')))");
        assertAnswer(
            "[1,2]|2",
            "SELECT * FROM TABLE(u7_ta(ARRAY_CONSTRUCT(1, 2)))");
        assertAnswer(
            "Function U7_TB successfully created.",
            "CREATE OR REPLACE FUNCTION u7_tb(b BINARY) RETURNS TABLE (x VARCHAR) AS 'SELECT TO_VARCHAR(b, "
                + "''HEX'')'");
        assertAnswer(
            "ABCD",
            "SELECT * FROM TABLE(u7_tb(TO_BINARY('ABCD', 'HEX')))");
        assertAnswer(
            "Function U7_TF2 successfully created.",
            "CREATE OR REPLACE FUNCTION u7_tf2(f FLOAT) RETURNS TABLE (x FLOAT, t VARCHAR) AS 'SELECT f, "
                + "SYSTEM$TYPEOF(f)'");
        assertAnswer(
            "NaN|FLOAT[DOUBLE]",
            "SELECT * FROM TABLE(u7_tf2('NaN'::FLOAT))");
        assertAnswer(
            "-Infinity|FLOAT[DOUBLE]",
            "SELECT * FROM TABLE(u7_tf2('-inf'::FLOAT))");
        assertAnswer(
            "1.5|FLOAT[DOUBLE]",
            "SELECT * FROM TABLE(u7_tf2(1.5::FLOAT))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'U7_TF2': (NUMBER(1,0))",
            "SELECT * FROM TABLE(u7_tf2(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'U7_TF2': (NULL)",
            "SELECT * FROM TABLE(u7_tf2(NULL))");
        assertAnswer(
            "Function U7_TS successfully created.",
            "CREATE OR REPLACE FUNCTION u7_ts(s VARCHAR) RETURNS TABLE (x VARCHAR, n NUMBER) AS 'SELECT s, "
                + "LENGTH(s)'");
        assertAnswer(
            "C:\\temp\\new|11",
            "SELECT * FROM TABLE(u7_ts('C:\\\\temp\\\\new'))");
        assertAnswer(
            "C:\\temp\\new|11",
            "SELECT 'C:\\\\temp\\\\new', LENGTH('C:\\\\temp\\\\new')");
        assertAnswer(
            "it's|4",
            "SELECT * FROM TABLE(u7_ts('it''s'))");
        assertAnswer(
            "-- x|4",
            "SELECT * FROM TABLE(u7_ts('-- x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'U7_TS': (NULL)",
            "SELECT * FROM TABLE(u7_ts(NULL))");
        assertAnswer(
            "|0",
            "SELECT * FROM TABLE(u7_ts(''))");
        assertAnswer(
            "Table U7_DSRC successfully created.",
            "CREATE OR REPLACE TABLE u7_dsrc (d DATE, s VARCHAR)");
        assertAnswer(
            "2",
            "INSERT INTO u7_dsrc VALUES ('2024-03-15', 'a\\\\b'), ('2023-01-01', 'it''s')");
        assertAnswer(
            "2023 ;; 2024",
            "SELECT u.y FROM u7_dsrc, TABLE(u7_td2(u7_dsrc.d)) u ORDER BY 1");
        assertAnswer(
            "a\\b|3 ;; it's|4",
            "SELECT u.x, u.n FROM u7_dsrc, TABLE(u7_ts(u7_dsrc.s)) u ORDER BY 2, 1");
        assertAnswer(
            "Function U7_TN successfully created.",
            "CREATE OR REPLACE FUNCTION u7_tn(n NUMBER, b BOOLEAN) RETURNS TABLE (x NUMBER, y BOOLEAN, t VARCHAR) "
                + "AS 'SELECT n, b, SYSTEM$TYPEOF(n) || '' '' || SYSTEM$TYPEOF(b)'");
        assertAnswer(
            "7|true|NUMBER(1,0)[SB1] BOOLEAN[SB1]",
            "SELECT * FROM TABLE(u7_tn(7, TRUE))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'U7_TN': (NUMBER(2,1), BOOLEAN)",
            "SELECT * FROM TABLE(u7_tn(1.5, FALSE))");
        assertAnswer(
            "Function U7_TO successfully created.",
            "CREATE OR REPLACE FUNCTION u7_to(o OBJECT) RETURNS TABLE (x VARCHAR) AS 'SELECT TYPEOF(o:d) || '' '' "
                + "|| o:d::VARCHAR'");
        assertAnswer(
            "DATE 2024-01-15",
            "SELECT * FROM TABLE(u7_to(OBJECT_CONSTRUCT('d', '2024-01-15'::DATE)))");
        assertAnswer(
            "Function U7_TZ successfully created.",
            "CREATE OR REPLACE FUNCTION u7_tz(t TIMESTAMP_TZ) RETURNS TABLE (x VARCHAR) AS 'SELECT "
                + "REPLACE(REPLACE(TO_VARCHAR(t, ''YYYY-MM-DD HH24:MI:SS.FF3 TZHTZM''), ''-'', ''_''), '':'', '';'')'");
        assertAnswer(
            "2024_01_15 10;30;00.000 +0530",
            "SELECT * FROM TABLE(u7_tz('2024-01-15 10:30:00 +05:30'::TIMESTAMP_TZ))");
    }

    @Test
    public void onlyArgumentsOfTheParametersOwnType() {
        assertAnswer(
            "Function U8_TTY successfully created.",
            "CREATE OR REPLACE FUNCTION u8_tty(d DATE, t TIME, ts TIMESTAMP_NTZ, v VARIANT, s VARCHAR, b BINARY) "
                + "RETURNS TABLE (x VARCHAR) AS 'SELECT SYSTEM$TYPEOF(d) || '' | '' || SYSTEM$TYPEOF(t) || '' | '' "
                + "|| SYSTEM$TYPEOF(ts) || '' | '' || SYSTEM$TYPEOF(v) || '' | '' || SYSTEM$TYPEOF(s) || '' | '' || "
                + "SYSTEM$TYPEOF(b)'");
        assertAnswer(
            "DATE[SB4] | TIME(9)[SB8] | TIMESTAMP_NTZ(9)[SB16] | VARIANT[LOB] | VARCHAR(3)[LOB] | BINARY[LOB]",
            "SELECT * FROM TABLE(u8_tty('2024-01-15'::DATE, '10:30:00'::TIME, '2024-01-15 "
                + "10:30:00'::TIMESTAMP_NTZ, PARSE_JSON('1'), 'abc', TO_BINARY('AB', 'HEX')))");
        assertAnswer(
            "Function U8_TN2 successfully created.",
            "CREATE OR REPLACE FUNCTION u8_tn2(n NUMBER(10,2)) RETURNS TABLE (x VARCHAR) AS 'SELECT "
                + "SYSTEM$TYPEOF(n) || '' '' || n'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'U8_TN2': (NUMBER(2,1))",
            "SELECT * FROM TABLE(u8_tn2(1.50))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'U8_TN2': (NUMBER(1,0))",
            "SELECT * FROM TABLE(u8_tn2(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'U8_TN2': (NUMBER(4,3))",
            "SELECT * FROM TABLE(u8_tn2(1.234))");
        assertAnswer(
            "Function U8_TF successfully created.",
            "CREATE OR REPLACE FUNCTION u8_tf(f FLOAT) RETURNS TABLE (x VARCHAR) AS 'SELECT SYSTEM$TYPEOF(f) || "
                + "'' '' || f'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'U8_TF': (NUMBER(2,1))",
            "SELECT * FROM TABLE(u8_tf(1.5))");
        assertAnswer(
            "FLOAT[DOUBLE] 1.5",
            "SELECT * FROM TABLE(u8_tf(1.5::FLOAT))");
        assertAnswer(
            "Function U8_TV successfully created.",
            "CREATE OR REPLACE FUNCTION u8_tv(v VARIANT) RETURNS TABLE (x VARCHAR) AS 'SELECT TYPEOF(v) || '':'' "
                + "|| v::VARCHAR'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'U8_TV': (NUMBER(1,0))",
            "SELECT * FROM TABLE(u8_tv(5))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'U8_TV': (VARCHAR(3))",
            "SELECT * FROM TABLE(u8_tv('abc'))");
        assertAnswer(
            "INTEGER:5",
            "SELECT * FROM TABLE(u8_tv(TO_VARIANT(5)))");
        assertAnswer(
            "BINARY:AB",
            "SELECT * FROM TABLE(u8_tv(TO_VARIANT(TO_BINARY('AB', 'HEX'))))");
        assertAnswer(
            "Function U8_TSV successfully created.",
            "CREATE OR REPLACE FUNCTION u8_tsv(s VARCHAR) RETURNS TABLE (x VARCHAR) AS 'SELECT SYSTEM$TYPEOF(s) "
                + "|| '':'' || s'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'U8_TSV': (NUMBER(1,0))",
            "SELECT * FROM TABLE(u8_tsv(5))");
        assertAnswer(
            "VARCHAR(3)[LOB]:abc",
            "SELECT * FROM TABLE(u8_tsv('abc'))");
        assertAnswer(
            "Function U8_TDD successfully created.",
            "CREATE OR REPLACE FUNCTION u8_tdd(d DATE) RETURNS TABLE (x VARCHAR) AS 'SELECT SYSTEM$TYPEOF(d) || "
                + "'' '' || d'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'U8_TDD': (TIMESTAMP_NTZ(9))",
            "SELECT * FROM TABLE(u8_tdd('2024-01-15 10:00:00'::TIMESTAMP_NTZ))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'U8_TDD': (VARCHAR(10))",
            "SELECT * FROM TABLE(u8_tdd('2024-01-15'))");
        assertAnswer(
            "DATE[SB4] 2024-01-15",
            "SELECT * FROM TABLE(u8_tdd('2024-01-15'::DATE))");
        assertAnswer(
            "Function U8_TT9 successfully created.",
            "CREATE OR REPLACE FUNCTION u8_tt9(t TIME) RETURNS TABLE (x VARCHAR) AS 'SELECT SYSTEM$TYPEOF(t) || "
                + "'' '' || TO_VARCHAR(t, ''HH24:MI:SS.FF9'')'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'U8_TT9': (TIME(3))",
            "SELECT * FROM TABLE(u8_tt9('10:30:00.5'::TIME(3)))");
        assertAnswer(
            "Function U8_TBO successfully created.",
            "CREATE OR REPLACE FUNCTION u8_tbo(b BOOLEAN) RETURNS TABLE (x VARCHAR) AS 'SELECT SYSTEM$TYPEOF(b) "
                + "|| '' '' || b'");
        assertAnswer(
            "BOOLEAN[SB1] true",
            "SELECT * FROM TABLE(u8_tbo(TRUE))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'U8_TBO': (NUMBER(1,0))",
            "SELECT * FROM TABLE(u8_tbo(1))");
        assertAnswer(
            "Function U9_TNEG successfully created.",
            "CREATE OR REPLACE FUNCTION u9_tneg(n NUMBER) RETURNS TABLE (x NUMBER) AS 'SELECT 2-n'");
        assertAnswer(
            "7",
            "SELECT * FROM TABLE(u9_tneg(-5))");
        assertAnswer(
            "Function U9_TTS successfully created.",
            "CREATE OR REPLACE FUNCTION u9_tts(t TIMESTAMP_NTZ) RETURNS TABLE (x VARCHAR) AS 'SELECT "
                + "SYSTEM$TYPEOF(t)'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'U9_TTS': (TIMESTAMP_NTZ(3))",
            "SELECT * FROM TABLE(u9_tts('2024-01-15 10:30:00.5'::TIMESTAMP_NTZ(3)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'U9_TTS': (TIMESTAMP_LTZ(9))",
            "SELECT * FROM TABLE(u9_tts('2024-01-15 10:30:00'::TIMESTAMP_LTZ))");
        assertAnswer(
            "TIMESTAMP_NTZ(9)[SB16]",
            "SELECT * FROM TABLE(u9_tts('2024-01-15 10:30:00'::TIMESTAMP_NTZ))");
        assertAnswer(
            "Function U9_TT3 successfully created.",
            "CREATE OR REPLACE FUNCTION u9_tt3(t TIME(3)) RETURNS TABLE (x VARCHAR) AS 'SELECT SYSTEM$TYPEOF(t)'");
        assertAnswer(
            "TIME(3)[SB4]",
            "SELECT * FROM TABLE(u9_tt3('10:30:00.5'::TIME(3)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'U9_TT3': (TIME(9))",
            "SELECT * FROM TABLE(u9_tt3('10:30:00.5'::TIME))");
        assertAnswer(
            "Function U9_TB successfully created.",
            "CREATE OR REPLACE FUNCTION u9_tb(b BINARY) RETURNS TABLE (x VARCHAR) AS 'SELECT SYSTEM$TYPEOF(b)'");
        assertAnswer(
            "BINARY[LOB]",
            "SELECT * FROM TABLE(u9_tb(TO_BINARY('AB', 'HEX')))");
        answer(
            "SELECT * FROM TABLE(u9_tb(X'AB'))");
    }
}
