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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A table stage's files are on disk, not in the write-ahead log, and when the log replays at startup they are
 * already where the statements left them. A replayed RENAME moved a directory that had already moved, a replayed
 * SWAP swapped the directories back, and a replayed CREATE TABLE emptied the stage of the table it made, so the
 * recovered tables listed each other's files or none.
 *
 * <p>A dropped table's files wait for an UNDROP where a new table of its name does not reach them: the new table
 * starts with an empty stage, and the UNDROP brings the dropped table's files back with it, recovered or not.
 */
public class WalReplayStageFilesTest {

    private Path dir;
    private Path stages;
    private Path upload;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("wal_stage_files_");
        stages = dir.resolve("stages");
        upload = dir.resolve("upload");
        Files.createDirectories(upload);
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
        cfg.setProperty(EngineConfig.PROP_STAGE_INTERNAL_LOCAL_ROOT, stages.toString());
        cfg.setProperty(EngineConfig.PROP_COMMAND_REMOVE_ENABLED, "true");
        return cfg;
    }

    /** The same, checkpointing after every transaction, so a restart restores a snapshot and replays nothing. */
    private EngineConfig checkpointingConfig() {
        final EngineConfig cfg = walConfig();
        cfg.setProperty(EngineConfig.PROP_DURABILITY_CHECKPOINT_INTERVAL, "1");
        return cfg;
    }

    /** A local file to PUT, holding one CSV row. */
    private String localFile(final String name, final String row) throws IOException {
        final Path file = upload.resolve(name);
        Files.write(file, (row + "\n").getBytes(StandardCharsets.UTF_8));
        return "file://" + file.toAbsolutePath();
    }

    /** Every row's first cell, a bar between rows. */
    private static String answer(final DatabaseEngine engine, final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? " | " : "").append(row.getValue(0));
        }
        return out.toString();
    }

    @Test
    public void aReplayedRenameAndSwapLeaveEachStageWithItsTable() throws IOException {
        final DatabaseEngine first = new DatabaseEngine(walConfig());
        first.execute("CREATE DATABASE d");
        first.execute("USE SCHEMA d.PUBLIC");
        first.execute("CREATE TABLE t (a INT)");
        first.execute("INSERT INTO t VALUES (1)");
        first.execute("PUT " + localFile("x.csv", "1") + " @%t");
        first.execute("CREATE TABLE u (a INT)");
        first.execute("INSERT INTO u VALUES (2)");
        first.execute("PUT " + localFile("y.csv", "2") + " @%u");
        first.execute("ALTER TABLE t RENAME TO t2");
        first.execute("ALTER TABLE t2 SWAP WITH u");
        // The table that was t answers to u now, and holds x; the one that was u answers to t2 and holds y.
        assertEquals("x.csv.gz", answer(first, "LIST @%u"));
        assertEquals("y.csv.gz", answer(first, "LIST @%t2"));
        first.shutdown();

        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertTrue(recovered.getWalReplayFailures().isEmpty(), String.valueOf(recovered.getWalReplayFailures()));
            recovered.execute("USE SCHEMA d.PUBLIC");
            assertEquals("1", answer(recovered, "SELECT a FROM u"));
            assertEquals("2", answer(recovered, "SELECT a FROM t2"));
            assertEquals("x.csv.gz", answer(recovered, "LIST @%u"));
            assertEquals("y.csv.gz", answer(recovered, "LIST @%t2"));
            assertEquals("1", answer(recovered, "SELECT $1 FROM @%u"));
            assertEquals("2", answer(recovered, "SELECT $1 FROM @%t2"));
        } finally {
            recovered.shutdown();
        }
    }

    @Test
    public void aReplayedCreateLeavesTheFilesPutAfterIt() throws IOException {
        final DatabaseEngine first = new DatabaseEngine(walConfig());
        first.execute("CREATE DATABASE d");
        first.execute("USE SCHEMA d.PUBLIC");
        first.execute("CREATE TABLE t (a INT)");
        first.execute("PUT " + localFile("x.csv", "1") + " @%t");
        first.shutdown();
        // The PUT's source is gone by the time the engine restarts; the staged copy is what counts.
        Files.delete(upload.resolve("x.csv"));

        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertTrue(recovered.getWalReplayFailures().isEmpty(), String.valueOf(recovered.getWalReplayFailures()));
            assertEquals("x.csv.gz", answer(recovered, "LIST @d.PUBLIC.%t"));
        } finally {
            recovered.shutdown();
        }
    }

    @Test
    public void aTableCreatedOverADroppedOneStartsEmptyAndTheUndropBringsTheFilesBack() throws IOException {
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        try {
            engine.execute("CREATE DATABASE d");
            engine.execute("USE SCHEMA d.PUBLIC");
            engine.execute("CREATE TABLE w (a INT)");
            engine.execute("PUT " + localFile("z.csv", "3") + " @%w");
            engine.execute("DROP TABLE w");
            engine.execute("CREATE TABLE w (a INT)");
            assertEquals("", answer(engine, "LIST @%w"));
            engine.execute("ALTER TABLE w RENAME TO w_new");
            engine.execute("UNDROP TABLE w");
            assertEquals("z.csv.gz", answer(engine, "LIST @%w"));
            assertEquals("", answer(engine, "LIST @%w_new"));
        } finally {
            engine.shutdown();
        }
    }

    @Test
    public void aReplayedDropAndCreateKeepTheDroppedTablesFilesForTheUndrop() throws IOException {
        final DatabaseEngine first = new DatabaseEngine(walConfig());
        first.execute("CREATE DATABASE d");
        first.execute("USE SCHEMA d.PUBLIC");
        first.execute("CREATE TABLE w (a INT)");
        first.execute("PUT " + localFile("z.csv", "3") + " @%w");
        first.execute("DROP TABLE w");
        first.execute("CREATE TABLE w (a INT)");
        first.execute("PUT " + localFile("n.csv", "4") + " @%w");
        first.shutdown();

        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertTrue(recovered.getWalReplayFailures().isEmpty(), String.valueOf(recovered.getWalReplayFailures()));
            recovered.execute("USE SCHEMA d.PUBLIC");
            assertEquals("n.csv.gz", answer(recovered, "LIST @%w"));
            recovered.execute("ALTER TABLE w RENAME TO w_new");
            recovered.execute("UNDROP TABLE w");
            assertEquals("z.csv.gz", answer(recovered, "LIST @%w"));
            assertEquals("n.csv.gz", answer(recovered, "LIST @%w_new"));
        } finally {
            recovered.shutdown();
        }
    }

    @Test
    public void aDroppedVersionRestoredFromACheckpointStillFindsItsFiles() throws IOException {
        final DatabaseEngine first = new DatabaseEngine(checkpointingConfig());
        first.execute("CREATE DATABASE d");
        first.execute("USE SCHEMA d.PUBLIC");
        first.execute("CREATE TABLE w (a INT)");
        first.execute("PUT " + localFile("z.csv", "3") + " @%w");
        first.execute("DROP TABLE w");
        first.shutdown();

        final DatabaseEngine recovered = new DatabaseEngine(checkpointingConfig());
        try {
            recovered.execute("USE SCHEMA d.PUBLIC");
            recovered.execute("CREATE TABLE w (a INT)");
            assertEquals("", answer(recovered, "LIST @%w"));
            recovered.execute("ALTER TABLE w RENAME TO w_new");
            recovered.execute("UNDROP TABLE w");
            assertEquals("z.csv.gz", answer(recovered, "LIST @%w"));
            assertEquals("3", answer(recovered, "SELECT $1 FROM @%w"));
        } finally {
            recovered.shutdown();
        }
    }

    @Test
    public void aReplayedDropSchemaKeepsItsTablesStagesForTheUndrop() {
        final DatabaseEngine first = new DatabaseEngine(walConfig());
        first.execute("CREATE DATABASE d");
        first.execute("CREATE SCHEMA d.s");
        first.execute("CREATE TABLE d.s.t (a INT)");
        first.execute("COPY INTO @d.s.%t/f FROM (SELECT 5 AS a) FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE");
        first.execute("DROP SCHEMA d.s");
        first.execute("CREATE SCHEMA d.s");
        first.execute("CREATE TABLE d.s.t (a INT)");
        first.shutdown();

        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertTrue(recovered.getWalReplayFailures().isEmpty(), String.valueOf(recovered.getWalReplayFailures()));
            assertEquals("", answer(recovered, "LIST @d.s.%t"));
            recovered.execute("ALTER SCHEMA d.s RENAME TO d.s_new");
            recovered.execute("UNDROP SCHEMA d.s");
            assertEquals("f", answer(recovered, "LIST @d.s.%t"));
            assertEquals("5", answer(recovered, "SELECT $1 FROM @d.s.%t/f"));
        } finally {
            recovered.shutdown();
        }
    }

    @Test
    public void aDurableEngineKeepsWhatItSetAsideAcrossARestartUntilTheVersionEnds() throws IOException {
        final DatabaseEngine first = new DatabaseEngine(walConfig());
        first.execute("CREATE DATABASE d");
        first.execute("USE SCHEMA d.PUBLIC");
        first.execute("CREATE TABLE k (a INT)");
        first.execute("COPY INTO @%k/h FROM (SELECT 1 AS a) FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE");
        first.execute("CREATE TABLE z (a INT) DATA_RETENTION_TIME_IN_DAYS = 0");
        first.execute("COPY INTO @%z/h FROM (SELECT 2 AS a) FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE");
        first.execute("DROP TABLE k");
        first.execute("DROP TABLE z");
        first.shutdown();
        // The engine that restarts on this log may still UNDROP them.
        assertEquals("D.PUBLIC.K, D.PUBLIC.Z", setAside());

        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            // Z kept no day of history: the first look at the dropped versions forgets it, and its files with it.
            recovered.executeQuery("SHOW TABLES HISTORY IN DATABASE d");
            assertEquals("D.PUBLIC.K", setAside());
            recovered.execute("UNDROP TABLE d.PUBLIC.k");
            assertEquals("1", answer(recovered, "SELECT $1 FROM @d.PUBLIC.%k/h"));
            assertEquals("", setAside());
        } finally {
            recovered.shutdown();
        }
    }

    /** The stage directories set aside under the stage root, by their names, sorted and comma-separated. */
    private String setAside() throws IOException {
        final java.util.List<String> found = new java.util.ArrayList<>();
        final Path dropped = stages.resolve("dropped");
        if (Files.isDirectory(dropped)) {
            try (final java.util.stream.Stream<Path> walk = Files.walk(dropped)) {
                final java.util.Iterator<Path> paths = walk.iterator();
                while (paths.hasNext()) {
                    final String name = paths.next().getFileName().toString();
                    if (name.contains("@")) {
                        found.add(name.substring(0, name.indexOf('@')));
                    }
                }
            }
        }
        java.util.Collections.sort(found);
        return String.join(", ", found);
    }

    @Test
    public void aReplayedRemoveLeavesAFileStagedAgainAfterIt() throws IOException {
        final DatabaseEngine first = new DatabaseEngine(walConfig());
        first.execute("CREATE DATABASE d");
        first.execute("USE SCHEMA d.PUBLIC");
        first.execute("CREATE TABLE t (a INT)");
        first.execute("PUT " + localFile("x.csv", "1") + " @%t");
        first.execute("REMOVE @%t/x.csv.gz");
        first.execute("PUT " + localFile("x.csv", "9") + " @%t");
        first.shutdown();

        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertTrue(recovered.getWalReplayFailures().isEmpty(), String.valueOf(recovered.getWalReplayFailures()));
            assertEquals("9", answer(recovered, "SELECT $1 FROM @d.PUBLIC.%t"));
        } finally {
            recovered.shutdown();
        }
    }

    @Test
    public void aReplayedUnloadDoesNotRefuseOverItsOwnFiles() {
        final DatabaseEngine first = new DatabaseEngine(walConfig());
        first.execute("CREATE DATABASE d");
        first.execute("USE SCHEMA d.PUBLIC");
        first.execute("CREATE TABLE t (a INT)");
        first.execute("COPY INTO @%t/out FROM (SELECT 7 AS a) FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE");
        first.shutdown();

        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertTrue(recovered.getWalReplayFailures().isEmpty(), String.valueOf(recovered.getWalReplayFailures()));
            assertEquals("7", answer(recovered, "SELECT $1 FROM @d.PUBLIC.%t/out"));
        } finally {
            recovered.shutdown();
        }
    }
}
