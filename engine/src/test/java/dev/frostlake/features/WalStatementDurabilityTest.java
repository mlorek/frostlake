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

package dev.frostlake.features;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the write-ahead log carries, and what a restart therefore keeps.
 *
 * <p>The decision is made from the parse tree rather than the SQL text, and the case that forced
 * that is here: {@code BEGIN} opens both a transaction and a procedural block, so a text rule that
 * skipped statements starting with "BEGIN" dropped the DML inside an anonymous {@code BEGIN … END}
 * block — the rows were there until the engine restarted and then silently gone.
 */
public class WalStatementDurabilityTest {

    private Path dataDir;
    private EngineConfig config;

    @BeforeEach
    public void setUp() throws IOException {
        dataDir = Files.createTempDirectory("wal_statements_");
        final Properties overrides = new Properties();
        overrides.setProperty(EngineConfig.PROP_PERSISTENCE_ENABLED, "false");
        overrides.setProperty(EngineConfig.PROP_DURABILITY_WAL_ENABLED, "true");
        overrides.setProperty(EngineConfig.PROP_DURABILITY_WAL_FILE,
            dataDir.toString() + File.separator + "wal.log");
        config = new EngineConfig(overrides);
    }

    @AfterEach
    public void tearDown() {
        deleteRecursively(dataDir.toFile());
    }

    private void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    /** A fresh engine over the same WAL — its constructor replays whatever the last one committed. */
    private DatabaseEngine engine() {
        final DatabaseEngine engine = new DatabaseEngine(config);
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE SCHEMA IF NOT EXISTS test_schema");
        engine.execute("USE SCHEMA test_schema");
        return engine;
    }

    private String text(final DatabaseEngine engine, final String sql) {
        final Object value = engine.executeQuery(sql).getRows().get(0).getValue(0);
        return value == null ? null : value.toString();
    }

    private long count(final DatabaseEngine engine, final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return ((Number) rs.getRows().get(0).getValue(0)).longValue();
    }

    // ── the bug this rule exists for ──────────────────────────────────────────────

    /** DML inside an anonymous block survives a restart; reading the leading BEGIN lost it. */
    @Test
    public void dmlInsideAnAnonymousBlockSurvivesARestart() {
        final DatabaseEngine first = engine();
        first.execute("CREATE OR REPLACE TABLE t(id INT)");
        first.execute("INSERT INTO t VALUES (1)");
        first.execute("BEGIN INSERT INTO t VALUES (2); END;");
        assertEquals(2L, count(first, "SELECT COUNT(*) FROM t"));
        first.shutdown();

        final DatabaseEngine second = engine();
        assertEquals(2L, count(second, "SELECT COUNT(*) FROM t"),
            "the anonymous block's INSERT must be in the log");
        assertEquals(1L, count(second, "SELECT COUNT(*) FROM t WHERE id = 2"));
        second.shutdown();
    }

    /** A block that both writes and reads is still logged whole. */
    @Test
    public void aBlockMixingReadsAndWritesIsLogged() {
        final DatabaseEngine first = engine();
        first.execute("CREATE OR REPLACE TABLE t(id INT)");
        first.execute("BEGIN INSERT INTO t VALUES (7); SELECT COUNT(*) FROM t; END;");
        first.shutdown();

        final DatabaseEngine second = engine();
        assertEquals(1L, count(second, "SELECT COUNT(*) FROM t WHERE id = 7"));
        second.shutdown();
    }

    // ── the classification itself, family by family ───────────────────────────────

    @Test
    public void readsAndTransactionControlAreNotLogged() {
        final DatabaseEngine engine = engine();
        engine.execute("CREATE OR REPLACE TABLE t(id INT)");
        for (final String sql : new String[] {
                "SELECT 1",
                "WITH c AS (SELECT 1 AS x) SELECT x FROM c",
                "SELECT * FROM t",
                "SHOW TABLES",
                "DESCRIBE TABLE t",
                "DESC TABLE t",
                "EXPLAIN SELECT * FROM t" }) {
            assertFalse(engine.isDurableStatementForTesting(sql), sql + " should stay out of the log");
        }
        engine.shutdown();
    }

    @Test
    public void mutatingFamiliesAreLogged() {
        final DatabaseEngine engine = engine();
        engine.execute("CREATE OR REPLACE TABLE t(id INT)");
        for (final String sql : new String[] {
                "CREATE TABLE t2(id INT)",
                "ALTER TABLE t ADD COLUMN v VARCHAR",
                "DROP TABLE t2",
                "TRUNCATE TABLE t",
                "INSERT INTO t VALUES (1)",
                "UPDATE t SET id = 2",
                "DELETE FROM t",
                "USE SCHEMA test_schema",
                "CREATE ROLE r1",
                "BEGIN INSERT INTO t VALUES (3); END;" }) {
            assertTrue(engine.isDurableStatementForTesting(sql), sql + " must be logged");
        }
        engine.shutdown();
    }

    /**
     * The rule denies rather than permits: a statement the classifier cannot place is logged. A
     * redundant record costs space, a missing one costs committed data.
     */
    @Test
    public void anUnparseableStatementIsTreatedAsDurable() {
        final DatabaseEngine engine = engine();
        assertTrue(engine.isDurableStatementForTesting("NOT ACTUALLY SQL AT ALL"));
        engine.shutdown();
    }

