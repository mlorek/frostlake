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
 * What a JavaScript handler receives for a VARIANT, an OBJECT and an ARRAY parameter (live-verified): a number, a text,
 * a boolean, a Uint8Array for a binary, an object or an array for JSON, null for a JSON null and undefined for a SQL
 * NULL, a TIME refused; a DATE or a text the parameter does not take is refused at the call. JavaScript functions
 * overload by the rules SQL functions do, beside a SQL overload too.
 */
public class JavaScriptSemiStructuredParameterTest extends MeasuredCellsTestSupport {

    /** A JavaScript procedure and function over each kind of VARIANT argument, and OBJECT and ARRAY ones. */
    @Test
    public void aProcedureAndAFunctionReceiveTheArgumentsJson() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"CREATE OR REPLACE PROCEDURE pvj(v VARIANT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return typeof V + "
                + "'|' + Object.prototype.toString.call(V) + '|' + JSON.stringify(V); $$",
                "Function PVJ successfully created."},
            {"CREATE OR REPLACE FUNCTION fvj(v VARIANT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return typeof V + "
                + "'|' + Object.prototype.toString.call(V) + '|' + JSON.stringify(V); $$",
                "Function FVJ successfully created."},
            {"CREATE OR REPLACE PROCEDURE pvjo(o OBJECT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return typeof O + "
                + "'|' + Object.prototype.toString.call(O) + '|' + JSON.stringify(O); $$",
                "Function PVJO successfully created."},
            {"CREATE OR REPLACE PROCEDURE pvja(a ARRAY) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return typeof A + "
                + "'|' + Object.prototype.toString.call(A) + '|' + JSON.stringify(A); $$",
                "Function PVJA successfully created."},
            {"CALL pvj(TO_VARIANT('10:00:00'::TIME))",
                "REFUSED JavaScript execution error: TIME inside VARIANT value - not supported in function PVJ with "
                + "handler PVJ"},
            {"CALL pvj(TO_VARIANT(1.5::FLOAT))",
                "number|[object Number]|1.5"},
            {"CALL pvj(TO_VARIANT(1.50))",
                "number|[object Number]|1.5"},
            {"CALL pvj(TO_VARIANT(123456789012345678))",
                "number|[object Number]|123456789012345680"},
            {"CALL pvj(TO_VARIANT(X'AB'))",
                "object|[object Uint8Array]|{\"0\":171}"},
            {"CALL pvj(TO_VARIANT(TRUE))",
                "boolean|[object Boolean]|true"},
            {"CALL pvj(TO_VARIANT('x'))",
                "string|[object String]|\"x\""},
            {"CALL pvj(PARSE_JSON('{\"a\": 1, \"d\": \"2024-01-02\"}'))",
                "object|[object Object]|{\"a\":1,\"d\":\"2024-01-02\"}"},
            {"CALL pvj(PARSE_JSON('null'))",
                "object|[object Null]|null"},
            {"CALL pvj(NULL)",
                "undefined|[object Undefined]|undefined"},
            {"CALL pvj('2024-01-02'::DATE)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PVJ': (DATE)"},
            {"CALL pvj(5)",
                "number|[object Number]|5"},
            {"CALL pvj('x')",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PVJ': (VARCHAR(1))"},
            {"SELECT fvj(TO_VARIANT('10:00:00'::TIME))",
                "REFUSED JavaScript execution error: TIME inside VARIANT value - not supported in function FVJ with "
                + "handler FVJ"},
            {"SELECT fvj(TO_VARIANT(1.5::FLOAT))",
                "number|[object Number]|1.5"},
            {"SELECT fvj(TO_VARIANT(1.50))",
                "number|[object Number]|1.5"},
            {"SELECT fvj(TO_VARIANT(123456789012345678))",
                "number|[object Number]|123456789012345680"},
            {"SELECT fvj(TO_VARIANT(X'AB'))",
                "object|[object Uint8Array]|{\"0\":171}"},
            {"SELECT fvj(TO_VARIANT(TRUE))",
                "boolean|[object Boolean]|true"},
            {"SELECT fvj(TO_VARIANT('x'))",
                "string|[object String]|\"x\""},
            {"SELECT fvj(PARSE_JSON('{\"a\": 1, \"d\": \"2024-01-02\"}'))",
                "object|[object Object]|{\"a\":1,\"d\":\"2024-01-02\"}"},
            {"SELECT fvj(PARSE_JSON('null'))",
                "object|[object Null]|null"},
            {"SELECT fvj(NULL)",
                "undefined|[object Undefined]|undefined"},
            {"SELECT fvj('2024-01-02'::DATE)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'FVJ': (DATE)"},
            {"SELECT fvj(5)",
                "number|[object Number]|5"},
            {"SELECT fvj('x')",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'FVJ': (VARCHAR(1))"},
            {"CALL pvjo(PARSE_JSON('{\"t\": \"10:00:00\"}'))",
                "object|[object Object]|{\"t\":\"10:00:00\"}"},
            {"CALL pvja(ARRAY_CONSTRUCT())",
                "object|[object Array]|[]"},
        });
    }

    /** JavaScript overloads alone and beside a SQL overload. */
    @Test
    public void javaScriptOverloadsChooseAsSqlOnesDo() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION ovj(a NUMBER) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS 'return \"N\";'",
                "REFUSED Language JAVASCRIPT does not support type 'NUMBER(38,0)' for argument or return type."},
            {"CREATE OR REPLACE FUNCTION ovj(a VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS 'return \"V\";'",
                "Function OVJ successfully created."},
            {"SELECT ovj(1), ovj('1'), ovj(1.5::FLOAT), ovj(TRUE), ovj(NULL), ovj('2024-01-02'::DATE)",
                "V|V|V|V|V|V"},
            {"CREATE OR REPLACE FUNCTION ovm(a NUMBER) RETURNS VARCHAR AS $$ 'SQL-N' $$",
                "Function OVM successfully created."},
            {"CREATE OR REPLACE FUNCTION ovm(a VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS 'return \"JS-V\";'",
                "Function OVM successfully created."},
            {"SELECT ovm(1), ovm('1'), ovm(1.5::FLOAT), ovm(TRUE), ovm(NULL)",
                "SQL-N|JS-V|JS-V|JS-V|SQL-N"},
        });
    }
}
