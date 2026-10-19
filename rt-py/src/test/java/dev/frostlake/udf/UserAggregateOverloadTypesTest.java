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
 * A call of a user aggregate runs the overload its arguments' static types choose, the same for every group - an
 * all-NULL group too - and a typed NULL runs its type's overload, as a scalar function's call chooses (all
 * live-verified).
 */
public class UserAggregateOverloadTypesTest extends MeasuredCellsTestSupport {

    @Test
    public void everyGroupRunsTheOverloadTheCallsTypesChoose() {
        engine.execute("CREATE OR REPLACE TABLE pot (g INT, v INT, s VARCHAR, j VARIANT)");
        engine.execute("INSERT INTO pot SELECT 1, 1, '1', TO_VARIANT(1)");
        engine.execute("INSERT INTO pot SELECT 1, 2, 'x', TO_VARIANT(2)");
        engine.execute("INSERT INTO pot SELECT 2, NULL, NULL, TO_VARIANT('a')");
        engine.execute("INSERT INTO pot SELECT 3, 5, '5', NULL");
        assertAnswer("Function PA successfully created.",
            "CREATE OR REPLACE AGGREGATE FUNCTION pa(x INT) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "HANDLER = 'S' AS $$\nclass S:\n    def __init__(self):\n        self._s = []\n    @property\n    "
                + "def aggregate_state(self):\n        return self._s\n    def accumulate(self, x):\n        "
                + "self._s.append(repr(x))\n    def merge(self, other):\n        self._s.extend(other)\n    def "
                + "finish(self):\n        return 'INT:' + ','.join(sorted(self._s))\n$$");
        assertAnswer("Function PA successfully created.",
            "CREATE OR REPLACE AGGREGATE FUNCTION pa(x VARCHAR) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' HANDLER = 'S' AS $$\nclass S:\n    def __init__(self):\n        self._s = []\n    "
                + "@property\n    def aggregate_state(self):\n        return self._s\n    def accumulate(self, x):\n "
                + "       self._s.append(repr(x))\n    def merge(self, other):\n        self._s.extend(other)\n    "
                + "def finish(self):\n        return 'VARCHAR:' + ','.join(sorted(self._s))\n$$");
        assertAnswer("1|VARCHAR:'1','x' ;; 2|VARCHAR:None ;; 3|VARCHAR:'5'",
            "SELECT g, pa(s) FROM pot GROUP BY g ORDER BY g");
        assertAnswer("1|INT:1,2 ;; 2|INT:None ;; 3|INT:5", "SELECT g, pa(v) FROM pot GROUP BY g ORDER BY g");
        assertAnswer("1|VARCHAR:'1','2' ;; 2|VARCHAR:'a' ;; 3|VARCHAR:None",
            "SELECT g, pa(j) FROM pot GROUP BY g ORDER BY g");
        assertAnswer("INT:None|VARCHAR:None", "SELECT pa(NULL::INT), pa(NULL::VARCHAR)");
        assertAnswer("INT:None", "SELECT pa(NULL)");
        assertAnswer("1|VARCHAR:'1','2' ;; 2|VARCHAR:None ;; 3|VARCHAR:'5'",
            "SELECT g, pa(v::VARCHAR) FROM pot GROUP BY g ORDER BY g");
        assertAnswer("VARCHAR:None", "SELECT pa(s) FROM pot WHERE g = 2");
        assertAnswer("Function SFO successfully created.",
            "CREATE OR REPLACE FUNCTION sfo(x INT) RETURNS VARCHAR AS $$ 'INT' $$");
        assertAnswer("Function SFO successfully created.",
            "CREATE OR REPLACE FUNCTION sfo(x VARCHAR) RETURNS VARCHAR AS $$ 'VARCHAR' $$");
        assertAnswer("INT|VARCHAR|INT", "SELECT sfo(NULL), sfo(NULL::VARCHAR), sfo(NULL::INT)");
        assertAnswer("1|VARCHAR ;; 1|VARCHAR ;; 2|VARCHAR ;; 3|VARCHAR", "SELECT g, sfo(s) FROM pot ORDER BY g, 2");
    }
}
