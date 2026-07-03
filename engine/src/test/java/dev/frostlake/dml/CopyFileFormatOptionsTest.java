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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * COPY INTO honors the CSV FILE_FORMAT options TRIM_SPACE (strip leading/trailing field whitespace) and
 * NULL_IF (field values matching a token load as SQL NULL) — previously both were parsed then ignored, so
 * the raw text was loaded verbatim. Covers the inline form, the {@code NULL_IF = ('a','b')} list, a named
 * FILE FORMAT, and the default (options off → text preserved). Uses a {@code file://} stage over a temp dir.
 */
public class CopyFileFormatOptionsTest {

    private DatabaseEngine engine;
    private Path stageDir;

    @BeforeEach
    public void setUp() throws IOException {
        stageDir = Files.createTempDirectory("copy_ff_opts_");
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE db");
        engine.execute("USE DATABASE db");
        engine.execute("CREATE SCHEMA s");
        engine.execute("USE SCHEMA s");
        engine.execute("CREATE STAGE data_stage URL='file://" + stageDir + "'");
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (engine != null) {
            engine.shutdown();
        }
        deleteRecursively(stageDir.toFile());
    }

    private void writeStageFile(final String name, final String content) throws IOException {
        Files.writeString(stageDir.resolve(name), content);
    }

    private Object value(final String query) {
        final ResultSet rs = engine.executeQuery(query);
        return rs.getRows().get(0).getValue(0);
    }

    @Test
    public void trimSpaceStripsFieldWhitespace() throws IOException {
        writeStageFile("t.csv", "1,  Alice  \n");
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");

        engine.execute("COPY INTO t FROM @data_stage FILE_FORMAT = (TYPE = 'CSV', TRIM_SPACE = TRUE)");

        assertEquals("Alice", value("SELECT name FROM t WHERE id = 1").toString());
    }

    @Test
    public void withoutTrimSpaceWhitespaceIsPreserved() throws IOException {
        writeStageFile("t.csv", "1,  Alice  \n");
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");

        engine.execute("COPY INTO t FROM @data_stage FILE_FORMAT = (TYPE = 'CSV')");

        assertEquals("  Alice  ", value("SELECT name FROM t WHERE id = 1").toString());
    }

    @Test
    public void nullIfListConvertsMatchingValuesToNull() throws IOException {
        writeStageFile("t.csv", "1,\\N\n2,NULL\n3,real\n");
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");

        engine.execute("COPY INTO t FROM @data_stage FILE_FORMAT = (TYPE = 'CSV', NULL_IF = ('\\N', 'NULL'))");

        assertNull(value("SELECT name FROM t WHERE id = 1"));
        assertNull(value("SELECT name FROM t WHERE id = 2"));
        assertEquals("real", value("SELECT name FROM t WHERE id = 3").toString());
    }

    @Test
    public void withoutNullIfTokensAreLoadedLiterally() throws IOException {
        writeStageFile("t.csv", "1,\\N\n");
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");

        engine.execute("COPY INTO t FROM @data_stage FILE_FORMAT = (TYPE = 'CSV')");

        assertEquals("\\N", value("SELECT name FROM t WHERE id = 1").toString());
    }

    @Test
    public void trimSpaceThenNullIfCombine() throws IOException {
        writeStageFile("t.csv", "1,  NULL  \n2,  keep  \n");
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");

        // TRIM_SPACE runs first, so "  NULL  " trims to "NULL" and then matches NULL_IF.
        engine.execute(
            "COPY INTO t FROM @data_stage FILE_FORMAT = (TYPE = 'CSV', TRIM_SPACE = TRUE, NULL_IF = ('NULL'))");

        assertNull(value("SELECT name FROM t WHERE id = 1"));
        assertEquals("keep", value("SELECT name FROM t WHERE id = 2").toString());
    }

    @Test
    public void namedFileFormatCarriesTrimSpaceAndNullIf() throws IOException {
        writeStageFile("t.csv", "1,  \\N  \n2,  Bob  \n");
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.execute("CREATE FILE FORMAT ff TYPE = 'CSV' TRIM_SPACE = TRUE NULL_IF = ('\\N')");

        engine.execute("COPY INTO t FROM @data_stage FILE_FORMAT = (FORMAT_NAME = 'ff')");

        assertNull(value("SELECT name FROM t WHERE id = 1"));
        assertEquals("Bob", value("SELECT name FROM t WHERE id = 2").toString());
    }

    private void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
