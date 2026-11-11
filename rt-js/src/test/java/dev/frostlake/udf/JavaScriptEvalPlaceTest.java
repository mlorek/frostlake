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
 * A JavaScript handler's direct {@code eval(...)} is refused at the last place the account recorded before the call:
 * the argument's name, a property's name, a callee, an index's bracket or an operator, a conditional's condition; a
 * constant argument records nothing, so the place around the call stands - the statement's start, or the eval itself
 * as a declaration's value, an element or another call's argument. An indirect eval and the Function constructor
 * are refused where they are called. Every answer is live-verified.
 */
public class JavaScriptEvalPlaceTest extends MeasuredCellsTestSupport {

    @Test
    public void theArgumentsLastPlaceStands() {
        engine.execute("CREATE OR REPLACE FUNCTION je987_00() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return "
            + "eval('1'); $$");
        engine.execute("CREATE OR REPLACE FUNCTION je987_01() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ var s = "
            + "'1'; return eval(s); $$");
        engine.execute("CREATE OR REPLACE FUNCTION je987_02() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ var a = {b: "
            + "'1'}; return eval(a.b); $$");
        engine.execute("CREATE OR REPLACE FUNCTION je987_03() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ function "
            + "f(x) { return x; } return eval(f('1')); $$");
        engine.execute("CREATE OR REPLACE FUNCTION je987_04() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ var x = "
            + "eval('1'); return x; $$");
        engine.execute("CREATE OR REPLACE FUNCTION je987_05() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ var x; x = "
            + "eval('1'); return x; $$");
        engine.execute("CREATE OR REPLACE FUNCTION je987_06() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ var s = "
            + "'1'; return eval(s) + 1; $$");
        engine.execute("CREATE OR REPLACE FUNCTION je987_07() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ if "
            + "(eval('1')) { return 1; } return 0; $$");
        engine.execute("CREATE OR REPLACE FUNCTION je987_08() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ var s = "
            + "'1'; var r = eval(s); return r; $$");
        engine.execute("CREATE OR REPLACE FUNCTION je987_09() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return "
            + "eval(('1')); $$");
        engine.execute("CREATE OR REPLACE FUNCTION je987_10() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ var s = "
            + "'1'; return eval(s + '2'); $$");
        engine.execute("CREATE OR REPLACE FUNCTION je987_11() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ var s = "
            + "['1']; return eval(s[0]); $$");
        engine.execute("CREATE OR REPLACE FUNCTION je987_12() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return "
            + "[eval('1')]; $$");
        engine.execute("CREATE OR REPLACE FUNCTION je987_13() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return "
            + "eval(`1`); $$");
        assertCells(new String[][] {
            {"SELECT je987_00()",
                "REFUSED JavaScript execution error: Uncaught EvalError: Dynamic code evaluation disallowed in "
                + "JE987_00 at ' return eval('1'); ' position 1 | stackstrace:  | JE987_00 line: 1"},
            {"SELECT je987_01()",
                "REFUSED JavaScript execution error: Uncaught EvalError: Dynamic code evaluation disallowed in "
                + "JE987_01 at ' var s = '1'; return eval(s); ' position 26 | stackstrace:  | JE987_01 line: 1"},
            {"SELECT je987_02()",
                "REFUSED JavaScript execution error: Uncaught EvalError: Dynamic code evaluation disallowed in "
                + "JE987_02 at ' var a = {b: '1'}; return eval(a.b); ' position 33 | stackstrace:  | JE987_02 line: 1"},
            {"SELECT je987_03()",
                "REFUSED JavaScript execution error: Uncaught EvalError: Dynamic code evaluation disallowed in "
                + "JE987_03 at ' function f(x) { return x; } return eval(f('1')); ' position 41 | stackstrace:  | "
                + "JE987_03 line: 1"},
            {"SELECT je987_04()",
                "REFUSED JavaScript execution error: Uncaught EvalError: Dynamic code evaluation disallowed in "
                + "JE987_04 at ' var x = eval('1'); return x; ' position 9 | stackstrace:  | JE987_04 line: 1"},
            {"SELECT je987_05()",
                "REFUSED JavaScript execution error: Uncaught EvalError: Dynamic code evaluation disallowed in "
                + "JE987_05 at ' var x; x = eval('1'); return x; ' position 8 | stackstrace:  | JE987_05 line: 1"},
            {"SELECT je987_06()",
                "REFUSED JavaScript execution error: Uncaught EvalError: Dynamic code evaluation disallowed in "
                + "JE987_06 at ' var s = '1'; return eval(s) + 1; ' position 26 | stackstrace:  | JE987_06 line: 1"},
            {"SELECT je987_07()",
                "REFUSED JavaScript execution error: Uncaught EvalError: Dynamic code evaluation disallowed in "
                + "JE987_07 at ' if (eval('1')) { return 1; } return 0; ' position 1 | stackstrace:  | JE987_07 "
                + "line: 1"},
            {"SELECT je987_08()",
                "REFUSED JavaScript execution error: Uncaught EvalError: Dynamic code evaluation disallowed in "
                + "JE987_08 at ' var s = '1'; var r = eval(s); return r; ' position 27 | stackstrace:  | JE987_08 "
                + "line: 1"},
            {"SELECT je987_09()",
                "REFUSED JavaScript execution error: Uncaught EvalError: Dynamic code evaluation disallowed in "
                + "JE987_09 at ' return eval(('1')); ' position 1 | stackstrace:  | JE987_09 line: 1"},
            {"SELECT je987_10()",
                "REFUSED JavaScript execution error: Uncaught EvalError: Dynamic code evaluation disallowed in "
                + "JE987_10 at ' var s = '1'; return eval(s + '2'); ' position 28 | stackstrace:  | JE987_10 line: 1"},
            {"SELECT je987_11()",
                "REFUSED JavaScript execution error: Uncaught EvalError: Dynamic code evaluation disallowed in "
                + "JE987_11 at ' var s = ['1']; return eval(s[0]); ' position 29 | stackstrace:  | JE987_11 line: 1"},
            {"SELECT je987_12()",
                "REFUSED JavaScript execution error: Uncaught EvalError: Dynamic code evaluation disallowed in "
                + "JE987_12 at ' return [eval('1')]; ' position 9 | stackstrace:  | JE987_12 line: 1"},
            {"SELECT je987_13()",
                "REFUSED JavaScript execution error: Uncaught EvalError: Dynamic code evaluation disallowed in "
                + "JE987_13 at ' return eval(`1`); ' position 1 | stackstrace:  | JE987_13 line: 1"},
        });
    }

