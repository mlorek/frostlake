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
import dev.frostlake.storage.ResultSetColumn;
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
 * A table's shapes over its history are part of the engine's state a copy keeps: after an engine clone, a checkpoint's
 * restore and a write-ahead log's recovery from a checkpoint, a CLONE ... AT a point before a column change made
 * before the copy takes the shape of the point and its rows in that shape — a dropped column's values, a renamed
 * column's old name, no column added since — as on the engine the copy was taken from, while a time-travel SELECT
 * reads the point through the table's current columns.
 */
public class CloneShapeAcrossCopyTest {

    /** How far apart a point is kept from the statements around it, so each falls in a millisecond of its own. */
    private static final long GAP_MILLIS = 30L;

    private Path dir;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("clone_shape_");
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

    /** A statement's answer on one line: its column names, then its rows as "a, b | c, d"; or its refusal. */
    private static String answer(final DatabaseEngine engine, final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder out = new StringBuilder();
            for (final ResultSetColumn column : rs.getColumns()) {
                out.append(out.length() > 0 ? ", " : "").append(column.getName());
            }
            out.append(" :");
            for (final Row row : rs.getRows()) {
                final StringBuilder cells = new StringBuilder();
                for (final Object value : row.getValues()) {
                    cells.append(cells.length() > 0 ? ", " : "").append(value);
                }
                out.append(' ').append(cells).append(';');
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return "REFUSED " + refused.getMessage();
        }
    }

    /** The clock now, kept apart from the statements run before and after it. */
    private static long pointBetween() throws InterruptedException {
        Thread.sleep(GAP_MILLIS);
        final long point = System.currentTimeMillis();
        Thread.sleep(GAP_MILLIS);
        return point;
    }

    private static String at(final long millis) {
        return "AT(TIMESTAMP => TO_TIMESTAMP_LTZ(" + millis + ", 3))";
    }

    /** Gives d.PUBLIC.t two shapes after its rows and a point before each change; answers the point's statements. */
    private static String[] reshape(final DatabaseEngine engine) throws InterruptedException {
        engine.execute("CREATE DATABASE d");
        engine.execute("CREATE TABLE d.PUBLIC.t (a INT, b INT)");
        engine.execute("INSERT INTO d.PUBLIC.t VALUES (1, 2)");
        final long first = pointBetween();
        engine.execute("ALTER TABLE d.PUBLIC.t DROP COLUMN b");
        final long second = pointBetween();
        engine.execute("ALTER TABLE d.PUBLIC.t RENAME COLUMN a TO a2");
        engine.execute("ALTER TABLE d.PUBLIC.t ADD COLUMN c INT");
        return new String[] {
            "CREATE TABLE d.PUBLIC.c1 CLONE d.PUBLIC.t " + at(first),
            "SELECT * FROM d.PUBLIC.c1",
            "CREATE TABLE d.PUBLIC.c2 CLONE d.PUBLIC.t " + at(second),
            "SELECT * FROM d.PUBLIC.c2",
            "SELECT * FROM d.PUBLIC.t " + at(first),
            "SELECT * FROM d.PUBLIC.t",
        };
    }

    /** The copy answers the point's statements as the engine it copies does. */
    private static void assertCopyClonesAlike(final DatabaseEngine source, final DatabaseEngine copy,
                                              final String[] statements) {
        final StringBuilder expected = new StringBuilder();
        final StringBuilder actual = new StringBuilder();
        for (final String statement : statements) {
            expected.append(statement).append(" => ").append(answer(source, statement)).append('\n');
            actual.append(statement).append(" => ").append(answer(copy, statement)).append('\n');
        }
        assertTrue(expected.toString().contains("SELECT * FROM d.PUBLIC.c1 => A, B : 1, 2;\n"), expected.toString());
        assertTrue(expected.toString().contains("SELECT * FROM d.PUBLIC.c2 => A : 1;\n"), expected.toString());
        assertEquals(expected.toString(), actual.toString());
    }

    @Test
    public void aCloneOfTheEngineClonesThePointsShape() throws Exception {
        final DatabaseEngine source = new DatabaseEngine();
        final String[] statements = reshape(source);
        assertCopyClonesAlike(source, source.cloneInstance(), statements);
    }

    @Test
    public void aCheckpointRestoreClonesThePointsShape() throws Exception {
        final DatabaseEngine source = new DatabaseEngine();
        final String[] statements = reshape(source);
        source.checkpointStateTo(dir.resolve("state"));
        final DatabaseEngine restored = new DatabaseEngine();
        restored.restoreStateFrom(dir.resolve("state"));
        assertCopyClonesAlike(source, restored, statements);
    }

    @Test
    public void aWalRecoveryFromACheckpointReplaysTheClonesAsTheyRan() throws InterruptedException {
        final DatabaseEngine first = new DatabaseEngine(walConfig());
        final StringBuilder expected = new StringBuilder();
        final String[] statements;
        try {
            statements = reshape(first);
            first.checkpoint();
            // Logged after the checkpoint, the clones replay over the history the checkpoint restores.
            for (final String statement : statements) {
                final String answered = answer(first, statement);
                if (statement.startsWith("SELECT")) {
                    expected.append(statement).append(" => ").append(answered).append('\n');
                }
            }
        } finally {
            first.shutdown();
        }
        assertTrue(expected.toString().contains("SELECT * FROM d.PUBLIC.c1 => A, B : 1, 2;\n"), expected.toString());
        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertTrue(recovered.getWalReplayFailures().isEmpty(), String.valueOf(recovered.getWalReplayFailures()));
            final StringBuilder actual = new StringBuilder();
            for (final String statement : statements) {
                if (statement.startsWith("SELECT")) {
                    actual.append(statement).append(" => ").append(answer(recovered, statement)).append('\n');
                }
            }
            assertEquals(expected.toString(), actual.toString());
        } finally {
            recovered.shutdown();
        }
    }
}
