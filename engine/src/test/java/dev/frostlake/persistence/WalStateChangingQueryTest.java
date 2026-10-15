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
 * A query that changes what the write-ahead log must carry is logged: a SELECT of a sequence's NEXTVAL moves the
 * sequence, and SYSTEM$TASK_DEPENDENTS_ENABLE resumes a task graph. A statement reading the query its session ran
 * last by RESULT_SCAN has that query logged before it, so the replay reads the same rows again.
 */
public class WalStateChangingQueryTest {

    private Path dir;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("wal_queries_");
    }

    @AfterEach
    public void tearDown() throws IOException {
        final File[] files = dir.toFile().listFiles();
        if (files != null) {
            for (final File file : files) {
                Files.deleteIfExists(file.toPath());
            }
        }
        Files.deleteIfExists(dir);
    }

    private EngineConfig walConfig() {
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_ENABLED, "true");
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_FILE, dir.resolve("wal.log").toString());
        return cfg;
    }

    /** A statement's answer on one line: rows as "a, b | c, d", or the refusal. */
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

    private static String answers(final DatabaseEngine engine, final String[] checks) {
        final StringBuilder out = new StringBuilder();
        for (final String check : checks) {
            out.append(check).append(" => ").append(answer(engine, check)).append('\n');
        }
        return out.toString();
    }

    @Test
    public void aQueryThatChangesStateIsReplayed() {
        final String[] checks = {
            "SELECT id, note FROM d.PUBLIC.ord ORDER BY id",
            "SELECT name FROM d.PUBLIC.audit_tbl",
            "SHOW SEQUENCES LIKE 'SQ' IN SCHEMA d.PUBLIC ->> SELECT \"next_value\" FROM $1",
            "SHOW TASKS LIKE 'TK%' IN SCHEMA d.PUBLIC ->> SELECT \"name\", \"state\" FROM $1 ORDER BY 1",
        };
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        final String before;
        try {
            final String[] script = {
                "CREATE DATABASE d",
                "USE SCHEMA d.PUBLIC",
                "CREATE TABLE ord (id INT, note VARCHAR)",
                "CREATE SEQUENCE sq",
                "SELECT sq.NEXTVAL",
                "INSERT INTO ord VALUES (1, 'by hand')",
                "INSERT INTO ord SELECT sq.NEXTVAL, 'from the sequence'",
                "CREATE TABLE audit_tbl (name VARCHAR)",
                "SHOW TABLES LIKE 'ORD'",
                "INSERT INTO audit_tbl SELECT \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "CREATE TASK tka WAREHOUSE = COMPUTE_WH SCHEDULE = '60 MINUTE' AS SELECT 1",
                "CREATE TASK tkb AFTER tka AS SELECT 2",
                "SELECT SYSTEM$TASK_DEPENDENTS_ENABLE('tka')",
            };
            for (final String sql : script) {
                final String answered = answer(engine, sql);
                assertTrue(!answered.startsWith("REFUSED"), sql + " => " + answered);
            }
            before = answers(engine, checks);
        } finally {
            engine.shutdown();
        }
        assertTrue(before.contains("ORDER BY id => 1, by hand | 2, from the sequence\n"), before);
        assertTrue(before.contains("audit_tbl => ORD\n"), before);
        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertEquals(before, answers(recovered, checks));
            assertTrue(recovered.getWalReplayFailures().isEmpty(), String.valueOf(recovered.getWalReplayFailures()));
        } finally {
            recovered.shutdown();
        }
    }
}
