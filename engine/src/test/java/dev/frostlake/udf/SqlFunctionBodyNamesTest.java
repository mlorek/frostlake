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
 * The bare words of a SQL function's expression body that are no names of its signature. The UNIT of a date or
 * time function - {@code DATEADD(day, 1, t)}, {@code DATE_TRUNC(month, d)}, {@code LAST_DAY(d, week)},
 * {@code DATE_PART(dow, d)}, the abbreviations too - is a word the function reads, and such a function is
 * created and answers as its query-bodied twin does. A unit that is no part is refused at CREATE in the
 * function's own sentence, and one that is a parameter's name - a unit being an identifier or a string literal,
 * never a value - as {@code Date/time component [CORRELATION(NULL) ]for function DATEADD needs to be an
 * identifier or a string literal.} The parameters a lambda binds - {@code FILTER(a, x -> x > 1)},
 * {@code TRANSFORM(a, e INT -> e * 10)}, {@code REDUCE(a, 0, (acc, v) -> acc + v)} - are names inside that
 * lambda's body, beside the signature's; where the two share a name the signature's parameter is the one the
 * body reads. Every other unknown name is still {@code invalid identifier}, in a lambda's body as outside one.
 * Every answer is live-verified.
 */
public class SqlFunctionBodyNamesTest extends MeasuredCellsTestSupport {

