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
 * A Python aggregate function's arguments reach {@code accumulate} converted to their parameters' SQL types, and
 * what {@code finish} answers is the RETURNS type's, as a scalar function's values are (live-verified): an INT
 * parameter rounds 1.5 and 2.4 to 2, a NUMBER(10,2) one holds 1.255 as {@code Decimal('1.26')}, a VARCHAR one
 * reads a number as its text and a BOOLEAN one reads 'off' and 'yes'; a NUMBER(10,2) answer rounds half to even,
 * a VARCHAR one is {@code str()} of a {@code Decimal} and a DATE one refuses a text.
 */
public class PythonAggregateValueTypesTest extends MeasuredCellsTestSupport {

    @Test
    public void argumentsTakeTheirParametersTypes() {
        engine.execute("CREATE OR REPLACE AGGREGATE FUNCTION a_int(x INT) RETURNS VARCHAR LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' HANDLER = 'A' AS $$\nclass A:\n    def __init__(self):\n        "
            + "self._seen = []\n    @property\n    def aggregate_state(self):\n        return self._seen\n    "
            + "def accumulate(self, x):\n        self._seen.append(type(x).__name__ + ':' + str(x))\n    def "
            + "merge(self, other):\n        self._seen.extend(other)\n    def finish(self):\n        return ',"
            + "'.join(sorted(self._seen))\n$$");
        engine.execute("CREATE OR REPLACE AGGREGATE FUNCTION a_dec(x NUMBER(10,2)) RETURNS VARCHAR LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' HANDLER = 'A' AS $$\nclass A:\n    def __init__(self):\n        "
            + "self._seen = []\n    @property\n    def aggregate_state(self):\n        return self._seen\n    "
            + "def accumulate(self, x):\n        self._seen.append(type(x).__name__ + ':' + str(x))\n    def "
            + "merge(self, other):\n        self._seen.extend(other)\n    def finish(self):\n        return ',"
            + "'.join(sorted(self._seen))\n$$");
        engine.execute("CREATE OR REPLACE AGGREGATE FUNCTION a_text(x VARCHAR) RETURNS VARCHAR LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' HANDLER = 'A' AS $$\nclass A:\n    def __init__(self):\n        "
            + "self._seen = []\n    @property\n    def aggregate_state(self):\n        return self._seen\n    "
            + "def accumulate(self, x):\n        self._seen.append(type(x).__name__ + ':' + str(x))\n    def "
            + "merge(self, other):\n        self._seen.extend(other)\n    def finish(self):\n        return ',"
            + "'.join(sorted(self._seen))\n$$");
        engine.execute("CREATE OR REPLACE AGGREGATE FUNCTION a_bool(x BOOLEAN) RETURNS VARCHAR LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' HANDLER = 'A' AS $$\nclass A:\n    def __init__(self):\n        "
            + "self._seen = []\n    @property\n    def aggregate_state(self):\n        return self._seen\n    "
            + "def accumulate(self, x):\n        self._seen.append(type(x).__name__ + ':' + str(x))\n    def "
            + "merge(self, other):\n        self._seen.extend(other)\n    def finish(self):\n        return ',"
            + "'.join(sorted(self._seen))\n$$");
        assertCells(new String[][] {
            {"SELECT a_int(v) FROM (SELECT 1.5 AS v UNION ALL SELECT 2.4)", "int:2,int:2"},
            {"SELECT a_dec(v) FROM (SELECT 1.255 AS v UNION ALL SELECT 3)", "Decimal:1.26,Decimal:3.00"},
            {"SELECT a_text(v) FROM (SELECT 12 AS v UNION ALL SELECT 3.5)", "str:12.0,str:3.5"},
            {"SELECT a_bool(v) FROM (SELECT 'off' AS v UNION ALL SELECT 'yes')", "bool:False,bool:True"},
        });
    }

    @Test
    public void theAnswerTakesTheReturnsType() {
        engine.execute("CREATE OR REPLACE AGGREGATE FUNCTION a_sum(x NUMBER(10,2)) RETURNS NUMBER(10,2) LANGUAGE "
            + "PYTHON RUNTIME_VERSION = '3.12' HANDLER = 'A' AS $$\nimport decimal\nclass A:\n    def "
            + "__init__(self):\n        self._s = decimal.Decimal(0)\n    @property\n    def "
            + "aggregate_state(self):\n        return self._s\n    def accumulate(self, x):\n        self._s += "
            + "x\n    def merge(self, other):\n        self._s += other\n    def finish(self):\n        return "
            + "self._s + decimal.Decimal('0.005')\n$$");
        engine.execute("CREATE OR REPLACE AGGREGATE FUNCTION a_text(x NUMBER(10,2)) RETURNS VARCHAR LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' HANDLER = 'A' AS $$\nimport decimal\nclass A:\n    def __init__(self):\n "
            + "       self._s = decimal.Decimal(0)\n    @property\n    def aggregate_state(self):\n        "
            + "return self._s\n    def accumulate(self, x):\n        self._s += x\n    def merge(self, other):\n "
            + "       self._s += other\n    def finish(self):\n        return self._s\n$$");
        engine.execute("CREATE OR REPLACE AGGREGATE FUNCTION a_date(x NUMBER(10,2)) RETURNS DATE LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' HANDLER = 'A' AS $$\nimport decimal\nclass A:\n    def __init__(self):\n "
            + "       self._s = decimal.Decimal(0)\n    @property\n    def aggregate_state(self):\n        "
            + "return self._s\n    def accumulate(self, x):\n        self._s += x\n    def merge(self, other):\n "
            + "       self._s += other\n    def finish(self):\n        return '2024-01-02'\n$$");
        assertCells(new String[][] {
            {"SELECT a_sum(v), a_sum(v) + 1 FROM (SELECT 1.25 AS v UNION ALL SELECT 2)", "3.26|4.26"},
            {"SELECT a_text(v) FROM (SELECT 1.25 AS v UNION ALL SELECT 2)", "3.25"},
            {"SELECT a_date(v) FROM (SELECT 1.25 AS v UNION ALL SELECT 2)",
                "REFUSED Return value should be a date object in function A_DATE with handler A"},
        });
    }
}
