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
import java.nio.charset.StandardCharsets;
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
    private Path localDir;

    @BeforeEach
    public void setUp() throws IOException {
        internalRoot = Files.createTempDirectory("implicit_stage_test_");
        localDir = Files.createTempDirectory("implicit_stage_local_");
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
        deleteRecursively(localDir.toFile());
    }

    private long count(final String table) {
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM " + table);
        return ((Number) rs.getRows().get(0).getValue(0)).longValue();
    }

    /** Write a CSV outside the stage root and return the {@code file://} URL a PUT can upload from. */
    private String localCsvUrl(final String fileName, final String content) throws IOException {
        final Path file = localDir.resolve(fileName);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return "file://" + file;
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

    /**
     * {@code COPY INTO <table>} with NO FROM clause loads from that table's own stage {@code @%<table>} —
     * the documented local-file load path (PUT the file into the table stage, then COPY it in). Live-verified
     * on a real account: the staged file is loaded and reported LOADED exactly as it is under an
     * explicit {@code FROM @%<table>}.
     */
    @Test
    public void tableStageIsTheDefaultCopySource() throws IOException {
        engine.execute("CREATE TABLE c (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("u.csv", """
            id,name
            7,gina
            8,hank
            """) + " @%c AUTO_COMPRESS=FALSE");

        final ResultSet copied = engine.executeQuery("COPY INTO c FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");

        assertEquals(1, copied.getRows().size(), "the one staged file should be reported");
        assertEquals("u.csv", copied.getRows().get(0).getValue(0));
        assertEquals("LOADED", copied.getRows().get(0).getValue(1));
        assertEquals(2, count("c"));
    }

    /**
     * The no-FROM form shares the explicit path's load history: a second identical COPY re-reads the same
     * staged file and loads nothing, while FORCE = TRUE reloads it (live-verified). The skipped
     * file is not itself reported — with nothing loaded the statement answers with the one-column summary.
     */
    @Test
    public void defaultTableStageCopySkipsAlreadyLoadedFiles() throws IOException {
        engine.execute("CREATE TABLE c (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("u.csv", """
            id,name
            7,gina
            8,hank
            """) + " @%c AUTO_COMPRESS=FALSE");

        engine.executeQuery("COPY INTO c FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");
        assertEquals(2, count("c"));

        final ResultSet again = engine.executeQuery("COPY INTO c FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");
        assertEquals(1, again.getColumns().size());
        assertEquals("Copy executed with 0 files processed.", again.getRows().get(0).getValue(0));
        assertEquals(2, count("c"));

        engine.executeQuery("COPY INTO c FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1) FORCE = TRUE");
        assertEquals(4, count("c"));
    }

    /**
     * An EMPTY table stage is a success, not an error, and it answers with the one-column summary rather than
     * an empty per-file result — live-verified, where the account returned a single {@code status}
     * column holding {@code Copy executed with 0 files processed.} instead of failing the statement.
     */
    @Test
    public void defaultTableStageCopyOnEmptyStageLoadsNothing() {
        engine.execute("CREATE TABLE c (id INTEGER, name VARCHAR)");

        final ResultSet copied = engine.executeQuery("COPY INTO c FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");

        assertEquals(1, copied.getColumns().size(), "an empty table stage answers with one column");
        assertEquals("status", copied.getColumns().get(0).getName());
        assertEquals(1, copied.getRows().size(), "an empty table stage answers with one row");
        assertEquals("Copy executed with 0 files processed.", copied.getRows().get(0).getValue(0));
        assertEquals(0, count("c"));
    }

    /** PATTERN narrows the table stage's files on the no-FROM form just as it does with an explicit FROM. */
    @Test
    public void defaultTableStageCopyHonoursPattern() throws IOException {
        engine.execute("CREATE TABLE c (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("keep.csv", """
            id,name
            7,gina
            8,hank
            """) + " @%c AUTO_COMPRESS=FALSE");
        engine.executeQuery("PUT " + localCsvUrl("skip.csv", """
            id,name
            9,ivy
            """) + " @%c AUTO_COMPRESS=FALSE");

        engine.execute("COPY INTO c PATTERN = '.*keep[.]csv' FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");

        assertEquals(2, count("c"));
    }

    /**
     * A qualified target defaults to ITS OWN stage, not to a same-named table in the current schema —
     * live-verified with exactly this two-schema shape.
     */
    @Test
    public void defaultTableStageCopyUsesTheQualifiedTargetsOwnStage() throws IOException {
        engine.execute("CREATE SCHEMA s2");
        engine.execute("USE SCHEMA s2");
        engine.execute("CREATE TABLE tq (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("q.csv", """
            id,name
            7,gina
            8,hank
            """) + " @%tq AUTO_COMPRESS=FALSE");

        // A same-named table in another schema stages a different file in its own table stage.
        engine.execute("USE SCHEMA s");
        engine.execute("CREATE TABLE tq (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("d.csv", """
            id,name
            99,zoe
            """) + " @%tq AUTO_COMPRESS=FALSE");

        engine.execute("COPY INTO db.s2.tq FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");

        assertEquals(2, count("db.s2.tq"));
        assertEquals(0, count("tq"), "the current schema's same-named table must be untouched");
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
