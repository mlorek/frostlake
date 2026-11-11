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
 * The frames of a JavaScript stack that overflowed and of a function called where it is written (live-verified): a
 * recursion that overflows lists nine frames, each the recursing function's name at line 0, and no frame for the
 * handler, for a function, an arrow and a method alike; a function expression without a name of its own called
 * where it is written, {@code var a = function () { … }()}, has a frame without a name, where one called through its
 * variable is named by it. Where the account places a stack's overflow follows the frame sizes of its engine, not the
 * source, so the line and position of an overflow are not compared.
 */
public class JavaScriptRecursionFramesTest extends MeasuredCellsTestSupport {

    @Override
    protected String answer(final String sql) {
        final String answered = super.answer(sql);
        return answered.contains("Maximum call stack size exceeded")
            ? answered.replaceAll(" at '.*' position \\d+ \\| stackstrace", " at '<LINE>' position <N> | stackstrace")
            : answered;
    }

    @Test
    public void anOverflowListsNineFramesAndAnInvokedExpressionIsUnnamed() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION jf904_0() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (function "
                + "f(){ return f(); })(); $$",
                "Function JF904_0 successfully created."},
            {"SELECT jf904_0()",
                "REFUSED JavaScript execution error: Uncaught RangeError: Maximum call stack size exceeded in "
                + "JF904_0 at '<LINE>' position <N> | stackstrace:  | f line: 0 | f line: 0 | f line: 0 | f line: 0 "
                + "| f line: 0 | f line: 0 | f line: 0 | f line: 0 | f line: 0"},
            {"CREATE OR REPLACE FUNCTION jf904_1() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var a = function "
                + "() { return foo; }(); return a; $$",
                "Function JF904_1 successfully created."},
            {"SELECT jf904_1()",
                "REFUSED JavaScript execution error: Uncaught ReferenceError: foo is not defined in JF904_1 at ' "
                + "var a = function () { return foo; }(); return a; ' position 23 | stackstrace:  |  line: 1 | "
                + "JF904_1 line: 1"},
            {"CREATE OR REPLACE FUNCTION jf904_2() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ function f(n){ "
                + "return f(n + 1); } return f(0); $$",
                "Function JF904_2 successfully created."},
            {"SELECT jf904_2()",
                "REFUSED JavaScript execution error: Uncaught RangeError: Maximum call stack size exceeded in "
                + "JF904_2 at '<LINE>' position <N> | stackstrace:  | f line: 0 | f line: 0 | f line: 0 | f line: 0 "
                + "| f line: 0 | f line: 0 | f line: 0 | f line: 0 | f line: 0"},
            {"CREATE OR REPLACE FUNCTION jf904_3() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var g = () => g(); "
                + "return g(); $$",
                "Function JF904_3 successfully created."},
            {"SELECT jf904_3()",
                "REFUSED JavaScript execution error: Uncaught RangeError: Maximum call stack size exceeded in "
                + "JF904_3 at '<LINE>' position <N> | stackstrace:  | g line: 0 | g line: 0 | g line: 0 | g line: 0 "
                + "| g line: 0 | g line: 0 | g line: 0 | g line: 0 | g line: 0"},
            {"CREATE OR REPLACE FUNCTION jf904_4() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var a = (() => "
                + "foo)(); return a; $$",
                "Function JF904_4 successfully created."},
            {"SELECT jf904_4()",
                "REFUSED JavaScript execution error: Uncaught ReferenceError: foo is not defined in JF904_4 at ' "
                + "var a = (() => foo)(); return a; ' position 16 | stackstrace:  |  line: 1 | JF904_4 line: 1"},
            {"CREATE OR REPLACE FUNCTION jf904_5() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var a = function "
                + "named() { return foo; }(); return a; $$",
                "Function JF904_5 successfully created."},
            {"SELECT jf904_5()",
                "REFUSED JavaScript execution error: Uncaught ReferenceError: foo is not defined in JF904_5 at ' "
                + "var a = function named() { return foo; }(); return a; ' position 28 | stackstrace:  | named line: "
                + "1 | JF904_5 line: 1"},
            {"CREATE OR REPLACE FUNCTION jf904_6() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var o = { m: "
                + "function () { return foo; } }; return o.m(); $$",
                "Function JF904_6 successfully created."},
            {"SELECT jf904_6()",
                "REFUSED JavaScript execution error: Uncaught ReferenceError: foo is not defined in JF904_6 at ' "
                + "var o = { m: function () { return foo; } }; return o.m(); ' position 28 | stackstrace:  | m line: "
                + "1 | JF904_6 line: 1"},
            {"CREATE OR REPLACE FUNCTION jf904_7() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var h = function "
                + "() { return foo; }; return h(); $$",
                "Function JF904_7 successfully created."},
            {"SELECT jf904_7()",
                "REFUSED JavaScript execution error: Uncaught ReferenceError: foo is not defined in JF904_7 at ' "
                + "var h = function () { return foo; }; return h(); ' position 23 | stackstrace:  | h line: 1 | "
                + "JF904_7 line: 1"},
            {"CREATE OR REPLACE FUNCTION jf904_8() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (function "
                + "() { return foo; })(); $$",
                "Function JF904_8 successfully created."},
            {"SELECT jf904_8()",
                "REFUSED JavaScript execution error: Uncaught ReferenceError: foo is not defined in JF904_8 at ' "
                + "return (function () { return foo; })(); ' position 23 | stackstrace:  |  line: 1 | JF904_8 line: 1"},
            {"CREATE OR REPLACE FUNCTION jf904_9() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ function k() { "
                + "return foo; } return k(); $$",
                "Function JF904_9 successfully created."},
            {"SELECT jf904_9()",
                "REFUSED JavaScript execution error: Uncaught ReferenceError: foo is not defined in JF904_9 at ' "
                + "function k() { return foo; } return k(); ' position 16 | stackstrace:  | k line: 1 | JF904_9 "
                + "line: 1"},
            {"CREATE OR REPLACE PROCEDURE jp904_0() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (function "
                + "f(){ return f(); })(); $$",
                "Function JP904_0 successfully created."},
            {"CALL jp904_0()",
                "REFUSED JavaScript execution error: Uncaught RangeError: Maximum call stack size exceeded in "
                + "JP904_0 at '<LINE>' position <N> | stackstrace:  | f line: 0 | f line: 0 | f line: 0 | f line: 0 "
                + "| f line: 0 | f line: 0 | f line: 0 | f line: 0 | f line: 0"},
            {"CREATE OR REPLACE PROCEDURE jp904_1() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var a = function "
                + "() { return foo; }(); return a; $$",
                "Function JP904_1 successfully created."},
            {"CALL jp904_1()",
                "REFUSED JavaScript execution error: Uncaught ReferenceError: foo is not defined in JP904_1 at ' "
                + "var a = function () { return foo; }(); return a; ' position 23 | stackstrace:  |  line: 1 | "
                + "JP904_1 line: 1"},
        });
    }

    @Test
    public void theOverflowFramesOfOtherShapes() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION jo905_0() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (function "
                + "f(a){ return f(a); })(1); $$",
                "Function JO905_0 successfully created."},
            {"SELECT jo905_0()",
                "REFUSED JavaScript execution error: Uncaught RangeError: Maximum call stack size exceeded in "
                + "JO905_0 at '<LINE>' position <N> | stackstrace:  | f line: 0 | f line: 0 | f line: 0 | f line: 0 "
                + "| f line: 0 | f line: 0 | f line: 0 | f line: 0 | f line: 0"},
            {"CREATE OR REPLACE FUNCTION jo905_1() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (function "
                + "f(){ var x = 1; return f(); })(); $$",
                "Function JO905_1 successfully created."},
            {"SELECT jo905_1()",
                "REFUSED JavaScript execution error: Uncaught RangeError: Maximum call stack size exceeded in "
                + "JO905_1 at '<LINE>' position <N> | stackstrace:  | f line: 0 | f line: 0 | f line: 0 | f line: 0 "
                + "| f line: 0 | f line: 0 | f line: 0 | f line: 0 | f line: 0"},
            {"CREATE OR REPLACE FUNCTION jo905_2() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ function f(n){ "
                + "return f(n); } return f(0); $$",
                "Function JO905_2 successfully created."},
            {"SELECT jo905_2()",
                "REFUSED JavaScript execution error: Uncaught RangeError: Maximum call stack size exceeded in "
                + "JO905_2 at '<LINE>' position <N> | stackstrace:  | f line: 0 | f line: 0 | f line: 0 | f line: 0 "
                + "| f line: 0 | f line: 0 | f line: 0 | f line: 0 | f line: 0"},
            {"CREATE OR REPLACE FUNCTION jo905_3() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var g = (n) => "
                + "g(n); return g(1); $$",
                "Function JO905_3 successfully created."},
            {"SELECT jo905_3()",
                "REFUSED JavaScript execution error: Uncaught RangeError: Maximum call stack size exceeded in "
                + "JO905_3 at '<LINE>' position <N> | stackstrace:  | g line: 0 | g line: 0 | g line: 0 | g line: 0 "
                + "| g line: 0 | g line: 0 | g line: 0 | g line: 0 | g line: 0"},
            {"CREATE OR REPLACE FUNCTION jo905_4() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var g = () => { "
                + "return g(); }; return g(); $$",
                "Function JO905_4 successfully created."},
            {"SELECT jo905_4()",
                "REFUSED JavaScript execution error: Uncaught RangeError: Maximum call stack size exceeded in "
                + "JO905_4 at '<LINE>' position <N> | stackstrace:  | g line: 0 | g line: 0 | g line: 0 | g line: 0 "
                + "| g line: 0 | g line: 0 | g line: 0 | g line: 0 | g line: 0"},
            {"CREATE OR REPLACE FUNCTION jo905_5() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ function f(){ f(); "
                + "} f(); return 1; $$",
                "Function JO905_5 successfully created."},
            {"SELECT jo905_5()",
                "REFUSED JavaScript execution error: Uncaught RangeError: Maximum call stack size exceeded in "
                + "JO905_5 at '<LINE>' position <N> | stackstrace:  | f line: 0 | f line: 0 | f line: 0 | f line: 0 "
                + "| f line: 0 | f line: 0 | f line: 0 | f line: 0 | f line: 0"},
            {"CREATE OR REPLACE FUNCTION jo905_6() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ function f(){ "
                + "return 1 + f(); } return f(); $$",
                "Function JO905_6 successfully created."},
            {"SELECT jo905_6()",
                "REFUSED JavaScript execution error: Uncaught RangeError: Maximum call stack size exceeded in "
                + "JO905_6 at '<LINE>' position <N> | stackstrace:  | f line: 0 | f line: 0 | f line: 0 | f line: 0 "
                + "| f line: 0 | f line: 0 | f line: 0 | f line: 0 | f line: 0"},
            {"CREATE OR REPLACE FUNCTION jo905_7() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ function f(a, b){ "
                + "return f(a, b); } return f(1, 2); $$",
                "Function JO905_7 successfully created."},
            {"SELECT jo905_7()",
                "REFUSED JavaScript execution error: Uncaught RangeError: Maximum call stack size exceeded in "
                + "JO905_7 at '<LINE>' position <N> | stackstrace:  | f line: 0 | f line: 0 | f line: 0 | f line: 0 "
                + "| f line: 0 | f line: 0 | f line: 0 | f line: 0 | f line: 0"},
            {"CREATE OR REPLACE FUNCTION jo905_8() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ function f(){ "
                + "return f(); } return f(); $$",
                "Function JO905_8 successfully created."},
            {"SELECT jo905_8()",
                "REFUSED JavaScript execution error: Uncaught RangeError: Maximum call stack size exceeded in "
                + "JO905_8 at '<LINE>' position <N> | stackstrace:  | f line: 0 | f line: 0 | f line: 0 | f line: 0 "
                + "| f line: 0 | f line: 0 | f line: 0 | f line: 0 | f line: 0"},
            {"CREATE OR REPLACE FUNCTION jo905_9() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var o = { m() { "
                + "return this.m(); } }; return o.m(); $$",
                "Function JO905_9 successfully created."},
            {"SELECT jo905_9()",
                "REFUSED JavaScript execution error: Uncaught RangeError: Maximum call stack size exceeded in "
                + "JO905_9 at '<LINE>' position <N> | stackstrace:  | m line: 0 | m line: 0 | m line: 0 | m line: 0 "
                + "| m line: 0 | m line: 0 | m line: 0 | m line: 0 | m line: 0"},
            {"CREATE OR REPLACE FUNCTION jo905_10() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ function   f  ( ) "
                + "{ return f(); } return f(); $$",
                "Function JO905_10 successfully created."},
            {"SELECT jo905_10()",
                "REFUSED JavaScript execution error: Uncaught RangeError: Maximum call stack size exceeded in "
                + "JO905_10 at '<LINE>' position <N> | stackstrace:  | f line: 0 | f line: 0 | f line: 0 | f line: 0 "
                + "| f line: 0 | f line: 0 | f line: 0 | f line: 0 | f line: 0"},
            {"CREATE OR REPLACE FUNCTION jo905_11() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ function f(n){\n  "
                + "return f(n + 1);\n}\nreturn f(0); $$",
                "Function JO905_11 successfully created."},
            {"SELECT jo905_11()",
                "REFUSED JavaScript execution error: Uncaught RangeError: Maximum call stack size exceeded in "
                + "JO905_11 at '<LINE>' position <N> | stackstrace:  | f line: 0 | f line: 0 | f line: 0 | f line: 0 "
                + "| f line: 0 | f line: 0 | f line: 0 | f line: 0 | f line: 0"},
        });
    }
}
