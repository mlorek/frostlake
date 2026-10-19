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
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The objects an engine makes for itself — the system roles, the default user, COMPUTE_WH, the SNOWFLAKE database and
 * its schemas, a configured default database — are created at its start, and a write-ahead log keeps that start: a
 * restart with no checkpoint to read, which makes them again, reports the created_on they had on the engine that
 * began the log, whether the log holds records or none.
 */
public class BuiltInCreatedOnRestartTest {

    /** Long enough that a restart's own instant is a different millisecond from the first start's. */
    private static final long RESTART_GAP_MILLIS = 50L;

    private static final String[] QUERIES = {
        "SHOW ROLES LIKE 'SYSADMIN' ->> SELECT \"name\", \"created_on\" FROM $1",
        "SHOW ROLES LIKE 'PUBLIC' ->> SELECT \"name\", \"created_on\" FROM $1",
        "SHOW USERS ->> SELECT \"name\", \"created_on\" FROM $1 ORDER BY 1",
        "SHOW WAREHOUSES LIKE 'COMPUTE_WH' ->> SELECT \"name\", \"created_on\" FROM $1",
        "SHOW DATABASES LIKE 'SNOWFLAKE' ->> SELECT \"name\", \"created_on\" FROM $1",
        "SHOW SCHEMAS IN DATABASE SNOWFLAKE ->> SELECT \"name\", \"created_on\" FROM $1 ORDER BY 1",
    };

    private Path dir;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("built_in_created_on_");
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

    /** What the queries answer on an engine on the log, and then on a restart on it. */
    private String[] beforeAndAfterRestart(final EngineConfig config, final String[] queries, final boolean writes)
            throws InterruptedException {
        final String before;
        final DatabaseEngine first = new DatabaseEngine(config);
        try {
            if (writes) {
                first.execute("CREATE DATABASE d");
                first.execute("CREATE ROLE r1");
            }
            before = answers(first, queries);
        } finally {
            first.shutdown();
        }
        Thread.sleep(RESTART_GAP_MILLIS);
        final DatabaseEngine restarted = new DatabaseEngine(config);
        try {
            assertFalse(answer(restarted, "SHOW ROLES LIKE 'SYSADMIN'").startsWith("REFUSED"));
            return new String[] {before, answers(restarted, queries)};
        } finally {
            restarted.shutdown();
        }
    }

    @Test
    public void aRestartFromALogOfRecordsKeepsTheBuiltInsCreation() throws InterruptedException {
        final String[] answered = beforeAndAfterRestart(walConfig(), QUERIES, true);
        assertEquals(answered[0], answered[1]);
    }

    @Test
    public void aRestartFromALogOfNoRecordsKeepsTheBuiltInsCreation() throws InterruptedException {
        final String[] answered = beforeAndAfterRestart(walConfig(), QUERIES, false);
        assertEquals(answered[0], answered[1]);
    }

    @Test
    public void aRestartKeepsAConfiguredDefaultDatabasesCreation() throws InterruptedException {
        final EngineConfig config = walConfig();
        config.setProperty(EngineConfig.PROP_DATABASE_DEFAULT, "APPDB");
        config.setProperty(EngineConfig.PROP_SCHEMA_DEFAULT, "APP");
        final String[] queries = {
            "SHOW DATABASES LIKE 'APPDB' ->> SELECT \"name\", \"created_on\" FROM $1",
            "SHOW SCHEMAS LIKE 'APP' IN DATABASE APPDB ->> SELECT \"name\", \"created_on\" FROM $1",
            "SHOW DATABASES LIKE 'SNOWFLAKE' ->> SELECT \"name\", \"created_on\" FROM $1",
        };
        final String[] answered = beforeAndAfterRestart(config, queries, true);
        assertEquals(answered[0], answered[1]);
    }
}
