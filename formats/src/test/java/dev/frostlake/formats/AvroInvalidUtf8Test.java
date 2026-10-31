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

package dev.frostlake.formats;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.storage.Row;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;
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
 * An Avro string holding a byte that is no part of UTF-8, read by a staged-file query: refused in the account's words,
 * {@code Error parsing AVRO: error parsing AVRO data: Invalid UTF8 detected while decoding 'caf0xE9'}, and under
 * REPLACE_INVALID_CHARACTERS = TRUE read with U+FFFD in the byte's place (the answers a live account gave for the same
 * file). The file is written with the Avro API into a temp {@code file://} stage.
 */
public class AvroInvalidUtf8Test {

    private static final Schema SCHEMA = new Schema.Parser().parse(
        "{\"type\":\"record\",\"name\":\"r\",\"fields\":[{\"name\":\"s\",\"type\":\"string\"}]}");

    private DatabaseEngine engine;
    private Path stageDir;

    @BeforeEach
    public void setUp() throws IOException {
        stageDir = Files.createTempDirectory("avro_utf8_");
        engine = new DatabaseEngine();
        engine.getConfig().setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "true");
        engine.execute("CREATE DATABASE db");
        engine.execute("USE SCHEMA db.PUBLIC");
        engine.execute("CREATE STAGE st URL='file://" + stageDir + "'");
        engine.execute("CREATE FILE FORMAT fa TYPE = AVRO");
        engine.execute("CREATE FILE FORMAT fa_rep TYPE = AVRO REPLACE_INVALID_CHARACTERS = TRUE");
        writeAvro("good.avro", "café".getBytes(StandardCharsets.UTF_8));
        writeAvro("bad.avro", new byte[] {'c', 'a', 'f', (byte) 0xE9}, "ok".getBytes(StandardCharsets.UTF_8));
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
        deleteRecursively(stageDir.toFile());
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

    /** One record per string, each written as its raw bytes, whatever they hold. */
    private void writeAvro(final String name, final byte[]... strings) throws IOException {
        final GenericDatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<>(SCHEMA);
        try (DataFileWriter<GenericRecord> writer = new DataFileWriter<>(datumWriter)) {
            writer.create(SCHEMA, stageDir.resolve(name).toFile());
            for (final byte[] string : strings) {
                final GenericRecord record = new GenericData.Record(SCHEMA);
                record.put("s", new Utf8(string));
                writer.append(record);
            }
        }
    }

    /** A statement's answer on one line: rows as "a|b ;; c|d", or "REFUSED " and the refusal. */
    private String answer(final String sql) {
        try {
            final StringBuilder rows = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                final StringBuilder cells = new StringBuilder();
                for (final Object value : row.getValues()) {
                    cells.append(cells.length() > 0 ? "|" : "").append(value);
                }
                rows.append(rows.length() > 0 ? " ;; " : "").append(cells);
            }
            return rows.toString();
        } catch (final RuntimeException refused) {
            return "REFUSED " + refused.getMessage();
        }
    }

    @Test
    public void aStringThatIsNoUtf8IsRefusedOrReplaced() {
        final String refused = "REFUSED Error parsing AVRO: error parsing AVRO data: Invalid UTF8 detected while "
            + "decoding 'caf0xE9'";
        assertEquals("{\"s\":\"café\"}", answer("SELECT $1 FROM @st/good.avro (FILE_FORMAT => 'fa')"));
        assertEquals(refused, answer("SELECT $1 FROM @st/bad.avro (FILE_FORMAT => 'fa')"));
        assertEquals(refused, answer("SELECT COUNT(*) FROM @st/bad.avro (FILE_FORMAT => 'fa')"));
        assertEquals("{\"s\":\"caf�\"} ;; {\"s\":\"ok\"}",
            answer("SELECT $1 FROM @st/bad.avro (FILE_FORMAT => 'fa_rep')"));
        assertEquals("ok|2|6F6B ;; caf�|4|636166EFBFBD", answer("SELECT $1:s::VARCHAR, LENGTH($1:s::VARCHAR), "
            + "HEX_ENCODE($1:s::VARCHAR) FROM @st/bad.avro (FILE_FORMAT => 'fa_rep') ORDER BY 2"));
    }
}
