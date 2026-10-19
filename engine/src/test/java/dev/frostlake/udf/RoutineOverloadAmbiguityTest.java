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
 * Which overloads of one name may stand side by side. A function or a procedure whose signature an existing
 * overload would compete with for some call is refused when it is created - with OR REPLACE and with IF NOT
 * EXISTS too - and when another routine is renamed onto the name: {@code Cannot overload FUNCTION 'DFO' as it
 * would cause ambiguous FUNCTION overloading.}, 949 / 42723 to a handler. Two signatures compete when both take
 * a call of some argument count and agree in the types of that many leading parameters, so that optional
 * arguments alone tell them apart: {@code f(a NUMBER)} beside {@code f(a NUMBER, b NUMBER DEFAULT 0)}, in either
 * order, {@code f()} beside {@code f(o NUMBER DEFAULT 0)}, and two overloads whose optional arguments alone
 * differ in type. Overloads a call tells apart by a required argument's type stand, and a call reaches the one
 * its arguments fit; the same signature again is the name already taken, which OR REPLACE replaces. A function
 * is not created over a procedure of its name and signature, nor a procedure over a function - {@code Object
 * 'QF' already exists as FUNCTION} -, with OR REPLACE and with IF NOT EXISTS neither, and neither is renamed
 * onto the other's name. Every answer is live-verified.
 */
public class RoutineOverloadAmbiguityTest extends MeasuredCellsTestSupport {

