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
 * A Python function's answer is its RETURNS type's, converted as Python converts and judged as the account judges
 * it, as a procedure's is (live-verified): a {@code Decimal} is the NUMBER it spells and {@code bytes} a BINARY, a
 * TIMESTAMP_TZ keeps a {@code datetime}'s offset and a TIMESTAMP_NTZ or TIMESTAMP_LTZ refuses one with an offset,
 * a VARCHAR is {@code str()} of the answer, an INT {@code int()} of it and a FLOAT {@code float()} of it, and inside
 * a VARIANT a {@code Decimal}, {@code bytes} and a {@code datetime} are their {@code str()}. A NUMBER with a scale
 * rounds half to even, and an answer its type does not take is refused naming the function and its handler. A
 * VECTOR answer is a list, a tuple or a memoryview of its elements, cast as {@code ::VECTOR} casts it, with its
 * refusals (unmeasured on an account).
 */
public class PythonFunctionAnswerTypesTest extends MeasuredCellsTestSupport {

    @Test
    public void decimalsBinariesAndZonedTimestamps() {
        engine.execute("CREATE OR REPLACE FUNCTION dec_ret() RETURNS NUMBER(10,2) LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.12' HANDLER = 'h' AS $$\nimport decimal\ndef h():\n    return decimal.Decimal('2.50')\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION dec_arg(x NUMBER(10,2)) RETURNS NUMBER(10,2) LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' HANDLER = 'h' AS $$\ndef h(x):\n    return x + 1\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION bin_ret(x BINARY) RETURNS BINARY LANGUAGE PYTHON RUNTIME_VERSION "
            + "= '3.12' HANDLER = 'h' AS $$\ndef h(x):\n    return x + b'\\x00'\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION tz_ret(x TIMESTAMP_TZ) RETURNS TIMESTAMP_TZ LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' HANDLER = 'h' AS $$\ndef h(x):\n    return x\n$$");
        assertCells(new String[][] {
            {"SELECT dec_ret() + 1", "3.50"},
            {"SELECT dec_ret(), SYSTEM$TYPEOF(dec_ret())", "2.50|NUMBER(10,2)[SB8]"},
            {"SELECT dec_arg(1.25)", "2.25"},
            {"SELECT HEX_ENCODE(bin_ret(X'FF'))", "FF00"},
            {"CREATE OR REPLACE TABLE dect AS SELECT dec_ret() AS v", "Table DECT successfully created."},
            {"SELECT v + 1 FROM dect", "3.50"},
        });
    }

    @Test
    public void zonedTimestampsKeepTheirOffset() {
        engine.execute("CREATE OR REPLACE FUNCTION tz_ret(x TIMESTAMP_TZ) RETURNS TIMESTAMP_TZ LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' HANDLER = 'h' AS $$\ndef h(x):\n    return x\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION tz_arg(x TIMESTAMP_TZ) RETURNS VARCHAR LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' HANDLER = 'h' AS $$\ndef h(x):\n    return x.isoformat().replace(':', "
            + "'.')\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION tz_rows(c TIMESTAMP_TZ) RETURNS TABLE(t VARCHAR) LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' HANDLER = 'H' AS $$\nclass H:\n    def process(self, c):\n        yield "
            + "(c.isoformat().replace(':', '.'),)\n$$");
        assertCells(new String[][] {
            {"SELECT REPLACE(tz_ret('2024-01-02 03:04:05 +05:30'::TIMESTAMP_TZ)::VARCHAR, ':', '.')",
                "2024-01-02 03.04.05.000 +0530"},
            {"SELECT tz_arg('2024-01-02 03:04:05 +05:30'::TIMESTAMP_TZ)", "2024-01-02T03.04.05+05.30"},
            {"SELECT * FROM TABLE(tz_rows('2024-01-02 03:04:05 +05:30'::TIMESTAMP_TZ))", "2024-01-02T03.04.05+05.30"},
        });
    }

