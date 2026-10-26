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
 * A Python function's module is imported once for a call of it and serves the rows that call reaches, so what the
 * module's code sets up is there for the next row: over a thousand rows a module counter passes 1 and an lru_cache
 * hits, and a later statement starts over (all live-verified). How far a counter gets depends on how the account
 * spreads the rows over its workers.
 */
public class PythonModuleOutlivesRowsTest extends MeasuredCellsTestSupport {

    private static final String COUNTER = """
        CREATE OR REPLACE FUNCTION mo_count(x INT) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.12'
        HANDLER = 'h' AS $$
        calls = 0
        def h(x):
            global calls
            calls += 1
            return calls
        $$""";

    private static final String HITS = """
        CREATE OR REPLACE FUNCTION mo_hits(x INT) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.12'
        HANDLER = 'h' AS $$
        import functools
        @functools.lru_cache(maxsize=None)
        def sq(n):
            return n * n
        def h(x):
            sq(x)
            return sq.cache_info().hits
        $$""";

    @Test
    public void aModuleOutlivesItsRow() {
        assertAnswer("Function MO_COUNT successfully created.", COUNTER);
        assertAnswer("Function MO_HITS successfully created.", HITS);
        assertAnswer("true|1000",
            "SELECT MAX(mo_count(SEQ4()::INT)) > 1, COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 1000))");
        assertAnswer("true", "SELECT MAX(mo_count(SEQ4()::INT)) <= 1000 FROM TABLE(GENERATOR(ROWCOUNT => 1000))");
        assertAnswer("true", "SELECT MAX(mo_hits(1)) > 0 FROM TABLE(GENERATOR(ROWCOUNT => 1000))");
    }
}
