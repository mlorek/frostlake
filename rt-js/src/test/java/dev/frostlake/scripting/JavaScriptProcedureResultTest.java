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
package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * What a CALL of a JavaScript procedure answers: one row in a column named after the procedure and typed by
 * its RETURNS - a NULL when the handler returns {@code null}, {@code undefined} or nothing - holding the
 * handler's value as that type. A text type takes {@code String(value)}, a FLOAT {@code Number(value)} and a
 * BOOLEAN the value's truthiness; a VARIANT takes the value's JSON; an ARRAY only an array and an OBJECT only a
 * plain object - {@code JavaScript execution error: Return value is not an ARRAY} -, an array's
 * {@code undefined} element staying one of its SQL NULLs; a DATE and a TIMESTAMP take a {@code Date} or a number
 * of milliseconds - {@code Incorrect Timestamp returned} for anything else - and a BINARY a {@code Uint8Array}. A
 * text longer than a declared VARCHAR(n) and bytes longer than a declared BINARY(n) are refused, and the column
 * keeps the declared width. For a semi-structured type an {@code undefined} is a SQL NULL, while a {@code null}
 * is a JSON null for a VARIANT and no array and no object; inside the answer a number with a fraction, one from
 * 1e21 on, a NaN and an infinity are DOUBLEs, a {@code Uint8Array} is its hex text, a function an empty object,
 * and an array's trailing {@code undefined} elements are dropped. Every answer is live-verified; the cells that
 * turn on the session's time zone are asked in {@code JavaScriptProcedureDateResultTest}.
 */
public class JavaScriptProcedureResultTest extends MeasuredCellsTestSupport {

