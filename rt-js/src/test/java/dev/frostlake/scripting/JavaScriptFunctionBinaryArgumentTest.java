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
 * What a JavaScript function's handler receives for a BINARY parameter: a {@code Uint8Array} of the value's
 * bytes, each from 0 to 255, with the typed array's length, methods and buffer - whatever built the value, a
 * column included, and whatever width the parameter declares. A SQL NULL is {@code undefined}. A text, a
 * number and a VARIANT are refused while the call compiles. A BINARY answer takes a {@code Uint8Array} and
 * nothing else - a text, an array, another typed array and an ArrayBuffer are no BINARY - and bytes longer
 * than a declared width are refused; a VARCHAR answer of a {@code Uint8Array} is its elements joined by
 * commas. Every answer is live-verified.
 */
public class JavaScriptFunctionBinaryArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void aBinaryParameterIsAUint8Array() {
        assertAnswer(
            "Function FB successfully created.",
            "CREATE OR REPLACE FUNCTION fb(b BINARY) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return B[0] + ',' "
                + "+ B[1] + ',' + B.length + ',' + (B instanceof Uint8Array); $$");
        assertAnswer(
            "255,1,2,true",
            "SELECT fb(TO_BINARY('FF01', 'HEX'))");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught TypeError: Cannot read properties of undefined (reading "
                + "'0') in FB at ' return B[0] + ',' + B[1] + ',' + B.length + ',' + (B instanceof Uint8Array); ' "
                + "position 9 | stackstrace:  | FB line: 1",
            "SELECT fb(NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'FB': (VARCHAR(4))",
            "SELECT fb('FF01')");
        assertAnswer(
            "undefined,undefined,0,true",
            "SELECT fb(TO_BINARY('', 'HEX'))");
        assertAnswer(
            "Function FB successfully created.",
            "CREATE OR REPLACE FUNCTION fb(b BINARY) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (B "
                + "instanceof Uint8Array) + ',' + B.constructor.name + ',' + B.length + ',' + B.byteLength + ',' + "
                + "Array.prototype.join.call(B, ' ') + ',' + typeof B.slice + ',' + "
                + "Object.prototype.toString.call(B); $$");
        assertAnswer(
            "true,Uint8Array,2,2,255 1,function,[object Uint8Array]",
            "SELECT fb(TO_BINARY('FF01', 'HEX'))");
        assertAnswer(
            "true,Uint8Array,4,4,128 255 127 0,function,[object Uint8Array]",
            "SELECT fb(TO_BINARY('80FF7F00', 'HEX'))");
        assertAnswer(
            "true,Uint8Array,2,2,104 105,function,[object Uint8Array]",
            "SELECT fb(TO_BINARY('aGk=', 'BASE64'))");
        assertAnswer(
            "true,Uint8Array,2,2,104 105,function,[object Uint8Array]",
            "SELECT fb(TO_BINARY('hi', 'UTF-8'))");
        assertAnswer(
            "true,Uint8Array,2,2,202 254,function,[object Uint8Array]",
            "SELECT fb(X'CAFE')");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught TypeError: Cannot read properties of undefined (reading "
                + "'constructor') in FB at ' return (B instanceof Uint8Array) + ',' + B.constructor.name + ',' + "
                + "B.length + ',' + B.byteLength + ',' + Array.prototype.join.call(B, ' ') + ',' + typeof B.slice + "
                + "',' + Object.prototype.toString.call(B); ' position 44 | stackstrace:  | FB line: 1",
            "SELECT fb(NULL::BINARY) IS NULL");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'FB': (VARIANT)",
            "SELECT fb(TO_VARIANT(TO_BINARY('FF01', 'HEX')))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'FB': (VARIANT)",
            "SELECT fb(PARSE_JSON('\"FF01\"'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'FB': (NUMBER(1,0))",
            "SELECT fb(1)");
        assertAnswer(
            "true,Uint8Array,1,1,0,function,[object Uint8Array]",
            "SELECT fb(HEX_DECODE_BINARY('00'))");
        assertAnswer(
            "true,Uint8Array,16,16,12 193 117 185 192 241 182 168 49 195 153 226 105 119 38 97,function,[object "
                + "Uint8Array]",
            "SELECT fb(MD5_BINARY('a'))");
        assertAnswer(
            "Table TB successfully created.",
            "CREATE OR REPLACE TABLE tb (id NUMBER, b BINARY, s VARCHAR)");
        assertAnswer(
            "3",
            "INSERT INTO tb SELECT 1, TO_BINARY('FF01', 'HEX'), 'FF01' UNION ALL SELECT 2, NULL, NULL UNION ALL "
                + "SELECT 3, TO_BINARY('', 'HEX'), ''");
        assertAnswer(
            "Function FBN successfully created.",
            "CREATE OR REPLACE FUNCTION fbn(b BINARY) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return B === "
                + "undefined ? 'undef' : B === null ? 'null' : B.length + ':' + Array.prototype.join.call(B, ' '); $$");
        assertAnswer(
            "1|2:255 1 ;; 2|undef ;; 3|0:",
            "SELECT id, fbn(b) FROM tb ORDER BY id");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'FBN': (VARCHAR(16777216))",
            "SELECT id, fbn(s) FROM tb ORDER BY id");
        assertAnswer(
            "1|2:255 1 ;; 2|undef ;; 3|0:",
            "SELECT id, fbn(TRY_TO_BINARY(s, 'HEX')) FROM tb ORDER BY id");
        assertAnswer(
            "Function FB8 successfully created.",
            "CREATE OR REPLACE FUNCTION fb8(b BINARY(1)) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return "
                + "B.length; $$");
        assertAnswer(
            "2",
            "SELECT fb8(TO_BINARY('FF01', 'HEX'))");
        assertAnswer(
            "1",
            "SELECT fb8(TO_BINARY('FF', 'HEX'))");
        assertAnswer(
            "Function FBM successfully created.",
            "CREATE OR REPLACE FUNCTION fbm(b BINARY) RETURNS BINARY LANGUAGE JAVASCRIPT AS $$ B[0] = 7; return "
                + "B; $$");
        assertAnswer(
            "0701",
            "SELECT HEX_ENCODE(fbm(TO_BINARY('FF01', 'HEX')))");
        assertAnswer(
            "Function FBS successfully created.",
            "CREATE OR REPLACE FUNCTION fbs(b BINARY) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var s = 0; for "
                + "(var i = 0; i < B.length; i++) { s += B[i]; } return s + ':' + B.slice(1).length + ':' + "
                + "(B.buffer instanceof ArrayBuffer); $$");
        assertAnswer(
            "510:2:true",
            "SELECT fbs(TO_BINARY('FFFE01', 'HEX'))");
    }

    @Test
    public void aBinaryAnswerIsAUint8Array() {
        assertAnswer(
            "Function RB successfully created.",
            "CREATE OR REPLACE FUNCTION rb(b BINARY) RETURNS BINARY LANGUAGE JAVASCRIPT AS $$ return B; $$");
        assertAnswer(
            "FF01",
            "SELECT HEX_ENCODE(rb(TO_BINARY('FF01', 'HEX')))");
        assertAnswer(
            "Function RB2 successfully created.",
            "CREATE OR REPLACE FUNCTION rb2(b BINARY) RETURNS BINARY LANGUAGE JAVASCRIPT AS $$ return new "
                + "Uint8Array([B[1], B[0], 128]); $$");
        assertAnswer(
            "01FF80",
            "SELECT HEX_ENCODE(rb2(TO_BINARY('FF01', 'HEX')))");
        assertAnswer(
            "Function RB3 successfully created.",
            "CREATE OR REPLACE FUNCTION rb3() RETURNS BINARY LANGUAGE JAVASCRIPT AS $$ return 'FF01'; $$");
        assertAnswer(
            "REFUSED JavaScript execution error: Return value is not a BINARY",
            "SELECT rb3()");
        assertAnswer(
            "Function RB4 successfully created.",
            "CREATE OR REPLACE FUNCTION rb4() RETURNS BINARY LANGUAGE JAVASCRIPT AS $$ return [255, 1]; $$");
        assertAnswer(
            "REFUSED JavaScript execution error: Return value is not a BINARY",
            "SELECT rb4()");
        assertAnswer(
            "Function RB5 successfully created.",
            "CREATE OR REPLACE FUNCTION rb5() RETURNS BINARY LANGUAGE JAVASCRIPT AS $$ return undefined; $$");
        assertAnswer(
            "true",
            "SELECT rb5() IS NULL");
        assertAnswer(
            "Function RB6 successfully created.",
            "CREATE OR REPLACE FUNCTION rb6() RETURNS BINARY LANGUAGE JAVASCRIPT AS $$ return new Uint8Array(0); "
                + "$$");
        assertAnswer(
            "|0",
            "SELECT HEX_ENCODE(rb6()), LENGTH(rb6())");
        assertAnswer(
            "Function RB7 successfully created.",
            "CREATE OR REPLACE FUNCTION rb7() RETURNS BINARY(1) LANGUAGE JAVASCRIPT AS $$ return new "
                + "Uint8Array([1, 2]); $$");
        assertAnswer(
            "REFUSED Binary value '0102' is too long and would be truncated",
            "SELECT HEX_ENCODE(rb7())");
        assertAnswer(
            "Function RB8 successfully created.",
            "CREATE OR REPLACE FUNCTION rb8() RETURNS BINARY LANGUAGE JAVASCRIPT AS $$ return new "
                + "Uint16Array([258]); $$");
        assertAnswer(
            "REFUSED JavaScript execution error: Return value is not a BINARY",
            "SELECT rb8()");
        assertAnswer(
            "Function RB9 successfully created.",
            "CREATE OR REPLACE FUNCTION rb9() RETURNS BINARY LANGUAGE JAVASCRIPT AS $$ return new Uint8Array([1, "
                + "2]).buffer; $$");
        assertAnswer(
            "REFUSED JavaScript execution error: Return value is not a BINARY",
            "SELECT rb9()");
        assertAnswer(
            "Function RB10 successfully created.",
            "CREATE OR REPLACE FUNCTION rb10() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return new "
                + "Uint8Array([255, 1]); $$");
        assertAnswer(
            "255,1",
            "SELECT rb10()");
    }
}
