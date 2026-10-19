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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A logged write that reads a table at a STATEMENT point keeps the instant the point named in its record, so its replay
 * reads the table there even when the restarted engine knows no statement of that ID: a SELECT's, which the log never
 * holds, or one from before a checkpoint, which the checkpoint truncated away.
 */
public class WalReplayUnloggedPointTest {

    private Path dir;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("wal_unlogged_point_");
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

    /** What the restarted engine answers for the check, its replay failing nowhere. */
    private String afterRestart(final String check) {
        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertTrue(recovered.getWalReplayFailures().isEmpty(), String.valueOf(recovered.getWalReplayFailures()));
            return answer(recovered, check);
        } finally {
            recovered.shutdown();
        }
    }

    @Test
    public void aSelectsIdAsThePoint() {
        final String check = "SELECT (SELECT COUNT(*) FROM d.PUBLIC.t5), (SELECT COUNT(*) FROM d.PUBLIC.t6)";
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        final String before;
        try {
            engine.execute("CREATE DATABASE d");
            engine.execute("USE SCHEMA d.PUBLIC");
            engine.execute("CREATE TABLE t (a INT)");
            engine.execute("CREATE TABLE t6 (a INT)");
            engine.execute("INSERT INTO t VALUES (1)");
            engine.execute("SELECT COUNT(*) FROM t");
            final String read = answer(engine, "SELECT LAST_QUERY_ID()");
            engine.execute("INSERT INTO t VALUES (2)");
            engine.execute("CREATE TABLE t5 AS SELECT * FROM t AT(STATEMENT => '" + read + "')");
            engine.execute("INSERT INTO t6 SELECT * FROM t BEFORE(STATEMENT => '" + read + "')");
            before = answer(engine, check);
        } finally {
            engine.shutdown();
        }
        assertEquals("1, 1", before);
        assertEquals(before, afterRestart(check));
    }

    /**
     * Statements that run inside one millisecond are told apart by the order their snapshots were taken in, which
     * the replay keeps: the point's place is mapped through the last logged write before it.
     */
    @Test
    public void writesInsideOneMillisecond() {
        final String check = "SELECT (SELECT COUNT(*) FROM d.PUBLIC.t5), (SELECT COUNT(*) FROM d.PUBLIC.t6)";
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        final String before;
        try {
            engine.execute("CREATE DATABASE d");
            engine.execute("USE SCHEMA d.PUBLIC");
            engine.execute("CREATE TABLE t (a INT)");
            engine.execute("CREATE TABLE t6 (a INT)");
            String read = null;
            for (int i = 1; i <= 20; i++) {
                engine.execute("INSERT INTO t VALUES (" + i + ")");
                if (i == 10) {
                    engine.execute("SELECT MAX(a) FROM t");
                    read = answer(engine, "SELECT LAST_QUERY_ID()");
                }
            }
            engine.execute("CREATE TABLE t5 AS SELECT * FROM t AT(STATEMENT => '" + read + "')");
            engine.execute("INSERT INTO t6 SELECT * FROM t BEFORE(STATEMENT => '" + read + "')");
            before = answer(engine, check);
        } finally {
            engine.shutdown();
        }
        assertEquals("10, 10", before);
        assertEquals(before, afterRestart(check));
    }

    @Test
    public void anIdFromBeforeACheckpoint() {
        final String check = "SELECT (SELECT COUNT(*) FROM d.PUBLIC.t5), (SELECT COUNT(*) FROM d.PUBLIC.t6), "
            + "(SELECT COUNT(*) FROM d.PUBLIC.t7), (SELECT COUNT(*) FROM d.PUBLIC.t)";
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        final String before;
        try {
            engine.execute("CREATE DATABASE d");
            engine.execute("USE SCHEMA d.PUBLIC");
            engine.execute("CREATE TABLE t (a INT)");
            engine.execute("CREATE TABLE t6 (a INT)");
            engine.execute("INSERT INTO t VALUES (1)");
            final String inserted = answer(engine, "SELECT LAST_QUERY_ID()");
            engine.execute("INSERT INTO t VALUES (2)");
            engine.checkpoint();
            engine.execute("CREATE TABLE t5 AS SELECT * FROM t AT(STATEMENT => '" + inserted + "')");
            engine.execute("INSERT INTO t6 SELECT * FROM t BEFORE(STATEMENT => '" + inserted + "')");
            engine.execute("CREATE TABLE t7 CLONE t AT(STATEMENT => '" + inserted + "')");
            before = answer(engine, check);
        } finally {
            engine.shutdown();
        }
        assertEquals("1, 0, 1, 2", before);
        assertEquals(before, afterRestart(check));
    }
}
