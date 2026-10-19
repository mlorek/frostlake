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
 * TRUNCATECOLUMNS = TRUE, and its inverse ENFORCE_LENGTH = FALSE, cut a loaded text longer than its column to the
 * column's length, counted in characters; the default refuses it. A CSV field and a JSON string loaded into a column
 * of its own alike, a validation included (documented on COPY INTO &lt;table&gt;).
 */
public class CopyTruncateColumnsTest {

    private DatabaseEngine engine;
    private Path internalRoot;
    private Path localDir;

    @BeforeEach
    public void setUp() throws IOException {
        internalRoot = Files.createTempDirectory("copy_truncate_root_");
        localDir = Files.createTempDirectory("copy_truncate_local_");
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
    public void truncateColumnsCutsALongText() throws IOException {
        engine.execute("CREATE TABLE t5 (id INT, v VARCHAR(5))");
        stage("long.csv", "1,abcdefghij\n");
        assertEquals("LOADED", status(engine.executeQuery(
            "COPY INTO t5 FROM @s FILES = ('long.csv') FILE_FORMAT = (TYPE = CSV) TRUNCATECOLUMNS = TRUE")));
        assertEquals("1, abcde", answer("SELECT id, v FROM t5"));
    }

    @Test
    public void enforceLengthFalseCutsALongText() throws IOException {
        engine.execute("CREATE TABLE t5 (id INT, v VARCHAR(5))");
        stage("long.csv", "1,abcdefghij\n");
        assertEquals("LOADED", status(engine.executeQuery(
            "COPY INTO t5 FROM @s FILES = ('long.csv') FILE_FORMAT = (TYPE = CSV) ENFORCE_LENGTH = FALSE")));
        assertEquals("LOADED", status(engine.executeQuery("COPY INTO t5 FROM @s FILES = ('long.csv')"
            + " FILE_FORMAT = (TYPE = CSV) TRUNCATECOLUMNS = TRUE ENFORCE_LENGTH = FALSE FORCE = TRUE")));
        assertEquals("1, abcde | 1, abcde", answer("SELECT id, v FROM t5"));
    }

    @Test
    public void theDefaultAndItsSpellingsRefuseALongText() throws IOException {
        engine.execute("CREATE TABLE t5 (id INT, v VARCHAR(5))");
        stage("long.csv", "1,abcdefghij\n");
        final String tooLong = "User character length limit (5) exceeded by string 'abcdefghij'";
        assertEquals(tooLong, firstError(engine.executeQuery("COPY INTO t5 FROM @s FILES = ('long.csv')"
            + " FILE_FORMAT = (TYPE = CSV) ON_ERROR = CONTINUE")));
        assertEquals(tooLong, firstError(engine.executeQuery("COPY INTO t5 FROM @s FILES = ('long.csv')"
            + " FILE_FORMAT = (TYPE = CSV) TRUNCATECOLUMNS = FALSE ON_ERROR = CONTINUE FORCE = TRUE")));
        assertEquals(tooLong, firstError(engine.executeQuery("COPY INTO t5 FROM @s FILES = ('long.csv')"
            + " FILE_FORMAT = (TYPE = CSV) ENFORCE_LENGTH = TRUE ON_ERROR = CONTINUE FORCE = TRUE")));
        assertEquals("", answer("SELECT id, v FROM t5"));
    }

    @Test
    public void aWidthCountsCharactersNotBytes() throws IOException {
        engine.execute("CREATE TABLE t2 (id INT, v VARCHAR(2), c CHAR(3))");
        stage("wide.csv", "1,\u00e9\u00e9\u00e9\u00e9,abcdef\n");
        engine.executeQuery(
            "COPY INTO t2 FROM @s FILES = ('wide.csv') FILE_FORMAT = (TYPE = CSV) TRUNCATECOLUMNS = TRUE");
        assertEquals("1, \u00e9\u00e9, abc", answer("SELECT id, v, c FROM t2"));
    }

    @Test
    public void aJsonStringInAColumnOfItsOwnIsCutToo() throws IOException {
        engine.execute("CREATE TABLE tj (id INT, v VARCHAR(3))");
        stage("rows.json", "{\"ID\": 1, \"V\": \"abcdefg\"}\n");
        assertEquals("LOADED", status(engine.executeQuery("COPY INTO tj FROM @s FILES = ('rows.json')"
            + " FILE_FORMAT = (TYPE = JSON) MATCH_BY_COLUMN_NAME = CASE_INSENSITIVE TRUNCATECOLUMNS = TRUE")));
        assertEquals("1, abc", answer("SELECT id, v FROM tj"));
    }

    @Test
    public void aValidationTruncatesAsTheLoadWould() throws IOException {
        engine.execute("CREATE TABLE t5 (id INT, v VARCHAR(5))");
        stage("long.csv", "1,abcdefghij\n");
        assertEquals(0, engine.executeQuery("COPY INTO t5 FROM @s FILES = ('long.csv') FILE_FORMAT = (TYPE = CSV)"
            + " TRUNCATECOLUMNS = TRUE VALIDATION_MODE = RETURN_ERRORS").getRows().size());
        assertEquals(1, engine.executeQuery("COPY INTO t5 FROM @s FILES = ('long.csv') FILE_FORMAT = (TYPE = CSV)"
            + " VALIDATION_MODE = RETURN_ERRORS").getRows().size());
    }

    @Test
    public void aBinaryIsNotCut() throws IOException {
        engine.execute("CREATE TABLE tb (b BINARY(2))");
        stage("wide.csv", "ABCDEF\n");
        assertEquals("LOAD_FAILED", status(engine.executeQuery("COPY INTO tb FROM @s FILES = ('wide.csv')"
            + " FILE_FORMAT = (TYPE = CSV) TRUNCATECOLUMNS = TRUE ON_ERROR = CONTINUE")));
    }
}
