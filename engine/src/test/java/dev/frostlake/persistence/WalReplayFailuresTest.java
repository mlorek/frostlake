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

package dev.frostlake.persistence;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recovery reports the logged transactions it could not re-apply. It rolled each one back and went on, as it still
 * does, but said so only in the log, so an engine that started without committed work looked like one holding all
 * of it. A strict start refuses instead, and leaves the log as it was for the next start.
 */
public class WalReplayFailuresTest {

    private Path dir;
    private Path walFile;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("wal_failures_");
        walFile = dir.resolve("wal.log");
    }

    @AfterEach
    public void tearDown() throws IOException {
        deleteRecursively(dir.toFile());
    }

    /** Remove a test's data directory whole: the engine keeps its stages in a directory inside it. */
    private static void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    private EngineConfig walConfig(final boolean strict) {
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_ENABLED, "true");
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_FILE, walFile.toString());
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_REPLAY_STRICT, String.valueOf(strict));
        return cfg;
    }

    /** A log holding a table and a row, then two transactions recovery cannot re-apply, then one it can. */
    private void writeLog() throws IOException {
        final DatabaseEngine first = new DatabaseEngine(walConfig(false));
        first.execute("CREATE DATABASE w");
        first.execute("CREATE TABLE w.PUBLIC.t (n INT)");
        first.execute("INSERT INTO w.PUBLIC.t VALUES (1)");
        first.shutdown();
        final WriteAheadLog log = new WriteAheadLog(walFile);
        try {
            log.appendTransaction(Collections.singletonList("INSERT INTO w.PUBLIC.missing VALUES (2)"));
            log.appendTransaction(Arrays.asList("INSERT INTO w.PUBLIC.t VALUES (3)",
                "INSERT INTO w.PUBLIC.nosuch VALUES (4)"));
            log.appendTransaction(Collections.singletonList("INSERT INTO w.PUBLIC.t VALUES (5)"));
        } finally {
            log.close();
        }
    }

    private static String values(final DatabaseEngine engine) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery("SELECT n FROM w.PUBLIC.t ORDER BY n").getRows()) {
            out.append(out.length() > 0 ? "," : "").append(row.getValue(0));
        }
        return out.toString();
    }

    @Test
    public void transactionsRecoveryCannotReapplyAreReportedAndTheRestRecover() throws IOException {
        writeLog();
        final DatabaseEngine engine = new DatabaseEngine(walConfig(false));
        try {
            assertEquals("1,5", values(engine), "each failed transaction is rolled back whole; the rest recover");
            final List<WalReplayFailure> failures = engine.getWalReplayFailures();
            assertEquals(2, failures.size(), String.valueOf(failures));
            assertEquals("INSERT INTO w.PUBLIC.missing VALUES (2)", failures.get(0).getStatement());
            assertEquals("INSERT INTO w.PUBLIC.nosuch VALUES (4)", failures.get(1).getStatement(),
                "the statement of the transaction that failed, not its first");
            assertEquals(failures.get(0).getRecordIndex() + 1, failures.get(1).getRecordIndex());
            assertTrue(failures.get(1).getMessage().contains("NOSUCH"), failures.get(1).getMessage());
        } finally {
            engine.shutdown();
        }
    }

    @Test
    public void aStrictStartRefusesAndLeavesTheLogForTheNextStart() throws IOException {
        writeLog();
        final long logged = Files.size(walFile);
        String refusal = null;
        try {
            new DatabaseEngine(walConfig(true)).shutdown();
        } catch (final IllegalStateException refused) {
            refusal = refused.getMessage();
        }
        assertTrue(refusal != null && refusal.contains("2 logged transaction(s)"), String.valueOf(refusal));
        assertEquals(logged, Files.size(walFile), "the refused start leaves the log as it was");

        final DatabaseEngine lenient = new DatabaseEngine(walConfig(false));
        try {
            assertEquals("1,5", values(lenient));
            assertEquals(2, lenient.getWalReplayFailures().size());
        } finally {
            lenient.shutdown();
        }
    }

    @Test
    public void aCleanRecoveryReportsNothing() throws IOException {
        final DatabaseEngine first = new DatabaseEngine(walConfig(true));
        first.execute("CREATE DATABASE w");
        first.execute("CREATE TABLE w.PUBLIC.t (n INT)");
        first.execute("INSERT INTO w.PUBLIC.t VALUES (1)");
        first.shutdown();
        final DatabaseEngine engine = new DatabaseEngine(walConfig(true));
        try {
            assertEquals("1", values(engine));
            assertTrue(engine.getWalReplayFailures().isEmpty());
        } finally {
            engine.shutdown();
        }
    }
}
