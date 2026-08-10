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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The COPY INTO &lt;location&gt; (unload) option surface, measured cell by cell on a real account
 * over an internal stage: the default AUTO compression gzips the file (named {@code data_0_0_0.csv.gz})
 * while {@code input_bytes} stays the uncompressed size; an occupied destination refuses without
 * OVERWRITE, echoing the target as written; SINGLE with a file-shaped target writes exactly that name;
 * DETAILED_OUTPUT swaps the summary for bare per-file rows; TYPE=JSON unloads exactly one column of
 * JSON documents and refuses anything else; an empty source writes no file at all; the option values
 * refuse with the same three invalid-value renderings the load side uses, an unknown option is an
 * invalid parameter, and VALIDATION_MODE never fits an unload; and PARTITION BY belongs directly
 * after FROM — an option before it stops the parse at the BY.
 */
public class CopyUnloadOptionSurfaceTest extends BaseDatabaseTest {

    @BeforeEach
    public void createFixtures() {
        engine.execute("CREATE OR REPLACE STAGE ust ENCRYPTION = (TYPE = 'SNOWFLAKE_SSE')");
        engine.execute("CREATE OR REPLACE TABLE ut (a INTEGER, b VARCHAR)");
        engine.execute("INSERT INTO ut VALUES (1, 'x'), (2, 'y')");
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

    @Test
    public void defaultUnloadGzipsAndReportsBothByteCounts() {
        final ResultSet rs = engine.executeQuery("COPY INTO @ust/u1/ FROM ut");
        assertEquals("2", String.valueOf(cell(rs, 0, "rows_unloaded")));
        assertEquals("8", String.valueOf(cell(rs, 0, "input_bytes")));
        assertEquals("28", String.valueOf(cell(rs, 0, "output_bytes")));

        final ResultSet listed = engine.executeQuery("LIST @ust PATTERN = '.*u1.*'");
        assertEquals("ust/u1/data_0_0_0.csv.gz", cell(listed, 0, "name"));
        assertEquals("28", String.valueOf(cell(listed, 0, "size")));
    }

    @Test
    public void occupiedDestinationRefusesWithoutOverwrite() {
        engine.executeQuery("COPY INTO @ust/u1/ FROM ut");
        assertEquals("Files already existing at the unload destination: @ust/u1/."
                + " Use overwrite option to force unloading.",
            refusal("COPY INTO @ust/u1/ FROM ut").getMessage());

        // OVERWRITE = TRUE forces the re-unload through.
        final ResultSet rs = engine.executeQuery("COPY INTO @ust/u1/ FROM ut OVERWRITE = TRUE");
        assertEquals("2", String.valueOf(cell(rs, 0, "rows_unloaded")));
    }

    @Test
    public void singleWritesExactlyTheNamedFile() {
        engine.executeQuery("COPY INTO @ust/u7/out.csv FROM ut SINGLE = TRUE");
        final ResultSet listed = engine.executeQuery("LIST @ust PATTERN = '.*u7.*'");
        assertEquals(1, listed.getRowCount());
        // The name is used verbatim — the gzipped bytes get no .gz suffix.
        assertEquals("ust/u7/out.csv", cell(listed, 0, "name"));
        assertEquals("28", String.valueOf(cell(listed, 0, "size")));
    }

    @Test
    public void compressionNoneAndFileExtensionShapeTheFile() {
        final ResultSet rs = engine.executeQuery(
            "COPY INTO @ust/u9/ FROM ut FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE)");
        assertEquals("8", String.valueOf(cell(rs, 0, "output_bytes")));
        assertEquals("ust/u9/data_0_0_0.csv",
            cell(engine.executeQuery("LIST @ust PATTERN = '.*u9.*'"), 0, "name"));

        engine.executeQuery("COPY INTO @ust/u25/ FROM ut"
            + " FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE FILE_EXTENSION = 'dat')");
        assertEquals("ust/u25/data_0_0_0.dat",
            cell(engine.executeQuery("LIST @ust PATTERN = '.*u25.*'"), 0, "name"));
    }

    @Test
    public void headerAddsTheColumnLine() {
        final ResultSet rs = engine.executeQuery("COPY INTO @ust/u11/ FROM ut"
            + " FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) HEADER = TRUE");
        assertEquals("12", String.valueOf(cell(rs, 0, "output_bytes")));
    }

    @Test
    public void detailedOutputListsBarePerFileRows() {
        final ResultSet rs = engine.executeQuery("COPY INTO @ust/u13/ FROM ut DETAILED_OUTPUT = TRUE");
        assertEquals("FILE_NAME", rs.getColumns().get(0).getName());
        assertEquals("FILE_SIZE", rs.getColumns().get(1).getName());
        assertEquals("ROW_COUNT", rs.getColumns().get(2).getName());
        assertEquals(1, rs.getRowCount());
        assertEquals("data_0_0_0.csv.gz", cell(rs, 0, "FILE_NAME"));
        assertEquals("28", String.valueOf(cell(rs, 0, "FILE_SIZE")));
        assertEquals("2", String.valueOf(cell(rs, 0, "ROW_COUNT")));
    }

    @Test
    public void jsonUnloadRefusesMoreThanOneColumn() {
        assertEquals("Unsupported feature 'unloading of more than one column or non-json values'.",
            refusal("COPY INTO @ust/u16/ FROM ut FILE_FORMAT = (TYPE = JSON)").getMessage());
    }

    @Test
    public void emptySourceWritesNoFile() {
        engine.execute("CREATE OR REPLACE TABLE ue (a INTEGER)");
        final ResultSet rs = engine.executeQuery("COPY INTO @ust/u18/ FROM ue");
        assertEquals("0", String.valueOf(cell(rs, 0, "rows_unloaded")));
        assertEquals("0", String.valueOf(cell(rs, 0, "output_bytes")));
        assertEquals(0, engine.executeQuery("LIST @ust PATTERN = '.*u18.*'").getRowCount());
    }

    @Test
    public void optionValuesRefuseWithTheMeasuredRenderings() {
        assertEquals("SQL compilation error:\ninvalid value ['TRUE'] for parameter 'OVERWRITE'",
            refusal("COPY INTO @ust/u20/ FROM ut OVERWRITE = 'TRUE'").getMessage());
        assertEquals("SQL compilation error:\ninvalid value [MAYBE] for parameter 'SINGLE'",
            refusal("COPY INTO @ust/u21/ FROM ut SINGLE = MAYBE").getMessage());
        assertEquals("SQL compilation error:\ninvalid value [-1] for parameter 'MAX_FILE_SIZE'",
            refusal("COPY INTO @ust/u22/ FROM ut MAX_FILE_SIZE = -1").getMessage());
        assertEquals("SQL compilation error:\ninvalid parameter 'NO_SUCH_OPT'",
            refusal("COPY INTO @ust/u23/ FROM ut NO_SUCH_OPT = 1").getMessage());
        // VALIDATION_MODE never fits an unload — every value is invalid, echoed bare or quoted.
        assertEquals("SQL compilation error:\ninvalid value [RETURN_ERRORS] for parameter"
                + " 'VALIDATION_MODE'",
            refusal("COPY INTO @ust/u24/ FROM ut VALIDATION_MODE = RETURN_ERRORS").getMessage());
    }

    @Test
    public void partitionByAfterAnOptionStopsAtTheBy() {
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 52 unexpected 'BY'.",
            refusal("COPY INTO @ust/u29/ FROM ut SINGLE = TRUE PARTITION BY ('p')").getMessage());
    }

