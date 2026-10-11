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
 * What a JavaScript procedure's handler receives for the arguments of its CALL: each converted to its
 * parameter's SQL type as for any body - a text into a FLOAT parameter is a number, a number into a VARCHAR
 * parameter a string, and a text that does not convert is the CALL's refusal -, then handed over as JavaScript
 * has it: a BOOLEAN as the number 1 or 0, a SQL NULL as {@code undefined} while a JSON null stays {@code null}, a
 * semi-structured value as an object or an array, a non-array given for an ARRAY parameter wrapped in one, and a
 * BINARY as a {@code Uint8Array}. A NUMBER and a TIME parameter are refused when the procedure is created. Every
 * answer is live-verified; the cells that turn on the session's time zone are asked in
 * {@code JavaScriptProcedureDateArgumentTest}.
 */
public class JavaScriptProcedureArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void eachParameterTypeAsJavaScriptHasIt() {
        assertAnswer(
            "Function JS_BOOLEAN successfully created.",
            "CREATE OR REPLACE PROCEDURE js_boolean(a BOOLEAN) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return "
                + "(typeof A) + ':' + (A === null ? 'null' : A instanceof Date ? 'Date ' + A.toISOString() : "
                + "JSON.stringify(A)); $$");
        assertAnswer(
            "number:1",
            "CALL js_boolean(TRUE)");
        assertAnswer(
            "number:0",
            "CALL js_boolean(FALSE)");
        assertAnswer(
            "number:0",
            "CALL js_boolean('off')");
        assertAnswer(
            "number:1",
            "CALL js_boolean('true')");
        assertAnswer(
            "number:1",
            "CALL js_boolean('yes')");
        assertAnswer(
            "REFUSED Boolean value 'maybe' is not recognized",
            "CALL js_boolean('maybe')");
        assertAnswer(
            "REFUSED Boolean value '' is not recognized",
            "CALL js_boolean('')");
        assertAnswer(
            "number:0",
            "CALL js_boolean(0)");
        assertAnswer(
            "Function JS_FLOAT successfully created.",
            "CREATE OR REPLACE PROCEDURE js_float(a FLOAT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return "
                + "(typeof A) + ':' + (A === null ? 'null' : A instanceof Date ? 'Date ' + A.toISOString() : "
                + "JSON.stringify(A)); $$");
        assertAnswer(
            "number:1",
            "CALL js_float(1)");
        assertAnswer(
            "number:1.5",
            "CALL js_float(1.5)");
        assertAnswer(
            "number:1.5",
            "CALL js_float('1.5')");
        assertAnswer(
            "REFUSED Numeric value 'x' is not recognized",
            "CALL js_float('x')");
        assertAnswer(
            "number:null",
            "CALL js_float('NaN')");
        assertAnswer(
            "number:null",
            "CALL js_float('inf')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'JS_FLOAT': (BOOLEAN)",
            "CALL js_float(TRUE)");
        assertAnswer(
            "undefined:undefined",
            "CALL js_float(NULL)");
        assertAnswer(
            "Function JS_VARCHAR successfully created.",
            "CREATE OR REPLACE PROCEDURE js_varchar(a VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return "
                + "(typeof A) + ':' + (A === null ? 'null' : A instanceof Date ? 'Date ' + A.toISOString() : "
                + "JSON.stringify(A)); $$");
        assertAnswer(
            "string:\"42\"",
            "CALL js_varchar(42)");
        assertAnswer(
            "string:\"1.5\"",
            "CALL js_varchar(1.50)");
        assertAnswer(
            "string:\"true\"",
            "CALL js_varchar(TRUE)");
        assertAnswer(
            "string:\"2024-01-02\"",
            "CALL js_varchar('2024-01-02'::DATE)");
        answer(
            "CALL js_varchar(TO_TIMESTAMP_NTZ('2024-01-02 03:04:05'))");
        assertAnswer(
            "string:\"03:04:05\"",
            "CALL js_varchar(TO_TIME('03:04:05'))");
        assertAnswer(
            "undefined:undefined",
            "CALL js_varchar(NULL)");
        assertAnswer(
            "string:\"{\\\"a\\\":1}\"",
            "CALL js_varchar(PARSE_JSON('{\"a\":1}'))");
        assertAnswer(
            "Function JS_DATE successfully created.",
            "CREATE OR REPLACE PROCEDURE js_date(a DATE) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (typeof "
                + "A) + ':' + (A === null ? 'null' : A instanceof Date ? 'Date ' + A.toISOString() : "
                + "JSON.stringify(A)); $$");
        answer(
            "CALL js_date('2024-01-31')");
        answer(
            "CALL js_date('2024-01-31'::DATE)");
        answer(
            "CALL js_date('2024-01-31 10:11:12'::TIMESTAMP_NTZ)");
        assertAnswer(
            "REFUSED Date 'x' is not recognized",
            "CALL js_date('x')");
        assertAnswer(
            "REFUSED Date '31/01/2024' is not recognized",
            "CALL js_date('31/01/2024')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'JS_DATE': (NUMBER(8,0))",
            "CALL js_date(20240131)");
        answer(
            "CALL js_date('2024-01-31 10:11:12')");
        assertAnswer(
            "undefined:undefined",
            "CALL js_date(NULL)");
        assertAnswer(
            "Function JS_TIMESTAMP_NTZ successfully created.",
            "CREATE OR REPLACE PROCEDURE js_timestamp_ntz(a TIMESTAMP_NTZ) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS "
                + "$$ return (typeof A) + ':' + (A === null ? 'null' : A instanceof Date ? 'Date ' + A.toISOString() "
                + ": JSON.stringify(A)); $$");
        answer(
            "CALL js_timestamp_ntz('2024-01-31 10:11:12')");
        answer(
            "CALL js_timestamp_ntz('2024-01-31')");
        answer(
            "CALL js_timestamp_ntz('2024-01-31'::DATE)");
        assertAnswer(
            "REFUSED Timestamp 'x' is not recognized",
            "CALL js_timestamp_ntz('x')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'JS_TIMESTAMP_NTZ': (NUMBER(10,0))",
            "CALL js_timestamp_ntz(1706695872)");
        answer(
            "CALL js_timestamp_ntz('2024-01-31 10:11:12 +02:00')");
        assertAnswer(
            "Function JS_VARIANT successfully created.",
            "CREATE OR REPLACE PROCEDURE js_variant(a VARIANT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return "
                + "(typeof A) + ':' + (A === null ? 'null' : A instanceof Date ? 'Date ' + A.toISOString() : "
                + "JSON.stringify(A)); $$");
        assertAnswer(
            "number:1",
            "CALL js_variant(1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'JS_VARIANT': (VARCHAR(1))",
            "CALL js_variant('a')");
        assertAnswer(
            "number:1.5",
            "CALL js_variant(1.5)");
        assertAnswer(
            "boolean:true",
            "CALL js_variant(TRUE)");
        assertAnswer(
            "object:{\"a\":1}",
            "CALL js_variant(PARSE_JSON('{\"a\":1}'))");
        assertAnswer(
            "object:[1,2]",
            "CALL js_variant(ARRAY_CONSTRUCT(1, 2))");
        assertAnswer(
            "undefined:undefined",
            "CALL js_variant(NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'JS_VARIANT': (DATE)",
            "CALL js_variant('2024-01-31'::DATE)");
        assertAnswer(
            "Function JS_ARRAY successfully created.",
            "CREATE OR REPLACE PROCEDURE js_array(a ARRAY) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return "
                + "(typeof A) + ':' + (A === null ? 'null' : A instanceof Date ? 'Date ' + A.toISOString() : "
                + "JSON.stringify(A)); $$");
        assertAnswer(
            "object:[1,2]",
            "CALL js_array(ARRAY_CONSTRUCT(1, 2))");
        assertAnswer(
            "object:[1,2]",
            "CALL js_array(PARSE_JSON('[1, 2]'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'JS_ARRAY': (VARCHAR(6))",
            "CALL js_array('[1, 2]')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'JS_ARRAY': (NUMBER(1,0))",
            "CALL js_array(1)");
        assertAnswer(
            "object:[{\"a\":1}]",
            "CALL js_array(PARSE_JSON('{\"a\":1}'))");
        assertAnswer(
            "undefined:undefined",
            "CALL js_array(NULL)");
        assertAnswer(
            "Function JS_OBJECT successfully created.",
            "CREATE OR REPLACE PROCEDURE js_object(a OBJECT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return "
                + "(typeof A) + ':' + (A === null ? 'null' : A instanceof Date ? 'Date ' + A.toISOString() : "
                + "JSON.stringify(A)); $$");
        assertAnswer(
            "object:{\"a\":1}",
            "CALL js_object(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "object:{\"a\":1}",
            "CALL js_object(PARSE_JSON('{\"a\":1}'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'JS_OBJECT': (VARCHAR(1))",
            "CALL js_object('x')");
        assertAnswer(
            "REFUSED Failed to cast variant value [1] to OBJECT",
            "CALL js_object(PARSE_JSON('[1]'))");
        assertAnswer(
            "undefined:undefined",
            "CALL js_object(NULL)");
        assertAnswer(
            "Function JS_BINARY successfully created.",
            "CREATE OR REPLACE PROCEDURE js_binary(a BINARY) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return "
                + "(typeof A) + ':' + (A === null ? 'null' : A instanceof Date ? 'Date ' + A.toISOString() : "
                + "JSON.stringify(A)); $$");
        assertAnswer(
            "object:{\"0\":171,\"1\":205}",
            "CALL js_binary(TO_BINARY('ABCD'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'JS_BINARY': (VARCHAR(4))",
            "CALL js_binary('ABCD')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'JS_BINARY': (VARCHAR(2))",
            "CALL js_binary('zz')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'JS_BINARY': (NUMBER(1,0))",
            "CALL js_binary(1)");
        assertAnswer(
            "REFUSED Language JAVASCRIPT does not support type 'NUMBER(38,0)' for argument or return type.",
            "CREATE OR REPLACE PROCEDURE js_number(a NUMBER) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return "
                + "(typeof A) + ':' + (A === null ? 'null' : A instanceof Date ? 'Date ' + A.toISOString() : "
                + "JSON.stringify(A)); $$");
        assertAnswer(
            "REFUSED Language JAVASCRIPT does not support type 'TIME(9)' for argument or return type.",
            "CREATE OR REPLACE PROCEDURE js_time(a TIME) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (typeof "
                + "A) + ':' + (A === null ? 'null' : A instanceof Date ? 'Date ' + A.toISOString() : "
                + "JSON.stringify(A)); $$");
    }

