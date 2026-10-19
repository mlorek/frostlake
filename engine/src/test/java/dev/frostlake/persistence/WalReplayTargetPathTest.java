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
 * A routine's TARGET_PATH JAR is on disk, not in the write-ahead log, and when the log replays at startup the JAR
 * is already where the statement left it. The replayed CREATE takes the path again instead of refusing it as an
 * existing file, and the recovered routine runs and is told as it was.
 */
public class WalReplayTargetPathTest {

    private Path dir;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("wal_target_path_");
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
        cfg.setProperty(EngineConfig.PROP_STAGE_INTERNAL_LOCAL_ROOT, dir.resolve("stages").toString());
        return cfg;
    }

    private static String answer(final DatabaseEngine engine, final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? " | " : "").append(row.getValue(0));
        }
        return out.toString();
    }

    @Test
    public void aReplayedCreateTakesItsOwnJarAgain() {
        final DatabaseEngine first = new DatabaseEngine(walConfig());
        first.execute("CREATE DATABASE d");
        first.execute("USE SCHEMA d.PUBLIC");
        first.execute("CREATE STAGE st");
        first.execute("CREATE FUNCTION f(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.h' TARGET_PATH ="
            + " '@st/sub/f.jar' AS $$ class H { public static long h(long x) { return x + 1; } } $$");
        first.execute("CREATE FUNCTION g(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.h' IMPORTS ="
            + " ('@st/sub/f.jar')");
        assertEquals("6", answer(first, "SELECT f(5)"));
        first.shutdown();

        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertTrue(recovered.getWalReplayFailures().isEmpty(), String.valueOf(recovered.getWalReplayFailures()));
            recovered.execute("USE SCHEMA d.PUBLIC");
            assertEquals("6", answer(recovered, "SELECT f(5)"));
            assertEquals("8", answer(recovered, "SELECT g(7)"), "the routine that imports the JAR runs too");
            assertEquals("st/sub/f.jar", answer(recovered, "LIST @st"));
            assertEquals("@D.PUBLIC.ST/sub/f.jar", answer(recovered, "DESCRIBE FUNCTION f(NUMBER) ->> SELECT"
                + " \"value\" FROM $1 WHERE \"property\" = 'target_path'"));
        } finally {
            recovered.shutdown();
        }
    }
}
