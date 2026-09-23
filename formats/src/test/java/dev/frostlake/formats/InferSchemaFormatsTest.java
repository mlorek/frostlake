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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.orc.OrcFile;
import org.apache.orc.TypeDescription;
import org.apache.orc.Writer;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.io.LocalOutputFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * INFER_SCHEMA over Parquet, Avro and ORC files: the types each format's schema gives its columns, NULLABLE from
 * the declared repetition, how files and case-folded names merge, KIND => 'ICEBERG', and the files a format skips.
 * The files are written in-test with each format's own writer into a {@code file://} stage.
 */
public class InferSchemaFormatsTest {

    private static final String TYPES_SCHEMA = """
        {"type":"record","name":"r","fields":[
          {"name":"i","type":"int"},
          {"name":"oi","type":["null","int"],"default":null},
          {"name":"l","type":"long"},
          {"name":"s","type":"string"},
          {"name":"os","type":["null","string"],"default":null},
          {"name":"b","type":"boolean"},
          {"name":"d","type":"double"},
          {"name":"f","type":"float"},
          {"name":"dec","type":{"type":"bytes","logicalType":"decimal","precision":9,"scale":2}},
          {"name":"decf","type":{"type":"fixed","name":"fx","size":16,"logicalType":"decimal","precision":38,"scale":4}},
          {"name":"dt","type":{"type":"int","logicalType":"date"}},
          {"name":"tsm","type":{"type":"long","logicalType":"timestamp-millis"}},
          {"name":"tsu","type":{"type":"long","logicalType":"timestamp-micros"}},
          {"name":"tm","type":{"type":"int","logicalType":"time-millis"}},
          {"name":"tmu","type":{"type":"long","logicalType":"time-micros"}},
          {"name":"bin","type":"bytes"},
          {"name":"rec","type":{"type":"record","name":"inner","fields":[{"name":"x","type":"int"}]}},
          {"name":"arr","type":{"type":"array","items":"int"}},
          {"name":"m","type":{"type":"map","values":"int"}},
          {"name":"e","type":{"type":"enum","name":"en","symbols":["A","B"]}},
          {"name":"Up","type":"int"},
          {"name":"up","type":["null","string"],"default":null}
        ]}
        """;

    private static final String SECOND_SCHEMA = """
        {"type":"record","name":"r2","fields":[
          {"name":"i","type":"long"},
          {"name":"s","type":["null","string"],"default":null},
          {"name":"extra","type":"double"}
        ]}
        """;

    private static final String FLAT_SCHEMA = """
        {"type":"record","name":"f","fields":[
          {"name":"i32","type":"int"},
          {"name":"i64","type":"long"},
          {"name":"f","type":"float"},
          {"name":"d","type":"double"},
          {"name":"b","type":"boolean"},
          {"name":"s","type":"string"},
          {"name":"bin","type":"bytes"},
          {"name":"dec","type":{"type":"bytes","logicalType":"decimal","precision":9,"scale":2}},
          {"name":"dt","type":{"type":"int","logicalType":"date"}},
          {"name":"tsm","type":{"type":"long","logicalType":"timestamp-millis"}},
          {"name":"tm","type":{"type":"int","logicalType":"time-millis"}},
          {"name":"os","type":["null","string"],"default":null}
        ]}
        """;

    private DatabaseEngine engine;
    private Path stageDir;

