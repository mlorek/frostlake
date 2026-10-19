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
 * The names a SQL function's body may read. A parameter is read by its name as an identifier folds it, so a
 * parameter declared {@code "low"} is read as {@code low} and {@code "low"} is an invalid identifier, told with
 * its quotes. A dotted name is no parameter's: outside a subquery it names nothing - {@code t.x}, the function's
 * own name before a parameter's, {@code x.a} over a VARIANT parameter - and the function is not created,
 * {@code invalid identifier 'T.X'}, a quoted part kept as written. Every answer is live-verified.
 */
public class SqlBodyDottedNameTest extends MeasuredCellsTestSupport {

    @Test
    public void aQuotedParameterAndItsEcho() {
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 1 | invalid identifier '\"low\"'",
            "CREATE OR REPLACE FUNCTION nf_q(\"low\" NUMBER) RETURNS NUMBER AS '\"low\"'");
        assertAnswer(
            "Function NF_Q2 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_q2(\"low\" NUMBER) RETURNS NUMBER AS 'low'");
        assertAnswer(
            "3",
            "SELECT nf_q2(3)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 1 | invalid identifier '\"low\"'",
            "CREATE OR REPLACE FUNCTION nf_q3(low NUMBER) RETURNS NUMBER AS '\"low\"'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 1 | invalid identifier '\"Low x\"'",
            "CREATE OR REPLACE FUNCTION nf_q4(\"Low x\" NUMBER) RETURNS NUMBER AS '\"Low x\" + 1'");
        assertAnswer(
            "Function NF_Q5 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_q5(\"LOW\" NUMBER) RETURNS NUMBER AS '\"LOW\"'");
        assertAnswer(
            "3",
            "SELECT nf_q5(3)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 1 | invalid identifier 'NOSUCH'",
            "CREATE OR REPLACE FUNCTION nf_q6(\"low\" NUMBER) RETURNS NUMBER AS 'nosuch'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 1 | invalid identifier '\"nosuch\"'",
            "CREATE OR REPLACE FUNCTION nf_q7(x NUMBER) RETURNS NUMBER AS '\"nosuch\"'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 1 | invalid identifier '\"No Such\"'",
            "CREATE OR REPLACE FUNCTION nf_q8(x NUMBER) RETURNS NUMBER AS '\"No Such\"'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 1 | invalid identifier 'T.\"low\"'",
            "CREATE OR REPLACE FUNCTION nf_q9(x NUMBER) RETURNS NUMBER AS 't.\"low\"'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 1 | invalid identifier '\"low\"'",
            "CREATE OR REPLACE FUNCTION nf_q10(x NUMBER) RETURNS NUMBER AS '\"low\" + x'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 1 | invalid identifier '\"low\"'",
            "CREATE OR REPLACE FUNCTION nf_q11(\"low\" NUMBER) RETURNS NUMBER AS '\"low\" + 1'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 1 | invalid identifier '\"Low\"'",
            "CREATE OR REPLACE FUNCTION nf_q12(\"low\" NUMBER) RETURNS NUMBER AS '\"Low\"'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 5 | invalid identifier '\"low\"'",
            "CREATE OR REPLACE FUNCTION nf_q13(\"low\" NUMBER) RETURNS NUMBER AS 'ABS(\"low\")'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 9 | invalid identifier '\"low\"'",
            "CREATE OR REPLACE FUNCTION nf_q14(\"low\" NUMBER) RETURNS NUMBER AS '(SELECT \"low\")'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 8 | invalid identifier '\"low\"'",
            "CREATE OR REPLACE FUNCTION nf_q15(\"low\" NUMBER) RETURNS TABLE (y NUMBER) AS 'SELECT \"low\"'");
        assertAnswer(
            "Function NF_Q16 successfully created.",
            "CREATE OR REPLACE PROCEDURE nf_q16(\"low\" NUMBER) RETURNS NUMBER LANGUAGE SQL AS 'BEGIN RETURN "
                + "\"low\"; END'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '\"low\"'",
            "SELECT \"low\" FROM (SELECT 1 a)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '\"Low x\"'",
            "SELECT \"Low x\" + 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'T.\"low\"'",
            "SELECT t.\"low\" FROM (SELECT 1 a) t");
        assertAnswer(
            "signature|(low NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|low",
            "DESCRIBE FUNCTION nf_q2(NUMBER)");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_Q2\"(\"low\" NUMBER(38,0)) | RETURNS NUMBER(38,0) | LANGUAGE SQL | "
                + "AS 'low';",
            "SELECT GET_DDL('FUNCTION', 'nf_q2(NUMBER)')");
    }

    @Test
    public void aDottedNameIsNoParameter() {
        answer(
            "CREATE OR REPLACE TABLE nf_t (a NUMBER)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 1 | invalid identifier 'T.X'",
            "CREATE OR REPLACE FUNCTION nf_r1(x NUMBER) RETURNS NUMBER AS 't.x'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 1 | invalid identifier 'NF_R2.X'",
            "CREATE OR REPLACE FUNCTION nf_r2(x NUMBER) RETURNS NUMBER AS 'nf_r2.x'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 1 | invalid identifier 'T.X'",
            "CREATE OR REPLACE FUNCTION nf_r3(x NUMBER) RETURNS NUMBER AS 't.x + 1'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 5 | invalid identifier 'T.X'",
            "CREATE OR REPLACE FUNCTION nf_r4(x NUMBER) RETURNS NUMBER AS 'ABS(t.x)'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 1 | invalid identifier 'A.B.C'",
            "CREATE OR REPLACE FUNCTION nf_r5(x NUMBER) RETURNS NUMBER AS 'a.b.c'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 9 | invalid identifier 'T.X'",
            "CREATE OR REPLACE FUNCTION nf_r6(x NUMBER) RETURNS NUMBER AS '(SELECT t.x)'");
        assertAnswer(
            "Function NF_R7 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_r7(x NUMBER) RETURNS NUMBER AS '(SELECT MAX(t.a) FROM nf_t t)'");
        assertAnswer(
            "Function NF_R8 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_r8(x VARIANT) RETURNS VARIANT AS 'x:a'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 1 | invalid identifier 'X.A'",
            "CREATE OR REPLACE FUNCTION nf_r8b(x VARIANT) RETURNS VARIANT AS 'x.a'");
    }
}