    @Test
    public void booleansNullsAndContainers() {
        assertAnswer(
            "Function JB successfully created.",
            "CREATE OR REPLACE PROCEDURE jb(a BOOLEAN) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return String(A) "
                + "+ '|' + (typeof A) + '|' + (A === true) + '|' + (A === 1) + '|' + (A === undefined) + '|' + (A "
                + "=== null) + '|' + (A ? 'truthy' : 'falsy') + '|' + arguments.length; $$");
        assertAnswer(
            "1|number|false|true|false|false|truthy|1",
            "CALL jb(TRUE)");
        assertAnswer(
            "0|number|false|false|false|false|falsy|1",
            "CALL jb(FALSE)");
        assertAnswer(
            "0|number|false|false|false|false|falsy|1",
            "CALL jb('off')");
        assertAnswer(
            "1|number|false|true|false|false|truthy|1",
            "CALL jb(1)");
        assertAnswer(
            "undefined|undefined|false|false|true|false|falsy|1",
            "CALL jb(NULL)");
        assertAnswer(
            "Function JF successfully created.",
            "CREATE OR REPLACE PROCEDURE jf(a FLOAT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return String(A) + "
                + "'|' + (typeof A) + '|' + (A === true) + '|' + (A === 1) + '|' + (A === undefined) + '|' + (A === "
                + "null) + '|' + (A ? 'truthy' : 'falsy') + '|' + arguments.length; $$");
        assertAnswer(
            "1.5|number|false|false|false|false|truthy|1",
            "CALL jf(1.5)");
        assertAnswer(
            "1.5|number|false|false|false|false|truthy|1",
            "CALL jf('1.5')");
        assertAnswer(
            "undefined|undefined|false|false|true|false|falsy|1",
            "CALL jf(NULL)");
        assertAnswer(
            "1|number|false|true|false|false|truthy|1",
            "CALL jf(1)");
        assertAnswer(
            "Function JV successfully created.",
            "CREATE OR REPLACE PROCEDURE jv(a VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return String(A) "
                + "+ '|' + (typeof A) + '|' + (A === true) + '|' + (A === 1) + '|' + (A === undefined) + '|' + (A "
                + "=== null) + '|' + (A ? 'truthy' : 'falsy') + '|' + arguments.length; $$");
        assertAnswer(
            "a|string|false|false|false|false|truthy|1",
            "CALL jv('a')");
        assertAnswer(
            "42|string|false|false|false|false|truthy|1",
            "CALL jv(42)");
        assertAnswer(
            "undefined|undefined|false|false|true|false|falsy|1",
            "CALL jv(NULL)");
        assertAnswer(
            "true|string|false|false|false|false|truthy|1",
            "CALL jv(TRUE)");
        assertAnswer(
            "Function JD successfully created.",
            "CREATE OR REPLACE PROCEDURE jd(a DATE) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (A "
                + "instanceof Date) + '|' + (A === undefined ? 'undefined' : A.getTime()); $$");
        answer(
            "CALL jd('2024-01-31')");
        answer(
            "CALL jd('2024-01-31'::DATE)");
        assertAnswer(
            "false|undefined",
            "CALL jd(NULL)");
        assertAnswer(
            "Function JT successfully created.",
            "CREATE OR REPLACE PROCEDURE jt(a TIMESTAMP_NTZ) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (A "
                + "instanceof Date) + '|' + (A === undefined ? 'undefined' : A.getTime()) + '|' + (A === undefined ? "
                + "'' : A.toISOString()); $$");
        answer(
            "CALL jt('2024-01-31 10:11:12')");
        answer(
            "CALL jt('2024-01-31 10:11:12.123456789')");
        answer(
            "CALL jt('2024-01-31'::DATE)");
        assertAnswer(
            "Function JZ successfully created.",
            "CREATE OR REPLACE PROCEDURE jz(a TIMESTAMP_LTZ) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (A "
                + "instanceof Date) + '|' + A.getTime(); $$");
        assertAnswer(
            "true|1706695872000",
            "CALL jz('2024-01-31 10:11:12 +00:00')");
        assertAnswer(
            "Function JBIN successfully created.",
            "CREATE OR REPLACE PROCEDURE jbin(a BINARY) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return "
                + "Object.prototype.toString.call(A) + '|' + A.length + '|' + A[0] + '|' + A[1]; $$");
        assertAnswer(
            "[object Uint8Array]|2|171|205",
            "CALL jbin(TO_BINARY('ABCD'))");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught TypeError: Cannot read properties of undefined (reading "
                + "'length') in JBIN at ' return Object.prototype.toString.call(A) + '|' + A.length + '|' + A[0] + "
                + "'|' + A[1]; ' position 52 | stackstrace:  | JBIN line: 1",
            "CALL jbin(NULL)");
        assertAnswer(
            "Function JVAR successfully created.",
            "CREATE OR REPLACE PROCEDURE jvar(a VARIANT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return "
                + "String(A) + '|' + (typeof A) + '|' + (A === true) + '|' + (A === 1) + '|' + (A === undefined) + "
                + "'|' + (A === null) + '|' + (A ? 'truthy' : 'falsy') + '|' + arguments.length; $$");
        assertAnswer(
            "undefined|undefined|false|false|true|false|falsy|1",
            "CALL jvar(NULL)");
        assertAnswer(
            "null|object|false|false|false|true|falsy|1",
            "CALL jvar(PARSE_JSON('null'))");
        assertAnswer(
            "1|number|false|true|false|false|truthy|1",
            "CALL jvar(1)");
        assertAnswer(
            "[object Object]|object|false|false|false|false|truthy|1",
            "CALL jvar(PARSE_JSON('{\"a\":null}'))");
        assertAnswer(
            "Function JARR successfully created.",
            "CREATE OR REPLACE PROCEDURE jarr(a ARRAY) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return "
                + "JSON.stringify(A) + '|' + Array.isArray(A) + '|' + A.length; $$");
        assertAnswer(
            "[1,null,3]|true|3",
            "CALL jarr(ARRAY_CONSTRUCT(1, NULL, 3))");
        assertAnswer(
            "[1]|true|1",
            "CALL jarr(PARSE_JSON('1'))");
        assertAnswer(
            "[\"a\"]|true|1",
            "CALL jarr(PARSE_JSON('\"a\"'))");
    }
}
