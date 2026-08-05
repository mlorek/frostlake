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
 * Tests the TWO result-set shapes of {@code COPY INTO <table>} — the wide per-file shape and the one-column
 * summary a load that processed no files answers with instead.
 *
 * <p>Which shape comes back is decided by whether the statement produced any per-file row, NOT by how many
 * rows it loaded: a zero-byte file, a header-only file and a wholly rejected file each load nothing and each
 * still come back wide. Conversely a stage whose only file is already in the load history produces no row at
 * all and collapses to the summary, even though the stage is not empty.
 *
 * <p>The rule and every case below were live-verified against a real account on each paired with a
 * control run that produced the other shape from the same stage.
 */
public class CopyNoFilesResultTest {

    /** The exact summary text, trailing period included, as returned by a real account. */
    private static final String NO_FILES = "Copy executed with 0 files processed.";

    private static final String TWO_GOOD_ROWS = """
        id,name
        1,alice
        2,bob
        """;

    private static final String TWO_MORE_GOOD_ROWS = """
        id,name
        3,carol
        4,dave
        """;

    private static final String ALL_ROWS_BAD = """
        id,name
        NOTANUM,eve
        ALSOBAD,frank
        """;

    private static final String HEADER_ONLY = """
        id,name
        """;

    private DatabaseEngine engine;
    private Path internalRoot;
    private Path namedStageDir;
    private Path localDir;

    @BeforeEach
    public void setUp() throws IOException {
        internalRoot = Files.createTempDirectory("copy_nofiles_root_");
        namedStageDir = Files.createTempDirectory("copy_nofiles_stage_");
        localDir = Files.createTempDirectory("copy_nofiles_local_");
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

    /** Assert the one-column, one-row summary shape, down to the column name and the literal status text. */
    private void assertNoFilesSummary(final ResultSet rs) {
        assertEquals(1, rs.getColumns().size(), "the summary shape has exactly one column");
        assertEquals("status", rs.getColumns().get(0).getName(), "the summary column is named status");
        assertEquals(1, rs.getRows().size(), "the summary shape has exactly one row");
        assertEquals(NO_FILES, rs.getRows().get(0).getValue(0));
    }

    /** Assert the wide per-file shape, and return the status reported for the first file. */
    private String assertPerFileShape(final ResultSet rs, final int expectedFiles) {
        assertEquals(10, rs.getColumns().size(), "the per-file shape has ten columns");
        assertEquals("file", rs.getColumns().get(0).getName());
        assertEquals("status", rs.getColumns().get(1).getName());
        assertEquals(expectedFiles, rs.getRows().size());
        return (String) rs.getRows().get(0).getValue(1);
    }

    // ── no files at all ────────────────────────────────────────────────────────────────────────────

    /** An empty table stage: the baseline case, reached through the implicit no-FROM form. */
    @Test
    public void emptyTableStageAnswersWithTheSummary() {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");

        assertNoFilesSummary(engine.executeQuery("COPY INTO t FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)"));
        assertEquals(0, count("t"));
    }

    /** The explicit {@code FROM @%t} form shares the rule — it is not specific to the implicit path. */
    @Test
    public void emptyTableStageAnswersWithTheSummaryOnTheExplicitFromForm() {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");

        assertNoFilesSummary(
            engine.executeQuery("COPY INTO t FROM @%t FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)"));
    }

    /** …and so does a named stage. */
    @Test
    public void emptyNamedStageAnswersWithTheSummary() {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STAGE st URL = 'file://" + namedStageDir + "'");

        assertNoFilesSummary(
            engine.executeQuery("COPY INTO t FROM @st FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)"));
    }

    /** …and the user stage {@code @~}. */
    @Test
    public void emptyUserStageAnswersWithTheSummary() {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");

        assertNoFilesSummary(
            engine.executeQuery("COPY INTO t FROM @~/nothinghere FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)"));
    }

    /** The shape is decided by the file count, not the file format: an empty JSON load collapses too. */
    @Test
    public void emptyStageAnswersWithTheSummaryForJson() {
        engine.execute("CREATE TABLE t (v VARIANT)");

        assertNoFilesSummary(engine.executeQuery("COPY INTO t FILE_FORMAT = (TYPE = JSON)"));
    }

    /** FORCE = TRUE has nothing to re-load on an empty stage, so it collapses as well. */
    @Test
    public void forceOnAnEmptyStageAnswersWithTheSummary() {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");

        assertNoFilesSummary(
            engine.executeQuery("COPY INTO t FORCE = TRUE FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)"));
    }

    // ── files present, none processed ──────────────────────────────────────────────────────────────

    /**
     * THE discriminator: the stage is NOT empty, but its only file is already in the load history. Live
     * Snowflake answers with the summary, so the rule is "no file was processed", not "the stage was empty".
     */
    @Test
    public void aStageWhoseOnlyFileIsAlreadyLoadedAnswersWithTheSummary() throws IOException {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("u.csv", TWO_GOOD_ROWS) + " @%t");

        // Control: the first load of the very same stage reports the file, wide.
        final ResultSet first = engine.executeQuery("COPY INTO t FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");
        assertEquals("LOADED", assertPerFileShape(first, 1));

        assertNoFilesSummary(engine.executeQuery("COPY INTO t FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)"));
        assertEquals(2, count("t"), "the second COPY loads nothing");
    }

