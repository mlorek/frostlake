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
 * A Python handler's semi-structured argument: a VARIANT holding a DATE, a TIME, a timestamp or a BINARY arrives
 * as the text the VARIANT shows - '2024-01-02 10:11:12.000', '10:11:12', 'ABCD' -, as the members of an ARRAY
 * and an OBJECT do; a NaN is its text, and a number, a boolean and parsed JSON are Python values (all
 * live-verified).
 */
public class PythonVariantArgumentTextTest extends MeasuredCellsTestSupport {

    @Test
    public void typedScalarsInsideArguments() {
        engine.execute("CREATE OR REPLACE FUNCTION py_t(v VARIANT) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.11' HANDLER = 'f' AS $$\ndef f(v):\n    return type(v).__name__ + ':' + repr(v)\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION py_a(a ARRAY) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.11' HANDLER = 'f' AS $$\ndef f(a):\n    return ','.join(type(x).__name__ + ':' + repr(x) for x "
            + "in a)\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION py_o(o OBJECT) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.11' HANDLER = 'f' AS $$\ndef f(o):\n    return ','.join(k + '=' + type(o[k]).__name__ + ':' + "
            + "repr(o[k]) for k in sorted(o))\n$$");
        assertAnswer("str:'2024-01-02'", "SELECT py_t(TO_VARIANT('2024-01-02'::DATE))");
        assertAnswer("str:'AB'", "SELECT py_t(TO_VARIANT(TO_BINARY('AB', 'HEX')))");
        assertAnswer("str:'NaN'", "SELECT py_t(TO_VARIANT('NaN'::FLOAT))");
        assertAnswer("str:'10:11:12'", "SELECT py_t(TO_VARIANT('10:11:12'::TIME))");
        answer("SELECT py_t(TO_VARIANT('2024-01-02 10:11:12'::TIMESTAMP_NTZ))");
        answer("SELECT py_t(TO_VARIANT('2024-01-02 10:11:12 +01:00'::TIMESTAMP_TZ))");
        assertAnswer("float:1.5", "SELECT py_t(TO_VARIANT(1.5::NUMBER(5,2)))");
        assertAnswer("str:'2024-01-02',str:'AB',str:'NaN',str:'10:11:12'",
            "SELECT py_a(ARRAY_CONSTRUCT('2024-01-02'::DATE, TO_BINARY('AB', 'HEX'), 'NaN'::FLOAT, '10:11:12'::TIME))");
        assertAnswer("b=str:'AB',d=str:'2024-01-02',n=str:'NaN',t=str:'10:11:12'",
            "SELECT py_o(OBJECT_CONSTRUCT('d', '2024-01-02'::DATE, 'b', TO_BINARY('AB', 'HEX'), 'n', 'NaN'::FLOAT, "
                + "'t', '10:11:12'::TIME))");
        assertAnswer("dict:{'a': 1.5, 'b': [1, 'x']}", "SELECT py_t(PARSE_JSON('{\"a\": 1.5, \"b\": [1, \"x\"]}'))");
    }

    @Test
    public void variantTextsSpelledOut() {
        engine.execute("CREATE OR REPLACE FUNCTION py_ts(v VARIANT) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION "
            + "= '3.11' HANDLER = 'f' AS $$\ndef f(v):\n    return type(v).__name__ + ':' + str(v).replace('-', "
            + "'/').replace(':', '.')\n$$");
        assertAnswer("str:2024/01/02 10.11.12.000", "SELECT py_ts(TO_VARIANT('2024-01-02 10:11:12'::TIMESTAMP_NTZ))");
        assertAnswer("str:2024/01/02 10.11.12.123",
            "SELECT py_ts(TO_VARIANT('2024-01-02 10:11:12.123456789'::TIMESTAMP_NTZ(9)))");
        assertAnswer("str:2024/01/02 10.11.12.000 +0100",
            "SELECT py_ts(TO_VARIANT('2024-01-02 10:11:12 +01:00'::TIMESTAMP_TZ))");
        assertAnswer("str:10.11.12", "SELECT py_ts(TO_VARIANT('10:11:12.5'::TIME))");
        assertAnswer("str:2024/01/02", "SELECT py_ts(TO_VARIANT('2024-01-02'::DATE))");
        assertAnswer("str:ABCD", "SELECT py_ts(TO_VARIANT(TO_BINARY('ABCD', 'HEX')))");
        assertAnswer("2024/01/02 10.11.12.000",
            "SELECT REPLACE(REPLACE(TO_VARIANT('2024-01-02 10:11:12'::TIMESTAMP_NTZ)::VARCHAR, '-', '/'), ':', '.')");
        assertAnswer("bool:True", "SELECT py_ts(TO_VARIANT(TRUE))");
        assertAnswer("int:12", "SELECT py_ts(TO_VARIANT(12::NUMBER(10,0)))");
    }
}
