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
 * What a Python handler receives for a VARIANT parameter (live-verified): a str for a text, a DATE, a TIME and a
 * binary's hex, an int or a float for a number, a bool, a dict or a list for JSON, None for a JSON null and a
 * sqlNullWrapper for a SQL NULL; a DATE or a text the parameter does not take is refused at the call. Python functions
 * overload by the rules SQL functions do.
 */
public class PythonSemiStructuredParameterTest extends MeasuredCellsTestSupport {

    /** A Python procedure and function over each kind of VARIANT argument. */
    @Test
    public void aProcedureAndAFunctionReceiveTheArgumentsJson() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"CREATE OR REPLACE PROCEDURE pvp(v VARIANT) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session, v):\n    return "
                + "type(v).__name__ + '|' + repr(v)\n$$",
                "Function PVP successfully created."},
            {"CREATE OR REPLACE FUNCTION fvp(v VARIANT) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "HANDLER = 'run' AS $$\ndef run(v):\n    return type(v).__name__ + '|' + repr(v)\n$$",
                "Function FVP successfully created."},
            {"CALL pvp(TO_VARIANT('2024-01-02'::DATE))",
                "str|'2024-01-02'"},
            {"CALL pvp(TO_VARIANT('10:00:00'::TIME))",
                "str|'10:00:00'"},
            {"CALL pvp(TO_VARIANT(1.5::FLOAT))",
                "float|1.5"},
            {"CALL pvp(TO_VARIANT(1.50))",
                "float|1.5"},
            {"CALL pvp(TO_VARIANT(123456789012345678))",
                "int|123456789012345678"},
            {"CALL pvp(TO_VARIANT(X'AB'))",
                "str|'AB'"},
            {"CALL pvp(TO_VARIANT(TRUE))",
                "bool|True"},
            {"CALL pvp(TO_VARIANT('x'))",
                "str|'x'"},
            {"CALL pvp(PARSE_JSON('{\"a\": 1, \"d\": \"2024-01-02\"}'))",
                "dict|{'a': 1, 'd': '2024-01-02'}"},
            {"CALL pvp(ARRAY_CONSTRUCT(1, '2024-01-02'::DATE))",
                "list|[1, '2024-01-02']"},
            {"CALL pvp(OBJECT_CONSTRUCT('d', '2024-01-02'::DATE, 'b', X'AB'))",
                "dict|{'b': 'AB', 'd': '2024-01-02'}"},
            {"CALL pvp(PARSE_JSON('null'))",
                "NoneType|None"},
            {"CALL pvp('2024-01-02'::DATE)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PVP': (DATE)"},
            {"CALL pvp(5)",
                "int|5"},
            {"CALL pvp('x')",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PVP': (VARCHAR(1))"},
            {"SELECT fvp(TO_VARIANT('2024-01-02'::DATE))",
                "str|'2024-01-02'"},
            {"SELECT fvp(TO_VARIANT('10:00:00'::TIME))",
                "str|'10:00:00'"},
            {"SELECT fvp(TO_VARIANT(1.5::FLOAT))",
                "float|1.5"},
            {"SELECT fvp(TO_VARIANT(1.50))",
                "float|1.5"},
            {"SELECT fvp(TO_VARIANT(123456789012345678))",
                "int|123456789012345678"},
            {"SELECT fvp(TO_VARIANT(X'AB'))",
                "str|'AB'"},
            {"SELECT fvp(TO_VARIANT(TRUE))",
                "bool|True"},
            {"SELECT fvp(TO_VARIANT('x'))",
                "str|'x'"},
            {"SELECT fvp(PARSE_JSON('{\"a\": 1, \"d\": \"2024-01-02\"}'))",
                "dict|{'a': 1, 'd': '2024-01-02'}"},
            {"SELECT fvp(ARRAY_CONSTRUCT(1, '2024-01-02'::DATE))",
                "list|[1, '2024-01-02']"},
            {"SELECT fvp(OBJECT_CONSTRUCT('d', '2024-01-02'::DATE, 'b', X'AB'))",
                "dict|{'b': 'AB', 'd': '2024-01-02'}"},
            {"SELECT fvp(PARSE_JSON('null'))",
                "NoneType|None"},
            {"SELECT fvp('2024-01-02'::DATE)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'FVP': (DATE)"},
            {"SELECT fvp(5)",
                "int|5"},
            {"SELECT fvp('x')",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'FVP': (VARCHAR(1))"},
        });
    }

    /** Python overloads of a FLOAT and a BOOLEAN parameter. */
    @Test
    public void pythonOverloadsChooseAsSqlOnesDo() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION ovp(a FLOAT) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.11' HANDLER "
                + "= 'f' AS $$\ndef f(a):\n    return 'F'\n$$",
                "Function OVP successfully created."},
            {"CREATE OR REPLACE FUNCTION ovp(a BOOLEAN) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "HANDLER = 'f' AS $$\ndef f(a):\n    return 'B'\n$$",
                "Function OVP successfully created."},
            {"SELECT ovp(1), ovp('1'), ovp(1.5::FLOAT), ovp(TRUE), ovp(NULL), ovp('true')",
                "F|B|F|B|B|B"},
        });
    }
}
