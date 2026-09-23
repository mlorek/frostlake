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

package dev.frostlake.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The path after a stage reference is a PREFIX of the staged files' names, not a directory: it may end inside
 * a directory's name or a file's, it reaches files at any depth, and it is case-sensitive. The stage holds
 * {@code f1}, {@code f1.csv}, {@code dir/g}, {@code dir/h.csv} and {@code dirx/k}, one number each:
 *
 * <pre>
 *   @st/f      3,4          @st/di     5,6,7          @st/f1/     nothing
 *   @st/f1     3,4          @st/dir    5,6,7          @st/DIR     nothing
 *   @st/f1.csv 4            @st/dir/   5,6            @st          all five
 * </pre>
 *
 * <p>COPY, a query over the stage and REMOVE all read it that way, and so does an unload's destination: a
 * path ending in a slash (or none) writes {@code data…} after it, any other path IS the name. Every cell is
 * live-verified; the sizes a LIST reports differ (the account pads what it stores), so only names are read.
 */
public class StagePathPrefixTest extends BaseDatabaseTest {

    private static final String CSV = " FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE";

    @Override
    protected void setupTest() {
        engine.execute("CREATE STAGE st");
        engine.execute("CREATE TABLE t1 (a INT)");
        engine.execute("COPY INTO @st/f1 FROM (SELECT 3)" + CSV);
        engine.execute("COPY INTO @st/f1.csv FROM (SELECT 4)" + CSV);
        engine.execute("COPY INTO @st/dir/g FROM (SELECT 5)" + CSV);
        engine.execute("COPY INTO @st/dir/h.csv FROM (SELECT 6)" + CSV);
        engine.execute("COPY INTO @st/dirx/k FROM (SELECT 7)" + CSV);
    }

    /** What a fresh load from {@code source} puts in the table, in order, or the refusal on one line. */
    private String loaded(final String source) {
        engine.execute("TRUNCATE TABLE t1");
        try {
            engine.executeQuery("COPY INTO t1 FROM " + source + " FILE_FORMAT = (TYPE = CSV) FORCE = TRUE");
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
        final Object value = engine.executeQuery(
            "SELECT COALESCE(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), '') FROM t1").getRows().get(0).getValue(0);
        return String.valueOf(value);
    }

    /** Every row's cells joined by {@code :}, a bar between rows, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            final StringBuilder out = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                out.append(out.length() > 0 ? " | " : "");
                for (int i = 0; i < row.getValues().size(); i++) {
                    out.append(i > 0 ? ":" : "").append(row.getValue(i));
                }
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** The first column of every row, sorted — for the answers whose row order the account leaves open. */
    private List<String> names(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> names = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            names.add(String.valueOf(row.getValue(0)));
        }
        Collections.sort(names);
        return names;
    }

    /** A load reads every file whose name starts with the path, at any depth. */
    @Test
    public void aLoadReadsEveryFileThePathPrefixes() {
        assertEquals("3,4", loaded("@st/f"));
        assertEquals("3,4", loaded("@st/f1"));
        assertEquals("4", loaded("@st/f1.csv"));
        assertEquals("5,6,7", loaded("@st/di"));
        assertEquals("5,6,7", loaded("@st/dir"), "dirx/k starts with dir too");
        assertEquals("5,6", loaded("@st/dir/"));
        assertEquals("3,4,5,6,7", loaded("@st"), "the bare stage reaches files at every depth");
    }

    /** A path no name starts with loads nothing — a trailing slash after a file's name included. */
    @Test
    public void aPathNoNameStartsWithLoadsNothing() {
        assertEquals("", loaded("@st/f1/"));
        assertEquals("", loaded("@st/nomatch"));
        assertEquals("", loaded("@st/DIR"), "the prefix is case-sensitive");
        assertEquals("Copy executed with 0 files processed.",
            answer("COPY INTO t1 FROM @st/f1/ FILE_FORMAT = (TYPE = CSV)"));
    }

    /** PATTERN matches the file's whole stored path, never the part after the prefix nor the bare name. */
    @Test
    public void aPatternMatchesTheWholeStoredPath() {
        assertEquals("5", loaded("@st/dir PATTERN = '.*g'"));
        assertEquals("", loaded("@st/dir PATTERN = 'g'"));
        assertEquals("5", loaded("@st/dir PATTERN = '.*dir/g'"));
        assertEquals("", loaded("@st/di PATTERN = 'r/g'"));
        assertEquals("6", loaded("@st/di PATTERN = '.*[.]csv'"));
        assertEquals("3", loaded("@st/f PATTERN = '.*f1'"));
    }

    /** A FILES entry continues the written path as one string, exactly where the path stopped. */
    @Test
    public void aFilesEntryContinuesTheWrittenPath() {
        assertEquals("5", loaded("@st/di FILES = ('r/g')"));
        assertEquals("5", loaded("@st/dir/ FILES = ('g')"));
        assertEquals("5", loaded("@st/dir FILES = ('/g')"));
        assertEquals("5", loaded("@st FILES = ('dir/g')"));
        assertEquals("Remote file '@st/dirg' was not found. If you are running a copy command, please make"
            + " sure files are not deleted when they are being loaded or files are not being loaded into two"
            + " different tables concurrently with auto purge option.",
            loaded("@st/dir FILES = ('g')"));
    }

