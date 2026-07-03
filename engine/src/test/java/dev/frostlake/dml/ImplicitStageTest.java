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

package dev.frostlake.dml;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.storage.ResultSet;

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
 * Tests the implicit internal stages — the user stage {@code @~} and per-table stages {@code @%table} —
 * which have no CREATE STAGE / URL and resolve to directories beneath a configurable internal-stage root
 * ({@code stage.internal.localRoot}). Each stage round-trips: a COPY unload writes a file, a COPY load reads
 * it back.
 */
public class ImplicitStageTest {

    private DatabaseEngine engine;
    private Path internalRoot;

    @BeforeEach
    public void setUp() throws IOException {
        internalRoot = Files.createTempDirectory("implicit_stage_test_");
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_STAGE_INTERNAL_LOCAL_ROOT, internalRoot.toString());
        engine = new DatabaseEngine(cfg);
        engine.execute("CREATE DATABASE db");
        engine.execute("USE DATABASE db");
        engine.execute("CREATE SCHEMA s");
        engine.execute("USE SCHEMA s");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
        deleteRecursively(internalRoot.toFile());
    }

    private long count(final String table) {
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM " + table);
        return ((Number) rs.getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void userStageRoundTrip() {
        engine.execute("CREATE TABLE src (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO src VALUES (1, 'Alice'), (2, 'Bob')");

        // Unload into the current user's stage @~, then load it back into another table.
        final ResultSet unload = engine.executeQuery("COPY INTO @~ FROM src FILE_FORMAT = (TYPE = CSV)");
        assertEquals(2, ((Number) unload.getRows().get(0).getValue(0)).intValue());

        engine.execute("CREATE TABLE dst (id INTEGER, name VARCHAR)");
        engine.execute("COPY INTO dst FROM @~ FILE_FORMAT = (TYPE = CSV)");
        assertEquals(2, count("dst"));
    }

    @Test
    public void userStageWritesUnderUsersDir() {
        engine.execute("CREATE TABLE src (id INTEGER)");
        engine.execute("INSERT INTO src VALUES (7)");

        engine.executeQuery("COPY INTO @~/out FROM src FILE_FORMAT = (TYPE = CSV)");

        // @~ resolves under <internalRoot>/users/<user>/...
        assertTrue(internalRoot.resolve("users").toFile().isDirectory(),
            "user stage should write beneath <root>/users/<user>");
    }

    @Test
    public void tableStageRoundTrip() {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO t VALUES (1, 'Alice'), (2, 'Bob'), (3, 'Carol')");

        // @%t is the table t's own implicit stage.
        final ResultSet unload = engine.executeQuery("COPY INTO @%t FROM t FILE_FORMAT = (TYPE = CSV)");
        assertEquals(3, ((Number) unload.getRows().get(0).getValue(0)).intValue());

        engine.execute("CREATE TABLE t2 (id INTEGER, name VARCHAR)");
        engine.execute("COPY INTO t2 FROM @%t FILE_FORMAT = (TYPE = CSV)");
        assertEquals(3, count("t2"));
    }

    @Test
    public void tableStageWritesUnderTablesDir() {
        engine.execute("CREATE TABLE billing (id INTEGER)");
        engine.execute("INSERT INTO billing VALUES (1)");

        engine.executeQuery("COPY INTO @%billing FROM billing FILE_FORMAT = (TYPE = CSV)");

        // Table stage resolves under <internalRoot>/tables/<db.schema.table>/...
        assertTrue(internalRoot.resolve("tables").toFile().isDirectory(),
            "table stage should write beneath <root>/tables/<fq-table>");
    }

    @Test
    public void userAndTableStagesAreDistinct() {
        engine.execute("CREATE TABLE a (id INTEGER)");
        engine.execute("INSERT INTO a VALUES (1), (2)");
        engine.execute("CREATE TABLE b (id INTEGER)");
        engine.execute("INSERT INTO b VALUES (9)");

        // Two different stages hold different data; loading each back keeps them separate.
        engine.executeQuery("COPY INTO @~ FROM a FILE_FORMAT = (TYPE = CSV)");
        engine.executeQuery("COPY INTO @%b FROM b FILE_FORMAT = (TYPE = CSV)");

        engine.execute("CREATE TABLE from_user (id INTEGER)");
        engine.execute("CREATE TABLE from_table (id INTEGER)");
        engine.execute("COPY INTO from_user FROM @~ FILE_FORMAT = (TYPE = CSV)");
        engine.execute("COPY INTO from_table FROM @%b FILE_FORMAT = (TYPE = CSV)");

        assertEquals(2, count("from_user"));
        assertEquals(1, count("from_table"));
    }

    private static void deleteRecursively(final File f) {
        if (f == null || !f.exists()) {
            return;
        }
        if (f.isDirectory()) {
            final File[] children = f.listFiles();
            if (children != null) {
                for (final File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        f.delete();
    }
}
