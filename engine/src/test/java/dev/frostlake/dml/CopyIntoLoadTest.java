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
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies COPY INTO &lt;table&gt; FROM @stage physically reads + parses staged CSV files and inserts rows
 * (no longer a simulation): positional + column-mapped loading, SKIP_HEADER, FIELD_OPTIONALLY_ENCLOSED_BY,
 * ON_ERROR, load history (already-loaded files skipped unless FORCE), and end-to-end pipe REFRESH ingestion.
 * Uses a {@code file://} stage backed by a temp directory.
 */
public class CopyIntoLoadTest {

    private DatabaseEngine engine;
    private Path stageDir;

    @BeforeEach
    public void setUp() throws IOException {
        stageDir = Files.createTempDirectory("copy_load_test_");
        engine = new DatabaseEngine();
        // The tests point stages at local file:// directories - opt in to the affordance the
        // default config refuses (a real account refuses those URLs).
        engine.getConfig().setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "true");
        engine.execute("CREATE DATABASE db");
        engine.execute("USE DATABASE db");
        engine.execute("CREATE SCHEMA s");
        engine.execute("USE SCHEMA s");
        engine.execute("CREATE STAGE data_stage URL='file://" + stageDir + "'");
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (engine != null) {
            engine.shutdown();
        }
        deleteRecursively(stageDir.toFile());
    }

    private void writeStageFile(final String name, final String content) throws IOException {
        Files.writeString(stageDir.resolve(name), content);
    }

    private long count(final String table) {
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM " + table);
        return ((Number) rs.getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void copyLoadsCsvRows() throws IOException {
        writeStageFile("people.csv", "1,Alice,100.50\n2,Bob,200.00\n");
        engine.execute("CREATE TABLE people (id INTEGER, name VARCHAR, amount NUMBER(10,2))");

        engine.execute("COPY INTO people FROM @data_stage FILE_FORMAT = (TYPE = 'CSV')");

        assertEquals(2, count("people"));
        final ResultSet rs = engine.executeQuery("SELECT name FROM people WHERE id = 1");
        assertEquals("Alice", rs.getRows().get(0).getValue(0).toString());
    }

    @Test
    public void copySkipHeader() throws IOException {
        writeStageFile("h.csv", "id,name\n1,Alice\n2,Bob\n");
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");

        engine.execute("COPY INTO t FROM @data_stage FILE_FORMAT = (TYPE = 'CSV', SKIP_HEADER = 1)");

        assertEquals(2, count("t"));
    }

    @Test
    public void copyColumnMappingReorders() throws IOException {
        writeStageFile("m.csv", "Alice,1\nBob,2\n");   // file is name,id
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");

        engine.execute("COPY INTO t (name, id) FROM @data_stage FILE_FORMAT = (TYPE = 'CSV')");

        final ResultSet rs = engine.executeQuery("SELECT name FROM t WHERE id = 1");
        assertEquals("Alice", rs.getRows().get(0).getValue(0).toString());
    }

    @Test
    public void copyHonorsFieldOptionallyEnclosedBy() throws IOException {
        writeStageFile("q.csv", "1,\"Smith, John\"\n");   // quoted field contains the delimiter
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");

        engine.execute("COPY INTO t FROM @data_stage "
            + "FILE_FORMAT = (TYPE = 'CSV', FIELD_OPTIONALLY_ENCLOSED_BY = '\"')");

        final ResultSet rs = engine.executeQuery("SELECT name FROM t");
        assertEquals("Smith, John", rs.getRows().get(0).getValue(0).toString());
    }

    @Test
    public void copyOnErrorContinueSkipsBadRows() throws IOException {
        writeStageFile("e.csv", "1,Alice\nbad,Bob\n3,Carol\n");   // 'bad' is not a valid INTEGER
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");

        engine.execute("COPY INTO t FROM @data_stage FILE_FORMAT = (TYPE = 'CSV') ON_ERROR = 'CONTINUE'");

        assertEquals(2, count("t"));
    }

    @Test
    public void copyDefaultOnErrorAborts() throws IOException {
        writeStageFile("e.csv", "1,Alice\nbad,Bob\n");
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("COPY INTO t FROM @data_stage FILE_FORMAT = (TYPE = 'CSV')");
            }
        });
    }

    @Test
    public void copySkipsAlreadyLoadedUnlessForce() throws IOException {
        writeStageFile("once.csv", "1,Alice\n");
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");

        engine.execute("COPY INTO t FROM @data_stage FILE_FORMAT = (TYPE = 'CSV')");
        assertEquals(1, count("t"));

        // Re-COPY: the file is already loaded → skipped, no duplicate rows.
        engine.execute("COPY INTO t FROM @data_stage FILE_FORMAT = (TYPE = 'CSV')");
        assertEquals(1, count("t"));

        // FORCE = TRUE reloads the file.
        engine.execute("COPY INTO t FROM @data_stage FILE_FORMAT = (TYPE = 'CSV') FORCE = TRUE");
        assertEquals(2, count("t"));
    }

    @Test
    public void pipeRefreshLoadsDataEndToEnd() throws IOException {
        writeStageFile("events.csv", "1,login\n2,logout\n");
        engine.execute("CREATE TABLE events (id INTEGER, kind VARCHAR)");
        engine.execute("CREATE PIPE evt_pipe AS COPY INTO events FROM @data_stage FILE_FORMAT = (TYPE = 'CSV')");

        engine.execute("ALTER PIPE evt_pipe REFRESH");

        assertEquals(2, count("events"));
    }

    @Test
    public void copyLoadsJsonIntoVariantColumn() throws IOException {
        writeStageFile("e.json", "{\"event\":\"login\",\"user\":\"alice\"}\n{\"event\":\"logout\",\"user\":\"bob\"}\n");
        engine.execute("CREATE TABLE events (payload VARIANT)");

        engine.execute("COPY INTO events FROM @data_stage FILE_FORMAT = (TYPE = 'JSON')");

        assertEquals(2, count("events"));
        final ResultSet rs = engine.executeQuery("SELECT payload FROM events ORDER BY payload:user::VARCHAR");
        assertTrue(rs.getRows().get(0).getValue(0).toString().contains("login"));
    }

    @Test
    public void copyLoadsJsonMappedToColumns() throws IOException {
        writeStageFile("p.json", "{\"id\":1,\"name\":\"Alice\"}\n{\"id\":2,\"name\":\"Bob\"}\n");
        engine.execute("CREATE TABLE people (id INTEGER, name VARCHAR)");

        // Splitting a JSON record across several columns is what MATCH_BY_COLUMN_NAME is for —
        // without it Snowflake rejects a multi-column target (live-verified).
        engine.execute("COPY INTO people FROM @data_stage FILE_FORMAT = (TYPE = 'JSON')"
            + " MATCH_BY_COLUMN_NAME = 'CASE_INSENSITIVE'");

        assertEquals(2, count("people"));
        final ResultSet rs = engine.executeQuery("SELECT name FROM people WHERE id = 2");
        assertEquals("Bob", rs.getRows().get(0).getValue(0).toString());
    }

    @Test
    public void copyLoadsJsonArrayFile() throws IOException {
        writeStageFile("a.json", "[{\"v\":1}, {\"v\":2}, {\"v\":3}]");
        engine.execute("CREATE TABLE arr (doc VARIANT)");

        engine.execute("COPY INTO arr FROM @data_stage FILE_FORMAT = (TYPE = 'JSON')");

        assertEquals(3, count("arr"));
    }

    @Test
    public void copyAbortLeavesTableUnchanged() throws IOException {
        writeStageFile("bad.csv", "1,Alice\nbad,Bob\n3,Carol\n");   // 'bad' fails the INTEGER coercion
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("COPY INTO t FROM @data_stage FILE_FORMAT = (TYPE = 'CSV')");
            }
        });

        // ABORT_STATEMENT is atomic: nothing loads, not even the good row before the bad one.
        assertEquals(0, count("t"));
    }

    @Test
    public void testCopyIntoStageUnloadWritesCsv() throws IOException {
        engine.execute("CREATE TABLE export_t (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO export_t VALUES (1, 'Alice'), (2, 'Bob')");

        final ResultSet rs = engine.executeQuery("COPY INTO @data_stage FROM export_t FILE_FORMAT = (TYPE = 'CSV' COMPRESSION = NONE)");
        assertEquals(2, ((Number) rs.getRows().get(0).getValue(0)).intValue());

        // The unload wrote a CSV file into the file:// stage directory containing both rows.
        final Path csv = stageDir.resolve("data_0_0_0.csv");
        assertTrue(Files.exists(csv), "unload should write data_0_0_0.csv into the stage dir");
        final String content = Files.readString(csv);
        assertTrue(content.contains("1,Alice"), "CSV should contain the first row");
        assertTrue(content.contains("2,Bob"), "CSV should contain the second row");
    }

    @Test
    public void copyAcceptsUnquotedFormatOptions() throws IOException {
        writeStageFile("u.csv", "1,Alice\n2,Bob\n");
        engine.execute("CREATE TABLE u (id INTEGER, name VARCHAR)");

        // Snowflake's canonical unquoted forms: TYPE = CSV, COMPRESSION = NONE, ON_ERROR = CONTINUE
        // (CONTINUE/AUTO are keyword-valued; CSV/NONE are bare identifiers).
        engine.execute("COPY INTO u FROM @data_stage "
            + "FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) ON_ERROR = CONTINUE");

        assertEquals(2, count("u"));
    }

    @Test
    public void copyUnloadBareHeaderWritesColumnNames() throws IOException {
        engine.execute("CREATE TABLE hdr_t (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO hdr_t VALUES (1, 'Alice')");

        // Bare HEADER (no "= TRUE") means HEADER = TRUE: the CSV gets a column-name header row.
        engine.executeQuery("COPY INTO @data_stage FROM hdr_t FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) HEADER");

        final String content = Files.readString(stageDir.resolve("data_0_0_0.csv"));
        final String firstLine = content.split("\n", 2)[0].toUpperCase();
        assertTrue(firstLine.contains("ID") && firstLine.contains("NAME"),
            "bare HEADER should emit a column-name header row, got: " + firstLine);
    }

    @Test
    public void copyLoadsFromStageSubPath() throws IOException {
        Files.createDirectories(stageDir.resolve("sub"));
        Files.writeString(stageDir.resolve("sub").resolve("p.csv"), "1,Alice\n2,Bob\n");
        engine.execute("CREATE TABLE sp (id INTEGER, name VARCHAR)");

        // FROM @stage/sub narrows the load to files beneath that path.
        engine.execute("COPY INTO sp FROM @data_stage/sub FILE_FORMAT = (TYPE = CSV)");

        assertEquals(2, count("sp"));
    }

    @Test
    public void copyUnloadToStageSubPath() throws IOException {
        engine.execute("CREATE TABLE up (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO up VALUES (1, 'Alice')");

        engine.executeQuery("COPY INTO @data_stage/out FROM up FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE)");

        final Path csv = stageDir.resolve("out").resolve("data_0_0_0.csv");
        assertTrue(Files.exists(csv), "unload to @stage/out should write beneath the sub-path");
        assertTrue(Files.readString(csv).contains("1,Alice"));
    }

    @Test
    public void copyUnloadToExternalLocationWritesCsv() throws IOException {
        engine.execute("CREATE TABLE ext_t (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO ext_t VALUES (1, 'Alice'), (2, 'Bob')");

        // COPY INTO '<external location>' — unload directly to a URL rather than a named stage.
        final ResultSet rs = engine.executeQuery(
            "COPY INTO 'file://" + stageDir + "/ext' FROM ext_t FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE)");
        assertEquals(2, ((Number) rs.getRows().get(0).getValue(0)).intValue());

        final Path csv = stageDir.resolve("ext").resolve("data_0_0_0.csv");
        assertTrue(Files.exists(csv), "external-location unload should write data_0_0_0.csv");
        assertTrue(Files.readString(csv).contains("1,Alice"));
    }

    @Test
    public void copyUnloadPartitionByWritesPerPartitionFiles() throws IOException {
        engine.execute("CREATE TABLE part_t (id INTEGER, region VARCHAR)");
        engine.execute("INSERT INTO part_t VALUES (1, 'EAST'), (2, 'WEST'), (3, 'EAST')");

        final ResultSet rs = engine.executeQuery(
            "COPY INTO @data_stage FROM part_t PARTITION BY region FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE)");
        assertEquals(3, ((Number) rs.getRows().get(0).getValue(0)).intValue());

        // Each distinct partition value lands in its own sub-directory with a data file.
        final Path eastCsv = stageDir.resolve("EAST").resolve("data_0_0_0.csv");
        final Path westCsv = stageDir.resolve("WEST").resolve("data_0_0_0.csv");
        assertTrue(Files.exists(eastCsv), "PARTITION BY should create an EAST partition file");
        assertTrue(Files.exists(westCsv), "PARTITION BY should create a WEST partition file");
        final String east = Files.readString(eastCsv);
        assertTrue(east.contains("1,EAST") && east.contains("3,EAST"), "EAST partition should hold both EAST rows");
        assertTrue(Files.readString(westCsv).contains("2,WEST"), "WEST partition should hold the WEST row");
    }

    @Test
    public void copyUnloadRoundTripsBackIntoTable() {
        // End-to-end: the files an unload writes must be loadable back into a table with the same values.
        engine.execute("CREATE TABLE rt_src (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO rt_src VALUES (1, 'Alice'), (2, 'Bob')");
        engine.executeQuery("COPY INTO @data_stage FROM rt_src FILE_FORMAT = (TYPE = CSV)");

        engine.execute("CREATE TABLE rt_dst (id INTEGER, name VARCHAR)");
        engine.execute("COPY INTO rt_dst FROM @data_stage FILE_FORMAT = (TYPE = CSV)");

        assertEquals(2, count("rt_dst"));
        final ResultSet rs = engine.executeQuery("SELECT name FROM rt_dst WHERE id = 2");
        assertEquals("Bob", rs.getRows().get(0).getValue(0).toString());
    }

    @Test
    public void copyUnloadSingleTrueRoundTrips() {
        // SINGLE = TRUE unloads to a single file; it must still be loadable back.
        engine.execute("CREATE TABLE sg_src (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO sg_src VALUES (1, 'Alice'), (2, 'Bob'), (3, 'Carol')");
        engine.executeQuery("COPY INTO @data_stage FROM sg_src FILE_FORMAT = (TYPE = CSV) SINGLE = TRUE");

        engine.execute("CREATE TABLE sg_dst (id INTEGER, name VARCHAR)");
        engine.execute("COPY INTO sg_dst FROM @data_stage FILE_FORMAT = (TYPE = CSV)");
        assertEquals(3, count("sg_dst"));
    }

    @Test
    public void copyUnloadOverwriteReUnloads() {
        // OVERWRITE = TRUE lets a second unload to the same stage replace the prior file without error.
        engine.execute("CREATE TABLE ow_src (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO ow_src VALUES (1, 'Alice')");
        engine.executeQuery("COPY INTO @data_stage FROM ow_src FILE_FORMAT = (TYPE = CSV)");

        final ResultSet rs = engine.executeQuery(
            "COPY INTO @data_stage FROM ow_src FILE_FORMAT = (TYPE = CSV) OVERWRITE = TRUE");
        assertEquals(1, ((Number) rs.getRows().get(0).getValue(0)).intValue());

        engine.execute("CREATE TABLE ow_dst (id INTEGER, name VARCHAR)");
        engine.execute("COPY INTO ow_dst FROM @data_stage FILE_FORMAT = (TYPE = CSV)");
        assertEquals(1, count("ow_dst"));
    }

    @Test
    public void copyLoadsWithColumnTransformation() throws IOException {
        writeStageFile("t.csv", "1,alice,5\n2,bob,10\n");
        engine.execute("CREATE TABLE transformed (id INTEGER, tagged_name VARCHAR, as_number INTEGER)");

        // $1 → id, CONCAT/SUBSTR shaping → tagged_name, TO_NUMBER($3) → as_number — all from the
        // account's COPY-transformation allowlist (UPPER and arithmetic are refused there).
        engine.execute("COPY INTO transformed"
            + " FROM (SELECT $1, CONCAT(SUBSTR($2, 1, 3), '-x'), TO_NUMBER($3) FROM @data_stage) "
            + "FILE_FORMAT = (TYPE = CSV)");

        assertEquals(2, count("transformed"));
        final ResultSet rs = engine.executeQuery("SELECT tagged_name, as_number FROM transformed WHERE id = 1");
        assertEquals("ali-x", rs.getRows().get(0).getValue(0).toString());
        assertEquals(5, ((Number) rs.getRows().get(0).getValue(1)).intValue());
    }

    @Test
    public void copyTransformationRespectsColumnList() throws IOException {
        writeStageFile("c.csv", "alice,1\nbob,2\n");   // file is name,id
        engine.execute("CREATE TABLE ppl (id INTEGER, name VARCHAR)");

        // Explicit column list (name, id) maps the two projected expressions positionally to those columns.
        engine.execute("COPY INTO ppl (name, id) FROM (SELECT $1, $2 FROM @data_stage) FILE_FORMAT = (TYPE = CSV)");

        final ResultSet rs = engine.executeQuery("SELECT name FROM ppl WHERE id = 1");
        assertEquals("alice", rs.getRows().get(0).getValue(0).toString());
    }

    @Test
    public void copyTransformationMissingFieldIsNull() throws IOException {
        writeStageFile("short.csv", "1\n");   // only one field; $2 is absent
        engine.execute("CREATE TABLE tn (id INTEGER, name VARCHAR)");

        engine.execute("COPY INTO tn FROM (SELECT $1, $2 FROM @data_stage) FILE_FORMAT = (TYPE = CSV)");

        final ResultSet rs = engine.executeQuery("SELECT id, name FROM tn");
        assertEquals(1, ((Number) rs.getRows().get(0).getValue(0)).intValue());
        assertNull(rs.getRows().get(0).getValue(1), "a positional reference past the row's fields is NULL");
    }

    @Test
    public void copyTransformationSupportsCastAndConditionals() throws IOException {
        writeStageFile("x.csv", "1,alice,\n2,,bob\n");   // row 2 has an empty $2
        engine.execute("CREATE TABLE xf (id INTEGER, who VARCHAR, initial VARCHAR)");

        // Constructs documented in docs/functions.md: ::cast, COALESCE, SUBSTR over staged fields.
        engine.execute("COPY INTO xf FROM ("
            + "SELECT $1::INTEGER, COALESCE($2, $3), SUBSTR(COALESCE($2, $3), 1, 1) FROM @data_stage) "
            + "FILE_FORMAT = (TYPE = CSV)");

        final ResultSet rs = engine.executeQuery("SELECT who, initial FROM xf WHERE id = 2");
        assertEquals("bob", rs.getRows().get(0).getValue(0).toString());
        assertEquals("b", rs.getRows().get(0).getValue(1).toString());
    }

    @Test
    public void copyLoadsJsonWithPathTransformation() throws IOException {
        writeStageFile("j.json", "{\"id\":1,\"name\":\"Alice\"}\n{\"id\":2,\"name\":\"Bob\"}\n");
        engine.execute("CREATE TABLE jt (id INTEGER, name VARCHAR)");

        // $1 is the whole JSON document; $1:id / $1:name extract fields by path.
        engine.execute("COPY INTO jt FROM (SELECT $1:id, $1:name FROM @data_stage) FILE_FORMAT = (TYPE = JSON)");

        assertEquals(2, count("jt"));
        final ResultSet rs = engine.executeQuery("SELECT name FROM jt WHERE id = 2");
        assertEquals("Bob", rs.getRows().get(0).getValue(0).toString());
    }

    @Test
    public void copyLoadsJsonNestedPathTransformation() throws IOException {
        writeStageFile("n.json", "{\"id\":1,\"addr\":{\"city\":\"NYC\"}}\n");
        engine.execute("CREATE TABLE jn (id INTEGER, city VARCHAR)");

        // Nested path: $1:addr.city navigates into the embedded object.
        engine.execute("COPY INTO jn FROM (SELECT $1:id, $1:addr.city FROM @data_stage) FILE_FORMAT = (TYPE = JSON)");

        final ResultSet rs = engine.executeQuery("SELECT city FROM jn WHERE id = 1");
        assertEquals("NYC", rs.getRows().get(0).getValue(0).toString());
    }

    // ── VALIDATION_MODE: validate WITHOUT loading (previously parsed and ignored — data loaded) ────

    @Test
    public void validationReturnErrorsListsBadRowsAndLoadsNothing() throws IOException {
        writeStageFile("v.csv", "1,alice\n,bob\n");
        engine.execute("CREATE TABLE v1 (id INTEGER NOT NULL, name VARCHAR)");

        final ResultSet rs = engine.executeQuery(
            "COPY INTO v1 FROM @data_stage VALIDATION_MODE = 'RETURN_ERRORS'");
        assertEquals(1, rs.getRowCount(), "one would-be-rejected record");
        assertEquals("ERROR", rs.getColumns().get(0).getName());
        assertEquals("v.csv", rs.getRows().get(0).getValue(1));
        assertEquals(0L, count("v1"), "validation must not load any rows");

        // Validation must not record load history either: a real COPY afterwards still loads.
        engine.execute("COPY INTO v1 FROM @data_stage ON_ERROR = 'CONTINUE'");
        assertEquals(1L, count("v1"), "the good row loads after validation");
    }

    @Test
    public void validationReturnErrorsCleanFileIsEmptyAndLoadsNothing() throws IOException {
        writeStageFile("clean.csv", "1,alice\n2,bob\n");
        engine.execute("CREATE TABLE v2 (id INTEGER, name VARCHAR)");

        final ResultSet rs = engine.executeQuery(
            "COPY INTO v2 FROM @data_stage VALIDATION_MODE = 'RETURN_ERRORS'");
        assertEquals(0, rs.getRowCount(), "clean file: no validation errors");
        assertEquals(0L, count("v2"), "validation must not load any rows");
    }

    @Test
    public void validationReturnNRowsPreviewsWithoutLoading() throws IOException {
        writeStageFile("preview.csv", "1,a\n2,b\n3,c\n");
        engine.execute("CREATE TABLE v3 (id INTEGER, name VARCHAR)");

        final ResultSet rs = engine.executeQuery(
            "COPY INTO v3 FROM @data_stage VALIDATION_MODE = 'RETURN_2_ROWS'");
        assertEquals(2, rs.getRowCount(), "RETURN_2_ROWS previews exactly two rows");
        assertEquals("ID", rs.getColumns().get(0).getName().toUpperCase());
        assertEquals(0L, count("v3"), "the preview must not load any rows");
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