    @Test
    public void partitionByDirectlyAfterFromSplitsIntoSubdirectories() {
        engine.executeQuery("COPY INTO @ust/u14/ FROM ut PARTITION BY ('a=' || a)"
            + " FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE)");
        final ResultSet listed = engine.executeQuery("LIST @ust PATTERN = '.*u14.*'");
        assertEquals(2, listed.getRowCount());
        boolean sawA1 = false;
        boolean sawA2 = false;
        for (final Row row : listed.getRows()) {
            final String name = String.valueOf(row.getValue(listed.getColumnIndex("name")));
            sawA1 |= name.startsWith("ust/u14/a=1/data_");
            sawA2 |= name.startsWith("ust/u14/a=2/data_");
        }
        assertTrue(sawA1 && sawA2, "both partitions listed");
    }

    @Test
    public void getReportsRootRelativeNamesAndSseFilesUndecorated() {
        engine.executeQuery("COPY INTO @ust/u30/ FROM (SELECT a, b FROM ut ORDER BY a DESC)"
            + " FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE)");
        final ResultSet got = engine.executeQuery(
            "GET @ust/u30 file://" + tempDownloadDir());
        assertEquals(1, got.getRowCount());
        // The file column is relative to the STAGE ROOT (subdirectory kept, stage name dropped),
        // and a server-side-encrypted stage leaves the encryption column empty.
        assertEquals("u30/data_0_0_0.csv", cell(got, 0, "file"));
        assertEquals("DOWNLOADED", cell(got, 0, "status"));
        assertEquals("", cell(got, 0, "encryption"));
    }

    @Test
    public void unloadedCsvRoundTripsThroughTheLoader() {
        engine.executeQuery("COPY INTO @ust/rt/ FROM ut"
            + " FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) HEADER = TRUE");
        engine.execute("CREATE OR REPLACE TABLE ut2 (b VARCHAR, a INTEGER)");
        engine.executeQuery("COPY INTO ut2 FROM @ust/rt/"
            + " FILE_FORMAT = (TYPE = CSV PARSE_HEADER = TRUE)"
            + " MATCH_BY_COLUMN_NAME = CASE_INSENSITIVE");
        final ResultSet rs = engine.executeQuery("SELECT a, b FROM ut2 ORDER BY a");
        assertEquals(2, rs.getRowCount());
        assertEquals("1", String.valueOf(cell(rs, 0, "a")));
        assertEquals("x", cell(rs, 0, "b"));
        assertEquals("2", String.valueOf(cell(rs, 1, "a")));
        assertEquals("y", cell(rs, 1, "b"));
    }

    private String tempDownloadDir() {
        try {
            return Files.createTempDirectory("fl_unload_get").toAbsolutePath().toString();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
