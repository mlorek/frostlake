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
 * A correlated subquery that calls a name with a scalar overload beside an aggregate one is judged by the overload
 * its arguments choose: the scalar over a FROM promises no one row and is refused as an unsupported subquery, the
 * aggregate is answered, and a subquery with no FROM is one row whatever it calls (all live-verified).
 */
public class ScalarOverloadCorrelationTest extends MeasuredCellsTestSupport {

    @Test
    public void aCorrelatedCallIsJudgedByTheOverloadItsArgumentsChoose() {
        assertAnswer("Table T successfully created.", "CREATE TABLE t(v INT, s VARCHAR)");
        assertAnswer("2", "INSERT INTO t VALUES (1,'a'),(2,'b')");
        assertAnswer("Function MIX successfully created.",
            "CREATE FUNCTION mix(x INT) RETURNS VARCHAR AS $$ 'scalar' || x $$");
        assertAnswer("Function MIX successfully created.",
            "CREATE AGGREGATE FUNCTION mix(x VARCHAR) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION='3.12' "
                + "HANDLER='S' AS $$\nclass S:\n    def __init__(self):\n        self._s = ''\n    @property\n    "
                + "def aggregate_state(self):\n        return self._s\n    def accumulate(self, x):\n        self._s "
                + "+= str(x)\n    def merge(self, other):\n        self._s += other\n    def finish(self):\n        "
                + "return 'agg:' + self._s\n$$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8",
            "SELECT (SELECT mix(t2.v) FROM t t2 WHERE t2.v = t.v) FROM t ORDER BY 1");
        assertAnswer("agg:a ;; agg:b", "SELECT (SELECT mix(t2.s) FROM t t2 WHERE t2.v = t.v) FROM t ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8",
            "SELECT (SELECT UPPER(t2.s) FROM t t2 WHERE t2.v = t.v) FROM t ORDER BY 1");
        assertAnswer("a ;; b", "SELECT (SELECT MAX(t2.s) FROM t t2 WHERE t2.v = t.v) FROM t ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8",
            "SELECT (SELECT mix(t2.v) FROM t t2 WHERE t2.v = t.v LIMIT 1) FROM t ORDER BY 1");
        assertAnswer("1", "SELECT v FROM t WHERE (SELECT mix(t.v)) = 'scalar1'");
        assertAnswer("1", "SELECT v FROM t WHERE (SELECT UPPER(t.s)) = 'A'");
        assertAnswer("scalar1 ;; scalar2", "SELECT (SELECT mix(t.v)) FROM t ORDER BY 1");
        assertAnswer("1", "SELECT v FROM t WHERE mix(v) = 'scalar1'");
        assertAnswer("scalar1 ;; scalar1", "SELECT (SELECT mix(t2.v) FROM t t2 WHERE t2.v = 1) FROM t ORDER BY 1");
    }
}
