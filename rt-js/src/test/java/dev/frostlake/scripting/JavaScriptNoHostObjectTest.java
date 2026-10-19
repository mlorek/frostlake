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
 * A JavaScript handler reaches nothing of the engine that hosts it. Each argument is a value of the language
 * - a number, a string, a {@code Date}, a {@code Uint8Array}, an array, an object - with no {@code getClass}
 * and no {@code hashCode}, whatever the parameter's type, for a function and a procedure alike; the globals
 * name no {@code Java}, {@code Packages}, {@code Polyglot} or {@code Graal}; and the procedure API's
 * {@code snowflake}, result set and statement are objects of the handler's own world, which print as
 * {@code [object Object]}. Every answer is live-verified.
 */
public class JavaScriptNoHostObjectTest extends MeasuredCellsTestSupport {

    @Test
    public void anArgumentIsAValueOfTheLanguage() {
        assertAnswer(
            "Function H_F successfully created.",
            "CREATE OR REPLACE FUNCTION h_f(x FLOAT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var h = function "
                + "(x) { return x === undefined ? 'undef' : x === null ? 'null' : typeof x + ':' + typeof x.getClass "
                + "+ ':' + typeof x.hashCode + ':' + Object.prototype.toString.call(x); }; return h(X); $$");
        assertAnswer(
            "number:undefined:undefined:[object Number]|number:undefined:undefined:[object Number]|undef",
            "SELECT h_f(0.1), h_f(1), h_f(NULL)");
        assertAnswer(
            "Function H_D successfully created.",
            "CREATE OR REPLACE FUNCTION h_d(x DATE) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var h = function "
                + "(x) { return x === undefined ? 'undef' : x === null ? 'null' : typeof x + ':' + typeof x.getClass "
                + "+ ':' + typeof x.hashCode + ':' + Object.prototype.toString.call(x); }; return h(X); $$");
        assertAnswer(
            "object:undefined:undefined:[object Date]",
            "SELECT h_d('2024-03-05'::DATE)");
        assertAnswer(
            "Function H_T successfully created.",
            "CREATE OR REPLACE FUNCTION h_t(x TIMESTAMP_NTZ) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var h = "
                + "function (x) { return x === undefined ? 'undef' : x === null ? 'null' : typeof x + ':' + typeof "
                + "x.getClass + ':' + typeof x.hashCode + ':' + Object.prototype.toString.call(x); }; return h(X); $$");
        assertAnswer(
            "object:undefined:undefined:[object Date]",
            "SELECT h_t('2024-03-05 10:11:12'::TIMESTAMP_NTZ)");
        assertAnswer(
            "Function H_Z successfully created.",
            "CREATE OR REPLACE FUNCTION h_z(x TIMESTAMP_TZ) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var h = "
                + "function (x) { return x === undefined ? 'undef' : x === null ? 'null' : typeof x + ':' + typeof "
                + "x.getClass + ':' + typeof x.hashCode + ':' + Object.prototype.toString.call(x); }; return h(X); $$");
        assertAnswer(
            "object:undefined:undefined:[object Date]",
            "SELECT h_z('2024-03-05 10:11:12 +01:00'::TIMESTAMP_TZ)");
        assertAnswer(
            "Function H_B successfully created.",
            "CREATE OR REPLACE FUNCTION h_b(x BINARY) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var h = function "
                + "(x) { return x === undefined ? 'undef' : x === null ? 'null' : typeof x + ':' + typeof x.getClass "
                + "+ ':' + typeof x.hashCode + ':' + Object.prototype.toString.call(x); }; return h(X); $$");
        assertAnswer(
            "object:undefined:undefined:[object Uint8Array]",
            "SELECT h_b(TO_BINARY('FF01', 'HEX'))");
        assertAnswer(
            "Function H_S successfully created.",
            "CREATE OR REPLACE FUNCTION h_s(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var h = function "
                + "(x) { return x === undefined ? 'undef' : x === null ? 'null' : typeof x + ':' + typeof x.getClass "
                + "+ ':' + typeof x.hashCode + ':' + Object.prototype.toString.call(x); }; return h(X); $$");
        assertAnswer(
            "string:undefined:undefined:[object String]|string:undefined:undefined:[object "
                + "String]|string:undefined:undefined:[object String]",
            "SELECT h_s('abc'), h_s(1.5), h_s(TRUE)");
        assertAnswer(
            "Function H_BO successfully created.",
            "CREATE OR REPLACE FUNCTION h_bo(x BOOLEAN) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var h = "
                + "function (x) { return x === undefined ? 'undef' : x === null ? 'null' : typeof x + ':' + typeof "
                + "x.getClass + ':' + typeof x.hashCode + ':' + Object.prototype.toString.call(x); }; return h(X); $$");
        assertAnswer(
            "number:undefined:undefined:[object Number]",
            "SELECT h_bo(TRUE)");
        assertAnswer(
            "Function H_V successfully created.",
            "CREATE OR REPLACE FUNCTION h_v(x VARIANT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var h = function "
                + "(x) { return x === undefined ? 'undef' : x === null ? 'null' : typeof x + ':' + typeof x.getClass "
                + "+ ':' + typeof x.hashCode + ':' + Object.prototype.toString.call(x); }; return h(X) + (X !== null "
                + "&& typeof X === 'object' && !(X instanceof Date) && !(X instanceof Uint8Array) ? '/' + "
                + "h(Array.isArray(X) ? X[0] : X.k) : ''); $$");
        assertAnswer(
            "number:undefined:undefined:[object Number]|number:undefined:undefined:[object "
                + "Number]|boolean:undefined:undefined:[object Boolean]|string:undefined:undefined:[object "
                + "String]|null",
            "SELECT h_v(1), h_v(1.5), h_v(TRUE), h_v(TO_VARIANT('s')), h_v(PARSE_JSON('null'))");
        assertAnswer(
            "object:undefined:undefined:[object Array]/number:undefined:undefined:[object "
                + "Number]|object:undefined:undefined:[object Object]/number:undefined:undefined:[object "
                + "Number]|object:undefined:undefined:[object Array]/object:undefined:undefined:[object Date]",
            "SELECT h_v(ARRAY_CONSTRUCT(1.5)), h_v(OBJECT_CONSTRUCT('k', 12345678901234567890)), "
                + "h_v(ARRAY_CONSTRUCT('2024-03-05'::DATE))");
        assertAnswer(
            "object:undefined:undefined:[object Date]|object:undefined:undefined:[object Uint8Array]",
            "SELECT h_v(TO_VARIANT('2024-03-05'::DATE)), h_v(TO_VARIANT(TO_BINARY('FF', 'HEX')))");
        assertAnswer(
            "Function H_A successfully created.",
            "CREATE OR REPLACE FUNCTION h_a(x ARRAY) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var h = function "
                + "(x) { return x === undefined ? 'undef' : x === null ? 'null' : typeof x + ':' + typeof x.getClass "
                + "+ ':' + typeof x.hashCode + ':' + Object.prototype.toString.call(x); }; return h(X) + '/' + "
                + "h(X[0]); $$");
        assertAnswer(
            "object:undefined:undefined:[object Array]/number:undefined:undefined:[object "
                + "Number]|object:undefined:undefined:[object Array]/object:undefined:undefined:[object Object]",
            "SELECT h_a(ARRAY_CONSTRUCT(1.5, 'x')), h_a(ARRAY_CONSTRUCT(OBJECT_CONSTRUCT('a', 1)))");
        assertAnswer(
            "Function H_O successfully created.",
            "CREATE OR REPLACE FUNCTION h_o(x OBJECT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var h = function "
                + "(x) { return x === undefined ? 'undef' : x === null ? 'null' : typeof x + ':' + typeof x.getClass "
                + "+ ':' + typeof x.hashCode + ':' + Object.prototype.toString.call(x); }; return h(X) + '/' + "
                + "h(X.k); $$");
        assertAnswer(
            "object:undefined:undefined:[object Object]/number:undefined:undefined:[object "
                + "Number]|object:undefined:undefined:[object Object]/object:undefined:undefined:[object Array]",
            "SELECT h_o(OBJECT_CONSTRUCT('k', 1.5)), h_o(OBJECT_CONSTRUCT('k', ARRAY_CONSTRUCT(1)))");
    }

