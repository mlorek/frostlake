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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A virtual column survives a reload: the engine that reopens the directory tells its expression back and computes
 * it for the rows written after.
 */
public class VirtualColumnPersistenceTest {

    private EngineConfig config;
    private Path dataDir;

    @BeforeEach
    public void setUp() throws IOException {
        dataDir = Files.createTempDirectory("persist_virtual_");
        config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_PERSISTENCE_ENABLED, "true");
        config.setProperty(EngineConfig.PROP_PERSISTENCE_DIRECTORY, dataDir.toString());
        config.setProperty(EngineConfig.PROP_PERSISTENCE_AUTO_SAVE, "false");
    }

    @AfterEach
    public void tearDown() {
        if (dataDir != null) {
            deleteRecursively(dataDir.toFile());
        }
    }

    @Test
    public void aVirtualColumnIsComputedAfterAReload() {
        final DatabaseEngine engine1 = new DatabaseEngine(config);
        engine1.execute("CREATE DATABASE IF NOT EXISTS test_db");
        engine1.execute("USE DATABASE test_db");
        engine1.execute("CREATE SCHEMA IF NOT EXISTS test_schema");
        engine1.execute("USE SCHEMA test_schema");
        engine1.execute("CREATE TABLE v (a INT, b INT AS (a + 1))");
        engine1.execute("INSERT INTO v VALUES (1)");
        engine1.shutdown();

        final DatabaseEngine engine2 = new DatabaseEngine(config);
        engine2.execute("USE DATABASE test_db");
        engine2.execute("USE SCHEMA test_schema");
        engine2.execute("INSERT INTO v VALUES (5)");
        final ResultSet rows = engine2.executeQuery("SELECT a, b FROM v ORDER BY a");
        assertEquals(2, rows.getRows().size());
        assertEquals(2L, ((Number) rows.getRows().get(0).getValue(1)).longValue());
        assertEquals(6L, ((Number) rows.getRows().get(1).getValue(1)).longValue());
        final ResultSet described = engine2.executeQuery("DESCRIBE TABLE v");
        assertEquals("VIRTUAL", described.getRows().get(1).getValue(2));
        assertEquals("A + 1", described.getRows().get(1).getValue(8));
        engine2.shutdown();
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
}
