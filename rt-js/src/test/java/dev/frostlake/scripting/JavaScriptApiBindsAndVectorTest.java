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
 * The JavaScript procedure API: a statement's metadata asked before it is executed, execute given binds that are
 * no array or an object (Invalid binds argument, 100183 / P0000), the code and state of the type refusals, and
 * getColumnValue over a VECTOR - its text - and a UUID (all live-verified).
 */
public class JavaScriptApiBindsAndVectorTest extends MeasuredCellsTestSupport {

    @Test
    public void apiRefusals() {
        engine.execute("CREATE OR REPLACE PROCEDURE jm1() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var s = "
            + "snowflake.createStatement({sqlText: 'SELECT 1 AS a'}); return s.getColumnCount() + '|' + "
            + "s.getColumnName(1); $$");
        engine.execute("CREATE OR REPLACE PROCEDURE jm2() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var s = "
            + "snowflake.createStatement({sqlText: 'SELECT 1 AS a'}); return s.getColumnName(5); $$");
        engine.execute("CREATE OR REPLACE PROCEDURE jb1() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ "
            + "snowflake.execute({sqlText: 'SELECT ?', binds: 5}); return 'ok'; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE jb2() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ try { "
            + "snowflake.execute({sqlText: 'SELECT ?', binds: 5}); return 'ok'; } catch (e) { return e.code + '|"
            + "' + e.state + '|' + e.message; } $$");
        engine.execute("CREATE OR REPLACE PROCEDURE jb3() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ try { "
            + "snowflake.execute({sqlText: 'SELECT ?', binds: {a: 1}}); return 'ok'; } catch (e) { return e.code "
            + "+ '|' + e.state + '|' + e.message; } $$");
        engine.execute("CREATE OR REPLACE PROCEDURE jt1() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ try { "
            + "snowflake.createStatement({sqlText: 5}); return 'ok'; } catch (e) { return e.code + '|' + e.state "
            + "+ '|' + e.message; } $$");
        engine.execute("CREATE OR REPLACE PROCEDURE jt2() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ try { "
            + "snowflake.execute({sqlText: 5}); return 'ok'; } catch (e) { return e.code + '|' + e.state + '|' + "
            + "e.message; } $$");
        engine.execute("CREATE OR REPLACE PROCEDURE jt3() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ try { "
            + "snowflake.createStatement({sqlText: 'SELECT 1', binds: 5}); return 'ok'; } catch (e) { return "
            + "e.code + '|' + e.state + '|' + e.message; } $$");
        assertAnswer("REFUSED Execution error in store procedure JM1: | Statement is not executed yet. | At "
                + "Statement.getColumnCount, line 1 position 73 | ",
            "CALL jm1()");
        assertAnswer("REFUSED Execution error in store procedure JM2: | Statement is not executed yet. | At "
                + "Statement.getColumnName, line 1 position 73 | ",
            "CALL jm2()");
        assertAnswer("REFUSED Execution error in store procedure JB1: | Invalid binds argument | At Snowflake.execute,"
                + " line 1 position 11 | ",
            "CALL jb1()");
        assertAnswer("100183|P0000|Invalid binds argument", "CALL jb2()");
        assertAnswer("100183|P0000|Invalid binds argument", "CALL jb3()");
        assertAnswer("100183|P0000|Argument with invalid type number passed", "CALL jt1()");
        assertAnswer("100183|P0000|Argument with invalid type number passed", "CALL jt2()");
        assertAnswer("100183|P0000|Invalid binds argument", "CALL jt3()");
    }

    @Test
    public void columnValues() {
        engine.execute("CREATE OR REPLACE PROCEDURE jv(q VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var rs = "
            + "snowflake.execute({sqlText: Q}); rs.next(); var v = rs.getColumnValue(1); return (v === null ? "
            + "'null' : typeof v) + ':' + (Array.isArray(v) ? 'array:' : '') + JSON.stringify(v) + ':' + "
            + "rs.getColumnValueAsString(1); $$");
        assertAnswer("string:\"[1,2]\":[1,2]", "CALL jv('SELECT [1, 2]::VECTOR(INT, 2)')");
        assertAnswer("string:\"[1.500000,2.000000]\":[1.500000,2.000000]",
            "CALL jv('SELECT [1.5, 2]::VECTOR(FLOAT, 2)')");
        assertShapedAnswer("string:\"<QID>\":<QID>",
            "CALL jv('SELECT ''6a3b7c9e-1f2d-4e5a-8b6c-7d8e9f0a1b2c''::UUID')");
    }
}