    @Test
    public void overloadsThatDifferByOptionalArgumentsAlone() {
        assertAnswer(
            "Function OV successfully created.",
            "CREATE PROCEDURE ov(a NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'one'; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: Cannot overload PROCEDURE 'OV' as it would cause ambiguous PROCEDURE "
                + "overloading.",
            "CREATE PROCEDURE ov(a NUMBER, b NUMBER DEFAULT 1) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "'two'; END; $$");
        assertAnswer(
            "one",
            "CALL ov(1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | too many arguments for function [OV(1, "
                + "2)] expected 1, got 2",
            "CALL ov(1, 2)");
        assertAnswer(
            "Function OV2 successfully created.",
            "CREATE PROCEDURE ov2(a NUMBER, b NUMBER DEFAULT 1) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "'two'; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: Cannot overload PROCEDURE 'OV2' as it would cause ambiguous PROCEDURE "
                + "overloading.",
            "CREATE PROCEDURE ov2(a NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'one'; END; $$");
        assertAnswer(
            "two",
            "CALL ov2(1)");
        assertAnswer(
            "Function DFO successfully created.",
            "CREATE FUNCTION dfo(a NUMBER) RETURNS VARCHAR AS $$ 'one' $$");
        assertAnswer(
            "REFUSED SQL compilation error: Cannot overload FUNCTION 'DFO' as it would cause ambiguous FUNCTION "
                + "overloading.",
            "CREATE FUNCTION dfo(a NUMBER, b NUMBER DEFAULT 0) RETURNS VARCHAR AS $$ 'two' $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function [DFO(1, "
                + "2)] expected 1, got 2",
            "SELECT dfo(1, 2)");
        assertAnswer(
            "one",
            "SELECT dfo(1)");
        assertAnswer(
            "Function Z0 successfully created.",
            "CREATE FUNCTION z0() RETURNS VARCHAR AS $$ 'zero' $$");
        assertAnswer(
            "REFUSED SQL compilation error: Cannot overload FUNCTION 'Z0' as it would cause ambiguous FUNCTION "
                + "overloading.",
            "CREATE FUNCTION z0(o NUMBER DEFAULT 0) RETURNS VARCHAR AS $$ 'opt' $$");
        assertAnswer(
            "zero",
            "SELECT z0()");
        assertAnswer(
            "Function RN1 successfully created.",
            "CREATE FUNCTION rn1(a NUMBER) RETURNS VARCHAR AS $$ 'rn1' $$");
        assertAnswer(
            "Function RN2 successfully created.",
            "CREATE FUNCTION rn2(a NUMBER, b NUMBER DEFAULT 0) RETURNS VARCHAR AS $$ 'rn2' $$");
        assertAnswer(
            "REFUSED SQL compilation error: Cannot overload FUNCTION 'RN1' as it would cause ambiguous FUNCTION "
                + "overloading.",
            "ALTER FUNCTION rn2(NUMBER, NUMBER) RENAME TO rn1");
        assertAnswer(
            "rn1",
            "SELECT rn1(1)");
        assertAnswer(
            "rn2",
            "SELECT rn2(1)");
        assertAnswer(
            "Function PRN1 successfully created.",
            "CREATE PROCEDURE prn1(a NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'prn1'; END; $$");
        assertAnswer(
            "Function PRN2 successfully created.",
            "CREATE PROCEDURE prn2(a NUMBER, b NUMBER DEFAULT 0) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "'prn2'; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: Cannot overload PROCEDURE 'PRN1' as it would cause ambiguous "
                + "PROCEDURE overloading.",
            "ALTER PROCEDURE prn2(NUMBER, NUMBER) RENAME TO prn1");
        assertAnswer(
            "prn1",
            "CALL prn1(1)");
        assertAnswer(
            "prn2",
            "CALL prn2(1)");
        assertAnswer(
            "Function T1 successfully created.",
            "CREATE FUNCTION t1(a NUMBER) RETURNS VARCHAR AS $$ 'number' $$");
        assertAnswer(
            "Function T1 successfully created.",
            "CREATE FUNCTION t1(a VARCHAR, b NUMBER DEFAULT 0) RETURNS VARCHAR AS $$ 'varchar' $$");
        assertAnswer(
            "number",
            "SELECT t1(1)");
        assertAnswer(
            "varchar",
            "SELECT t1('x')");
        assertAnswer(
            "Function T2 successfully created.",
            "CREATE FUNCTION t2(a NUMBER, b VARCHAR) RETURNS VARCHAR AS $$ 'required' $$");
        assertAnswer(
            "Function T2 successfully created.",
            "CREATE FUNCTION t2(a NUMBER, b NUMBER DEFAULT 0) RETURNS VARCHAR AS $$ 'optional' $$");
        assertAnswer(
            "optional",
            "SELECT t2(1)");
        assertAnswer(
            "required",
            "SELECT t2(1, 'x')");
        assertAnswer(
            "optional",
            "SELECT t2(1, 2)");
        assertAnswer(
            "Function T3 successfully created.",
            "CREATE FUNCTION t3(a NUMBER, b NUMBER DEFAULT 0) RETURNS VARCHAR AS $$ 'number' $$");
        assertAnswer(
            "REFUSED SQL compilation error: Cannot overload FUNCTION 'T3' as it would cause ambiguous FUNCTION "
                + "overloading.",
            "CREATE FUNCTION t3(a NUMBER, c VARCHAR DEFAULT 'x') RETURNS VARCHAR AS $$ 'varchar' $$");
        assertAnswer(
            "number",
            "SELECT t3(1)");
        assertAnswer(
            "Function T4 successfully created.",
            "CREATE FUNCTION t4(a NUMBER DEFAULT 0) RETURNS VARCHAR AS $$ 'number' $$");
        assertAnswer(
            "REFUSED SQL compilation error: Cannot overload FUNCTION 'T4' as it would cause ambiguous FUNCTION "
                + "overloading.",
            "CREATE FUNCTION t4(a VARCHAR DEFAULT 'x') RETURNS VARCHAR AS $$ 'varchar' $$");
        assertAnswer(
            "number",
            "SELECT t4()");
        assertAnswer(
            "Function T5 successfully created.",
            "CREATE FUNCTION t5(a NUMBER, b NUMBER DEFAULT 0, c NUMBER DEFAULT 0) RETURNS VARCHAR AS $$ 'three' $$");
        assertAnswer(
            "REFUSED SQL compilation error: Cannot overload FUNCTION 'T5' as it would cause ambiguous FUNCTION "
                + "overloading.",
            "CREATE FUNCTION t5(a NUMBER, b NUMBER) RETURNS VARCHAR AS $$ 'two' $$");
        assertAnswer(
            "three",
            "SELECT t5(1, 2)");
        assertAnswer(
            "Function T6 successfully created.",
            "CREATE FUNCTION t6(a NUMBER, b NUMBER) RETURNS VARCHAR AS $$ 'plain' $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'T6' already exists.",
            "CREATE FUNCTION t6(a NUMBER, b NUMBER DEFAULT 0) RETURNS VARCHAR AS $$ 'defaulted' $$");
        assertAnswer(
            "Function T6 successfully created.",
            "CREATE OR REPLACE FUNCTION t6(a NUMBER, b NUMBER DEFAULT 0) RETURNS VARCHAR AS $$ 'replaced' $$");
        assertAnswer(
            "replaced",
            "SELECT t6(1)");
        assertAnswer(
            "replaced",
            "SELECT t6(1, 2)");
        assertAnswer(
            "Function T7 successfully created.",
            "CREATE FUNCTION t7(a NUMBER) RETURNS VARCHAR AS $$ 'one' $$");
        assertAnswer(
            "REFUSED SQL compilation error: Cannot overload FUNCTION 'T7' as it would cause ambiguous FUNCTION "
                + "overloading.",
            "CREATE OR REPLACE FUNCTION t7(a NUMBER, b NUMBER DEFAULT 0) RETURNS VARCHAR AS $$ 'two' $$");
        assertAnswer(
            "one",
            "SELECT t7(1)");
        assertAnswer(
            "REFUSED SQL compilation error: Cannot overload FUNCTION 'T7' as it would cause ambiguous FUNCTION "
                + "overloading.",
            "CREATE FUNCTION IF NOT EXISTS t7(a NUMBER, b NUMBER DEFAULT 0) RETURNS VARCHAR AS $$ 'two' $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function [T7(1, "
                + "2)] expected 1, got 2",
            "SELECT t7(1, 2)");
        assertAnswer(
            "Function T8 successfully created.",
            "CREATE FUNCTION t8(a NUMBER) RETURNS VARCHAR AS $$ 'one' $$");
        assertAnswer(
            "Function T8 successfully created.",
            "CREATE FUNCTION t8(a NUMBER, b NUMBER) RETURNS VARCHAR AS $$ 'two' $$");
        assertAnswer(
            "Function T8 successfully created.",
            "CREATE FUNCTION t8(a VARCHAR) RETURNS VARCHAR AS $$ 'text' $$");
        assertAnswer(
            "one",
            "SELECT t8(1)");
        assertAnswer(
            "two",
            "SELECT t8(1, 2)");
        assertAnswer(
            "text",
            "SELECT t8('x')");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'T8' already exists.",
            "CREATE FUNCTION t8(a INT) RETURNS VARCHAR AS $$ 'int' $$");
        assertAnswer(
            "Function TF successfully created.",
            "CREATE FUNCTION tf(a NUMBER) RETURNS TABLE (x NUMBER) AS $$ SELECT a $$");
        assertAnswer(
            "REFUSED SQL compilation error: Cannot overload FUNCTION 'TF' as it would cause ambiguous FUNCTION "
                + "overloading.",
            "CREATE FUNCTION tf(a NUMBER, b NUMBER DEFAULT 0) RETURNS TABLE (x NUMBER) AS $$ SELECT a + b $$");
        assertAnswer(
            "1",
            "SELECT x FROM TABLE(tf(1))");
        assertAnswer(
            "Function FP successfully created.",
            "CREATE FUNCTION fp(a NUMBER) RETURNS VARCHAR AS $$ 'function' $$");
        assertAnswer(
            "Function FP successfully created.",
            "CREATE PROCEDURE fp(a NUMBER, b NUMBER DEFAULT 0) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "'procedure'; END; $$");
        assertAnswer(
            "function",
            "SELECT fp(1)");
        assertAnswer(
            "procedure",
            "CALL fp(1)");
        assertAnswer(
            "Function PF successfully created.",
            "CREATE PROCEDURE pf(a NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'procedure'; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'PF' already exists as PROCEDURE",
            "CREATE FUNCTION pf(a NUMBER) RETURNS VARCHAR AS $$ 'function' $$");
        assertAnswer(
            "procedure",
            "CALL pf(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function PF.",
            "SELECT pf(1)");
        assertAnswer(
            "Function P3 successfully created.",
            "CREATE PROCEDURE p3(a NUMBER DEFAULT 0, b NUMBER DEFAULT 0) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
                + "RETURN 'two'; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: Cannot overload PROCEDURE 'P3' as it would cause ambiguous PROCEDURE "
                + "overloading.",
            "CREATE PROCEDURE p3(a NUMBER DEFAULT 0) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'one'; END; "
                + "$$");
        assertAnswer(
            "two",
            "CALL p3()");
        assertAnswer(
            "two",
            "CALL p3(1)");
        assertAnswer(
            "949 42723 SQL compilation error: Cannot overload FUNCTION 'DFO' as it would cause ambiguous FUNCTION "
                + "overloading.",
            "BEGIN CREATE FUNCTION dfo(a NUMBER, b NUMBER DEFAULT 0) RETURNS VARCHAR AS $$ 'two' $$; RETURN "
                + "'made'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END;");
        answer(
            "SHOW USER FUNCTIONS LIKE 'DFO' IN SCHEMA test_db.test_schema");
    }

    @Test
    public void aNameAndSignatureARoutineOfTheOtherKindHolds() {
        assertAnswer(
            "Function QF successfully created.",
            "CREATE FUNCTION qf(a NUMBER) RETURNS VARCHAR AS $$ 'function' $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'QF' already exists as FUNCTION",
            "CREATE PROCEDURE qf(a NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'procedure'; END; $$");
        assertAnswer(
            "function",
            "SELECT qf(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function QF.",
            "CALL qf(1)");
        assertAnswer(
            "Function QG successfully created.",
            "CREATE PROCEDURE qg(a NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'procedure'; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'QG' already exists as PROCEDURE",
            "CREATE OR REPLACE FUNCTION qg(a NUMBER) RETURNS VARCHAR AS $$ 'function' $$");
        assertAnswer(
            "procedure",
            "CALL qg(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function QG.",
            "SELECT qg(1)");
        assertAnswer(
            "Function QH successfully created.",
            "CREATE PROCEDURE qh(a NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'procedure'; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'QH' already exists as PROCEDURE",
            "CREATE FUNCTION IF NOT EXISTS qh(a NUMBER) RETURNS VARCHAR AS $$ 'function' $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function QH.",
            "SELECT qh(1)");
        assertAnswer(
            "Function QI successfully created.",
            "CREATE FUNCTION qi(a NUMBER) RETURNS VARCHAR AS $$ 'function' $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'QI' already exists as FUNCTION",
            "CREATE OR REPLACE PROCEDURE qi(a NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "'procedure'; END; $$");
        assertAnswer(
            "function",
            "SELECT qi(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function QI.",
            "CALL qi(1)");
        assertAnswer(
            "Function QK successfully created.",
            "CREATE FUNCTION qk(a NUMBER) RETURNS VARCHAR AS $$ 'function' $$");
        assertAnswer(
            "Function QL successfully created.",
            "CREATE PROCEDURE ql(a NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'procedure'; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'QL' already exists as PROCEDURE",
            "ALTER FUNCTION qk(NUMBER) RENAME TO ql");
        assertAnswer(
            "function",
            "SELECT qk(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'QK' already exists as FUNCTION",
            "ALTER PROCEDURE ql(NUMBER) RENAME TO qk");
        assertAnswer(
            "procedure",
            "CALL ql(1)");
        assertAnswer(
            "Function QM successfully created.",
            "CREATE FUNCTION qm(a NUMBER) RETURNS TABLE (x NUMBER) AS $$ SELECT a $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'QM' already exists.",
            "CREATE FUNCTION qm(a NUMBER) RETURNS VARCHAR AS $$ 'scalar' $$");
        assertAnswer(
            "1",
            "SELECT x FROM TABLE(qm(1))");
        assertAnswer(
            "Function QA successfully created.",
            "CREATE FUNCTION qa(a NUMBER) RETURNS VARCHAR AS $$ 'function' $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'QA' already exists as FUNCTION",
            "CREATE PROCEDURE IF NOT EXISTS qa(a NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "'procedure'; END; $$");
        assertAnswer(
            "function",
            "SELECT qa(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function QA.",
            "CALL qa(1)");
    }
}
