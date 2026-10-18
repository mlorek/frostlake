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
 * A dropped table's storage, and the time-travel history in it, is part of the engine's state a copy keeps: after an
 * engine clone, a checkpoint's restore and a write-ahead log's recovery, with a checkpoint taken while the table was
 * dropped or with none, an UNDROP of the table, or of the schema it was in, brings it back reading a point before the
 * drop as the engine the copy was taken from does.
 */
public class DroppedHistoryAcrossCopyTest {

    /** How far apart a point is kept from the writes around it, so each falls in a millisecond of its own. */
    private static final long GAP_MILLIS = 30L;

    private Path dir;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("dropped_history_");
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

    private static String answers(final DatabaseEngine engine, final String[] queries) {
        final StringBuilder out = new StringBuilder();
        for (final String query : queries) {
            out.append(query).append(" => ").append(answer(engine, query)).append('\n');
        }
        return out.toString();
    }

    /** The clock now, kept apart from the statements run before and after it. */
    private static long pointBetweenWrites() throws InterruptedException {
        Thread.sleep(GAP_MILLIS);
        final long point = System.currentTimeMillis();
        Thread.sleep(GAP_MILLIS);
        return point;
    }

    private static String at(final long millis) {
        return "AT(TIMESTAMP => TO_TIMESTAMP_LTZ(" + millis + ", 3))";
    }

    /**
     * Gives d.PUBLIC.t and d.s.u a history of two rows, a point between them, then drops the table and the schema;
     * answers the queries that read them once they are undropped.
     */
    private static String[] writeAndDrop(final DatabaseEngine engine) throws InterruptedException {
        engine.execute("CREATE DATABASE d");
        engine.execute("CREATE SCHEMA d.s");
        engine.execute("CREATE TABLE d.PUBLIC.t (a INT)");
        engine.execute("CREATE TABLE d.s.u (a INT)");
        engine.execute("INSERT INTO d.PUBLIC.t VALUES (1)");
        engine.execute("INSERT INTO d.s.u VALUES (10)");
        final long one = pointBetweenWrites();
        engine.execute("INSERT INTO d.PUBLIC.t VALUES (2)");
        engine.execute("INSERT INTO d.s.u VALUES (20)");
        final long two = pointBetweenWrites();
        engine.execute("DROP TABLE d.PUBLIC.t");
        engine.execute("DROP SCHEMA d.s");
        return new String[] {
            "SELECT a FROM d.PUBLIC.t ORDER BY a",
            "SELECT a FROM d.PUBLIC.t " + at(one) + " ORDER BY a",
            "SELECT a FROM d.PUBLIC.t " + at(two) + " ORDER BY a",
            "SELECT a FROM d.s.u " + at(one) + " ORDER BY a",
            "SELECT a FROM d.s.u " + at(two) + " ORDER BY a",
        };
    }

    private static void undrop(final DatabaseEngine engine) {
        engine.execute("UNDROP TABLE d.PUBLIC.t");
        engine.execute("UNDROP SCHEMA d.s");
    }

    /** The copy, once both undrop, reads the past as the engine it copies does. */
    private static void assertCopyReadsAlike(final DatabaseEngine source, final DatabaseEngine copy,
                                             final String[] queries) {
        undrop(source);
        undrop(copy);
        final String expected = answers(source, queries);
        assertTrue(expected.contains(" ORDER BY a => 1 | 2\n"), expected);
        assertTrue(expected.contains("3)) ORDER BY a => 1\n"), expected);
        assertTrue(expected.contains("3)) ORDER BY a => 10\n"), expected);
        assertEquals(expected, answers(copy, queries));
    }

    @Test
    public void aCloneUndropsATableWithItsHistory() throws Exception {
        final DatabaseEngine source = new DatabaseEngine();
        final String[] queries = writeAndDrop(source);
        assertCopyReadsAlike(source, source.cloneInstance(), queries);
    }

    @Test
    public void aCheckpointRestoreUndropsATableWithItsHistory() throws Exception {
        final DatabaseEngine source = new DatabaseEngine();
        final String[] queries = writeAndDrop(source);
        source.checkpointStateTo(dir.resolve("state"));
        final DatabaseEngine restored = new DatabaseEngine();
        restored.restoreStateFrom(dir.resolve("state"));
        assertCopyReadsAlike(source, restored, queries);
    }

    @Test
    public void aWalRecoveryUndropsATableWithItsHistory() throws Exception {
        for (final boolean checkpointed : new boolean[] {false, true}) {
            deleteRecursively(dir.toFile());
            dir.toFile().mkdirs();
            final DatabaseEngine first = new DatabaseEngine(walConfig());
            final String[] queries;
            final String before;
            try {
                queries = writeAndDrop(first);
                if (checkpointed) {
                    first.checkpoint();
                }
                first.execute("CREATE TABLE d.PUBLIC.other (a INT)");
                final DatabaseEngine reference = first.cloneInstance();
                undrop(reference);
                before = answers(reference, queries);
            } finally {
                first.shutdown();
            }
            assertTrue(before.contains("3)) ORDER BY a => 1\n"), before);
            final DatabaseEngine recovered = new DatabaseEngine(walConfig());
            try {
                assertTrue(recovered.getWalReplayFailures().isEmpty(),
                    String.valueOf(recovered.getWalReplayFailures()));
                undrop(recovered);
                assertEquals(before, answers(recovered, queries), "checkpointed=" + checkpointed);
            } finally {
                recovered.shutdown();
            }
        }
    }
}
