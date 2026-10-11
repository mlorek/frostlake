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
 * What a JavaScript handler leaves in its globals is its own call's, for one statement. A global one function
 * sets - by name, through {@code globalThis}, or by assigning a name it never declared - is not there for
 * another function, in a later statement or in the same one; neither is a member it adds to a built-in
 * prototype or a built-in function it replaces. The same function finds nothing of an earlier statement, and
 * two calls of it written in one statement do not share. A procedure's globals end with its CALL: another
 * procedure, the same procedure called again, a function a statement of the procedure calls and a procedure
 * it calls see none of them, and a procedure sees none a function set. Every answer is live-verified.
 */
public class JavaScriptGlobalStateTest extends MeasuredCellsTestSupport {

    @Test
    public void aGlobalIsNotAnotherFunctions() {
        assertAnswer(
            "Function JS_SET successfully created.",
            "CREATE OR REPLACE FUNCTION js_set(v VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ "
                + "globalThis.shared = V; return 'set'; $$");
        assertAnswer(
            "Function JS_GET successfully created.",
            "CREATE OR REPLACE FUNCTION js_get(x FLOAT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return typeof "
                + "globalThis.shared === 'undefined' ? 'nothing' : String(globalThis.shared); $$");
        assertAnswer(
            "set",
            "SELECT js_set('first function')");
        assertAnswer(
            "nothing",
            "SELECT js_get(1)");
        assertAnswer(
            "set|nothing",
            "SELECT js_set('same statement'), js_get(1)");
        assertAnswer(
            "nothing|set|nothing",
            "SELECT js_get(1), js_set('same statement 2'), js_get(2)");
    }

    @Test
    public void aChangedBuiltInIsNotAnotherFunctions() {
        assertAnswer(
            "Function POL successfully created.",
            "CREATE OR REPLACE FUNCTION pol(x FLOAT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ "
                + "Array.prototype.polluted = 1; Object.prototype.polluted2 = 2; JSON.myown = 3; Math.max = function "
                + "() { return 'hijacked'; }; return 'done'; $$");
        assertAnswer(
            "Function SEE successfully created.",
            "CREATE OR REPLACE FUNCTION see(x FLOAT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return [typeof "
                + "[].polluted, typeof ({}).polluted2, typeof JSON.myown, String(Math.max(1, 2))].join('|'); $$");
        assertAnswer(
            "done",
            "SELECT pol(1)");
        assertAnswer(
            "undefined|undefined|undefined|2",
            "SELECT see(1)");
        assertAnswer(
            "done|undefined|undefined|undefined|2",
            "SELECT pol(1), see(1)");
        assertAnswer(
            "undefined|undefined|undefined|2|done|undefined|undefined|undefined|2",
            "SELECT see(1), pol(1), see(2)");
    }

    @Test
    public void aFunctionsGlobalsEndWithTheStatement() {
        assertAnswer(
            "Function CNT successfully created.",
            "CREATE OR REPLACE FUNCTION cnt(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ globalThis.c = "
                + "(globalThis.c || 0) + 1; return globalThis.c; $$");
        assertAnswer(
            "1",
            "SELECT cnt(1)::VARCHAR");
        assertAnswer(
            "1",
            "SELECT cnt(1)::VARCHAR");
        assertAnswer(
            "1|1",
            "SELECT cnt(1)::VARCHAR, cnt(2)::VARCHAR");
        assertAnswer(
            "Function IMP successfully created.",
            "CREATE OR REPLACE FUNCTION imp(x FLOAT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ leaked = "
                + "'implicit'; return 'set'; $$");
        assertAnswer(
            "Function IMP2 successfully created.",
            "CREATE OR REPLACE FUNCTION imp2(x FLOAT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return typeof "
                + "leaked; $$");
        assertAnswer(
            "set",
            "SELECT imp(1)");
        assertAnswer(
            "undefined",
            "SELECT imp2(1)");
        assertAnswer(
            "set|undefined",
            "SELECT imp(1), imp2(1)");
        assertAnswer(
            "Function OWN successfully created.",
            "CREATE OR REPLACE FUNCTION own(x FLOAT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var before = "
                + "typeof globalThis.mine; globalThis.mine = 1; return before; $$");
        assertAnswer(
            "undefined",
            "SELECT own(1)");
        assertAnswer(
            "undefined",
            "SELECT own(1)");
        assertAnswer(
            "Function OWN successfully created.",
            "CREATE OR REPLACE FUNCTION own(x FLOAT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var before = "
                + "typeof globalThis.mine; globalThis.mine = 1; return before + '!'; $$");
        assertAnswer(
            "undefined!",
            "SELECT own(1)");
    }

