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
 * A Python function's arguments reach its handler converted to their parameters' SQL types and then mapped to
 * Python (live-verified): an INT parameter rounds 1.5 to the {@code int} 2 and reads '7', a BOOLEAN one reads
 * 'off' and 0 as {@code False}, a VARCHAR one reads 12345 as the {@code str} '12345', a NUMBER(10,2) one holds
 * 1.255 as {@code Decimal('1.26')} and a FLOAT one reads 2 and '1.5' as {@code float}s. A VECTOR arrives as the
 * {@code memoryview} Snowflake's SQL-Python type mapping names (docs.snowflake.com/en/developer-guide/
 * udf-stored-procedure-data-type-mapping), of its 32-bit elements, on both backends: a function and a procedure index
 * it, measure it and read it as a list (#2391; the memoryview's format is unmeasured).
 */
public class PythonFunctionArgumentTypesTest extends MeasuredCellsTestSupport {

    @Test
    public void argumentsTakeTheirParametersTypes() {
        engine.execute("CREATE OR REPLACE FUNCTION py_int(x INT) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.12' HANDLER = 'h' AS $$\ndef h(x):\n    return type(x).__name__ + ':' + str(x)\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION py_bool(a BOOLEAN) RETURNS VARCHAR LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' HANDLER = 'h' AS $$\ndef h(a):\n    return type(a).__name__ + ':' + ('T' "
            + "if a else 'F')\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION py_text(s VARCHAR) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.12' HANDLER = 'h' AS $$\ndef h(s):\n    return len(s)\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION py_dec(x NUMBER(10,2)) RETURNS VARCHAR LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' HANDLER = 'h' AS $$\ndef h(x):\n    return type(x).__name__ + ':' + "
            + "str(x)\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION py_flt(x FLOAT) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.12' HANDLER = 'h' AS $$\ndef h(x):\n    return type(x).__name__ + ':' + str(x)\n$$");
        assertCells(new String[][] {
            {"SELECT py_int(1.5), py_int('7')", "int:2|int:7"},
            {"SELECT py_bool('off'), py_bool(0)", "bool:F|bool:F"},
            {"SELECT py_text(12345)", "5"},
            {"SELECT py_dec(1.255), py_dec(3)", "Decimal:1.26|Decimal:3.00"},
            {"SELECT py_flt(2), py_flt('1.5')", "float:2.0|float:1.5"},
        });
    }

    @Test
    public void aVectorArrivesAsAMemoryviewOfItsElements() {
        engine.execute("CREATE OR REPLACE FUNCTION vlen(x VECTOR(FLOAT,2)) RETURNS FLOAT LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' HANDLER = 'h' AS $$\ndef h(x):\n    return x[0] + x[1]\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION vt(x VECTOR(FLOAT,3), y VECTOR(INT,2)) RETURNS VARCHAR LANGUAGE "
            + "PYTHON RUNTIME_VERSION = '3.12' HANDLER = 'h' AS $$\ndef h(x, y):\n    return ' '.join(["
            + "type(x).__name__, x.format, str(len(x)), repr(x.tolist()), type(y).__name__, y.format, "
            + "repr(list(y))])\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE vp(x VECTOR(INT,2)) RETURNS VARCHAR LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'main' AS $$\n"
            + "def main(session, x):\n    return type(x).__name__ + ' ' + repr(x.tolist())\n$$");
        assertCells(new String[][] {
            {"SELECT vlen([1.0,2.5]::VECTOR(FLOAT,2))", "3.5"},
            {"SELECT vt([0.1, 2.5, -3]::VECTOR(FLOAT,3), [7, -8]::VECTOR(INT,2))",
                "memoryview f 3 [0.10000000149011612, 2.5, -3.0] memoryview i [7, -8]"},
            {"CALL vp([4, 5]::VECTOR(INT,2))", "memoryview [4, 5]"},
        });
    }
}
