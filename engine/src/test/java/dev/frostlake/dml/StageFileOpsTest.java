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

package dev.frostlake.dml;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the stage file operations PUT (local → stage), GET (stage → local), and REMOVE / RM (delete staged
 * files), against a {@code file://} stage backed by a temp directory.
 */
public class StageFileOpsTest {

    private DatabaseEngine engine;
    private Path stageDir;
    private Path localDir;

    @BeforeEach
    public void setUp() throws IOException {
        stageDir = Files.createTempDirectory("stage_ops_stage_");
        localDir = Files.createTempDirectory("stage_ops_local_");
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_COMMAND_REMOVE_ENABLED, "true");  // REMOVE/RM is gated off by default; this suite exercises it
        config.setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "true");
        engine = new DatabaseEngine(config);
        engine.execute("CREATE DATABASE db");
        engine.execute("USE DATABASE db");
        engine.execute("CREATE SCHEMA s");
        engine.execute("USE SCHEMA s");
        engine.execute("CREATE STAGE st URL='file://" + stageDir + "'");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
        deleteRecursively(stageDir.toFile());
        deleteRecursively(localDir.toFile());
    }

    @Test
    public void putUploadsLocalFile() throws IOException {
        final Path local = localDir.resolve("up.csv");
        Files.writeString(local, "1,Alice\n");

        // Default AUTO_COMPRESS gzips and renames the target, exactly like a real account.
        final ResultSet rs = engine.executeQuery("PUT 'file://" + local + "' @st");
        assertEquals("UPLOADED", String.valueOf(rs.getRows().get(0).getValue(rs.getColumnIndex("status"))));
        assertEquals("up.csv.gz", String.valueOf(rs.getRows().get(0).getValue(rs.getColumnIndex("target"))));
        assertTrue(Files.exists(stageDir.resolve("up.csv.gz")), "PUT should land the gzipped file in the stage dir");
    }

    @Test
    public void getDownloadsStagedFile() throws IOException {
        Files.writeString(stageDir.resolve("down.csv"), "x\n");
        final Path target = Files.createTempDirectory("stage_ops_get_");

        engine.executeQuery("GET @st 'file://" + target + "'");
        assertTrue(Files.exists(target.resolve("down.csv")), "GET should download the staged file");
        deleteRecursively(target.toFile());
    }

    @Test
    public void removeDeletesStagedFiles() throws IOException {
        Files.writeString(stageDir.resolve("a.csv"), "x\n");
        Files.writeString(stageDir.resolve("b.csv"), "y\n");

        final ResultSet rs = engine.executeQuery("REMOVE @st");
        assertEquals(2, rs.getRows().size());
        assertFalse(Files.exists(stageDir.resolve("a.csv")), "REMOVE should delete the staged file");
        assertFalse(Files.exists(stageDir.resolve("b.csv")), "REMOVE should delete the staged file");
    }

    @Test
    public void removePatternDeletesMatchingOnly() throws IOException {
        Files.writeString(stageDir.resolve("keep.txt"), "k\n");
        Files.writeString(stageDir.resolve("drop.csv"), "d\n");

        engine.executeQuery("RM @st PATTERN = '.*\\.csv'");
        assertTrue(Files.exists(stageDir.resolve("keep.txt")), "non-matching file should remain");
        assertFalse(Files.exists(stageDir.resolve("drop.csv")), "matching file should be removed");
    }

    private static void deleteRecursively(final File f) {
        if (f == null || !f.exists()) {
            return;
        }
        if (f.isDirectory()) {
            final File[] children = f.listFiles();
            if (children != null) {
                for (final File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        f.delete();
    }
}
