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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An external stage on {@code s3://} is backed by local files under {@code stage.s3.localRoot}, and nothing its URL
 * spells reaches outside that root: a key's {@code ..} segments are names inside the bucket, as they are on S3, and a
 * URL with no bucket names no stage location at all. LIST, a staged-file query and an unload all stay inside.
 */
public class S3StageLocalRootTest {

    private static final String CSV = " FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE";

    private Path base;
    private Path root;
    private Path outside;
    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() throws IOException {
        base = Files.createTempDirectory("s3_stage_local_root_");
        root = Files.createDirectories(base.resolve("root"));
        Files.createDirectories(root.resolve("bucket"));
        outside = Files.createDirectories(base.resolve("outside"));
        Files.write(outside.resolve("secret.csv"), "secret,1\n".getBytes(StandardCharsets.UTF_8));
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_STAGE_S3_LOCAL_ROOT, root.toString());
        engine = new DatabaseEngine(cfg);
        engine.execute("CREATE DATABASE d");
        engine.execute("USE SCHEMA d.PUBLIC");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
        deleteRecursively(base.toFile());
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

    /** Every row's first cell, a bar between rows. */
    private String answer(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? " | " : "").append(row.getValue(0));
        }
        return out.toString();
    }

    @Test
    public void aUrlsDotSegmentsReadNothingOutsideTheRoot() {
        engine.execute("CREATE STAGE up URL = 's3://bucket/../../outside/'");
        assertEquals("", answer("LIST @up"));
        assertEquals("", answer("SELECT $1 FROM @up"));
    }

    @Test
    public void anUnloadThroughDotSegmentsWritesInsideTheRoot() {
        engine.execute("CREATE STAGE up URL = 's3://bucket/../../outside/'");
        engine.execute("COPY INTO @up/written FROM (SELECT 'through the stage')" + CSV);
        assertFalse(Files.exists(outside.resolve("written")));
        assertTrue(Files.isRegularFile(root.resolve("bucket").resolve("%2E%2E").resolve("%2E%2E")
            .resolve("outside").resolve("written")));
        assertEquals("through the stage", answer("SELECT $1 FROM @up/written"));
    }

    @Test
    public void aUrlWithNoBucketIsNoStageLocation() {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE STAGE abs URL = 's3://" + outside + "/'");
            }
        });
        assertEquals("Bucket name '' in the stage location is not supported. Valid bucket names must consist of"
            + " lowercase letters, digits, hyphens '-', and periods '.'.", refused.getMessage());
    }

    @Test
    public void aKeyInsideTheRootStillReads() throws IOException {
        Files.write(Files.createDirectories(root.resolve("bucket").resolve("in")).resolve("ok.csv"),
            "inside,2\n".getBytes(StandardCharsets.UTF_8));
        engine.execute("CREATE STAGE inside URL = 's3://bucket/in/'");
        assertEquals("inside/ok.csv", answer("LIST @inside"));
        assertEquals("inside", answer("SELECT $1 FROM @inside"));
    }
}
