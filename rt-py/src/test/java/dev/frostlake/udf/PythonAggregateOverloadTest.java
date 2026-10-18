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
 * A Python aggregate function's overloads are chosen by the arguments' types (live-verified): {@code pa(1)} runs
 * {@code pa(INT)} and {@code pa('a')} runs {@code pa(VARCHAR)}, over constants and over columns alike, and a
 * NUMBER with a fraction or a NULL runs the INT one.
 */
public class PythonAggregateOverloadTest extends MeasuredCellsTestSupport {

    @Test
    public void anOverloadIsChosenByType() {
        engine.execute("CREATE OR REPLACE TABLE ut (v INT, s VARCHAR)");
        engine.execute("INSERT INTO ut VALUES (1, 'a'), (2, 'b')");
        engine.execute("CREATE OR REPLACE AGGREGATE FUNCTION pa(x INT) RETURNS VARCHAR LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' HANDLER = 'S' AS $$\nclass S:\n    def __init__(self):\n        self._s "
            + "= 'int'\n    @property\n    def aggregate_state(self):\n        return self._s\n    def "
            + "accumulate(self, x):\n        pass\n    def merge(self, other):\n        pass\n    def "
            + "finish(self):\n        return self._s\n$$");
        engine.execute("CREATE OR REPLACE AGGREGATE FUNCTION pa(x VARCHAR) RETURNS VARCHAR LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' HANDLER = 'S' AS $$\nclass S:\n    def __init__(self):\n        self._s "
            + "= 'varchar'\n    @property\n    def aggregate_state(self):\n        return self._s\n    def "
            + "accumulate(self, x):\n        pass\n    def merge(self, other):\n        pass\n    def "
            + "finish(self):\n        return self._s\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION mix(x INT) RETURNS VARCHAR AS $$ 'scalar' || x $$");
        engine.execute("CREATE OR REPLACE AGGREGATE FUNCTION mix(x VARCHAR) RETURNS VARCHAR LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' HANDLER = 'S' AS $$\nclass S:\n    def __init__(self):\n        self._s "
            + "= 'agg'\n    @property\n    def aggregate_state(self):\n        return self._s\n    def "
            + "accumulate(self, x):\n        pass\n    def merge(self, other):\n        pass\n    def "
            + "finish(self):\n        return self._s\n$$");
        assertCells(new String[][] {
            {"SELECT pa(1), pa('a')", "int|varchar"},
            {"SELECT pa(v), pa(s) FROM ut", "int|varchar"},
            {"SELECT mix(s) FROM ut", "agg"},
            {"SELECT pa(1.5)", "int"},
            {"SELECT pa(NULL)", "int"},
        });
    }
}
