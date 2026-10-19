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
 * A transaction's record is appended to the write-ahead log when it commits, after what other sessions committed
 * while it was open, and its statements replay in the places they ran: each reads what it read when it first ran. An
 * INSERT … SELECT, an UPDATE … FROM, a MERGE and a DELETE … USING keep the rows they first read past another
 * session's later commit, a statement that ran after that commit reads it, and two transactions open across the same
 * commit replay beside each other.
 */
public class WalReplayInPlaceTest {

    private Path dir;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("wal_in_place_");
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
    public void aTransactionReadsWhatItReadWhereItRan() {
        final String[] checks = {
            "SELECT s FROM d.PUBLIC.copied ORDER BY s",
            "SELECT s FROM d.PUBLIC.cur",
            "SELECT id, v FROM d.PUBLIC.tgt ORDER BY id",
            "SELECT id, v FROM d.PUBLIC.merged ORDER BY id",
            "SELECT id FROM d.PUBLIC.pruned ORDER BY id",
        };
        final ConcurrentDatabaseEngine engine = new ConcurrentDatabaseEngine(walConfig());
        final String before;
        try {
            run(engine, new HashMap<String, SessionContext>(), new String[][] {
                {"A", "CREATE DATABASE d"},
                {"A", "USE SCHEMA d.PUBLIC"},
                {"B", "USE SCHEMA d.PUBLIC"},
                {"A", "CREATE TABLE cur (s VARCHAR)"},
                {"A", "INSERT INTO cur VALUES ('old')"},
                {"A", "CREATE TABLE copied (s VARCHAR)"},
                {"A", "CREATE TABLE tgt (id INT, v VARCHAR)"},
                {"A", "INSERT INTO tgt VALUES (1, 'none')"},
                {"A", "CREATE TABLE merged (id INT, v VARCHAR)"},
                {"A", "CREATE TABLE pruned (id INT)"},
                {"A", "INSERT INTO pruned VALUES (1), (2)"},
                {"A", "BEGIN"},
                {"A", "INSERT INTO copied SELECT s FROM cur"},
                {"A", "UPDATE tgt SET v = cur.s FROM cur"},
                {"A", "MERGE INTO merged USING cur ON merged.v = cur.s WHEN NOT MATCHED THEN INSERT (id, v) "
                    + "VALUES (1, cur.s)"},
                {"A", "DELETE FROM pruned USING cur WHERE cur.s = 'old' AND pruned.id = 1"},
                {"B", "UPDATE cur SET s = 'new'"},
                {"A", "INSERT INTO copied SELECT s FROM cur"},
                {"A", "COMMIT"},
            });
            before = answers(engine, checks);
        } finally {
            engine.shutdown();
        }
        assertTrue(before.contains("copied ORDER BY s => new | old\n"), before);
        assertTrue(before.contains("tgt ORDER BY id => 1, old\n"), before);
        assertTrue(before.contains("pruned ORDER BY id => 2\n"), before);
        assertRecovered(before, checks);
    }

    @Test
    public void twoTransactionsOpenAcrossOneCommitReplayBesideEachOther() {
        final String[] checks = {
            "SELECT who, s FROM d.PUBLIC.seen ORDER BY who, s",
            "SELECT n FROM d.PUBLIC.counter",
        };
        final ConcurrentDatabaseEngine engine = new ConcurrentDatabaseEngine(walConfig());
        final String before;
        try {
            run(engine, new HashMap<String, SessionContext>(), new String[][] {
                {"A", "CREATE DATABASE d"},
                {"A", "USE SCHEMA d.PUBLIC"},
                {"B", "USE SCHEMA d.PUBLIC"},
                {"C", "USE SCHEMA d.PUBLIC"},
                {"A", "CREATE TABLE counter (n INT)"},
                {"A", "INSERT INTO counter VALUES (1)"},
                {"A", "CREATE TABLE seen (who VARCHAR, s INT)"},
                {"A", "BEGIN"},
                {"C", "BEGIN"},
                {"A", "INSERT INTO seen SELECT 'a', n FROM counter"},
                {"B", "UPDATE counter SET n = 2"},
                {"C", "INSERT INTO seen SELECT 'c', n FROM counter"},
                {"B", "UPDATE counter SET n = 3"},
                {"A", "INSERT INTO seen SELECT 'a', n FROM counter"},
                {"C", "COMMIT"},
                {"B", "UPDATE counter SET n = 4"},
                {"A", "INSERT INTO seen SELECT 'a', n FROM counter"},
                {"A", "COMMIT"},
            });
            before = answers(engine, checks);
        } finally {
            engine.shutdown();
        }
        assertTrue(before.contains("ORDER BY who, s => a, 1 | a, 3 | a, 4 | c, 2\n"), before);
        assertRecovered(before, checks);
    }
}
