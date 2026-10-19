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
 * A logged statement that reads a result by RESULT_SCAN replays with the rows it first read, kept in its write-ahead
 * log record: whichever query it names, by LAST_QUERY_ID(-2), by a query ID a variable holds or by the query its
 * session ran last, and whatever the table that query read holds by the time the log replays.
 */
public class WalReplayResultScanTest {

    private Path dir;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("wal_result_scan_");
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
    public void aResultScanReadsTheRowsItFirstRead() {
        final String[] checks = {
            "SELECT id, s FROM d.PUBLIC.two_back",
            "SELECT id, s FROM d.PUBLIC.by_id",
            "SELECT c FROM d.PUBLIC.counted",
            "SELECT s FROM d.PUBLIC.src",
        };
        final ConcurrentDatabaseEngine engine = new ConcurrentDatabaseEngine(walConfig());
        final String before;
        try {
            run(engine, new HashMap<String, SessionContext>(), new String[][] {
                {"A", "CREATE DATABASE d"},
                {"A", "USE SCHEMA d.PUBLIC"},
                {"B", "USE SCHEMA d.PUBLIC"},
                {"A", "CREATE TABLE src (id INT, s VARCHAR)"},
                {"A", "INSERT INTO src VALUES (1, 'old')"},
                {"A", "SELECT id, s FROM src"},
                {"A", "SELECT 'between'"},
                {"A", "CREATE TABLE two_back AS SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID(-2)))"},
                {"A", "SELECT id, s FROM src"},
                {"A", "SET qid = LAST_QUERY_ID()"},
                {"B", "UPDATE src SET s = 'new'"},
                {"A", "CREATE TABLE by_id AS SELECT * FROM TABLE(RESULT_SCAN($qid))"},
                {"A", "SELECT COUNT(*) AS c FROM src"},
                {"A", "CREATE TABLE counted AS SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"},
            });
            before = answers(engine, checks);
        } finally {
            engine.shutdown();
        }
        assertTrue(before.contains("two_back => 1, old\n"), before);
        assertTrue(before.contains("by_id => 1, old\n"), before);
        assertTrue(before.contains("counted => 1\n"), before);
        assertRecovered(before, checks);
    }
}