    @Test
    public void insideAVariantClassesAreTheirText() {
        engine.execute("CREATE OR REPLACE FUNCTION ret_v() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.12' "
            + "HANDLER = 'h' AS $$\nimport datetime, decimal\nZ = datetime.timezone(datetime.timedelta(hours=5, "
            + "minutes=30))\ndef h():\n    return {'d': decimal.Decimal('1.50'), 'b': b'\\x01\\xff', 'z': "
            + "datetime.datetime(2024, 1, 2, 3, 4, 5, tzinfo=Z), 'l': [decimal.Decimal('2.5')]}\n$$");
        assertCells(new String[][] {
            {"SELECT ret_v():d, TYPEOF(ret_v():d)", "1.50|VARCHAR"},
            {"SELECT ret_v():b, TYPEOF(ret_v():b)", "b'\\x01\\xff'|VARCHAR"},
            {"SELECT REPLACE(ret_v():z::VARCHAR, ':', '.'), TYPEOF(ret_v():z)", "2024-01-02 03.04.05+05.30|VARCHAR"},
            {"SELECT ret_v():l, TYPEOF(ret_v():l[0])", "[\"2.5\"]|VARCHAR"},
        });
    }

    @Test
    public void theDeclaredTypeConvertsTheAnswer() {
        engine.execute("CREATE OR REPLACE FUNCTION dec_v() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.12' "
            + "HANDLER = 'h' AS $$\nimport datetime, decimal\nZ = datetime.timezone(datetime.timedelta(hours=5, "
            + "minutes=30))\ndef h():\n    return decimal.Decimal('2.50')\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION dec_s() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' "
            + "HANDLER = 'h' AS $$\nimport datetime, decimal\nZ = datetime.timezone(datetime.timedelta(hours=5, "
            + "minutes=30))\ndef h():\n    return decimal.Decimal('2.50')\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION dec_f() RETURNS FLOAT LANGUAGE PYTHON RUNTIME_VERSION = '3.12' "
            + "HANDLER = 'h' AS $$\nimport datetime, decimal\nZ = datetime.timezone(datetime.timedelta(hours=5, "
            + "minutes=30))\ndef h():\n    return decimal.Decimal('2.50')\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION dec_i() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.12' "
            + "HANDLER = 'h' AS $$\nimport datetime, decimal\nZ = datetime.timezone(datetime.timedelta(hours=5, "
            + "minutes=30))\ndef h():\n    return decimal.Decimal('2.50')\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION bin_s() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' "
            + "HANDLER = 'h' AS $$\nimport datetime, decimal\nZ = datetime.timezone(datetime.timedelta(hours=5, "
            + "minutes=30))\ndef h():\n    return b'\\x01\\xff'\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION bin_v() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.12' "
            + "HANDLER = 'h' AS $$\nimport datetime, decimal\nZ = datetime.timezone(datetime.timedelta(hours=5, "
            + "minutes=30))\ndef h():\n    return b'\\x01\\xff'\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION tz_n() RETURNS TIMESTAMP_NTZ LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.12' HANDLER = 'h' AS $$\nimport datetime, decimal\nZ = "
            + "datetime.timezone(datetime.timedelta(hours=5, minutes=30))\ndef h():\n    return "
            + "datetime.datetime(2024, 1, 2, 3, 4, 5, tzinfo=Z)\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION tz_s() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' "
            + "HANDLER = 'h' AS $$\nimport datetime, decimal\nZ = datetime.timezone(datetime.timedelta(hours=5, "
            + "minutes=30))\ndef h():\n    return datetime.datetime(2024, 1, 2, 3, 4, 5, tzinfo=Z)\n$$");
        assertCells(new String[][] {
            {"SELECT dec_v(), TYPEOF(dec_v())", "2.50|VARCHAR"},
            {"SELECT dec_s()", "2.50"},
            {"SELECT dec_f()", "2.5"},
            {"SELECT dec_i()", "2"},
            {"SELECT bin_s()", "b'\\x01\\xff'"},
            {"SELECT bin_v(), TYPEOF(bin_v())", "b'\\x01\\xff'|VARCHAR"},
            {"SELECT REPLACE(tz_n()::VARCHAR, ':', '.')",
                "REFUSED Datetime object must be naive to be convertible to TIMESTAMP_NTZ in function TZ_N with "
                + "handler h"},
            {"SELECT REPLACE(tz_s(), ':', '.')", "2024-01-02 03.04.05+05.30"},
        });
    }