    @Test
    public void theGlobalsNameNothingOfTheHost() {
        assertAnswer(
            "Function H_G successfully created.",
            "CREATE OR REPLACE FUNCTION h_g(x FLOAT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return [typeof "
                + "Java, typeof Packages, typeof Polyglot, typeof Graal, typeof java, typeof load, typeof print, "
                + "typeof console, typeof require, typeof process, typeof globalThis.snowflake, typeof Snowflake, "
                + "typeof importScripts, typeof quit, typeof read].join('|'); $$");
        assertAnswer(
            "undefined|undefined|undefined|undefined|undefined|undefined|undefined|object|undefined|undefined|object|function|undefined|undefined|undefined",
            "SELECT h_g(1)");
        assertAnswer(
            "Function H_K successfully created.",
            "CREATE OR REPLACE FUNCTION h_k(x FLOAT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return "
                + "Object.getOwnPropertyNames(globalThis).filter(function (n) { return n.charAt(0) !== "
                + "n.charAt(0).toUpperCase() || n.charAt(0) === '_'; }).sort().join(','); $$");
        assertAnswer(
            "_c_snowflake_logger,console,decodeURI,decodeURIComponent,encodeURI,encodeURIComponent,escape,eval,"
                + "globalThis,isFinite,isNaN,parseFloat,parseInt,snowflake,snowflakeLogger,undefined,unescape",
            "SELECT h_k(1)");
    }

