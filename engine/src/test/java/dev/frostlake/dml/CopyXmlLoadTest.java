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
import org.junit.jupiter.api.function.Executable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * COPY INTO with FILE_FORMAT = (TYPE = 'XML'). XML files load as a semi-structured VARIANT (one row per
 * document, keyed by the root element — attributes become {@code @name} fields, repeated children become
 * arrays). This exercises the new {@code StageFileReader} seam that also carries JSON. Uses a
 * {@code file://} stage over a temp directory.
 */
public class CopyXmlLoadTest {

    private DatabaseEngine engine;
    private Path stageDir;

    @BeforeEach
    public void setUp() throws IOException {
        stageDir = Files.createTempDirectory("copy_xml_");
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
        final String v = variant();
        assertTrue(v.contains("\"note\""), v);          // root element kept
        assertTrue(v.contains("\"to\":\"Tove\""), v);   // nested leaf → scalar
        assertTrue(v.contains("\"@id\":\"7\""), v);     // attribute → @name field
    }

    @Test
    public void repeatedChildElementsBecomeAnArray() throws IOException {
        writeStageFile("catalog.xml", "<catalog><book>A</book><book>B</book><book>C</book></catalog>");
        engine.execute("CREATE TABLE docs (doc VARIANT)");

        engine.execute("COPY INTO docs FROM @data_stage FILE_FORMAT = (TYPE = 'XML')");

        assertTrue(variant().contains("\"book\":[\"A\",\"B\",\"C\"]"), variant());
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
    public void unsupportedFormatReportsCsvJsonXml() throws IOException {
        writeStageFile("a.xml", "<r/>");
        engine.execute("CREATE TABLE docs (doc VARIANT)");

        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("COPY INTO docs FROM @data_stage FILE_FORMAT = (TYPE = 'PROTOBUF')");
            }
        });
        assertTrue(ex.getMessage().contains("CSV, JSON, XML"), ex.getMessage());
    }

    @Test
    public void jsonStillLoadsThroughTheSharedReaderSeam() throws IOException {
        writeStageFile("p.json", "{\"id\":1,\"name\":\"Alice\"}\n{\"id\":2,\"name\":\"Bob\"}\n");
        engine.execute("CREATE TABLE people (id INTEGER, name VARCHAR)");

        engine.execute("COPY INTO people FROM @data_stage FILE_FORMAT = (TYPE = 'JSON')");

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
