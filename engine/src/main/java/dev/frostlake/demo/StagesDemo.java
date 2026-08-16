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

package dev.frostlake.demo;

import dev.frostlake.config.EngineConfig;
import dev.frostlake.metastore.model.Stage;
import dev.frostlake.storage.ResultSet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Demonstration of STAGE functionality - Local filesystem staging
 */
public class StagesDemo extends AbstractDemo {

    public static void main(final String[] args) {
        new StagesDemo().execute();
    }

    @Override
    protected String getDemoTitle() {
        return "FROSTLAKE SQL ENGINE - STAGES DEMO";
    }

    @Override
    protected void runDemo() throws Exception {

            // The demo's stages point at local file:// directories — opt in to the affordance the
            // default config refuses (a real account refuses those URLs).
            engine.getConfig().setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "true");

            // Create a temporary directory for our stage
            final Path stageDir = Files.createTempDirectory("demo_stage");
            System.out.println("Created temporary stage directory: " + stageDir);

            // Create some sample data files
            createSampleFiles(stageDir);

            // ==================== DEMO 1: CREATE STAGE ====================
            System.out.println("\n----- DEMO 1: CREATE STAGE -----");
            engine.execute("CREATE STAGE my_data_stage URL = 'file://" + stageDir + "'");
            System.out.println("✓ Created stage: my_data_stage");

            // ==================== DEMO 2: LIST STAGE (ALL FILES) ====================
            System.out.println("\n----- DEMO 2: LIST ALL FILES IN STAGE -----");
            final ResultSet result = engine.executeQuery("LIST @my_data_stage");
            System.out.println("Files in stage:");
            printResultSet(result);

            // ==================== DEMO 3: LIST WITH PATTERN ====================
            System.out.println("\n----- DEMO 3: LIST CSV FILES ONLY -----");
            final ResultSet csvResult = engine.executeQuery("LIST @my_data_stage PATTERN = '*.csv'");
            System.out.println("CSV files:");
            printResultSet(csvResult);

            // ==================== DEMO 4: CREATE MULTIPLE STAGES ====================
            System.out.println("\n----- DEMO 4: CREATE MULTIPLE STAGES -----");

            final Path csvStageDir = stageDir.resolve("csv_data");
            final Path jsonStageDir = stageDir.resolve("json_data");
            Files.createDirectories(csvStageDir);
            Files.createDirectories(jsonStageDir);

            Files.write(csvStageDir.resolve("users.csv"), "id,name,email\n1,Alice,alice@example.com\n".getBytes());
            Files.write(jsonStageDir.resolve("data.json"), "{\"key\": \"value\"}".getBytes());

            engine.execute("CREATE STAGE csv_stage " +
                "URL = 'file://" + csvStageDir + "' " +
                "FILE_FORMAT = 'CSV' " +
                "COMMENT = 'Stage for CSV files'");

            engine.execute("CREATE STAGE json_stage " +
                "URL = 'file://" + jsonStageDir + "' " +
                "FILE_FORMAT = 'JSON' " +
                "COMMENT = 'Stage for JSON files'");

            System.out.println("✓ Created csv_stage");
            System.out.println("✓ Created json_stage");

            // ==================== DEMO 5: LIST DIFFERENT STAGES ====================
            System.out.println("\n----- DEMO 5: LIST FILES IN DIFFERENT STAGES -----");

            System.out.println("\nCSV Stage:");
            final ResultSet csvStageResult = engine.executeQuery("LIST @csv_stage");
            printResultSet(csvStageResult);

            System.out.println("JSON Stage:");
            final ResultSet jsonStageResult = engine.executeQuery("LIST @json_stage");
            printResultSet(jsonStageResult);

            // ==================== DEMO 6: STAGE METADATA ====================
            System.out.println("\n----- DEMO 6: STAGE METADATA -----");
            final Stage csvStage = engine.getCatalog().getStage("csv_stage");
            System.out.println("Stage Name: " + csvStage.getName());
            System.out.println("Stage Type: " + csvStage.getType());
            System.out.println("Stage URL: " + csvStage.getUrl());
            System.out.println("File Format: " + csvStage.getFileFormat());
            System.out.println("Comment: " + csvStage.getComment());
            System.out.println("Created At: " + csvStage.getCreatedAt());

            // ==================== DEMO 7: PROGRAMMATIC FILE OPERATIONS ====================
            System.out.println("\n----- DEMO 7: PROGRAMMATIC FILE OPERATIONS -----");
            final Stage myStage = engine.getCatalog().getStage("my_data_stage");

            // Check if file exists
            final boolean exists = myStage.fileExists("customers.csv");
            System.out.println("customers.csv exists: " + exists);

            // Put a new file
            final Path newFile = Files.createTempFile("upload", ".txt");
            Files.write(newFile, "This file will be uploaded to stage".getBytes());
            myStage.putFile(newFile);
            System.out.println("✓ Uploaded file: " + newFile.getFileName());

            // List files again
            System.out.println("\nFiles after upload:");
            final ResultSet afterUpload = engine.executeQuery("LIST @my_data_stage");
            printResultSet(afterUpload);

            // Clean up temp file
            Files.delete(newFile);

