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
 * What a JavaScript function answers for the value its handler returns, by the declared RETURNS type. A
 * VARCHAR is the value's string as the language converts it - an array its elements joined by commas, an
 * object {@code [object Object]} unless it has a {@code toString} - a FLOAT its number and a BOOLEAN its
 * truthiness; a symbol is no text, a BigInt no number and a conversion that throws no value:
 * {@code Late exception in return value conversion}. A DATE and a TIMESTAMP take a {@code Date} or the
 * milliseconds the value converts to and refuse what names no instant. A VARIANT is a copy of the value,
 * member by member: a {@code Date} a TIMESTAMP_LTZ, a {@code Uint8Array} a BINARY, a number with a fraction a
 * DOUBLE, a function an empty object, an {@code undefined} element one of the array's SQL NULLs unless it
 * trails, an {@code undefined} member left out, and {@code toJSON} never called. An ARRAY takes only an array
 * and an OBJECT any object that is no array; {@code undefined} is a SQL NULL and {@code null} a VARIANT's
 * JSON null. A value handed in comes back as it arrived, so an array's SQL NULL element stays one. The
 * session's TIMEZONE is set by the tests and unset whatever the assertions make of the statements between.
 * Every answer is live-verified.
 */
public class JavaScriptFunctionAnswerTest extends MeasuredCellsTestSupport {