    // ── the surrounding durability guarantees still hold ──────────────────────────

    @Test
    public void plainDmlSurvivesARestart() {
        final DatabaseEngine first = engine();
        first.execute("CREATE OR REPLACE TABLE t(id INT)");
        first.execute("INSERT INTO t VALUES (1), (2), (3)");
        first.shutdown();

        final DatabaseEngine second = engine();
        assertEquals(3L, count(second, "SELECT COUNT(*) FROM t"));
        second.shutdown();
    }

    @Test
    public void anExplicitTransactionSurvivesAsOneRecord() {
        final DatabaseEngine first = engine();
        first.execute("CREATE OR REPLACE TABLE t(id INT)");
        first.execute("BEGIN");
        first.execute("INSERT INTO t VALUES (1)");
        first.execute("INSERT INTO t VALUES (2)");
        first.execute("COMMIT");
        first.shutdown();

        final DatabaseEngine second = engine();
        assertEquals(2L, count(second, "SELECT COUNT(*) FROM t"));
        second.shutdown();
    }

    /** A rolled-back transaction leaves nothing behind for the log to replay. */
    @Test
    public void aRolledBackTransactionIsNotReplayed() {
        final DatabaseEngine first = engine();
        first.execute("CREATE OR REPLACE TABLE t(id INT)");
        first.execute("INSERT INTO t VALUES (1)");
        first.execute("BEGIN");
        first.execute("INSERT INTO t VALUES (2)");
        first.execute("ROLLBACK");
        first.shutdown();

        final DatabaseEngine second = engine();
        assertEquals(1L, count(second, "SELECT COUNT(*) FROM t"));
        second.shutdown();
    }

    // ── what a replayed statement calls "now" ─────────────────────────────────────

    /**
     * Replay re-executes SQL, so a per-call clock made CURRENT_TIMESTAMP resolve to the RECOVERY time
     * and a stored timestamp silently changed on restart. The record carries the statement's instant
     * and replay puts it back, so the value survives exactly.
     */
    @Test
    public void aStoredTimestampSurvivesReplayUnchanged() throws InterruptedException {
        final DatabaseEngine first = engine();
        first.execute("CREATE OR REPLACE TABLE t(ts TIMESTAMP_NTZ)");
        first.execute("INSERT INTO t SELECT CURRENT_TIMESTAMP()");
        final String written = text(first, "SELECT ts FROM t");
        first.shutdown();

        Thread.sleep(50);   // a per-call clock would resolve to a visibly later instant

        final DatabaseEngine second = engine();
        assertEquals(written, text(second, "SELECT ts FROM t"),
            "the replayed row must keep the instant it was written with");
        second.shutdown();
    }

    /** Every row of a multi-row insert keeps the one instant across a restart. */
    @Test
    public void aMultiRowInsertKeepsItsSingleInstantAcrossARestart() {
        final DatabaseEngine first = engine();
        first.execute("CREATE OR REPLACE TABLE t(id INT, ts TIMESTAMP_NTZ)");
        first.execute("INSERT INTO t SELECT 1, CURRENT_TIMESTAMP() FROM TABLE(GENERATOR(ROWCOUNT => 4))");
        final String written = text(first, "SELECT MIN(ts) FROM t");
        assertEquals(1L, count(first, "SELECT COUNT(DISTINCT ts) FROM t"));
        first.shutdown();

        final DatabaseEngine second = engine();
        assertEquals(1L, count(second, "SELECT COUNT(DISTINCT ts) FROM t"));
        assertEquals(written, text(second, "SELECT MIN(ts) FROM t"));
        second.shutdown();
    }

    /** Statements of one explicit transaction keep their own instants through replay. */
    @Test
    public void eachStatementOfATransactionKeepsItsOwnInstant() {
        final DatabaseEngine first = engine();
        first.execute("CREATE OR REPLACE TABLE t(n INT, ts TIMESTAMP_NTZ)");
        first.execute("BEGIN");
        first.execute("INSERT INTO t SELECT 1, CURRENT_TIMESTAMP()");
        first.execute("INSERT INTO t SELECT 2, CURRENT_TIMESTAMP()");
        first.execute("COMMIT");
        final String one = text(first, "SELECT ts FROM t WHERE n = 1");
        final String two = text(first, "SELECT ts FROM t WHERE n = 2");
        first.shutdown();

        final DatabaseEngine second = engine();
        assertEquals(one, text(second, "SELECT ts FROM t WHERE n = 1"));
        assertEquals(two, text(second, "SELECT ts FROM t WHERE n = 2"));
        second.shutdown();
    }

    /** DDL is logged too, so a table created in one run exists in the next. */
    @Test
    public void ddlSurvivesARestart() {
        final DatabaseEngine first = engine();
        first.execute("CREATE OR REPLACE TABLE persisted(id INT, v VARCHAR)");
        first.shutdown();

        final DatabaseEngine second = engine();
        assertEquals(0L, count(second, "SELECT COUNT(*) FROM persisted"));
        second.shutdown();
    }
}