    @Test
    public void datePartsAndLambdasInAnExpressionBody() {
        assertAnswer(
            "Function D_A1 successfully created.",
            "CREATE OR REPLACE FUNCTION d_a1(t TIMESTAMP_NTZ) RETURNS TIMESTAMP_NTZ AS 'DATEADD(day, 1, t)'");
        assertAnswer(
            "Function D_A2 successfully created.",
            "CREATE OR REPLACE FUNCTION d_a2() RETURNS NUMBER AS 'DATEDIFF(day, ''2024-01-01''::DATE, "
                + "''2024-01-05''::DATE)'");
        assertAnswer(
            "Function D_A3 successfully created.",
            "CREATE OR REPLACE FUNCTION d_a3() RETURNS DATE AS 'DATE_TRUNC(month, ''2024-01-15''::DATE)'");
        assertAnswer(
            "Function D_A4 successfully created.",
            "CREATE OR REPLACE FUNCTION d_a4() RETURNS DATE AS 'LAST_DAY(''2024-01-15''::DATE, month)'");
        assertAnswer(
            "Function D_A5 successfully created.",
            "CREATE OR REPLACE FUNCTION d_a5(t TIME) RETURNS NUMBER AS 'DATE_PART(second, t)'");
        assertAnswer(
            "Function D_A6 successfully created.",
            "CREATE OR REPLACE FUNCTION d_a6(t TIME) RETURNS TIME AS 'TIMEADD(hour, 1, t)'");
        assertAnswer(
            "Function D_A7 successfully created.",
            "CREATE OR REPLACE FUNCTION d_a7(a TIMESTAMP_NTZ, b TIMESTAMP_NTZ) RETURNS NUMBER AS "
                + "'TIMESTAMPDIFF(minute, a, b)'");
        assertAnswer(
            "Function D_H1 successfully created.",
            "CREATE OR REPLACE FUNCTION d_h1(a ARRAY) RETURNS ARRAY AS 'FILTER(a, x -> x > 1)'");
        assertAnswer(
            "Function D_H2 successfully created.",
            "CREATE OR REPLACE FUNCTION d_h2(a ARRAY) RETURNS ARRAY AS 'TRANSFORM(a, e -> e * 10)'");
        assertAnswer(
            "Function D_OK1 successfully created.",
            "CREATE OR REPLACE FUNCTION d_ok1(t TIMESTAMP_NTZ) RETURNS TIMESTAMP_NTZ AS 'DATEADD(''day'', 1, t)'");
        assertAnswer(
            "Function D_OK2 successfully created.",
            "CREATE OR REPLACE FUNCTION d_ok2() RETURNS NUMBER AS 'EXTRACT(day FROM ''2024-01-15''::DATE)'");
        assertAnswer(
            "Function D_OK3 successfully created.",
            "CREATE OR REPLACE FUNCTION d_ok3(t TIMESTAMP_NTZ) RETURNS TIMESTAMP_NTZ AS 'SELECT DATEADD(day, 1, "
                + "t)'");
        assertAnswer(
            "2024_01_02 00;00;00.000|2024_01_02 00;00;00.000|2024_01_02 00;00;00.000",
            "SELECT REPLACE(REPLACE(d_a1('2024-01-01'::TIMESTAMP_NTZ)::VARCHAR, '-', '_'), ':', ';'), "
                + "REPLACE(REPLACE(d_ok1('2024-01-01'::TIMESTAMP_NTZ)::VARCHAR, '-', '_'), ':', ';'), "
                + "REPLACE(REPLACE(d_ok3('2024-01-01'::TIMESTAMP_NTZ)::VARCHAR, '-', '_'), ':', ';')");
        assertAnswer(
            "4|2024_01_01|2024_01_31|15",
            "SELECT d_a2(), REPLACE(REPLACE(d_a3()::VARCHAR, '-', '_'), ':', ';'), "
                + "REPLACE(REPLACE(d_a4()::VARCHAR, '-', '_'), ':', ';'), d_ok2()");
        assertAnswer(
            "12|11;11;12|90",
            "SELECT d_a5('10:11:12'::TIME), REPLACE(REPLACE(d_a6('10:11:12'::TIME)::VARCHAR, '-', '_'), ':', ';'),"
                + " d_a7('2024-01-01 00:00:00'::TIMESTAMP_NTZ, '2024-01-01 01:30:00'::TIMESTAMP_NTZ)");
        assertAnswer(
            "[2,3]|[1.000000000000000e+01,2.000000000000000e+01,3.000000000000000e+01]",
            "SELECT d_h1(ARRAY_CONSTRUCT(1, 2, 3)), d_h2(ARRAY_CONSTRUCT(1, 2, 3))");
        assertAnswer(
            "Function D_B1 successfully created.",
            "CREATE OR REPLACE FUNCTION d_b1(d DATE) RETURNS DATE AS 'DATEADD(dd, 1, d)'");
        assertAnswer(
            "Function D_B2 successfully created.",
            "CREATE OR REPLACE FUNCTION d_b2(d DATE) RETURNS DATE AS 'DATEADD(mm, 1, d)'");
        assertAnswer(
            "Function D_B3 successfully created.",
            "CREATE OR REPLACE FUNCTION d_b3(d DATE) RETURNS NUMBER AS 'DATE_PART(dow, d)'");
        assertAnswer(
            "Function D_B4 successfully created.",
            "CREATE OR REPLACE FUNCTION d_b4(d DATE) RETURNS DATE AS 'LAST_DAY(d, week)'");
        assertAnswer(
            "Function D_B5 successfully created.",
            "CREATE OR REPLACE FUNCTION d_b5(t TIMESTAMP_NTZ) RETURNS TIMESTAMP_NTZ AS 'TIMESTAMPADD(quarter, 1, "
                + "t)'");
        assertAnswer(
            "Function D_B6 successfully created.",
            "CREATE OR REPLACE FUNCTION d_b6(d DATE) RETURNS NUMBER AS 'DATEDIFF(year, d, CURRENT_DATE()) * 0'");
        assertAnswer(
            "Function D_B7 successfully created.",
            "CREATE OR REPLACE FUNCTION d_b7(t TIMESTAMP_NTZ) RETURNS NUMBER AS 'DATE_PART(epoch_second, t)'");
        assertAnswer(
            "2024_02_01|2024_02_29|3|2024_02_04",
            "SELECT REPLACE(REPLACE(d_b1('2024-01-31'::DATE)::VARCHAR, '-', '_'), ':', ';'), "
                + "REPLACE(REPLACE(d_b2('2024-01-31'::DATE)::VARCHAR, '-', '_'), ':', ';'), d_b3('2024-01-31'::DATE),"
                + " REPLACE(REPLACE(d_b4('2024-01-31'::DATE)::VARCHAR, '-', '_'), ':', ';')");
        assertAnswer(
            "2024_04_30 10;00;00.000|0|86400",
            "SELECT REPLACE(REPLACE(d_b5('2024-01-31 10:00:00'::TIMESTAMP_NTZ)::VARCHAR, '-', '_'), ':', ';'), "
                + "d_b6('2020-01-31'::DATE), d_b7('1970-01-02 00:00:00'::TIMESTAMP_NTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: Date/time component [CORRELATION(NULL) ]for function DATEADD needs to "
                + "be an identifier or a string literal.",
            "CREATE OR REPLACE FUNCTION d_c1(day NUMBER) RETURNS DATE AS 'DATEADD(day, day, ''2024-01-01''::DATE)'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function D_C1.",
            "SELECT REPLACE(REPLACE(d_c1(5)::VARCHAR, '-', '_'), ':', ';')");
        assertAnswer(
            "REFUSED SQL compilation error: Date/time component [CORRELATION(NULL) ]for function DATE_TRUNC needs "
                + "to be an identifier or a string literal.",
            "CREATE OR REPLACE FUNCTION d_c2(month DATE) RETURNS DATE AS 'DATE_TRUNC(month, month)'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function D_C2.",
            "SELECT REPLACE(REPLACE(d_c2('2024-03-15'::DATE)::VARCHAR, '-', '_'), ':', ';')");
        assertAnswer(
            "Function D_H3 successfully created.",
            "CREATE OR REPLACE FUNCTION d_h3(a ARRAY, k NUMBER) RETURNS ARRAY AS 'FILTER(a, x -> x > k)'");
        assertAnswer(
            "[2,3]",
            "SELECT d_h3(ARRAY_CONSTRUCT(1, 2, 3), 1)");
        assertAnswer(
            "Function D_H4 successfully created.",
            "CREATE OR REPLACE FUNCTION d_h4(a ARRAY, x NUMBER) RETURNS ARRAY AS 'FILTER(a, x -> x > 1)'");
        assertAnswer(
            "[1,2,3]",
            "SELECT d_h4(ARRAY_CONSTRUCT(1, 2, 3), 99)");
        assertAnswer(
            "REFUSED Declared return type 'NUMBER(38,0)' is incompatible with actual return type 'FLOAT'",
            "CREATE OR REPLACE FUNCTION d_h5(a ARRAY) RETURNS NUMBER AS 'REDUCE(a, 0, (acc, v) -> acc + v)'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function D_H5.",
            "SELECT d_h5(ARRAY_CONSTRUCT(1, 2, 3))");
        assertAnswer(
            "Function D_H6 successfully created.",
            "CREATE OR REPLACE FUNCTION d_h6(a ARRAY) RETURNS ARRAY AS 'TRANSFORM(a, e INT -> e * 10)'");
        assertAnswer(
            "[10,20,30]",
            "SELECT d_h6(ARRAY_CONSTRUCT(1, 2, 3))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 1 | invalid identifier 'NOSUCH_NAME'",
            "CREATE OR REPLACE FUNCTION d_x1() RETURNS NUMBER AS 'nosuch_name + 1'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 16 | invalid identifier 'NOSUCH_NAME'",
            "CREATE OR REPLACE FUNCTION d_x2(a ARRAY) RETURNS ARRAY AS 'FILTER(a, x -> nosuch_name > 1)'");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [NOSUCH_PART] for parameter 'DATE_PART date/time part'",
            "CREATE OR REPLACE FUNCTION d_x3(t TIME) RETURNS NUMBER AS 'DATE_PART(nosuch_part, t)'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 12 | invalid identifier 'DAY'",
            "CREATE OR REPLACE FUNCTION d_x4(d DATE) RETURNS DATE AS 'DATEADD(1, day, d)'");
        assertAnswer(
            "Function D_S1 successfully created.",
            "CREATE OR REPLACE FUNCTION d_s1(t TIMESTAMP_NTZ) RETURNS TIMESTAMP_NTZ AS $$ BEGIN RETURN "
                + "DATEADD(day, 1, t); END $$");
        assertAnswer(
            "2024_01_02 00;00;00.000",
            "SELECT REPLACE(REPLACE(d_s1('2024-01-01'::TIMESTAMP_NTZ)::VARCHAR, '-', '_'), ':', ';')");
        assertAnswer(
            "Function D_T1 successfully created.",
            "CREATE OR REPLACE FUNCTION d_t1(d DATE) RETURNS TABLE (x DATE) AS 'SELECT DATEADD(day, 1, d)'");
        assertAnswer(
            "2024_01_02",
            "SELECT REPLACE(REPLACE(x::VARCHAR, '-', '_'), ':', ';') FROM TABLE(d_t1('2024-01-01'::DATE))");
    }

    @Test
    public void unitWordsParameterNamesAndLambdaScopes() {
        assertAnswer(
            "Function E_R1 successfully created.",
            "CREATE OR REPLACE FUNCTION e_r1(a ARRAY) RETURNS FLOAT AS 'REDUCE(a, 0, (acc, v) -> acc + v)'");
        assertAnswer(
            "6",
            "SELECT e_r1(ARRAY_CONSTRUCT(1, 2, 3))::VARCHAR");
        assertAnswer(
            "Function E_R2 successfully created.",
            "CREATE OR REPLACE FUNCTION e_r2(a ARRAY, k NUMBER) RETURNS ARRAY AS 'TRANSFORM(a, e -> e + k)'");
        assertAnswer(
            "[1.100000000000000e+01,1.200000000000000e+01]",
            "SELECT e_r2(ARRAY_CONSTRUCT(1, 2), 10)");
        assertAnswer(
            "Function E_R3 successfully created.",
            "CREATE OR REPLACE FUNCTION e_r3(a ARRAY, x NUMBER) RETURNS ARRAY AS 'TRANSFORM(a, x -> x * 2)'");
        assertAnswer(
            "[14,14]",
            "SELECT e_r3(ARRAY_CONSTRUCT(1, 2), 7)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [NOSUCH_PART] for parameter 'DATE_PART date/time part'",
            "SELECT DATE_PART(nosuch_part, CURRENT_TIME())");
        assertAnswer(
            "REFUSED SQL compilation error: ['NOSUCH_PART'] is not a valid date/time component for function "
                + "DATEADD.",
            "SELECT DATEADD(nosuch_part, 1, '2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: ['NOSUCH_PART'] is not a valid date/time component for function "
                + "DATEADD.",
            "CREATE OR REPLACE FUNCTION e_x1(d DATE) RETURNS DATE AS 'DATEADD(nosuch_part, 1, d)'");
        assertAnswer(
            "REFUSED SQL compilation error: ['NOSUCH_PART'] is not a valid date/time component for function "
                + "DATE_TRUNC.",
            "CREATE OR REPLACE FUNCTION e_x2(d DATE) RETURNS DATE AS 'DATE_TRUNC(nosuch_part, d)'");
        assertAnswer(
            "REFUSED SQL compilation error: Date/time component [CORRELATION(NULL) ]for function DATEADD needs to "
                + "be an identifier or a string literal.",
            "CREATE OR REPLACE FUNCTION e_x3(d DATE, u VARCHAR) RETURNS DATE AS 'DATEADD(u, 1, d)'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function E_X3.",
            "SELECT REPLACE(e_x3('2024-01-01'::DATE, 'day')::VARCHAR, '-', '_')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 23 | Invalid argument types for function "
                + "'||': (ARRAY, ARRAY)",
            "CREATE OR REPLACE FUNCTION e_x4(a ARRAY) RETURNS ARRAY AS 'FILTER(a, x -> x > 1) || FILTER(a, y -> y "
                + "> 2)'");
        assertAnswer(
            "Function E_X5 successfully created.",
            "CREATE OR REPLACE FUNCTION e_x5(a ARRAY) RETURNS ARRAY AS 'ARRAY_CAT(FILTER(a, x -> x > 1), FILTER(a,"
                + " y -> y > 2))'");
        assertAnswer(
            "[2,3,3]",
            "SELECT e_x5(ARRAY_CONSTRUCT(1, 2, 3))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 26 | invalid identifier 'Y'",
            "CREATE OR REPLACE FUNCTION e_x6(a ARRAY) RETURNS ARRAY AS 'FILTER(a, x -> x > 1 AND y > 2)'");
        assertAnswer(
            "Function E_X7 successfully created.",
            "CREATE OR REPLACE FUNCTION e_x7(day NUMBER) RETURNS NUMBER AS 'day + 1'");
        assertAnswer(
            "5",
            "SELECT e_x7(4)");
    }
}
