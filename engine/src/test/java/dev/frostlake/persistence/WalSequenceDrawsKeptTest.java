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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A sequence never hands a value out twice across a restart (documented: values a sequence generates are unique, and
 * are not rolled back with a transaction): a value drawn through a view or a SQL function, one drawn inside a
 * transaction that rolled back and one drawn by a statement that then failed stay drawn, though no record of the
 * write-ahead log carries the statement.
 */
public class WalSequenceDrawsKeptTest {

    private Path dir;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("wal_seq_draws_");
    }

    @AfterEach
    public void tearDown() {
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

    /** A statement's answer: its first row's first cell, or the refusal. */
    private static String answer(final ConcurrentDatabaseEngine engine, final SessionContext session,
                                 final String sql) {
        try {
            final Row first = engine.executeQuery(sql, session).getRows().get(0);
            return String.valueOf(first.getValue(0));
        } catch (final RuntimeException refused) {
            return "REFUSED " + refused.getMessage();
        }
    }

    /** Runs the statements in one session and answers the last one's answer. */
    private static String run(final ConcurrentDatabaseEngine engine, final String... statements) {
        final SessionContext session = engine.createSession();
        String last = null;
        for (final String statement : statements) {
            last = answer(engine, session, statement);
        }
        return last;
    }

    /** The value the sequence draws next after a restart. */
    private String nextAfterRestart() {
        final ConcurrentDatabaseEngine recovered = new ConcurrentDatabaseEngine(walConfig());
        try {
            final String next = run(recovered, "SELECT d.PUBLIC.s.NEXTVAL");
            assertTrue(recovered.getEngine().getWalReplayFailures().isEmpty(),
                String.valueOf(recovered.getEngine().getWalReplayFailures()));
            return next;
        } finally {
            recovered.shutdown();
        }
    }

    @Test
    public void aValueDrawnThroughAViewOrAFunctionStaysDrawn() {
        final ConcurrentDatabaseEngine engine = new ConcurrentDatabaseEngine(walConfig());
        final long viaView;
        final long viaFunction;
        try {
            run(engine, "CREATE DATABASE d", "USE SCHEMA d.PUBLIC", "CREATE SEQUENCE s",
                "CREATE VIEW v AS SELECT s.NEXTVAL AS n", "CREATE FUNCTION f() RETURNS INT AS 's.NEXTVAL'");
            final long first = Long.parseLong(run(engine, "SELECT d.PUBLIC.s.NEXTVAL"));
            viaView = Long.parseLong(run(engine, "SELECT * FROM d.PUBLIC.v"));
            assertEquals(first + 1, viaView);
            viaFunction = Long.parseLong(run(engine, "SELECT d.PUBLIC.f()"));
            assertEquals(viaView + 1, viaFunction);
        } finally {
            engine.shutdown();
        }
        assertEquals(String.valueOf(viaFunction + 1), nextAfterRestart());
    }

    @Test
    public void aValueDrawnInsideARolledBackTransactionStaysDrawn() {
        final ConcurrentDatabaseEngine engine = new ConcurrentDatabaseEngine(walConfig());
        try {
            assertEquals("2", run(engine, "CREATE DATABASE d", "USE SCHEMA d.PUBLIC", "CREATE SEQUENCE s",
                "SELECT s.NEXTVAL", "BEGIN", "SELECT s.NEXTVAL"));
            run(engine, "ROLLBACK");
        } finally {
            engine.shutdown();
        }
        assertEquals("3", nextAfterRestart());
    }

    @Test
    public void aValueDrawnByAStatementThatFailedStaysDrawn() {
        final ConcurrentDatabaseEngine engine = new ConcurrentDatabaseEngine(walConfig());
        try {
            run(engine, "CREATE DATABASE d", "USE SCHEMA d.PUBLIC", "CREATE SEQUENCE s", "CREATE TABLE t (a INT)");
            assertTrue(run(engine, "USE SCHEMA d.PUBLIC", "INSERT INTO t SELECT s.NEXTVAL / 0").startsWith("REFUSED"));
        } finally {
            engine.shutdown();
        }
        assertEquals("2", nextAfterRestart());
    }
}
