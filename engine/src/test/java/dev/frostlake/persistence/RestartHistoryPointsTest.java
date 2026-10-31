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
 * What a restart keeps of the account's histories: the files each COPY loaded, which LOAD_HISTORY and COPY_HISTORY
 * list after a checkpoint and a restart as before them, and the query ID of every statement, logged or not, which a
 * time-travel point names after a restart and reads where it did — a query the log holds no record of included,
 * whether a checkpoint came between or not.
 */
public class RestartHistoryPointsTest {

    private static final String COUNT_AT = "SELECT COUNT(*) FROM d.PUBLIC.t AT(STATEMENT => '%s')";

    private Path dir;
    /** The files a stage reads, kept apart from the data directory, whose own stages a start settles. */
    private Path files;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("restart_history_points_");
        files = Files.createTempDirectory("restart_history_files_");
    }

    @AfterEach
    public void tearDown() {
        deleteRecursively(dir.toFile());
        deleteRecursively(files.toFile());
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
        cfg.setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "true");
        return cfg;
    }

    /** A statement's answer on one line: rows as "a, b | c, d", or "REFUSED " and the refusal. */
    private static String answer(final DatabaseEngine engine, final String sql) {
        try {
            final StringBuilder rows = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                final StringBuilder cells = new StringBuilder();
                for (final Object value : row.getValues()) {
                    cells.append(cells.length() > 0 ? ", " : "").append(value);
                }
                rows.append(rows.length() > 0 ? " | " : "").append(cells);
            }
            return rows.toString();
        } catch (final RuntimeException refused) {
            return "REFUSED " + refused.getMessage();
        }
    }

    @Test
    public void loadHistoryOutlivesACheckpointAndARestart() throws IOException {
        Files.writeString(files.resolve("a.json"), "{\"v\": 1}\n{\"v\": 2}\n");
        final String loads = "SELECT FILE_NAME, STATUS, ROW_COUNT FROM d.INFORMATION_SCHEMA.LOAD_HISTORY";
        final String copies = "SELECT FILE_NAME, ROW_COUNT FROM TABLE(d.INFORMATION_SCHEMA.COPY_HISTORY("
            + "TABLE_NAME => 'D.PUBLIC.DOCS', START_TIME => DATEADD(HOUR, -1, CURRENT_TIMESTAMP())))";
        final String listedLoads;
        final String listedCopies;
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        try {
            engine.execute("CREATE DATABASE d");
            engine.execute("USE SCHEMA d.PUBLIC");
            engine.execute("CREATE STAGE st URL = 'file://" + files + "'");
            engine.execute("CREATE TABLE docs (doc VARIANT)");
            engine.execute("COPY INTO docs FROM @st FILE_FORMAT = (TYPE = JSON)");
            listedLoads = answer(engine, loads);
            listedCopies = answer(engine, copies);
            engine.checkpoint();
        } finally {
            engine.shutdown();
        }
        assertEquals("st/a.json, LOADED, 2", listedLoads);
        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertEquals(listedLoads, answer(recovered, loads));
            assertEquals(listedCopies, answer(recovered, copies));
        } finally {
            recovered.shutdown();
        }
    }

    @Test
    public void aQueryTheLogHoldsNoRecordOfIsNamedAfterARestart() {
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        final String query;
        try {
            engine.execute("CREATE DATABASE d");
            engine.execute("USE SCHEMA d.PUBLIC");
            engine.execute("CREATE TABLE t (a INT)");
            engine.execute("INSERT INTO t VALUES (1)");
            engine.execute("SELECT COUNT(*) FROM t");
            query = answer(engine, "SELECT LAST_QUERY_ID()");
            engine.execute("INSERT INTO t VALUES (2)");
            assertEquals("1", answer(engine, String.format(COUNT_AT, query)));
        } finally {
            engine.shutdown();
        }
        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        final String later;
        try {
            assertTrue(recovered.getWalReplayFailures().isEmpty(), String.valueOf(recovered.getWalReplayFailures()));
            assertEquals("1", answer(recovered, String.format(COUNT_AT, query)));
            // A query the recovered engine runs is named after the next restart too, over the same replay.
            recovered.execute("SELECT COUNT(*) FROM d.PUBLIC.t");
            later = answer(recovered, "SELECT LAST_QUERY_ID()");
            recovered.execute("INSERT INTO d.PUBLIC.t VALUES (3)");
        } finally {
            recovered.shutdown();
        }
        final DatabaseEngine again = new DatabaseEngine(walConfig());
        try {
            assertEquals("1", answer(again, String.format(COUNT_AT, query)));
            assertEquals("2", answer(again, String.format(COUNT_AT, later)));
        } finally {
            again.shutdown();
        }
    }

    @Test
    public void aStatementBeforeACheckpointIsNamedAfterARestart() {
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        final String inserted;
        final String query;
        try {
            engine.execute("CREATE DATABASE d");
            engine.execute("USE SCHEMA d.PUBLIC");
            engine.execute("CREATE TABLE t (a INT)");
            engine.execute("INSERT INTO t VALUES (1)");
            inserted = answer(engine, "SELECT LAST_QUERY_ID()");
            engine.execute("INSERT INTO t VALUES (2)");
            engine.execute("SELECT COUNT(*) FROM t");
            query = answer(engine, "SELECT LAST_QUERY_ID()");
            engine.checkpoint();
            engine.execute("INSERT INTO t VALUES (3)");
        } finally {
            engine.shutdown();
        }
        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertEquals("1", answer(recovered, String.format(COUNT_AT, inserted)));
            assertEquals("2", answer(recovered, String.format(COUNT_AT, query)));
            recovered.checkpoint();
        } finally {
            recovered.shutdown();
        }
        // A second checkpoint keeps the points the first restored.
        final DatabaseEngine again = new DatabaseEngine(walConfig());
        try {
            assertEquals("1", answer(again, String.format(COUNT_AT, inserted)));
            assertEquals("2", answer(again, String.format(COUNT_AT, query)));
            assertEquals("3", answer(again, "SELECT COUNT(*) FROM d.PUBLIC.t"));
        } finally {
            again.shutdown();
        }
    }
}
