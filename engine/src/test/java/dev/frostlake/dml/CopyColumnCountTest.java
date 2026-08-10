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
import org.junit.jupiter.api.function.Executable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests {@code ERROR_ON_COLUMN_COUNT_MISMATCH} — the CSV file-format option, Snowflake default TRUE, that
 * decides what happens to a staged record whose field count does not match the target table's column count.
 *
 * <p>The two settings are genuinely different loads, not different diagnostics, so every case here asserts
 * the resulting TABLE CONTENTS beside the per-file result: with the option off a malformed record is reshaped
 * into the table (an extra field dropped, a missing one NULL-padded) and reported {@code LOADED} with a null
 * first_error, which is exactly what makes the wrong default a silent-corruption defect rather than a
 * cosmetic one.
 *
 * <p>Every value asserted here was live-verified against a real account on each case paired with
 * a control run producing the other outcome: the option on beside the option off over the very same staged
 * file, a mismatched record beside a well-formed one, a first data record beside a later one, and a
 * comma-delimited file beside a pipe-delimited one.
 */
public class CopyColumnCountTest {

    private DatabaseEngine engine;
    private Path internalRoot;
    private Path localDir;

    @BeforeEach
    public void setUp() throws IOException {
        internalRoot = Files.createTempDirectory("copy_col_count_root_");
        localDir = Files.createTempDirectory("copy_col_count_local_");
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_STAGE_INTERNAL_LOCAL_ROOT, internalRoot.toString());
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

    /** An EXTRA field on the file's FIRST data record; the second record is well formed. */
    private static final String EXTRA_ON_FIRST = """
        id,name
        1,a,extra
        2,b
        """;

    /** An extra field on a MIDDLE record, with a well-formed record on either side. */
    private static final String EXTRA_IN_MIDDLE = """
        id,name
        1,a
        2,b,extra
        3,c
        """;

    /** An extra field on the file's LAST record; the first record is well formed. */
    private static final String EXTRA_ON_LAST = """
        id,name
        1,a
        2,b,extra
        """;

    /** An extra field on the ONLY record — first and last at once. */
    private static final String EXTRA_ON_ONLY = """
        id,name
        1,a,extra
        """;

    /** A MISSING field on the file's FIRST data record; the second record is well formed. */
    private static final String SHORT_FIRST = """
        id,name
        1
        2,b
        """;

    /** A missing field on the file's LAST record; the first record is well formed. */
    private static final String SHORT_LAST = """
        id,name
        1,a
        2
        """;

    /** A missing field on the ONLY record. */
    private static final String SHORT_ONLY = """
        id,name
        1
        """;

    /** A wholly BLANK line between two well-formed records. */
    private static final String BLANK_IN_MIDDLE = """
        id,name
        1,a

        2,b
        """;

    /** The right field count throughout, the second record's trailing field simply empty. */
    private static final String EMPTY_TRAILING_FIELD = """
        id,name
        1,a
        2,
        """;

    /** Two well-formed records — the control every rejecting case is read against. */
    private static final String TWO_GOOD_ROWS = """
        id,name
        1,a
        2,b
        """;

    /** Three fields per record, for the three-column table. */
    private static final String THREE_FIELDS = """
        id,name,age
        1,a,30
        2,b,40
        """;

    /** ONE field per record against the three-column table — narrower by two. */
    private static final String ONE_FIELD_OF_THREE = """
        id,name,age
        1
        """;

    /** FIVE fields per record against the three-column table — wider by two. */
    private static final String FIVE_FIELDS_OF_THREE = """
        id,name,age
        1,a,30,x,y
        """;

    /** TWO fields per record against the three-column table, on BOTH records. */
    private static final String TWO_FIELDS_OF_THREE = """
        id,name,age
        1,a
        2,b
        """;

    /** Pipe-delimited, extra field on the FIRST record. */
    private static final String PIPE_EXTRA_ON_FIRST = """
        id|name
        1|a|extra
        2|b
        """;

    /** Pipe-delimited, extra field on the LAST record. */
    private static final String PIPE_EXTRA_ON_LAST = """
        id|name
        1|a
        2|b|extra
        """;

