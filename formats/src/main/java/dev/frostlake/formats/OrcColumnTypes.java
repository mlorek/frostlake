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
import org.apache.hadoop.conf.Configuration;
import org.apache.orc.OrcFile;
import org.apache.orc.Reader;
import org.apache.orc.TypeDescription;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The columns INFER_SCHEMA reads off an ORC file's type description — the top-level struct's fields, every one
 * nullable. Every integer width is NUMBER(38, 0), FLOAT and DOUBLE are REAL, STRING, CHAR and VARCHAR are TEXT, a
 * DECIMAL keeps its precision and scale, a TIMESTAMP is TIMESTAMP_NTZ, a LIST is ARRAY, and a STRUCT, a MAP or a
 * UNION is VARIANT. An Iceberg table asks for the same types.
 */
final class OrcColumnTypes {

    private OrcColumnTypes() {
    }

    /**
     * The file's top-level columns.
     *
     * @param file the staged file
     * @return the columns, empty when the file is no ORC file
     */
    static List<StagedFileColumn> read(final Path file) {
        final TypeDescription schema;
        // org.apache.hadoop.fs.Path collides with java.nio.file.Path (the parameter), so it is qualified inline.
        final org.apache.hadoop.fs.Path hadoopPath = new org.apache.hadoop.fs.Path(file.toUri());
        try (Reader reader = OrcFile.createReader(hadoopPath, OrcFile.readerOptions(new Configuration()))) {
            schema = reader.getSchema();
        } catch (final IOException | RuntimeException notOrc) {
            return new ArrayList<StagedFileColumn>();
        }
        final List<StagedFileColumn> columns = new ArrayList<StagedFileColumn>();
        if (schema.getCategory() != TypeDescription.Category.STRUCT) {
            return columns;
        }
        final List<String> names = schema.getFieldNames();
        final List<TypeDescription> types = schema.getChildren();
        for (int i = 0; i < names.size(); i++) {
            columns.add(new StagedFileColumn(names.get(i), typeOf(types.get(i)), true));
        }
        return columns;
    }

    private static String typeOf(final TypeDescription type) {
        switch (type.getCategory()) {
            case BYTE:
            case SHORT:
            case INT:
            case LONG:
                return "NUMBER(38, 0)";
            case FLOAT:
            case DOUBLE:
                return "REAL";
            case BOOLEAN:
                return "BOOLEAN";
            case STRING:
            case CHAR:
            case VARCHAR:
                return "TEXT";
            case BINARY:
                return "BINARY";
            case DECIMAL:
                return "NUMBER(" + type.getPrecision() + ", " + type.getScale() + ")";
            case DATE:
                return "DATE";
            case TIMESTAMP:
            case TIMESTAMP_INSTANT:
                return "TIMESTAMP_NTZ";
            case LIST:
                return "ARRAY";
            default:
                return "VARIANT";
        }
    }
}