    @Test
    public void aVarcharAnswerIsTheValuesString() {
        try {
            answer(
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function Q1 successfully created.",
                "CREATE OR REPLACE FUNCTION q1() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return [1,2];$$");
            assertAnswer(
                "1,2",
                "SELECT q1()");
            assertAnswer(
                "Function Q2 successfully created.",
                "CREATE OR REPLACE FUNCTION q2() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return {a:1};$$");
            assertAnswer(
                "[object Object]",
                "SELECT q2()");
            assertAnswer(
                "Function Q3 successfully created.",
                "CREATE OR REPLACE FUNCTION q3() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return new Date(0);$$");
            assertAnswer(
                "Thu Jan 01 1970 00:00:00 GMT+0000 (Coordinated Universal Time)",
                "SELECT q3()");
            assertAnswer(
                "Function Q4 successfully created.",
                "CREATE OR REPLACE FUNCTION q4() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return /x/g;$$");
            assertAnswer(
                "/x/g",
                "SELECT q4()");
            assertAnswer(
                "Function Q5 successfully created.",
                "CREATE OR REPLACE FUNCTION q5() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return -0;$$");
            assertAnswer(
                "0",
                "SELECT q5()");
            assertAnswer(
                "Function Q6 successfully created.",
                "CREATE OR REPLACE FUNCTION q6() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return 1e21;$$");
            assertAnswer(
                "1e+21",
                "SELECT q6()");
            assertAnswer(
                "Function Q7 successfully created.",
                "CREATE OR REPLACE FUNCTION q7() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return "
                    + "123456789012345680000;$$");
            assertAnswer(
                "123456789012345680000",
                "SELECT q7()");
            assertAnswer(
                "Function Q8 successfully created.",
                "CREATE OR REPLACE FUNCTION q8() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return new "
                    + "Error('boom');$$");
            assertAnswer(
                "Error: boom",
                "SELECT q8()");
            assertAnswer(
                "Function Q9 successfully created.",
                "CREATE OR REPLACE FUNCTION q9() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return new Map([[1,"
                    + "2]]);$$");
            assertAnswer(
                "[object Map]",
                "SELECT q9()");
            assertAnswer(
                "Function Q10 successfully created.",
                "CREATE OR REPLACE FUNCTION q10() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return new Set([1]);$$");
            assertAnswer(
                "[object Set]",
                "SELECT q10()");
            assertAnswer(
                "Function Q11 successfully created.",
                "CREATE OR REPLACE FUNCTION q11() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return "
                    + "Promise.resolve(1);$$");
            assertAnswer(
                "[object Promise]",
                "SELECT q11()");
            assertAnswer(
                "Function Q12 successfully created.",
                "CREATE OR REPLACE FUNCTION q12() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return {toString: "
                    + "function () { return 'custom'; }};$$");
            assertAnswer(
                "custom",
                "SELECT q12()");
            assertAnswer(
                "Function Q13 successfully created.",
                "CREATE OR REPLACE FUNCTION q13() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return {toJSON: "
                    + "function () { return 'j'; }};$$");
            assertAnswer(
                "[object Object]",
                "SELECT q13()");
            assertAnswer(
                "Function Q14 successfully created.",
                "CREATE OR REPLACE FUNCTION q14() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return new "
                    + "Uint8Array([1,2]);$$");
            assertAnswer(
                "1,2",
                "SELECT q14()");
            assertAnswer(
                "Function Q15 successfully created.",
                "CREATE OR REPLACE FUNCTION q15() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return Symbol('x');$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Late exception in return value conversion",
                "SELECT q15()");
            assertAnswer(
                "Function Q16 successfully created.",
                "CREATE OR REPLACE FUNCTION q16() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return "
                    + "Object.create(null);$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Late exception in return value conversion",
                "SELECT q16()");
            assertAnswer(
                "Function Q17 successfully created.",
                "CREATE OR REPLACE FUNCTION q17() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return 1n;$$");
            assertAnswer(
                "1",
                "SELECT q17()");
            assertAnswer(
                "Function Q18 successfully created.",
                "CREATE OR REPLACE FUNCTION q18() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return function f() { "
                    + "return 1; };$$");
            assertAnswer(
                "function f() { return 1; }",
                "SELECT q18()");
            assertAnswer(
                "Function Q19 successfully created.",
                "CREATE OR REPLACE FUNCTION q19() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return NaN;$$");
            assertAnswer(
                "NaN",
                "SELECT q19()");
            assertAnswer(
                "Function Q20 successfully created.",
                "CREATE OR REPLACE FUNCTION q20() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return [];$$");
            assertAnswer(
                "",
                "SELECT q20()");
            assertAnswer(
                "Function Q21 successfully created.",
                "CREATE OR REPLACE FUNCTION q21() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return '';$$");
            assertAnswer(
                "",
                "SELECT q21()");
            assertAnswer(
                "Function Q22 successfully created.",
                "CREATE OR REPLACE FUNCTION q22() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return false;$$");
            assertAnswer(
                "false",
                "SELECT q22()");
            assertAnswer(
                "Function Q23 successfully created.",
                "CREATE OR REPLACE FUNCTION q23() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return 0.1 + 0.2;$$");
            assertAnswer(
                "0.30000000000000004",
                "SELECT q23()");
            assertAnswer(
                "Function Q24 successfully created.",
                "CREATE OR REPLACE FUNCTION q24() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return [null];$$");
            assertAnswer(
                "",
                "SELECT q24()");
            assertAnswer(
                "Function Q25 successfully created.",
                "CREATE OR REPLACE FUNCTION q25() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return [[1,[2]],3];$$");
            assertAnswer(
                "1,2,3",
                "SELECT q25()");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }

    @Test
    public void aVariantAnswerIsACopyOfTheValue() {
        try {
            answer(
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function Q26 successfully created.",
                "CREATE OR REPLACE FUNCTION q26() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return new Date(0);$$");
            answer(
                "SELECT TYPEOF(q26()), q26() IS NULL, TO_JSON(q26())");
            assertAnswer(
                "Function Q27 successfully created.",
                "CREATE OR REPLACE FUNCTION q27() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return function(){};$$");
            assertAnswer(
                "OBJECT|false|{}",
                "SELECT TYPEOF(q27()), q27() IS NULL, TO_JSON(q27())");
            assertAnswer(
                "Function Q28 successfully created.",
                "CREATE OR REPLACE FUNCTION q28() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return [null, "
                    + "undefined];$$");
            assertAnswer(
                "ARRAY|false|[null]",
                "SELECT TYPEOF(q28()), q28() IS NULL, TO_JSON(q28())");
            assertAnswer(
                "Function Q29 successfully created.",
                "CREATE OR REPLACE FUNCTION q29() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return {d: new Date(0),"
                    + " u: undefined, f: function () {}, n: null};$$");
            answer(
                "SELECT TYPEOF(q29()), q29() IS NULL, TO_JSON(q29())");
            assertAnswer(
                "Function Q30 successfully created.",
                "CREATE OR REPLACE FUNCTION q30() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return new "
                    + "Uint8Array([1,2]);$$");
            assertAnswer(
                "BINARY|false|\"0102\"",
                "SELECT TYPEOF(q30()), q30() IS NULL, TO_JSON(q30())");
            assertAnswer(
                "Function Q31 successfully created.",
                "CREATE OR REPLACE FUNCTION q31() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return Symbol('x');$$");
            assertAnswer(
                "NULL_VALUE|false|null",
                "SELECT TYPEOF(q31()), q31() IS NULL, TO_JSON(q31())");
            assertAnswer(
                "Function Q32 successfully created.",
                "CREATE OR REPLACE FUNCTION q32() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return 1n;$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Late exception in return value conversion",
                "SELECT TYPEOF(q32()), q32() IS NULL, TO_JSON(q32())");
            assertAnswer(
                "Function Q33 successfully created.",
                "CREATE OR REPLACE FUNCTION q33() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return 0.1 + 0.2;$$");
            assertAnswer(
                "DOUBLE|false|3.000000000000000e-01",
                "SELECT TYPEOF(q33()), q33() IS NULL, TO_JSON(q33())");
            assertAnswer(
                "Function Q34 successfully created.",
                "CREATE OR REPLACE FUNCTION q34() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return {toJSON: "
                    + "function () { return 'j'; }};$$");
            assertAnswer(
                "OBJECT|false|{\"toJSON\":{}}",
                "SELECT TYPEOF(q34()), q34() IS NULL, TO_JSON(q34())");
            assertAnswer(
                "Function Q35 successfully created.",
                "CREATE OR REPLACE FUNCTION q35() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return [Symbol('x'), "
                    + "1];$$");
            assertAnswer(
                "ARRAY|false|[null,1]",
                "SELECT TYPEOF(q35()), q35() IS NULL, TO_JSON(q35())");
            assertAnswer(
                "Function Q36 successfully created.",
                "CREATE OR REPLACE FUNCTION q36() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return {s: Symbol('x'),"
                    + " k: 1};$$");
            assertAnswer(
                "OBJECT|false|{\"k\":1}",
                "SELECT TYPEOF(q36()), q36() IS NULL, TO_JSON(q36())");
            assertAnswer(
                "Function Q37 successfully created.",
                "CREATE OR REPLACE FUNCTION q37() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return new Map([[1,"
                    + "2]]);$$");
            assertAnswer(
                "OBJECT|false|{}",
                "SELECT TYPEOF(q37()), q37() IS NULL, TO_JSON(q37())");
            assertAnswer(
                "Function Q38 successfully created.",
                "CREATE OR REPLACE FUNCTION q38() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return new Set([1]);$$");
            assertAnswer(
                "OBJECT|false|{}",
                "SELECT TYPEOF(q38()), q38() IS NULL, TO_JSON(q38())");
            assertAnswer(
                "Function Q39 successfully created.",
                "CREATE OR REPLACE FUNCTION q39() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return /x/g;$$");
            assertAnswer(
                "OBJECT|false|{}",
                "SELECT TYPEOF(q39()), q39() IS NULL, TO_JSON(q39())");
            assertAnswer(
                "Function Q40 successfully created.",
                "CREATE OR REPLACE FUNCTION q40() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return new "
                    + "Error('boom');$$");
            assertAnswer(
                "OBJECT|false|{}",
                "SELECT TYPEOF(q40()), q40() IS NULL, TO_JSON(q40())");
            assertAnswer(
                "Function Q41 successfully created.",
                "CREATE OR REPLACE FUNCTION q41() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return [1n];$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Late exception in return value conversion",
                "SELECT TYPEOF(q41()), q41() IS NULL, TO_JSON(q41())");
            assertAnswer(
                "Function Q42 successfully created.",
                "CREATE OR REPLACE FUNCTION q42() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return "
                    + "Object.create(null);$$");
            assertAnswer(
                "OBJECT|false|{}",
                "SELECT TYPEOF(q42()), q42() IS NULL, TO_JSON(q42())");
            assertAnswer(
                "Function Q43 successfully created.",
                "CREATE OR REPLACE FUNCTION q43() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return new Date(NaN);$$");
            answer(
                "SELECT TYPEOF(q43()), q43() IS NULL, TO_JSON(q43())");
            assertAnswer(
                "Function Q44 successfully created.",
                "CREATE OR REPLACE FUNCTION q44() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return [new "
                    + "Date(NaN)];$$");
            answer(
                "SELECT TYPEOF(q44()), q44() IS NULL, TO_JSON(q44())");
            assertAnswer(
                "Function Q45 successfully created.",
                "CREATE OR REPLACE FUNCTION q45() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return new "
                    + "String('s');$$");
            assertAnswer(
                "VARCHAR|false|\"s\"",
                "SELECT TYPEOF(q45()), q45() IS NULL, TO_JSON(q45())");
            assertAnswer(
                "Function Q46 successfully created.",
                "CREATE OR REPLACE FUNCTION q46() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return new Number(5);$$");
            assertAnswer(
                "INTEGER|false|5",
                "SELECT TYPEOF(q46()), q46() IS NULL, TO_JSON(q46())");
            assertAnswer(
                "Function Q47 successfully created.",
                "CREATE OR REPLACE FUNCTION q47() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return new "
                    + "Boolean(false);$$");
            assertAnswer(
                "BOOLEAN|false|false",
                "SELECT TYPEOF(q47()), q47() IS NULL, TO_JSON(q47())");
            assertAnswer(
                "Function Q48 successfully created.",
                "CREATE OR REPLACE FUNCTION q48() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return [new "
                    + "String(\"s\"), new Number(1.5), new Boolean(true)];$$");
            assertAnswer(
                "ARRAY|false|[\"s\",1.500000000000000e+00,true]",
                "SELECT TYPEOF(q48()), q48() IS NULL, TO_JSON(q48())");
            assertAnswer(
                "Function Q49 successfully created.",
                "CREATE OR REPLACE FUNCTION q49() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return 1.0;$$");
            assertAnswer(
                "INTEGER|false|1",
                "SELECT TYPEOF(q49()), q49() IS NULL, TO_JSON(q49())");
            assertAnswer(
                "Function Q50 successfully created.",
                "CREATE OR REPLACE FUNCTION q50() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return -1.5e-7;$$");
            assertAnswer(
                "DOUBLE|false|-1.500000000000000e-07",
                "SELECT TYPEOF(q50()), q50() IS NULL, TO_JSON(q50())");
            assertAnswer(
                "Function Q51 successfully created.",
                "CREATE OR REPLACE FUNCTION q51() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return 2e-7;$$");
            assertAnswer(
                "DOUBLE|false|2.000000000000000e-07",
                "SELECT TYPEOF(q51()), q51() IS NULL, TO_JSON(q51())");
            assertAnswer(
                "Function Q52 successfully created.",
                "CREATE OR REPLACE FUNCTION q52() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return [0.1, 1e21, "
                    + "1e20, -0, 123456789012345680000];$$");
            assertAnswer(
                "ARRAY|false|[1.000000000000000e-01,1.000000000000000e+21,100000000000000000000,0,"
                    + "123456789012345680000]",
                "SELECT TYPEOF(q52()), q52() IS NULL, TO_JSON(q52())");
            assertAnswer(
                "Function Q53 successfully created.",
                "CREATE OR REPLACE FUNCTION q53() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return '\\u0002F:1';$$");
            assertAnswer(
                "VARCHAR|false|\"\\u0002F:1\"",
                "SELECT TYPEOF(q53()), q53() IS NULL, TO_JSON(q53())");
            assertAnswer(
                "Function Q54 successfully created.",
                "CREATE OR REPLACE FUNCTION q54() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return "
                    + "['\\u0002D:5'];$$");
            assertAnswer(
                "ARRAY|false|[\"\\u0002D:5\"]",
                "SELECT TYPEOF(q54()), q54() IS NULL, TO_JSON(q54())");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }

    @Test
    public void aFloatAnswerIsTheValuesNumber() {
        try {
            answer(
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function Q55 successfully created.",
                "CREATE OR REPLACE FUNCTION q55() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return '12';$$");
            assertAnswer(
                "12",
                "SELECT q55()::VARCHAR");
            assertAnswer(
                "Function Q56 successfully created.",
                "CREATE OR REPLACE FUNCTION q56() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return ' 7 ';$$");
            assertAnswer(
                "7",
                "SELECT q56()::VARCHAR");
            assertAnswer(
                "Function Q57 successfully created.",
                "CREATE OR REPLACE FUNCTION q57() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return '1e2';$$");
            assertAnswer(
                "100",
                "SELECT q57()::VARCHAR");
            assertAnswer(
                "Function Q58 successfully created.",
                "CREATE OR REPLACE FUNCTION q58() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return '';$$");
            assertAnswer(
                "0",
                "SELECT q58()::VARCHAR");
            assertAnswer(
                "Function Q59 successfully created.",
                "CREATE OR REPLACE FUNCTION q59() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return 'x';$$");
            assertAnswer(
                "NaN",
                "SELECT q59()::VARCHAR");
            assertAnswer(
                "Function Q60 successfully created.",
                "CREATE OR REPLACE FUNCTION q60() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return {};$$");
            assertAnswer(
                "NaN",
                "SELECT q60()::VARCHAR");
            assertAnswer(
                "Function Q61 successfully created.",
                "CREATE OR REPLACE FUNCTION q61() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return true;$$");
            assertAnswer(
                "1",
                "SELECT q61()::VARCHAR");
            assertAnswer(
                "Function Q62 successfully created.",
                "CREATE OR REPLACE FUNCTION q62() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return [5];$$");
            assertAnswer(
                "5",
                "SELECT q62()::VARCHAR");
            assertAnswer(
                "Function Q63 successfully created.",
                "CREATE OR REPLACE FUNCTION q63() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return new Date(0);$$");
            assertAnswer(
                "0",
                "SELECT q63()::VARCHAR");
            assertAnswer(
                "Function Q64 successfully created.",
                "CREATE OR REPLACE FUNCTION q64() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return 1n;$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Late exception in return value conversion",
                "SELECT q64()::VARCHAR");
            assertAnswer(
                "Function Q65 successfully created.",
                "CREATE OR REPLACE FUNCTION q65() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return [];$$");
            assertAnswer(
                "0",
                "SELECT q65()::VARCHAR");
            assertAnswer(
                "Function Q66 successfully created.",
                "CREATE OR REPLACE FUNCTION q66() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return [1,2];$$");
            assertAnswer(
                "NaN",
                "SELECT q66()::VARCHAR");
            assertAnswer(
                "Function Q67 successfully created.",
                "CREATE OR REPLACE FUNCTION q67() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return Infinity;$$");
            assertAnswer(
                "inf",
                "SELECT q67()::VARCHAR");
            assertAnswer(
                "Function Q68 successfully created.",
                "CREATE OR REPLACE FUNCTION q68() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return -0;$$");
            assertAnswer(
                "-0",
                "SELECT q68()::VARCHAR");
            assertAnswer(
                "Function Q69 successfully created.",
                "CREATE OR REPLACE FUNCTION q69() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return '0x10';$$");
            assertAnswer(
                "16",
                "SELECT q69()::VARCHAR");
            assertAnswer(
                "Function Q70 successfully created.",
                "CREATE OR REPLACE FUNCTION q70() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return false;$$");
            assertAnswer(
                "0",
                "SELECT q70()::VARCHAR");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }

    @Test
    public void aBooleanAnswerIsTheValuesTruthiness() {
        try {
            answer(
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function Q71 successfully created.",
                "CREATE OR REPLACE FUNCTION q71() RETURNS BOOLEAN LANGUAGE JAVASCRIPT AS $$return 1;$$");
            assertAnswer(
                "true",
                "SELECT q71()");
            assertAnswer(
                "Function Q72 successfully created.",
                "CREATE OR REPLACE FUNCTION q72() RETURNS BOOLEAN LANGUAGE JAVASCRIPT AS $$return 'x';$$");
            assertAnswer(
                "true",
                "SELECT q72()");
            assertAnswer(
                "Function Q73 successfully created.",
                "CREATE OR REPLACE FUNCTION q73() RETURNS BOOLEAN LANGUAGE JAVASCRIPT AS $$return {};$$");
            assertAnswer(
                "true",
                "SELECT q73()");
            assertAnswer(
                "Function Q74 successfully created.",
                "CREATE OR REPLACE FUNCTION q74() RETURNS BOOLEAN LANGUAGE JAVASCRIPT AS $$return [];$$");
            assertAnswer(
                "true",
                "SELECT q74()");
            assertAnswer(
                "Function Q75 successfully created.",
                "CREATE OR REPLACE FUNCTION q75() RETURNS BOOLEAN LANGUAGE JAVASCRIPT AS $$return 'false';$$");
            assertAnswer(
                "true",
                "SELECT q75()");
            assertAnswer(
                "Function Q76 successfully created.",
                "CREATE OR REPLACE FUNCTION q76() RETURNS BOOLEAN LANGUAGE JAVASCRIPT AS $$return 0;$$");
            assertAnswer(
                "false",
                "SELECT q76()");
            assertAnswer(
                "Function Q77 successfully created.",
                "CREATE OR REPLACE FUNCTION q77() RETURNS BOOLEAN LANGUAGE JAVASCRIPT AS $$return NaN;$$");
            assertAnswer(
                "false",
                "SELECT q77()");
            assertAnswer(
                "Function Q78 successfully created.",
                "CREATE OR REPLACE FUNCTION q78() RETURNS BOOLEAN LANGUAGE JAVASCRIPT AS $$return '';$$");
            assertAnswer(
                "false",
                "SELECT q78()");
            assertAnswer(
                "Function Q79 successfully created.",
                "CREATE OR REPLACE FUNCTION q79() RETURNS BOOLEAN LANGUAGE JAVASCRIPT AS $$return -0;$$");
            assertAnswer(
                "false",
                "SELECT q79()");
            assertAnswer(
                "Function Q80 successfully created.",
                "CREATE OR REPLACE FUNCTION q80() RETURNS BOOLEAN LANGUAGE JAVASCRIPT AS $$return 0n;$$");
            assertAnswer(
                "false",
                "SELECT q80()");
            assertAnswer(
                "Function Q81 successfully created.",
                "CREATE OR REPLACE FUNCTION q81() RETURNS BOOLEAN LANGUAGE JAVASCRIPT AS $$return 1n;$$");
            assertAnswer(
                "true",
                "SELECT q81()");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }

    @Test
    public void anArrayAnswerTakesOnlyAnArray() {
        try {
            answer(
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function Q82 successfully created.",
                "CREATE OR REPLACE FUNCTION q82() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$return 'x';$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not an ARRAY",
                "SELECT TYPEOF(q82()), q82() IS NULL, TO_JSON(q82())");
            assertAnswer(
                "Function Q83 successfully created.",
                "CREATE OR REPLACE FUNCTION q83() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$return 5;$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not an ARRAY",
                "SELECT TYPEOF(q83()), q83() IS NULL, TO_JSON(q83())");
            assertAnswer(
                "Function Q84 successfully created.",
                "CREATE OR REPLACE FUNCTION q84() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$return {a:1};$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not an ARRAY",
                "SELECT TYPEOF(q84()), q84() IS NULL, TO_JSON(q84())");
            assertAnswer(
                "Function Q85 successfully created.",
                "CREATE OR REPLACE FUNCTION q85() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$return null;$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not an ARRAY",
                "SELECT TYPEOF(q85()), q85() IS NULL, TO_JSON(q85())");
            assertAnswer(
                "Function Q86 successfully created.",
                "CREATE OR REPLACE FUNCTION q86() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$return '[1,2]';$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not an ARRAY",
                "SELECT TYPEOF(q86()), q86() IS NULL, TO_JSON(q86())");
            assertAnswer(
                "Function Q87 successfully created.",
                "CREATE OR REPLACE FUNCTION q87() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$return new Set([1]);$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not an ARRAY",
                "SELECT TYPEOF(q87()), q87() IS NULL, TO_JSON(q87())");
            assertAnswer(
                "Function Q88 successfully created.",
                "CREATE OR REPLACE FUNCTION q88() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$return new Date(0);$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not an ARRAY",
                "SELECT TYPEOF(q88()), q88() IS NULL, TO_JSON(q88())");
            assertAnswer(
                "Function Q89 successfully created.",
                "CREATE OR REPLACE FUNCTION q89() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$return new "
                    + "Uint8Array([1]);$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not an ARRAY",
                "SELECT TYPEOF(q89()), q89() IS NULL, TO_JSON(q89())");
            assertAnswer(
                "Function Q90 successfully created.",
                "CREATE OR REPLACE FUNCTION q90() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$return [[1,undefined]];$$");
            assertAnswer(
                "ARRAY|false|[[1]]",
                "SELECT TYPEOF(q90()), q90() IS NULL, TO_JSON(q90())");
            assertAnswer(
                "Function Q91 successfully created.",
                "CREATE OR REPLACE FUNCTION q91() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$return new Array(3);$$");
            assertAnswer(
                "ARRAY|false|[]",
                "SELECT TYPEOF(q91()), q91() IS NULL, TO_JSON(q91())");
            assertAnswer(
                "Function Q92 successfully created.",
                "CREATE OR REPLACE FUNCTION q92() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$return [undefined];$$");
            assertAnswer(
                "ARRAY|false|[]",
                "SELECT TYPEOF(q92()), q92() IS NULL, TO_JSON(q92())");
            assertAnswer(
                "Function Q93 successfully created.",
                "CREATE OR REPLACE FUNCTION q93() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$return true;$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not an ARRAY",
                "SELECT TYPEOF(q93()), q93() IS NULL, TO_JSON(q93())");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }

    @Test
    public void anObjectAnswerTakesAnyObjectButAnArray() {
        try {
            answer(
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function Q94 successfully created.",
                "CREATE OR REPLACE FUNCTION q94() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return [1,2];$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not an OBJECT",
                "SELECT TYPEOF(q94()), q94() IS NULL, TO_JSON(q94())");
            assertAnswer(
                "Function Q95 successfully created.",
                "CREATE OR REPLACE FUNCTION q95() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return 'x';$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not an OBJECT",
                "SELECT TYPEOF(q95()), q95() IS NULL, TO_JSON(q95())");
            assertAnswer(
                "Function Q96 successfully created.",
                "CREATE OR REPLACE FUNCTION q96() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return 5;$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not an OBJECT",
                "SELECT TYPEOF(q96()), q96() IS NULL, TO_JSON(q96())");
            assertAnswer(
                "Function Q97 successfully created.",
                "CREATE OR REPLACE FUNCTION q97() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return null;$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not an OBJECT",
                "SELECT TYPEOF(q97()), q97() IS NULL, TO_JSON(q97())");
            assertAnswer(
                "Function Q98 successfully created.",
                "CREATE OR REPLACE FUNCTION q98() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return '{\"a\":1}';$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not an OBJECT",
                "SELECT TYPEOF(q98()), q98() IS NULL, TO_JSON(q98())");
            assertAnswer(
                "Function Q99 successfully created.",
                "CREATE OR REPLACE FUNCTION q99() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return new Date(0);$$");
            answer(
                "SELECT TYPEOF(q99()), q99() IS NULL, TO_JSON(q99())");
            assertAnswer(
                "Function Q100 successfully created.",
                "CREATE OR REPLACE FUNCTION q100() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return new "
                    + "Uint8Array([1]);$$");
            assertAnswer(
                "BINARY|false|\"01\"",
                "SELECT TYPEOF(q100()), q100() IS NULL, TO_JSON(q100())");
            assertAnswer(
                "Function Q101 successfully created.",
                "CREATE OR REPLACE FUNCTION q101() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return new Map([[1,"
                    + "2]]);$$");
            assertAnswer(
                "OBJECT|false|{}",
                "SELECT TYPEOF(q101()), q101() IS NULL, TO_JSON(q101())");
            assertAnswer(
                "Function Q102 successfully created.",
                "CREATE OR REPLACE FUNCTION q102() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return /x/g;$$");
            assertAnswer(
                "OBJECT|false|{}",
                "SELECT TYPEOF(q102()), q102() IS NULL, TO_JSON(q102())");
            assertAnswer(
                "Function Q103 successfully created.",
                "CREATE OR REPLACE FUNCTION q103() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return new "
                    + "Error('boom');$$");
            assertAnswer(
                "OBJECT|false|{}",
                "SELECT TYPEOF(q103()), q103() IS NULL, TO_JSON(q103())");
            assertAnswer(
                "Function Q104 successfully created.",
                "CREATE OR REPLACE FUNCTION q104() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return function () "
                    + "{};$$");
            assertAnswer(
                "OBJECT|false|{}",
                "SELECT TYPEOF(q104()), q104() IS NULL, TO_JSON(q104())");
            assertAnswer(
                "Function Q105 successfully created.",
                "CREATE OR REPLACE FUNCTION q105() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return new Date(NaN);$$");
            answer(
                "SELECT TYPEOF(q105()), q105() IS NULL, TO_JSON(q105())");
            assertAnswer(
                "Function Q106 successfully created.",
                "CREATE OR REPLACE FUNCTION q106() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return "
                    + "Object.create(null);$$");
            assertAnswer(
                "OBJECT|false|{}",
                "SELECT TYPEOF(q106()), q106() IS NULL, TO_JSON(q106())");
            assertAnswer(
                "Function Q107 successfully created.",
                "CREATE OR REPLACE FUNCTION q107() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return new "
                    + "String('s');$$");
            assertAnswer(
                "VARCHAR|false|\"s\"",
                "SELECT TYPEOF(q107()), q107() IS NULL, TO_JSON(q107())");
            assertAnswer(
                "Function Q108 successfully created.",
                "CREATE OR REPLACE FUNCTION q108() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return true;$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not an OBJECT",
                "SELECT TYPEOF(q108()), q108() IS NULL, TO_JSON(q108())");
            assertAnswer(
                "Function Q109 successfully created.",
                "CREATE OR REPLACE FUNCTION q109() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return {a: {b: "
                    + "undefined}};$$");
            assertAnswer(
                "OBJECT|false|{\"a\":{}}",
                "SELECT TYPEOF(q109()), q109() IS NULL, TO_JSON(q109())");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }

    @Test
    public void aDateAnswerTakesAnInstant() {
        try {
            answer(
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function Q110 successfully created.",
                "CREATE OR REPLACE FUNCTION q110() RETURNS DATE LANGUAGE JAVASCRIPT AS $$return new Date(0);$$");
            assertAnswer(
                "1970-01-01",
                "SELECT q110()::VARCHAR");
            assertAnswer(
                "Function Q111 successfully created.",
                "CREATE OR REPLACE FUNCTION q111() RETURNS DATE LANGUAGE JAVASCRIPT AS $$return 0;$$");
            assertAnswer(
                "1970-01-01",
                "SELECT q111()::VARCHAR");
            assertAnswer(
                "Function Q112 successfully created.",
                "CREATE OR REPLACE FUNCTION q112() RETURNS DATE LANGUAGE JAVASCRIPT AS $$return '2020-01-02';$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Incorrect Timestamp returned",
                "SELECT q112()::VARCHAR");
            assertAnswer(
                "Function Q113 successfully created.",
                "CREATE OR REPLACE FUNCTION q113() RETURNS DATE LANGUAGE JAVASCRIPT AS $$return new Date(NaN);$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Incorrect Timestamp returned",
                "SELECT q113()::VARCHAR");
            assertAnswer(
                "Function Q114 successfully created.",
                "CREATE OR REPLACE FUNCTION q114() RETURNS DATE LANGUAGE JAVASCRIPT AS $$return 86400000.5;$$");
            assertAnswer(
                "1970-01-02",
                "SELECT q114()::VARCHAR");
            assertAnswer(
                "Function Q115 successfully created.",
                "CREATE OR REPLACE FUNCTION q115() RETURNS DATE LANGUAGE JAVASCRIPT AS $$return true;$$");
            assertAnswer(
                "1970-01-01",
                "SELECT q115()::VARCHAR");
            assertAnswer(
                "Function Q116 successfully created.",
                "CREATE OR REPLACE FUNCTION q116() RETURNS DATE LANGUAGE JAVASCRIPT AS $$return [];$$");
            assertAnswer(
                "1970-01-01",
                "SELECT q116()::VARCHAR");
            assertAnswer(
                "Function Q117 successfully created.",
                "CREATE OR REPLACE FUNCTION q117() RETURNS DATE LANGUAGE JAVASCRIPT AS $$return -1;$$");
            assertAnswer(
                "1969-12-31",
                "SELECT q117()::VARCHAR");
            assertAnswer(
                "Function Q118 successfully created.",
                "CREATE OR REPLACE FUNCTION q118() RETURNS DATE LANGUAGE JAVASCRIPT AS $$return NaN;$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Incorrect Timestamp returned",
                "SELECT q118()::VARCHAR");
            assertAnswer(
                "Function Q119 successfully created.",
                "CREATE OR REPLACE FUNCTION q119() RETURNS TIMESTAMP_NTZ LANGUAGE JAVASCRIPT AS $$return new "
                    + "Date(0);$$");
            assertAnswer(
                "1970/01/01 00.00.00.000 Z",
                "SELECT TO_VARCHAR(q119(), 'YYYY/MM/DD HH24.MI.SS.FF3 TZHTZM')");
            assertAnswer(
                "Function Q120 successfully created.",
                "CREATE OR REPLACE FUNCTION q120() RETURNS TIMESTAMP_NTZ LANGUAGE JAVASCRIPT AS $$return 0;$$");
            assertAnswer(
                "1970/01/01 00.00.00.000 Z",
                "SELECT TO_VARCHAR(q120(), 'YYYY/MM/DD HH24.MI.SS.FF3 TZHTZM')");
            assertAnswer(
                "Function Q121 successfully created.",
                "CREATE OR REPLACE FUNCTION q121() RETURNS TIMESTAMP_NTZ LANGUAGE JAVASCRIPT AS $$return "
                    + "'2020-01-02';$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Incorrect Timestamp returned",
                "SELECT TO_VARCHAR(q121(), 'YYYY/MM/DD HH24.MI.SS.FF3 TZHTZM')");
            assertAnswer(
                "Function Q122 successfully created.",
                "CREATE OR REPLACE FUNCTION q122() RETURNS TIMESTAMP_NTZ LANGUAGE JAVASCRIPT AS $$return new "
                    + "Date(NaN);$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Incorrect Timestamp returned",
                "SELECT TO_VARCHAR(q122(), 'YYYY/MM/DD HH24.MI.SS.FF3 TZHTZM')");
            assertAnswer(
                "Function Q123 successfully created.",
                "CREATE OR REPLACE FUNCTION q123() RETURNS TIMESTAMP_NTZ LANGUAGE JAVASCRIPT AS $$return 1.5;$$");
            assertAnswer(
                "1970/01/01 00.00.00.001 Z",
                "SELECT TO_VARCHAR(q123(), 'YYYY/MM/DD HH24.MI.SS.FF3 TZHTZM')");
            assertAnswer(
                "Function Q124 successfully created.",
                "CREATE OR REPLACE FUNCTION q124() RETURNS TIMESTAMP_NTZ LANGUAGE JAVASCRIPT AS $$return null;$$");
            assertAnswer(
                "null",
                "SELECT TO_VARCHAR(q124(), 'YYYY/MM/DD HH24.MI.SS.FF3 TZHTZM')");
            assertAnswer(
                "Function Q125 successfully created.",
                "CREATE OR REPLACE FUNCTION q125() RETURNS TIMESTAMP_LTZ LANGUAGE JAVASCRIPT AS $$return new "
                    + "Date(0);$$");
            assertAnswer(
                "1970/01/01 00.00.00.000 Z",
                "SELECT TO_VARCHAR(q125(), 'YYYY/MM/DD HH24.MI.SS.FF3 TZHTZM')");
            assertAnswer(
                "Function Q126 successfully created.",
                "CREATE OR REPLACE FUNCTION q126() RETURNS TIMESTAMP_LTZ LANGUAGE JAVASCRIPT AS $$return 1000;$$");
            assertAnswer(
                "1970/01/01 00.00.01.000 Z",
                "SELECT TO_VARCHAR(q126(), 'YYYY/MM/DD HH24.MI.SS.FF3 TZHTZM')");
            assertAnswer(
                "Function Q127 successfully created.",
                "CREATE OR REPLACE FUNCTION q127() RETURNS TIMESTAMP_TZ LANGUAGE JAVASCRIPT AS $$return new "
                    + "Date(0);$$");
            assertAnswer(
                "1970/01/01 00.00.00.000 Z",
                "SELECT TO_VARCHAR(q127(), 'YYYY/MM/DD HH24.MI.SS.FF3 TZHTZM')");
            assertAnswer(
                "Function Q128 successfully created.",
                "CREATE OR REPLACE FUNCTION q128() RETURNS TIMESTAMP_TZ LANGUAGE JAVASCRIPT AS $$return 1000;$$");
            assertAnswer(
                "1970/01/01 00.00.01.000 Z",
                "SELECT TO_VARCHAR(q128(), 'YYYY/MM/DD HH24.MI.SS.FF3 TZHTZM')");
            assertAnswer(
                "REFUSED Language JAVASCRIPT does not support type 'TIME(9)' for argument or return type.",
                "CREATE OR REPLACE FUNCTION q129() RETURNS TIME LANGUAGE JAVASCRIPT AS $$return new "
                    + "Date(3600000);$$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function Q129.",
                "SELECT q129()::VARCHAR");
            assertAnswer(
                "REFUSED Language JAVASCRIPT does not support type 'TIME(9)' for argument or return type.",
                "CREATE OR REPLACE FUNCTION q130() RETURNS TIME LANGUAGE JAVASCRIPT AS $$return 3600000;$$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function Q130.",
                "SELECT q130()::VARCHAR");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }

    @Test
    public void aBinaryAnswerTakesAUint8Array() {
        try {
            answer(
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function Q131 successfully created.",
                "CREATE OR REPLACE FUNCTION q131() RETURNS BINARY LANGUAGE JAVASCRIPT AS $$return new "
                    + "Uint8Array([1,2]);$$");
            assertAnswer(
                "0102",
                "SELECT HEX_ENCODE(q131())");
            assertAnswer(
                "Function Q132 successfully created.",
                "CREATE OR REPLACE FUNCTION q132() RETURNS BINARY LANGUAGE JAVASCRIPT AS $$return '0102';$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not a BINARY",
                "SELECT HEX_ENCODE(q132())");
            assertAnswer(
                "Function Q133 successfully created.",
                "CREATE OR REPLACE FUNCTION q133() RETURNS BINARY LANGUAGE JAVASCRIPT AS $$return new "
                    + "Uint8Array(0);$$");
            assertAnswer(
                "",
                "SELECT HEX_ENCODE(q133())");
            assertAnswer(
                "Function Q134 successfully created.",
                "CREATE OR REPLACE FUNCTION q134() RETURNS BINARY LANGUAGE JAVASCRIPT AS $$return new "
                    + "Int8Array([-1]);$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not a BINARY",
                "SELECT HEX_ENCODE(q134())");
            assertAnswer(
                "Function Q135 successfully created.",
                "CREATE OR REPLACE FUNCTION q135() RETURNS BINARY LANGUAGE JAVASCRIPT AS $$return new "
                    + "Uint8ClampedArray([300]);$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not a BINARY",
                "SELECT HEX_ENCODE(q135())");
            assertAnswer(
                "Function Q136 successfully created.",
                "CREATE OR REPLACE FUNCTION q136() RETURNS BINARY LANGUAGE JAVASCRIPT AS $$return [1,2];$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not a BINARY",
                "SELECT HEX_ENCODE(q136())");
            assertAnswer(
                "Function Q137 successfully created.",
                "CREATE OR REPLACE FUNCTION q137() RETURNS BINARY LANGUAGE JAVASCRIPT AS $$return null;$$");
            assertAnswer(
                "null",
                "SELECT HEX_ENCODE(q137())");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }

    @Test
    public void anAnswerOfHolesDatesAndBytes() {
        assertAnswer(
            "Function R0 successfully created.",
            "CREATE OR REPLACE FUNCTION r0() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return [1, undefined, 2]; "
                + "$$");
        assertAnswer(
            "[1,undefined,2]",
            "SELECT r0()");
        assertAnswer(
            "null|true|3",
            "SELECT TYPEOF(r0()[1]), r0()[1] IS NULL, ARRAY_SIZE(r0())");
        assertAnswer(
            "Function R1 successfully created.",
            "CREATE OR REPLACE FUNCTION r1() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return [1, null, 2]; $$");
        assertAnswer(
            "[1,null,2]",
            "SELECT r1()");
        assertAnswer(
            "NULL_VALUE|false",
            "SELECT TYPEOF(r1()[1]), r1()[1] IS NULL");
        assertAnswer(
            "Function R2 successfully created.",
            "CREATE OR REPLACE FUNCTION r2() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return {a: undefined, b: "
                + "null, c: 1}; $$");
        assertAnswer(
            "{\"b\":null,\"c\":1}",
            "SELECT r2()");
        assertAnswer(
            "Function R3 successfully created.",
            "CREATE OR REPLACE FUNCTION r3() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return undefined; $$");
        assertAnswer(
            "true|null",
            "SELECT r3() IS NULL, TYPEOF(r3())");
        assertAnswer(
            "Function R4 successfully created.",
            "CREATE OR REPLACE FUNCTION r4() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return null; $$");
        assertAnswer(
            "false|NULL_VALUE",
            "SELECT r4() IS NULL, TYPEOF(r4())");
        assertAnswer(
            "Function R5 successfully created.",
            "CREATE OR REPLACE FUNCTION r5() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return new Date(86400000); "
                + "$$");
        answer(
            "SELECT TYPEOF(r5()), DATE_PART(epoch_second, r5()::TIMESTAMP_NTZ)");
        assertAnswer(
            "Function R6 successfully created.",
            "CREATE OR REPLACE FUNCTION r6() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return [new Date(86400000),"
                + " 1.5, NaN, new Uint8Array([255, 1]), function () {}, 's', true, 3, 1e21, -Infinity]; $$");
        assertAnswer(
            "TIMESTAMP_LTZ|DOUBLE|DOUBLE|BINARY|OBJECT|VARCHAR|BOOLEAN|INTEGER|DOUBLE|DOUBLE|10",
            "SELECT TYPEOF(r6()[0]), TYPEOF(r6()[1]), TYPEOF(r6()[2]), TYPEOF(r6()[3]), TYPEOF(r6()[4]), "
                + "TYPEOF(r6()[5]), TYPEOF(r6()[6]), TYPEOF(r6()[7]), TYPEOF(r6()[8]), TYPEOF(r6()[9]), "
                + "ARRAY_SIZE(r6())");
        assertAnswer(
            "1.5|NaN|FF01|{}|1.0E21|-Infinity",
            "SELECT r6()[1], r6()[2], r6()[3], r6()[4], r6()[8], r6()[9]");
        assertAnswer(
            "Function R7 successfully created.",
            "CREATE OR REPLACE FUNCTION r7() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$ return [1, undefined]; $$");
        assertAnswer(
            "[1]|1",
            "SELECT r7(), ARRAY_SIZE(r7())");
        assertAnswer(
            "Function R7B successfully created.",
            "CREATE OR REPLACE FUNCTION r7b() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$ return [undefined, 1, "
                + "undefined, undefined]; $$");
        assertAnswer(
            "[undefined,1]|2",
            "SELECT r7b(), ARRAY_SIZE(r7b())");
        assertAnswer(
            "Function R7C successfully created.",
            "CREATE OR REPLACE FUNCTION r7c() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return [1, undefined]; $$");
        assertAnswer(
            "[1]|1",
            "SELECT r7c(), ARRAY_SIZE(r7c())");
        assertAnswer(
            "Function R7D successfully created.",
            "CREATE OR REPLACE FUNCTION r7d() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ var a = [1]; a[3] = 4; "
                + "return a; $$");
        assertAnswer(
            "[1,undefined,undefined,4]|4",
            "SELECT r7d(), ARRAY_SIZE(r7d())");
        assertAnswer(
            "Function R8 successfully created.",
            "CREATE OR REPLACE FUNCTION r8() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$ return 5; $$");
        assertAnswer(
            "REFUSED JavaScript execution error: Return value is not an ARRAY",
            "SELECT r8()");
        assertAnswer(
            "Function R8B successfully created.",
            "CREATE OR REPLACE FUNCTION r8b() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$ return 'x'; $$");
        assertAnswer(
            "REFUSED JavaScript execution error: Return value is not an ARRAY",
            "SELECT r8b()");
        assertAnswer(
            "Function R8C successfully created.",
            "CREATE OR REPLACE FUNCTION r8c() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$ return {a: 1}; $$");
        assertAnswer(
            "REFUSED JavaScript execution error: Return value is not an ARRAY",
            "SELECT r8c()");
        assertAnswer(
            "Function R9 successfully created.",
            "CREATE OR REPLACE FUNCTION r9() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$ return [1]; $$");
        assertAnswer(
            "REFUSED JavaScript execution error: Return value is not an OBJECT",
            "SELECT r9()");
        assertAnswer(
            "Function R9B successfully created.",
            "CREATE OR REPLACE FUNCTION r9b() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$ return 5; $$");
        assertAnswer(
            "REFUSED JavaScript execution error: Return value is not an OBJECT",
            "SELECT r9b()");
        assertAnswer(
            "Function R10 successfully created.",
            "CREATE OR REPLACE FUNCTION r10() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$ return {a: [undefined, 1], "
                + "d: new Date(0), n: null, u: undefined}; $$");
        answer(
            "SELECT r10()::VARCHAR NOT LIKE '%1970%', TYPEOF(r10():d), r10():a, TYPEOF(r10():n), r10():u IS NULL");
        assertAnswer(
            "Function R11 successfully created.",
            "CREATE OR REPLACE FUNCTION r11() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$ return undefined; $$");
        assertAnswer(
            "true",
            "SELECT r11() IS NULL");
        assertAnswer(
            "Function R12 successfully created.",
            "CREATE OR REPLACE FUNCTION r12() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$ return null; $$");
        assertAnswer(
            "REFUSED JavaScript execution error: Return value is not an ARRAY",
            "SELECT r12() IS NULL");
        assertAnswer(
            "Function R13 successfully created.",
            "CREATE OR REPLACE FUNCTION r13() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$ return null; $$");
        assertAnswer(
            "REFUSED JavaScript execution error: Return value is not an OBJECT",
            "SELECT r13() IS NULL");
        assertAnswer(
            "Function R14 successfully created.",
            "CREATE OR REPLACE FUNCTION r14() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return 1.5; $$");
        assertAnswer(
            "1.5|DOUBLE",
            "SELECT r14(), TYPEOF(r14())");
        assertAnswer(
            "Function R15 successfully created.",
            "CREATE OR REPLACE FUNCTION r15() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return 3; $$");
        assertAnswer(
            "3|INTEGER",
            "SELECT r15(), TYPEOF(r15())");
        assertAnswer(
            "Function R16 successfully created.",
            "CREATE OR REPLACE FUNCTION r16() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return 'txt'; $$");
        assertAnswer(
            "txt|VARCHAR",
            "SELECT r16(), TYPEOF(r16())");
        assertAnswer(
            "Function R17 successfully created.",
            "CREATE OR REPLACE FUNCTION r17() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return true; $$");
        assertAnswer(
            "true|BOOLEAN",
            "SELECT r17(), TYPEOF(r17())");
        assertAnswer(
            "Function R18 successfully created.",
            "CREATE OR REPLACE FUNCTION r18() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return NaN; $$");
        assertAnswer(
            "NaN|DOUBLE",
            "SELECT r18(), TYPEOF(r18())");
        assertAnswer(
            "Function R19 successfully created.",
            "CREATE OR REPLACE FUNCTION r19() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return new "
                + "Uint8Array([255, 1]); $$");
        assertAnswer(
            "FF01|BINARY",
            "SELECT r19(), TYPEOF(r19())");
        assertAnswer(
            "Function R20 successfully created.",
            "CREATE OR REPLACE FUNCTION r20() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return function () { "
                + "return 1; }; $$");
        assertAnswer(
            "{}|OBJECT",
            "SELECT r20(), TYPEOF(r20())");
        assertAnswer(
            "Function R21 successfully created.",
            "CREATE OR REPLACE FUNCTION r21() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return "
                + "12345678901234567890; $$");
        assertAnswer(
            "12345678901234567000|INTEGER",
            "SELECT r21(), TYPEOF(r21())");
    }

    @Test
    public void aValueHandedBackAsItArrived() {
        assertAnswer(
            "Function RV successfully created.",
            "CREATE OR REPLACE FUNCTION rv(v VARIANT) RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return V; $$");
        assertAnswer(
            "[1,undefined,2]",
            "SELECT rv(ARRAY_CONSTRUCT(1, NULL, 2))");
        assertAnswer(
            "[1,null,2]",
            "SELECT rv(PARSE_JSON('[1,null,2]'))");
        assertAnswer(
            "[1]",
            "SELECT rv(ARRAY_CONSTRUCT(1, NULL))");
        assertAnswer(
            "1",
            "SELECT ARRAY_SIZE(rv(ARRAY_CONSTRUCT(1, NULL)))");
        answer(
            "SELECT TYPEOF(rv(TO_VARIANT('2024-01-01'::DATE))), rv(TO_VARIANT('2024-01-01'::DATE))::DATE = "
                + "'2024-01-01'::DATE");
        assertAnswer(
            "BINARY|FF01",
            "SELECT TYPEOF(rv(TO_VARIANT(TO_BINARY('FF01', 'HEX')))), rv(TO_VARIANT(TO_BINARY('FF01', 'HEX')))");
        assertAnswer(
            "REFUSED JavaScript execution error: TIME inside VARIANT value - not supported in function RV with "
                + "handler RV",
            "SELECT TYPEOF(rv(TO_VARIANT('10:11:12'::TIME))), rv(TO_VARIANT('10:11:12'::TIME))");
        assertAnswer(
            "DOUBLE|INTEGER|DOUBLE|INTEGER|3",
            "SELECT TYPEOF(rv(1.5)), TYPEOF(rv(7)), TYPEOF(rv(1.5::FLOAT)), TYPEOF(rv(3::FLOAT)), rv(3::FLOAT)");
        assertAnswer(
            "true|NULL_VALUE|false",
            "SELECT rv(NULL) IS NULL, TYPEOF(rv(PARSE_JSON('null'))), rv(PARSE_JSON('null')) IS NULL");
        assertAnswer(
            "Function RA successfully created.",
            "CREATE OR REPLACE FUNCTION ra(a ARRAY) RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$ return A; $$");
        assertAnswer(
            "[1,undefined,2]",
            "SELECT ra(ARRAY_CONSTRUCT(1, NULL, 2))");
        assertAnswer(
            "[]",
            "SELECT ra(ARRAY_CONSTRUCT(NULL))");
        assertAnswer(
            "1",
            "SELECT ARRAY_SIZE(ra(ARRAY_CONSTRUCT(1, NULL)))");
        assertAnswer(
            "[null,1,null]|3",
            "SELECT ra(PARSE_JSON('[null, 1, null]')::ARRAY), ARRAY_SIZE(ra(PARSE_JSON('[null, 1, null]')::ARRAY))");
        assertAnswer(
            "Function RO successfully created.",
            "CREATE OR REPLACE FUNCTION ro(o OBJECT) RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$ return O; $$");
        assertAnswer(
            "{\"a\":null,\"b\":[undefined,1]}",
            "SELECT ro(OBJECT_CONSTRUCT_KEEP_NULL('a', NULL, 'b', ARRAY_CONSTRUCT(NULL, 1)))");
        assertAnswer(
            "{\"a\":2,\"z\":1}",
            "SELECT ro(OBJECT_CONSTRUCT('z', 1, 'a', 2))");
        assertAnswer(
            "Function RS successfully created.",
            "CREATE OR REPLACE FUNCTION rs(v VARIANT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return V; $$");
        assertAnswer(
            "1,,2",
            "SELECT rs(ARRAY_CONSTRUCT(1, NULL, 2))");
        assertAnswer(
            "[object Object]",
            "SELECT rs(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "t",
            "SELECT rs('t'::VARIANT)");
        assertAnswer(
            "Function RF successfully created.",
            "CREATE OR REPLACE FUNCTION rf() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return 'abc'; $$");
        assertAnswer(
            "NaN",
            "SELECT rf()");
        assertAnswer(
            "Function RN successfully created.",
            "CREATE OR REPLACE FUNCTION rn() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return [1, undefined, {a: "
                + "1}]; $$");
        assertAnswer(
            "1,,[object Object]",
            "SELECT rn()");
        assertAnswer(
            "Function RN2 successfully created.",
            "CREATE OR REPLACE FUNCTION rn2() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return {a: 1}; $$");
        assertAnswer(
            "[object Object]",
            "SELECT rn2()");
    }

    @Test
    public void aTypedValueHandedBack() {
        try {
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function RV successfully created.",
                "CREATE OR REPLACE FUNCTION rv(v VARIANT) RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return V; $$");
            assertAnswer(
                "TIMESTAMP_LTZ",
                "SELECT TYPEOF(rv(TO_VARIANT('2024-01-01'::DATE)))");
            assertAnswer(
                "1704067200",
                "SELECT DATE_PART(epoch_second, rv(TO_VARIANT('2024-01-01'::DATE))::TIMESTAMP_LTZ)");
            assertAnswer(
                "TIMESTAMP_LTZ|TIMESTAMP_LTZ",
                "SELECT TYPEOF(rv(ARRAY_CONSTRUCT('2024-01-01'::DATE))[0]), TYPEOF(rv(OBJECT_CONSTRUCT('d', "
                    + "'2024-03-05 10:11:12.345'::TIMESTAMP_NTZ)):d)");
            assertAnswer(
                "TIMESTAMP_LTZ",
                "SELECT TYPEOF(rv(TO_VARIANT('2024-03-05 10:11:12.345 +05:00'::TIMESTAMP_TZ)))");
            assertAnswer(
                "BINARY|[\"FF01\"]",
                "SELECT TYPEOF(rv(ARRAY_CONSTRUCT(TO_BINARY('FF01', 'HEX')))[0]), "
                    + "rv(ARRAY_CONSTRUCT(TO_BINARY('FF01', 'HEX')))");
            assertAnswer(
                "Function RD successfully created.",
                "CREATE OR REPLACE FUNCTION rd() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return [new "
                    + "Date(86400000), new Uint8Array([255, 1])]; $$");
            assertAnswer(
                "TIMESTAMP_LTZ|BINARY|86400|FF01",
                "SELECT TYPEOF(rd()[0]), TYPEOF(rd()[1]), DATE_PART(epoch_second, rd()[0]::TIMESTAMP_LTZ), rd()[1]");
            assertAnswer(
                "Function RDA successfully created.",
                "CREATE OR REPLACE FUNCTION rda() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$ return [new "
                    + "Date(86400000), new Uint8Array([255, 1])]; $$");
            assertAnswer(
                "TIMESTAMP_LTZ|BINARY|86400",
                "SELECT TYPEOF(rda()[0]), TYPEOF(rda()[1]), DATE_PART(epoch_second, rda()[0]::TIMESTAMP_LTZ)");
            assertAnswer(
                "Function RDO successfully created.",
                "CREATE OR REPLACE FUNCTION rdo() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$ return {d: new "
                    + "Date(86400000), b: new Uint8Array([255, 1])}; $$");
            assertAnswer(
                "TIMESTAMP_LTZ|BINARY|FF01",
                "SELECT TYPEOF(rdo():d), TYPEOF(rdo():b), rdo():b");
            assertAnswer(
                "Function ROD successfully created.",
                "CREATE OR REPLACE FUNCTION rod() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$ return new "
                    + "Date(86400000); $$");
            assertAnswer(
                "TIMESTAMP_LTZ",
                "SELECT TYPEOF(rod())");
            assertAnswer(
                "Function RSYM successfully created.",
                "CREATE OR REPLACE FUNCTION rsym() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return Symbol('x'); "
                    + "$$");
            assertAnswer(
                "false|NULL_VALUE",
                "SELECT rsym() IS NULL, TYPEOF(rsym())");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }

    @Test
    public void aScalarAnswerOfAnyValue() {
        assertAnswer(
            "Function RBO3 successfully created.",
            "CREATE OR REPLACE FUNCTION rbo3() RETURNS BOOLEAN LANGUAGE JAVASCRIPT AS $$ return 'x'; $$");
        assertAnswer(
            "true",
            "SELECT rbo3()");
        assertAnswer(
            "Function RBO4 successfully created.",
            "CREATE OR REPLACE FUNCTION rbo4() RETURNS BOOLEAN LANGUAGE JAVASCRIPT AS $$ return 0; $$");
        assertAnswer(
            "false",
            "SELECT rbo4()");
        assertAnswer(
            "Function RBO5 successfully created.",
            "CREATE OR REPLACE FUNCTION rbo5() RETURNS BOOLEAN LANGUAGE JAVASCRIPT AS $$ return ''; $$");
        assertAnswer(
            "false",
            "SELECT rbo5()");
        assertAnswer(
            "Function RBO6 successfully created.",
            "CREATE OR REPLACE FUNCTION rbo6() RETURNS BOOLEAN LANGUAGE JAVASCRIPT AS $$ return undefined; $$");
        assertAnswer(
            "true",
            "SELECT rbo6() IS NULL");
        assertAnswer(
            "Function RBO7 successfully created.",
            "CREATE OR REPLACE FUNCTION rbo7() RETURNS BOOLEAN LANGUAGE JAVASCRIPT AS $$ return {}; $$");
        assertAnswer(
            "true",
            "SELECT rbo7()");
        assertAnswer(
            "Function RS1 successfully created.",
            "CREATE OR REPLACE FUNCTION rs1() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return 5; $$");
        assertAnswer(
            "5|VARCHAR",
            "SELECT rs1(), TYPEOF(rs1()::VARIANT)");
        assertAnswer(
            "Function RS2 successfully created.",
            "CREATE OR REPLACE FUNCTION rs2() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return true; $$");
        assertAnswer(
            "true",
            "SELECT rs2()");
        assertAnswer(
            "Function RS3 successfully created.",
            "CREATE OR REPLACE FUNCTION rs3() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return 1.5; $$");
        assertAnswer(
            "1.5",
            "SELECT rs3()");
        assertAnswer(
            "Function RS4 successfully created.",
            "CREATE OR REPLACE FUNCTION rs4() RETURNS VARCHAR(2) LANGUAGE JAVASCRIPT AS $$ return 'abcdef'; $$");
        assertAnswer(
            "REFUSED String 'abcdef' is too long and would be truncated",
            "SELECT rs4()");
        assertAnswer(
            "Function RS5 successfully created.",
            "CREATE OR REPLACE FUNCTION rs5() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return new "
                + "Date(0).getTime(); $$");
        assertAnswer(
            "0",
            "SELECT rs5()");
        assertAnswer(
            "Function RFL successfully created.",
            "CREATE OR REPLACE FUNCTION rfl() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return 3; $$");
        assertAnswer(
            "3.0|DOUBLE|3",
            "SELECT rfl(), TYPEOF(rfl()::VARIANT), rfl()::VARCHAR");
        assertAnswer(
            "Function RFL2 successfully created.",
            "CREATE OR REPLACE FUNCTION rfl2() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return '12'; $$");
        assertAnswer(
            "12",
            "SELECT rfl2()::VARCHAR");
        assertAnswer(
            "Function RFL3 successfully created.",
            "CREATE OR REPLACE FUNCTION rfl3() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return true; $$");
        assertAnswer(
            "1",
            "SELECT rfl3()::VARCHAR");
        assertAnswer(
            "Function RFL4 successfully created.",
            "CREATE OR REPLACE FUNCTION rfl4() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return undefined; $$");
        assertAnswer(
            "true",
            "SELECT rfl4() IS NULL");
        assertAnswer(
            "Function RFL5 successfully created.",
            "CREATE OR REPLACE FUNCTION rfl5() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return null; $$");
        assertAnswer(
            "true",
            "SELECT rfl5() IS NULL");
        assertAnswer(
            "Function RST successfully created.",
            "CREATE OR REPLACE FUNCTION rst() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return null; $$");
        assertAnswer(
            "true",
            "SELECT rst() IS NULL");
        assertAnswer(
            "Function RST2 successfully created.",
            "CREATE OR REPLACE FUNCTION rst2() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return undefined; $$");
        assertAnswer(
            "true",
            "SELECT rst2() IS NULL");
        assertAnswer(
            "Function RST3 successfully created.",
            "CREATE OR REPLACE FUNCTION rst3() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return -0; $$");
        assertAnswer(
            "0",
            "SELECT rst3()");
        assertAnswer(
            "Function RST4 successfully created.",
            "CREATE OR REPLACE FUNCTION rst4() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return 1e21; $$");
        assertAnswer(
            "1e+21",
            "SELECT rst4()");
        assertAnswer(
            "Function RST5 successfully created.",
            "CREATE OR REPLACE FUNCTION rst5() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return "
                + "123456789012345680000; $$");
        assertAnswer(
            "123456789012345680000",
            "SELECT rst5()");
        assertAnswer(
            "Function RST6 successfully created.",
            "CREATE OR REPLACE FUNCTION rst6() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return {toString: "
                + "function () { return 'custom'; }}; $$");
        assertAnswer(
            "custom",
            "SELECT rst6()");
        assertAnswer(
            "Function RST7 successfully created.",
            "CREATE OR REPLACE FUNCTION rst7() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return /x/g; $$");
        assertAnswer(
            "/x/g",
            "SELECT rst7()");
        assertAnswer(
            "Function RST8 successfully created.",
            "CREATE OR REPLACE FUNCTION rst8() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return new "
                + "Error('boom'); $$");
        assertAnswer(
            "Error: boom",
            "SELECT rst8()");
        assertAnswer(
            "Function RST9 successfully created.",
            "CREATE OR REPLACE FUNCTION rst9() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return [1, [2, 3], null, "
                + "undefined, 4]; $$");
        assertAnswer(
            "1,2,3,,,4",
            "SELECT rst9()");
        assertAnswer(
            "Function RVN successfully created.",
            "CREATE OR REPLACE FUNCTION rvn() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return 0.1 + 0.2; $$");
        answer(
            "SELECT rvn(), TYPEOF(rvn()), rvn() = 0.3::FLOAT, rvn()::FLOAT = 0.1::FLOAT + 0.2::FLOAT");
        assertAnswer(
            "Function RVO successfully created.",
            "CREATE OR REPLACE FUNCTION rvo() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return {z: 1, a: {y: [1, "
                + "{q: undefined}], b: 2}}; $$");
        assertAnswer(
            "{\"a\":{\"b\":2,\"y\":[1,{}]},\"z\":1}",
            "SELECT rvo()");
        assertAnswer(
            "Function RVS successfully created.",
            "CREATE OR REPLACE FUNCTION rvs() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return '[1,2]'; $$");
        assertAnswer(
            "[1,2]|VARCHAR",
            "SELECT rvs(), TYPEOF(rvs())");
        assertAnswer(
            "Function RVS2 successfully created.",
            "CREATE OR REPLACE FUNCTION rvs2() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return ''; $$");
        assertAnswer(
            "VARCHAR|false",
            "SELECT TYPEOF(rvs2()), rvs2() IS NULL");
        assertAnswer(
            "Function RVB successfully created.",
            "CREATE OR REPLACE FUNCTION rvb() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return false; $$");
        assertAnswer(
            "false|BOOLEAN",
            "SELECT rvb(), TYPEOF(rvb())");
        assertAnswer(
            "Function RVZ successfully created.",
            "CREATE OR REPLACE FUNCTION rvz() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return -0; $$");
        assertAnswer(
            "0|INTEGER",
            "SELECT rvz(), TYPEOF(rvz())");
        assertAnswer(
            "Function RVE successfully created.",
            "CREATE OR REPLACE FUNCTION rve() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return []; $$");
        assertAnswer(
            "[]|ARRAY",
            "SELECT rve(), TYPEOF(rve())");
        assertAnswer(
            "Function RVO2 successfully created.",
            "CREATE OR REPLACE FUNCTION rvo2() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return {}; $$");
        assertAnswer(
            "{}|OBJECT",
            "SELECT rvo2(), TYPEOF(rvo2())");
    }

    @Test
    public void moreValuesByDeclaredType() {
        try {
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function FQ34 successfully created.",
                "CREATE OR REPLACE FUNCTION fq34() RETURNS DATE LANGUAGE JAVASCRIPT AS $$return '5';$$");
            assertAnswer(
                "1970-01-01",
                "SELECT fq34()::VARCHAR");
            assertAnswer(
                "Function FQ35 successfully created.",
                "CREATE OR REPLACE FUNCTION fq35() RETURNS DATE LANGUAGE JAVASCRIPT AS $$return ' 7 ';$$");
            assertAnswer(
                "1970-01-01",
                "SELECT fq35()::VARCHAR");
            assertAnswer(
                "Function FQ36 successfully created.",
                "CREATE OR REPLACE FUNCTION fq36() RETURNS DATE LANGUAGE JAVASCRIPT AS $$return Infinity;$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Incorrect Timestamp returned",
                "SELECT fq36()::VARCHAR");
            assertAnswer(
                "Function FQ37 successfully created.",
                "CREATE OR REPLACE FUNCTION fq37() RETURNS DATE LANGUAGE JAVASCRIPT AS $$return {valueOf: "
                    + "function () { return 86400000; }};$$");
            assertAnswer(
                "1970-01-02",
                "SELECT fq37()::VARCHAR");
            assertAnswer(
                "Function FQ38 successfully created.",
                "CREATE OR REPLACE FUNCTION fq38() RETURNS DATE LANGUAGE JAVASCRIPT AS $$return 1n;$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Incorrect Timestamp returned",
                "SELECT fq38()::VARCHAR");
            assertAnswer(
                "Function FQ39 successfully created.",
                "CREATE OR REPLACE FUNCTION fq39() RETURNS DATE LANGUAGE JAVASCRIPT AS $$return false;$$");
            assertAnswer(
                "1970-01-01",
                "SELECT fq39()::VARCHAR");
            assertAnswer(
                "Function FQ40 successfully created.",
                "CREATE OR REPLACE FUNCTION fq40() RETURNS DATE LANGUAGE JAVASCRIPT AS $$return [86400000];$$");
            assertAnswer(
                "1970-01-02",
                "SELECT fq40()::VARCHAR");
            assertAnswer(
                "Function FQ41 successfully created.",
                "CREATE OR REPLACE FUNCTION fq41() RETURNS TIMESTAMP_NTZ LANGUAGE JAVASCRIPT AS $$return true;$$");
            assertAnswer(
                "1970/01/01 00.00.00.001 Z",
                "SELECT TO_VARCHAR(fq41(), 'YYYY/MM/DD HH24.MI.SS.FF3 TZHTZM')");
            assertAnswer(
                "Function FQ42 successfully created.",
                "CREATE OR REPLACE FUNCTION fq42() RETURNS TIMESTAMP_NTZ LANGUAGE JAVASCRIPT AS $$return '1000';$$");
            assertAnswer(
                "1970/01/01 00.00.01.000 Z",
                "SELECT TO_VARCHAR(fq42(), 'YYYY/MM/DD HH24.MI.SS.FF3 TZHTZM')");
            assertAnswer(
                "Function FQ43 successfully created.",
                "CREATE OR REPLACE FUNCTION fq43() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return {get a() { "
                    + "throw new Error('g'); }};$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Late exception in return value conversion",
                "SELECT TYPEOF(fq43()), fq43() IS NULL, TO_JSON(fq43())");
            assertAnswer(
                "Function FQ44 successfully created.",
                "CREATE OR REPLACE FUNCTION fq44() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return [[[[1]]]];$$");
            assertAnswer(
                "ARRAY|false|[[[[1]]]]",
                "SELECT TYPEOF(fq44()), fq44() IS NULL, TO_JSON(fq44())");
            assertAnswer(
                "Function FQ45 successfully created.",
                "CREATE OR REPLACE FUNCTION fq45() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return {a: {b: {c: [1,"
                    + " {d: 2}]}}};$$");
            assertAnswer(
                "OBJECT|false|{\"a\":{\"b\":{\"c\":[1,{\"d\":2}]}}}",
                "SELECT TYPEOF(fq45()), fq45() IS NULL, TO_JSON(fq45())");
            assertAnswer(
                "Function FQ46 successfully created.",
                "CREATE OR REPLACE FUNCTION fq46() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return [, 1];$$");
            assertAnswer(
                "ARRAY|false|[undefined,1]",
                "SELECT TYPEOF(fq46()), fq46() IS NULL, TO_JSON(fq46())");
            assertAnswer(
                "Function FQ47 successfully created.",
                "CREATE OR REPLACE FUNCTION fq47() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return [1, , 3];$$");
            assertAnswer(
                "ARRAY|false|[1,undefined,3]",
                "SELECT TYPEOF(fq47()), fq47() IS NULL, TO_JSON(fq47())");
            assertAnswer(
                "Function FQ48 successfully created.",
                "CREATE OR REPLACE FUNCTION fq48() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return new "
                    + "Int8Array([1, -1]);$$");
            assertAnswer(
                "OBJECT|false|{\"0\":1,\"1\":-1}",
                "SELECT TYPEOF(fq48()), fq48() IS NULL, TO_JSON(fq48())");
            assertAnswer(
                "Function FQ49 successfully created.",
                "CREATE OR REPLACE FUNCTION fq49() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return new "
                    + "Float64Array([1.5]);$$");
            assertAnswer(
                "OBJECT|false|{\"0\":1.500000000000000e+00}",
                "SELECT TYPEOF(fq49()), fq49() IS NULL, TO_JSON(fq49())");
            assertAnswer(
                "Function FQ50 successfully created.",
                "CREATE OR REPLACE FUNCTION fq50() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return new "
                    + "ArrayBuffer(2);$$");
            assertAnswer(
                "OBJECT|false|{}",
                "SELECT TYPEOF(fq50()), fq50() IS NULL, TO_JSON(fq50())");
            assertAnswer(
                "Function FQ51 successfully created.",
                "CREATE OR REPLACE FUNCTION fq51() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return Object('s');$$");
            assertAnswer(
                "VARCHAR|false|\"s\"",
                "SELECT TYPEOF(fq51()), fq51() IS NULL, TO_JSON(fq51())");
            assertAnswer(
                "Function FQ52 successfully created.",
                "CREATE OR REPLACE FUNCTION fq52() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return [new "
                    + "Date(86400000)];$$");
            answer(
                "SELECT TYPEOF(fq52()), fq52() IS NULL, TO_JSON(fq52())");
            assertAnswer(
                "Function FQ53 successfully created.",
                "CREATE OR REPLACE FUNCTION fq53() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return {valueOf: "
                    + "function () { return 5; }};$$");
            assertAnswer(
                "OBJECT|false|{\"valueOf\":{}}",
                "SELECT TYPEOF(fq53()), fq53() IS NULL, TO_JSON(fq53())");
            assertAnswer(
                "Function FQ54 successfully created.",
                "CREATE OR REPLACE FUNCTION fq54() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return {toString: "
                    + "function () { throw new Error('ts'); }};$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Late exception in return value conversion",
                "SELECT fq54()");
            assertAnswer(
                "Function FQ55 successfully created.",
                "CREATE OR REPLACE FUNCTION fq55() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return {valueOf: "
                    + "function () { return 5; }};$$");
            assertAnswer(
                "[object Object]",
                "SELECT fq55()");
            assertAnswer(
                "Function FQ56 successfully created.",
                "CREATE OR REPLACE FUNCTION fq56() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return {toString: "
                    + "function () { return 7; }};$$");
            assertAnswer(
                "7",
                "SELECT fq56()");
            assertAnswer(
                "Function FQ57 successfully created.",
                "CREATE OR REPLACE FUNCTION fq57() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return [undefined, "
                    + "null];$$");
            assertAnswer(
                ",",
                "SELECT fq57()");
            assertAnswer(
                "Function FQ58 successfully created.",
                "CREATE OR REPLACE FUNCTION fq58() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return new Number(5);$$");
            assertAnswer(
                "INTEGER|false|5",
                "SELECT TYPEOF(fq58()), fq58() IS NULL, TO_JSON(fq58())");
            assertAnswer(
                "Function FQ59 successfully created.",
                "CREATE OR REPLACE FUNCTION fq59() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return new "
                    + "Boolean(true);$$");
            assertAnswer(
                "BOOLEAN|false|true",
                "SELECT TYPEOF(fq59()), fq59() IS NULL, TO_JSON(fq59())");
            assertAnswer(
                "Function FQ60 successfully created.",
                "CREATE OR REPLACE FUNCTION fq60() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return new "
                    + "Int8Array([1]);$$");
            assertAnswer(
                "OBJECT|false|{\"0\":1}",
                "SELECT TYPEOF(fq60()), fq60() IS NULL, TO_JSON(fq60())");
            assertAnswer(
                "Function FQ61 successfully created.",
                "CREATE OR REPLACE FUNCTION fq61() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return Symbol('x');$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not an OBJECT",
                "SELECT TYPEOF(fq61()), fq61() IS NULL, TO_JSON(fq61())");
            assertAnswer(
                "Function FQ62 successfully created.",
                "CREATE OR REPLACE FUNCTION fq62() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return 1n;$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not an OBJECT",
                "SELECT TYPEOF(fq62()), fq62() IS NULL, TO_JSON(fq62())");
            assertAnswer(
                "Function FQ63 successfully created.",
                "CREATE OR REPLACE FUNCTION fq63() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$return [new "
                    + "String('s')];$$");
            assertAnswer(
                "ARRAY|false|[\"s\"]",
                "SELECT TYPEOF(fq63()), fq63() IS NULL, TO_JSON(fq63())");
            assertAnswer(
                "Function FQ64 successfully created.",
                "CREATE OR REPLACE FUNCTION fq64() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$return [1n];$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Late exception in return value conversion",
                "SELECT TYPEOF(fq64()), fq64() IS NULL, TO_JSON(fq64())");
            assertAnswer(
                "Function FQ65 successfully created.",
                "CREATE OR REPLACE FUNCTION fq65() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$return Symbol('x');$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not an ARRAY",
                "SELECT TYPEOF(fq65()), fq65() IS NULL, TO_JSON(fq65())");
            assertAnswer(
                "Function FQ66 successfully created.",
                "CREATE OR REPLACE FUNCTION fq66() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return Symbol('x');$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Late exception in return value conversion",
                "SELECT fq66()::VARCHAR");
            assertAnswer(
                "Function FQ67 successfully created.",
                "CREATE OR REPLACE FUNCTION fq67() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return {valueOf: "
                    + "function () { return 5; }};$$");
            assertAnswer(
                "5",
                "SELECT fq67()::VARCHAR");
            assertAnswer(
                "Function FQ68 successfully created.",
                "CREATE OR REPLACE FUNCTION fq68() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return '1,5';$$");
            assertAnswer(
                "NaN",
                "SELECT fq68()::VARCHAR");
            assertAnswer(
                "Function FQ69 successfully created.",
                "CREATE OR REPLACE FUNCTION fq69() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return null;$$");
            assertAnswer(
                "null",
                "SELECT fq69()::VARCHAR");
            assertAnswer(
                "Function FQ70 successfully created.",
                "CREATE OR REPLACE FUNCTION fq70() RETURNS BOOLEAN LANGUAGE JAVASCRIPT AS $$return Symbol('x');$$");
            assertAnswer(
                "true",
                "SELECT fq70()");
            assertAnswer(
                "Function FQ71 successfully created.",
                "CREATE OR REPLACE FUNCTION fq71() RETURNS BOOLEAN LANGUAGE JAVASCRIPT AS $$return null;$$");
            assertAnswer(
                "null",
                "SELECT fq71()");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }
}