    private static final String COUNT_MISMATCH_3_OF_2 =
        "Number of columns in file (3) does not match that of the corresponding table (2),"
            + " use file format option error_on_column_count_mismatch=false to ignore this error";

    private static final String COUNT_MISMATCH_1_OF_2 =
        "Number of columns in file (1) does not match that of the corresponding table (2),"
            + " use file format option error_on_column_count_mismatch=false to ignore this error";

    /** PUT a CSV written outside every stage root onto the table stage {@code @%<table>}. */
    private void stage(final String table, final String fileName, final String content) throws IOException {
        final Path file = localDir.resolve(fileName);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        engine.executeQuery("PUT file://" + file + " @%" + table + " AUTO_COMPRESS=FALSE");
    }

    private ResultSet copy(final String table, final String options) {
        return copyWithFormat(table, "", options);
    }

    /**
     * COPY with extra options folded INSIDE the FILE_FORMAT parens, which is the only place Snowflake takes
     * a format-type option: {@code ERROR_ON_COLUMN_COUNT_MISMATCH} is not a statement-level copy option, and
     * writing it beside ON_ERROR leaves it unread.
     */
    private ResultSet copyWithFormat(final String table, final String formatOptions, final String options) {
        return engine.executeQuery("COPY INTO " + table
            + " FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1 " + formatOptions + ") " + options);
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

    /** The whole two-column table as {@code id/name} strings, ordered, with a null name shown as {@code -}. */
    private List<String> contents(final String table) {
        final ResultSet rs = engine.executeQuery("SELECT id, name FROM " + table + " ORDER BY id");
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            final Object name = row.getValue(1);
            out.add(String.valueOf(row.getValue(0)) + "/" + (name == null ? "-" : name.toString()));
        }
        return out;
    }

    private long count(final String table) {
        return ((Number) engine.executeQuery("SELECT COUNT(*) FROM " + table)
            .getRows().get(0).getValue(0)).longValue();
    }

    // ── the default: a mismatched record is REJECTED, and its data never reaches the table ──

    /**
     * THE defect this pins: an extra field is not silently dropped. The account rejects the record, loads only
     * the well-formed one beside it, and names the file's own column count in a bracket carrying NO column
     * name — the one first_error_column_name shape in the whole COPY result without one.
     */
    @Test
    public void anExtraFieldOnTheFirstRecordIsRejected() throws IOException {
        stage("t", "extrafirst.csv", EXTRA_ON_FIRST);

        final Row row = fileRow(copy("t", "ON_ERROR = CONTINUE"), "extrafirst.csv");
        assertEquals("PARTIALLY_LOADED", row.getValue(1));
        assertEquals(2, intCell(row, 2).intValue(), "rows_parsed");
        assertEquals(1, intCell(row, 3).intValue(), "rows_loaded");
        assertEquals(1, intCell(row, 5).intValue(), "errors_seen");
        assertEquals(COUNT_MISMATCH_3_OF_2, row.getValue(6));
        assertEquals(3, intCell(row, 7).intValue(), "first_error_line — just past the record");
        assertEquals(1, intCell(row, 8).intValue(), "first_error_character");
        assertEquals("\"T\"[3]", row.getValue(9));
        assertEquals(List.of("2/b"), contents("t"), "the malformed record's data never lands");
    }

    /**
     * The same rejection in the other direction: a missing field is not NULL-padded. The column named is the
     * ordinal the file DID reach, not the one it was missing.
     */
    @Test
    public void aMissingFieldOnTheFirstRecordIsRejected() throws IOException {
        stage("t", "shortfirst.csv", SHORT_FIRST);

        final Row row = fileRow(copy("t", "ON_ERROR = CONTINUE"), "shortfirst.csv");
        assertEquals("PARTIALLY_LOADED", row.getValue(1));
        assertEquals(1, intCell(row, 3).intValue(), "rows_loaded");
        assertEquals(1, intCell(row, 5).intValue(), "errors_seen");
        assertEquals(COUNT_MISMATCH_1_OF_2, row.getValue(6));
        assertEquals(3, intCell(row, 7).intValue(), "first_error_line");
        assertEquals(1, intCell(row, 8).intValue(), "first_error_character");
        assertEquals("\"T\"[\"ID\":1]", row.getValue(9));
        assertEquals(List.of("2/b"), contents("t"));
    }

