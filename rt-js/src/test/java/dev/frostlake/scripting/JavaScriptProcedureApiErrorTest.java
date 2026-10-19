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
 * What a JavaScript procedure's handler catches when a statement it runs fails, and what reaches SQL when it
 * does not catch it. The error is an object that is no {@code Error}, with the statement's {@code code},
 * {@code state} and {@code message} - 904, 42000 and the compilation error for a name that is not there,
 * 100051 and 22012 for a division by zero - and {@code stackTraceTxt}, the place of the API call:
 * {@code Snowflake.execute, line 1 position 130}. Uncaught, it is the CALL's failure: {@code Execution error
 * in store procedure D2:}, the statement's sentence, and {@code At Statement.execute, line 1 position 77}. A
 * misuse of the API - a column read before {@code next()}, a column the result does not have, a statement
 * asked for its columns before it ran, no options - is thrown the same way with the code 100183 and the
 * state P0000. Every answer is live-verified.
 */
public class JavaScriptProcedureApiErrorTest extends MeasuredCellsTestSupport {

    @Test
    public void aFailedStatementIsAnObjectWithCodeAndState() {
        assertAnswer(
            "Function D1 successfully created.",
            "CREATE OR REPLACE PROCEDURE d1() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var esc = function (s) { "
                + "return typeof s === 'string' ? s.replace(/\\n/g, ' / ') : typeof s + ':' + String(s); }; try { "
                + "snowflake.execute({sqlText: 'SELECT nosuch_column_x'}); return 'ran'; } catch (e) { return "
                + "['txt=' + esc(e.stackTraceTxt), 'stack=' + esc(e.stack), 'str=' + esc(String(e)), 'tostr=' + "
                + "esc(e.toString()), 'json=' + JSON.stringify(e).replace(/\\\\n/g, ' / ')].join(' | '); } $$");
        assertAnswer(
            "txt=Snowflake.execute, line 1 position 130 | stack=Snowflake.execute, line 1 position 130 | str=SQL "
                + "compilation error: error line 1 at position 7 / invalid identifier 'NOSUCH_COLUMN_X' | tostr=SQL "
                + "compilation error: error line 1 at position 7 / invalid identifier 'NOSUCH_COLUMN_X' | "
                + "json={\"code\":904,\"state\":\"42000\",\"message\":\"SQL compilation error: error line 1 at "
                + "position 7 / invalid identifier 'NOSUCH_COLUMN_X'\",\"stack\":\"Snowflake.execute, line 1 "
                + "position 130\",\"stackTraceTxt\":\"Snowflake.execute, line 1 position 130\"}",
            "CALL d1()");
        assertAnswer(
            "Function D2 successfully created.",
            "CREATE OR REPLACE PROCEDURE d2() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var st = "
                + "snowflake.createStatement({sqlText: 'SELECT nosuch_column_z'}); st.execute(); return 'ran'; $$");
        assertAnswer(
            "REFUSED Execution error in store procedure D2: | SQL compilation error: error line 1 at position 7 | "
                + "invalid identifier 'NOSUCH_COLUMN_Z' | At Statement.execute, line 1 position 77 | ",
            "CALL d2()");
        assertAnswer(
            "Function D3 successfully created.",
            "CREATE OR REPLACE PROCEDURE d3() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var rs = "
                + "snowflake.execute({sqlText: 'SELECT 1 AS a'}); return rs.getColumnValue(1); $$");
        assertAnswer(
            "REFUSED Execution error in store procedure D3: | ResultSet is empty or not prepared (call next() "
                + "first). | At ResultSet.getColumnValue, line 1 position 67 | ",
            "CALL d3()");
        assertAnswer(
            "Function D4 successfully created.",
            "CREATE OR REPLACE PROCEDURE d4() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var esc = function (s) { "
                + "return typeof s === 'string' ? s.replace(/\\n/g, ' / ') : typeof s + ':' + String(s); }; var rs = "
                + "snowflake.execute({sqlText: 'SELECT 1 AS a'}); try { rs.getColumnValue(1); return 'ran'; } catch "
                + "(e) { var names = []; for (var k in e) { names.push(k); } return [typeof e, e instanceof Error, "
                + "e.constructor.name, names.sort().join(','), esc(e.code), esc(e.state), esc(e.message), 'txt=' + "
                + "esc(e.stackTraceTxt)].join(' | '); } $$");
        assertAnswer(
            "object | false | createExecError | code,message,stack,state,toString | number:100183 | P0000 | "
                + "ResultSet is empty or not prepared (call next() first). | txt=undefined:undefined",
            "CALL d4()");
        assertAnswer(
            "Function D5 successfully created.",
            "CREATE OR REPLACE PROCEDURE d5() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var esc = function (s) { "
                + "return typeof s === 'string' ? s.replace(/\\n/g, ' / ') : typeof s + ':' + String(s); }; var rs = "
                + "snowflake.execute({sqlText: \"SELECT 7 AS a, 'x' AS b\"}); var before = typeof rs.A; rs.next(); "
                + "var after = [typeof rs.A, rs.A, typeof rs.B, rs.B, typeof rs.a, rs.getColumnValue('A'), "
                + "rs.getColumnValue('B'), rs.getColumnValue(2), rs.getRowCount(), rs.getColumnCount()]; var more = "
                + "rs.next(); var gone; try { gone = String(rs.getColumnValue(1)); } catch (e) { gone = 'threw ' + "
                + "esc(e.message); } return [before].concat(after).concat([more, gone]).join(' | '); $$");
        assertAnswer(
            "undefined | number | 7 | string | x | undefined | 7 | x | x | 1 | 2 | false | null",
            "CALL d5()");
        assertAnswer(
            "Function D6 successfully created.",
            "CREATE OR REPLACE PROCEDURE d6() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var esc = function (s) { "
                + "return typeof s === 'string' ? s.replace(/\\n/g, ' / ') : typeof s + ':' + String(s); }; try { "
                + "snowflake.execute(); return 'ran'; } catch (e) { var names = []; for (var k in e) { "
                + "names.push(k); } return [e.constructor.name, names.sort().join(','), esc(e.code), esc(e.state), "
                + "'txt=' + esc(e.stackTraceTxt)].join(' | '); } $$");
        assertAnswer(
            "createExecError | code,message,stack,state,toString | number:100183 | P0000 | txt=undefined:undefined",
            "CALL d6()");
        assertAnswer(
            "Function D7 successfully created.",
            "CREATE OR REPLACE PROCEDURE d7() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var esc = function (s) { "
                + "return typeof s === 'string' ? s.replace(/\\n/g, ' / ') : typeof s + ':' + String(s); }; try { "
                + "snowflake.createStatement({}); return 'made'; } catch (e) { return ['create', esc(e.message), "
                + "esc(e.code), esc(e.state)].join(' | '); } $$");
        assertAnswer(
            "create | empty argument passed | number:100183 | P0000",
            "CALL d7()");
        assertAnswer(
            "Function D8 successfully created.",
            "CREATE OR REPLACE PROCEDURE d8() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var esc = function (s) { "
                + "return typeof s === 'string' ? s.replace(/\\n/g, ' / ') : typeof s + ':' + String(s); }; var st; "
                + "try { st = snowflake.createStatement({sqlText: 'SELECT 1 AS a'}); } catch (e) { return 'create "
                + "threw ' + esc(e.message); } var before = [st.getColumnCount === undefined ? 'nocc' : 'cc']; try { "
                + "before.push(String(st.getColumnCount())); } catch (e) { before.push('threw ' + esc(e.message)); } "
                + "var rs = st.execute(); before.push(st.getColumnCount(), st.getColumnName(1), st.getRowCount(), "
                + "typeof st.getSqlText === 'function' ? st.getSqlText() : 'no getSqlText'); return before.join(' | "
                + "'); $$");
        assertAnswer(
            "cc | threw Statement is not executed yet. | 1 | A | 1 | SELECT 1 AS a",
            "CALL d8()");
        assertAnswer(
            "Function D9 successfully created.",
            "CREATE OR REPLACE PROCEDURE d9() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ "
                + "snowflake.execute({sqlText: 'SELECT 1/0'}); return 'ran'; $$");
        assertAnswer(
            "REFUSED Execution error in store procedure D9: | Division by zero | At Snowflake.execute, line 1 "
                + "position 11 | ",
            "CALL d9()");
        assertAnswer(
            "Function D10 successfully created.",
            "CREATE OR REPLACE PROCEDURE d10() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var esc = function (s) { "
                + "return typeof s === 'string' ? s.replace(/\\n/g, ' / ') : typeof s + ':' + String(s); }; try { "
                + "snowflake.execute({sqlText: 'SELECT 1/0'}); return 'ran'; } catch (e) { return [esc(e.code), "
                + "esc(e.state), esc(e.message)].join(' | '); } $$");
        assertAnswer(
            "number:100051 | 22012 | Division by zero",
            "CALL d10()");
        assertAnswer(
            "Function D11 successfully created.",
            "CREATE OR REPLACE PROCEDURE d11() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var esc = function (s) { "
                + "return typeof s === 'string' ? s.replace(/\\n/g, ' / ') : typeof s + ':' + String(s); }; try { "
                + "snowflake.execute({sqlText: 'SELEC 1'}); return 'ran'; } catch (e) { return [esc(e.code), "
                + "esc(e.state), esc(e.message)].join(' | '); } $$");
        assertAnswer(
            "number:1003 | 42000 | SQL compilation error: / syntax error line 1 at position 0 unexpected 'SELEC'.",
            "CALL d11()");
        assertAnswer(
            "Function D12 successfully created.",
            "CREATE OR REPLACE PROCEDURE d12() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ \n  var rs = "
                + "snowflake.execute({sqlText: 'SELECT 1 AS a'});\n  rs.next();\n  return rs\n    "
                + ".getColumnValue(9);\n $$");
        assertAnswer(
            "REFUSED Execution error in store procedure D12: | Given column name/index does not exist: 9 | At "
                + "ResultSet.getColumnValue, line 5 position 5 | ",
            "CALL d12()");
        assertAnswer(
            "Function D13 successfully created.",
            "CREATE OR REPLACE PROCEDURE d13() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var esc = function (s) { "
                + "return typeof s === 'string' ? s.replace(/\\n/g, ' / ') : typeof s + ':' + String(s); }; try { "
                + "throw snowflake.execute({sqlText: 'SELECT 1 AS a'}); } catch (e) { return typeof e + ':' + "
                + "e.constructor.name; } $$");
        assertAnswer(
            "object:ResultSet",
            "CALL d13()");
        assertAnswer(
            "Function D14 successfully created.",
            "CREATE OR REPLACE PROCEDURE d14() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var esc = function (s) { "
                + "return typeof s === 'string' ? s.replace(/\\n/g, ' / ') : typeof s + ':' + String(s); }; var rs = "
                + "snowflake.execute({sqlText: 'SELECT 1 AS a'}); rs.next(); var out = []; try { "
                + "rs.getColumnValue(); out.push('ran'); } catch (e) { out.push(esc(e.message)); } try { "
                + "rs.getColumnValue(0); out.push('ran'); } catch (e) { out.push(esc(e.message)); } try { "
                + "rs.getColumnValue('a'); out.push('ran'); } catch (e) { out.push(esc(e.message)); } try { "
                + "out.push(String(rs.getColumnValue('A'))); } catch (e) { out.push(esc(e.message)); } try { "
                + "out.push(String(rs.getColumnValue(1.0))); } catch (e) { out.push(esc(e.message)); } try { "
                + "out.push(String(rs.getColumnValue('1'))); } catch (e) { out.push(esc(e.message)); } return "
                + "out.join(' | '); $$");
        assertAnswer(
            "Given column name/index does not exist: undefined | Given column name/index does not exist: 0 | "
                + "Given column name/index does not exist: a | 1 | 1 | Given column name/index does not exist: 1",
            "CALL d14()");
        assertAnswer(
            "Function D15 successfully created.",
            "CREATE OR REPLACE PROCEDURE d15() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var esc = function (s) { "
                + "return typeof s === 'string' ? s.replace(/\\n/g, ' / ') : typeof s + ':' + String(s); }; var rs = "
                + "snowflake.execute({sqlText: 'SELECT 1 AS \"mixed Case\", 2 AS plain'}); rs.next(); var out = []; "
                + "try { out.push(String(rs.getColumnValue('mixed Case'))); } catch (e) { out.push(esc(e.message)); "
                + "} try { out.push(String(rs.getColumnValue('PLAIN'))); } catch (e) { out.push(esc(e.message)); } "
                + "try { out.push(String(rs.getColumnValue('plain'))); } catch (e) { out.push(esc(e.message)); } "
                + "return out.join(' | '); $$");
        assertAnswer(
            "1 | 2 | Given column name/index does not exist: plain",
            "CALL d15()");
    }
}
