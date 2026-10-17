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
 * A temporary table ends with the session that created it, so none survives a restart: neither one the log's replay
 * created again nor one a checkpoint's snapshot held. What a statement copied out of it into a permanent table stays,
 * and a session creates a temporary table of the name afresh.
 */
public class WalReplayTemporaryTest {

    private Path dir;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("wal_temporary_");
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

    @Test
    public void aTemporaryTableDoesNotOutliveARestart() {
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        try {
            engine.execute("CREATE DATABASE d");
            engine.execute("USE SCHEMA d.PUBLIC");
            engine.execute("CREATE TABLE perm (v INT)");
            engine.execute("CREATE TEMPORARY TABLE tmp (v INT)");
            engine.execute("INSERT INTO tmp VALUES (10)");
            engine.execute("INSERT INTO perm SELECT v FROM tmp");
            engine.execute("CREATE TEMPORARY TABLE kept (v INT)");
            engine.execute("INSERT INTO kept VALUES (20)");
            engine.checkpoint();
        } finally {
            engine.shutdown();
        }
        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertEquals("", answer(recovered, "SHOW TABLES LIKE 'TMP' IN d.PUBLIC"));
            assertEquals("", answer(recovered, "SHOW TABLES LIKE 'KEPT' IN d.PUBLIC"));
            assertTrue(answer(recovered, "SELECT * FROM d.PUBLIC.tmp").startsWith("REFUSED"));
            assertEquals("10", answer(recovered, "SELECT v FROM d.PUBLIC.perm"));
            recovered.execute("CREATE TEMPORARY TABLE d.PUBLIC.tmp (v INT)");
            assertEquals("0", answer(recovered, "SELECT COUNT(*) FROM d.PUBLIC.tmp"));
        } finally {
            recovered.shutdown();
        }
    }
}