            // ==================== DEMO 8: REAL-WORLD SCENARIO ====================
            System.out.println("\n----- DEMO 8: REAL-WORLD ETL SCENARIO -----");

            // Create database and table
            engine.execute("CREATE DATABASE etl_demo");
            engine.execute("USE DATABASE etl_demo");
            engine.execute("USE SCHEMA PUBLIC");
            engine.execute("CREATE TABLE customers (final id INTEGER, final name VARCHAR, final region VARCHAR)");
            System.out.println("✓ Created database and table");

            // Create staging area
            final Path etlStageDir = stageDir.resolve("etl_stage");
            Files.createDirectories(etlStageDir);
            Files.write(etlStageDir.resolve("batch1.csv"),
                "id,name,region\n1,Alice,East\n2,Bob,West\n".getBytes());
            Files.write(etlStageDir.resolve("batch2.csv"),
                "id,name,region\n3,Charlie,North\n4,Diana,South\n".getBytes());

            engine.execute("CREATE STAGE etl_stage URL = 'file://" + etlStageDir + "'");
            System.out.println("✓ Created ETL stage with data files");

            // List files to process
            System.out.println("\nFiles ready for loading:");
            final ResultSet etlFiles = engine.executeQuery("LIST @etl_stage");
            printResultSet(etlFiles);

            System.out.println("\n✓ In a real ETL pipeline, you would:");
            System.out.println("  1. LIST @etl_stage to discover files");
            System.out.println("  2. COPY INTO customers FROM @etl_stage");
            System.out.println("  3. Process each file and load into table");
            System.out.println("  4. Archive or remove processed files");

            // ==================== DEMO 9: DROP STAGE ====================
            System.out.println("\n----- DEMO 9: DROP STAGE -----");
            engine.execute("DROP STAGE json_stage");
            System.out.println("✓ Dropped json_stage");

            // ==================== DEMO 10: LIST ALL STAGES ====================
            System.out.println("\n----- DEMO 10: LIST ALL STAGES -----");
            final var allStages = engine.getCatalog().getAllStages();
            System.out.println("Total stages: " + allStages.size());
            for (final Stage stage : allStages) {
                System.out.println("  - " + stage.getName() + " (" + stage.getType() + ")");
            }

            // ==================== SUMMARY ====================
            System.out.println("\n========================================");
            System.out.println("DEMO COMPLETE - STAGES FEATURES");
            System.out.println("========================================");
            System.out.println("✓ CREATE STAGE with local filesystem");
            System.out.println("✓ LIST @stage_name");
            System.out.println("✓ LIST with PATTERN filtering");
            System.out.println("✓ Multiple stages with different formats");
            System.out.println("✓ Stage metadata (URL, format, comments)");
            System.out.println("✓ Programmatic file operations");
            System.out.println("✓ Real-world ETL scenario");
            System.out.println("✓ DROP STAGE");
            System.out.println("✓ List all stages");

            // Clean up
            cleanup(stageDir);
    }

    private void createSampleFiles(final Path stageDir) throws IOException {
        // Create CSV files
        Files.write(stageDir.resolve("customers.csv"),
            "id,name,balance\n1,Alice,1000\n2,Bob,2000\n".getBytes());

        Files.write(stageDir.resolve("orders.csv"),
            "order_id,customer_id,amount\n101,1,500\n102,2,750\n".getBytes());

        // Create text file
        Files.write(stageDir.resolve("readme.txt"),
            "Sample data files for Frostlake SQL Engine staging demo".getBytes());

        printSuccess("Created sample data files");
    }

    @Override
    protected void printResultSet(final ResultSet result) {
        if (result.getRowCount() == 0) {
            System.out.println("  (no files)");
            return;
        }

        // Print header
        System.out.printf("  %-30s %10s %10s %-30s%n",
            "Name", "Size", "MD5", "Last Modified");
        System.out.println("  " + "-".repeat(85));

        // Print rows
        result.reset();
        while (result.next()) {
            final String name = (String) result.getValue("name");
            final Long size = (Long) result.getValue("size");
            final String md5 = (String) result.getValue("md5");
            final String lastModified = (String) result.getValue("last_modified");

            // Truncate last_modified for display
            final String lastModifiedShort = lastModified.length() > 30 ?
                lastModified.substring(0, 27) + "..." : lastModified;

            System.out.printf("  %-30s %10d %10s %-30s%n",
                name, size, md5.isEmpty() ? "-" : md5, lastModifiedShort);
        }
    }

    private void cleanup(final Path dir) {
        try {
            // Collect paths and sort them (deepest first for deletion)
            final List<Path> paths = new ArrayList<>();
            final Iterator<Path> iterator = Files.walk(dir).iterator();
            while (iterator.hasNext()) {
                paths.add(iterator.next());
            }

            // Sort in reverse order (deepest paths first)
            Collections.sort(paths, new Comparator<Path>() {
                @Override
                public int compare(final Path a, final Path b) {
                    return b.compareTo(a);
                }
            });

            // Delete each path
            for (final Path path : paths) {
                try {
                    Files.delete(path);
                } catch (final IOException e) {
                    // Ignore
                }
            }
            printSuccess("\nCleaned up temporary files");
        } catch (final IOException e) {
            System.err.println("Warning: Could not clean up temporary directory");
        }
    }
}