    /**
     * A LATER record with an extra field gets a different message entirely — the parser's own, naming the
     * delimiter that should have ended the record and pointing straight at it.
     */
    @Test
    public void anExtraFieldOnALaterRecordReportsTheDelimiterWording() throws IOException {
        stage("t", "extralast.csv", EXTRA_ON_LAST);

        final Row row = fileRow(copy("t", "ON_ERROR = CONTINUE"), "extralast.csv");
        assertEquals("PARTIALLY_LOADED", row.getValue(1));
        assertEquals(1, intCell(row, 5).intValue(), "errors_seen");
        assertEquals("Field delimiter ',' found while expecting record delimiter '\\n'", row.getValue(6));
        assertEquals(3, intCell(row, 7).intValue(), "first_error_line — the record's own line");
        assertEquals(4, intCell(row, 8).intValue(), "first_error_character — the offending delimiter");
        assertEquals("\"T\"[\"NAME\":2]", row.getValue(9));
        assertEquals(List.of("1/a"), contents("t"));
    }

    /** A later record with a MISSING field names the first column it never reached, inside the message. */
    @Test
    public void aMissingFieldOnALaterRecordReportsTheEndOfRecordWording() throws IOException {
        stage("t", "shortlast.csv", SHORT_LAST);

        final Row row = fileRow(copy("t", "ON_ERROR = CONTINUE"), "shortlast.csv");
        assertEquals("PARTIALLY_LOADED", row.getValue(1));
        assertEquals("End of record reached while expected to parse column '\"T\"[\"NAME\":2]'", row.getValue(6));
        assertEquals(3, intCell(row, 7).intValue(), "first_error_line");
        assertEquals(2, intCell(row, 8).intValue(), "first_error_character — just past the record");
        assertEquals("\"T\"[\"NAME\":2]", row.getValue(9));
        assertEquals(List.of("1/a"), contents("t"));
    }

    /**
     * When the mismatched first record is also the file's LAST line there is no following line to point at,
     * so the same position is reported as the end of the record's own line instead.
     */
    @Test
    public void aMismatchedOnlyRecordReportsAtTheEndOfItsOwnLine() throws IOException {
        stage("t", "extraonly.csv", EXTRA_ON_ONLY);

        final Row row = fileRow(copy("t", "ON_ERROR = CONTINUE"), "extraonly.csv");
        assertEquals("LOAD_FAILED", row.getValue(1));
        assertEquals(1, intCell(row, 2).intValue(), "rows_parsed");
        assertEquals(0, intCell(row, 3).intValue(), "rows_loaded");
        assertEquals(COUNT_MISMATCH_3_OF_2, row.getValue(6));
        assertEquals(2, intCell(row, 7).intValue(), "first_error_line — the record's own line");
        assertEquals(10, intCell(row, 8).intValue(), "first_error_character — one past '1,a,extra'");
        assertEquals("\"T\"[3]", row.getValue(9));
        assertEquals(0, count("t"));
    }

    /** The same clamp for a short ONLY record. */
    @Test
    public void aShortOnlyRecordReportsAtTheEndOfItsOwnLine() throws IOException {
        stage("t", "shortonly.csv", SHORT_ONLY);

        final Row row = fileRow(copy("t", "ON_ERROR = CONTINUE"), "shortonly.csv");
        assertEquals("LOAD_FAILED", row.getValue(1));
        assertEquals(COUNT_MISMATCH_1_OF_2, row.getValue(6));
        assertEquals(2, intCell(row, 7).intValue(), "first_error_line");
        assertEquals(2, intCell(row, 8).intValue(), "first_error_character — one past '1'");
        assertEquals("\"T\"[\"ID\":1]", row.getValue(9));
        assertEquals(0, count("t"));
    }

