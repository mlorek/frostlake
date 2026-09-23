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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A JavaScript handler is a function declared under its routine's name, taking the routine's arguments as its
 * parameters. {@code arguments} holds them, no argument is a global, the handler's name is the handler, and
 * {@code this} is the global object — undefined under {@code 'use strict'}. A function sees the account's
 * {@code snowflake}, {@code Snowflake}, {@code snowflakeLogger} and {@code SnowflakeLogger} globals, and a
 * procedure's {@code snowflake} keeps its methods on its prototype. A routine or an argument JavaScript cannot
 * declare is refused at CREATE. Every cell is live-verified.
 */
public class JavaScriptHandlerFunctionTest extends BaseDatabaseTest {

    /** The first row's first cell, "no row", or the refusal. */
    private String answer(final String sql) {
        try {
            for (final Row row : engine.executeQuery(sql).getRows()) {
                return String.valueOf(row.getValue(0));
            }
            return "no row";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    private void assertCells(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    private static String function(final String signature, final String body) {
        return "CREATE OR REPLACE FUNCTION " + signature + " RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$" + body + "$$";
    }

    @Test
    public void aHandlerTakesItsArgumentsAsParameters() {
        engine.execute(function("fa(x FLOAT, \"y\" VARCHAR)", "return [arguments.length, typeof arguments[0], arguments[1],"
            + " typeof X, typeof globalThis.X, typeof y, typeof globalThis.y, Object.getOwnPropertyNames(globalThis).indexOf('X'),"
            + " typeof this, this === globalThis].join('|');"));
        engine.execute(function("fb(x FLOAT)", "X = 5; return [arguments[0], X].join('|');"));
        engine.execute(function("fc(x FLOAT)", "'use strict'; return [typeof this, arguments.length].join('|');"));
        engine.execute(function("fe(x FLOAT)", "function inner() { return X * 2; } return String(inner());"));
        engine.execute(function("fo(o OBJECT, a ARRAY, v VARIANT)", "return [typeof arguments[0], JSON.stringify(arguments[0]),"
            + " Array.isArray(arguments[1]), typeof arguments[2], O.k].join('|');"));
        engine.execute(function("ov2(a FLOAT)", "return 'one:' + OV2.length;"));
        engine.execute(function("ov2(a FLOAT, b FLOAT)", "return 'two:' + OV2.length;"));
        engine.execute("CREATE OR REPLACE FUNCTION rec(n FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS"
            + " $$return N <= 1 ? 1 : N * REC(N - 1);$$");
        assertCells(new String[][] {
            {"SELECT fa(2, 'b')", "2|number|b|number|undefined|string|undefined|-1|object|true"},
            {"SELECT fb(2)", "5|5"},
            {"SELECT fc(2)", "undefined|1"},
            {"SELECT fe(4)", "8"},
            {"SELECT fo(OBJECT_CONSTRUCT('k', 1), ARRAY_CONSTRUCT(1, 2), TO_VARIANT(3))", "object|{\"k\":1}|true|number|1"},
            {"SELECT ov2(1) || ov2(1, 2) || ov2(3)", "one:1two:2one:1"},
        });
        assertEquals(120.0, Double.parseDouble(answer("SELECT rec(5)")), 0.0);
    }

    @Test
    public void aHandlerSeesTheAccountsGlobals() {
        engine.execute(function("fg(x FLOAT, \"y\" VARCHAR)", "return [typeof snowflake, JSON.stringify(Object.getOwnPropertyNames(snowflake)),"
            + " typeof snowflake.log, typeof snowflake.execute, typeof Snowflake, typeof snowflakeLogger, typeof FG,"
            + " String(typeof FG === 'function' ? FG.length : '-')].join('|');"));
        engine.execute(function("fl(x FLOAT)", "snowflake.log('info', 'hello'); return 'logged';"));
        engine.execute(function("fd()", "var names = Object.getOwnPropertyNames(globalThis); return ['FD', 'Snowflake',"
            + " 'SnowflakeLogger', '_c_snowflake_logger', 'snowflake', 'snowflakeLogger', 'console', 'arguments', 'context',"
            + " 'engine'].filter(function (n) { return names.indexOf(n) >= 0; }).join(',');"));
        engine.execute("CREATE OR REPLACE PROCEDURE pg() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return [typeof snowflake,"
            + " JSON.stringify(Object.getOwnPropertyNames(snowflake)), typeof PG, arguments.length].join('|');$$");
        assertCells(new String[][] {
            {"SELECT fg(1, 'a')", "object|[]|function|undefined|function|object|function|2"},
            {"SELECT fg(NULL, NULL)", "object|[]|function|undefined|function|object|function|2"},
            {"SELECT fl(1)", "logged"},
            {"SELECT fd()", "FD,Snowflake,SnowflakeLogger,_c_snowflake_logger,snowflake,snowflakeLogger,console"},
            {"CALL pg()", "object|[]|function|0"},
        });
    }

    @Test
    public void aNameJavaScriptCannotDeclareIsRefused() {
        assertCells(new String[][] {
            {function("\"delete\"()", "return 'd';"), "Invalid UDF function name: 'delete'"},
            {function("\"1abc\"()", "return 'x';"), "Invalid UDF function name: '1abc'"},
            {function("\"x.y\"()", "return 'x';"), "Invalid UDF function name: 'x.y'"},
            {function("\"await\"()", "return 'x';"), "Invalid UDF function name: 'await'"},
            {function("\"public\"()", "return 'x';"), "Invalid UDF function name: 'public'"},
            {"CREATE FUNCTION IF NOT EXISTS \"my fn\"() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return 'x';$$",
                "Invalid UDF function name: 'my fn'"},
            {"CREATE PROCEDURE \"my proc\"() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return 'p';$$",
                "Invalid UDF function name: 'my proc'"},
            {function("pa(\"my arg\" FLOAT)", "return 'x';"), "Invalid argument name(s): 'my arg'"},
            {function("pz(\"my arg\" FLOAT, \"class\" FLOAT, ok FLOAT)", "return 'x';"), "Invalid argument name(s): 'my arg, class'"},
            {function("pw(\"await\" FLOAT)", "return 'x';"), "Invalid argument name(s): 'await'"},
            {"CREATE PROCEDURE pp(\"my arg\" FLOAT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return 'x';$$",
                "Invalid argument name(s): 'my arg'"},
            {function("\"delete\"(\"my arg\" FLOAT)", "return 'x';"), "Invalid UDF function name: 'delete'"},
            {function("\"delete\"(x NUMBER)", "return 'x';"),
                "Language JAVASCRIPT does not support type 'NUMBER(38,0)' for argument or return type."},
            {"SELECT pa(1)", "SQL compilation error:\nUnknown function PA."},
        });
        engine.execute(function("\"a$b\"()", "return typeof a$b;"));
        engine.execute(function("\"ünï\"()", "return typeof ünï;"));
        engine.execute(function("\"eval\"()", "return 'e';"));
        engine.execute(function("\"snowflake\"()", "return typeof snowflake;"));
        engine.execute(function("pd(\"arguments\" FLOAT)", "return typeof arguments;"));
        engine.execute(function("\"async\"()", "return 'a';"));
        assertCells(new String[][] {
            {"SELECT \"a$b\"()", "function"},
            {"SELECT \"ünï\"()", "function"},
            {"SELECT \"eval\"()", "e"},
            {"SELECT \"snowflake\"()", "function"},
            {"SELECT pd(1)", "number"},
            {"SELECT \"async\"()", "a"},
        });
    }
}
