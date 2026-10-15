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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Every file a COPY that ran to completion read is recorded, errors or not, and INFORMATION_SCHEMA.LOAD_HISTORY and
 * COPY_HISTORY report it; an aborted COPY records none, and a table's records go when it is truncated, replaced or
 * dropped (documented).
 */
public class LoadHistoryTest {

    private DatabaseEngine engine;
    private Path internalRoot;
    private Path localDir;

    @BeforeEach
    public void setUp() throws IOException {
        internalRoot = Files.createTempDirectory("load_history_root_");
        localDir = Files.createTempDirectory("load_history_local_");
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_STAGE_INTERNAL_LOCAL_ROOT, internalRoot.toString());
        cfg.setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "true");
        engine = new DatabaseEngine(cfg);
        engine.execute("CREATE DATABASE db");
        engine.execute("USE SCHEMA db.PUBLIC");
        engine.execute("CREATE STAGE s");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
        deleteRecursively(internalRoot.toFile());
        deleteRecursively(localDir.toFile());
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

    /** PUT a file written outside every stage root onto stage {@code s}. */
    private void stage(final String fileName, final byte[] content) throws IOException {
        final Path file = localDir.resolve(fileName);
        Files.write(file, content);
        engine.executeQuery("PUT file://" + file + " @s AUTO_COMPRESS=FALSE");
    }

    private void stage(final String fileName, final String content) throws IOException {
        stage(fileName, content.getBytes(StandardCharsets.UTF_8));
    }

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String answer(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? " | " : "");
            for (int i = 0; i < row.getValues().size(); i++) {
                out.append(i > 0 ? ", " : "").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    /** The per-file status of a one-file COPY. */
    private static String status(final ResultSet result) {
        return String.valueOf(result.getRows().get(0).getValue(1));
    }

    /** The first error of a one-file COPY. */
    private static String firstError(final ResultSet result) {
        return String.valueOf(result.getRows().get(0).getValue(6));
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        return refused.getMessage();
    }

    private static final String RANGE = "START_TIME => DATEADD(hour, -1, CURRENT_TIMESTAMP())";

    @Test
    public void aLoadIsInBothHistories() throws IOException {
        engine.execute("CREATE TABLE h (id INT, v VARCHAR)");
        stage("one.csv", "1,one\n");
        engine.executeQuery("COPY INTO h FROM @s FILE_FORMAT = (TYPE = CSV)");
        engine.executeQuery("COPY INTO h FROM @s FILE_FORMAT = (TYPE = CSV)");
        assertEquals("PUBLIC, s/one.csv, H, LOADED, 1, 1, 0", answer("SELECT SCHEMA_NAME, FILE_NAME, TABLE_NAME,"
            + " STATUS, ROW_COUNT, ROW_PARSED, ERROR_COUNT FROM INFORMATION_SCHEMA.LOAD_HISTORY"));
        assertEquals("s/one.csv, @s, Loaded, 1, 6, DB, PUBLIC, H, null", answer("SELECT FILE_NAME, STAGE_LOCATION,"
            + " STATUS, ROW_COUNT, FILE_SIZE, TABLE_CATALOG_NAME, TABLE_SCHEMA_NAME, TABLE_NAME, PIPE_NAME"
            + " FROM TABLE(INFORMATION_SCHEMA.COPY_HISTORY(TABLE_NAME => 'h', " + RANGE + "))"));
        assertEquals("1", answer("SELECT COUNT(*) FROM INFORMATION_SCHEMA.LOAD_HISTORY"
            + " WHERE LAST_LOAD_TIME <= CURRENT_TIMESTAMP()"));
    }

    @Test
    public void aPartialLoadAndItsFirstErrorAreRecorded() throws IOException {
        engine.execute("CREATE TABLE h (id INT, v VARCHAR)");
        stage("mixed.csv", "1,one\nx,two\n");
        engine.executeQuery("COPY INTO h FROM @s FILE_FORMAT = (TYPE = CSV) ON_ERROR = CONTINUE");
        assertEquals("PARTIALLY LOADED, 1, 2, 1, 2", answer("SELECT STATUS, ROW_COUNT, ROW_PARSED, ERROR_COUNT,"
            + " FIRST_ERROR_LINE_NUMBER FROM INFORMATION_SCHEMA.LOAD_HISTORY"));
        assertEquals("Partially loaded, Numeric value 'x' is not recognized", answer("SELECT STATUS,"
            + " FIRST_ERROR_MESSAGE FROM TABLE(INFORMATION_SCHEMA.COPY_HISTORY('H',"
            + " DATEADD(hour, -1, CURRENT_TIMESTAMP())))"));
    }

    @Test
    public void anAbortedCopyRecordsNothing() throws IOException {
        engine.execute("CREATE TABLE h (id INT, v VARCHAR)");
        stage("bad.csv", "x,one\n");
        refusal("COPY INTO h FROM @s FILE_FORMAT = (TYPE = CSV)");
        assertEquals("0", answer("SELECT COUNT(*) FROM INFORMATION_SCHEMA.LOAD_HISTORY"));
    }

    @Test
    public void truncateReplaceAndDropForgetATablesLoads() throws IOException {
        engine.execute("CREATE TABLE h (id INT, v VARCHAR)");
        engine.execute("CREATE TABLE k (id INT, v VARCHAR)");
        stage("one.csv", "1,one\n");
        engine.executeQuery("COPY INTO h FROM @s FILE_FORMAT = (TYPE = CSV)");
        engine.executeQuery("COPY INTO k FROM @s FILE_FORMAT = (TYPE = CSV)");
        engine.execute("TRUNCATE TABLE h");
        assertEquals("K", answer("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.LOAD_HISTORY"));
        engine.executeQuery("COPY INTO h FROM @s FILE_FORMAT = (TYPE = CSV)");
        engine.execute("CREATE OR REPLACE TABLE k (id INT, v VARCHAR)");
        assertEquals("H", answer("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.LOAD_HISTORY"));
        engine.execute("DROP TABLE h");
        assertEquals("0", answer("SELECT COUNT(*) FROM INFORMATION_SCHEMA.LOAD_HISTORY"));
    }

    @Test
    public void copyHistoryReadsItsRange() throws IOException {
        engine.execute("CREATE TABLE h (id INT, v VARCHAR)");
        stage("one.csv", "1,one\n");
        engine.executeQuery("COPY INTO h FROM @s FILE_FORMAT = (TYPE = CSV)");
        assertEquals("0", answer("SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.COPY_HISTORY(TABLE_NAME => 'H',"
            + " START_TIME => DATEADD(hour, -2, CURRENT_TIMESTAMP()), END_TIME => DATEADD(hour, -1,"
            + " CURRENT_TIMESTAMP())))"));
        assertEquals("1", answer("SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.COPY_HISTORY("
            + "TABLE_NAME => 'DB.PUBLIC.H', " + RANGE + "))"));
        assertEquals("0", answer("SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.COPY_HISTORY(TABLE_NAME => 'H',"
            + " START_TIME => DATEADD(hour, 1, CURRENT_TIMESTAMP())))"));
    }
}
