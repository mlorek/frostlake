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
 * What a JavaScript function's handler receives for a FLOAT parameter: a JavaScript number, whatever the
 * argument was - a decimal literal, a NUMBER column, a text, a VARIANT - because the argument becomes a FLOAT as
 * it arrives, so {@code X + 1} adds and the value handed back is a number. A text that is no number is the
 * call's refusal, {@code Numeric value 'abc' is not recognized}; NaN and the infinities read; a BOOLEAN and a
 * DATE are refused while the call compiles. A SQL NULL is {@code undefined}, not {@code null}. A NUMBER
 * parameter or result is refused when the function is created. Every answer is live-verified.
 */
public class JavaScriptFunctionFloatArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void aFloatParameterIsANumber() {
        assertAnswer(
            "Function DEC1 successfully created.",
            "CREATE OR REPLACE FUNCTION dec1(x FLOAT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return typeof X + "
                + "'|' + (X + 1) + '|' + (X * 2); $$");
        assertAnswer(
            "number|1.1|0.2",
            "SELECT dec1(0.1)");
        assertAnswer(
            "number|3.5|5",
            "SELECT dec1(2.5)");
        assertAnswer(
            "number|2|2",
            "SELECT dec1(1)");
        assertAnswer(
            "number|1.1|0.2",
            "SELECT dec1(0.1::FLOAT)");
        assertAnswer(
            "undefined|NaN|NaN",
            "SELECT dec1(NULL)");
        assertAnswer(
            "number|3.5|5",
            "SELECT dec1('2.5')");
        assertAnswer(
            "REFUSED Numeric value 'abc' is not recognized",
            "SELECT dec1('abc')");
        assertAnswer(
            "number|NaN|NaN",
            "SELECT dec1('NaN')");
        assertAnswer(
            "number|Infinity|Infinity",
            "SELECT dec1('inf')");
        assertAnswer(
            "number|12345678901234567000|24691357802469134000",
            "SELECT dec1(12345678901234567890.123)");
        assertAnswer(
            "number|2.5|3",
            "SELECT dec1(PARSE_JSON('1.5'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DEC1': (BOOLEAN)",
            "SELECT dec1(TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DEC1': (DATE)",
            "SELECT dec1('2024-01-01'::DATE)");
        assertAnswer(
            "number|2.5|3",
            "SELECT dec1(1.5::NUMBER(10,2))");
        assertAnswer(
            "number|1|0",
            "SELECT dec1(-0.0::FLOAT)");
        assertAnswer(
            "Function DEC2 successfully created.",
            "CREATE OR REPLACE FUNCTION dec2(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return X + 1; $$");
        assertAnswer(
            "1.1",
            "SELECT dec2(0.1)::VARCHAR");
        assertAnswer(
            "3.5",
            "SELECT dec2(2.5)::VARCHAR");
        assertAnswer(
            "2",
            "SELECT dec2(1)::VARCHAR");
        assertAnswer(
            "Table TN successfully created.",
            "CREATE OR REPLACE TABLE tn (n NUMBER(10,2), f FLOAT, s VARCHAR, i NUMBER)");
        assertAnswer(
            "1",
            "INSERT INTO tn VALUES (1.25, 1.5, '2.5', 7)");
        assertAnswer(
            "Function FRET successfully created.",
            "CREATE OR REPLACE FUNCTION fret(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return X; $$");
        assertAnswer(
            "2.25",
            "SELECT (fret(n) + 1)::VARCHAR FROM tn");
        assertAnswer(
            "1.5",
            "SELECT fret(f)::VARCHAR FROM tn");
        assertAnswer(
            "7",
            "SELECT fret(i)::VARCHAR FROM tn");
        assertAnswer(
            "2.5",
            "SELECT fret(s)::VARCHAR FROM tn");
        assertAnswer(
            "null",
            "SELECT fret(NULL)::VARCHAR");
        assertAnswer(
            "Function DBL successfully created.",
            "CREATE OR REPLACE FUNCTION dbl(x DOUBLE) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return typeof X + "
                + "'|' + (X + 1) + '|' + (X * 2); $$");
        assertAnswer(
            "number|1.1|0.2",
            "SELECT dbl(0.1)");
        assertAnswer(
            "Function RL successfully created.",
            "CREATE OR REPLACE FUNCTION rl(x REAL) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return typeof X + "
                + "'|' + (X + 1) + '|' + (X * 2); $$");
        assertAnswer(
            "number|3.5|5",
            "SELECT rl(2.5)");
        assertAnswer(
            "Function TWO successfully created.",
            "CREATE OR REPLACE FUNCTION two(a FLOAT, b FLOAT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (A "
                + "+ B) + '|' + typeof A + '|' + typeof B; $$");
        assertAnswer(
            "0.30000000000000004|number|number",
            "SELECT two(0.1, 0.2)");
        assertAnswer(
            "3.5|number|number",
            "SELECT two(1, 2.5)");
        assertAnswer(
            "3|number|number",
            "SELECT two('1', 2)");
        assertAnswer(
            "REFUSED Language JAVASCRIPT does not support type 'NUMBER(38,0)' for argument or return type.",
            "CREATE OR REPLACE FUNCTION num(x NUMBER) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return typeof X; "
                + "$$");
        assertAnswer(
            "REFUSED Language JAVASCRIPT does not support type 'NUMBER(38,0)' for argument or return type.",
            "CREATE OR REPLACE FUNCTION retn(x FLOAT) RETURNS NUMBER LANGUAGE JAVASCRIPT AS $$ return X; $$");
        assertAnswer(
            "Function ARGS successfully created.",
            "CREATE OR REPLACE FUNCTION args(x FLOAT, y FLOAT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return "
                + "arguments.length + '|' + (X === undefined) + '|' + (Y === null); $$");
        assertAnswer(
            "2|true|false",
            "SELECT args(NULL, NULL)");
        assertAnswer(
            "2|false|false",
            "SELECT args(1, NULL)");
    }
}
