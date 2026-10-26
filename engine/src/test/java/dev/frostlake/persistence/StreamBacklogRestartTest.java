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
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A stream's backlog is the table's versions past its offset, one per statement, and a restart is no consumption: a
 * checkpoint's restore and a restart from the write-ahead log answer SYSTEM$STREAM_BACKLOG as the engine did before
 * it, a version written after the restart joins the backlog, and consuming the stream still empties it.
 */
public class StreamBacklogRestartTest {

    private static final String[] SETUP = {
        "CREATE DATABASE d", "USE SCHEMA d.public", "CREATE TABLE t (id INT, v STRING)",
        "INSERT INTO t VALUES (1, 'a'), (2, 'b')", "CREATE STREAM s ON TABLE t",
        "CREATE STREAM ao ON TABLE t APPEND_ONLY = TRUE", "INSERT INTO t VALUES (3, 'c'), (4, 'd')",
        "UPDATE t SET v = 'z' WHERE id = 1", "DELETE FROM t WHERE id = 2", "TRUNCATE TABLE t",
        "INSERT INTO t VALUES (5, 'e')",
    };

    private final List<Path> dirs = new ArrayList<>();
    private final List<DatabaseEngine> engines = new ArrayList<>();

    @AfterEach
    public void cleanup() {
        for (final DatabaseEngine engine : engines) {
            engine.shutdown();
        }
        for (final Path dir : dirs) {
            deleteRecursively(dir.toFile());
        }
    }

    private DatabaseEngine track(final DatabaseEngine engine) {
        engines.add(engine);
        return engine;
    }

    private Path tempDir() throws Exception {
        final Path dir = Files.createTempDirectory("stream_backlog_");
        dirs.add(dir);
        return dir;
    }

    /** The stream's backlog, every column of every version, in order. */
    private static String backlog(final DatabaseEngine engine, final String stream) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery("SELECT * FROM TABLE(SYSTEM$STREAM_BACKLOG('" + stream + "'))")
                .getRows()) {
            out.append(out.length() > 0 ? "; " : "");
            final List<Object> values = row.getValues();
            for (int i = 0; i < values.size(); i++) {
                out.append(i > 0 ? "|" : "").append(values.get(i));
            }
        }
        return out.toString();
    }

    /** The backlog's DML modes alone, which do not depend on when each version was written. */
    private static String modes(final DatabaseEngine engine, final String stream) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery("SELECT dml_mode FROM TABLE(SYSTEM$STREAM_BACKLOG('" + stream
                + "'))").getRows()) {
            out.append(out.length() > 0 ? ", " : "").append(row.getValues().get(0));
        }
        return out.toString();
    }

    private static DatabaseEngine populated(final DatabaseEngine engine) {
        for (final String sql : SETUP) {
            engine.execute(sql);
        }
        return engine;
    }

    private EngineConfig walConfig() throws Exception {
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_DURABILITY_WAL_ENABLED, "true");
        config.setProperty(EngineConfig.PROP_DURABILITY_WAL_FILE, tempDir().resolve("wal.log").toString());
        return config;
    }

    @Test
    public void aRestoreKeepsTheBacklog() throws Exception {
        final DatabaseEngine original = track(populated(new DatabaseEngine()));
        final String full = backlog(original, "d.public.s");
        final String appendOnly = backlog(original, "d.public.ao");
        assertEquals("INSERT, UPDATE, UPDATE, TRUNCATE, INSERT", modes(original, "d.public.s"));
        final Path saved = tempDir();
        original.checkpointStateTo(saved);
        final DatabaseEngine restored = track(new DatabaseEngine());
        restored.restoreStateFrom(saved);
        assertEquals(full, backlog(restored, "d.public.s"));
        assertEquals(appendOnly, backlog(restored, "d.public.ao"));
        // A version written after the restore follows the ones before it.
        restored.execute("INSERT INTO d.public.t VALUES (6, 'f')");
        assertEquals("INSERT, UPDATE, UPDATE, TRUNCATE, INSERT, INSERT", modes(restored, "d.public.s"));
        // Consuming the stream moves its offset past every version.
        restored.execute("CREATE TABLE d.public.sink (id INT)");
        restored.execute("INSERT INTO d.public.sink SELECT id FROM d.public.s");
        assertEquals("", backlog(restored, "d.public.s"));
        assertEquals("INSERT, UPDATE, UPDATE, TRUNCATE, INSERT, INSERT", modes(restored, "d.public.ao"));
    }

    @Test
    public void aRestartFromACheckpointKeepsTheBacklog() throws Exception {
        final EngineConfig config = walConfig();
        final DatabaseEngine first = populated(new DatabaseEngine(config));
        final String full = backlog(first, "d.public.s");
        final String appendOnly = backlog(first, "d.public.ao");
        first.checkpoint();
        first.shutdown();
        final DatabaseEngine restarted = track(new DatabaseEngine(config));
        assertEquals(full, backlog(restarted, "d.public.s"));
        assertEquals(appendOnly, backlog(restarted, "d.public.ao"));
        restarted.execute("CREATE TABLE d.public.sink (id INT)");
        restarted.execute("INSERT INTO d.public.sink SELECT id FROM d.public.ao");
        assertEquals("", backlog(restarted, "d.public.ao"));
        assertEquals(full, backlog(restarted, "d.public.s"));
    }

    @Test
    public void aRestartReplayingTheLogPastACheckpointKeepsTheBacklog() throws Exception {
        final EngineConfig config = walConfig();
        final DatabaseEngine first = populated(new DatabaseEngine(config));
        first.checkpoint();
        first.execute("UPDATE d.public.t SET v = 'y' WHERE id = 5");
        first.execute("INSERT INTO d.public.t VALUES (6, 'f')");
        final String full = backlog(first, "d.public.s");
        assertEquals("INSERT, UPDATE, UPDATE, TRUNCATE, INSERT, UPDATE, INSERT", modes(first, "d.public.s"));
        first.shutdown();
        final DatabaseEngine restarted = track(new DatabaseEngine(config));
        assertEquals(full, backlog(restarted, "d.public.s"));
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
}
