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
 * CREATE OR REPLACE AGGREGATE FUNCTION of a scalar one's signature replaces it (all live-verified). An argument
 * qualified by a lone relation's alias, in a correlated subquery as at the top level, chooses as a bare one does
 *.
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

    @Test
    public void anAliasQualifiedArgumentChoosesAsABareOneDoes() {
        engine.execute("CREATE OR REPLACE TABLE uq (v INT, s VARCHAR)");
        engine.execute("INSERT INTO uq VALUES (1, 'a'), (2, 'b')");
        assertAnswer("Function MIXQ successfully created.",
            "CREATE OR REPLACE FUNCTION mixq(x INT) RETURNS VARCHAR AS $$ 'scalar' || x $$");
        assertAnswer("Function MIXQ successfully created.",
            "CREATE OR REPLACE AGGREGATE FUNCTION mixq(x VARCHAR) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' HANDLER = 'S' AS $$\nclass S:\n    def __init__(self):\n        self._s = ''\n    "
                + "@property\n    def aggregate_state(self):\n        return self._s\n    def accumulate(self, x):\n "
                + "       self._s += str(x)\n    def merge(self, other):\n        self._s += other\n    def "
                + "finish(self):\n        return 'agg:' + self._s\n$$");
        assertAnswer("1|scalar1 ;; 2|scalar2", "SELECT v, mixq(v) FROM uq ORDER BY 1");
        assertAnswer("1|scalar1 ;; 2|scalar2", "SELECT v, mixq(u2.v) FROM uq u2 ORDER BY 1");
        // Typed as the scalar, a correlated subquery over a FROM promises no one row and is refused.
        assertAnswer(unsupportedAt(8), "SELECT (SELECT mixq(u2.v) FROM uq u2 WHERE u2.v = uq.v) FROM uq ORDER BY 1");
        assertAnswer(unsupportedAt(8), "SELECT (SELECT mixq(v) FROM uq u2 WHERE u2.v = uq.v) FROM uq ORDER BY 1");
        assertAnswer("scalar1", "SELECT (SELECT mixq(u2.v) FROM uq u2 WHERE u2.v = 1)");
        assertAnswer(unsupportedAt(24),
            "SELECT v FROM uq WHERE (SELECT mixq(u2.v) FROM uq u2 WHERE u2.v = uq.v) = 'scalar1'");
        assertAnswer("1|scalar1 ;; 2|scalar2",
            "SELECT uq.v, l.m FROM uq, LATERAL (SELECT mixq(u2.v) m FROM uq u2 WHERE u2.v = uq.v) l ORDER BY 1");
        assertAnswer("agg:a ;; agg:b", "SELECT (SELECT mixq(u2.s) FROM uq u2 WHERE u2.v = uq.v) FROM uq ORDER BY 1");
    }

    @Test
    public void aScalarOverloadIsNoAggregateInAWhereOrAnOn() {
        engine.execute("CREATE OR REPLACE TABLE uw (v INT, s VARCHAR)");
        engine.execute("INSERT INTO uw VALUES (1, 'a'), (2, 'b')");
        assertAnswer("Function MIXW successfully created.",
            "CREATE OR REPLACE FUNCTION mixw(x INT) RETURNS VARCHAR AS $$ 'scalar' || x $$");
        assertAnswer("Function MIXW successfully created.",
            "CREATE OR REPLACE AGGREGATE FUNCTION mixw(x VARCHAR) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' HANDLER = 'S' AS $$\nclass S:\n    def __init__(self):\n        self._s = ''\n    "
                + "@property\n    def aggregate_state(self):\n        return self._s\n    def accumulate(self, x):\n "
                + "       self._s += str(x)\n    def merge(self, other):\n        self._s += other\n    def "
                + "finish(self):\n        return 'agg:' + self._s\n$$");
        // The arguments choose the scalar overload, which runs for each row wherever a scalar may stand.
        assertAnswer("1", "SELECT v FROM uw WHERE mixw(v) = 'scalar1'");
        assertAnswer("1", "SELECT v FROM uw WHERE 'scalar' || v = 'scalar1'");
        assertAnswer("1", "SELECT v FROM uw WHERE v IN (SELECT 1 FROM uw u2 WHERE mixw(u2.v) = 'scalar1')");
        assertAnswer("1|1 ;; 2|2",
            "SELECT uw.v, u2.v FROM uw JOIN uw u2 ON mixw(uw.v) = 'scalar' || u2.v ORDER BY 1");
        assertAnswer("1|1 ;; 2|null",
            "SELECT uw.v, u2.v FROM uw LEFT JOIN uw u2 ON mixw(u2.v) = 'scalar1' AND u2.v = uw.v ORDER BY 1");
        assertAnswer("1|1 ;; 2|null", "SELECT uw.v, l.x FROM uw LEFT JOIN LATERAL (SELECT u2.v x FROM uw u2 "
            + "WHERE u2.v = uw.v) l ON mixw(l.x) = 'scalar1' ORDER BY 1");
        assertAnswer("1|0", "UPDATE uw SET s = mixw(v) WHERE mixw(v) = 'scalar1'");
        assertAnswer("1", "MERGE INTO uw USING (SELECT 2 w) src ON mixw(uw.v) = mixw(src.w) "
            + "WHEN MATCHED THEN UPDATE SET s = mixw(src.w)");
        assertAnswer("1|scalar1 ;; 2|scalar2", "SELECT v, s FROM uw ORDER BY 1");
        assertAnswer("0", "DELETE FROM uw WHERE mixw(v) = 'none'");
        // The aggregate overload is still one, and still refused there.
        assertAnswer("REFUSED SQL compilation error: | Invalid aggregate function in where clause [EXTENSION_FUNC_A(UW.S)]",
            "SELECT v FROM uw WHERE mixw(s) = 'agg:a'");
        assertAnswer("REFUSED SQL compilation error: | Invalid aggregate function in ON clause [EXTENSION_FUNC_A(UW.S)]",
            "SELECT uw.v FROM uw JOIN uw u2 ON mixw(uw.s) = 'x'");
        assertAnswer("REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
            + "[EXTENSION_FUNC_A(UW.S)] can only appear in having or select clause",
            "SELECT v FROM uw WHERE (SELECT mixw(uw.s)) = 'x'");
        assertAnswer("REFUSED SQL compilation error: | [PSEUDOCOLUMN(VOL_ID)] is not a valid group by expression",
            "UPDATE uw SET s = mixw(s)");
        // A scalar call over the outer row alone is no correlated aggregate: a subquery with no FROM is one row
        // whatever it reads, answered here as in the select list.
        assertAnswer("1", "SELECT v FROM uw WHERE (SELECT mixw(uw.v)) = 'scalar1'");
        assertAnswer("scalar1 ;; scalar2", "SELECT (SELECT mixw(uw.v)) FROM uw ORDER BY 1");
    }
}
