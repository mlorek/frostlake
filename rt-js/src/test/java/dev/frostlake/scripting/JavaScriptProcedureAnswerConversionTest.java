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
 * What a JavaScript procedure answers for values its handler returns that the declared RETURNS type converts.
 * The rules are a function's: a VARCHAR is the value's string and a FLOAT its number, a symbol being no text
 * and a BigInt no number; a DATE takes the milliseconds a value converts to; a VARIANT is a copy of the value
 * that never calls {@code toJSON}, unwraps a {@code Number} or a {@code String} object and keeps a text that
 * looks like one of the engine's own marks as that text; an OBJECT takes any object that is no array. Where a
 * function keeps a {@code Date} and a {@code Uint8Array} typed, a procedure answers their texts. The
 * session's TIMEZONE is set by the tests and unset whatever the assertions make of the statements between.
 * Every answer is live-verified.
 */
public class JavaScriptProcedureAnswerConversionTest extends MeasuredCellsTestSupport {

    @Test
    public void aProceduresAnswerByDeclaredType() {
        try {
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function PQ1 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq1() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return Symbol('x');$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Late exception in return value conversion",
                "CALL pq1()");
            assertAnswer(
                "Function PQ2 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq2() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return "
                    + "Object.create(null);$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Late exception in return value conversion",
                "CALL pq2()");
            assertAnswer(
                "Function PQ3 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq3() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return 1n;$$");
            assertAnswer(
                "1",
                "CALL pq3()");
            assertAnswer(
                "Function PQ4 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq4() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return [1, [2]];$$");
            assertAnswer(
                "1,2",
                "CALL pq4()");
            assertAnswer(
                "Function PQ5 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq5() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return {a: 1};$$");
            assertAnswer(
                "[object Object]",
                "CALL pq5()");
            assertAnswer(
                "Function PQ6 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq6() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return 1n;$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Late exception in return value conversion",
                "CALL pq6()");
            assertShapedAnswer(
                "REFUSED Query <QID> has no result because it failed",
                "SELECT TYPEOF($1), $1 IS NULL, TO_JSON($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function PQ7 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq7() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return {toJSON: "
                    + "function () { return 'j'; }};$$");
            assertAnswer(
                "{\"toJSON\":{}}",
                "CALL pq7()");
            assertAnswer(
                "OBJECT|false|{\"toJSON\":{}}",
                "SELECT TYPEOF($1), $1 IS NULL, TO_JSON($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function PQ8 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq8() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return [new "
                    + "Number(1.5)];$$");
            assertAnswer(
                "[1.500000000000000e+00]",
                "CALL pq8()");
            assertAnswer(
                "ARRAY|false|[1.500000000000000e+00]",
                "SELECT TYPEOF($1), $1 IS NULL, TO_JSON($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function PQ9 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq9() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return '\\u0002F:1';$$");
            assertAnswer(
                "\u0002F:1",
                "CALL pq9()");
            assertAnswer(
                "VARCHAR|false|\"\\u0002F:1\"",
                "SELECT TYPEOF($1), $1 IS NULL, TO_JSON($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function PQ10 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq10() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return Symbol('x');$$");
            assertAnswer(
                "null",
                "CALL pq10()");
            assertAnswer(
                "NULL_VALUE|false|null",
                "SELECT TYPEOF($1), $1 IS NULL, TO_JSON($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function PQ11 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq11() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return "
                    + "[Symbol('x')];$$");
            assertAnswer(
                "[null]",
                "CALL pq11()");
            assertAnswer(
                "ARRAY|false|[null]",
                "SELECT TYPEOF($1), $1 IS NULL, TO_JSON($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function PQ12 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq12() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return new Map([[1,"
                    + "2]]);$$");
            assertAnswer(
                "{}",
                "CALL pq12()");
            assertAnswer(
                "OBJECT|false|{}",
                "SELECT TYPEOF($1), $1 IS NULL, TO_JSON($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function PQ13 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq13() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return new "
                    + "String('s');$$");
            assertAnswer(
                "s",
                "CALL pq13()");
            assertAnswer(
                "VARCHAR|false|\"s\"",
                "SELECT TYPEOF($1), $1 IS NULL, TO_JSON($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function PQ14 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq14() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return new "
                    + "Uint8Array([1,2]);$$");
            assertAnswer(
                "0102",
                "CALL pq14()");
            assertAnswer(
                "VARCHAR|false|\"0102\"",
                "SELECT TYPEOF($1), $1 IS NULL, TO_JSON($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function PQ15 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq15() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return new Date(0);$$");
            answer(
                "CALL pq15()");
            answer(
                "SELECT TYPEOF($1), $1 IS NULL, TO_JSON($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function PQ16 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq16() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return [1, "
                    + "undefined];$$");
            assertAnswer(
                "[1]",
                "CALL pq16()");
            assertAnswer(
                "ARRAY|false|[1]",
                "SELECT TYPEOF($1), $1 IS NULL, TO_JSON($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function PQ17 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq17() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return 1n;$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Late exception in return value conversion",
                "CALL pq17()");
            assertAnswer(
                "Function PQ18 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq18() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return '12';$$");
            assertAnswer(
                "12.0",
                "CALL pq18()");
            assertAnswer(
                "Function PQ19 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq19() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return true;$$");
            assertAnswer(
                "1.0",
                "CALL pq19()");
            assertAnswer(
                "Function PQ20 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq20() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return new "
                    + "Uint8Array([1]);$$");
            assertAnswer(
                "01",
                "CALL pq20()");
            answer(
                "SELECT TYPEOF($1), $1 IS NULL, TO_JSON($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function PQ21 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq21() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return new Map([[1,"
                    + "2]]);$$");
            assertAnswer(
                "{}",
                "CALL pq21()");
            assertAnswer(
                "OBJECT|false|{}",
                "SELECT TYPEOF($1), $1 IS NULL, TO_JSON($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function PQ22 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq22() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return /x/g;$$");
            assertAnswer(
                "{}",
                "CALL pq22()");
            assertAnswer(
                "OBJECT|false|{}",
                "SELECT TYPEOF($1), $1 IS NULL, TO_JSON($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function PQ23 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq23() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return function () "
                    + "{};$$");
            assertAnswer(
                "{}",
                "CALL pq23()");
            assertAnswer(
                "OBJECT|false|{}",
                "SELECT TYPEOF($1), $1 IS NULL, TO_JSON($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function PQ24 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq24() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return new "
                    + "String('s');$$");
            assertAnswer(
                "s",
                "CALL pq24()");
            answer(
                "SELECT TYPEOF($1), $1 IS NULL, TO_JSON($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function PQ25 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq25() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return new Date(0);$$");
            answer(
                "CALL pq25()");
            answer(
                "SELECT TYPEOF($1), $1 IS NULL, TO_JSON($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function PQ26 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq26() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return "
                    + "Object.create(null);$$");
            assertAnswer(
                "{}",
                "CALL pq26()");
            assertAnswer(
                "OBJECT|false|{}",
                "SELECT TYPEOF($1), $1 IS NULL, TO_JSON($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function PQ27 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq27() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return new "
                    + "Error('boom');$$");
            assertAnswer(
                "{}",
                "CALL pq27()");
            assertAnswer(
                "OBJECT|false|{}",
                "SELECT TYPEOF($1), $1 IS NULL, TO_JSON($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function PQ28 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq28() RETURNS DATE LANGUAGE JAVASCRIPT AS $$return true;$$");
            assertAnswer(
                "1970-01-01",
                "CALL pq28()");
            assertAnswer(
                "1970-01-01",
                "SELECT $1::VARCHAR FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function PQ29 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq29() RETURNS DATE LANGUAGE JAVASCRIPT AS $$return [];$$");
            assertAnswer(
                "1970-01-01",
                "CALL pq29()");
            assertAnswer(
                "1970-01-01",
                "SELECT $1::VARCHAR FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function PQ30 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq30() RETURNS DATE LANGUAGE JAVASCRIPT AS $$return '5';$$");
            assertAnswer(
                "1970-01-01",
                "CALL pq30()");
            assertAnswer(
                "1970-01-01",
                "SELECT $1::VARCHAR FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function PQ31 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq31() RETURNS DATE LANGUAGE JAVASCRIPT AS $$return 0;$$");
            assertAnswer(
                "1970-01-01",
                "CALL pq31()");
            assertAnswer(
                "1970-01-01",
                "SELECT $1::VARCHAR FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function PQ32 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq32() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$return new "
                    + "Uint8Array([1]);$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not an ARRAY",
                "CALL pq32()");
            assertShapedAnswer(
                "REFUSED Query <QID> has no result because it failed",
                "SELECT TYPEOF($1), $1 IS NULL, TO_JSON($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function PQ33 successfully created.",
                "CREATE OR REPLACE PROCEDURE pq33() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$return [1n];$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Late exception in return value conversion",
                "CALL pq33()");
            assertShapedAnswer(
                "REFUSED Query <QID> has no result because it failed",
                "SELECT TYPEOF($1), $1 IS NULL, TO_JSON($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }

    @Test
    public void aProceduresDateAndBytesAreTexts() {
        try {
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function PRD successfully created.",
                "CREATE OR REPLACE PROCEDURE prd() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return [new "
                    + "Date(86400000), new Uint8Array([255, 1]), 1.5]; $$");
            answer(
                "CALL prd()");
            assertAnswer(
                "VARCHAR|VARCHAR|DOUBLE|FF01",
                "SELECT TYPEOF($1[0]), TYPEOF($1[1]), TYPEOF($1[2]), $1[1] FROM "
                    + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function PRT successfully created.",
                "CREATE OR REPLACE PROCEDURE prt() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return new "
                    + "Date(86400000); $$");
            answer(
                "CALL prt()");
            assertAnswer(
                "VARCHAR",
                "SELECT TYPEOF($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }
}
