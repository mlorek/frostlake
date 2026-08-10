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
import dev.frostlake.metastore.model.Stage;
import dev.frostlake.metastore.model.StageFile;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for S3 stage simulation using local filesystem
 */
public class S3StageSimulationTest {
    private static final Logger logger = LoggerFactory.getLogger(S3StageSimulationTest.class);

    private DatabaseEngine engine;
    private Stage s3Stage;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        // The tests point stages at local file:// directories - opt in to the affordance the
        // default config refuses (a real account refuses those URLs).
        engine.getConfig().setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "true");
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");

        // Create S3 stage
        engine.execute("CREATE STAGE s3_stage URL='s3://my-bucket/data/files'");

        s3Stage = engine.getCatalog().getStage("s3_stage");

        logger.info("S3 stage created with local path: {}", s3Stage.getLocalPath());
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }

        // Clean up simulated S3 directory
        if (s3Stage != null && s3Stage.getLocalPath() != null) {
            try {
                // Delete directory and contents
                deleteDirectory(s3Stage.getLocalPath());
            } catch (final IOException e) {
                logger.warn("Failed to clean up S3 stage directory: {}", e.getMessage());
            }
        }
    }

    @Test
    public void testS3StageCreation() {
        assertNotNull(s3Stage);
        assertEquals("s3://my-bucket/data/files", s3Stage.getUrl());
        assertTrue(s3Stage.isS3Simulated());
        assertEquals("my-bucket", s3Stage.getS3Bucket());
        assertEquals("data/files", s3Stage.getS3Prefix());
        assertNotNull(s3Stage.getLocalPath());
        assertTrue(Files.exists(s3Stage.getLocalPath()));
        logger.info("S3 stage properties verified");
    }

    @Test
    public void testS3StageWithBucketOnly() {
        engine.execute("CREATE STAGE s3_bucket_only URL='s3://another-bucket'");
        final Stage stage = engine.getCatalog().getStage("s3_bucket_only");

        assertTrue(stage.isS3Simulated());
        assertEquals("another-bucket", stage.getS3Bucket());
        assertEquals("", stage.getS3Prefix());
        logger.info("S3 bucket-only stage verified");
    }

    @Test
    public void testPutFileToS3Stage() throws IOException {
        // Create a temporary file
        final Path tempFile = Files.createTempFile("test", ".csv");
        Files.writeString(tempFile, "id,name,value\n1,Alice,100\n2,Bob,200\n");

        // Put file into S3 stage
        s3Stage.putFile(tempFile);

        // Verify file exists
        assertTrue(s3Stage.fileExists(tempFile.getFileName().toString()));

        // List files
        final List<StageFile> files = s3Stage.listFiles();
        assertEquals(1, files.size());
        assertEquals(tempFile.getFileName().toString(), files.get(0).getName());

        // Clean up temp file
        Files.deleteIfExists(tempFile);
        logger.info("PUT file to S3 stage verified");
    }

    @Test
    public void testGetFileFromS3Stage() throws IOException {
        // Create and put a file
        final Path tempFile = Files.createTempFile("test", ".json");
        final String content = "{\"key\": \"value\"}";
        Files.writeString(tempFile, content);
        s3Stage.putFile(tempFile);
        final String fileName = tempFile.getFileName().toString();

        // Get the file
        final Path destFile = Files.createTempFile("dest", ".json");
        s3Stage.getFile(fileName, destFile);

        // Verify content
        final String retrievedContent = Files.readString(destFile);
        assertEquals(content, retrievedContent);

        // Clean up
        Files.deleteIfExists(tempFile);
        Files.deleteIfExists(destFile);
        logger.info("GET file from S3 stage verified");
    }

    @Test
    public void testListFilesInS3Stage() throws IOException {
        // Put multiple files
        for (int i = 1; i <= 3; i++) {
            final Path tempFile = Files.createTempFile("data" + i, ".csv");
            Files.writeString(tempFile, "data" + i);
            s3Stage.putFile(tempFile);
            Files.deleteIfExists(tempFile);
        }

        // List all files
        final List<StageFile> files = s3Stage.listFiles();
        assertEquals(3, files.size());

        // List with pattern
        final Path tempFile = Files.createTempFile("data1", ".csv");
        final String fileName = tempFile.getFileName().toString();
        Files.deleteIfExists(tempFile);

        final List<StageFile> filtered = s3Stage.listFiles("data1*.csv");
        assertTrue(filtered.size() >= 1);
        logger.info("LIST files in S3 stage verified");
    }

    @Test
    public void testRemoveFileFromS3Stage() throws IOException {
        // Create and put a file
        final Path tempFile = Files.createTempFile("remove", ".txt");
        Files.writeString(tempFile, "to be removed");
        s3Stage.putFile(tempFile);
        final String fileName = tempFile.getFileName().toString();

        // Verify file exists
        assertTrue(s3Stage.fileExists(fileName));

        // Remove file
        final boolean removed = s3Stage.removeFile(fileName);
        assertTrue(removed);

        // Verify file doesn't exist
        assertFalse(s3Stage.fileExists(fileName));

        // Clean up
        Files.deleteIfExists(tempFile);
        logger.info("REMOVE file from S3 stage verified");
    }

    @Test
    public void testS3StageListCommand() {
        // Create a file in the stage
        try {
            final Path tempFile = Files.createTempFile("list_test", ".csv");
            Files.writeString(tempFile, "test data");
            s3Stage.putFile(tempFile);
            Files.deleteIfExists(tempFile);
        } catch (final IOException e) {
            fail("Failed to prepare test file: " + e.getMessage());
        }

        // Execute LIST command
        final ResultSet result = engine.executeQuery("LIST @s3_stage");
        assertNotNull(result);
        assertTrue(result.getRowCount() > 0);
        logger.info("LIST @stage command for S3 stage verified");
    }

    @Test
    public void testMultipleS3Stages() {
        // Create multiple S3 stages with different buckets/paths
        engine.execute("CREATE STAGE s3_raw URL='s3://data-lake/raw'");
        engine.execute("CREATE STAGE s3_processed URL='s3://data-lake/processed'");
        engine.execute("CREATE STAGE s3_archive URL='s3://archive-bucket/old-data'");

        final Stage raw = engine.getCatalog().getStage("s3_raw");
        final Stage processed = engine.getCatalog().getStage("s3_processed");
        final Stage archive = engine.getCatalog().getStage("s3_archive");

        // Verify all stages have different local paths
        assertNotEquals(raw.getLocalPath(), processed.getLocalPath());
        assertNotEquals(raw.getLocalPath(), archive.getLocalPath());
        assertNotEquals(processed.getLocalPath(), archive.getLocalPath());

        // Verify S3 properties
        assertEquals("data-lake", raw.getS3Bucket());
        assertEquals("data-lake", processed.getS3Bucket());
        assertEquals("archive-bucket", archive.getS3Bucket());

        logger.info("Multiple S3 stages verified");
    }

    @Test
    public void testFileStageVsS3Stage() {
        // Create a file:// stage for comparison
        final String tempDir = System.getProperty("java.io.tmpdir") + "/test_file_stage";
        engine.execute("CREATE STAGE file_stage URL='file://" + tempDir + "'");

        final Stage fileStage = engine.getCatalog().getStage("file_stage");

        // Verify file stage is not S3 simulated
        assertFalse(fileStage.isS3Simulated());
        assertNull(fileStage.getS3Bucket());
        assertNull(fileStage.getS3Prefix());

        // Verify S3 stage is S3 simulated
        assertTrue(s3Stage.isS3Simulated());
        assertNotNull(s3Stage.getS3Bucket());
        assertNotNull(s3Stage.getS3Prefix());

        logger.info("File stage vs S3 stage differentiation verified");
    }

    private void deleteDirectory(final Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        // Children before the directory that holds them.
        if (Files.isDirectory(directory)) {
            try (DirectoryStream<Path> children = Files.newDirectoryStream(directory)) {
                for (final Path child : children) {
                    deleteDirectory(child);
                }
            }
        }
        try {
            Files.deleteIfExists(directory);
        } catch (final IOException e) {
            // Ignore
        }
    }
}
