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
 * What a JavaScript procedure's result set hands its handler for a column: a number for a NUMBER and a FLOAT -
 * the nearest double for a NUMBER too long for one - a boolean, a string, {@code null} for a NULL and for a
 * VARIANT's JSON null, a {@code Date} for a DATE and a TIMESTAMP, a {@code Uint8Array} for a BINARY, an array or
 * an object for an ARRAY, an OBJECT and a VARIANT that holds one, and a TIME as its text to the nanosecond. A
 * column is read by its number, counted from 1, or by its name exactly as the result names it, and is a member
 * of the result set once a row is read. The result set and the statement are a {@code ResultSet} and a
 * {@code Statement}; a statement's binds reach its text. The session's TIMEZONE is set by the test and unset
 * whatever the assertions make of the statements between. Every answer is live-verified.
 */
public class JavaScriptProcedureApiValueTest extends MeasuredCellsTestSupport {

    @Test
    public void aColumnsValueByItsType() {
        try {
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function C1 successfully created.",
                "CREATE OR REPLACE PROCEDURE c1() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var h = function (x) "
                    + "{ if (x === undefined) { return 'undef'; } if (x === null) { return 'null'; } var t = typeof "
                    + "x; if (t !== 'object' && t !== 'function') { return t + '(' + x + ')'; } var c = "
                    + "x.constructor ? x.constructor.name : 'none'; return t + ':' + c + ':' + "
                    + "Object.prototype.toString.call(x) + ':' + typeof x.getClass + (x instanceof Date ? ':' + "
                    + "x.getTime() : x instanceof Uint8Array ? ':' + Array.prototype.join.call(x, ' ') : "
                    + "Array.isArray(x) ? ':' + JSON.stringify(x) : c === 'Object' ? ':' + JSON.stringify(x) : ''); "
                    + "}; var rs = snowflake.execute({sqlText: \"SELECT 1 AS a, 1.5::FLOAT AS f, "
                    + "12345678901234567890 AS big, 1.5 AS dec, TRUE AS t, NULL AS n, 'txt' AS s, 0 AS z, -2.50 AS "
                    + "neg\"}); rs.next(); var o = []; for (var i = 1; i <= 9; i++) { "
                    + "o.push(h(rs.getColumnValue(i))); } return o.join(' | '); $$");
            assertAnswer(
                "number(1) | number(1.5) | number(12345678901234567000) | number(1.5) | boolean(true) | null | "
                    + "string(txt) | number(0) | number(-2.5)",
                "CALL c1()");
            assertAnswer(
                "Function C2 successfully created.",
                "CREATE OR REPLACE PROCEDURE c2() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var h = function (x) "
                    + "{ if (x === undefined) { return 'undef'; } if (x === null) { return 'null'; } var t = typeof "
                    + "x; if (t !== 'object' && t !== 'function') { return t + '(' + x + ')'; } var c = "
                    + "x.constructor ? x.constructor.name : 'none'; return t + ':' + c + ':' + "
                    + "Object.prototype.toString.call(x) + ':' + typeof x.getClass + (x instanceof Date ? ':' + "
                    + "x.getTime() : x instanceof Uint8Array ? ':' + Array.prototype.join.call(x, ' ') : "
                    + "Array.isArray(x) ? ':' + JSON.stringify(x) : c === 'Object' ? ':' + JSON.stringify(x) : ''); "
                    + "}; var rs = snowflake.execute({sqlText: \"SELECT '2024-03-05'::DATE AS d, '2024-03-05 "
                    + "10:11:12.345'::TIMESTAMP_NTZ AS ts, '2024-03-05 10:11:12.345 +01:00'::TIMESTAMP_TZ AS tz, "
                    + "'2024-03-05 10:11:12.345'::TIMESTAMP_LTZ AS ltz, '10:11:12'::TIME AS tm\"}); rs.next(); var o "
                    + "= []; for (var i = 1; i <= 5; i++) { o.push(h(rs.getColumnValue(i))); } return o.join(' | '); "
                    + "$$");
            assertAnswer(
                "object:Date:[object Date]:undefined:1709596800000 | object:Date:[object "
                    + "Date]:undefined:1709633472345 | object:Date:[object Date]:undefined:1709629872345 | "
                    + "object:Date:[object Date]:undefined:1709633472345 | string(10:11:12.000000000)",
                "CALL c2()");
            assertAnswer(
                "Function C3 successfully created.",
                "CREATE OR REPLACE PROCEDURE c3() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var h = function (x) "
                    + "{ if (x === undefined) { return 'undef'; } if (x === null) { return 'null'; } var t = typeof "
                    + "x; if (t !== 'object' && t !== 'function') { return t + '(' + x + ')'; } var c = "
                    + "x.constructor ? x.constructor.name : 'none'; return t + ':' + c + ':' + "
                    + "Object.prototype.toString.call(x) + ':' + typeof x.getClass + (x instanceof Date ? ':' + "
                    + "x.getTime() : x instanceof Uint8Array ? ':' + Array.prototype.join.call(x, ' ') : "
                    + "Array.isArray(x) ? ':' + JSON.stringify(x) : c === 'Object' ? ':' + JSON.stringify(x) : ''); "
                    + "}; var rs = snowflake.execute({sqlText: \"SELECT TO_BINARY('FF01', 'HEX') AS b, "
                    + "PARSE_JSON('{\\\"k\\\":[1,null]}') AS v, ARRAY_CONSTRUCT(1, 'x') AS arr, OBJECT_CONSTRUCT('a',"
                    + " 1.5) AS obj, PARSE_JSON('null') AS jn, TO_VARIANT(1.5) AS vn, TO_VARIANT('s') AS vs, "
                    + "TO_VARIANT(TRUE) AS vb\"}); rs.next(); var o = []; for (var i = 1; i <= 8; i++) { "
                    + "o.push(h(rs.getColumnValue(i))); } return o.join(' | '); $$");
            assertAnswer(
                "object:Uint8Array:[object Uint8Array]:undefined:255 1 | object:Object:[object "
                    + "Object]:undefined:{\"k\":[1,null]} | object:Array:[object Array]:undefined:[1,\"x\"] | "
                    + "object:Object:[object Object]:undefined:{\"a\":1.5} | null | number(1.5) | string(s) | "
                    + "boolean(true)",
                "CALL c3()");
            assertAnswer(
                "Function C4 successfully created.",
                "CREATE OR REPLACE PROCEDURE c4() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var h = function (x) "
                    + "{ if (x === undefined) { return 'undef'; } if (x === null) { return 'null'; } var t = typeof "
                    + "x; if (t !== 'object' && t !== 'function') { return t + '(' + x + ')'; } var c = "
                    + "x.constructor ? x.constructor.name : 'none'; return t + ':' + c + ':' + "
                    + "Object.prototype.toString.call(x) + ':' + typeof x.getClass + (x instanceof Date ? ':' + "
                    + "x.getTime() : x instanceof Uint8Array ? ':' + Array.prototype.join.call(x, ' ') : "
                    + "Array.isArray(x) ? ':' + JSON.stringify(x) : c === 'Object' ? ':' + JSON.stringify(x) : ''); "
                    + "}; var rs = snowflake.execute({sqlText: \"SELECT '2024-03-05 10:11:12.345'::TIMESTAMP_NTZ AS "
                    + "ts\"}); rs.next(); var x = rs.getColumnValue(1); var names = []; for (var k in x) { "
                    + "names.push(k); } return [h(x), names.sort().join(','), "
                    + "Object.getOwnPropertyNames(Object.getPrototypeOf(x)).sort().join(','), String(x), typeof "
                    + "x.getTime, typeof x.getEpochSeconds].join(' | '); $$");
            answer(
                "CALL c4()");
            assertAnswer(
                "Function C5 successfully created.",
                "CREATE OR REPLACE PROCEDURE c5() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var h = function (x) "
                    + "{ if (x === undefined) { return 'undef'; } if (x === null) { return 'null'; } var t = typeof "
                    + "x; if (t !== 'object' && t !== 'function') { return t + '(' + x + ')'; } var c = "
                    + "x.constructor ? x.constructor.name : 'none'; return t + ':' + c + ':' + "
                    + "Object.prototype.toString.call(x) + ':' + typeof x.getClass + (x instanceof Date ? ':' + "
                    + "x.getTime() : x instanceof Uint8Array ? ':' + Array.prototype.join.call(x, ' ') : "
                    + "Array.isArray(x) ? ':' + JSON.stringify(x) : c === 'Object' ? ':' + JSON.stringify(x) : ''); "
                    + "}; var rs = snowflake.execute({sqlText: \"SELECT 7 AS a, 'x' AS b\"}); var before = h(rs.A); "
                    + "var more = rs.next(); var out = [before, more, h(rs.A), h(rs.B), h(rs.getColumnValue('A')), "
                    + "h(rs.getColumnValue('b')), rs.getRowCount(), rs.getColumnCount(), rs.next()]; return "
                    + "out.join(' | '); $$");
            assertAnswer(
                "REFUSED Execution error in store procedure C5: | Given column name/index does not exist: b | At "
                    + "ResultSet.getColumnValue, line 1 position 720 | ",
                "CALL c5()");
            assertAnswer(
                "Function C6 successfully created.",
                "CREATE OR REPLACE PROCEDURE c6() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var h = function (x) "
                    + "{ if (x === undefined) { return 'undef'; } if (x === null) { return 'null'; } var t = typeof "
                    + "x; if (t !== 'object' && t !== 'function') { return t + '(' + x + ')'; } var c = "
                    + "x.constructor ? x.constructor.name : 'none'; return t + ':' + c + ':' + "
                    + "Object.prototype.toString.call(x) + ':' + typeof x.getClass + (x instanceof Date ? ':' + "
                    + "x.getTime() : x instanceof Uint8Array ? ':' + Array.prototype.join.call(x, ' ') : "
                    + "Array.isArray(x) ? ':' + JSON.stringify(x) : c === 'Object' ? ':' + JSON.stringify(x) : ''); "
                    + "}; var rs = snowflake.execute({sqlText: 'SELECT 7 AS a'}); var st = "
                    + "snowflake.createStatement({sqlText: 'SELECT 7 AS a'}); return [rs.constructor.name, "
                    + "st.constructor.name, snowflake.constructor.name, typeof rs.getClass, typeof st.getClass, "
                    + "String(rs), String(st), Object.prototype.toString.call(rs), rs instanceof Object, typeof "
                    + "rs.next, rs.next.name, String(rs.next).length > 0].join(' | '); $$");
            assertAnswer(
                "ResultSet | Statement | Snowflake | undefined | undefined | [object Object] | [object Object] | "
                    + "[object Object] | true | function |  | true",
                "CALL c6()");
            assertAnswer(
                "Function C7 successfully created.",
                "CREATE OR REPLACE PROCEDURE c7() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var h = function (x) "
                    + "{ if (x === undefined) { return 'undef'; } if (x === null) { return 'null'; } var t = typeof "
                    + "x; if (t !== 'object' && t !== 'function') { return t + '(' + x + ')'; } var c = "
                    + "x.constructor ? x.constructor.name : 'none'; return t + ':' + c + ':' + "
                    + "Object.prototype.toString.call(x) + ':' + typeof x.getClass + (x instanceof Date ? ':' + "
                    + "x.getTime() : x instanceof Uint8Array ? ':' + Array.prototype.join.call(x, ' ') : "
                    + "Array.isArray(x) ? ':' + JSON.stringify(x) : c === 'Object' ? ':' + JSON.stringify(x) : ''); "
                    + "}; try { snowflake.execute({sqlText: 'SELECT nosuch_column_x'}); return 'ran'; } catch (e) { "
                    + "var names = []; for (var k in e) { names.push(k); } return [h(e), typeof e.getClass, typeof "
                    + "e.getMessage, e instanceof Error, names.sort().join(','), e.code, e.state, "
                    + "String(e.message).replace(/\\n/g, ' / '), typeof e.stackTraceTxt, typeof e.stack].join(' | "
                    + "'); } $$");
            assertAnswer(
                "object:createExecQueryError:[object Object]:undefined | undefined | undefined | false | code,"
                    + "message,stack,stackTraceTxt,state,toString | 904 | 42000 | SQL compilation error: error line "
                    + "1 at position 7 / invalid identifier 'NOSUCH_COLUMN_X' | string | string",
                "CALL c7()");
            assertAnswer(
                "Function C8 successfully created.",
                "CREATE OR REPLACE PROCEDURE c8() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var h = function (x) "
                    + "{ if (x === undefined) { return 'undef'; } if (x === null) { return 'null'; } var t = typeof "
                    + "x; if (t !== 'object' && t !== 'function') { return t + '(' + x + ')'; } var c = "
                    + "x.constructor ? x.constructor.name : 'none'; return t + ':' + c + ':' + "
                    + "Object.prototype.toString.call(x) + ':' + typeof x.getClass + (x instanceof Date ? ':' + "
                    + "x.getTime() : x instanceof Uint8Array ? ':' + Array.prototype.join.call(x, ' ') : "
                    + "Array.isArray(x) ? ':' + JSON.stringify(x) : c === 'Object' ? ':' + JSON.stringify(x) : ''); "
                    + "}; try { var st = snowflake.createStatement({sqlText: 'SELECT * FROM no_such_table_x'}); "
                    + "st.execute(); return 'ran'; } catch (e) { return [e.code, e.state, typeof e.getMessage, "
                    + "String(e.message).replace(/\\n/g, ' / '), String(e).replace(/\\n/g, ' / ')].join(' | '); } $$");
            assertAnswer(
                "2003 | 42S02 | undefined | SQL compilation error: / Object 'NO_SUCH_TABLE_X' does not exist or "
                    + "not authorized. This executable runs with owner's rights. The owner role <ROLE> must have at "
                    + "least one privilege granted on TABLE NO_SUCH_TABLE_X. | SQL compilation error: / Object "
                    + "'NO_SUCH_TABLE_X' does not exist or not authorized. This executable runs with owner's rights. "
                    + "The owner role <ROLE> must have at least one privilege granted on TABLE NO_SUCH_TABLE_X.",
                "CALL c8()");
            assertAnswer(
                "Function C9 successfully created.",
                "CREATE OR REPLACE PROCEDURE c9() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var st = "
                    + "snowflake.createStatement({sqlText: 'SELECT ? AS a, ? AS b, ? AS c', binds: [1, 'two', "
                    + "3.5]}); var rs = st.execute(); rs.next(); return [typeof rs.getColumnValue(1), "
                    + "rs.getColumnValue(1), typeof rs.getColumnValue(2), rs.getColumnValue(2), typeof "
                    + "rs.getColumnValue(3), rs.getColumnValue(3), st.getColumnCount(), st.getColumnName(1), "
                    + "st.getRowCount()].join(' | '); $$");
            assertAnswer(
                "number | 1 | string | two | number | 3.5 | 3 | A | 1",
                "CALL c9()");
            assertAnswer(
                "Function C10 successfully created.",
                "CREATE OR REPLACE PROCEDURE c10() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ "
                    + "snowflake.execute({sqlText: 'SELECT nosuch_column_y'}); return 'ran'; $$");
            assertAnswer(
                "REFUSED Execution error in store procedure C10: | SQL compilation error: error line 1 at "
                    + "position 7 | invalid identifier 'NOSUCH_COLUMN_Y' | At Snowflake.execute, line 1 position 11 "
                    + "| ",
                "CALL c10()");
            assertAnswer(
                "Function C11 successfully created.",
                "CREATE OR REPLACE PROCEDURE c11() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var rs = "
                    + "snowflake.execute({sqlText: 'SELECT 1 AS a'}); try { return String(rs.getColumnValue(1)); } "
                    + "catch (e) { return 'threw ' + String(e.message).replace(/\\n/g, ' / '); } $$");
            assertAnswer(
                "threw ResultSet is empty or not prepared (call next() first).",
                "CALL c11()");
            assertAnswer(
                "Function C12 successfully created.",
                "CREATE OR REPLACE PROCEDURE c12() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var rs = "
                    + "snowflake.execute({sqlText: 'SELECT 1 AS a'}); rs.next(); try { return "
                    + "String(rs.getColumnValue(5)); } catch (e) { return 'threw ' + "
                    + "String(e.message).replace(/\\n/g, ' / '); } $$");
            assertAnswer(
                "threw Given column name/index does not exist: 5",
                "CALL c12()");
            assertAnswer(
                "Function C13 successfully created.",
                "CREATE OR REPLACE PROCEDURE c13() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var rs = "
                    + "snowflake.execute({sqlText: 'SELECT 1 AS a'}); rs.next(); try { return "
                    + "String(rs.getColumnValue('nope')); } catch (e) { return 'threw ' + "
                    + "String(e.message).replace(/\\n/g, ' / '); } $$");
            assertAnswer(
                "threw Given column name/index does not exist: nope",
                "CALL c13()");
            assertAnswer(
                "Function C14 successfully created.",
                "CREATE OR REPLACE PROCEDURE c14() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ try { "
                    + "snowflake.execute(); return 'ran'; } catch (e) { return [typeof e, e instanceof Error, "
                    + "String(e.message), e.name].join(' | '); } $$");
            assertAnswer(
                "object | false | TypeError: Cannot read properties of undefined (reading 'sqlText') | ",
                "CALL c14()");
            assertAnswer(
                "Function C15 successfully created.",
                "CREATE OR REPLACE PROCEDURE c15() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ try { "
                    + "snowflake.execute({}); return 'ran'; } catch (e) { return [typeof e, e instanceof Error, "
                    + "String(e.message).replace(/\\n/g, ' / '), e.code, e.state].join(' | '); } $$");
            assertAnswer(
                "object | false | empty argument passed | 100183 | P0000",
                "CALL c15()");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }
}
