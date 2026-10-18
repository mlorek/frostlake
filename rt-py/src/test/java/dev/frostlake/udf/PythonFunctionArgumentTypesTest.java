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
 * 1.255 as {@code Decimal('1.26')} and a FLOAT one reads 2 and '1.5' as {@code float}s.
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
}
