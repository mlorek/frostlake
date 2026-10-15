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
 * A table's time-travel history is part of the engine's state a copy keeps: an engine clone, a checkpoint's restore
 * and a write-ahead log's recovery after a checkpoint read a table at a point before the copy as the engine they
 * copy did — an AT and a BEFORE point, a CHANGES clause and a stream over it, and the refusal of a point before the
 * table was created — and a logged write that read such a point replays as it ran.
 */
public class TimeTravelHistoryRestoreTest {

    /** How far apart a point is kept from the writes around it, so each falls in a millisecond of its own. */
    private static final long GAP_MILLIS = 30L;

    private Path dir;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("time_travel_history_");
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

    private static String at(final String kind, final long millis) {
        return kind + "(TIMESTAMP => TO_TIMESTAMP_LTZ(" + millis + ", 3))";
    }

    /**
     * Gives d.PUBLIC.t a history — two rows, then one updated, one deleted and one inserted, with a stream taken
     * between — and answers the queries that read it.
     */
    private static String[] writeHistory(final DatabaseEngine engine) throws InterruptedException {
        engine.execute("CREATE DATABASE d");
        final long beforeTable = pointBetweenWrites();
        engine.execute("CREATE TABLE d.PUBLIC.t (id INT, v VARCHAR) CHANGE_TRACKING = TRUE");
        final long empty = pointBetweenWrites();
        engine.execute("INSERT INTO d.PUBLIC.t VALUES (1, 'a'), (2, 'b')");
        final long two = pointBetweenWrites();
        engine.execute("CREATE STREAM d.PUBLIC.st ON TABLE d.PUBLIC.t");
        engine.execute("UPDATE d.PUBLIC.t SET v = 'a2' WHERE id = 1");
        engine.execute("DELETE FROM d.PUBLIC.t WHERE id = 2");
        engine.execute("INSERT INTO d.PUBLIC.t VALUES (3, 'c')");
        return new String[] {
            "SELECT id, v FROM d.PUBLIC.t ORDER BY id",
            "SELECT COUNT(*) FROM d.PUBLIC.t " + at("AT", beforeTable),
            "SELECT COUNT(*) FROM d.PUBLIC.t " + at("AT", empty),
            "SELECT id, v FROM d.PUBLIC.t " + at("AT", two) + " ORDER BY id",
            "SELECT COUNT(*) FROM d.PUBLIC.t " + at("BEFORE", two),
            "SELECT id, v, METADATA$ACTION, METADATA$ISUPDATE FROM d.PUBLIC.t CHANGES(INFORMATION => DEFAULT) "
                + at("AT", two) + " ORDER BY id, METADATA$ACTION",
            "SELECT id, v, METADATA$ACTION, METADATA$ISUPDATE FROM d.PUBLIC.st ORDER BY id, METADATA$ACTION",
        };
    }

    /** The source and its copy answer alike, before and after each writes the same row again. */
    private static void assertCopyReadsAlike(final DatabaseEngine source, final DatabaseEngine copy,
                                             final String[] queries) {
        final String expected = answers(source, queries);
        assertTrue(expected.contains(" ORDER BY id => 1, a | 2, b\n"), expected);
        assertTrue(expected.contains(" ORDER BY id, METADATA$ACTION => 1, a, DELETE, true | 1, a2, INSERT, true"
            + " | 2, b, DELETE, false | 3, c, INSERT, false\n"), expected);
        assertEquals(expected, answers(copy, queries));
        source.execute("UPDATE d.PUBLIC.t SET v = 'c2' WHERE id = 3");
        copy.execute("UPDATE d.PUBLIC.t SET v = 'c2' WHERE id = 3");
        assertEquals(answers(source, queries), answers(copy, queries));
    }

    @Test
    public void aCloneReadsThePastAsTheEngineItCopies() throws Exception {
        final DatabaseEngine source = new DatabaseEngine();
        final String[] queries = writeHistory(source);
        assertCopyReadsAlike(source, source.cloneInstance(), queries);
    }

    @Test
    public void aCheckpointRestoreReadsThePastAsTheEngineItCopies() throws Exception {
        final DatabaseEngine source = new DatabaseEngine();
        final String[] queries = writeHistory(source);
        source.checkpointStateTo(dir.resolve("state"));
        final DatabaseEngine restored = new DatabaseEngine();
        restored.restoreStateFrom(dir.resolve("state"));
        assertCopyReadsAlike(source, restored, queries);
    }

    @Test
    public void aWalRecoveryReplaysAWriteThatReadThePastBeforeTheCheckpoint() throws InterruptedException {
        final DatabaseEngine first = new DatabaseEngine(walConfig());
        final String[] queries;
        final String before;
        try {
            first.execute("CREATE DATABASE d");
            first.execute("CREATE TABLE d.PUBLIC.t (a INT)");
            first.execute("INSERT INTO d.PUBLIC.t VALUES (1)");
            final long one = pointBetweenWrites();
            first.execute("INSERT INTO d.PUBLIC.t VALUES (2)");
            first.checkpoint();
            first.execute("INSERT INTO d.PUBLIC.t SELECT a + 10 FROM d.PUBLIC.t " + at("AT", one));
            queries = new String[] {
                "SELECT a FROM d.PUBLIC.t ORDER BY a",
                "SELECT a FROM d.PUBLIC.t " + at("AT", one) + " ORDER BY a",
            };
            before = answers(first, queries);
        } finally {
            first.shutdown();
        }
        assertTrue(before.contains("ORDER BY a => 1 | 2 | 11\n"), before);
        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertEquals(before, answers(recovered, queries));
            assertTrue(recovered.getWalReplayFailures().isEmpty(), String.valueOf(recovered.getWalReplayFailures()));
        } finally {
            recovered.shutdown();
        }
    }
}
