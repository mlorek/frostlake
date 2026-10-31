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
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.ql.exec.vector.BytesColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.LongColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.orc.OrcFile;
import org.apache.orc.TypeDescription;
import org.apache.orc.Writer;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.io.LocalOutputFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A Parquet or ORC string holding a byte that is no part of UTF-8, read by a staged-file query: refused in the
 * account's words where a value is read, the rows counted where none is, and under REPLACE_INVALID_CHARACTERS = TRUE
 * read with U+FFFD in the byte's place (the answers a live account gave for the same files). Each file holds the rows
 * {@code ('ok', 1)} and {@code ('caf' 0xE9, 2)}, written with the format's own API into a temp {@code file://} stage.
 */
public class ParquetOrcInvalidUtf8Test {

    private static final byte[] GOOD = {'o', 'k'};
    private static final byte[] BAD = {'c', 'a', 'f', (byte) 0xE9};

    private DatabaseEngine engine;
    private Path stageDir;

    @BeforeEach
    public void setUp() throws IOException {
        stageDir = Files.createTempDirectory("columnar_utf8_");
        engine = new DatabaseEngine();
        engine.getConfig().setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "true");
        engine.execute("CREATE DATABASE db");
        engine.execute("USE SCHEMA db.PUBLIC");
        engine.execute("CREATE STAGE st URL='file://" + stageDir + "'");
        engine.execute("CREATE FILE FORMAT fp TYPE = PARQUET");
        engine.execute("CREATE FILE FORMAT fp_rep TYPE = PARQUET REPLACE_INVALID_CHARACTERS = TRUE");
        engine.execute("CREATE FILE FORMAT fo TYPE = ORC");
        engine.execute("CREATE FILE FORMAT fo_rep TYPE = ORC REPLACE_INVALID_CHARACTERS = TRUE");
        writeParquet();
        writeOrc();
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

    private void writeParquet() throws IOException {
        final Schema schema = SchemaBuilder.record("r").fields().requiredString("s").requiredInt("n").endRecord();
        try (ParquetWriter<GenericRecord> writer = AvroParquetWriter.<GenericRecord>builder(
                new LocalOutputFile(stageDir.resolve("bad.parquet"))).withSchema(schema).build()) {
            final GenericRecord first = new GenericData.Record(schema);
            first.put("s", new Utf8(GOOD));
            first.put("n", 1);
            writer.write(first);
            final GenericRecord second = new GenericData.Record(schema);
            second.put("s", new Utf8(BAD));
            second.put("n", 2);
            writer.write(second);
        }
    }

    private void writeOrc() throws IOException {
        final Configuration conf = new Configuration();
        // RawLocalFileSystem writes no ".name.crc" checksum sidecar, which the stage would list.
        conf.set("fs.file.impl", "org.apache.hadoop.fs.RawLocalFileSystem");
        final TypeDescription schema = TypeDescription.fromString("struct<s:string,n:int>");
        try (Writer writer = OrcFile.createWriter(new org.apache.hadoop.fs.Path(stageDir.resolve("bad.orc").toUri()),
                OrcFile.writerOptions(conf).setSchema(schema).overwrite(true))) {
            final VectorizedRowBatch batch = schema.createRowBatch();
            int row = batch.size++;
            ((BytesColumnVector) batch.cols[0]).setVal(row, GOOD);
            ((LongColumnVector) batch.cols[1]).vector[row] = 1;
            row = batch.size++;
            ((BytesColumnVector) batch.cols[0]).setVal(row, BAD);
            ((LongColumnVector) batch.cols[1]).vector[row] = 2;
            writer.addRowBatch(batch);
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
    public void aParquetStringThatIsNoUtf8IsRefusedWhereItIsRead() {
        final String refused = "REFUSED Invalid UTF8 detected while decoding 'caf0xE9'";
        assertEquals(refused, answer("SELECT $1 FROM @st/bad.parquet (FILE_FORMAT => 'fp')"));
        assertEquals(refused, answer("SELECT $1:n FROM @st/bad.parquet (FILE_FORMAT => 'fp')"));
        assertEquals("2", answer("SELECT COUNT(*) FROM @st/bad.parquet (FILE_FORMAT => 'fp')"));
        assertEquals("1|ok ;; 2|caf\uFFFD",
            answer("SELECT $1:n, $1:s::STRING FROM @st/bad.parquet (FILE_FORMAT => 'fp_rep') ORDER BY $1:n"));
        assertEquals("ok|0 ;; caf\uFFFD|65533", answer("SELECT $1:s::STRING, UNICODE(SUBSTR($1:s::STRING, 4, 1)) "
            + "FROM @st/bad.parquet (FILE_FORMAT => 'fp_rep') ORDER BY $1:n"));
    }

    @Test
    public void anOrcStringThatIsNoUtf8IsRefusedWhereItIsRead() {
        final String refused = "REFUSED Error parsing the ORC file: Error parsing the ORC file: Invalid UTF8 detected "
            + "while decoding 'caf0xE9'";
        assertEquals(refused, answer("SELECT $1 FROM @st/bad.orc (FILE_FORMAT => 'fo')"));
        assertEquals(refused, answer("SELECT $1:n FROM @st/bad.orc (FILE_FORMAT => 'fo')"));
        assertEquals("2", answer("SELECT COUNT(*) FROM @st/bad.orc (FILE_FORMAT => 'fo')"));
        assertEquals("1|ok ;; 2|caf\uFFFD",
            answer("SELECT $1:n, $1:s::STRING FROM @st/bad.orc (FILE_FORMAT => 'fo_rep') ORDER BY $1:n"));
        assertEquals("ok|0 ;; caf\uFFFD|65533", answer("SELECT $1:s::STRING, UNICODE(SUBSTR($1:s::STRING, 4, 1)) "
            + "FROM @st/bad.orc (FILE_FORMAT => 'fo_rep') ORDER BY $1:n"));
    }
}
