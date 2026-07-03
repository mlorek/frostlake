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

package dev.frostlake.perf;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Benchmarks UDF cost when a UDF is invoked PER ROW in SELECT (projection), WHERE (filter), and GROUP BY
 * (grouping key), across all five languages, versus a built-in baseline. Tagged {@code perf} (run with
 * {@code mvn test -Pperf -Dtest=UdfQueryPerformanceTest}); logs ms + µs/row, no timing assertions.
 *
 * <p>The metric that matters is µs per input row. All five languages now reuse setup across rows — Java
 * caches the compiled class, SQL the AST, and Scala/JS/Python cache the compiled handler / script / code
 * (see the *Executor classes) — so all run at the same row count. (Before that caching, JS/Python were
 * ~1000x a built-in and Scala recompiled every row, leaving it effectively unusable in queries.)
 */
@Tag("perf")
public class UdfQueryPerformanceTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(UdfQueryPerformanceTest.class);

    @Test
    public void udfPerRowOverheadAcrossClauses() {
        defineUdfs();
        logger.info("==== UDF per-row cost in SELECT / WHERE / GROUP BY (lower µs/row is better) ====");
        bench("builtin", "v * 2", 10_000);   // baseline: no UDF dispatch
        bench("sql",     "sql_dbl(v)", 10_000);
        bench("java",    "java_dbl(v)", 10_000);
        bench("js",      "js_dbl(v)", 10_000);
        bench("python",  "py_dbl(v)", 10_000);
        bench("scala",   "sc_dbl(v)", 10_000);
    }

    private void defineUdfs() {
        engine.execute("CREATE FUNCTION sql_dbl(x INTEGER) RETURNS INTEGER AS 'x * 2'");
        engine.execute("""
            CREATE FUNCTION java_dbl(x INTEGER) RETURNS INTEGER LANGUAGE JAVA HANDLER = 'D.dbl'
            AS $$ public class D { public static int dbl(int x) { return x * 2; } } $$
            """);
        engine.execute("""
            CREATE FUNCTION js_dbl(x INTEGER) RETURNS INTEGER LANGUAGE JAVASCRIPT
            AS $$ return x * 2; $$
            """);
        engine.execute("""
            CREATE FUNCTION py_dbl(x INTEGER) RETURNS INTEGER LANGUAGE PYTHON HANDLER = 'dbl'
            AS $$
            def dbl(x):
                return x * 2
            $$
            """);
        engine.execute("""
            CREATE FUNCTION sc_dbl(x INTEGER) RETURNS INTEGER LANGUAGE SCALA HANDLER = 'D.dbl'
            AS $$ object D { def dbl(x: Int): Int = x * 2 } $$
            """);
    }

    private void bench(final String label, final String expr, final int n) {
        final String tbl = "perf_" + label;
        seed(tbl, n);

        final long projNs = timeQuery("SELECT " + expr + " FROM " + tbl);
        final long whereNs = timeQuery("SELECT * FROM " + tbl + " WHERE " + expr + " > 0");
        final long groupNs = timeQuery("SELECT " + expr + " AS k, COUNT(*) AS c FROM " + tbl + " GROUP BY " + expr);

        logger.info(String.format(
            "%-8s n=%-6d | SELECT %9.1f ms (%8.1f µs/row) | WHERE %9.1f ms | GROUP BY %9.1f ms",
            label, n, projNs / 1e6, (projNs / 1_000.0) / n, whereNs / 1e6, groupNs / 1e6));

        // Correctness (not timing): the projection produces one row per input row.
        assertEquals(n, engine.executeQuery("SELECT " + expr + " FROM " + tbl).getRowCount(),
            label + " projection row count");
    }

    private void seed(final String tbl, final int n) {
        engine.execute("CREATE TABLE " + tbl + " (id INTEGER, v INTEGER)");
        final int batch = 1_000;
        int i = 0;
        while (i < n) {
            final int end = Math.min(i + batch, n);
            final StringBuilder sb = new StringBuilder("INSERT INTO " + tbl + " VALUES ");
            for (int j = i; j < end; j++) {
                if (j > i) {
                    sb.append(',');
                }
                sb.append('(').append(j).append(',').append(j % 100).append(')');   // v in 0..99 → 100 groups
            }
            engine.execute(sb.toString());
            i = end;
        }
    }

    private long timeQuery(final String sql) {
        final long start = System.nanoTime();
        final ResultSet rs = engine.executeQuery(sql);
        long sink = 0;
        for (final Row r : rs.getRows()) {
            if (r.getValue(0) != null) {
                sink++;
            }
        }
        final long elapsed = System.nanoTime() - start;
        if (sink == Long.MIN_VALUE) {
            logger.info("unreachable");   // keep the drain from being optimized away
        }
        return elapsed;
    }
}
