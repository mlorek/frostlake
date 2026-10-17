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
 * A COPY INTO a table that writes no FILE_FORMAT reads its files by the format their stage keeps — a named stage's
 * FILE_FORMAT, written out or naming a file format, and a table stage's STAGE_FILE_FORMAT — as a query over the
 * stage reads them (documented: the COPY statement's options first, then the stage definition's, then the table
 * definition's). A FILE_FORMAT the statement writes replaces the stage's whole: its options do not add up with the
 * stage's.
 */
public class CopyStageFileFormatTest {

    private DatabaseEngine engine;
    private Path internalRoot;
    private Path localDir;

    @BeforeEach
    public void setUp() throws IOException {
        internalRoot = Files.createTempDirectory("copy_stage_format_root_");
        localDir = Files.createTempDirectory("copy_stage_format_local_");
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
    public void aStagesSkipHeaderSkipsTheHeaderRow() throws IOException {
        engine.execute("CREATE STAGE st FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");
        stage("f3.csv", "h1,h2\na,1\nb,2\n", "@st");
        engine.execute("CREATE TABLE t1 (a VARCHAR, b VARCHAR)");
        engine.execute("COPY INTO t1 FROM @st");
        assertEquals("a, 1 | b, 2", answer("SELECT * FROM t1 ORDER BY a"));
    }

    @Test
    public void aTableStagesFormatSplitsItsFiles() throws IOException {
        engine.execute("CREATE TABLE tt (a VARCHAR, b INT) "
            + "STAGE_FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = '|' SKIP_HEADER = 1)");
        stage("f1.csv", "h1|h2\na|1\nb|2\n", "@%tt");
        engine.execute("COPY INTO tt");
        assertEquals("a, 1 | b, 2", answer("SELECT * FROM tt ORDER BY a"));
        engine.execute("CREATE TABLE tt2 (a VARCHAR, b INT) "
            + "STAGE_FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = '|' SKIP_HEADER = 1)");
        stage("f2.csv", "h1|h2\nc|3\n", "@%tt2");
        engine.execute("COPY INTO tt2 FROM @%tt2");
        assertEquals("c, 3", answer("SELECT * FROM tt2"));
    }

    @Test
    public void aStageNamingAFileFormatReadsByIt() throws IOException {
        engine.execute("CREATE FILE FORMAT ff TYPE = CSV FIELD_DELIMITER = ';' SKIP_HEADER = 1 NULL_IF = ('nil')");
        engine.execute("CREATE STAGE st2 FILE_FORMAT = (FORMAT_NAME = 'ff')");
        stage("f4.csv", "h1;h2\nc;3\nd;nil\n", "@st2");
        engine.execute("CREATE TABLE t2 (a VARCHAR, b VARCHAR)");
        engine.execute("COPY INTO t2 FROM @st2");
        assertEquals("c, 3 | d, null", answer("SELECT * FROM t2 ORDER BY a"));
    }

    @Test
    public void aTransformingCopyReadsByTheStagesFormat() throws IOException {
        engine.execute("CREATE STAGE st3 FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = '|' SKIP_HEADER = 1)");
        stage("f5.csv", "h1|h2\ne|5\n", "@st3");
        engine.execute("CREATE TABLE t3 (b VARCHAR, a VARCHAR)");
        engine.execute("COPY INTO t3 FROM (SELECT $2, $1 FROM @st3)");
        assertEquals("5, e", answer("SELECT * FROM t3"));
    }

    @Test
    public void theStatementsFileFormatReplacesTheStagesWhole() throws IOException {
        engine.execute("CREATE STAGE st FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");
        stage("f3.csv", "h1,h2\na,1\nb,2\n", "@st");
        engine.execute("CREATE TABLE t1 (a VARCHAR, b VARCHAR)");
        engine.execute("COPY INTO t1 FROM @st FILE_FORMAT = (TYPE = CSV)");
        assertEquals("a, 1 | b, 2 | h1, h2", answer("SELECT * FROM t1 ORDER BY a"));
    }
}
