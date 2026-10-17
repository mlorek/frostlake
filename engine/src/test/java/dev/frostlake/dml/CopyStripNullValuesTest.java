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
 * STRIP_NULL_VALUES = TRUE loads a JSON record without its null-valued object fields, at every depth, and with each
 * null array element a hole, inline or in a named format — as a staged-file query reads the same file
 * (live-verified); without it the nulls stay.
 */
public class CopyStripNullValuesTest {

    private DatabaseEngine engine;
    private Path internalRoot;
    private Path localDir;

    @BeforeEach
    public void setUp() throws IOException {
        internalRoot = Files.createTempDirectory("copy_strip_nulls_root_");
        localDir = Files.createTempDirectory("copy_strip_nulls_local_");
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

    private static final String NULLS = "{\"id\": 1, \"x\": null, \"a\": [1, null], \"o\": {\"k\": null, \"j\": 2}}\n";

    @Test
    public void nullFieldsAreDroppedAndNullElementsLeaveHoles() throws IOException {
        engine.execute("CREATE TABLE j (v VARIANT)");
        stage("nulls.json", NULLS);
        assertEquals("LOADED", status(engine.executeQuery("COPY INTO j FROM @s FILES = ('nulls.json')"
            + " FILE_FORMAT = (TYPE = JSON STRIP_NULL_VALUES = TRUE)")));
        assertEquals("[\"a\",\"id\",\"o\"], true, 2, [\"j\"], true",
            answer("SELECT OBJECT_KEYS(v), v:x IS NULL, ARRAY_SIZE(v:a), OBJECT_KEYS(v:o), v:a[1] IS NULL FROM j"));
    }

    @Test
    public void aNamedFormatStripsAlike() throws IOException {
        engine.execute("CREATE TABLE j (v VARIANT)");
        engine.execute("CREATE FILE FORMAT nonulls TYPE = JSON STRIP_NULL_VALUES = TRUE");
        stage("nulls.json", NULLS);
        engine.executeQuery("COPY INTO j FROM @s FILES = ('nulls.json') FILE_FORMAT = (FORMAT_NAME = 'nonulls')");
        assertEquals("[\"a\",\"id\",\"o\"]", answer("SELECT OBJECT_KEYS(v) FROM j"));
    }

    @Test
    public void withoutTheOptionTheNullsStay() throws IOException {
        engine.execute("CREATE TABLE j (v VARIANT)");
        stage("nulls.json", NULLS);
        engine.executeQuery("COPY INTO j FROM @s FILES = ('nulls.json') FILE_FORMAT = (TYPE = JSON)");
        assertEquals("[\"a\",\"id\",\"o\",\"x\"], false, 2",
            answer("SELECT OBJECT_KEYS(v), v:x IS NULL, ARRAY_SIZE(v:a) FROM j"));
    }

    @Test
    public void aColumnLoadedByNameSeesNoNull() throws IOException {
        engine.execute("CREATE TABLE c (id INT, x VARCHAR)");
        stage("nulls.json", NULLS);
        engine.executeQuery("COPY INTO c FROM @s FILES = ('nulls.json') FILE_FORMAT = (TYPE = JSON STRIP_NULL_VALUES"
            + " = TRUE) MATCH_BY_COLUMN_NAME = CASE_INSENSITIVE");
        assertEquals("1, null", answer("SELECT id, x FROM c"));
    }
}