    /** The load history remembers each file by its whole path, so a same-named file elsewhere still loads. */
    @Test
    public void theLoadHistoryKeepsSameNamedFilesApart() {
        engine.execute("COPY INTO @st/a/m FROM (SELECT 8)" + CSV);
        engine.execute("COPY INTO @st/b/m FROM (SELECT 8)" + CSV);
        engine.execute("TRUNCATE TABLE t1");
        engine.executeQuery("COPY INTO t1 FROM @st/a/ FILE_FORMAT = (TYPE = CSV)");
        engine.executeQuery("COPY INTO t1 FROM @st FILE_FORMAT = (TYPE = CSV)");
        assertEquals("3,4,5,6,7,8,8", answer("SELECT LISTAGG(a, ',') WITHIN GROUP (ORDER BY a) FROM t1"),
            "b/m loads beside a/m, and a/m is not loaded a second time");
    }

    /** A query over the stage reads the same files, named by their stage-relative path. */
    @Test
    public void aQueryOverTheStageReadsThePrefix() {
        assertEquals("3:f1 | 4:f1.csv", answer("SELECT $1, METADATA$FILENAME FROM @st/f1 ORDER BY 1"));
        assertEquals("5:dir/g | 6:dir/h.csv | 7:dirx/k",
            answer("SELECT $1, METADATA$FILENAME FROM @st/di ORDER BY 1"));
        assertEquals("5 | 6", answer("SELECT $1 FROM @st/dir/ ORDER BY 1"));
        assertEquals("3 | 4 | 5 | 6 | 7", answer("SELECT $1 FROM @st ORDER BY 1"));
        assertEquals("", answer("SELECT $1 FROM @st/f1/ ORDER BY 1"));
        assertEquals("", answer("SELECT $1 FROM @st (PATTERN => 'f1.csv') ORDER BY 1"));
        assertEquals("4", answer("SELECT $1 FROM @st (PATTERN => '.*f1[.]csv') ORDER BY 1"));
        assertEquals("6", answer("SELECT $1 FROM @st/di (PATTERN => '.*[.]csv') ORDER BY 1"));
    }

    /** A table's stage names its files after the table, spelled as an identifier; a named stage does not. */
    @Test
    public void aTableStageNamesItsFilesAfterTheTable() {
        engine.execute("CREATE TABLE \"lt\" (a INT)");
        engine.execute("COPY INTO @%t1/f1 FROM (SELECT 3)" + CSV);
        engine.execute("COPY INTO @%t1/d/f2 FROM (SELECT 4)" + CSV);
        engine.execute("COPY INTO @%\"lt\"/f1 FROM (SELECT 3)" + CSV);
        assertEquals("3:@T1/f1 | 4:@T1/d/f2", answer("SELECT $1, METADATA$FILENAME FROM @%t1 ORDER BY 1"));
        assertEquals("4:@T1/d/f2", answer("SELECT $1, METADATA$FILENAME FROM @test_schema.%t1/d ORDER BY 1"));
        assertEquals("3:@\"lt\"/f1", answer("SELECT $1, METADATA$FILENAME FROM @%\"lt\" ORDER BY 1"));
        assertEquals("3", loaded("@%t1/f"));
    }

    /** REMOVE takes the path as the same prefix, and its PATTERN the same whole stored path. */
    @Test
    public void removeTakesThePathAsAPrefix() {
        assertEquals(List.of("st/f1", "st/f1.csv"), names("REMOVE @st/f1"));
        assertEquals(List.of(), names("REMOVE @st PATTERN = 'g'"));
        assertEquals(List.of("st/dir/g"), names("REMOVE @st/dir PATTERN = '.*g'"));
        assertEquals(List.of("st/dir/h.csv", "st/dirx/k"), names("REMOVE @st/di"));
        assertEquals(List.of(), names("LIST @st"));
    }

    /**
     * An unload refuses only over the name it would write. SINGLE = TRUE writes the path itself — or
     * {@code data} after a trailing slash — and minds only that exact name; several files are numbered after
     * the name and refuse when any staged name starts with it.
     */
    @Test
    public void anUnloadRefusesOnlyTheNameItWouldWrite() {
        assertEquals("1:2:2", answer("COPY INTO @st/f FROM (SELECT 1)" + CSV), "f1 and f1.csv do not block f");
        assertEquals("Files already existing at the unload destination: @st/f. Use overwrite option to force"
            + " unloading.", answer("COPY INTO @st/f FROM (SELECT 1)" + CSV));
        assertEquals("1:2:2", answer("COPY INTO @st/dir/ FROM (SELECT 1)" + CSV), "dir/data is free");
        assertEquals("Files already existing at the unload destination: @st/di. Use overwrite option to force"
            + " unloading.", answer("COPY INTO @st/di FROM (SELECT 1) FILE_FORMAT = (TYPE = CSV)"),
            "dir/g starts with di");
        answer("COPY INTO @st/pfx FROM (SELECT 1) FILE_FORMAT = (TYPE = CSV)");
        assertEquals("Files already existing at the unload destination: @st/pfx. Use overwrite option to"
            + " force unloading.", answer("COPY INTO @st/pfx FROM (SELECT 2) FILE_FORMAT = (TYPE = CSV)"));
        answer("COPY INTO @st FROM (SELECT 1) FILE_FORMAT = (TYPE = CSV)");
        assertEquals("Files already existing at the unload destination: @st. Use overwrite option to force"
            + " unloading.", answer("COPY INTO @st FROM (SELECT 1) FILE_FORMAT = (TYPE = CSV)"));
        answer("COPY INTO @st/f FROM (SELECT 9)" + CSV + " OVERWRITE = TRUE");
        assertEquals(List.of("st/data_0_0_0.csv.gz", "st/dir/data", "st/dir/g", "st/dir/h.csv", "st/dirx/k",
            "st/f", "st/f1", "st/f1.csv", "st/pfx_0_0_0.csv.gz"), names("LIST @st"));
        assertEquals("9", answer("SELECT $1 FROM @st/f (PATTERN => '.*/f') ORDER BY 1"));
    }
}
