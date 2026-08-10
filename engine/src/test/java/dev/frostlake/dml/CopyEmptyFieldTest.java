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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests {@code EMPTY_FIELD_AS_NULL} — the CSV file-format option, Snowflake default TRUE, that decides
 * whether an empty staged field loads as SQL NULL or as the empty string.
 *
 * <p>The COPY result set looks identical either way (LOADED, the same counts, no error), so every case here
 * asserts the resulting TABLE CONTENTS — {@code IS NULL} against {@code = ''} — which is what makes the
 * difference a silent-wrong-data defect rather than a cosmetic one: it only surfaces later, in IS NULL
 * checks, joins and aggregate counts.
 *
 * <p>Every value asserted here was live-verified against a real account on each case paired with
 * a control run producing the other outcome: the option off beside the option on over the very same staged
 * file, a string column beside a numeric/temporal/boolean one, an enclosed empty field beside a bare one,
 * and a NULL_IF list that lists the empty string beside one that does not.
 */
public class CopyEmptyFieldTest {

    private DatabaseEngine engine;
    private Path internalRoot;
    private Path localDir;
    private Path stageDir;

    @BeforeEach
    public void setUp() throws IOException {
        internalRoot = Files.createTempDirectory("copy_empty_field_root_");
        localDir = Files.createTempDirectory("copy_empty_field_local_");
        stageDir = Files.createTempDirectory("copy_empty_field_stage_");
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_STAGE_INTERNAL_LOCAL_ROOT, internalRoot.toString());
        cfg.setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "true");
        engine = new DatabaseEngine(cfg);
        engine.execute("CREATE DATABASE db");
        engine.execute("USE DATABASE db");
        engine.execute("CREATE SCHEMA s");
        engine.execute("USE SCHEMA s");
        engine.execute("CREATE TABLE t (k INTEGER, v VARCHAR)");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
        deleteRecursively(internalRoot.toFile());
        deleteRecursively(localDir.toFile());
        deleteRecursively(stageDir.toFile());
    }

    /** One record whose LAST field is empty. */
    private static final String TRAILING_EMPTY = """
        k,v
        1,
        """;

    /** One record whose FIRST field is empty. */
    private static final String LEADING_EMPTY = """
        a,b,c
        ,2,3
        """;

    /** One record whose MIDDLE field is empty, with a real field on either side. */
    private static final String MIDDLE_EMPTY = """
        a,b,c
        1,,3
        """;

    /** An ENCLOSED empty field beside a bare one, in the same record. */
    private static final String QUOTED_EMPTY = """
        k,q,b
        1,"",
        """;

    /** An empty field on one record and a real value on the next, for the NULL_IF cases. */
    private static final String EMPTY_AND_TEXT = """
        k,v
        1,
        2,x
        """;

    /** A wholly blank line after a one-field record — a one-empty-field record of its own. */
    private static final String BLANK_LINE = """
        v
        x

        """;

    /**
     * A field of nothing but spaces, for the TRIM_SPACE interaction. Written as an escaped literal rather
     * than a text block: Java strips trailing whitespace from every text-block line, which is exactly the
     * whitespace this fixture is about.
     */
    private static final String SPACES_ONLY = "k,v\n1,   \n";

    /** PUT a CSV written outside every stage root onto the table stage {@code @%<table>}. */
    private void stage(final String table, final String fileName, final String content) throws IOException {
        final Path file = localDir.resolve(fileName);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        engine.executeQuery("PUT file://" + file + " @%" + table + " AUTO_COMPRESS=FALSE");
    }

    /** COPY with the given extra options folded INSIDE the FILE_FORMAT parens, where format options belong. */
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

    /**
     * One column's loaded value read back through SQL: {@code NULL} when it is SQL NULL, otherwise the text
     * wrapped in angle brackets — so the empty string reads as {@code <>} and is impossible to confuse with
     * NULL, which is the whole distinction under test.
     */
    private String loaded(final String table, final String column) {
        final ResultSet rs = engine.executeQuery("SELECT CASE WHEN " + column + " IS NULL THEN 'NULL'"
            + " ELSE '<' || " + column + " || '>' END FROM " + table);
        assertEquals(1, rs.getRows().size(), "expected exactly one loaded row in " + table);
        return String.valueOf(rs.getRows().get(0).getValue(0));
    }

    private long count(final String table, final String where) {
        return ((Number) engine.executeQuery("SELECT COUNT(*) FROM " + table + " " + where)
            .getRows().get(0).getValue(0)).longValue();
    }

    // ── the default: an empty field is SQL NULL ──

    /** The default (option absent) nulls an empty field — pinned so the fix cannot invert it. */
    @Test
    public void theDefaultLoadsAnEmptyFieldAsNull() throws IOException {
        stage("t", "trail.csv", TRAILING_EMPTY);

        final Row row = fileRow(copyWithFormat("t", "", "ON_ERROR = CONTINUE"), "trail.csv");
        assertEquals("LOADED", row.getValue(1));
        assertEquals(1, intCell(row, 3).intValue(), "rows_loaded");
        assertEquals(0, intCell(row, 5).intValue(), "errors_seen");
        assertEquals("NULL", loaded("t", "v"));
        assertEquals(1, count("t", "WHERE v IS NULL"));
        assertEquals(0, count("t", "WHERE v = ''"));
    }

    /** {@code = TRUE} spelled out is the default, not a change of behaviour. */
    @Test
    public void theOptionOnSpelledOutIsTheDefault() throws IOException {
        stage("t", "trail.csv", TRAILING_EMPTY);

        copyWithFormat("t", "EMPTY_FIELD_AS_NULL = TRUE", "ON_ERROR = CONTINUE");
        assertEquals("NULL", loaded("t", "v"));
    }

    // ── the option: FALSE loads the empty string, and is the ONLY way to get it ──

    /** THE defect this pins: {@code = FALSE} loads the empty string, and the load reports nothing either way. */
    @Test
    public void theOptionOffLoadsTheEmptyString() throws IOException {
        stage("t", "trail.csv", TRAILING_EMPTY);

        final Row row = fileRow(
            copyWithFormat("t", "EMPTY_FIELD_AS_NULL = FALSE", "ON_ERROR = CONTINUE"), "trail.csv");
        assertEquals("LOADED", row.getValue(1));
        assertEquals(1, intCell(row, 3).intValue(), "rows_loaded");
        assertEquals(0, intCell(row, 5).intValue(), "errors_seen");
        assertNull(row.getValue(6), "first_error — the two settings are the same load, reported identically");
        assertEquals("<>", loaded("t", "v"));
        assertEquals(0, count("t", "WHERE v IS NULL"));
        assertEquals(1, count("t", "WHERE v = ''"));
    }

    /** The position of the empty field in the record makes no difference — a LEADING one. */
    @Test
    public void theOptionOffCoversALeadingEmptyField() throws IOException {
        engine.execute("CREATE TABLE w (a VARCHAR, b INTEGER, c INTEGER)");
        stage("w", "lead.csv", LEADING_EMPTY);

        copyWithFormat("w", "EMPTY_FIELD_AS_NULL = FALSE", "ON_ERROR = CONTINUE");
        assertEquals("<>", loaded("w", "a"));
        assertEquals(1, count("w", "WHERE b = 2 AND c = 3"), "the fields beside it are unaffected");
    }

    /** …and a MIDDLE one, which #147 established is a real field like any other. */
    @Test
    public void theOptionOffCoversAMiddleEmptyField() throws IOException {
        engine.execute("CREATE TABLE w (a INTEGER, b VARCHAR, c INTEGER)");
        stage("w", "mid.csv", MIDDLE_EMPTY);

        copyWithFormat("w", "EMPTY_FIELD_AS_NULL = FALSE", "ON_ERROR = CONTINUE");
        assertEquals("<>", loaded("w", "b"));
        assertEquals(1, count("w", "WHERE a = 1 AND c = 3"));
    }

    /** The default nulls a middle empty field too — the control for the case above. */
    @Test
    public void theDefaultNullsAMiddleEmptyField() throws IOException {
        engine.execute("CREATE TABLE w (a INTEGER, b VARCHAR, c INTEGER)");
        stage("w", "mid.csv", MIDDLE_EMPTY);

        copyWithFormat("w", "", "ON_ERROR = CONTINUE");
        assertEquals("NULL", loaded("w", "b"));
    }

    // ── a non-string column: the empty string is not a number, and the record is REJECTED ──

    /**
     * The case a naive implementation gets wrong. Turning the empty field into the empty string does NOT
     * quietly load a zero (or a NULL) into a numeric column: the value is coerced like any other text and
     * fails, so the whole record is rejected — with the account's own wording, character position and column
     * reference.
     */
    @Test
    public void anEmptyFieldIntoANumericColumnIsRejected() throws IOException {
        engine.execute("CREATE TABLE n (k INTEGER, v INTEGER)");
        stage("n", "trail.csv", TRAILING_EMPTY);

        final Row row = fileRow(
            copyWithFormat("n", "EMPTY_FIELD_AS_NULL = FALSE", "ON_ERROR = CONTINUE"), "trail.csv");
        assertEquals("LOAD_FAILED", row.getValue(1));
        assertEquals(1, intCell(row, 2).intValue(), "rows_parsed");
        assertEquals(0, intCell(row, 3).intValue(), "rows_loaded");
        assertEquals(1, intCell(row, 5).intValue(), "errors_seen");
        assertEquals("Numeric value '' is not recognized", row.getValue(6));
        assertEquals(2, intCell(row, 7).intValue(), "first_error_line");
        assertEquals(3, intCell(row, 8).intValue(), "first_error_character — where the empty field starts");
        assertEquals("\"N\"[\"V\":2]", row.getValue(9));
        assertEquals(0, count("n", ""));
    }

    /** The same column under the DEFAULT loads a NULL and reports nothing — the control. */
    @Test
    public void theDefaultLoadsNullIntoANumericColumn() throws IOException {
        engine.execute("CREATE TABLE n (k INTEGER, v INTEGER)");
        stage("n", "trail.csv", TRAILING_EMPTY);

        final Row row = fileRow(copyWithFormat("n", "", "ON_ERROR = CONTINUE"), "trail.csv");
        assertEquals("LOADED", row.getValue(1));
        assertEquals(0, intCell(row, 5).intValue(), "errors_seen");
        assertEquals("NULL", loaded("n", "v"));
    }

    /** A BOOLEAN column rejects it the same way, in Snowflake's own wording for that type. */
    @Test
    public void anEmptyFieldIntoABooleanColumnIsRejected() throws IOException {
        engine.execute("CREATE TABLE b (k INTEGER, v BOOLEAN)");
        stage("b", "trail.csv", TRAILING_EMPTY);

        final Row row = fileRow(
            copyWithFormat("b", "EMPTY_FIELD_AS_NULL = FALSE", "ON_ERROR = CONTINUE"), "trail.csv");
        assertEquals("LOAD_FAILED", row.getValue(1));
        assertEquals("Boolean value '' is not recognized", row.getValue(6));
        assertEquals(0, count("b", ""));
    }

    /**
     * A DATE column rejects it too. Only the outcome is asserted, not the message: the account says
     * {@code Date '' is not recognized} where this engine's temporal write path reports java.time's own
     * wording for every unparseable value, empty or not — a message divergence of its own, wider than this
     * option.
     */
    @Test
    public void anEmptyFieldIntoADateColumnIsRejected() throws IOException {
        engine.execute("CREATE TABLE d (k INTEGER, v DATE)");
        stage("d", "trail.csv", TRAILING_EMPTY);

        final Row row = fileRow(
            copyWithFormat("d", "EMPTY_FIELD_AS_NULL = FALSE", "ON_ERROR = CONTINUE"), "trail.csv");
        assertEquals("LOAD_FAILED", row.getValue(1));
        assertEquals(1, intCell(row, 5).intValue(), "errors_seen");
        assertEquals(0, count("d", ""));
    }

    /** Under the aborting default ON_ERROR the same rejection fails the whole statement. */
    @Test
    public void aRejectedEmptyFieldAbortsTheStatementByDefault() throws IOException {
        engine.execute("CREATE TABLE n (k INTEGER, v INTEGER)");
        stage("n", "trail.csv", TRAILING_EMPTY);

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                copyWithFormat("n", "EMPTY_FIELD_AS_NULL = FALSE", "");
            }
        });
        assertEquals(0, count("n", ""), "an aborting COPY loads nothing at all");
    }

    /**
     * A semi-structured column is the exception: an empty field is NULL there whatever the option says, and
     * the file still loads cleanly rather than being rejected the way a numeric column is.
     */
    @Test
    public void anEmptyFieldIntoAVariantColumnStaysNull() throws IOException {
        engine.execute("CREATE TABLE vt (k INTEGER, v VARIANT)");
        stage("vt", "trail.csv", TRAILING_EMPTY);

        final Row row = fileRow(
            copyWithFormat("vt", "EMPTY_FIELD_AS_NULL = FALSE", "ON_ERROR = CONTINUE"), "trail.csv");
        assertEquals("LOADED", row.getValue(1));
        assertEquals(0, intCell(row, 5).intValue(), "errors_seen");
        assertEquals(1, count("vt", "WHERE v IS NULL"));
    }

    /** …and an ARRAY column behaves identically, so it is the column KIND and not one type. */
    @Test
    public void anEmptyFieldIntoAnArrayColumnStaysNull() throws IOException {
        engine.execute("CREATE TABLE ar (k INTEGER, v ARRAY)");
        stage("ar", "trail.csv", TRAILING_EMPTY);

        final Row row = fileRow(
            copyWithFormat("ar", "EMPTY_FIELD_AS_NULL = FALSE", "ON_ERROR = CONTINUE"), "trail.csv");
        assertEquals("LOADED", row.getValue(1));
        assertEquals(1, count("ar", "WHERE v IS NULL"));
    }

    // ── NOT NULL: the option decides whether the record satisfies it ──

    /** With the default the empty field is a NULL, so a NOT NULL column rejects the record. */
    @Test
    public void theDefaultRejectsAnEmptyFieldInANotNullColumn() throws IOException {
        engine.execute("CREATE TABLE nn (k INTEGER, v VARCHAR NOT NULL)");
        stage("nn", "trail.csv", TRAILING_EMPTY);

        final Row row = fileRow(copyWithFormat("nn", "", "ON_ERROR = CONTINUE"), "trail.csv");
        assertEquals("LOAD_FAILED", row.getValue(1));
        assertEquals("NULL result in a non-nullable column", row.getValue(6));
        assertEquals(0, count("nn", ""));
    }

    /** {@code = FALSE} makes the same record load: the empty string is a value, and it satisfies NOT NULL. */
    @Test
    public void theOptionOffLetsAnEmptyFieldSatisfyNotNull() throws IOException {
        engine.execute("CREATE TABLE nn (k INTEGER, v VARCHAR NOT NULL)");
        stage("nn", "trail.csv", TRAILING_EMPTY);

        final Row row = fileRow(
            copyWithFormat("nn", "EMPTY_FIELD_AS_NULL = FALSE", "ON_ERROR = CONTINUE"), "trail.csv");
        assertEquals("LOADED", row.getValue(1));
        assertEquals(0, intCell(row, 5).intValue(), "errors_seen");
        assertEquals("<>", loaded("nn", "v"));
    }

    // ── an ENCLOSED empty field is not an empty field ──

    /**
     * With {@code FIELD_OPTIONALLY_ENCLOSED_BY} set, a {@code ""} field loads as the empty string even under
     * the default TRUE — beside a bare empty field in the very same record that loads NULL. The option only
     * ever speaks about the unenclosed one.
     */
    @Test
    public void anEnclosedEmptyFieldIsTheEmptyStringEvenByDefault() throws IOException {
        engine.execute("CREATE TABLE q (k INTEGER, q VARCHAR, b VARCHAR)");
        stage("q", "quoted.csv", QUOTED_EMPTY);

        copyWithFormat("q", "FIELD_OPTIONALLY_ENCLOSED_BY = '\"'", "ON_ERROR = CONTINUE");
        assertEquals("<>", loaded("q", "q"), "the enclosed empty field");
        assertEquals("NULL", loaded("q", "b"), "the bare empty field beside it");
    }

    /** {@code = FALSE} then loads both as the empty string. */
    @Test
    public void theOptionOffLoadsBothEnclosedAndBareEmptyFields() throws IOException {
        engine.execute("CREATE TABLE q (k INTEGER, q VARCHAR, b VARCHAR)");
        stage("q", "quoted.csv", QUOTED_EMPTY);

        copyWithFormat("q", "FIELD_OPTIONALLY_ENCLOSED_BY = '\"' EMPTY_FIELD_AS_NULL = FALSE", "ON_ERROR = CONTINUE");
        assertEquals("<>", loaded("q", "q"));
        assertEquals("<>", loaded("q", "b"));
    }

    /**
     * FIELD_OPTIONALLY_ENCLOSED_BY defaults to NONE, so without it the quotes are ordinary characters and
     * the exemption cannot arise: the {@code ""} field is a two-character string, and only the bare empty
     * one follows the option.
     */
    @Test
    public void withoutTheEnclosureOptionQuotesAreOrdinaryCharacters() throws IOException {
        engine.execute("CREATE TABLE q (k INTEGER, q VARCHAR, b VARCHAR)");
        stage("q", "quoted.csv", QUOTED_EMPTY);

        copyWithFormat("q", "", "ON_ERROR = CONTINUE");
        assertEquals("<\"\">", loaded("q", "q"));
        assertEquals("NULL", loaded("q", "b"));
    }

    // ── NULL_IF and the option are independent, and NULL_IF wins ──

    /** {@code NULL_IF = ('')} nulls the empty field even with the option off. */
    @Test
    public void nullIfWinsOverTheOptionOff() throws IOException {
        stage("t", "both.csv", EMPTY_AND_TEXT);

        copyWithFormat("t", "EMPTY_FIELD_AS_NULL = FALSE NULL_IF = ('')", "ON_ERROR = CONTINUE");
        assertEquals(1, count("t", "WHERE k = 1 AND v IS NULL"));
        assertEquals(1, count("t", "WHERE k = 2 AND v = 'x'"), "the other record is untouched");
    }

    /** A NULL_IF list that does NOT name the empty string leaves the option in charge of it. */
    @Test
    public void aNullIfListThatOmitsTheEmptyStringLeavesItToTheOption() throws IOException {
        stage("t", "both.csv", EMPTY_AND_TEXT);

        copyWithFormat("t", "EMPTY_FIELD_AS_NULL = FALSE NULL_IF = ('x')", "ON_ERROR = CONTINUE");
        assertEquals(1, count("t", "WHERE k = 1 AND v = ''"));
        assertEquals(1, count("t", "WHERE k = 2 AND v IS NULL"));
    }

    /** …and under the default TRUE both the listed token and the empty field come out NULL. */
    @Test
    public void theDefaultNullsTheEmptyFieldBesideANullIfToken() throws IOException {
        stage("t", "both.csv", EMPTY_AND_TEXT);

        copyWithFormat("t", "NULL_IF = ('x')", "ON_ERROR = CONTINUE");
        assertEquals(2, count("t", "WHERE v IS NULL"));
    }

    // ── TRIM_SPACE runs first, so it decides WHAT is empty ──

    /** A spaces-only field trimmed to nothing follows the option: {@code = FALSE} keeps the empty string. */
    @Test
    public void trimSpaceThenTheOptionOffLoadsTheEmptyString() throws IOException {
        stage("t", "spaces.csv", SPACES_ONLY);

        copyWithFormat("t", "TRIM_SPACE = TRUE EMPTY_FIELD_AS_NULL = FALSE", "ON_ERROR = CONTINUE");
        assertEquals("<>", loaded("t", "v"));
    }

    /** …and under the default it becomes NULL, which is what makes the order observable. */
    @Test
    public void trimSpaceThenTheDefaultLoadsNull() throws IOException {
        stage("t", "spaces.csv", SPACES_ONLY);

        copyWithFormat("t", "TRIM_SPACE = TRUE", "ON_ERROR = CONTINUE");
        assertEquals("NULL", loaded("t", "v"));
    }

    /** Without TRIM_SPACE the field is not empty at all, so neither setting touches it. */
    @Test
    public void withoutTrimSpaceASpacesOnlyFieldIsUntouched() throws IOException {
        stage("t", "spaces.csv", SPACES_ONLY);

        copyWithFormat("t", "", "ON_ERROR = CONTINUE");
        assertEquals("<   >", loaded("t", "v"));
    }

    // ── the neighbouring shapes: a blank line, a column list, a named format, a transformation ──

    /** A blank line into a one-column table is a one-empty-field record, and it follows the option. */
    @Test
    public void aBlankLineIntoAOneColumnTableFollowsTheOption() throws IOException {
        engine.execute("CREATE TABLE one (v VARCHAR)");
        stage("one", "blank.csv", BLANK_LINE);

        final Row row = fileRow(
            copyWithFormat("one", "EMPTY_FIELD_AS_NULL = FALSE", "ON_ERROR = CONTINUE"), "blank.csv");
        assertEquals("LOADED", row.getValue(1));
        assertEquals(2, intCell(row, 3).intValue(), "rows_loaded");
        assertEquals(1, count("one", "WHERE v = ''"));
        assertEquals(0, count("one", "WHERE v IS NULL"));
    }

    /** …and under the default the same blank line lands as NULL. */
    @Test
    public void aBlankLineIntoAOneColumnTableIsNullByDefault() throws IOException {
        engine.execute("CREATE TABLE one (v VARCHAR)");
        stage("one", "blank.csv", BLANK_LINE);

        copyWithFormat("one", "", "ON_ERROR = CONTINUE");
        assertEquals(1, count("one", "WHERE v IS NULL"));
    }

    /** An explicit column list changes nothing about the option; the unlisted column stays NULL. */
    @Test
    public void anExplicitColumnListKeepsTheOption() throws IOException {
        engine.execute("CREATE TABLE w (k INTEGER, v VARCHAR, extra VARCHAR)");
        stage("w", "trail.csv", TRAILING_EMPTY);

        engine.executeQuery("COPY INTO w (k, v) FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1"
            + " EMPTY_FIELD_AS_NULL = FALSE) ON_ERROR = CONTINUE");
        assertEquals("<>", loaded("w", "v"));
        assertEquals("NULL", loaded("w", "extra"), "a column no field maps onto is still NULL");
    }

    /** A named FILE FORMAT carries the option exactly as an inline one does. */
    @Test
    public void aNamedFileFormatCarriesTheOption() throws IOException {
        engine.execute("CREATE FILE FORMAT keep_empty TYPE = CSV SKIP_HEADER = 1"
            + " EMPTY_FIELD_AS_NULL = FALSE");
        stage("t", "trail.csv", TRAILING_EMPTY);

        final Row row = fileRow(
            engine.executeQuery("COPY INTO t FILE_FORMAT = (FORMAT_NAME = 'keep_empty')"), "trail.csv");
        assertEquals("LOADED", row.getValue(1));
        assertEquals("<>", loaded("t", "v"));
    }

    /** A named format WITHOUT the option keeps the default — the control for the case above. */
    @Test
    public void aNamedFileFormatWithoutTheOptionKeepsTheDefault() throws IOException {
        engine.execute("CREATE FILE FORMAT plain TYPE = CSV SKIP_HEADER = 1");
        stage("t", "trail.csv", TRAILING_EMPTY);

        engine.executeQuery("COPY INTO t FILE_FORMAT = (FORMAT_NAME = 'plain')");
        assertEquals("NULL", loaded("t", "v"));
    }

    /** A COPY transformation honours the option too — the staged field reaches $2 as the empty string. */
    @Test
    public void aCopyTransformationHonoursTheOption() throws IOException {
        stage("t", "trail.csv", TRAILING_EMPTY);

        engine.executeQuery("COPY INTO t FROM (SELECT $1, $2 FROM @%t) FILE_FORMAT = (TYPE = CSV"
            + " SKIP_HEADER = 1 EMPTY_FIELD_AS_NULL = FALSE) ON_ERROR = CONTINUE");
        assertEquals("<>", loaded("t", "v"));
    }

    /** …and under the default the same transformation projects a NULL. */
    @Test
    public void aCopyTransformationNullsTheEmptyFieldByDefault() throws IOException {
        stage("t", "trail.csv", TRAILING_EMPTY);

        engine.executeQuery("COPY INTO t FROM (SELECT $1, $2 FROM @%t) FILE_FORMAT = (TYPE = CSV"
            + " SKIP_HEADER = 1) ON_ERROR = CONTINUE");
        assertEquals("NULL", loaded("t", "v"));
    }

    // ── VALIDATION_MODE sees exactly what the load would do ──

    /** RETURN_ERRORS lists the numeric rejection the option produces, and loads nothing. */
    @Test
    public void validationModeReturnErrorsListsTheRejection() throws IOException {
        engine.execute("CREATE TABLE n (k INTEGER, v INTEGER)");
        stage("n", "trail.csv", TRAILING_EMPTY);

        final ResultSet rs = copyWithFormat("n", "EMPTY_FIELD_AS_NULL = FALSE",
            "VALIDATION_MODE = RETURN_ERRORS");
        assertEquals(1, rs.getRows().size(), "one would-be-rejected record");
        assertEquals("Numeric value '' is not recognized", rs.getRows().get(0).getValue(0));
        assertEquals(0, count("n", ""), "VALIDATION_MODE loads nothing");
    }

    /** The same file under the default validates clean — nothing would be rejected. */
    @Test
    public void validationModeReturnErrorsIsCleanUnderTheDefault() throws IOException {
        engine.execute("CREATE TABLE n (k INTEGER, v INTEGER)");
        stage("n", "trail.csv", TRAILING_EMPTY);

        assertEquals(0, copyWithFormat("n", "", "VALIDATION_MODE = RETURN_ERRORS").getRows().size());
    }

    /** RETURN_&lt;n&gt;_ROWS previews the row the load would produce, empty string and all. */
    @Test
    public void validationModeReturnRowsPreviewsTheEmptyString() throws IOException {
        stage("t", "trail.csv", TRAILING_EMPTY);

        final ResultSet rs = copyWithFormat("t", "EMPTY_FIELD_AS_NULL = FALSE",
            "VALIDATION_MODE = RETURN_1_ROWS");
        assertEquals(1, rs.getRows().size());
        assertEquals("", rs.getRows().get(0).getValue(1));
    }

    /** …and previews a NULL under the default. */
    @Test
    public void validationModeReturnRowsPreviewsNullByDefault() throws IOException {
        stage("t", "trail.csv", TRAILING_EMPTY);

        final ResultSet rs = copyWithFormat("t", "", "VALIDATION_MODE = RETURN_1_ROWS");
        assertEquals(1, rs.getRows().size());
        assertNull(rs.getRows().get(0).getValue(1));
    }

    // ── the same option on the stage-query path (SELECT $1, $2 FROM @stage) ──

    /** Reading a staged file directly nulls an empty field too, the option's default reaching that path. */
    @Test
    public void aStageQueryReadsAnEmptyFieldAsNull() throws IOException {
        Files.write(stageDir.resolve("trail.csv"), TRAILING_EMPTY.getBytes(StandardCharsets.UTF_8));
        engine.execute("CREATE STAGE sq URL='file://" + stageDir + "'");
        engine.execute("CREATE FILE FORMAT sq_plain TYPE = CSV SKIP_HEADER = 1");

        final ResultSet rs = engine.executeQuery("SELECT $1, $2 FROM @sq (FILE_FORMAT => 'sq_plain')");
        assertEquals(1, rs.getRows().size());
        assertEquals("1", rs.getRows().get(0).getValue(0));
        assertNull(rs.getRows().get(0).getValue(1));
    }

    /** …and a format carrying {@code = FALSE} hands back the empty string instead. */
    @Test
    public void aStageQueryUnderTheOptionOffReadsTheEmptyString() throws IOException {
        Files.write(stageDir.resolve("trail.csv"), TRAILING_EMPTY.getBytes(StandardCharsets.UTF_8));
        engine.execute("CREATE STAGE sq URL='file://" + stageDir + "'");
        engine.execute("CREATE FILE FORMAT sq_keep TYPE = CSV SKIP_HEADER = 1 EMPTY_FIELD_AS_NULL = FALSE");

        final ResultSet rs = engine.executeQuery("SELECT $1, $2 FROM @sq (FILE_FORMAT => 'sq_keep')");
        assertEquals(1, rs.getRows().size());
        assertEquals("", rs.getRows().get(0).getValue(1));
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
