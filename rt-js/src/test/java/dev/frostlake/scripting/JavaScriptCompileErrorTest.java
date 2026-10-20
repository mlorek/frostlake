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
 * A JavaScript function's body that does not parse is refused when the function is called, in the account's words
 * (live-verified): {@code JavaScript compilation error: Uncaught SyntaxError: }, the parser's sentence as the
 * account's engine words it - a token it did not expect named by its kind, a text, a number or a character no token
 * starts as {@code Invalid or unexpected token} where it starts - then the routine's name, the quoted line and the
 * position, and no stack lines. A fault past the body's end is quoted on the closing brace's line. A table
 * function's body is read as the parenthesized expression it is.
 */
public class JavaScriptCompileErrorTest extends MeasuredCellsTestSupport {

    /** Each body is created, then refused at its first call. */
    @Test
    public void aScalarBodyThatDoesNotParse() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return (;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Unexpected token ';' in JSE at 'return "
                + "(;' position 8"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return 1 +;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Unexpected token ';' in JSE at 'return "
                + "1 +;' position 10"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return A B;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Unexpected identifier 'B' in JSE at "
                + "'return A B;' position 9"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return 'abc;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Invalid or unexpected token in JSE at "
                + "'return 'abc;' position 7"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return 1 2;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Unexpected number in JSE at 'return 1 "
                + "2;' position 9"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return 'a' 'b';$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Unexpected string in JSE at 'return "
                + "'a' 'b';' position 11"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$if (A) {$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Unexpected end of input in JSE at '}' "
                + "position 1"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return A.;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Unexpected token ';' in JSE at 'return "
                + "A.;' position 9"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return 1 = 2;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Invalid left-hand side in assignment "
                + "in JSE at 'return 1 = 2;' position 7"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return @;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Invalid or unexpected token in JSE at "
                + "'return @;' position 7"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$break;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Illegal break statement in JSE at "
                + "'break;' position 0"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return /abc;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Invalid regular expression: missing / "
                + "in JSE at 'return /abc;' position 7"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$let let = 1; return 1;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: let is disallowed as a lexically bound "
                + "name in JSE at 'let let = 1; return 1;' position 4"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return 1;}$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Unexpected token '}' in JSE at '}' "
                + "position 0"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$var x = ;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Unexpected token ';' in JSE at 'var x "
                + "= ;' position 8"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return #x;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Private field '#x' must be declared in "
                + "an enclosing class in JSE at 'return #x;' position 0"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$function () {}$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Function statements require a function "
                + "name in JSE at 'function () {}' position 0"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return [1, 2;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Unexpected token ';' in JSE at 'return "
                + "[1, 2;' position 12"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return {a: 1;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Unexpected token ';' in JSE at 'return "
                + "{a: 1;' position 12"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return x => ;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Unexpected token ';' in JSE at 'return "
                + "x => ;' position 12"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$class {}$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Unexpected token '{' in JSE at 'class "
                + "{}' position 6"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return 0x;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Invalid or unexpected token in JSE at "
                + "'return 0x;' position 7"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return `abc;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Unexpected end of input in JSE at '}' "
                + "position 1"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$var var = 1;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Unexpected token 'var' in JSE at 'var "
                + "var = 1;' position 4"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return new;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Unexpected token ';' in JSE at 'return "
                + "new;' position 10"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$for (;;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Unexpected token '}' in JSE at '}' "
                + "position 0"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return A +\n  ;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Unexpected token ';' in JSE at '  ;' "
                + "position 2"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$\n\nreturn (;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Unexpected token ';' in JSE at 'return "
                + "(;' position 8"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return 1 ++ 2;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Invalid left-hand side expression in "
                + "postfix operation in JSE at 'return 1 ++ 2;' position 7"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return (1, );$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Unexpected token ')' in JSE at 'return "
                + "(1, );' position 11"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return A ? 1;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Unexpected token ';' in JSE at 'return "
                + "A ? 1;' position 12"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$continue;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Illegal continue statement: no "
                + "surrounding iteration statement in JSE at 'continue;' position 0"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return 08n;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Invalid or unexpected token in JSE at "
                + "'return 08n;' position 7"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return a.b.;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Unexpected token ';' in JSE at 'return "
                + "a.b.;' position 11"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return 'a' + ;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Unexpected token ';' in JSE at 'return "
                + "'a' + ;' position 13"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return 1\n2;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "1.0"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return { 'a': };$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Unexpected token '}' in JSE at 'return "
                + "{ 'a': };' position 14"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$if A return 1;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Unexpected identifier 'A' in JSE at "
                + "'if A return 1;' position 3"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return 1 }$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Unexpected token '}' in JSE at '}' "
                + "position 0"},
            {"CREATE OR REPLACE FUNCTION jse(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$return [;$$",
                "Function JSE successfully created."},
            {"SELECT jse(1::FLOAT)",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Unexpected token ';' in JSE at 'return "
                + "[;' position 8"},
        });
    }

    /** A table function's body is quoted inside its parentheses. */
    @Test
    public void aTableBodyThatDoesNotParse() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION jsq(n FLOAT) RETURNS TABLE (v FLOAT) LANGUAGE JAVASCRIPT AS $$SELECT 42$$",
                "Function JSQ successfully created."},
            {"SELECT * FROM TABLE(jsq(1::FLOAT))",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Unexpected number in JSQ at '(SELECT "
                + "42)' position 8"},
            {"CREATE OR REPLACE FUNCTION jsq(n FLOAT) RETURNS TABLE (v FLOAT) LANGUAGE JAVASCRIPT AS $${processRow: "
                + "function (row, rowWriter, context) { rowWriter.writeRow({V: 1}) ;}$$",
                "Function JSQ successfully created."},
            {"SELECT * FROM TABLE(jsq(1::FLOAT))",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Unexpected token ')' in JSQ at "
                + "'({processRow: function (row, rowWriter, context) { rowWriter.writeRow({V: 1}) ;})' position 80"},
            {"CREATE OR REPLACE FUNCTION jsq(n FLOAT) RETURNS TABLE (v FLOAT) LANGUAGE JAVASCRIPT AS $${processRow: "
                + "function (row, rowWriter, context) { return 1 +; }}$$",
                "Function JSQ successfully created."},
            {"SELECT * FROM TABLE(jsq(1::FLOAT))",
                "REFUSED JavaScript compilation error: Uncaught SyntaxError: Unexpected token ';' in JSQ at "
                + "'({processRow: function (row, rowWriter, context) { return 1 +; }})' position 61"},
        });
    }
}