    @Test
    public void aProceduresGlobalsEndWithItsCall() {
        assertAnswer(
            "Function JS_SET successfully created.",
            "CREATE OR REPLACE FUNCTION js_set(v VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ "
                + "globalThis.shared = V; return 'set'; $$");
        assertAnswer(
            "Function JS_GET successfully created.",
            "CREATE OR REPLACE FUNCTION js_get(x FLOAT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return typeof "
                + "globalThis.shared === 'undefined' ? 'nothing' : String(globalThis.shared); $$");
        assertAnswer(
            "Function PSET successfully created.",
            "CREATE OR REPLACE PROCEDURE pset() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ globalThis.pshared = "
                + "'from procedure'; return 'set'; $$");
        assertAnswer(
            "Function PGET successfully created.",
            "CREATE OR REPLACE PROCEDURE pget() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return typeof "
                + "globalThis.pshared; $$");
        assertAnswer(
            "set",
            "CALL pset()");
        assertAnswer(
            "undefined",
            "CALL pget()");
        assertAnswer(
            "Function PCNT successfully created.",
            "CREATE OR REPLACE PROCEDURE pcnt() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ globalThis.pc = "
                + "(globalThis.pc || 0) + 1; return globalThis.pc; $$");
        assertAnswer(
            "1.0",
            "CALL pcnt()");
        assertAnswer(
            "1.0",
            "CALL pcnt()");
        assertAnswer(
            "Function PCALL successfully created.",
            "CREATE OR REPLACE PROCEDURE pcall() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ globalThis.shared = "
                + "'procedure global'; var rs = snowflake.execute({sqlText: 'SELECT js_get(1)'}); rs.next(); return "
                + "rs.getColumnValue(1); $$");
        assertAnswer(
            "nothing",
            "CALL pcall()");
        assertAnswer(
            "set",
            "SELECT js_set('for procedure')");
        assertAnswer(
            "Function PREAD successfully created.",
            "CREATE OR REPLACE PROCEDURE pread() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return typeof "
                + "globalThis.shared; $$");
        assertAnswer(
            "undefined",
            "CALL pread()");
        assertAnswer(
            "Function PSELF successfully created.",
            "CREATE OR REPLACE PROCEDURE pself() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ globalThis.inproc = "
                + "'p'; var rs = snowflake.execute({sqlText: 'CALL pinner()'}); rs.next(); return "
                + "rs.getColumnValue(1) + '|' + typeof globalThis.inner; $$");
        assertAnswer(
            "Function PINNER successfully created.",
            "CREATE OR REPLACE PROCEDURE pinner() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var seen = typeof "
                + "globalThis.inproc; globalThis.inner = 1; return seen; $$");
        assertAnswer(
            "undefined|undefined",
            "CALL pself()");
    }

    @Test
    public void aHandlersWorldIsFresh() {
        assertAnswer(
            "Function FRZ successfully created.",
            "CREATE OR REPLACE FUNCTION frz(x FLOAT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return "
                + "[Object.isFrozen(Object.prototype), Object.isFrozen(Array.prototype), "
                + "Object.isExtensible(globalThis), Object.isFrozen(JSON), Object.isFrozen(Math)].join('|'); $$");
        assertAnswer(
            "false|false|true|false|false",
            "SELECT frz(1)");
        assertAnswer(
            "Function STRICT successfully created.",
            "CREATE OR REPLACE FUNCTION strict(x FLOAT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ 'use strict'; "
                + "try { undeclared = 1; return 'assigned'; } catch (e) { return e.name; } $$");
        assertAnswer(
            "ReferenceError",
            "SELECT strict(1)");
        assertAnswer(
            "Function REDEF successfully created.",
            "CREATE OR REPLACE FUNCTION redef(x FLOAT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var out = typeof "
                + "Date.now; Date.now = undefined; return out; $$");
        assertAnswer(
            "function",
            "SELECT redef(1)");
        assertAnswer(
            "function",
            "SELECT redef(1)");
        assertAnswer(
            "Function REDEF2 successfully created.",
            "CREATE OR REPLACE FUNCTION redef2(x FLOAT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return typeof "
                + "Date.now; $$");
        assertAnswer(
            "function",
            "SELECT redef2(1)");
        assertAnswer(
            "function|function",
            "SELECT redef(1), redef2(1)");
    }
}