    @BeforeEach
    public void setUp() throws IOException {
        stageDir = Files.createTempDirectory("infer_schema_");
        engine = new DatabaseEngine();
        // The test stage points at a local file:// directory — opt in to the affordance the default config refuses.
        engine.getConfig().setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "true");
        engine.execute("CREATE DATABASE db");
        engine.execute("USE DATABASE db");
        engine.execute("CREATE SCHEMA s");
        engine.execute("USE SCHEMA s");
        engine.execute("CREATE STAGE st URL='file://" + stageDir + "'");
        engine.execute("CREATE FILE FORMAT fp TYPE = PARQUET");
        engine.execute("CREATE FILE FORMAT fa TYPE = AVRO");
        engine.execute("CREATE FILE FORMAT fo TYPE = ORC");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
        deleteRecursively(stageDir.toFile());
    }

    @Test
    public void parquetTypesAndNullability() throws IOException {
        writeParquet("pq/types.parquet", TYPES_SCHEMA);
        assertEquals(Arrays.asList(
            "i | NUMBER(38, 0) | FALSE | $1:i::NUMBER(38, 0) | pq/types.parquet | 0",
            "oi | NUMBER(38, 0) | TRUE | $1:oi::NUMBER(38, 0) | pq/types.parquet | 1",
            "l | NUMBER(38, 0) | FALSE | $1:l::NUMBER(38, 0) | pq/types.parquet | 2",
            "s | TEXT | FALSE | $1:s::TEXT | pq/types.parquet | 3",
            "os | TEXT | TRUE | $1:os::TEXT | pq/types.parquet | 4",
            "b | BOOLEAN | FALSE | $1:b::BOOLEAN | pq/types.parquet | 5",
            "d | REAL | FALSE | $1:d::REAL | pq/types.parquet | 6",
            "f | REAL | FALSE | $1:f::REAL | pq/types.parquet | 7",
            "dec | NUMBER(9, 2) | FALSE | $1:dec::NUMBER(9, 2) | pq/types.parquet | 8",
            "decf | NUMBER(38, 4) | FALSE | $1:decf::NUMBER(38, 4) | pq/types.parquet | 9",
            "dt | DATE | FALSE | $1:dt::DATE | pq/types.parquet | 10",
            "tsm | TIMESTAMP_NTZ | FALSE | $1:tsm::TIMESTAMP_NTZ | pq/types.parquet | 11",
            "tsu | TIMESTAMP_NTZ | FALSE | $1:tsu::TIMESTAMP_NTZ | pq/types.parquet | 12",
            "tm | TIME | FALSE | $1:tm::TIME | pq/types.parquet | 13",
            "tmu | TIME | FALSE | $1:tmu::TIME | pq/types.parquet | 14",
            "bin | BINARY | FALSE | $1:bin::BINARY | pq/types.parquet | 15",
            "rec | VARIANT | FALSE | $1:rec::VARIANT | pq/types.parquet | 16",
            "arr | VARIANT | FALSE | $1:arr::VARIANT | pq/types.parquet | 17",
            "m | VARIANT | FALSE | $1:m::VARIANT | pq/types.parquet | 18",
            "e | TEXT | FALSE | $1:e::TEXT | pq/types.parquet | 19",
            "Up | NUMBER(38, 0) | FALSE | $1:Up::NUMBER(38, 0) | pq/types.parquet | 20",
            "up | TEXT | TRUE | $1:up::TEXT | pq/types.parquet | 21"),
            infer("LOCATION => '@st/pq/', FILE_FORMAT => 'fp'"));
    }

    @Test
    public void parquetIgnoreCaseMergesConflictingColumnsToVariant() throws IOException {
        writeParquet("pq/types.parquet", TYPES_SCHEMA);
        final List<String> rows = infer("LOCATION => '@st/pq/', FILE_FORMAT => 'fp', IGNORE_CASE => TRUE");
        assertEquals(21, rows.size());
        assertEquals("I | NUMBER(38, 0) | FALSE | GET_IGNORE_CASE($1, 'I')::NUMBER(38, 0) | pq/types.parquet | 0",
            rows.get(0));
        assertEquals("UP | VARIANT | FALSE | GET_IGNORE_CASE($1, 'UP')::VARIANT | pq/types.parquet | 20",
            rows.get(20));
    }

    @Test
    public void parquetLogicalTimestampsAndIcebergTypes() throws IOException {
        writeParquet("pq/types.parquet", TYPES_SCHEMA);
        writeParquet("flat/flat.parquet", FLAT_SCHEMA);
        engine.execute("CREATE FILE FORMAT fpl TYPE = PARQUET USE_LOGICAL_TYPE = TRUE");
        assertEquals(Arrays.asList("tsm | TIMESTAMP_LTZ", "tsu | TIMESTAMP_LTZ"),
            lines("SELECT COLUMN_NAME, TYPE FROM TABLE(INFER_SCHEMA(LOCATION => '@st/pq/', FILE_FORMAT => 'fpl'))"
                + " WHERE COLUMN_NAME LIKE 'ts%' ORDER BY ORDER_ID"));
        assertEquals(Arrays.asList(
            "i32 | INT | FALSE", "i64 | LONG | FALSE", "f | FLOAT | FALSE", "d | DOUBLE | FALSE",
            "b | BOOLEAN | FALSE", "s | TEXT | FALSE", "bin | BINARY | FALSE", "dec | NUMBER(9, 2) | FALSE",
            "dt | DATE | FALSE", "tsm | TIMESTAMP_LTZ(6) | FALSE", "tm | TIME(6) | FALSE", "os | TEXT | TRUE"),
            lines("SELECT COLUMN_NAME, TYPE, NULLABLE FROM TABLE(INFER_SCHEMA(LOCATION => '@st/flat/',"
                + " FILE_FORMAT => 'fp', KIND => 'ICEBERG')) ORDER BY ORDER_ID"));
        final RuntimeException nested = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                infer("LOCATION => '@st/pq/', FILE_FORMAT => 'fp', KIND => 'ICEBERG'");
            }
        });
        assertEquals("Encountered unsupported logical type NESTED Datatype for physical type UNKNOWN",
            nested.getMessage());
    }

    @Test
    public void parquetFilesMergeAndSkipOtherFormats() throws IOException {
        writeParquet("pq/types.parquet", TYPES_SCHEMA);
        writeParquet("pq/types2.parquet", SECOND_SCHEMA);
        // A column missing from a file, or nullable in one, is nullable; one required everywhere stays NOT NULL.
        assertEquals(Arrays.asList(
            "i | NUMBER(38, 0) | FALSE | pq/types.parquet, pq/types2.parquet | 0",
            "l | NUMBER(38, 0) | TRUE | pq/types.parquet | 2",
            "extra | REAL | TRUE | pq/types2.parquet | 2",
            "s | TEXT | TRUE | pq/types.parquet, pq/types2.parquet | 3"),
            lines("SELECT COLUMN_NAME, TYPE, NULLABLE, FILENAMES, ORDER_ID FROM TABLE(INFER_SCHEMA("
                + "LOCATION => '@st/pq/', FILE_FORMAT => 'fp')) WHERE COLUMN_NAME IN ('i', 'l', 'extra', 's')"
                + " ORDER BY ORDER_ID, FILENAMES"));
        // Types that disagree across files meet as VARIANT.
        writeParquet("c/conf.parquet", """
            {"type":"record","name":"c","fields":[{"name":"s","type":"int"},{"name":"i","type":"string"}]}
            """);
        writeParquet("c/types.parquet", TYPES_SCHEMA);
        assertEquals(Arrays.asList("i | VARIANT | FALSE", "s | VARIANT | FALSE"),
            lines("SELECT COLUMN_NAME, TYPE, NULLABLE FROM TABLE(INFER_SCHEMA(LOCATION => '@st/c/',"
                + " FILE_FORMAT => 'fp')) WHERE COLUMN_NAME IN ('s', 'i') ORDER BY COLUMN_NAME"));
        // A file not of the format declares nothing, so every column is nullable; a format reading none of the
        // files answers no rows.
        writeParquet("ne/noext", SECOND_SCHEMA);
        Files.writeString(stageDir.resolve("ne/small.csv"), "a,b\n1,x\n");
        assertEquals(Arrays.asList("i | NUMBER(38, 0) | TRUE | ne/noext", "s | TEXT | TRUE | ne/noext",
            "extra | REAL | TRUE | ne/noext"),
            lines("SELECT COLUMN_NAME, TYPE, NULLABLE, FILENAMES FROM TABLE(INFER_SCHEMA(LOCATION => '@st/ne/',"
                + " FILE_FORMAT => 'fp')) ORDER BY ORDER_ID"));
        assertEquals(0, infer("LOCATION => '@st/pq/', FILE_FORMAT => 'fa'").size());
    }

    @Test
    public void avroTypesIgnoreLogicalTypes() throws IOException {
        writeAvro("av/types.avro", TYPES_SCHEMA);
        assertEquals(Arrays.asList(
            "i | NUMBER(38, 0) | FALSE", "oi | NUMBER(38, 0) | TRUE", "l | NUMBER(38, 0) | FALSE", "s | TEXT | FALSE",
            "os | TEXT | TRUE", "b | BOOLEAN | FALSE", "d | REAL | FALSE", "f | REAL | FALSE", "dec | BINARY | FALSE",
            "decf | BINARY | FALSE", "dt | NUMBER(38, 0) | FALSE", "tsm | NUMBER(38, 0) | FALSE",
            "tsu | NUMBER(38, 0) | FALSE", "tm | NUMBER(38, 0) | FALSE", "tmu | NUMBER(38, 0) | FALSE",
            "bin | BINARY | FALSE", "rec | VARIANT | FALSE", "arr | ARRAY | FALSE", "m | VARIANT | FALSE",
            "e | VARIANT | FALSE", "Up | NUMBER(38, 0) | FALSE", "up | TEXT | TRUE"),
            lines("SELECT COLUMN_NAME, TYPE, NULLABLE FROM TABLE(INFER_SCHEMA(LOCATION => '@st/av/',"
                + " FILE_FORMAT => 'fa')) ORDER BY ORDER_ID"));
        // An Iceberg table asks for the same Avro types.
        assertEquals(22, infer("LOCATION => '@st/av/', FILE_FORMAT => 'fa', KIND => 'ICEBERG'").size());
    }

    @Test
    public void orcTypesAreAllNullable() throws IOException {
        writeOrc("orc/types.orc", "struct<i:int,l:bigint,sm:smallint,ti:tinyint,s:string,b:boolean,d:double,"
            + "f:float,dec:decimal(9,2),dt:date,ts:timestamp,bin:binary,st:struct<x:int>,arr:array<int>,"
            + "m:map<string,int>,ch:char(5),vc:varchar(10),Up:int>");
        assertEquals(Arrays.asList(
            "i | NUMBER(38, 0) | TRUE | $1:i::NUMBER(38, 0) | orc/types.orc | 0",
            "l | NUMBER(38, 0) | TRUE | $1:l::NUMBER(38, 0) | orc/types.orc | 1",
            "sm | NUMBER(38, 0) | TRUE | $1:sm::NUMBER(38, 0) | orc/types.orc | 2",
            "ti | NUMBER(38, 0) | TRUE | $1:ti::NUMBER(38, 0) | orc/types.orc | 3",
            "s | TEXT | TRUE | $1:s::TEXT | orc/types.orc | 4",
            "b | BOOLEAN | TRUE | $1:b::BOOLEAN | orc/types.orc | 5",
            "d | REAL | TRUE | $1:d::REAL | orc/types.orc | 6",
            "f | REAL | TRUE | $1:f::REAL | orc/types.orc | 7",
            "dec | NUMBER(9, 2) | TRUE | $1:dec::NUMBER(9, 2) | orc/types.orc | 8",
            "dt | DATE | TRUE | $1:dt::DATE | orc/types.orc | 9",
            "ts | TIMESTAMP_NTZ | TRUE | $1:ts::TIMESTAMP_NTZ | orc/types.orc | 10",
            "bin | BINARY | TRUE | $1:bin::BINARY | orc/types.orc | 11",
            "st | VARIANT | TRUE | $1:st::VARIANT | orc/types.orc | 12",
            "arr | ARRAY | TRUE | $1:arr::ARRAY | orc/types.orc | 13",
            "m | VARIANT | TRUE | $1:m::VARIANT | orc/types.orc | 14",
            "ch | TEXT | TRUE | $1:ch::TEXT | orc/types.orc | 15",
            "vc | TEXT | TRUE | $1:vc::TEXT | orc/types.orc | 16",
            "Up | NUMBER(38, 0) | TRUE | $1:Up::NUMBER(38, 0) | orc/types.orc | 17"),
            infer("LOCATION => '@st/orc/', FILE_FORMAT => 'fo'"));
    }

    @Test
    public void usingTemplateKeepsNotNullColumns() throws IOException {
        writeParquet("pq/types.parquet", TYPES_SCHEMA);
        engine.execute("CREATE TABLE tp USING TEMPLATE (SELECT ARRAY_AGG(OBJECT_CONSTRUCT(*)) WITHIN GROUP"
            + " (ORDER BY ORDER_ID) FROM TABLE(INFER_SCHEMA(LOCATION => '@st/pq/', FILE_FORMAT => 'fp')))");
        final ResultSet described = engine.executeQuery("DESCRIBE TABLE tp");
        final List<String> columns = new ArrayList<String>();
        for (final Row row : described.getRows()) {
            columns.add(row.getValue(described.getColumnIndex("name")) + " | "
                + row.getValue(described.getColumnIndex("type")) + " | "
                + row.getValue(described.getColumnIndex("null?")));
        }
        assertEquals(Arrays.asList("i | NUMBER(38,0) | N", "oi | NUMBER(38,0) | Y", "l | NUMBER(38,0) | N",
            "s | VARCHAR(16777216) | N", "os | VARCHAR(16777216) | Y", "b | BOOLEAN | N", "d | FLOAT | N",
            "f | FLOAT | N", "dec | NUMBER(9,2) | N", "decf | NUMBER(38,4) | N", "dt | DATE | N",
            "tsm | TIMESTAMP_NTZ(9) | N", "tsu | TIMESTAMP_NTZ(9) | N", "tm | TIME(9) | N", "tmu | TIME(9) | N",
            "bin | BINARY(8388608) | N", "rec | VARIANT | N", "arr | VARIANT | N", "m | VARIANT | N",
            "e | VARCHAR(16777216) | N", "Up | NUMBER(38,0) | N", "up | VARCHAR(16777216) | Y"), columns);
    }

    private List<String> infer(final String arguments) {
        return lines("SELECT COLUMN_NAME, TYPE, NULLABLE, EXPRESSION, FILENAMES, ORDER_ID FROM TABLE(INFER_SCHEMA("
            + arguments + ")) ORDER BY ORDER_ID, COLUMN_NAME");
    }

    /** A query's rows, each cell as text, joined by {@code " | "}; a FILENAMES list sorted. */
    private List<String> lines(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> lines = new ArrayList<String>();
        for (final Row row : rs.getRows()) {
            final StringBuilder line = new StringBuilder();
            for (int i = 0; i < rs.getColumns().size(); i++) {
                if (i > 0) {
                    line.append(" | ");
                }
                final Object value = row.getValue(i);
                String text = value == null ? "NULL" : value.toString();
                if (value instanceof Boolean) {
                    text = text.toUpperCase(Locale.ROOT);
                }
                if (text.contains(", ") && !text.contains("(")) {
                    final List<String> files = new ArrayList<String>(Arrays.asList(text.split(", ")));
                    Collections.sort(files);
                    text = String.join(", ", files);
                }
                line.append(text);
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private void writeParquet(final String name, final String schemaText) throws IOException {
        final Schema schema = new Schema.Parser().parse(schemaText);
        final Path target = stageDir.resolve(name);
        Files.createDirectories(target.getParent());
        try (ParquetWriter<GenericRecord> writer =
                 AvroParquetWriter.<GenericRecord>builder(new LocalOutputFile(target)).withSchema(schema).build()) {
            writer.write(filledRecord(schema));
        }
    }

    private void writeAvro(final String name, final String schemaText) throws IOException {
        final Schema schema = new Schema.Parser().parse(schemaText);
        final Path target = stageDir.resolve(name);
        Files.createDirectories(target.getParent());
        try (DataFileWriter<GenericRecord> writer =
                 new DataFileWriter<GenericRecord>(new GenericDatumWriter<GenericRecord>(schema))) {
            writer.create(schema, target.toFile());
            writer.append(filledRecord(schema));
        }
    }

    private void writeOrc(final String name, final String typeText) throws IOException {
        final Path target = stageDir.resolve(name);
        Files.createDirectories(target.getParent());
        final Configuration conf = new Configuration();
        // RawLocalFileSystem writes no ".name.crc" checksum sidecar beside the file.
        conf.set("fs.file.impl", "org.apache.hadoop.fs.RawLocalFileSystem");
        // The file declares its columns in its footer; INFER_SCHEMA reads nothing else of it.
        final Writer writer = OrcFile.createWriter(new org.apache.hadoop.fs.Path(target.toUri()),
            OrcFile.writerOptions(conf).setSchema(TypeDescription.fromString(typeText)).overwrite(true));
        writer.close();
    }

    /** A record whose every field holds a value of its type, a nullable one NULL. */
    private static GenericRecord filledRecord(final Schema schema) {
        final GenericRecord record = new GenericData.Record(schema);
        for (final Schema.Field field : schema.getFields()) {
            record.put(field.name(), valueOf(field.schema()));
        }
        return record;
    }

    private static Object valueOf(final Schema schema) {
        switch (schema.getType()) {
            case INT:
                return Integer.valueOf(1);
            case LONG:
                return Long.valueOf(1L);
            case FLOAT:
                return Float.valueOf(1.5f);
            case DOUBLE:
                return Double.valueOf(2.5);
            case BOOLEAN:
                return Boolean.TRUE;
            case STRING:
                return "x";
            case BYTES:
                return ByteBuffer.wrap(new byte[] {1, 2});
            case FIXED:
                return new GenericData.Fixed(schema, new byte[schema.getFixedSize()]);
            case RECORD:
                return filledRecord(schema);
            case ARRAY:
                return new ArrayList<Object>(Arrays.asList(valueOf(schema.getElementType())));
            case MAP: {
                final Map<String, Object> map = new HashMap<String, Object>();
                map.put("k", valueOf(schema.getValueType()));
                return map;
            }
            case ENUM:
                return new GenericData.EnumSymbol(schema, schema.getEnumSymbols().get(0));
            default:
                return null;
        }
    }

    private static void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete() && file.exists()) {
            file.deleteOnExit();
        }
    }
}
