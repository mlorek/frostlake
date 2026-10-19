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
 * A name that holds a scalar overload beside an aggregate one aggregates only where the call's arguments choose
 * the aggregate (live-verified): over mix(x INT) and an aggregate mix(x VARCHAR), mix(1) and mix(v) over an INT
 * column run the scalar one for each row, grouped or not, mix(MAX(v)) is the scalar one over an aggregate, and
 * mix('a') and mix(s) aggregate.
 */
public class PythonAggregateBesideScalarTest extends MeasuredCellsTestSupport {

    @Test
    public void theArgumentsChooseTheKind() {
        engine.execute("CREATE OR REPLACE TABLE ut (v INT, s VARCHAR)");
        engine.execute("INSERT INTO ut VALUES (1, 'a'), (2, 'b'), (2, 'c')");
        engine.execute("CREATE OR REPLACE FUNCTION mix(x INT) RETURNS VARCHAR AS $$ 'scalar' || x $$");
        engine.execute("CREATE OR REPLACE AGGREGATE FUNCTION mix(x VARCHAR) RETURNS VARCHAR LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' HANDLER = 'S' AS $$\nclass S:\n    def __init__(self):\n        self._s "
            + "= 'agg'\n    @property\n    def aggregate_state(self):\n        return self._s\n    def "
            + "accumulate(self, x):\n        pass\n    def merge(self, other):\n        pass\n    def "
            + "finish(self):\n        return self._s\n$$");
        assertCells(new String[][] {
            {"SELECT mix(1), mix('a')", "scalar1|agg"},
            {"SELECT v, mix(v) FROM ut GROUP BY v ORDER BY 1", "1|scalar1 ;; 2|scalar2"},
            {"SELECT mix(v), COUNT(*) FROM ut GROUP BY v ORDER BY 1", "scalar1|1 ;; scalar2|2"},
            {"SELECT v, mix(s) FROM ut GROUP BY v ORDER BY 1", "1|agg ;; 2|agg"},
            {"SELECT mix(v) FROM ut WHERE v > 1 ORDER BY 1", "scalar2 ;; scalar2"},
            {"SELECT mix(v || '') FROM ut", "agg"},
            {"SELECT v FROM ut ORDER BY mix(v), s", "1 ;; 2 ;; 2"},
            {"SELECT mix(v), s FROM ut ORDER BY 2", "scalar1|a ;; scalar2|b ;; scalar2|c"},
            {"SELECT COUNT(*), mix(MAX(v)) FROM ut", "3|scalar2"},
            {"SELECT mix(v) OVER (ORDER BY v) FROM ut",
                "REFUSED SQL compilation error: | Invalid function type [MIX] for window function."},
        });
    }
}
