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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Load / performance / memory-footprint harness for the engine. Not a correctness gate: it MEASURES
 * and logs metrics (grep the test log for "PERF |"); assertions only check that operations complete
 * and return the expected row counts — never timings (per the project's no-timing-assertion rule).
 *
 * <p>Run on demand: {@code mvn test -Dtest=PerfLoadTest}. Tagged {@code perf} so it can be excluded
 * from the default suite. Sizes are deliberately modest because bulk insert is O(N^2) (every insert
 * deep-copies the whole table for time-travel snapshots) — that quadratic cost is itself a measured
 * result, see {@link #t1_insertScaling()}.
 */
@Tag("perf")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PerfLoadTest {

    private static final Logger log = LoggerFactory.getLogger(PerfLoadTest.class);
    private static DatabaseEngine engine;

    @BeforeAll
    public static void setup() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE perf_db");
        engine.execute("USE DATABASE perf_db");
        engine.execute("CREATE SCHEMA perf");
        engine.execute("USE SCHEMA perf");
        log.info("PERF | jvm.maxHeapMB | {}", Runtime.getRuntime().maxMemory() / (1024 * 1024));
    }

    @AfterAll
    public static void teardown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    // ---- helpers -------------------------------------------------------------

    private static void createTable(final String name) {
        engine.execute("DROP TABLE IF EXISTS " + name);
        engine.execute("CREATE TABLE " + name + " (id INTEGER, n INTEGER, grp VARCHAR, payload VARCHAR)");
    }

    /** Load n rows using multi-row INSERTs (batchSize rows per statement) to amortise parse cost. */
    private static void loadRows(final String table, final int n, final int batchSize) {
        int i = 0;
        while (i < n) {
            final int end = Math.min(i + batchSize, n);
            final StringBuilder sb = new StringBuilder("INSERT INTO ").append(table).append(" VALUES ");
            for (int j = i; j < end; j++) {
                if (j > i) {
                    sb.append(',');
                }
                sb.append('(').append(j).append(',').append(j % 1000)
                  .append(",'g").append(j % 10).append("','row_").append(j).append("')");
            }
            engine.execute(sb.toString());
            i = end;
        }
    }

    private static long usedHeapBytes() {
        final Runtime rt = Runtime.getRuntime();
        for (int k = 0; k < 3; k++) {
            rt.gc();
            try {
                Thread.sleep(40);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return rt.totalMemory() - rt.freeMemory();
    }

    private static long rowCount(final ResultSet rs) {
        return rs.getRowCount();
    }

    // ---- 1. insert scaling (write path) -------------------------------------

    @Test
    @Order(1)
    public void t1_insertScaling() {
        // Same per-row work at each size: if cost/row grows with N, the write path is super-linear.
        final int[] sizes = { 1000, 2000, 4000, 8000 };
        for (final int n : sizes) {
            createTable("ins");
            final long start = System.nanoTime();
            loadRows("ins", n, 200);
            final long ms = (System.nanoTime() - start) / 1_000_000L;
            final double perRowUs = (ms * 1000.0) / n;
            log.info("PERF | insert.scaling | rows={} totalMs={} perRowUs={}",
                n, ms, String.format("%.2f", perRowUs));
            assertEquals(n, rowCount(engine.executeQuery("SELECT id FROM ins")));
        }
    }

    // ---- 2. memory footprint -------------------------------------------------

    @Test
    @Order(2)
    public void t2_memoryFootprint() {
        final int n = 20000;
        createTable("mem");
        final long base = usedHeapBytes();
        loadRows("mem", n, 500);
        final long after = usedHeapBytes();
        final long bytes = after - base;
        log.info("PERF | memory.footprint | rows={} totalBytes={} bytesPerRow={} (incl. up to 200 snapshot copies)",
            n, bytes, bytes / n);
        assertEquals(n, rowCount(engine.executeQuery("SELECT id FROM mem")));
    }

    // ---- 3. query latency on a loaded table ---------------------------------

    @Test
    @Order(3)
    public void t3_queryLatency() {
        final int n = 20000;
        createTable("q");
        loadRows("q", n, 500);
        createTable("dim");
        loadRows("dim", 1000, 500);

        timeQuery("scan.all", "SELECT id, n, grp, payload FROM q", n);
        timeQuery("where.selective", "SELECT id FROM q WHERE n = 7", n / 1000);
        timeQuery("where.limit (streaming)", "SELECT id FROM q WHERE n >= 0 LIMIT 10", 10);
        timeQuery("group.by", "SELECT grp, COUNT(*) c FROM q GROUP BY grp", 10);
        timeQuery("distinct", "SELECT DISTINCT grp FROM q", 10);
        timeQuery("order.by", "SELECT id FROM q ORDER BY n, id", n);
        timeQuery("aggregate.sum", "SELECT SUM(n) s FROM q", 1);
        // equi self-join (hash join): q.id == dim.id for the first 1000 ids
        timeQuery("join.equi (hash)", "SELECT q.id FROM q JOIN dim ON q.id = dim.id", 1000);
        // uncorrelated IN-subquery (memoized once): dim has ids 0..999, so n in dim-ids
        timeQuery("subquery.uncorrelated", "SELECT id FROM q WHERE n IN (SELECT id FROM dim)", -1);
        // correlated EXISTS (re-evaluated per outer row)
        timeQuery("subquery.correlated", "SELECT id FROM dim d WHERE EXISTS (SELECT 1 FROM q WHERE q.n = d.id)", -1);
    }

    private static void timeQuery(final String label, final String sql, final long expectedRows) {
        // one warm-up (parse-cache, JIT), then a timed run
        engine.executeQuery(sql);
        final long start = System.nanoTime();
        final ResultSet rs = engine.executeQuery(sql);
        final long ms = (System.nanoTime() - start) / 1_000_000L;
        log.info("PERF | query.{} | ms={} rows={}", label, ms, rs.getRowCount());
        if (expectedRows >= 0) {
            assertEquals(expectedRows, rs.getRowCount(), "row count for " + label);
        }
    }

    // ---- 4. concurrency: read parallelism vs write serialization ------------

    @Test
    @Order(4)
    public void t4_concurrency() throws InterruptedException {
        final int n = 10000;
        createTable("c");
        loadRows("c", n, 500);

        final String readSql = "SELECT COUNT(*) FROM c WHERE n = 5";
        final int totalReads = 800;

        // serial baseline
        long start = System.nanoTime();
        for (int i = 0; i < totalReads; i++) {
            engine.executeQuery(readSql);
        }
        final long serialMs = (System.nanoTime() - start) / 1_000_000L;

        // 8 threads sharing the engine
        final int threads = 8;
        final AtomicInteger done = new AtomicInteger();
        final List<Thread> pool = new ArrayList<>();
        start = System.nanoTime();
        for (int t = 0; t < threads; t++) {
            final Thread th = new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < totalReads / threads; i++) {
                        engine.executeQuery(readSql);
                        done.incrementAndGet();
                    }
                }
            });
            pool.add(th);
            th.start();
        }
        for (final Thread th : pool) {
            th.join();
        }
        final long concMs = (System.nanoTime() - start) / 1_000_000L;
        log.info("PERF | concurrency.reads | serialMs={} concurrentMs={} threads={} speedup={}",
            serialMs, concMs, threads, String.format("%.2f", concMs == 0 ? 0.0 : (double) serialMs / concMs));
        assertEquals(totalReads, done.get());
    }

    // ---- 5. delete scaling ---------------------------------------------------

    @Test
    @Order(5)
    public void t5_deleteScaling() {
        final int n = 3000;
        createTable("del");
        loadRows("del", n, 500);
        final long start = System.nanoTime();
        // delete in chunks so each DELETE still rewrites/snapshots the table
        for (int i = 0; i < n; i += 100) {
            engine.execute("DELETE FROM del WHERE id >= " + i + " AND id < " + (i + 100));
        }
        final long ms = (System.nanoTime() - start) / 1_000_000L;
        log.info("PERF | delete.scaling | rows={} deletes={} totalMs={}", n, n / 100, ms);
        assertTrue(rowCount(engine.executeQuery("SELECT id FROM del")) == 0);
    }

    // ---- 6. WHERE clause: scaling + per-predicate cost ----------------------

    @Test
    @Order(6)
    public void t6_whereClause() {
        // Scaling: the materializing WHERE evaluates the predicate for EVERY row, so cost should be
        // O(rows). nsPerRow flat as rows grow = linear; rising = super-linear.
        for (final int n : new int[] { 10000, 20000, 40000, 80000 }) {
            createTable("w");
            loadRows("w", n, 500);
            final long ns = benchNanos("SELECT id FROM w WHERE n = 7");
            log.info("PERF | where.scaling | rows={} ms={} nsPerRow={}", n, ms(ns), ns / n);
        }

        // Per-predicate cost at fixed N=40000 — compare to the near-trivial baseline to isolate the
        // per-row expression-evaluation cost of arithmetic / function / LIKE predicates.
        createTable("w");
        loadRows("w", 40000, 500);
        log.info("PERF | where.baseline n>=0 | ms={}", ms(benchNanos("SELECT id FROM w WHERE n >= 0")));
        log.info("PERF | where.eq n=7 | ms={}", ms(benchNanos("SELECT id FROM w WHERE n = 7")));
        log.info("PERF | where.arith n*2+1>1000 | ms={}", ms(benchNanos("SELECT id FROM w WHERE n * 2 + 1 > 1000")));
        log.info("PERF | where.func ABS(n-500)<5 | ms={}", ms(benchNanos("SELECT id FROM w WHERE ABS(n - 500) < 5")));
        log.info("PERF | where.like payload | ms={}", ms(benchNanos("SELECT id FROM w WHERE payload LIKE 'row_1%'")));
    }

    // ---- 7. GROUP BY: scaling + cardinality + per-aggregate cost ------------

    @Test
    @Order(7)
    public void t7_groupByClause() {
        // Scaling at fixed (low) group count.
        for (final int n : new int[] { 10000, 20000, 40000, 80000 }) {
            createTable("g");
            loadRows("g", n, 500);
            final long ns = benchNanos("SELECT grp, COUNT(*) c FROM g GROUP BY grp");
            log.info("PERF | groupby.scaling | rows={} groups=10 ms={} nsPerRow={}", n, ms(ns), ns / n);
        }

        createTable("g");
        loadRows("g", 40000, 500);
        // Cardinality: same N=40000, vary the number of distinct group keys (10 / 1000 / all-distinct).
        log.info("PERF | groupby.card.10 | ms={}", ms(benchNanos("SELECT grp, COUNT(*) c FROM g GROUP BY grp")));
        log.info("PERF | groupby.card.1000 | ms={}", ms(benchNanos("SELECT n, COUNT(*) c FROM g GROUP BY n")));
        log.info("PERF | groupby.card.allDistinct | ms={}", ms(benchNanos("SELECT id, COUNT(*) c FROM g GROUP BY id")));
        log.info("PERF | groupby.multikey grp,n | ms={}", ms(benchNanos("SELECT grp, n, COUNT(*) c FROM g GROUP BY grp, n")));
        // Per-aggregate cost: fixed 10 groups, vary the aggregate.
        log.info("PERF | groupby.agg.count | ms={}", ms(benchNanos("SELECT grp, COUNT(*) FROM g GROUP BY grp")));
        log.info("PERF | groupby.agg.sum | ms={}", ms(benchNanos("SELECT grp, SUM(n) FROM g GROUP BY grp")));
        log.info("PERF | groupby.agg.avg | ms={}", ms(benchNanos("SELECT grp, AVG(n) FROM g GROUP BY grp")));
        log.info("PERF | groupby.agg.countDistinct | ms={}", ms(benchNanos("SELECT grp, COUNT(DISTINCT n) FROM g GROUP BY grp")));
    }

    /** Min of 3 timed runs (after a warm-up) — min is the most GC/scheduling-stable timing estimate. */
    private static long benchNanos(final String sql) {
        engine.executeQuery(sql);
        long best = Long.MAX_VALUE;
        for (int k = 0; k < 3; k++) {
            final long start = System.nanoTime();
            final ResultSet rs = engine.executeQuery(sql);
            best = Math.min(best, System.nanoTime() - start);
            assertTrue(rs.getRowCount() >= 0);
        }
        return best;
    }

    private static String ms(final long nanos) {
        return String.format("%.2f", nanos / 1_000_000.0);
    }

    // ---- 8. large-scale stats: 100k / 200k / 500k rows ----------------------

    @Test
    @Order(8)
    public void t8_largeScale() {
        // Time-travel OFF: multi-hundred-k loads are fast and query timings aren't skewed by snapshot GC.
        // (Loading at this scale is only feasible because the snapshot fix made writes O(N); with the
        //  old per-row snapshot, loading 100k+ was O(N^2).)
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_TIME_TRAVEL_ENABLED, "false");
        final DatabaseEngine e = new DatabaseEngine(cfg);
        e.execute("CREATE DATABASE big");
        e.execute("USE DATABASE big");
        e.execute("CREATE SCHEMA s");
        e.execute("USE SCHEMA s");
        e.execute("CREATE TABLE dim (id INTEGER, n INTEGER, grp VARCHAR, payload VARCHAR)");
        loadInto(e, "dim", 1000, 1000);

        for (final int n : new int[] { 100000, 200000, 500000 }) {
            e.execute("DROP TABLE IF EXISTS big");
            e.execute("CREATE TABLE big (id INTEGER, n INTEGER, grp VARCHAR, payload VARCHAR)");
            final long loadStart = System.nanoTime();
            loadInto(e, "big", n, 2000);
            final long loadMs = (System.nanoTime() - loadStart) / 1_000_000L;
            log.info("PERF | large.load | rows={} loadMs={} rowsPerSec={}",
                n, loadMs, loadMs == 0 ? 0 : n * 1000L / loadMs);
            log.info("PERF | large.scan | rows={} ms={}", n, ms(benchNanos(e, "SELECT id FROM big")));
            log.info("PERF | large.where.eq | rows={} ms={}", n, ms(benchNanos(e, "SELECT id FROM big WHERE n = 7")));
            log.info("PERF | large.where.like | rows={} ms={}", n, ms(benchNanos(e, "SELECT id FROM big WHERE payload LIKE 'row_1%'")));
            log.info("PERF | large.limit | rows={} ms={}", n, ms(benchNanos(e, "SELECT id FROM big WHERE n >= 0 LIMIT 10")));
            log.info("PERF | large.groupby.10 | rows={} ms={}", n, ms(benchNanos(e, "SELECT grp, COUNT(*) c FROM big GROUP BY grp")));
            log.info("PERF | large.groupby.1000 | rows={} ms={}", n, ms(benchNanos(e, "SELECT n, COUNT(*) c FROM big GROUP BY n")));
            log.info("PERF | large.groupby.highcard | rows={} ms={}", n, ms(benchNanos(e, "SELECT id, COUNT(*) c FROM big GROUP BY id")));
            log.info("PERF | large.groupby.sum | rows={} ms={}", n, ms(benchNanos(e, "SELECT grp, SUM(n) FROM big GROUP BY grp")));
            log.info("PERF | large.groupby.countDistinct | rows={} ms={}", n, ms(benchNanos(e, "SELECT grp, COUNT(DISTINCT n) FROM big GROUP BY grp")));
            log.info("PERF | large.distinct | rows={} ms={}", n, ms(benchNanos(e, "SELECT DISTINCT grp FROM big")));
            log.info("PERF | large.orderby | rows={} ms={}", n, ms(benchNanos(e, "SELECT id FROM big ORDER BY n, id")));
            log.info("PERF | large.join.hash | rows={} ms={}", n, ms(benchNanos(e, "SELECT b.id FROM big b JOIN dim d ON b.id = d.id")));
            log.info("PERF | large.subquery.uncorr | rows={} ms={}", n, ms(benchNanos(e, "SELECT id FROM big WHERE n IN (SELECT id FROM dim)")));
        }
        e.shutdown();
    }

    private static void loadInto(final DatabaseEngine e, final String table, final int n, final int batch) {
        int i = 0;
        while (i < n) {
            final int end = Math.min(i + batch, n);
            final StringBuilder sb = new StringBuilder("INSERT INTO ").append(table).append(" VALUES ");
            for (int j = i; j < end; j++) {
                if (j > i) {
                    sb.append(',');
                }
                sb.append('(').append(j).append(',').append(j % 1000)
                  .append(",'g").append(j % 10).append("','row_").append(j).append("')");
            }
            e.execute(sb.toString());
            i = end;
        }
    }

    private static long benchNanos(final DatabaseEngine e, final String sql) {
        e.executeQuery(sql);
        long best = Long.MAX_VALUE;
        for (int k = 0; k < 3; k++) {
            final long start = System.nanoTime();
            final ResultSet rs = e.executeQuery(sql);
            best = Math.min(best, System.nanoTime() - start);
            assertTrue(rs.getRowCount() >= 0);
        }
        return best;
    }
}
