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
 * An engine recovering from the write-ahead log starts with a clean session. The replay ran through the session the
 * engine starts with, so that session began holding the replayed statements' query IDs and history rows, the
 * variables they SET, the parameters they ALTERed and the role they USEd.
 */
public class WalReplaySessionTest {

    private Path dir;
    private Path walFile;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("wal_session_");
        walFile = dir.resolve("wal.log");
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

    private static String refusal(final DatabaseEngine engine, final String sql) {
        try {
            engine.executeQuery(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    @Test
    public void theRecoveredEngineStartsWithACleanSession() {
        final DatabaseEngine fresh = new DatabaseEngine(walConfig());
        final String timezone = rows(fresh, "SHOW PARAMETERS LIKE 'TIMEZONE' IN SESSION");
        final String role = rows(fresh, "SELECT CURRENT_ROLE()");
        fresh.execute("CREATE DATABASE d");
        fresh.execute("USE DATABASE d");
        fresh.execute("CREATE TABLE t (a INT)");
        fresh.execute("INSERT INTO t VALUES (1)");
        fresh.execute("SET myvar = 5");
        fresh.execute("ALTER SESSION SET TIMEZONE = 'Asia/Tokyo'");
        fresh.execute("USE ROLE ACCOUNTADMIN");
        fresh.execute("INSERT INTO t SELECT $myvar");
        fresh.shutdown();

        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertEquals("0", rows(recovered,
                "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY())"
                    + " WHERE execution_status <> 'RUNNING'").replaceAll("[\\[\\]]", ""),
                "no replayed statement is in the history");
            assertEquals("[true]", rows(recovered, "SELECT LAST_QUERY_ID(-2) IS NULL AND LAST_QUERY_ID(2) IS NULL"
                + " AND LAST_QUERY_ID(1) = LAST_QUERY_ID()"), "the only query ID is the count's above");
            assertTrue(refusal(recovered, "SELECT $myvar").contains("does not exist"));
            assertEquals(timezone, rows(recovered, "SHOW PARAMETERS LIKE 'TIMEZONE' IN SESSION"));
            assertEquals(role, rows(recovered, "SELECT CURRENT_ROLE()"));
            assertEquals("[1][5]", rows(recovered, "SELECT a FROM d.PUBLIC.t ORDER BY a"),
                "the replay itself still read the variable it SET");
            assertTrue(recovered.getWalReplayFailures().isEmpty());
        } finally {
            recovered.shutdown();
        }
    }
}
