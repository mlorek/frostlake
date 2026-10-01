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

import dev.frostlake.executor.copy.StagedFileColumn;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The columns INFER_SCHEMA reads off an Avro container file's schema — the top-level record's fields.
 *
 * <p>A field is nullable when its type is a union with {@code null}, and its type is read from the Avro type
 * alone, the logical type left aside: {@code int} and {@code long} are NUMBER(38, 0) (a date or a timestamp
 * included), {@code float} and {@code double} REAL, {@code string} TEXT, {@code bytes} and {@code fixed} BINARY
 * (a decimal included), {@code array} ARRAY, and a record, a map or an enum VARIANT. An Iceberg table asks for
 * the same types.
 */
final class AvroColumnTypes {

    private AvroColumnTypes() {
    }

    /**
     * The file's top-level columns.
     *
     * @param file the staged file
     * @return the columns, empty when the file is no Avro container file
     */
    static List<StagedFileColumn> read(final Path file) {
        final Schema schema;
        try (DataFileReader<GenericRecord> reader =
                 new DataFileReader<GenericRecord>(file.toFile(), new GenericDatumReader<GenericRecord>())) {
            schema = reader.getSchema();
        } catch (final IOException | RuntimeException notAvro) {
            return new ArrayList<StagedFileColumn>();
        }
        final List<StagedFileColumn> columns = new ArrayList<StagedFileColumn>();
        if (schema.getType() != Schema.Type.RECORD) {
            return columns;
        }
        for (final Schema.Field field : schema.getFields()) {
            Schema type = field.schema();
            boolean nullable = false;
            if (type.getType() == Schema.Type.UNION) {
                Schema only = null;
                int others = 0;
                for (final Schema member : type.getTypes()) {
                    if (member.getType() == Schema.Type.NULL) {
                        nullable = true;
                    } else {
                        only = member;
                        others++;
                    }
                }
                type = others == 1 ? only : type;
            }
            columns.add(new StagedFileColumn(field.name(), typeOf(type), nullable));
        }
        return columns;
    }

    private static String typeOf(final Schema type) {
        switch (type.getType()) {
            case INT:
            case LONG:
                return "NUMBER(38, 0)";
            case FLOAT:
            case DOUBLE:
                return "REAL";
            case BOOLEAN:
                return "BOOLEAN";
            case STRING:
                return "TEXT";
            case BYTES:
            case FIXED:
                return "BINARY";
            case ARRAY:
                return "ARRAY";
            default:
                return "VARIANT";
        }
    }
}