    /** Two files, both already loaded — the summary is not a one-file special case. */
    @Test
    public void aStageWhoseFilesAreAllAlreadyLoadedAnswersWithTheSummary() throws IOException {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("a.csv", TWO_GOOD_ROWS) + " @%t");
        engine.executeQuery("PUT " + localCsvUrl("b.csv", TWO_MORE_GOOD_ROWS) + " @%t");

        final ResultSet first = engine.executeQuery("COPY INTO t FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");
        assertPerFileShape(first, 2);

        assertNoFilesSummary(engine.executeQuery("COPY INTO t FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)"));
        assertEquals(4, count("t"));
    }

    /** The same holds on a named stage, so it is not a table-stage quirk. */
    @Test
    public void aNamedStageWhoseFilesAreAllAlreadyLoadedAnswersWithTheSummary() throws IOException {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STAGE st URL = 'file://" + namedStageDir + "'");
        engine.executeQuery("PUT " + localCsvUrl("u.csv", TWO_GOOD_ROWS) + " @st");

        engine.executeQuery("COPY INTO t FROM @st FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");

        assertNoFilesSummary(
            engine.executeQuery("COPY INTO t FROM @st FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)"));
    }

    /** A PATTERN that matches nothing on a NON-empty stage collapses; one that matches does not. */
    @Test
    public void aPatternMatchingNothingAnswersWithTheSummary() throws IOException {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("u.csv", TWO_GOOD_ROWS) + " @%t");

        assertNoFilesSummary(engine.executeQuery(
            "COPY INTO t PATTERN = '.*nosuchfile.*' FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)"));
        assertEquals(0, count("t"));

        // Control: the same stage with a PATTERN that does match reports the file, wide.
        final ResultSet matched = engine.executeQuery(
            "COPY INTO t PATTERN = '.*u[.]csv' FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");
        assertEquals("LOADED", assertPerFileShape(matched, 1));
    }

    /** An already-loaded file picked up again by a MATCHING pattern still collapses — nothing is processed. */
    @Test
    public void aPatternSelectingOnlyAnAlreadyLoadedFileAnswersWithTheSummary() throws IOException {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("u.csv", TWO_GOOD_ROWS) + " @%t");
        engine.executeQuery("COPY INTO t FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");

        assertNoFilesSummary(engine.executeQuery(
            "COPY INTO t PATTERN = '.*u[.]csv' FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)"));
    }

    // ── files present and processed: the wide shape survives ───────────────────────────────────────

    /**
     * A file that loads no rows is still a processed file. A zero-byte file comes back LOADED with the wide
     * shape — this is what separates "no files processed" from "no rows loaded".
     */
    @Test
    public void aZeroByteFileKeepsThePerFileShape() throws IOException {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("empty.csv", "") + " @%t");

        final ResultSet rs = engine.executeQuery("COPY INTO t FILE_FORMAT = (TYPE = CSV)");

        assertEquals("LOADED", assertPerFileShape(rs, 1));
        assertEquals(0, count("t"), "a zero-byte file loads no rows but is still reported");
    }

    /** A header-only file parses zero data rows and likewise keeps the wide shape. */
    @Test
    public void aHeaderOnlyFileKeepsThePerFileShape() throws IOException {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("hdr.csv", HEADER_ONLY) + " @%t");

        final ResultSet rs = engine.executeQuery("COPY INTO t FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");

        assertPerFileShape(rs, 1);
        assertEquals(0, count("t"));
    }

    /** A file whose every record is rejected loads nothing, yet is reported — again not the summary. */
    @Test
    public void aWhollyRejectedFileKeepsThePerFileShape() throws IOException {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("bad.csv", ALL_ROWS_BAD) + " @%t");

        final ResultSet rs = engine.executeQuery(
            "COPY INTO t FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1) ON_ERROR = CONTINUE");

        assertEquals(10, rs.getColumns().size(), "a rejected file still reports the per-file shape");
        assertEquals(1, rs.getRows().size());
        assertEquals(0, ((Number) rs.getRows().get(0).getValue(3)).intValue(), "no rows loaded");
        assertEquals(0, count("t"));
    }

    /**
     * A mixed run reports only the file it processed: the already-loaded one leaves no row behind, so the
     * result is wide with a single row rather than wide with a skipped-file row beside it.
     */
    @Test
    public void aMixedRunReportsOnlyTheProcessedFile() throws IOException {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("a.csv", TWO_GOOD_ROWS) + " @%t");
        engine.executeQuery("COPY INTO t FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");

        engine.executeQuery("PUT " + localCsvUrl("b.csv", TWO_MORE_GOOD_ROWS) + " @%t");
        final ResultSet mixed = engine.executeQuery("COPY INTO t FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");

        assertEquals("LOADED", assertPerFileShape(mixed, 1));
        assertEquals("b.csv", mixed.getRows().get(0).getValue(0), "only the newly loaded file is reported");
        assertEquals(4, count("t"));
    }

    /**
     * The one exception to that: a file the statement NAMED in FILES = (…) does report its load-history skip,
     * so the same already-loaded file yields the wide shape here and the summary when picked by PATTERN.
     */
    @Test
    public void anExplicitlyNamedAlreadyLoadedFileKeepsThePerFileShape() throws IOException {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.executeQuery("PUT " + localCsvUrl("u.csv", TWO_GOOD_ROWS) + " @%t");
        engine.executeQuery("COPY INTO t FILES = ('u.csv') FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");

        final ResultSet again = engine.executeQuery(
            "COPY INTO t FILES = ('u.csv') FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");

        assertEquals("LOAD_SKIPPED", assertPerFileShape(again, 1));
        assertEquals(2, count("t"), "the named file is still not re-loaded");
    }

    // ── neighbours that deliberately do NOT collapse ───────────────────────────────────────────────

    /** COPY unload keeps its own shape: an empty source table reports zero rows unloaded, not the summary. */
    @Test
    public void unloadingAnEmptyTableKeepsTheUnloadShape() {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STAGE st URL = 'file://" + namedStageDir + "'");

        final ResultSet rs = engine.executeQuery("COPY INTO @st FROM t FILE_FORMAT = (TYPE = CSV)");

        assertEquals(3, rs.getColumns().size(), "the unload shape has three columns");
        assertEquals("rows_unloaded", rs.getColumns().get(0).getName());
        assertEquals(1, rs.getRows().size());
        assertEquals(0, ((Number) rs.getRows().get(0).getValue(0)).intValue());
    }

    /** VALIDATION_MODE keeps its own error shape even when the stage holds nothing to validate. */
    @Test
    public void validationModeOnAnEmptyStageKeepsItsOwnShape() throws IOException {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        // An EXISTING but empty table-stage directory — the state a stage is in once its files are gone.
        Files.createDirectories(internalRoot.resolve("tables").resolve("DB.S.T"));

        final ResultSet rs = engine.executeQuery(
            "COPY INTO t VALIDATION_MODE = RETURN_ERRORS FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");

        assertTrue(rs.getColumns().size() > 1, "VALIDATION_MODE does not collapse to the summary column");
        assertEquals("ERROR", rs.getColumns().get(0).getName());
        assertTrue(rs.getRows().isEmpty(), "nothing staged means nothing to report");
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
