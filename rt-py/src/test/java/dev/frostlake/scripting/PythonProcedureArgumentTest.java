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
 * What a Python procedure's handler receives for the arguments of its CALL: each converted to its parameter's
 * SQL type as for any body, then handed over as Python has it - a BOOLEAN a {@code bool}, a whole NUMBER an
 * {@code int} of any width, a NUMBER with a scale a {@code Decimal} at that scale, a FLOAT a {@code float}, a
 * text a {@code str}, a DATE a {@code datetime.date}, a TIME a {@code datetime.time}, a TIMESTAMP a
 * {@code datetime.datetime} to the microsecond, a semi-structured value a dict, a list or the scalar it holds -
 * an array's SQL NULL element a {@code None} - and a BINARY {@code bytes}. A text that does not convert is the
 * CALL's refusal, before the handler runs. Every answer is live-verified.
 */
public class PythonProcedureArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void eachParameterTypeAsPythonHasIt() {
        assertAnswer(
            "Function PY_BOOLEAN successfully created.",
            "CREATE OR REPLACE PROCEDURE py_boolean(a BOOLEAN) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session, a):\n    "
                + "return type(a).__name__ + ':' + repr(a)\n$$");
        assertAnswer(
            "bool:True",
            "CALL py_boolean(TRUE)");
        assertAnswer(
            "bool:False",
            "CALL py_boolean(FALSE)");
        assertAnswer(
            "bool:False",
            "CALL py_boolean('off')");
        assertAnswer(
            "bool:True",
            "CALL py_boolean('true')");
        assertAnswer(
            "bool:True",
            "CALL py_boolean('yes')");
        assertAnswer(
            "REFUSED Boolean value 'maybe' is not recognized",
            "CALL py_boolean('maybe')");
        assertAnswer(
            "REFUSED Boolean value '' is not recognized",
            "CALL py_boolean('')");
        assertAnswer(
            "bool:False",
            "CALL py_boolean(0)");
        assertAnswer(
            "Function PY_NUMBER successfully created.",
            "CREATE OR REPLACE PROCEDURE py_number(a NUMBER) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session, a):\n    "
                + "return type(a).__name__ + ':' + repr(a)\n$$");
        assertAnswer(
            "int:7",
            "CALL py_number(7)");
        assertAnswer(
            "int:7",
            "CALL py_number('7')");
        assertAnswer(
            "int:3",
            "CALL py_number(2.5)");
        assertAnswer(
            "int:3",
            "CALL py_number('2.5')");
        assertAnswer(
            "int:2",
            "CALL py_number(2.4)");
        assertAnswer(
            "int:-3",
            "CALL py_number(-2.5)");
        assertAnswer(
            "REFUSED Numeric value 'x' is not recognized",
            "CALL py_number('x')");
        assertAnswer(
            "REFUSED Numeric value '' is not recognized",
            "CALL py_number('')");
        assertAnswer(
            "Function PY_NUMBER_10_2 successfully created.",
            "CREATE OR REPLACE PROCEDURE py_number_10_2(a NUMBER(10,2)) RETURNS VARCHAR LANGUAGE PYTHON "
                + "RUNTIME_VERSION = '3.11' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef "
                + "run(session, a):\n    return type(a).__name__ + ':' + repr(a)\n$$");
        assertAnswer(
            "Decimal:Decimal('1.25')",
            "CALL py_number_10_2(1.25)");
        assertAnswer(
            "Decimal:Decimal('1.26')",
            "CALL py_number_10_2(1.255)");
        assertAnswer(
            "Decimal:Decimal('1.25')",
            "CALL py_number_10_2(1.245)");
        assertAnswer(
            "Decimal:Decimal('1.26')",
            "CALL py_number_10_2('1.255')");
        assertAnswer(
            "REFUSED Number out of representable range: type FIXED[SB8](10,2){not null}, value 123456789.12",
            "CALL py_number_10_2(123456789.12)");
        assertAnswer(
            "REFUSED Numeric value 'x' is not recognized",
            "CALL py_number_10_2('x')");
        assertAnswer(
            "Decimal:Decimal('7.00')",
            "CALL py_number_10_2(7)");
        assertAnswer(
            "Function PY_FLOAT successfully created.",
            "CREATE OR REPLACE PROCEDURE py_float(a FLOAT) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session, a):\n    "
                + "return type(a).__name__ + ':' + repr(a)\n$$");
        assertAnswer(
            "float:1.0",
            "CALL py_float(1)");
        assertAnswer(
            "float:1.5",
            "CALL py_float(1.5)");
        assertAnswer(
            "float:1.5",
            "CALL py_float('1.5')");
        assertAnswer(
            "REFUSED Numeric value 'x' is not recognized",
            "CALL py_float('x')");
        assertAnswer(
            "float:nan",
            "CALL py_float('NaN')");
        assertAnswer(
            "float:inf",
            "CALL py_float('inf')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PY_FLOAT': (BOOLEAN)",
            "CALL py_float(TRUE)");
        assertAnswer(
            "NoneType:None",
            "CALL py_float(NULL)");
        assertAnswer(
            "Function PY_VARCHAR successfully created.",
            "CREATE OR REPLACE PROCEDURE py_varchar(a VARCHAR) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session, a):\n    "
                + "return type(a).__name__ + ':' + repr(a)\n$$");
        assertAnswer(
            "str:'42'",
            "CALL py_varchar(42)");
        assertAnswer(
            "str:'1.5'",
            "CALL py_varchar(1.50)");
        assertAnswer(
            "str:'true'",
            "CALL py_varchar(TRUE)");
        assertAnswer(
            "str:'2024-01-02'",
            "CALL py_varchar('2024-01-02'::DATE)");
        answer(
            "CALL py_varchar(TO_TIMESTAMP_NTZ('2024-01-02 03:04:05'))");
        assertAnswer(
            "str:'03:04:05'",
            "CALL py_varchar(TO_TIME('03:04:05'))");
        assertAnswer(
            "NoneType:None",
            "CALL py_varchar(NULL)");
        assertAnswer(
            "str:'{\"a\":1}'",
            "CALL py_varchar(PARSE_JSON('{\"a\":1}'))");
        assertAnswer(
            "Function PY_DATE successfully created.",
            "CREATE OR REPLACE PROCEDURE py_date(a DATE) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session, a):\n    return "
                + "type(a).__name__ + ':' + repr(a)\n$$");
        assertAnswer(
            "date:datetime.date(2024, 1, 31)",
            "CALL py_date('2024-01-31')");
        assertAnswer(
            "date:datetime.date(2024, 1, 31)",
            "CALL py_date('2024-01-31'::DATE)");
        assertAnswer(
            "date:datetime.date(2024, 1, 31)",
            "CALL py_date('2024-01-31 10:11:12'::TIMESTAMP_NTZ)");
        assertAnswer(
            "REFUSED Date 'x' is not recognized",
            "CALL py_date('x')");
        assertAnswer(
            "REFUSED Date '31/01/2024' is not recognized",
            "CALL py_date('31/01/2024')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PY_DATE': (NUMBER(8,0))",
            "CALL py_date(20240131)");
        assertAnswer(
            "date:datetime.date(2024, 1, 31)",
            "CALL py_date('2024-01-31 10:11:12')");
        assertAnswer(
            "NoneType:None",
            "CALL py_date(NULL)");
        assertAnswer(
            "Function PY_TIME successfully created.",
            "CREATE OR REPLACE PROCEDURE py_time(a TIME) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session, a):\n    return "
                + "type(a).__name__ + ':' + repr(a)\n$$");
        assertAnswer(
            "time:datetime.time(10, 11, 12)",
            "CALL py_time('10:11:12')");
        assertAnswer(
            "time:datetime.time(10, 11)",
            "CALL py_time('10:11')");
        assertAnswer(
            "REFUSED Time 'x' is not recognized",
            "CALL py_time('x')");
        assertAnswer(
            "time:datetime.time(10, 11, 12)",
            "CALL py_time('2024-01-31 10:11:12'::TIMESTAMP_NTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PY_TIME': (NUMBER(5,0))",
            "CALL py_time(36672)");
        assertAnswer(
            "Function PY_TIMESTAMP_NTZ successfully created.",
            "CREATE OR REPLACE PROCEDURE py_timestamp_ntz(a TIMESTAMP_NTZ) RETURNS VARCHAR LANGUAGE PYTHON "
                + "RUNTIME_VERSION = '3.11' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef "
                + "run(session, a):\n    return type(a).__name__ + ':' + repr(a)\n$$");
        assertAnswer(
            "datetime:datetime.datetime(2024, 1, 31, 10, 11, 12)",
            "CALL py_timestamp_ntz('2024-01-31 10:11:12')");
        assertAnswer(
            "datetime:datetime.datetime(2024, 1, 31, 0, 0)",
            "CALL py_timestamp_ntz('2024-01-31')");
        assertAnswer(
            "datetime:datetime.datetime(2024, 1, 31, 0, 0)",
            "CALL py_timestamp_ntz('2024-01-31'::DATE)");
        assertAnswer(
            "REFUSED Timestamp 'x' is not recognized",
            "CALL py_timestamp_ntz('x')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PY_TIMESTAMP_NTZ': (NUMBER(10,0))",
            "CALL py_timestamp_ntz(1706695872)");
        assertAnswer(
            "datetime:datetime.datetime(2024, 1, 31, 10, 11, 12)",
            "CALL py_timestamp_ntz('2024-01-31 10:11:12 +02:00')");
        assertAnswer(
            "Function PY_VARIANT successfully created.",
            "CREATE OR REPLACE PROCEDURE py_variant(a VARIANT) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session, a):\n    "
                + "return type(a).__name__ + ':' + repr(a)\n$$");
        assertAnswer(
            "int:1",
            "CALL py_variant(1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PY_VARIANT': (VARCHAR(1))",
            "CALL py_variant('a')");
        assertAnswer(
            "float:1.5",
            "CALL py_variant(1.5)");
        assertAnswer(
            "bool:True",
            "CALL py_variant(TRUE)");
        assertAnswer(
            "dict:{'a': 1}",
            "CALL py_variant(PARSE_JSON('{\"a\":1}'))");
        assertAnswer(
            "list:[1, 2]",
            "CALL py_variant(ARRAY_CONSTRUCT(1, 2))");
        answer(
            "CALL py_variant(NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PY_VARIANT': (DATE)",
            "CALL py_variant('2024-01-31'::DATE)");
        assertAnswer(
            "Function PY_ARRAY successfully created.",
            "CREATE OR REPLACE PROCEDURE py_array(a ARRAY) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session, a):\n    "
                + "return type(a).__name__ + ':' + repr(a)\n$$");
        assertAnswer(
            "list:[1, 2]",
            "CALL py_array(ARRAY_CONSTRUCT(1, 2))");
        assertAnswer(
            "list:[1, 2]",
            "CALL py_array(PARSE_JSON('[1, 2]'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PY_ARRAY': (VARCHAR(6))",
            "CALL py_array('[1, 2]')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PY_ARRAY': (NUMBER(1,0))",
            "CALL py_array(1)");
        assertAnswer(
            "list:[{'a': 1}]",
            "CALL py_array(PARSE_JSON('{\"a\":1}'))");
        assertAnswer(
            "NoneType:None",
            "CALL py_array(NULL)");
        assertAnswer(
            "Function PY_OBJECT successfully created.",
            "CREATE OR REPLACE PROCEDURE py_object(a OBJECT) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session, a):\n    "
                + "return type(a).__name__ + ':' + repr(a)\n$$");
        assertAnswer(
            "dict:{'a': 1}",
            "CALL py_object(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "dict:{'a': 1}",
            "CALL py_object(PARSE_JSON('{\"a\":1}'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PY_OBJECT': (VARCHAR(1))",
            "CALL py_object('x')");
        assertAnswer(
            "REFUSED Failed to cast variant value [1] to OBJECT",
            "CALL py_object(PARSE_JSON('[1]'))");
        assertAnswer(
            "NoneType:None",
            "CALL py_object(NULL)");
        assertAnswer(
            "Function PY_BINARY successfully created.",
            "CREATE OR REPLACE PROCEDURE py_binary(a BINARY) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session, a):\n    "
                + "return type(a).__name__ + ':' + repr(a)\n$$");
        assertAnswer(
            "bytes:b'\\xab\\xcd'",
            "CALL py_binary(TO_BINARY('ABCD'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PY_BINARY': (VARCHAR(4))",
            "CALL py_binary('ABCD')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PY_BINARY': (VARCHAR(2))",
            "CALL py_binary('zz')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PY_BINARY': (NUMBER(1,0))",
            "CALL py_binary(1)");
    }

    @Test
    public void nullsWideNumbersAndContainers() {
        assertAnswer(
            "Function PN successfully created.",
            "CREATE OR REPLACE PROCEDURE pn(a NUMBER) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session, a):\n    return "
                + "type(a).__name__ + ':' + repr(a)\n$$");
        assertAnswer(
            "NoneType:None",
            "CALL pn(NULL)");
        assertAnswer(
            "int:12345678901234567890123456789012345678",
            "CALL pn(12345678901234567890123456789012345678)");
        assertAnswer(
            "int:3",
            "CALL pn(2.5::FLOAT)");
        assertAnswer(
            "Function PD successfully created.",
            "CREATE OR REPLACE PROCEDURE pd(a NUMBER(38,10)) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session, a):\n    "
                + "return type(a).__name__ + ':' + repr(a)\n$$");
        assertAnswer(
            "Decimal:Decimal('1.5000000000')",
            "CALL pd(1.5)");
        assertAnswer(
            "NoneType:None",
            "CALL pd(NULL)");
        assertAnswer(
            "Function PB successfully created.",
            "CREATE OR REPLACE PROCEDURE pb(a BOOLEAN) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session, a):\n    return "
                + "type(a).__name__ + ':' + repr(a)\n$$");
        assertAnswer(
            "NoneType:None",
            "CALL pb(NULL)");
        assertAnswer(
            "bool:True",
            "CALL pb(2.5)");
        assertAnswer(
            "Function PV successfully created.",
            "CREATE OR REPLACE PROCEDURE pv(a VARIANT) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session, a):\n    return "
                + "type(a).__name__ + ':' + repr(a)\n$$");
        assertAnswer(
            "NoneType:None",
            "CALL pv(PARSE_JSON('null'))");
        assertAnswer(
            "str:'a'",
            "CALL pv(PARSE_JSON('\"a\"'))");
        assertAnswer(
            "list:[1, None]",
            "CALL pv(PARSE_JSON('[1, null]'))");
        assertAnswer(
            "dict:{'a': {'b': [1.5, True]}}",
            "CALL pv(PARSE_JSON('{\"a\": {\"b\": [1.5, true]}}'))");
        assertAnswer(
            "Function PA successfully created.",
            "CREATE OR REPLACE PROCEDURE pa(a ARRAY) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session, a):\n    return "
                + "type(a).__name__ + ':' + repr(a)\n$$");
        assertAnswer(
            "list:[1, None, 'x']",
            "CALL pa(ARRAY_CONSTRUCT(1, NULL, 'x'))");
        assertAnswer(
            "list:[1]",
            "CALL pa(PARSE_JSON('1'))");
        assertAnswer(
            "Function PT successfully created.",
            "CREATE OR REPLACE PROCEDURE pt(a TIMESTAMP_NTZ) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session, a):\n    "
                + "return type(a).__name__ + ':' + repr(a)\n$$");
        assertAnswer(
            "datetime:datetime.datetime(2024, 1, 31, 10, 11, 12, 123456)",
            "CALL pt('2024-01-31 10:11:12.123456789')");
        assertAnswer(
            "Function PZ successfully created.",
            "CREATE OR REPLACE PROCEDURE pz(a TIMESTAMP_LTZ) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session, a):\n    "
                + "return type(a).__name__ + ':' + repr(a)\n$$");
        answer(
            "CALL pz('2024-01-31 10:11:12 +00:00')");
        assertAnswer(
            "Function PW successfully created.",
            "CREATE OR REPLACE PROCEDURE pw(a TIMESTAMP_TZ) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session, a):\n    "
                + "return type(a).__name__ + ':' + repr(a)\n$$");
        assertAnswer(
            "datetime:datetime.datetime(2024, 1, 31, 10, 11, 12, "
                + "tzinfo=datetime.timezone(datetime.timedelta(seconds=7200)))",
            "CALL pw('2024-01-31 10:11:12 +02:00')");
        assertAnswer(
            "Function PF successfully created.",
            "CREATE OR REPLACE PROCEDURE pf(a FLOAT) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session, a):\n    return "
                + "type(a).__name__ + ':' + repr(a)\n$$");
        assertAnswer(
            "float:nan",
            "CALL pf('NaN')");
        assertAnswer(
            "float:0.333333",
            "CALL pf(1/3)");
    }
}
