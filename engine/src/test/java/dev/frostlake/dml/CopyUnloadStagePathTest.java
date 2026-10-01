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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * COPY INTO {@code @stage/sub/path/} unload targets: a path beneath the stage, written in Snowflake's
 * conventional directory form with a trailing slash, combined with PARTITION BY, FILE_FORMAT and
 * MAX_FILE_SIZE — the exact statement shape used by warehouse export procedures.
 */
public class CopyUnloadStagePathTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(CopyUnloadStagePathTest.class);

    @TempDir
    Path stageDir;

    /**
     * Every test unloads to (or loads from) a stage whose URL is a local {@code file://} directory and
     * then reads the produced file off the local disk — neither half exists on a real account.
     */
    @BeforeEach
    public void skipWhenLive() {
        Assumptions.assumeFalse(isLiveSnowflake(),
            "unloads into a local `file://` stage and then inspects the produced file on the local "
            + "filesystem, which no account-side run can do; the local-URL affordance itself is an "
            + "explicit opt-in (stage.file.urlEnabled) whose default surface StageUrlPolicyTest pins");
    }

    @Test
    public void unloadToStageSubPathWithTrailingSlash() throws IOException {
        engine.execute("CREATE TABLE items (id INTEGER, label VARCHAR)");
        engine.execute("INSERT INTO items VALUES (1, 'one'), (2, 'two')");
        engine.execute("CREATE STAGE exp_stage URL = 'file://" + stageDir + "'");

        final ResultSet rs = engine.executeQuery("""
            COPY INTO @exp_stage/reports/daily/ FROM (SELECT id, label FROM items)
            FILE_FORMAT = (TYPE = 'CSV' COMPRESSION = NONE)
            """);
        assertEquals(2, ((Number) rs.getRows().get(0).getValue(0)).intValue());

        final Path out = stageDir.resolve("reports/daily/data_0_0_0.csv");
        assertTrue(Files.exists(out), "unload file must land under the stage sub-path");
        final String content = new String(Files.readAllBytes(out), StandardCharsets.UTF_8);
        logger.info("Unloaded CSV content: {}", content);
        assertTrue(content.contains("one") && content.contains("two"));
    }

    @Test
    public void unloadWithPartitionByFileFormatAndMaxFileSize() throws IOException {
        engine.execute("CREATE TABLE orders (region_id VARCHAR, order_id VARCHAR)");
        engine.execute("INSERT INTO orders VALUES ('r1', 'o1'), ('r1', 'o2')");
        engine.execute("CREATE STAGE unload_all URL = 'file://" + stageDir + "'");

        // The full export-procedure shape: object projection, trailing-slash target, literal
        // PARTITION BY, JSON format with compression, and a MAX_FILE_SIZE hint.
        final ResultSet rs = engine.executeQuery("""
            COPY INTO @unload_all/exports/orders/ FROM (
                SELECT OBJECT_CONSTRUCT('region_id', region_id, 'order_id', order_id) AS record
                FROM orders
                ORDER BY order_id
            )
            PARTITION BY ('full-2026-07-28')
            FILE_FORMAT = (TYPE = JSON COMPRESSION = GZIP)
            MAX_FILE_SIZE = 134217728
            """);
        assertEquals(2, ((Number) rs.getRows().get(0).getValue(0)).intValue());

        // COMPRESSION = GZIP names the file .json.gz and writes gzip bytes, as the account does.
        final Path out = CopyIntoLoadTest.partitionFile(stageDir.resolve("exports/orders/full-2026-07-28"), ".json.gz");
        assertTrue(Files.exists(out), "partitioned unload must write under <sub-path>/<partition-key>/");
        final String content = gunzip(out);
        logger.info("Unloaded JSON content: {}", content);
        // A single-VARIANT-column unload writes the raw documents themselves (Snowflake TYPE=JSON
        // contract) — no {"RECORD": "..."} wrapper and no string-escaped embedding.
        final String[] lines = content.trim().split("\n");
        assertEquals(2, lines.length);
        // OBJECT keys come back alphabetically sorted, as in Snowflake.
        assertEquals("{\"order_id\":\"o1\",\"region_id\":\"r1\"}", lines[0]);
        assertEquals("{\"order_id\":\"o2\",\"region_id\":\"r1\"}", lines[1]);
    }

    private String gunzip(final Path file) throws IOException {
        try (final GZIPInputStream in = new GZIPInputStream(Files.newInputStream(file))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void unloadToSchemaQualifiedStageSubPath() {
        engine.execute("CREATE TABLE metrics (k VARCHAR, v INTEGER)");
        engine.execute("INSERT INTO metrics VALUES ('a', 1)");
        engine.execute("CREATE STAGE q_stage URL = 'file://" + stageDir + "'");

        final ResultSet rs = engine.executeQuery("""
            COPY INTO @test_schema.q_stage/out/ FROM (SELECT k, v FROM metrics)
            FILE_FORMAT = (TYPE = 'CSV' COMPRESSION = NONE)
            """);
        assertEquals(1, ((Number) rs.getRows().get(0).getValue(0)).intValue());
        assertTrue(Files.exists(stageDir.resolve("out/data_0_0_0.csv")));
    }

    @Test
    public void loadFromStageSubPathWithTrailingSlash() throws IOException {
        final Path sub = stageDir.resolve("incoming");
        Files.createDirectories(sub);
        Files.write(sub.resolve("rows.csv"), "10,ten\n20,twenty\n".getBytes(StandardCharsets.UTF_8));

        engine.execute("CREATE TABLE loaded (id INTEGER, label VARCHAR)");
        engine.execute("CREATE STAGE in_stage URL = 'file://" + stageDir + "'");
        engine.execute("COPY INTO loaded FROM @in_stage/incoming/ FILE_FORMAT = (TYPE = 'CSV')");

        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM loaded");
        assertEquals(2, ((Number) rs.getRows().get(0).getValue(0)).intValue());
    }
}
