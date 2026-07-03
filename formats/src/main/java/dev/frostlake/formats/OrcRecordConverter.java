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

import org.apache.hadoop.hive.ql.exec.vector.BytesColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.ColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.DecimalColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.DoubleColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.ListColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.LongColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.MapColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.StructColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.TimestampColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.orc.TypeDescription;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Converts one row of an ORC {@link VectorizedRowBatch} (column-vector layout) to a {@link JsonNode} object,
 * driven by the file's {@link TypeDescription}. Nested struct/list/map recurse; bytes are base64; dates and
 * timestamps become ISO strings. So ORC loads through the same record path as JSON/Avro/Parquet.
 */
public final class OrcRecordConverter {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private OrcRecordConverter() {
    }

    /** The {@code row}-th row of {@code batch} as a JSON object (the root schema is a struct of columns). */
    public static ObjectNode rowToJson(final TypeDescription schema, final VectorizedRowBatch batch, final int row) {
        final ObjectNode object = NODES.objectNode();
        final List<String> names = schema.getFieldNames();
        final List<TypeDescription> children = schema.getChildren();
        for (int i = 0; i < names.size(); i++) {
            object.set(names.get(i), columnValue(batch.cols[i], row, children.get(i)));
        }
        return object;
    }

    private static JsonNode columnValue(final ColumnVector vector, final int row, final TypeDescription type) {
        final int idx = vector.isRepeating ? 0 : row;
        if (!vector.noNulls && vector.isNull[idx]) {
            return NODES.nullNode();
        }
        switch (type.getCategory()) {
            case BOOLEAN:
                return NODES.booleanNode(((LongColumnVector) vector).vector[idx] != 0);
            case BYTE:
            case SHORT:
            case INT:
            case LONG:
                return NODES.numberNode(((LongColumnVector) vector).vector[idx]);
            case FLOAT:
            case DOUBLE:
                return NODES.numberNode(((DoubleColumnVector) vector).vector[idx]);
            case STRING:
            case VARCHAR:
            case CHAR:
                return NODES.textNode(utf8((BytesColumnVector) vector, idx));
            case BINARY: {
                final BytesColumnVector bytes = (BytesColumnVector) vector;
                final byte[] slice = Arrays.copyOfRange(bytes.vector[idx], bytes.start[idx],
                    bytes.start[idx] + bytes.length[idx]);
                return NODES.textNode(Base64.getEncoder().encodeToString(slice));
            }
            case DECIMAL:
                return NODES.numberNode(((DecimalColumnVector) vector).vector[idx].getHiveDecimal().bigDecimalValue());
            case DATE:
                return NODES.textNode(LocalDate.ofEpochDay(((LongColumnVector) vector).vector[idx]).toString());
            case TIMESTAMP: {
                final TimestampColumnVector ts = (TimestampColumnVector) vector;
                final long seconds = Math.floorDiv(ts.time[idx], 1000L);
                return NODES.textNode(LocalDateTime.ofEpochSecond(seconds, ts.nanos[idx], ZoneOffset.UTC).toString());
            }
            case STRUCT: {
                final StructColumnVector struct = (StructColumnVector) vector;
                final ObjectNode object = NODES.objectNode();
                final List<String> names = type.getFieldNames();
                final List<TypeDescription> children = type.getChildren();
                for (int i = 0; i < names.size(); i++) {
                    object.set(names.get(i), columnValue(struct.fields[i], idx, children.get(i)));
                }
                return object;
            }
            case LIST: {
                final ListColumnVector list = (ListColumnVector) vector;
                final ArrayNode array = NODES.arrayNode();
                final TypeDescription element = type.getChildren().get(0);
                final int offset = (int) list.offsets[idx];
                final int length = (int) list.lengths[idx];
                for (int e = 0; e < length; e++) {
                    array.add(columnValue(list.child, offset + e, element));
                }
                return array;
            }
            case MAP: {
                final MapColumnVector map = (MapColumnVector) vector;
                final ObjectNode object = NODES.objectNode();
                final TypeDescription keyType = type.getChildren().get(0);
                final TypeDescription valueType = type.getChildren().get(1);
                final int offset = (int) map.offsets[idx];
                final int length = (int) map.lengths[idx];
                for (int e = 0; e < length; e++) {
                    final JsonNode key = columnValue(map.keys, offset + e, keyType);
                    object.set(key.asString(), columnValue(map.values, offset + e, valueType));
                }
                return object;
            }
            default:
                return NODES.textNode(vector.toString());
        }
    }

    private static String utf8(final BytesColumnVector bytes, final int idx) {
        return new String(bytes.vector[idx], bytes.start[idx], bytes.length[idx], StandardCharsets.UTF_8);
    }
}
