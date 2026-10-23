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
 * A JavaScript function's JSON.stringify of a value that holds a circle is refused in the words of the account's
 * engine: where the circle starts, its first two links and its last, an ellipsis for the rest, and the key that
 * closes it, an array's member by its index (all live-verified).
 */
public class JavaScriptCircularJsonTest extends MeasuredCellsTestSupport {

    @Test
    public void circularJson() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertAnswer("Function JC1 successfully created.",
            "CREATE OR REPLACE FUNCTION jc1() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var a = {}; a.a = a; return "
                + "JSON.stringify(a); $$");
        assertAnswer("REFUSED JavaScript execution error: Uncaught TypeError: Converting circular structure to JSON | "
                + "    --> starting at object with constructor 'Object' |     --- property 'a' closes the circle in "
                + "JC1 at ' var a = {}; a.a = a; return JSON.stringify(a); ' position 34 | stackstrace:  | JC1 line: 1",
            "SELECT jc1()");
        assertAnswer("Function JC2 successfully created.",
            "CREATE OR REPLACE FUNCTION jc2() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var a = {b: {c: {}}}; "
                + "a.b.c.d = a; return JSON.stringify(a); $$");
        assertAnswer("REFUSED JavaScript execution error: Uncaught TypeError: Converting circular structure to JSON | "
                + "    --> starting at object with constructor 'Object' |     |     property 'b' -> object with "
                + "constructor 'Object' |     |     property 'c' -> object with constructor 'Object' |     --- "
                + "property 'd' closes the circle in JC2 at ' var a = {b: {c: {}}}; a.b.c.d = a; return "
                + "JSON.stringify(a); ' position 48 | stackstrace:  | JC2 line: 1",
            "SELECT jc2()");
        assertAnswer("Function JC3 successfully created.",
            "CREATE OR REPLACE FUNCTION jc3() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var a = [1, {}]; a[1].x = a; "
                + "return JSON.stringify(a); $$");
        assertAnswer("REFUSED JavaScript execution error: Uncaught TypeError: Converting circular structure to JSON | "
                + "    --> starting at object with constructor 'Array' |     |     index 1 -> object with constructor "
                + "'Object' |     --- property 'x' closes the circle in JC3 at ' var a = [1, {}]; a[1].x = a; return "
                + "JSON.stringify(a); ' position 42 | stackstrace:  | JC3 line: 1",
            "SELECT jc3()");
        assertAnswer("Function JC4 successfully created.",
            "CREATE OR REPLACE FUNCTION jc4() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var a = {p: {q: {r: {}}}}; "
                + "a.p.q.r.s = a; return JSON.stringify(a); $$");
        assertAnswer("REFUSED JavaScript execution error: Uncaught TypeError: Converting circular structure to JSON | "
                + "    --> starting at object with constructor 'Object' |     |     property 'p' -> object with "
                + "constructor 'Object' |     |     property 'q' -> object with constructor 'Object' |     |     "
                + "property 'r' -> object with constructor 'Object' |     --- property 's' closes the circle in JC4 "
                + "at ' var a = {p: {q: {r: {}}}}; a.p.q.r.s = a; return JSON.stringify(a); ' position 55 | "
                + "stackstrace:  | JC4 line: 1",
            "SELECT jc4()");
        assertAnswer("Function JC5 successfully created.",
            "CREATE OR REPLACE FUNCTION jc5() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var a = {p: {q: {r: {s: "
                + "{}}}}}; a.p.q.r.s.t = a; return JSON.stringify(a); $$");
        assertAnswer("REFUSED JavaScript execution error: Uncaught TypeError: Converting circular structure to JSON | "
                + "    --> starting at object with constructor 'Object' |     |     property 'p' -> object with "
                + "constructor 'Object' |     |     property 'q' -> object with constructor 'Object' |     |     ... |"
                + "     |     property 's' -> object with constructor 'Object' |     --- property 't' closes the "
                + "circle in JC5 at ' var a = {p: {q: {r: {s: {}}}}}; a.p.q.r.s.t = a; return JSON.stringify(a); ' "
                + "position 62 | stackstrace:  | JC5 line: 1",
            "SELECT jc5()");
        assertAnswer("Function JC6 successfully created.",
            "CREATE OR REPLACE FUNCTION jc6() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var a = {p: {q: {r: {s: {t: "
                + "{u: {}}}}}}}; a.p.q.r.s.t.u.v = a; return JSON.stringify(a); $$");
        assertAnswer("REFUSED JavaScript execution error: Uncaught TypeError: Converting circular structure to JSON | "
                + "    --> starting at object with constructor 'Object' |     |     property 'p' -> object with "
                + "constructor 'Object' |     |     property 'q' -> object with constructor 'Object' |     |     ... |"
                + "     |     property 'u' -> object with constructor 'Object' |     --- property 'v' closes the "
                + "circle in JC6 at ' var a = {p: {q: {r: {s: {t: {u: {}}}}}}}; a.p.q.r.s.t.u.v = a; return "
                + "JSON.stringify(a); ' position 76 | stackstrace:  | JC6 line: 1",
            "SELECT jc6()");
        assertAnswer("Function JC7 successfully created.",
            "CREATE OR REPLACE FUNCTION jc7() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ class K {}; var k = new K(); "
                + "k.self = k; return JSON.stringify(k); $$");
        assertAnswer("REFUSED JavaScript execution error: Uncaught TypeError: Converting circular structure to JSON | "
                + "    --> starting at object with constructor 'K' |     --- property 'self' closes the circle in JC7 "
                + "at ' class K {}; var k = new K(); k.self = k; return JSON.stringify(k); ' position 54 | "
                + "stackstrace:  | JC7 line: 1",
            "SELECT jc7()");
        assertAnswer("Function JC8 successfully created.",
            "CREATE OR REPLACE FUNCTION jc8() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var o = Object.create(null); "
                + "o.o = o; return JSON.stringify(o); $$");
        assertAnswer("REFUSED JavaScript execution error: Uncaught TypeError: Converting circular structure to JSON | "
                + "    --> starting at object with constructor 'Object' |     --- property 'o' closes the circle in "
                + "JC8 at ' var o = Object.create(null); o.o = o; return JSON.stringify(o); ' position 51 | "
                + "stackstrace:  | JC8 line: 1",
            "SELECT jc8()");
        assertAnswer("Function JC9 successfully created.",
            "CREATE OR REPLACE FUNCTION jc9() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var a = {x: {}}; a.x.y = "
                + "a.x; return JSON.stringify(a); $$");
        assertAnswer("REFUSED JavaScript execution error: Uncaught TypeError: Converting circular structure to JSON | "
                + "    --> starting at object with constructor 'Object' |     --- property 'y' closes the circle in "
                + "JC9 at ' var a = {x: {}}; a.x.y = a.x; return JSON.stringify(a); ' position 43 | stackstrace:  | "
                + "JC9 line: 1",
            "SELECT jc9()");
        assertAnswer("Function JC10 successfully created.",
            "CREATE OR REPLACE FUNCTION jc10() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var a = []; a.push(a); "
                + "return JSON.stringify(a); $$");
        assertAnswer("REFUSED JavaScript execution error: Uncaught TypeError: Converting circular structure to JSON | "
                + "    --> starting at object with constructor 'Array' |     --- index 0 closes the circle in JC10 at "
                + "' var a = []; a.push(a); return JSON.stringify(a); ' position 36 | stackstrace:  | JC10 line: 1",
            "SELECT jc10()");
        assertAnswer("Function JC11 successfully created.",
            "CREATE OR REPLACE FUNCTION jc11() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var a = {}; a[''] = a; "
                + "return JSON.stringify(a); $$");
        assertAnswer("REFUSED JavaScript execution error: Uncaught TypeError: Converting circular structure to JSON | "
                + "    --> starting at object with constructor 'Object' |     --- <anonymous> closes the circle in "
                + "JC11 at ' var a = {}; a[''] = a; return JSON.stringify(a); ' position 36 | stackstrace:  | JC11 "
                + "line: 1",
            "SELECT jc11()");
        assertAnswer("Function JC12 successfully created.",
            "CREATE OR REPLACE FUNCTION jc12() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var a = {n: 1, b: [0, {c: "
                + "null}]}; a.b[1].c = a.b; return JSON.stringify(a); $$");
        assertAnswer("REFUSED JavaScript execution error: Uncaught TypeError: Converting circular structure to JSON | "
                + "    --> starting at object with constructor 'Array' |     |     index 1 -> object with constructor "
                + "'Object' |     --- property 'c' closes the circle in JC12 at ' var a = {n: 1, b: [0, {c: null}]}; "
                + "a.b[1].c = a.b; return JSON.stringify(a); ' position 64 | stackstrace:  | JC12 line: 1",
            "SELECT jc12()");
        assertAnswer("Function JC13 successfully created.",
            "CREATE OR REPLACE FUNCTION jc13() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var a = {}; a.a = a; return "
                + "JSON.stringify({w: a}); $$");
        assertAnswer("REFUSED JavaScript execution error: Uncaught TypeError: Converting circular structure to JSON | "
                + "    --> starting at object with constructor 'Object' |     --- property 'a' closes the circle in "
                + "JC13 at ' var a = {}; a.a = a; return JSON.stringify({w: a}); ' position 34 | stackstrace:  | JC13 "
                + "line: 1",
            "SELECT jc13()");
        assertAnswer("Function JC14 successfully created.",
            "CREATE OR REPLACE FUNCTION jc14() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var a = {}; a.a = a; return "
                + "JSON.stringify(a, null, 2); $$");
        assertAnswer("REFUSED JavaScript execution error: Uncaught TypeError: Converting circular structure to JSON | "
                + "    --> starting at object with constructor 'Object' |     --- property 'a' closes the circle in "
                + "JC14 at ' var a = {}; a.a = a; return JSON.stringify(a, null, 2); ' position 34 | stackstrace:  | "
                + "JC14 line: 1",
            "SELECT jc14()");
        assertAnswer("Function JC15 successfully created.",
            "CREATE OR REPLACE FUNCTION jc15() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var a = {}; a.a = a; return "
                + "JSON.stringify(a, ['a']); $$");
        assertAnswer("REFUSED JavaScript execution error: Uncaught TypeError: Converting circular structure to JSON | "
                + "    --> starting at object with constructor 'Object' |     --- property 'a' closes the circle in "
                + "JC15 at ' var a = {}; a.a = a; return JSON.stringify(a, ['a']); ' position 34 | stackstrace:  | "
                + "JC15 line: 1",
            "SELECT jc15()");
        assertAnswer("Function JC16 successfully created.",
            "CREATE OR REPLACE FUNCTION jc16() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (void 0)(); $$");
        assertAnswer("Function JC17 successfully created.",
            "CREATE OR REPLACE FUNCTION jc17() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (1, 2)(); $$");
        assertAnswer("Function JC18 successfully created.",
            "CREATE OR REPLACE FUNCTION jc18() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return [1][0](); $$");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