    @Test
    public void aProceduresArgumentsAndApiObjects() {
        assertAnswer(
            "Function HP successfully created.",
            "CREATE OR REPLACE PROCEDURE hp(x FLOAT, d DATE, b BINARY, v VARIANT, s VARCHAR) RETURNS VARCHAR "
                + "LANGUAGE JAVASCRIPT AS $$ var h = function (x) { return x === undefined ? 'undef' : x === null ? "
                + "'null' : typeof x + ':' + typeof x.getClass + ':' + typeof x.hashCode + ':' + "
                + "Object.prototype.toString.call(x); }; return [h(X), h(D), h(B), h(V), h(S)].join('|'); $$");
        assertAnswer(
            "number:undefined:undefined:[object Number]|object:undefined:undefined:[object "
                + "Date]|object:undefined:undefined:[object Uint8Array]|object:undefined:undefined:[object "
                + "Array]|string:undefined:undefined:[object String]",
            "CALL hp(0.1, '2024-03-05'::DATE, TO_BINARY('FF', 'HEX'), ARRAY_CONSTRUCT(1.5), 'abc')");
        assertAnswer(
            "REFUSED Language JAVASCRIPT does not support type 'NUMBER(38,0)' for argument or return type.",
            "CREATE OR REPLACE PROCEDURE hpn(x NUMBER) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var h = function "
                + "(x) { return x === undefined ? 'undef' : x === null ? 'null' : typeof x + ':' + typeof x.getClass "
                + "+ ':' + typeof x.hashCode + ':' + Object.prototype.toString.call(x); }; return h(X); $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function HPN.",
            "CALL hpn(1.5)");
        assertAnswer(
            "Function HAPI successfully created.",
            "CREATE OR REPLACE PROCEDURE hapi() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var h = function (x) { "
                + "return x === undefined ? 'undef' : x === null ? 'null' : typeof x + ':' + typeof x.getClass + ':' "
                + "+ typeof x.hashCode + ':' + Object.prototype.toString.call(x); }; var rs = "
                + "snowflake.execute({sqlText: 'SELECT 1 AS a'}); var st = snowflake.createStatement({sqlText: "
                + "'SELECT 1 AS a'}); return [h(snowflake), h(rs), h(st), typeof st.wait, typeof rs.notify, typeof "
                + "snowflake.toString, typeof rs.equals].join('|'); $$");
        answer(
            "CALL hapi()");
        assertAnswer(
            "Function HSTR successfully created.",
            "CREATE OR REPLACE PROCEDURE hstr() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var rs = "
                + "snowflake.execute({sqlText: 'SELECT 1 AS a'}); var st = snowflake.createStatement({sqlText: "
                + "'SELECT 1 AS a'}); return [String(rs), String(st), String(snowflake), JSON.stringify(rs), "
                + "JSON.stringify(st), JSON.stringify(snowflake)].join('|'); $$");
        assertAnswer(
            "[object Object]|[object Object]|[object Object]|{}|{}|{}",
            "CALL hstr()");
        assertAnswer(
            "Function HKEYS successfully created.",
            "CREATE OR REPLACE PROCEDURE hkeys() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var rs = "
                + "snowflake.execute({sqlText: 'SELECT 1 AS a'}); var st = snowflake.createStatement({sqlText: "
                + "'SELECT 1 AS a'}); var names = function (o) { var p = Object.getPrototypeOf(o); return "
                + "Object.keys(o).sort().join(',') + '/' + (p === null ? 'null' : "
                + "Object.getOwnPropertyNames(p).sort().join(',')); }; return [names(rs), names(st), "
                + "names(snowflake)].join(' | '); $$");
        answer(
            "CALL hkeys()");
        assertAnswer(
            "Function HCOL successfully created.",
            "CREATE OR REPLACE PROCEDURE hcol() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var h = function (x) { "
                + "return x === undefined ? 'undef' : x === null ? 'null' : typeof x + ':' + typeof x.getClass + ':' "
                + "+ typeof x.hashCode + ':' + Object.prototype.toString.call(x); }; var rs = "
                + "snowflake.execute({sqlText: \"SELECT 1 AS a, 1.5::FLOAT AS f, '2024-03-05'::DATE AS d, "
                + "TO_BINARY('FF', 'HEX') AS b, PARSE_JSON('{\\\"k\\\":[1]}') AS v, 12345678901234567890 AS big, 1.5 "
                + "AS dec, TRUE AS t, NULL AS n, 'txt' AS s, '2024-03-05 10:11:12'::TIMESTAMP_NTZ AS ts, "
                + "ARRAY_CONSTRUCT(1, NULL) AS arr\"}); rs.next(); var o = []; for (var i = 1; i <= 12; i++) { "
                + "o.push(h(rs.getColumnValue(i))); } return o.join(' | '); $$");
        answer(
            "CALL hcol()");
        assertAnswer(
            "Function HCOLV successfully created.",
            "CREATE OR REPLACE PROCEDURE hcolv() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var rs = "
                + "snowflake.execute({sqlText: \"SELECT 1 AS a, 1.5::FLOAT AS f, 12345678901234567890 AS big, 1.5 AS "
                + "dec, TRUE AS t, 'txt' AS s, PARSE_JSON('{\\\"k\\\":[1]}') AS v, ARRAY_CONSTRUCT(1, NULL) AS "
                + "arr\"}); rs.next(); var o = []; for (var i = 1; i <= 8; i++) { var c = rs.getColumnValue(i); "
                + "o.push(typeof c === 'object' ? JSON.stringify(c) : String(c)); } return o.join(' | '); $$");
        answer(
            "CALL hcolv()");
    }
}
