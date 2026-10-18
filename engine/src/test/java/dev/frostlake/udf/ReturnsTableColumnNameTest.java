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
 * A RETURNS TABLE column is written as a routine's argument is (live-verified). Its name takes the words an argument's
 * does — CONSTRAINT, DEFAULT, CASE, CAST and the join words among them — and a reserved word is refused: the word,
 * then the list's ')'. It takes a DEFAULT, though not :=, a column without one may not follow a column with one,
 * {@code required argument B cannot follow optional argument A}, and no column option of a table's is taken: NULL,
 * UNIQUE and AUTOINCREMENT are refused where they stand. A column with no type is {@code Return signature missing data
 * type for column 'B'.}
 */
public class ReturnsTableColumnNameTest extends MeasuredCellsTestSupport {

    @Test
    public void aColumnIsWrittenAsAnArgumentIs() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION rtk0(x INT) RETURNS TABLE (constraint INT) AS $$ SELECT x $$",
                "Function RTK0 successfully created."},
            {"SELECT * FROM TABLE(rtk0(0))", "0"},
            {"CREATE OR REPLACE FUNCTION rtk1(x INT) RETURNS TABLE (default INT) AS $$ SELECT x $$",
                "Function RTK1 successfully created."},
            {"SELECT * FROM TABLE(rtk1(1))", "1"},
            {"CREATE OR REPLACE FUNCTION rtk2(x INT) RETURNS TABLE (case INT) AS $$ SELECT x $$",
                "Function RTK2 successfully created."},
            {"SELECT * FROM TABLE(rtk2(2))", "2"},
            {"CREATE OR REPLACE FUNCTION rtk3(x INT) RETURNS TABLE (when INT) AS $$ SELECT x $$",
                "Function RTK3 successfully created."},
            {"SELECT * FROM TABLE(rtk3(3))", "3"},
            {"CREATE OR REPLACE FUNCTION rtk4(x INT) RETURNS TABLE (cast INT) AS $$ SELECT x $$",
                "Function RTK4 successfully created."},
            {"SELECT * FROM TABLE(rtk4(4))", "4"},
            {"CREATE OR REPLACE FUNCTION rtk5(x INT) RETURNS TABLE (join INT) AS $$ SELECT x $$",
                "Function RTK5 successfully created."},
            {"SELECT * FROM TABLE(rtk5(5))", "5"},
            {"CREATE OR REPLACE FUNCTION rtk6(x INT) RETURNS TABLE (inner INT) AS $$ SELECT x $$",
                "Function RTK6 successfully created."},
            {"SELECT * FROM TABLE(rtk6(6))", "6"},
            {"CREATE OR REPLACE FUNCTION rtk7(x INT) RETURNS TABLE (cross INT) AS $$ SELECT x $$",
                "Function RTK7 successfully created."},
            {"SELECT * FROM TABLE(rtk7(7))", "7"},
            {"CREATE OR REPLACE FUNCTION rtk8(x INT) RETURNS TABLE (order INT) AS $$ SELECT x $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected 'order'. | syntax "
                + "error line 1 at position 63 unexpected ')'."},
            {"SELECT * FROM TABLE(rtk8(8))", "REFUSED SQL compilation error: | Unknown table function RTK8"},
            {"CREATE OR REPLACE FUNCTION rtk9(x INT) RETURNS TABLE (trigger INT) AS $$ SELECT x $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected 'trigger'. | "
                + "syntax error line 1 at position 65 unexpected ')'."},
            {"SELECT * FROM TABLE(rtk9(9))", "REFUSED SQL compilation error: | Unknown table function RTK9"},
            {"CREATE OR REPLACE FUNCTION rtk10(x INT) RETURNS TABLE (on INT) AS $$ SELECT x $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'on'. | syntax "
                + "error line 1 at position 61 unexpected ')'."},
            {"SELECT * FROM TABLE(rtk10(10))", "REFUSED SQL compilation error: | Unknown table function RTK10"},
            {"CREATE OR REPLACE FUNCTION rtk11(x INT) RETURNS TABLE (qualify INT) AS $$ SELECT x $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'qualify'. | "
                + "syntax error line 1 at position 66 unexpected ')'."},
            {"SELECT * FROM TABLE(rtk11(11))", "REFUSED SQL compilation error: | Unknown table function RTK11"},
            {"CREATE OR REPLACE FUNCTION rtk12(x INT) RETURNS TABLE (select INT) AS $$ SELECT x $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'select'. | syntax "
                + "error line 1 at position 65 unexpected ')'."},
            {"SELECT * FROM TABLE(rtk12(12))", "REFUSED SQL compilation error: | Unknown table function RTK12"},
            {"CREATE OR REPLACE FUNCTION rtk13(x INT) RETURNS TABLE (primary INT) AS $$ SELECT x $$",
                "Function RTK13 successfully created."},
            {"SELECT * FROM TABLE(rtk13(13))", "13"},
            {"CREATE OR REPLACE FUNCTION rtk14(x INT) RETURNS TABLE (unique INT) AS $$ SELECT x $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'unique'. | syntax "
                + "error line 1 at position 65 unexpected ')'."},
            {"SELECT * FROM TABLE(rtk14(14))", "REFUSED SQL compilation error: | Unknown table function RTK14"},
            {"CREATE OR REPLACE FUNCTION rtk15(x INT) RETURNS TABLE (foreign INT) AS $$ SELECT x $$",
                "Function RTK15 successfully created."},
            {"SELECT * FROM TABLE(rtk15(15))", "15"},
            {"CREATE OR REPLACE FUNCTION rtk16(x INT) RETURNS TABLE (check INT) AS $$ SELECT x $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'check'. | syntax "
                + "error line 1 at position 64 unexpected ')'."},
            {"SELECT * FROM TABLE(rtk16(16))", "REFUSED SQL compilation error: | Unknown table function RTK16"},
            {"CREATE OR REPLACE FUNCTION rtk17(x INT) RETURNS TABLE (comment INT) AS $$ SELECT x $$",
                "Function RTK17 successfully created."},
            {"SELECT * FROM TABLE(rtk17(17))", "17"},
            {"CREATE OR REPLACE FUNCTION rtk18(x INT) RETURNS TABLE (collate INT) AS $$ SELECT x $$",
                "Function RTK18 successfully created."},
            {"SELECT * FROM TABLE(rtk18(18))", "18"},
            {"CREATE OR REPLACE FUNCTION rtk19(x INT) RETURNS TABLE (identity INT) AS $$ SELECT x $$",
                "Function RTK19 successfully created."},
            {"SELECT * FROM TABLE(rtk19(19))", "19"},
            {"CREATE OR REPLACE FUNCTION rtk20(x INT) RETURNS TABLE (autoincrement INT) AS $$ SELECT x $$",
                "Function RTK20 successfully created."},
            {"SELECT * FROM TABLE(rtk20(20))", "20"},
            {"CREATE OR REPLACE FUNCTION rtk21(x INT) RETURNS TABLE (not INT) AS $$ SELECT x $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'not'. | syntax "
                + "error line 1 at position 59 unexpected 'INT'. | syntax error line 1 at position 62 unexpected ')'."},
            {"SELECT * FROM TABLE(rtk21(21))", "REFUSED SQL compilation error: | Unknown table function RTK21"},
            {"CREATE OR REPLACE FUNCTION rtk22(x INT) RETURNS TABLE (null INT) AS $$ SELECT x $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'null'. | syntax "
                + "error line 1 at position 63 unexpected ')'."},
            {"SELECT * FROM TABLE(rtk22(22))", "REFUSED SQL compilation error: | Unknown table function RTK22"},
            {"CREATE OR REPLACE FUNCTION rtk23(x INT) RETURNS TABLE (table INT) AS $$ SELECT x $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'INT'. | syntax "
                + "error line 1 at position 66 unexpected 'AS'."},
            {"SELECT * FROM TABLE(rtk23(23))", "REFUSED SQL compilation error: | Unknown table function RTK23"},
            {"CREATE OR REPLACE FUNCTION rtk24(x INT) RETURNS TABLE (values INT) AS $$ SELECT x $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'values'. | syntax "
                + "error line 1 at position 65 unexpected ')'."},
            {"SELECT * FROM TABLE(rtk24(24))", "REFUSED SQL compilation error: | Unknown table function RTK24"},
            {"CREATE OR REPLACE FUNCTION rtk25(x INT) RETURNS TABLE (by INT) AS $$ SELECT x $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'by'. | syntax "
                + "error line 1 at position 61 unexpected ')'."},
            {"SELECT * FROM TABLE(rtk25(25))", "REFUSED SQL compilation error: | Unknown table function RTK25"},
            {"CREATE OR REPLACE FUNCTION rtk26(x INT) RETURNS TABLE (start INT) AS $$ SELECT x $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'start'. | syntax "
                + "error line 1 at position 64 unexpected ')'."},
            {"SELECT * FROM TABLE(rtk26(26))", "REFUSED SQL compilation error: | Unknown table function RTK26"},
            {"CREATE OR REPLACE FUNCTION rtk27(x INT) RETURNS TABLE (with INT) AS $$ SELECT x $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'with'. | syntax "
                + "error line 1 at position 63 unexpected ')'."},
            {"SELECT * FROM TABLE(rtk27(27))", "REFUSED SQL compilation error: | Unknown table function RTK27"},
            {"CREATE OR REPLACE FUNCTION rtk28(x INT) RETURNS TABLE (key INT) AS $$ SELECT x $$",
                "Function RTK28 successfully created."},
            {"SELECT * FROM TABLE(rtk28(28))", "28"},
            {"CREATE OR REPLACE FUNCTION rtk29(x INT) RETURNS TABLE (references INT) AS $$ SELECT x $$",
                "Function RTK29 successfully created."},
            {"SELECT * FROM TABLE(rtk29(29))", "29"},
            {"CREATE OR REPLACE FUNCTION rt1(x INT) RETURNS TABLE (constraint INT, default INT) AS $$ SELECT x, "
                + "x + 1 $$",
                "Function RT1 successfully created."},
            {"SELECT * FROM TABLE(rt1(1))", "1|2"},
            {"SELECT \"CONSTRAINT\", \"DEFAULT\" FROM TABLE(rt1(5))", "5|6"},
            {"DESCRIBE FUNCTION rt1(INT)",
                "signature|(X NUMBER) ;; returns|TABLE (CONSTRAINT NUMBER, DEFAULT NUMBER) ;; language|SQL ;; "
                + "body| SELECT x, x + 1 "},
            {"CREATE OR REPLACE FUNCTION rto1(x INT) RETURNS TABLE (a INT NULL) AS $$ SELECT x $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'NULL'."},
            {"CREATE OR REPLACE FUNCTION rto2(x INT) RETURNS TABLE (a INT DEFAULT 1) AS $$ SELECT x $$",
                "Function RTO2 successfully created."},
            {"CREATE OR REPLACE FUNCTION rto7(x INT) RETURNS TABLE (a INT AUTOINCREMENT) AS $$ SELECT x $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'AUTOINCREMENT'."},
            {"CREATE OR REPLACE FUNCTION rto8(x INT) RETURNS TABLE (a INT UNIQUE) AS $$ SELECT x $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'UNIQUE'."},
            {"CREATE OR REPLACE FUNCTION rto10(x INT) RETURNS TABLE (a INT, b) AS $$ SELECT x $$",
                "REFUSED Return signature missing data type for column 'B'."},
            {"CREATE OR REPLACE FUNCTION rto11(x INT) RETURNS TABLE (a) AS $$ SELECT x $$",
                "REFUSED Return signature missing data type for column 'A'."},
            {"CREATE OR REPLACE FUNCTION rto12(x INT) RETURNS TABLE (a INT,) AS $$ SELECT x $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION rto13(x INT) RETURNS TABLE () AS $$ SELECT x $$",
                "REFUSED Mismatch between declared return signature column count (0) and actual column count (1)"},
            {"CREATE OR REPLACE FUNCTION rto14(x INT) RETURNS TABLE (\"case\" INT) AS $$ SELECT x $$",
                "Function RTO14 successfully created."},
            {"CREATE OR REPLACE PROCEDURE rtp() RETURNS TABLE (constraint INT, case INT) LANGUAGE SQL AS $$ "
                + "DECLARE r RESULTSET DEFAULT (SELECT 1 AS c1, 2 AS c2); BEGIN RETURN TABLE(r); END; $$",
                "Function RTP successfully created."},
            {"CALL rtp()", "1|2"},
        });
    }

    @Test
    public void leftADefaultAndAProcedure() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION rl1(x INT) RETURNS TABLE (left INT) AS $$ SELECT x $$",
                "Function RL1 successfully created."},
            {"SELECT * FROM TABLE(rl1(4))", "4"},
            {"CREATE OR REPLACE FUNCTION rl2(left INT) RETURNS INT AS $$ left + 1 $$",
                "Function RL2 successfully created."},
            {"SELECT rl2(1)", "2"},
            {"CREATE OR REPLACE FUNCTION rl3(x INT) RETURNS TABLE (a INT := 1) AS $$ SELECT x $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected ':='."},
            {"CREATE OR REPLACE FUNCTION rl4(x INT) RETURNS TABLE (a INT DEFAULT 1, b VARCHAR) AS $$ SELECT x, 'b' $$",
                "REFUSED SQL compilation error: error line 1 at position 70 | required argument B cannot follow "
                + "optional argument A"},
            {"SELECT * FROM TABLE(rl4(3))", "REFUSED SQL compilation error: | Unknown table function RL4"},
            {"DESCRIBE FUNCTION rl4(INT)",
                "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.RL4' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.RL4."},
            {"CREATE OR REPLACE PROCEDURE rlp() RETURNS TABLE (a) LANGUAGE SQL AS $$ BEGIN RETURN TABLE(SELECT "
                + "1); END; $$",
                "REFUSED Return signature missing data type for column 'A'."},
            {"CREATE OR REPLACE FUNCTION rl5(x INT) RETURNS TABLE (a INT, a INT) AS $$ SELECT x, x $$",
                "REFUSED Return signature contains a duplicate column name 'A'."},
            {"CREATE OR REPLACE FUNCTION rl6(x INT) RETURNS TABLE (a INT, order INT) AS $$ SELECT x, x $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'order'."},
            {"CREATE OR REPLACE FUNCTION rl7(x INT) RETURNS TABLE (a INT, b) AS $$ SELECT x $$",
                "REFUSED Return signature missing data type for column 'B'."},
            {"CREATE OR REPLACE FUNCTION rl8(x INT) RETURNS TABLE (b, a INT) AS $$ SELECT x $$",
                "REFUSED Return signature missing data type for column 'B'."},
        });
    }
}
