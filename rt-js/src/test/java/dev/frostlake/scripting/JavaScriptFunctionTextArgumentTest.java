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
 * What a JavaScript function's handler receives for a VARCHAR parameter: a string, whatever the argument was.
 * A number, a BOOLEAN, a DATE, a TIME, a FLOAT and a VARIANT become the text a cast to VARCHAR makes of them
 * as they arrive - a DATE in the session's DATE_OUTPUT_FORMAT, a VARIANT's object as its JSON - so
 * {@code X.length} counts characters. A declared width is not enforced. A SQL NULL and a VARIANT's JSON null
 * are {@code undefined}. A BINARY, an ARRAY and an OBJECT are refused while the call compiles. The session's
 * DATE_OUTPUT_FORMAT is set by one test and unset whatever its assertions make of the statements between.
 * Every answer is live-verified.
 */
public class JavaScriptFunctionTextArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void aVarcharParameterIsAString() {
        assertAnswer(
            "Function FS successfully created.",
            "CREATE OR REPLACE FUNCTION fs(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return typeof X + "
                + "':' + X.length + ':' + X; $$");
        assertAnswer(
            "string:3:123",
            "SELECT fs(123)");
        assertAnswer(
            "string:3:1.5",
            "SELECT fs(1.5)");
        assertAnswer(
            "string:10:2024-01-01",
            "SELECT fs('2024-01-01'::DATE)");
        assertAnswer(
            "string:4:true",
            "SELECT fs(TRUE)");
        assertAnswer(
            "string:3:1.5",
            "SELECT fs(1.5::FLOAT)");
        assertAnswer(
            "string:3:abc",
            "SELECT fs('abc')");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught TypeError: Cannot read properties of undefined (reading "
                + "'length') in FS at ' return typeof X + ':' + X.length + ':' + X; ' position 27 | stackstrace:  | "
                + "FS line: 1",
            "SELECT fs(NULL)");
        assertAnswer(
            "string:1:q",
            "SELECT fs(PARSE_JSON('\"q\"'))");
        assertAnswer(
            "string:8:10:11:12",
            "SELECT fs('10:11:12'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'FS': (ARRAY)",
            "SELECT fs(ARRAY_CONSTRUCT(1))");
        assertAnswer(
            "Function FS successfully created.",
            "CREATE OR REPLACE FUNCTION fs(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return X === "
                + "undefined ? 'undef' : typeof X + ':' + X.length + ':' + X; $$");
        assertAnswer(
            "string:3:1.5",
            "SELECT fs(1.50)");
        assertAnswer(
            "string:4:1.50",
            "SELECT fs(1.50::NUMBER(10,2))");
        assertAnswer(
            "string:3:100",
            "SELECT fs(100::FLOAT)");
        assertAnswer(
            "string:3:0.3",
            "SELECT fs(0.1::FLOAT + 0.2::FLOAT)");
        assertAnswer(
            "string:5:1e+30",
            "SELECT fs(1e30::FLOAT)");
        assertAnswer(
            "string:3:NaN",
            "SELECT fs('NaN'::FLOAT)");
        assertAnswer(
            "string:4:-0.5",
            "SELECT fs(-0.5)");
        assertAnswer(
            "string:38:12345678901234567890123456789012345678",
            "SELECT fs(12345678901234567890123456789012345678)");
        assertAnswer(
            "false",
            "SELECT fs('2024-03-05 10:11:12.345'::TIMESTAMP_NTZ) = '2024-03-05 "
                + "10:11:12.345'::TIMESTAMP_NTZ::VARCHAR");
        assertAnswer(
            "string:8:10:11:12",
            "SELECT fs('10:11:12.5'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'FS': (BINARY(67108864))",
            "SELECT fs(TO_BINARY('FF01', 'HEX'))");
        assertAnswer(
            "string:3:1.5",
            "SELECT fs(PARSE_JSON('1.5'))");
        assertAnswer(
            "string:7:{\"a\":1}",
            "SELECT fs(PARSE_JSON('{\"a\":1}'))");
        assertAnswer(
            "string:5:[1,2]",
            "SELECT fs(PARSE_JSON('[1,2]'))");
        assertAnswer(
            "undef",
            "SELECT fs(PARSE_JSON('null'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'FS': (OBJECT)",
            "SELECT fs(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "string:1:q",
            "SELECT fs(TO_VARIANT('q'))");
        assertAnswer(
            "string:0:",
            "SELECT fs('')");
        assertAnswer(
            "undef",
            "SELECT fs(NULL)");
        assertAnswer(
            "string:5:false",
            "SELECT fs(FALSE)");
        assertAnswer(
            "string:7:h\u00e9llo \u2713",
            "SELECT fs('h\u00e9llo \u2713')");
        assertAnswer(
            "Function FS3 successfully created.",
            "CREATE OR REPLACE FUNCTION fs3(x VARCHAR(3)) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return typeof "
                + "X + ':' + X.length + ':' + X; $$");
        assertAnswer(
            "string:6:abcdef",
            "SELECT fs3('abcdef')");
        assertAnswer(
            "string:6:123456",
            "SELECT fs3(123456)");
        assertAnswer(
            "string:2:ab",
            "SELECT fs3('ab')");
        assertAnswer(
            "Function FC successfully created.",
            "CREATE OR REPLACE FUNCTION fc(x CHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return typeof X + "
                + "':' + X.length + ':' + X; $$");
        assertAnswer(
            "string:3:abc",
            "SELECT fc('abc')");
    }

    @Test
    public void aDateBecomesTextInTheSessionsFormat() {
        try {
            assertAnswer(
                "Function FS successfully created.",
                "CREATE OR REPLACE FUNCTION fs(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return X === "
                    + "undefined ? 'undef' : typeof X + ':' + X.length + ':' + X; $$");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET DATE_OUTPUT_FORMAT = 'DD/MM/YYYY'");
            assertAnswer(
                "string:10:05/03/2024",
                "SELECT fs('2024-03-05'::DATE)");
            assertAnswer(
                "05/03/2024",
                "SELECT '2024-03-05'::DATE::VARCHAR");
        } finally {
            answer(
                "ALTER SESSION UNSET DATE_OUTPUT_FORMAT");
        }
    }

    @Test
    public void aColumnForAVarcharParameter() {
        assertAnswer(
            "Function FS successfully created.",
            "CREATE OR REPLACE FUNCTION fs(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return X === "
                + "undefined ? 'undef' : typeof X + ':' + X.length + ':' + X; $$");
        assertAnswer(
            "Table TX successfully created.",
            "CREATE OR REPLACE TABLE tx (id NUMBER, n NUMBER(10,2), f FLOAT, d DATE, b BOOLEAN, s VARCHAR)");
        assertAnswer(
            "3",
            "INSERT INTO tx VALUES (1, 1.50, 2.5, '2024-03-05', TRUE, 'yes'), (2, NULL, NULL, NULL, NULL, NULL), "
                + "(3, -7, 0, '1999-12-31', FALSE, 'f')");
        assertAnswer(
            "1|string:4:1.50|string:3:2.5|string:10:2024-03-05|string:4:true|string:3:yes ;; "
                + "2|undef|undef|undef|undef|undef ;; "
                + "3|string:5:-7.00|string:1:0|string:10:1999-12-31|string:5:false|string:1:f",
            "SELECT id, fs(n), fs(f), fs(d), fs(b), fs(s) FROM tx ORDER BY id");
    }
}