    /** A file wider than the table by TWO still names the file's own count, and still names no column. */
    @Test
    public void aFileWiderThanTheTableNamesNoColumnAtAll() throws IOException {
        engine.execute("CREATE TABLE w (id INTEGER, name VARCHAR, age INTEGER)");
        stage("w", "five.csv", FIVE_FIELDS_OF_THREE);

        final Row row = fileRow(copy("w", "ON_ERROR = CONTINUE"), "five.csv");
        assertEquals("LOAD_FAILED", row.getValue(1));
        assertEquals("Number of columns in file (5) does not match that of the corresponding table (3),"
            + " use file format option error_on_column_count_mismatch=false to ignore this error",
            row.getValue(6));
        assertEquals(2, intCell(row, 7).intValue(), "first_error_line");
        assertEquals(11, intCell(row, 8).intValue(), "first_error_character — one past '1,a,30,x,y'");
        assertEquals("\"W\"[5]", row.getValue(9));
    }

    /** A file narrower than the table names the column at the ordinal it DID reach. */
    @Test
    public void aFileNarrowerThanTheTableNamesTheColumnItStoppedAt() throws IOException {
        engine.execute("CREATE TABLE w (id INTEGER, name VARCHAR, age INTEGER)");
        stage("w", "one.csv", ONE_FIELD_OF_THREE);

        final Row row = fileRow(copy("w", "ON_ERROR = CONTINUE"), "one.csv");
        assertEquals("Number of columns in file (1) does not match that of the corresponding table (3),"
            + " use file format option error_on_column_count_mismatch=false to ignore this error",
            row.getValue(6));
        assertEquals("\"W\"[\"ID\":1]", row.getValue(9));
    }

    /** Every record short by one counts as its own error, and the FIRST of them is the one described. */
    @Test
    public void everyMismatchedRecordCountsAsItsOwnError() throws IOException {
        engine.execute("CREATE TABLE w (id INTEGER, name VARCHAR, age INTEGER)");
        stage("w", "twoofthree.csv", TWO_FIELDS_OF_THREE);

        final Row row = fileRow(copy("w", "ON_ERROR = CONTINUE"), "twoofthree.csv");
        assertEquals("LOAD_FAILED", row.getValue(1));
        assertEquals(2, intCell(row, 2).intValue(), "rows_parsed");
        assertEquals(2, intCell(row, 5).intValue(), "errors_seen — both records rejected");
        assertEquals("Number of columns in file (2) does not match that of the corresponding table (3),"
            + " use file format option error_on_column_count_mismatch=false to ignore this error",
            row.getValue(6));
        assertEquals(3, intCell(row, 7).intValue(), "first_error_line");
        assertEquals("\"W\"[\"NAME\":2]", row.getValue(9));
    }

