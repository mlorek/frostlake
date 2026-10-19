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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A COPY load reads its fields' text by its file format: DATE_FORMAT, TIME_FORMAT and TIMESTAMP_FORMAT take the
 * session's DATE_INPUT_FORMAT, TIME_INPUT_FORMAT and TIMESTAMP_INPUT_FORMAT's place, and a format written AUTO, or none
 * written, leaves the session's own; BINARY_FORMAT reads a BINARY column's text as HEX, BASE64 or UTF8, HEX when none
 * is written. Inline and named formats alike, for a plain load and a transformation's columns, and the session's own
 * formats are back once the load ends (documented on COPY INTO &lt;table&gt;).
 */
public class CopyLoadFormatsTest {

    private static final String DAY_FIRST = "05/01/2024|05/01/2024 10:20:30|10.20.30\n"
        + "13/01/2024|13/01/2024 10:20:30|23.59.58\n";

    private static final String DAY_FIRST_FORMATS = "DATE_FORMAT = 'DD/MM/YYYY'"
        + " TIMESTAMP_FORMAT = 'DD/MM/YYYY HH24:MI:SS' TIME_FORMAT = 'HH24.MI.SS'";

    private DatabaseEngine engine;
    private Path internalRoot;
    private Path localDir;

    @BeforeEach
    public void setUp() throws IOException {
        internalRoot = Files.createTempDirectory("copy_load_formats_root_");
        localDir = Files.createTempDirectory("copy_load_formats_local_");
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_STAGE_INTERNAL_LOCAL_ROOT, internalRoot.toString());
        cfg.setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "true");
        engine = new DatabaseEngine(cfg);
        engine.execute("CREATE DATABASE db");
        engine.execute("USE SCHEMA db.PUBLIC");
        engine.execute("CREATE STAGE s");
        engine.execute("CREATE TABLE f (d DATE, ts TIMESTAMP_NTZ, t TIME)");
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
    private void stage(final String fileName, final String content) throws IOException {
        final Path file = localDir.resolve(fileName);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        engine.executeQuery("PUT file://" + file + " @s AUTO_COMPRESS=FALSE");
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

    /** The loaded rows, as text the session's output formats do not touch. */
    private String loaded() {
        return answer("SELECT TO_CHAR(d, 'YYYY-MM-DD'), TO_CHAR(ts, 'YYYY-MM-DD HH24:MI:SS'),"
            + " TO_CHAR(t, 'HH24:MI:SS') FROM f ORDER BY d");
    }

    /** The per-file status of a one-file COPY. */
    private static String status(final ResultSet result) {
        return String.valueOf(result.getRows().get(0).getValue(1));
    }

    @Test
    public void inlineFormatsReadDayFirstFields() throws IOException {
        stage("dmy.csv", DAY_FIRST);
        assertEquals("LOADED", status(engine.executeQuery("COPY INTO f FROM @s FILES = ('dmy.csv')"
            + " FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = '|' " + DAY_FIRST_FORMATS + ")")));
        assertEquals("2024-01-05, 2024-01-05 10:20:30, 10:20:30 | 2024-01-13, 2024-01-13 10:20:30, 23:59:58",
            loaded());
    }

    @Test
    public void aNamedFormatsFormatsReadDayFirstFields() throws IOException {
        stage("dmy.csv", DAY_FIRST);
        engine.execute("CREATE FILE FORMAT dmy TYPE = CSV FIELD_DELIMITER = '|' " + DAY_FIRST_FORMATS);
        assertEquals("LOADED", status(engine.executeQuery(
            "COPY INTO f FROM @s FILES = ('dmy.csv') FILE_FORMAT = (FORMAT_NAME = 'dmy')")));
        assertEquals("2024-01-05, 2024-01-05 10:20:30, 10:20:30 | 2024-01-13, 2024-01-13 10:20:30, 23:59:58",
            loaded());
    }

    @Test
    public void aTransformationsColumnsReadByTheFormats() throws IOException {
        stage("dmy.csv", DAY_FIRST);
        assertEquals("LOADED", status(engine.executeQuery("COPY INTO f FROM (SELECT $1, $2, $3 FROM @s)"
            + " FILES = ('dmy.csv') FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = '|' " + DAY_FIRST_FORMATS + ")")));
        assertEquals("2024-01-05, 2024-01-05 10:20:30, 10:20:30 | 2024-01-13, 2024-01-13 10:20:30, 23:59:58",
            loaded());
    }

    @Test
    public void autoAndNoFormatLeaveTheSessionsInputFormats() throws IOException {
        stage("dmy.csv", DAY_FIRST);
        engine.execute("ALTER SESSION SET DATE_INPUT_FORMAT = 'DD/MM/YYYY'");
        engine.execute("ALTER SESSION SET TIMESTAMP_INPUT_FORMAT = 'DD/MM/YYYY HH24:MI:SS'");
        engine.execute("ALTER SESSION SET TIME_INPUT_FORMAT = 'HH24.MI.SS'");
        assertEquals("LOADED", status(engine.executeQuery("COPY INTO f FROM @s FILES = ('dmy.csv')"
            + " FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = '|' DATE_FORMAT = AUTO)")));
        assertEquals("2024-01-05, 2024-01-05 10:20:30, 10:20:30 | 2024-01-13, 2024-01-13 10:20:30, 23:59:58",
            loaded());
    }

    @Test
    public void withoutAFormatAnAutoSessionReadsMonthFirst() throws IOException {
        stage("dmy.csv", DAY_FIRST);
        final ResultSet result = engine.executeQuery("COPY INTO f FROM @s FILES = ('dmy.csv')"
            + " FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = '|' TIME_FORMAT = 'HH24.MI.SS') ON_ERROR = CONTINUE");
        assertEquals("PARTIALLY_LOADED", status(result));
        assertEquals("Date '13/01/2024' is not recognized", result.getRows().get(0).getValue(6));
        assertEquals("2024-05-01, 2024-05-01 10:20:30, 10:20:30", loaded());
    }

    @Test
    public void theSessionsOwnFormatsAreBackAfterTheLoad() throws IOException {
        stage("dmy.csv", DAY_FIRST);
        engine.executeQuery("COPY INTO f FROM @s FILES = ('dmy.csv')"
            + " FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = '|' " + DAY_FIRST_FORMATS + ")");
        assertEquals("2024-05-01", answer("SELECT TO_CHAR('05/01/2024'::DATE, 'YYYY-MM-DD')"));
        assertEquals("null", answer("SELECT TRY_TO_DATE('13/01/2024')"));
    }

    @Test
    public void binaryFormatReadsABinaryColumnsText() throws IOException {
        engine.execute("CREATE TABLE fb (b BINARY)");
        stage("b64.csv", "SGk=\n");
        stage("utf8.csv", "Hi\n");
        stage("hex.csv", "4869\n");
        assertEquals("LOADED", status(engine.executeQuery(
            "COPY INTO fb FROM @s FILES = ('b64.csv') FILE_FORMAT = (TYPE = CSV BINARY_FORMAT = BASE64)")));
        assertEquals("LOADED", status(engine.executeQuery(
            "COPY INTO fb FROM @s FILES = ('utf8.csv') FILE_FORMAT = (TYPE = CSV BINARY_FORMAT = UTF8)")));
        assertEquals("LOADED", status(engine.executeQuery(
            "COPY INTO fb FROM @s FILES = ('hex.csv') FILE_FORMAT = (TYPE = CSV)")));
        assertEquals("4869 | 4869 | 4869", answer("SELECT TO_VARCHAR(b) FROM fb"));
    }

    @Test
    public void aNamedFormatsBinaryFormatReadsTheText() throws IOException {
        engine.execute("CREATE TABLE fb (b BINARY)");
        engine.execute("CREATE FILE FORMAT b64 TYPE = CSV BINARY_FORMAT = BASE64");
        stage("b64.csv", "SGk=\n");
        assertEquals("LOADED", status(engine.executeQuery(
            "COPY INTO fb FROM @s FILES = ('b64.csv') FILE_FORMAT = (FORMAT_NAME = 'b64')")));
        assertEquals("4869", answer("SELECT TO_VARCHAR(b) FROM fb"));
    }

    @Test
    public void base64TextThatIsNoBase64IsRefusedAsIs() throws IOException {
        engine.execute("CREATE TABLE fb (b BINARY)");
        stage("bad.csv", "SGk=\n%%%\n");
        final ResultSet result = engine.executeQuery("COPY INTO fb FROM @s FILES = ('bad.csv')"
            + " FILE_FORMAT = (TYPE = CSV BINARY_FORMAT = BASE64) ON_ERROR = CONTINUE");
        assertEquals("PARTIALLY_LOADED", status(result));
        assertEquals("The following string is not a legal base64-encoded value: '%%%'",
            result.getRows().get(0).getValue(6));
        assertEquals("4869", answer("SELECT TO_VARCHAR(b) FROM fb"));
    }
}
