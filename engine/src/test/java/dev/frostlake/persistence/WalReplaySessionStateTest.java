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
 * A logged statement replays under the variables and parameters its session held when it first ran, which the
 * write-ahead log keeps with it: a variable and a time zone the session set before a checkpoint, which the log no
 * longer holds the statements of, are in force again. ALTER SESSION SET AUTOCOMMIT replays for its own session's
 * transactions only, so another session's statements still commit as they did.
 */
public class WalReplaySessionStateTest {

    private Path dir;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("wal_session_state_");
    }

    @AfterEach
    public void tearDown() throws IOException {
        deleteRecursively(dir.toFile());
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
        return cfg;
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

    private static String answers(final ConcurrentDatabaseEngine engine, final String[] checks) {
        final SessionContext reader = engine.createSession();
        final StringBuilder out = new StringBuilder();
        for (final String check : checks) {
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

    /** What the checks answer before a restart, which the engine recovering from the log answers again. */
    private void assertRecovered(final String before, final String[] checks) {
        final ConcurrentDatabaseEngine recovered = new ConcurrentDatabaseEngine(walConfig());
        try {
            assertEquals(before, answers(recovered, checks));
            assertTrue(recovered.getEngine().getWalReplayFailures().isEmpty(),
                String.valueOf(recovered.getEngine().getWalReplayFailures()));
        } finally {
            recovered.shutdown();
        }
    }

    @Test
    public void whatASessionSetBeforeACheckpointIsInForceAgain() {
        final String[] checks = {
            "SELECT v FROM d.PUBLIC.vt",
            "SELECT s FROM d.PUBLIC.tz",
        };
        final ConcurrentDatabaseEngine engine = new ConcurrentDatabaseEngine(walConfig());
        final String before;
        try {
            final Map<String, SessionContext> sessions = new HashMap<String, SessionContext>();
            run(engine, sessions, new String[][] {
                {"A", "CREATE DATABASE d"},
                {"A", "USE SCHEMA d.PUBLIC"},
                {"A", "SET v = 'set before the checkpoint'"},
                {"A", "ALTER SESSION SET TIMEZONE = 'Asia/Tokyo'"},
            });
            engine.getEngine().checkpoint();
            run(engine, sessions, new String[][] {
                {"A", "CREATE TABLE vt AS SELECT $v AS v"},
                {"A", "CREATE TABLE tz AS SELECT TO_VARCHAR('2024-01-01 00:00:00 +00:00'::TIMESTAMP_TZ"
                    + "::TIMESTAMP_LTZ) AS s"},
            });
            before = answers(engine, checks);
        } finally {
            engine.shutdown();
        }
        assertTrue(before.contains("vt => set before the checkpoint\n"), before);
        assertTrue(before.contains("tz => 2024-01-01 09:00:00.000 +0900\n"), before);
        assertRecovered(before, checks);
    }

    @Test
    public void anAutocommitSwitchReachesItsOwnSessionOnly() {
        final String[] checks = {
            "SELECT a FROM d.PUBLIC.t ORDER BY a",
        };
        final ConcurrentDatabaseEngine engine = new ConcurrentDatabaseEngine(walConfig());
        final String before;
        try {
            run(engine, new HashMap<String, SessionContext>(), new String[][] {
                {"A", "CREATE DATABASE d"},
                {"A", "USE SCHEMA d.PUBLIC"},
                {"B", "USE SCHEMA d.PUBLIC"},
                {"A", "CREATE TABLE t (a INT)"},
                {"A", "ALTER SESSION SET AUTOCOMMIT = FALSE"},
                {"A", "INSERT INTO t VALUES (1)"},
                {"A", "COMMIT"},
                {"B", "INSERT INTO t VALUES (2)"},
                {"A", "INSERT INTO t VALUES (3)"},
                {"A", "COMMIT"},
                {"B", "INSERT INTO t VALUES (4)"},
            });
            before = answers(engine, checks);
        } finally {
            engine.shutdown();
        }
        assertTrue(before.contains("ORDER BY a => 1 | 2 | 3 | 4\n"), before);
        assertRecovered(before, checks);
    }
}
