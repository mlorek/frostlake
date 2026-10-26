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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * One process answers every row of a statement here, so the module a call of a Python function imports serves all of
 * the rows that call reaches, in order: a module counter counts 1, 2, 3 over three rows and an lru_cache hits from
 * the second row on. Each call of the function in a statement keeps a module of its own, and none outlives its
 * statement.
 */
public class PythonModulePerCallSiteTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE d");
        engine.execute("USE DATABASE d");
        engine.execute("USE SCHEMA PUBLIC");
        engine.execute("CREATE TABLE t (v INT)");
        engine.execute("INSERT INTO t VALUES (1), (2), (3)");
        engine.execute("""
            CREATE FUNCTION cnt(x INT) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.12' HANDLER = 'h' AS $$
            calls = 0
            def h(x):
                global calls
                calls += 1
                return calls
            $$""");
        engine.execute("""
            CREATE FUNCTION hits(x INT) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.12' HANDLER = 'h' AS $$
            import functools
            @functools.lru_cache(maxsize=None)
            def sq(n):
                return n * n
            def h(x):
                sq(x)
                return sq.cache_info().hits
            $$""");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            if (out.length() > 0) {
                out.append(" ;; ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                out.append(i == 0 ? "" : "|").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    @Test
    public void aCallsModuleServesEveryRowItReaches() {
        assertEquals("1|1 ;; 2|2 ;; 3|3", rows("SELECT v, cnt(v) FROM t ORDER BY v"));
        assertEquals("1|0 ;; 2|1 ;; 3|2", rows("SELECT v, hits(1) FROM t ORDER BY v"));
    }

    @Test
    public void eachCallKeepsItsOwnModuleForItsStatementAlone() {
        assertEquals("1|1|1 ;; 2|2|2 ;; 3|3|3", rows("SELECT v, cnt(v), cnt(v + 100) FROM t ORDER BY v"));
        assertEquals("3", rows("SELECT MAX(cnt(v)) FROM t"));
        assertEquals("3", rows("SELECT MAX(cnt(v)) FROM t"));
    }
}
