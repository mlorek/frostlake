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
 * A scalar function and a user aggregate may share a name; a call runs the overload its arguments' types choose,
 * row by row for the scalar one and over the group for the aggregate, and neither may be called OVER a window. A
 * CREATE OR REPLACE AGGREGATE FUNCTION of a scalar one's signature replaces it (all live-verified).
 */
public class ScalarBesideAggregateOverloadTest extends MeasuredCellsTestSupport {

    @Test
    public void aCallRunsTheOverloadItsTypesChoose() {
        engine.execute("CREATE OR REPLACE TABLE ut (v INT, s VARCHAR)");
        engine.execute("INSERT INTO ut VALUES (1, 'a'), (2, 'b')");
        assertAnswer("Function MIX successfully created.",
            "CREATE OR REPLACE FUNCTION mix(x INT) RETURNS VARCHAR AS $$ 'scalar' || x $$");
        assertAnswer("Function MIX successfully created.",
            "CREATE OR REPLACE AGGREGATE FUNCTION mix(x VARCHAR) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' HANDLER = 'S' AS $$\nclass S:\n    def __init__(self):\n        self._s = ''\n    "
                + "@property\n    def aggregate_state(self):\n        return self._s\n    def accumulate(self, x):\n "
                + "       self._s += str(x)\n    def merge(self, other):\n        self._s += other\n    def "
                + "finish(self):\n        return 'agg:' + self._s\n$$");
        assertAnswer("scalar1 ;; scalar2", "SELECT mix(v) FROM ut ORDER BY 1");
        assertAnswer("scalar1", "SELECT mix(1)");
        assertAnswer("1|scalar1 ;; 2|scalar2", "SELECT v, mix(v) FROM ut ORDER BY 1");
        assertAnswer("agg:ab", "SELECT mix(s) FROM ut");
        assertAnswer("agg:a", "SELECT mix('a')");
        assertAnswer("1|agg:a ;; 2|agg:b", "SELECT v, mix(s) FROM ut GROUP BY v ORDER BY 1");
        assertAnswer("null", "SELECT mix(NULL)");
        assertAnswer("scalar1.5", "SELECT mix(1.5)");
        assertAnswer("agg:12", "SELECT mix(v::VARCHAR) FROM ut");
        answer("SHOW USER FUNCTIONS LIKE 'MIX'");
        assertAnswer("MIX(NUMBER) RETURN VARCHAR|N ;; MIX(VARCHAR) RETURN VARCHAR|Y",
            "SELECT \"arguments\", \"is_aggregate\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Invalid function type [MIX] for window function.",
            "SELECT v, mix(v) OVER () FROM ut ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Invalid function type [MIX] for window function.",
            "SELECT mix(s) OVER () FROM ut");
        assertAnswer("Function MIX2 successfully created.",
            "CREATE OR REPLACE AGGREGATE FUNCTION mix2(x INT) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' HANDLER = 'S' AS $$\nclass S:\n    def __init__(self):\n        self._s = ''\n    "
                + "@property\n    def aggregate_state(self):\n        return self._s\n    def accumulate(self, x):\n "
                + "       self._s += str(x)\n    def merge(self, other):\n        self._s += other\n    def "
                + "finish(self):\n        return 'agg:' + self._s\n$$");
        assertAnswer("Function MIX2 successfully created.",
            "CREATE OR REPLACE FUNCTION mix2(x VARCHAR) RETURNS VARCHAR AS $$ 'scalar' || x $$");
        assertAnswer("agg:12", "SELECT mix2(v) FROM ut");
        assertAnswer("scalara ;; scalarb", "SELECT mix2(s) FROM ut ORDER BY 1");
        assertAnswer("scalara", "SELECT mix2('a')");
        assertAnswer("agg:1", "SELECT mix2(1)");
        assertAnswer("Function MIX3 successfully created.",
            "CREATE OR REPLACE FUNCTION mix3(x INT) RETURNS VARCHAR AS $$ 'scalar' || x $$");
        assertAnswer("Function MIX3 successfully created.",
            "CREATE OR REPLACE AGGREGATE FUNCTION mix3(x INT) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' HANDLER = 'S' AS $$\nclass S:\n    def __init__(self):\n        self._s = ''\n    "
                + "@property\n    def aggregate_state(self):\n        return self._s\n    def accumulate(self, x):\n "
                + "       self._s += str(x)\n    def merge(self, other):\n        self._s += other\n    def "
                + "finish(self):\n        return 'agg:' + self._s\n$$");
        assertAnswer("agg:1", "SELECT mix3(1)");
        assertAnswer("Function MIX4 successfully created.",
            "CREATE OR REPLACE FUNCTION mix4(x INT) RETURNS VARCHAR AS $$ 'scalar' || x $$");
        assertAnswer("Function MIX4 successfully created.",
            "CREATE AGGREGATE FUNCTION mix4(x VARCHAR) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "HANDLER = 'S' AS $$\nclass S:\n    def __init__(self):\n        self._s = ''\n    @property\n    "
                + "def aggregate_state(self):\n        return self._s\n    def accumulate(self, x):\n        self._s "
                + "+= str(x)\n    def merge(self, other):\n        self._s += other\n    def finish(self):\n        "
                + "return 'agg:' + self._s\n$$");
        assertAnswer("MIX successfully dropped.", "DROP FUNCTION mix(INT)");
        assertAnswer("agg:12", "SELECT mix(v) FROM ut");
        assertAnswer("MIX successfully dropped.", "DROP FUNCTION mix(VARCHAR)");
    }
}