    @Test
    public void answersTheTypeDoesNotTakeAreRefused() {
        engine.execute("CREATE OR REPLACE FUNCTION v_none() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.12' "
            + "HANDLER = 'h' AS $$\nimport datetime, decimal\ndef h():\n    return None\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION v_obj() RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION = '3.12' "
            + "HANDLER = 'h' AS $$\nimport datetime, decimal\ndef h():\n    return {'b': 1, 'a': 2.0, 'c': [1.5, "
            + "None]}\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION n_half() RETURNS NUMBER(10,2) LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.12' HANDLER = 'h' AS $$\nimport datetime, decimal\ndef h():\n    return 1.005\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION n_half2() RETURNS NUMBER(10,2) LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.12' HANDLER = 'h' AS $$\nimport datetime, decimal\ndef h():\n    return 1.015\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION i_frac() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.12' "
            + "HANDLER = 'h' AS $$\nimport datetime, decimal\ndef h():\n    return 2.7\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION b_text() RETURNS BOOLEAN LANGUAGE PYTHON RUNTIME_VERSION = '3.12' "
            + "HANDLER = 'h' AS $$\nimport datetime, decimal\ndef h():\n    return 'abc'\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION d_text() RETURNS DATE LANGUAGE PYTHON RUNTIME_VERSION = '3.12' "
            + "HANDLER = 'h' AS $$\nimport datetime, decimal\ndef h():\n    return '2024-01-02'\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION a_tuple() RETURNS ARRAY LANGUAGE PYTHON RUNTIME_VERSION = '3.12' "
            + "HANDLER = 'h' AS $$\nimport datetime, decimal\ndef h():\n    return (1, 2)\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION o_list() RETURNS OBJECT LANGUAGE PYTHON RUNTIME_VERSION = '3.12' "
            + "HANDLER = 'h' AS $$\nimport datetime, decimal\ndef h():\n    return [1]\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION n_big() RETURNS NUMBER(4,2) LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.12' HANDLER = 'h' AS $$\nimport datetime, decimal\ndef h():\n    return "
            + "decimal.Decimal('123.45')\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION t_text() RETURNS TIMESTAMP_NTZ LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.12' HANDLER = 'h' AS $$\nimport datetime, decimal\ndef h():\n    return '2024-01-02 "
            + "03:04:05'\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION l_aware() RETURNS TIMESTAMP_LTZ LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.12' HANDLER = 'h' AS $$\nimport datetime, decimal\ndef h():\n    return datetime.datetime(2024,"
            + " 1, 2, 3, 4, 5, tzinfo=datetime.timezone(datetime.timedelta(hours=5, minutes=30)))\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION bin_text() RETURNS BINARY LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.12' HANDLER = 'h' AS $$\nimport datetime, decimal\ndef h():\n    return 'ab'\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION f_text() RETURNS FLOAT LANGUAGE PYTHON RUNTIME_VERSION = '3.12' "
            + "HANDLER = 'h' AS $$\nimport datetime, decimal\ndef h():\n    return '1.5'\n$$");
        assertCells(new String[][] {
            {"SELECT v_none() IS NULL, TYPEOF(v_none())", "false|NULL_VALUE"},
            {"SELECT v_obj(), TYPEOF(v_obj():a)",
                "{\"a\":2.000000000000000e+00,\"b\":1,\"c\":[1.500000000000000e+00,null]}|DOUBLE"},
            {"SELECT n_half(), n_half2()", "1.00|1.02"},
            {"SELECT i_frac()", "2"},
            {"SELECT b_text()", "true"},
            {"SELECT d_text()", "REFUSED Return value should be a date object in function D_TEXT with handler h"},
            {"SELECT a_tuple()",
                "REFUSED python value needs to be a list to convert to Snowflake ARRAY in function A_TUPLE with "
                + "handler h"},
            {"SELECT o_list()",
                "REFUSED python value needs to be a dict to convert to Snowflake OBJECT in function O_LIST with "
                + "handler h"},
            {"SELECT n_big()",
                "REFUSED Decimal value cannot be losslessly represented as a Snowflake NUMBER with a precision of 4 "
                + "and a scale of 2 in function N_BIG with handler h"},
            {"SELECT t_text()", "REFUSED Return value should be a datetime object in function T_TEXT with handler h"},
            {"SELECT DATE_PART(EPOCH_SECOND, l_aware())",
                "REFUSED Datetime object must be naive to be convertible to TIMESTAMP_LTZ in function L_AWARE with "
                + "handler h"},
            {"SELECT bin_text()",
                "REFUSED Python Interpreter Error: | TypeError: cannot convert 'str' object to bytes in function "
                + "BIN_TEXT with handler h"},
            {"SELECT f_text()", "1.5"},
        });
    }

