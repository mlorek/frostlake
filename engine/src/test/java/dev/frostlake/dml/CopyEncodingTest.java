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
 * A delimited file is read in the character set its format's ENCODING names, UTF8 by default, and a byte sequence that
 * set cannot read refuses its record unless REPLACE_INVALID_CHARACTERS = TRUE turns it into U+FFFD (documented on COPY
 * INTO &lt;table&gt;).
 */
public class CopyEncodingTest {

    private DatabaseEngine engine;
    private Path internalRoot;
    private Path localDir;

    @BeforeEach
    public void setUp() throws IOException {
        internalRoot = Files.createTempDirectory("copy_encoding_root_");
        localDir = Files.createTempDirectory("copy_encoding_local_");
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

    /** {@code 1,caf\u00e9} in ISO-8859-1: the last byte, 0xE9, is no UTF-8. */
    private static final byte[] LATIN1 = {'1', ',', 'c', 'a', 'f', (byte) 0xE9, '\n'};

    @Test
    public void anIso88591FileLoadsItsAccents() throws IOException {
        engine.execute("CREATE TABLE l (id INT, v VARCHAR)");
        stage("latin1.csv", LATIN1);
        assertEquals("LOADED", status(engine.executeQuery("COPY INTO l FROM @s FILES = ('latin1.csv')"
            + " FILE_FORMAT = (TYPE = CSV ENCODING = 'ISO88591')")));
        assertEquals("caf\u00e9, 636166C3A9", answer("SELECT v, HEX_ENCODE(v) FROM l"));
    }

    @Test
    public void aNamedFormatsEncodingReadsTheFile() throws IOException {
        engine.execute("CREATE TABLE l (id INT, v VARCHAR)");
        engine.execute("CREATE FILE FORMAT latin1 TYPE = CSV ENCODING = 'WINDOWS1252'");
        stage("latin1.csv", LATIN1);
        engine.executeQuery("COPY INTO l FROM @s FILES = ('latin1.csv') FILE_FORMAT = (FORMAT_NAME = 'latin1')");
        assertEquals("caf\u00e9", answer("SELECT v FROM l"));
    }

    @Test
    public void invalidUtf8RefusesItsRecord() throws IOException {
        engine.execute("CREATE TABLE l (id INT, v VARCHAR)");
        stage("bad.csv", LATIN1);
        final ResultSet result = engine.executeQuery("COPY INTO l FROM @s FILES = ('bad.csv')"
            + " FILE_FORMAT = (TYPE = CSV) ON_ERROR = CONTINUE");
        assertEquals("LOAD_FAILED", status(result));
        assertEquals("Invalid UTF8 detected in string 'caf\ufffd'", firstError(result));
        assertEquals("", answer("SELECT v FROM l"));
    }

    @Test
    public void replaceInvalidCharactersLoadsTheReplacementCharacter() throws IOException {
        engine.execute("CREATE TABLE l (id INT, v VARCHAR)");
        stage("bad.csv", LATIN1);
        assertEquals("LOADED", status(engine.executeQuery("COPY INTO l FROM @s FILES = ('bad.csv')"
            + " FILE_FORMAT = (TYPE = CSV REPLACE_INVALID_CHARACTERS = TRUE)")));
        assertEquals("636166EFBFBD", answer("SELECT HEX_ENCODE(v) FROM l"));
    }

    @Test
    public void aGoodRecordBesideABadOneStillLoads() throws IOException {
        engine.execute("CREATE TABLE l (id INT, v VARCHAR)");
        final byte[] mixed = {'1', ',', 'o', 'k', '\n', '2', ',', 'c', 'a', 'f', (byte) 0xE9, '\n'};
        stage("mixed.csv", mixed);
        final ResultSet result = engine.executeQuery("COPY INTO l FROM @s FILES = ('mixed.csv')"
            + " FILE_FORMAT = (TYPE = CSV) ON_ERROR = CONTINUE");
        assertEquals("PARTIALLY_LOADED", status(result));
        assertEquals("1, ok", answer("SELECT id, v FROM l"));
    }

    @Test
    public void aUtf16FileReads() throws IOException {
        engine.execute("CREATE TABLE l (id INT, v VARCHAR)");
        stage("utf16.csv", "1,caf\u00e9\n".getBytes(StandardCharsets.UTF_16LE));
        engine.executeQuery(
            "COPY INTO l FROM @s FILES = ('utf16.csv') FILE_FORMAT = (TYPE = CSV ENCODING = 'UTF16LE')");
        assertEquals("1, caf\u00e9", answer("SELECT id, v FROM l"));
    }
}
