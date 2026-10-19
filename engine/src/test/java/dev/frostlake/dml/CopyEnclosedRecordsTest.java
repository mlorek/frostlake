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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A COPY load reads a delimited file's records as a query over the stage reads them. A field is enclosed only when it
 * starts with the enclosure, and a line break inside it, a doubled enclosure or an ESCAPEd character there ends
 * nothing. A quote inside an unenclosed field is a character of the value. Both hold under a RECORD_DELIMITER other
 * than a line break, and the lines a record spans count toward the line a later record's error names.
 */
public class CopyEnclosedRecordsTest {

    private DatabaseEngine engine;
    private Path internalRoot;
    private Path localDir;

    @BeforeEach
    public void setUp() throws IOException {
        internalRoot = Files.createTempDirectory("copy_enclosed_records_root_");
        localDir = Files.createTempDirectory("copy_enclosed_records_local_");
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_STAGE_INTERNAL_LOCAL_ROOT, internalRoot.toString());
        cfg.setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "true");
        engine = new DatabaseEngine(cfg);
        engine.execute("CREATE DATABASE db");
        engine.execute("USE SCHEMA db.PUBLIC");
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

    /** PUT a file written outside every stage root onto a stage. */
    private void stage(final String fileName, final String content, final String stage) throws IOException {
        final Path file = localDir.resolve(fileName);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        engine.executeQuery("PUT file://" + file + " " + stage + " AUTO_COMPRESS=FALSE");
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

    @Test
    public void aLineBreakInsideAnEnclosedFieldStaysInTheValue() throws IOException {
        engine.execute("CREATE STAGE s");
        engine.execute("CREATE FILE FORMAT ff TYPE = CSV FIELD_OPTIONALLY_ENCLOSED_BY = '\"'");
        engine.execute("CREATE TABLE t (id INT, s VARCHAR)");
        stage("ml.csv", "1,\"two\nlines\"\n2,ok\n", "@s");
        assertEquals("1, two\nlines | 2, ok", answer("SELECT $1, $2 FROM @s (FILE_FORMAT => 'ff') ORDER BY 1"));
        assertEquals("s/ml.csv, LOADED, 2, 2",
            copyStatus("COPY INTO t FROM @s FILE_FORMAT = (FORMAT_NAME = 'ff') ON_ERROR = CONTINUE"));
        assertEquals("1, two\nlines | 2, ok", answer("SELECT id, s FROM t ORDER BY id"));
        engine.execute("CREATE TABLE t2 (id INT, s VARCHAR)");
        engine.execute("COPY INTO t2 FROM @s FILE_FORMAT = (TYPE = CSV FIELD_OPTIONALLY_ENCLOSED_BY = '\"')");
        assertEquals("1, two\nlines | 2, ok", answer("SELECT id, s FROM t2 ORDER BY id"));
    }

    @Test
    public void anEscapedQuoteAndAQuoteInAnUnenclosedFieldUnderARecordDelimiter() throws IOException {
        engine.execute("CREATE STAGE s");
        engine.execute("CREATE FILE FORMAT ff TYPE = CSV RECORD_DELIMITER = ';' FIELD_OPTIONALLY_ENCLOSED_BY = '\"' "
            + "ESCAPE = '\\\\'");
        engine.execute("CREATE TABLE t (id INT, s VARCHAR)");
        stage("rd.csv", "1,\"a\\\";b\";2,x\"y;3,z", "@s");
        assertEquals("1, a\";b | 2, x\"y | 3, z", answer("SELECT $1, $2 FROM @s (FILE_FORMAT => 'ff') ORDER BY 1"));
        assertEquals("s/rd.csv, LOADED, 3, 3",
            copyStatus("COPY INTO t FROM @s FILE_FORMAT = (FORMAT_NAME = 'ff') ON_ERROR = CONTINUE"));
        assertEquals("1, a\";b | 2, x\"y | 3, z", answer("SELECT id, s FROM t ORDER BY id"));
    }

    @Test
    public void aLaterRecordsErrorNamesItsOwnLine() throws IOException {
        engine.execute("CREATE STAGE s");
        engine.execute("CREATE TABLE t (id INT, s VARCHAR)");
        stage("ln.csv", "1,\"a\nb\"\nx,c\n", "@s");
        final Row result = engine.executeQuery("COPY INTO t FROM @s FILE_FORMAT = (TYPE = CSV "
            + "FIELD_OPTIONALLY_ENCLOSED_BY = '\"') ON_ERROR = CONTINUE").getRows().get(0);
        assertEquals("PARTIALLY_LOADED", String.valueOf(result.getValue(1)));
        assertEquals("3", String.valueOf(result.getValue(7)));
        assertEquals("1, a\nb", answer("SELECT id, s FROM t ORDER BY id"));
    }

    /** A COPY's first result row: the file, its status, the rows parsed and the rows loaded. */
    private String copyStatus(final String copy) {
        final Row row = engine.executeQuery(copy).getRows().get(0);
        return row.getValue(0) + ", " + row.getValue(1) + ", " + row.getValue(2) + ", " + row.getValue(3);
    }
}