    @Test
    public void theIssuesShapesAndTheIndirectRoutes() {
        engine.execute("CREATE OR REPLACE FUNCTION je988_00() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return "
            + "eval('1');$$");
        engine.execute("CREATE OR REPLACE FUNCTION je988_01() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$var s = '1'; "
            + "return eval(s);$$");
        engine.execute("CREATE OR REPLACE FUNCTION je988_02() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$  return  "
            + "eval ( 'x' ) ;$$");
        engine.execute("CREATE OR REPLACE FUNCTION je988_03() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return "
            + "String(eval('1' + '2'));$$");
        engine.execute("CREATE OR REPLACE FUNCTION je988_04() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return "
            + "String((0, eval)('1'));$$");
        engine.execute("CREATE OR REPLACE FUNCTION je988_05() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return "
            + "String(new Function('return 1')());$$");
        engine.execute("CREATE OR REPLACE FUNCTION je988_06() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return "
            + "String(Function('return 1')());$$");
        engine.execute("CREATE OR REPLACE FUNCTION je988_07() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$var f = "
            + "eval; return String(f('1'));$$");
        engine.execute("CREATE OR REPLACE FUNCTION je988_08() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$var names = "
            + "['a']; var out = []; for (var i = 0; i < names.length; i++) { out.push(names[i] + '=' + eval('typeof "
            + "' + names[i])); } return out;$$");
        engine.execute("CREATE OR REPLACE FUNCTION je988_09() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$var a = 1; "
            + "return eval(a === 1 ? '1' : '2');$$");
        assertCells(new String[][] {
            {"SELECT je988_00()",
                "REFUSED JavaScript execution error: Uncaught EvalError: Dynamic code evaluation disallowed in "
                + "JE988_00 at 'return eval('1');' position 0 | stackstrace:  | JE988_00 line: 1"},
            {"SELECT je988_01()",
                "REFUSED JavaScript execution error: Uncaught EvalError: Dynamic code evaluation disallowed in "
                + "JE988_01 at 'var s = '1'; return eval(s);' position 25 | stackstrace:  | JE988_01 line: 1"},
            {"SELECT je988_02()",
                "REFUSED JavaScript execution error: Uncaught EvalError: Dynamic code evaluation disallowed in "
                + "JE988_02 at '  return  eval ( 'x' ) ;' position 2 | stackstrace:  | JE988_02 line: 1"},
            {"SELECT je988_03()",
                "REFUSED JavaScript execution error: Uncaught EvalError: Dynamic code evaluation disallowed in "
                + "JE988_03 at 'return String(eval('1' + '2'));' position 14 | stackstrace:  | JE988_03 line: 1"},
            {"SELECT je988_04()",
                "REFUSED JavaScript execution error: Uncaught EvalError: Dynamic code evaluation disallowed in "
                + "JE988_04 at 'return String((0, eval)('1'));' position 23 | stackstrace:  | JE988_04 line: 1"},
            {"SELECT je988_05()",
                "REFUSED JavaScript execution error: Uncaught EvalError: Dynamic code evaluation disallowed in "
                + "JE988_05 at 'return String(new Function('return 1')());' position 14 | stackstrace:  | JE988_05 "
                + "line: 1"},
            {"SELECT je988_06()",
                "REFUSED JavaScript execution error: Uncaught EvalError: Dynamic code evaluation disallowed in "
                + "JE988_06 at 'return String(Function('return 1')());' position 14 | stackstrace:  | JE988_06 "
                + "line: 1"},
            {"SELECT je988_07()",
                "REFUSED JavaScript execution error: Uncaught EvalError: Dynamic code evaluation disallowed in "
                + "JE988_07 at 'var f = eval; return String(f('1'));' position 28 | stackstrace:  | JE988_07 line: 1"},
            {"SELECT je988_08()",
                "REFUSED JavaScript execution error: Uncaught EvalError: Dynamic code evaluation disallowed in "
                + "JE988_08 at 'var names = ['a']; var out = []; for (var i = 0; i < names.length; i++) { "
                + "out.push(names[i] + '=' + eval('typeof ' + names[i])); } return out;' position 115 | "
                + "stackstrace:  | JE988_08 line: 1"},
            {"SELECT je988_09()",
                "REFUSED JavaScript execution error: Uncaught EvalError: Dynamic code evaluation disallowed in "
                + "JE988_09 at 'var a = 1; return eval(a === 1 ? '1' : '2');' position 25 | stackstrace:  | "
                + "JE988_09 line: 1"},
        });
    }
}
