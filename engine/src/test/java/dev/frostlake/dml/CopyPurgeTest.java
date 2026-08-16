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
import org.junit.jupiter.api.function.Executable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@code COPY INTO <table> … PURGE = TRUE} — deleting the staged source files a load consumed.
 *
 * <p>Every assertion is made against the ACTUAL on-disk stage directory, never against the COPY result set:
 * live Snowflake reports the identical result columns and rows with and without PURGE, so the result set
 * cannot distinguish a purge that happened from one that did not. Only a subsequent LIST (here, the backing
 * directory) reveals it.
 *
 * <p>The purge rule and every case below were live-verified against a real account on each with
 * a no-PURGE control run beside it.
 */
public class CopyPurgeTest {

    private DatabaseEngine engine;
    private Path internalRoot;
    private Path namedStageDir;
    private Path localDir;

    @BeforeEach
    public void setUp() throws IOException {
        internalRoot = Files.createTempDirectory("copy_purge_root_");
        namedStageDir = Files.createTempDirectory("copy_purge_stage_");
        localDir = Files.createTempDirectory("copy_purge_local_");
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_STAGE_INTERNAL_LOCAL_ROOT, internalRoot.toString());
        // NOTE: command.removeEnabled is left at its default (false) throughout this suite — PURGE is
        // deliberately not gated behind the REMOVE / RM guard, and these tests pin that.
        cfg.setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "true");
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
        deleteRecursively(namedStageDir.toFile());
        deleteRecursively(localDir.toFile());
    }

    private long count(final String table) {
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM " + table);
        return ((Number) rs.getRows().get(0).getValue(0)).longValue();
    }

    /** Write a CSV outside every stage root and return the {@code file://} URL a PUT can upload from. */
    private String localCsvUrl(final String fileName, final String content) throws IOException {
        final Path file = localDir.resolve(fileName);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return "file://" + file;
    }

    /** The directory backing {@code @%<table>} for a table in the test schema. */
    private Path tableStageDir(final String table) {
        return internalRoot.resolve("tables").resolve("DB.S." + table.toUpperCase());
    }

    private void assertStaged(final Path stageDir, final String fileName) {
        assertTrue(Files.exists(stageDir.resolve(fileName)),
            fileName + " should still be on the stage at " + stageDir);
    }

    private void assertPurged(final Path stageDir, final String fileName) {
        assertFalse(Files.exists(stageDir.resolve(fileName)),
            fileName + " should have been purged from the stage at " + stageDir);
    }

    private static final String TWO_GOOD_ROWS = """
        id,name
        1,alice
        2,bob
        """;

    private static final String ONE_GOOD_ONE_BAD_ROW = """
        id,name
        5,eve
        NOTANUM,frank
        """;

    private static final String ALL_BAD_ROWS = """
        id,name
        BAD1,x
        BAD2,y
        """;

    /** The headline case: a clean load with PURGE = TRUE deletes the source file off the stage. */
    @Test
    public void purgeDeletesTheLoadedFile() throws IOException {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("u.csv", TWO_GOOD_ROWS) + " @%t AUTO_COMPRESS=FALSE");
        assertStaged(tableStageDir("t"), "u.csv");

        final ResultSet copied = engine.executeQuery(
            "COPY INTO t FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1) PURGE = TRUE");

        // The result set looks exactly as it does without PURGE — the file is gone all the same.
        assertEquals("LOADED", copied.getRows().get(0).getValue(1));
        assertEquals(2, count("t"));
        assertPurged(tableStageDir("t"), "u.csv");
    }

    /** No PURGE clause at all: the default is FALSE, so the staged file survives the load. */
    @Test
    public void defaultKeepsTheStagedFile() throws IOException {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("u.csv", TWO_GOOD_ROWS) + " @%t AUTO_COMPRESS=FALSE");

        engine.executeQuery("COPY INTO t FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");

        assertEquals(2, count("t"));
        assertStaged(tableStageDir("t"), "u.csv");
    }

    /** PURGE = FALSE spelled out keeps the file, exactly like the default. */
    @Test
    public void purgeFalseKeepsTheStagedFile() throws IOException {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("u.csv", TWO_GOOD_ROWS) + " @%t AUTO_COMPRESS=FALSE");

        engine.executeQuery("COPY INTO t FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1) PURGE = FALSE");

        assertEquals(2, count("t"));
        assertStaged(tableStageDir("t"), "u.csv");
    }

    /**
     * PURGE empties the LOADED set, never the stage: a file PATTERN excluded is untouched. This is the
     * control that proves the purge is scoped to the files the statement read.
     */
    @Test
    public void purgeKeepsFilesExcludedByPattern() throws IOException {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("keep.csv", TWO_GOOD_ROWS) + " @%t AUTO_COMPRESS=FALSE");
        engine.executeQuery("PUT " + localCsvUrl("load.csv", TWO_GOOD_ROWS) + " @%t AUTO_COMPRESS=FALSE");

        engine.executeQuery(
            "COPY INTO t PATTERN = '.*load[.]csv' FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1) PURGE = TRUE");

        assertPurged(tableStageDir("t"), "load.csv");
        assertStaged(tableStageDir("t"), "keep.csv");
    }

    /** Same control for the FILES option: a file not named is not purged. */
    @Test
    public void purgeKeepsFilesExcludedByFilesOption() throws IOException {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("keep.csv", TWO_GOOD_ROWS) + " @%t AUTO_COMPRESS=FALSE");
        engine.executeQuery("PUT " + localCsvUrl("load.csv", TWO_GOOD_ROWS) + " @%t AUTO_COMPRESS=FALSE");

        engine.executeQuery(
            "COPY INTO t FILES = ('load.csv') FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1) PURGE = TRUE");

        assertPurged(tableStageDir("t"), "load.csv");
        assertStaged(tableStageDir("t"), "keep.csv");
    }

    /**
     * A file already in the load history loads nothing this run and is therefore NOT purged — PURGE deletes
     * what this statement consumed, not what some earlier statement did. With nothing loaded the statement
     * answers with the one-column summary rather than reporting the skipped file.
     */
    @Test
    public void purgeKeepsAnAlreadyLoadedFile() throws IOException {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("u.csv", TWO_GOOD_ROWS) + " @%t AUTO_COMPRESS=FALSE");
        engine.executeQuery("COPY INTO t FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");

        final ResultSet again = engine.executeQuery(
            "COPY INTO t FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1) PURGE = TRUE");

        assertEquals(1, again.getColumns().size());
        assertEquals("Copy executed with 0 files processed.", again.getRows().get(0).getValue(0));
        assertEquals(2, count("t"));
        assertStaged(tableStageDir("t"), "u.csv");
    }

    /** FORCE = TRUE re-reads an already-loaded file, so PURGE then does delete it. */
    @Test
    public void forceReloadPurgesAnAlreadyLoadedFile() throws IOException {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("u.csv", TWO_GOOD_ROWS) + " @%t AUTO_COMPRESS=FALSE");
        engine.executeQuery("COPY INTO t FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");

        engine.executeQuery(
            "COPY INTO t FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1) FORCE = TRUE PURGE = TRUE");

        assertEquals(4, count("t"));
        assertPurged(tableStageDir("t"), "u.csv");
    }

    /** A partial load (ON_ERROR = CONTINUE, some records rejected) still purges the file. */
    @Test
    public void partiallyLoadedFileIsPurged() throws IOException {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("mixed.csv", ONE_GOOD_ONE_BAD_ROW) + " @%t AUTO_COMPRESS=FALSE");

        final ResultSet copied = engine.executeQuery(
            "COPY INTO t FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1) ON_ERROR = CONTINUE PURGE = TRUE");

        assertEquals("PARTIALLY_LOADED", copied.getRows().get(0).getValue(1));
        assertEquals(1, count("t"));
        assertPurged(tableStageDir("t"), "mixed.csv");
    }

    /**
     * A wholly rejected file — every record bad, so nothing at all loaded — is KEPT, even though the
     * statement itself succeeds. Purging it would destroy data that never reached the table.
     */
    @Test
    public void whollyRejectedFileIsNotPurged() throws IOException {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("bad.csv", ALL_BAD_ROWS) + " @%t AUTO_COMPRESS=FALSE");

        engine.executeQuery(
            "COPY INTO t FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1) ON_ERROR = CONTINUE PURGE = TRUE");

        assertEquals(0, count("t"));
        assertStaged(tableStageDir("t"), "bad.csv");
    }

    /**
     * The other route to a kept file: ON_ERROR's error budget dropped it. Under SKIP_FILE one bad record is
     * enough, so a file whose remaining records WOULD have loaded still reaches the table with nothing — and
     * a file that loaded nothing is never purged. The control is
     * {@link #partiallyLoadedFileIsPurged()}: the very same file under ON_ERROR = CONTINUE does load, and is
     * purged.
     */
    @Test
    public void fileDroppedByTheErrorBudgetIsNotPurged() throws IOException {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("mixed.csv", ONE_GOOD_ONE_BAD_ROW) + " @%t AUTO_COMPRESS=FALSE");

        engine.executeQuery(
            "COPY INTO t FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1) ON_ERROR = SKIP_FILE PURGE = TRUE");

        assertEquals(0, count("t"));
        assertStaged(tableStageDir("t"), "mixed.csv");
    }

    /**
     * In one multi-file run, the loaded and partially-loaded files go and the wholly rejected one stays —
     * the mixed-outcome case, decided per file.
     */
    @Test
    public void multiFileMixedOutcomePurgesOnlyTheLoadedFiles() throws IOException {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("good.csv", TWO_GOOD_ROWS) + " @%t AUTO_COMPRESS=FALSE");
        engine.executeQuery("PUT " + localCsvUrl("mixed.csv", ONE_GOOD_ONE_BAD_ROW) + " @%t AUTO_COMPRESS=FALSE");
        engine.executeQuery("PUT " + localCsvUrl("bad.csv", ALL_BAD_ROWS) + " @%t AUTO_COMPRESS=FALSE");

        engine.executeQuery(
            "COPY INTO t FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1) ON_ERROR = CONTINUE PURGE = TRUE");

        assertEquals(3, count("t"));
        assertPurged(tableStageDir("t"), "good.csv");
        assertPurged(tableStageDir("t"), "mixed.csv");
        assertStaged(tableStageDir("t"), "bad.csv");
    }

    /**
     * ON_ERROR = ABORT_STATEMENT (the default) fails the statement, and then NOTHING is purged — not even a
     * file that had already parsed cleanly. The load is atomic, and so is the purge.
     */
    @Test
    public void abortedLoadPurgesNothing() throws IOException {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("good.csv", TWO_GOOD_ROWS) + " @%t AUTO_COMPRESS=FALSE");
        engine.executeQuery("PUT " + localCsvUrl("mixed.csv", ONE_GOOD_ONE_BAD_ROW) + " @%t AUTO_COMPRESS=FALSE");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("COPY INTO t FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1) PURGE = TRUE");
            }
        });

        assertEquals(0, count("t"));
        assertStaged(tableStageDir("t"), "good.csv");
        assertStaged(tableStageDir("t"), "mixed.csv");
    }

    /** VALIDATION_MODE does not load, so it does not purge — for either validation flavour. */
    @Test
    public void validationModePurgesNothing() throws IOException {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("u.csv", TWO_GOOD_ROWS) + " @%t AUTO_COMPRESS=FALSE");

        engine.executeQuery("COPY INTO t FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)"
            + " VALIDATION_MODE = 'RETURN_ERRORS' PURGE = TRUE");
        assertEquals(0, count("t"));
        assertStaged(tableStageDir("t"), "u.csv");

        engine.executeQuery("COPY INTO t FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)"
            + " VALIDATION_MODE = 'RETURN_2_ROWS' PURGE = TRUE");
        assertEquals(0, count("t"));
        assertStaged(tableStageDir("t"), "u.csv");
    }

    /**
     * A file that loads ZERO rows without any error — here one holding nothing but a skipped header — is
     * still LOADED, and so is still purged. The rule is "the file was read and not wholly rejected", NOT
     * "rows were loaded": live Snowflake purges a header-only and a zero-byte file alike.
     */
    @Test
    public void fileLoadingZeroRowsCleanlyIsStillPurged() throws IOException {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("headeronly.csv", "id,name\n") + " @%t AUTO_COMPRESS=FALSE");

        final ResultSet copied = engine.executeQuery(
            "COPY INTO t FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1) PURGE = TRUE");

        assertEquals("LOADED", copied.getRows().get(0).getValue(1));
        assertEquals(0, count("t"));
        assertPurged(tableStageDir("t"), "headeronly.csv");
    }

    /** PURGE works the same on a named external-URL stage. */
    @Test
    public void purgeWorksOnANamedStage() throws IOException {
        engine.execute("CREATE STAGE st URL='file://" + namedStageDir + "'");
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("keep.csv", TWO_GOOD_ROWS) + " @st AUTO_COMPRESS=FALSE");
        engine.executeQuery("PUT " + localCsvUrl("load.csv", TWO_GOOD_ROWS) + " @st AUTO_COMPRESS=FALSE");

        engine.executeQuery("COPY INTO t FROM @st FILES = ('load.csv')"
            + " FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1) PURGE = TRUE");

        assertEquals(2, count("t"));
        assertPurged(namedStageDir, "load.csv");
        assertStaged(namedStageDir, "keep.csv");
    }

    /** PURGE works the same on the user stage {@code @~}. */
    @Test
    public void purgeWorksOnTheUserStage() throws IOException {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("u.csv", TWO_GOOD_ROWS) + " @~/p141 AUTO_COMPRESS=FALSE");

        engine.executeQuery("COPY INTO t FROM @~/p141 FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1) PURGE = TRUE");

        assertEquals(2, count("t"));
        final File[] userDirs = internalRoot.resolve("users").toFile().listFiles();
        assertTrue(userDirs != null && userDirs.length == 1, "exactly one user stage dir expected");
        assertPurged(userDirs[0].toPath().resolve("p141"), "u.csv");
    }

    /**
     * PURGE is NOT gated behind {@code command.removeEnabled} — the guard on the bare REMOVE / RM command.
     * This engine leaves that flag at its default (false), and PURGE still deletes: a per-statement option
     * the user opted into, scoped to the files this very COPY just read, is not the unscoped bare command
     * the guard exists for — and gating it would leave PURGE silently doing nothing by default.
     */
    @Test
    public void purgeIsNotGatedByTheRemoveCommandGuard() throws IOException {
        assertFalse(engine.getConfig().isRemoveCommandEnabled(),
            "this suite must run with the REMOVE guard at its default (disabled)");

        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("u.csv", TWO_GOOD_ROWS) + " @%t AUTO_COMPRESS=FALSE");

        // The bare command is refused …
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("REMOVE @%t");
            }
        });
        assertStaged(tableStageDir("t"), "u.csv");

        // … while the COPY option is honoured.
        engine.executeQuery("COPY INTO t FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1) PURGE = TRUE");
        assertPurged(tableStageDir("t"), "u.csv");
    }

    /** PURGE over a stage with no files is a plain success, reported by the no-files-processed summary. */
    @Test
    public void purgeOnAnEmptyStageLoadsAndDeletesNothing() {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");

        final ResultSet copied = engine.executeQuery(
            "COPY INTO t FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1) PURGE = TRUE");

        assertEquals(1, copied.getColumns().size(), "an empty stage should report no files");
        assertEquals("Copy executed with 0 files processed.", copied.getRows().get(0).getValue(0));
        assertEquals(0, count("t"));
    }

    /**
     * A purge that cannot delete the file does NOT fail the statement: Snowflake reports no error when a
     * purge fails, so the load's own outcome stands and the undeletable file simply stays on the stage
     * (the engine logs a warning). Frostlake threw here originally, which failed a statement Snowflake
     * completes.
     *
     * <p>The failure is induced by making the stage directory non-writable. Where the environment cannot
     * enforce that (a privileged user, or a filesystem ignoring the mode) the delete succeeds and the
     * assertions below still hold — the test then simply does not exercise the warning path, rather than
     * reporting a false failure.
     */
    @Test
    public void aPurgeThatCannotDeleteStillReportsASuccessfulLoad() throws IOException {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("u.csv", TWO_GOOD_ROWS) + " @%t AUTO_COMPRESS=FALSE");
        final File stageDir = tableStageDir("t").toFile();

        assertTrue(stageDir.setWritable(false), "could not make the stage directory read-only");
        try {
            final ResultSet copied = engine.executeQuery(
                "COPY INTO t FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1) PURGE = TRUE");

            assertEquals("LOADED", copied.getRows().get(0).getValue(1), "the load itself still succeeded");
            assertEquals(2, count("t"), "the rows landed regardless of the purge outcome");
        } finally {
            stageDir.setWritable(true);
        }
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