    /** The aborting default fails the statement outright and leaves the table untouched. */
    @Test
    public void theDefaultOnErrorFailsTheStatement() throws IOException {
        stage("t", "extrafirst.csv", EXTRA_ON_FIRST);

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                copy("t", "");
            }
        });
        assertEquals(0, count("t"), "an aborting COPY loads nothing at all");
    }

    // ── the option: FALSE restores the lenient reshaping, and is the ONLY way to get it ──

    /** {@code = FALSE}: the extra field is dropped, the record loads, and nothing is reported. */
    @Test
    public void theOptionOffDropsTheExtraField() throws IOException {
        stage("t", "extrafirst.csv", EXTRA_ON_FIRST);

        final Row row = fileRow(copyWithFormat("t", "ERROR_ON_COLUMN_COUNT_MISMATCH = FALSE", ""), "extrafirst.csv");
        assertEquals("LOADED", row.getValue(1));
        assertEquals(2, intCell(row, 2).intValue(), "rows_parsed");
        assertEquals(2, intCell(row, 3).intValue(), "rows_loaded");
        assertEquals(0, intCell(row, 5).intValue(), "errors_seen");
        assertNull(row.getValue(6), "first_error");
        assertNull(row.getValue(7), "first_error_line");
        assertNull(row.getValue(8), "first_error_character");
        assertNull(row.getValue(9), "first_error_column_name");
        assertEquals(List.of("1/a", "2/b"), contents("t"));
    }

    /** {@code = FALSE}: the missing field is NULL-padded rather than rejected. */
    @Test
    public void theOptionOffNullPadsTheMissingField() throws IOException {
        stage("t", "shortfirst.csv", SHORT_FIRST);

        final Row row = fileRow(copyWithFormat("t", "ERROR_ON_COLUMN_COUNT_MISMATCH = FALSE", ""), "shortfirst.csv");
        assertEquals("LOADED", row.getValue(1));
        assertEquals(2, intCell(row, 3).intValue(), "rows_loaded");
        assertEquals(0, intCell(row, 5).intValue(), "errors_seen");
        assertEquals(List.of("1/-", "2/b"), contents("t"));
    }

    /** The option covers LATER records too, not only the one that decides the message. */
    @Test
    public void theOptionOffCoversLaterRecordsToo() throws IOException {
        stage("t", "extramid.csv", EXTRA_IN_MIDDLE);

        final Row row = fileRow(copyWithFormat("t", "ERROR_ON_COLUMN_COUNT_MISMATCH = FALSE", ""), "extramid.csv");
        assertEquals("LOADED", row.getValue(1));
        assertEquals(3, intCell(row, 3).intValue(), "rows_loaded");
        assertEquals(List.of("1/a", "2/b", "3/c"), contents("t"));
    }

    /** {@code = TRUE} spelled out is the default, not a change of behaviour. */
    @Test
    public void theOptionOnSpelledOutIsTheDefault() throws IOException {
        stage("t", "extrafirst.csv", EXTRA_ON_FIRST);

        final Row row = fileRow(
            copyWithFormat("t", "ERROR_ON_COLUMN_COUNT_MISMATCH = TRUE", "ON_ERROR = CONTINUE"), "extrafirst.csv");
        assertEquals("PARTIALLY_LOADED", row.getValue(1));
        assertEquals(1, intCell(row, 5).intValue(), "errors_seen");
        assertEquals(COUNT_MISMATCH_3_OF_2, row.getValue(6));
    }

    /** A named FILE FORMAT carries the option exactly as an inline one does. */
    @Test
    public void aNamedFileFormatCarriesTheOption() throws IOException {
        engine.execute("CREATE FILE FORMAT lenient TYPE = CSV SKIP_HEADER = 1"
            + " ERROR_ON_COLUMN_COUNT_MISMATCH = FALSE");
        stage("t", "extrafirst.csv", EXTRA_ON_FIRST);

        final Row row = fileRow(
            engine.executeQuery("COPY INTO t FILE_FORMAT = (FORMAT_NAME = 'lenient')"), "extrafirst.csv");
        assertEquals("LOADED", row.getValue(1));
        assertEquals(List.of("1/a", "2/b"), contents("t"));
    }

    // ── the three clauses that suspend the check entirely ──

    /** An explicit column list suspends the check — a record SHORTER than the list still loads. */
    @Test
    public void anExplicitColumnListSuspendsTheCheck() throws IOException {
        stage("t", "shortfirst.csv", SHORT_FIRST);

        final Row row = fileRow(engine.executeQuery("COPY INTO t (id, name) "
            + "FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1) ON_ERROR = CONTINUE"), "shortfirst.csv");
        assertEquals("LOADED", row.getValue(1));
        assertEquals(0, intCell(row, 5).intValue(), "errors_seen");
        assertEquals(List.of("1/-", "2/b"), contents("t"));
    }

    /** …and in the other direction: a file WIDER than the list loads too, its surplus dropped. */
    @Test
    public void anExplicitColumnListSuspendsTheCheckForExtraFieldsToo() throws IOException {
        engine.execute("CREATE TABLE w (id INTEGER, name VARCHAR, age INTEGER)");
        stage("w", "three.csv", THREE_FIELDS);

        final Row row = fileRow(engine.executeQuery("COPY INTO w (id, name) "
            + "FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1) ON_ERROR = CONTINUE"), "three.csv");
        assertEquals("LOADED", row.getValue(1));
        assertEquals(2, intCell(row, 3).intValue(), "rows_loaded");
        assertEquals(0, ((Number) engine.executeQuery("SELECT COUNT(age) FROM w")
            .getRows().get(0).getValue(0)).longValue(), "the unlisted column stays NULL");
    }

    /** A COPY transformation suspends it too — the workaround Snowflake's own docs point at. */
    @Test
    public void aCopyTransformationSuspendsTheCheck() throws IOException {
        stage("t", "extrafirst.csv", EXTRA_ON_FIRST);

        final Row row = fileRow(engine.executeQuery("COPY INTO t FROM (SELECT $1, $2 FROM @%t) "
            + "FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1) ON_ERROR = CONTINUE"), "extrafirst.csv");
        assertEquals("LOADED", row.getValue(1));
        assertEquals(0, intCell(row, 5).intValue(), "errors_seen");
        assertEquals(List.of("1/a", "2/b"), contents("t"));
    }

    /** …including over a file whose records are SHORT of the projection. */
    @Test
    public void aCopyTransformationSuspendsTheCheckForShortRecordsToo() throws IOException {
        stage("t", "shortfirst.csv", SHORT_FIRST);

        final Row row = fileRow(engine.executeQuery("COPY INTO t FROM (SELECT $1, $2 FROM @%t) "
            + "FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1) ON_ERROR = CONTINUE"), "shortfirst.csv");
        assertEquals("LOADED", row.getValue(1));
        assertEquals(List.of("1/-", "2/b"), contents("t"));
    }

    // ── a count reject is an ORDINARY error: it spends ON_ERROR's budget like any other ──

    /** SKIP_FILE's budget of one is reached by a single count mismatch, dropping the whole file. */
    @Test
    public void aCountMismatchSpendsTheOnErrorBudget() throws IOException {
        stage("t", "extramid.csv", EXTRA_IN_MIDDLE);

        final Row row = fileRow(copy("t", "ON_ERROR = SKIP_FILE"), "extramid.csv");
        assertEquals("LOAD_FAILED", row.getValue(1));
        assertEquals(3, intCell(row, 2).intValue(), "rows_parsed");
        assertEquals(0, intCell(row, 3).intValue(), "rows_loaded");
        assertEquals(1, intCell(row, 4).intValue(), "error_limit");
        assertEquals(1, intCell(row, 5).intValue(), "errors_seen");
        assertEquals(0, count("t"), "the file's clean records go down with it");
    }

    /** A wider budget lets the same file through, minus the one rejected record. */
    @Test
    public void aCountMismatchUnderAWiderBudgetIsPartiallyLoaded() throws IOException {
        stage("t", "extramid.csv", EXTRA_IN_MIDDLE);

        final Row row = fileRow(copy("t", "ON_ERROR = SKIP_FILE_2"), "extramid.csv");
        assertEquals("PARTIALLY_LOADED", row.getValue(1));
        assertEquals(2, intCell(row, 3).intValue(), "rows_loaded");
        assertEquals(2, intCell(row, 4).intValue(), "error_limit");
        assertEquals(1, intCell(row, 5).intValue(), "errors_seen");
        assertEquals(List.of("1/a", "3/c"), contents("t"));
    }

    // ── the delimiter the message quotes is the file format's own ──

    /** A pipe-delimited file's LATER-record message quotes the pipe, not a comma. */
    @Test
    public void theReportedDelimiterIsTheFileFormatsOwn() throws IOException {
        stage("t", "pipelast.csv", PIPE_EXTRA_ON_LAST);

        final Row row = fileRow(
            copyWithFormat("t", "FIELD_DELIMITER = '|'", "ON_ERROR = CONTINUE"), "pipelast.csv");
        assertEquals("Field delimiter '|' found while expecting record delimiter '\\n'", row.getValue(6));
        assertEquals(4, intCell(row, 8).intValue(), "first_error_character");
    }

    /** The FIRST-record message names no delimiter at all, whichever one the file uses. */
    @Test
    public void theFirstRecordMessageNamesNoDelimiter() throws IOException {
        stage("t", "pipefirst.csv", PIPE_EXTRA_ON_FIRST);

        final Row row = fileRow(
            copyWithFormat("t", "FIELD_DELIMITER = '|'", "ON_ERROR = CONTINUE"), "pipefirst.csv");
        assertEquals(COUNT_MISMATCH_3_OF_2, row.getValue(6));
        assertEquals(3, intCell(row, 7).intValue(), "first_error_line");
        assertEquals(1, intCell(row, 8).intValue(), "first_error_character");
    }

    // ── a blank line is a record, and SKIP_BLANK_LINES is what passes it over ──

    /** By default a blank line is a one-empty-field record, so on a two-column table it is a short record. */
    @Test
    public void aBlankLineIsAShortRecordAndIsRejected() throws IOException {
        stage("t", "blank.csv", BLANK_IN_MIDDLE);

        final Row row = fileRow(copy("t", "ON_ERROR = CONTINUE"), "blank.csv");
        assertEquals("PARTIALLY_LOADED", row.getValue(1));
        assertEquals(3, intCell(row, 2).intValue(), "rows_parsed — the blank line counts");
        assertEquals(2, intCell(row, 3).intValue(), "rows_loaded");
        assertEquals(1, intCell(row, 5).intValue(), "errors_seen");
        assertEquals("End of record reached while expected to parse column '\"T\"[\"NAME\":2]'", row.getValue(6));
        assertEquals(3, intCell(row, 7).intValue(), "first_error_line");
        assertEquals(1, intCell(row, 8).intValue(), "first_error_character");
        assertEquals(List.of("1/a", "2/b"), contents("t"));
    }

    /** SKIP_BLANK_LINES passes it over — but it is still counted among the records parsed. */
    @Test
    public void skipBlankLinesPassesTheBlankLineOver() throws IOException {
        stage("t", "blank.csv", BLANK_IN_MIDDLE);

        final Row row = fileRow(
            copyWithFormat("t", "SKIP_BLANK_LINES = TRUE", "ON_ERROR = CONTINUE"), "blank.csv");
        assertEquals("LOADED", row.getValue(1));
        assertEquals(3, intCell(row, 2).intValue(), "rows_parsed — still counted");
        assertEquals(2, intCell(row, 3).intValue(), "rows_loaded");
        assertEquals(0, intCell(row, 5).intValue(), "errors_seen");
        assertEquals(List.of("1/a", "2/b"), contents("t"));
    }

    /**
     * The blank line is only an ERROR because the table is wider than one column. Against a ONE-column table
     * its single empty field is the right count, so it loads as a NULL record rather than being rejected —
     * which is what makes it a count rule and not a blank-line rule.
     */
    @Test
    public void aBlankLineIntoAOneColumnTableIsANullRecord() throws IOException {
        engine.execute("CREATE TABLE one (v VARCHAR)");
        stage("one", "onecol.csv", """
            v
            1

            2
            """);

        final Row row = fileRow(copy("one", "ON_ERROR = CONTINUE"), "onecol.csv");
        assertEquals("LOADED", row.getValue(1));
        assertEquals(3, intCell(row, 2).intValue(), "rows_parsed");
        assertEquals(3, intCell(row, 3).intValue(), "rows_loaded — the blank line among them");
        assertEquals(0, intCell(row, 5).intValue(), "errors_seen");
        assertEquals(2, ((Number) engine.executeQuery("SELECT COUNT(v) FROM one")
            .getRows().get(0).getValue(0)).longValue(), "the blank line landed as NULL");
    }

    /**
     * A file ending in TWO newlines carries a real trailing blank line, and it is a record like any other —
     * whereas the single trailing newline every well-formed file ends with is a terminator and adds nothing.
     */
    @Test
    public void aTrailingBlankLineIsARecordOfItsOwn() throws IOException {
        stage("t", "trailblank.csv", "id,name\n1,a\n2,b\n\n");

        final Row row = fileRow(copy("t", "ON_ERROR = CONTINUE"), "trailblank.csv");
        assertEquals("PARTIALLY_LOADED", row.getValue(1));
        assertEquals(3, intCell(row, 2).intValue(), "rows_parsed");
        assertEquals(2, intCell(row, 3).intValue(), "rows_loaded");
        assertEquals("End of record reached while expected to parse column '\"T\"[\"NAME\":2]'", row.getValue(6));
        assertEquals(4, intCell(row, 7).intValue(), "first_error_line");
        assertEquals(1, intCell(row, 8).intValue(), "first_error_character");
        assertEquals(List.of("1/a", "2/b"), contents("t"));
    }

    /** …and a file with NO trailing newline at all still ends after its last real record. */
    @Test
    public void aFileWithoutATrailingNewlineHasNoPhantomRecord() throws IOException {
        stage("t", "notrail.csv", "id,name\n1,a\n2,b");

        final Row row = fileRow(copy("t", "ON_ERROR = CONTINUE"), "notrail.csv");
        assertEquals("LOADED", row.getValue(1));
        assertEquals(2, intCell(row, 2).intValue(), "rows_parsed");
        assertEquals(0, intCell(row, 5).intValue(), "errors_seen");
    }

    // ── controls: what the rule must NOT reject ──

    /** An EMPTY trailing field is a field. The record's count is right, so nothing is rejected. */
    @Test
    public void anEmptyTrailingFieldIsStillAField() throws IOException {
        stage("t", "emptytrail.csv", EMPTY_TRAILING_FIELD);

        final Row row = fileRow(copy("t", "ON_ERROR = CONTINUE"), "emptytrail.csv");
        assertEquals("LOADED", row.getValue(1));
        assertEquals(2, intCell(row, 3).intValue(), "rows_loaded");
        assertEquals(0, intCell(row, 5).intValue(), "errors_seen");
        assertNull(row.getValue(6), "first_error");
        assertEquals(List.of("1/a", "2/-"), contents("t"));
    }

    /** A well-formed file is untouched by the rule. */
    @Test
    public void aWellFormedFileIsUnaffected() throws IOException {
        stage("t", "good.csv", TWO_GOOD_ROWS);

        final Row row = fileRow(copy("t", "ON_ERROR = CONTINUE"), "good.csv");
        assertEquals("LOADED", row.getValue(1));
        assertEquals(0, intCell(row, 5).intValue(), "errors_seen");
        assertNull(row.getValue(6), "first_error");
        assertEquals(List.of("1/a", "2/b"), contents("t"));
    }

    /** A header-only file has no record to count, so the rule never fires. */
    @Test
    public void aHeaderOnlyFileHasNothingToCount() throws IOException {
        stage("t", "headeronly.csv", "id,name\n");

        final Row row = fileRow(copy("t", "ON_ERROR = CONTINUE"), "headeronly.csv");
        assertEquals("LOADED", row.getValue(1));
        assertEquals(0, intCell(row, 2).intValue(), "rows_parsed");
        assertNull(row.getValue(6), "first_error");
    }

    // ── VALIDATION_MODE sees the same rejection ──

    /** RETURN_ERRORS lists the count mismatch as a would-be-rejected record and loads nothing. */
    @Test
    public void validationModeReturnErrorsListsTheCountMismatch() throws IOException {
        stage("t", "extrafirst.csv", EXTRA_ON_FIRST);

        final ResultSet rs = copy("t", "VALIDATION_MODE = RETURN_ERRORS");
        assertEquals(1, rs.getRows().size(), "one would-be-rejected record");
        assertEquals(COUNT_MISMATCH_3_OF_2, rs.getRows().get(0).getValue(0));
        assertEquals("extrafirst.csv", rs.getRows().get(0).getValue(1));
        assertEquals(3, ((Number) rs.getRows().get(0).getValue(2)).intValue(), "LINE — just past the record");
        assertEquals(0, count("t"), "VALIDATION_MODE loads nothing");
    }

    /** RETURN_&lt;n&gt;_ROWS fails on the mismatched record rather than previewing past it. */
    @Test
    public void validationModeReturnRowsThrowsOnTheCountMismatch() throws IOException {
        stage("t", "extrafirst.csv", EXTRA_ON_FIRST);

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                copy("t", "VALIDATION_MODE = RETURN_2_ROWS");
            }
        });
        assertEquals(0, count("t"));
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
}
