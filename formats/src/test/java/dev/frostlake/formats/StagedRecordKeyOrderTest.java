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
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.ql.exec.vector.BytesColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.LongColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.StructColumnVector;
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
 * A staged Parquet, ORC or Avro record is the OBJECT the account holds: its keys sorted at every level, not in the
 * file's column order — a record written {@code z, a, m(y, b)} reads {@code {"a":"x","m":{"b":3,"y":2},"z":1}}, its
 * OBJECT_KEYS, TO_JSON and text alike (the answers a live account gave for the same files). The files are written
 * with each format's own API into a temp {@code file://} stage.
 */
public class StagedRecordKeyOrderTest {

    private static final String SORTED = "{\"a\":\"x\",\"m\":{\"b\":3,\"y\":2},\"z\":1}";

    private DatabaseEngine engine;
    private Path stageDir;

    @BeforeEach
    public void setUp() throws IOException {
        stageDir = Files.createTempDirectory("record_order_");
        engine = new DatabaseEngine();
        engine.getConfig().setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "true");
        engine.execute("CREATE DATABASE db");
        engine.execute("USE SCHEMA db.PUBLIC");
        engine.execute("CREATE STAGE st URL='file://" + stageDir + "'");
        engine.execute("CREATE FILE FORMAT fp TYPE = PARQUET");
        engine.execute("CREATE FILE FORMAT fo TYPE = ORC");
        engine.execute("CREATE FILE FORMAT fa TYPE = AVRO");
        writeFiles();
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

    /** One record, {@code z, a, m(y, b)}, in each of the three formats. */
    private void writeFiles() throws IOException {
        final Schema inner = SchemaBuilder.record("m").fields().requiredInt("y").requiredInt("b").endRecord();
        final Schema schema = SchemaBuilder.record("r").fields().requiredInt("z").requiredString("a")
            .name("m").type(inner).noDefault().endRecord();
        final GenericRecord nested = new GenericData.Record(inner);
        nested.put("y", 2);
        nested.put("b", 3);
        final GenericRecord record = new GenericData.Record(schema);
        record.put("z", 1);
        record.put("a", new Utf8("x"));
        record.put("m", nested);
        try (ParquetWriter<GenericRecord> writer = AvroParquetWriter.<GenericRecord>builder(
                new LocalOutputFile(stageDir.resolve("order.parquet"))).withSchema(schema).build()) {
            writer.write(record);
        }
        try (DataFileWriter<GenericRecord> writer =
                 new DataFileWriter<>(new GenericDatumWriter<GenericRecord>(schema))) {
            writer.create(schema, stageDir.resolve("order.avro").toFile());
            writer.append(record);
        }
        final Configuration conf = new Configuration();
        // RawLocalFileSystem writes no ".name.crc" checksum sidecar, which the stage would list.
        conf.set("fs.file.impl", "org.apache.hadoop.fs.RawLocalFileSystem");
        final TypeDescription orcSchema = TypeDescription.fromString("struct<z:int,a:string,m:struct<y:int,b:int>>");
        try (Writer writer = OrcFile.createWriter(new org.apache.hadoop.fs.Path(stageDir.resolve("order.orc").toUri()),
                OrcFile.writerOptions(conf).setSchema(orcSchema).overwrite(true))) {
            final VectorizedRowBatch batch = orcSchema.createRowBatch();
            final int row = batch.size++;
            ((LongColumnVector) batch.cols[0]).vector[row] = 1;
            ((BytesColumnVector) batch.cols[1]).setVal(row, new byte[] {'x'});
            final StructColumnVector m = (StructColumnVector) batch.cols[2];
            ((LongColumnVector) m.fields[0]).vector[row] = 2;
            ((LongColumnVector) m.fields[1]).vector[row] = 3;
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
    public void keysSortedInEveryFormat() {
        final String[][] files = {{"order.parquet", "fp"}, {"order.orc", "fo"}, {"order.avro", "fa"}};
        for (final String[] file : files) {
            final String source = " FROM @st/" + file[0] + " (FILE_FORMAT => '" + file[1] + "')";
            assertEquals(SORTED, answer("SELECT $1" + source), file[0]);
            assertEquals("{\"b\":3,\"y\":2}", answer("SELECT $1:m" + source), file[0]);
            assertEquals("[\"a\",\"m\",\"z\"]", answer("SELECT OBJECT_KEYS($1)" + source), file[0]);
            assertEquals(SORTED, answer("SELECT TO_JSON($1)" + source), file[0]);
            assertEquals(SORTED, answer("SELECT $1::VARCHAR" + source), file[0]);
            assertEquals("OBJECT", answer("SELECT TYPEOF($1)" + source), file[0]);
        }
    }
}
