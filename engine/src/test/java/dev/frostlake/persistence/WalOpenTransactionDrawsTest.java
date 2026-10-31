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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A value a sequence or an IDENTITY column handed out inside a transaction stays drawn when the transaction never
 * ends by a statement: a shutdown rolls it back, a crash leaves it open, and neither writes its record. Documented:
 * "Sequence values are not rolled back if a transaction is rolled back", and values are unique; an IDENTITY column
 * draws from a sequence of its own. The write-ahead log marks where the counters stand when the drawing statement
 * ends, and the replay moves them on from there, in the place the mark was logged.
 */
public class WalOpenTransactionDrawsTest {

    private Path dir;

    /** The engines a test leaves running as a process that ended would, shut down once the test is over. */
    private final List<ConcurrentDatabaseEngine> crashed = new ArrayList<ConcurrentDatabaseEngine>();

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("wal_open_draws_");
    }

    @AfterEach
    public void tearDown() {
        for (final ConcurrentDatabaseEngine engine : crashed) {
            engine.shutdown();
        }
        deleteRecursively(dir.toFile());
    }

    /** Remove a test's data directory whole: the engine keeps its checkpoints in a directory inside it. */
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

    /** An engine the test leaves running when it restarts, as a process that ended would leave its log. */
    private ConcurrentDatabaseEngine crashingEngine() {
        final ConcurrentDatabaseEngine engine = new ConcurrentDatabaseEngine(walConfig());
        crashed.add(engine);
        return engine;
    }

    /** An engine recovered from the log, its replay checked to have re-applied everything. */
    private ConcurrentDatabaseEngine recovered() {
        final ConcurrentDatabaseEngine engine = new ConcurrentDatabaseEngine(walConfig());
        assertTrue(engine.getEngine().getWalReplayFailures().isEmpty(),
            String.valueOf(engine.getEngine().getWalReplayFailures()));
        return engine;
    }

    /** A session's statements, answering the last one's first cell. */
    private static String run(final ConcurrentDatabaseEngine engine, final SessionContext session,
                              final String... statements) {
        String last = null;
        for (final String statement : statements) {
            final List<Row> rows = engine.executeQuery(statement, session).getRows();
            last = rows.isEmpty() ? null : String.valueOf(rows.get(0).getValue(0));
        }
        return last;
    }

    private static String run(final ConcurrentDatabaseEngine engine, final String... statements) {
        return run(engine, engine.createSession(), statements);
    }

    /** Draws a value from s and an id from ai in a transaction the session leaves open. */
    private static void drawInAnOpenTransaction(final ConcurrentDatabaseEngine engine, final SessionContext session) {
        assertEquals("1", run(engine, session, "USE SCHEMA d.PUBLIC", "BEGIN", "SELECT s.NEXTVAL"));
        run(engine, session, "INSERT INTO ai (v) VALUES (1)");
        assertEquals("1", run(engine, session, "SELECT id FROM ai WHERE v = 1"));
    }

    private static void createObjects(final ConcurrentDatabaseEngine engine) {
        run(engine, "CREATE DATABASE d", "USE SCHEMA d.PUBLIC", "CREATE SEQUENCE s",
            "CREATE TABLE ai (id INT AUTOINCREMENT, v INT)");
    }

    /** After a restart s serves 2, and ai's next row is id 2. */
    private void assertNothingHandedOutAgain() {
        final ConcurrentDatabaseEngine engine = recovered();
        try {
            final SessionContext session = engine.createSession();
            assertEquals("2", run(engine, session, "USE SCHEMA d.PUBLIC", "SELECT s.NEXTVAL"));
            run(engine, session, "INSERT INTO ai (v) VALUES (2)");
            assertEquals("2", run(engine, session, "SELECT id FROM ai WHERE v = 2"));
            assertEquals("1", run(engine, session, "SELECT COUNT(*) FROM ai"));
        } finally {
            engine.shutdown();
        }
    }

    @Test
    public void drawsOfATransactionOpenAtShutdownAreNotHandedOutAgain() {
        final ConcurrentDatabaseEngine engine = new ConcurrentDatabaseEngine(walConfig());
        try {
            createObjects(engine);
            drawInAnOpenTransaction(engine, engine.createSession());
        } finally {
            engine.shutdown();
        }
        assertNothingHandedOutAgain();
    }

    @Test
    public void drawsOfATransactionOpenAtACrashAreNotHandedOutAgain() {
        final ConcurrentDatabaseEngine engine = crashingEngine();
        createObjects(engine);
        drawInAnOpenTransaction(engine, engine.createSession());
        assertNothingHandedOutAgain();
    }

    @Test
    public void aCommitOfAnotherSessionAfterTheDrawsReplaysInPlace() {
        final ConcurrentDatabaseEngine engine = crashingEngine();
        createObjects(engine);
        drawInAnOpenTransaction(engine, engine.createSession());
        // Another session draws and commits after the open transaction's draws: its values are logged with it.
        assertEquals("2", run(engine, "USE SCHEMA d.PUBLIC", "SELECT s.NEXTVAL"));
        run(engine, "INSERT INTO d.PUBLIC.ai (v) VALUES (7)");
        final ConcurrentDatabaseEngine again = recovered();
        try {
            final SessionContext session = again.createSession();
            assertEquals("2", run(again, session, "USE SCHEMA d.PUBLIC", "SELECT id FROM ai WHERE v = 7"));
            assertEquals("3", run(again, session, "SELECT s.NEXTVAL"));
            run(again, session, "INSERT INTO ai (v) VALUES (8)");
            assertEquals("3", run(again, session, "SELECT id FROM ai WHERE v = 8"));
        } finally {
            again.shutdown();
        }
    }

    @Test
    public void aMarkMovesTheCounterItWasLoggedForAndNoLaterOne() {
        final ConcurrentDatabaseEngine engine = crashingEngine();
        run(engine, "CREATE DATABASE d", "USE SCHEMA d.PUBLIC", "CREATE SEQUENCE s", "CREATE SEQUENCE r");
        final SessionContext open = engine.createSession();
        assertEquals("1", run(engine, open, "USE SCHEMA d.PUBLIC", "BEGIN", "SELECT s.NEXTVAL"));
        assertEquals("1", run(engine, open, "SELECT r.NEXTVAL"));
        // After the draws, s is renamed and r dropped and created again: the marks are s's and the old r's.
        run(engine, "USE SCHEMA d.PUBLIC", "ALTER SEQUENCE s RENAME TO s2", "DROP SEQUENCE r", "CREATE SEQUENCE r");
        final ConcurrentDatabaseEngine again = recovered();
        try {
            final SessionContext session = again.createSession();
            assertEquals("2", run(again, session, "USE SCHEMA d.PUBLIC", "SELECT s2.NEXTVAL"));
            assertEquals("1", run(again, session, "SELECT r.NEXTVAL"));
        } finally {
            again.shutdown();
        }
    }

    @Test
    public void aCheckpointAfterTheDrawsKeepsThem() {
        final ConcurrentDatabaseEngine engine = crashingEngine();
        createObjects(engine);
        drawInAnOpenTransaction(engine, engine.createSession());
        // A checkpoint another session takes truncates the log, marks included; its snapshot keeps the counters.
        engine.getEngine().checkpoint();
        assertNothingHandedOutAgain();
    }
}
