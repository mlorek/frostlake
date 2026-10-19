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
package dev.frostlake.ddl;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * Which user-defined functions a DEFAULT may call. A column's DEFAULT may call a SQL function whose body is an
 * expression - the account expands the body into the default, so the table is created and an INSERT fills the
 * column from the function, nested SQL functions included. A JavaScript, a Python or a Java function, and a SQL
 * function whose body reaches one, is {@code Invalid column default expression [JSF()]}, the default echoed as
 * the plan prints it with each function under its bare name; a SQL function whose body aggregates is refused
 * while it is expanded; a call with too many arguments where it stands; and a function's FLOAT in an exact
 * NUMBER column for its type first. ALTER TABLE ADD COLUMN, which takes a literal alone, echoes a call the same
 * way and refuses a name that is no function as one. A routine parameter's DEFAULT calls no user-defined
 * function at all - {@code invalid default argument expression F1()}, placed where the expression stands -
 * and reads no name, while a built-in call and arithmetic are taken. Every answer is live-verified.
 */
public class DefaultCallsUserFunctionTest extends MeasuredCellsTestSupport {

    @Test
    public void aColumnDefaultCallsASqlFunction() {
        assertAnswer(
            "Function F1 successfully created.",
            "CREATE OR REPLACE FUNCTION f1() RETURNS NUMBER AS '42'");
        assertAnswer(
            "Table TD successfully created.",
            "CREATE OR REPLACE TABLE td (a NUMBER DEFAULT f1(), b NUMBER)");
        assertAnswer(
            "1",
            "INSERT INTO td (b) VALUES (1)");
        assertAnswer(
            "42|1",
            "SELECT * FROM td");
        assertAnswer(
            "Table TD2 successfully created.",
            "CREATE OR REPLACE TABLE td2 (a NUMBER DEFAULT test_schema.f1(), b NUMBER)");
        assertAnswer(
            "Table TD3 successfully created.",
            "CREATE OR REPLACE TABLE td3 (a NUMBER DEFAULT test_db.test_schema.f1(), b NUMBER)");
        assertAnswer(
            "Table TD4 successfully created.",
            "CREATE OR REPLACE TABLE td4 (a NUMBER DEFAULT f1() + 1, b NUMBER)");
        assertAnswer(
            "Table TD5 successfully created.",
            "CREATE OR REPLACE TABLE td5 (a NUMBER DEFAULT ABS(f1()), b NUMBER)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NOSUCH_FN_ZZ.",
            "CREATE OR REPLACE TABLE td6 (a NUMBER DEFAULT nosuch_fn_zz(), b NUMBER)");
        assertAnswer(
            "Table TD7 successfully created.",
            "CREATE OR REPLACE TABLE td7 (a NUMBER DEFAULT ABS(-1), b NUMBER)");
        assertAnswer(
            "Table TD8 successfully created.",
            "CREATE OR REPLACE TABLE td8 (b NUMBER)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid column default expression [F1()]",
            "ALTER TABLE td8 ADD COLUMN a NUMBER DEFAULT f1()");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid column default expression [ABS(-2)]",
            "ALTER TABLE td8 ADD COLUMN c NUMBER DEFAULT ABS(-2)");
        assertAnswer(
            "Function F2 successfully created.",
            "CREATE OR REPLACE FUNCTION f2(x NUMBER) RETURNS NUMBER AS 'x + 1'");
        assertAnswer(
            "Table TD9 successfully created.",
            "CREATE OR REPLACE TABLE td9 (a NUMBER DEFAULT f2(1), b NUMBER)");
        assertAnswer(
            "Table TD10 successfully created.",
            "CREATE OR REPLACE TABLE td10 (b NUMBER, a NUMBER DEFAULT f2(b))");
        assertAnswer(
            "Table TD11 successfully created.",
            "CREATE OR REPLACE TEMPORARY TABLE td11 (a NUMBER DEFAULT f1(), b NUMBER)");
        assertAnswer(
            "Table TD12 successfully created.",
            "CREATE OR REPLACE TRANSIENT TABLE td12 (a NUMBER DEFAULT f1(), b NUMBER)");
        assertAnswer(
            "Function F3 successfully created.",
            "CREATE OR REPLACE FUNCTION f3() RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "Table TD13 successfully created.",
            "CREATE OR REPLACE TABLE td13 (a VARCHAR DEFAULT f3(), b NUMBER)");
        assertAnswer(
            "Sequence SQ successfully created.",
            "CREATE OR REPLACE SEQUENCE sq");
        assertAnswer(
            "Table TD14 successfully created.",
            "CREATE OR REPLACE TABLE td14 (a NUMBER DEFAULT sq.NEXTVAL, b NUMBER)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 48 | sub-queries are not supported as part "
                + "of the specification of a default value clause.",
            "CREATE OR REPLACE TABLE td15 (a NUMBER DEFAULT (SELECT 1), b NUMBER)");
        assertAnswer(
            "Table TD17 successfully created.",
            "CREATE OR REPLACE TABLE td17 (a NUMBER, b NUMBER)");
        assertAnswer(
            "REFUSED Unsupported feature 'Alter Column Set Default'.",
            "ALTER TABLE td17 ALTER COLUMN a SET DEFAULT f1()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 48 | invalid default argument expression F1()",
            "CREATE OR REPLACE PROCEDURE pd(x NUMBER DEFAULT f1()) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN "
                + "x; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function PD.",
            "CALL pd()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 48 | invalid default argument expression F1()",
            "CREATE OR REPLACE FUNCTION fdf(x NUMBER DEFAULT f1()) RETURNS NUMBER AS 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FDF.",
            "SELECT fdf()");
        assertAnswer(
            "REFUSED SQL compilation error: | Default value data type does not match data type for column A",
            "CREATE OR REPLACE TABLE td20 (a NUMBER DEFAULT UPPER(1), b NUMBER)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function \"f1\".",
            "CREATE OR REPLACE TABLE td21 (a NUMBER DEFAULT \"f1\"(), b NUMBER)");
        assertAnswer(
            "Table TD22 successfully created.",
            "CREATE OR REPLACE TABLE td22 (a NUMBER DEFAULT F1(), b NUMBER DEFAULT f1())");
        assertAnswer(
            "Table TD23 successfully created.",
            "CREATE OR REPLACE TABLE td23 (a NUMBER DEFAULT CASE WHEN f1() > 0 THEN 1 ELSE 0 END, b NUMBER)");
        assertAnswer(
            "Function JSF successfully created.",
            "CREATE OR REPLACE FUNCTION jsf() RETURNS FLOAT LANGUAGE JAVASCRIPT AS 'return 7;'");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid column default expression [JSF()]",
            "CREATE OR REPLACE TABLE td24 (a FLOAT DEFAULT jsf(), b NUMBER)");
        assertAnswer(
            "Table TD25 successfully created.",
            "CREATE OR REPLACE TABLE td25 LIKE td17");
        assertAnswer(
            "REFUSED SQL compilation error: | Default value data type does not match data type for column A",
            "CREATE OR REPLACE TABLE td26 (a NUMBER DEFAULT SYSTEM$TYPEOF(1), b NUMBER)");
    }

    @Test
    public void whatAColumnDefaultCannotExpand() {
        assertAnswer(
            "Function F1 successfully created.",
            "CREATE OR REPLACE FUNCTION f1() RETURNS NUMBER AS '42'");
        assertAnswer(
            "Function JSF successfully created.",
            "CREATE OR REPLACE FUNCTION jsf() RETURNS FLOAT LANGUAGE JAVASCRIPT AS 'return 7;'");
        assertAnswer(
            "Function PYF successfully created.",
            "CREATE OR REPLACE FUNCTION pyf() RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = '3.11' HANDLER = "
                + "'f' AS 'def f():\n    return 7'");
        assertAnswer(
            "Function JVF successfully created.",
            "CREATE OR REPLACE FUNCTION jvf() RETURNS NUMBER LANGUAGE JAVA HANDLER = 'C.f' AS 'class C { public "
                + "static int f() { return 7; } }'");
        assertAnswer(
            "Table SRC successfully created.",
            "CREATE OR REPLACE TABLE src (x NUMBER)");
        assertAnswer(
            "1",
            "INSERT INTO src VALUES (5)");
        assertAnswer(
            "Function FQ successfully created.",
            "CREATE OR REPLACE FUNCTION fq() RETURNS NUMBER AS 'SELECT MAX(x) FROM src'");
        assertAnswer(
            "Function FSUB successfully created.",
            "CREATE OR REPLACE FUNCTION fsub() RETURNS NUMBER AS '(SELECT MAX(x) FROM src)'");
        assertAnswer(
            "Function FJS successfully created.",
            "CREATE OR REPLACE FUNCTION fjs() RETURNS FLOAT AS 'jsf() + 1'");
        assertAnswer(
            "Function FNEST successfully created.",
            "CREATE OR REPLACE FUNCTION fnest() RETURNS NUMBER AS 'f1() + 1'");
        assertAnswer(
            "Function FBLK successfully created.",
            "CREATE OR REPLACE FUNCTION fblk() RETURNS NUMBER AS $$ BEGIN RETURN 3; END $$");
        assertAnswer(
            "Function FMEMO successfully created.",
            "CREATE OR REPLACE FUNCTION fmemo() RETURNS NUMBER MEMOIZABLE AS '42'");
        assertAnswer(
            "Function FTS successfully created.",
            "CREATE OR REPLACE FUNCTION fts() RETURNS TIMESTAMP_LTZ AS 'CURRENT_TIMESTAMP()'");
        assertAnswer(
            "Function FSEQ successfully created.",
            "CREATE OR REPLACE FUNCTION fseq() RETURNS NUMBER AS 'UNIFORM(1, 1, RANDOM())'");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid column default expression [JSF()]",
            "CREATE OR REPLACE TABLE d1 (a FLOAT DEFAULT jsf(), b NUMBER)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid column default expression [ABS(JSF())]",
            "CREATE OR REPLACE TABLE d3 (a FLOAT DEFAULT ABS(jsf()), b NUMBER)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid column default expression [JSF()]",
            "CREATE OR REPLACE TABLE d4 (a FLOAT DEFAULT test_schema.jsf(), b NUMBER)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid column default expression [JSF()]",
            "CREATE OR REPLACE TABLE d5 (a FLOAT DEFAULT test_db.test_schema.jsf(), b NUMBER)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid column default expression [PYF()]",
            "CREATE OR REPLACE TABLE d6 (a NUMBER DEFAULT pyf(), b NUMBER)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid column default expression [JVF()]",
            "CREATE OR REPLACE TABLE d7 (a NUMBER DEFAULT jvf(), b NUMBER)");
        assertAnswer(
            "REFUSED SQL compilation error: | error in line 1 position 45: While expanding UDF in default "
                + "expression: SQL compilation error: error line 1 at position 8 | aggregate functions are not "
                + "allowed as part of the specification of a default value clause.",
            "CREATE OR REPLACE TABLE d8 (a NUMBER DEFAULT fq(), b NUMBER)");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'D8' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE D8.",
            "INSERT INTO d8 (b) VALUES (1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'D8' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE D8.",
            "SELECT * FROM d8");
        assertAnswer(
            "REFUSED SQL compilation error: | error in line 1 position 45: While expanding UDF in default "
                + "expression: SQL compilation error: error line 1 at position 9 | aggregate functions are not "
                + "allowed as part of the specification of a default value clause.",
            "CREATE OR REPLACE TABLE d9 (a NUMBER DEFAULT fsub(), b NUMBER)");
        assertAnswer(
            "Table D11 successfully created.",
            "CREATE OR REPLACE TABLE d11 (a NUMBER DEFAULT fnest(), b NUMBER)");
        assertAnswer(
            "1",
            "INSERT INTO d11 (b) VALUES (1)");
        assertAnswer(
            "43|1",
            "SELECT * FROM d11");
        assertAnswer(
            "Table D13 successfully created.",
            "CREATE OR REPLACE TABLE d13 (a NUMBER DEFAULT fmemo(), b NUMBER)");
        assertAnswer(
            "Table D14 successfully created.",
            "CREATE OR REPLACE TABLE d14 (a TIMESTAMP_LTZ DEFAULT fts(), b NUMBER)");
        assertAnswer(
            "Table D15 successfully created.",
            "CREATE OR REPLACE TABLE d15 (a NUMBER DEFAULT fseq(), b NUMBER)");
        assertAnswer(
            "REFUSED SQL compilation error: | Default value data type does not match data type for column A",
            "CREATE OR REPLACE TABLE d16 (a NUMBER DEFAULT f1() + jsf(), b NUMBER)");
        assertAnswer(
            "REFUSED SQL compilation error: | Default value data type does not match data type for column A",
            "CREATE OR REPLACE TABLE d18 (a NUMBER DEFAULT IFF(TRUE, 1, jsf()), b NUMBER)");
        assertAnswer(
            "Table D19 successfully created.",
            "CREATE OR REPLACE TABLE d19 (b NUMBER)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid column default expression [JSF()]",
            "ALTER TABLE d19 ADD COLUMN a FLOAT DEFAULT jsf()");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid column default expression [F1() + 1]",
            "ALTER TABLE d19 ADD COLUMN c NUMBER DEFAULT f1() + 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid column default expression [F1()]",
            "ALTER TABLE d19 ADD COLUMN e NUMBER DEFAULT test_schema.f1()");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE d19 ADD COLUMN g NUMBER DEFAULT 7");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid column default expression [1 + 1]",
            "ALTER TABLE d19 ADD COLUMN h NUMBER DEFAULT 1 + 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid column default expression [UPPER('a')]",
            "ALTER TABLE d19 ADD COLUMN i VARCHAR DEFAULT UPPER('a')");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid column default expression [CURRENT_TIMESTAMP()]",
            "ALTER TABLE d19 ADD COLUMN j TIMESTAMP_LTZ DEFAULT CURRENT_TIMESTAMP()");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function \"f1\".",
            "ALTER TABLE d19 ADD COLUMN k NUMBER DEFAULT \"f1\"()");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NOSUCH_FN_ZZ.",
            "ALTER TABLE d19 ADD COLUMN l NUMBER DEFAULT nosuch_fn_zz()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 46 | too many arguments for function [F1(1)] "
                + "expected 0, got 1",
            "CREATE OR REPLACE TABLE d20 (a NUMBER DEFAULT f1(1), b NUMBER)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 46 | too many arguments for function "
                + "[JSF(1)] expected 0, got 1",
            "CREATE OR REPLACE TABLE d21 (a NUMBER DEFAULT jsf(1), b NUMBER)");
        assertAnswer(
            "REFUSED SQL compilation error: | Default value data type does not match data type for column B",
            "CREATE OR REPLACE TABLE d22 (a NUMBER DEFAULT f1(), b NUMBER DEFAULT jsf())");
        assertAnswer(
            "Table D23 successfully created.",
            "CREATE OR REPLACE TABLE d23 AS SELECT 1 AS b");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid column default expression [JSF()]",
            "CREATE OR REPLACE TABLE d24 (a FLOAT DEFAULT jsf()) AS SELECT 1::FLOAT AS a");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid column default expression [JSF()]",
            "CREATE OR REPLACE TEMPORARY TABLE d25 (a FLOAT DEFAULT jsf(), b NUMBER)");
    }

    @Test
    public void aRoutineParametersDefault() {
        assertAnswer(
            "Function F1 successfully created.",
            "CREATE OR REPLACE FUNCTION f1() RETURNS NUMBER AS '42'");
        assertAnswer(
            "Function JSF successfully created.",
            "CREATE OR REPLACE FUNCTION jsf() RETURNS FLOAT LANGUAGE JAVASCRIPT AS 'return 7;'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 48 | invalid default argument expression F1()",
            "CREATE OR REPLACE PROCEDURE p1(x NUMBER DEFAULT f1()) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
                + "RETURN x::VARCHAR; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function P1.",
            "CALL p1()");
        assertAnswer(
            "Function P2 successfully created.",
            "CREATE OR REPLACE PROCEDURE p2(x NUMBER DEFAULT ABS(-1)) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
                + "RETURN x::VARCHAR; END; $$");
        assertAnswer(
            "1",
            "CALL p2()");
        assertAnswer(
            "Function P3 successfully created.",
            "CREATE OR REPLACE PROCEDURE p3(x NUMBER DEFAULT 1 + 1) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
                + "RETURN x::VARCHAR; END; $$");
        assertAnswer(
            "2",
            "CALL p3()");
        assertAnswer(
            "Function P4 successfully created.",
            "CREATE OR REPLACE PROCEDURE p4(x DATE DEFAULT CURRENT_DATE()) RETURNS VARCHAR LANGUAGE SQL AS $$ "
                + "BEGIN RETURN x::VARCHAR; END; $$");
        assertAnswer(
            "1",
            "SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 47 | invalid default argument expression "
                + "JSF()",
            "CREATE OR REPLACE PROCEDURE p5(x FLOAT DEFAULT jsf()) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
                + "RETURN x::VARCHAR; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 48 | invalid default argument expression F1()",
            "CREATE OR REPLACE PROCEDURE p6(x NUMBER DEFAULT test_schema.f1()) RETURNS VARCHAR LANGUAGE SQL AS $$ "
                + "BEGIN RETURN x::VARCHAR; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 48 | invalid default argument expression "
                + "ABS(F1())",
            "CREATE OR REPLACE PROCEDURE p7(x NUMBER DEFAULT ABS(f1())) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
                + "RETURN x::VARCHAR; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 53 | invalid default argument expression "
                + "F1() + 1",
            "CREATE OR REPLACE PROCEDURE p8(x NUMBER DEFAULT f1() + 1) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
                + "RETURN x::VARCHAR; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NOSUCH_FN_ZZ.",
            "CREATE OR REPLACE PROCEDURE p9(x NUMBER DEFAULT nosuch_fn_zz()) RETURNS VARCHAR LANGUAGE SQL AS $$ "
                + "BEGIN RETURN x::VARCHAR; END; $$");
        assertAnswer(
            "Function P11 successfully created.",
            "CREATE OR REPLACE PROCEDURE p11(x VARCHAR DEFAULT UPPER('a')) RETURNS VARCHAR LANGUAGE SQL AS $$ "
                + "BEGIN RETURN x::VARCHAR; END; $$");
        assertAnswer(
            "A",
            "CALL p11()");
        assertAnswer(
            "Function P12 successfully created.",
            "CREATE OR REPLACE PROCEDURE p12(x NUMBER DEFAULT 'abc') RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
                + "RETURN x::VARCHAR; END; $$");
        assertAnswer(
            "Function P13 successfully created.",
            "CREATE OR REPLACE PROCEDURE p13(x NUMBER DEFAULT NULL) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
                + "RETURN x::VARCHAR; END; $$");
        assertAnswer(
            "null",
            "CALL p13()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 47 | invalid default argument expression F1()",
            "CREATE OR REPLACE FUNCTION g1(x NUMBER DEFAULT f1()) RETURNS VARCHAR AS 'x::VARCHAR'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function G1.",
            "SELECT g1()");
        assertAnswer(
            "Function G2 successfully created.",
            "CREATE OR REPLACE FUNCTION g2(x NUMBER DEFAULT ABS(-1)) RETURNS VARCHAR AS 'x::VARCHAR'");
        assertAnswer(
            "1",
            "SELECT g2()");
        assertAnswer(
            "Function G3 successfully created.",
            "CREATE OR REPLACE FUNCTION g3(x NUMBER DEFAULT 1 + 1) RETURNS VARCHAR AS 'x::VARCHAR'");
        assertAnswer(
            "2",
            "SELECT g3()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 46 | invalid default argument expression "
                + "JSF()",
            "CREATE OR REPLACE FUNCTION g5(x FLOAT DEFAULT jsf()) RETURNS VARCHAR AS 'x::VARCHAR'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 47 | invalid default argument expression F1()",
            "CREATE OR REPLACE FUNCTION g6(x NUMBER DEFAULT test_schema.f1()) RETURNS VARCHAR AS 'x::VARCHAR'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 47 | invalid default argument expression "
                + "ABS(F1())",
            "CREATE OR REPLACE FUNCTION g7(x NUMBER DEFAULT ABS(f1())) RETURNS VARCHAR AS 'x::VARCHAR'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid default argument expression "
                + "F1() + 1",
            "CREATE OR REPLACE FUNCTION g8(x NUMBER DEFAULT f1() + 1) RETURNS VARCHAR AS 'x::VARCHAR'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NOSUCH_FN_ZZ.",
            "CREATE OR REPLACE FUNCTION g9(x NUMBER DEFAULT nosuch_fn_zz()) RETURNS VARCHAR AS 'x::VARCHAR'");
        assertAnswer(
            "Function G11 successfully created.",
            "CREATE OR REPLACE FUNCTION g11(x VARCHAR DEFAULT UPPER('a')) RETURNS VARCHAR AS 'x::VARCHAR'");
        assertAnswer(
            "A",
            "SELECT g11()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 58 | invalid default argument expression F1()",
            "CREATE OR REPLACE FUNCTION g12(a NUMBER, x NUMBER DEFAULT f1()) RETURNS NUMBER AS 'a + x'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 58 | invalid identifier 'A'",
            "CREATE OR REPLACE FUNCTION g13(a NUMBER, x NUMBER DEFAULT a) RETURNS NUMBER AS 'a + x'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 69 | invalid default argument expression F1()",
            "CREATE OR REPLACE FUNCTION g14(x NUMBER DEFAULT 1, y NUMBER DEFAULT  f1()) RETURNS NUMBER AS 'x + y'");
    }
}
