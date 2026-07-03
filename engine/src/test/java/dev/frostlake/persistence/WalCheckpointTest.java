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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WAL checkpoint + truncation (Phase 5 Part B; {@code durability.checkpointInterval}, default off). A
 * checkpoint snapshots full state, writes a marker, and truncates the log; recovery loads the last
 * checkpoint's snapshot and replays only the transactions logged after it — so the log stays bounded while
 * committed work (snapshot + tail) is still fully recovered. See docs/acid-snowflake-plan.md.
 */
public class WalCheckpointTest {

    private Path dir;
    private Path walFile;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("wal_checkpoint_test_");
        walFile = dir.resolve("wal.log");
    }

    @AfterEach
    public void tearDown() throws IOException {
        deleteRecursively(dir);
    }

    private DatabaseEngine newWalEngine(final int checkpointInterval) {
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_ENABLED, "true");
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_FILE, walFile.toString());
        cfg.setProperty(EngineConfig.PROP_DURABILITY_CHECKPOINT_INTERVAL, String.valueOf(checkpointInterval));
        return new DatabaseEngine(cfg);
    }

    @Test
    public void explicitCheckpointTruncatesTheLogAndStillRecovers() throws IOException {
        final DatabaseEngine e1 = newWalEngine(0);
        e1.execute("CREATE DATABASE db");
        e1.execute("USE DATABASE db");
        e1.execute("CREATE SCHEMA s");
        e1.execute("USE SCHEMA s");
        e1.execute("CREATE TABLE t (id INTEGER, v VARCHAR)");
        e1.execute("INSERT INTO t VALUES (1, 'a')");
        e1.execute("INSERT INTO t VALUES (2, 'b')");
        final long beforeCheckpoint = Files.size(walFile);

        e1.checkpoint();   // snapshot the 2 rows, then truncate the log down to the marker
        final long afterCheckpoint = Files.size(walFile);
        assertTrue(afterCheckpoint < beforeCheckpoint, "a checkpoint must truncate the write-ahead log");
        assertTrue(Files.isDirectory(dir.resolve("wal-checkpoints")), "checkpoint snapshot directory must exist");

        e1.execute("INSERT INTO t VALUES (3, 'c')");   // logged in the post-checkpoint tail
        e1.shutdown();

        // Recovery = load the checkpoint snapshot (2 rows) + replay the tail (1 row).
        final DatabaseEngine e2 = newWalEngine(0);
        final ResultSet rs = e2.executeQuery("SELECT * FROM db.s.t");
        assertEquals(3, rs.getRowCount(), "snapshot rows plus the post-checkpoint tail must both be recovered");
        e2.shutdown();
    }

    @Test
    public void autoCheckpointByIntervalStillRecoversEverything() {
        final DatabaseEngine e1 = newWalEngine(2);   // checkpoint after every 2 committed transactions
        e1.execute("CREATE DATABASE db");
        e1.execute("USE DATABASE db");
        e1.execute("CREATE SCHEMA s");
        e1.execute("USE SCHEMA s");
        e1.execute("CREATE TABLE t (id INTEGER)");
        for (int i = 1; i <= 6; i++) {
            e1.execute("INSERT INTO t VALUES (" + i + ")");
        }
        e1.shutdown();

        final DatabaseEngine e2 = newWalEngine(2);
        final ResultSet rs = e2.executeQuery("SELECT * FROM db.s.t");
        assertEquals(6, rs.getRowCount(), "all rows must survive across repeated automatic checkpoints");
        e2.shutdown();
    }

    @Test
    public void rolledBackTailAfterCheckpointIsNotRecovered() {
        final DatabaseEngine e1 = newWalEngine(0);
        e1.execute("CREATE DATABASE db");
        e1.execute("USE DATABASE db");
        e1.execute("CREATE SCHEMA s");
        e1.execute("USE SCHEMA s");
        e1.execute("CREATE TABLE t (id INTEGER)");
        e1.execute("INSERT INTO t VALUES (1)");
        e1.checkpoint();
        e1.execute("BEGIN");
        e1.execute("INSERT INTO t VALUES (2)");
        e1.execute("ROLLBACK");
        e1.shutdown();

        final DatabaseEngine e2 = newWalEngine(0);
        final ResultSet rs = e2.executeQuery("SELECT * FROM db.s.t");
        assertEquals(1, rs.getRowCount(), "checkpoint snapshot is recovered; the rolled-back tail is not");
        e2.shutdown();
    }

    private void deleteRecursively(final Path p) throws IOException {
        if (Files.isDirectory(p)) {
            try (DirectoryStream<Path> children = Files.newDirectoryStream(p)) {
                for (final Path child : children) {
                    deleteRecursively(child);
                }
            }
        }
        Files.deleteIfExists(p);
    }
}
