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

package dev.frostlake.features;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.metastore.model.Stage;
import dev.frostlake.metastore.model.StageFile;
import dev.frostlake.metastore.model.StageType;
import dev.frostlake.storage.ResultSet;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for STAGE functionality (local filesystem staging)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class StagesTest {
    private static final Logger logger = LoggerFactory.getLogger(StagesTest.class);

    private DatabaseEngine engine;
    private Path testStageDir;

    @BeforeAll
    public void setUp() throws IOException {
        engine = new DatabaseEngine();
        // The tests point stages at local file:// directories - opt in to the affordance the
        // default config refuses (a real account refuses those URLs).
        engine.getConfig().setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "true");
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");

        // Create temporary directory for testing
        testStageDir = Files.createTempDirectory("snowflake_stage_test");

        // Create some test files
        Files.write(testStageDir.resolve("data1.csv"), "id,name,value\n1,Alice,100\n".getBytes());
        Files.write(testStageDir.resolve("data2.csv"), "id,name,value\n2,Bob,200\n".getBytes());
        Files.write(testStageDir.resolve("report.txt"), "Test report content".getBytes());

        logger.info("Test stage directory: {}", testStageDir);
    }

    @AfterAll
    public void tearDown() throws IOException {
        // Clean up test directory
        if (testStageDir != null && Files.exists(testStageDir)) {
            deleteRecursively(testStageDir);
        }

        if (engine != null) {
            engine.shutdown();
        }
    }

    /** Depth-first delete: children before the directory that holds them. */
    private void deleteRecursively(final Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> children = Files.newDirectoryStream(path)) {
                for (final Path child : children) {
                    deleteRecursively(child);
                }
            }
        }
        try {
            Files.delete(path);
        } catch (final IOException e) {
            logger.error("Failed to delete: {}", path, e);
        }
    }

    @Test
    @Order(1)
    public void testCreateStage() {
        engine.execute("CREATE STAGE my_stage URL = 'file://" + testStageDir + "'");

        final Stage stage = engine.getCatalog().getStage("my_stage");
        assertNotNull(stage);
        assertEquals("MY_STAGE", stage.getName());
        assertEquals(StageType.INTERNAL, stage.getType());
        assertEquals("file://" + testStageDir, stage.getUrl());
    }

    @Test
    @Order(2)
    public void testCreateStageWithOptions() {
        final Path csvStageDir = testStageDir.resolve("csv_stage");
        try {
            Files.createDirectories(csvStageDir);
        } catch (final IOException e) {
            fail("Failed to create CSV stage directory");
        }

        engine.execute("""
            CREATE STAGE csv_stage
            URL = 'file://%s'
            FILE_FORMAT = 'CSV'
            COMMENT = 'CSV data stage'
            """.formatted(csvStageDir));

        final Stage stage = engine.getCatalog().getStage("csv_stage");
        assertNotNull(stage);
        assertEquals("CSV", stage.getFileFormat());
        assertEquals("CSV data stage", stage.getComment());
    }

    @Test
    @Order(3)
    public void testListStage() {
        final ResultSet result = engine.executeQuery("LIST @my_stage");

        assertNotNull(result);
        assertTrue(result.getRowCount() >= 3, "Should have at least 3 files");

        // Check columns
        assertEquals(4, result.getColumnCount());
        assertEquals("name", result.getColumns().get(0).getName());
        assertEquals("size", result.getColumns().get(1).getName());
        assertEquals("md5", result.getColumns().get(2).getName());
        assertEquals("last_modified", result.getColumns().get(3).getName());

        // Check file names
        boolean foundData1 = false;
        boolean foundData2 = false;
        boolean foundReport = false;

        while (result.next()) {
            final String fileName = (String) result.getValue("name");
            if ("my_stage/data1.csv".equals(fileName)) foundData1 = true;
            if ("my_stage/data2.csv".equals(fileName)) foundData2 = true;
            if ("my_stage/report.txt".equals(fileName)) foundReport = true;
        }

        assertTrue(foundData1, "Should find data1.csv");
        assertTrue(foundData2, "Should find data2.csv");
        assertTrue(foundReport, "Should find report.txt");
    }

    @Test
    @Order(4)
    public void testListStageWithPattern() {
        final ResultSet result = engine.executeQuery("LIST @my_stage PATTERN = '.*\\.csv'");

        assertNotNull(result);
        assertTrue(result.getRowCount() >= 2, "Should have at least 2 CSV files");

        // All files should be CSV
        result.reset();
        while (result.next()) {
            final String fileName = (String) result.getValue("name");
            assertTrue(fileName.endsWith(".csv"), "File should be CSV: " + fileName);
        }
    }

    @Test
    @Order(5)
    public void testListEmptyStage() {
        final Path emptyStageDir = testStageDir.resolve("empty_stage");
        try {
            Files.createDirectories(emptyStageDir);
        } catch (final IOException e) {
            fail("Failed to create empty stage directory");
        }

        engine.execute("CREATE STAGE empty_stage URL = 'file://" + emptyStageDir + "'");
        final ResultSet result = engine.executeQuery("LIST @empty_stage");

        assertNotNull(result);
        assertEquals(0, result.getRowCount(), "Empty stage should have no files");
    }

    @Test
    @Order(6)
    public void testDropStage() {
        engine.execute("DROP STAGE csv_stage");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.getCatalog().getStage("csv_stage");
                
            }
        }, "Stage should not exist after DROP");
    }

    @Test
    @Order(7)
    public void testStageAlreadyExists() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.getCatalog().createStage("my_stage", StageType.INTERNAL,
                                               "file://" + testStageDir);
                
            }
        }, "Should not allow creating stage with duplicate name");
    }

    @Test
    @Order(8)
    public void testDropNonExistentStage() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.getCatalog().dropStage("nonexistent_stage");
                
            }
        }, "Should fail when dropping non-existent stage");
    }

    @Test
    @Order(9)
    public void testListNonExistentStage() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.getCatalog().getStage("nonexistent_stage");
                
            }
        }, "Should fail when getting non-existent stage");
    }

    @Test
    @Order(10)
    public void testStageFileOperations() throws IOException {
        final Stage stage = engine.getCatalog().getStage("my_stage");

        // Test file exists
        assertTrue(stage.fileExists("data1.csv"));
        assertFalse(stage.fileExists("nonexistent.csv"));

        // Test get file path
        final Path filePath = stage.getFilePath("data1.csv");
        assertTrue(Files.exists(filePath));

        // Test list files
        final List<StageFile> files = stage.listFiles();
        assertTrue(files.size() >= 3);

        // Test list files with pattern
        final List<StageFile> csvFiles = stage.listFiles("*.csv");
        assertTrue(csvFiles.size() >= 2);
        for (final StageFile file : csvFiles) {
            assertTrue(file.getName().endsWith(".csv"));
        }
    }

    @Test
    @Order(11)
    public void testStagePutFile() throws IOException {
        final Stage stage = engine.getCatalog().getStage("my_stage");

        // Create a test file
        final Path tempFile = Files.createTempFile("test", ".txt");
        Files.write(tempFile, "Test content".getBytes());

        // Put file into stage
        stage.putFile(tempFile);

        // Verify file exists in stage
        assertTrue(stage.fileExists(tempFile.getFileName().toString()));

        // Clean up
        Files.delete(tempFile);
    }

    @Test
    @Order(12)
    public void testStageGetFile() throws IOException {
        final Stage stage = engine.getCatalog().getStage("my_stage");

        // Get file from stage
        final Path destFile = Files.createTempFile("retrieved", ".csv");
        stage.getFile("data1.csv", destFile);

        // Verify file was retrieved
        assertTrue(Files.exists(destFile));
        assertTrue(Files.size(destFile) > 0);

        // Clean up
        Files.delete(destFile);
    }

    @Test
    @Order(13)
    public void testStageRemoveFile() throws IOException {
        final Stage stage = engine.getCatalog().getStage("my_stage");

        // Create a test file in stage
        final Path testFile = testStageDir.resolve("to_delete.txt");
        Files.write(testFile, "Will be deleted".getBytes());

        assertTrue(stage.fileExists("to_delete.txt"));

        // Remove file
        final boolean removed = stage.removeFile("to_delete.txt");
        assertTrue(removed);
        assertFalse(stage.fileExists("to_delete.txt"));

        // Try to remove non-existent file
        final boolean removedAgain = stage.removeFile("to_delete.txt");
        assertFalse(removedAgain);
    }

    @Test
    @Order(14)
    public void testMultipleStages() {
        final Path stage1Dir = testStageDir.resolve("stage1");
        final Path stage2Dir = testStageDir.resolve("stage2");

        try {
            Files.createDirectories(stage1Dir);
            Files.createDirectories(stage2Dir);

            Files.write(stage1Dir.resolve("file1.txt"), "Stage 1 data".getBytes());
            Files.write(stage2Dir.resolve("file2.txt"), "Stage 2 data".getBytes());
        } catch (final IOException e) {
            fail("Failed to create test directories");
        }

        engine.execute("CREATE STAGE stage1 URL = 'file://" + stage1Dir + "'");
        engine.execute("CREATE STAGE stage2 URL = 'file://" + stage2Dir + "'");

        // List stage1
        final ResultSet result1 = engine.executeQuery("LIST @stage1");
        assertEquals(1, result1.getRowCount());
        result1.next();
        assertEquals("stage1/file1.txt", result1.getValue("name"));

        // List stage2
        final ResultSet result2 = engine.executeQuery("LIST @stage2");
        assertEquals(1, result2.getRowCount());
        result2.next();
        assertEquals("stage2/file2.txt", result2.getValue("name"));

        // Verify stages are independent
        final Stage s1 = engine.getCatalog().getStage("stage1");
        final Stage s2 = engine.getCatalog().getStage("stage2");
        assertNotEquals(s1.getUrl(), s2.getUrl());
    }

    @Test
    @Order(15)
    public void testGetAllStages() {
        final List<Stage> stages = engine.getCatalog().getAllStages();

        // Should have my_stage, empty_stage, stage1, stage2
        assertTrue(stages.size() >= 4);

        boolean foundMyStage = false;
        for (final Stage stage : stages) {
            if ("MY_STAGE".equals(stage.getName())) {
                foundMyStage = true;
                break;
            }
        }
        assertTrue(foundMyStage);
    }

    @Test
    public void testAlterStageSetProperties() {
        engine.execute("CREATE STAGE alter_stage_test URL = 'file://" + testStageDir + "'");

        engine.execute("ALTER STAGE alter_stage_test SET COMMENT = 'altered comment'");
        assertEquals("altered comment", engine.getCatalog().getStage("alter_stage_test").getComment());

        engine.execute("ALTER STAGE alter_stage_test SET FILE_FORMAT = 'JSON'");
        assertEquals("JSON", engine.getCatalog().getStage("alter_stage_test").getFileFormat());

        // SET URL repoints the stage (and recomputes its local directory).
        engine.execute("ALTER STAGE alter_stage_test SET URL = 'file://" + testStageDir + "/sub'");
        assertEquals("file://" + testStageDir + "/sub",
            engine.getCatalog().getStage("alter_stage_test").getUrl());
    }
}
