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
 * A callee that is no function is named as the account's engine prints it from its source when the call passes nothing
 * (live-verified): a literal array as written with no space after its commas, a sequence with its operands spaced
 * around the commas, a void as (void 0), an object literal by an intermediate value for each property, a conditional as
 * three intermediate values and an arithmetic of number literals as the number it folds to, where comparisons and
 * logical operators keep their parentheses and a function, a construction or a call stays an intermediate value.
 */
public class JavaScriptCalleeWordingTest extends MeasuredCellsTestSupport {

    /** Thirty callees, each called with nothing in a function of its own. */
    @Test
    public void aCalleeIsNamedFromItsSource() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION jsc0() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (void 0)(); $$",
                "Function JSC0 successfully created."},
            {"SELECT jsc0()",
                "REFUSED JavaScript execution error: Uncaught TypeError: (void 0) is not a function in JSC0 at ' "
                + "return (void 0)(); ' position 16 | stackstrace:  | JSC0 line: 1"},
            {"CREATE OR REPLACE FUNCTION jsc1() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (1, 2)(); $$",
                "Function JSC1 successfully created."},
            {"SELECT jsc1()",
                "REFUSED JavaScript execution error: Uncaught TypeError: (1 , 2) is not a function in JSC1 at ' return "
                + "(1, 2)(); ' position 14 | stackstrace:  | JSC1 line: 1"},
            {"CREATE OR REPLACE FUNCTION jsc2() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return [1][0](); $$",
                "Function JSC2 successfully created."},
            {"SELECT jsc2()",
                "REFUSED JavaScript execution error: Uncaught TypeError: [1][0] is not a function in JSC2 at ' return "
                + "[1][0](); ' position 14 | stackstrace:  | JSC2 line: 1"},
            {"CREATE OR REPLACE FUNCTION jsc3() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (1 + 2)(); $$",
                "Function JSC3 successfully created."},
            {"SELECT jsc3()",
                "REFUSED JavaScript execution error: Uncaught TypeError: 3 is not a function in JSC3 at ' return (1 + "
                + "2)(); ' position 15 | stackstrace:  | JSC3 line: 1"},
            {"CREATE OR REPLACE FUNCTION jsc4() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (typeof 1)(); $$",
                "Function JSC4 successfully created."},
            {"SELECT jsc4()",
                "REFUSED JavaScript execution error: Uncaught TypeError: (typeof 1) is not a function in JSC4 at ' "
                + "return (typeof 1)(); ' position 18 | stackstrace:  | JSC4 line: 1"},
            {"CREATE OR REPLACE FUNCTION jsc5() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return [1, 2][0](); $$",
                "Function JSC5 successfully created."},
            {"SELECT jsc5()",
                "REFUSED JavaScript execution error: Uncaught TypeError: [1,2][0] is not a function in JSC5 at ' "
                + "return [1, 2][0](); ' position 17 | stackstrace:  | JSC5 line: 1"},
            {"CREATE OR REPLACE FUNCTION jsc6() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return ({a: 1}).a(); $$",
                "Function JSC6 successfully created."},
            {"SELECT jsc6()",
                "REFUSED JavaScript execution error: Uncaught TypeError: {(intermediate value)}.a is not a function in "
                + "JSC6 at ' return ({a: 1}).a(); ' position 17 | stackstrace:  | JSC6 line: 1"},
            {"CREATE OR REPLACE FUNCTION jsc7() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (!0)(); $$",
                "Function JSC7 successfully created."},
            {"SELECT jsc7()",
                "REFUSED JavaScript execution error: Uncaught TypeError: true is not a function in JSC7 at ' return "
                + "(!0)(); ' position 12 | stackstrace:  | JSC7 line: 1"},
            {"CREATE OR REPLACE FUNCTION jsc8() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (-1)(); $$",
                "Function JSC8 successfully created."},
            {"SELECT jsc8()",
                "REFUSED JavaScript execution error: Uncaught TypeError: -1 is not a function in JSC8 at ' return "
                + "(-1)(); ' position 12 | stackstrace:  | JSC8 line: 1"},
            {"CREATE OR REPLACE FUNCTION jsc9() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return 'x'(); $$",
                "Function JSC9 successfully created."},
            {"SELECT jsc9()",
                "REFUSED JavaScript execution error: Uncaught TypeError: \"x\" is not a function in JSC9 at ' return "
                + "'x'(); ' position 11 | stackstrace:  | JSC9 line: 1"},
            {"CREATE OR REPLACE FUNCTION jsc10() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (function(){})()(); "
                + "$$",
                "Function JSC10 successfully created."},
            {"SELECT jsc10()",
                "REFUSED JavaScript execution error: Uncaught TypeError: (intermediate value)(...) is not a function "
                + "in JSC10 at ' return (function(){})()(); ' position 24 | stackstrace:  | JSC10 line: 1"},
            {"CREATE OR REPLACE FUNCTION jsc11() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return [1][0][0](); $$",
                "Function JSC11 successfully created."},
            {"SELECT jsc11()",
                "REFUSED JavaScript execution error: Uncaught TypeError: [1][0][0] is not a function in JSC11 at ' "
                + "return [1][0][0](); ' position 17 | stackstrace:  | JSC11 line: 1"},
            {"CREATE OR REPLACE FUNCTION jsc12() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return [[1]][0][0](); $$",
                "Function JSC12 successfully created."},
            {"SELECT jsc12()",
                "REFUSED JavaScript execution error: Uncaught TypeError: [[1]][0][0] is not a function in JSC12 at ' "
                + "return [[1]][0][0](); ' position 19 | stackstrace:  | JSC12 line: 1"},
            {"CREATE OR REPLACE FUNCTION jsc13() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (null)(); $$",
                "Function JSC13 successfully created."},
            {"SELECT jsc13()",
                "REFUSED JavaScript execution error: Uncaught TypeError: null is not a function in JSC13 at ' return "
                + "(null)(); ' position 14 | stackstrace:  | JSC13 line: 1"},
            {"CREATE OR REPLACE FUNCTION jsc14() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return [1].x(); $$",
                "Function JSC14 successfully created."},
            {"SELECT jsc14()",
                "REFUSED JavaScript execution error: Uncaught TypeError: [1].x is not a function in JSC14 at ' return "
                + "[1].x(); ' position 12 | stackstrace:  | JSC14 line: 1"},
            {"CREATE OR REPLACE FUNCTION jsc15() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return [1]['x'](); $$",
                "Function JSC15 successfully created."},
            {"SELECT jsc15()",
                "REFUSED JavaScript execution error: Uncaught TypeError: [1].x is not a function in JSC15 at ' return "
                + "[1]['x'](); ' position 16 | stackstrace:  | JSC15 line: 1"},
            {"CREATE OR REPLACE FUNCTION jsc16() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (1, 2, 3)(); $$",
                "Function JSC16 successfully created."},
            {"SELECT jsc16()",
                "REFUSED JavaScript execution error: Uncaught TypeError: (1 , 2 , 3) is not a function in JSC16 at ' "
                + "return (1, 2, 3)(); ' position 17 | stackstrace:  | JSC16 line: 1"},
            {"CREATE OR REPLACE FUNCTION jsc17() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (void 0, 1)(); $$",
                "Function JSC17 successfully created."},
            {"SELECT jsc17()",
                "REFUSED JavaScript execution error: Uncaught TypeError: ((void 0) , 1) is not a function in JSC17 at "
                + "' return (void 0, 1)(); ' position 19 | stackstrace:  | JSC17 line: 1"},
            {"CREATE OR REPLACE FUNCTION jsc18() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return new Array(1)[0](); "
                + "$$",
                "Function JSC18 successfully created."},
            {"SELECT jsc18()",
                "REFUSED JavaScript execution error: Uncaught TypeError: (intermediate value)[0] is not a function in "
                + "JSC18 at ' return new Array(1)[0](); ' position 23 | stackstrace:  | JSC18 line: 1"},
            {"CREATE OR REPLACE FUNCTION jsc19() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (1 ? 2 : 3)(); $$",
                "Function JSC19 successfully created."},
            {"SELECT jsc19()",
                "REFUSED JavaScript execution error: Uncaught TypeError: (intermediate value)(intermediate "
                + "value)(intermediate value) is not a function in JSC19 at ' return (1 ? 2 : 3)(); ' position 19 | "
                + "stackstrace:  | JSC19 line: 1"},
            {"CREATE OR REPLACE FUNCTION jsc20() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (1 < 2)(); $$",
                "Function JSC20 successfully created."},
            {"SELECT jsc20()",
                "REFUSED JavaScript execution error: Uncaught TypeError: (1 < 2) is not a function in JSC20 at ' "
                + "return (1 < 2)(); ' position 15 | stackstrace:  | JSC20 line: 1"},
            {"CREATE OR REPLACE FUNCTION jsc21() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return [](); $$",
                "Function JSC21 successfully created."},
            {"SELECT jsc21()",
                "REFUSED JavaScript execution error: Uncaught TypeError: [] is not a function in JSC21 at ' return "
                + "[](); ' position 10 | stackstrace:  | JSC21 line: 1"},
            {"CREATE OR REPLACE FUNCTION jsc22() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return ({})(); $$",
                "Function JSC22 successfully created."},
            {"SELECT jsc22()",
                "REFUSED JavaScript execution error: Uncaught TypeError: {} is not a function in JSC22 at ' return "
                + "({})(); ' position 12 | stackstrace:  | JSC22 line: 1"},
            {"CREATE OR REPLACE FUNCTION jsc23() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (x => 1)()(); $$",
                "Function JSC23 successfully created."},
            {"SELECT jsc23()",
                "REFUSED JavaScript execution error: Uncaught TypeError: (intermediate value)(...) is not a function "
                + "in JSC23 at ' return (x => 1)()(); ' position 18 | stackstrace:  | JSC23 line: 1"},
            {"CREATE OR REPLACE FUNCTION jsc24() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return Math.PI(); $$",
                "Function JSC24 successfully created."},
            {"SELECT jsc24()",
                "REFUSED JavaScript execution error: Uncaught TypeError: Math.PI is not a function in JSC24 at ' "
                + "return Math.PI(); ' position 13 | stackstrace:  | JSC24 line: 1"},
            {"CREATE OR REPLACE FUNCTION jsc25() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (2 ** 3)(); $$",
                "Function JSC25 successfully created."},
            {"SELECT jsc25()",
                "REFUSED JavaScript execution error: Uncaught TypeError: 8 is not a function in JSC25 at ' return (2 "
                + "** 3)(); ' position 16 | stackstrace:  | JSC25 line: 1"},
            {"CREATE OR REPLACE FUNCTION jsc26() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (1 && 0)(); $$",
                "Function JSC26 successfully created."},
            {"SELECT jsc26()",
                "REFUSED JavaScript execution error: Uncaught TypeError: (1 && 0) is not a function in JSC26 at ' "
                + "return (1 && 0)(); ' position 16 | stackstrace:  | JSC26 line: 1"},
            {"CREATE OR REPLACE FUNCTION jsc27() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (0 || null)(); $$",
                "Function JSC27 successfully created."},
            {"SELECT jsc27()",
                "REFUSED JavaScript execution error: Uncaught TypeError: (0 || null) is not a function in JSC27 at ' "
                + "return (0 || null)(); ' position 19 | stackstrace:  | JSC27 line: 1"},
            {"CREATE OR REPLACE FUNCTION jsc28() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return ('a' + 'b')(); $$",
                "Function JSC28 successfully created."},
            {"SELECT jsc28()",
                "REFUSED JavaScript execution error: Uncaught TypeError: \"ab\" is not a function in JSC28 at ' return "
                + "('a' + 'b')(); ' position 19 | stackstrace:  | JSC28 line: 1"},
            {"CREATE OR REPLACE FUNCTION jsc29() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return [1,2,3].length(); $$",
                "Function JSC29 successfully created."},
            {"SELECT jsc29()",
                "REFUSED JavaScript execution error: Uncaught TypeError: [1,2,3].length is not a function in JSC29 at "
                + "' return [1,2,3].length(); ' position 16 | stackstrace:  | JSC29 line: 1"},
        });
    }
}
