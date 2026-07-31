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
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.ql.exec.vector.BytesColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.ListColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.LongColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.DoubleColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.StructColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.orc.OrcFile;
import org.apache.orc.TypeDescription;
import org.apache.orc.Writer;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * COPY INTO with FILE_FORMAT = (TYPE = 'ORC'). ORC is read via the Hadoop LocalFileSystem (the {@code file://}
 * path needs no cluster) as column-vector batches and each row is converted to a record, so it shares the
 * record path with JSON/Avro/Parquet (fields to columns by name, or the whole row into a VARIANT). Test
 * {@code .orc} files are written with the ORC Writer API into a temp {@code file://} stage — no committed
 * binary fixture. The writer uses RawLocalFileSystem so no {@code .crc} checksum sidecar is left in the stage
 * (COPY would otherwise try to read it as ORC).
 */
public class CopyOrcLoadTest {

    private DatabaseEngine engine;
    private Path stageDir;

    @BeforeEach
    public void setUp() throws IOException {
        stageDir = Files.createTempDirectory("copy_orc_");
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

    private Writer newWriter(final String name, final TypeDescription schema) throws IOException {
        final Configuration conf = new Configuration();
        // RawLocalFileSystem writes no ".name.crc" checksum sidecar, which COPY would list and fail to parse.
        conf.set("fs.file.impl", "org.apache.hadoop.fs.RawLocalFileSystem");
        return OrcFile.createWriter(new org.apache.hadoop.fs.Path(stageDir.resolve(name).toUri()),
            OrcFile.writerOptions(conf).setSchema(schema).overwrite(true));
    }

    private static void addUser(final VectorizedRowBatch batch, final long id, final String name,
                                final double score) {
        final int row = batch.size++;
        ((LongColumnVector) batch.cols[0]).vector[row] = id;
        ((BytesColumnVector) batch.cols[1]).setVal(row, name.getBytes(StandardCharsets.UTF_8));
        ((DoubleColumnVector) batch.cols[2]).vector[row] = score;
    }

    private void writeTwoUsers() throws IOException {
        final TypeDescription schema = TypeDescription.fromString("struct<id:int,name:string,score:double>");
        try (final Writer writer = newWriter("users.orc", schema)) {
            final VectorizedRowBatch batch = schema.createRowBatch();
            addUser(batch, 1, "Alice", 9.5);
            addUser(batch, 2, "Bob", 7.0);
            writer.addRowBatch(batch);
        }
    }

    private long count(final String table) {
        return ((Number) engine.executeQuery("SELECT COUNT(*) FROM " + table).getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void loadsOrcIntoNamedColumnsByFieldName() throws IOException {
        writeTwoUsers();
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, score DOUBLE)");

        // Splitting a schema-bearing record across several columns is what MATCH_BY_COLUMN_NAME is
        // for — without it Snowflake rejects a multi-column target (
        // "ORC file format can produce one and only one column of type variant, object, or
        // array. Load data into separate columns using the MATCH_BY_COLUMN_NAME copy option ...").
        engine.execute("COPY INTO users FROM @data_stage FILE_FORMAT = (TYPE = 'ORC')"
            + " MATCH_BY_COLUMN_NAME = 'CASE_INSENSITIVE'");

        assertEquals(2L, count("users"));
        final ResultSet rs = engine.executeQuery("SELECT name, score FROM users WHERE id = 2");
        assertEquals("Bob", rs.getRows().get(0).getValue(0).toString());
        assertEquals(7.0, ((Number) rs.getRows().get(0).getValue(1)).doubleValue(), 1e-9);
    }

    @Test
    public void loadsOrcIntoVariant() throws IOException {
        writeTwoUsers();
        engine.execute("CREATE TABLE docs (rec VARIANT)");

        engine.execute("COPY INTO docs FROM @data_stage FILE_FORMAT = (TYPE = 'ORC')");

        assertEquals(2L, count("docs"));
        final Object v = engine.executeQuery("SELECT rec FROM docs").getRows().get(0).getValue(0);
        assertTrue(v.toString().contains("\"name\":\"Alice\""), v.toString());
    }

    @Test
    public void nestedStructBecomesNestedVariant() throws IOException {
        final TypeDescription schema = TypeDescription.fromString("struct<id:int,customer:struct<name:string>>");
        try (final Writer writer = newWriter("orders.orc", schema)) {
            final VectorizedRowBatch batch = schema.createRowBatch();
            final int row = batch.size++;
            ((LongColumnVector) batch.cols[0]).vector[row] = 10;
            final StructColumnVector customer = (StructColumnVector) batch.cols[1];
            ((BytesColumnVector) customer.fields[0]).setVal(row, "Alice".getBytes(StandardCharsets.UTF_8));
            writer.addRowBatch(batch);
        }
        engine.execute("CREATE TABLE docs (rec VARIANT)");

        engine.execute("COPY INTO docs FROM @data_stage FILE_FORMAT = (TYPE = 'ORC')");

        assertTrue(engine.executeQuery("SELECT rec FROM docs").getRows().get(0).getValue(0).toString()
            .contains("\"customer\":{\"name\":\"Alice\"}"));
    }

    @Test
    public void booleanAndArrayColumnsConvert() throws IOException {
        final TypeDescription schema = TypeDescription.fromString("struct<id:int,active:boolean,tags:array<string>>");
        try (final Writer writer = newWriter("flags.orc", schema)) {
            final VectorizedRowBatch batch = schema.createRowBatch();
            final int row = batch.size++;
            ((LongColumnVector) batch.cols[0]).vector[row] = 5;
            ((LongColumnVector) batch.cols[1]).vector[row] = 1;   // boolean true
            final ListColumnVector tags = (ListColumnVector) batch.cols[2];
            final BytesColumnVector child = (BytesColumnVector) tags.child;
            child.ensureSize(2, false);
            tags.offsets[row] = 0;
            tags.lengths[row] = 2;
            tags.childCount = 2;
            child.setVal(0, "x".getBytes(StandardCharsets.UTF_8));
            child.setVal(1, "y".getBytes(StandardCharsets.UTF_8));
            writer.addRowBatch(batch);
        }
        engine.execute("CREATE TABLE docs (rec VARIANT)");

        engine.execute("COPY INTO docs FROM @data_stage FILE_FORMAT = (TYPE = 'ORC')");

        final String v = engine.executeQuery("SELECT rec FROM docs").getRows().get(0).getValue(0).toString();
        assertTrue(v.contains("\"active\":true"), v);
        assertTrue(v.contains("\"tags\":[\"x\",\"y\"]"), v);
    }

    @Test
    public void nonOrcFileThrows() throws IOException {
        Files.writeString(stageDir.resolve("bad.orc"), "not an orc file");
        engine.execute("CREATE TABLE docs (rec VARIANT)");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("COPY INTO docs FROM @data_stage FILE_FORMAT = (TYPE = 'ORC')");
            }
        });
    }

    @Test
    public void unsupportedFormatMessageListsOrc() throws IOException {
        Files.writeString(stageDir.resolve("x.dat"), "x");
        engine.execute("CREATE TABLE docs (rec VARIANT)");

        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("COPY INTO docs FROM @data_stage FILE_FORMAT = (TYPE = 'PROTOBUF')");
            }
        });
        assertTrue(ex.getMessage().contains("ORC"), ex.getMessage());
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
