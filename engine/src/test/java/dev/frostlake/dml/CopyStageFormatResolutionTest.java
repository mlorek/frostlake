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
import org.junit.jupiter.api.function.Executable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A COPY that writes no FILE_FORMAT reads the files by the format its stage names, and reads that name where the stage
 * lives: an unqualified name in the stage's schema, whatever schema the session is in. A name nothing resolves is
 * refused as a staged-file query over the stage refuses it, never read as the default CSV.
 */
public class CopyStageFormatResolutionTest {

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
    public void aDroppedFormatIsRefused() throws IOException {
        engine.execute("CREATE FILE FORMAT pf TYPE = CSV FIELD_DELIMITER = '|' SKIP_HEADER = 1");
        engine.execute("CREATE STAGE st FILE_FORMAT = pf");
        stage("f1.csv", "h1|h2\na|1\nb|2\n", "@st");
        engine.execute("CREATE TABLE one (a VARCHAR)");
        engine.execute("DROP FILE FORMAT pf");
        final String refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("COPY INTO one FROM @st");
            }
        }).getMessage();
        assertTrue(refused.contains("File format 'PF' does not exist or not authorized."), refused);
        assertEquals("0", answer("SELECT COUNT(*) FROM one"));
    }

    @Test
    public void anUnqualifiedNameIsReadInTheStagesSchema() throws IOException {
        engine.execute("CREATE FILE FORMAT pf TYPE = CSV FIELD_DELIMITER = '|' SKIP_HEADER = 1");
        engine.execute("CREATE STAGE st FILE_FORMAT = pf");
        stage("f1.csv", "h1|h2\na|1\nb|2\n", "@st");
        engine.execute("CREATE TABLE two (a VARCHAR, b INT)");
        engine.execute("CREATE SCHEMA other");
        engine.execute("USE SCHEMA db.other");
        engine.execute("COPY INTO db.PUBLIC.two FROM @db.PUBLIC.st");
        assertEquals("a, 1 | b, 2", answer("SELECT a, b FROM db.PUBLIC.two ORDER BY a"));
    }
}
