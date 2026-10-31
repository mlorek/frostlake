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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * COPY INTO with FILE_FORMAT = (TYPE = 'XML'). XML files load as VARIANT values in Snowflake's XML model, as PARSE_XML
 * builds them: one row per top-level element, which prints as XML and which XMLGET reads, attributes and text turned
 * into numbers where they read as numbers. This exercises the {@code StageFileReader} seam that also carries JSON.
 * Uses a {@code file://} stage over a temp directory.
 */
public class CopyXmlLoadTest {

    private DatabaseEngine engine;
    private Path stageDir;

    @BeforeEach
    public void setUp() throws IOException {
        stageDir = Files.createTempDirectory("copy_xml_");
        engine = new DatabaseEngine();
        // The tests point stages at local file:// directories - opt in to the affordance the
        // default config refuses (a real account refuses those URLs).
        engine.getConfig().setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "true");
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

    private long count(final String table) {
        return ((Number) engine.executeQuery("SELECT COUNT(*) FROM " + table).getRows().get(0).getValue(0)).longValue();
    }

    private String variant() {
        final ResultSet rs = engine.executeQuery("SELECT doc FROM docs");
        return rs.getRows().get(0).getValue(0).toString();
    }

    @Test
    public void loadsXmlDocumentAsVariant() throws IOException {
        writeStageFile("note.xml",
            "<note id=\"7\"><to>Tove</to><from>Jani</from><body>Reminder</body></note>");
        engine.execute("CREATE TABLE docs (doc VARIANT)");

        engine.execute("COPY INTO docs FROM @data_stage FILE_FORMAT = (TYPE = 'XML')");

        assertEquals(1L, count("docs"));
        assertEquals("<note id=\"7\"><to>Tove</to><from>Jani</from><body>Reminder</body></note>", variant());
        final ResultSet read = engine.executeQuery(
            "SELECT XMLGET(doc, 'to'):\"$\"::VARCHAR, GET(doc, '@id'), TYPEOF(doc) FROM docs");
        assertEquals("Tove", read.getRows().get(0).getValue(0));
        assertEquals("7", String.valueOf(read.getRows().get(0).getValue(1)));
        assertEquals("XML", read.getRows().get(0).getValue(2));
    }

    @Test
    public void repeatedChildElementsKeepTheirOrder() throws IOException {
        writeStageFile("catalog.xml", "<catalog><book>A</book><book>B</book><book>C</book></catalog>");
        engine.execute("CREATE TABLE docs (doc VARIANT)");

        engine.execute("COPY INTO docs FROM @data_stage FILE_FORMAT = (TYPE = 'XML')");

        assertEquals("<catalog><book>A</book><book>B</book><book>C</book></catalog>", variant());
        assertEquals("C", engine.executeQuery("SELECT XMLGET(doc, 'book', 2):\"$\"::VARCHAR FROM docs").getRows()
            .get(0).getValue(0));
    }

    @Test
    public void everyTopLevelElementIsARowAndStripOuterElementReadsWhatTheyHold() throws IOException {
        writeStageFile("several.xml", "<a>1</a><a>2</a>");
        writeStageFile("held.xml", "<r><b>x</b><c/></r>");
        engine.execute("CREATE TABLE docs (doc VARIANT)");

        engine.execute("COPY INTO docs FROM @data_stage FILES = ('several.xml') FILE_FORMAT = (TYPE = 'XML')");
        engine.execute("COPY INTO docs FROM @data_stage FILES = ('held.xml') "
            + "FILE_FORMAT = (TYPE = 'XML' STRIP_OUTER_ELEMENT = TRUE)");

        final ResultSet rows = engine.executeQuery("SELECT doc::VARCHAR FROM docs ORDER BY 1");
        assertEquals(4, rows.getRowCount());
        assertEquals("<a>1</a>", rows.getRows().get(0).getValue(0));
        assertEquals("<a>2</a>", rows.getRows().get(1).getValue(0));
        assertEquals("<b>x</b>", rows.getRows().get(2).getValue(0));
        assertEquals("<c></c>", rows.getRows().get(3).getValue(0));
    }

    @Test
    public void oneRowPerFile() throws IOException {
        writeStageFile("a.xml", "<r><v>1</v></r>");
        writeStageFile("b.xml", "<r><v>2</v></r>");
        engine.execute("CREATE TABLE docs (doc VARIANT)");

        engine.execute("COPY INTO docs FROM @data_stage FILE_FORMAT = (TYPE = 'XML')");

        assertEquals(2L, count("docs"));
    }

    @Test
    public void malformedXmlThrows() throws IOException {
        writeStageFile("bad.xml", "<note><to>Tove</note>");   // mismatched close tag
        engine.execute("CREATE TABLE docs (doc VARIANT)");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("COPY INTO docs FROM @data_stage FILE_FORMAT = (TYPE = 'XML')");
            }
        });
    }

    @Test
    public void anUnknownTypeIsAnInvalidValueWhereAnAbsentReaderNamesWhatIsSupported() throws IOException {
        writeStageFile("a.xml", "<r/>");
        engine.execute("CREATE TABLE docs (doc VARIANT)");

        // PROTOBUF is not a file format type at all, so the value itself is refused, in the account's
        // own words and echoing the value as it was written (live-verified for the same shape).
        final RuntimeException unknownType = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("COPY INTO docs FROM @data_stage FILE_FORMAT = (TYPE = 'PROTOBUF')");
            }
        });
        assertTrue(unknownType.getMessage().contains("invalid value ['PROTOBUF'] for parameter 'TYPE'"),
            unknownType.getMessage());

        // AVRO is a real type whose reader lives in the optional module, so it gets past the value
        // check and reaches the reader: THAT is where this classpath names what it can read.
        final RuntimeException absentReader = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("COPY INTO docs FROM @data_stage FILE_FORMAT = (TYPE = 'AVRO')");
            }
        });
        assertTrue(absentReader.getMessage().contains("CSV, JSON, XML"), absentReader.getMessage());
    }

    @Test
    public void jsonStillLoadsThroughTheSharedReaderSeam() throws IOException {
        writeStageFile("p.json", "{\"id\":1,\"name\":\"Alice\"}\n{\"id\":2,\"name\":\"Bob\"}\n");
        engine.execute("CREATE TABLE people (id INTEGER, name VARCHAR)");

        // Splitting a JSON record across several columns is what MATCH_BY_COLUMN_NAME is for —
        // without it Snowflake rejects a multi-column target (live-verified).
        engine.execute("COPY INTO people FROM @data_stage FILE_FORMAT = (TYPE = 'JSON')"
            + " MATCH_BY_COLUMN_NAME = 'CASE_INSENSITIVE'");

        assertEquals(2L, count("people"));
        assertEquals("Alice",
            engine.executeQuery("SELECT name FROM people WHERE id = 1").getRows().get(0).getValue(0).toString());
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
