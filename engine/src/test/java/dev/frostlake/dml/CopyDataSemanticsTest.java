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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * COPY INTO &lt;table&gt; over REAL staged data, measured cell by cell on a real account against an
 * internal stage: the per-file result row spells the file with the lowercase stage name prefixed;
 * ON_ERROR settles each file as LOADED / PARTIALLY_LOADED / LOAD_FAILED / LOAD_SKIPPED with the
 * measured counter and first-error columns; the aborting default fails with a four-line message —
 * the record's own error, indented File and Row lines, and a fixed advice sentence; TRUNCATE (and
 * recreating the table) forgets the load history where DELETE keeps it; VALIDATION_MODE =
 * RETURN_ERRORS answers twelve columns with byte-precise geometry; PATTERN is applied to the file's
 * whole internal path, never its bare name; SIZE_LIMIT admits the next file only while the bytes
 * read so far do not exceed it; and TRIM_SPACE trims around an enclosure while keeping the enclosed
 * content verbatim.
 */
public class CopyDataSemanticsTest extends BaseDatabaseTest {

    @BeforeEach
    public void createFixtures() {
        engine.execute("CREATE OR REPLACE STAGE st ENCRYPTION = (TYPE = 'SNOWFLAKE_SSE')");
        engine.execute("CREATE OR REPLACE TABLE t (a INTEGER, b VARCHAR)");
    }

