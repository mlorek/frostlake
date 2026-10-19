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
 * A stream on a join view reads the view's rows at its offset and now, so the offset's place among the tables'
 * snapshots is part of the stream: a clone, a checkpoint's restore and a restart from the write-ahead log keep it,
 * and the stream still answers the changes no statement consumed - a restart is no consumption (documented). The
 * view's own query is read where the view lives, so a session with no schema of its own reads the stream too.
 */
public class JoinViewStreamOffsetTest {

    private static final String[] SETUP = {
        "CREATE DATABASE d", "USE SCHEMA d.public", "CREATE TABLE a (id INT, x STRING)",
        "CREATE TABLE b (id INT, y STRING)", "INSERT INTO a VALUES (1, 'a1'), (2, 'a2')",
        "INSERT INTO b VALUES (1, 'b1'), (2, 'b2')",
        "CREATE VIEW jv AS SELECT a.id, x, y FROM a JOIN b ON a.id = b.id", "CREATE STREAM js ON VIEW jv",
        "UPDATE a SET x = 'a1u' WHERE id = 1", "INSERT INTO b VALUES (3, 'b3')", "INSERT INTO a VALUES (3, 'a3')",
    };

    private static final String CHANGES = "1|a1|b1|DELETE, 1|a1u|b1|INSERT, 3|a3|b3|INSERT";

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
        final Path dir = Files.createTempDirectory("join_view_offset_");
        dirs.add(dir);
        return dir;
    }

    /** The stream's changes, each row as id|x|y|action, in order. */
    private static String changes(final DatabaseEngine engine, final String stream) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery("SELECT id, x, y, METADATA$ACTION FROM " + stream
                + " ORDER BY id, METADATA$ACTION").getRows()) {
            out.append(out.length() > 0 ? ", " : "");
            final List<Object> values = row.getValues();
            for (int i = 0; i < values.size(); i++) {
                out.append(i > 0 ? "|" : "").append(values.get(i));
            }
        }
        return out.toString();
    }

    private static String count(final DatabaseEngine engine, final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValues().get(0));
    }

    private static DatabaseEngine populated(final DatabaseEngine engine) {
        for (final String sql : SETUP) {
            engine.execute(sql);
        }
        return engine;
    }

    @Test
    public void aCloneAndARestoreKeepTheOffset() throws Exception {
        final DatabaseEngine original = track(populated(new DatabaseEngine()));
        assertEquals(CHANGES, changes(original, "d.public.js"));
        final DatabaseEngine clone = track(original.cloneInstance());
        assertEquals(CHANGES, changes(clone, "d.public.js"));
        assertEquals("3", count(clone,
            "SELECT COUNT(*) FROM d.public.jv CHANGES(INFORMATION => DEFAULT) AT(STREAM => 'd.public.js')"));
        final Path saved = tempDir();
        original.checkpointStateTo(saved);
        final DatabaseEngine restored = track(new DatabaseEngine());
        restored.restoreStateFrom(saved);
        assertEquals(CHANGES, changes(restored, "d.public.js"));
        // What is written after the restore is the stream's too.
        restored.execute("INSERT INTO d.public.a VALUES (4, 'a4')");
        restored.execute("INSERT INTO d.public.b VALUES (4, 'b4')");
        assertEquals(CHANGES + ", 4|a4|b4|INSERT", changes(restored, "d.public.js"));
    }

    @Test
    public void aRestartFromTheLogKeepsTheOffset() throws Exception {
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_DURABILITY_WAL_ENABLED, "true");
        config.setProperty(EngineConfig.PROP_DURABILITY_WAL_FILE, tempDir().resolve("wal.log").toString());
        final DatabaseEngine first = populated(new DatabaseEngine(config));
        assertEquals(CHANGES, changes(first, "d.public.js"));
        first.checkpoint();
        first.shutdown();
        final DatabaseEngine restarted = track(new DatabaseEngine(config));
        // Read from a session that names no schema: the view's names are the view's own.
        assertEquals(CHANGES, changes(restarted, "d.public.js"));
        assertEquals("3", count(restarted,
            "SELECT COUNT(*) FROM d.public.jv CHANGES(INFORMATION => DEFAULT) AT(STREAM => 'd.public.js')"));
        restarted.execute("CREATE TABLE d.public.sink (id INT)");
        restarted.execute("INSERT INTO d.public.sink SELECT id FROM d.public.js");
        assertEquals("", changes(restarted, "d.public.js"));
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
