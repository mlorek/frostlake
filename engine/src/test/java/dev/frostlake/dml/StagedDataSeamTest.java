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
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The staged-data seam over INTERNAL named stages, live-verified end to end: PUT lands a file
 * (nine-column result, AUTO_COMPRESS defaulting to gzip with the {@code .gz} rename, an identical
 * re-PUT answering SKIPPED), LIST names it {@code stagename/path} with size/md5/RFC-1123 columns,
 * COPY loads the rows — through gzip for the compressed flavor — and REMOVE clears it with the
 * lowercase {@code removed}. The same SQL runs on both transports: embedded lands in the stage's
 * engine-managed directory, live uploads through the JDBC driver.
 */
public class StagedDataSeamTest extends BaseDatabaseTest {

    @BeforeEach
    public void createFixture() {
        engine.execute("CREATE STAGE seam_stage");
        engine.execute("CREATE TABLE seam_t (id INTEGER, name VARCHAR)");
    }

    private Row soleRow(final ResultSet rs) {
        assertEquals(1, rs.getRowCount());
        return rs.getRows().get(0);
    }

    @Test
    public void putListCopyRemoveRoundTrip() {
        stageLocalFile("seam_stage", "seam_rows.csv", "1,alice\n2,bob\n");

        final ResultSet listed = engine.executeQuery("LIST @seam_stage");
        final Row file = soleRow(listed);
        assertEquals("seam_stage/seam_rows.csv",
            file.getValue(listed.getColumnIndex("name")).toString());
        assertTrue(((Number) file.getValue(listed.getColumnIndex("size"))).longValue() >= 14,
            "listed size covers the content");

        engine.execute("COPY INTO seam_t FROM @seam_stage FILE_FORMAT = (TYPE = CSV)");
        final ResultSet loaded = engine.executeQuery("SELECT COUNT(*), MIN(name) FROM seam_t");
        assertEquals("2", loaded.getRows().get(0).getValue(0).toString());
        assertEquals("alice", loaded.getRows().get(0).getValue(1).toString());

        final ResultSet removed = engine.executeQuery("REMOVE @seam_stage/seam_rows.csv");
        final Row removal = soleRow(removed);
        assertEquals("seam_stage/seam_rows.csv",
            removal.getValue(removed.getColumnIndex("name")).toString());
        assertEquals("removed", removal.getValue(removed.getColumnIndex("result")).toString());
        assertEquals(0, engine.executeQuery("LIST @seam_stage").getRowCount());
    }

    @Test
    public void putReportsLiveShapedColumnsAndSkipsIdenticalRePuts() {
        stageLocalFile("seam_stage", "dup.csv", "9,zed\n");

        // The same content PUT again under the same name: SKIPPED, size 0, live's message.
        final Path dir;
        try {
            dir = Files.createTempDirectory("fl_seam_dup");
            Files.writeString(dir.resolve("dup.csv"), "9,zed\n");
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        final ResultSet rePut = engine.executeQuery(
            "PUT file://" + dir.resolve("dup.csv").toAbsolutePath()
                + " @seam_stage AUTO_COMPRESS=FALSE");
        final Row row = soleRow(rePut);
        assertEquals("dup.csv", row.getValue(rePut.getColumnIndex("source")).toString());
        assertEquals("dup.csv", row.getValue(rePut.getColumnIndex("target")).toString());
        assertEquals("SKIPPED", row.getValue(rePut.getColumnIndex("status")).toString());
        assertEquals("0", row.getValue(rePut.getColumnIndex("target_size")).toString());
        assertTrue(row.getValue(rePut.getColumnIndex("message")).toString()
            .contains("same destination name and checksum already exists"),
            "live's skip message");
    }

    @Test
    public void defaultAutoCompressGzipsAndCopyReadsThroughIt() {
        final Path dir;
        try {
            dir = Files.createTempDirectory("fl_seam_gz");
            Files.writeString(dir.resolve("zipped.csv"), "5,eve\n6,mallory\n");
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        final ResultSet put = engine.executeQuery(
            "PUT file://" + dir.resolve("zipped.csv").toAbsolutePath() + " @seam_stage");
        final Row row = soleRow(put);
        assertEquals("zipped.csv.gz", row.getValue(put.getColumnIndex("target")).toString());
        assertEquals("GZIP", row.getValue(put.getColumnIndex("target_compression")).toString());
        assertEquals("UPLOADED", row.getValue(put.getColumnIndex("status")).toString());

        final ResultSet listed = engine.executeQuery("LIST @seam_stage PATTERN = '.*zipped.*'");
        assertEquals("seam_stage/zipped.csv.gz",
            soleRow(listed).getValue(listed.getColumnIndex("name")).toString());

        engine.execute("COPY INTO seam_t FROM @seam_stage FILE_FORMAT = (TYPE = CSV)");
        assertEquals("2",
            engine.executeQuery("SELECT COUNT(*) FROM seam_t").getRows().get(0).getValue(0).toString());
    }

    @Test
    public void tableStagePutFeedsCopy() {
        final Path dir;
        try {
            dir = Files.createTempDirectory("fl_seam_ts");
            Files.writeString(dir.resolve("tstage.csv"), "7,grace\n");
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        final ResultSet put = engine.executeQuery(
            "PUT file://" + dir.resolve("tstage.csv").toAbsolutePath()
                + " @%seam_t AUTO_COMPRESS=FALSE");
        assertEquals("UPLOADED",
            soleRow(put).getValue(put.getColumnIndex("status")).toString());

        engine.execute("COPY INTO seam_t FROM @%seam_t FILE_FORMAT = (TYPE = CSV)");
        assertEquals("1",
            engine.executeQuery("SELECT COUNT(*) FROM seam_t").getRows().get(0).getValue(0).toString());
    }
}
