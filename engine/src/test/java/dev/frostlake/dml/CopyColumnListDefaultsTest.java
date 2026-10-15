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
 * A COPY's column list fills the columns it names, and each column it leaves out takes its DEFAULT, or the next
 * IDENTITY value, as an INSERT naming the same columns does; one whose DEFAULT is a sequence cannot be left out
 * (documented on COPY INTO &lt;table&gt;).
 */
public class CopyColumnListDefaultsTest {

    private DatabaseEngine engine;
    private Path internalRoot;
    private Path localDir;

    @BeforeEach
    public void setUp() throws IOException {
        internalRoot = Files.createTempDirectory("copy_defaults_root_");
        localDir = Files.createTempDirectory("copy_defaults_local_");
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
    public void leftOutColumnsTakeTheirDefaultsAndIdentity() throws IOException {
        engine.execute("CREATE TABLE d (id INT IDENTITY(1, 1), n INT, v VARCHAR, c VARCHAR DEFAULT 'def',"
            + " ts TIMESTAMP_NTZ DEFAULT CURRENT_TIMESTAMP())");
        stage("one.csv", "1,one\n");
        assertEquals("LOADED", status(engine.executeQuery(
            "COPY INTO d (n, v) FROM (SELECT $1, $2 FROM @s) FILES = ('one.csv') FILE_FORMAT = (TYPE = CSV)")));
        engine.execute("INSERT INTO d (n, v) VALUES (2, 'insert')");
        assertEquals("1, 1, one, def, true | 2, 2, insert, def, true",
            answer("SELECT id, n, v, c, ts IS NOT NULL FROM d ORDER BY n"));
    }

    @Test
    public void aPlainLoadsColumnListFillsTheRestAlike() throws IOException {
        engine.execute("CREATE TABLE d2 (n INT, v VARCHAR, c VARCHAR DEFAULT 'def' NOT NULL)");
        stage("one.csv", "1,one\n");
        assertEquals("LOADED", status(engine.executeQuery(
            "COPY INTO d2 (n, v) FROM @s FILES = ('one.csv') FILE_FORMAT = (TYPE = CSV)")));
        assertEquals("1, one, def", answer("SELECT n, v, c FROM d2"));
    }

    @Test
    public void aColumnWithNoDefaultIsNull() throws IOException {
        engine.execute("CREATE TABLE d3 (n INT, v VARCHAR, x INT)");
        stage("one.csv", "1,one\n");
        engine.executeQuery("COPY INTO d3 (v, n) FROM (SELECT $2, $1 FROM @s) FILES = ('one.csv')"
            + " FILE_FORMAT = (TYPE = CSV)");
        assertEquals("1, one, null", answer("SELECT n, v, x FROM d3"));
    }

    @Test
    public void everyLoadedRowTakesTheNextIdentity() throws IOException {
        engine.execute("CREATE TABLE d4 (id INT AUTOINCREMENT START 10 INCREMENT 5, v VARCHAR)");
        stage("three.csv", "a\nb\nc\n");
        engine.executeQuery("COPY INTO d4 (v) FROM @s FILES = ('three.csv') FILE_FORMAT = (TYPE = CSV)");
        assertEquals("10, a | 15, b | 20, c", answer("SELECT id, v FROM d4 ORDER BY id"));
    }

    @Test
    public void withoutAColumnListNoDefaultIsTaken() throws IOException {
        engine.execute("CREATE TABLE d5 (n INT, c VARCHAR DEFAULT 'def')");
        stage("short.csv", "1,\n");
        engine.executeQuery("COPY INTO d5 FROM @s FILES = ('short.csv') FILE_FORMAT = (TYPE = CSV)");
        assertEquals("1, null", answer("SELECT n, c FROM d5"));
    }

    @Test
    public void aSequenceDefaultCannotBeLeftOut() throws IOException {
        engine.execute("CREATE SEQUENCE seq1");
        engine.execute("CREATE TABLE d6 (id INT DEFAULT seq1.NEXTVAL, v VARCHAR)");
        stage("one.csv", "one\n");
        assertEquals("SQL compilation error:\nColumn ID is left out of the COPY column list, and a column left out"
            + " cannot take a sequence as its default value.",
            refusal("COPY INTO d6 (v) FROM @s FILES = ('one.csv') FILE_FORMAT = (TYPE = CSV)"));
        assertEquals("", answer("SELECT id, v FROM d6"));
    }
}
