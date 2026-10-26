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
 * A session variable a SET gave a DECFLOAT before a checkpoint is a DECFLOAT again when the write-ahead log replays a
 * statement that reads it: the SET is no longer in the log, and the log names such variables beside their values, which
 * carry no type of their own, so a replayed INSERT writes the DECFLOAT's text and its DECFLOAT arithmetic, not a
 * NUMBER's.
 */
public class WalDecfloatVariableReplayTest {

    private static final String CHECK = "SELECT k, s, d::VARCHAR FROM d.PUBLIC.t ORDER BY k";

    private Path dir;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("wal_decfloat_variable_");
    }

    @AfterEach
    public void tearDown() {
        deleteRecursively(dir.toFile());
    }

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

    /** The check's rows, cells joined by a comma and rows by a bar. */
    private static String answer(final DatabaseEngine engine, final String sql) {
        final StringBuilder rows = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            final StringBuilder cells = new StringBuilder();
            for (final Object value : row.getValues()) {
                cells.append(cells.length() > 0 ? ", " : "").append(value);
            }
            rows.append(rows.length() > 0 ? " | " : "").append(cells);
        }
        return rows.toString();
    }

    @Test
    public void aReplayedStatementReadsTheVariableAsADecfloat() {
        final String expected = "a, 3.3333333333333333333333333333333333333e-1, null"
            + " | b, null, 9.9999999999999999999999999999999999999e-1";
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        try {
            engine.execute("CREATE DATABASE d");
            engine.execute("USE SCHEMA d.PUBLIC");
            engine.execute("CREATE TABLE t (k VARCHAR, s VARCHAR, d DECFLOAT)");
            engine.execute("SET w = (SELECT 1::DECFLOAT / 3)");
            engine.checkpoint();
            engine.execute("INSERT INTO t (k, s) SELECT 'a', $w::VARCHAR");
            engine.execute("INSERT INTO t (k, d) SELECT 'b', $w * 3");
            assertEquals(expected, answer(engine, CHECK));
        } finally {
            engine.shutdown();
        }
        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertTrue(recovered.getWalReplayFailures().isEmpty(), String.valueOf(recovered.getWalReplayFailures()));
            assertEquals(expected, answer(recovered, CHECK));
        } finally {
            recovered.shutdown();
        }
    }
}
