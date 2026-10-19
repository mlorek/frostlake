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

/**
 * A dynamic table's refreshes, which a read AT or BEFORE a point reaches, are kept with a snapshot of the engine's
 * state: after a checkpoint and a restart a point between two refreshes still reads the rows the earlier one left.
 */
public class DynamicTableHistoryRestartTest {

    private Path dir;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("dt_history_");
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

    /** The rows a query answers on one line, as "a | b". */
    private static String answer(final DatabaseEngine engine, final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? " | " : "").append(row.getValue(0));
        }
        return out.toString();
    }

    @Test
    public void aPointBetweenTwoRefreshesReadsTheEarlierOneAfterARestart() throws InterruptedException {
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        final String between;
        try {
            engine.execute("CREATE DATABASE d");
            engine.execute("USE SCHEMA d.PUBLIC");
            engine.execute("CREATE TABLE src (a INT)");
            engine.execute("INSERT INTO src VALUES (1), (2)");
            engine.execute("CREATE DYNAMIC TABLE dtt TARGET_LAG = '1 day' WAREHOUSE = COMPUTE_WH AS SELECT a FROM src");
            engine.execute("INSERT INTO src VALUES (3)");
            engine.execute("ALTER DYNAMIC TABLE dtt REFRESH");
            Thread.sleep(20L);
            between = answer(engine, "SELECT TO_VARCHAR(CURRENT_TIMESTAMP(), 'YYYY-MM-DD HH24:MI:SS.FF9 TZHTZM')");
            Thread.sleep(20L);
            engine.execute("INSERT INTO src VALUES (4)");
            engine.execute("ALTER DYNAMIC TABLE dtt REFRESH");
            engine.checkpoint();
            assertEquals("1 | 2 | 3", answer(engine, "SELECT a FROM d.PUBLIC.dtt AT (TIMESTAMP => '" + between
                + "'::TIMESTAMP_LTZ) ORDER BY a"));
        } finally {
            engine.shutdown();
        }
        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertEquals("1 | 2 | 3", answer(recovered, "SELECT a FROM d.PUBLIC.dtt AT (TIMESTAMP => '" + between
                + "'::TIMESTAMP_LTZ) ORDER BY a"));
            assertEquals("1 | 2 | 3 | 4", answer(recovered, "SELECT a FROM d.PUBLIC.dtt ORDER BY a"));
        } finally {
            recovered.shutdown();
        }
    }
}