    private RuntimeException refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
    }

    private Object cell(final ResultSet rs, final int row, final String column) {
        return rs.getRows().get(row).getValue(rs.getColumnIndex(column));
    }

    private void assertResultRow(final ResultSet rs, final int row, final Object... expected) {
        final String[] columns = {"file", "status", "rows_parsed", "rows_loaded", "error_limit",
            "errors_seen", "first_error", "first_error_line", "first_error_character",
            "first_error_column_name"};
        for (int i = 0; i < columns.length; i++) {
            final Object actual = cell(rs, row, columns[i]);
            assertEquals(expected[i] == null ? null : String.valueOf(expected[i]),
                actual == null ? null : String.valueOf(actual), columns[i]);
        }
    }

    @Test
    public void cleanLoadReportsThePrefixedPerFileRow() {
        stageLocalFile("st", "good.csv", "1,x\n2,y\n");

        final ResultSet first = engine.executeQuery("COPY INTO t FROM @st FILES = ('good.csv')");
        assertEquals(1, first.getRowCount());
        assertResultRow(first, 0, "st/good.csv", "LOADED", 2, 2, 1, 0, null, null, null, null);

        // The same file again: an explicit FILES entry reports its skip, with the measured
        // NULL error_limit beside errors_seen = 1 and the skip as the file's first error.
        final ResultSet again = engine.executeQuery("COPY INTO t FROM @st FILES = ('good.csv')");
        assertResultRow(again, 0, "st/good.csv", "LOAD_SKIPPED", 0, 0, null, 1,
            "File was loaded before.", null, null, null);

        // Found only by listing, the skip leaves no row — just the one-column summary.
        final ResultSet listed = engine.executeQuery("COPY INTO t FROM @st");
        assertEquals("Copy executed with 0 files processed.", cell(listed, 0, "status"));

        final ResultSet forced = engine.executeQuery("COPY INTO t FROM @st FILES = ('good.csv') FORCE = TRUE");
        assertEquals("LOADED", cell(forced, 0, "status"));
        assertEquals("4", String.valueOf(cell(engine.executeQuery("SELECT COUNT(*) FROM t"), 0, "COUNT(*)")));
    }

    @Test
    public void onErrorFamiliesSettleTheFileWithMeasuredCounters() {
        stageLocalFile("st", "bad.csv", "1,x\nNOPE,y\n3,z\n");

        // CONTINUE: the clean records load, the error budget reads as the record count.
        final ResultSet cont = engine.executeQuery("COPY INTO t FROM @st FILES = ('bad.csv') ON_ERROR = CONTINUE");
        assertResultRow(cont, 0, "st/bad.csv", "PARTIALLY_LOADED", 3, 2, 3, 1,
            "Numeric value 'NOPE' is not recognized", 2, 1, "\"T\"[\"A\":1]");

        // SKIP_FILE: one bad record forfeits the whole file — and the clean records with it.
        engine.execute("CREATE OR REPLACE TABLE t (a INTEGER, b VARCHAR)");
        final ResultSet skip = engine.executeQuery("COPY INTO t FROM @st FILES = ('bad.csv') ON_ERROR = SKIP_FILE");
        assertResultRow(skip, 0, "st/bad.csv", "LOAD_FAILED", 3, 0, 1, 1,
            "Numeric value 'NOPE' is not recognized", 2, 1, "\"T\"[\"A\":1]");
        assertEquals("0", String.valueOf(cell(engine.executeQuery("SELECT COUNT(*) FROM t"), 0, "COUNT(*)")));

        // SKIP_FILE_2: one error stays under the budget of two, so the file half-loads.
        engine.execute("CREATE OR REPLACE TABLE t (a INTEGER, b VARCHAR)");
        final ResultSet skip2 = engine.executeQuery("COPY INTO t FROM @st FILES = ('bad.csv') ON_ERROR = SKIP_FILE_2");
        assertResultRow(skip2, 0, "st/bad.csv", "PARTIALLY_LOADED", 3, 2, 2, 1,
            "Numeric value 'NOPE' is not recognized", 2, 1, "\"T\"[\"A\":1]");
    }

    @Test
    public void abortStatementFailsWithTheFourLineMessageAndLoadsNothing() {
        stageLocalFile("st", "good.csv", "1,x\n2,y\n");
        stageLocalFile("st", "bad.csv", "1,x\nNOPE,y\n3,z\n");

        final RuntimeException e = refusal("COPY INTO t FROM @st FILES = ('good.csv', 'bad.csv')");
        assertEquals("Numeric value 'NOPE' is not recognized\n"
            + "  File 'bad.csv', line 2, character 1\n"
            + "  Row 2, column \"T\"[\"A\":1]\n"
            + "  If you would like to continue loading when an error is encountered, use other values"
            + " such as 'SKIP_FILE' or 'CONTINUE' for the ON_ERROR option. For more information on"
            + " loading options, please run 'info loading_data' in a SQL client.", e.getMessage());

        // The abort spans the whole statement: the clean file's rows are not kept either.
        assertEquals("0", String.valueOf(cell(engine.executeQuery("SELECT COUNT(*) FROM t"), 0, "COUNT(*)")));
    }

    @Test
    public void truncateForgetsTheLoadHistoryWhereDeleteKeepsIt() {
        stageLocalFile("st", "good.csv", "1,x\n2,y\n");
        engine.executeQuery("COPY INTO t FROM @st FILES = ('good.csv')");

        engine.execute("TRUNCATE TABLE t");
        final ResultSet afterTruncate = engine.executeQuery("COPY INTO t FROM @st FILES = ('good.csv')");
        assertEquals("LOADED", cell(afterTruncate, 0, "status"));

        engine.execute("DELETE FROM t");
        final ResultSet afterDelete = engine.executeQuery("COPY INTO t FROM @st FILES = ('good.csv')");
        assertEquals("LOAD_SKIPPED", cell(afterDelete, 0, "status"));

        engine.execute("CREATE OR REPLACE TABLE t (a INTEGER, b VARCHAR)");
        final ResultSet afterReplace = engine.executeQuery("COPY INTO t FROM @st FILES = ('good.csv')");
        assertEquals("LOADED", cell(afterReplace, 0, "status"));
    }

    @Test
    public void returnErrorsAnswersTheTwelveColumnGeometry() {
        stageLocalFile("st", "bad.csv", "1,x\nNOPE,y\n3,z\n");

        final ResultSet rs = engine.executeQuery(
            "COPY INTO t FROM @st FILES = ('bad.csv') VALIDATION_MODE = RETURN_ERRORS");
        assertEquals(1, rs.getRowCount());
        assertEquals("Numeric value 'NOPE' is not recognized", cell(rs, 0, "ERROR"));
        assertEquals("bad.csv", cell(rs, 0, "FILE"));
        assertEquals("2", String.valueOf(cell(rs, 0, "LINE")));
        assertEquals("1", String.valueOf(cell(rs, 0, "CHARACTER")));
        assertEquals("4", String.valueOf(cell(rs, 0, "BYTE_OFFSET")));
        assertEquals("conversion", cell(rs, 0, "CATEGORY"));
        assertEquals("100038", String.valueOf(cell(rs, 0, "CODE")));
        assertEquals("22018", String.valueOf(cell(rs, 0, "SQL_STATE")));
        assertEquals("\"T\"[\"A\":1]", cell(rs, 0, "COLUMN_NAME"));
        assertEquals("2", String.valueOf(cell(rs, 0, "ROW_NUMBER")));
        assertEquals("2", String.valueOf(cell(rs, 0, "ROW_START_LINE")));
        // A rejected record that another record follows keeps its line terminator…
        assertEquals("NOPE,y\n", cell(rs, 0, "REJECTED_RECORD"));
    }

    @Test
    public void returnErrorsPlacesTheCharacterAtTheFailingField() {
        engine.execute("CREATE OR REPLACE TABLE ti (a INTEGER, b INTEGER)");
        stageLocalFile("st", "badcol2.csv", "1,2\n3,NO\n");

        final ResultSet rs = engine.executeQuery(
            "COPY INTO ti FROM @st FILES = ('badcol2.csv') VALIDATION_MODE = RETURN_ERRORS");
        assertEquals("Numeric value 'NO' is not recognized", cell(rs, 0, "ERROR"));
        assertEquals("3", String.valueOf(cell(rs, 0, "CHARACTER")));
        assertEquals("6", String.valueOf(cell(rs, 0, "BYTE_OFFSET")));
        assertEquals("\"TI\"[\"B\":2]", cell(rs, 0, "COLUMN_NAME"));
        // …while the file's LAST record is quoted back without one.
        assertEquals("3,NO", cell(rs, 0, "REJECTED_RECORD"));
    }

    @Test
    public void returnErrorsReportsAMissingNamedFileWithTheOtherTaxonomy() {
        final ResultSet rs = engine.executeQuery(
            "COPY INTO t FROM @st FILES = ('nosuch.csv') VALIDATION_MODE = RETURN_ERRORS");
        assertEquals(1, rs.getRowCount());
        assertTrue(String.valueOf(cell(rs, 0, "ERROR")).contains("was not found."),
            String.valueOf(cell(rs, 0, "ERROR")));
        assertEquals("nosuch.csv", cell(rs, 0, "FILE"));
        assertNull(cell(rs, 0, "LINE"));
        assertNull(cell(rs, 0, "CHARACTER"));
        assertNull(cell(rs, 0, "BYTE_OFFSET"));
        assertEquals("other", cell(rs, 0, "CATEGORY"));
        assertEquals("100112", String.valueOf(cell(rs, 0, "CODE")));
        assertEquals("22000", String.valueOf(cell(rs, 0, "SQL_STATE")));
        assertNull(cell(rs, 0, "COLUMN_NAME"));
        assertEquals("0", String.valueOf(cell(rs, 0, "ROW_NUMBER")));
        assertEquals("0", String.valueOf(cell(rs, 0, "ROW_START_LINE")));
        assertNull(cell(rs, 0, "REJECTED_RECORD"));
    }

    @Test
    public void returnErrorsCallsAShortRecordParsing() {
        stageLocalFile("st", "short.csv", "1\n");

        final ResultSet rs = engine.executeQuery(
            "COPY INTO t FROM @st FILES = ('short.csv') VALIDATION_MODE = RETURN_ERRORS");
        assertEquals("Number of columns in file (1) does not match that of the corresponding table"
            + " (2), use file format option error_on_column_count_mismatch=false to ignore this"
            + " error", cell(rs, 0, "ERROR"));
        assertEquals("parsing", cell(rs, 0, "CATEGORY"));
        assertEquals("100080", String.valueOf(cell(rs, 0, "CODE")));
        assertEquals("22000", String.valueOf(cell(rs, 0, "SQL_STATE")));
        assertEquals("2", String.valueOf(cell(rs, 0, "CHARACTER")));
        assertEquals("1", String.valueOf(cell(rs, 0, "ROW_NUMBER")));
        assertEquals("1", cell(rs, 0, "REJECTED_RECORD"));
    }

    @Test
    public void patternMatchesTheWholeInternalPathNotTheBareName() {
        stageLocalFile("st", "good.csv", "1,x\n2,y\n");

        // The bare name misses, the stage-name-prefixed spelling misses too — only a pattern
        // covering the opaque internal prefix reaches the file.
        assertEquals("Copy executed with 0 files processed.",
            cell(engine.executeQuery("COPY INTO t FROM @st PATTERN = 'good\\.csv' FORCE = TRUE"), 0, "status"));
        assertEquals("Copy executed with 0 files processed.",
            cell(engine.executeQuery("COPY INTO t FROM @st PATTERN = 'st/good\\.csv' FORCE = TRUE"), 0, "status"));
        assertEquals("LOADED",
            cell(engine.executeQuery("COPY INTO t FROM @st PATTERN = '.*good\\.csv' FORCE = TRUE"), 0, "status"));
    }

    @Test
    public void sizeLimitAdmitsTheNextFileUntilStrictlyExceeded() {
        stageLocalFile("st", "s1.csv", "1,a\n2,b\n");
        stageLocalFile("st", "s2.csv", "3,c\n4,d\n");

        // Both files hold 8 bytes. A limit of 1 is exceeded after the first file; a limit the
        // total merely REACHES (8) still admits the next.
        final ResultSet one = engine.executeQuery(
            "COPY INTO t FROM @st FILES = ('s1.csv', 's2.csv') SIZE_LIMIT = 1 FORCE = TRUE");
        assertEquals(1, one.getRowCount());
        final ResultSet both = engine.executeQuery(
            "COPY INTO t FROM @st FILES = ('s1.csv', 's2.csv') SIZE_LIMIT = 8 FORCE = TRUE");
        assertEquals(2, both.getRowCount());
    }

    @Test
    public void trimSpaceKeepsTheEnclosedContentVerbatim() {
        stageLocalFile("st", "sp.csv", " 1 , \" x y \" \n");

        engine.executeQuery("COPY INTO t FROM @st FILES = ('sp.csv')"
            + " FILE_FORMAT = (TYPE = CSV TRIM_SPACE = TRUE FIELD_OPTIONALLY_ENCLOSED_BY = '\"')");
        final ResultSet rs = engine.executeQuery("SELECT a, '[' || b || ']' AS wrapped FROM t");
        assertEquals("1", String.valueOf(cell(rs, 0, "a")));
        assertEquals("[ x y ]", cell(rs, 0, "wrapped"));
    }

    @Test
    public void blankLineIsARecordByDefaultAndSkippable() {
        stageLocalFile("st", "blank.csv", "1,x\n\n2,y\n");

        final RuntimeException e = refusal("COPY INTO t FROM @st FILES = ('blank.csv')");
        assertEquals("End of record reached while expected to parse column '\"T\"[\"B\":2]'\n"
            + "  File 'blank.csv', line 2, character 1\n"
            + "  Row 2, column \"T\"[\"B\":2]\n"
            + "  If you would like to continue loading when an error is encountered, use other values"
            + " such as 'SKIP_FILE' or 'CONTINUE' for the ON_ERROR option. For more information on"
            + " loading options, please run 'info loading_data' in a SQL client.", e.getMessage());

        // SKIP_BLANK_LINES counts the blank line as parsed and loads past it.
        final ResultSet rs = engine.executeQuery("COPY INTO t FROM @st FILES = ('blank.csv')"
            + " FILE_FORMAT = (TYPE = CSV SKIP_BLANK_LINES = TRUE)");
        assertEquals("LOADED", cell(rs, 0, "status"));
        assertEquals("3", String.valueOf(cell(rs, 0, "rows_parsed")));
        assertEquals("2", String.valueOf(cell(rs, 0, "rows_loaded")));
    }

    @Test
    public void emptyAndHeaderOnlyFilesLoadZeroRows() {
        stageLocalFile("st", "empty.csv", "");
        stageLocalFile("st", "onlyhead.csv", "a,b\n");

        final ResultSet empty = engine.executeQuery("COPY INTO t FROM @st FILES = ('empty.csv')");
        assertResultRow(empty, 0, "st/empty.csv", "LOADED", 0, 0, 1, 0, null, null, null, null);

        final ResultSet headed = engine.executeQuery("COPY INTO t FROM @st FILES = ('onlyhead.csv')"
            + " FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");
        assertResultRow(headed, 0, "st/onlyhead.csv", "LOADED", 0, 0, 1, 0, null, null, null, null);
    }

    @Test
    public void gzippedStagedFileLoadsTransparently() {
        final Path local;
        try {
            local = Files.createTempDirectory("fl_copy_gz").resolve("zip.csv");
            Files.writeString(local, "8,g\n");
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        engine.execute("PUT file://" + local.toAbsolutePath() + " @st AUTO_COMPRESS = TRUE");

        final ResultSet rs = engine.executeQuery("COPY INTO t FROM @st FILES = ('zip.csv.gz')");
        assertResultRow(rs, 0, "st/zip.csv.gz", "LOADED", 1, 1, 1, 0, null, null, null, null);
        assertEquals("8", String.valueOf(cell(engine.executeQuery("SELECT MAX(a) FROM t"), 0, "MAX(A)")));
    }

    @Test
    public void matchByColumnNameLoadsByTheParsedHeader() {
        stageLocalFile("st", "mb.csv", "B,A\nhey,9\n");

        engine.executeQuery("COPY INTO t FROM @st FILES = ('mb.csv')"
            + " FILE_FORMAT = (TYPE = CSV PARSE_HEADER = TRUE)"
            + " MATCH_BY_COLUMN_NAME = CASE_INSENSITIVE");
        final ResultSet rs = engine.executeQuery("SELECT a, b FROM t");
        assertEquals("9", String.valueOf(cell(rs, 0, "a")));
        assertEquals("hey", cell(rs, 0, "b"));
    }

    @Test
    public void multiFileResultCoversEveryNamedFile() {
        stageLocalFile("st", "m1.csv", "1,a\n");
        stageLocalFile("st", "m2.csv", "2,b\n");

        final ResultSet rs = engine.executeQuery(
            "COPY INTO t FROM @st FILES = ('m1.csv', 'm2.csv') FORCE = TRUE");
        assertEquals(2, rs.getRowCount());
        // The account settles files in no promised order — find each row by its file name.
        boolean sawFirst = false;
        boolean sawSecond = false;
        for (final Row row : rs.getRows()) {
            final String file = String.valueOf(row.getValue(rs.getColumnIndex("file")));
            if ("st/m1.csv".equals(file)) {
                sawFirst = true;
            } else if ("st/m2.csv".equals(file)) {
                sawSecond = true;
            }
            assertEquals("LOADED", row.getValue(rs.getColumnIndex("status")));
        }
        assertTrue(sawFirst && sawSecond);
    }
}
