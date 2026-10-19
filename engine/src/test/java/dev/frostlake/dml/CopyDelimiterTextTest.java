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
 * A COPY load splits a delimited file as a query over the stage splits it: at a FIELD_DELIMITER of several
 * characters, at a RECORD_DELIMITER other than a line break, which an enclosed field may hold, and at neither under
 * NONE. SKIP_HEADER skips line-break-delimited lines whatever the record delimiter (documented), and an escaped
 * delimiter of several characters stays in the value. A validating COPY and a named or a stage's file format split
 * the same way.
 */
public class CopyDelimiterTextTest {

    private DatabaseEngine engine;
    private Path internalRoot;
    private Path localDir;

    @BeforeEach
    public void setUp() throws IOException {
        internalRoot = Files.createTempDirectory("copy_delimiter_text_root_");
        localDir = Files.createTempDirectory("copy_delimiter_text_local_");
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
    public void aFieldDelimiterOfSeveralCharactersSplitsTheFields() throws IOException {
        engine.execute("CREATE STAGE s");
        stage("f1.csv", "a||1\nb||2\nc\\||d||3\n", "@s");
        engine.execute("CREATE TABLE t (a VARCHAR, b INT)");
        engine.execute("COPY INTO t FROM @s FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = '||')");
        assertEquals("a, 1 | b, 2 | c||d, 3", answer("SELECT * FROM t ORDER BY a"));
    }

    @Test
    public void aRecordDelimiterOtherThanALineBreakEndsTheRecord() throws IOException {
        engine.execute("CREATE STAGE s");
        stage("f2.csv", "\"x;y\",1;\"p\nq\",2;z,3", "@s");
        engine.execute("CREATE TABLE t (a VARCHAR, b INT)");
        engine.execute("COPY INTO t FROM @s FILE_FORMAT = (TYPE = CSV RECORD_DELIMITER = ';' "
            + "FIELD_OPTIONALLY_ENCLOSED_BY = '\"')");
        assertEquals("p\nq, 2 | x;y, 1 | z, 3", answer("SELECT * FROM t ORDER BY a"));
    }

    @Test
    public void bothDelimitersMayBeSeveralCharacters() throws IOException {
        engine.execute("CREATE STAGE s");
        stage("f3.csv", "a::1##b::2##", "@s");
        engine.execute("CREATE TABLE t (a VARCHAR, b INT)");
        engine.execute("COPY INTO t FROM @s FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = '::' RECORD_DELIMITER = '##')");
        assertEquals("a, 1 | b, 2", answer("SELECT * FROM t ORDER BY a"));
    }

    @Test
    public void skipHeaderSkipsLinesWhateverTheRecordDelimiter() throws IOException {
        engine.execute("CREATE STAGE s");
        stage("f4.csv", "h1,h2\na,1;b,2", "@s");
        engine.execute("CREATE TABLE t (a VARCHAR, b INT)");
        engine.execute("COPY INTO t FROM @s FILE_FORMAT = (TYPE = CSV RECORD_DELIMITER = ';' SKIP_HEADER = 1)");
        assertEquals("a, 1 | b, 2", answer("SELECT * FROM t ORDER BY a"));
    }

    @Test
    public void noneSplitsNothing() throws IOException {
        engine.execute("CREATE STAGE s");
        stage("f5.csv", "a,b\nc,d", "@s");
        engine.execute("CREATE TABLE t (v VARCHAR)");
        engine.execute("COPY INTO t FROM @s FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = NONE)");
        assertEquals("a,b | c,d", answer("SELECT * FROM t ORDER BY v"));
        engine.execute("CREATE STAGE s2");
        stage("f6.csv", "a,1", "@s2");
        engine.execute("CREATE TABLE t2 (a VARCHAR, b INT)");
        engine.execute("COPY INTO t2 FROM @s2 FILE_FORMAT = (TYPE = CSV RECORD_DELIMITER = NONE)");
        assertEquals("a, 1", answer("SELECT * FROM t2"));
    }

    @Test
    public void aNamedFormatAndAStagesFormatSplitAlike() throws IOException {
        engine.execute("CREATE FILE FORMAT ff TYPE = CSV FIELD_DELIMITER = '<>' RECORD_DELIMITER = '|'");
        engine.execute("CREATE STAGE s FILE_FORMAT = (FORMAT_NAME = 'ff')");
        stage("f7.csv", "a<>1|b<>2", "@s");
        engine.execute("CREATE TABLE t (a VARCHAR, b INT)");
        engine.execute("COPY INTO t FROM @s");
        assertEquals("a, 1 | b, 2", answer("SELECT * FROM t ORDER BY a"));
        engine.execute("CREATE TABLE t2 (a VARCHAR, b INT)");
        engine.execute("COPY INTO t2 FROM @s FILE_FORMAT = (FORMAT_NAME = 'ff')");
        assertEquals("a, 1 | b, 2", answer("SELECT * FROM t2 ORDER BY a"));
    }

    @Test
    public void aValidatingCopySplitsAsTheLoadDoes() throws IOException {
        engine.execute("CREATE STAGE s");
        stage("f8.csv", "a::1##b::2##c::3", "@s");
        engine.execute("CREATE TABLE t (a VARCHAR, b INT)");
        assertEquals("a, 1 | b, 2", answer("COPY INTO t FROM @s FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = '::' "
            + "RECORD_DELIMITER = '##') VALIDATION_MODE = RETURN_2_ROWS"));
    }
}
