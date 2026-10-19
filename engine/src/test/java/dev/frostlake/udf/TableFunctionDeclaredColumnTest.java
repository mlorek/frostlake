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
 * What a SQL table function's rows hold against its declared RETURNS TABLE columns. A column declared an exact
 * NUMBER holds each value at the declared scale, rounded half away from zero: {@code RETURNS TABLE (a INT) AS
 * 'SELECT 10 / k'} answers 3 for k = 3 and a WHERE a = 3 finds the row, a NUMBER(5,2) column answers 1.26 for
 * 1.257 and 1.50 for 1.5 - directly, laterally, in an aggregate, a UNION, a view, a CTAS and an INSERT. A value
 * with more digits than the column's precision holds is {@code Number out of representable range: type
 * FIXED[SB2](3,1){nullable}, value 1234.5}, raised where a statement reads that column and not where it reads
 * the function's other columns or counts its rows. A text keeps its characters whatever width the column
 * declares. A body whose column is of another family - a number for a FLOAT, a text for a DATE, a timestamp of
 * other digits - is refused when the function is created, as is a body with another number of columns. Every
 * answer is live-verified.
 */
public class TableFunctionDeclaredColumnTest extends MeasuredCellsTestSupport {

    @Test
    public void numbersAtTheDeclaredScale() {
        assertAnswer(
            "Function SQL_DIV successfully created.",
            "CREATE OR REPLACE FUNCTION sql_div(k INT) RETURNS TABLE (a INT) AS 'SELECT 10 / k'");
        assertAnswer(
            "3",
            "SELECT a FROM TABLE(sql_div(3)) WHERE a = 3");
        assertAnswer(
            "3|9",
            "SELECT a::VARCHAR, a * 3 FROM TABLE(sql_div(3))");
        assertAnswer(
            "Function TYPED successfully created.",
            "CREATE OR REPLACE FUNCTION typed() RETURNS TABLE (n NUMBER(5,2), s VARCHAR(3)) AS 'SELECT 1.257, "
                + "''abcdef'''");
        assertAnswer(
            "1.26",
            "SELECT n::VARCHAR FROM TABLE(typed())");
        assertAnswer(
            "Table CT successfully created.",
            "CREATE OR REPLACE TABLE ct AS SELECT * FROM TABLE(typed())");
        assertAnswer(
            "N|NUMBER(5,2)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "S|VARCHAR(3)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE ct");
        assertAnswer(
            "1.26|abcdef|6",
            "SELECT n, s, LENGTH(s) FROM ct");
        assertAnswer(
            "Function T_NUM successfully created.",
            "CREATE OR REPLACE FUNCTION t_num() RETURNS TABLE (n NUMBER(5,2)) AS 'SELECT 1.257'");
        assertAnswer(
            "1.26|2.52|true|false",
            "SELECT n::VARCHAR, n * 2, n = 1.26, n = 1.257 FROM TABLE(t_num())");
        assertAnswer(
            "Table CT2 successfully created.",
            "CREATE OR REPLACE TABLE ct2 AS SELECT * FROM TABLE(t_num())");
        assertAnswer(
            "N|NUMBER(5,2)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE ct2");
        assertAnswer(
            "1.26",
            "SELECT n::VARCHAR FROM ct2");
        assertAnswer(
            "View TV successfully created.",
            "CREATE OR REPLACE VIEW tv AS SELECT * FROM TABLE(t_num())");
        assertAnswer(
            "Function T_INT successfully created.",
            "CREATE OR REPLACE FUNCTION t_int() RETURNS TABLE (a INT) AS 'SELECT 1.7'");
        assertAnswer(
            "Function T_OVF successfully created.",
            "CREATE OR REPLACE FUNCTION t_ovf() RETURNS TABLE (n NUMBER(3,1)) AS 'SELECT 1234.5'");
        assertAnswer(
            "REFUSED Number out of representable range: type FIXED[SB2](3,1){nullable}, value 1234.5",
            "SELECT n FROM TABLE(t_ovf())");
        assertAnswer(
            "Function T_STR successfully created.",
            "CREATE OR REPLACE FUNCTION t_str() RETURNS TABLE (s VARCHAR(3)) AS 'SELECT ''abcdef'''");
        assertAnswer(
            "abcdef|6|VARCHAR(3)[LOB]",
            "SELECT s, LENGTH(s), SYSTEM$TYPEOF(s) FROM TABLE(t_str())");
        assertAnswer(
            "Function T_STR2 successfully created.",
            "CREATE OR REPLACE FUNCTION t_str2() RETURNS TABLE (s VARCHAR(3)) AS 'SELECT ''ab'''");
        assertAnswer(
            "ab|VARCHAR(3)[LOB]",
            "SELECT s, SYSTEM$TYPEOF(s) FROM TABLE(t_str2())");
    }

    @Test
    public void bodiesOfAnotherFamilyOrCount() {
        assertAnswer(
            "REFUSED Declared return type 'FLOAT' for column 'F' is incompatible with actual return type "
                + "'NUMBER(1,0)'",
            "CREATE OR REPLACE FUNCTION t_fl() RETURNS TABLE (f FLOAT) AS 'SELECT 1'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown table function T_FL",
            "SELECT f::VARCHAR, SYSTEM$TYPEOF(f), f / 3 = 1::FLOAT / 3 FROM TABLE(t_fl())");
        assertAnswer(
            "REFUSED Declared return type 'NUMBER(10,2)' for column 'N' is incompatible with actual return type "
                + "'FLOAT'",
            "CREATE OR REPLACE FUNCTION t_nf() RETURNS TABLE (n NUMBER(10,2)) AS 'SELECT 1.5::FLOAT'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown table function T_NF",
            "SELECT n::VARCHAR, SYSTEM$TYPEOF(n) FROM TABLE(t_nf())");
        assertAnswer(
            "REFUSED Declared return type 'DATE' for column 'D' is incompatible with actual return type "
                + "'VARCHAR(10)'",
            "CREATE OR REPLACE FUNCTION t_dt() RETURNS TABLE (d DATE) AS 'SELECT ''2024-01-02'''");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown table function T_DT",
            "SELECT d, SYSTEM$TYPEOF(d) FROM TABLE(t_dt())");
        assertAnswer(
            "REFUSED Declared return type 'DATE' for column 'D' is incompatible with actual return type "
                + "'TIMESTAMP_NTZ(9)'",
            "CREATE OR REPLACE FUNCTION t_dt2() RETURNS TABLE (d DATE) AS 'SELECT ''2024-01-02 "
                + "10:11:12''::TIMESTAMP_NTZ'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown table function T_DT2",
            "SELECT d, SYSTEM$TYPEOF(d) FROM TABLE(t_dt2())");
        assertAnswer(
            "REFUSED Declared return type 'VARCHAR(134217728)' for column 'S' is incompatible with actual return "
                + "type 'NUMBER(3,1)'",
            "CREATE OR REPLACE FUNCTION t_vc() RETURNS TABLE (s VARCHAR) AS 'SELECT 12.50'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown table function T_VC",
            "SELECT s, SYSTEM$TYPEOF(s) FROM TABLE(t_vc())");
        assertAnswer(
            "REFUSED Declared return type 'BOOLEAN' for column 'B' is incompatible with actual return type "
                + "'NUMBER(1,0)'",
            "CREATE OR REPLACE FUNCTION t_bo() RETURNS TABLE (b BOOLEAN) AS 'SELECT 1'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown table function T_BO",
            "SELECT b, SYSTEM$TYPEOF(b) FROM TABLE(t_bo())");
        assertAnswer(
            "REFUSED Declared return type 'TIMESTAMP_NTZ(3)' for column 'T' is incompatible with actual return "
                + "type 'TIMESTAMP_NTZ(9)'",
            "CREATE OR REPLACE FUNCTION t_ts() RETURNS TABLE (t TIMESTAMP_NTZ(3)) AS 'SELECT ''2024-01-02 "
                + "10:11:12.123456789''::TIMESTAMP_NTZ(9)'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown table function T_TS",
            "SELECT REPLACE(REPLACE(t::VARCHAR, '-', '_'), ':', ';'), SYSTEM$TYPEOF(t) FROM TABLE(t_ts())");
        assertAnswer(
            "REFUSED Declared return type 'TIMESTAMP_NTZ(9)' for column 'T' is incompatible with actual return "
                + "type 'DATE'",
            "CREATE OR REPLACE FUNCTION t_ts2() RETURNS TABLE (t TIMESTAMP_NTZ) AS 'SELECT ''2024-01-02''::DATE'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown table function T_TS2",
            "SELECT REPLACE(REPLACE(t::VARCHAR, '-', '_'), ':', ';'), SYSTEM$TYPEOF(t) FROM TABLE(t_ts2())");
        assertAnswer(
            "REFUSED Declared return type 'VARIANT' for column 'V' is incompatible with actual return type "
                + "'NUMBER(1,0)'",
            "CREATE OR REPLACE FUNCTION t_va() RETURNS TABLE (v VARIANT) AS 'SELECT 5'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown table function T_VA",
            "SELECT v, TYPEOF(v), SYSTEM$TYPEOF(v) FROM TABLE(t_va())");
        assertAnswer(
            "Function T_NU successfully created.",
            "CREATE OR REPLACE FUNCTION t_nu() RETURNS TABLE (n NUMBER(5,2), s VARCHAR(3)) AS 'SELECT NULL, NULL'");
        assertAnswer(
            "REFUSED Declared return type 'NUMBER(38,0)' for column 'N' is incompatible with actual return type "
                + "'VARCHAR(3)'",
            "CREATE OR REPLACE FUNCTION t_bad() RETURNS TABLE (n NUMBER) AS 'SELECT ''abc'''");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown table function T_BAD",
            "SELECT n FROM TABLE(t_bad())");
        assertAnswer(
            "REFUSED Declared return type 'DATE' for column 'D' is incompatible with actual return type 'NUMBER(1,"
                + "0)'",
            "CREATE OR REPLACE FUNCTION t_bad2() RETURNS TABLE (d DATE) AS 'SELECT 5'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown table function T_BAD2",
            "SELECT d FROM TABLE(t_bad2())");
        assertAnswer(
            "REFUSED Mismatch between declared return signature column count (2) and actual column count (1)",
            "CREATE OR REPLACE FUNCTION t_cnt() RETURNS TABLE (a INT, b INT) AS 'SELECT 1'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown table function T_CNT",
            "SELECT * FROM TABLE(t_cnt())");
        assertAnswer(
            "REFUSED Mismatch between declared return signature column count (1) and actual column count (2)",
            "CREATE OR REPLACE FUNCTION t_cnt2() RETURNS TABLE (a INT) AS 'SELECT 1, 2'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown table function T_CNT2",
            "SELECT * FROM TABLE(t_cnt2())");
    }

    @Test
    public void rowsOfATableLaterallyAndWide() {
        assertAnswer(
            "Table SRC successfully created.",
            "CREATE OR REPLACE TABLE src (x NUMBER(10,4), y VARCHAR(10))");
        assertAnswer(
            "2",
            "INSERT INTO src VALUES (1.2345, 'abcdefgh'), (2.5, 'xy')");
        assertAnswer(
            "Function T_SRC successfully created.",
            "CREATE OR REPLACE FUNCTION t_src() RETURNS TABLE (n NUMBER(5,2), s VARCHAR(3)) AS 'SELECT x, y FROM "
                + "src'");
        assertAnswer(
            "1.23|abcdefgh ;; 2.50|xy",
            "SELECT n, s FROM TABLE(t_src()) ORDER BY 1");
        assertAnswer(
            "Function T_SRC2 successfully created.",
            "CREATE OR REPLACE FUNCTION t_src2() RETURNS TABLE (n NUMBER(5,2), s VARCHAR(10)) AS 'SELECT x, y "
                + "FROM src'");
        assertAnswer(
            "3.73",
            "SELECT SUM(n)::VARCHAR FROM TABLE(t_src2())");
        assertAnswer(
            "Function T_SRC3 successfully created.",
            "CREATE OR REPLACE FUNCTION t_src3() RETURNS TABLE (n NUMBER(5,2), s VARCHAR(3)) AS 'SELECT x, y FROM "
                + "src WHERE y = ''xy'''");
        assertAnswer(
            "2.50|xy",
            "SELECT n::VARCHAR, s FROM TABLE(t_src3())");
        assertAnswer(
            "Function T_LAT successfully created.",
            "CREATE OR REPLACE FUNCTION t_lat(k NUMBER(10,4)) RETURNS TABLE (a INT) AS 'SELECT 10 / k'");
        assertAnswer(
            "1.2345|8 ;; 2.5000|4",
            "SELECT x, a FROM src, TABLE(t_lat(x)) ORDER BY 1");
        assertAnswer(
            "Function T_SAME successfully created.",
            "CREATE OR REPLACE FUNCTION t_same() RETURNS TABLE (n NUMBER(10,4), s VARCHAR(10)) AS 'SELECT x, y "
                + "FROM src'");
        assertAnswer(
            "Function T_WIDE successfully created.",
            "CREATE OR REPLACE FUNCTION t_wide() RETURNS TABLE (n NUMBER(38,10)) AS 'SELECT 1.5'");
        assertAnswer(
            "Function T_EXPR successfully created.",
            "CREATE OR REPLACE FUNCTION t_expr() RETURNS TABLE (n NUMBER(5,2)) AS 'SELECT 1.004 UNION ALL SELECT "
                + "1.005 UNION ALL SELECT -1.005'");
        assertAnswer(
            "-1.01 ;; 1.00 ;; 1.01",
            "SELECT n::VARCHAR FROM TABLE(t_expr()) ORDER BY 1");
    }

    @Test
    public void roundingRangeAndWhereTheFaultIsRead() {
        assertAnswer(
            "Function T_CAR successfully created.",
            "CREATE OR REPLACE FUNCTION t_car() RETURNS TABLE (n NUMBER(3,1)) AS 'SELECT 99.95'");
        assertAnswer(
            "REFUSED Number out of representable range: type FIXED[SB2](3,1){nullable}, value 99.95",
            "SELECT n::VARCHAR FROM TABLE(t_car())");
        assertAnswer(
            "Function T_CAR2 successfully created.",
            "CREATE OR REPLACE FUNCTION t_car2() RETURNS TABLE (n NUMBER(3,1)) AS 'SELECT 99.94'");
        assertAnswer(
            "99.9",
            "SELECT n::VARCHAR FROM TABLE(t_car2())");
        assertAnswer(
            "Function T_BIG successfully created.",
            "CREATE OR REPLACE FUNCTION t_big() RETURNS TABLE (n NUMBER(4,0)) AS 'SELECT 123456'");
        assertAnswer(
            "REFUSED Number out of representable range: type FIXED[SB4](4,0){nullable}, value 123456",
            "SELECT n FROM TABLE(t_big())");
        assertAnswer(
            "Function T_NEG successfully created.",
            "CREATE OR REPLACE FUNCTION t_neg() RETURNS TABLE (n NUMBER(3,1)) AS 'SELECT -1234.5'");
        assertAnswer(
            "REFUSED Number out of representable range: type FIXED[SB2](3,1){nullable}, value -1234.5",
            "SELECT n FROM TABLE(t_neg())");
        assertAnswer(
            "Function T_TWO successfully created.",
            "CREATE OR REPLACE FUNCTION t_two() RETURNS TABLE (a NUMBER(3,1), b NUMBER(3,1)) AS 'SELECT 1.26, "
                + "1234.5'");
        assertAnswer(
            "1.3",
            "SELECT a FROM TABLE(t_two())");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM TABLE(t_two())");
        assertAnswer(
            "",
            "SELECT 1 FROM TABLE(t_two()) WHERE 1 = 0");
        assertAnswer(
            "Function T_INT2 successfully created.",
            "CREATE OR REPLACE FUNCTION t_int2() RETURNS TABLE (a INT) AS 'SELECT 2.5 UNION ALL SELECT 3.5 UNION "
                + "ALL SELECT -2.5'");
        assertAnswer(
            "-3 ;; 3 ;; 4",
            "SELECT a FROM TABLE(t_int2()) ORDER BY 1");
        assertAnswer(
            "Function T_SC successfully created.",
            "CREATE OR REPLACE FUNCTION t_sc() RETURNS TABLE (n NUMBER(10,4)) AS 'SELECT 1'");
        assertAnswer(
            "1.0000",
            "SELECT n::VARCHAR FROM TABLE(t_sc())");
        assertAnswer(
            "Function T_ARG successfully created.",
            "CREATE OR REPLACE FUNCTION t_arg(k NUMBER(10,2)) RETURNS TABLE (n NUMBER(5,0)) AS 'SELECT k'");
        assertAnswer(
            "2",
            "SELECT n FROM TABLE(t_arg(1.50::NUMBER(10,2)))");
        assertAnswer(
            "Table INS successfully created.",
            "CREATE OR REPLACE TABLE ins (n NUMBER(3,1))");
        assertAnswer(
            "Function T_INS successfully created.",
            "CREATE OR REPLACE FUNCTION t_ins() RETURNS TABLE (n NUMBER(3,1)) AS 'SELECT 1.26'");
        assertAnswer(
            "1",
            "INSERT INTO ins SELECT * FROM TABLE(t_ins())");
        assertAnswer(
            "1.3",
            "SELECT n::VARCHAR FROM ins");
        assertAnswer(
            "Function T_UN successfully created.",
            "CREATE OR REPLACE FUNCTION t_un() RETURNS TABLE (n NUMBER(3,1)) AS 'SELECT 1.26'");
        assertAnswer(
            "1.300 ;; 2.345",
            "SELECT n::VARCHAR FROM (SELECT n FROM TABLE(t_un()) UNION ALL SELECT 2.345) ORDER BY 1");
        assertAnswer(
            "1.3|1.3",
            "SELECT MAX(n)::VARCHAR, MIN(n)::VARCHAR FROM TABLE(t_un())");
    }
}
