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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests the four trailing columns of the {@code COPY INTO <table>} per-file result — {@code first_error},
 * {@code first_error_line}, {@code first_error_character} and {@code first_error_column_name} — plus the
 * {@code error_limit} / {@code errors_seen} pair they sit beside.
 *
 * <p>The per-file result is TEN columns wide, not six. Every value asserted here was live-verified against a
 * real account on each case paired with a control run producing the other outcome: a clean file
 * beside a rejecting one, a headerless file beside a three-header one, a bad first field beside a bad third
 * field, a quoted field beside an unquoted one, and a threshold-skipped file beside a loaded one.
 */
public class CopyFirstErrorTest {

    private DatabaseEngine engine;
    private Path internalRoot;
    private Path localDir;

    @BeforeEach
    public void setUp() throws IOException {
        internalRoot = Files.createTempDirectory("copy_first_error_root_");
        localDir = Files.createTempDirectory("copy_first_error_local_");
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_STAGE_INTERNAL_LOCAL_ROOT, internalRoot.toString());
        cfg.setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "true");
        engine = new DatabaseEngine(cfg);
        engine.execute("CREATE DATABASE db");
        engine.execute("USE DATABASE db");
        engine.execute("CREATE SCHEMA s");
        engine.execute("USE SCHEMA s");
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
        deleteRecursively(internalRoot.toFile());
        deleteRecursively(localDir.toFile());
    }

    /** Two clean records. */
    private static final String TWO_GOOD_ROWS = """
        id,name
        1,alice
        2,bob
        """;

    /** Four records, the LAST of them bad — its bad value sits on physical line 5, character 1. */
    private static final String ONE_BAD_OF_FOUR = """
        id,name
        10,a
        11,b
        12,c
        BADX,d
        """;

    /** Four records, two bad: the first on line 3, the second on line 5. */
    private static final String TWO_BAD_OF_FOUR = """
        id,name
        20,a
        BADY,b
        22,c
        BADZ,d
        """;

    private static final String HEADER_ONLY = "id,name\n";

    /** PUT a CSV written outside every stage root onto the table stage {@code @%<table>}. */
    private void stage(final String table, final String fileName, final String content) throws IOException {
        final Path file = localDir.resolve(fileName);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        engine.executeQuery("PUT file://" + file + " @%" + table + " AUTO_COMPRESS=FALSE");
    }

    private ResultSet copy(final String table, final String options) {
        return engine.executeQuery("COPY INTO " + table
            + " FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1) " + options);
    }

    /** The per-file row for one staged file, found by name so file listing order cannot matter. */
    private Row fileRow(final ResultSet rs, final String fileName) {
        for (final Row row : rs.getRows()) {
            if (fileName.equals(row.getValue(0))) {
                return row;
            }
        }
        fail("no per-file row for " + fileName + " in the COPY result");
        return null;
    }

    private static Integer intCell(final Row row, final int index) {
        final Object value = row.getValue(index);
        return value == null ? null : Integer.valueOf(((Number) value).intValue());
    }

    /**
     * THE shape: ten columns, named and ordered exactly as the account reports them. A positional reader
     * that stops at index 5 misses the whole first_error quartet.
     */
    @Test
    public void perFileResultHasTheTenNamedColumns() throws IOException {
        stage("t", "good.csv", TWO_GOOD_ROWS);

        final ResultSet rs = copy("t", "");
        final List<String> expected = Arrays.asList("file", "status", "rows_parsed", "rows_loaded",
            "error_limit", "errors_seen", "first_error", "first_error_line", "first_error_character",
            "first_error_column_name");
        assertEquals(expected.size(), rs.getColumns().size(), "the per-file shape has ten columns");
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), rs.getColumns().get(i).getName(), "column " + i);
        }
    }

    /** A clean LOADED file leaves the whole quartet null — there was no first error to describe. */
    @Test
    public void cleanFileReportsNoFirstError() throws IOException {
        stage("t", "good.csv", TWO_GOOD_ROWS);

        final Row row = fileRow(copy("t", ""), "good.csv");
        assertEquals("LOADED", row.getValue(1));
        assertEquals(0, intCell(row, 5).intValue(), "errors_seen");
        assertNull(row.getValue(6), "first_error");
        assertNull(row.getValue(7), "first_error_line");
        assertNull(row.getValue(8), "first_error_character");
        assertNull(row.getValue(9), "first_error_column_name");
    }

    /** A header-only file parses nothing, loads nothing, and still reports no first error. */
    @Test
    public void headerOnlyFileReportsNoFirstError() throws IOException {
        stage("t", "headeronly.csv", HEADER_ONLY);

        final Row row = fileRow(copy("t", "ON_ERROR = CONTINUE"), "headeronly.csv");
        assertEquals("LOADED", row.getValue(1));
        assertNull(row.getValue(6), "first_error");
        assertNull(row.getValue(7), "first_error_line");
    }

    /**
     * A partially loaded file describes its one rejected record: the message, the 1-based PHYSICAL line
     * (the header counted, though SKIP_HEADER dropped it), the character the bad field starts at, and the
     * column it belongs to.
     */
    @Test
    public void partiallyLoadedFileDescribesItsFirstError() throws IOException {
        stage("t", "bad1of4.csv", ONE_BAD_OF_FOUR);

        final Row row = fileRow(copy("t", "ON_ERROR = CONTINUE"), "bad1of4.csv");
        assertEquals("PARTIALLY_LOADED", row.getValue(1));
        assertEquals(1, intCell(row, 5).intValue(), "errors_seen");
        assertEquals("Numeric value 'BADX' is not recognized", row.getValue(6));
        assertEquals(5, intCell(row, 7).intValue(), "first_error_line — physical, header counted");
        assertEquals(1, intCell(row, 8).intValue(), "first_error_character");
        assertEquals("\"T\"[\"ID\":1]", row.getValue(9));
    }

    /** A file dropped by ON_ERROR's budget still describes the record that tripped it. */
    @Test
    public void thresholdSkippedFileStillDescribesItsFirstError() throws IOException {
        stage("t", "bad1of4.csv", ONE_BAD_OF_FOUR);

        final Row row = fileRow(copy("t", "ON_ERROR = SKIP_FILE_1"), "bad1of4.csv");
        assertEquals("LOAD_FAILED", row.getValue(1));
        assertEquals(0, intCell(row, 3).intValue(), "rows_loaded");
        assertEquals(1, intCell(row, 5).intValue(), "errors_seen");
        assertEquals("Numeric value 'BADX' is not recognized", row.getValue(6));
        assertEquals(5, intCell(row, 7).intValue(), "first_error_line");
    }

    /**
     * The counterpart the status rule turns on: {@code SKIP_FILE_0} drops a WHOLLY CLEAN file, and that
     * LOAD_FAILED row carries {@code errors_seen = 0} with the quartet all null — no record was rejected,
     * so there is nothing to point at.
     */
    @Test
    public void cleanFileDroppedByAZeroBudgetReportsNoFirstError() throws IOException {
        stage("t", "good.csv", TWO_GOOD_ROWS);

        final Row row = fileRow(copy("t", "ON_ERROR = SKIP_FILE_0"), "good.csv");
        assertEquals("LOAD_FAILED", row.getValue(1));
        assertEquals(2, intCell(row, 2).intValue(), "rows_parsed");
        assertEquals(0, intCell(row, 3).intValue(), "rows_loaded");
        assertEquals(0, intCell(row, 4).intValue(), "error_limit");
        assertEquals(0, intCell(row, 5).intValue(), "errors_seen");
        assertNull(row.getValue(6), "first_error");
        assertNull(row.getValue(7), "first_error_line");
        assertNull(row.getValue(8), "first_error_character");
        assertNull(row.getValue(9), "first_error_column_name");
    }

    /** Only the FIRST rejected record is described: a later one raises errors_seen and nothing else. */
    @Test
    public void onlyTheFirstRejectedRecordIsDescribed() throws IOException {
        stage("t", "bad2of4.csv", TWO_BAD_OF_FOUR);

        final Row row = fileRow(copy("t", "ON_ERROR = CONTINUE"), "bad2of4.csv");
        assertEquals(2, intCell(row, 5).intValue(), "errors_seen");
        assertEquals("Numeric value 'BADY' is not recognized", row.getValue(6), "the EARLIER of the two");
        assertEquals(3, intCell(row, 7).intValue(), "first_error_line — line 3, not line 5");
    }

    /**
     * The line is PHYSICAL and 1-based: three header lines push a bad first data record to line 4, while a
     * headerless file reports its own first record as line 1. The pair is what pins the base — one reading
     * alone cannot tell "physical" from "data-relative".
     */
    @Test
    public void firstErrorLineCountsHeaderLines() throws IOException {
        engine.execute("CREATE TABLE h3 (id INTEGER, name VARCHAR)");
        stage("h3", "hdr3.csv", """
            h1,h2
            h3,h4
            h5,h6
            BADH,x
            """);
        final ResultSet withHeaders = engine.executeQuery(
            "COPY INTO h3 FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 3) ON_ERROR = CONTINUE");
        assertEquals(4, intCell(fileRow(withHeaders, "hdr3.csv"), 7).intValue(),
            "three header lines put the first data record on physical line 4");

        engine.execute("CREATE TABLE h0 (id INTEGER, name VARCHAR)");
        stage("h0", "hdr0.csv", """
            BADN,x
            9,y
            """);
        final ResultSet noHeader = engine.executeQuery(
            "COPY INTO h0 FILE_FORMAT = (TYPE = CSV) ON_ERROR = CONTINUE");
        assertEquals(1, intCell(fileRow(noHeader, "hdr0.csv"), 7).intValue(),
            "a headerless file reports its first record as line 1");
    }

    /**
     * The character is the 1-based offset of the bad FIELD in the raw line — so it moves with the field,
     * and it counts enclosure characters rather than recomputing from parsed widths.
     */
    @Test
    public void firstErrorCharacterIsTheRawFieldOffset() throws IOException {
        engine.execute("CREATE TABLE c3 (id INTEGER, name VARCHAR, age INTEGER)");
        stage("c3", "charpos3.csv", """
            id,name,age
            1,alice,30
            2,bob,BADAGE
            """);
        final Row third = fileRow(copy("c3", "ON_ERROR = CONTINUE"), "charpos3.csv");
        assertEquals(7, intCell(third, 8).intValue(), "'BADAGE' starts at character 7 of '2,bob,BADAGE'");
        assertEquals("\"C3\"[\"AGE\":3]", third.getValue(9));

        engine.execute("CREATE TABLE c1 (id INTEGER, name VARCHAR, age INTEGER)");
        stage("c1", "charpos1.csv", """
            id,name,age
            1,alice,30
            BADID,bob,30
            """);
        final Row first = fileRow(copy("c1", "ON_ERROR = CONTINUE"), "charpos1.csv");
        assertEquals(1, intCell(first, 8).intValue(), "a bad FIRST field starts at character 1");
        assertEquals("\"C1\"[\"ID\":1]", first.getValue(9));
    }

    /** A quoted field ahead of the bad one pushes the offset along — quotes are counted. */
    @Test
    public void firstErrorCharacterCountsEnclosureCharacters() throws IOException {
        engine.execute("CREATE TABLE q (id INTEGER, name VARCHAR, age INTEGER)");
        stage("q", "quoted.csv", """
            id,name,age
            1,"al,ice",30
            2,"b,ob",BADAGE
            """);

        final ResultSet rs = engine.executeQuery("COPY INTO q FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1"
            + " FIELD_OPTIONALLY_ENCLOSED_BY = '\"') ON_ERROR = CONTINUE");
        final Row row = fileRow(rs, "quoted.csv");
        assertEquals(10, intCell(row, 8).intValue(), "'BADAGE' starts at character 10 of '2,\"b,ob\",BADAGE'");
    }

    /** The column name is rendered {@code "TABLE"["COLUMN":ordinal]} — bare, never database-qualified. */
    @Test
    public void firstErrorColumnNameIsNeverQualified() throws IOException {
        engine.execute("CREATE TABLE fq (id INTEGER, name VARCHAR, age INTEGER)");
        stage("fq", "charpos3.csv", """
            id,name,age
            1,alice,30
            2,bob,BADAGE
            """);

        final ResultSet rs = engine.executeQuery("COPY INTO db.s.fq"
            + " FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1) ON_ERROR = CONTINUE");
        assertEquals("\"FQ\"[\"AGE\":3]", fileRow(rs, "charpos3.csv").getValue(9),
            "a qualified COPY target still reports the bare table name");
    }

    /** A NOT NULL violation points at the column that held the null, not at the first column. */
    @Test
    public void notNullViolationNamesItsOwnColumn() throws IOException {
        engine.execute("CREATE TABLE nn (id INTEGER, name VARCHAR NOT NULL)");
        stage("nn", "allnull.csv", """
            id,name
            30,
            31,
            """);

        final Row row = fileRow(copy("nn", "ON_ERROR = CONTINUE"), "allnull.csv");
        assertEquals("LOAD_FAILED", row.getValue(1));
        assertEquals(2, intCell(row, 5).intValue(), "errors_seen");
        // The message names nothing — the column travels in first_error_column_name beside it.
        assertEquals("NULL result in a non-nullable column", row.getValue(6));
        assertEquals(2, intCell(row, 7).intValue(), "first_error_line");
        assertEquals("\"NN\"[\"NAME\":2]", row.getValue(9));
    }

    /**
     * An over-long value is the ONE write violation Snowflake words differently per path: the COPY result
     * says {@code User character length limit (n) exceeded by string '…'} where the DML paths say
     * {@code String '…' is too long and would be truncated} (pinned in {@code TypeCoercionMessageTest}).
     */
    @Test
    public void overLongValueUsesTheCopyWording() throws IOException {
        engine.execute("CREATE TABLE tl (id INTEGER, name VARCHAR(3))");
        stage("tl", "toolong.csv", """
            id,name
            1,ab
            2,abcdefgh
            """);

        final Row row = fileRow(copy("tl", "ON_ERROR = CONTINUE"), "toolong.csv");
        assertEquals("PARTIALLY_LOADED", row.getValue(1));
        assertEquals(1, intCell(row, 5).intValue(), "errors_seen");
        assertEquals("User character length limit (3) exceeded by string 'abcdefgh'", row.getValue(6));
        assertEquals(3, intCell(row, 7).intValue(), "first_error_line");
        assertEquals(3, intCell(row, 8).intValue(), "first_error_character");
        assertEquals("\"TL\"[\"NAME\":2]", row.getValue(9));
    }

    /**
     * A LOAD_SKIPPED row — the load-history skip a {@code FILES = (…)} list makes visible — is NOT four
     * zeros: {@code error_limit} is null (no budget was ever applied), {@code errors_seen} is 1, and the
     * skip itself is the file's first_error, with no line, character or column to go with it.
     */
    @Test
    public void loadSkippedRowReportsTheSkipAsItsFirstError() throws IOException {
        stage("t", "good.csv", TWO_GOOD_ROWS);
        engine.executeQuery("COPY INTO t FILES = ('good.csv') FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");

        final ResultSet again = engine.executeQuery(
            "COPY INTO t FILES = ('good.csv') FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");
        final Row row = fileRow(again, "good.csv");
        assertEquals("LOAD_SKIPPED", row.getValue(1));
        assertEquals(0, intCell(row, 2).intValue(), "rows_parsed");
        assertEquals(0, intCell(row, 3).intValue(), "rows_loaded");
        assertNull(row.getValue(4), "error_limit is NULL, not 0");
        assertEquals(1, intCell(row, 5).intValue(), "errors_seen is 1, not 0");
        assertEquals("File was loaded before.", row.getValue(6));
        assertNull(row.getValue(7), "first_error_line");
        assertNull(row.getValue(8), "first_error_character");
        assertNull(row.getValue(9), "first_error_column_name");
    }

    /** The LOAD_SKIPPED shape does not move with ON_ERROR — the file is passed over before any budget. */
    @Test
    public void loadSkippedRowIsTheSameUnderEveryOnError() throws IOException {
        stage("t", "good.csv", TWO_GOOD_ROWS);
        engine.executeQuery("COPY INTO t FILES = ('good.csv') FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");

        final ResultSet withContinue = engine.executeQuery("COPY INTO t FILES = ('good.csv')"
            + " FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1) ON_ERROR = CONTINUE");
        final Row row = fileRow(withContinue, "good.csv");
        assertNull(row.getValue(4), "error_limit stays NULL under CONTINUE");
        assertEquals(1, intCell(row, 5).intValue(), "errors_seen");
        assertEquals("File was loaded before.", row.getValue(6));
    }

    /** FORCE = TRUE is the control: the very same file comes back LOADED, with the quartet null again. */
    @Test
    public void forceReloadsInsteadOfSkipping() throws IOException {
        stage("t", "good.csv", TWO_GOOD_ROWS);
        engine.executeQuery("COPY INTO t FILES = ('good.csv') FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");

        final ResultSet forced = engine.executeQuery("COPY INTO t FILES = ('good.csv')"
            + " FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1) FORCE = TRUE");
        final Row row = fileRow(forced, "good.csv");
        assertEquals("LOADED", row.getValue(1));
        assertEquals(1, intCell(row, 4).intValue(), "error_limit");
        assertNull(row.getValue(6), "first_error");
    }

    /** ON_ERROR = ABORT_STATEMENT (the default) reports a flat error_limit of 1, whatever the file holds. */
    @Test
    public void abortStatementReportsAnErrorLimitOfOne() throws IOException {
        stage("t", "good.csv", TWO_GOOD_ROWS);
        assertEquals(1, intCell(fileRow(copy("t", ""), "good.csv"), 4).intValue(),
            "a 2-record clean load under the default ON_ERROR");

        engine.execute("CREATE TABLE t2 (id INTEGER, name VARCHAR)");
        stage("t2", "headeronly.csv", HEADER_ONLY);
        assertEquals(1, intCell(fileRow(copy("t2", ""), "headeronly.csv"), 4).intValue(),
            "and a 0-record one");
    }

    /** ON_ERROR = CONTINUE reports the record count itself, floored at one. */
    @Test
    public void continueReportsTheRecordCountAsItsErrorLimit() throws IOException {
        stage("t", "bad1of4.csv", ONE_BAD_OF_FOUR);
        assertEquals(4, intCell(fileRow(copy("t", "ON_ERROR = CONTINUE"), "bad1of4.csv"), 4).intValue(),
            "four records parsed");

        engine.execute("CREATE TABLE t2 (id INTEGER, name VARCHAR)");
        stage("t2", "headeronly.csv", HEADER_ONLY);
        assertEquals(1, intCell(fileRow(copy("t2", "ON_ERROR = CONTINUE"), "headeronly.csv"), 4).intValue(),
            "nothing parsed still reports 1, not 0");
    }

    /** The SKIP_FILE family reports its own budget: bare = 1, SKIP_FILE_&lt;n&gt; = n, taken as written. */
    @Test
    public void skipFileFamilyReportsItsOwnBudget() throws IOException {
        stage("t", "bad1of4.csv", ONE_BAD_OF_FOUR);
        assertEquals(1, intCell(fileRow(copy("t", "ON_ERROR = SKIP_FILE"), "bad1of4.csv"), 4).intValue());

        engine.execute("CREATE TABLE t2 (id INTEGER, name VARCHAR)");
        stage("t2", "bad1of4.csv", ONE_BAD_OF_FOUR);
        assertEquals(2, intCell(fileRow(copy("t2", "ON_ERROR = SKIP_FILE_2"), "bad1of4.csv"), 4).intValue());

        engine.execute("CREATE TABLE t3 (id INTEGER, name VARCHAR)");
        stage("t3", "bad1of4.csv", ONE_BAD_OF_FOUR);
        assertEquals(5, intCell(fileRow(copy("t3", "ON_ERROR = SKIP_FILE_5"), "bad1of4.csv"), 4).intValue());
    }

    /** The percentage form reports n% of the records parsed, rounded DOWN but never below 1. */
    @Test
    public void percentageBudgetIsFlooredAtOne() throws IOException {
        stage("t", "bad1of4.csv", ONE_BAD_OF_FOUR);
        assertEquals(2, intCell(fileRow(copy("t", "ON_ERROR = 'SKIP_FILE_50%'"), "bad1of4.csv"), 4).intValue(),
            "50% of 4 records");

        engine.execute("CREATE TABLE t2 (id INTEGER, name VARCHAR)");
        stage("t2", "bad1of4.csv", ONE_BAD_OF_FOUR);
        assertEquals(1, intCell(fileRow(copy("t2", "ON_ERROR = 'SKIP_FILE_30%'"), "bad1of4.csv"), 4).intValue(),
            "30% of 4 rounds DOWN to 1");

        engine.execute("CREATE TABLE t3 (id INTEGER, name VARCHAR)");
        stage("t3", "good.csv", TWO_GOOD_ROWS);
        assertEquals(1, intCell(fileRow(copy("t3", "ON_ERROR = 'SKIP_FILE_0%'"), "good.csv"), 4).intValue(),
            "0% floors at 1 — and so LOADS the clean file that SKIP_FILE_0 drops");
    }

    /**
     * A record-based format has no line-and-column geometry: the record's 1-based ORDINAL stands in for the
     * line and the character and column name stay null.
     */
    @Test
    public void recordFormatReportsTheRecordOrdinalAndNoGeometry() throws IOException {
        final Path jsonDir = Files.createDirectory(localDir.resolve("json_stage"));
        Files.write(jsonDir.resolve("mixed.json"), """
            {"id": 1, "name": "a"}
            {"id": "ZZ", "name": "b"}
            """.getBytes(StandardCharsets.UTF_8));
        engine.execute("CREATE STAGE js URL='file://" + jsonDir + "'");
        engine.execute("CREATE TABLE jt (id INTEGER, name VARCHAR)");

        final ResultSet rs = engine.executeQuery("COPY INTO jt FROM (SELECT $1:id, $1:name FROM @js)"
            + " FILES = ('mixed.json') FILE_FORMAT = (TYPE = JSON) ON_ERROR = CONTINUE");
        // The per-file row spells a named-stage file with the lowercase stage prefix.
        final Row row = fileRow(rs, "js/mixed.json");
        assertEquals(1, intCell(row, 5).intValue(), "errors_seen");
        assertNotNull(row.getValue(6), "first_error");
        assertEquals(2, intCell(row, 7).intValue(), "the SECOND record's ordinal");
        assertNull(row.getValue(8), "first_error_character");
        assertNull(row.getValue(9), "first_error_column_name");
    }

    /** The narrow no-files summary is untouched by the widening: still ONE column, one row. */
    @Test
    public void noFilesSummaryKeepsItsOneColumnShape() {
        final ResultSet rs = engine.executeQuery("COPY INTO t FILE_FORMAT = (TYPE = CSV)");
        assertEquals(1, rs.getColumns().size(), "the summary shape has exactly one column");
        assertEquals("status", rs.getColumns().get(0).getName());
        assertEquals("Copy executed with 0 files processed.", rs.getRows().get(0).getValue(0));
    }

    /** The unload path does not share the shape: it still answers rows_unloaded / input / output bytes. */
    @Test
    public void unloadResultIsUnchanged() {
        engine.execute("CREATE STAGE out_stage");
        engine.execute("INSERT INTO t VALUES (1, 'alice')");

        final ResultSet rs = engine.executeQuery("COPY INTO @out_stage FROM t");
        assertEquals(3, rs.getColumns().size(), "the unload shape has three columns");
        assertEquals("rows_unloaded", rs.getColumns().get(0).getName());
    }

    private void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