    @Test
    public void theAnswerIsTypedByReturns() {
        assertAnswer(
            "Function J00 successfully created.",
            "CREATE OR REPLACE PROCEDURE j00() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return 1.5; $$");
        assertAnswer(
            "1.5",
            "CALL j00()");
        shape(
            "CALL j00()");
        assertAnswer(
            "FLOAT[DOUBLE] / 1.5",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J01 successfully created.",
            "CREATE OR REPLACE PROCEDURE j01() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return '1.5'; $$");
        assertAnswer(
            "1.5",
            "CALL j01()");
        shape(
            "CALL j01()");
        assertAnswer(
            "FLOAT[DOUBLE] / 1.5",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J02 successfully created.",
            "CREATE OR REPLACE PROCEDURE j02() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return 1; $$");
        assertAnswer(
            "1.0",
            "CALL j02()");
        shape(
            "CALL j02()");
        assertAnswer(
            "FLOAT[DOUBLE] / 1",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J03 successfully created.",
            "CREATE OR REPLACE PROCEDURE j03() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return true; $$");
        assertAnswer(
            "1.0",
            "CALL j03()");
        shape(
            "CALL j03()");
        assertAnswer(
            "FLOAT[DOUBLE] / 1",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J04 successfully created.",
            "CREATE OR REPLACE PROCEDURE j04() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return null; $$");
        assertAnswer(
            "null",
            "CALL j04()");
        shape(
            "CALL j04()");
        assertAnswer(
            "FLOAT[DOUBLE] / null",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J05 successfully created.",
            "CREATE OR REPLACE PROCEDURE j05() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ var x = 1; $$");
        assertAnswer(
            "null",
            "CALL j05()");
        shape(
            "CALL j05()");
        assertAnswer(
            "FLOAT[DOUBLE] / null",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J06 successfully created.",
            "CREATE OR REPLACE PROCEDURE j06() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return NaN; $$");
        assertAnswer(
            "NaN",
            "CALL j06()");
        shape(
            "CALL j06()");
        assertAnswer(
            "FLOAT[DOUBLE] / NaN",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J07 successfully created.",
            "CREATE OR REPLACE PROCEDURE j07() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return 'x'; $$");
        assertAnswer(
            "NaN",
            "CALL j07()");
        shape(
            "CALL j07()");
        assertAnswer(
            "FLOAT[DOUBLE] / NaN",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J08 successfully created.",
            "CREATE OR REPLACE PROCEDURE j08() RETURNS BOOLEAN LANGUAGE JAVASCRIPT AS $$ return true; $$");
        assertAnswer(
            "true",
            "CALL j08()");
        assertShape(
            "J08:BOOLEAN",
            "CALL j08()");
        assertAnswer(
            "BOOLEAN[SB1] / true",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J09 successfully created.",
            "CREATE OR REPLACE PROCEDURE j09() RETURNS BOOLEAN LANGUAGE JAVASCRIPT AS $$ return 1; $$");
        assertAnswer(
            "true",
            "CALL j09()");
        assertShape(
            "J09:BOOLEAN",
            "CALL j09()");
        assertAnswer(
            "BOOLEAN[SB1] / true",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J10 successfully created.",
            "CREATE OR REPLACE PROCEDURE j10() RETURNS BOOLEAN LANGUAGE JAVASCRIPT AS $$ return 'true'; $$");
        assertAnswer(
            "true",
            "CALL j10()");
        assertShape(
            "J10:BOOLEAN",
            "CALL j10()");
        assertAnswer(
            "BOOLEAN[SB1] / true",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J11 successfully created.",
            "CREATE OR REPLACE PROCEDURE j11() RETURNS BOOLEAN LANGUAGE JAVASCRIPT AS $$ return 0; $$");
        assertAnswer(
            "false",
            "CALL j11()");
        assertShape(
            "J11:BOOLEAN",
            "CALL j11()");
        assertAnswer(
            "BOOLEAN[SB1] / false",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J12 successfully created.",
            "CREATE OR REPLACE PROCEDURE j12() RETURNS BOOLEAN LANGUAGE JAVASCRIPT AS $$ return null; $$");
        assertAnswer(
            "null",
            "CALL j12()");
        assertShape(
            "J12:BOOLEAN",
            "CALL j12()");
        assertAnswer(
            "BOOLEAN[SB1] / null",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J13 successfully created.",
            "CREATE OR REPLACE PROCEDURE j13() RETURNS BOOLEAN LANGUAGE JAVASCRIPT AS $$ return 'maybe'; $$");
        assertAnswer(
            "true",
            "CALL j13()");
        assertShape(
            "J13:BOOLEAN",
            "CALL j13()");
        assertAnswer(
            "BOOLEAN[SB1] / true",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J14 successfully created.",
            "CREATE OR REPLACE PROCEDURE j14() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return 1.5; $$");
        assertAnswer(
            "1.5",
            "CALL j14()");
        assertShape(
            "J14:VARCHAR(134217728)",
            "CALL j14()");
        assertAnswer(
            "VARCHAR(134217728)[LOB] / 1.5",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J15 successfully created.",
            "CREATE OR REPLACE PROCEDURE j15() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return true; $$");
        assertAnswer(
            "true",
            "CALL j15()");
        assertShape(
            "J15:VARCHAR(134217728)",
            "CALL j15()");
        assertAnswer(
            "VARCHAR(134217728)[LOB] / true",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J16 successfully created.",
            "CREATE OR REPLACE PROCEDURE j16() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return {a: 1}; $$");
        assertAnswer(
            "[object Object]",
            "CALL j16()");
        assertShape(
            "J16:VARCHAR(134217728)",
            "CALL j16()");
        assertAnswer(
            "VARCHAR(134217728)[LOB] / [object Object]",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J17 successfully created.",
            "CREATE OR REPLACE PROCEDURE j17() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return [1, 2]; $$");
        assertAnswer(
            "1,2",
            "CALL j17()");
        assertShape(
            "J17:VARCHAR(134217728)",
            "CALL j17()");
        assertAnswer(
            "VARCHAR(134217728)[LOB] / 1,2",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J18 successfully created.",
            "CREATE OR REPLACE PROCEDURE j18() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return new "
                + "Date(Date.UTC(2024, 0, 2)); $$");
        answer(
            "CALL j18()");
        assertShape(
            "J18:VARCHAR(134217728)",
            "CALL j18()");
        answer(
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J19 successfully created.",
            "CREATE OR REPLACE PROCEDURE j19() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return null; $$");
        assertAnswer(
            "null",
            "CALL j19()");
        assertShape(
            "J19:VARCHAR(134217728)",
            "CALL j19()");
        assertAnswer(
            "VARCHAR(134217728)[LOB] / null",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J20 successfully created.",
            "CREATE OR REPLACE PROCEDURE j20() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return undefined; $$");
        assertAnswer(
            "null",
            "CALL j20()");
        assertShape(
            "J20:VARCHAR(134217728)",
            "CALL j20()");
        assertAnswer(
            "VARCHAR(134217728)[LOB] / null",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J21 successfully created.",
            "CREATE OR REPLACE PROCEDURE j21() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return 'abc'; $$");
        assertAnswer(
            "abc",
            "CALL j21()");
        assertShape(
            "J21:VARCHAR(134217728)",
            "CALL j21()");
        assertAnswer(
            "VARCHAR(134217728)[LOB] / abc",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J22 successfully created.",
            "CREATE OR REPLACE PROCEDURE j22() RETURNS VARCHAR(2) LANGUAGE JAVASCRIPT AS $$ return 'abc'; $$");
        assertAnswer(
            "REFUSED String 'abc' is too long and would be truncated",
            "CALL j22()");
        assertShape(
            "REFUSED String 'abc' is too long and would be truncated",
            "CALL j22()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J23 successfully created.",
            "CREATE OR REPLACE PROCEDURE j23() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return {a: 1, b: [1, "
                + "2]}; $$");
        assertAnswer(
            "{\"a\":1,\"b\":[1,2]}",
            "CALL j23()");
        assertShape(
            "J23:VARIANT",
            "CALL j23()");
        assertAnswer(
            "VARIANT[LOB] / {\"a\":1,\"b\":[1,2]}",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J24 successfully created.",
            "CREATE OR REPLACE PROCEDURE j24() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return 1; $$");
        assertAnswer(
            "1",
            "CALL j24()");
        assertShape(
            "J24:VARIANT",
            "CALL j24()");
        assertAnswer(
            "VARIANT[LOB] / 1",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J25 successfully created.",
            "CREATE OR REPLACE PROCEDURE j25() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return 'a'; $$");
        assertAnswer(
            "a",
            "CALL j25()");
        assertShape(
            "J25:VARIANT",
            "CALL j25()");
        assertAnswer(
            "VARIANT[LOB] / a",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J26 successfully created.",
            "CREATE OR REPLACE PROCEDURE j26() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return null; $$");
        assertAnswer(
            "null",
            "CALL j26()");
        assertShape(
            "J26:VARIANT",
            "CALL j26()");
        assertAnswer(
            "VARIANT[LOB] / null",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J27 successfully created.",
            "CREATE OR REPLACE PROCEDURE j27() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return undefined; $$");
        assertAnswer(
            "null",
            "CALL j27()");
        assertShape(
            "J27:VARIANT",
            "CALL j27()");
        assertAnswer(
            "VARIANT[LOB] / null",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J28 successfully created.",
            "CREATE OR REPLACE PROCEDURE j28() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return [1, null]; $$");
        assertAnswer(
            "[1,null]",
            "CALL j28()");
        assertShape(
            "J28:VARIANT",
            "CALL j28()");
        assertAnswer(
            "VARIANT[LOB] / [1,null]",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J29 successfully created.",
            "CREATE OR REPLACE PROCEDURE j29() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return true; $$");
        assertAnswer(
            "true",
            "CALL j29()");
        assertShape(
            "J29:VARIANT",
            "CALL j29()");
        assertAnswer(
            "VARIANT[LOB] / true",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J30 successfully created.",
            "CREATE OR REPLACE PROCEDURE j30() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return new "
                + "Date(Date.UTC(2024, 0, 2)); $$");
        answer(
            "CALL j30()");
        assertShape(
            "J30:VARIANT",
            "CALL j30()");
        answer(
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J31 successfully created.",
            "CREATE OR REPLACE PROCEDURE j31() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$ return [1, 2]; $$");
        assertAnswer(
            "[1,2]",
            "CALL j31()");
        assertShape(
            "J31:ARRAY",
            "CALL j31()");
        assertAnswer(
            "ARRAY[LOB] / [1,2]",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J32 successfully created.",
            "CREATE OR REPLACE PROCEDURE j32() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$ return 1; $$");
        assertAnswer(
            "REFUSED JavaScript execution error: Return value is not an ARRAY",
            "CALL j32()");
        assertShape(
            "REFUSED JavaScript execution error: Return value is not an ARRAY",
            "CALL j32()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J33 successfully created.",
            "CREATE OR REPLACE PROCEDURE j33() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$ return 'a'; $$");
        assertAnswer(
            "REFUSED JavaScript execution error: Return value is not an ARRAY",
            "CALL j33()");
        assertShape(
            "REFUSED JavaScript execution error: Return value is not an ARRAY",
            "CALL j33()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J34 successfully created.",
            "CREATE OR REPLACE PROCEDURE j34() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$ return {a: 1}; $$");
        assertAnswer(
            "REFUSED JavaScript execution error: Return value is not an ARRAY",
            "CALL j34()");
        assertShape(
            "REFUSED JavaScript execution error: Return value is not an ARRAY",
            "CALL j34()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J35 successfully created.",
            "CREATE OR REPLACE PROCEDURE j35() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$ return null; $$");
        assertAnswer(
            "REFUSED JavaScript execution error: Return value is not an ARRAY",
            "CALL j35()");
        assertShape(
            "REFUSED JavaScript execution error: Return value is not an ARRAY",
            "CALL j35()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J36 successfully created.",
            "CREATE OR REPLACE PROCEDURE j36() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$ return {a: 1}; $$");
        assertAnswer(
            "{\"a\":1}",
            "CALL j36()");
        assertShape(
            "J36:OBJECT",
            "CALL j36()");
        assertAnswer(
            "OBJECT[LOB] / {\"a\":1}",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J37 successfully created.",
            "CREATE OR REPLACE PROCEDURE j37() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$ return [1]; $$");
        assertAnswer(
            "REFUSED JavaScript execution error: Return value is not an OBJECT",
            "CALL j37()");
        assertShape(
            "REFUSED JavaScript execution error: Return value is not an OBJECT",
            "CALL j37()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J38 successfully created.",
            "CREATE OR REPLACE PROCEDURE j38() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$ return 1; $$");
        assertAnswer(
            "REFUSED JavaScript execution error: Return value is not an OBJECT",
            "CALL j38()");
        assertShape(
            "REFUSED JavaScript execution error: Return value is not an OBJECT",
            "CALL j38()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J39 successfully created.",
            "CREATE OR REPLACE PROCEDURE j39() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$ return null; $$");
        assertAnswer(
            "REFUSED JavaScript execution error: Return value is not an OBJECT",
            "CALL j39()");
        assertShape(
            "REFUSED JavaScript execution error: Return value is not an OBJECT",
            "CALL j39()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J40 successfully created.",
            "CREATE OR REPLACE PROCEDURE j40() RETURNS DATE LANGUAGE JAVASCRIPT AS $$ return new "
                + "Date(Date.UTC(2024, 0, 2)); $$");
        answer(
            "CALL j40()");
        assertShape(
            "J40:DATE",
            "CALL j40()");
        answer(
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J41 successfully created.",
            "CREATE OR REPLACE PROCEDURE j41() RETURNS DATE LANGUAGE JAVASCRIPT AS $$ return '2024-01-02'; $$");
        assertAnswer(
            "REFUSED JavaScript execution error: Incorrect Timestamp returned",
            "CALL j41()");
        assertShape(
            "REFUSED JavaScript execution error: Incorrect Timestamp returned",
            "CALL j41()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J42 successfully created.",
            "CREATE OR REPLACE PROCEDURE j42() RETURNS DATE LANGUAGE JAVASCRIPT AS $$ return 5; $$");
        answer(
            "CALL j42()");
        assertShape(
            "J42:DATE",
            "CALL j42()");
        answer(
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J43 successfully created.",
            "CREATE OR REPLACE PROCEDURE j43() RETURNS DATE LANGUAGE JAVASCRIPT AS $$ return null; $$");
        assertAnswer(
            "null",
            "CALL j43()");
        assertShape(
            "J43:DATE",
            "CALL j43()");
        assertAnswer(
            "DATE[SB4] / null",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J44 successfully created.",
            "CREATE OR REPLACE PROCEDURE j44() RETURNS DATE LANGUAGE JAVASCRIPT AS $$ return 'x'; $$");
        assertAnswer(
            "REFUSED JavaScript execution error: Incorrect Timestamp returned",
            "CALL j44()");
        assertShape(
            "REFUSED JavaScript execution error: Incorrect Timestamp returned",
            "CALL j44()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J45 successfully created.",
            "CREATE OR REPLACE PROCEDURE j45() RETURNS TIMESTAMP_NTZ LANGUAGE JAVASCRIPT AS $$ return new "
                + "Date(Date.UTC(2024, 0, 2, 3, 4, 5, 678)); $$");
        answer(
            "CALL j45()");
        assertShape(
            "J45:TIMESTAMP_NTZ",
            "CALL j45()");
        answer(
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J46 successfully created.",
            "CREATE OR REPLACE PROCEDURE j46() RETURNS TIMESTAMP_NTZ LANGUAGE JAVASCRIPT AS $$ return '2024-01-02 "
                + "03:04:05'; $$");
        assertAnswer(
            "REFUSED JavaScript execution error: Incorrect Timestamp returned",
            "CALL j46()");
        assertShape(
            "REFUSED JavaScript execution error: Incorrect Timestamp returned",
            "CALL j46()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J47 successfully created.",
            "CREATE OR REPLACE PROCEDURE j47() RETURNS TIMESTAMP_LTZ LANGUAGE JAVASCRIPT AS $$ return new "
                + "Date(Date.UTC(2024, 0, 2, 3, 4, 5)); $$");
        answer(
            "CALL j47()");
        assertShape(
            "J47:TIMESTAMP_LTZ",
            "CALL j47()");
        answer(
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J48 successfully created.",
            "CREATE OR REPLACE PROCEDURE j48() RETURNS BINARY LANGUAGE JAVASCRIPT AS $$ return new "
                + "Uint8Array([171, 205]); $$");
        assertAnswer(
            "ABCD",
            "CALL j48()");
        assertShape(
            "J48:BINARY",
            "CALL j48()");
        assertAnswer(
            "BINARY(67108864)[LOB] / ABCD",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J49 successfully created.",
            "CREATE OR REPLACE PROCEDURE j49() RETURNS BINARY LANGUAGE JAVASCRIPT AS $$ return 'ABCD'; $$");
        assertAnswer(
            "REFUSED JavaScript execution error: Return value is not a BINARY",
            "CALL j49()");
        assertShape(
            "REFUSED JavaScript execution error: Return value is not a BINARY",
            "CALL j49()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J50 successfully created.",
            "CREATE OR REPLACE PROCEDURE j50() RETURNS BINARY LANGUAGE JAVASCRIPT AS $$ return null; $$");
        assertAnswer(
            "null",
            "CALL j50()");
        assertShape(
            "J50:BINARY",
            "CALL j50()");
        assertAnswer(
            "BINARY(67108864)[LOB] / null",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "REFUSED Language JAVASCRIPT does not support type 'NUMBER(38,0)' for argument or return type.",
            "CREATE OR REPLACE PROCEDURE j51() RETURNS NUMBER LANGUAGE JAVASCRIPT AS $$ return 7; $$");
        assertAnswer(
            "Function J52 successfully created.",
            "CREATE OR REPLACE PROCEDURE j52() RETURNS STRING LANGUAGE JAVASCRIPT AS $$ return 7; $$");
        assertAnswer(
            "7",
            "CALL j52()");
        assertShape(
            "J52:VARCHAR(134217728)",
            "CALL j52()");
        assertAnswer(
            "VARCHAR(134217728)[LOB] / 7",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J53 successfully created.",
            "CREATE OR REPLACE PROCEDURE j53() RETURNS DOUBLE LANGUAGE JAVASCRIPT AS $$ return 7; $$");
        assertAnswer(
            "7.0",
            "CALL j53()");
        shape(
            "CALL j53()");
        assertAnswer(
            "FLOAT[DOUBLE] / 7",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
    }

    @Test
    public void nullsTruthinessAndContainers() {
        assertAnswer(
            "Function XN0 successfully created.",
            "CREATE OR REPLACE PROCEDURE xn0() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return null; $$");
        assertAnswer(
            "null",
            "CALL xn0()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 81 | Invalid argument types for function "
                + "'||': (BOOLEAN, VARCHAR(3), VARCHAR(134217728), VARCHAR(3), VARCHAR(134217728))",
            "SELECT ($1 IS NULL) || ' / ' || NVL(IS_NULL_VALUE($1)::VARCHAR, 'null') || ' / ' || NVL(TYPEOF($1), "
                + "'null') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XN1 successfully created.",
            "CREATE OR REPLACE PROCEDURE xn1() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return undefined; $$");
        assertAnswer(
            "null",
            "CALL xn1()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 81 | Invalid argument types for function "
                + "'||': (BOOLEAN, VARCHAR(3), VARCHAR(134217728), VARCHAR(3), VARCHAR(134217728))",
            "SELECT ($1 IS NULL) || ' / ' || NVL(IS_NULL_VALUE($1)::VARCHAR, 'null') || ' / ' || NVL(TYPEOF($1), "
                + "'null') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XN2 successfully created.",
            "CREATE OR REPLACE PROCEDURE xn2() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return {a: null}; $$");
        assertAnswer(
            "{\"a\":null}",
            "CALL xn2()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 81 | Invalid argument types for function "
                + "'||': (BOOLEAN, VARCHAR(3), VARCHAR(134217728), VARCHAR(3), VARCHAR(134217728))",
            "SELECT ($1 IS NULL) || ' / ' || NVL(IS_NULL_VALUE($1)::VARCHAR, 'null') || ' / ' || NVL(TYPEOF($1), "
                + "'null') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XN3 successfully created.",
            "CREATE OR REPLACE PROCEDURE xn3() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return null; $$");
        assertAnswer(
            "null",
            "CALL xn3()");
        answer(
            "SELECT ($1 IS NULL) || ' / ' || NVL(IS_NULL_VALUE($1)::VARCHAR, 'null') || ' / ' || NVL(TYPEOF($1), "
                + "'null') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XB0 successfully created.",
            "CREATE OR REPLACE PROCEDURE xb0() RETURNS BOOLEAN LANGUAGE JAVASCRIPT AS $$ return 'false'; $$");
        assertAnswer(
            "true",
            "CALL xb0()");
        assertAnswer(
            "BOOLEAN[SB1] / true",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XB1 successfully created.",
            "CREATE OR REPLACE PROCEDURE xb1() RETURNS BOOLEAN LANGUAGE JAVASCRIPT AS $$ return ''; $$");
        assertAnswer(
            "false",
            "CALL xb1()");
        assertAnswer(
            "BOOLEAN[SB1] / false",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XB2 successfully created.",
            "CREATE OR REPLACE PROCEDURE xb2() RETURNS BOOLEAN LANGUAGE JAVASCRIPT AS $$ return {}; $$");
        assertAnswer(
            "true",
            "CALL xb2()");
        assertAnswer(
            "BOOLEAN[SB1] / true",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XV0 successfully created.",
            "CREATE OR REPLACE PROCEDURE xv0() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return new "
                + "Date(Date.UTC(2024, 0, 2, 3, 4, 5)); $$");
        answer(
            "CALL xv0()");
        answer(
            "SELECT TYPEOF($1) || ' / ' || $1::VARCHAR FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XV1 successfully created.",
            "CREATE OR REPLACE PROCEDURE xv1() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return {d: new "
                + "Date(Date.UTC(2024, 0, 2, 3, 4, 5)), u: undefined, f: 1.5, n: NaN}; $$");
        answer(
            "CALL xv1()");
        answer(
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XV2 successfully created.",
            "CREATE OR REPLACE PROCEDURE xv2() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return new "
                + "Uint8Array([171, 205]); $$");
        assertAnswer(
            "ABCD",
            "CALL xv2()");
        assertAnswer(
            "VARIANT[LOB] / ABCD",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XV3 successfully created.",
            "CREATE OR REPLACE PROCEDURE xv3() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return "
                + "12345678901234567890; $$");
        assertAnswer(
            "12345678901234567000",
            "CALL xv3()");
        assertAnswer(
            "VARIANT[LOB] / 12345678901234567000",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XA0 successfully created.",
            "CREATE OR REPLACE PROCEDURE xa0() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$ return [1, undefined, {a: "
                + "1}, [2]]; $$");
        assertAnswer(
            "[1,undefined,{\"a\":1},[2]]",
            "CALL xa0()");
        assertAnswer(
            "ARRAY[LOB] / [1,undefined,{\"a\":1},[2]]",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XO0 successfully created.",
            "CREATE OR REPLACE PROCEDURE xo0() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$ return {a: [1, 2], b: {c: "
                + "null}}; $$");
        assertAnswer(
            "{\"a\":[1,2],\"b\":{\"c\":null}}",
            "CALL xo0()");
        assertAnswer(
            "OBJECT[LOB] / {\"a\":[1,2],\"b\":{\"c\":null}}",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XT0 successfully created.",
            "CREATE OR REPLACE PROCEDURE xt0() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return ''; $$");
        assertAnswer(
            "0.0",
            "CALL xt0()");
        assertAnswer(
            "FLOAT[DOUBLE] / 0",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XT1 successfully created.",
            "CREATE OR REPLACE PROCEDURE xt1() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return [5]; $$");
        assertAnswer(
            "5.0",
            "CALL xt1()");
        assertAnswer(
            "FLOAT[DOUBLE] / 5",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XT2 successfully created.",
            "CREATE OR REPLACE PROCEDURE xt2() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return {}; $$");
        assertAnswer(
            "NaN",
            "CALL xt2()");
        assertAnswer(
            "FLOAT[DOUBLE] / NaN",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XT3 successfully created.",
            "CREATE OR REPLACE PROCEDURE xt3() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return Infinity; $$");
        assertAnswer(
            "Infinity",
            "CALL xt3()");
        assertAnswer(
            "FLOAT[DOUBLE] / inf",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XT4 successfully created.",
            "CREATE OR REPLACE PROCEDURE xt4() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return 1e21; $$");
        assertAnswer(
            "1e+21",
            "CALL xt4()");
        assertAnswer(
            "VARCHAR(134217728)[LOB] / 1e+21",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XT5 successfully created.",
            "CREATE OR REPLACE PROCEDURE xt5() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return 0.1 + 0.2; $$");
        assertAnswer(
            "0.30000000000000004",
            "CALL xt5()");
        assertAnswer(
            "VARCHAR(134217728)[LOB] / 0.30000000000000004",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XT6 successfully created.",
            "CREATE OR REPLACE PROCEDURE xt6() RETURNS DATE LANGUAGE JAVASCRIPT AS $$ return new Date(NaN); $$");
        assertAnswer(
            "REFUSED JavaScript execution error: Incorrect Timestamp returned",
            "CALL xt6()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XT7 successfully created.",
            "CREATE OR REPLACE PROCEDURE xt7() RETURNS TIMESTAMP_NTZ LANGUAGE JAVASCRIPT AS $$ return "
                + "1704164645678; $$");
        answer(
            "CALL xt7()");
        answer(
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function XR0 successfully created.",
            "CREATE OR REPLACE PROCEDURE xr0() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return 1.5; $$");
        assertAnswer(
            "1.5",
            "CALL xr0()");
        assertAnswer(
            "FLOAT[DOUBLE] / 1.5",
            "DECLARE r FLOAT; BEGIN CALL xr0() INTO :r; RETURN SYSTEM$TYPEOF(r) || ' / ' || r; END;");
    }

    @Test
    public void whatASemiStructuredAnswerHolds() {
        assertAnswer(
            "Function N0 successfully created.",
            "CREATE OR REPLACE PROCEDURE n0() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return null; $$");
        assertAnswer(
            "null",
            "CALL n0()");
        assertAnswer(
            "value / NULL_VALUE",
            "SELECT IFF($1 IS NULL, 'sql-null', 'value') || ' / ' || NVL(TYPEOF($1), 'no-type') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function N1 successfully created.",
            "CREATE OR REPLACE PROCEDURE n1() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return undefined; $$");
        assertAnswer(
            "null",
            "CALL n1()");
        assertAnswer(
            "sql-null / no-type",
            "SELECT IFF($1 IS NULL, 'sql-null', 'value') || ' / ' || NVL(TYPEOF($1), 'no-type') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function N2 successfully created.",
            "CREATE OR REPLACE PROCEDURE n2() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ var x = 1; $$");
        assertAnswer(
            "null",
            "CALL n2()");
        assertAnswer(
            "sql-null / no-type",
            "SELECT IFF($1 IS NULL, 'sql-null', 'value') || ' / ' || NVL(TYPEOF($1), 'no-type') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function N3 successfully created.",
            "CREATE OR REPLACE PROCEDURE n3() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$ return undefined; $$");
        assertAnswer(
            "null",
            "CALL n3()");
        assertAnswer(
            "sql-null / no-type",
            "SELECT IFF($1 IS NULL, 'sql-null', 'value') || ' / ' || NVL(TYPEOF($1), 'no-type') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function N4 successfully created.",
            "CREATE OR REPLACE PROCEDURE n4() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$ var x = 1; $$");
        assertAnswer(
            "null",
            "CALL n4()");
        assertAnswer(
            "sql-null / no-type",
            "SELECT IFF($1 IS NULL, 'sql-null', 'value') || ' / ' || NVL(TYPEOF($1), 'no-type') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function N5 successfully created.",
            "CREATE OR REPLACE PROCEDURE n5() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return {a: null}; $$");
        assertAnswer(
            "{\"a\":null}",
            "CALL n5()");
        assertAnswer(
            "value / OBJECT",
            "SELECT IFF($1 IS NULL, 'sql-null', 'value') || ' / ' || NVL(TYPEOF($1), 'no-type') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function D0 successfully created.",
            "CREATE OR REPLACE PROCEDURE d0() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return new "
                + "Date(Date.UTC(2024, 0, 2, 3, 4, 5, 678)); $$");
        answer(
            "CALL d0()");
        answer(
            "SELECT REPLACE(REPLACE($1::VARCHAR, '-', '_'), ':', ';') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function D1 successfully created.",
            "CREATE OR REPLACE PROCEDURE d1() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return {d: new "
                + "Date(Date.UTC(2024, 0, 2, 3, 4, 5))}; $$");
        answer(
            "CALL d1()");
        answer(
            "SELECT REPLACE(REPLACE($1::VARCHAR, '-', '_'), ':', ';') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function D2 successfully created.",
            "CREATE OR REPLACE PROCEDURE d2() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$ return [new "
                + "Date(Date.UTC(2024, 6, 2, 3, 4, 5)), new Date(NaN)]; $$");
        answer(
            "CALL d2()");
        answer(
            "SELECT REPLACE(REPLACE($1::VARCHAR, '-', '_'), ':', ';') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function D3 successfully created.",
            "CREATE OR REPLACE PROCEDURE d3() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return 1.5; $$");
        assertAnswer(
            "1.5",
            "CALL d3()");
        assertAnswer(
            "VARIANT[LOB] / 1.5",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function D4 successfully created.",
            "CREATE OR REPLACE PROCEDURE d4() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return 2; $$");
        assertAnswer(
            "2",
            "CALL d4()");
        assertAnswer(
            "VARIANT[LOB] / 2",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function D5 successfully created.",
            "CREATE OR REPLACE PROCEDURE d5() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return NaN; $$");
        assertAnswer(
            "NaN",
            "CALL d5()");
        assertAnswer(
            "VARIANT[LOB] / NaN",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function D6 successfully created.",
            "CREATE OR REPLACE PROCEDURE d6() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return Infinity; $$");
        assertAnswer(
            "Infinity",
            "CALL d6()");
        assertAnswer(
            "VARIANT[LOB] / inf",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function D7 successfully created.",
            "CREATE OR REPLACE PROCEDURE d7() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return -Infinity; $$");
        assertAnswer(
            "-Infinity",
            "CALL d7()");
        assertAnswer(
            "VARIANT[LOB] / -inf",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function D8 successfully created.",
            "CREATE OR REPLACE PROCEDURE d8() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$ return [1.5, 2, 1e21, -0.25,"
                + " 1e-7, 0.1 + 0.2, -0, 3.0, 123456.789]; $$");
        assertAnswer(
            "[1.500000000000000e+00,2,1.000000000000000e+21,-2.500000000000000e-01,1.000000000000000e-07,"
                + "3.000000000000000e-01,0,3,1.234567890000000e+05]",
            "CALL d8()");
        assertAnswer(
            "ARRAY[LOB] / [1.500000000000000e+00,2,1.000000000000000e+21,-2.500000000000000e-01,"
                + "1.000000000000000e-07,3.000000000000000e-01,0,3,1.234567890000000e+05]",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function D9 successfully created.",
            "CREATE OR REPLACE PROCEDURE d9() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$ return {f: 2.5, i: 4, big: "
                + "9007199254740993, n: NaN, m: -Infinity}; $$");
        assertAnswer(
            "{\"big\":9007199254740992,\"f\":2.500000000000000e+00,\"i\":4,\"m\":-Infinity,\"n\":NaN}",
            "CALL d9()");
        assertAnswer(
            "OBJECT[LOB] / {\"big\":9007199254740992,\"f\":2.500000000000000e+00,\"i\":4,\"m\":-Infinity,"
                + "\"n\":NaN}",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function DA successfully created.",
            "CREATE OR REPLACE PROCEDURE da() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return '1.5'; $$");
        assertAnswer(
            "1.5",
            "CALL da()");
        assertAnswer(
            "VARIANT[LOB] / 1.5",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function DB successfully created.",
            "CREATE OR REPLACE PROCEDURE db() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return true; $$");
        assertAnswer(
            "true",
            "CALL db()");
        assertAnswer(
            "VARIANT[LOB] / true",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function DC successfully created.",
            "CREATE OR REPLACE PROCEDURE dc() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return [[1.5], {x: {y: "
                + "0.5}}]; $$");
        assertAnswer(
            "[[1.500000000000000e+00],{\"x\":{\"y\":5.000000000000000e-01}}]",
            "CALL dc()");
        assertAnswer(
            "VARIANT[LOB] / [[1.500000000000000e+00],{\"x\":{\"y\":5.000000000000000e-01}}]",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function W2 successfully created.",
            "CREATE OR REPLACE PROCEDURE w2() RETURNS VARCHAR(2) LANGUAGE JAVASCRIPT AS $$ return 'ab'; $$");
        assertAnswer(
            "ab",
            "CALL w2()");
        assertShape(
            "W2:VARCHAR(2)",
            "CALL w2()");
        assertAnswer(
            "VARCHAR(2)[LOB] / ab",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function W7 successfully created.",
            "CREATE OR REPLACE PROCEDURE w7() RETURNS BINARY(2) LANGUAGE JAVASCRIPT AS $$ return new "
                + "Uint8Array([1, 2]); $$");
        assertAnswer(
            "0102",
            "CALL w7()");
        assertShape(
            "W7:BINARY",
            "CALL w7()");
        assertAnswer(
            "BINARY(2)[LOB] / 0102",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function W8 successfully created.",
            "CREATE OR REPLACE PROCEDURE w8() RETURNS BINARY(2) LANGUAGE JAVASCRIPT AS $$ return new "
                + "Uint8Array([1, 2, 3]); $$");
        assertAnswer(
            "REFUSED Binary value '010203' is too long and would be truncated",
            "CALL w8()");
        assertShape(
            "REFUSED Binary value '010203' is too long and would be truncated",
            "CALL w8()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J0 successfully created.",
            "CREATE OR REPLACE PROCEDURE j0() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return {a: undefined, b: "
                + "[undefined], c: function () {}, d: null}; $$");
        assertAnswer(
            "{\"b\":[],\"c\":{},\"d\":null}",
            "CALL j0()");
        assertAnswer(
            "VARIANT[LOB] / {\"b\":[],\"c\":{},\"d\":null}",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J1 successfully created.",
            "CREATE OR REPLACE PROCEDURE j1() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return [new Uint8Array([1,"
                + " 2])]; $$");
        assertAnswer(
            "[\"0102\"]",
            "CALL j1()");
        assertAnswer(
            "VARIANT[LOB] / [\"0102\"]",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J2 successfully created.",
            "CREATE OR REPLACE PROCEDURE j2() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return 1e21; $$");
        assertAnswer(
            "1.0E21",
            "CALL j2()");
        assertAnswer(
            "VARIANT[LOB] / 1e+21",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J3 successfully created.",
            "CREATE OR REPLACE PROCEDURE j3() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return 0.1 + 0.2; $$");
        answer(
            "CALL j3()");
        assertAnswer(
            "VARIANT[LOB] / 0.3",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J4 successfully created.",
            "CREATE OR REPLACE PROCEDURE j4() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return [function () {}, "
                + "Symbol.iterator]; $$");
        assertAnswer(
            "[{},null]",
            "CALL j4()");
        assertAnswer(
            "VARIANT[LOB] / [{},null]",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function J5 successfully created.",
            "CREATE OR REPLACE PROCEDURE j5() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$ return {d: new "
                + "Date(Date.UTC(2024, 0, 2, 3, 4, 5)), n: null}; $$");
        answer(
            "CALL j5()");
        answer(
            "SELECT REPLACE(REPLACE($1::VARCHAR, '-', '_'), ':', ';') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
    }
}
