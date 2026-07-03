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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Head-to-head performance + memory footprint against five embedded in-memory databases — Apache Derby
 * ({@code jdbc:derby:memory:}), H2 ({@code jdbc:h2:mem:}) and HSQLDB ({@code jdbc:hsqldb:mem:}), all pure
 * Java, plus SQLite ({@code jdbc:sqlite::memory:}) and DuckDB ({@code jdbc:duckdb:}), embedded native
 * engines reached over JDBC. The workload runs over an identical {@value #COLS}-column table loaded with
 * {@value #N} rows and NO indexes on any side: a fair table-scan comparison. Run on demand:
 * {@code mvn test -Dtest=EmbeddedDbComparisonTest} (add {@code -Pperf-mem} for a fixed 12 GB heap;
 * excluded from the default suite); grep the test log for "PERF | cmp.".
 *
 * <p>Each engine is loaded, measured, queried and disposed <em>one at a time</em> — only one dataset is
 * resident at any moment — so each heap delta is attributable to that engine and peak memory stays
 * bounded. Memory: the four JVM-resident engines (this one, Derby, H2, HSQLDB) are measured by heap delta
 * around the load (GC'd {@code totalMemory - freeMemory}); SQLite and DuckDB keep their data in
 * <em>native, off-heap</em> memory the JVM heap cannot see, so they are reported via an engine query
 * (SQLite {@code PRAGMA page_count * page_size}; DuckDB {@code duckdb_memory()}) and their heap delta
 * reflects only JDBC/driver overhead.
 *
 * <p>Caveat: apples-to-oranges by design. The five are full ACID engines (Derby/H2/HSQLDB row stores,
 * DuckDB a columnar/vectorized analytical engine) even in memory; this engine is a bare in-memory store
 * with no durability, optimizer, or indexes. Query timings include JDBC result marshaling (SQLite and
 * DuckDB additionally cross JNI per cell). DuckDB is built for bulk/columnar ingestion, so its row-by-row
 * JDBC load is a worst case, not its intended path. Order-of-magnitude positioning, not a ranking.
 * Single JVM, min-of-3.
 */
@Tag("perf")
public class EmbeddedDbComparisonTest {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedDbComparisonTest.class);

    private static final int N = 500_000;
    private static final int DIM = 1000;
    private static final int COLS = 12;

    private static final int OURS = 0;
    private static final int DERBY = 1;
    private static final int H2 = 2;
    private static final int SQLITE = 3;
    private static final int HSQLDB = 4;
    private static final int DUCKDB = 5;
    private static final int ENGINES = 6;

    /**
     * One row per benchmarked operation: {label, LIMIT-dialect SQL (this engine + SQLite + DuckDB),
     * FETCH-FIRST SQL (Derby + H2 + HSQLDB)}. The two SQL variants differ only in the row-limit clause.
     */
    private static final String[][] OPS = {
        { "scan",          "SELECT id FROM big",                                  "SELECT id FROM big" },
        { "scan.wide",     "SELECT * FROM big",                                   "SELECT * FROM big" },
        { "where.eq",      "SELECT id FROM big WHERE n = 7",                      "SELECT id FROM big WHERE n = 7" },
        { "where.like",    "SELECT id FROM big WHERE payload LIKE 'row_1%'",      "SELECT id FROM big WHERE payload LIKE 'row_1%'" },
        { "limit10",       "SELECT id FROM big WHERE n >= 0 LIMIT 10",            "SELECT id FROM big WHERE n >= 0 FETCH FIRST 10 ROWS ONLY" },
        { "groupby.10",    "SELECT grp, COUNT(*) c FROM big GROUP BY grp",        "SELECT grp, COUNT(*) c FROM big GROUP BY grp" },
        { "groupby.1000",  "SELECT n, COUNT(*) c FROM big GROUP BY n",            "SELECT n, COUNT(*) c FROM big GROUP BY n" },
        { "distinct",      "SELECT DISTINCT grp FROM big",                        "SELECT DISTINCT grp FROM big" },
        { "orderby",       "SELECT id FROM big ORDER BY n, id",                   "SELECT id FROM big ORDER BY n, id" },
        { "sum",           "SELECT SUM(amount) s FROM big",                       "SELECT SUM(amount) s FROM big" },
        { "countDistinct", "SELECT grp, COUNT(DISTINCT n) FROM big GROUP BY grp", "SELECT grp, COUNT(DISTINCT n) FROM big GROUP BY grp" },
        { "join",          "SELECT b.id FROM big b JOIN dim d ON b.id = d.id",    "SELECT b.id FROM big b JOIN dim d ON b.id = d.id" },
    };

    private long sqliteNativeBytes;
    private long duckdbNativeBytes;

    @Test
    public void compare() throws SQLException {
        final Map<String, long[]> results = new LinkedHashMap<String, long[]>();
        final long[] mem = new long[ENGINES];
        measureOurs(results, mem);
        measureJdbc(results, mem, DERBY, "jdbc:derby:memory:bench;create=true");
        measureJdbc(results, mem, H2, "jdbc:h2:mem:bench;DB_CLOSE_DELAY=-1;QUERY_CACHE_SIZE=0");
        measureJdbc(results, mem, HSQLDB, "jdbc:hsqldb:mem:bench");
        measureJdbc(results, mem, SQLITE, "jdbc:sqlite::memory:");
        measureJdbc(results, mem, DUCKDB, "jdbc:duckdb:");
        report(results, mem);
    }

    // ---- this engine --------------------------------------------------------

    private void measureOurs(final Map<String, long[]> results, final long[] mem) {
        final long base = usedHeapBytes();
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_TIME_TRAVEL_ENABLED, "false");
        final DatabaseEngine e = new DatabaseEngine(cfg);
        try {
            e.execute("CREATE DATABASE db");
            e.execute("USE DATABASE db");
            e.execute("CREATE SCHEMA s");
            e.execute("USE SCHEMA s");
            e.execute(oursCreate("big"));
            e.execute(oursCreate("dim"));
            loadOurs(e, "dim", DIM);
            final long t0 = System.nanoTime();
            loadOurs(e, "big", N);
            recordTime(results, "load", OURS, System.nanoTime() - t0);
            // Parse cache is bounded (BoundedParseCache), so the bulk-INSERT ASTs no longer accumulate —
            // this is the loaded-data footprint, measured directly with no cache-eviction workaround.
            mem[OURS] = usedHeapBytes() - base;
            for (final String[] op : OPS) {
                recordTime(results, op[0], OURS, ourNanos(e, op[1]));
            }
        } finally {
            e.shutdown();
        }
    }

    private static String oursCreate(final String table) {
        return "CREATE TABLE " + table + " (id INTEGER, n INTEGER, grp VARCHAR, payload VARCHAR, "
            + "c5 INTEGER, c6 INTEGER, amount DOUBLE, score DOUBLE, big_n BIGINT, "
            + "code VARCHAR, label VARCHAR, descr VARCHAR)";
    }

    private static void loadOurs(final DatabaseEngine e, final String table, final int n) {
        int i = 0;
        while (i < n) {
            final int end = Math.min(i + 2000, n);
            final StringBuilder sb = new StringBuilder("INSERT INTO ").append(table).append(" VALUES ");
            for (int j = i; j < end; j++) {
                if (j > i) {
                    sb.append(',');
                }
                sb.append('(').append(j).append(',').append(j % 1000)
                  .append(",'g").append(j % 10).append("','row_").append(j).append("',")
                  .append(j % 100).append(',').append(j % 7).append(',')
                  .append(j * 1.5).append(',').append((j % 1000) * 0.25).append(',')
                  .append((long) j * 1000L).append(",'c_").append(j % 50)
                  .append("','lbl_").append(j % 200).append("','desc_").append(j).append("')");
            }
            e.execute(sb.toString());
            i = end;
        }
    }

    private static long ourNanos(final DatabaseEngine e, final String sql) {
        drainOurs(e, sql);
        long best = Long.MAX_VALUE;
        for (int k = 0; k < 3; k++) {
            final long start = System.nanoTime();
            final long sink = drainOurs(e, sql);
            best = Math.min(best, System.nanoTime() - start);
            if (sink < 0) {
                throw new IllegalStateException("unexpected");
            }
        }
        return best;
    }

    /** Read every column of every row (the in-process analogue of {@link #drain}) so wide scans pay a comparable cost. */
    private static long drainOurs(final DatabaseEngine e, final String sql) {
        final dev.frostlake.storage.ResultSet rs = e.executeQuery(sql);
        final List<Row> rows = rs.getRows();
        final int cols = rs.getColumnCount();
        long sink = 0;
        for (int r = 0; r < rows.size(); r++) {
            final Row row = rows.get(r);
            for (int c = 0; c < cols; c++) {
                if (row.getValue(c) != null) {
                    sink++;
                }
            }
        }
        return sink;
    }

    // ---- JDBC engines (Derby / H2 / HSQLDB / SQLite / DuckDB) ----------------

    private void measureJdbc(final Map<String, long[]> results, final long[] mem, final int idx, final String url)
            throws SQLException {
        final long base = usedHeapBytes();
        final Connection c = DriverManager.getConnection(url);
        try {
            createJdbcTables(c);
            loadJdbc(c, "dim", DIM);
            final long t0 = System.nanoTime();
            loadJdbc(c, "big", N);
            recordTime(results, "load", idx, System.nanoTime() - t0);
            mem[idx] = usedHeapBytes() - base;
            final boolean limitDialect = (idx == SQLITE || idx == DUCKDB);
            for (final String[] op : OPS) {
                recordTime(results, op[0], idx, jdbcNanos(c, limitDialect ? op[1] : op[2]));
            }
            if (idx == SQLITE) {
                sqliteNativeBytes = pragmaLong(c, "PRAGMA page_count") * pragmaLong(c, "PRAGMA page_size");
            } else if (idx == DUCKDB) {
                duckdbNativeBytes = duckdbMemoryBytes(c);
            }
        } finally {
            disposeJdbc(idx, c);
        }
    }

    private static void createJdbcTables(final Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(jdbcCreate("big"));
            st.execute(jdbcCreate("dim"));
        }
    }

    private static String jdbcCreate(final String table) {
        return "CREATE TABLE " + table + " (id INTEGER, n INTEGER, grp VARCHAR(8), payload VARCHAR(32), "
            + "c5 INTEGER, c6 INTEGER, amount DOUBLE, score DOUBLE, big_n BIGINT, "
            + "code VARCHAR(16), label VARCHAR(24), descr VARCHAR(48))";
    }

    private static void loadJdbc(final Connection conn, final String table, final int n) throws SQLException {
        conn.setAutoCommit(false);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO " + table + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) {
            int pending = 0;
            for (int i = 0; i < n; i++) {
                ps.setInt(1, i);
                ps.setInt(2, i % 1000);
                ps.setString(3, "g" + (i % 10));
                ps.setString(4, "row_" + i);
                ps.setInt(5, i % 100);
                ps.setInt(6, i % 7);
                ps.setDouble(7, i * 1.5);
                ps.setDouble(8, (i % 1000) * 0.25);
                ps.setLong(9, (long) i * 1000L);
                ps.setString(10, "c_" + (i % 50));
                ps.setString(11, "lbl_" + (i % 200));
                ps.setString(12, "desc_" + i);
                ps.addBatch();
                if (++pending == 1000) {
                    ps.executeBatch();
                    pending = 0;
                }
            }
            if (pending > 0) {
                ps.executeBatch();   // HSQLDB throws on executeBatch with no pending rows, so guard it
            }
        }
        conn.commit();
        conn.setAutoCommit(true);
    }

    private long jdbcNanos(final Connection conn, final String sql) throws SQLException {
        drain(conn, sql);
        long best = Long.MAX_VALUE;
        for (int k = 0; k < 3; k++) {
            final long start = System.nanoTime();
            drain(conn, sql);
            best = Math.min(best, System.nanoTime() - start);
        }
        return best;
    }

    /** Execute + fully materialize a JDBC result (read every column of every row), like our ResultSet. */
    private long drain(final Connection conn, final String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            final ResultSetMetaData md = rs.getMetaData();
            final int cols = md.getColumnCount();
            long rows = 0;
            while (rs.next()) {
                for (int i = 1; i <= cols; i++) {
                    rs.getObject(i);
                }
                rows++;
            }
            return rows;
        }
    }

    private static long pragmaLong(final Connection conn, final String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** DuckDB keeps its data off-heap; total native memory via duckdb_memory(). Returns -1 if unavailable. */
    private static long duckdbMemoryBytes(final Connection conn) {
        try {
            return pragmaLong(conn, "SELECT COALESCE(SUM(memory_usage_bytes), 0) FROM duckdb_memory()");
        } catch (final SQLException e) {
            return -1L;
        }
    }

    private static void disposeJdbc(final int idx, final Connection conn) {
        if (idx == H2 || idx == HSQLDB) {
            try (Statement st = conn.createStatement()) {
                st.execute("SHUTDOWN");
            } catch (final SQLException ignored) {
                // best-effort shutdown of the in-memory H2 / HSQLDB database
            }
        }
        try {
            conn.close();
        } catch (final SQLException ignored) {
            // best-effort close
        }
        if (idx == DERBY) {
            try {
                DriverManager.getConnection("jdbc:derby:memory:bench;drop=true");
            } catch (final SQLException expected) {
                // Derby signals a successful drop by throwing — ignore.
            }
        }
    }

    // ---- reporting + helpers ------------------------------------------------

    private void report(final Map<String, long[]> results, final long[] mem) {
        log.info("PERF | cmp.maxHeapMB | {}", Runtime.getRuntime().maxMemory() / (1024 * 1024));
        for (final Map.Entry<String, long[]> e : results.entrySet()) {
            final long[] v = e.getValue();
            log.info("PERF | cmp.{} | ours={} derby={} h2={} hsqldb={} sqlite={} duckdb={} (ms)",
                e.getKey(), ms(v[OURS]), ms(v[DERBY]), ms(v[H2]), ms(v[HSQLDB]), ms(v[SQLITE]), ms(v[DUCKDB]));
        }
        final String duckMb = duckdbNativeBytes < 0 ? "n/a" : mb(duckdbNativeBytes);
        final String duckPerRow = duckdbNativeBytes < 0 ? "n/a" : String.valueOf(duckdbNativeBytes / N);
        log.info("PERF | cmp.memory | rows={} cols={} jvmHeapMB[ours={} derby={} h2={} hsqldb={}] nativeOffHeapMB[sqlite={} duckdb={}]",
            N, COLS, mb(mem[OURS]), mb(mem[DERBY]), mb(mem[H2]), mb(mem[HSQLDB]), mb(sqliteNativeBytes), duckMb);
        log.info("PERF | cmp.bytesPerRow | ours={} derby={} h2={} hsqldb={} sqlite-native={} duckdb-native={}",
            mem[OURS] / N, mem[DERBY] / N, mem[H2] / N, mem[HSQLDB] / N, sqliteNativeBytes / N, duckPerRow);
    }

    private static void recordTime(final Map<String, long[]> results, final String op, final int idx, final long nanos) {
        long[] slot = results.get(op);
        if (slot == null) {
            slot = new long[ENGINES];
            results.put(op, slot);
        }
        slot[idx] = nanos;
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

    private static String ms(final long nanos) {
        return String.format("%.2f", nanos / 1_000_000.0);
    }

    private static String mb(final long bytes) {
        return String.format("%.1f", bytes / (1024.0 * 1024.0));
    }
}
