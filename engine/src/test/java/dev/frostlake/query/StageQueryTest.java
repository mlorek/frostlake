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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Querying staged files directly: {@code SELECT ... FROM @stage[/path] [(FILE_FORMAT => 'name',
 * PATTERN => 'regex')]} with $1..$n positional fields and the metadata$filename /
 * metadata$file_row_number columns (present by name, hidden from {@code SELECT *}), plus the
 * {@code DIRECTORY(@stage)} directory table.
 */
public class StageQueryTest extends BaseDatabaseTest {

    private Path stageDir;

    @BeforeEach
    public void createStageWithFiles() throws IOException {
        stageDir = Files.createTempDirectory("stage_query_test_");
        Files.writeString(stageDir.resolve("a.csv"), "1,alpha,10\n2,beta,20\n");
        Files.writeString(stageDir.resolve("b.csv"), "3,gamma\n");
        Files.writeString(stageDir.resolve("data1.json"), "{\"a\": {\"b\": \"deep\"}, \"n\": 7}\n");
        Assumptions.assumeFalse(isLiveSnowflake(),
            "every test here reads files from a local `file://` directory the harness just wrote, "
            + "which no account-side stage can see; the local-URL affordance itself is an explicit "
            + "opt-in (stage.file.urlEnabled) whose default surface StageUrlPolicyTest pins");
        engine.execute("CREATE STAGE q_stage URL='file://" + stageDir + "'");
        engine.execute("CREATE FILE FORMAT q_json TYPE = 'JSON'");
    }

    @AfterEach
    public void removeFiles() {
        final File[] children = stageDir.toFile().listFiles();
        if (children != null) {
            for (final File child : children) {
                child.delete();
            }
        }
        stageDir.toFile().delete();
    }

    private ResultSet q(final String sql) {
        return engine.executeQuery(sql);
    }

    @Test
    public void selectStarExposesPositionalFieldsOnly() {
        final ResultSet rs = q("SELECT * FROM @q_stage (PATTERN => 'a[.]csv') ORDER BY 1");
        assertEquals(2, rs.getRowCount());
        assertEquals(3, rs.getColumns().size(), "SELECT * must expose the field columns but no METADATA$ columns");
        for (final ResultSetColumn col : rs.getColumns()) {
            assertTrue(col.getName().startsWith("COLUMN"), "unexpected star column: " + col.getName());
        }
        assertEquals("alpha", rs.getRows().get(0).getValue(1));
    }

