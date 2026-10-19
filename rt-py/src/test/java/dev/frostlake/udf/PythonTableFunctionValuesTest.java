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
 * A Python table function's arguments reach {@code process} as the Python values their types map to, and each
 * value it writes is its column's type, converted as a function's answer is (live-verified): a TIMESTAMP_TZ
 * argument is a {@code datetime} with its offset, a VARIANT one a dict holding None, a NUMBER(10,2) one a
 * {@code Decimal}; a NUMBER(10,2) column holds {@code Decimal('1.255')} as 1.26 and a DATE column refuses a text.
 */
public class PythonTableFunctionValuesTest extends MeasuredCellsTestSupport {

    @Test
    public void argumentsAreValuesOfTheirTypes() {
        engine.execute("CREATE OR REPLACE FUNCTION tz_rows(c TIMESTAMP_TZ) RETURNS TABLE(t VARCHAR) LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' HANDLER = 'H' AS $$\nclass H:\n    def process(self, c):\n        yield "
            + "(c.isoformat(),)\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION v_rows(c VARIANT) RETURNS TABLE(t VARCHAR) LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' HANDLER = 'H' AS $$\nclass H:\n    def process(self, c):\n        yield "
            + "(repr(c),)\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION n_rows(c NUMBER(10,2)) RETURNS TABLE(t VARCHAR) LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' HANDLER = 'H' AS $$\nclass H:\n    def process(self, c):\n        yield "
            + "(repr(c),)\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION b_rows(c BOOLEAN) RETURNS TABLE(t VARCHAR) LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' HANDLER = 'H' AS $$\nclass H:\n    def process(self, c):\n        yield "
            + "(repr(c),)\n$$");
        assertCells(new String[][] {
            {"SELECT * FROM TABLE(v_rows(PARSE_JSON('{\"a\":null,\"b\":1.5}')))", "{'a': None, 'b': 1.5}"},
            {"SELECT * FROM TABLE(n_rows(1.25))", "Decimal('1.25')"},
            {"SELECT * FROM TABLE(b_rows('off'))",
                "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'B_ROWS': (VARCHAR(3))"},
        });
    }

    @Test
    public void writtenValuesTakeTheirColumnsTypes() {
        engine.execute("CREATE OR REPLACE FUNCTION o_num() RETURNS TABLE(c NUMBER(10,2)) LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' HANDLER = 'H' AS $$\nimport datetime, decimal\nZ = "
            + "datetime.timezone(datetime.timedelta(hours=5, minutes=30))\nclass H:\n    def process(self):\n    "
            + "    yield (decimal.Decimal('1.255'),)\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION o_int() RETURNS TABLE(c INT) LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.12' HANDLER = 'H' AS $$\nimport datetime, decimal\nZ = "
            + "datetime.timezone(datetime.timedelta(hours=5, minutes=30))\nclass H:\n    def process(self):\n    "
            + "    yield (2.7,)\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION o_text() RETURNS TABLE(c VARCHAR) LANGUAGE PYTHON RUNTIME_VERSION "
            + "= '3.12' HANDLER = 'H' AS $$\nimport datetime, decimal\nZ = "
            + "datetime.timezone(datetime.timedelta(hours=5, minutes=30))\nclass H:\n    def process(self):\n    "
            + "    yield (decimal.Decimal('2.50'),)\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION o_bin() RETURNS TABLE(c BINARY) LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.12' HANDLER = 'H' AS $$\nimport datetime, decimal\nZ = "
            + "datetime.timezone(datetime.timedelta(hours=5, minutes=30))\nclass H:\n    def process(self):\n    "
            + "    yield (b'\\x01\\xff',)\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION o_tz() RETURNS TABLE(c TIMESTAMP_TZ) LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' HANDLER = 'H' AS $$\nimport datetime, decimal\nZ = "
            + "datetime.timezone(datetime.timedelta(hours=5, minutes=30))\nclass H:\n    def process(self):\n    "
            + "    yield (datetime.datetime(2024, 1, 2, 3, 4, 5, tzinfo=Z),)\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION o_ntz() RETURNS TABLE(c TIMESTAMP_NTZ) LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' HANDLER = 'H' AS $$\nimport datetime, decimal\nZ = "
            + "datetime.timezone(datetime.timedelta(hours=5, minutes=30))\nclass H:\n    def process(self):\n    "
            + "    yield (datetime.datetime(2024, 1, 2, 3, 4, 5, tzinfo=Z),)\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION o_var() RETURNS TABLE(c VARIANT) LANGUAGE PYTHON RUNTIME_VERSION "
            + "= '3.12' HANDLER = 'H' AS $$\nimport datetime, decimal\nZ = "
            + "datetime.timezone(datetime.timedelta(hours=5, minutes=30))\nclass H:\n    def process(self):\n    "
            + "    yield ({'d': decimal.Decimal('1.5')},)\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION o_date() RETURNS TABLE(c DATE) LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.12' HANDLER = 'H' AS $$\nimport datetime, decimal\nZ = "
            + "datetime.timezone(datetime.timedelta(hours=5, minutes=30))\nclass H:\n    def process(self):\n    "
            + "    yield ('2024-01-02',)\n$$");
        assertCells(new String[][] {
            {"SELECT c FROM TABLE(o_num())", "1.26"},
            {"SELECT c FROM TABLE(o_int())", "2"},
            {"SELECT c FROM TABLE(o_text())", "2.50"},
            {"SELECT HEX_ENCODE(c) FROM TABLE(o_bin())", "01FF"},
            {"SELECT REPLACE(c::VARCHAR, ':', '.') FROM TABLE(o_tz())", "2024-01-02 03.04.05.000 +0530"},
            {"SELECT REPLACE(c::VARCHAR, ':', '.') FROM TABLE(o_ntz())",
                "REFUSED Datetime object must be naive to be convertible to TIMESTAMP_NTZ in function O_NTZ with "
                + "handler H"},
            {"SELECT c, TYPEOF(c:d) FROM TABLE(o_var())", "{\"d\":\"1.5\"}|VARCHAR"},
            {"SELECT c FROM TABLE(o_date())",
                "REFUSED Return value should be a date object in function O_DATE with handler H"},
        });
    }
}