    @Test
    public void aProceduresNaiveTimestampRefusesAnOffset() {
        engine.execute("CREATE OR REPLACE PROCEDURE tz_np() RETURNS TIMESTAMP_NTZ LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.12' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'main' AS $$\nimport datetime\ndef "
            + "main(session):\n    return datetime.datetime(2024, 1, 2, 3, 4, 5, "
            + "tzinfo=datetime.timezone(datetime.timedelta(hours=5, minutes=30)))\n$$");
        assertCells(new String[][] {
            {"CALL tz_np()",
                "REFUSED Datetime object must be naive to be convertible to TIMESTAMP_NTZ in function TZ_NP with "
                + "handler main"},
        });
    }

    @Test
    public void aProceduresZonedTimestampKeepsItsOffset() {
        engine.execute("CREATE OR REPLACE PROCEDURE tz_p() RETURNS TIMESTAMP_TZ LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.12' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'main' AS $$\nimport datetime\ndef "
            + "main(session):\n    return datetime.datetime(2024, 1, 2, 3, 4, 5, "
            + "tzinfo=datetime.timezone(datetime.timedelta(hours=5, minutes=30)))\n$$");
        engine.execute("CREATE OR REPLACE TABLE tzt (t TIMESTAMP_TZ)");
        engine.execute("CALL tz_p()");
        assertCells(new String[][] {
            {"INSERT INTO tzt SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID(-1)))", "1"},
            {"SELECT REPLACE(t::VARCHAR, ':', '.') FROM tzt", "2024-01-02 03.04.05.000 +0530"},
        });
    }

    @Test
    public void aVectorAnswerIsCastAsAVector() {
        engine.execute("CREATE OR REPLACE FUNCTION vf() RETURNS VECTOR(FLOAT,2) LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.12' HANDLER = 'h' AS $$\ndef h():\n    return [1.0, 2.5]\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION vi(k INT) RETURNS VECTOR(INT,2) LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.12' HANDLER = 'h' AS $$\nimport array\ndef h(k):\n    return [[1, 2], (3, 4), "
            + "memoryview(array.array('i', [5, 6])), [1, 2, 3], [1.5, 2], 'abc', None][k]\n$$");
        assertCells(new String[][] {
            {"SELECT vf(), TYPEOF(vf()::VARIANT), vf() = [1,2.5]::VECTOR(FLOAT,2)", "[1.000000,2.500000]|VECTOR|true"},
            {"SELECT vi(0), vi(2), vi(4), vi(6)", "[1,2]|[5,6]|[1,2]|null"},
            {"SELECT VECTOR_INNER_PRODUCT(vi(0), [1,1]::VECTOR(INT,2))", "3.0"},
            // Only a list or what answers tolist() is a vector, of the declared length.
            {"SELECT vi(1)", "REFUSED Vectors returned from Python must be lists or array-like in function VI with "
                + "handler h"},
            {"SELECT vi(3)", "REFUSED Returned list dimension, 3, does not match the expected vector dimension, 2 in "
                + "function VI with handler h"},
            {"SELECT vi(5)", "REFUSED Vectors returned from Python must be lists or array-like in function VI with "
                + "handler h"},
        });
    }
}
