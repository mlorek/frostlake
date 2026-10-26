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
 * A time or a timestamp variable a SET gave a precision before a checkpoint has that precision again when the
 * write-ahead log replays a statement that reads it: the SET is no longer in the log, and the log keeps the
 * precision beside the value, which cannot tell it, so a replayed CREATE TABLE AS builds the column at the
 * variable's precision and a replayed INSERT writes its SYSTEM$TYPEOF.
 */
public class WalTemporalVariableReplayTest {

    private static final String TYPES = "SELECT k, s FROM d.PUBLIC.t ORDER BY k";

    private static final String COLUMNS = "SELECT column_name, data_type, datetime_precision"
        + " FROM d.information_schema.columns WHERE table_name = 'C' ORDER BY ordinal_position";

    private Path dir;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("wal_temporal_variable_");
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
    public void aReplayedStatementReadsTheVariableAtItsPrecision() {
        final String types = "a, TIME(3)[SB4] | b, TIMESTAMP_NTZ(0)[SB8]";
        final String columns = "A, TIME, 3 | B, TIMESTAMP_NTZ, 0";
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        try {
            engine.execute("CREATE DATABASE d");
            engine.execute("USE SCHEMA d.PUBLIC");
            engine.execute("CREATE TABLE t (k VARCHAR, s VARCHAR)");
            engine.execute("SET (t3, n0) = ('10:00:00.123'::TIME(3), '2024-01-02 03:04:05'::TIMESTAMP_NTZ(0))");
            engine.checkpoint();
            engine.execute("INSERT INTO t SELECT 'a', SYSTEM$TYPEOF($t3)");
            engine.execute("INSERT INTO t SELECT 'b', SYSTEM$TYPEOF($n0)");
            engine.execute("CREATE TABLE c AS SELECT $t3 AS a, $n0 AS b");
            assertEquals(types, answer(engine, TYPES));
            assertEquals(columns, answer(engine, COLUMNS));
        } finally {
            engine.shutdown();
        }
        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertTrue(recovered.getWalReplayFailures().isEmpty(), String.valueOf(recovered.getWalReplayFailures()));
            assertEquals(types, answer(recovered, TYPES));
            assertEquals(columns, answer(recovered, COLUMNS));
        } finally {
            recovered.shutdown();
        }
    }
}
