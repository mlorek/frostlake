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
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.io.LocalInputFile;
import org.apache.parquet.schema.LogicalTypeAnnotation.DateLogicalTypeAnnotation;
import org.apache.parquet.schema.LogicalTypeAnnotation.DecimalLogicalTypeAnnotation;
import org.apache.parquet.schema.LogicalTypeAnnotation.EnumLogicalTypeAnnotation;
import org.apache.parquet.schema.LogicalTypeAnnotation.JsonLogicalTypeAnnotation;
import org.apache.parquet.schema.LogicalTypeAnnotation.StringLogicalTypeAnnotation;
import org.apache.parquet.schema.LogicalTypeAnnotation.TimeLogicalTypeAnnotation;
import org.apache.parquet.schema.LogicalTypeAnnotation.TimestampLogicalTypeAnnotation;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The columns INFER_SCHEMA reads off a Parquet file's footer.
 *
 * <p>A column is nullable unless the file declares it REQUIRED. Its type follows the physical type and its
 * annotation: an integer is NUMBER(38, 0) whatever its width, a DECIMAL keeps its precision and scale, FLOAT and
 * DOUBLE are REAL, a STRING or ENUM is TEXT and any other byte array BINARY, DATE is DATE, TIME is TIME and a
 * TIMESTAMP is TIMESTAMP_NTZ — TIMESTAMP_LTZ when the file format asks for USE_LOGICAL_TYPE and the timestamp is
 * adjusted to UTC. A group — a struct, a list or a map — is VARIANT.
 *
 * <p>For an Iceberg table ({@code KIND => 'ICEBERG'}) the physical types keep Iceberg's names — INT, LONG, FLOAT,
 * DOUBLE — a timestamp is TIMESTAMP_LTZ(6) or TIMESTAMP_NTZ(6) and a time TIME(6), and a group is refused:
 * {@code Encountered unsupported logical type NESTED Datatype for physical type UNKNOWN}.
 */
final class ParquetColumnTypes {

    private ParquetColumnTypes() {
    }

    /**
     * The file's top-level columns.
     *
     * @param file the staged file
     * @param formatOptions the file format's options
     * @param iceberg whether Iceberg's types are asked for
     * @return the columns, empty when the file is no Parquet file
     */
    static List<StagedFileColumn> read(final Path file, final Map<String, String> formatOptions,
                                       final boolean iceberg) {
        final MessageType schema;
        try (ParquetFileReader reader = ParquetFileReader.open(new LocalInputFile(file))) {
            schema = reader.getFooter().getFileMetaData().getSchema();
        } catch (final IOException | RuntimeException notParquet) {
            return new ArrayList<StagedFileColumn>();
        }
        final boolean logicalTypes = "TRUE".equalsIgnoreCase(formatOptions.get("USE_LOGICAL_TYPE"));
        final List<StagedFileColumn> columns = new ArrayList<StagedFileColumn>();
        for (final Type field : schema.getFields()) {
            columns.add(new StagedFileColumn(field.getName(), typeOf(field, logicalTypes, iceberg),
                field.getRepetition() != Type.Repetition.REQUIRED));
        }
        return columns;
    }

    private static String typeOf(final Type field, final boolean logicalTypes, final boolean iceberg) {
        if (!field.isPrimitive()) {
            if (iceberg) {
                throw new RuntimeException("Encountered unsupported logical type NESTED Datatype for physical type"
                    + " UNKNOWN");
            }
            return "VARIANT";
        }
        final PrimitiveType primitive = field.asPrimitiveType();
        final LogicalTypeAnnotation annotation = primitive.getLogicalTypeAnnotation();
        if (annotation instanceof DecimalLogicalTypeAnnotation) {
            final DecimalLogicalTypeAnnotation decimal = (DecimalLogicalTypeAnnotation) annotation;
            return "NUMBER(" + decimal.getPrecision() + ", " + decimal.getScale() + ")";
        }
        if (annotation instanceof DateLogicalTypeAnnotation) {
            return "DATE";
        }
        if (annotation instanceof TimeLogicalTypeAnnotation) {
            return iceberg ? "TIME(6)" : "TIME";
        }
        if (annotation instanceof TimestampLogicalTypeAnnotation) {
            final boolean utc = ((TimestampLogicalTypeAnnotation) annotation).isAdjustedToUTC();
            if (iceberg) {
                return utc ? "TIMESTAMP_LTZ(6)" : "TIMESTAMP_NTZ(6)";
            }
            return logicalTypes && utc ? "TIMESTAMP_LTZ" : "TIMESTAMP_NTZ";
        }
        if (annotation instanceof StringLogicalTypeAnnotation || annotation instanceof EnumLogicalTypeAnnotation
                || annotation instanceof JsonLogicalTypeAnnotation) {
            return "TEXT";
        }
        switch (primitive.getPrimitiveTypeName()) {
            case INT32:
                return iceberg ? "INT" : "NUMBER(38, 0)";
            case INT64:
                return iceberg ? "LONG" : "NUMBER(38, 0)";
            case INT96:
                return "TIMESTAMP_NTZ";
            case FLOAT:
                return iceberg ? "FLOAT" : "REAL";
            case DOUBLE:
                return iceberg ? "DOUBLE" : "REAL";
            case BOOLEAN:
                return "BOOLEAN";
            default:
                return "BINARY";
        }
    }
}
