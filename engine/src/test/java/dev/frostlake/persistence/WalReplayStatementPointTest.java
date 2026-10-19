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
 * A logged statement keeps the query ID it first answered with, and its replay answers to it: a CTAS, an
 * INSERT ... SELECT and a CLONE that read a table AT a statement replay as they first ran, and after the restart a
 * read AT that statement still finds it.
 */
public class WalReplayStatementPointTest {

    private Path dir;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("wal_statement_point_");
    }

    @AfterEach
    public void tearDown() {
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

    @Test
    public void aStatementPointOutlivesARestart() {
        final String[] checks = {
            "SELECT (SELECT COUNT(*) FROM d.PUBLIC.t2), (SELECT COUNT(*) FROM d.PUBLIC.t3), "
                + "(SELECT COUNT(*) FROM d.PUBLIC.t4), (SELECT COUNT(*) FROM d.PUBLIC.t)",
        };
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        final String inserted;
        final String before;
        try {
            engine.execute("CREATE DATABASE d");
            engine.execute("USE SCHEMA d.PUBLIC");
            engine.execute("CREATE TABLE t (a INT)");
            engine.execute("CREATE TABLE t4 (a INT)");
            engine.execute("INSERT INTO t VALUES (1)");
            inserted = answer(engine, "SELECT LAST_QUERY_ID()");
            engine.execute("DELETE FROM t");
            engine.execute("CREATE TABLE t2 AS SELECT * FROM t AT(STATEMENT => '" + inserted + "')");
            engine.execute("CREATE TABLE t3 CLONE t AT(STATEMENT => '" + inserted + "')");
            engine.execute("INSERT INTO t4 SELECT * FROM t AT(STATEMENT => '" + inserted + "')");
            before = answer(engine, checks[0]);
        } finally {
            engine.shutdown();
        }
        assertEquals("1, 1, 1, 0", before);
        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertEquals(before, answer(recovered, checks[0]));
            assertTrue(recovered.getWalReplayFailures().isEmpty(), String.valueOf(recovered.getWalReplayFailures()));
            assertEquals("1", answer(recovered, "SELECT COUNT(*) FROM d.PUBLIC.t AT(STATEMENT => '" + inserted
                + "')"));
        } finally {
            recovered.shutdown();
        }
    }
}
