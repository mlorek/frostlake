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
 * A JSON load with MATCH_BY_COLUMN_NAME matches each column to a key as the mode says (documented): CASE_INSENSITIVE
 * takes a key in any case, the exact spelling first, and CASE_SENSITIVE only the key spelled as the column is, so a
 * record whose keys match no column loads as NULLs.
 */
public class CopyJsonMatchByColumnNameTest {

    private DatabaseEngine engine;
    private Path internalRoot;
    private Path localDir;

    @BeforeEach
    public void setUp() throws IOException {
        internalRoot = Files.createTempDirectory("copy_json_match_root_");
        localDir = Files.createTempDirectory("copy_json_match_local_");
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
    public void eachModeMatchesKeysAsItSays() throws IOException {
        engine.execute("CREATE STAGE s");
        stage("mb.json", "{\"ID\":1,\"Name\":\"a\"}\n{\"id\":2,\"name\":\"b\"}\n{\"Id\":3,\"NAME\":\"c\"}\n", "@s");
        engine.execute("CREATE TABLE ci (id INT, name VARCHAR)");
        engine.execute("COPY INTO ci FROM @s FILE_FORMAT = (TYPE = JSON) MATCH_BY_COLUMN_NAME = CASE_INSENSITIVE");
        assertEquals("1, a | 2, b | 3, c", answer("SELECT * FROM ci ORDER BY id NULLS LAST"));
        engine.execute("CREATE TABLE cs (id INT, \"Name\" VARCHAR)");
        engine.execute("COPY INTO cs FROM @s FILE_FORMAT = (TYPE = JSON) MATCH_BY_COLUMN_NAME = CASE_SENSITIVE");
        assertEquals("1, a | null, null | null, null", answer("SELECT * FROM cs ORDER BY id NULLS LAST"));
    }

    @Test
    public void caseInsensitivePrefersTheExactSpelling() throws IOException {
        engine.execute("CREATE STAGE s");
        stage("both.json", "{\"id\":7,\"ID\":8}\n", "@s");
        engine.execute("CREATE TABLE b (id INT, x INT)");
        engine.execute("COPY INTO b FROM @s FILE_FORMAT = (TYPE = JSON) MATCH_BY_COLUMN_NAME = CASE_INSENSITIVE");
        assertEquals("8, null", answer("SELECT * FROM b"));
    }
}
