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
 * Which overload a call written with named arguments runs. The names choose before the types do: only an
 * overload that declares every name written, and is left no required parameter unfilled, is a candidate, and
 * each value is judged against the parameter its name fills, in whatever order the call writes them -
 * {@code pick(y => 5)} runs {@code pick(y VARCHAR)} beside {@code pick(x NUMBER)}, and {@code ov(b => 1, a =>
 * 'x')} the overload whose a is a text. Names no overload takes, and names whose only overloads refuse a value's
 * type, are {@code named arguments [X] do not match any signature for function PICK}, the function named as
 * the call writes it; a name written twice is refused first, under the bare name. Overloads that take the
 * values equally well are refused - {@code More than one function of OV are resolved in prepare state.} - while
 * the statement compiles, over a table with no rows and in the body of a function being created too, where the
 * same arguments written by position run the first overload in the order of the signatures. An untyped NULL
 * prefers no overload. A table function converts nothing, so its names fit an overload or none; a procedure's
 * CALL chooses as a function's call does. Every answer is live-verified.
 */
public class NamedArgumentOverloadTest extends MeasuredCellsTestSupport {

    @Test
    public void namesChooseTheOverload() {
        assertAnswer(
            "Function PICK successfully created.",
            "CREATE OR REPLACE FUNCTION pick(x NUMBER) RETURNS VARCHAR AS '''x-number'''");
        assertAnswer(
            "Function PICK successfully created.",
            "CREATE OR REPLACE FUNCTION pick(y VARCHAR) RETURNS VARCHAR AS '''y-varchar'''");
        assertAnswer(
            "x-number|y-varchar",
            "SELECT pick(x => 5), pick(y => 'a')");
        assertAnswer(
            "x-number",
            "SELECT pick(x => '5')");
        assertAnswer(
            "y-varchar",
            "SELECT pick(y => 5)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [Z] do not match any "
                + "signature for function PICK",
            "SELECT pick(z => 5)");
        assertAnswer(
            "x-number",
            "SELECT pick(5)");
        assertAnswer(
            "y-varchar",
            "SELECT pick('a')");
        assertAnswer(
            "Function OV successfully created.",
            "CREATE OR REPLACE FUNCTION ov(a NUMBER, b VARCHAR) RETURNS VARCHAR AS '''num-var'''");
        assertAnswer(
            "Function OV successfully created.",
            "CREATE OR REPLACE FUNCTION ov(a VARCHAR, b NUMBER) RETURNS VARCHAR AS '''var-num'''");
        assertAnswer(
            "num-var",
            "SELECT ov(a => 1, b => 'x')");
        assertAnswer(
            "num-var",
            "SELECT ov(b => 'x', a => 1)");
        assertAnswer(
            "var-num",
            "SELECT ov(b => 1, a => 'x')");
        assertAnswer(
            "num-var",
            "SELECT ov(1, b => 'x')");
        assertAnswer(
            "var-num",
            "SELECT ov('x', b => 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | More than one function of OV are resolved in prepare state.",
            "SELECT ov(a => 1, b => 2)");
        assertAnswer(
            "num-var",
            "SELECT ov(1, 'x')");
        assertAnswer(
            "Function OVB successfully created.",
            "CREATE OR REPLACE FUNCTION ovb(a NUMBER, b VARCHAR) RETURNS VARCHAR AS '''nv:'' || a || ''-'' || b'");
        assertAnswer(
            "Function OVB successfully created.",
            "CREATE OR REPLACE FUNCTION ovb(a VARCHAR, b NUMBER) RETURNS VARCHAR AS '''vn:'' || a || ''-'' || b'");
        assertAnswer(
            "nv:1-x",
            "SELECT ovb(b => 'x', a => 1)");
        assertAnswer(
            "vn:x-1",
            "SELECT ovb(b => 1, a => 'x')");
        assertAnswer(
            "Function ONE successfully created.",
            "CREATE OR REPLACE FUNCTION one(p NUMBER, q VARCHAR) RETURNS VARCHAR AS 'p || ''-'' || q'");
        assertAnswer(
            "1-x",
            "SELECT one(q => 'x', p => 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [Q, Z] do not match any "
                + "signature for function ONE",
            "SELECT one(z => 1, q => 'x')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 24 | duplicate named argument P for function "
                + "ONE",
            "SELECT one(p => 1, p => 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [P] do not match any "
                + "signature for function ONE",
            "SELECT one(p => 1)");
        assertAnswer(
            "Function DFL successfully created.",
            "CREATE OR REPLACE FUNCTION dfl(p NUMBER, q VARCHAR DEFAULT 'dq') RETURNS VARCHAR AS 'p || ''-'' || q'");
        assertAnswer(
            "Function DFL successfully created.",
            "CREATE OR REPLACE FUNCTION dfl(p VARCHAR, r VARCHAR DEFAULT 'dr') RETURNS VARCHAR AS 'p || ''+'' || "
                + "r'");
        assertAnswer(
            "1-dq",
            "SELECT dfl(p => 1)");
        assertAnswer(
            "a+dr",
            "SELECT dfl(p => 'a')");
        assertAnswer(
            "1-x",
            "SELECT dfl(p => 1, q => 'x')");
        assertAnswer(
            "a+y",
            "SELECT dfl(p => 'a', r => 'y')");
        assertAnswer(
            "1+y",
            "SELECT dfl(r => 'y', p => 1)");
        assertAnswer(
            "Function PK successfully created.",
            "CREATE OR REPLACE PROCEDURE pk(x NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'x-number'; "
                + "END; $$");
        assertAnswer(
            "Function PK successfully created.",
            "CREATE OR REPLACE PROCEDURE pk(y VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "'y-varchar'; END; $$");
        assertAnswer(
            "x-number",
            "CALL pk(x => 5)");
        assertAnswer(
            "y-varchar",
            "CALL pk(y => 'a')");
        assertAnswer(
            "x-number",
            "CALL pk(x => '5')");
        assertAnswer(
            "y-varchar",
            "CALL pk(y => 5)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | named arguments [Z] do not match any "
                + "signature for function PK",
            "CALL pk(z => 5)");
    }

    @Test
    public void valuesAreJudgedAgainstTheParameterTheyName() {
        assertAnswer(
            "Function OV successfully created.",
            "CREATE OR REPLACE FUNCTION ov(a NUMBER, b VARCHAR) RETURNS VARCHAR AS '''num-var'''");
        assertAnswer(
            "Function OV successfully created.",
            "CREATE OR REPLACE FUNCTION ov(a VARCHAR, b NUMBER) RETURNS VARCHAR AS '''var-num'''");
        assertAnswer(
            "num-var",
            "SELECT ov(1, 2)");
        assertAnswer(
            "num-var",
            "SELECT ov('x', 'y')");
        assertAnswer(
            "REFUSED SQL compilation error: | More than one function of OV are resolved in prepare state.",
            "SELECT ov(a => 'x', b => 'y')");
        assertAnswer(
            "REFUSED SQL compilation error: | More than one function of OV are resolved in prepare state.",
            "SELECT ov(b => 2, a => 1)");
        assertAnswer(
            "num-var",
            "SELECT ov(b => s, a => n) FROM (SELECT 1 AS n, 'x' AS s)");
        assertAnswer(
            "var-num",
            "SELECT ov(b => n, a => s) FROM (SELECT 1 AS n, 'x' AS s)");
        assertAnswer(
            "num-var",
            "SELECT ov(a => NULL, b => 'x')");
        assertAnswer(
            "var-num",
            "SELECT ov(a => NULL, b => 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | More than one function of OV are resolved in prepare state.",
            "SELECT ov(a => NULL, b => NULL)");
        assertAnswer(
            "num-var",
            "SELECT ov(NULL, NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | More than one function of OV are resolved in prepare state.",
            "SELECT ov(1, b => 2)");
        assertAnswer(
            "var-num",
            "SELECT ov(b => 'x', a => 1.5::FLOAT)");
        assertAnswer(
            "num-var|var-num",
            "SELECT ov(1, 'x'), ov('x', 1)");
        assertAnswer(
            "Function PICK successfully created.",
            "CREATE OR REPLACE FUNCTION pick(x NUMBER) RETURNS VARCHAR AS '''x-number'''");
        assertAnswer(
            "Function PICK successfully created.",
            "CREATE OR REPLACE FUNCTION pick(y VARCHAR) RETURNS VARCHAR AS '''y-varchar'''");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [X] do not match any "
                + "signature for function PICK",
            "SELECT pick(x => '2024-01-01'::DATE)");
        assertAnswer(
            "y-varchar",
            "SELECT pick(y => '2024-01-01'::DATE)");
        assertAnswer(
            "x-number",
            "SELECT pick(x => NULL)");
        assertAnswer(
            "y-varchar",
            "SELECT pick(y => NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [X] do not match any "
                + "signature for function PICK",
            "SELECT pick(x => TRUE)");
        assertAnswer(
            "x-number",
            "SELECT pick(x => 'abc')");
        assertAnswer(
            "x-number",
            "SELECT pick(X => 5)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [x] do not match any "
                + "signature for function PICK",
            "SELECT pick(\"x\" => 5)");
        assertAnswer(
            "x-number",
            "SELECT pick(\"X\" => 5)");
        assertAnswer(
            "x-number",
            "SELECT pick(NULL)");
        assertAnswer(
            "Function TRI successfully created.",
            "CREATE OR REPLACE FUNCTION tri(a NUMBER) RETURNS VARCHAR AS '''a-num'''");
        assertAnswer(
            "Function TRI successfully created.",
            "CREATE OR REPLACE FUNCTION tri(a VARCHAR) RETURNS VARCHAR AS '''a-var'''");
        assertAnswer(
            "Function TRI successfully created.",
            "CREATE OR REPLACE FUNCTION tri(b DATE) RETURNS VARCHAR AS '''b-date'''");
        assertAnswer(
            "a-num",
            "SELECT tri(a => 1)");
        assertAnswer(
            "a-var",
            "SELECT tri(a => 'x')");
        assertAnswer(
            "b-date",
            "SELECT tri(b => '2024-01-01')");
        assertAnswer(
            "a-var",
            "SELECT tri(a => '2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [B] do not match any "
                + "signature for function TRI",
            "SELECT tri(b => 5)");
        assertAnswer(
            "a-var",
            "SELECT tri(a => 1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: | More than one function of TRI are resolved in prepare state.",
            "SELECT tri(a => NULL)");
        assertAnswer(
            "b-date",
            "SELECT tri(b => NULL)");
        assertAnswer(
            "a-var",
            "SELECT tri(a => TRUE)");
        assertAnswer(
            "Function DFL successfully created.",
            "CREATE OR REPLACE FUNCTION dfl(p NUMBER, q VARCHAR DEFAULT 'dq') RETURNS VARCHAR AS 'p || ''-'' || q'");
        assertAnswer(
            "Function DFL successfully created.",
            "CREATE OR REPLACE FUNCTION dfl(p VARCHAR, r VARCHAR DEFAULT 'dr') RETURNS VARCHAR AS 'p || ''+'' || "
                + "r'");
        assertAnswer(
            "1-x",
            "SELECT dfl(q => 'x', p => 1)");
        assertAnswer(
            "REFUSED Numeric value 'a' is not recognized",
            "SELECT dfl(q => 'x', p => 'a')");
        assertAnswer(
            "a+y",
            "SELECT dfl(r => 'y', p => 'a')");
        assertAnswer(
            "REFUSED SQL compilation error: | More than one function of DFL are resolved in prepare state.",
            "SELECT dfl(p => NULL)");
        assertAnswer(
            "null",
            "SELECT dfl(NULL)");
        assertAnswer(
            "7-x",
            "SELECT dfl(q => 'x', p => '7')");
        assertAnswer(
            "Function TPICK successfully created.",
            "CREATE OR REPLACE FUNCTION tpick(x NUMBER) RETURNS TABLE (v VARCHAR) AS 'SELECT ''x-number'''");
        assertAnswer(
            "Function TPICK successfully created.",
            "CREATE OR REPLACE FUNCTION tpick(y VARCHAR) RETURNS TABLE (v VARCHAR) AS 'SELECT ''y-varchar'''");
        assertAnswer(
            "x-number",
            "SELECT v FROM TABLE(tpick(x => 5))");
        assertAnswer(
            "y-varchar",
            "SELECT v FROM TABLE(tpick(y => 'a'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | named arguments [X] do not match any "
                + "signature for function TPICK",
            "SELECT v FROM TABLE(tpick(x => '5'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | named arguments [Y] do not match any "
                + "signature for function TPICK",
            "SELECT v FROM TABLE(tpick(y => 5))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | named arguments [Z] do not match any "
                + "signature for function TPICK",
            "SELECT v FROM TABLE(tpick(z => 5))");
        assertAnswer(
            "x-number",
            "SELECT v FROM TABLE(tpick(5))");
        assertAnswer(
            "y-varchar",
            "SELECT v FROM TABLE(tpick('a'))");
        assertAnswer(
            "Function TOV successfully created.",
            "CREATE OR REPLACE FUNCTION tov(a NUMBER, b VARCHAR) RETURNS TABLE (v VARCHAR) AS 'SELECT ''num-var'''");
        assertAnswer(
            "Function TOV successfully created.",
            "CREATE OR REPLACE FUNCTION tov(a VARCHAR, b NUMBER) RETURNS TABLE (v VARCHAR) AS 'SELECT ''var-num'''");
        assertAnswer(
            "num-var",
            "SELECT v FROM TABLE(tov(b => 'x', a => 1))");
        assertAnswer(
            "var-num",
            "SELECT v FROM TABLE(tov(b => 1, a => 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | named arguments [A, B] do not match any "
                + "signature for function TOV",
            "SELECT v FROM TABLE(tov(a => 1, b => 2))");
        assertAnswer(
            "num-var",
            "SELECT v FROM TABLE(tov(1, 'x'))");
        assertAnswer(
            "Function PV successfully created.",
            "CREATE OR REPLACE PROCEDURE pv(a NUMBER, b VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "'num-var'; END; $$");
        assertAnswer(
            "Function PV successfully created.",
            "CREATE OR REPLACE PROCEDURE pv(a VARCHAR, b NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "'var-num'; END; $$");
        assertAnswer(
            "num-var",
            "CALL pv(b => 'x', a => 1)");
        assertAnswer(
            "var-num",
            "CALL pv(b => 1, a => 'x')");
        assertAnswer(
            "REFUSED SQL compilation error: | More than one function of PV are resolved in prepare state.",
            "CALL pv(a => 1, b => 2)");
        assertAnswer(
            "num-var",
            "CALL pv(1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 5 | illegal mixing of named and positional "
                + "arguments for function PV",
            "CALL pv(a => 1, 'x')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 5 | illegal mixing of named and positional "
                + "arguments for function PV",
            "CALL pv(1, b => 'x')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 5 | illegal mixing of named and positional "
                + "arguments for function PV",
            "CALL pv('x', b => 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | More than one function of PV are resolved in prepare state.",
            "CALL pv(a => 'x', b => 'y')");
        assertAnswer(
            "Function PK successfully created.",
            "CREATE OR REPLACE PROCEDURE pk(x NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'x-number'; "
                + "END; $$");
        assertAnswer(
            "Function PK successfully created.",
            "CREATE OR REPLACE PROCEDURE pk(y VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "'y-varchar'; END; $$");
        assertAnswer(
            "y-varchar",
            "CALL pk(y => NULL)");
        assertAnswer(
            "x-number",
            "CALL pk(x => NULL)");
        assertAnswer(
            "y-varchar",
            "CALL pk(y => '2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | named arguments [X] do not match any "
                + "signature for function PK",
            "CALL pk(x => '2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED Numeric value 'abc' is not recognized",
            "CALL pk(x => 'abc')");
        assertAnswer(
            "y-varchar",
            "CALL pk(y => TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | named arguments [X] do not match any "
                + "signature for function PK",
            "CALL pk(x => TRUE)");
        assertAnswer(
            "x-number",
            "CALL pk(X => 5)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | named arguments [x] do not match any "
                + "signature for function PK",
            "CALL pk(\"x\" => 5)");
        assertAnswer(
            "Function ONE successfully created.",
            "CREATE OR REPLACE FUNCTION one(p NUMBER, q VARCHAR) RETURNS VARCHAR AS 'p || ''-'' || q'");
    }

    @Test
    public void overloadsThatWeighAlike() {
        assertAnswer(
            "Function RV successfully created.",
            "CREATE OR REPLACE FUNCTION rv(a VARCHAR, b NUMBER) RETURNS VARCHAR AS '''var-num'''");
        assertAnswer(
            "Function RV successfully created.",
            "CREATE OR REPLACE FUNCTION rv(a NUMBER, b VARCHAR) RETURNS VARCHAR AS '''num-var'''");
        assertAnswer(
            "num-var",
            "SELECT rv(1, 2)");
        assertAnswer(
            "num-var",
            "SELECT rv('x', 'y')");
        assertAnswer(
            "num-var",
            "SELECT rv(NULL, NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | More than one function of RV are resolved in prepare state.",
            "SELECT rv(a => 1, b => 2)");
        assertAnswer(
            "Function OV successfully created.",
            "CREATE OR REPLACE FUNCTION ov(a NUMBER, b VARCHAR) RETURNS VARCHAR AS '''num-var'''");
        assertAnswer(
            "Function OV successfully created.",
            "CREATE OR REPLACE FUNCTION ov(a VARCHAR, b NUMBER) RETURNS VARCHAR AS '''var-num'''");
        assertAnswer(
            "var-num",
            "SELECT ov(1.5::FLOAT, 'x')");
        assertAnswer(
            "num-var",
            "SELECT ov('x', 1.5::FLOAT)");
        assertAnswer(
            "num-var",
            "SELECT ov(a => 'x', b => 1.5::FLOAT)");
        assertAnswer(
            "var-num",
            "SELECT ov(TRUE, 'x')");
        assertAnswer(
            "var-num",
            "SELECT ov(a => TRUE, b => 'x')");
        assertAnswer(
            "num-var",
            "SELECT ov(a => NULL::NUMBER, b => NULL::VARCHAR)");
        assertAnswer(
            "var-num",
            "SELECT ov(a => NULL::VARCHAR, b => NULL)");
        assertAnswer(
            "var-num",
            "SELECT ov(NULL::VARCHAR, NULL)");
        assertAnswer(
            "Function X1 successfully created.",
            "CREATE OR REPLACE FUNCTION x1(a NUMBER, b BOOLEAN) RETURNS VARCHAR AS '''num-bool'''");
        assertAnswer(
            "Function X1 successfully created.",
            "CREATE OR REPLACE FUNCTION x1(a FLOAT, b VARCHAR) RETURNS VARCHAR AS '''float-var'''");
        assertAnswer(
            "REFUSED SQL compilation error: | More than one function of X1 are resolved in prepare state.",
            "SELECT x1(a => 1, b => 'x')");
        assertAnswer(
            "num-bool",
            "SELECT x1(1, 'x')");
        assertAnswer(
            "num-bool",
            "SELECT x1(a => 1, b => TRUE)");
        assertAnswer(
            "float-var",
            "SELECT x1(a => 1.5::FLOAT, b => TRUE)");
        assertAnswer(
            "float-var",
            "SELECT x1(1.5::FLOAT, TRUE)");
        assertAnswer(
            "Function Y1 successfully created.",
            "CREATE OR REPLACE FUNCTION y1(a VARCHAR, b VARCHAR) RETURNS VARCHAR AS '''var-var'''");
        assertAnswer(
            "Function Y1 successfully created.",
            "CREATE OR REPLACE FUNCTION y1(a VARIANT, b NUMBER) RETURNS VARCHAR AS '''variant-num'''");
        assertAnswer(
            "var-var",
            "SELECT y1(a => 1.5::FLOAT, b => 'x')");
        assertAnswer(
            "var-var",
            "SELECT y1(1.5::FLOAT, 'x')");
        assertAnswer(
            "Function Z1 successfully created.",
            "CREATE OR REPLACE FUNCTION z1(a VARCHAR, b NUMBER) RETURNS VARCHAR AS '''var-num'''");
        assertAnswer(
            "Function Z1 successfully created.",
            "CREATE OR REPLACE FUNCTION z1(a VARCHAR, b FLOAT) RETURNS VARCHAR AS '''var-float'''");
        assertAnswer(
            "var-num",
            "SELECT z1(a => 1.5::FLOAT, b => 1)");
        assertAnswer(
            "var-num",
            "SELECT z1(1.5::FLOAT, 1)");
        assertAnswer(
            "var-float",
            "SELECT z1(a => 'x', b => 'y')");
        assertAnswer(
            "var-float",
            "SELECT z1('x', 'y')");
        assertAnswer(
            "REFUSED SQL compilation error: | More than one function of Z1 are resolved in prepare state.",
            "SELECT z1(a => 'x', b => NULL)");
        assertAnswer(
            "var-num",
            "SELECT z1('x', NULL)");
        assertAnswer(
            "Function U1 successfully created.",
            "CREATE OR REPLACE FUNCTION u1(a NUMBER, b NUMBER) RETURNS VARCHAR AS '''num-num'''");
        assertAnswer(
            "Function U1 successfully created.",
            "CREATE OR REPLACE FUNCTION u1(a BOOLEAN, b VARCHAR) RETURNS VARCHAR AS '''bool-var'''");
        assertAnswer(
            "bool-var",
            "SELECT u1(a => 'x', b => 'y')");
        assertAnswer(
            "bool-var",
            "SELECT u1('x', 'y')");
        assertAnswer(
            "Function T1 successfully created.",
            "CREATE OR REPLACE FUNCTION t1(a FLOAT, b FLOAT) RETURNS VARCHAR AS '''float-float'''");
        assertAnswer(
            "Function T1 successfully created.",
            "CREATE OR REPLACE FUNCTION t1(a NUMBER, b VARCHAR) RETURNS VARCHAR AS '''num-var'''");
        assertAnswer(
            "num-var",
            "SELECT t1(a => 'x', b => 'y')");
        assertAnswer(
            "num-var",
            "SELECT t1('x', 'y')");
        assertAnswer(
            "float-float",
            "SELECT t1(a => 1, b => 2)");
        assertAnswer(
            "float-float",
            "SELECT t1(1, 2)");
        assertAnswer(
            "Function W3 successfully created.",
            "CREATE OR REPLACE FUNCTION w3(a NUMBER, b NUMBER) RETURNS VARCHAR AS '''nn'''");
        assertAnswer(
            "Function W3 successfully created.",
            "CREATE OR REPLACE FUNCTION w3(a VARCHAR, b VARCHAR) RETURNS VARCHAR AS '''vv'''");
        assertAnswer(
            "Function W3 successfully created.",
            "CREATE OR REPLACE FUNCTION w3(a NUMBER, b VARCHAR) RETURNS VARCHAR AS '''nv'''");
        assertAnswer(
            "nv",
            "SELECT w3(a => 1, b => 'x')");
        assertAnswer(
            "nn",
            "SELECT w3(a => 'x', b => 1)");
        assertAnswer(
            "nn",
            "SELECT w3('x', 1)");
        assertAnswer(
            "nn",
            "SELECT w3(a => 1, b => 2)");
        assertAnswer(
            "vv",
            "SELECT w3(a => 'x', b => 'y')");
        assertAnswer(
            "REFUSED SQL compilation error: | More than one function of W3 are resolved in prepare state.",
            "SELECT w3(a => NULL, b => NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | More than one function of W3 are resolved in prepare state.",
            "SELECT w3(a => 1, b => NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | More than one function of W3 are resolved in prepare state.",
            "SELECT w3(a => NULL, b => 'x')");
        assertAnswer(
            "nn",
            "SELECT w3(NULL, NULL)");
        assertAnswer(
            "Function Q3 successfully created.",
            "CREATE OR REPLACE FUNCTION q3(a NUMBER, b NUMBER, c VARCHAR) RETURNS VARCHAR AS '''nnv'''");
        assertAnswer(
            "Function Q3 successfully created.",
            "CREATE OR REPLACE FUNCTION q3(a VARCHAR, b VARCHAR, c NUMBER) RETURNS VARCHAR AS '''vvn'''");
        assertAnswer(
            "nnv",
            "SELECT q3(a => 1, b => 2, c => 3)");
        assertAnswer(
            "nnv",
            "SELECT q3(1, 2, 3)");
        assertAnswer(
            "vvn",
            "SELECT q3(a => 'x', b => 'y', c => 'z')");
        assertAnswer(
            "vvn",
            "SELECT q3('x', 'y', 'z')");
        assertAnswer(
            "nnv",
            "SELECT q3(c => 3, b => 2, a => 1)");
        assertAnswer(
            "nnv",
            "SELECT q3(a => 1, b => 'y', c => 'z')");
        assertAnswer(
            "vvn",
            "SELECT q3(a => 1, b => 'y', c => 3)");
        assertAnswer(
            "Function PICK successfully created.",
            "CREATE OR REPLACE FUNCTION pick(x NUMBER) RETURNS VARCHAR AS '''x-number'''");
        assertAnswer(
            "Function PICK successfully created.",
            "CREATE OR REPLACE FUNCTION pick(y VARCHAR) RETURNS VARCHAR AS '''y-varchar'''");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [Z] do not match any "
                + "signature for function TEST_SCHEMA.PICK",
            "SELECT test_schema.pick(z => 5)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [Z] do not match any "
                + "signature for function TEST_DB.TEST_SCHEMA.PICK",
            "SELECT test_db.test_schema.pick(z => 5)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [X] do not match any "
                + "signature for function TEST_SCHEMA.PICK",
            "SELECT test_schema.pick(x => TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 45 | duplicate named argument X for function "
                + "PICK",
            "SELECT TEST_DB.test_schema.pick(x => 1, x => 2)");
        assertAnswer(
            "y-varchar",
            "SELECT pick(TRUE)");
        assertAnswer(
            "y-varchar",
            "SELECT test_schema.pick(TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | More than one function of TEST_SCHEMA.OV are resolved in prepare "
                + "state.",
            "SELECT test_schema.ov(a => 1, b => 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | More than one function of TEST_DB.TEST_SCHEMA.OV are resolved in "
                + "prepare state.",
            "SELECT test_db.test_schema.ov(a => 1, b => 2)");
        assertAnswer(
            "Table N_EMPTY successfully created.",
            "CREATE OR REPLACE TABLE n_empty (v NUMBER)");
        assertAnswer(
            "REFUSED SQL compilation error: | More than one function of OV are resolved in prepare state.",
            "SELECT ov(a => v, b => v) FROM n_empty");
        assertAnswer(
            "REFUSED SQL compilation error: | More than one function of OV are resolved in prepare state.",
            "SELECT ov(a => 1, b => 2) FROM n_empty");
        assertAnswer(
            "",
            "SELECT ov(v, v) FROM n_empty");
        assertAnswer(
            "REFUSED SQL compilation error: | More than one function of OV are resolved in prepare state.",
            "CREATE OR REPLACE FUNCTION n_amb() RETURNS VARCHAR AS 'ov(a => 1, b => 2)'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function N_AMB.",
            "SELECT n_amb()");
        assertAnswer(
            "Function PRV successfully created.",
            "CREATE OR REPLACE PROCEDURE prv(a VARCHAR, b NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "'var-num'; END; $$");
        assertAnswer(
            "Function PRV successfully created.",
            "CREATE OR REPLACE PROCEDURE prv(a NUMBER, b VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "'num-var'; END; $$");
        assertAnswer(
            "num-var",
            "CALL prv(1, 2)");
        assertAnswer(
            "REFUSED Numeric value 'x' is not recognized",
            "CALL prv('x', 'y')");
        assertAnswer(
            "num-var",
            "CALL prv(NULL, NULL)");
        assertAnswer(
            "Function PV successfully created.",
            "CREATE OR REPLACE PROCEDURE pv(a NUMBER, b VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "'num-var'; END; $$");
        assertAnswer(
            "Function PV successfully created.",
            "CREATE OR REPLACE PROCEDURE pv(a VARCHAR, b NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "'var-num'; END; $$");
        assertAnswer(
            "REFUSED Numeric value 'x' is not recognized",
            "CALL pv(1.5::FLOAT, 'x')");
        assertAnswer(
            "REFUSED Numeric value 'x' is not recognized",
            "CALL pv(a => 1.5::FLOAT, b => 'x')");
        assertAnswer(
            "REFUSED Numeric value 'x' is not recognized",
            "CALL pv('x', 'y')");
        assertAnswer(
            "var-num",
            "CALL pv(a => NULL, b => 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | More than one function of PV are resolved in prepare state.",
            "CALL pv(a => NULL, b => NULL)");
        assertAnswer(
            "num-var",
            "CALL pv(NULL, NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | More than one function of TEST_SCHEMA.PV are resolved in prepare "
                + "state.",
            "CALL test_schema.pv(a => 1, b => 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | named arguments [B, Z] do not match any "
                + "signature for function PV",
            "CALL pv(z => 1, b => 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | named arguments [B, Z] do not match any "
                + "signature for function TEST_SCHEMA.PV",
            "CALL test_schema.pv(z => 1, b => 2)");
        assertAnswer(
            "num-var",
            "CALL pv(b => 'x', a => 1)");
        assertAnswer(
            "var-num",
            "CALL pv(b => 1, a => 'x')");
    }
}
