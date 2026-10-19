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
 * What a JavaScript function's handler receives for a BOOLEAN parameter: the number 1 or 0, never
 * {@code true} or {@code false}. A text that spells a boolean, a number and a VARIANT holding a boolean or
 * such a text become the BOOLEAN first, so {@code 'false'} and {@code 'no'} are 0 and the handler takes the
 * falsy branch; a text that spells none, an empty one and a VARIANT holding a number are the call's refusal,
 * and a DATE is refused while the call compiles. A SQL NULL and a VARIANT's JSON null are {@code undefined}.
 * A BOOLEAN answer is the truthiness of what the handler returns, and a NUMBER answer cannot be declared.
 * Every answer is live-verified.
 */
public class JavaScriptFunctionBooleanArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void aBooleanParameterIsOneOrZero() {
        assertAnswer(
            "Function FBO successfully created.",
            "CREATE OR REPLACE FUNCTION fbo(x BOOLEAN) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return typeof X "
                + "+ ':' + (X ? 'yes' : 'no') + ':' + X + ':' + (X === true) + ':' + (X === 1); $$");
        assertAnswer(
            "number:no:0:false:false",
            "SELECT fbo('false')");
        assertAnswer(
            "number:no:0:false:false",
            "SELECT fbo('no')");
        assertAnswer(
            "number:yes:1:false:true",
            "SELECT fbo(TRUE)");
        assertAnswer(
            "number:no:0:false:false",
            "SELECT fbo(FALSE)");
        assertAnswer(
            "number:no:0:false:false",
            "SELECT fbo(0)");
        assertAnswer(
            "number:yes:1:false:true",
            "SELECT fbo(5)");
        assertAnswer(
            "undefined:no:undefined:false:false",
            "SELECT fbo(NULL)");
        assertAnswer(
            "REFUSED Boolean value 'maybe' is not recognized",
            "SELECT fbo('maybe')");
        assertAnswer(
            "Function RBO successfully created.",
            "CREATE OR REPLACE FUNCTION rbo(x BOOLEAN) RETURNS BOOLEAN LANGUAGE JAVASCRIPT AS $$ return X; $$");
        assertAnswer(
            "true|false",
            "SELECT rbo(TRUE), rbo(FALSE)");
        assertAnswer(
            "Function RBO2 successfully created.",
            "CREATE OR REPLACE FUNCTION rbo2(x BOOLEAN) RETURNS BOOLEAN LANGUAGE JAVASCRIPT AS $$ return !X; $$");
        assertAnswer(
            "false|true",
            "SELECT rbo2(TRUE), rbo2(FALSE)");
        assertAnswer(
            "Table TX successfully created.",
            "CREATE OR REPLACE TABLE tx (id NUMBER, n NUMBER(10,2), f FLOAT, d DATE, b BOOLEAN, s VARCHAR)");
        assertAnswer(
            "3",
            "INSERT INTO tx VALUES (1, 1.50, 2.5, '2024-03-05', TRUE, 'yes'), (2, NULL, NULL, NULL, NULL, NULL), "
                + "(3, -7, 0, '1999-12-31', FALSE, 'f')");
        assertAnswer(
            "Function FBO successfully created.",
            "CREATE OR REPLACE FUNCTION fbo(x BOOLEAN) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return X === "
                + "undefined ? 'undef' : typeof X + ':' + X + ':' + (X ? 'yes' : 'no'); $$");
        assertAnswer(
            "number:1:yes|number:1:yes|number:1:yes|number:1:yes|number:1:yes|number:1:yes",
            "SELECT fbo('TRUE'), fbo('t'), fbo('yes'), fbo('y'), fbo('on'), fbo('1')");
        assertAnswer(
            "number:0:no|number:0:no|number:0:no|number:0:no|number:0:no|number:0:no",
            "SELECT fbo('FALSE'), fbo('f'), fbo('no'), fbo('n'), fbo('off'), fbo('0')");
        assertAnswer(
            "number:1:yes|number:0:no|number:1:yes|number:0:no|number:1:yes",
            "SELECT fbo(1.5), fbo(0.0), fbo(-1), fbo(0::FLOAT), fbo(2::FLOAT)");
        assertAnswer(
            "REFUSED Boolean value '' is not recognized",
            "SELECT fbo('')");
        assertAnswer(
            "number:1:yes",
            "SELECT fbo(' true ')");
        assertAnswer(
            "REFUSED Failed to cast variant value 1 to BOOLEAN",
            "SELECT fbo(PARSE_JSON('true')), fbo(PARSE_JSON('false')), fbo(PARSE_JSON('1')), "
                + "fbo(PARSE_JSON('\"yes\"'))");
        assertAnswer(
            "undef",
            "SELECT fbo(PARSE_JSON('null'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'FBO': (DATE)",
            "SELECT fbo('2024-01-01'::DATE)");
        assertAnswer(
            "number:1:yes",
            "SELECT fbo('NaN'::FLOAT)");
        assertAnswer(
            "1|number:1:yes|number:1:yes|number:1:yes|number:1:yes ;; 2|undef|undef|undef|undef ;; "
                + "3|number:0:no|number:0:no|number:1:yes|number:0:no",
            "SELECT id, fbo(b), fbo(s), fbo(n), fbo(f) FROM tx ORDER BY id");
        assertAnswer(
            "REFUSED Language JAVASCRIPT does not support type 'NUMBER(38,0)' for argument or return type.",
            "CREATE OR REPLACE FUNCTION fbi(x BOOLEAN) RETURNS NUMBER LANGUAGE JAVASCRIPT AS $$ return X + X; $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown functions FBI, FBI.",
            "SELECT fbi(TRUE), fbi(FALSE)");
        assertAnswer(
            "Function FBE successfully created.",
            "CREATE OR REPLACE FUNCTION fbe(x BOOLEAN) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (X == "
                + "true) + ':' + (X === true) + ':' + (X == 1) + ':' + (!X) + ':' + JSON.stringify(X) + ':' + "
                + "JSON.stringify([X]); $$");
        assertAnswer(
            "true:false:true:false:1:[1]|false:false:false:true:0:[0]",
            "SELECT fbe(TRUE), fbe(FALSE)");
    }

    @Test
    public void aVariantForABooleanParameter() {
        assertAnswer(
            "Function FBO successfully created.",
            "CREATE OR REPLACE FUNCTION fbo(x BOOLEAN) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return X === "
                + "undefined ? 'undef' : typeof X + ':' + X; $$");
        assertAnswer(
            "number:1",
            "SELECT fbo(PARSE_JSON('true'))");
        assertAnswer(
            "number:0",
            "SELECT fbo(PARSE_JSON('false'))");
        assertAnswer(
            "REFUSED Failed to cast variant value 1 to BOOLEAN",
            "SELECT fbo(PARSE_JSON('1'))");
        assertAnswer(
            "number:1",
            "SELECT fbo(PARSE_JSON('\"yes\"'))");
        assertAnswer(
            "REFUSED Failed to cast variant value \"maybe\" to BOOLEAN",
            "SELECT fbo(PARSE_JSON('\"maybe\"'))");
        assertAnswer(
            "REFUSED Failed to cast variant value 0 to BOOLEAN",
            "SELECT fbo(PARSE_JSON('0.0'))");
    }
}
