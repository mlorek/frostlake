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

import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.http.SessionContext;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A WAL checkpoint waits for every session's open transaction that has statements in the log: taken while one is
 * open, it would hold what other sessions committed since those statements ran, and recovery would replay them over
 * it. An explicit checkpoint is skipped, and the automatic one is tried again after each statement until the
 * transaction ends.
 */
public class WalCheckpointOpenTransactionTest {

    private static final String[] CHECKS = {
        "SELECT s FROM d.PUBLIC.copied ORDER BY s",
        "SELECT s FROM d.PUBLIC.cur",
    };

    private Path dir;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("wal_checkpoint_open_");
    }

    @AfterEach
    public void tearDown() {
        deleteRecursively(dir.toFile());
    }

    /** Remove a test's data directory whole: the engine keeps its stages and checkpoints in directories inside it. */
    private static void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    private EngineConfig walConfig(final int checkpointInterval) {
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_ENABLED, "true");
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_FILE, dir.resolve("wal.log").toString());
        cfg.setProperty(EngineConfig.PROP_DURABILITY_CHECKPOINT_INTERVAL, String.valueOf(checkpointInterval));
        return cfg;
    }

    /** The newest checkpoint's sequence number, 0 before the first. */
    private int newestCheckpoint() {
        final File[] made = dir.resolve("wal-checkpoints").toFile().listFiles();
        int newest = 0;
        if (made != null) {
            for (final File checkpoint : made) {
                if (checkpoint.getName().startsWith("cp-")) {
                    newest = Math.max(newest, Integer.parseInt(checkpoint.getName().substring(3)));
                }
            }
        }
        return newest;
    }

    /** A statement's answer on one line: rows as "a, b | c, d", or the refusal. */
    private static String answer(final ConcurrentDatabaseEngine engine, final SessionContext session,
                                 final String sql) {
        try {
            final StringBuilder rows = new StringBuilder();
            for (final Row row : engine.executeQuery(sql, session).getRows()) {
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

    private static String answers(final ConcurrentDatabaseEngine engine) {
        final SessionContext reader = engine.createSession();
        final StringBuilder out = new StringBuilder();
        for (final String check : CHECKS) {
            out.append(check).append(" => ").append(answer(engine, reader, check)).append('\n');
        }
        return out.toString();
    }

    /** Runs each line in the session its first cell names, every one of them answering. */
    private static void run(final ConcurrentDatabaseEngine engine, final Map<String, SessionContext> sessions,
                            final String[][] script) {
        for (final String[] line : script) {
            SessionContext session = sessions.get(line[0]);
            if (session == null) {
                session = engine.createSession();
                sessions.put(line[0], session);
            }
            final String answered = answer(engine, session, line[1]);
            assertTrue(!answered.startsWith("REFUSED"), line[1] + " => " + answered);
        }
    }

    /** The two sessions' setup: A has read the table B then changes, inside a transaction still open. */
    private static void openTransactionBesideACommit(final ConcurrentDatabaseEngine engine,
                                                     final Map<String, SessionContext> sessions) {
        run(engine, sessions, new String[][] {
            {"A", "CREATE DATABASE d"},
            {"A", "USE SCHEMA d.PUBLIC"},
            {"B", "USE SCHEMA d.PUBLIC"},
            {"A", "CREATE TABLE cur (s VARCHAR)"},
            {"A", "INSERT INTO cur VALUES ('old')"},
            {"A", "CREATE TABLE copied (s VARCHAR)"},
            {"A", "BEGIN"},
            {"A", "INSERT INTO copied SELECT s FROM cur"},
            {"B", "UPDATE cur SET s = 'new'"},
        });
    }

    /** What the checks answer before a restart, which the engine recovering from the log answers again. */
    private void assertRecovered(final String before, final int checkpointInterval) {
        final ConcurrentDatabaseEngine recovered = new ConcurrentDatabaseEngine(walConfig(checkpointInterval));
        try {
            assertEquals(before, answers(recovered));
            assertTrue(recovered.getEngine().getWalReplayFailures().isEmpty(),
                String.valueOf(recovered.getEngine().getWalReplayFailures()));
        } finally {
            recovered.shutdown();
        }
    }

    @Test
    public void aCheckpointWaitsForAnotherSessionsOpenTransaction() {
        final ConcurrentDatabaseEngine engine = new ConcurrentDatabaseEngine(walConfig(0));
        final String before;
        try {
            final Map<String, SessionContext> sessions = new HashMap<>();
            openTransactionBesideACommit(engine, sessions);
            // The calling thread holds no transaction; session A's still has a statement in the log.
            engine.getEngine().checkpoint();
            assertEquals(0, newestCheckpoint());
            run(engine, sessions, new String[][] {{"A", "COMMIT"}});
            before = answers(engine);
            engine.getEngine().checkpoint();
            assertEquals(1, newestCheckpoint());
        } finally {
            engine.shutdown();
        }
        assertTrue(before.contains("copied ORDER BY s => old\n"), before);
        assertTrue(before.contains("cur => new\n"), before);
        assertRecovered(before, 0);
    }

    @Test
    public void theAutomaticCheckpointComesOnceTheTransactionEnds() {
        final ConcurrentDatabaseEngine engine = new ConcurrentDatabaseEngine(walConfig(1));
        final String before;
        try {
            final Map<String, SessionContext> sessions = new HashMap<>();
            openTransactionBesideACommit(engine, sessions);
            final int held = newestCheckpoint();
            // Another commit while A's transaction is open would checkpoint at an interval of one; it waits.
            run(engine, sessions, new String[][] {{"B", "UPDATE cur SET s = 'newer'"}});
            assertEquals(held, newestCheckpoint());
            run(engine, sessions, new String[][] {{"A", "COMMIT"}});
            assertTrue(newestCheckpoint() > held, "no checkpoint after the transaction ended");
            before = answers(engine);
        } finally {
            engine.shutdown();
        }
        assertTrue(before.contains("copied ORDER BY s => old\n"), before);
        assertTrue(before.contains("cur => newer\n"), before);
        assertRecovered(before, 1);
    }
}
