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
 * A stage whose quoted name, or whose schema's quoted name, holds a dot takes files and gives them back like any other:
 * PUT, LIST, a staged-file query, an unload into it and a load from it all reach the one stage.
 */
public class DottedStageFilesTest {

    private DatabaseEngine engine;
    private Path internalRoot;
    private Path localDir;

    @BeforeEach
    public void setUp() throws IOException {
        internalRoot = Files.createTempDirectory("dotted_stage_files_root_");
        localDir = Files.createTempDirectory("dotted_stage_files_local_");
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

    @Test
    public void aDottedStageTakesAndGivesFiles() throws IOException {
        engine.execute("CREATE STAGE \"s.dotted\"");
        final Path file = localDir.resolve("one.csv");
        Files.write(file, "1,one\n".getBytes(StandardCharsets.UTF_8));
        engine.executeQuery("PUT file://" + file + " @\"s.dotted\" AUTO_COMPRESS=FALSE");
        assertEquals("s.dotted/one.csv",
            engine.executeQuery("LIST @\"s.dotted\"").getRows().get(0).getValue(0));
        assertEquals("1, one", answer("SELECT $1, $2 FROM @\"s.dotted\""));
        engine.executeQuery("COPY INTO @\"s.dotted\"/u FROM (SELECT 2, 'two')"
            + " FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE");
        engine.execute("CREATE TABLE t (id INT, v VARCHAR)");
        assertEquals("LOADED", status(engine.executeQuery("COPY INTO t FROM @\"s.dotted\"/u"
            + " FILE_FORMAT = (TYPE = CSV)")));
        assertEquals("2, two", answer("SELECT id, v FROM t"));
    }

    @Test
    public void aStageOfADottedSchemaTakesFiles() throws IOException {
        engine.execute("CREATE SCHEMA \"S.A\"");
        engine.execute("CREATE STAGE \"S.A\".c");
        final Path file = localDir.resolve("one.csv");
        Files.write(file, "1,one\n".getBytes(StandardCharsets.UTF_8));
        engine.executeQuery("PUT file://" + file + " @\"S.A\".c AUTO_COMPRESS=FALSE");
        assertEquals("1, one", answer("SELECT $1, $2 FROM @\"S.A\".c"));
    }
}