    @Test
    public void positionalFieldsAndMetadataResolveByName() {
        final ResultSet rs = q("SELECT $2, metadata$filename, metadata$file_row_number "
            + "FROM @q_stage (PATTERN => 'a[.]csv') ORDER BY $1");
        assertEquals(2, rs.getRowCount());
        assertEquals("alpha", rs.getRows().get(0).getValue(0));
        assertEquals("a.csv", rs.getRows().get(0).getValue(1));
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(2)).longValue());
        assertEquals(2L, ((Number) rs.getRows().get(1).getValue(2)).longValue());
    }

    @Test
    public void aliasQualifiedPositionalFields() {
        final ResultSet rs = q("SELECT t.$1, t.$2 FROM @q_stage (PATTERN => '.*[.]csv') AS t ORDER BY t.$1");
        assertEquals(3, rs.getRowCount());
        assertEquals("3", rs.getRows().get(2).getValue(0));
        assertEquals("gamma", rs.getRows().get(2).getValue(1));
    }

    @Test
    public void shortRowsPadWithNulls() {
        // Scanning a.csv (3 fields) and b.csv (2 fields) together: the width is the widest file,
        // and b.csv's missing $3 must arrive as NULL, not as an error or a ragged row.
        final ResultSet rs = q("SELECT $1, $3 FROM @q_stage (PATTERN => '.*[.]csv') ORDER BY $1");
        assertEquals(3, rs.getRowCount());
        assertEquals("10", rs.getRows().get(0).getValue(1));
        assertEquals(null, rs.getRows().get(2).getValue(1), "the short b.csv row pads $3 with NULL");
    }

    @Test
    public void jsonRecordsArriveAsVariantDollarOne() {
        assertEquals("deep", String.valueOf(
            q("SELECT PARSE_JSON($1):a.b FROM @q_stage/data1.json (FILE_FORMAT => 'q_json')")
                .getRows().get(0).getValue(0)).replace("\"", ""));
        assertEquals(1, q("SELECT $1 FROM @q_stage (FILE_FORMAT => 'q_json', PATTERN => '.*[.]json')").getRowCount());
        // The format may also be an unquoted, fully qualified name, and the stage source may carry an alias.
        assertEquals(1, q("SELECT $1 FROM @q_stage (FILE_FORMAT => test_db.test_schema.q_json, "
            + "PATTERN => '.*[.]json') AS j").getRowCount());
        assertEquals(1, q("SELECT $1 FROM @q_stage (PATTERN => '.*[.]json', FILE_FORMAT => q_json) AS j")
            .getRowCount());
    }

    @Test
    public void aliasColumnListRenamesFields() {
        final ResultSet rs = q("SELECT c1 FROM @q_stage (PATTERN => 'b[.]csv') t (c1, c2)");
        assertEquals(1, rs.getRowCount());
        assertEquals("3", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void schemaQualifiedStageName() {
        final ResultSet rs = q("SELECT $1 FROM @test_schema.q_stage (PATTERN => 'b[.]csv')");
        assertEquals(1, rs.getRowCount());
        assertEquals("3", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void bareTrailingSlashAndValuesListsCoexist() {
        assertEquals(3, q("SELECT $1 FROM @q_stage/ (PATTERN => '.*[.]csv')").getRowCount());
        // The vendor idiom a quoted-source grammar once broke: a VALUES list of string tuples.
        // The quoted form is now predicate-gated to '@…' strings, so both must coexist.
        assertEquals(2, q("SELECT t.tag FROM (VALUES ('a', 'x'), ('b', 'y')) as t (tag, source)").getRowCount());
    }

    @Test
    public void keywordishPathSegmentsResolve() throws IOException {
        // Path segments that lex as keyword tokens (some, to) must stay valid stage-path words.
        final Path sub = Files.createDirectories(stageDir.resolve("some/path/to"));
        Files.writeString(sub.resolve("deep.csv"), "9\n");
        assertEquals(1, q("SELECT $1 FROM @q_stage/some/path/to/deep.csv").getRowCount());
    }

    @Test
    public void directoryTableListsFiles() {
        final ResultSet all = q("SELECT relative_path, size, file_url FROM DIRECTORY(@q_stage) ORDER BY relative_path");
        assertEquals(3, all.getRowCount());
        assertEquals("a.csv", all.getRows().get(0).getValue(0));
        assertTrue(((Number) all.getRows().get(0).getValue(1)).longValue() > 0);
        assertTrue(String.valueOf(all.getRows().get(0).getValue(2)).startsWith("file:"));

        final ResultSet filtered = q("SELECT file_url FROM DIRECTORY(@q_stage) WHERE size > 100000");
        assertEquals(0, filtered.getRowCount(), "all fixture files are small");
    }

    @Test
    public void putAcceptsTransferOptions() throws IOException {
        final Path local = Files.createTempFile("put_opts_", ".csv");
        Files.writeString(local, "9,zeta,90\n");
        engine.execute("PUT file://" + local + " @q_stage PARALLEL=4 AUTO_COMPRESS=FALSE "
            + "source_compression=gzip OVERWRITE=TRUE");
        assertTrue(Files.exists(stageDir.resolve(local.getFileName().toString())),
            "PUT with transfer options must still upload the file");
        Files.deleteIfExists(local);
    }
}

