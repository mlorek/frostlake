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

import dev.frostlake.BaseDatabaseTest;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A user-defined function's arguments are judged against its parameter types while the query compiles,
 * before any row is read: a pair no implicit conversion carries is "Invalid argument types for function
 * 'F': (...)" at the call, every argument's type listed, and a TIME into a TIMESTAMP parameter is
 * "incompatible types". With overloads the call is refused only when every overload of that arity refuses.
 * Every cell is live-verified.
 */
public class UdfArgumentTypeTest extends BaseDatabaseTest {

    /** Parameter function, argument, and the account's refusal, or Y where the call compiles. */
    private static final String[][] MATRIX = {
        {"varchar", "'abc'", "Y"},
        {"varchar", "1", "Y"},
        {"varchar", "1.5", "Y"},
        {"varchar", "1.5::FLOAT", "Y"},
        {"varchar", "TRUE", "Y"},
        {"varchar", "'2024-01-01'::DATE", "Y"},
        {"varchar", "'2024-01-01 10:00:00'::TIMESTAMP_NTZ", "Y"},
        {"varchar", "'2024-01-01 10:00:00'::TIMESTAMP_LTZ", "Y"},
        {"varchar", "'2024-01-01 10:00:00 +0200'::TIMESTAMP_TZ", "Y"},
        {"varchar", "'10:00:00'::TIME", "Y"},
        {"varchar", "PARSE_JSON('1')", "Y"},
        {"varchar", "[0]", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_VARCHAR': (ARRAY)"},
        {"varchar", "{'a': 1}", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_VARCHAR': (OBJECT)"},
        {"varchar", "TO_BINARY('AB', 'HEX')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_VARCHAR': (BINARY(67108864))"},
        {"varchar", "[1,2,3]::VECTOR(INT, 3)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_VARCHAR': (VECTOR(INT, 3))"},
        {"varchar", "NULL", "Y"},
        {"number", "'abc'", "Y"},
        {"number", "1", "Y"},
        {"number", "1.5", "Y"},
        {"number", "1.5::FLOAT", "Y"},
        {"number", "TRUE", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_NUMBER': (BOOLEAN)"},
        {"number", "'2024-01-01'::DATE", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_NUMBER': (DATE)"},
        {"number", "'2024-01-01 10:00:00'::TIMESTAMP_NTZ", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_NUMBER': (TIMESTAMP_NTZ(9))"},
        {"number", "'2024-01-01 10:00:00'::TIMESTAMP_LTZ", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_NUMBER': (TIMESTAMP_LTZ(9))"},
        {"number", "'2024-01-01 10:00:00 +0200'::TIMESTAMP_TZ", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_NUMBER': (TIMESTAMP_TZ(9))"},
        {"number", "'10:00:00'::TIME", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_NUMBER': (TIME(9))"},
        {"number", "PARSE_JSON('1')", "Y"},
        {"number", "[0]", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_NUMBER': (ARRAY)"},
        {"number", "{'a': 1}", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_NUMBER': (OBJECT)"},
        {"number", "TO_BINARY('AB', 'HEX')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_NUMBER': (BINARY(67108864))"},
        {"number", "[1,2,3]::VECTOR(INT, 3)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_NUMBER': (VECTOR(INT, 3))"},
        {"number", "NULL", "Y"},
        {"float", "'abc'", "Y"},
        {"float", "1", "Y"},
        {"float", "1.5", "Y"},
        {"float", "1.5::FLOAT", "Y"},
        {"float", "TRUE", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_FLOAT': (BOOLEAN)"},
        {"float", "'2024-01-01'::DATE", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_FLOAT': (DATE)"},
        {"float", "'2024-01-01 10:00:00'::TIMESTAMP_NTZ", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_FLOAT': (TIMESTAMP_NTZ(9))"},
        {"float", "'2024-01-01 10:00:00'::TIMESTAMP_LTZ", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_FLOAT': (TIMESTAMP_LTZ(9))"},
        {"float", "'2024-01-01 10:00:00 +0200'::TIMESTAMP_TZ", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_FLOAT': (TIMESTAMP_TZ(9))"},
        {"float", "'10:00:00'::TIME", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_FLOAT': (TIME(9))"},
        {"float", "PARSE_JSON('1')", "Y"},
        {"float", "[0]", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_FLOAT': (ARRAY)"},
        {"float", "{'a': 1}", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_FLOAT': (OBJECT)"},
        {"float", "TO_BINARY('AB', 'HEX')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_FLOAT': (BINARY(67108864))"},
        {"float", "[1,2,3]::VECTOR(INT, 3)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_FLOAT': (VECTOR(INT, 3))"},
        {"float", "NULL", "Y"},
        {"boolean", "'abc'", "Y"},
        {"boolean", "1", "Y"},
        {"boolean", "1.5", "Y"},
        {"boolean", "1.5::FLOAT", "Y"},
        {"boolean", "TRUE", "Y"},
        {"boolean", "'2024-01-01'::DATE", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_BOOLEAN': (DATE)"},
        {"boolean", "'2024-01-01 10:00:00'::TIMESTAMP_NTZ", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_BOOLEAN': (TIMESTAMP_NTZ(9))"},
        {"boolean", "'2024-01-01 10:00:00'::TIMESTAMP_LTZ", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_BOOLEAN': (TIMESTAMP_LTZ(9))"},
        {"boolean", "'2024-01-01 10:00:00 +0200'::TIMESTAMP_TZ", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_BOOLEAN': (TIMESTAMP_TZ(9))"},
        {"boolean", "'10:00:00'::TIME", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_BOOLEAN': (TIME(9))"},
        {"boolean", "PARSE_JSON('1')", "Y"},
        {"boolean", "[0]", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_BOOLEAN': (ARRAY)"},
        {"boolean", "{'a': 1}", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_BOOLEAN': (OBJECT)"},
        {"boolean", "TO_BINARY('AB', 'HEX')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_BOOLEAN': (BINARY(67108864))"},
        {"boolean", "[1,2,3]::VECTOR(INT, 3)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_BOOLEAN': (VECTOR(INT, 3))"},
        {"boolean", "NULL", "Y"},
        {"date", "'abc'", "Y"},
        {"date", "1", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_DATE': (NUMBER(1,0))"},
        {"date", "1.5", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_DATE': (NUMBER(2,1))"},
        {"date", "1.5::FLOAT", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_DATE': (FLOAT)"},
        {"date", "TRUE", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_DATE': (BOOLEAN)"},
        {"date", "'2024-01-01'::DATE", "Y"},
        {"date", "'2024-01-01 10:00:00'::TIMESTAMP_NTZ", "Y"},
        {"date", "'2024-01-01 10:00:00'::TIMESTAMP_LTZ", "Y"},
        {"date", "'2024-01-01 10:00:00 +0200'::TIMESTAMP_TZ", "Y"},
        {"date", "'10:00:00'::TIME", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_DATE': (TIME(9))"},
        {"date", "PARSE_JSON('1')", "Y"},
        {"date", "[0]", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_DATE': (ARRAY)"},
        {"date", "{'a': 1}", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_DATE': (OBJECT)"},
        {"date", "TO_BINARY('AB', 'HEX')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_DATE': (BINARY(67108864))"},
        {"date", "[1,2,3]::VECTOR(INT, 3)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_DATE': (VECTOR(INT, 3))"},
        {"date", "NULL", "Y"},
        {"timestamp_ntz", "'abc'", "Y"},
        {"timestamp_ntz", "1", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIMESTAMP_NTZ': (NUMBER(1,0))"},
        {"timestamp_ntz", "1.5", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIMESTAMP_NTZ': (NUMBER(2,1))"},
        {"timestamp_ntz", "1.5::FLOAT", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIMESTAMP_NTZ': (FLOAT)"},
        {"timestamp_ntz", "TRUE", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIMESTAMP_NTZ': (BOOLEAN)"},
        {"timestamp_ntz", "'2024-01-01'::DATE", "Y"},
        {"timestamp_ntz", "'2024-01-01 10:00:00'::TIMESTAMP_NTZ", "Y"},
        {"timestamp_ntz", "'2024-01-01 10:00:00'::TIMESTAMP_LTZ", "Y"},
        {"timestamp_ntz", "'2024-01-01 10:00:00 +0200'::TIMESTAMP_TZ", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIMESTAMP_NTZ': (TIMESTAMP_TZ(9))"},
        {"timestamp_ntz", "'10:00:00'::TIME", "SQL compilation error:\nincompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
        {"timestamp_ntz", "PARSE_JSON('1')", "Y"},
        {"timestamp_ntz", "[0]", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIMESTAMP_NTZ': (ARRAY)"},
        {"timestamp_ntz", "{'a': 1}", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIMESTAMP_NTZ': (OBJECT)"},
        {"timestamp_ntz", "TO_BINARY('AB', 'HEX')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIMESTAMP_NTZ': (BINARY(67108864))"},
        {"timestamp_ntz", "[1,2,3]::VECTOR(INT, 3)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIMESTAMP_NTZ': (VECTOR(INT, 3))"},
        {"timestamp_ntz", "NULL", "Y"},
        {"timestamp_ltz", "'abc'", "Y"},
        {"timestamp_ltz", "1", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIMESTAMP_LTZ': (NUMBER(1,0))"},
        {"timestamp_ltz", "1.5::FLOAT", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIMESTAMP_LTZ': (FLOAT)"},
        {"timestamp_ltz", "TRUE", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIMESTAMP_LTZ': (BOOLEAN)"},
        {"timestamp_ltz", "'2024-01-01'::DATE", "Y"},
        {"timestamp_ltz", "'2024-01-01 10:00:00'::TIMESTAMP_NTZ", "Y"},
        {"timestamp_ltz", "'2024-01-01 10:00:00'::TIMESTAMP_LTZ", "Y"},
        {"timestamp_ltz", "'2024-01-01 10:00:00 +0200'::TIMESTAMP_TZ", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIMESTAMP_LTZ': (TIMESTAMP_TZ(9))"},
        {"timestamp_ltz", "'10:00:00'::TIME", "SQL compilation error:\nincompatible types: [TIME(9)] and [TIMESTAMP_LTZ(9)]"},
        {"timestamp_ltz", "PARSE_JSON('1')", "Y"},
        {"timestamp_ltz", "[0]", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIMESTAMP_LTZ': (ARRAY)"},
        {"timestamp_ltz", "{'a': 1}", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIMESTAMP_LTZ': (OBJECT)"},
        {"timestamp_ltz", "TO_BINARY('AB', 'HEX')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIMESTAMP_LTZ': (BINARY(67108864))"},
        {"timestamp_ltz", "[1,2,3]::VECTOR(INT, 3)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIMESTAMP_LTZ': (VECTOR(INT, 3))"},
        {"timestamp_ltz", "NULL", "Y"},
        {"timestamp_tz", "'abc'", "Y"},
        {"timestamp_tz", "1", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIMESTAMP_TZ': (NUMBER(1,0))"},
        {"timestamp_tz", "1.5::FLOAT", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIMESTAMP_TZ': (FLOAT)"},
        {"timestamp_tz", "TRUE", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIMESTAMP_TZ': (BOOLEAN)"},
        {"timestamp_tz", "'2024-01-01'::DATE", "Y"},
        {"timestamp_tz", "'2024-01-01 10:00:00'::TIMESTAMP_NTZ", "Y"},
        {"timestamp_tz", "'2024-01-01 10:00:00'::TIMESTAMP_LTZ", "Y"},
        {"timestamp_tz", "'2024-01-01 10:00:00 +0200'::TIMESTAMP_TZ", "Y"},
        {"timestamp_tz", "'10:00:00'::TIME", "SQL compilation error:\nincompatible types: [TIME(9)] and [TIMESTAMP_TZ(9)]"},
        {"timestamp_tz", "PARSE_JSON('1')", "Y"},
        {"timestamp_tz", "[0]", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIMESTAMP_TZ': (ARRAY)"},
        {"timestamp_tz", "{'a': 1}", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIMESTAMP_TZ': (OBJECT)"},
        {"timestamp_tz", "TO_BINARY('AB', 'HEX')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIMESTAMP_TZ': (BINARY(67108864))"},
        {"timestamp_tz", "[1,2,3]::VECTOR(INT, 3)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIMESTAMP_TZ': (VECTOR(INT, 3))"},
        {"timestamp_tz", "NULL", "Y"},
        {"time", "'abc'", "Y"},
        {"time", "1", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIME': (NUMBER(1,0))"},
        {"time", "1.5", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIME': (NUMBER(2,1))"},
        {"time", "1.5::FLOAT", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIME': (FLOAT)"},
        {"time", "TRUE", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIME': (BOOLEAN)"},
        {"time", "'2024-01-01'::DATE", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIME': (DATE)"},
        {"time", "'2024-01-01 10:00:00'::TIMESTAMP_NTZ", "Y"},
        {"time", "'2024-01-01 10:00:00'::TIMESTAMP_LTZ", "Y"},
        {"time", "'2024-01-01 10:00:00 +0200'::TIMESTAMP_TZ", "Y"},
        {"time", "'10:00:00'::TIME", "Y"},
        {"time", "PARSE_JSON('1')", "Y"},
        {"time", "[0]", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIME': (ARRAY)"},
        {"time", "{'a': 1}", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIME': (OBJECT)"},
        {"time", "TO_BINARY('AB', 'HEX')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIME': (BINARY(67108864))"},
        {"time", "[1,2,3]::VECTOR(INT, 3)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_TIME': (VECTOR(INT, 3))"},
        {"time", "NULL", "Y"},
        {"variant", "'abc'", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_VARIANT': (VARCHAR(3))"},
        {"variant", "1", "Y"},
        {"variant", "1.5", "Y"},
        {"variant", "1.5::FLOAT", "Y"},
        {"variant", "TRUE", "Y"},
        {"variant", "'2024-01-01'::DATE", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_VARIANT': (DATE)"},
        {"variant", "'2024-01-01 10:00:00'::TIMESTAMP_NTZ", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_VARIANT': (TIMESTAMP_NTZ(9))"},
        {"variant", "'2024-01-01 10:00:00'::TIMESTAMP_LTZ", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_VARIANT': (TIMESTAMP_LTZ(9))"},
        {"variant", "'2024-01-01 10:00:00 +0200'::TIMESTAMP_TZ", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_VARIANT': (TIMESTAMP_TZ(9))"},
        {"variant", "'10:00:00'::TIME", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_VARIANT': (TIME(9))"},
        {"variant", "PARSE_JSON('1')", "Y"},
        {"variant", "[0]", "Y"},
        {"variant", "{'a': 1}", "Y"},
        {"variant", "TO_BINARY('AB', 'HEX')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_VARIANT': (BINARY(67108864))"},
        {"variant", "[1,2,3]::VECTOR(INT, 3)", "Y"},
        {"variant", "NULL", "Y"},
        {"array", "'abc'", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_ARRAY': (VARCHAR(3))"},
        {"array", "1", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_ARRAY': (NUMBER(1,0))"},
        {"array", "1.5", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_ARRAY': (NUMBER(2,1))"},
        {"array", "1.5::FLOAT", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_ARRAY': (FLOAT)"},
        {"array", "TRUE", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_ARRAY': (BOOLEAN)"},
        {"array", "'2024-01-01'::DATE", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_ARRAY': (DATE)"},
        {"array", "'2024-01-01 10:00:00'::TIMESTAMP_NTZ", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_ARRAY': (TIMESTAMP_NTZ(9))"},
        {"array", "'2024-01-01 10:00:00'::TIMESTAMP_LTZ", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_ARRAY': (TIMESTAMP_LTZ(9))"},
        {"array", "'2024-01-01 10:00:00 +0200'::TIMESTAMP_TZ", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_ARRAY': (TIMESTAMP_TZ(9))"},
        {"array", "'10:00:00'::TIME", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_ARRAY': (TIME(9))"},
        {"array", "PARSE_JSON('1')", "Y"},
        {"array", "[0]", "Y"},
        {"array", "{'a': 1}", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_ARRAY': (OBJECT)"},
        {"array", "TO_BINARY('AB', 'HEX')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_ARRAY': (BINARY(67108864))"},
        {"array", "[1,2,3]::VECTOR(INT, 3)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_ARRAY': (VECTOR(INT, 3))"},
        {"array", "NULL", "Y"},
        {"object", "'abc'", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_OBJECT': (VARCHAR(3))"},
        {"object", "1", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_OBJECT': (NUMBER(1,0))"},
        {"object", "1.5", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_OBJECT': (NUMBER(2,1))"},
        {"object", "1.5::FLOAT", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_OBJECT': (FLOAT)"},
        {"object", "TRUE", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_OBJECT': (BOOLEAN)"},
        {"object", "'2024-01-01'::DATE", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_OBJECT': (DATE)"},
        {"object", "'2024-01-01 10:00:00'::TIMESTAMP_NTZ", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_OBJECT': (TIMESTAMP_NTZ(9))"},
        {"object", "'2024-01-01 10:00:00'::TIMESTAMP_LTZ", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_OBJECT': (TIMESTAMP_LTZ(9))"},
        {"object", "'2024-01-01 10:00:00 +0200'::TIMESTAMP_TZ", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_OBJECT': (TIMESTAMP_TZ(9))"},
        {"object", "'10:00:00'::TIME", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_OBJECT': (TIME(9))"},
        {"object", "PARSE_JSON('1')", "Y"},
        {"object", "[0]", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_OBJECT': (ARRAY)"},
        {"object", "{'a': 1}", "Y"},
        {"object", "TO_BINARY('AB', 'HEX')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_OBJECT': (BINARY(67108864))"},
        {"object", "[1,2,3]::VECTOR(INT, 3)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_OBJECT': (VECTOR(INT, 3))"},
        {"object", "NULL", "Y"},
        {"binary", "'abc'", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_BINARY': (VARCHAR(3))"},
        {"binary", "1", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_BINARY': (NUMBER(1,0))"},
        {"binary", "1.5", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_BINARY': (NUMBER(2,1))"},
        {"binary", "1.5::FLOAT", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_BINARY': (FLOAT)"},
        {"binary", "TRUE", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_BINARY': (BOOLEAN)"},
        {"binary", "'2024-01-01'::DATE", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_BINARY': (DATE)"},
        {"binary", "'2024-01-01 10:00:00'::TIMESTAMP_NTZ", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_BINARY': (TIMESTAMP_NTZ(9))"},
        {"binary", "'2024-01-01 10:00:00'::TIMESTAMP_LTZ", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_BINARY': (TIMESTAMP_LTZ(9))"},
        {"binary", "'2024-01-01 10:00:00 +0200'::TIMESTAMP_TZ", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_BINARY': (TIMESTAMP_TZ(9))"},
        {"binary", "'10:00:00'::TIME", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_BINARY': (TIME(9))"},
        {"binary", "PARSE_JSON('1')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_BINARY': (VARIANT)"},
        {"binary", "[0]", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_BINARY': (ARRAY)"},
        {"binary", "{'a': 1}", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_BINARY': (OBJECT)"},
        {"binary", "TO_BINARY('AB', 'HEX')", "Y"},
        {"binary", "[1,2,3]::VECTOR(INT, 3)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_BINARY': (VECTOR(INT, 3))"},
        {"binary", "NULL", "Y"},
        {"geography", "'abc'", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_GEOGRAPHY': (VARCHAR(3))"},
        {"geography", "1", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_GEOGRAPHY': (NUMBER(1,0))"},
        {"geography", "1.5", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_GEOGRAPHY': (NUMBER(2,1))"},
        {"geography", "1.5::FLOAT", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_GEOGRAPHY': (FLOAT)"},
        {"geography", "TRUE", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_GEOGRAPHY': (BOOLEAN)"},
        {"geography", "'2024-01-01'::DATE", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_GEOGRAPHY': (DATE)"},
        {"geography", "'2024-01-01 10:00:00'::TIMESTAMP_NTZ", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_GEOGRAPHY': (TIMESTAMP_NTZ(9))"},
        {"geography", "'2024-01-01 10:00:00'::TIMESTAMP_LTZ", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_GEOGRAPHY': (TIMESTAMP_LTZ(9))"},
        {"geography", "'2024-01-01 10:00:00 +0200'::TIMESTAMP_TZ", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_GEOGRAPHY': (TIMESTAMP_TZ(9))"},
        {"geography", "'10:00:00'::TIME", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_GEOGRAPHY': (TIME(9))"},
        {"geography", "PARSE_JSON('1')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_GEOGRAPHY': (VARIANT)"},
        {"geography", "[0]", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_GEOGRAPHY': (ARRAY)"},
        {"geography", "{'a': 1}", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_GEOGRAPHY': (OBJECT)"},
        {"geography", "TO_BINARY('AB', 'HEX')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_GEOGRAPHY': (BINARY(67108864))"},
        {"geography", "[1,2,3]::VECTOR(INT, 3)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_GEOGRAPHY': (VECTOR(INT, 3))"},
        {"geography", "NULL", "Y"},
        {"vector", "'abc'", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_VECTOR': (VARCHAR(3))"},
        {"vector", "1", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_VECTOR': (NUMBER(1,0))"},
        {"vector", "1.5::FLOAT", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_VECTOR': (FLOAT)"},
        {"vector", "TRUE", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_VECTOR': (BOOLEAN)"},
        {"vector", "'2024-01-01'::DATE", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_VECTOR': (DATE)"},
        {"vector", "'2024-01-01 10:00:00'::TIMESTAMP_NTZ", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_VECTOR': (TIMESTAMP_NTZ(9))"},
        {"vector", "'2024-01-01 10:00:00'::TIMESTAMP_LTZ", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_VECTOR': (TIMESTAMP_LTZ(9))"},
        {"vector", "'2024-01-01 10:00:00 +0200'::TIMESTAMP_TZ", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_VECTOR': (TIMESTAMP_TZ(9))"},
        {"vector", "'10:00:00'::TIME", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_VECTOR': (TIME(9))"},
        {"vector", "PARSE_JSON('1')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_VECTOR': (VARIANT)"},
        {"vector", "[0]", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_VECTOR': (ARRAY)"},
        {"vector", "{'a': 1}", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_VECTOR': (OBJECT)"},
        {"vector", "TO_BINARY('AB', 'HEX')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_VECTOR': (BINARY(67108864))"},
        {"vector", "[1,2,3]::VECTOR(INT, 3)", "Y"},
        {"vector", "NULL", "Y"},
    };

    private final List<String> mismatches = new ArrayList<>();

    private void assertRefused(final String sql, final String message) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(message), sql + " -> " + refused.getMessage());
    }

    private String scalar(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private void createFunctions() {
        engine.execute("CREATE OR REPLACE FUNCTION f_varchar(p VARCHAR) RETURNS VARCHAR AS $$ p $$");
        engine.execute("CREATE OR REPLACE FUNCTION f_number(p NUMBER) RETURNS NUMBER AS $$ p $$");
        engine.execute("CREATE OR REPLACE FUNCTION f_float(p FLOAT) RETURNS FLOAT AS $$ p $$");
        engine.execute("CREATE OR REPLACE FUNCTION f_boolean(p BOOLEAN) RETURNS BOOLEAN AS $$ p $$");
        engine.execute("CREATE OR REPLACE FUNCTION f_date(p DATE) RETURNS DATE AS $$ p $$");
        engine.execute("CREATE OR REPLACE FUNCTION f_timestamp_ntz(p TIMESTAMP_NTZ) RETURNS TIMESTAMP_NTZ AS $$ p $$");
        engine.execute("CREATE OR REPLACE FUNCTION f_timestamp_ltz(p TIMESTAMP_LTZ) RETURNS TIMESTAMP_LTZ AS $$ p $$");
        engine.execute("CREATE OR REPLACE FUNCTION f_timestamp_tz(p TIMESTAMP_TZ) RETURNS TIMESTAMP_TZ AS $$ p $$");
        engine.execute("CREATE OR REPLACE FUNCTION f_time(p TIME) RETURNS TIME AS $$ p $$");
        engine.execute("CREATE OR REPLACE FUNCTION f_variant(p VARIANT) RETURNS VARIANT AS $$ p $$");
        engine.execute("CREATE OR REPLACE FUNCTION f_array(p ARRAY) RETURNS ARRAY AS $$ p $$");
        engine.execute("CREATE OR REPLACE FUNCTION f_object(p OBJECT) RETURNS OBJECT AS $$ p $$");
        engine.execute("CREATE OR REPLACE FUNCTION f_binary(p BINARY) RETURNS BINARY AS $$ p $$");
        engine.execute("CREATE OR REPLACE FUNCTION f_geography(p GEOGRAPHY) RETURNS GEOGRAPHY AS $$ p $$");
        engine.execute("CREATE OR REPLACE FUNCTION f_vector(p VECTOR(INT, 3)) RETURNS VECTOR(INT, 3) AS $$ p $$");
    }

    @Test
    public void theParameterArgumentMatrix() {
        createFunctions();
        for (final String[] cell : MATRIX) {
            // Over no rows at all: the pair is judged while the query compiles.
            final String sql = "SELECT f_" + cell[0] + "(" + cell[1] + ") FROM (SELECT 1 AS d) WHERE FALSE";
            String refusal = null;
            try {
                engine.executeQuery(sql);
            } catch (final RuntimeException e) {
                refusal = String.valueOf(e.getMessage());
            }
            if ("Y".equals(cell[2]) ? refusal != null : refusal == null || !refusal.contains(cell[2])) {
                mismatches.add(sql + " -> " + (refusal == null ? "compiled" : refusal));
            }
        }
        assertEquals("", String.join("\n", mismatches));
    }

    @Test
    public void theIssuesCalls() {
        engine.execute("CREATE OR REPLACE FUNCTION STORE_PROFIT2(p_name VARCHAR) RETURNS VARCHAR LANGUAGE SQL "
            + "AS $$ p_name $$");
        assertRefused("SELECT STORE_PROFIT2([0])",
            "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'STORE_PROFIT2': (ARRAY)");
        assertRefused("SELECT STORE_PROFIT2({'a': 1})",
            "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'STORE_PROFIT2': (OBJECT)");
        assertRefused("SELECT 1 AS a, STORE_PROFIT2([0]) AS b",
            "SQL compilation error: error line 1 at position 15\nInvalid argument types for function 'STORE_PROFIT2': (ARRAY)");
        assertRefused("SELECT UPPER(STORE_PROFIT2([0]))",
            "SQL compilation error: error line 1 at position 13\nInvalid argument types for function 'STORE_PROFIT2': (ARRAY)");
        assertRefused("SELECT STORE_PROFIT2(p_name => [0])",
            "SQL compilation error: error line 1 at position 7\nnamed arguments [P_NAME] do not match any signature for function STORE_PROFIT2");
        assertEquals("x", scalar("SELECT STORE_PROFIT2(PARSE_JSON('\"x\"'))"));
        assertEquals("1", scalar("SELECT STORE_PROFIT2(1)"));
        assertEquals("true", scalar("SELECT STORE_PROFIT2(TRUE)"));
        assertEquals("2024-01-01", scalar("SELECT STORE_PROFIT2('2024-01-01'::DATE)"));
    }

    @Test
    public void everyArgumentIsListed() {
        engine.execute("CREATE OR REPLACE FUNCTION f2(a VARCHAR, b NUMBER) RETURNS VARCHAR AS $$ a || b $$");
        assertRefused("SELECT f2([0], 1)",
            "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F2': (ARRAY, NUMBER(1,0))");
        assertRefused("SELECT f2('x', [0])",
            "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F2': (VARCHAR(1), ARRAY)");
        assertRefused("SELECT f2([0], {'a': 1})",
            "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F2': (ARRAY, OBJECT)");
    }

    @Test
    public void overloadsRefuseOnlyWhenEveryOneRefuses() {
        engine.execute("CREATE OR REPLACE FUNCTION ov1(p VARCHAR) RETURNS VARCHAR AS $$ 'v' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov1(p NUMBER) RETURNS VARCHAR AS $$ 'n' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov2(p NUMBER) RETURNS VARCHAR AS $$ 'n' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov2(p FLOAT) RETURNS VARCHAR AS $$ 'f' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ow(p ARRAY) RETURNS VARCHAR AS $$ 'a' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ow(p OBJECT) RETURNS VARCHAR AS $$ 'o' $$");
        assertRefused("SELECT ov1([0])",
            "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'OV1': (ARRAY)");
        assertRefused("SELECT ov2(TRUE)",
            "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'OV2': (BOOLEAN)");
        assertRefused("SELECT ow(1)",
            "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'OW': (NUMBER(1,0))");
        assertEquals("v", scalar("SELECT ov1('x')"));
        assertEquals("n", scalar("SELECT ov1(1)"));
        assertEquals("a", scalar("SELECT ow([1])"));
    }

    @Test
    public void columnArgumentsAreJudgedBeforeAnyRow() {
        engine.execute("CREATE OR REPLACE FUNCTION f_varchar(p VARCHAR) RETURNS VARCHAR AS $$ p $$");
        engine.execute("CREATE OR REPLACE FUNCTION f_number(p NUMBER) RETURNS NUMBER AS $$ p $$");
        engine.execute("CREATE OR REPLACE TABLE ta (a ARRAY, v VARIANT, o OBJECT)");
        engine.execute("INSERT INTO ta SELECT [1], PARSE_JSON('[1]'), {'k': 2}");
        assertRefused("SELECT f_varchar(a) FROM ta",
            "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_VARCHAR': (ARRAY)");
        assertRefused("SELECT f_varchar(o) FROM ta",
            "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_VARCHAR': (OBJECT)");
        assertRefused("SELECT f_number(a) FROM ta",
            "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_NUMBER': (ARRAY)");
        assertRefused("SELECT f_varchar(a) FROM ta WHERE FALSE",
            "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'F_VARCHAR': (ARRAY)");
        assertEquals("[1]", scalar("SELECT f_varchar(v) FROM ta"));
    }
}
