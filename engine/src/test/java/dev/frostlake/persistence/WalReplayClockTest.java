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
import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.http.SessionContext;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A replay from the write-ahead log reproduces what the statement clock gave the original run. Notebooks two
 * sessions created on two threads got other url_ids after recovery, because their statements shared an instant
 * the replay could not tell apart. Writes that read time travel at CURRENT_TIMESTAMP() or at an OFFSET were lost,
 * because the replay stamped the tables' history with the recovery's wall clock while pinning the recorded instant.
 */
public class WalReplayClockTest {

    private Path dir;
    private Path walFile;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("wal_clock_");
        walFile = dir.resolve("wal.log");
    }

    @AfterEach
    public void tearDown() throws IOException {
        Files.deleteIfExists(walFile);
        Files.deleteIfExists(dir);
    }

    private EngineConfig walConfig() {
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_ENABLED, "true");
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_FILE, walFile.toString());
        return cfg;
    }

    private static String rows(final DatabaseEngine engine, final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(row.getValues());
        }
        return out.toString();
    }

    private static Map<String, String> urlIds(final DatabaseEngine engine) {
        engine.execute("SHOW NOTEBOOKS IN SCHEMA nb.PUBLIC");
        final ResultSet listed = engine.executeQuery(
            "SELECT \"name\", \"url_id\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        final Map<String, String> ids = new TreeMap<>();
        for (final Row row : listed.getRows()) {
            ids.put(String.valueOf(row.getValue(0)), String.valueOf(row.getValue(1)));
        }
        return ids;
    }

    /** Run one statement of a session on a thread of its own, and wait for it. */
    private static void onThread(final ConcurrentDatabaseEngine engine, final String sql, final SessionContext session)
            throws InterruptedException {
        final RuntimeException[] failure = new RuntimeException[1];
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    engine.executeQuery(sql, session);
                } catch (final RuntimeException e) {
                    failure[0] = e;
                }
            }
        });
        thread.start();
        thread.join();
        if (failure[0] != null) {
            throw failure[0];
        }
    }

    @Test
    public void tokensTwoSessionsDrewSurviveRecovery() throws InterruptedException {
        final ConcurrentDatabaseEngine original = new ConcurrentDatabaseEngine(walConfig());
        final SessionContext a = original.createSession();
        final SessionContext b = original.createSession();
        onThread(original, "CREATE DATABASE nb", a);
        for (int i = 0; i < 50; i++) {
            onThread(original, "CREATE NOTEBOOK nb.PUBLIC.na" + i, a);
            onThread(original, "CREATE NOTEBOOK nb.PUBLIC.nb" + i, b);
        }
        final Map<String, String> before = urlIds(original.getEngine());
        original.shutdown();

        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertEquals(100, before.size());
            assertEquals(100, new HashSet<>(before.values()).size(), "each notebook its own url_id");
            assertEquals(before, urlIds(recovered), "recovery hands out the url_ids the original run did");
        } finally {
            recovered.shutdown();
        }
    }

    @Test
    public void timeTravelReadsReplayAsTheyRan() throws InterruptedException {
        final DatabaseEngine original = new DatabaseEngine(walConfig());
        original.execute("CREATE DATABASE db");
        original.execute("USE SCHEMA db.PUBLIC");
        original.execute("CREATE TABLE src (id INT)");
        original.execute("INSERT INTO src VALUES (1), (2)");
        original.execute("CREATE TABLE at_now (id INT)");
        original.execute("CREATE TABLE at_offset (id INT)");
        original.execute("INSERT INTO at_now SELECT id FROM src AT(TIMESTAMP => CURRENT_TIMESTAMP())");
        Thread.sleep(1100L);
        original.execute("INSERT INTO src VALUES (3)");
        original.execute("INSERT INTO at_offset SELECT id FROM src AT(OFFSET => -1)");
        original.execute("CREATE TABLE ctas AS SELECT * FROM src AT(TIMESTAMP => CURRENT_TIMESTAMP())");
        final String[] reads = {
            "SELECT id FROM db.PUBLIC.at_now ORDER BY id",
            "SELECT id FROM db.PUBLIC.at_offset ORDER BY id",
            "SELECT id FROM db.PUBLIC.ctas ORDER BY id",
        };
        final String[] expected = {"[1][2]", "[1][2]", "[1][2][3]"};
        for (int i = 0; i < reads.length; i++) {
            assertEquals(expected[i], rows(original, reads[i]), "original: " + reads[i]);
        }
        original.shutdown();

        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            for (int i = 0; i < reads.length; i++) {
                assertEquals(expected[i], rows(recovered, reads[i]), "recovered: " + reads[i]);
            }
        } finally {
            recovered.shutdown();
        }
    }
}
