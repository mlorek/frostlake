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
import dev.frostlake.storage.ResultSet;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.io.LocalOutputFile;
import org.apache.parquet.io.OutputFile;
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
 * COPY INTO with FILE_FORMAT = (TYPE = 'PARQUET'). Parquet is read via a Hadoop-free {@code LocalInputFile}
 * and surfaced as Avro {@code GenericRecord}s, so it shares Avro's mapping (fields to columns by name, or the
 * whole row into a VARIANT). Test .parquet files are written with the Parquet API into a temp
 * {@code file://} stage — no committed binary fixture.
 */
public class CopyParquetLoadTest {

    private DatabaseEngine engine;
    private Path stageDir;

    @BeforeEach
    public void setUp() throws IOException {
        stageDir = Files.createTempDirectory("copy_parquet_");
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

    private void writeParquet(final String name, final Schema schema, final GenericRecord... records) throws IOException {
        final OutputFile output = new LocalOutputFile(stageDir.resolve(name));
        try (final ParquetWriter<GenericRecord> writer =
                 AvroParquetWriter.<GenericRecord>builder(output).withSchema(schema).build()) {
            for (final GenericRecord record : records) {
                writer.write(record);
            }
        }
    }

    private long count(final String table) {
        return ((Number) engine.executeQuery("SELECT COUNT(*) FROM " + table).getRows().get(0).getValue(0)).longValue();
    }

    private static final String USER_SCHEMA =
        "{\"type\":\"record\",\"name\":\"User\",\"fields\":["
        + "{\"name\":\"id\",\"type\":\"int\"},{\"name\":\"name\",\"type\":\"string\"},"
        + "{\"name\":\"score\",\"type\":\"double\"}]}";

    private void writeTwoUsers() throws IOException {
        final Schema schema = new Schema.Parser().parse(USER_SCHEMA);
        final GenericRecord a = new GenericData.Record(schema);
        a.put("id", 1);
        a.put("name", "Alice");
        a.put("score", 9.5);
        final GenericRecord b = new GenericData.Record(schema);
        b.put("id", 2);
        b.put("name", "Bob");
        b.put("score", 7.0);
        writeParquet("users.parquet", schema, a, b);
    }

    @Test
    public void loadsParquetIntoNamedColumnsByFieldName() throws IOException {
        writeTwoUsers();
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, score DOUBLE)");

        engine.execute("COPY INTO users FROM @data_stage FILE_FORMAT = (TYPE = 'PARQUET')");

        assertEquals(2L, count("users"));
        final ResultSet rs = engine.executeQuery("SELECT name, score FROM users WHERE id = 2");
        assertEquals("Bob", rs.getRows().get(0).getValue(0).toString());
        assertEquals(7.0, ((Number) rs.getRows().get(0).getValue(1)).doubleValue(), 1e-9);
    }

    @Test
    public void loadsParquetIntoVariant() throws IOException {
        writeTwoUsers();
        engine.execute("CREATE TABLE docs (rec VARIANT)");

        engine.execute("COPY INTO docs FROM @data_stage FILE_FORMAT = (TYPE = 'PARQUET')");

        assertEquals(2L, count("docs"));
        final Object v = engine.executeQuery("SELECT rec FROM docs").getRows().get(0).getValue(0);
        assertTrue(v.toString().contains("\"name\":\"Alice\""), v.toString());
    }

    @Test
    public void nestedRecordBecomesNestedVariant() throws IOException {
        final Schema schema = new Schema.Parser().parse(
            "{\"type\":\"record\",\"name\":\"Order\",\"fields\":["
            + "{\"name\":\"id\",\"type\":\"int\"},"
            + "{\"name\":\"customer\",\"type\":{\"type\":\"record\",\"name\":\"Cust\","
            + "\"fields\":[{\"name\":\"name\",\"type\":\"string\"}]}}]}");
        final GenericRecord customer = new GenericData.Record(schema.getField("customer").schema());
        customer.put("name", "Alice");
        final GenericRecord order = new GenericData.Record(schema);
        order.put("id", 10);
        order.put("customer", customer);
        writeParquet("orders.parquet", schema, order);
        engine.execute("CREATE TABLE docs (rec VARIANT)");

        engine.execute("COPY INTO docs FROM @data_stage FILE_FORMAT = (TYPE = 'PARQUET')");

        assertTrue(engine.executeQuery("SELECT rec FROM docs").getRows().get(0).getValue(0).toString()
            .contains("\"customer\":{\"name\":\"Alice\"}"));
    }

    @Test
    public void nonParquetFileThrows() throws IOException {
        Files.writeString(stageDir.resolve("bad.parquet"), "not a parquet file");
        engine.execute("CREATE TABLE docs (rec VARIANT)");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("COPY INTO docs FROM @data_stage FILE_FORMAT = (TYPE = 'PARQUET')");
            }
        });
    }

    @Test
    public void unsupportedFormatMessageListsParquet() throws IOException {
        Files.writeString(stageDir.resolve("x.dat"), "x");
        engine.execute("CREATE TABLE docs (rec VARIANT)");

        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("COPY INTO docs FROM @data_stage FILE_FORMAT = (TYPE = 'PROTOBUF')");
            }
        });
        assertTrue(ex.getMessage().contains("PARQUET"), ex.getMessage());
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
