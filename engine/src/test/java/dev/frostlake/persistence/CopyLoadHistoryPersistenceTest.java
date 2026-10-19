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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The files a table has loaded go with a snapshot of the engine's state: after a checkpoint and a restart, and in a
 * clone, a COPY of the stage skips the file the table already loaded rather than loading it a second time.
 */
public class CopyLoadHistoryPersistenceTest {

    private Path dir;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("copy_load_history_");
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
    public void aLoadedFileStaysLoadedAcrossARestartAndInAClone() throws Exception {
        final Path csv = dir.resolve("f3.csv");
        Files.write(csv, "x,1\n".getBytes(StandardCharsets.UTF_8));
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        try {
            engine.execute("CREATE DATABASE d");
            engine.execute("USE SCHEMA d.PUBLIC");
            engine.execute("CREATE TABLE t (a VARCHAR, b INT)");
            engine.execute("CREATE STAGE st");
            engine.execute("PUT 'file://" + csv + "' @st AUTO_COMPRESS = FALSE");
            engine.execute("COPY INTO t FROM @st");
            engine.execute("COPY INTO t FROM @st");
            assertEquals("1", answer(engine, "SELECT COUNT(*) FROM t"));
            engine.checkpoint();
        } finally {
            engine.shutdown();
        }
        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            recovered.execute("COPY INTO d.PUBLIC.t FROM @d.PUBLIC.st");
            assertEquals("1", answer(recovered, "SELECT COUNT(*) FROM d.PUBLIC.t"));
            final DatabaseEngine clone = recovered.cloneInstance();
            try {
                clone.execute("COPY INTO d.PUBLIC.t FROM @d.PUBLIC.st");
                assertEquals("1", answer(clone, "SELECT COUNT(*) FROM d.PUBLIC.t"));
            } finally {
                clone.shutdown();
            }
        } finally {
            recovered.shutdown();
        }
    }
}
