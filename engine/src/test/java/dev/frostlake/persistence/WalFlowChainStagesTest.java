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
import org.junit.jupiter.api.function.Executable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each stage of a {@code ->>} chain is a statement of its own: a stage that completed keeps its work when a later
 * stage fails, committed as it ended under autocommit, or in the transaction it ran in. A failed chain is never
 * logged, so it owes a checkpoint, and a restart keeps what its earlier stages left; a chain that completes is
 * logged once, whole, and a restart runs it once.
 */
public class WalFlowChainStagesTest {

    private Path dir;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("wal_flow_chain_");
    }

    @AfterEach
    public void tearDown() {
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

    private EngineConfig walConfig() {
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_ENABLED, "true");
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_FILE, dir.resolve("wal.log").toString());
        return cfg;
    }

    /** A statement's answer on one line: rows as "a, b | c, d", or "REFUSED " and the refusal. */
    private static String answer(final DatabaseEngine engine, final String sql) {
        try {
            final StringBuilder rows = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                final StringBuilder cells = new StringBuilder();
                for (final Object value : row.getValues()) {
                    cells.append(cells.length() > 0 ? ", " : "").append(value);
                }
                rows.append(rows.length() > 0 ? " | " : "").append(cells);
            }
            return rows.toString();
        } catch (final RuntimeException refused) {
            return "REFUSED " + refused.getMessage();
        }
    }

    private static void fails(final DatabaseEngine engine, final String sql) {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql);
    }

    /** What the restarted engine answers for each check, its replay failing nowhere. */
    private String[] afterRestart(final String... checks) {
        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertTrue(recovered.getWalReplayFailures().isEmpty(), String.valueOf(recovered.getWalReplayFailures()));
            final String[] answers = new String[checks.length];
            for (int i = 0; i < checks.length; i++) {
                answers[i] = answer(recovered, checks[i]);
            }
            return answers;
        } finally {
            recovered.shutdown();
        }
    }

    @Test
    public void aFailedChainKeepsItsEarlierStagesAcrossARestart() {
        final String rows = "SELECT v FROM d.PUBLIC.t ORDER BY 1";
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        try {
            engine.execute("CREATE DATABASE d");
            engine.execute("USE SCHEMA d.PUBLIC");
            engine.execute("CREATE TABLE t (v INT)");
            fails(engine, "INSERT INTO t VALUES (1) ->> INSERT INTO t VALUES (11) ->> SELECT 1/0");
            fails(engine, "INSERT INTO t SELECT v + 100 FROM t WHERE v = 1 ->> SELECT * FROM $1 ->> SELECT 1/0");
            fails(engine, "SELECT 1/0 ->> INSERT INTO t VALUES (99)");
            assertEquals("1 | 11 | 101", answer(engine, rows));
        } finally {
            engine.shutdown();
        }
        assertEquals("1 | 11 | 101", afterRestart(rows)[0]);
    }

    @Test
    public void aCompletedChainRunsOnceAfterARestart() {
        final String rows = "SELECT v FROM d.PUBLIC.t ORDER BY 1";
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        try {
            engine.execute("CREATE DATABASE d");
            engine.execute("USE SCHEMA d.PUBLIC");
            engine.execute("CREATE TABLE t (v INT)");
            engine.execute("INSERT INTO t VALUES (2) ->> INSERT INTO t SELECT v + 1 FROM t WHERE v = 2");
            assertEquals("2 | 3", answer(engine, rows));
        } finally {
            engine.shutdown();
        }
        assertEquals("2 | 3", afterRestart(rows)[0]);
    }

    @Test
    public void aFailedChainInsideATransactionCommitsWithIt() {
        final String rows = "SELECT v FROM d.PUBLIC.t ORDER BY 1";
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        try {
            engine.execute("CREATE DATABASE d");
            engine.execute("USE SCHEMA d.PUBLIC");
            engine.execute("CREATE TABLE t (v INT)");
            engine.execute("BEGIN TRANSACTION");
            fails(engine, "INSERT INTO t VALUES (4) ->> INSERT INTO t VALUES (44) ->> SELECT 1/0");
            engine.execute("COMMIT");
            engine.execute("BEGIN TRANSACTION");
            fails(engine, "INSERT INTO t VALUES (5) ->> SELECT 1/0");
            engine.execute("ROLLBACK");
            assertEquals("4 | 44", answer(engine, rows));
        } finally {
            engine.shutdown();
        }
        assertEquals("4 | 44", afterRestart(rows)[0]);
    }

    @Test
    public void aSequenceAStageDrewNeverHandsTheValueOutAgain() {
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        try {
            engine.execute("CREATE DATABASE d");
            engine.execute("USE SCHEMA d.PUBLIC");
            engine.execute("CREATE TABLE t (v INT)");
            engine.execute("CREATE SEQUENCE s");
            fails(engine, "INSERT INTO t SELECT s.NEXTVAL ->> SELECT 1/0");
            assertEquals("1", answer(engine, "SELECT v FROM d.PUBLIC.t"));
        } finally {
            engine.shutdown();
        }
        final String[] after = afterRestart("SELECT v FROM d.PUBLIC.t", "SELECT d.PUBLIC.s.NEXTVAL > 1");
        assertEquals("1", after[0]);
        assertEquals("true", after[1]);
    }
}
